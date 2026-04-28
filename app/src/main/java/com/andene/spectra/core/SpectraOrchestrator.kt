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

        // Build candidate profile, with a best-effort manufacturer/category
        // guess from the RF + mDNS hints that the RF module already produced.
        val (inferredManufacturer, inferredCategory) = inferIdentity(rfSig)
        val candidate = DeviceProfile(
            manufacturer = inferredManufacturer,
            category = inferredCategory,
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
        // If we already inferred the brand from RF, use it to put matching
        // codes at the front of the sweep — most users will get a hit in
        // the first 3–5 tries instead of grinding through 30+.
        val brandHint = _discoveredDevice.value?.manufacturer
        if (brandHint != null) {
            appendLog("Module 5: Starting brute force sweep (brand hint: $brandHint)")
        } else {
            appendLog("Module 5: Starting brute force sweep...")
        }

        bruteForce.startSweep(
            brandFilter = brandHint,
            onSkip = { protocol, manufacturer, reason ->
                // Surface transmit-time skips into the user-visible scan log
                // — silent skips were hiding hardware-reject failures.
                appendLog("  ⚠ Skipped $manufacturer ($protocol): $reason")
            },
        ) { protocol, manufacturer, attempt ->
            appendLog("  Trying $manufacturer ($protocol) — attempt #$attempt")
            onAttempt(protocol, manufacturer, attempt)
        }

        val state = bruteForce.state.value
        val pattern = bruteForce.lastFoundPattern
        if (state.foundProtocol != null && pattern != null) {
            appendLog("Protocol found: ${state.foundProtocol}")
            // Persist the working pattern as a real power command so the
            // user can immediately replay it from the remote screen.
            val device = _discoveredDevice.value
            if (device != null) {
                val powerCommand = IrCommand(
                    name = IrControl.Commands.POWER,
                    rawTimings = pattern,
                    protocol = state.foundProtocol!!,
                    capturedVia = CaptureMethod.BRUTE_FORCE,
                )
                val updatedProfile = (device.irProfile ?: IrProfile()).let { existing ->
                    existing.copy(
                        protocol = state.foundProtocol!!,
                        carrierFrequency = bruteForce.lastFoundCarrier,
                        commands = existing.commands.toMutableMap().apply {
                            put(powerCommand.name, powerCommand)
                        },
                    )
                }
                val updated = device.copy(
                    irProfile = updatedProfile,
                    manufacturer = device.manufacturer ?: bruteForce.lastFoundManufacturer,
                )
                _discoveredDevice.value = updated
                control.saveDevice(updated)
            }
            _phase.value = Phase.READY
        } else if (state.foundProtocol != null) {
            // Hit was reported but the pattern wasn't captured — log only.
            appendLog("Protocol found: ${state.foundProtocol} (pattern not captured)")
            _phase.value = Phase.READY
        } else {
            appendLog("No protocol matched — device may not use standard IR")
        }
    }

    // ── Device Matching ───────────────────────────────────────

    /**
     * Compare candidate against known device signatures.
     *
     * RF is the only reliable cross-session anchor: a BSSID or BLE address
     * match identifies a specific physical radio. Acoustic and EM fingerprints
     * shift too much with device state, distance, and ambient noise to be
     * trusted as primary identifiers — they're captured for display but not
     * weighted into the match decision here.
     */
    private fun matchKnownDevice(candidate: DeviceProfile): DeviceProfile? {
        val candidateRf = candidate.rfSignature ?: return null

        var bestMatch: DeviceProfile? = null
        var bestScore = 0f

        val snapshot = synchronized(knownSignatures) { knownSignatures.toList() }
        for (known in snapshot) {
            val knownRf = known.rfSignature ?: continue
            val score = compareRf(candidateRf, knownRf)
            if (score > bestScore) {
                bestScore = score
                bestMatch = known
            }
        }

        return if (bestScore >= SIMILARITY_THRESHOLD) {
            bestMatch?.copy(confidence = bestScore)
        } else null
    }

    /**
     * Best-effort manufacturer + category guess from the strongest RF hits.
     * Prefers WiFi (closest signal first) over BLE; mDNS hints map to category.
     * Returns nulls/UNKNOWN when the signal is ambiguous.
     */
    private fun inferIdentity(rf: RfSignature): Pair<String?, DeviceCategory> {
        val topWifi = rf.wifiDevices.firstOrNull { !it.modelHint.isNullOrBlank() }
        val topBle = rf.bleDevices.firstOrNull { !it.name.isNullOrBlank() }

        val manufacturer = topWifi?.modelHint
            ?: extractManufacturerFromBleName(topBle?.name)

        val category = guessCategory(rf)
        return manufacturer to category
    }

    private fun extractManufacturerFromBleName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val lower = name.lowercase()
        return when {
            "samsung" in lower -> "Samsung"
            "lg" in lower -> "LG Electronics"
            "sony" in lower -> "Sony"
            "roku" in lower -> "Roku"
            "sonos" in lower -> "Sonos"
            "bose" in lower -> "Bose"
            "apple" in lower || "airpods" in lower -> "Apple"
            "xiaomi" in lower || lower.startsWith("mi ") -> "Xiaomi"
            else -> null
        }
    }

    private fun guessCategory(rf: RfSignature): DeviceCategory {
        // mDNS service hints are the most reliable categoriser.
        val mdnsHints = rf.wifiDevices.mapNotNull { it.modelHint?.lowercase() }
        if (mdnsHints.any { "chromecast" in it || "android tv" in it || "fire tv" in it }) {
            return DeviceCategory.SET_TOP_BOX
        }
        if (mdnsHints.any { "airplay" in it || "tv" in it }) {
            return DeviceCategory.TV
        }
        if (mdnsHints.any { "spotify" in it || "sonos" in it || "airplay audio" in it }) {
            return DeviceCategory.SPEAKER
        }

        // BLE device names occasionally tell us directly.
        val bleNames = rf.bleDevices.mapNotNull { it.name?.lowercase() }
        if (bleNames.any { "tv" in it || "soundbar" in it && "tv" in it }) return DeviceCategory.TV
        if (bleNames.any { "speaker" in it || "soundbar" in it || "audio" in it }) {
            return DeviceCategory.SPEAKER
        }

        return DeviceCategory.UNKNOWN
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
