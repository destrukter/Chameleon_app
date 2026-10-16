package com.destrukter.chameleon.core.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException

/** One player's connection to a host, over any [ClientTransport]. */
class GameClient(
    private val transport: ClientTransport,
    private val name: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {

    sealed interface Status {
        data object Connecting : Status
        data object Connected : Status
        data class Rejected(val reason: String) : Status
        data class Disconnected(val reason: String) : Status
    }

    data class State(
        val status: Status = Status.Connecting,
        val myId: Int? = null,
        val view: GameView? = null,
        /** Local clock time at which [view] arrived; timers count down from there. */
        val viewReceivedAt: Long = 0,
        val chat: List<ChatMessage> = emptyList(),
    ) {
        fun remainingMillis(now: Long): Long? =
            view?.remainingMillis?.let { (it - (now - viewReceivedAt)).coerceAtLeast(0) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = Channel<String>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(State())
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    @Volatile private var closed = false

    val state: StateFlow<State> = _state.asStateFlow()

    /** Short-lived messages from the host, e.g. "It is not your turn." */
    val errors: SharedFlow<String> = _errors

    fun connect() {
        scope.launch { run() }
    }

    fun send(message: ClientMessage) {
        outbox.trySend(Protocol.encode(message))
    }

    private suspend fun run() {
        try {
            transport.connect()
            scope.launch {
                try {
                    for (line in outbox) transport.writeLine(line)
                } catch (_: IOException) {
                }
            }
            scope.launch {
                while (isActive) {
                    delay(Protocol.PING_INTERVAL_MS)
                    send(ClientMessage.Ping)
                }
            }
            send(ClientMessage.Hello(name))
            while (scope.isActive) {
                val line = transport.readLine() ?: break
                if (line.isBlank()) continue
                val message = runCatching { Protocol.decodeServer(line) }.getOrNull() ?: continue
                onMessage(message)
                if (message is ServerMessage.Rejected) return
            }
            finish(if (_state.value.status == Status.Connecting) "Could not join the lobby." else "The host closed the lobby.")
        } catch (e: IOException) {
            finish(if (_state.value.status == Status.Connecting) transport.unreachableMessage else "Lost the connection to the host.")
        } finally {
            transport.close()
        }
    }

    private fun onMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> _state.update { it.copy(status = Status.Connected, myId = message.playerId) }
            is ServerMessage.Rejected -> _state.update { it.copy(status = Status.Rejected(message.reason)) }
            is ServerMessage.State -> _state.update { it.copy(view = message.view, viewReceivedAt = clock()) }
            is ServerMessage.ChatHistory -> _state.update { it.copy(chat = message.messages) }
            is ServerMessage.Chat -> _state.update { it.copy(chat = (it.chat + message.message).takeLast(CHAT_LIMIT)) }
            is ServerMessage.Error -> _errors.tryEmit(message.message)
            ServerMessage.Ping -> Unit
        }
    }

    private fun finish(reason: String) {
        if (closed) return
        _state.update { current ->
            if (current.status is Status.Rejected) current else current.copy(status = Status.Disconnected(reason))
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Say goodbye so the host frees our seat right away instead of waiting for a timeout,
        // then give the writer a moment to send it. close() may run on the main thread, so no I/O here.
        outbox.trySend(Protocol.encode(ClientMessage.Bye))
        outbox.close()
        scope.launch {
            delay(CLOSE_GRACE_MS)
            transport.close()
            scope.cancel()
        }
    }

    private companion object {
        const val CHAT_LIMIT = 200
        const val CLOSE_GRACE_MS = 400L
    }
}
