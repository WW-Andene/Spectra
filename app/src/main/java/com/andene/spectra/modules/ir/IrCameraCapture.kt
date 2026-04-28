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

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState

    private val _capturedCommand = MutableStateFlow<IrCommand?>(null)
    val capturedCommand: StateFlow<IrCommand?> = _capturedCommand

    // Frames captured during a press, in order. Each frame is one column of
    // luminance values from the row position where the LED is brightest.
    private val frames = mutableListOf<Pair<Long, IntArray>>()
    private var captureStartNanos = 0L

    enum class CaptureState {
        IDLE, CAPTURING, PROCESSING, DECODED, ERROR
    }

    fun buildAnalyzer(): ImageAnalysis {
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (_captureState.value == CaptureState.CAPTURING) {
                        analyzeFrame(imageProxy)
                    }
                    imageProxy.close()
                }
            }
        return imageAnalysis!!
    }

    fun startCapture() {
        frames.clear()
        captureStartNanos = System.nanoTime()
        _capturedCommand.value = null
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

        val protocol = attemptProtocolDecode(timings.toList())
        _capturedCommand.value = IrCommand(
            name = "captured_${System.currentTimeMillis()}",
            rawTimings = timings,
            protocol = protocol ?: IrProtocol.RAW,
            capturedVia = CaptureMethod.CAMERA_DECODE,
        )
        _captureState.value = CaptureState.DECODED
        Log.d(TAG, "Decoded ${timings.size} pulses, protocol: $protocol, threshold: $threshold")
    }

    /**
     * Header-pulse based protocol guess. Tolerances are wide because rolling-
     * shutter timestamps have ~per-row jitter; the goal is to bucket the
     * remote's family, not pin down the exact protocol variant.
     */
    private fun attemptProtocolDecode(timings: List<Int>): IrProtocol? {
        if (timings.size < 4) return null

        val headerMark = timings[0]
        val headerSpace = timings[1]

        if (headerMark in 8000..10000 && headerSpace in 4000..5000) return IrProtocol.NEC
        if (headerMark in 4000..5000 && headerSpace in 4000..5000) return IrProtocol.SAMSUNG
        if (headerMark in 7500..9500 && headerSpace in 3800..4700) return IrProtocol.LG
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
