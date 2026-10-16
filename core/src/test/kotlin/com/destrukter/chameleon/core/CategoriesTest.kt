package com.destrukter.chameleon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CategoriesTest {

    @Test
    fun bundledWordListHas500FullCategories() {
        val repo = CategoryRepository.loadBundled()
        assertEquals(500, repo.categories.size)
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
