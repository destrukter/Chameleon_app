package com.destrukter.chameleon.core.online

import com.destrukter.chameleon.core.CategoryRepository
import com.destrukter.chameleon.core.CustomBoard
import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.testRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private var now = 1_000_000L
    private val engine = GameEngine(testRepository(), Random(7)) { now }

    private fun joinAll(vararg names: String): List<Int> =
        names.map { (engine.join(it) as GameEngine.JoinResult.Accepted).playerId }

    private fun host(ids: List<Int>) = ids.first()

    @Test
    fun rejectsDuplicateNamesIgnoringCase() {
        joinAll("Anna", "Ben")
        val result = engine.join(" anna ")
        assertTrue(result is GameEngine.JoinResult.Rejected)
        assertTrue(engine.join("") is GameEngine.JoinResult.Rejected)
    }

    @Test
    fun firstPlayerIsHostAndOnlyHostControlsGame() {
        val ids = joinAll("Anna", "Ben", "Cleo")
        assertEquals(ids[0], engine.hostId)
        assertNotNull(engine.start(ids[1]))
        assertNotNull(engine.updateSettings(ids[1], OnlineSettings(impostors = 2)))
        assertNull(engine.updateSettings(ids[0], OnlineSettings(matrixSize = MatrixSize.LARGE)))
        assertEquals(MatrixSize.LARGE, engine.settings.matrixSize)
    }

    @Test
    fun needsTwoPlayers() {
        val alone = joinAll("Anna")
        assertNotNull(engine.start(alone[0]))
    }

    @Test
    fun twoPlayerRoundForTesting() {
        val ids = joinAll("Anna", "Ben")
        assertNull(engine.start(ids[0]))
        engine.viewFor(ids[0]).turnOrder.forEach { assertNull(engine.submitClue(it, "x")) }
        val impostor = ids.single { engine.viewFor(it).amImpostor }
        val other = ids.single { it != impostor }
        assertNull(engine.vote(impostor, other))
        assertNull(engine.vote(other, impostor))
        // A 1:1 tie is broken by the start player's vote, so either player may end up accused.
        val accused = engine.viewFor(other).outcomes.single().accusedId
        assertEquals(engine.viewFor(other).turnOrder.first().let { if (it == impostor) other else impostor }, accused)
        if (accused == impostor) assertNull(engine.guess(impostor, 0))
        assertEquals(Phase.RESULTS, engine.phase)
    }

    @Test
    fun viewsHideTheSecretFromChameleons() {
        val ids = joinAll("Anna", "Ben", "Cleo", "Dan")
        assertNull(engine.start(host(ids)))
        val views = ids.map { engine.viewFor(it) }
        assertEquals(1, views.count { it.amImpostor })
        views.forEach { view ->
            assertEquals(Phase.CLUES, view.phase)
            assertEquals(16, view.matrix!!.words.size)
            if (view.amImpostor) assertNull(view.secretIndex) else assertNotNull(view.secretIndex)
        }
        assertEquals(1, views.mapNotNull { it.secretIndex }.toSet().size)
    }

    @Test
    fun fullRoundCatchingTheChameleon() {
        val ids = joinAll("Anna", "Ben", "Cleo", "Dan")
        engine.start(host(ids))
        val order = engine.viewFor(ids[0]).turnOrder
        assertEquals(ids.toSet(), order.toSet())

        assertNotNull("out of turn", engine.submitClue(order[1], "nope"))
        order.forEach { assertNull(engine.submitClue(it, "clue")) }
        val view = engine.viewFor(ids[0])
        assertEquals(Phase.VOTING, view.phase)
        assertEquals(4, view.clues.size)

        val impostor = ids.first { engine.viewFor(it).amImpostor }
        val secret = engine.viewFor(ids.first { it != impostor }).secretIndex!!
        assertNotNull("self vote", engine.vote(impostor, impostor))
        ids.forEach { voter ->
            val target = if (voter == impostor) ids.first { it != impostor } else impostor
            assertNull(engine.vote(voter, target))
        }
        assertEquals(Phase.GUESSING, engine.viewFor(ids[0]).phase)
        assertEquals(impostor, engine.viewFor(ids[0]).guesserId)
        assertNotNull(engine.guess(ids.first { it != impostor }, 0))
        val wrong = (secret + 1) % 16
        assertNull(engine.guess(impostor, wrong))

        val result = engine.viewFor(impostor).result!!
        assertEquals(Phase.RESULTS, engine.phase)
        assertTrue(result.playersWin)
        assertEquals(secret, engine.viewFor(impostor).secretIndex)
        val scores = engine.viewFor(ids[0]).players.associate { it.id to it.score }
        ids.forEach { assertEquals(if (it == impostor) 0 else 2, scores[it]) }
    }

    @Test
    fun correctGuessScoresForChameleon() {
        val ids = joinAll("Anna", "Ben", "Cleo")
        engine.start(host(ids))
        engine.viewFor(ids[0]).turnOrder.forEach { engine.submitClue(it, "x") }
        val impostor = ids.first { engine.viewFor(it).amImpostor }
        val secret = engine.viewFor(ids.first { it != impostor }).secretIndex!!
        ids.forEach { if (it != impostor) engine.vote(it, impostor) else engine.vote(it, ids.first { o -> o != impostor }) }
        engine.guess(impostor, secret)
        val result = engine.viewFor(ids[0]).result!!
        assertFalse(result.playersWin)
        assertTrue(result.impostors.single().guessedCorrectly)
        assertEquals(1, engine.viewFor(ids[0]).player(impostor)!!.score)
    }

    @Test
    fun timeoutsSkipTurnsAndCloseVoting() {
        val ids = joinAll("Anna", "Ben", "Cleo")
        engine.updateSettings(ids[0], OnlineSettings(turnSeconds = 30, voteSeconds = 60))
        engine.start(host(ids))
        val first = engine.viewFor(ids[0]).currentTurnId
        assertFalse(engine.tick())
        now += 30_000
        assertTrue(engine.tick())
        val view = engine.viewFor(ids[0])
        assertEquals(ClueEntry(first!!, null), view.clues.single())
        assertTrue(view.currentTurnId != first)
        now += 30_000; engine.tick()
        now += 30_000; engine.tick()
        assertEquals(Phase.VOTING, engine.phase)
        now += 60_000; engine.tick()
        // Nobody voted: the chameleon gets away.
        assertEquals(Phase.RESULTS, engine.phase)
        assertFalse(engine.viewFor(ids[0]).result!!.playersWin)
    }

    @Test
    fun oneVotingPhasePerChameleon() {
        val ids = joinAll("A", "B", "C", "D", "E", "F", "G")
        engine.updateSettings(ids[0], OnlineSettings(impostors = 2))
        engine.start(host(ids))
        engine.viewFor(ids[0]).turnOrder.forEach { engine.submitClue(it, "x") }
        val impostors = ids.filter { engine.viewFor(it).amImpostor }
        assertEquals(2, impostors.size)
        val innocent = ids.filter { it !in impostors }
        assertEquals(2, engine.viewFor(ids[0]).votingRounds)

        // Phase 1: the group accuses an innocent player.
        val scapegoat = innocent.first()
        ids.forEach { engine.vote(it, if (it == scapegoat) innocent[1] else scapegoat) }
        var view = engine.viewFor(ids[0])
        assertEquals(Phase.VOTING, view.phase)
        assertEquals(2, view.votingRound)
        assertFalse(scapegoat in view.eligibleVoters)
        assertFalse(scapegoat in view.candidates)
        assertNotNull(engine.vote(scapegoat, impostors[0]))

        // Phase 2: they find one chameleon, who guesses wrong; the other one escapes.
        val voters = view.eligibleVoters
        voters.forEach { engine.vote(it, if (it == impostors[0]) innocent[1] else impostors[0]) }
        assertEquals(Phase.GUESSING, engine.phase)
        engine.guess(impostors[0], -1).let { assertNotNull(it) }
        now += 60_000; engine.tick()
        view = engine.viewFor(ids[0])
        assertEquals(Phase.RESULTS, view.phase)
        val result = view.result!!
        assertFalse(result.playersWin)
        assertEquals(setOf(impostors[0]), result.impostors.filter { it.caught }.map { it.playerId }.toSet())
        assertEquals(2, view.player(impostors[1])!!.score)
        innocent.forEach { assertEquals(2, view.player(it)!!.score) }
    }

    @Test
    fun disconnectedPlayersAreSkippedAndMayRejoin() {
        val ids = joinAll("Anna", "Ben", "Cleo", "Dan")
        engine.start(host(ids))
        val order = engine.viewFor(ids[0]).turnOrder
        val leaver = order[0]
        engine.disconnect(leaver)
        assertEquals(order[1], engine.viewFor(ids[0]).currentTurnId)
        assertTrue(engine.join("Newbie") is GameEngine.JoinResult.Rejected)
        val name = engine.nameOf(leaver)!!
        val rejoin = engine.join(name) as GameEngine.JoinResult.Accepted
        assertEquals(leaver, rejoin.playerId)
        assertTrue(rejoin.reconnected)
    }

    @Test
    fun playAgainKeepsScoresAndAllowsNewPlayersBetweenRounds() {
        val ids = joinAll("Anna", "Ben", "Cleo")
        engine.start(host(ids))
        repeat(3) { now += 45_000; engine.tick() }
        now += 90_000; engine.tick()
        assertEquals(Phase.RESULTS, engine.phase)
        assertTrue(engine.join("Late") is GameEngine.JoinResult.Accepted)
        assertNull(engine.start(host(ids)))
        assertEquals(2, engine.viewFor(ids[0]).roundNumber)
        assertEquals(4, engine.viewFor(ids[0]).turnOrder.size)
        assertTrue(engine.viewFor(ids[0]).players.sumOf { it.score } >= 2)
    }

    @Test
    fun tiesAreBrokenByTheStartPlayer() {
        val ids = joinAll("A", "B", "C", "D")
        engine.start(host(ids))
        val order = engine.viewFor(ids[0]).turnOrder
        order.forEach { engine.submitClue(it, "x") }
        val (s, p1, p2, p3) = order
        engine.vote(s, p2)
        engine.vote(p1, p3)
        engine.vote(p2, p3)
        engine.vote(p3, p2)
        val outcome = engine.viewFor(ids[0]).outcomes.first()
        assertEquals(p2, outcome.accusedId)
    }

    @Test
    fun hostChoosesTopicPacks() {
        val ids = joinAll("Anna", "Ben", "Cleo")
        assertEquals(listOf("Gaming", "Other"), engine.viewFor(ids[1]).packs.map { it.name })
        assertNull(engine.updateSettings(ids[0], OnlineSettings(disabledPacks = setOf("Gaming", "Other"))))
        assertNotNull("no packs left", engine.start(ids[0]))
        assertNull(engine.updateSettings(ids[0], OnlineSettings(disabledPacks = setOf("Other", "Unknown"))))
        assertEquals(setOf("Other"), engine.settings.disabledPacks)
        assertNull(engine.start(ids[0]))
        assertEquals("Topic 1", engine.viewFor(ids[0]).matrix!!.category)
    }

    @Test
    fun hostCustomBoardIsSentToEveryPlayer() {
        val custom = CustomBoard(name = "Our Inside Jokes", size = MatrixSize.SMALL, words = (1..16).map { "Joke $it" })
        val repo = CategoryRepository.withCustomBoards(testRepository(), listOf(custom))
        val engine = GameEngine(repo, Random(5)) { now }
        val ids = listOf("Anna", "Ben", "Cleo").map { (engine.join(it) as GameEngine.JoinResult.Accepted).playerId }
        val onlyCustom = repo.packs.map { it.name }.filter { it != CategoryRepository.CUSTOM_PACK }.toSet()

        // The Custom pack has no 5x5 board, so a 5x5 game with only that pack cannot start.
        assertNull(engine.updateSettings(ids[0], OnlineSettings(matrixSize = MatrixSize.MEDIUM, disabledPacks = onlyCustom)))
        assertNotNull(engine.start(ids[0]))

        assertNull(engine.updateSettings(ids[0], OnlineSettings(matrixSize = MatrixSize.SMALL, disabledPacks = onlyCustom)))
        assertNull(engine.start(ids[0]))
        ids.forEach { id ->
            val matrix = engine.viewFor(id).matrix!!
            assertEquals("Our Inside Jokes", matrix.category)
            assertEquals(custom.words, matrix.words)
        }
    }
}
