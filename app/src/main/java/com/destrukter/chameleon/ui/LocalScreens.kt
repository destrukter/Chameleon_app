@file:OptIn(ExperimentalMaterial3Api::class)

package com.destrukter.chameleon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.destrukter.chameleon.AppViewModel
import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.Rules

@Composable
fun LocalSetupScreen(local: AppViewModel.LocalController, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val playerCount = local.names.size
    val maxChameleons = Rules.maxChameleons(maxOf(playerCount, Rules.MIN_PLAYERS))

    fun add() {
        if (local.addName(input)) input = ""
    }

    Scaffold(topBar = { ChameleonTopBar("Local game", onBack) }) { padding ->
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
            SectionCard(title = "Players") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(20) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilledIconButton(onClick = { add() }, enabled = input.isNotBlank()) {
                        Icon(Icons.Default.Add, contentDescription = "Add player")
                    }
                }
                local.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (local.names.isEmpty()) {
                    Text(
                        "Add everybody who is playing. You need at least ${Rules.MIN_PLAYERS}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                local.names.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}.",
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { local.removeName(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove $name")
                        }
                    }
                }
            }

            SectionCard(title = "Settings") {
                Stepper(
                    label = "Chameleons",
                    value = local.chameleons.coerceAtMost(maxChameleons),
                    min = 1,
                    max = maxChameleons,
                    onChange = local::updateChameleons,
                )
                Text(
                    if (playerCount < Rules.MIN_PLAYERS) {
                        "More chameleons become available with more players."
                    } else {
                        "Up to $maxChameleons chameleon(s) for $playerCount players."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("Board size", style = MaterialTheme.typography.bodyLarge)
                SegmentedChoice(
                    options = MatrixSize.entries,
                    selected = local.size,
                    label = { "${it.dimension}×${it.dimension}" },
                    onSelect = local::updateSize,
                )
                Text(
                    "${local.size.label} · ${local.size.cellCount} words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { local.start() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Start with $playerCount player${if (playerCount == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun LocalCategoryScreen(local: AppViewModel.LocalController, onBack: () -> Unit) {
    val matrix = local.matrix ?: return
    Scaffold(topBar = { ChameleonTopBar("Choose a topic", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Topic", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                matrix.category,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            WordGrid(matrix)
            Text(
                "Show this to everybody. Happy with the topic?",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { local.reshuffle() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Reshuffle")
                }
                Button(onClick = { local.accept() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
fun LocalPassScreen(local: AppViewModel.LocalController, onBack: () -> Unit) {
    val round = local.round ?: return
    val index = local.passIndex
    val player = round.players[index]
    val isLast = index == round.players.lastIndex
    KeepScreenOn()
    Scaffold(topBar = { ChameleonTopBar("Player ${index + 1} of ${round.players.size}", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!local.revealed) {
                Text("Pass the phone to", style = MaterialTheme.typography.titleMedium)
                Text(
                    player,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Make sure nobody else can see the screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { local.reveal() }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Show", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                Text(player, style = MaterialTheme.typography.titleLarge)
                Text("Topic: ${round.matrix.category}", style = MaterialTheme.typography.titleMedium)
                if (round.isChameleon(index)) {
                    Banner(
                        text = "You are the Chameleon!",
                        subtitle = "You don't know the secret word. Listen carefully and blend in." +
                            if (round.chameleons.size > 1) " There are ${round.chameleons.size} chameleons this round." else "",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    Banner(
                        text = round.secretWord,
                        subtitle = "The secret word (${round.matrix.coordinateOf(round.secretIndex)}). " +
                            if (round.chameleons.size > 1) "There are ${round.chameleons.size} chameleons." else "Don't give it away!",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Button(onClick = { local.next() }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text(if (isLast) "Hide & start the round" else "Hide & next player", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun LocalOverviewScreen(local: AppViewModel.LocalController, onMenu: () -> Unit) {
    val round = local.round ?: return
    var confirmReveal by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()
    Scaffold(topBar = { ChameleonTopBar(round.matrix.category, onBack = local::playAgain) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Banner(
                text = "${round.players[round.startPlayer]} starts!",
                subtitle = "Go around the table, everyone says one word. Then discuss and point at the chameleon.",
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            WordGrid(round.matrix, highlight = if (local.showSolution) round.secretIndex else null)
            if (local.showSolution) {
                val names = round.chameleons.sorted().joinToString(", ") { round.players[it] }
                Banner(
                    text = if (round.chameleons.size > 1) "Chameleons: $names" else "Chameleon: $names",
                    subtitle = "The secret word was \"${round.secretWord}\".",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else {
                OutlinedButton(onClick = { confirmReveal = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reveal chameleon & secret word")
                }
            }
            Button(onClick = local::playAgain, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Play again", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
                Text("Back to menu")
            }
        }
    }
    if (confirmReveal) {
        AlertDialog(
            onDismissRequest = { confirmReveal = false },
            title = { Text("Reveal the answer?") },
            text = { Text("Only do this after the vote and the chameleon's guess.") },
            confirmButton = {
                TextButton(onClick = {
                    local.showSolution = true
                    confirmReveal = false
                }) { Text("Reveal") }
            },
            dismissButton = { TextButton(onClick = { confirmReveal = false }) { Text("Cancel") } },
        )
    }
}
