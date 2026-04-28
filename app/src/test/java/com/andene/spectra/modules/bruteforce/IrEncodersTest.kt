package com.andene.spectra.modules.bruteforce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the static IR protocol encoders in IrBruteForce.
 *
 * The encoders are pure Kotlin arithmetic in a companion object — no
 * Android APIs touched — so they run on the host JVM. These tests pin
 * down the exact bit layout because a one-off off-by-one error here
 * would silently produce IR pulses that no real device understands.
 */
class IrEncodersTest {

    @Test
    fun `NEC header is 9000 mark + 4500 space`() {
        val timings = IrBruteForce.encodeNEC(0x07, 0x02)
        assertEquals(9000, timings[0])
        assertEquals(4500, timings[1])
    }

    @Test
    fun `NEC produces 32 bits + header + stop = 67 entries`() {
        val timings = IrBruteForce.encodeNEC(0x07, 0x02)
        // 2 header + 32 bits × 2 entries each + 1 stop bit
        assertEquals(2 + 64 + 1, timings.size)
    }

    @Test
    fun `NEC bits encode address, ~address, command, ~command`() {
        // Pick distinct values so we can read off the bit pattern.
        val timings = IrBruteForce.encodeNEC(0xAB, 0x12)
        // Skip the 2-entry header. The remainder is mark/space pairs:
        //   mark = 562, space = 562 (bit 0) or 1687 (bit 1)
        val bits = mutableListOf<Int>()
        var i = 2
        while (i + 1 < timings.size - 1) { // -1 to skip stop bit
            assertEquals("mark $i should be 562", 562, timings[i])
            val space = timings[i + 1]
            bits.add(if (space == 1687) 1 else 0)
            i += 2
        }
        // Reconstruct the 32-bit value LSB-first
        var value = 0L
        for ((idx, bit) in bits.withIndex()) {
            value = value or (bit.toLong() shl idx)
        }
        // Layout: addr | ~addr | cmd | ~cmd
        val addr = (value and 0xFF).toInt()
        val notAddr = ((value shr 8) and 0xFF).toInt()
        val cmd = ((value shr 16) and 0xFF).toInt()
        val notCmd = ((value shr 24) and 0xFF).toInt()
        assertEquals(0xAB, addr)
        assertEquals(0xAB.inv() and 0xFF, notAddr)
        assertEquals(0x12, cmd)
        assertEquals(0x12.inv() and 0xFF, notCmd)
    }

    @Test
    fun `NEC stop bit is a 562us mark`() {
        val timings = IrBruteForce.encodeNEC(0x07, 0x02)
        assertEquals(562, timings.last())
    }

    @Test
    fun `Samsung header is 4500 mark + 4500 space`() {
        val timings = IrBruteForce.encodeSamsung(0x07, 0x02)
        assertEquals(4500, timings[0])
        assertEquals(4500, timings[1])
    }

    @Test
    fun `Samsung uses 560us mark, bit-1 space 1690us`() {
        val timings = IrBruteForce.encodeSamsung(0xFF, 0x00) // bit-1-heavy address
        var sawBit1Space = false
        for (i in 2 until timings.size - 1 step 2) {
            assertEquals(560, timings[i])
            if (timings[i + 1] == 1690) sawBit1Space = true
        }
        assertTrue("expected at least one bit-1 (1690us) space", sawBit1Space)
    }

    @Test
    fun `SIRC 12-bit has 2400 header and 26 entries`() {
        val timings = IrBruteForce.encodeSIRC(0x01, 0x15)
        assertEquals(2400, timings[0])
        assertEquals(600, timings[1])
        // 2 header + 12 bits × 2 entries each = 26
        assertEquals(2 + 24, timings.size)
    }

    @Test
    fun `SIRC uses 600us spaces and variable-width marks`() {
        val timings = IrBruteForce.encodeSIRC(0x01, 0x15)
        // Pairs after header: mark (600 or 1200) + space (600).
        for (i in 2 until timings.size step 2) {
            val mark = timings[i]
            val space = timings[i + 1]
            assertTrue("mark $mark should be 600 or 1200", mark == 600 || mark == 1200)
            assertEquals(600, space)
        }
    }

    @Test
    fun `Different NEC inputs produce different timings`() {
        val a = IrBruteForce.encodeNEC(0x07, 0x02)
        val b = IrBruteForce.encodeNEC(0x07, 0x03)
        assertTrue(!a.contentEquals(b))
    }
}
