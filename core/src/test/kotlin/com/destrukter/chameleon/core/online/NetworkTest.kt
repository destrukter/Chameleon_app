package com.destrukter.chameleon.core.online

import com.destrukter.chameleon.core.testRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NetworkTest {

    private fun client(server: HostServer, name: String) =
        GameClient(TcpClientTransport("127.0.0.1", server.port), name).also { it.connect() }

    @Test
    fun playsARoundOverSockets() = runBlocking<Unit> {
        val session = HostSession(GameEngine(testRepository(), Random(1)))
        val server = HostServer.start(session)
        val clients = listOf("Host", "Ben", "Cleo").map { name ->
            client(server, name).also { c -> withTimeout(5000) { c.state.first { it.status == GameClient.Status.Connected } } }
        }
        try {
            val duplicate = client(server, "ben")
            val status = withTimeout(5000) { duplicate.state.first { it.status is GameClient.Status.Rejected }.status }
            assertTrue((status as GameClient.Status.Rejected).reason.contains("taken"))
            duplicate.close()

            clients[0].send(ClientMessage.Chat("hello all"))
            withTimeout(5000) { clients[2].state.first { s -> s.chat.any { it.text == "hello all" && it.senderName == "Host" } } }

            clients[0].send(ClientMessage.Start)
            val order = withTimeout(5000) { clients[1].state.first { it.view?.phase == Phase.CLUES }.view!!.turnOrder }
            for (id in order) {
                val c = clients.first { it.state.value.myId == id }
                withTimeout(5000) { c.state.first { it.view?.currentTurnId == id } }
                c.send(ClientMessage.SubmitClue("word$id"))
            }
            val voting = withTimeout(5000) { clients[0].state.first { it.view?.phase == Phase.VOTING }.view!! }
            assertEquals(3, voting.clues.size)
            val secretViews = clients.map { c -> withTimeout(5000) { c.state.first { it.view?.phase == Phase.VOTING }.view!! } }
            val impostor = secretViews.single { it.amImpostor }
            assertNull(impostor.secretIndex)

            secretViews.forEach { v ->
                val target = if (v.myId == impostor.myId) v.turnOrder.first { it != v.myId } else impostor.myId
                clients.first { it.state.value.myId == v.myId }.send(ClientMessage.Vote(target))
            }
            val impostorClient = clients.first { it.state.value.myId == impostor.myId }
            withTimeout(5000) { impostorClient.state.first { it.view?.phase == Phase.GUESSING } }
            impostorClient.send(ClientMessage.Guess(0))
            val result = withTimeout(5000) { clients[1].state.first { it.view?.phase == Phase.RESULTS }.view!! }
            assertEquals(impostor.myId, result.result!!.impostors.single().playerId)
            assertTrue(result.secretIndex != null)
        } finally {
            clients.forEach { it.close() }
            server.close()
            session.close()
        }
    }

    @Test
    fun clientsNoticeWhenTheHostLeaves() = runBlocking<Unit> {
        val session = HostSession(GameEngine(testRepository(), Random(1)))
        val server = HostServer.start(session)
        val c = client(server, "Ben")
        withTimeout(5000) { c.state.first { it.status == GameClient.Status.Connected } }
        server.close()
        session.close()
        withTimeout(5000) { c.state.first { it.status is GameClient.Status.Disconnected } }
        c.close()
    }

    @Test
    fun leavingFreesTheNameRightAway() = runBlocking<Unit> {
        val session = HostSession(GameEngine(testRepository(), Random(1)))
        val server = HostServer.start(session)
        try {
            val host = client(server, "Host")
            withTimeout(5000) { host.state.first { it.status == GameClient.Status.Connected } }
            val ben = client(server, "Ben")
            withTimeout(5000) { ben.state.first { it.status == GameClient.Status.Connected } }
            ben.close()
            withTimeout(5000) { host.state.first { s -> s.view?.players?.size == 1 } }
            val again = client(server, "Ben")
            withTimeout(5000) { again.state.first { it.status == GameClient.Status.Connected } }
            host.close()
            again.close()
        } finally {
            server.close()
            session.close()
        }
    }
}
