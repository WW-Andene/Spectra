package com.andene.spectra.modules.bruteforce

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Acoustic auto-confirm for the brute-force IR sweep (B-101).
 *
 * When the phone is held within ~30 cm of a TV / set-top box, almost
 * every device emits an audible signal when it powers on or off:
 * speaker chime, relay click, fan starting, capacitor charging hum.
 * Listening for that with the mic lets us answer the "did the device
 * react?" question automatically — no user tap-after-each-attempt
 * needed for the bulk of a sweep.
 *
 * Approach (deliberately simple):
 *  1. Capture a 1-second baseline RMS energy at sweep start (no IR
 *     fired yet; this is the room's quiet floor).
 *  2. After each IR transmit, capture 1.5 s of audio, compute RMS.
 *  3. Compare ratio against thresholds:
 *       ratio ≥ HIGH  → CONFIRMED  (definite reaction)
 *       ratio ≤ LOW   → REJECTED   (silence; advance to next code)
 *       otherwise     → UNCERTAIN  (ask the user as before)
 *
 * Thresholds are conservative — false-positive auto-confirm is much
 * worse than missing a real reaction (we just fall back to user
 * prompt). The user always has the override either way.
 *
 * Mic permission required; without it [checkForReaction] returns
 * UNCERTAIN immediately so callers can fall through to the user
 * prompt without special-casing the no-mic path.
 */
class AcousticAutoConfirm(private val context: Context) {

    companion object {
        private const val TAG = "AcousticAutoConfirm"

        // 16 kHz mono 16-bit PCM. Voice-band sample rate, fine for
        // detecting transients in the audible range; uses ~32 KB/sec
        // so the 1-1.5 sec windows fit easily in memory.
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        private const val BASELINE_DURATION_MS = 1_000L
        private const val REACTION_DURATION_MS = 1_500L

        // Thresholds tuned for the typical near-field phone-to-TV gap.
        // 1.7x energy ratio captures a TV chime / relay click reliably
        // without firing on conversation in the next room.
        private const val CONFIRM_RATIO = 1.7f
        private const val REJECT_RATIO = 1.15f
    }

    enum class Confidence { CONFIRMED, REJECTED, UNCERTAIN }

    /** Captured during [captureBaseline] and compared against on each
     *  [checkForReaction]. -1 means baseline never captured (no mic
     *  permission, mic init failure, etc.) — every subsequent
     *  checkForReaction returns UNCERTAIN. */
    private var baselineRms: Double = -1.0

    fun isMicAvailable(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Sample the room's quiet floor before any IR is transmitted.
     * Call once when the sweep starts. Subsequent
     * [checkForReaction] calls compare against this.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun captureBaseline() = withContext(Dispatchers.IO) {
        if (!isMicAvailable()) {
            Log.d(TAG, "Mic permission not granted — auto-confirm disabled")
            baselineRms = -1.0
            return@withContext
        }
        baselineRms = recordRms(BASELINE_DURATION_MS) ?: -1.0
        Log.d(TAG, "Baseline RMS: $baselineRms")
    }

    /**
     * Listen for ~1.5 s after the most recent IR fire and decide
     * whether the device reacted audibly. Returns UNCERTAIN if the
     * baseline wasn't captured or the mic call fails — the caller
     * then falls through to the user-prompt path.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun checkForReaction(): Confidence = withContext(Dispatchers.IO) {
        if (baselineRms <= 0.0) return@withContext Confidence.UNCERTAIN
        val rms = recordRms(REACTION_DURATION_MS) ?: return@withContext Confidence.UNCERTAIN
        val ratio = (rms / baselineRms).toFloat()
        Log.d(TAG, "Reaction ratio: ${"%.2f".format(ratio)}")
        return@withContext when {
            ratio >= CONFIRM_RATIO -> Confidence.CONFIRMED
            ratio <= REJECT_RATIO -> Confidence.REJECTED
            else -> Confidence.UNCERTAIN
        }
    }

    /**
     * Open mic, capture for [durationMs], compute RMS over the whole
     * window. Returns null on init failure. Same outer / inner
     * try/finally pattern as [com.andene.spectra.modules.acoustic.AcousticFingerprint]
     * so we never leak the AudioRecord handle even if the recording
     * loop throws.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordRms(durationMs: Long): Double? {
        var record: AudioRecord? = null
        try {
            val bufferSize = maxOf(
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING),
                4096,
            )
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                ENCODING,
                bufferSize,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord init failed")
                return null
            }

            val totalSamples = (SAMPLE_RATE * durationMs / 1000L).toInt()
            val collected = ShortArray(totalSamples)
            val chunk = ShortArray(2048)
            var filled = 0

            record.startRecording()
            try {
                val deadline = System.currentTimeMillis() + durationMs + 200
                while (filled < totalSamples && System.currentTimeMillis() < deadline) {
                    val n = record.read(chunk, 0, chunk.size)
                    if (n <= 0) continue
                    val take = minOf(n, totalSamples - filled)
                    System.arraycopy(chunk, 0, collected, filled, take)
                    filled += take
                }
            } finally {
                try { record.stop() } catch (_: Exception) {}
            }

            if (filled == 0) return null
            // Ignore the first 50 ms to avoid catching the IR-blast
            // electrical noise that some phones pick up through their
            // own LED driver. 50 ms × 16 samples/ms = 800 samples.
            val skip = minOf(800, filled / 4)
            var sumSq = 0.0
            for (i in skip until filled) {
                val s = collected[i].toDouble()
                sumSq += s * s
            }
            val n = filled - skip
            return if (n > 0) sqrt(sumSq / n) else null
        } catch (e: Exception) {
            Log.w(TAG, "recordRms failed", e)
            return null
        } finally {
            try { record?.release() } catch (_: Exception) {}
        }
    }
}
