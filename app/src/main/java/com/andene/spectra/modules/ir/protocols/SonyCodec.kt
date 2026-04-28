package com.andene.spectra.modules.ir.protocols

import kotlin.math.abs

/**
 * Sony SIRC (Serial Infrared Remote Control) codec.
 *
 * Wire format (microseconds, alternating mark/space):
 *   leader   :  2400  +   600
 *   data     :  N bits, each = 600us mark + 600 (0) / 1200 (1) us space
 *               LSB-first: 7 command bits, then address bits.
 *               N is 12 (most common), 15, or 20 depending on the variant.
 *   end      :  no closing mark — the final bit's space is the last entry.
 *
 * SIRC has no inverted-byte checksum the way NEC does. To detect garbage
 * we rely on (a) the leader timing matching tightly and (b) every bit
 * mark being a clean 600us. Spec-conformant remotes also retransmit
 * the same code three times with a 45ms gap, but we don't depend on
 * that — multi-press voting in IrCameraCapture handles consistency.
 *
 * Packed code convention: command in low [SIRC_12 cmd width] bits,
 * address shifted up. For SIRC-12: code = (address shl 7) or command.
 * Variants store their bit count on [Decoded] so the caller knows how
 * many bits to interpret on transmit.
 */
object SonyCodec {

    private const val LEADER_MARK = 2400
    private const val LEADER_SPACE = 600
    private const val BIT_MARK = 600
    private const val ZERO_SPACE = 600
    private const val ONE_SPACE = 1200

    private const val LEADER_MARK_TOL = 600
    private const val LEADER_SPACE_TOL = 250
    private const val BIT_MARK_TOL = 250
    private const val ZERO_ONE_BOUNDARY = (ZERO_SPACE + ONE_SPACE) / 2

    /** Variants supported. The bit count drives both decode bit loop
     *  length and the address/command split. */
    enum class Variant(val totalBits: Int, val commandBits: Int) {
        SIRC_12(12, 7),
        SIRC_15(15, 7),
        SIRC_20(20, 7),
    }

    data class Decoded(
        val variant: Variant,
        val address: Int,
        val command: Int,
    ) {
        /** Pack into a Long for IrCommand.code: command in low bits,
         *  address shifted up by [Variant.commandBits]. */
        fun packed(): Long =
            ((address shl variant.commandBits) or (command and ((1 shl variant.commandBits) - 1))).toLong()
    }

    /**
     * Decode a SIRC frame. Tries 12-bit first (most common), then 15,
     * then 20 — the timings array length determines which is plausible.
     * Returns null if the leader doesn't match or the array is too
     * short for any variant.
     */
    fun decode(timings: IntArray): Decoded? {
        if (timings.size < 4) return null
        if (!within(timings[0], LEADER_MARK, LEADER_MARK_TOL)) return null
        if (!within(timings[1], LEADER_SPACE, LEADER_SPACE_TOL)) return null

        // Try variants in order of bit count. A 20-bit frame's first
        // 12 bits look like a complete SIRC-12 to a naive decoder, so
        // we prefer the longest variant the array length supports —
        // remotes using SIRC-15 / -20 always emit those bits, never
        // a truncated 12-bit prefix.
        val tryOrder = listOf(Variant.SIRC_20, Variant.SIRC_15, Variant.SIRC_12)
        for (variant in tryOrder) {
            // Each bit takes 2 array entries (mark + space). Plus 2 for
            // leader. No closing mark.
            val needed = 2 + variant.totalBits * 2
            if (timings.size < needed) continue
            val packed = decodeBits(timings, variant.totalBits) ?: continue
            return splitPacked(packed, variant)
        }
        return null
    }

    /** Decode a fixed [totalBits] LSB-first starting at index 2 (after leader). */
    private fun decodeBits(timings: IntArray, totalBits: Int): Long? {
        var packed = 0L
        for (b in 0 until totalBits) {
            val markIdx = 2 + b * 2
            val spaceIdx = markIdx + 1
            if (!within(timings[markIdx], BIT_MARK, BIT_MARK_TOL)) return null
            if (timings[spaceIdx] > ZERO_ONE_BOUNDARY) packed = packed or (1L shl b)
        }
        return packed
    }

    private fun splitPacked(packed: Long, variant: Variant): Decoded {
        val commandMask = (1L shl variant.commandBits) - 1
        val command = (packed and commandMask).toInt()
        val address = (packed ushr variant.commandBits).toInt() and
            ((1 shl (variant.totalBits - variant.commandBits)) - 1)
        return Decoded(variant, address, command)
    }

    /** Build a SIRC frame for the given variant, address, and command. */
    fun encode(variant: Variant, address: Int, command: Int): IntArray {
        // Mask to the variant's bit widths so callers can pass a Long /
        // Int without worrying about high-bit junk.
        val commandMask = (1 shl variant.commandBits) - 1
        val addressMask = (1 shl (variant.totalBits - variant.commandBits)) - 1
        val cmd = command and commandMask
        val addr = address and addressMask
        val packed = (addr.toLong() shl variant.commandBits) or cmd.toLong()

        val out = IntArray(2 + variant.totalBits * 2)
        out[0] = LEADER_MARK
        out[1] = LEADER_SPACE
        for (b in 0 until variant.totalBits) {
            out[2 + b * 2] = BIT_MARK
            out[2 + b * 2 + 1] = if ((packed ushr b) and 1L == 1L) ONE_SPACE else ZERO_SPACE
        }
        return out
    }

    /**
     * Re-synthesize a SIRC frame from a packed Long. The variant has
     * to be inferred from a stored field — for now we always assume
     * SIRC-12 because that's what 99% of consumer SIRC remotes use.
     * SIRC-15 / -20 transmit-from-code support is queued; until then
     * captures of those variants fall back to rawTimings replay.
     */
    fun encodeFromPacked(code: Long, variant: Variant = Variant.SIRC_12): IntArray {
        val commandMask = (1L shl variant.commandBits) - 1
        val command = (code and commandMask).toInt()
        val address = (code ushr variant.commandBits).toInt() and
            ((1 shl (variant.totalBits - variant.commandBits)) - 1)
        return encode(variant, address, command)
    }

    private fun within(value: Int, target: Int, tolerance: Int): Boolean =
        abs(value - target) <= tolerance
}
