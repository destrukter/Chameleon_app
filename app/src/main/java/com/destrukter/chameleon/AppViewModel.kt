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
import com.destrukter.chameleon.core.CustomBoard
import com.destrukter.chameleon.core.CustomBoardStore
import com.destrukter.chameleon.core.LocalRound
import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.PackInfo
import com.destrukter.chameleon.core.Rules
import com.destrukter.chameleon.core.WordMatrix
import com.destrukter.chameleon.core.online.ClientMessage
import com.destrukter.chameleon.core.online.ClientTransport
import com.destrukter.chameleon.core.online.GameClient
import com.destrukter.chameleon.core.online.GameEngine
import com.destrukter.chameleon.core.online.HostServer
import com.destrukter.chameleon.core.online.HostSession
import com.destrukter.chameleon.core.online.LobbyCode
import com.destrukter.chameleon.core.online.RelayClientTransport
import com.destrukter.chameleon.core.online.RelayHost
import com.destrukter.chameleon.core.online.TcpClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

enum class Screen {
    Menu, Rules,
    LocalSetup, LocalCategory, LocalPass, LocalOverview,
    OnlineHome, OnlineSession,
    CustomBoards, CustomBoardEditor,
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("chameleon", Context.MODE_PRIVATE)
    private val boardStore = CustomBoardStore(File(application.filesDir, "custom_boards.json"))
    private var repository: CategoryRepository? = null

    var screen by mutableStateOf(Screen.Menu)
        private set

    /** Topic packs of the bundled word list plus the "Custom" pack, available once loaded. */
    var packs by mutableStateOf<List<PackInfo>>(emptyList())
        private set

    /** The boards this player designed. Only the host's own boards are used in multiplayer. */
    var customBoards by mutableStateOf<List<CustomBoard>>(emptyList())
        private set

    val local = LocalController()
    val online = OnlineController()
    val editor = BoardEditor()

    init {
        viewModelScope.launch {
            customBoards = withContext(Dispatchers.IO) { boardStore.load() }
            refreshPacks()
        }
    }

    private suspend fun bundled(): CategoryRepository =
        repository ?: withContext(Dispatchers.IO) { CategoryRepository.loadBundled() }.also { repository = it }

    /** The bundled topics plus this player's custom boards. */
    private suspend fun repo(): CategoryRepository = CategoryRepository.withCustomBoards(bundled(), customBoards)

    private suspend fun refreshPacks() {
        packs = repo().packs
    }

    fun navigate(target: Screen) {
        screen = target
    }

    /** Handles the system back button. Returns false when the app should close. */
    fun back(): Boolean {
        screen = when (screen) {
            Screen.Menu -> return false
            Screen.Rules, Screen.LocalSetup, Screen.OnlineHome, Screen.CustomBoards -> Screen.Menu
            Screen.CustomBoardEditor -> Screen.CustomBoards
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
        /** Packs switched off for the local game; new packs added in an update start switched on. */
        var disabledPacks by mutableStateOf(prefs.getStringSet(KEY_LOCAL_DISABLED_PACKS, null).orEmpty().toSet())
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

        fun togglePack(pack: String) {
            savePacks(if (pack in disabledPacks) disabledPacks - pack else disabledPacks + pack)
        }

        fun setAllPacks(enabled: Boolean) {
            savePacks(if (enabled) emptySet() else packs.map { it.name }.toSet())
        }

        private fun savePacks(updated: Set<String>) {
            disabledPacks = updated
            error = null
            prefs.edit().putStringSet(KEY_LOCAL_DISABLED_PACKS, updated).apply()
        }

        /** Counts the entered names and moves on to picking a topic. */
        fun start() {
            if (names.size < Rules.MIN_PLAYERS) {
                error = "Add at least ${Rules.MIN_PLAYERS} players (you have ${names.size})."
                return
            }
            if (packs.isNotEmpty() && packs.all { it.name in disabledPacks }) {
                error = "Pick at least one topic pack."
                return
            }
            error = null
            updateChameleons(chameleons)
            rejectedCategories.clear()
            viewModelScope.launch {
                val repository = repo()
                if (!repository.hasTopics(size, disabledPacks)) {
                    error = "The selected packs have no ${size.dimension}×${size.dimension} boards."
                    return@launch
                }
                matrix = repository.deal(size, random, disabledPacks = disabledPacks)
                screen = Screen.LocalCategory
            }
        }

        fun reshuffle() {
            val current = matrix ?: return
            rejectedCategories += current.category
            viewModelScope.launch { matrix = repo().deal(size, random, rejectedCategories, disabledPacks) }
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

    /** A code players can type to join, with where it works ("Over the internet", "Same Wi-Fi or hotspot", …). */
    data class ShareCode(val label: String, val code: String)

    sealed interface RelayStatus {
        data object Off : RelayStatus
        data object Connecting : RelayStatus
        data object Ready : RelayStatus
        data class Failed(val message: String) : RelayStatus
    }

    /**
     * Multiplayer: one phone hosts the lobby. Others join directly over the local network or a VPN, or through
     * a public internet relay when they are somewhere else.
     */
    inner class OnlineController {
        var name by mutableStateOf(prefs.getString(KEY_ONLINE_NAME, "").orEmpty())
        /** The typed lobby code without separators; the text field shows it grouped in blocks of four. */
        var code by mutableStateOf(LobbyCode.normalize(prefs.getString(KEY_LAST_CODE, "").orEmpty()))
            private set

        fun updateCode(input: String) {
            code = input.uppercase().filter { it.isLetterOrDigit() }.take(MAX_CODE_LENGTH)
        }
        var error by mutableStateOf<String?>(null)
            private set
        var busy by mutableStateOf(false)
            private set
        /** Codes for the current lobby: all ways in for the host, the code used for everyone else. */
        var codes by mutableStateOf<List<ShareCode>>(emptyList())
            private set
        var relayStatus by mutableStateOf<RelayStatus>(RelayStatus.Off)
            private set
        var client by mutableStateOf<GameClient?>(null)
            private set

        private var session: HostSession? = null
        private var server: HostServer? = null
        private var relay: RelayHost? = null
        private var directCodes: List<ShareCode> = emptyList()
        private val idleState = MutableStateFlow(GameClient.State())

        val state: StateFlow<GameClient.State> get() = client?.state ?: idleState
        val isHosting: Boolean get() = session != null

        fun host() {
            val player = validName() ?: return
            busy = true
            error = null
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val newSession = HostSession(GameEngine(repo()))
                        val newServer = try {
                            HostServer.start(newSession)
                        } catch (e: Exception) {
                            newSession.close()
                            throw e
                        }
                        val direct = LobbyCode.findHostAddresses().map {
                            ShareCode(it.label, LobbyCode.encode(it.address, newServer.port))
                        }
                        Triple(newSession, newServer, direct)
                    }
                }
                busy = false
                result.onSuccess { (newSession, newServer, direct) ->
                    session = newSession
                    server = newServer
                    directCodes = direct
                    codes = direct
                    connect(TcpClientTransport("127.0.0.1", newServer.port), player)
                    startRelay()
                }.onFailure {
                    error = it.message ?: "Could not create a lobby."
                }
            }
        }

        /** Opens the lobby to the internet through a public relay; the host can retry if it failed. */
        fun startRelay() {
            val current = session ?: return
            if (relayStatus == RelayStatus.Connecting || relayStatus == RelayStatus.Ready) return
            relayStatus = RelayStatus.Connecting
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { RelayHost.start(current) } }
                if (session !== current) {
                    result.getOrNull()?.close()
                    return@launch
                }
                result.onSuccess {
                    relay = it
                    relayStatus = RelayStatus.Ready
                    codes = listOf(ShareCode("Over the internet", it.code)) + directCodes
                }.onFailure {
                    relayStatus = RelayStatus.Failed("Couldn't reach the internet relay. Check your connection.")
                }
            }
        }

        fun join() {
            val player = validName() ?: return
            val target = LobbyCode.parseJoinCode(code)
            if (target == null) {
                error = "That lobby code is not valid. Check it for typos."
                return
            }
            error = null
            val formatted = LobbyCode.format(code)
            prefs.edit().putString(KEY_LAST_CODE, formatted).apply()
            when (target) {
                is LobbyCode.JoinTarget.Direct -> {
                    codes = listOf(ShareCode("Same network", formatted))
                    connect(TcpClientTransport(target.address.host, target.address.port), player)
                }
                is LobbyCode.JoinTarget.Internet -> {
                    codes = listOf(ShareCode("Over the internet", formatted))
                    connect(RelayClientTransport(target.code), player)
                }
            }
        }

        fun send(message: ClientMessage) {
            client?.send(message)
        }

        fun leave() {
            client?.close()
            client = null
            val oldRelay = relay
            val oldServer = server
            val oldSession = session
            relay = null
            server = null
            session = null
            codes = emptyList()
            directCodes = emptyList()
            relayStatus = RelayStatus.Off
            // Closing the relay waits for the broker, so keep it off the main thread. The client's goodbye goes first.
            if (oldRelay != null || oldServer != null || oldSession != null) {
                Thread {
                    Thread.sleep(500)
                    runCatching { oldRelay?.close() }
                    runCatching { oldServer?.close() }
                    runCatching { oldSession?.close() }
                }.start()
            }
        }

        fun leaveToHome() {
            leave()
            screen = Screen.OnlineHome
        }

        private fun connect(transport: ClientTransport, player: String) {
            client?.close()
            client = GameClient(transport, player).also { it.connect() }
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

    /** Creates, edits and deletes the player's custom boards. */
    inner class BoardEditor {
        var editingId by mutableStateOf<String?>(null)
            private set
        var name by mutableStateOf("")
        var size by mutableStateOf(MatrixSize.SMALL)
            private set
        val words = mutableStateListOf<String>()
        var error by mutableStateOf<String?>(null)
            private set

        fun create() {
            editingId = null
            name = ""
            size = MatrixSize.SMALL
            words.clear()
            repeat(size.cellCount) { words += "" }
            error = null
            screen = Screen.CustomBoardEditor
        }

        fun edit(board: CustomBoard) {
            editingId = board.id
            name = board.name
            size = board.size
            words.clear()
            words += board.words
            error = null
            screen = Screen.CustomBoardEditor
        }

        /** Changes the board size, keeping the words typed so far (extra ones are dropped from the end). */
        fun changeSize(newSize: MatrixSize) {
            val kept = words.toList().take(newSize.cellCount)
            words.clear()
            words += kept + List(newSize.cellCount - kept.size) { "" }
            size = newSize
            error = null
        }

        fun setWord(index: Int, value: String) {
            if (index in words.indices) words[index] = value.take(CustomBoard.MAX_WORD_LENGTH)
            error = null
        }

        fun save() {
            val board = CustomBoard(id = editingId ?: java.util.UUID.randomUUID().toString(), name = name, size = size, words = words.toList())
            val problem = board.problem()
            if (problem != null) {
                error = problem
                return
            }
            val cleaned = board.cleaned()
            if (customBoards.any { it.id != cleaned.id && it.name.equals(cleaned.name, ignoreCase = true) }) {
                error = "You already have a board called \"${cleaned.name}\"."
                return
            }
            persist(customBoards.filter { it.id != cleaned.id } + cleaned)
            screen = Screen.CustomBoards
        }

        fun delete(board: CustomBoard) {
            persist(customBoards.filter { it.id != board.id })
            if (editingId == board.id) screen = Screen.CustomBoards
        }

        private fun persist(boards: List<CustomBoard>) {
            customBoards = boards.sortedWith(compareBy({ it.size }, { it.name.lowercase() }))
            viewModelScope.launch {
                withContext(Dispatchers.IO) { boardStore.save(customBoards) }
                refreshPacks()
            }
        }
    }

    private companion object {
        const val MAX_CODE_LENGTH = 12
        const val KEY_NAMES = "local_names"
        const val KEY_LOCAL_CHAMELEONS = "local_chameleons"
        const val KEY_LOCAL_SIZE = "local_size"
        const val KEY_LOCAL_DISABLED_PACKS = "local_disabled_packs"
        const val KEY_ONLINE_NAME = "online_name"
        const val KEY_LAST_CODE = "last_code"
    }
}
