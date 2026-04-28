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
     * command using the NEC-family 32-bit + inverted-byte-checksum
     * layout. Wraps [decodeRaw] with the checksum validation that
     * NEC, Samsung, and LG share.
     *
     * The first array element must be a mark.
     */
    fun decode(
        timings: IntArray,
        header: Header,
        bit: BitTiming = BitTiming(),
        repeat: RepeatShape? = null,
    ): Decoded? {
        val raw = decodeRaw(timings, header, bit, totalBits = 32, repeat = repeat) ?: return null
        if (raw.isRepeat) return Decoded(address = 0, command = 0, isRepeat = true)

        val packed = raw.packed.toInt()
        val address = packed and 0xFF
        val addressInv = (packed ushr 8) and 0xFF
        val command = (packed ushr 16) and 0xFF
        val commandInv = (packed ushr 24) and 0xFF
        if ((address xor addressInv) != 0xFF) return null
        if ((command xor commandInv) != 0xFF) return null

        return Decoded(address = address, command = command, isRepeat = false)
    }

    /** [decodeRaw]'s output: the raw [totalBits] LSB-first as a Long
     *  plus the repeat flag. Used by codecs whose data layout isn't
     *  the NEC-family 8+8+8+8 inverted shape (Panasonic 48-bit, etc.). */
    data class Raw(val packed: Long, val isRepeat: Boolean)

    /**
     * Decode [totalBits] of pulse-distance data without imposing any
     * particular address / command layout or checksum. Returns the
     * raw LSB-first packed value as a Long (≤64 bits supported).
     * Caller is responsible for byte unpacking + protocol-specific
     * checksum validation.
     */
    fun decodeRaw(
        timings: IntArray,
        header: Header,
        bit: BitTiming = BitTiming(),
        totalBits: Int = 32,
        repeat: RepeatShape? = null,
        requireClosingMark: Boolean = true,
    ): Raw? {
        if (timings.size < 4) return null
        if (!within(timings[0], header.mark, header.markTol)) return null

        if (repeat != null && within(timings[1], repeat.space, repeat.spaceTol)) {
            return if (timings.size >= 3 && within(timings[2], bit.mark, bit.markTol)) {
                Raw(packed = 0L, isRepeat = true)
            } else null
        }
        if (!within(timings[1], header.space, header.spaceTol)) return null

        val needed = 2 + totalBits * 2 + (if (requireClosingMark) 1 else 0)
        if (timings.size < needed) return null

        var packed = 0L
        for (b in 0 until totalBits) {
            val markIdx = 2 + b * 2
            val spaceIdx = markIdx + 1
            if (!within(timings[markIdx], bit.mark, bit.markTol)) return null
            if (timings[spaceIdx] > bit.zeroOneBoundary) packed = packed or (1L shl b)
        }
        return Raw(packed = packed, isRepeat = false)
    }

    /**
     * Build an alternating mark/space timings array from address +
     * command using the NEC-family 32-bit + inverted-byte-checksum
     * layout. Output length: 2 (header) + 64 (32 bits) + 1 (closing
     * mark) = 67 entries.
     */
    fun encode(
        address: Int,
        command: Int,
        header: Header,
        bit: BitTiming = BitTiming(),
    ): IntArray {
        val packed = (address.toLong() and 0xFF) or
            ((address.inv().toLong() and 0xFF) shl 8) or
            ((command.toLong() and 0xFF) shl 16) or
            ((command.inv().toLong() and 0xFF) shl 24)
        return encodeRaw(packed, totalBits = 32, header = header, bit = bit)
    }

    /**
     * Build an alternating mark/space timings array from a raw packed
     * Long (LSB-first) of [totalBits] bits. Used by codecs that don't
     * fit the NEC-family inverted-byte layout (Panasonic 48-bit, etc.).
     */
    fun encodeRaw(
        packed: Long,
        totalBits: Int,
        header: Header,
        bit: BitTiming = BitTiming(),
        includeClosingMark: Boolean = true,
    ): IntArray {
        val out = IntArray(2 + totalBits * 2 + (if (includeClosingMark) 1 else 0))
        out[0] = header.mark
        out[1] = header.space
        var i = 2
        for (b in 0 until totalBits) {
            out[i++] = bit.mark
            out[i++] = if ((packed ushr b) and 1L == 1L) bit.oneSpace else bit.zeroSpace
        }
        if (includeClosingMark) out[i] = bit.mark
        return out
    }

    private fun within(value: Int, target: Int, tolerance: Int): Boolean =
        abs(value - target) <= tolerance
}
