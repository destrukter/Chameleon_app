@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.destrukter.chameleon.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.destrukter.chameleon.core.CategoryRepository
import com.destrukter.chameleon.core.CustomLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
fun CustomBoardsScreen(library: CustomLibrary, editor: AppViewModel.BoardEditor, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<CustomBoard?>(null) }
    var categoryDialog by remember { mutableStateOf<CategoryDialog?>(null) }
    var deleteCategory by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }.isSuccess
                }
                snackbar.showSnackbar(if (ok) "Boards saved." else "Couldn't save the file.")
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().takeIf { b -> b.size < MAX_IMPORT_BYTES } }
                    }.getOrNull()?.toString(Charsets.UTF_8)
                }
                snackbar.showSnackbar(if (text == null) "Couldn't read that file." else editor.import(text))
            }
        }
    }

    Scaffold(
        topBar = { ChameleonTopBar("Custom boards", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
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
                "Each category is its own topic pack. A board can be played in games of its size. " +
                    "In multiplayer the host's boards are used and sent to every player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { categoryDialog = CategoryDialog(null) }, modifier = Modifier.weight(1f)) {
                    Text("New category")
                }
                OutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                    Text("Import")
                }
                OutlinedButton(onClick = { exporting = true }, enabled = library.boards.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Export")
                }
            }
            library.allCategories().forEach { category ->
                val boards = library.boardsIn(category)
                val isDefault = category.equals(CategoryRepository.CUSTOM_PACK, ignoreCase = true)
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                sizeSummary(boards),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editor.create(category) }) {
                            Icon(Icons.Default.Add, contentDescription = "New board in $category")
                        }
                        if (!isDefault) {
                            IconButton(onClick = { categoryDialog = CategoryDialog(category) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename $category")
                            }
                            IconButton(onClick = { deleteCategory = category }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete $category")
                            }
                        }
                    }
                    if (boards.isEmpty()) {
                        Text(
                            "No boards yet – tap + to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    boards.forEach { board ->
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${board.name}  ·  ${board.size.dimension}×${board.size.dimension}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
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
    deleteCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteCategory = null },
            title = { Text("Delete category \"$category\"?") },
            text = { Text("Its boards are kept and moved to \"Custom\".") },
            confirmButton = {
                TextButton(onClick = {
                    editor.deleteCategory(category)
                    deleteCategory = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCategory = null }) { Text("Cancel") } },
        )
    }
    categoryDialog?.let { dialog ->
        CategoryNameDialog(
            initial = dialog.renaming.orEmpty(),
            title = if (dialog.renaming == null) "New category" else "Rename category",
            onDismiss = { categoryDialog = null },
            onConfirm = { name ->
                val error = if (dialog.renaming == null) editor.addCategory(name) else editor.renameCategory(dialog.renaming, name)
                if (error == null) categoryDialog = null
                error
            },
        )
    }
    if (exporting) {
        ExportDialog(
            library = library,
            onDismiss = { exporting = false },
            onShare = { categories ->
                exporting = false
                shareBoards(context, editor.export(categories), fileName(categories))
            },
            onSave = { categories ->
                exporting = false
                pendingExport = editor.export(categories)
                saveFile.launch(fileName(categories))
            },
        )
    }
}

private class CategoryDialog(val renaming: String?)

@Composable
private fun CategoryNameDialog(initial: String, title: String, onDismiss: () -> Unit, onConfirm: (String) -> String?) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(CustomLibrary.MAX_CATEGORY_LENGTH)
                    error = null
                },
                label = { Text("Category name") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> @Composable { Text(message) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = { TextButton(onClick = { error = onConfirm(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExportDialog(
    library: CustomLibrary,
    onDismiss: () -> Unit,
    onShare: (List<String>) -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val withBoards = library.allCategories().filter { library.boardsIn(it).isNotEmpty() }
    var selected by remember { mutableStateOf(withBoards.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export boards") },
        text = {
            Column {
                Text("Choose the categories to share. Friends import the file under Custom boards → Import.")
                Spacer(Modifier.height(8.dp))
                withBoards.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = category in selected,
                            onCheckedChange = { selected = if (it) selected + category else selected - category },
                        )
                        Text("$category (${library.boardsIn(category).size})")
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSave(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("Save file") }
                TextButton(onClick = { onShare(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("Share") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun sizeSummary(boards: List<CustomBoard>): String =
    if (boards.isEmpty()) {
        "Empty"
    } else {
        MatrixSize.entries.mapNotNull { size ->
            boards.count { it.size == size }.takeIf { it > 0 }?.let { "$it× ${size.dimension}×${size.dimension}" }
        }.joinToString(" · ")
    }

private fun fileName(categories: List<String>): String {
    val base = categories.singleOrNull()?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-')?.ifEmpty { null } ?: "boards"
    return "chameleon-$base.json"
}

/** Hands the export to another app (messenger, mail, Drive…) as a file. */
private fun shareBoards(context: Context, text: String, name: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, name).apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Chameleon boards")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share boards"))
}

private const val MAX_IMPORT_BYTES = 2_000_000

@Composable
fun CustomBoardEditorScreen(editor: AppViewModel.BoardEditor, categories: List<String>, onBack: () -> Unit) {
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
                Text("Category", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = category.equals(editor.category, ignoreCase = true),
                            onClick = { editor.category = category },
                            label = { Text(category) },
                        )
                    }
                }
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
