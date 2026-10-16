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
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * The lobby lives on the host's phone: this server accepts the other players' sockets, feeds their actions
 * into the [GameEngine] and sends each of them their personal [GameView] after every change.
 * The host's own UI connects to it like any other player, over the loopback interface.
 */
class HostServer private constructor(
    private val serverSocket: ServerSocket,
    private val engine: GameEngine,
) : Closeable {

    private class Connection(val socket: Socket) {
        val outbox = Channel<String>(Channel.UNLIMITED)
        fun send(message: ServerMessage) {
            outbox.trySend(Protocol.encode(message))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val connections = mutableMapOf<Int, Connection>()
    private val chat = ArrayDeque<ChatMessage>()
    private var nextChatId = 1L

    val port: Int get() = serverSocket.localPort

    private fun start() {
        scope.launch { acceptLoop() }
        scope.launch { tickLoop() }
        scope.launch { pingLoop() }
    }

    private suspend fun acceptLoop() {
        while (scope.isActive) {
            val socket = try {
                serverSocket.accept()
            } catch (e: IOException) {
                break
            }
            scope.launch { handle(socket) }
        }
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(TICK_MS)
            mutex.withLock { if (engine.tick()) publishLocked() }
        }
    }

    private suspend fun pingLoop() {
        while (scope.isActive) {
            delay(Protocol.PING_INTERVAL_MS)
            mutex.withLock { connections.values.forEach { it.send(ServerMessage.Ping) } }
        }
    }

    private suspend fun handle(socket: Socket) {
        val connection = Connection(socket)
        var playerId: Int? = null
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = Protocol.READ_TIMEOUT_MS
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            scope.launch {
                try {
                    for (line in connection.outbox) {
                        writer.write(line)
                        writer.write("\n")
                        if (connection.outbox.isEmpty) writer.flush()
                    }
                    writer.flush()
                } catch (_: IOException) {
                } finally {
                    runCatching { socket.close() }
                }
            }

            val hello = readMessage(reader) as? ClientMessage.Hello
            if (hello == null) {
                connection.outbox.close()
                return
            }
            if (hello.version != Protocol.VERSION) {
                connection.send(ServerMessage.Rejected("This lobby runs a different version of the app."))
                connection.outbox.close()
                return
            }
            mutex.withLock {
                when (val result = engine.join(hello.name)) {
                    is GameEngine.JoinResult.Rejected -> {
                        connection.send(ServerMessage.Rejected(result.reason))
                        connection.outbox.close()
                    }
                    is GameEngine.JoinResult.Accepted -> {
                        connections.remove(result.playerId)?.outbox?.close()
                        connections[result.playerId] = connection
                        playerId = result.playerId
                        connection.send(ServerMessage.Welcome(result.playerId))
                        connection.send(ServerMessage.ChatHistory(chat.toList()))
                        publishLocked()
                    }
                }
            }
            val id = playerId ?: return
            while (scope.isActive) {
                val message = readMessage(reader) ?: break
                mutex.withLock { onMessage(id, connection, message) }
            }
        } catch (_: IOException) {
        } finally {
            val id = playerId
            if (id != null) {
                mutex.withLock {
                    if (connections[id] === connection) {
                        connections.remove(id)
                        engine.disconnect(id)
                        publishLocked()
                    }
                }
            }
            connection.outbox.close()
        }
    }

    /** Reads the next non-ping message; returns null at end of stream. Blocking, so only call on the IO pool. */
    private fun readMessage(reader: BufferedReader): ClientMessage? {
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isBlank()) continue
            val message = runCatching { Protocol.decodeClient(line) }.getOrNull() ?: continue
            if (message != ClientMessage.Ping) return message
        }
    }

    private fun onMessage(id: Int, connection: Connection, message: ClientMessage) {
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
            is ClientMessage.Hello, ClientMessage.Ping -> return
        }
        if (error != null) connection.send(ServerMessage.Error(error))
        publishLocked()
    }

    private fun postChat(senderId: Int?, senderName: String, text: String, system: Boolean) {
        val message = ChatMessage(nextChatId++, senderId, senderName, text, system)
        chat.addLast(message)
        while (chat.size > CHAT_HISTORY) chat.removeFirst()
        connections.values.forEach { it.send(ServerMessage.Chat(message)) }
    }

    private fun publishLocked() {
        engine.drainSystemMessages().forEach { postChat(null, "Game", it, system = true) }
        connections.forEach { (id, connection) -> connection.send(ServerMessage.State(engine.viewFor(id))) }
    }

    override fun close() {
        scope.cancel()
        runCatching { serverSocket.close() }
        connections.values.forEach { runCatching { it.socket.close() } }
    }

    companion object {
        private const val TICK_MS = 200L
        private const val CHAT_HISTORY = 200

        /** Binds the first free port of the lobby range on all interfaces and starts serving. */
        fun start(engine: GameEngine): HostServer {
            var lastError: IOException? = null
            for (offset in 0 until LobbyCode.PORT_COUNT) {
                val socket = ServerSocket()
                try {
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(LobbyCode.BASE_PORT + offset))
                    return HostServer(socket, engine).also { it.start() }
                } catch (e: IOException) {
                    lastError = e
                    runCatching { socket.close() }
                }
            }
            throw IOException("No free port to host a lobby", lastError)
        }
    }
}
