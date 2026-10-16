package com.destrukter.chameleon.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The three board presets. SMALL matches the 4x4 card of the original game. */
@Serializable
enum class MatrixSize(val dimension: Int, val label: String) {
    SMALL(4, "Small 4×4"),
    MEDIUM(5, "Medium 5×5"),
    LARGE(6, "Large 6×6");

    val cellCount: Int get() = dimension * dimension
}

/** A topic with enough words to fill the largest matrix. */
data class Category(val name: String, val words: List<String>)

/** A dealt topic card: [words] are laid out row by row in a [size] grid. */
@Serializable
data class WordMatrix(
    val category: String,
    val size: MatrixSize,
    val words: List<String>,
) {
    init {
        require(words.size == size.cellCount) { "Matrix needs ${size.cellCount} words, got ${words.size}" }
    }

    /** Row/column label like "B3", mirroring the dice coordinates on the physical card. */
    fun coordinateOf(index: Int): String {
        val row = index / size.dimension
        val col = index % size.dimension
        return "${'A' + col}${row + 1}"
    }
}

class CategoryRepository(val categories: List<Category>) {

    init {
        require(categories.isNotEmpty()) { "No categories loaded" }
    }

    /**
     * Draws a random category and a random selection of its words for the given size.
     * [exclude] lets callers avoid immediately re-dealing a category the group just rejected.
     */
    fun deal(size: MatrixSize, random: Random, exclude: Set<String> = emptySet()): WordMatrix {
        val pool = categories.filter { it.name !in exclude && it.words.size >= size.cellCount }
            .ifEmpty { categories.filter { it.words.size >= size.cellCount } }
        val category = pool.random(random)
        val words = category.words.shuffled(random).take(size.cellCount)
        return WordMatrix(category.name, size, words)
    }

    companion object {
        const val RESOURCE = "/categories.txt"
        const val WORDS_PER_CATEGORY = 36

        /** Parses lines of the form `Category|word1,word2,...`. Blank lines and `#` comments are skipped. */
        fun parse(text: String): CategoryRepository {
            val categories = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val name = line.substringBefore('|').trim()
                    val words = line.substringAfter('|').split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    Category(name, words)
                }
                .toList()
            return CategoryRepository(categories)
        }

        /** Loads the bundled word list shipped as a resource of this module. */
        fun loadBundled(): CategoryRepository {
            val stream = CategoryRepository::class.java.getResourceAsStream(RESOURCE)
                ?: error("Missing $RESOURCE resource")
            return stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }
    }
}

object Rules {
    const val MIN_PLAYERS = 3
    const val MAX_LOCAL_PLAYERS = 30
    const val MAX_ONLINE_PLAYERS = 16

    /** Chameleons must stay a minority so the group can still find them. */
    fun maxChameleons(playerCount: Int): Int = maxOf(1, (playerCount - 1) / 2)
}
