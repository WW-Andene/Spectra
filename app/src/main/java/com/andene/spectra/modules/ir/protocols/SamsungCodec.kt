package com.andene.spectra.modules.ir.protocols

/**
 * Samsung TV protocol codec (sometimes labelled Samsung36 in
 * third-party databases — Spectra uses the consumer-TV variant
 * specifically).
 *
 * Wire format: same NEC-family pulse-distance encoding (562us mark +
 * 562/1687us space) but with a shorter header — 4500us mark + 4500us
 * space instead of NEC's 9000+4500. Data is 32 bits with the same
 * address+~address+command+~command layout, both bytes inverted as
 * a hardware-level checksum.
 *
 * No repeat-frame variant — Samsung TVs accept retransmits of the
 * full frame for press-and-hold instead of NEC's compact repeat
 * stub. We therefore don't pass a RepeatShape into [PulseDistance].
 */
object SamsungCodec {

    private val HEADER = PulseDistance.Header(mark = 4500, space = 4500)

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
