package com.destrukter.chameleon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CategoriesTest {

    @Test
    fun bundledWordListHas500FullCategories() {
        val repo = CategoryRepository.loadBundled()
        assertTrue("only ${repo.categories.size} topics", repo.categories.size >= 500)
        val names = repo.categories.map { it.name.lowercase() }
        assertEquals("Duplicate category names: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}", names.size, names.toSet().size)
        for (category in repo.categories) {
            assertEquals("${category.name} word count", CategoryRepository.WORDS_PER_CATEGORY, category.words.size)
            val lower = category.words.map { it.lowercase() }
            assertEquals("${category.name} has duplicates: ${lower.groupBy { it }.filter { it.value.size > 1 }.keys}", lower.size, lower.toSet().size)
            category.words.forEach { assertTrue("${category.name}: '$it' too long", it.length <= 18) }
        }
    }

    @Test
    fun bundledTopicsAreSortedIntoPacks() {
        val repo = CategoryRepository.loadBundled()
        assertTrue(repo.categories.none { it.pack == CategoryRepository.DEFAULT_PACK })
        assertTrue(repo.packs.size >= 8)
        val gaming = repo.packs.single { it.name == "Gaming" }
        assertTrue("Gaming has ${gaming.topicCount} topics", gaming.topicCount >= 20)
        repo.packs.forEach { assertTrue("${it.name} is too small", it.topicCount >= 10) }
    }

    @Test
    fun parsesPackHeaders() {
        val repo = CategoryRepository.parse(
            """
            # comment
            Loose|a,b
            [Gaming]
            Consoles|x,y
            Arcade|z
            [Food]
            Fruit|f
            """.trimIndent()
        )
        assertEquals(listOf("General" to 1, "Gaming" to 2, "Food" to 1), repo.packs.map { it.name to it.topicCount })
        assertEquals("Gaming", repo.categories.single { it.name == "Arcade" }.pack)
    }

    @Test
    fun dealOnlyUsesEnabledPacks() {
        val repo = CategoryRepository(
            (1..6).map { c -> Category("Topic $c", (1..36).map { "W$c-$it" }, pack = if (c <= 2) "Gaming" else "Food") }
        )
        repeat(50) {
            val matrix = repo.deal(MatrixSize.LARGE, Random(it), disabledPacks = setOf("Food"))
            assertTrue(matrix.category in setOf("Topic 1", "Topic 2"))
        }
        // Switching off every pack falls back to all of them rather than failing.
        assertEquals(setOf("Gaming", "Food"), repo.enabledPacks(setOf("Gaming", "Food")))
    }

    @Test
    fun dealProducesMatrixOfRequestedSize() {
        val repo = testRepository()
        for (size in MatrixSize.entries) {
            val matrix = repo.deal(size, Random(1))
            assertEquals(size.cellCount, matrix.words.size)
            assertEquals(size.cellCount, matrix.words.toSet().size)
        }
    }

    @Test
    fun dealAvoidsExcludedCategories() {
        val repo = testRepository(2)
        repeat(20) { assertEquals("Topic 2", repo.deal(MatrixSize.SMALL, Random(it), setOf("Topic 1")).category) }
    }

    @Test
    fun localRoundClampsChameleons() {
        val matrix = testRepository().deal(MatrixSize.SMALL, Random(3))
        val round = LocalRound.create(listOf("A", "B", "C", "D"), chameleonCount = 5, matrix = matrix, random = Random(3))
        assertEquals(1, round.chameleons.size)
        val big = LocalRound.create((1..12).map { "P$it" }, chameleonCount = 3, matrix = matrix, random = Random(3))
        assertEquals(3, big.chameleons.size)
        assertTrue(big.startPlayer in 0 until 12)
    }

    @Test
    fun coordinatesMatchGrid() {
        val matrix = testRepository().deal(MatrixSize.MEDIUM, Random(0))
        assertEquals("A1", matrix.coordinateOf(0))
        assertEquals("E1", matrix.coordinateOf(4))
        assertEquals("A2", matrix.coordinateOf(5))
        assertEquals("E5", matrix.coordinateOf(24))
    }
}
