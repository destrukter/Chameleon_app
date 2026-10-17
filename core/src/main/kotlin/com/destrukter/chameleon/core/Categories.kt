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

/**
 * A topic. Bundled topics hold enough words for the largest board and smaller boards use a random subset.
 * A custom board has [fixedSize] set: it only fits that board size and keeps its words in the order laid out.
 */
data class Category(
    val name: String,
    val words: List<String>,
    val pack: String = CategoryRepository.DEFAULT_PACK,
    val fixedSize: MatrixSize? = null,
) {
    fun fits(size: MatrixSize): Boolean =
        if (fixedSize != null) fixedSize == size && words.size == size.cellCount else words.size >= size.cellCount
}

/** A themed deck of topics that players can switch on or off. [sizeCounts] says how many topics fit each board size. */
@Serializable
data class PackInfo(
    val name: String,
    val topicCount: Int,
    val sizeCounts: Map<MatrixSize, Int> = emptyMap(),
) {
    fun countFor(size: MatrixSize): Int = sizeCounts[size] ?: topicCount
}

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

    /** The packs in file order, with how many topics each holds. */
    val packs: List<PackInfo> = categories.groupBy { it.pack }.map { (name, topics) ->
        PackInfo(name, topics.size, MatrixSize.entries.associateWith { size -> topics.count { it.fits(size) } })
    }

    /** Whether the enabled packs hold at least one topic for this board size. */
    fun hasTopics(size: MatrixSize, disabledPacks: Set<String>): Boolean =
        categories.any { it.pack !in disabledPacks && it.fits(size) }

    /** Packs that would remain after switching off [disabledPacks]; all packs if that would leave none. */
    fun enabledPacks(disabledPacks: Set<String>): Set<String> {
        val enabled = packs.map { it.name }.filter { it !in disabledPacks }.toSet()
        return enabled.ifEmpty { packs.map { it.name }.toSet() }
    }

    /**
     * Draws a random category and a random selection of its words for the given size.
     * Topics from [disabledPacks] are skipped, and [exclude] lets callers avoid immediately re-dealing a
     * category the group just rejected. Both filters are relaxed when they would leave nothing to deal;
     * callers check [hasTopics] first so a pack choice that fits no topic of this size is reported instead.
     */
    fun deal(
        size: MatrixSize,
        random: Random,
        exclude: Set<String> = emptySet(),
        disabledPacks: Set<String> = emptySet(),
    ): WordMatrix {
        val packs = enabledPacks(disabledPacks)
        val playable = categories.filter { it.pack in packs && it.fits(size) }
            .ifEmpty { categories.filter { it.fits(size) } }
        val pool = playable.filter { it.name !in exclude }.ifEmpty { playable }
        val category = pool.random(random)
        val words = if (category.fixedSize != null) category.words else category.words.shuffled(random).take(size.cellCount)
        return WordMatrix(category.name, size, words)
    }

    companion object {
        const val RESOURCE = "/categories.txt"
        const val WORDS_PER_CATEGORY = 36
        const val DEFAULT_PACK = "General"
        const val CUSTOM_PACK = "Custom"

        /**
         * Parses lines of the form `Category|word1,word2,...`. A `[Pack name]` line puts the following
         * categories into that pack. Blank lines and `#` comments are skipped.
         */
        fun parse(text: String): CategoryRepository {
            var pack = DEFAULT_PACK
            val categories = mutableListOf<Category>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                when {
                    line.isEmpty() || line.startsWith("#") -> Unit
                    line.startsWith("[") && line.endsWith("]") -> pack = line.substring(1, line.length - 1).trim()
                    else -> {
                        val name = line.substringBefore('|').trim()
                        val words = line.substringAfter('|').split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        categories += Category(name, words, pack)
                    }
                }
            }
            return CategoryRepository(categories)
        }

        /** A copy of [base] with the player's own boards added, each category as its own pack. */
        fun withCustomBoards(base: CategoryRepository, boards: List<CustomBoard>): CategoryRepository =
            if (boards.isEmpty()) {
                base
            } else {
                CategoryRepository(base.categories + boards.map { Category(it.name, it.words, it.category, it.size) })
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

    /** Multiplayer can start with two phones so the online flow is easy to test; the game is meant for three or more. */
    const val MIN_ONLINE_PLAYERS = 2
    const val MAX_LOCAL_PLAYERS = 30
    const val MAX_ONLINE_PLAYERS = 16

    /** Chameleons must stay a minority so the group can still find them. */
    fun maxChameleons(playerCount: Int): Int = maxOf(1, (playerCount - 1) / 2)
}
