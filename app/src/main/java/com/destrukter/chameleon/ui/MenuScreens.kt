@file:OptIn(ExperimentalMaterial3Api::class)

package com.destrukter.chameleon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.destrukter.chameleon.R

@Composable
fun MenuScreen(onLocal: () -> Unit, onOnline: () -> Unit, onRules: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
            Text(
                "Chameleon",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Find the chameleon before it blends in.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = onLocal, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Local game · pass the phone", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOnline, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Multiplayer · one phone each", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("How to play")
            }
        }
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { ChameleonTopBar("How to play", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuleSection(
                "The idea",
                "Everybody sees the same topic card: a grid of words that fit one category. One word on it is secret. " +
                    "Every player knows the secret word – except the chameleon (or chameleons), who only knows the topic.",
            )
            RuleSection(
                "Giving words",
                "Starting with the start player, everybody says ONE word related to the secret word. Be vague enough that " +
                    "the chameleon cannot work out the secret, but specific enough to prove to the others that you know it. " +
                    "The chameleon has to bluff a word that blends in.",
            )
            RuleSection(
                "Voting",
                "Discuss, then vote for who you think the chameleon is. In the local game everybody points at the same time; " +
                    "ties are broken by the start player. If the group picks an innocent player, the chameleon escapes.",
            )
            RuleSection(
                "Caught!",
                "A caught chameleon gets one guess at the secret word. Guessing right still lets them escape.",
            )
            RuleSection(
                "Scoring (multiplayer)",
                "Chameleon not caught: +2 for the chameleon. Caught but guessed the word: +1 for the chameleon. " +
                    "Caught and guessed wrong: +2 for every other player. With several chameleons this is scored per chameleon.",
            )
            RuleSection(
                "More players, more chameleons",
                "Play with 3 up to 30 players locally or 16 in multiplayer. Larger groups can add more chameleons " +
                    "(always fewer than half the table) and use the bigger 5×5 or 6×6 boards. With several chameleons " +
                    "there is one voting round per chameleon.",
            )
            RuleSection(
                "Multiplayer without a server",
                "One phone hosts the lobby and shows a code. Everybody else must be on the same Wi-Fi – or connected to the " +
                    "host's mobile hotspot – and types in that code. No internet connection or account is needed.",
            )
        }
    }
}

@Composable
private fun RuleSection(title: String, body: String) {
    SectionCard(title = title) {
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
