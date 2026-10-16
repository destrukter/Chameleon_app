package com.destrukter.chameleon.core.online

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Messages are sent as one JSON object per line over a plain TCP socket. */
object Protocol {
    const val VERSION = 1
    const val MAX_NAME_LENGTH = 16
    const val MAX_CLUE_LENGTH = 32
    const val MAX_CHAT_LENGTH = 300
    const val PING_INTERVAL_MS = 4_000L
    const val READ_TIMEOUT_MS = 15_000

    val json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(message: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), message)
    fun encode(message: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), message)
    fun decodeClient(line: String): ClientMessage = json.decodeFromString(ClientMessage.serializer(), line)
    fun decodeServer(line: String): ServerMessage = json.decodeFromString(ServerMessage.serializer(), line)
}

@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("hello")
    data class Hello(val name: String, val version: Int = Protocol.VERSION) : ClientMessage

    @Serializable @SerialName("settings")
    data class UpdateSettings(val settings: OnlineSettings) : ClientMessage

    @Serializable @SerialName("start")
    data object Start : ClientMessage

    @Serializable @SerialName("clue")
    data class SubmitClue(val word: String) : ClientMessage

    @Serializable @SerialName("vote")
    data class Vote(val targetId: Int) : ClientMessage

    @Serializable @SerialName("guess")
    data class Guess(val index: Int) : ClientMessage

    @Serializable @SerialName("chat")
    data class Chat(val text: String) : ClientMessage

    @Serializable @SerialName("lobby")
    data object ReturnToLobby : ClientMessage

    @Serializable @SerialName("ping")
    data object Ping : ClientMessage

    /** Sent when leaving on purpose, so the host does not have to wait for a timeout (matters for the relay). */
    @Serializable @SerialName("bye")
    data object Bye : ClientMessage
}

@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("welcome")
    data class Welcome(val playerId: Int) : ServerMessage

    @Serializable @SerialName("rejected")
    data class Rejected(val reason: String) : ServerMessage

    @Serializable @SerialName("state")
    data class State(val view: GameView) : ServerMessage

    @Serializable @SerialName("chat")
    data class Chat(val message: ChatMessage) : ServerMessage

    @Serializable @SerialName("history")
    data class ChatHistory(val messages: List<ChatMessage>) : ServerMessage

    @Serializable @SerialName("error")
    data class Error(val message: String) : ServerMessage

    @Serializable @SerialName("ping")
    data object Ping : ServerMessage
}
