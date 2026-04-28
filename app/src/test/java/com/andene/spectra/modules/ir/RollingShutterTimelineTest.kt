package com.andene.spectra.modules.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for the rolling-shutter timeline reconstruction.
 *
 * The decoder is a pure function (no Android types) that takes a list of
 * (frame-start-nanos, per-row-luminance) pairs and produces an alternating
 * mark/space duration list in microseconds. We synthesize frames that
 * encode known IR pulse trains and check the round-trip.
 */
class RollingShutterTimelineTest {

    /**
     * Generate fake frames carrying a deterministic IR pulse sequence.
     * Mirrors a CMOS sensor: each row's luminance depends on whether the
     * IR LED is currently asserted at that row's exposure time.
     */
    private fun synthesize(
        timingsUs: IntArray,
        frameCount: Int = 8,
        rows: Int = 720,
        fps: Double = 30.0,
        startsOn: Boolean = true,
        onValue: Int = 240,
        offValue: Int = 20,
    ): List<Pair<Long, IntArray>> {
        val frameIntervalNs = (1_000_000_000.0 / fps).toLong()
        val perRowNs = frameIntervalNs.toDouble() / rows

        // Cumulative segment boundaries, in nanoseconds.
        val edges = LongArray(timingsUs.size + 1)
        for (i in timingsUs.indices) {
            edges[i + 1] = edges[i] + timingsUs[i] * 1_000L
        }

        fun isOnAt(tNs: Long): Boolean {
            for (i in timingsUs.indices) {
                if (tNs < edges[i + 1]) {
                    val onPhase = (i % 2 == 0)
                    return if (startsOn) onPhase else !onPhase
                }
            }
            return false
        }

        return (0 until frameCount).map { f ->
            val frameStartNs = f * frameIntervalNs
            val perRow = IntArray(rows) { row ->
                val tNs = frameStartNs + (row * perRowNs).toLong()
                if (isOnAt(tNs)) onValue else offValue
            }
            frameStartNs to perRow
        }
    }

    @Test
    fun `decodeTimeline reproduces a NEC header within row-tick tolerance`() {
        // 9000µs mark, 4500µs space — the NEC leader. The trailing 562µs
        // mark anchors the OFF segment so it doesn't bleed to capture end.
        val timings = intArrayOf(9000, 4500, 562)
        val frames = synthesize(timings)
        // perRowNs at 30fps × 720 rows ≈ 46.3 µs/row, so allow ~3 rows of slop.
        val tolerance = 150 // microseconds
        val decoded = IrCameraCapture.decodeTimeline(frames, onThreshold = 128)

        assertTrue("expected ≥ 3 segments, got ${decoded.size}", decoded.size >= 3)
        assertTrue(
            "leader mark: expected ${timings[0]} ±$tolerance, got ${decoded[0]}",
            abs(decoded[0] - timings[0]) <= tolerance,
        )
        assertTrue(
            "leader space: expected ${timings[1]} ±$tolerance, got ${decoded[1]}",
            abs(decoded[1] - timings[1]) <= tolerance,
        )
    }

    @Test
    fun `decodeTimeline resolves bit-width pulses well below frame rate`() {
        // Sub-millisecond bit pulses — the sample-per-frame approach can't
        // see these at all (33 ms/frame), but rolling-shutter at ~46 µs/row
        // gives us ~22 samples per pulse. The final 200µs anchors the last
        // closed segment; the trailing OFF that runs to capture-end is
        // outside the assertion window.
        val timings = intArrayOf(1000, 1000, 1000, 1000, 200)
        val frames = synthesize(timings, frameCount = 4)
        val decoded = IrCameraCapture.decodeTimeline(frames, onThreshold = 128)

        assertTrue("expected ≥ 5 segments, got ${decoded.size}", decoded.size >= 5)
        for (i in 0 until 4) {
            assertTrue(
                "segment $i expected ${timings[i]}, got ${decoded[i]}",
                abs(decoded[i] - timings[i]) <= 200,
            )
        }
    }

    @Test
    fun `decodeTimeline returns empty when input has fewer than two frames`() {
        val empty = emptyList<Pair<Long, IntArray>>()
        assertEquals(0, IrCameraCapture.decodeTimeline(empty, onThreshold = 128).size)

        val oneFrame = listOf(0L to IntArray(720) { 200 })
        assertEquals(0, IrCameraCapture.decodeTimeline(oneFrame, onThreshold = 128).size)
    }

    @Test
    fun `decodeTimeline records nothing when the column never crosses threshold`() {
        // Steady dark column across 4 frames — no ON segment, no OFF transitions.
        val frames = (0 until 4).map { f ->
            (f * 33_333_333L) to IntArray(720) { 30 }
        }
        val decoded = IrCameraCapture.decodeTimeline(frames, onThreshold = 200)
        // We may or may not emit a single trailing OFF segment; we just want to
        // confirm we don't fabricate transitions.
        assertTrue("decoded $decoded should have ≤ 1 entries", decoded.size <= 1)
    }

    @Test
    fun `computeThreshold sits between the dark baseline and bright peak`() {
        val frames = listOf(
            0L to IntArray(720) { row -> if (row < 200) 250 else 15 },
        )
        val threshold = IrCameraCapture.computeThreshold(frames)
        assertTrue("threshold $threshold should exceed baseline", threshold > 30)
        assertTrue("threshold $threshold should be below peak", threshold < 250)
    }

    @Test
    fun `computeThreshold returns a usable value for steady-dark input`() {
        val frames = listOf(0L to IntArray(100) { 20 })
        val threshold = IrCameraCapture.computeThreshold(frames)
        // gap is clamped to 20, midpoint = 20 + 10 = 30. Anything above the
        // floor is fine; we just want to not return 0 or a non-finite value.
        assertTrue("threshold $threshold should be ≥ baseline", threshold >= 20)
    }
}
