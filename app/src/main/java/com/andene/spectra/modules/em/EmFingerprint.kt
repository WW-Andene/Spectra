package com.andene.spectra.modules.em

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.andene.spectra.data.models.EmSignature
import com.andene.spectra.data.models.FrequencyPeak
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

/**
 * Module 4 — EM Fingerprint
 *
 * Dual-channel electromagnetic fingerprinting:
 *
 * Channel A — Magnetometer:
 *   Phone's magnetic field sensor detects EM fields from nearby electronics.
 *   Transformers, coils, motors produce oscillating fields at characteristic
 *   frequencies. FFT on magnetometer data reveals these.
 *
 * Channel B — EMI Audio:
 *   When you bring a phone near electronics, the mic picks up electromagnetic
 *   interference as audible buzzing/whining. This is the same effect you hear
 *   when a phone is near a speaker. FFT on this audio captures the EM leakage
 *   spectrum — unique per device model.
 *
 * Combined: Both channels are merged into a single feature vector for
 * device identification.
 */
class EmFingerprint(private val context: Context) {

    companion object {
        private const val TAG = "EmFingerprint"
        private const val MAG_SAMPLE_DURATION_MS = 3000L
        private const val MAG_SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val FFT_SIZE = 2048
        private const val MAX_PEAKS = 15
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _signature = MutableStateFlow<EmSignature?>(null)
    val signature: StateFlow<EmSignature?> = _signature

    // Raw magnetometer samples: [timestamp_ns, x, y, z]
    private val magSamples = mutableListOf<FloatArray>()

    enum class State {
        IDLE, CAPTURING, ANALYZING, COMPLETE, ERROR, NO_SENSOR
    }

    /**
     * Check if magnetometer is available on this device.
     */
    fun isAvailable(): Boolean = magnetometer != null

    /**
     * Capture EM signature using both magnetometer and EMI audio simultaneously.
     * Phone should be held 1-10cm from target device.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun capture(): EmSignature? = withContext(Dispatchers.IO) {
        if (magnetometer == null) {
            _state.value = State.NO_SENSOR
            return@withContext null
        }

        try {
            _state.value = State.CAPTURING
            magSamples.clear()

            // Run both captures concurrently
            val magJob = async { captureMagnetometer() }
            val emiJob = async { captureEmiAudio() }

            val magData = magJob.await()
            val emiPeaks = emiJob.await()

            _state.value = State.ANALYZING

            // Analyze magnetometer FFT
            val magPattern = analyzeMagnetometer(magData)

            // Compute average field strength
            val avgFieldStrength = if (magData.isNotEmpty()) {
                magData.map { sqrt(it[1] * it[1] + it[2] * it[2] + it[3] * it[3]) }.average().toFloat()
            } else 0f

            // Combine into unified fingerprint
            val combined = buildCombinedFingerprint(magPattern, emiPeaks)

            val sig = EmSignature(
                magnetometerPattern = magPattern,
                emiAudioFrequencies = emiPeaks,
                fieldStrength = avgFieldStrength,
                combinedFingerprint = combined
            )

            _signature.value = sig
            _state.value = State.COMPLETE
            sig
        } catch (e: Exception) {
            Log.e(TAG, "EM capture failed", e)
            _state.value = State.ERROR
            null
        }
    }

    /**
     * Channel A — Record magnetometer data at max sample rate.
     */
    private suspend fun captureMagnetometer(): List<FloatArray> {
        val collected = mutableListOf<FloatArray>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    synchronized(collected) {
                        collected.add(
                            floatArrayOf(
                                event.timestamp.toFloat(),
                                event.values[0], // µT X
                                event.values[1], // µT Y
                                event.values[2]  // µT Z
                            )
                        )
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, magnetometer, MAG_SENSOR_DELAY)
        delay(MAG_SAMPLE_DURATION_MS)
        sensorManager.unregisterListener(listener)

        return collected
    }

    /**
     * Channel B — Record audio to capture EMI leakage.
     * Electromagnetic interference from nearby electronics induces
     * currents in the mic circuit, producing characteristic audio.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun captureEmiAudio(): List<FrequencyPeak> {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            FFT_SIZE * 2
        )

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val allSamples = mutableListOf<Short>()
        val buffer = ShortArray(FFT_SIZE)

        recorder.startRecording()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < MAG_SAMPLE_DURATION_MS) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) allSamples.addAll(buffer.take(read))
        }

        recorder.stop()
        recorder.release()

        return extractPeaks(allSamples.toShortArray(), AUDIO_SAMPLE_RATE)
    }

    /**
     * FFT on magnetometer magnitude signal to find EM oscillation frequencies.
     * Returns dominant frequency bins as a float array.
     */
    private fun analyzeMagnetometer(samples: List<FloatArray>): FloatArray {
        if (samples.size < FFT_SIZE) return floatArrayOf()

        // Compute magnitude time series: sqrt(x² + y² + z²)
        val magnitudes = samples.map { s ->
            sqrt(s[1] * s[1] + s[2] * s[2] + s[3] * s[3])
        }

        // Estimate sample rate from timestamps
        val dtNanos = if (samples.size > 1) {
            (samples.last()[0] - samples.first()[0]) / (samples.size - 1)
        } else 1f
        val sampleRateHz = 1_000_000_000f / dtNanos

        // Take last FFT_SIZE samples, remove DC (subtract mean)
        val window = magnitudes.takeLast(FFT_SIZE)
        val mean = window.average().toFloat()
        val centered = window.map { it - mean }

        // Simple DFT (magnetometer rate is low enough for this)
        val spectrum = FloatArray(FFT_SIZE / 2)
        for (k in spectrum.indices) {
            var re = 0f
            var im = 0f
            for (n in centered.indices) {
                val angle = 2f * PI.toFloat() * k * n / FFT_SIZE
                re += centered[n] * cos(angle)
                im -= centered[n] * sin(angle)
            }
            spectrum[k] = sqrt(re * re + im * im)
        }

        // Normalize and return top frequency magnitudes
        val max = spectrum.max()
        return if (max > 0) {
            spectrum.map { it / max }.toFloatArray()
        } else spectrum
    }

    /**
     * Extract frequency peaks from audio samples via FFT.
     */
    private fun extractPeaks(samples: ShortArray, sampleRate: Int): List<FrequencyPeak> {
        if (samples.size < FFT_SIZE) return emptyList()

        // Use last FFT_SIZE samples with Hann window
        val offset = samples.size - FFT_SIZE
        val real = FloatArray(FFT_SIZE) { i ->
            val hann = 0.5f * (1f - cos(2f * PI.toFloat() * i / (FFT_SIZE - 1)))
            (samples[offset + i].toFloat() / Short.MAX_VALUE) * hann
        }
        val imag = FloatArray(FFT_SIZE)

        // In-place FFT (same as AcousticFingerprint implementation)
        fftInPlace(real, imag)

        val magnitudes = FloatArray(FFT_SIZE / 2) { i ->
            sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        val freqRes = sampleRate.toFloat() / FFT_SIZE
        val peaks = mutableListOf<FrequencyPeak>()

        for (i in 2 until magnitudes.size - 2) {
            if (magnitudes[i] > magnitudes[i - 1] &&
                magnitudes[i] > magnitudes[i + 1] &&
                magnitudes[i] > magnitudes[i - 2] &&
                magnitudes[i] > magnitudes[i + 2]
            ) {
                val db = 20f * log10(magnitudes[i].coerceAtLeast(1e-10f))
                if (db > -50f) {
                    peaks.add(FrequencyPeak(i * freqRes, db, freqRes))
                }
            }
        }

        return peaks.sortedByDescending { it.magnitudeDb }.take(MAX_PEAKS)
    }

    /**
     * Merge magnetometer and EMI audio features into one vector.
     * Used for device matching via cosine similarity or distance.
     */
    private fun buildCombinedFingerprint(
        magPattern: FloatArray,
        emiPeaks: List<FrequencyPeak>
    ): FloatArray {
        // Take top 32 magnetometer bins
        val magFeatures = magPattern.take(32).toFloatArray()

        // Encode EMI peaks as [freq, magnitude] pairs, padded to fixed size
        val emiFeatures = FloatArray(MAX_PEAKS * 2)
        emiPeaks.take(MAX_PEAKS).forEachIndexed { i, peak ->
            emiFeatures[i * 2] = peak.frequencyHz / AUDIO_SAMPLE_RATE // Normalized freq
            emiFeatures[i * 2 + 1] = (peak.magnitudeDb + 100f) / 100f // Normalize to ~0-1
        }

        return magFeatures + emiFeatures
    }

    /**
     * Radix-2 in-place FFT.
     */
    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var t = real[i]; real[i] = real[j]; real[j] = t
                t = imag[i]; imag[i] = imag[j]; imag[j] = t
            }
            var m = n / 2
            while (m >= 1 && j >= m) { j -= m; m /= 2 }
            j += m
        }

        var step = 1
        while (step < n) {
            val halfStep = step; step *= 2
            val wR = cos(PI / halfStep).toFloat()
            val wI = -sin(PI / halfStep).toFloat()
            var wr = 1f; var wi = 0f
            for (k in 0 until halfStep) {
                var i2 = k
                while (i2 < n) {
                    val j2 = i2 + halfStep
                    val tR = wr * real[j2] - wi * imag[j2]
                    val tI = wr * imag[j2] + wi * real[j2]
                    real[j2] = real[i2] - tR; imag[j2] = imag[i2] - tI
                    real[i2] += tR; imag[i2] += tI
                    i2 += step
                }
                val nw = wr * wR - wi * wI; wi = wr * wI + wi * wR; wr = nw
            }
        }
    }

    fun release() {
        // Nothing persistent to release; sensor listeners are unregistered after capture
    }
}
