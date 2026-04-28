package com.andene.spectra.data.repository

import android.content.Context
import android.util.Log
import com.andene.spectra.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists learned device profiles to local JSON storage.
 *
 * Each device gets its own file: /files/devices/{device_id}.json
 * IR command waveforms are stored as int arrays within the profile.
 *
 * This is intentionally simple file-based storage — no Room/SQLite
 * overhead for what is essentially a small collection of learned remotes.
 */
class DeviceRepository(private val context: Context) {

    companion object {
        private const val TAG = "DeviceRepository"
        private const val DEVICES_DIR = "devices"
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val devicesDir: File
        get() = File(context.filesDir, DEVICES_DIR).also { it.mkdirs() }

    /**
     * Save a device profile to disk.
     */
    suspend fun save(profile: DeviceProfile) = withContext(Dispatchers.IO) {
        try {
            val serializable = profile.toSerializable()
            val jsonStr = json.encodeToString(serializable)
            File(devicesDir, "${profile.id}.json").writeText(jsonStr)
            Log.d(TAG, "Saved device ${profile.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save device ${profile.id}", e)
        }
    }

    /**
     * Load all saved device profiles.
     */
    suspend fun loadAll(): List<DeviceProfile> = withContext(Dispatchers.IO) {
        try {
            devicesDir.listFiles { f -> f.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        val data = json.decodeFromString<SerializableDeviceProfile>(file.readText())
                        data.toModel()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load ${file.name}", e)
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load devices", e)
            emptyList()
        }
    }

    /**
     * Load a single device by ID.
     */
    suspend fun load(deviceId: String): DeviceProfile? = withContext(Dispatchers.IO) {
        try {
            val file = File(devicesDir, "$deviceId.json")
            if (!file.exists()) return@withContext null
            val data = json.decodeFromString<SerializableDeviceProfile>(file.readText())
            data.toModel()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load device $deviceId", e)
            null
        }
    }

    /**
     * Delete a device profile.
     */
    suspend fun delete(deviceId: String) = withContext(Dispatchers.IO) {
        File(devicesDir, "$deviceId.json").delete()
    }

    // ── Serializable DTOs ─────────────────────────────────────
    // DeviceProfile uses FloatArray/IntArray which need explicit serialization

    @Serializable
    data class SerializableDeviceProfile(
        val id: String,
        val name: String? = null,
        val manufacturer: String? = null,
        val model: String? = null,
        val category: String = "UNKNOWN",
        val irCommands: Map<String, SerializableIrCommand> = emptyMap(),
        val carrierFrequency: Int = 38000,
        val protocol: String = "RAW",
        val discoveredAt: Long = 0
    )

    @Serializable
    data class SerializableIrCommand(
        val name: String,
        val rawTimings: List<Int>,
        val protocol: String = "RAW",
        val code: Long? = null,
        val capturedVia: String = "MANUAL"
    )

    private fun DeviceProfile.toSerializable() = SerializableDeviceProfile(
        id = id,
        name = name,
        manufacturer = manufacturer,
        model = model,
        category = category.name,
        irCommands = irProfile?.commands?.mapValues { (_, cmd) ->
            SerializableIrCommand(
                name = cmd.name,
                rawTimings = cmd.rawTimings.toList(),
                protocol = cmd.protocol.name,
                code = cmd.code,
                capturedVia = cmd.capturedVia.name
            )
        } ?: emptyMap(),
        carrierFrequency = irProfile?.carrierFrequency ?: 38000,
        protocol = irProfile?.protocol?.name ?: "RAW",
        discoveredAt = discoveredAt
    )

    private fun SerializableDeviceProfile.toModel() = DeviceProfile(
        id = id,
        name = name,
        manufacturer = manufacturer,
        model = model,
        category = try { DeviceCategory.valueOf(category) } catch (_: Exception) { DeviceCategory.UNKNOWN },
        irProfile = IrProfile(
            protocol = try { IrProtocol.valueOf(protocol) } catch (_: Exception) { IrProtocol.RAW },
            carrierFrequency = carrierFrequency,
            commands = irCommands.mapValues { (_, cmd) ->
                IrCommand(
                    name = cmd.name,
                    rawTimings = cmd.rawTimings.toIntArray(),
                    protocol = try { IrProtocol.valueOf(cmd.protocol) } catch (_: Exception) { IrProtocol.RAW },
                    code = cmd.code,
                    capturedVia = try { CaptureMethod.valueOf(cmd.capturedVia) } catch (_: Exception) { CaptureMethod.MANUAL }
                )
            }.toMutableMap()
        ),
        discoveredAt = discoveredAt
    )
}
