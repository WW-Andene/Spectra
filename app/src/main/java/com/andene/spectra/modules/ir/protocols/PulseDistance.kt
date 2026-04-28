package com.andene.spectra.modules.ir.protocols

import kotlin.math.abs

/**
 * Shared decode + encode for IR protocols that use **pulse-distance**
 * encoding — fixed-width mark, variable-width space discriminating 0
 * vs 1. NEC1, Samsung, and LG all share this wire shape; only the
 * header differs.
 *
 * The 32-bit data layout is also identical across all three: address +
 * ~address + command + ~command, each byte LSB-first. The inverted
 * bytes act as a hardware-level checksum that we validate on decode
 * and recompute on encode.
 *
 * The packed Long [Decoded.packed] is `(address shl 8) or command`,
 * 8-bit each. Inverted bytes are derivable so we don't store them.
 */
internal object PulseDistance {

    /** Per-protocol header timing (microseconds). */
    data class Header(
        val mark: Int,
        val space: Int,
        val markTol: Int = 1500,
        val spaceTol: Int = 800,
    )

    /** Per-protocol bit timing. NEC family uses 562/562/1687 across all
     *  three named protocols; kept as a parameter so future codecs
     *  with different bit widths can reuse the same impl. */
    data class BitTiming(
        val mark: Int = 562,
        val zeroSpace: Int = 562,
        val oneSpace: Int = 1687,
        val markTol: Int = 250,
    ) {
        val zeroOneBoundary: Int get() = (zeroSpace + oneSpace) / 2
    }

    /** Optional repeat-frame shape (e.g. NEC's 9000+2250+562). */
    data class RepeatShape(val space: Int, val spaceTol: Int = 500)

    data class Decoded(
        val address: Int,
        val command: Int,
        val isRepeat: Boolean,
    ) {
        fun packed(): Long = ((address and 0xFF) shl 8 or (command and 0xFF)).toLong()
    }

    /**
     * Decode an alternating mark/space timings array into address +
     * command. Returns null if the header doesn't match, the bit count
     * is wrong, or the inverted-byte checksum fails.
     *
     * The first array element must be a mark.
     */
    fun decode(
        timings: IntArray,
        header: Header,
        bit: BitTiming = BitTiming(),
        repeat: RepeatShape? = null,
    ): Decoded? {
        if (timings.size < 4) return null
        if (!within(timings[0], header.mark, header.markTol)) return null

        // Distinguish data frame from repeat (if the protocol has one).
        if (repeat != null && within(timings[1], repeat.space, repeat.spaceTol)) {
            return if (timings.size >= 3 && within(timings[2], bit.mark, bit.markTol)) {
                Decoded(address = 0, command = 0, isRepeat = true)
            } else null
        }
        if (!within(timings[1], header.space, header.spaceTol)) return null

        // 32 bits = 64 entries, plus header (2) + closing mark (1) = 67.
        if (timings.size < 2 + 64 + 1) return null

        var packed = 0
        for (b in 0 until 32) {
            val markIdx = 2 + b * 2
            val spaceIdx = markIdx + 1
            if (!within(timings[markIdx], bit.mark, bit.markTol)) return null
            // Discriminate by midpoint instead of two windows so we don't
            // reject borderline-jittery cases that are clearly closer to
            // one nominal than the other.
            if (timings[spaceIdx] > bit.zeroOneBoundary) packed = packed or (1 shl b)
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
     * Build an alternating mark/space timings array from address +
     * command. Output length: 2 (header) + 64 (32 bits) + 1 (closing
     * mark) = 67 entries.
     */
    fun encode(
        address: Int,
        command: Int,
        header: Header,
        bit: BitTiming = BitTiming(),
    ): IntArray {
        val out = IntArray(2 + 32 * 2 + 1)
        out[0] = header.mark
        out[1] = header.space
        var i = 2
        for (b in intArrayOf(
            address and 0xFF,
            address.inv() and 0xFF,
            command and 0xFF,
            command.inv() and 0xFF,
        )) {
            for (k in 0 until 8) {
                out[i++] = bit.mark
                out[i++] = if ((b ushr k) and 1 == 1) bit.oneSpace else bit.zeroSpace
            }
        }
        out[i] = bit.mark  // closing mark
        return out
    }

    private fun within(value: Int, target: Int, tolerance: Int): Boolean =
        abs(value - target) <= tolerance
}
