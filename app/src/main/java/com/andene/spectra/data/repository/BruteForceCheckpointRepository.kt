package com.andene.spectra.data.repository

import android.content.Context
import android.util.Log
import com.andene.spectra.data.models.BruteForceCheckpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists a single in-flight brute-force checkpoint so the user can
 * resume after a process kill or accidental dismissal.
 *
 * Single-slot: only the most recent in-flight sweep is stored. Starting
 * a new sweep before resolving the prior one overwrites it. Successful
 * hits + explicit cancels both [clear].
 *
 * Atomic via temp-file-then-rename so a crash between save() steps
 * leaves either the previous-good or the new state on disk, never
 * a partial.
 */
class BruteForceCheckpointRepository(private val context: Context) {

    companion object {
        private const val TAG = "BFCheckpointRepo"
        private const val FILE_NAME = "bruteforce_checkpoint.json"

        /** Checkpoints older than this are considered stale at app start
         *  and quietly cleared — a brute-force prompt left dangling for
         *  weeks isn't useful to resume. */
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000  // 24 hours
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /**
     * Write or replace the current checkpoint. Truly atomic via
     * java.nio.file.Files.move(ATOMIC_MOVE, REPLACE_EXISTING) — the
     * old delete-then-rename had a window where a process kill would
     * leave NEITHER file readable, losing the previous-good state too.
     */
    suspend fun save(checkpoint: BruteForceCheckpoint) = withContext(Dispatchers.IO) {
        try {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(checkpoint.toSerializable()))
            try {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                // Filesystem rejects atomic move (rare — exotic FUSE mounts).
                // Fall back to the old behaviour for those edge cases.
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save BF checkpoint", e)
        }
    }

    /**
     * Load the persisted checkpoint, if any. Returns null if absent,
     * unparseable, or older than [MAX_AGE_MS].
     */
    suspend fun load(): BruteForceCheckpoint? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            val data = json.decodeFromString<Serializable>(file.readText())
            val cp = data.toModel()
            if (System.currentTimeMillis() - cp.startedAt > MAX_AGE_MS) {
                Log.d(TAG, "BF checkpoint stale, dropping")
                file.delete()
                null
            } else cp
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load BF checkpoint, removing corrupt file", e)
            try { file.delete() } catch (_: Exception) {}
            null
        }
    }

    /** Drop the persisted checkpoint (call on success / cancel). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear BF checkpoint", e)
        }
    }

    @kotlinx.serialization.Serializable
    private data class Serializable(
        val deviceId: String,
        val deviceName: String,
        val brandFilter: String? = null,
        val nextAttemptIndex: Int = 0,
        val startedAt: Long = 0,
    ) {
        fun toModel() = BruteForceCheckpoint(
            deviceId = deviceId,
            deviceName = deviceName,
            brandFilter = brandFilter,
            nextAttemptIndex = nextAttemptIndex,
            startedAt = startedAt,
        )
    }

    private fun BruteForceCheckpoint.toSerializable() = Serializable(
        deviceId = deviceId,
        deviceName = deviceName,
        brandFilter = brandFilter,
        nextAttemptIndex = nextAttemptIndex,
        startedAt = startedAt,
    )
}
