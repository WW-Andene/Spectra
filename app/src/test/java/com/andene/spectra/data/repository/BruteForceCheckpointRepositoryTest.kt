package com.andene.spectra.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andene.spectra.data.models.BruteForceCheckpoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Round-trip tests for BruteForceCheckpointRepository — the BF resume
 * feature added in cycle 8 (F-004) was untested. A regression here means
 * a user resuming a brute force resumes from the wrong attempt or fails
 * to resume at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 34])
class BruteForceCheckpointRepositoryTest {

    private lateinit var repo: BruteForceCheckpointRepository

    @Before
    fun setUp() {
        repo = BruteForceCheckpointRepository(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .filesDir.resolve("bruteforce_checkpoint.json").delete()
    }

    @Test
    fun `empty load returns null`() = runTest {
        assertNull(repo.load())
    }

    @Test
    fun `save then load round-trips all fields`() = runTest {
        val cp = BruteForceCheckpoint(
            deviceId = "tv-living-room",
            deviceName = "Living Room TV",
            brandFilter = "Samsung",
            nextAttemptIndex = 17,
            startedAt = 1_700_000_000L,
        )
        repo.save(cp)
        val loaded = repo.load()
        assertNotNull(loaded)
        assertEquals(cp.deviceId, loaded!!.deviceId)
        assertEquals(cp.deviceName, loaded.deviceName)
        assertEquals(cp.brandFilter, loaded.brandFilter)
        assertEquals(cp.nextAttemptIndex, loaded.nextAttemptIndex)
        assertEquals(cp.startedAt, loaded.startedAt)
    }

    @Test
    fun `save replaces previous checkpoint`() = runTest {
        repo.save(BruteForceCheckpoint(deviceId = "old", deviceName = "Old", nextAttemptIndex = 5))
        repo.save(BruteForceCheckpoint(deviceId = "new", deviceName = "New", nextAttemptIndex = 12))
        val loaded = repo.load()
        assertEquals("new", loaded!!.deviceId)
        assertEquals(12, loaded.nextAttemptIndex)
    }

    @Test
    fun `clear removes the checkpoint`() = runTest {
        repo.save(BruteForceCheckpoint(deviceId = "x", deviceName = "X"))
        assertNotNull(repo.load())
        repo.clear()
        assertNull(repo.load())
    }

    @Test
    fun `stale checkpoint older than 24h is dropped on load`() = runTest {
        // 25 hours ago — outside the 24h freshness window the repo enforces.
        val twentyFiveHoursAgo = System.currentTimeMillis() - (25L * 60 * 60 * 1000)
        repo.save(BruteForceCheckpoint(
            deviceId = "stale",
            deviceName = "Stale TV",
            nextAttemptIndex = 7,
            startedAt = twentyFiveHoursAgo,
        ))
        assertNull("stale checkpoint should not survive load", repo.load())
        // Stale-on-load also clears the file; subsequent loads are empty.
        assertNull(repo.load())
    }

    @Test
    fun `corrupted file returns null and self-heals`() = runTest {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .filesDir.resolve("bruteforce_checkpoint.json").writeText("{not valid json")
        assertNull(repo.load())
        // Self-heal: subsequent save+load works
        repo.save(BruteForceCheckpoint(deviceId = "fresh", deviceName = "Fresh"))
        assertEquals("fresh", repo.load()!!.deviceId)
    }
}
