package com.destrukter.chameleon.core.online

import com.destrukter.chameleon.core.testRepository
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.Properties
import kotlin.random.Random

class RelayTest {
    private lateinit var broker: Server
    private lateinit var brokers: List<String>

    @Before
    fun startBroker() {
        val port = ServerSocket(0).use { it.localPort }
        broker = Server()
        broker.startServer(
            MemoryConfig(
                Properties().apply {
                    setProperty("host", "127.0.0.1")
                    setProperty("port", port.toString())
                    setProperty("websocket_port", "disabled")
                    setProperty("allow_anonymous", "true")
                    setProperty("persistence_enabled", "false")
                }
            )
        )
        // The first entry is unreachable, so the host has to fall back to the second broker.
        brokers = listOf("tcp://127.0.0.1:1", "tcp://127.0.0.1:$port")
    }

    @After
    fun stopBroker() {
        broker.stopServer()
    }

    @Test
    fun codesRoundTripAndRejectTypos() {
        repeat(200) {
            val code = RelayCode.generate(it % 3)
            assertEquals(14, code.length)
            assertEquals(it % 3, RelayCode.parse(code)!!.brokerIndex)
            assertEquals(RelayCode.parse(code), RelayCode.parse(code.lowercase().replace("-", " ")))
            assertTrue(LobbyCode.parseJoinCode(code) is LobbyCode.JoinTarget.Internet)
        }
        val code = RelayCode.generate(0).replace("-", "")
        val typo = code.substring(0, 5) + (if (code[5] == 'A') 'B' else 'A') + code.substring(6)
        assertNull(RelayCode.parse(typo))
        assertNull(RelayCode.parse("ABCD-EFGH"))
        assertTrue(LobbyCode.parseJoinCode("192.168.1.2") is LobbyCode.JoinTarget.Direct)
    }

    @Test
    fun channelEncryptsPerLobby() {
        val a = RelayChannel("SECRET1")
        val b = RelayChannel("SECRET2")
        val sealed = a.seal("""{"t":"ping"}""")
        assertEquals("""{"t":"ping"}""", a.open(sealed))
        assertNull(b.open(sealed))
        assertNotEquals(a.topic, b.topic)
        assertTrue(!String(sealed, Charsets.ISO_8859_1).contains("ping"))
    }

    @Test
    fun playersJoinThroughTheRelayAlongsideDirectPlayers() = runBlocking<Unit> {
        val session = HostSession(GameEngine(testRepository(), Random(3)))
        val server = HostServer.start(session)
        val relay = RelayHost.start(session, brokers)
        assertEquals(1, RelayCode.parse(relay.code)!!.brokerIndex)
        val host = GameClient(TcpClientTransport("127.0.0.1", server.port), "Host").also { it.connect() }
        val remote1 = GameClient(RelayClientTransport(relay.code, brokers), "Remote1").also { it.connect() }
        val remote2 = GameClient(RelayClientTransport(relay.code, brokers), "Remote2").also { it.connect() }
        val all = listOf(host, remote1, remote2)
        try {
            all.forEach { c -> withTimeout(15_000) { c.state.first { it.status == GameClient.Status.Connected } } }
            withTimeout(10_000) { host.state.first { it.view?.players?.size == 3 } }

            val duplicate = GameClient(RelayClientTransport(relay.code, brokers), "remote1").also { it.connect() }
            withTimeout(15_000) { duplicate.state.first { it.status is GameClient.Status.Rejected } }
            duplicate.close()

            remote1.send(ClientMessage.Chat("hi from far away"))
            withTimeout(10_000) { host.state.first { s -> s.chat.any { it.text == "hi from far away" } } }

            host.send(ClientMessage.Start)
            val views = all.map { c -> withTimeout(10_000) { c.state.first { it.view?.phase == Phase.CLUES }.view!! } }
            assertEquals(1, views.count { it.amImpostor })
            assertEquals(1, views.mapNotNull { it.secretIndex }.toSet().size)

            remote2.close()
            withTimeout(10_000) { host.state.first { s -> s.view?.players?.any { it.name == "Remote2" && !it.connected } == true } }
        } finally {
            all.forEach { it.close() }
            relay.close()
            server.close()
            session.close()
        }
    }

    @Test
    fun unknownLobbyReportsNotFound() = runBlocking<Unit> {
        val code = RelayCode.generate(1)
        val client = GameClient(RelayClientTransport(code, brokers), "Lost").also { it.connect() }
        val status = withTimeout(30_000) { client.state.first { it.status is GameClient.Status.Disconnected }.status }
        assertTrue((status as GameClient.Status.Disconnected).reason.contains("Could not find"))
        client.close()
    }
}
