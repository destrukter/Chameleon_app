package com.destrukter.chameleon.core.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/** The host's side of one player's connection, whatever carries it (a TCP socket, the internet relay, …). */
interface PeerLink {
    /** Queues one protocol line for the player. Must not block. */
    fun send(line: String)

    /** Drops the connection. The transport then reports [HostSession.Peer.closed]. */
    fun close()
}

/**
 * The lobby as it lives on the host's phone: it feeds every player's actions into the [GameEngine] and sends
 * each player their personal [GameView] after every change. Transports hand it connections via [connect].
 */
class HostSession(private val engine: GameEngine) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val players = mutableMapOf<Int, Peer>()
    private val chat = ArrayDeque<ChatMessage>()
    private var nextChatId = 1L

    init {
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                mutex.withLock { if (engine.tick()) publishLocked() }
            }
        }
        scope.launch {
            while (isActive) {
                delay(Protocol.PING_INTERVAL_MS)
                mutex.withLock { players.values.forEach { it.send(ServerMessage.Ping) } }
            }
        }
    }

    /** Registers a new connection. Lines it receives go to [Peer.receive]; its end to [Peer.closed]. */
    fun connect(link: PeerLink): Peer = Peer(link)

    inner class Peer internal constructor(private val link: PeerLink) {
        private val inbox = Channel<String>(Channel.UNLIMITED)
        private var playerId: Int? = null

        init {
            scope.launch { process() }
        }

        fun receive(line: String) {
            inbox.trySend(line)
        }

        fun closed() {
            inbox.close()
        }

        internal fun send(message: ServerMessage) = link.send(Protocol.encode(message))

        private suspend fun process() {
            try {
                for (line in inbox) {
                    val message = runCatching { Protocol.decodeClient(line) }.getOrNull() ?: continue
                    when {
                        message == ClientMessage.Ping -> Unit
                        message == ClientMessage.Bye -> break
                        playerId == null -> if (message is ClientMessage.Hello && !hello(message)) break
                        else -> mutex.withLock { onMessage(playerId!!, this, message) }
                    }
                }
            } finally {
                val id = playerId
                if (id != null) {
                    mutex.withLock {
                        if (players[id] === this) {
                            players.remove(id)
                            engine.disconnect(id)
                            publishLocked()
                        }
                    }
                }
                link.close()
            }
        }

        /** Returns false when the player was turned away. */
        private suspend fun hello(hello: ClientMessage.Hello): Boolean {
            if (hello.version != Protocol.VERSION) {
                send(ServerMessage.Rejected("This lobby runs a different version of the app."))
                return false
            }
            return mutex.withLock {
                when (val result = engine.join(hello.name)) {
                    is GameEngine.JoinResult.Rejected -> {
                        send(ServerMessage.Rejected(result.reason))
                        false
                    }
                    is GameEngine.JoinResult.Accepted -> {
                        players.remove(result.playerId)?.link?.close()
                        players[result.playerId] = this
                        playerId = result.playerId
                        send(ServerMessage.Welcome(result.playerId))
                        send(ServerMessage.ChatHistory(chat.toList()))
                        publishLocked()
                        true
                    }
                }
            }
        }
    }

    private fun onMessage(id: Int, peer: Peer, message: ClientMessage) {
        val error = when (message) {
            is ClientMessage.UpdateSettings -> engine.updateSettings(id, message.settings)
            ClientMessage.Start -> engine.start(id)
            is ClientMessage.SubmitClue -> engine.submitClue(id, message.word)
            is ClientMessage.Vote -> engine.vote(id, message.targetId)
            is ClientMessage.Guess -> engine.guess(id, message.index)
            ClientMessage.ReturnToLobby -> engine.returnToLobby(id)
            is ClientMessage.Chat -> {
                val text = message.text.trim().take(Protocol.MAX_CHAT_LENGTH)
                if (text.isNotEmpty()) postChat(id, engine.nameOf(id) ?: "?", text, system = false)
                return
            }
            is ClientMessage.Hello, ClientMessage.Ping, ClientMessage.Bye -> return
        }
        if (error != null) peer.send(ServerMessage.Error(error))
        publishLocked()
    }

    private fun postChat(senderId: Int?, senderName: String, text: String, system: Boolean) {
        val message = ChatMessage(nextChatId++, senderId, senderName, text, system)
        chat.addLast(message)
        while (chat.size > CHAT_HISTORY) chat.removeFirst()
        players.values.forEach { it.send(ServerMessage.Chat(message)) }
    }

    private fun publishLocked() {
        engine.drainSystemMessages().forEach { postChat(null, "Game", it, system = true) }
        players.forEach { (id, peer) -> peer.send(ServerMessage.State(engine.viewFor(id))) }
    }

    override fun close() {
        scope.cancel()
        players.values.toList().forEach { it.closed() }
    }

    private companion object {
        const val TICK_MS = 200L
        const val CHAT_HISTORY = 200
    }
}
