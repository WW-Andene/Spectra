package com.andene.spectra.modules.ir.protocols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for Sony SIRC across the three supported variants
 * (12 / 15 / 20 bit). SIRC has no checksum, so the test relies on
 * the encoder + decoder being inverses to verify correctness.
 */
class SonyCodecTest {

    @Test
    fun `sirc-12 round-trip preserves address and command`() {
        // SIRC-12: 7 cmd bits + 5 addr bits.
        val samples = listOf(
            0 to 0,
            5 to 1,        // power on most Sony TVs
            0x10 to 0x21,
            0x1F to 0x7F,  // max values
        )
        for ((address, command) in samples) {
            val timings = SonyCodec.encode(SonyCodec.Variant.SIRC_12, address, command)
            assertEquals(2 + 12 * 2, timings.size)
            val decoded = SonyCodec.decode(timings)
            assertNotNull(decoded)
            assertEquals(SonyCodec.Variant.SIRC_12, decoded!!.variant)
            assertEquals(address, decoded.address)
            assertEquals(command, decoded.command)
        }
    }

    @Test
    fun `sirc-15 round-trip preserves address and command`() {
        // SIRC-15: 7 cmd bits + 8 addr bits.
        val timings = SonyCodec.encode(SonyCodec.Variant.SIRC_15, 0xA5, 0x12)
        assertEquals(2 + 15 * 2, timings.size)
        val decoded = SonyCodec.decode(timings)
        assertNotNull(decoded)
        assertEquals(SonyCodec.Variant.SIRC_15, decoded!!.variant)
        assertEquals(0xA5, decoded.address)
        assertEquals(0x12, decoded.command)
    }

    @Test
    fun `sirc-20 round-trip preserves address and command`() {
        // SIRC-20: 7 cmd bits + 13 addr bits (covers extended Sony +
        // Sony-style audio/CD remote codes).
        val timings = SonyCodec.encode(SonyCodec.Variant.SIRC_20, 0x1234, 0x42)
        assertEquals(2 + 20 * 2, timings.size)
        val decoded = SonyCodec.decode(timings)
        assertNotNull(decoded)
        assertEquals(SonyCodec.Variant.SIRC_20, decoded!!.variant)
        assertEquals(0x1234, decoded.address)
        assertEquals(0x42, decoded.command)
    }

    @Test
    fun `decode picks longest variant the array supports`() {
        // A 26-entry frame (2 leader + 24 bit pairs) is unambiguously
        // SIRC-12. A 32-entry frame is SIRC-15. A 42-entry frame is
        // SIRC-20. The decoder prefers the longest fit so a 20-bit
        // remote isn't truncated to its first 12 bits.
        val sirc20 = SonyCodec.encode(SonyCodec.Variant.SIRC_20, 0x100, 0x05)
        val decoded = SonyCodec.decode(sirc20)
        assertEquals(SonyCodec.Variant.SIRC_20, decoded!!.variant)
    }

    @Test
    fun `decode rejects bad leader mark`() {
        val good = SonyCodec.encode(SonyCodec.Variant.SIRC_12, 1, 5)
        val bad = good.copyOf().also { it[0] = 5000 }  // way off 2400
        assertNull(SonyCodec.decode(bad))
    }

    @Test
    fun `encodeFromPacked is the inverse of decode packed (SIRC-12)`() {
        for (address in 0..0x1F step 3) {  // 5-bit address
            for (command in 0..0x7F step 7) {  // 7-bit command
                val packed = ((address.toLong() shl 7) or command.toLong())
                val timings = SonyCodec.encodeFromPacked(packed, SonyCodec.Variant.SIRC_12)
                val decoded = SonyCodec.decode(timings)
                assertNotNull(decoded)
                assertEquals(SonyCodec.Variant.SIRC_12, decoded!!.variant)
                assertEquals(packed, decoded.packed())
            }
        }
    }
}
