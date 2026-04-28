package com.andene.spectra.data.repository

import android.util.Log
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.data.models.Macro
import com.andene.spectra.data.models.MacroStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Library-wide backup / restore. Bundles every saved device profile plus
 * every macro into a single JSON envelope users can write to a file (via
 * SAF) and restore on a new install or new phone.
 *
 * Design notes:
 *  - The envelope holds device entries as raw JsonElement, parsed from
 *    the same per-device JSON format DeviceRepository.exportProfile
 *    already produces. That decouples the envelope from
 *    DeviceRepository's internal serializable type so future schema
 *    changes there don't break the envelope's wire format.
 *  - Macros use a dedicated [BackupMacro] / [BackupMacroStep] mirror of
 *    the model. We don't reuse MacroRepository.SerializableMacro
 *    because it's private; copying the four fields here is cheaper
 *    than visibility surgery and keeps the backup format pinned even
 *    if MacroRepository's storage representation evolves.
 *  - Import is MERGE-only: each device gets a fresh id (so re-importing
 *    an already-installed library produces duplicates, not collisions);
 *    macros keep their original id so re-importing replaces them
 *    in-place. If a macro references a device id that wasn't restored,
 *    [MainViewModel.runMacro] already filters stale steps with a
 *    user-visible warning, so a partial restore stays safe.
 */
class BackupRepository(
    private val deviceRepository: DeviceRepository,
    private val macroRepository: MacroRepository,
) {

    companion object {
        private const val TAG = "BackupRepository"
        const val APP_TAG = "Spectra"
        const val CURRENT_VERSION = 1
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class LibraryBackup(
        val version: Int = CURRENT_VERSION,
        val app: String = APP_TAG,
        val exportedAt: Long = 0L,
        val devices: List<JsonElement> = emptyList(),
        val macros: List<BackupMacro> = emptyList(),
    )

    @Serializable
    data class BackupMacro(
        val id: String,
        val name: String,
        val steps: List<BackupMacroStep> = emptyList(),
        val createdAt: Long = 0L,
    )

    @Serializable
    data class BackupMacroStep(
        val deviceId: String,
        val deviceName: String,
        val commandName: String,
        val delayBeforeMs: Int = 0,
    )

    data class ImportResult(
        val devicesImported: Int,
        val devicesSkipped: Int,
        val macrosImported: Int,
        val macrosSkipped: Int,
    )

    /** Build a JSON string holding every saved device + macro. */
    suspend fun exportLibrary(): String = withContext(Dispatchers.IO) {
        val devices = deviceRepository.loadAll()
        val macros = macroRepository.loadAll()
        val envelope = LibraryBackup(
            version = CURRENT_VERSION,
            app = APP_TAG,
            exportedAt = System.currentTimeMillis(),
            devices = devices.map { json.parseToJsonElement(deviceRepository.exportProfile(it)) },
            macros = macros.map { it.toBackupForm() },
        )
        json.encodeToString(envelope)
    }

    /**
     * Parse [jsonText] and merge its contents into the existing library.
     * Returns null when the envelope is unparseable or has the wrong
     * `app` tag — callers can show a friendly "not a Spectra backup"
     * message rather than a stack trace. A non-null result is the
     * import summary even when partially-failed (per-entry parse
     * errors are counted as skipped, not propagated).
     */
    suspend fun importLibrary(jsonText: String): ImportResult? = withContext(Dispatchers.IO) {
        val envelope = try {
            json.decodeFromString<LibraryBackup>(jsonText)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse backup envelope", e)
            return@withContext null
        }
        if (envelope.app != APP_TAG) {
            Log.w(TAG, "Backup app tag '${envelope.app}' is not '$APP_TAG'")
            return@withContext null
        }
        // Future-proofing: if a newer backup turns up on an older app
        // build, we still try to import what we can. ignoreUnknownKeys
        // covers the single-document level; per-entry failures are
        // already counted as skipped so a v2 device entry that doesn't
        // round-trip through this v1 parser just gets dropped from the
        // import rather than poisoning the whole restore.

        var devicesImported = 0
        var devicesSkipped = 0
        for (deviceElem in envelope.devices) {
            try {
                val asString = json.encodeToString(JsonElement.serializer(), deviceElem)
                val imported = deviceRepository.importProfile(asString)
                if (imported != null) {
                    val saved = deviceRepository.save(imported)
                    if (saved) devicesImported++ else devicesSkipped++
                } else {
                    devicesSkipped++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to import device entry", e)
                devicesSkipped++
            }
        }

        // Macros: merge by id. Same id replaces; new id appends.
        val existing = macroRepository.loadAll().associateBy { it.id }.toMutableMap()
        var macrosImported = 0
        var macrosSkipped = 0
        for (m in envelope.macros) {
            try {
                existing[m.id] = m.toModel()
                macrosImported++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to convert macro ${m.id}", e)
                macrosSkipped++
            }
        }
        val saved = macroRepository.saveAll(existing.values.toList())
        if (!saved) {
            // Persistence failed — count macros as skipped instead of
            // imported. The caller can surface "couldn't save" without
            // claiming success.
            macrosSkipped += macrosImported
            macrosImported = 0
        }

        ImportResult(devicesImported, devicesSkipped, macrosImported, macrosSkipped)
    }

    private fun Macro.toBackupForm() = BackupMacro(
        id = id,
        name = name,
        steps = steps.map { BackupMacroStep(it.deviceId, it.deviceName, it.commandName, it.delayBeforeMs) },
        createdAt = createdAt,
    )

    private fun BackupMacro.toModel() = Macro(
        id = id,
        name = name,
        steps = steps.map { MacroStep(it.deviceId, it.deviceName, it.commandName, it.delayBeforeMs) },
        createdAt = createdAt,
    )
}
