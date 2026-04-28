package com.andene.spectra.modules.acoustic

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.andene.spectra.data.models.AcousticSignature
import com.andene.spectra.data.models.FrequencyPeak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Module 2 — Acoustic Fingerprint
 *
 * Captures ambient audio near a device and extracts its unique
 * acoustic signature from coil whine, backlight hum, fan noise,
 * and power supply buzz.
 *
 * Key insight: Electronic devices produce consistent, characteristic
 * audio frequencies determined by their circuit design:
 * - Switching power supplies: 20-200kHz (harmonics audible below 20kHz)
 * - Backlight PWM: typically 200-2000Hz
 * - Coil whine: 1-10kHz range
 * - Fan noise: broadband but with blade-pass frequency peaks
 *
 * These combine into a unique per-model fingerprint.
 */
class AcousticFingerprint {

    companion object {
        private const val TAG = "AcousticFingerprint"
        private const val SAMPLE_RATE = 44100
        private const val FFT_SIZE = 4096 // ~10Hz resolution at 44.1kHz
        private const val CAPTURE_DURATION_MS = 3000L
        private const val NUM_FFT_AVERAGES = 10 // Average multiple FFT windows for stability
        private const val PEAK_THRESHOLD_DB = -40f
        private const val MAX_PEAKS = 20
    }

    private var audioRecord: AudioRecord? = null

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _signature = MutableStateFlow<AcousticSignature?>(null)
    val signature: StateFlow<AcousticSignature?> = _signature

    enum class State {
        IDLE, RECORDING, ANALYZING, COMPLETE, ERROR
    }

    /**
     * Record audio and extract acoustic fingerprint.
     * Should be called with phone held ~5-30cm from target device.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun capture(): AcousticSignature? = withContext(Dispatchers.IO) {
        // Outer try/finally guarantees release() runs even if AudioRecord
        // construction succeeds but startRecording() throws. The previous
        // structure put release inside an inner try that wrapped only the
        // capture loop, so a STATE_UNINITIALIZED AudioRecord (mic in use,
        // sample rate unsupported, deeper SELinux denial) would leak —
        // audioRecord field would still hold the unreleased instance and
        // the system would treat the mic as held until the OS eventually
        // GC'd the AudioRecord and ran its native finalizer.
        try {
            _state.value = State.RECORDING

            val bufferSize = maxOf(
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ),
                FFT_SIZE * 2
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            // AudioRecord(...) doesn't throw on init failure — it returns an
            // instance with state STATE_UNINITIALIZED. Calling startRecording
            // on that instance throws IllegalStateException. Check explicitly
            // so we surface a clean ERROR state rather than relying on the
            // generic catch below.
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed (mic in use? unsupported sample rate?)")
                _state.value = State.ERROR
                return@withContext null
            }

            // Collect chunks instead of a List<Short> — boxing each sample
            // would blow ~3 MB on a 3-second 44.1 kHz mono recording.
            val chunks = mutableListOf<ShortArray>()
            val buffer = ShortArray(FFT_SIZE)

            audioRecord?.startRecording()
            val startTime = System.currentTimeMillis()

            try {
                while (System.currentTimeMillis() - startTime < CAPTURE_DURATION_MS) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) chunks.add(buffer.copyOf(read))
                }
            } finally {
                // Stop recording while we still hold the inner scope; the
                // outer finally will release the AudioRecord instance.
                try { audioRecord?.stop() } catch (_: Exception) {}
            }

            _state.value = State.ANALYZING
            val sig = analyzeAudio(concatChunks(chunks))
            _signature.value = sig
            _state.value = State.COMPLETE
            sig
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            _state.value = State.ERROR
            null
        } finally {
            // Outer finally — runs on every exit path (success, exception,
            // cancellation). Without this, an init-failure path leaked the
            // mic until GC.
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
        }
    }

    /**
     * Run FFT on captured audio, extract frequency peaks,
     * compute spectral centroid and harmonic patterns.
     */
    private fun analyzeAudio(samples: ShortArray): AcousticSignature {
        val numWindows = minOf(NUM_FFT_AVERAGES, samples.size / FFT_SIZE)
        val avgMagnitudes = FloatArray(FFT_SIZE / 2)

        // Average multiple FFT windows for noise reduction
        for (w in 0 until numWindows) {
            val offset = w * (samples.size - FFT_SIZE) / maxOf(numWindows - 1, 1)
            val window = samples.copyOfRange(offset, offset + FFT_SIZE)
            val magnitudes = computeFFT(window)
            for (i in avgMagnitudes.indices) {
                avgMagnitudes[i] += magnitudes[i] / numWindows
            }
        }

        // Convert to dB
        val magnitudesDb = FloatArray(avgMagnitudes.size) { i ->
            val mag = avgMagnitudes[i]
            if (mag > 0) 20f * log10(mag) else -100f
        }

        // Find noise floor
        val sortedDb = magnitudesDb.sorted()
        val noiseFloorDb = sortedDb[sortedDb.size / 4] // 25th percentile

        // Extract peaks above threshold
        val peaks = mutableListOf<FrequencyPeak>()
        val freqResolution = SAMPLE_RATE.toFloat() / FFT_SIZE

        for (i in 2 until magnitudesDb.size - 2) {
            val freq = i * freqResolution
            val mag = magnitudesDb[i]

            // Local maximum check + above threshold
            if (mag > PEAK_THRESHOLD_DB &&
                mag > magnitudesDb[i - 1] &&
                mag > magnitudesDb[i + 1] &&
                mag > magnitudesDb[i - 2] &&
                mag > magnitudesDb[i + 2]
            ) {
                // Estimate bandwidth at -3dB
                var bwLeft = i
                while (bwLeft > 0 && magnitudesDb[bwLeft] > mag - 3) bwLeft--
                var bwRight = i
                while (bwRight < magnitudesDb.size - 1 && magnitudesDb[bwRight] > mag - 3) bwRight++
                val bandwidth = (bwRight - bwLeft) * freqResolution

                peaks.add(FrequencyPeak(freq, mag, bandwidth))
            }
        }

        // Keep top N peaks by magnitude
        val topPeaks = peaks.sortedByDescending { it.magnitudeDb }.take(MAX_PEAKS)

        // Spectral centroid
        var weightedSum = 0f
        var totalMag = 0f
        for (i in avgMagnitudes.indices) {
            val freq = i * freqResolution
            weightedSum += freq * avgMagnitudes[i]
            totalMag += avgMagnitudes[i]
        }
        val centroid = if (totalMag > 0) weightedSum / totalMag else 0f

        // Harmonic pattern — ratios between top peaks
        val harmonicPattern = if (topPeaks.size >= 2) {
            val fundamental = topPeaks.first().frequencyHz
            topPeaks.map { it.frequencyHz / fundamental }.toFloatArray()
        } else {
            floatArrayOf()
        }

        return AcousticSignature(
            dominantFrequencies = topPeaks,
            spectralCentroid = centroid,
            harmonicPattern = harmonicPattern,
            noiseFloorDb = noiseFloorDb,
            rawFftSnapshot = magnitudesDb
        )
    }

    /**
     * Simple radix-2 DIT FFT implementation.
     * Returns magnitude spectrum (first half only — positive frequencies).
     */
    private fun computeFFT(samples: ShortArray): FloatArray {
        val n = samples.size
        // Normalize to -1..1 and apply Hann window
        val real = FloatArray(n) { i ->
            val hannWindow = 0.5f * (1f - cos(2f * PI.toFloat() * i / (n - 1)))
            (samples[i].toFloat() / Short.MAX_VALUE) * hannWindow
        }
        val imag = FloatArray(n)

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var m = n / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        // FFT butterfly
        var step = 1
        while (step < n) {
            val halfStep = step
            step *= 2
            val wReal = cos(PI / halfStep).toFloat()
            val wImag = -sin(PI / halfStep).toFloat()
            var wr = 1f
            var wi = 0f

            for (k in 0 until halfStep) {
                var i2 = k
                while (i2 < n) {
                    val j2 = i2 + halfStep
                    val tReal = wr * real[j2] - wi * imag[j2]
                    val tImag = wr * imag[j2] + wi * real[j2]
                    real[j2] = real[i2] - tReal
                    imag[j2] = imag[i2] - tImag
                    real[i2] += tReal
                    imag[i2] += tImag
                    i2 += step
                }
                val newWr = wr * wReal - wi * wImag
                wi = wr * wImag + wi * wReal
                wr = newWr
            }
        }

        // Magnitude of positive frequencies
        return FloatArray(n / 2) { i ->
            sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
    }

    fun release() {
        audioRecord?.release()
        audioRecord = null
    }

    /** Flatten a list of partial chunks into one contiguous ShortArray. */
    private fun concatChunks(chunks: List<ShortArray>): ShortArray {
        val total = chunks.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, out, offset, chunk.size)
            offset += chunk.size
        }
        return out
    }
}
