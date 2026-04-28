package com.andene.spectra.modules.ir.protocols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for RC6 mode 0. Verifies the Manchester encoder +
 * level-walking decoder are inverses across the address/command space.
 */
class Rc6CodecTest {

    @Test
    fun `round-trip preserves address and command across byte combos`() {
        val samples = listOf(
            0x00 to 0x00,
            0x12 to 0x34,
            0x40 to 0x91,
            0xAA to 0x55,
            0xFF to 0xFF,
            0x80 to 0x01,
        )
        for ((address, command) in samples) {
            val timings = Rc6Codec.encode(address, command)
            val decoded = Rc6Codec.decode(timings)
            assertNotNull("RC6 decode failed for 0x%02X / 0x%02X".format(address, command), decoded)
            assertEquals(address, decoded!!.address)
            assertEquals(command, decoded.command)
        }
    }

    @Test
    fun `decode rejects bad leader mark`() {
        val good = Rc6Codec.encode(0x12, 0x34)
        val bad = good.copyOf().also { it[0] = 5000 }  // far off 2666
        assertNull(Rc6Codec.decode(bad))
    }

    @Test
    fun `encodeFromPacked round-trips`() {
        for (a in listOf(0x10, 0x55, 0xAB)) {
            for (c in listOf(0x01, 0x77, 0xCC)) {
                val packed = ((a shl 8) or c).toLong()
                val timings = Rc6Codec.encodeFromPacked(packed)
                val decoded = Rc6Codec.decode(timings)
                assertNotNull(decoded)
                assertEquals(a, decoded!!.address)
                assertEquals(c, decoded.command)
            }
        }
    }
}
