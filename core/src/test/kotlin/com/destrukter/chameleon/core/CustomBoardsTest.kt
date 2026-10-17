package com.destrukter.chameleon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.random.Random

class CustomBoardsTest {
    private fun board(name: String, size: MatrixSize, prefix: String = name) =
        CustomBoard(name = name, size = size, words = (1..size.cellCount).map { "$prefix $it" })

    @Test
    fun validation() {
        assertNull(board("Friends", MatrixSize.SMALL).problem())
        assertNotNull(board(" ", MatrixSize.SMALL).problem())
        val small = board("Friends", MatrixSize.SMALL)
        assertNotNull(small.copy(words = small.words.dropLast(1)).problem())
        assertNotNull(small.copy(words = small.words.dropLast(1) + "").problem())
        assertTrue(small.copy(words = small.words.dropLast(1) + "friends 1").problem()!!.contains("more than once"))
        assertNotNull(small.copy(words = small.words.dropLast(1) + "x".repeat(19)).problem())
    }

    @Test
    fun storeRoundTrips() {
        val file = Files.createTempDirectory("boards").resolve("custom.json").toFile()
        val store = CustomBoardStore(file)
        assertEquals(emptyList<CustomBoard>(), store.load())
        val boards = listOf(board("A", MatrixSize.SMALL), board("B", MatrixSize.LARGE))
        store.save(boards)
        assertEquals(boards, store.load())
        file.writeText("not json")
        assertEquals(emptyList<CustomBoard>(), store.load())
    }

    @Test
    fun customBoardsOnlyFitTheirOwnSize() {
        val repo = CategoryRepository.withCustomBoards(testRepository(), listOf(board("Mine", MatrixSize.MEDIUM)))
        val custom = repo.packs.single { it.name == CategoryRepository.CUSTOM_PACK }
        assertEquals(0, custom.countFor(MatrixSize.SMALL))
        assertEquals(1, custom.countFor(MatrixSize.MEDIUM))
        assertEquals(0, custom.countFor(MatrixSize.LARGE))

        val onlyCustom = repo.packs.map { it.name }.filter { it != CategoryRepository.CUSTOM_PACK }.toSet()
        assertTrue(repo.hasTopics(MatrixSize.MEDIUM, onlyCustom))
        assertFalse(repo.hasTopics(MatrixSize.SMALL, onlyCustom))

        // A custom board is dealt exactly as laid out.
        val matrix = repo.deal(MatrixSize.MEDIUM, Random(1), disabledPacks = onlyCustom)
        assertEquals("Mine", matrix.category)
        assertEquals((1..25).map { "Mine $it" }, matrix.words)
    }

    @Test
    fun noCustomPackWithoutBoards() {
        val repo = CategoryRepository.withCustomBoards(testRepository(), emptyList())
        assertTrue(repo.packs.none { it.name == CategoryRepository.CUSTOM_PACK })
    }
}
