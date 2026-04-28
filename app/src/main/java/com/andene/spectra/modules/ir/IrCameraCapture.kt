package com.andene.spectra.modules.ir

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.andene.spectra.data.models.CaptureMethod
import com.andene.spectra.data.models.IrCommand
import com.andene.spectra.data.models.IrProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Module 1 — IR Camera Capture (rolling-shutter sampling)
 *
 * The naïve "one intensity per frame" approach is hard-capped at the camera
 * frame rate (~30 Hz on most ImageAnalysis pipelines), which is two orders of
 * magnitude too coarse for IR protocol bit decoding (NEC bits are 562 µs).
 *
 * CMOS rolling-shutter sensors expose each row sequentially with a fixed
 * per-row line-readout time. By scanning the column where the IR LED is
 * brightest *row-by-row* and giving each row its own timestamp, we lift the
 * effective sample rate to roughly fps × height — typically 20–60 kHz —
 * which is enough to resolve the 562 µs / 1687 µs NEC bit widths and the
 * sub-millisecond marks/spaces of SIRC and friends.
 *
 * Per-row line time is estimated from the frame interval (`frameDelta /
 * height`); this counts vertical blanking as part of the line time, which
 * slightly lengthens our per-row tick but eliminates the bookkeeping
 * required to span the inter-frame gap. Header detection still works.
 *
 * Decode quality is best when:
 *   1. The phone is held with the remote pointed at the camera, LED roughly
 *      centred horizontally.
 *   2. The user records a steady press (≥ 100 ms).
 *   3. Ambient lighting is low — a bright background washes out the LED.
 */
class IrCameraCapture(private val context: Context) {

    companion object {
        private const val TAG = "IrCameraCapture"

        /** A row is considered "ON" if its luminance is at least this fraction
         *  of the way from the noise floor to the brightest sampled value. */
        private const val ON_FRACTION = 0.5f

        /** Minimum number of distinct rows in the captured timeline to even
         *  attempt decoding. */
        private const val MIN_TIMELINE_ROWS = 256

        /** When picking the brightest column, sample every Nth column to keep
         *  per-frame work bounded. */
        private const val COLUMN_PROBE_STEP = 8

        /** Cap on retained frames during a single capture press. At 30fps × 720
         *  rows × 4 bytes each, 256 frames ≈ 22 MB — well clear of OOM but
         *  long enough (~8.5s) for any realistic IR press. Anything beyond
         *  this is almost certainly a stuck capture. */
        private const val MAX_CAPTURE_FRAMES = 256

        /**
         * Reconstruct an alternating ON/OFF microsecond timing list from a
         * sequence of (frame-start, per-row-intensity) samples.
         *
         * Pure function for testability; no Android types in the signature.
         *
         * @param frames frames in capture order; each frame's array length is
         *   the row count of the camera output.
         * @param onThreshold luminance value (0–255) at and above which a row
         *   counts as IR-on.
         * @return alternating ON/OFF durations in microseconds. The first
         *   entry is whichever state the capture began in.
         */
        fun decodeTimeline(
            frames: List<Pair<Long, IntArray>>,
            onThreshold: Int,
        ): IntArray {
            if (frames.size < 2) return IntArray(0)
            val rows = frames[0].second.size
            if (rows == 0) return IntArray(0)

            // Per-row tick: total elapsed nanoseconds divided by total samples.
            // Using whole-capture average instead of pairwise frame deltas
            // smooths over the occasional dropped/late frame.
            val totalElapsed = frames.last().first - frames.first().first
            val totalRows = (frames.size - 1).toLong() * rows
            val perRowNs = if (totalRows > 0) totalElapsed.toDouble() / totalRows else 0.0
            if (perRowNs <= 0) return IntArray(0)

            val out = ArrayList<Int>(frames.size * 4)
            var currentOn = frames[0].second[0] >= onThreshold
            var stateStartNs = frames[0].first

            for (frame in frames) {
                val (frameStartNs, perRow) = frame
                for (row in 0 until rows) {
                    val tNs = frameStartNs + (row * perRowNs).toLong()
                    val on = perRow[row] >= onThreshold
                    if (on != currentOn) {
                        val durationUs = ((tNs - stateStartNs) / 1000L).toInt()
                        if (durationUs > 0) out.add(durationUs)
                        currentOn = on
                        stateStartNs = tNs
                    }
                }
            }
            // Trailing segment.
            val tail = ((frames.last().first +
                ((rows - 1) * perRowNs).toLong() - stateStartNs) / 1000L).toInt()
            if (tail > 0) out.add(tail)

            return out.toIntArray()
        }

        /**
         * Pick a luminance threshold halfway between the captured min and
         * max — a bimodal-distribution split that lands between the IR-off
         * baseline and the IR-on peak.
         */
        fun computeThreshold(frames: List<Pair<Long, IntArray>>): Int {
            if (frames.isEmpty()) return 200
            var max = 0
            var min = 255
            for ((_, perRow) in frames) {
                for (v in perRow) {
                    if (v > max) max = v
                    if (v < min) min = v
                }
            }
            val gap = (max - min).coerceAtLeast(20)
            return min + (gap * ON_FRACTION).toInt()
        }
    }

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null

    /**
     * Lazily replace the executor when it's been shut down. Without this,
     * a buildAnalyzer() after release() would post tasks to a dead executor
     * and silently drop every frame.
     */
    private fun ensureExecutor() {
        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState

    private val _capturedCommand = MutableStateFlow<IrCommand?>(null)
    val capturedCommand: StateFlow<IrCommand?> = _capturedCommand

    /** Quality summary for the most recent successful capture
     *  (B-100 phase 6). Null until a decode finishes. The UI shows
     *  this beside the capture status text so the user knows whether
     *  their press(es) agreed cleanly or only one out of several
     *  decoded — directly answering the "did it work?" question
     *  without needing to test the saved command. */
    private val _lastCaptureQuality = MutableStateFlow<CaptureQuality?>(null)
    val lastCaptureQuality: StateFlow<CaptureQuality?> = _lastCaptureQuality

    data class CaptureQuality(
        val pressesDetected: Int,
        val pressesAgreeing: Int,
        val protocol: IrProtocol,
        /** Null when no codec'd protocol was detected — IrCommand.code
         *  is null in that case and we replay raw. */
        val packedCode: Long?,
    ) {
        val isHighConfidence: Boolean get() =
            pressesAgreeing >= 2 && pressesAgreeing == pressesDetected
        val isMixed: Boolean get() =
            pressesDetected >= 2 && pressesAgreeing < pressesDetected
    }

    // Frames captured during a press, in order. Each frame is one column of
    // luminance values from the row position where the LED is brightest.
    private val frames = mutableListOf<Pair<Long, IntArray>>()
    private var captureStartNanos = 0L

    enum class CaptureState {
        IDLE, CAPTURING, PROCESSING, DECODED, ERROR
    }

    fun buildAnalyzer(): ImageAnalysis {
        ensureExecutor()
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    // Read state and append-or-skip atomically wrt stopCapture's
                    // PROCESSING transition: analyzeFrame's synchronized block
                    // already gates the list, but the state check has to live
                    // inside it too, otherwise a frame can land in the list
                    // after processTimeline has snapshotted it.
                    analyzeFrame(imageProxy)
                    imageProxy.close()
                }
            }
        return imageAnalysis!!
    }

    fun startCapture() {
        frames.clear()
        captureStartNanos = System.nanoTime()
        _capturedCommand.value = null
        _lastCaptureQuality.value = null
        _captureState.value = CaptureState.CAPTURING
        Log.d(TAG, "IR capture started (rolling-shutter mode)")
    }

    fun stopCapture() {
        _captureState.value = CaptureState.PROCESSING
        Log.d(TAG, "IR capture stopped, ${frames.size} frames recorded")
        processTimeline()
    }

    /**
     * Per-frame: locate the brightest column in the centre band, then read
     * every row's luminance from that column. The result is a tall, narrow
     * intensity strip — one luminance value per pixel-row, time-stamped at
     * the frame's start.
     */
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = yPlane.rowStride
        val frameStartNs = System.nanoTime() - captureStartNanos

        // Locate the brightest column by probing two horizontal lines (top
        // third and middle) every COLUMN_PROBE_STEP pixels. Two lines guards
        // against a single hot row coincidentally winning.
        val probeRows = intArrayOf(height / 3, height / 2)
        var bestCol = width / 2
        var bestVal = 0
        var col = 0
        while (col < width) {
            for (row in probeRows) {
                val v = buffer.get(row * rowStride + col).toInt() and 0xFF
                if (v > bestVal) { bestVal = v; bestCol = col }
            }
            col += COLUMN_PROBE_STEP
        }

        // Read the full column (one luminance value per row).
        val perRow = IntArray(height)
        for (row in 0 until height) {
            perRow[row] = buffer.get(row * rowStride + bestCol).toInt() and 0xFF
        }

        synchronized(frames) {
            // The state check belongs inside the lock so a frame can never
            // land in the list after stopCapture has flipped state to
            // PROCESSING and processTimeline has begun snapshotting.
            if (_captureState.value != CaptureState.CAPTURING) return
            // Hard cap. A stuck capture can't grow without bound — at the
            // limit we stop appending and let processTimeline run on what
            // we have. The user still sees CAPTURING state; pressing stop
            // produces a result from the captured prefix.
            if (frames.size >= MAX_CAPTURE_FRAMES) return
            frames.add(frameStartNs to perRow)
        }
    }

    private fun processTimeline() {
        val snapshot = synchronized(frames) { frames.toList() }
        val totalRows = snapshot.sumOf { it.second.size }
        if (totalRows < MIN_TIMELINE_ROWS) {
            _captureState.value = CaptureState.ERROR
            return
        }

        val threshold = computeThreshold(snapshot)
        val timings = decodeTimeline(snapshot, threshold)
        if (timings.size < 4) {
            _captureState.value = CaptureState.ERROR
            return
        }

        // B-100 phase 3: multi-press averaging. Split the captured
        // timeline at long inter-press gaps (a quiet space ≥ 50 ms is
        // way longer than any intra-frame gap, so each segment is one
        // button press). Decode each segment independently, then vote
        // — if 3 of 5 presses agreed on NEC 0x1234, we ship that with
        // higher confidence than a single best-effort capture.
        val segments = splitOnLongGaps(timings)
        val perSegmentDecodes = segments.mapNotNull { decodeOneSegment(it) }
        val winner = if (perSegmentDecodes.isNotEmpty()) {
            // Vote on the (protocol, packedCode-or-raw) tuple. Codes
            // match across presses when the same physical button was
            // pressed and the protocol decoded cleanly. Ties broken by
            // first occurrence — preserves a sensible default when the
            // user only pressed once.
            perSegmentDecodes.groupingBy { it.signature() }.eachCount()
                .maxByOrNull { it.value }
                ?.let { entry -> perSegmentDecodes.first { it.signature() == entry.key } }
        } else null

        val (protocol, packedCode, chosenTimings, agreement) = if (winner != null) {
            val agreement = perSegmentDecodes.count { it.signature() == winner.signature() }
            Quad(winner.protocol, winner.packedCode, winner.timings, agreement)
        } else {
            // Fallback to whole-timeline decode (matches phase 1 / 2
            // behaviour for unsplittable captures — single fast press
            // with no inter-press gap, or non-codec'd protocol).
            val protocol = attemptProtocolDecode(timings.toList()) ?: IrProtocol.RAW
            Quad(protocol, decodeWithCodec(timings, protocol), timings, 1)
        }

        _capturedCommand.value = IrCommand(
            name = "captured_${System.currentTimeMillis()}",
            rawTimings = chosenTimings,
            protocol = protocol,
            code = packedCode,
            capturedVia = CaptureMethod.CAMERA_DECODE,
        )
        _lastCaptureQuality.value = CaptureQuality(
            pressesDetected = perSegmentDecodes.size.coerceAtLeast(1),
            pressesAgreeing = agreement,
            protocol = protocol,
            packedCode = packedCode,
        )
        _captureState.value = CaptureState.DECODED
        Log.d(
            TAG,
            "Decoded ${chosenTimings.size} pulses, protocol: $protocol, code: ${packedCode?.let { "0x%04X".format(it) }}, agreement: $agreement/${perSegmentDecodes.size.coerceAtLeast(1)}, threshold: $threshold",
        )
    }

    /** Result of decoding one isolated press within a multi-press capture. */
    private data class SegmentDecode(
        val protocol: IrProtocol,
        val packedCode: Long?,
        val timings: IntArray,
    ) {
        /** Vote signature: (protocol, code) when codec decoded; otherwise
         *  (protocol, null) so unrelated raw captures don't pile onto a
         *  single bucket just because they share a protocol bucket. */
        fun signature(): Pair<IrProtocol, Long?> = protocol to packedCode
    }

    /** 4-tuple helper for processTimeline's multi-press fallback path. */
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D) {
        operator fun component1() = a
        operator fun component2() = b
        operator fun component3() = c
        operator fun component4() = d
    }

    /**
     * Walk an alternating mark/space timings array and break it at any
     * space ≥ 50 ms — those are inter-press gaps. Returns each press's
     * timings as an independent IntArray. A single-press capture
     * returns a list of one element (the entire array).
     */
    private fun splitOnLongGaps(timings: IntArray): List<IntArray> {
        val gapThresholdUs = 50_000
        val segments = mutableListOf<IntArray>()
        var segmentStart = 0
        var i = 0
        while (i < timings.size) {
            // Spaces are at odd indices (mark/space alternation, starting
            // with mark at 0). Only spaces can be inter-press gaps —
            // marks are always sub-millisecond.
            if (i % 2 == 1 && timings[i] >= gapThresholdUs) {
                if (i > segmentStart) {
                    segments.add(timings.copyOfRange(segmentStart, i))
                }
                segmentStart = i + 1
            }
            i++
        }
        if (segmentStart < timings.size) {
            segments.add(timings.copyOfRange(segmentStart, timings.size))
        }
        // Filter segments too short to be a real press (need at least
        // a header + a few bits to be plausible).
        return segments.filter { it.size >= 8 }
            .ifEmpty { listOf(timings) }
    }

    private fun decodeOneSegment(segment: IntArray): SegmentDecode? {
        val protocol = attemptProtocolDecode(segment.toList()) ?: return null
        val code = decodeWithCodec(segment, protocol)
        return SegmentDecode(protocol = protocol, packedCode = code, timings = segment)
    }

    private fun decodeWithCodec(timings: IntArray, protocol: IrProtocol): Long? = when (protocol) {
        IrProtocol.NEC ->
            com.andene.spectra.modules.ir.protocols.NecCodec.decode(timings)?.let {
                if (it.isRepeat) null else it.packed()
            }
        IrProtocol.SAMSUNG ->
            com.andene.spectra.modules.ir.protocols.SamsungCodec.decode(timings)?.packed()
        IrProtocol.LG ->
            com.andene.spectra.modules.ir.protocols.LgCodec.decode(timings)?.packed()
        IrProtocol.SIRC_12, IrProtocol.SIRC_15, IrProtocol.SIRC_20 ->
            com.andene.spectra.modules.ir.protocols.SonyCodec.decode(timings)?.let { decoded ->
                // Only round-trip via packed-code for SIRC-12 — the
                // common case. SIRC-15 / SIRC-20 captures fall back to
                // rawTimings replay until SonyCodec.encodeFromPacked
                // tracks the variant on the IrCommand. attemptProtocolDecode
                // labels them all SIRC_12 currently anyway.
                if (decoded.variant == com.andene.spectra.modules.ir.protocols.SonyCodec.Variant.SIRC_12)
                    decoded.packed() else null
            }
        else -> null
    }

    /**
     * Header-pulse based protocol guess. Tolerances are wide because rolling-
     * shutter timestamps have ~per-row jitter; the goal is to bucket the
     * remote's family, not pin down the exact protocol variant.
     *
     * Order matters: NEC and LG share the same family so their windows
     * touch (NEC 9000+4500, LG 8500+4250 — only 500us / 250us apart).
     * Previously NEC's 8000..10000 mark window swallowed every LG
     * signal because NEC was checked first. The tightened NEC window
     * here (8800..10000) lets LG's mark fall through cleanly while
     * still catching jittery NEC captures down to 8800us.
     */
    private fun attemptProtocolDecode(timings: List<Int>): IrProtocol? {
        if (timings.size < 4) return null

        val headerMark = timings[0]
        val headerSpace = timings[1]

        if (headerMark in 8800..10000 && headerSpace in 4300..5000) return IrProtocol.NEC
        if (headerMark in 4000..5000 && headerSpace in 4000..5000) return IrProtocol.SAMSUNG
        if (headerMark in 7500..8800 && headerSpace in 3800..4500) return IrProtocol.LG
        if (headerMark in 2200..2600 && headerSpace in 400..800) return IrProtocol.SIRC_12
        if (headerMark in 200..450) return IrProtocol.SHARP

        val avgTiming = timings.average()
        if (avgTiming in 700.0..1100.0) return IrProtocol.RC5

        return IrProtocol.UNKNOWN
    }

    fun release() {
        cameraExecutor.shutdown()
    }
}
