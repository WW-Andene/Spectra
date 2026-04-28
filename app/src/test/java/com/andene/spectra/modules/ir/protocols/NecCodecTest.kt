package com.andene.spectra.modules.ir.protocols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the NEC1 codec. Decoder + encoder should be
 * inverses on any well-formed (address, command) pair, and the decoder
 * should reject obvious garbage rather than returning a bogus packed
 * code that wouldn't match anything in the wild.
 */
class NecCodecTest {

    @Test
    fun `encode decode round-trip reproduces address and command`() {
        // Sample of address/command pairs covering low + high bytes,
        // boundary values, and the kind of common remote codes that
        // tend to appear in real captures (Samsung TV power = 0xE0E0
        // → address 0x07, command 0x07; using arbitrary 8-bit bytes).
        val samples = listOf(
            0x00 to 0x00,
            0x07 to 0x07,
            0x40 to 0x12,
            0xFF to 0xFF,
            0x55 to 0xAA,
            0xAA to 0x55,
            0x80 to 0x01,
        )
        for ((address, command) in samples) {
            val timings = NecCodec.encode(address, command)
            val decoded = NecCodec.decode(timings)
            assertNotNull("decode failed for 0x%02X / 0x%02X".format(address, command), decoded)
            assertEquals(address, decoded!!.address)
            assertEquals(command, decoded.command)
            assertTrue(!decoded.isRepeat)
            // Packed Long matches the convention bit-by-bit.
            assertEquals(((address shl 8) or command).toLong(), decoded.packed())
        }
    }

    @Test
    fun `encoded frame has the canonical structure`() {
        val timings = NecCodec.encode(0x12, 0x34)
        // 2 (header) + 64 (32 bits × mark/space) + 1 (closing mark) = 67.
        assertEquals(67, timings.size)
        // Header mark + space.
        assertEquals(9000, timings[0])
        assertEquals(4500, timings[1])
        // Closing mark.
        assertEquals(562, timings.last())
        // Every mark slot is the same 562us.
        for (bit in 0 until 32) {
            assertEquals(562, timings[2 + bit * 2])
        }
    }

    @Test
    fun `decode rejects bad header mark`() {
        val good = NecCodec.encode(0x12, 0x34)
        val bad = good.copyOf().also { it[0] = 5000 }  // way off 9000
        assertNull(NecCodec.decode(bad))
    }

    @Test
    fun `decode rejects flipped checksum byte`() {
        // Build a frame that has the right shape but a corrupted
        // ~address byte — should fail the inverted-byte check.
        val good = NecCodec.encode(0x12, 0x34)
        val bad = good.copyOf()
        // Flip the very first bit of the ~address byte. NEC sends
        // bytes LSB-first starting at offset 2 (after header). The
        // ~address byte is bits 8..15, so its first sample slot is at
        // index 2 + 8 * 2 = 18 (mark) / 19 (space). Flipping that
        // bit's space from 562us to 1687us breaks the inversion check.
        bad[19] = if (bad[19] == 562) 1687 else 562
        assertNull(NecCodec.decode(bad))
    }

    @Test
    fun `decode recognises a repeat frame`() {
        // Repeat frame: 9000 + 2250 + 562 (closing mark only).
        val repeat = intArrayOf(9000, 2250, 562)
        val decoded = NecCodec.decode(repeat)
        assertNotNull(decoded)
        assertTrue(decoded!!.isRepeat)
    }

    @Test
    fun `decode tolerates rolling-shutter jitter in bit widths`() {
        // Take an encoded frame and add ±150us jitter to each entry
        // (within the codec's published tolerances). The decode should
        // still succeed and return the original code. Mirrors the
        // typical phone-camera rolling-shutter scatter — without this
        // tolerance the captured commands would only re-decode on the
        // exact phone model that captured them.
        val good = NecCodec.encode(0x40, 0x91)
        val rng = java.util.Random(0xCAFEBABE)
        val jittered = IntArray(good.size) { i -> good[i] + rng.nextInt(301) - 150 }
        val decoded = NecCodec.decode(jittered)
        assertNotNull(decoded)
        assertEquals(0x40, decoded!!.address)
        assertEquals(0x91, decoded.command)
    }

    @Test
    fun `encodeRepeat produces the canonical 9000+2250+562 frame`() {
        val frame = NecCodec.encodeRepeat()
        assertEquals(3, frame.size)
        assertEquals(9000, frame[0])
        assertEquals(2250, frame[1])
        assertEquals(562, frame[2])
        // And the decoder should recognise it as a repeat.
        val decoded = NecCodec.decode(frame)
        assertNotNull(decoded)
        assertTrue(decoded!!.isRepeat)
    }

    @Test
    fun `encodeFromPacked is the inverse of decode packed`() {
        for (address in 0..255 step 17) {
            for (command in 0..255 step 23) {
                val packed = ((address shl 8) or command).toLong()
                val timings = NecCodec.encodeFromPacked(packed)
                val decoded = NecCodec.decode(timings)
                assertNotNull(decoded)
                assertEquals(packed, decoded!!.packed())
            }
        }
    }
}
