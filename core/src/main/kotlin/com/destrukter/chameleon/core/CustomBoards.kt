package com.destrukter.chameleon.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * A board the player designed: a topic name and exactly one word per cell of [size], in reading order.
 * [category] is the player's own pack the board is sorted into.
 */
@Serializable
data class CustomBoard(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val size: MatrixSize,
    val words: List<String>,
    val category: String = CategoryRepository.CUSTOM_PACK,
) {
    /** Returns what is wrong with the board, or null when it can be played. */
    fun problem(): String? {
        val cleaned = words.map { it.trim() }
        return when {
            name.isBlank() -> "Give the board a topic name."
            name.trim().length > MAX_NAME_LENGTH -> "The topic name can have at most $MAX_NAME_LENGTH characters."
            cleaned.size != size.cellCount -> "A ${size.dimension}×${size.dimension} board needs ${size.cellCount} words."
            cleaned.any { it.isEmpty() } -> "Fill in all ${size.cellCount} words (${cleaned.count { it.isEmpty() }} empty)."
            cleaned.any { it.length > MAX_WORD_LENGTH } -> "Words can have at most $MAX_WORD_LENGTH characters."
            cleaned.map { it.lowercase() }.toSet().size != cleaned.size -> {
                val duplicate = cleaned.groupBy { it.lowercase() }.values.first { it.size > 1 }.first()
                "\"$duplicate\" appears more than once."
            }
            else -> null
        }
    }

    /** Trims whitespace so stored boards compare and display cleanly. */
    fun cleaned(): CustomBoard = copy(
        name = name.trim(),
        words = words.map { it.trim().replace(Regex("\\s+"), " ") },
        category = category.trim().ifEmpty { CategoryRepository.CUSTOM_PACK },
    )

    companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_WORD_LENGTH = 18
    }
}

/**
 * Everything the player made: their own categories (packs) and the boards sorted into them.
 * The default "Custom" category always exists.
 */
@Serializable
data class CustomLibrary(
    val categories: List<String> = listOf(CategoryRepository.CUSTOM_PACK),
    val boards: List<CustomBoard> = emptyList(),
) {
    /** Categories in display order: "Custom" first, then any a board uses even if it was never declared. */
    fun allCategories(): List<String> =
        (listOf(CategoryRepository.CUSTOM_PACK) + categories + boards.map { it.category }).distinctBy { it.lowercase() }

    fun boardsIn(category: String): List<CustomBoard> = boards.filter { it.category.equals(category, ignoreCase = true) }

    /** Returns a problem with the name, or null when [name] can be used as a new category. */
    fun categoryNameProblem(name: String, reserved: Collection<String>, ignoring: String? = null): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Give the category a name."
            trimmed.length > MAX_CATEGORY_LENGTH -> "Category names can have at most $MAX_CATEGORY_LENGTH characters."
            reserved.any { it.equals(trimmed, ignoreCase = true) } -> "\"$trimmed\" is already a built-in pack."
            allCategories().any { it.equals(trimmed, ignoreCase = true) && !it.equals(ignoring, ignoreCase = true) } ->
                "You already have a category called \"$trimmed\"."
            else -> null
        }
    }

    fun addCategory(name: String): CustomLibrary = copy(categories = allCategories() + name.trim())

    /** Renames a category and moves its boards along. */
    fun renameCategory(from: String, to: String): CustomLibrary = copy(
        categories = allCategories().map { if (it.equals(from, ignoreCase = true)) to.trim() else it },
        boards = boards.map { if (it.category.equals(from, ignoreCase = true)) it.copy(category = to.trim()) else it },
    )

    /** Deletes a category; its boards move to "Custom" rather than being lost. */
    fun deleteCategory(name: String): CustomLibrary = copy(
        categories = allCategories().filterNot { it.equals(name, ignoreCase = true) },
        boards = boards.map {
            if (it.category.equals(name, ignoreCase = true)) it.copy(category = CategoryRepository.CUSTOM_PACK) else it
        },
    )

    /** Only the given categories and their boards, e.g. to share some of them. */
    fun only(selected: Collection<String>): CustomLibrary {
        val keep = { c: String -> selected.any { it.equals(c, ignoreCase = true) } }
        return CustomLibrary(allCategories().filter(keep), boards.filter { keep(it.category) })
    }

    data class ImportResult(val library: CustomLibrary, val addedBoards: Int, val addedCategories: Int, val skipped: Int)

    /**
     * Adds an imported library. Categories are matched by name; a board that is already here (same id) is
     * replaced, a different board with a taken name in the same category gets a numbered name, and boards that
     * are not playable are skipped. Imported categories that clash with built-in packs get " (custom)" appended.
     */
    fun merge(imported: CustomLibrary, reserved: Collection<String>): ImportResult {
        var result = this
        var addedCategories = 0
        var addedBoards = 0
        var skipped = 0
        val renamed = mutableMapOf<String, String>()
        for (category in imported.allCategories()) {
            val target = if (reserved.any { it.equals(category, ignoreCase = true) }) "$category (custom)" else category
            renamed[category.lowercase()] = target
            if (result.allCategories().none { it.equals(target, ignoreCase = true) }) {
                result = result.addCategory(target)
                addedCategories++
            }
        }
        for (raw in imported.boards) {
            val board = raw.cleaned().let { it.copy(category = renamed[it.category.lowercase()] ?: it.category) }
            if (board.problem() != null || board.category.length > MAX_CATEGORY_LENGTH) {
                skipped++
                continue
            }
            val others = result.boards.filter { it.id != board.id }
            var name = board.name
            var n = 2
            while (others.any { it.category.equals(board.category, true) && it.name.equals(name, true) }) {
                name = "${board.name} ($n)".take(CustomBoard.MAX_NAME_LENGTH)
                n++
            }
            result = result.copy(boards = others + board.copy(name = name))
            addedBoards++
        }
        return ImportResult(result, addedBoards, addedCategories, skipped)
    }

    companion object {
        const val MAX_CATEGORY_LENGTH = 24
    }
}

/** The file format used to share boards between phones. */
object CustomLibraryCodec {
    const val FORMAT = "chameleon-boards"
    const val VERSION = 1

    @Serializable
    private data class Envelope(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val categories: List<String>,
        val boards: List<CustomBoard>,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun encode(library: CustomLibrary): String =
        json.encodeToString(Envelope.serializer(), Envelope(categories = library.categories, boards = library.boards))

    /** Reads a shared file. Throws [IllegalArgumentException] with a readable message if it is not one. */
    fun decode(text: String): CustomLibrary {
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This is not a Chameleon boards file.", e)
        }
        require(envelope.format == FORMAT) { "This is not a Chameleon boards file." }
        require(envelope.version <= VERSION) { "This file was made by a newer version of the app. Please update." }
        return CustomLibrary(envelope.categories, envelope.boards)
    }
}

/** Saves the player's categories and boards as JSON in a single file. */
class CustomBoardStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun load(): CustomLibrary {
        if (!file.exists()) return CustomLibrary()
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return CustomLibrary()
        return runCatching { json.decodeFromString(CustomLibrary.serializer(), text) }.getOrNull()
            // Version 1 of the app stored a plain list of boards.
            ?: runCatching { CustomLibrary(boards = json.decodeFromString(ListSerializer(CustomBoard.serializer()), text)) }.getOrNull()
            ?: CustomLibrary()
    }

    fun save(library: CustomLibrary) {
        file.parentFile?.mkdirs()
        // Write to a temporary file first so a crash mid-write cannot destroy the saved boards.
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(CustomLibrary.serializer(), library), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
