package com.destrukter.chameleon.core.online

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Lobby codes are serverless: the code *is* the host's address on the local network. Eight base-32 characters
 * carry the IPv4 address, which of [PORT_COUNT] ports the host bound, and a checksum against typos.
 * The bits are scrambled with an invertible multiplication so codes on the same network do not look alike.
 */
object LobbyCode {
    const val BASE_PORT = 47820
    const val PORT_COUNT = 16

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val BITS = 40
    private const val MASK = (1L shl BITS) - 1
    private const val MULTIPLIER = 0x9E3779B97L
    private val INVERSE: Long = run {
        // Newton iteration for the inverse of an odd number modulo 2^40.
        var x = MULTIPLIER
        repeat(6) { x = (x * (2 - MULTIPLIER * x)) and MASK }
        x
    }

    data class Address(val host: String, val port: Int)

    fun encode(ip: Inet4Address, port: Int): String {
        val portIndex = port - BASE_PORT
        require(portIndex in 0 until PORT_COUNT) { "Port $port is outside the lobby range" }
        val ipBits = ip.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        val payload = (ipBits shl 4) or portIndex.toLong()
        val value = (((payload shl 4) or checksum(payload)) * MULTIPLIER) and MASK
        val chars = CharArray(8) { i -> ALPHABET[((value shr (35 - 5 * i)) and 31).toInt()] }
        return String(chars, 0, 4) + "-" + String(chars, 4, 4)
    }

    /** Decodes a lobby code. As a fallback for debugging, a plain `ip` or `ip:port` is accepted too. */
    fun decode(input: String): Address? {
        val trimmed = input.trim()
        Regex("""^(\d{1,3}(?:\.\d{1,3}){3})(?::(\d{1,5}))?$""").matchEntire(trimmed)?.let { m ->
            val port = m.groupValues[2].toIntOrNull() ?: BASE_PORT
            return Address(m.groupValues[1], port)
        }
        val normalized = trimmed.uppercase()
            .filter { !it.isWhitespace() && it != '-' }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
        if (normalized.size != 8) return null
        var value = 0L
        for (c in normalized) {
            val digit = ALPHABET.indexOf(c)
            if (digit < 0) return null
            value = (value shl 5) or digit.toLong()
        }
        val raw = (value * INVERSE) and MASK
        val payload = raw shr 4
        if (raw and 0xF != checksum(payload)) return null
        val portIndex = (payload and 0xF).toInt()
        val ipBits = payload shr 4
        val host = (3 downTo 0).joinToString(".") { ((ipBits shr (8 * it)) and 0xFF).toString() }
        return Address(host, BASE_PORT + portIndex)
    }

    fun format(input: String): String {
        val clean = input.uppercase().filter { it.isLetterOrDigit() }.take(8)
        return if (clean.length > 4) clean.substring(0, 4) + "-" + clean.substring(4) else clean
    }

    private fun checksum(payload: Long): Long {
        var sum = 0L
        var p = payload
        var weight = 1L
        while (p != 0L) {
            sum += (p and 0xF) * weight
            p = p shr 4
            weight += 2
        }
        return (sum xor 0x5) and 0xF
    }

    /**
     * The address other phones on the same Wi-Fi (or on this phone's hotspot) can reach.
     * Mobile-data and VPN interfaces are skipped because they are not reachable from nearby devices.
     */
    fun findLanAddress(): Inet4Address? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
        val skipped = listOf("rmnet", "ccmni", "tun", "ppp", "dummy", "v4-", "ipsec", "clat", "lo")
        val candidates = interfaces
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .filter { iface -> skipped.none { iface.name.startsWith(it) } }
            .flatMap { iface -> iface.inetAddresses.toList().filterIsInstance<Inet4Address>().map { iface.name to it } }
            .filter { (_, address) -> address.isSiteLocalAddress }
        val preferred = listOf("wlan", "swlan", "ap", "softap", "eth")
        return candidates.minByOrNull { (name, _) ->
            preferred.indexOfFirst { name.startsWith(it) }.let { if (it < 0) preferred.size else it }
        }?.second
    }

    fun loopback(): InetAddress = InetAddress.getByName("127.0.0.1")
}
