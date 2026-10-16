package com.destrukter.chameleon.core

import kotlin.random.Random

/** One pass-and-play round: everybody shares the phone, so there is no hidden state beyond this object. */
data class LocalRound(
    val players: List<String>,
    val matrix: WordMatrix,
    val secretIndex: Int,
    val chameleons: Set<Int>,
    val startPlayer: Int,
) {
    val secretWord: String get() = matrix.words[secretIndex]

    fun isChameleon(playerIndex: Int): Boolean = playerIndex in chameleons

    companion object {
        /**
         * Picks the secret word, the chameleons and the start player for an already accepted [matrix].
         * The requested chameleon count is clamped to [Rules.maxChameleons].
         */
        fun create(players: List<String>, chameleonCount: Int, matrix: WordMatrix, random: Random): LocalRound {
            require(players.size >= Rules.MIN_PLAYERS) { "Need at least ${Rules.MIN_PLAYERS} players" }
            val count = chameleonCount.coerceIn(1, Rules.maxChameleons(players.size))
            val chameleons = players.indices.shuffled(random).take(count).toSet()
            return LocalRound(
                players = players,
                matrix = matrix,
                secretIndex = random.nextInt(matrix.words.size),
                chameleons = chameleons,
                startPlayer = random.nextInt(players.size),
            )
        }
    }
}
