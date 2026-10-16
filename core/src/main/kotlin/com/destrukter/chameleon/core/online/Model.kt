package com.destrukter.chameleon.core.online

import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.PackInfo
import com.destrukter.chameleon.core.WordMatrix
import kotlinx.serialization.Serializable

@Serializable
data class OnlineSettings(
    val impostors: Int = 1,
    val matrixSize: MatrixSize = MatrixSize.SMALL,
    val turnSeconds: Int = 45,
    val voteSeconds: Int = 90,
    /** Topic packs the host switched off. Empty means every pack is in play. */
    val disabledPacks: Set<String> = emptySet(),
) {
    fun sanitized(): OnlineSettings = copy(
        impostors = impostors.coerceIn(1, MAX_IMPOSTORS),
        turnSeconds = turnSeconds.coerceIn(10, 300),
        voteSeconds = voteSeconds.coerceIn(15, 600),
    )

    companion object {
        const val MAX_IMPOSTORS = 7
        val TURN_OPTIONS = listOf(15, 30, 45, 60, 90, 120)
        val VOTE_OPTIONS = listOf(30, 60, 90, 120, 180, 300)
    }
}

@Serializable
enum class Phase { LOBBY, CLUES, VOTING, GUESSING, RESULTS }

@Serializable
data class PlayerInfo(
    val id: Int,
    val name: String,
    val connected: Boolean,
    val isHost: Boolean,
    val score: Int,
)

/** A clue given during the word phase. [word] is null when the player ran out of time or was disconnected. */
@Serializable
data class ClueEntry(val playerId: Int, val word: String?)

@Serializable
data class CastVote(val voterId: Int, val targetId: Int)

/** The outcome of one voting phase. [accusedId] is null when nobody voted. */
@Serializable
data class VoteOutcome(
    val round: Int,
    val accusedId: Int?,
    val wasImpostor: Boolean,
    val votes: List<CastVote>,
)

@Serializable
data class ImpostorResult(
    val playerId: Int,
    val caught: Boolean,
    val guessIndex: Int?,
    val guessedCorrectly: Boolean,
)

@Serializable
data class RoundResult(
    val secretIndex: Int,
    val impostors: List<ImpostorResult>,
    /** True when every chameleon was caught and none of them guessed the secret word. */
    val playersWin: Boolean,
    /** Points gained this round, by player id. */
    val pointsGained: Map<Int, Int>,
)

/**
 * Everything one player is allowed to see. The host builds a separate view per player so the secret word
 * never reaches a chameleon's device before the round is over.
 */
@Serializable
data class GameView(
    val myId: Int,
    val hostId: Int,
    val phase: Phase,
    val players: List<PlayerInfo>,
    val settings: OnlineSettings,
    /** The host's topic packs, so every player sees the same choice in the lobby. */
    val packs: List<PackInfo> = emptyList(),
    val roundNumber: Int = 0,
    val impostorCount: Int = 0,
    val matrix: WordMatrix? = null,
    val secretIndex: Int? = null,
    val amImpostor: Boolean = false,
    val amParticipant: Boolean = false,
    val turnOrder: List<Int> = emptyList(),
    val clues: List<ClueEntry> = emptyList(),
    val currentTurnId: Int? = null,
    val remainingMillis: Long? = null,
    val phaseMillis: Long? = null,
    val votingRound: Int = 0,
    val votingRounds: Int = 0,
    val candidates: List<Int> = emptyList(),
    val eligibleVoters: List<Int> = emptyList(),
    val votedIds: List<Int> = emptyList(),
    val myVote: Int? = null,
    val eliminated: List<Int> = emptyList(),
    val outcomes: List<VoteOutcome> = emptyList(),
    val guesserId: Int? = null,
    val result: RoundResult? = null,
) {
    val isHost: Boolean get() = myId == hostId
    fun player(id: Int?): PlayerInfo? = players.firstOrNull { it.id == id }
    fun nameOf(id: Int?): String = player(id)?.name ?: "?"
}

@Serializable
data class ChatMessage(
    val id: Long,
    val senderId: Int?,
    val senderName: String,
    val text: String,
    val system: Boolean = false,
)
