package com.andene.spectra.modules.ir.protocols

import kotlin.math.abs

/**
 * NEC1 protocol decoder + encoder.
 *
 * Wire format (microseconds, alternating mark/space):
 *
 *   header     :  9000  +  4500
 *   data       :  32 bits, each bit is a 562us mark followed by:
 *                   562us space  → 0
 *                  1687us space  → 1
 *                Bytes go in this order, each LSB-first:
 *                  address, ~address, command, ~command
 *   end        :   562 (closing mark)
 *
 * Repeat frames (sent while a button is held) are short:
 *   header     :  9000  +  2250  +  562 (closing mark)
 *
 * The two inverted bytes act as a hardware-level checksum: a valid
 * frame requires `~address == address xor 0xFF` and the same for
 * command. We use that during decode to reject garbage timings —
 * better to surface "couldn't decode, falling back to raw replay"
 * than to silently store a bogus 16-bit code that won't match
 * anything when shared across phones.
 *
 * The packed Long [code] returned is `(address shl 8) or command`,
 * both 8-bit. Inverted bytes are recomputed during [encode] so we
 * don't waste storage on derivable data. NEC2 (16-bit address, no
 * inversion) would be a separate codec; this is NEC1 only.
 */
object NecCodec {

    // Timing nominals (microseconds).
    private const val HEADER_MARK = 9000
    private const val HEADER_SPACE = 4500
    private const val REPEAT_SPACE = 2250
    private const val BIT_MARK = 562
    private const val ZERO_SPACE = 562
    private const val ONE_SPACE = 1687

    // Tolerances. Rolling-shutter camera capture has more jitter than a
    // hardware IR receiver — these are intentionally loose. The bit-mark
    // check uses a fixed-width window because every mark is the same
    // 562us; the space check needs more room because we're discriminating
    // 562 vs 1687 with a midpoint near 1100us.
    private const val HEADER_MARK_TOL = 1500
    private const val HEADER_SPACE_TOL = 800
    private const val REPEAT_SPACE_TOL = 500
    private const val BIT_MARK_TOL = 250
    private const val ZERO_ONE_BOUNDARY = (ZERO_SPACE + ONE_SPACE) / 2

    data class Decoded(
        /** 8-bit address. */
        val address: Int,
        /** 8-bit command. */
        val command: Int,
        /** True for a repeat frame (no address/command, button still held). */
        val isRepeat: Boolean,
    ) {
        /** Pack into a single Long for IrCommand.code. */
        fun packed(): Long = ((address and 0xFF) shl 8 or (command and 0xFF)).toLong()
    }

    /**
     * Try to decode an alternating mark/space timings array as NEC1.
     * Returns null when the header doesn't match, when the bit count
     * is wrong, or when the inverted-byte checksum fails.
     *
     * The first array element must be a mark.
     */
    fun decode(timings: IntArray): Decoded? {
        if (timings.size < 4) return null
        if (!within(timings[0], HEADER_MARK, HEADER_MARK_TOL)) return null

        // Distinguish data frame (4500us space) from repeat (2250us).
        if (within(timings[1], REPEAT_SPACE, REPEAT_SPACE_TOL)) {
            // Repeat frame: header + closing mark, total ~3 entries.
            if (timings.size >= 3 && within(timings[2], BIT_MARK, BIT_MARK_TOL)) {
                return Decoded(address = 0, command = 0, isRepeat = true)
            }
            return null
        }
        if (!within(timings[1], HEADER_SPACE, HEADER_SPACE_TOL)) return null

        // 32 bits = 64 entries, plus header (2) + closing mark (1) = 67.
        // Allow trailing garbage (rolling-shutter capture often adds a
        // few extra timings) but require at least 65 entries to read
        // 32 bits' worth of mark/space pairs.
        if (timings.size < 2 + 64 + 1) return null

        var packed = 0
        for (bit in 0 until 32) {
            val markIdx = 2 + bit * 2
            val spaceIdx = markIdx + 1
            if (!within(timings[markIdx], BIT_MARK, BIT_MARK_TOL)) return null
            val space = timings[spaceIdx]
            // Discriminate by midpoint instead of two windows so we don't
            // reject borderline-jittery cases that are clearly closer to
            // one nominal than the other.
            val isOne = space > ZERO_ONE_BOUNDARY
            // NEC sends LSB-first per byte. Build packed as 32-bit:
            //   bits  0..7  = address
            //   bits  8..15 = ~address
            //   bits 16..23 = command
            //   bits 24..31 = ~command
            if (isOne) packed = packed or (1 shl bit)
        }

        val address = packed and 0xFF
        val addressInv = (packed ushr 8) and 0xFF
        val command = (packed ushr 16) and 0xFF
        val commandInv = (packed ushr 24) and 0xFF
        if ((address xor addressInv) != 0xFF) return null
        if ((command xor commandInv) != 0xFF) return null

        return Decoded(address = address, command = command, isRepeat = false)
    }

    /**
     * Build a NEC1 timings array from address + command. Output is the
     * 67-entry mark/space sequence ready to hand to
     * ConsumerIrManager.transmit at 38 kHz.
     */
    fun encode(address: Int, command: Int): IntArray {
        val out = IntArray(2 + 32 * 2 + 1)
        out[0] = HEADER_MARK
        out[1] = HEADER_SPACE
        var i = 2
        // Each byte LSB-first; order: address, ~address, command, ~command.
        for (byte in intArrayOf(
            address and 0xFF,
            address.inv() and 0xFF,
            command and 0xFF,
            command.inv() and 0xFF,
        )) {
            for (bit in 0 until 8) {
                out[i++] = BIT_MARK
                out[i++] = if ((byte ushr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE
            }
        }
        out[i] = BIT_MARK  // closing mark
        return out
    }

    /** Build a NEC1 timings array from a packed [Decoded.packed] code. */
    fun encodeFromPacked(code: Long): IntArray {
        val address = ((code ushr 8) and 0xFF).toInt()
        val command = (code and 0xFF).toInt()
        return encode(address, command)
    }

    private fun within(value: Int, target: Int, tolerance: Int): Boolean =
        abs(value - target) <= tolerance
}
