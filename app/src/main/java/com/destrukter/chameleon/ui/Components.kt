@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.destrukter.chameleon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.PackInfo
import com.destrukter.chameleon.core.WordMatrix
import kotlinx.coroutines.delay

@Composable
fun ChameleonTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
    )
}

/**
 * The topic card. [highlight] marks the secret word, [selected] the cell a chameleon picked as a guess.
 * Coordinates along the edges mirror the dice grid of the board game.
 */
@Composable
fun WordGrid(
    matrix: WordMatrix,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    selected: Int? = null,
    onCellClick: ((Int) -> Unit)? = null,
) {
    val dimension = matrix.size.dimension
    val (cellHeight, fontSize) = when (matrix.size) {
        MatrixSize.SMALL -> 58.dp to 14.sp
        MatrixSize.MEDIUM -> 52.dp to 12.sp
        MatrixSize.LARGE -> 48.dp to 10.5.sp
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(16.dp))
            for (col in 0 until dimension) {
                Text(
                    text = "${'A' + col}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (row in 0 until dimension) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${row + 1}",
                    modifier = Modifier.width(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (col in 0 until dimension) {
                    val index = row * dimension + col
                    val isSecret = index == highlight
                    val isSelected = index == selected
                    val colors = MaterialTheme.colorScheme
                    val container = when {
                        isSecret -> colors.primary
                        isSelected -> colors.tertiaryContainer
                        else -> colors.surfaceVariant
                    }
                    val content = when {
                        isSecret -> colors.onPrimary
                        isSelected -> colors.onTertiaryContainer
                        else -> colors.onSurfaceVariant
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight)
                            .let { if (onCellClick != null) it.clickable { onCellClick(index) } else it },
                        shape = RoundedCornerShape(8.dp),
                        color = container,
                        contentColor = content,
                        border = if (isSelected) BorderStroke(2.dp, colors.tertiary) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                            Text(
                                text = matrix.words[index],
                                fontSize = fontSize,
                                lineHeight = fontSize * 1.1f,
                                fontWeight = if (isSecret || isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = enabled && value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "$value",
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = enabled && value < max) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Chips for switching topic packs on and off, with "All" / "None" shortcuts for whoever may edit them. */
@Composable
fun PackSelector(
    packs: List<PackInfo>,
    disabled: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onSetAll: ((Boolean) -> Unit)? = null,
) {
    val activeCount = packs.count { it.name !in disabled }
    val topics = packs.filter { it.name !in disabled }.sumOf { it.topicCount }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (activeCount == 0) "Pick at least one pack" else "$activeCount of ${packs.size} packs · $topics topics",
            style = MaterialTheme.typography.bodySmall,
            color = if (activeCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (enabled && onSetAll != null) {
            TextButton(onClick = { onSetAll(true) }) { Text("All") }
            TextButton(onClick = { onSetAll(false) }) { Text("None") }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        packs.forEach { pack ->
            val on = pack.name !in disabled
            FilterChip(
                selected = on,
                onClick = { onToggle(pack.name) },
                enabled = enabled,
                label = { Text("${pack.name} (${pack.topicCount})") },
            )
        }
    }
}

/** A banner with a strong colour, used for roles and round results. */
@Composable
fun Banner(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, subtitle: String? = null) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun CountdownBar(remainingMillis: Long?, totalMillis: Long?) {
    if (remainingMillis == null || totalMillis == null || totalMillis <= 0) return
    val fraction = (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    val urgent = remainingMillis < 10_000
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            "${(remainingMillis + 999) / 1000}s left",
            style = MaterialTheme.typography.labelMedium,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Current wall-clock time, refreshed several times per second so countdowns animate. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    return now
}

/** Keeps the display on while the composable is shown, so the game does not drop out mid-round. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
