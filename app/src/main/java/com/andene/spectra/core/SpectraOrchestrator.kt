package com.andene.spectra.core

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.andene.spectra.data.models.*
import com.andene.spectra.modules.acoustic.AcousticFingerprint
import com.andene.spectra.modules.bruteforce.IrBruteForce
import com.andene.spectra.modules.control.IrControl
import com.andene.spectra.modules.em.EmFingerprint
import com.andene.spectra.modules.ir.IrCameraCapture
import com.andene.spectra.modules.rf.RfFingerprint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Spectra Orchestrator
 *
 * Coordinates all 6 modules into a unified device discovery + control pipeline:
 *
 * ┌─────────────────────────────────────────────┐
 * │           PASSIVE IDENTIFICATION            │
 * │  ┌──────────┐ ┌──────────┐ ┌──────────┐    │
 * │  │ Acoustic │ │    RF    │ │    EM    │    │
 * │  │ Module 2 │ │ Module 3 │ │ Module 4 │    │
 * │  └─────┬────┘ └─────┬────┘ └─────┬────┘    │
 * │        └─────────┬───┴────────────┘          │
 * │                  ▼                           │
 * │         Device Identification                │
 * │         (cross-reference all 3)              │
 * └──────────────────┬──────────────────────────┘
 *                    ▼
 * ┌─────────────────────────────────────────────┐
 * │            ACTIVE ACQUISITION               │
 * │  ┌──────────────┐    ┌──────────────┐       │
 * │  │ IR Camera    │ OR │ IR Brute     │       │
 * │  │ Module 1     │    │ Module 5     │       │
 * │  └──────┬───────┘    └──────┬───────┘       │
 * │         └────────┬──────────┘               │
 * │                  ▼                           │
 * │         IR Commands Learned                  │
 * └──────────────────┬──────────────────────────┘
 *                    ▼
 * ┌─────────────────────────────────────────────┐
 * │              CONTROL                        │
 * │  ┌──────────────┐                           │
 * │  │ IR Control   │ → Replay commands         │
 * │  │ Module 6     │ → Universal remote UI     │
 * │  └──────────────┘                           │
 * └─────────────────────────────────────────────┘
 */
class SpectraOrchestrator(private val context: Context) {

    companion object {
        private const val TAG = "Spectra"
        private const val SIMILARITY_THRESHOLD = 0.75f
    }

    // Modules
    val irCapture = IrCameraCapture(context)
    val acoustic = AcousticFingerprint()
    val rf = RfFingerprint(context)
    val em = EmFingerprint(context)
    val bruteForce = IrBruteForce(context)
    val control = IrControl(context)

    // Orchestrator state
    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase

    private val _discoveredDevice = MutableStateFlow<DeviceProfile?>(null)
    val discoveredDevice: StateFlow<DeviceProfile?> = _discoveredDevice

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    // Known device signature database (grows as user identifies devices)
    private val knownSignatures = mutableListOf<DeviceProfile>()

    enum class Phase {
        IDLE,
        SCANNING_PASSIVE,    // Modules 2+3+4 running
        DEVICE_IDENTIFIED,   // Match found in known signatures
        DEVICE_UNKNOWN,      // New device, needs IR learning
        LEARNING_CAMERA,     // Module 1 active
        LEARNING_BRUTE,      // Module 5 active
        READY,               // Device has IR commands, can control
        ERROR
    }

    // ── Main Discovery Pipeline ───────────────────────────────

    /**
     * Phase 1: Run all passive identification modules concurrently.
     * Phone should be held near target device.
     */
    @RequiresPermission(allOf = [
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_WIFI_STATE
    ])
    suspend fun scanPassive() = withContext(Dispatchers.IO) {
        _phase.value = Phase.SCANNING_PASSIVE
        appendLog("Starting passive scan — hold phone near device")

        // Run all three fingerprinting modules concurrently
        val acousticJob = async {
            appendLog("Module 2: Listening for acoustic signature...")
            acoustic.capture()
        }
        val rfJob = async {
            appendLog("Module 3: Scanning WiFi/BLE/mDNS...")
            rf.scan()
        }
        val emJob = async {
            appendLog("Module 4: Reading EM field pattern...")
            em.capture()
        }

        val acousticSig = acousticJob.await()
        val rfSig = rfJob.await()
        val emSig = emJob.await()

        appendLog("Passive scan complete")
        appendLog("  Acoustic: ${acousticSig?.dominantFrequencies?.size ?: 0} frequency peaks")
        appendLog("  RF: ${rfSig.wifiDevices.size} WiFi, ${rfSig.bleDevices.size} BLE devices")
        appendLog("  EM: field strength ${emSig?.fieldStrength ?: 0f} µT")

        // Build candidate profile
        val candidate = DeviceProfile(
            acousticSignature = acousticSig,
            rfSignature = rfSig,
            emSignature = emSig
        )

        // Try to match against known devices
        val match = matchKnownDevice(candidate)

        if (match != null) {
            appendLog("Device identified: ${match.name ?: match.manufacturer ?: "Unknown"}")
            _discoveredDevice.value = match
            _phase.value = if (match.irProfile?.commands?.isNotEmpty() == true) {
                Phase.READY
            } else {
                Phase.DEVICE_IDENTIFIED
            }
        } else {
            appendLog("New device — not in known database")
            _discoveredDevice.value = candidate
            _phase.value = Phase.DEVICE_UNKNOWN
        }
    }

    /**
     * Phase 2a: Learn IR commands via camera capture.
     * User points existing remote at phone camera.
     */
    fun startCameraLearn() {
        _phase.value = Phase.LEARNING_CAMERA
        appendLog("Module 1: Point remote at front camera and press buttons")
        irCapture.startCapture()
    }

    fun stopCameraLearn() {
        irCapture.stopCapture()
        val command = irCapture.capturedCommand.value
        if (command != null) {
            appendLog("Captured IR command: ${command.rawTimings.size} pulses, protocol: ${command.protocol}")
            val device = _discoveredDevice.value ?: return
            control.addCommand(device.id, command)
        } else {
            appendLog("No IR signal detected")
        }
    }

    /**
     * Phase 2b: Discover IR protocol via brute force sweep.
     * No existing remote needed.
     */
    suspend fun startBruteForce(
        onAttempt: suspend (protocol: IrProtocol, manufacturer: String, attemptNum: Int) -> Boolean
    ) {
        _phase.value = Phase.LEARNING_BRUTE
        appendLog("Module 5: Starting brute force sweep...")

        bruteForce.startSweep { protocol, manufacturer, attempt ->
            appendLog("  Trying $manufacturer ($protocol) — attempt #$attempt")
            onAttempt(protocol, manufacturer, attempt)
        }

        val state = bruteForce.state.value
        if (state.foundProtocol != null) {
            appendLog("Protocol found: ${state.foundProtocol}")
            _phase.value = Phase.READY
        } else {
            appendLog("No protocol matched — device may not use standard IR")
        }
    }

    // ── Device Matching ───────────────────────────────────────

    /**
     * Compare candidate against known device signatures.
     * Uses weighted combination of all three fingerprint channels.
     */
    private fun matchKnownDevice(candidate: DeviceProfile): DeviceProfile? {
        var bestMatch: DeviceProfile? = null
        var bestScore = 0f

        val snapshot = synchronized(knownSignatures) { knownSignatures.toList() }
        for (known in snapshot) {
            var score = 0f
            var weights = 0f

            // EM similarity (strongest signal for identification)
            if (candidate.emSignature != null && known.emSignature != null) {
                score += em.compareTo(candidate.emSignature, known.emSignature) * 3f
                weights += 3f
            }

            // Acoustic similarity
            if (candidate.acousticSignature != null && known.acousticSignature != null) {
                score += compareAcoustic(candidate.acousticSignature, known.acousticSignature) * 2f
                weights += 2f
            }

            // RF match (exact MAC/name match is very strong)
            if (candidate.rfSignature != null && known.rfSignature != null) {
                score += compareRf(candidate.rfSignature, known.rfSignature) * 4f
                weights += 4f
            }

            val normalized = if (weights > 0) score / weights else 0f

            if (normalized > bestScore) {
                bestScore = normalized
                bestMatch = known
            }
        }

        return if (bestScore >= SIMILARITY_THRESHOLD) {
            bestMatch?.copy(confidence = bestScore)
        } else null
    }

    private fun compareAcoustic(a: AcousticSignature, b: AcousticSignature): Float {
        // Compare dominant frequencies — how many peaks match within tolerance
        if (a.dominantFrequencies.isEmpty() || b.dominantFrequencies.isEmpty()) return 0f

        val toleranceHz = 20f
        var matches = 0

        for (peakA in a.dominantFrequencies.take(10)) {
            for (peakB in b.dominantFrequencies.take(10)) {
                if (kotlin.math.abs(peakA.frequencyHz - peakB.frequencyHz) < toleranceHz) {
                    matches++
                    break
                }
            }
        }

        return matches.toFloat() / minOf(a.dominantFrequencies.size, b.dominantFrequencies.size, 10)
    }

    private fun compareRf(a: RfSignature, b: RfSignature): Float {
        // WiFi BSSID exact match is definitive
        val commonBssids = a.wifiDevices.map { it.bssid }.intersect(b.wifiDevices.map { it.bssid }.toSet())
        if (commonBssids.isNotEmpty()) return 1f

        // BLE address match
        val commonBle = a.bleDevices.map { it.address }.intersect(b.bleDevices.map { it.address }.toSet())
        if (commonBle.isNotEmpty()) return 0.9f

        // Manufacturer match (weaker)
        val aManufacturers = a.wifiDevices.mapNotNull { it.modelHint }.toSet()
        val bManufacturers = b.wifiDevices.mapNotNull { it.modelHint }.toSet()
        if (aManufacturers.intersect(bManufacturers).isNotEmpty()) return 0.4f

        return 0f
    }

    // ── Persistence Hooks ─────────────────────────────────────

    fun registerKnownDevice(profile: DeviceProfile) {
        synchronized(knownSignatures) {
            knownSignatures.removeAll { it.id == profile.id }
            knownSignatures.add(profile)
        }
        control.saveDevice(profile)
    }

    /**
     * Seed the matcher with previously saved devices.
     * Call once on app startup, after the repository has loaded from disk.
     */
    fun loadKnownDevices(profiles: List<DeviceProfile>) {
        synchronized(knownSignatures) {
            knownSignatures.clear()
            knownSignatures.addAll(profiles)
        }
        profiles.forEach { control.saveDevice(it) }
        appendLog("Loaded ${profiles.size} known device(s)")
    }

    fun forgetDevice(deviceId: String) {
        synchronized(knownSignatures) {
            knownSignatures.removeAll { it.id == deviceId }
        }
        control.removeDevice(deviceId)
    }

    // ── Utilities ─────────────────────────────────────────────

    private fun appendLog(message: String) {
        Log.d(TAG, message)
        _log.value = _log.value + message
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun release() {
        irCapture.release()
        acoustic.release()
        em.release()
    }
}
