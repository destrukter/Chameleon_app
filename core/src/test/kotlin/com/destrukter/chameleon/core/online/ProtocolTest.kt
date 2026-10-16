package com.destrukter.chameleon.core.online

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {
    @Test
    fun messagesRoundTrip() {
        val client = listOf(
            ClientMessage.Hello("Anna"), ClientMessage.Start, ClientMessage.Vote(3),
            ClientMessage.UpdateSettings(OnlineSettings(impostors = 2)), ClientMessage.Ping,
        )
        client.forEach { assertEquals(it, Protocol.decodeClient(Protocol.encode(it))) }
        val view = GameView(myId = 1, hostId = 1, phase = Phase.LOBBY, players = emptyList(), settings = OnlineSettings())
        val result = RoundResult(2, listOf(ImpostorResult(1, true, 2, true)), false, mapOf(1 to 1))
        val server = listOf(
            ServerMessage.State(view.copy(result = result)), ServerMessage.Welcome(4), ServerMessage.Ping,
            ServerMessage.Chat(ChatMessage(1, null, "Game", "hi", system = true)),
        )
        server.forEach { assertEquals(it, Protocol.decodeServer(Protocol.encode(it))) }
    }
}
