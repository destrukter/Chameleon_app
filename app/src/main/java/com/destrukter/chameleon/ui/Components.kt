@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.destrukter.chameleon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
        MatrixSize.SMALL -> 60.dp to 14.sp
        MatrixSize.MEDIUM -> 54.dp to 12.sp
        MatrixSize.LARGE -> 50.dp to 10.5.sp
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Spacer(Modifier.width(14.dp))
                for (col in 0 until dimension) {
                    Text(
                        text = "${'A' + col}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            for (row in 0 until dimension) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${row + 1}",
                        modifier = Modifier.width(14.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    for (col in 0 until dimension) {
                        val index = row * dimension + col
                        WordCell(
                            word = matrix.words[index],
                            secret = index == highlight,
                            picked = index == selected,
                            height = cellHeight,
                            fontSize = fontSize,
                            onClick = onCellClick?.let { { it(index) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordCell(
    word: String,
    secret: Boolean,
    picked: Boolean,
    height: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        secret -> colors.primary
        picked -> colors.tertiaryContainer
        else -> colors.surface
    }
    val content = when {
        secret -> colors.onPrimary
        picked -> colors.onTertiaryContainer
        else -> colors.onSurface
    }
    Surface(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(10.dp),
        color = container,
        contentColor = content,
        shadowElevation = if (secret) 4.dp else 1.dp,
        border = when {
            picked -> BorderStroke(2.dp, colors.tertiary)
            secret -> null
            else -> BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(3.dp)) {
            Text(
                text = word,
                fontSize = fontSize,
                lineHeight = fontSize * 1.1f,
                fontWeight = if (secret || picked) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
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
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
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

/**
 * Chips for switching topic packs on and off, with "All" / "None" shortcuts for whoever may edit them.
 * A pack without any topic for the chosen board [size] (e.g. "Custom" with no 5×5 board yet) cannot be selected.
 */
@Composable
fun PackSelector(
    packs: List<PackInfo>,
    disabled: Set<String>,
    size: MatrixSize,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onSetAll: ((Boolean) -> Unit)? = null,
) {
    val playable = packs.filter { it.countFor(size) > 0 }
    val active = playable.filter { it.name !in disabled }
    val topics = active.sumOf { it.countFor(size) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (active.isEmpty()) "Pick at least one pack" else "${active.size} of ${playable.size} packs · $topics topics",
            style = MaterialTheme.typography.bodySmall,
            color = if (active.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (enabled && onSetAll != null) {
            TextButton(onClick = { onSetAll(true) }) { Text("All") }
            TextButton(onClick = { onSetAll(false) }) { Text("None") }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        packs.forEach { pack ->
            val count = pack.countFor(size)
            val available = count > 0
            FilterChip(
                selected = available && pack.name !in disabled,
                onClick = { onToggle(pack.name) },
                enabled = enabled && available,
                label = {
                    Text(
                        if (available) "${pack.name} ($count)" else "${pack.name} (no ${size.dimension}×${size.dimension})"
                    )
                },
            )
        }
    }
}

/** A banner with a soft gradient, used for roles and round results. */
@Composable
fun Banner(text: String, container: Color, content: Color, subtitle: String? = null) {
    Surface(
        color = Color.Transparent,
        contentColor = content,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(container, container.copy(alpha = 0.7f))))
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
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
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            "${(remainingMillis + 999) / 1000}s left",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Shows a code typed without separators in blocks of four ("ABCD-EFGH-JKMN"). Because the dashes exist only
 * on screen, the cursor stays right after the last typed character instead of jumping when a dash appears.
 */
object GroupedCodeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val grouped = raw.chunked(GROUP).joinToString("-")
        val mapping = object : OffsetMapping {
            // A dash follows every complete group of four that has more characters after it.
            override fun originalToTransformed(offset: Int): Int = offset + (offset - 1).coerceAtLeast(0) / GROUP

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / (GROUP + 1)).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }

    private const val GROUP = 4
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
