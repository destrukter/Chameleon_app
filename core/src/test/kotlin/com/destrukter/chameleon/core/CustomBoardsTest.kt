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
    fun storeRoundTripsAndReadsTheOldFormat() {
        val file = Files.createTempDirectory("boards").resolve("custom.json").toFile()
        val store = CustomBoardStore(file)
        assertEquals(CustomLibrary(), store.load())
        val library = CustomLibrary(listOf("Custom", "Family"), listOf(board("A", MatrixSize.SMALL), board("B", MatrixSize.LARGE).copy(category = "Family")))
        store.save(library)
        assertEquals(library, store.load())
        // The first release saved a bare list of boards.
        file.writeText("""[{"id":"x","name":"Old","size":"SMALL","words":${(1..16).map { "\"w$it\"" }}}]""")
        val old = store.load()
        assertEquals("Old", old.boards.single().name)
        assertEquals("Custom", old.boards.single().category)
        file.writeText("not json")
        assertEquals(CustomLibrary(), store.load())
    }

    @Test
    fun categoriesBecomePacksAndMoveBoards() {
        val reserved = testRepository().packs.map { it.name }
        var library = CustomLibrary()
        assertNotNull(library.categoryNameProblem("gaming", reserved))
        assertNotNull(library.categoryNameProblem("custom", reserved))
        assertNull(library.categoryNameProblem("Family", reserved))
        library = library.addCategory("Family")
            .copy(boards = listOf(board("Cousins", MatrixSize.SMALL).copy(category = "Family"), board("Misc", MatrixSize.SMALL)))
        val repo = CategoryRepository.withCustomBoards(testRepository(), library.boards)
        assertEquals(1, repo.packs.single { it.name == "Family" }.countFor(MatrixSize.SMALL))

        library = library.renameCategory("Family", "Relatives")
        assertEquals("Relatives", library.boards.first { it.name == "Cousins" }.category)
        library = library.deleteCategory("Relatives")
        assertEquals(listOf("Custom"), library.allCategories())
        assertEquals(setOf("Custom"), library.boards.map { it.category }.toSet())
    }

    @Test
    fun exportImportRoundTrip() {
        val mine = CustomLibrary(
            listOf("Custom", "Family", "Work"),
            listOf(board("Cousins", MatrixSize.SMALL).copy(category = "Family"), board("Office", MatrixSize.MEDIUM).copy(category = "Work")),
        )
        val shared = CustomLibraryCodec.decode(CustomLibraryCodec.encode(mine.only(listOf("Family"))))
        assertEquals(listOf("Family"), shared.categories)
        assertEquals(listOf("Cousins"), shared.boards.map { it.name })

        // A friend already has a different "Cousins" board in "Family" and a pack clashing with a built-in one.
        val reserved = listOf("Gaming")
        val friend = CustomLibrary(listOf("Custom", "Family"), listOf(board("Cousins", MatrixSize.SMALL, "other").copy(category = "Family")))
        val incoming = shared.copy(
            categories = shared.categories + "Gaming",
            boards = shared.boards + board("Broken", MatrixSize.SMALL).copy(words = emptyList()) +
                board("Retro", MatrixSize.SMALL).copy(category = "Gaming"),
        )
        val result = friend.merge(incoming, reserved)
        assertEquals(2, result.addedBoards)
        assertEquals(1, result.skipped)
        assertEquals(setOf("Cousins", "Cousins (2)"), result.library.boardsIn("Family").map { it.name }.toSet())
        assertEquals("Gaming (custom)", result.library.boards.single { it.name == "Retro" }.category)

        // Importing the same file again replaces the boards instead of duplicating them.
        val again = result.library.merge(incoming, reserved)
        assertEquals(result.library.boards.size, again.library.boards.size)
    }

    @Test
    fun rejectsForeignFiles() {
        assertTrue(runCatching { CustomLibraryCodec.decode("{}") }.isFailure)
        assertTrue(runCatching { CustomLibraryCodec.decode("""{"format":"other","version":1,"categories":[],"boards":[]}""") }.isFailure)
        assertTrue(runCatching { CustomLibraryCodec.decode("""{"format":"chameleon-boards","version":99,"categories":[],"boards":[]}""") }.isFailure)
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
