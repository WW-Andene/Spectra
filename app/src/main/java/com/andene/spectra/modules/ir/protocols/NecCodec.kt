package com.andene.spectra.modules.ir.protocols

/**
 * NEC1 protocol codec.
 *
 * Wire format (microseconds, alternating mark/space):
 *   header     :  9000  +  4500
 *   data       :  32 bits (address, ~address, command, ~command)
 *                each bit = 562us mark + 562 (0) / 1687 (1) us space
 *   end        :   562 (closing mark)
 *
 * Repeat frames (button held): 9000 + 2250 + 562.
 *
 * Implementation delegates to [PulseDistance] so the wire-shape logic
 * stays in one place across the NEC-family codecs.
 */
object NecCodec {

    private val HEADER = PulseDistance.Header(mark = 9000, space = 4500)
    private val REPEAT = PulseDistance.RepeatShape(space = 2250)

    /** Public re-export so external callers don't need to know
     *  about the internal PulseDistance type. */
    typealias Decoded = PulseDistance.Decoded

    fun decode(timings: IntArray): Decoded? =
        PulseDistance.decode(timings, HEADER, repeat = REPEAT)

    fun encode(address: Int, command: Int): IntArray =
        PulseDistance.encode(address, command, HEADER)

    fun encodeFromPacked(code: Long): IntArray {
        val address = ((code ushr 8) and 0xFF).toInt()
        val command = (code and 0xFF).toInt()
        return encode(address, command)
    }

    /**
     * Build the NEC repeat frame: a compact 11 ms burst signalling
     * "the button is still held" without re-transmitting the full
     * 67 ms data frame. Spec layout:
     *   9000 us mark + 2250 us space + 562 us closing mark
     *
     * Real NEC remotes send the full data frame once, then a repeat
     * frame every 110 ms while the user holds the button. IrControl's
     * press-and-hold path uses this to smooth out volume / channel /
     * cursor presses.
     */
    fun encodeRepeat(): IntArray = intArrayOf(9000, 2250, 562)
}
