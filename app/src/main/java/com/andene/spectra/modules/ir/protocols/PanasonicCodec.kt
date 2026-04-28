package com.andene.spectra.modules.ir.protocols

/**
 * Panasonic / Kaseikyo codec.
 *
 * Wire format (microseconds, alternating mark/space):
 *   header     :  3500 +  1750
 *   data       :  48 bits LSB-first via PulseDistance, with bit timing
 *                 432 us mark + 432 / 1296 us space.
 *   end        :  432 (closing mark)
 *
 * Layout of the 48 bits:
 *   bits  0..15 : OEM / vendor code (Panasonic = 0x4004, others vary —
 *                 Sharp, Mitsubishi, Denon, Sanyo, JVC also ride this
 *                 wire shape with different vendor codes; collectively
 *                 known as "Kaseikyo")
 *   bits 16..23 : OEM device id
 *   bits 24..31 : OEM sub-device
 *   bits 32..39 : function / command
 *   bits 40..47 : XOR of bytes 16..39 (4-byte parity)
 *
 * We don't validate the XOR parity on decode — header + bit timings
 * are distinctive enough that false positives are vanishing. The
 * vendor code IS extracted and surfaced through Decoded.vendor for
 * downstream use (categorisation hints, brand inference).
 *
 * 36.7 kHz carrier. Capture-time falls into the IrProtocol.PANASONIC
 * bucket from attemptProtocolDecode's header check.
 */
object PanasonicCodec {

    private val HEADER = PulseDistance.Header(mark = 3500, space = 1750)

    // Panasonic uses tighter bit timings than NEC family.
    private val BIT = PulseDistance.BitTiming(
        mark = 432,
        zeroSpace = 432,
        oneSpace = 1296,
        markTol = 200,
    )

    data class Decoded(
        /** Full 48-bit packed value (LSB-first across the wire). */
        val packed: Long,
        /** Vendor / OEM code from bits 0..15. Panasonic-branded
         *  remotes use 0x4004; Sharp 0x5AAA; Denon 0x3254 etc. */
        val vendor: Int,
        /** Function / command byte (bits 32..39) — the actual button. */
        val command: Int,
    ) {
        /** Compatible packing for IrCommand.code: lower 32 bits of
         *  the 48-bit packed payload — vendor code is preserved. */
        fun packed32(): Long = packed and 0xFFFFFFFFL
    }

    fun decode(timings: IntArray): Decoded? {
        val raw = PulseDistance.decodeRaw(timings, HEADER, BIT, totalBits = 48) ?: return null
        if (raw.isRepeat) return null
        val packed = raw.packed
        return Decoded(
            packed = packed,
            vendor = (packed and 0xFFFF).toInt(),
            command = ((packed ushr 32) and 0xFF).toInt(),
        )
    }

    /**
     * Encode from a 48-bit packed value. Used when transmitting from a
     * stored IrCommand.code. The full 48 bits are required because
     * the vendor code matters for matching at the receiver — a
     * Panasonic TV won't react to a Sharp-vendor frame even if the
     * function byte is identical.
     */
    fun encode(packed48: Long): IntArray =
        PulseDistance.encodeRaw(packed48, totalBits = 48, header = HEADER, bit = BIT)
}
