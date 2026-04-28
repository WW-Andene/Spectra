package com.andene.spectra.modules.ir

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.andene.spectra.data.models.CaptureMethod
import com.andene.spectra.data.models.IrCommand
import com.andene.spectra.data.models.IrProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Module 1 — IR Camera Capture
 *
 * Uses the front camera to detect IR LED flashes from existing remotes.
 * Analyzes frame-by-frame intensity changes at max FPS to reconstruct
 * the raw IR pulse timing waveform.
 *
 * Flow:
 * 1. Open front camera at max FPS (240fps ideal, 120fps workable)
 * 2. User points remote at camera and presses button
 * 3. Analyzer detects intensity spikes frame-by-frame
 * 4. Spike timing → pulse train → raw waveform
 * 5. Optional: attempt protocol decode (NEC, RC5, etc.)
 * 6. Store as IrCommand for replay via Module 6
 */
class IrCameraCapture(private val context: Context) {

    companion object {
        private const val TAG = "IrCameraCapture"
        private const val IR_INTENSITY_THRESHOLD = 200 // 0-255, tuned for IR LED brightness
        private const val MIN_PULSE_FRAMES = 2
        private const val REGION_SIZE = 80 // px, center crop for IR detection
    }

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState

    private val _capturedCommand = MutableStateFlow<IrCommand?>(null)
    val capturedCommand: StateFlow<IrCommand?> = _capturedCommand

    // Frame intensity timeline for waveform reconstruction
    private val intensityTimeline = mutableListOf<FrameSample>()
    private var captureStartNanos = 0L

    data class FrameSample(
        val timestampNanos: Long,
        val peakIntensity: Int,
        val avgIntensity: Float,
        val isIrDetected: Boolean
    )

    enum class CaptureState {
        IDLE, CAPTURING, PROCESSING, DECODED, ERROR
    }

    /**
     * Build a CameraX ImageAnalysis use case configured for max throughput.
     * Caller binds this to their CameraProvider alongside preview if needed.
     */
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
        intensityTimeline.clear()
        captureStartNanos = System.nanoTime()
        _capturedCommand.value = null
        _captureState.value = CaptureState.CAPTURING
        Log.d(TAG, "IR capture started")
    }

    fun stopCapture() {
        _captureState.value = CaptureState.PROCESSING
        Log.d(TAG, "IR capture stopped, ${intensityTimeline.size} frames recorded")
        processTimeline()
    }

    /**
     * Analyze a single camera frame for IR intensity.
     * Examines center region of Y (luminance) plane only — IR shows as bright spots.
     */
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = yPlane.rowStride

        // Sample center region where user aims the remote
        val centerX = width / 2
        val centerY = height / 2
        val halfRegion = REGION_SIZE / 2

        var peakIntensity = 0
        var totalIntensity = 0L
        var pixelCount = 0

        for (y in (centerY - halfRegion)..(centerY + halfRegion)) {
            for (x in (centerX - halfRegion)..(centerX + halfRegion)) {
                if (y in 0 until height && x in 0 until width) {
                    val intensity = buffer.get(y * rowStride + x).toInt() and 0xFF
                    if (intensity > peakIntensity) peakIntensity = intensity
                    totalIntensity += intensity
                    pixelCount++
                }
            }
        }

        val avg = if (pixelCount > 0) totalIntensity.toFloat() / pixelCount else 0f
        val isIr = peakIntensity >= IR_INTENSITY_THRESHOLD

        intensityTimeline.add(
            FrameSample(
                timestampNanos = System.nanoTime() - captureStartNanos,
                peakIntensity = peakIntensity,
                avgIntensity = avg,
                isIrDetected = isIr
            )
        )
    }

    /**
     * Convert frame intensity timeline into IR pulse timings.
     * Groups consecutive IR-detected frames into ON pulses,
     * gaps between them into OFF pulses.
     */
    private fun processTimeline() {
        if (intensityTimeline.size < MIN_PULSE_FRAMES) {
            _captureState.value = CaptureState.ERROR
            return
        }

        val timings = mutableListOf<Int>() // Alternating ON/OFF durations in microseconds
        var currentState = false // false = OFF, true = ON
        var stateStartNanos = intensityTimeline.first().timestampNanos

        for (sample in intensityTimeline) {
            if (sample.isIrDetected != currentState) {
                val durationUs = ((sample.timestampNanos - stateStartNanos) / 1000).toInt()
                if (durationUs > 0) {
                    timings.add(durationUs)
                }
                currentState = sample.isIrDetected
                stateStartNanos = sample.timestampNanos
            }
        }

        // Add final segment
        val lastDuration = ((intensityTimeline.last().timestampNanos - stateStartNanos) / 1000).toInt()
        if (lastDuration > 0) timings.add(lastDuration)

        if (timings.size < 4) {
            _captureState.value = CaptureState.ERROR
            return
        }

        val protocol = attemptProtocolDecode(timings)

        _capturedCommand.value = IrCommand(
            name = "captured_${System.currentTimeMillis()}",
            rawTimings = timings.toIntArray(),
            protocol = protocol ?: IrProtocol.RAW,
            capturedVia = CaptureMethod.CAMERA_DECODE
        )
        _captureState.value = CaptureState.DECODED
        Log.d(TAG, "Decoded ${timings.size} pulses, protocol: $protocol")
    }

    /**
     * Attempt to identify the IR protocol from pulse timings.
     * Checks header pulse patterns characteristic of each protocol family.
     */
    private fun attemptProtocolDecode(timings: List<Int>): IrProtocol? {
        if (timings.size < 4) return null

        val headerMark = timings[0]
        val headerSpace = timings[1]

        // NEC: 9000µs mark + 4500µs space
        if (headerMark in 8000..10000 && headerSpace in 4000..5000) return IrProtocol.NEC

        // Samsung: 4500µs mark + 4500µs space
        if (headerMark in 4000..5000 && headerSpace in 4000..5000) return IrProtocol.SAMSUNG

        // RC5: No header, manchester encoded, ~889µs bit time
        val avgTiming = timings.average()
        if (avgTiming in 700.0..1100.0) return IrProtocol.RC5

        // Sony SIRC: 2400µs mark + 600µs space
        if (headerMark in 2200..2600 && headerSpace in 400..800) return IrProtocol.SIRC_12

        // Sharp: 320µs mark + 680µs/1680µs space pattern
        if (headerMark in 200..450) return IrProtocol.SHARP

        // LG: 8500µs mark + 4250µs space
        if (headerMark in 7500..9500 && headerSpace in 3800..4700) return IrProtocol.LG

        return IrProtocol.UNKNOWN
    }

    fun release() {
        cameraExecutor.shutdown()
    }
}
