package com.andene.spectra.modules.ir.protocols

/**
 * LG TV protocol codec.
 *
 * Wire format: NEC-family pulse-distance (562us mark + 562/1687us
 * space), 32 data bits in address+~address+command+~command layout.
 * Header is 8500us mark + 4250us space — close to but not identical
 * with NEC's 9000+4500, so we tighten the header tolerance slightly
 * to avoid stealing genuine NEC frames. Capture-side detection in
 * IrCameraCapture checks NEC first anyway, so this codec only sees
 * timings that already failed the NEC bucket.
 *
 * 28-bit LG (older 1990s remotes) is NOT covered here — that variant
 * uses a different bit count and isn't present on modern LG TVs.
 */
object LgCodec {

    private val HEADER = PulseDistance.Header(
        mark = 8500,
        space = 4250,
        // Tighter than the default 1500/800 because LG's mark sits
        // closer to NEC than Samsung does. The capture detector
        // already filters by header bucket; this is belt-and-braces.
        markTol = 1000,
        spaceTol = 600,
    )

    typealias Decoded = PulseDistance.Decoded

    fun decode(timings: IntArray): Decoded? =
        PulseDistance.decode(timings, HEADER, repeat = null)

    fun encode(address: Int, command: Int): IntArray =
        PulseDistance.encode(address, command, HEADER)

    fun encodeFromPacked(code: Long): IntArray {
        val address = ((code ushr 8) and 0xFF).toInt()
        val command = (code and 0xFF).toInt()
        return encode(address, command)
    }
}
