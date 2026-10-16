package com.destrukter.chameleon.core.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.random.Random

class LobbyCodeTest {

    private fun ip(text: String) = InetAddress.getByName(text) as Inet4Address

    @Test
    fun roundTrips() {
        val random = Random(42)
        repeat(2000) {
            val address = (1..4).joinToString(".") { random.nextInt(256).toString() }
            val port = LobbyCode.BASE_PORT + random.nextInt(LobbyCode.PORT_COUNT)
            val code = LobbyCode.encode(ip(address), port)
            assertEquals(9, code.length)
            assertEquals(LobbyCode.Address(address, port), LobbyCode.decode(code))
            assertEquals(LobbyCode.Address(address, port), LobbyCode.decode(code.lowercase().replace("-", " ")))
        }
    }

    @Test
    fun codesOnTheSameNetworkLookDifferent() {
        val a = LobbyCode.encode(ip("192.168.0.10"), LobbyCode.BASE_PORT)
        val b = LobbyCode.encode(ip("192.168.0.11"), LobbyCode.BASE_PORT)
        assertTrue(a.take(4) != b.take(4))
    }

    @Test
    fun detectsMostTypos() {
        val code = LobbyCode.encode(ip("192.168.43.1"), LobbyCode.BASE_PORT + 3).replace("-", "")
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        var accepted = 0
        var total = 0
        for (i in code.indices) for (c in alphabet) {
            if (c == code[i]) continue
            total++
            val typo = code.substring(0, i) + c + code.substring(i + 1)
            if (LobbyCode.decode(typo) != null) accepted++
        }
        assertTrue("$accepted of $total typos accepted", accepted * 8 < total)
    }

    @Test
    fun acceptsRawAddresses() {
        assertEquals(LobbyCode.Address("10.0.2.2", 5000), LobbyCode.decode("10.0.2.2:5000"))
        assertEquals(LobbyCode.Address("10.0.2.2", LobbyCode.BASE_PORT), LobbyCode.decode("10.0.2.2"))
        assertNull(LobbyCode.decode("ABC"))
    }
}
