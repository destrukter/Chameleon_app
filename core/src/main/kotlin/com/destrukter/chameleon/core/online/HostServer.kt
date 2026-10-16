package com.destrukter.chameleon.core.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Direct connections: accepts other phones' TCP sockets on the local network (or a VPN such as Tailscale)
 * and plugs each one into the [HostSession]. The host's own UI connects to it over the loopback interface.
 */
class HostServer private constructor(
    private val serverSocket: ServerSocket,
    private val session: HostSession,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sockets = mutableSetOf<Socket>()

    val port: Int get() = serverSocket.localPort

    private fun start() {
        scope.launch {
            while (isActive) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    break
                }
                scope.launch { handle(socket) }
            }
        }
    }

    private fun handle(socket: Socket) {
        synchronized(sockets) { sockets += socket }
        val outbox = Channel<String>(Channel.UNLIMITED)
        val link = object : PeerLink {
            override fun send(line: String) {
                outbox.trySend(line)
            }

            override fun close() {
                // Let queued lines (e.g. a rejection) go out first; the writer closes the socket.
                outbox.close()
            }
        }
        val peer = session.connect(link)
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = Protocol.READ_TIMEOUT_MS
            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            scope.launch {
                try {
                    for (line in outbox) {
                        writer.write(line)
                        writer.write("\n")
                        if (outbox.isEmpty) writer.flush()
                    }
                    writer.flush()
                } catch (_: IOException) {
                } finally {
                    runCatching { socket.close() }
                }
            }
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) peer.receive(line)
            }
        } catch (_: IOException) {
        } finally {
            peer.closed()
            synchronized(sockets) { sockets -= socket }
        }
    }

    override fun close() {
        scope.cancel()
        runCatching { serverSocket.close() }
        synchronized(sockets) { sockets.toList() }.forEach { runCatching { it.close() } }
    }

    companion object {
        /** Binds the first free port of the lobby range on all interfaces and starts serving. */
        fun start(session: HostSession): HostServer {
            var lastError: IOException? = null
            for (offset in 0 until LobbyCode.PORT_COUNT) {
                val socket = ServerSocket()
                try {
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(LobbyCode.BASE_PORT + offset))
                    return HostServer(socket, session).also { it.start() }
                } catch (e: IOException) {
                    lastError = e
                    runCatching { socket.close() }
                }
            }
            throw IOException("No free port to host a lobby", lastError)
        }
    }
}
