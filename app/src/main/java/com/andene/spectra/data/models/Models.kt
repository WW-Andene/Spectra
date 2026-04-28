package com.andene.spectra.data.models

import java.util.UUID

/**
 * Represents a discovered device from any identification module.
 */
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    val acousticSignature: AcousticSignature? = null,
    val rfSignature: RfSignature? = null,
    val emSignature: EmSignature? = null,
    val irProfile: IrProfile? = null,
    val confidence: Float = 0f, // 0.0 - 1.0 combined confidence
    val discoveredAt: Long = System.currentTimeMillis()
)

enum class DeviceCategory {
    TV, AC, PROJECTOR, SPEAKER, LIGHT, FAN, SET_TOP_BOX, UNKNOWN
}

/**
 * Module 2 — Acoustic fingerprint data
 */
data class AcousticSignature(
    val dominantFrequencies: List<FrequencyPeak>,
    val spectralCentroid: Float,
    val harmonicPattern: FloatArray,
    val noiseFloorDb: Float,
    val rawFftSnapshot: FloatArray? = null
)

data class FrequencyPeak(
    val frequencyHz: Float,
    val magnitudeDb: Float,
    val bandwidth: Float
)

/**
 * Module 3 — RF fingerprint data
 */
data class RfSignature(
    val wifiDevices: List<WifiDeviceInfo> = emptyList(),
    val bleDevices: List<BleDeviceInfo> = emptyList(),
    val nfcTags: List<NfcTagInfo> = emptyList()
)

data class WifiDeviceInfo(
    val ssid: String?,
    val bssid: String,
    val macPrefix: String, // First 3 octets → manufacturer
    val signalStrength: Int,
    val mdnsServices: List<String> = emptyList(),
    val modelHint: String? = null
)

data class BleDeviceInfo(
    val name: String?,
    val address: String,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: ByteArray? = null,
    val rssi: Int
)

data class NfcTagInfo(
    val techList: List<String>,
    val id: ByteArray?,
    val records: List<String> = emptyList()
)

/**
 * Module 4 — EM fingerprint data
 */
data class EmSignature(
    val magnetometerPattern: FloatArray, // Dominant frequencies from mag sensor FFT
    val emiAudioFrequencies: List<FrequencyPeak>, // EMI picked up via mic
    val fieldStrength: Float, // µT magnitude
    val combinedFingerprint: FloatArray // Merged feature vector
)

/**
 * Module 1 & 5 & 6 — IR data
 */
data class IrProfile(
    val protocol: IrProtocol = IrProtocol.RAW,
    val carrierFrequency: Int = 38000, // Hz, typically 38kHz
    val commands: MutableMap<String, IrCommand> = mutableMapOf()
)

enum class IrProtocol {
    NEC, RC5, RC6, SIRC_12, SIRC_15, SIRC_20, SAMSUNG, SHARP, LG, PANASONIC, RAW, UNKNOWN
}

data class IrCommand(
    val name: String, // e.g. "power", "vol_up", "ch_1"
    val rawTimings: IntArray, // Microsecond on/off alternating pattern
    val protocol: IrProtocol = IrProtocol.RAW,
    val code: Long? = null, // Decoded command code if protocol known
    val capturedVia: CaptureMethod = CaptureMethod.MANUAL
)

enum class CaptureMethod {
    CAMERA_DECODE,  // Module 1
    BRUTE_FORCE,    // Module 5
    MANUAL,         // User entered
    LEARNED         // From another remote
}

/**
 * Brute force sweep state
 */
data class BruteForceState(
    val currentProtocol: IrProtocol = IrProtocol.NEC,
    val currentCode: Int = 0,
    val totalAttempts: Int = 0,
    val isRunning: Boolean = false,
    val foundProtocol: IrProtocol? = null,
    val foundCode: Long? = null
)

/**
 * Mid-flow brute-force checkpoint. Written to disk after every confirmed
 * "no" response so the user can resume from the same attempt index after
 * a process kill, low-memory reclaim, or accidental dismissal. Cleared
 * on successful hit or explicit cancel.
 *
 * Schema-stable: never break compatibility silently — old checkpoints
 * should still parse so a user who upgraded between versions doesn't
 * lose mid-flow state.
 */
data class BruteForceCheckpoint(
    val deviceId: String,
    val deviceName: String,
    val brandFilter: String? = null,
    /** Number of attempts already completed; the resume continues from
     *  attempt index = nextAttemptIndex. */
    val nextAttemptIndex: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
)

/**
 * A sequence of IR commands across one or more devices, e.g. "Movie night":
 *   1. Power on TV
 *   2. wait 1500ms
 *   3. Switch TV to HDMI 2
 *   4. Power on AVR
 *   5. Set AVR to AUX
 *
 * A step's deviceName is captured at creation time so a deleted/renamed
 * device still reads sensibly in the macro list — execution checks the
 * id, but the UI shows the snapshot.
 */
data class Macro(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class MacroStep(
    val deviceId: String,
    val deviceName: String,
    val commandName: String,
    val delayBeforeMs: Int = 0,
)
