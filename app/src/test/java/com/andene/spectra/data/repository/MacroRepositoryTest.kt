package com.andene.spectra.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andene.spectra.data.models.Macro
import com.andene.spectra.data.models.MacroStep
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Round-trip tests for the macros JSON file. Atomic temp-file-then-rename
 * is the persistence contract; verifying we can save → load → see
 * identical content also confirms no schema drift between writes and
 * reads.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 34])
class MacroRepositoryTest {

    private lateinit var repo: MacroRepository

    @Before
    fun setUp() {
        repo = MacroRepository(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .filesDir.resolve("macros.json").delete()
    }

    @Test
    fun `empty load returns empty list`() = runTest {
        assertEquals(0, repo.loadAll().size)
    }

    @Test
    fun `save then load reproduces all steps`() = runTest {
        val movieNight = Macro(
            id = "macro-1",
            name = "Movie Night",
            steps = listOf(
                MacroStep("dev-tv", "Living Room TV", "power", delayBeforeMs = 0),
                MacroStep("dev-tv", "Living Room TV", "input", delayBeforeMs = 1500),
                MacroStep("dev-avr", "AVR", "power", delayBeforeMs = 500),
            ),
            createdAt = 1234567890L,
        )
        assertTrue(repo.saveAll(listOf(movieNight)))

        val loaded = repo.loadAll()
        assertEquals(1, loaded.size)
        val r = loaded[0]
        assertEquals("macro-1", r.id)
        assertEquals("Movie Night", r.name)
        assertEquals(1234567890L, r.createdAt)
        assertEquals(3, r.steps.size)
        assertEquals("power", r.steps[0].commandName)
        assertEquals(1500, r.steps[1].delayBeforeMs)
        assertEquals("dev-avr", r.steps[2].deviceId)
    }

    @Test
    fun `saveAll replaces existing macros entirely`() = runTest {
        repo.saveAll(listOf(Macro(id = "old", name = "Old")))
        repo.saveAll(listOf(Macro(id = "new", name = "New")))
        val loaded = repo.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("new", loaded[0].id)
    }

    @Test
    fun `multiple macros preserve order`() = runTest {
        val macros = listOf(
            Macro(id = "a", name = "Alpha"),
            Macro(id = "b", name = "Beta"),
            Macro(id = "c", name = "Gamma"),
        )
        repo.saveAll(macros)
        val loaded = repo.loadAll()
        assertEquals(listOf("a", "b", "c"), loaded.map { it.id })
    }

    @Test
    fun `saveAll empty list clears the file`() = runTest {
        repo.saveAll(listOf(Macro(id = "x", name = "X")))
        assertEquals(1, repo.loadAll().size)
        repo.saveAll(emptyList())
        assertEquals(0, repo.loadAll().size)
    }

    @Test
    fun `corrupted file produces empty list and doesn't crash`() = runTest {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .filesDir.resolve("macros.json").writeText("{not valid")
        val loaded = repo.loadAll()
        assertEquals(0, loaded.size)
    }
}
