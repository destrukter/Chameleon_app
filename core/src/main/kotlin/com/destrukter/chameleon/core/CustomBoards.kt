package com.destrukter.chameleon.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A board the player designed: a topic name and exactly one word per cell of [size], in reading order. */
@Serializable
data class CustomBoard(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val size: MatrixSize,
    val words: List<String>,
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
    fun cleaned(): CustomBoard = copy(name = name.trim(), words = words.map { it.trim().replace(Regex("\\s+"), " ") })

    companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_WORD_LENGTH = 18
    }
}

/** Saves the player's custom boards as JSON in a single file. */
class CustomBoardStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val serializer = ListSerializer(CustomBoard.serializer())

    fun load(): List<CustomBoard> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    fun save(boards: List<CustomBoard>) {
        file.parentFile?.mkdirs()
        // Write to a temporary file first so a crash mid-write cannot destroy the saved boards.
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, boards), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
