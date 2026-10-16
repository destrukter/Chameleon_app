package com.destrukter.chameleon.core.online

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** How a [GameClient] reaches the host. All calls block, so they run on the IO dispatcher. */
interface ClientTransport {
    /** What to tell the player when the lobby cannot be reached at all. */
    val unreachableMessage: String

    /** Opens the connection; throws [IOException] when that fails. */
    fun connect()

    /** The next line from the host, or null when the connection ended. Throws [IOException] on a timeout. */
    fun readLine(): String?

    fun writeLine(line: String)

    fun close()
}

/** A direct TCP connection to a host on the same network (or VPN). */
class TcpClientTransport(private val host: String, private val port: Int) : ClientTransport {
    private val socket = Socket()
    private val reader by lazy { socket.getInputStream().bufferedReader(Charsets.UTF_8) }
    private val writer by lazy { socket.getOutputStream().bufferedWriter(Charsets.UTF_8) }

    override val unreachableMessage =
        "Could not reach the lobby. Make sure you are on the same Wi-Fi, hotspot or VPN as the host, " +
            "or use the host's internet code."

    override fun connect() {
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        socket.tcpNoDelay = true
        socket.soTimeout = Protocol.READ_TIMEOUT_MS
    }

    override fun readLine(): String? = reader.readLine()

    override fun writeLine(line: String) {
        synchronized(writer) {
            writer.write(line)
            writer.write("\n")
            writer.flush()
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
    }
}
