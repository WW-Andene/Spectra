package com.andene.spectra.data.repository

import android.content.Context
import android.util.Log
import com.andene.spectra.data.models.Macro
import com.andene.spectra.data.models.MacroStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists macros (named cross-device command sequences) to a single JSON
 * file. Single-file storage instead of per-macro because the dataset stays
 * small (typically under 20 macros) and atomic writes are easier to reason
 * about.
 */
class MacroRepository(private val context: Context) {

    companion object {
        private const val TAG = "MacroRepository"
        private const val FILE_NAME = "macros.json"
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    suspend fun loadAll(): List<Macro> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext emptyList()
            val data = json.decodeFromString<MacroFile>(file.readText())
            data.macros.map { it.toModel() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load macros", e)
            emptyList()
        }
    }

    /**
     * Replace the on-disk macro list with this one.
     * Atomic via temp-file-then-rename so a crash mid-write doesn't corrupt.
     */
    suspend fun saveAll(macros: List<Macro>) = withContext(Dispatchers.IO) {
        try {
            val data = MacroFile(macros = macros.map { it.toSerializable() })
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(data))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save macros", e)
        }
    }

    @Serializable
    private data class MacroFile(val macros: List<SerializableMacro> = emptyList())

    @Serializable
    private data class SerializableMacro(
        val id: String,
        val name: String,
        val steps: List<SerializableStep> = emptyList(),
        val createdAt: Long = 0,
    )

    @Serializable
    private data class SerializableStep(
        val deviceId: String,
        val deviceName: String,
        val commandName: String,
        val delayBeforeMs: Int = 0,
    )

    private fun Macro.toSerializable() = SerializableMacro(
        id = id,
        name = name,
        steps = steps.map {
            SerializableStep(it.deviceId, it.deviceName, it.commandName, it.delayBeforeMs)
        },
        createdAt = createdAt,
    )

    private fun SerializableMacro.toModel() = Macro(
        id = id,
        name = name,
        steps = steps.map { MacroStep(it.deviceId, it.deviceName, it.commandName, it.delayBeforeMs) },
        createdAt = createdAt,
    )
}
