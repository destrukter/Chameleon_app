package com.destrukter.chameleon.core.online

import com.destrukter.chameleon.core.CategoryRepository
import com.destrukter.chameleon.core.Rules
import com.destrukter.chameleon.core.WordMatrix
import kotlin.random.Random

/**
 * The authoritative game state, run on the host's phone. It is not thread-safe: the host server serialises
 * every call. Each mutating call returns an error message for the caller, or null on success.
 *
 * Round flow: LOBBY -> CLUES (every player gives one word in a random order, each within the turn timeout)
 * -> VOTING (repeated once per chameleon) -> GUESSING (only when a chameleon was accused) -> RESULTS.
 */
class GameEngine(
    private val repository: CategoryRepository,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface JoinResult {
        data class Accepted(val playerId: Int, val reconnected: Boolean) : JoinResult
        data class Rejected(val reason: String) : JoinResult
    }

    private class Player(val id: Int, val name: String, var connected: Boolean = true, var score: Int = 0)

    private class Round(
        val number: Int,
        val matrix: WordMatrix,
        val secretIndex: Int,
        val impostors: Set<Int>,
        val turnOrder: List<Int>,
        val votingRounds: Int,
    ) {
        var turnIndex = 0
        val clues = mutableListOf<ClueEntry>()
        var votingRound = 0
        val votes = LinkedHashMap<Int, Int>()
        val eliminated = mutableListOf<Int>()
        val outcomes = mutableListOf<VoteOutcome>()
        var guesserId: Int? = null
        val guesses = mutableMapOf<Int, Int?>()
        var result: RoundResult? = null

        val participants: Set<Int> = turnOrder.toSet()
        val currentTurnId: Int? get() = turnOrder.getOrNull(turnIndex)
    }

    private val players = LinkedHashMap<Int, Player>()
    private var nextId = 1
    private var round: Round? = null
    private var roundCounter = 0
    private val recentCategories = ArrayDeque<String>()
    private val systemMessages = mutableListOf<String>()

    var hostId: Int = -1
        private set
    var phase: Phase = Phase.LOBBY
        private set
    var settings: OnlineSettings = OnlineSettings()
        private set
    private var deadline: Long? = null
    private var phaseMillis: Long? = null

    val playerCount: Int get() = players.size

    /** Announcements produced since the last call, to be posted to the chat as system messages. */
    fun drainSystemMessages(): List<String> = systemMessages.toList().also { systemMessages.clear() }

    fun nameOf(id: Int): String? = players[id]?.name

    fun join(rawName: String): JoinResult {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return JoinResult.Rejected("Please enter a name.")
        if (name.length > Protocol.MAX_NAME_LENGTH) {
            return JoinResult.Rejected("Names can have at most ${Protocol.MAX_NAME_LENGTH} characters.")
        }
        val existing = players.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (existing.connected) return JoinResult.Rejected("The name \"$name\" is already taken in this lobby.")
            // A player who dropped out mid-round may come back under the same name.
            existing.connected = true
            systemMessages += "${existing.name} reconnected."
            return JoinResult.Accepted(existing.id, reconnected = true)
        }
        if (phase != Phase.LOBBY && phase != Phase.RESULTS) {
            return JoinResult.Rejected("A round is in progress. Try again when it is over.")
        }
        if (players.size >= Rules.MAX_ONLINE_PLAYERS) return JoinResult.Rejected("The lobby is full.")
        val player = Player(nextId++, name)
        players[player.id] = player
        if (hostId < 0) hostId = player.id
        systemMessages += "${player.name} joined."
        return JoinResult.Accepted(player.id, reconnected = false)
    }

    fun disconnect(id: Int) {
        val player = players[id] ?: return
        val r = round
        if (phase == Phase.LOBBY || phase == Phase.RESULTS || r == null || id !in r.participants) {
            players.remove(id)
            systemMessages += "${player.name} left."
            return
        }
        player.connected = false
        systemMessages += "${player.name} disconnected."
        when (phase) {
            Phase.CLUES -> if (r.currentTurnId == id) {
                r.clues += ClueEntry(id, null)
                r.turnIndex++
                beginTurn(r)
            }
            Phase.VOTING -> if (eligibleVoters(r).all { it in r.votes }) resolveVote(r)
            Phase.GUESSING -> if (r.guesserId == id) {
                r.guesses[id] = null
                afterVotingPhase(r)
            }
            else -> Unit
        }
    }

    fun updateSettings(by: Int, newSettings: OnlineSettings): String? {
        if (by != hostId) return "Only the host can change the settings."
        if (phase != Phase.LOBBY && phase != Phase.RESULTS) return "Settings can only be changed between rounds."
        val known = repository.packs.map { it.name }.toSet()
        val disabled = newSettings.disabledPacks.filter { it in known }.toSet()
        settings = newSettings.sanitized().copy(disabledPacks = disabled)
        return null
    }

    fun start(by: Int): String? {
        if (by != hostId) return "Only the host can start the game."
        if (phase != Phase.LOBBY && phase != Phase.RESULTS) return "A round is already running."
        players.values.filter { !it.connected }.map { it.id }.forEach { players.remove(it) }
        val participants = players.keys.toList()
        if (participants.size < Rules.MIN_ONLINE_PLAYERS) return "At least ${Rules.MIN_ONLINE_PLAYERS} players are needed."
        if (repository.packs.all { it.name in settings.disabledPacks }) return "Pick at least one topic pack."

        val impostorCount = settings.impostors.coerceIn(1, Rules.maxChameleons(participants.size))
        val matrix = repository.deal(settings.matrixSize, random, recentCategories.toSet(), settings.disabledPacks)
        recentCategories.addLast(matrix.category)
        while (recentCategories.size > RECENT_CATEGORY_MEMORY) recentCategories.removeFirst()

        val r = Round(
            number = ++roundCounter,
            matrix = matrix,
            secretIndex = random.nextInt(matrix.words.size),
            impostors = participants.shuffled(random).take(impostorCount).toSet(),
            turnOrder = participants.shuffled(random),
            votingRounds = impostorCount,
        )
        round = r
        phase = Phase.CLUES
        systemMessages += "Round ${r.number} started. Topic: ${matrix.category}. ${players.getValue(r.turnOrder.first()).name} goes first."
        beginTurn(r)
        return null
    }

    fun submitClue(by: Int, rawWord: String): String? {
        val r = round
        if (phase != Phase.CLUES || r == null) return "It is not time to give a word."
        if (r.currentTurnId != by) return "It is not your turn."
        val word = rawWord.trim().replace(Regex("\\s+"), " ")
        if (word.isEmpty()) return "Please enter a word."
        if (word.length > Protocol.MAX_CLUE_LENGTH) return "Keep it short – at most ${Protocol.MAX_CLUE_LENGTH} characters."
        r.clues += ClueEntry(by, word)
        r.turnIndex++
        beginTurn(r)
        return null
    }

    fun vote(by: Int, targetId: Int): String? {
        val r = round
        if (phase != Phase.VOTING || r == null) return "Voting is not open."
        if (by !in eligibleVoters(r)) return "You cannot vote in this phase."
        if (targetId == by) return "You cannot vote for yourself."
        if (targetId !in candidates(r)) return "That player cannot be accused."
        r.votes[by] = targetId
        if (eligibleVoters(r).all { it in r.votes }) resolveVote(r)
        return null
    }

    fun guess(by: Int, index: Int): String? {
        val r = round
        if (phase != Phase.GUESSING || r == null) return "There is nothing to guess right now."
        if (r.guesserId != by) return "Only the accused chameleon may guess."
        if (index !in r.matrix.words.indices) return "Invalid word."
        r.guesses[by] = index
        systemMessages += "${players[by]?.name} locked in a guess."
        afterVotingPhase(r)
        return null
    }

    fun returnToLobby(by: Int): String? {
        if (by != hostId) return "Only the host can do that."
        if (phase != Phase.RESULTS) return "Finish the round first."
        players.values.filter { !it.connected }.map { it.id }.forEach { players.remove(it) }
        round = null
        phase = Phase.LOBBY
        deadline = null
        phaseMillis = null
        return null
    }

    /** Advances the game when the current timer has run out. Returns true when the state changed. */
    fun tick(): Boolean {
        val end = deadline ?: return false
        val r = round ?: return false
        if (clock() < end) return false
        when (phase) {
            Phase.CLUES -> {
                val id = r.currentTurnId ?: return false
                systemMessages += "${players[id]?.name} ran out of time."
                r.clues += ClueEntry(id, null)
                r.turnIndex++
                beginTurn(r)
            }
            Phase.VOTING -> resolveVote(r)
            Phase.GUESSING -> {
                val id = r.guesserId ?: return false
                systemMessages += "${players[id]?.name} ran out of time to guess."
                r.guesses[id] = null
                afterVotingPhase(r)
            }
            else -> return false
        }
        return true
    }

    fun viewFor(id: Int): GameView {
        val r = round
        val base = GameView(
            myId = id,
            hostId = hostId,
            phase = phase,
            players = players.values.map { PlayerInfo(it.id, it.name, it.connected, it.id == hostId, it.score) },
            settings = settings,
            packs = if (phase == Phase.LOBBY || phase == Phase.RESULTS) repository.packs else emptyList(),
        )
        if (r == null) return base
        val participant = id in r.participants
        val impostor = id in r.impostors
        val over = phase == Phase.RESULTS
        return base.copy(
            roundNumber = r.number,
            impostorCount = r.impostors.size,
            matrix = r.matrix,
            secretIndex = if (over || (participant && !impostor)) r.secretIndex else null,
            amImpostor = impostor,
            amParticipant = participant,
            turnOrder = r.turnOrder,
            clues = r.clues.toList(),
            currentTurnId = if (phase == Phase.CLUES) r.currentTurnId else null,
            remainingMillis = deadline?.let { (it - clock()).coerceAtLeast(0) },
            phaseMillis = phaseMillis,
            votingRound = r.votingRound,
            votingRounds = r.votingRounds,
            candidates = if (phase == Phase.VOTING) candidates(r) else emptyList(),
            eligibleVoters = if (phase == Phase.VOTING) eligibleVoters(r) else emptyList(),
            votedIds = if (phase == Phase.VOTING) r.votes.keys.toList() else emptyList(),
            myVote = if (phase == Phase.VOTING) r.votes[id] else null,
            eliminated = r.eliminated.toList(),
            outcomes = r.outcomes.toList(),
            guesserId = if (phase == Phase.GUESSING) r.guesserId else null,
            result = r.result,
        )
    }

    private fun beginTurn(r: Round) {
        // Players who dropped out are skipped so the table does not wait for them.
        while (r.turnIndex < r.turnOrder.size && players[r.turnOrder[r.turnIndex]]?.connected != true) {
            r.clues += ClueEntry(r.turnOrder[r.turnIndex], null)
            r.turnIndex++
        }
        if (r.turnIndex >= r.turnOrder.size) {
            systemMessages += "Everybody has given a word. Time to discuss and vote!"
            startVoting(r)
        } else {
            setTimer(settings.turnSeconds)
        }
    }

    private fun startVoting(r: Round) {
        if (eligibleVoters(r).isEmpty() || candidates(r).size < 2) {
            finishRound(r)
            return
        }
        r.votingRound++
        r.votes.clear()
        phase = Phase.VOTING
        setTimer(settings.voteSeconds)
    }

    private fun resolveVote(r: Round) {
        val votes = r.votes.map { (voter, target) -> CastVote(voter, target) }
        val tally = r.votes.values.groupingBy { it }.eachCount()
        val accused = if (tally.isEmpty()) {
            null
        } else {
            val top = tally.values.max()
            val tied = tally.filterValues { it == top }.keys
            if (tied.size == 1) {
                tied.first()
            } else {
                // Like the dealer in the board game, the start player breaks ties when their vote is among them.
                val startVote = r.turnOrder.firstNotNullOfOrNull { r.votes[it] }
                if (startVote != null && startVote in tied) startVote else tied.random(random)
            }
        }
        val wasImpostor = accused != null && accused in r.impostors
        r.outcomes += VoteOutcome(r.votingRound, accused, wasImpostor, votes)
        if (accused == null) {
            systemMessages += "Nobody voted, so nobody was accused."
            afterVotingPhase(r)
            return
        }
        r.eliminated += accused
        val name = players[accused]?.name ?: "?"
        if (wasImpostor) {
            systemMessages += "$name was accused – and is a chameleon! They get one chance to guess the secret word."
            r.guesserId = accused
            if (players[accused]?.connected == true) {
                phase = Phase.GUESSING
                setTimer(maxOf(settings.turnSeconds, MIN_GUESS_SECONDS))
            } else {
                r.guesses[accused] = null
                afterVotingPhase(r)
            }
        } else {
            systemMessages += "$name was accused – but is not a chameleon."
            afterVotingPhase(r)
        }
    }

    private fun afterVotingPhase(r: Round) {
        r.guesserId = null
        val uncaught = r.impostors - r.eliminated.toSet()
        if (uncaught.isEmpty() || r.votingRound >= r.votingRounds) finishRound(r) else startVoting(r)
    }

    private fun finishRound(r: Round) {
        val caught = r.eliminated.toSet()
        val gained = mutableMapOf<Int, Int>()
        fun award(id: Int, points: Int) {
            gained[id] = (gained[id] ?: 0) + points
            players[id]?.let { it.score += points }
        }
        val results = r.impostors.sortedBy { r.turnOrder.indexOf(it) }.map { id ->
            val guess = r.guesses[id]
            val correct = guess == r.secretIndex
            val wasCaught = id in caught
            // Scoring follows the board game, applied once per chameleon.
            when {
                !wasCaught -> award(id, 2)
                correct -> award(id, 1)
                else -> r.participants.filter { it !in r.impostors }.forEach { award(it, 2) }
            }
            ImpostorResult(id, wasCaught, guess, wasCaught && correct)
        }
        r.result = RoundResult(
            secretIndex = r.secretIndex,
            impostors = results,
            playersWin = results.all { it.caught && !it.guessedCorrectly },
            pointsGained = gained,
        )
        phase = Phase.RESULTS
        deadline = null
        phaseMillis = null
        systemMessages += "The secret word was \"${r.matrix.words[r.secretIndex]}\"."
    }

    private fun candidates(r: Round): List<Int> = r.turnOrder.filter { it !in r.eliminated && players.containsKey(it) }

    private fun eligibleVoters(r: Round): List<Int> = candidates(r).filter { players[it]?.connected == true }

    private fun setTimer(seconds: Int) {
        phaseMillis = seconds * 1000L
        deadline = clock() + seconds * 1000L
    }

    private companion object {
        const val RECENT_CATEGORY_MEMORY = 50
        const val MIN_GUESS_SECONDS = 30
    }
}
