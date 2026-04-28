package com.andene.spectra.modules.ir.protocols

import kotlin.math.abs

/**
 * RC6 mode 0 codec.
 *
 * Wire format (microseconds):
 *   leader      : 2666 mark + 889 space    (one-shot, "AGC burst")
 *   start bit   : '1' (half-bit space + half-bit mark)
 *   mode bits   : 3 bits, MSB-first; mode 0 = '000'
 *   trailer     : 1 toggle bit at DOUBLE width (888 us per half)
 *   address     : 8 bits MSB-first
 *   command     : 8 bits MSB-first
 *   gap         : 96 ms minimum before any retransmit
 *
 * Manchester convention used by RC6 (IEC 60798 / Philips spec):
 *   '0' = first half mark, second half space (HIGH → LOW transition mid-bit)
 *   '1' = first half space, second half mark (LOW → HIGH transition mid-bit)
 *
 * Bit period t = 444 us; trailer is 2t (888 us per half).
 *
 * Carrier: 36 kHz.
 *
 * The Manchester wire shape doesn't fit [PulseDistance] — encoded
 * frames have variable timings-array length depending on how often
 * adjacent bit halves at the same level collapse into a single
 * mark / space entry. The decoder walks the array with a microsecond
 * cursor, reading two half-periods per bit and inferring value from
 * the transition direction.
 *
 * Packed code convention: (address shl 8) or command, lower 16 bits
 * of an Int. Same as NEC's packed shape so cross-codec storage stays
 * consistent.
 */
object Rc6Codec {

    private const val LEADER_MARK = 2666
    private const val LEADER_SPACE = 889
    private const val LEADER_MARK_TOL = 600
    private const val LEADER_SPACE_TOL = 300
    private const val BIT_PERIOD = 444
    private const val HALF_PERIOD = 222

    data class Decoded(
        val address: Int,
        val command: Int,
        /** RC6 toggle bit — flips on every key press. Two consecutive
         *  "press POWER" commands send the SAME address+command but
         *  with toggle alternated, so receivers can distinguish a held
         *  button from two presses. We surface it for completeness
         *  but encoded transmits always set toggle=0 (most receivers
         *  don't care for one-off transmits). */
        val toggle: Boolean,
    ) {
        fun packed(): Long = ((address and 0xFF) shl 8 or (command and 0xFF)).toLong()
    }

    fun decode(timings: IntArray): Decoded? {
        if (timings.size < 4) return null
        if (!within(timings[0], LEADER_MARK, LEADER_MARK_TOL)) return null
        if (!within(timings[1], LEADER_SPACE, LEADER_SPACE_TOL)) return null

        val cursor = Cursor(timings, startIdx = 2)

        // Start bit must be '1'.
        val start = readBit(cursor, doubleWidth = false) ?: return null
        if (start != 1) return null

        // 3 mode bits MSB-first; only mode 0 supported.
        var mode = 0
        repeat(3) {
            val b = readBit(cursor, doubleWidth = false) ?: return null
            mode = (mode shl 1) or b
        }
        if (mode != 0) return null

        // Trailer / toggle bit at double width.
        val toggle = readBit(cursor, doubleWidth = true) ?: return null

        // 8 bits address MSB-first.
        var address = 0
        repeat(8) {
            val b = readBit(cursor, doubleWidth = false) ?: return null
            address = (address shl 1) or b
        }

        // 8 bits command MSB-first.
        var command = 0
        repeat(8) {
            val b = readBit(cursor, doubleWidth = false) ?: return null
            command = (command shl 1) or b
        }

        return Decoded(address = address, command = command, toggle = toggle == 1)
    }

    fun encode(address: Int, command: Int): IntArray {
        // Build the wire as a sequence of (level, duration-us) half-bit
        // entries, then collapse adjacent same-level entries into the
        // mark/space alternating timings array.
        val halves = mutableListOf<Pair<Boolean, Int>>()  // (isMark, durUs)

        // Leader: 2666 mark + 889 space
        halves.add(true to LEADER_MARK)
        halves.add(false to LEADER_SPACE)

        // Start bit '1' — space then mark (each HALF_PERIOD).
        halves.add(false to HALF_PERIOD)
        halves.add(true to HALF_PERIOD)

        // 3 mode bits (mode 0 = 000) — each '0' is mark then space.
        repeat(3) {
            halves.add(true to HALF_PERIOD)
            halves.add(false to HALF_PERIOD)
        }

        // Trailer / toggle = '0' at double width — mark then space, each
        // 2 × HALF_PERIOD = 444 us.
        halves.add(true to BIT_PERIOD)
        halves.add(false to BIT_PERIOD)

        // 8 bits address MSB-first.
        for (k in 7 downTo 0) {
            val bit = (address ushr k) and 1
            if (bit == 1) {
                halves.add(false to HALF_PERIOD)
                halves.add(true to HALF_PERIOD)
            } else {
                halves.add(true to HALF_PERIOD)
                halves.add(false to HALF_PERIOD)
            }
        }

        // 8 bits command MSB-first.
        for (k in 7 downTo 0) {
            val bit = (command ushr k) and 1
            if (bit == 1) {
                halves.add(false to HALF_PERIOD)
                halves.add(true to HALF_PERIOD)
            } else {
                halves.add(true to HALF_PERIOD)
                halves.add(false to HALF_PERIOD)
            }
        }

        // Collapse adjacent same-level halves into single entries.
        val collapsed = mutableListOf<Pair<Boolean, Int>>()
        for (entry in halves) {
            val last = collapsed.lastOrNull()
            if (last != null && last.first == entry.first) {
                collapsed[collapsed.size - 1] = last.first to (last.second + entry.second)
            } else {
                collapsed.add(entry)
            }
        }

        // The alternating mark/space array always starts with mark.
        // Insert a zero-width prefix space if the first entry would
        // be a space (shouldn't happen for valid RC6, but defensive).
        if (collapsed.isNotEmpty() && !collapsed[0].first) {
            collapsed.add(0, true to 0)
        }
        return collapsed.map { it.second }.toIntArray()
    }

    fun encodeFromPacked(code: Long): IntArray {
        val address = ((code ushr 8) and 0xFF).toInt()
        val command = (code and 0xFF).toInt()
        return encode(address, command)
    }

    /** Reads one bit from the cursor: consumes one bit-period worth
     *  (or 2× for trailer), returns 0 / 1 from the mid-bit transition
     *  direction or null if no transition (invalid frame).  */
    private fun readBit(cursor: Cursor, doubleWidth: Boolean): Int? {
        val halfDur = if (doubleWidth) BIT_PERIOD else HALF_PERIOD
        val firstLevel = cursor.peek() ?: return null
        cursor.advance(halfDur)
        val secondLevel = cursor.peek() ?: return null
        cursor.advance(halfDur)
        return when {
            firstLevel && !secondLevel -> 0   // mark → space = '0'
            !firstLevel && secondLevel -> 1   // space → mark = '1'
            else -> null
        }
    }

    /**
     * Walking cursor over an alternating mark/space [timings] array.
     * advance(us) consumes that many microseconds; peek() returns the
     * level at the current position (true = mark / IR-on).
     */
    private class Cursor(val timings: IntArray, startIdx: Int) {
        private var idx = startIdx
        private var remaining = if (idx < timings.size) timings[idx] else 0

        /** Level at the current cursor position, or null if past end. */
        fun peek(): Boolean? {
            if (idx >= timings.size) return null
            return idx % 2 == 0  // even = mark, odd = space
        }

        /** Consume [microsToConsume] microseconds from the array. */
        fun advance(microsToConsume: Int) {
            var togo = microsToConsume
            while (togo > 0 && idx < timings.size) {
                if (remaining > togo) {
                    remaining -= togo
                    return
                }
                togo -= remaining
                idx++
                remaining = if (idx < timings.size) timings[idx] else 0
            }
        }
    }

    private fun within(value: Int, target: Int, tolerance: Int): Boolean =
        abs(value - target) <= tolerance
}
