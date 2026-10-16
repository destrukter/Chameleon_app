@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.destrukter.chameleon.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destrukter.chameleon.AppViewModel
import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.Rules
import com.destrukter.chameleon.core.online.ClientMessage
import com.destrukter.chameleon.core.online.GameClient
import com.destrukter.chameleon.core.online.GameView
import com.destrukter.chameleon.core.online.LobbyCode
import com.destrukter.chameleon.core.online.OnlineSettings
import com.destrukter.chameleon.core.online.Phase
import com.destrukter.chameleon.core.online.Protocol

@Composable
fun OnlineHomeScreen(online: AppViewModel.OnlineController, onBack: () -> Unit) {
    Scaffold(topBar = { ChameleonTopBar("Multiplayer", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Your name") {
                OutlinedTextField(
                    value = online.name,
                    onValueChange = { online.name = it.take(Protocol.MAX_NAME_LENGTH) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
            }
            SectionCard(title = "Create a lobby") {
                Text(
                    "Your phone hosts the game. Friends join with the code you'll see next.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { online.host() },
                    enabled = !online.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (online.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create lobby")
                    }
                }
            }
            SectionCard(title = "Join a lobby") {
                OutlinedTextField(
                    value = online.code,
                    onValueChange = { online.code = LobbyCode.format(it) },
                    label = { Text("Lobby code") },
                    placeholder = { Text("ABCD-EFGH") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { online.join() }),
                )
                Button(onClick = { online.join() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Join lobby")
                }
            }
            online.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Peer-to-peer, no server: all phones must be on the same Wi-Fi, or connected to the host's mobile hotspot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OnlineSessionScreen(online: AppViewModel.OnlineController) {
    val state by online.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val client = online.client

    LaunchedEffect(client) {
        client?.errors?.collect { snackbar.showSnackbar(it) }
    }
    BackHandler { confirmLeave = true }
    KeepScreenOn()

    val view = state.view
    val title = when {
        view == null -> "Lobby ${online.lobbyCode.orEmpty()}"
        view.phase == Phase.LOBBY -> "Lobby ${online.lobbyCode.orEmpty()}"
        else -> "Round ${view.roundNumber} · ${view.matrix?.category.orEmpty()}"
    }
    Scaffold(
        topBar = {
            ChameleonTopBar(title, onBack = null) {
                IconButton(onClick = { confirmLeave = true }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Leave lobby")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            when (val status = state.status) {
                GameClient.Status.Connecting -> CenteredMessage("Connecting to the lobby…", progress = true)
                is GameClient.Status.Rejected -> ConnectionProblem("Could not join", status.reason, online::leaveToHome)
                is GameClient.Status.Disconnected -> ConnectionProblem("Disconnected", status.reason, online::leaveToHome)
                GameClient.Status.Connected -> {
                    if (view == null) {
                        CenteredMessage("Loading…", progress = true)
                    } else {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val remaining = state.remainingMillis(rememberNow())
                            when (view.phase) {
                                Phase.LOBBY -> LobbyContent(view, online)
                                Phase.CLUES -> CluesContent(view, remaining, online)
                                Phase.VOTING -> VotingContent(view, remaining, online)
                                Phase.GUESSING -> GuessingContent(view, remaining, online)
                                Phase.RESULTS -> ResultsContent(view, online)
                            }
                        }
                        ChatPanel(
                            messages = state.chat,
                            myId = view.myId,
                            expandByDefault = view.phase == Phase.LOBBY || view.phase == Phase.VOTING,
                            onSend = { online.send(ClientMessage.Chat(it)) },
                        )
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the lobby?") },
            text = {
                Text(
                    if (online.isHosting) "You are the host – leaving closes the lobby for everybody." else "You can rejoin with the same code."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    online.leaveToHome()
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun CenteredMessage(text: String, progress: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress) CircularProgressIndicator()
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ConnectionProblem(title: String, reason: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(reason, textAlign = TextAlign.Center)
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun LobbyContent(view: GameView, online: AppViewModel.OnlineController) {
    val context = LocalContext.current
    val code = online.lobbyCode.orEmpty()
    SectionCard {
        Text("Lobby code", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                code,
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my Chameleon lobby with code $code (same Wi-Fi or my hotspot).")
                }
                context.startActivity(Intent.createChooser(send, "Share lobby code"))
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share code")
            }
        }
        Text(
            "Everybody must be on the same Wi-Fi as the host, or connected to the host's hotspot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    PlayerList(view)
    SettingsCard(view, online)
    if (view.isHost) {
        val enough = view.players.size >= Rules.MIN_PLAYERS
        Button(
            onClick = { online.send(ClientMessage.Start) },
            enabled = enough,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (enough) "Start game" else "Waiting for players (${view.players.size}/${Rules.MIN_PLAYERS})")
        }
    } else {
        Text(
            "Waiting for ${view.nameOf(view.hostId)} to start the game…",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlayerList(view: GameView, showScores: Boolean = view.players.any { it.score > 0 }) {
    SectionCard(title = "Players (${view.players.size})") {
        view.players.sortedByDescending { if (showScores) it.score else 0 }.forEach { player ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    player.name + if (player.id == view.myId) " (you)" else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (player.connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                if (!player.connected) Text("offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                if (player.isHost) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Host",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp).size(18.dp),
                    )
                }
                if (showScores) {
                    Text(
                        "${player.score} pts",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(view: GameView, online: AppViewModel.OnlineController) {
    val settings = view.settings
    val editable = view.isHost
    fun update(new: OnlineSettings) = online.send(ClientMessage.UpdateSettings(new))
    val maxImpostors = Rules.maxChameleons(maxOf(view.players.size, Rules.MIN_PLAYERS))

    SectionCard(title = if (editable) "Settings" else "Settings (chosen by the host)") {
        Stepper(
            label = "Chameleons",
            value = settings.impostors,
            min = 1,
            max = OnlineSettings.MAX_IMPOSTORS,
            enabled = editable,
            onChange = { update(settings.copy(impostors = it)) },
        )
        if (settings.impostors > maxImpostors) {
            Text(
                "With ${view.players.size} players only $maxImpostors chameleon(s) will be used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        HorizontalDivider()
        Text("Board size", style = MaterialTheme.typography.bodyLarge)
        SegmentedChoice(
            options = MatrixSize.entries,
            selected = settings.matrixSize,
            label = { "${it.dimension}×${it.dimension}" },
            onSelect = { update(settings.copy(matrixSize = it)) },
            enabled = editable,
        )
        HorizontalDivider()
        Text("Time to give a word", style = MaterialTheme.typography.bodyLarge)
        SecondsChips(OnlineSettings.TURN_OPTIONS, settings.turnSeconds, editable) { update(settings.copy(turnSeconds = it)) }
        Text("Time for each vote", style = MaterialTheme.typography.bodyLarge)
        SecondsChips(OnlineSettings.VOTE_OPTIONS, settings.voteSeconds, editable) { update(settings.copy(voteSeconds = it)) }
    }
}

@Composable
private fun SecondsChips(options: List<Int>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { seconds ->
            FilterChip(
                selected = seconds == selected,
                onClick = { onSelect(seconds) },
                enabled = enabled,
                label = { Text(if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} min" else "${seconds}s") },
            )
        }
    }
}

@Composable
private fun RoleBanner(view: GameView) {
    val matrix = view.matrix ?: return
    val secret = view.secretIndex
    when {
        !view.amParticipant -> Banner(
            text = "Watching this round",
            subtitle = "You joined between rounds and will play in the next one.",
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        view.amImpostor -> Banner(
            text = "You are the Chameleon!",
            subtitle = "Blend in – you don't know the secret word." +
                if (view.impostorCount > 1) " There are ${view.impostorCount} chameleons." else "",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        secret != null -> Banner(
            text = "Secret word: ${matrix.words[secret]}",
            subtitle = "Don't make it too obvious." +
                if (view.impostorCount > 1) " There are ${view.impostorCount} chameleons." else "",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CluesList(view: GameView) {
    SectionCard(title = "Words") {
        view.turnOrder.forEachIndexed { index, id ->
            val clue = view.clues.getOrNull(index)
            val isCurrent = view.currentTurnId == id
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${view.nameOf(id)}",
                    modifier = Modifier.weight(1f),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (id in view.eliminated) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        clue == null && isCurrent -> "thinking…"
                        clue == null -> ""
                        clue.word == null -> "— no word —"
                        else -> clue.word!!
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (clue?.word == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CluesContent(view: GameView, remaining: Long?, online: AppViewModel.OnlineController) {
    val matrix = view.matrix ?: return
    RoleBanner(view)
    WordGrid(matrix, highlight = view.secretIndex)
    val myTurn = view.currentTurnId == view.myId
    if (myTurn) {
        var word by rememberSaveable(view.roundNumber) { mutableStateOf("") }
        SectionCard(title = "Your turn! Say one word.") {
            CountdownBar(remaining, view.phaseMillis)
            OutlinedTextField(
                value = word,
                onValueChange = { word = it.take(Protocol.MAX_CLUE_LENGTH) },
                label = { Text("Your word") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (word.isNotBlank()) online.send(ClientMessage.SubmitClue(word))
                }),
            )
            Button(
                onClick = { online.send(ClientMessage.SubmitClue(word)) },
                enabled = word.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Submit") }
        }
    } else {
        SectionCard {
            Text(
                "Waiting for ${view.nameOf(view.currentTurnId)} to give a word…",
                style = MaterialTheme.typography.bodyLarge,
            )
            CountdownBar(remaining, view.phaseMillis)
        }
    }
    CluesList(view)
}

@Composable
private fun VotingContent(view: GameView, remaining: Long?, online: AppViewModel.OnlineController) {
    val matrix = view.matrix ?: return
    RoleBanner(view)
    SectionCard(title = "Vote ${view.votingRound} of ${view.votingRounds}: who is a chameleon?") {
        CountdownBar(remaining, view.phaseMillis)
        val canVote = view.myId in view.eligibleVoters
        if (!canVote) {
            Text(
                "You can't vote in this round – you can still discuss in the chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        view.candidates.forEach { id ->
            val voted = id in view.votedIds
            val mine = view.myVote == id
            val clue = view.clues.firstOrNull { it.playerId == id }?.word ?: "—"
            val label = "${view.nameOf(id)}${if (id == view.myId) " (you)" else ""} · “$clue”"
            if (mine) {
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text(label, modifier = Modifier.weight(1f))
                    VotedMark(voted)
                }
            } else {
                OutlinedButton(
                    onClick = { online.send(ClientMessage.Vote(id)) },
                    enabled = canVote && id != view.myId,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label, modifier = Modifier.weight(1f))
                    VotedMark(voted)
                }
            }
        }
        Text(
            "${view.votedIds.size} of ${view.eligibleVoters.size} voted. You can change your vote until everybody has voted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutcomesCard(view)
    WordGrid(matrix, highlight = view.secretIndex)
}

@Composable
private fun VotedMark(voted: Boolean) {
    if (voted) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Has voted", modifier = Modifier.size(18.dp))
    } else {
        Spacer(Modifier.width(18.dp))
    }
}

@Composable
private fun OutcomesCard(view: GameView) {
    if (view.outcomes.isEmpty()) return
    SectionCard(title = "Votes so far") {
        view.outcomes.forEach { outcome ->
            val accused = outcome.accusedId
            Text(
                when {
                    accused == null -> "Vote ${outcome.round}: nobody was accused."
                    outcome.wasImpostor -> "Vote ${outcome.round}: ${view.nameOf(accused)} – a chameleon!"
                    else -> "Vote ${outcome.round}: ${view.nameOf(accused)} – innocent."
                },
                fontWeight = FontWeight.Medium,
            )
            if (outcome.votes.isNotEmpty()) {
                Text(
                    outcome.votes.joinToString(" · ") { "${view.nameOf(it.voterId)} → ${view.nameOf(it.targetId)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuessingContent(view: GameView, remaining: Long?, online: AppViewModel.OnlineController) {
    val matrix = view.matrix ?: return
    val guesser = view.guesserId
    if (guesser == view.myId) {
        var choice by rememberSaveable(view.votingRound) { mutableStateOf<Int?>(null) }
        Banner(
            text = "You've been caught!",
            subtitle = "One last chance: tap the word you think is the secret word.",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        CountdownBar(remaining, view.phaseMillis)
        WordGrid(matrix, selected = choice, onCellClick = { choice = it })
        Button(
            onClick = { choice?.let { online.send(ClientMessage.Guess(it)) } },
            enabled = choice != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(choice?.let { "Guess \"${matrix.words[it]}\"" } ?: "Pick a word")
        }
        CluesList(view)
    } else {
        Banner(
            text = "${view.nameOf(guesser)} is a chameleon!",
            subtitle = "They are now guessing the secret word…",
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        CountdownBar(remaining, view.phaseMillis)
        WordGrid(matrix, highlight = view.secretIndex)
        OutcomesCard(view)
    }
}

@Composable
private fun ResultsContent(view: GameView, online: AppViewModel.OnlineController) {
    val matrix = view.matrix ?: return
    val result = view.result ?: return
    val myImpostor = result.impostors.firstOrNull { it.playerId == view.myId }
    val iWon = when {
        !view.amParticipant -> null
        myImpostor != null -> !myImpostor.caught || myImpostor.guessedCorrectly
        else -> result.playersWin
    }
    Banner(
        text = if (result.playersWin) "The chameleons were found!" else "The chameleons got away!",
        subtitle = when (iWon) {
            true -> "You won this round."
            false -> "You lost this round."
            null -> null
        },
        container = if (result.playersWin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        content = if (result.playersWin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
    )
    SectionCard(title = "Secret word: ${matrix.words[result.secretIndex]}") {
        result.impostors.forEach { impostor ->
            val name = view.nameOf(impostor.playerId)
            val guess = impostor.guessIndex?.let { matrix.words[it] }
            Text(
                when {
                    !impostor.caught -> "🦎 $name was never caught."
                    guess == null -> "🦎 $name was caught and didn't guess."
                    impostor.guessedCorrectly -> "🦎 $name was caught but guessed \"$guess\" correctly!"
                    else -> "🦎 $name was caught and guessed \"$guess\" – wrong!"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    WordGrid(
        matrix,
        highlight = result.secretIndex,
        selected = result.impostors.mapNotNull { it.guessIndex }.firstOrNull { it != result.secretIndex },
    )
    OutcomesCard(view)
    SectionCard(title = "Scores") {
        view.players.sortedByDescending { it.score }.forEach { player ->
            val gained = result.pointsGained[player.id] ?: 0
            Row {
                Text(player.name, modifier = Modifier.weight(1f))
                if (gained > 0) {
                    Text("+$gained  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Text("${player.score}", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (view.isHost) {
        Button(onClick = { online.send(ClientMessage.Start) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Play again")
        }
        OutlinedButton(onClick = { online.send(ClientMessage.ReturnToLobby) }, modifier = Modifier.fillMaxWidth()) {
            Text("Back to lobby (change settings)")
        }
    } else {
        Text(
            "Waiting for ${view.nameOf(view.hostId)} to start the next round…",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedButton(
        onClick = online::leaveToHome,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Leave lobby")
    }
}
