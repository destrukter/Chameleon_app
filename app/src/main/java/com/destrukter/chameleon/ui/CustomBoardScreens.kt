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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.destrukter.chameleon.AppViewModel
import com.destrukter.chameleon.core.CustomBoard
import com.destrukter.chameleon.core.MatrixSize
import com.destrukter.chameleon.core.WordMatrix

@Composable
fun CustomBoardsScreen(boards: List<CustomBoard>, editor: AppViewModel.BoardEditor, onBack: () -> Unit) {
    var toDelete by remember { mutableStateOf<CustomBoard?>(null) }
    Scaffold(
        topBar = { ChameleonTopBar("Custom boards", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editor.create() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New board") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Your boards appear in the \"Custom\" topic pack for games of the same size. " +
                    "In multiplayer the host's boards are used and sent to every player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (boards.isEmpty()) {
                SectionCard(title = "No custom boards yet", icon = Icons.Default.Edit) {
                    Text(
                        "Tap \"New board\" to make one: pick a size, give it a topic and fill in a word for every cell.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            MatrixSize.entries.forEach { size ->
                val ofSize = boards.filter { it.size == size }
                if (ofSize.isNotEmpty()) {
                    SectionCard(title = "${size.label} · ${ofSize.size}", icon = Icons.Default.List) {
                        ofSize.forEach { board ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(board.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        board.words.take(4).joinToString(", ") + "…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                IconButton(onClick = { editor.edit(board) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit ${board.name}")
                                }
                                IconButton(onClick = { toDelete = board }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${board.name}")
                                }
                            }
                        }
                    }
                }
            }
            // Room so the last row is not hidden behind the floating button.
            Spacer(Modifier.height(72.dp))
        }
    }
    toDelete?.let { board ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${board.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    editor.delete(board)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CustomBoardEditorScreen(editor: AppViewModel.BoardEditor, onBack: () -> Unit) {
    val size = editor.size
    val filled = editor.words.count { it.isNotBlank() }
    Scaffold(topBar = { ChameleonTopBar(if (editor.editingId == null) "New board" else "Edit board", onBack) }) { padding ->
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
            SectionCard(title = "Topic", icon = Icons.Default.Edit) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { editor.name = it.take(CustomBoard.MAX_NAME_LENGTH) },
                    label = { Text("Topic name, e.g. \"Our holidays\"") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                Text("Board size", style = MaterialTheme.typography.bodyLarge)
                SegmentedChoice(
                    options = MatrixSize.entries,
                    selected = size,
                    label = { "${it.dimension}×${it.dimension}" },
                    onSelect = editor::changeSize,
                )
            }

            SectionCard(title = "Words · $filled of ${size.cellCount}", icon = Icons.Default.List) {
                Text(
                    "One word per cell, row by row. The board is played exactly as laid out here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Two fields per row keeps even the 6×6 board comfortable to type on a phone.
                editor.words.indices.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { index ->
                            OutlinedTextField(
                                value = editor.words[index],
                                onValueChange = { editor.setWord(index, it) },
                                label = { Text(coordinate(size, index)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = if (index == size.cellCount - 1) ImeAction.Done else ImeAction.Next,
                                ),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (filled > 0 && editor.words.size == size.cellCount) {
                Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                WordGrid(WordMatrix(editor.name.ifBlank { "Custom" }, size, editor.words.map { it.ifBlank { "·" } }))
            }

            editor.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
                Button(onClick = { editor.save() }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Save board") }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

private fun coordinate(size: MatrixSize, index: Int): String =
    "${'A' + index % size.dimension}${index / size.dimension + 1}"
