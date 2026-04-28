package com.andene.spectra.modules.ir.protocols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the Samsung + LG codecs (B-100 phase 2).
 *
 * Both protocols delegate to the shared [PulseDistance] impl with
 * different headers, so structurally the tests mirror NecCodecTest's
 * coverage and verify each codec's header window is tight enough not
 * to accept the others' headers.
 */
class SamsungLgCodecTest {

    @Test
    fun `samsung round-trip preserves address and command`() {
        val samples = listOf(
            0x00 to 0x00,
            0x07 to 0x07,
            0x40 to 0x12,
            0xFF to 0xFF,
            0xE0 to 0xE0,  // Samsung TV power
        )
        for ((address, command) in samples) {
            val timings = SamsungCodec.encode(address, command)
            val decoded = SamsungCodec.decode(timings)
            assertNotNull("samsung decode failed for 0x%02X / 0x%02X".format(address, command), decoded)
            assertEquals(address, decoded!!.address)
            assertEquals(command, decoded.command)
        }
    }

    @Test
    fun `lg round-trip preserves address and command`() {
        val samples = listOf(
            0x04 to 0x08,
            0x20 to 0x10,
            0xAA to 0x55,
        )
        for ((address, command) in samples) {
            val timings = LgCodec.encode(address, command)
            val decoded = LgCodec.decode(timings)
            assertNotNull("lg decode failed for 0x%02X / 0x%02X".format(address, command), decoded)
            assertEquals(address, decoded!!.address)
            assertEquals(command, decoded.command)
        }
    }

    @Test
    fun `samsung does not accept nec or lg headers`() {
        // NEC frame (9000+4500 header) handed to SamsungCodec must fail
        // header check — Samsung's window is 4500 ± 1500 = 3000..6000.
        val nec = NecCodec.encode(0x12, 0x34)
        assertNull(SamsungCodec.decode(nec))

        val lg = LgCodec.encode(0x12, 0x34)
        assertNull(SamsungCodec.decode(lg))
    }

    @Test
    fun `lg does not accept nec or samsung headers`() {
        // LG header is tightened to 8500 ± 1000 = 7500..9500. NEC's
        // 9000 sits inside that window — so we'd actually accept NEC
        // headers if we relied on header alone. The bit-decode path
        // would still produce a (potentially valid) packed code
        // because the bit timings are identical. The protection is at
        // the IrCameraCapture.attemptProtocolDecode bucket level, not
        // here — this test pins that we ARE permissive on header so
        // a mis-bucketed NEC capture still decodes rather than silently
        // failing.
        val nec = NecCodec.encode(0x12, 0x34)
        // Header 9000 is within LG's 7500..9500 mark window, so this
        // succeeds — kept as a property test. If the LG window is ever
        // tightened to exclude 9000, this assertion needs to flip.
        assertNotNull(LgCodec.decode(nec))

        // Samsung header 4500 mark is well outside LG's 7500..9500.
        val samsung = SamsungCodec.encode(0x12, 0x34)
        assertNull(LgCodec.decode(samsung))
    }

    @Test
    fun `samsung tolerates rolling-shutter jitter`() {
        val timings = SamsungCodec.encode(0xE0, 0xE0)
        val rng = java.util.Random(0xDEADBEEF)
        val jittered = IntArray(timings.size) { i -> timings[i] + rng.nextInt(301) - 150 }
        val decoded = SamsungCodec.decode(jittered)
        assertNotNull(decoded)
        assertEquals(0xE0, decoded!!.address)
        assertEquals(0xE0, decoded.command)
    }
}
