@file:OptIn(ExperimentalMaterial3Api::class)

package com.destrukter.chameleon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
fun MenuScreen(onLocal: () -> Unit, onOnline: () -> Unit, onCustom: () -> Unit, onRules: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.55f), colors.background, colors.background))
                )
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.primary, colors.secondary))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Chameleon",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primary,
            )
            Text(
                "Blend in. Find the one who doesn't.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            MenuTile(
                icon = Icons.Default.Face,
                title = "Local game",
                subtitle = "One phone, passed around the table",
                container = colors.primary,
                content = colors.onPrimary,
                onClick = onLocal,
            )
            Spacer(Modifier.height(12.dp))
            MenuTile(
                icon = Icons.Default.Share,
                title = "Multiplayer",
                subtitle = "Everyone on their own phone – nearby or online",
                container = colors.secondary,
                content = colors.onSecondary,
                onClick = onOnline,
            )
            Spacer(Modifier.height(12.dp))
            MenuTile(
                icon = Icons.Default.Edit,
                title = "Custom boards",
                subtitle = "Create your own topic cards",
                container = colors.surface,
                content = colors.onSurface,
                onClick = onCustom,
            )
            Spacer(Modifier.height(12.dp))
            MenuTile(
                icon = Icons.Default.Info,
                title = "How to play",
                subtitle = "Rules, scoring and multiplayer tips",
                container = colors.surface,
                content = colors.onSurface,
                onClick = onRules,
            )
        }
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(content.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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
                "Custom boards",
                "Make your own topic cards under Custom boards in the menu – inside jokes, your friends' names, your " +
                    "favourite show. Each board has a fixed size (4×4, 5×5 or 6×6) and appears in the \"Custom\" pack " +
                    "for games of that size. In multiplayer, the host's custom boards are used and sent to everybody.",
            )
            RuleSection(
                "Multiplayer",
                "One phone hosts the lobby – the game runs on that phone – and shows codes for the others to type in. " +
                    "On the same Wi-Fi, the host's hotspot or a VPN like Tailscale, use the 8-character code: phones " +
                    "connect directly, no internet needed. Friends anywhere else use the 12-character internet code, " +
                    "which passes the game through a free public relay, end-to-end encrypted with the code. No account needed.",
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
