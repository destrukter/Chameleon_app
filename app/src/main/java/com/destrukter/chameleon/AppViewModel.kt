package com.destrukter.chameleon

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.destrukter.chameleon.core.CategoryRepository
import com.destrukter.chameleon.core.LocalRound
import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.Rules
import com.destrukter.chameleon.core.WordMatrix
import com.destrukter.chameleon.core.online.ClientMessage
import com.destrukter.chameleon.core.online.GameClient
import com.destrukter.chameleon.core.online.GameEngine
import com.destrukter.chameleon.core.online.HostServer
import com.destrukter.chameleon.core.online.LobbyCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Screen {
    Menu, Rules,
    LocalSetup, LocalCategory, LocalPass, LocalOverview,
    OnlineHome, OnlineSession,
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("chameleon", Context.MODE_PRIVATE)
    private var repository: CategoryRepository? = null

    var screen by mutableStateOf(Screen.Menu)
        private set

    val local = LocalController()
    val online = OnlineController()

    init {
        viewModelScope.launch {
            repository = withContext(Dispatchers.IO) { CategoryRepository.loadBundled() }
        }
    }

    private suspend fun repo(): CategoryRepository =
        repository ?: withContext(Dispatchers.IO) { CategoryRepository.loadBundled() }.also { repository = it }

    fun navigate(target: Screen) {
        screen = target
    }

    /** Handles the system back button. Returns false when the app should close. */
    fun back(): Boolean {
        screen = when (screen) {
            Screen.Menu -> return false
            Screen.Rules, Screen.LocalSetup, Screen.OnlineHome -> Screen.Menu
            Screen.LocalCategory -> Screen.LocalSetup
            Screen.LocalPass, Screen.LocalOverview -> Screen.LocalSetup
            Screen.OnlineSession -> {
                online.leave()
                Screen.OnlineHome
            }
        }
        return true
    }

    override fun onCleared() {
        online.leave()
    }

    /** Pass-and-play on a single phone. */
    inner class LocalController {
        val names = mutableStateListOf<String>().apply {
            addAll(prefs.getString(KEY_NAMES, "").orEmpty().split('\n').filter { it.isNotBlank() })
        }
        var chameleons by mutableIntStateOf(prefs.getInt(KEY_LOCAL_CHAMELEONS, 1))
            private set
        var size by mutableStateOf(
            runCatching { MatrixSize.valueOf(prefs.getString(KEY_LOCAL_SIZE, null) ?: "") }.getOrDefault(MatrixSize.SMALL)
        )
            private set
        var error by mutableStateOf<String?>(null)
            private set
        var matrix by mutableStateOf<WordMatrix?>(null)
            private set
        var round by mutableStateOf<LocalRound?>(null)
            private set
        var passIndex by mutableIntStateOf(0)
            private set
        var revealed by mutableStateOf(false)
            private set
        var showSolution by mutableStateOf(false)

        private val rejectedCategories = mutableSetOf<String>()
        private val random = Random(System.nanoTime())

        fun addName(raw: String): Boolean {
            val name = raw.trim().replace(Regex("\\s+"), " ")
            error = when {
                name.isEmpty() -> null
                names.any { it.equals(name, ignoreCase = true) } -> "\"$name\" is already playing."
                names.size >= Rules.MAX_LOCAL_PLAYERS -> "At most ${Rules.MAX_LOCAL_PLAYERS} players."
                else -> {
                    names += name
                    saveNames()
                    null
                }
            }
            return error == null && name.isNotEmpty()
        }

        fun removeName(index: Int) {
            if (index in names.indices) names.removeAt(index)
            saveNames()
        }

        fun updateChameleons(value: Int) {
            chameleons = value.coerceIn(1, Rules.maxChameleons(maxOf(names.size, Rules.MIN_PLAYERS)))
            prefs.edit().putInt(KEY_LOCAL_CHAMELEONS, chameleons).apply()
        }

        fun updateSize(value: MatrixSize) {
            size = value
            prefs.edit().putString(KEY_LOCAL_SIZE, value.name).apply()
        }

        /** Counts the entered names and moves on to picking a topic. */
        fun start() {
            if (names.size < Rules.MIN_PLAYERS) {
                error = "Add at least ${Rules.MIN_PLAYERS} players (you have ${names.size})."
                return
            }
            error = null
            updateChameleons(chameleons)
            rejectedCategories.clear()
            viewModelScope.launch {
                matrix = repo().deal(size, random)
                screen = Screen.LocalCategory
            }
        }

        fun reshuffle() {
            val current = matrix ?: return
            rejectedCategories += current.category
            viewModelScope.launch { matrix = repo().deal(size, random, rejectedCategories) }
        }

        fun accept() {
            val m = matrix ?: return
            round = LocalRound.create(names.toList(), chameleons, m, random)
            passIndex = 0
            revealed = false
            showSolution = false
            screen = Screen.LocalPass
        }

        fun reveal() {
            revealed = true
        }

        fun next() {
            val r = round ?: return
            revealed = false
            if (passIndex + 1 >= r.players.size) {
                screen = Screen.LocalOverview
            } else {
                passIndex++
            }
        }

        fun playAgain() {
            round = null
            matrix = null
            screen = Screen.LocalSetup
        }

        private fun saveNames() {
            prefs.edit().putString(KEY_NAMES, names.joinToString("\n")).apply()
            if (chameleons > Rules.maxChameleons(maxOf(names.size, Rules.MIN_PLAYERS))) updateChameleons(chameleons)
        }
    }

    /** Peer-to-peer play: one phone hosts the lobby, the others connect to it over the local network. */
    inner class OnlineController {
        var name by mutableStateOf(prefs.getString(KEY_ONLINE_NAME, "").orEmpty())
        var code by mutableStateOf(prefs.getString(KEY_LAST_CODE, "").orEmpty())
        var error by mutableStateOf<String?>(null)
            private set
        var busy by mutableStateOf(false)
            private set
        var lobbyCode by mutableStateOf<String?>(null)
            private set
        var client by mutableStateOf<GameClient?>(null)
            private set

        private var server: HostServer? = null
        private val idleState = MutableStateFlow(GameClient.State())

        val state: StateFlow<GameClient.State> get() = client?.state ?: idleState
        val isHosting: Boolean get() = server != null

        fun host() {
            val player = validName() ?: return
            busy = true
            error = null
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val address = LobbyCode.findLanAddress()
                            ?: throw IllegalStateException(
                                "No Wi-Fi network found. Connect to Wi-Fi or turn on your phone's hotspot, then try again."
                            )
                        val newServer = HostServer.start(GameEngine(repo()))
                        newServer to LobbyCode.encode(address, newServer.port)
                    }
                }
                busy = false
                result.onSuccess { (newServer, newCode) ->
                    server = newServer
                    lobbyCode = newCode
                    connect("127.0.0.1", newServer.port, player)
                }.onFailure {
                    error = it.message ?: "Could not create a lobby."
                }
            }
        }

        fun join() {
            val player = validName() ?: return
            val address = LobbyCode.decode(code)
            if (address == null) {
                error = "That lobby code is not valid. Check it for typos."
                return
            }
            error = null
            prefs.edit().putString(KEY_LAST_CODE, LobbyCode.format(code)).apply()
            lobbyCode = LobbyCode.format(code)
            connect(address.host, address.port, player)
        }

        fun send(message: ClientMessage) {
            client?.send(message)
        }

        fun leave() {
            client?.close()
            client = null
            server?.close()
            server = null
            lobbyCode = null
        }

        fun leaveToHome() {
            leave()
            screen = Screen.OnlineHome
        }

        private fun connect(host: String, port: Int, player: String) {
            client?.close()
            client = GameClient(host, port, player).also { it.connect() }
            screen = Screen.OnlineSession
        }

        private fun validName(): String? {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                error = "Please enter your name first."
                return null
            }
            prefs.edit().putString(KEY_ONLINE_NAME, trimmed).apply()
            return trimmed
        }
    }

    private companion object {
        const val KEY_NAMES = "local_names"
        const val KEY_LOCAL_CHAMELEONS = "local_chameleons"
        const val KEY_LOCAL_SIZE = "local_size"
        const val KEY_ONLINE_NAME = "online_name"
        const val KEY_LAST_CODE = "last_code"
    }
}
