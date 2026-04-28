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

        // Surface module-level errors that were previously logcat-only — the
        // user couldn't tell whether 'no signature' meant 'we listened and
        // heard silence' or 'the mic refused to open'.
        if (acoustic.state.value == AcousticFingerprint.State.ERROR) {
            appendLog("  ⚠ Acoustic module errored — check microphone access.")
        }
        if (rf.state.value == RfFingerprint.State.ERROR) {
            appendLog("  ⚠ RF module errored — check Bluetooth + Location.")
        }
        if (em.state.value == EmFingerprint.State.ERROR ||
            em.state.value == EmFingerprint.State.NO_SENSOR) {
            appendLog("  ⚠ EM module unavailable — phone may have no magnetometer.")
        }

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
        val device = _discoveredDevice.value
        if (command != null && device != null) {
            appendLog("Captured IR command: ${command.rawTimings.size} pulses, protocol: ${command.protocol}")
            control.addCommand(device.id, command)
            // Capture succeeded — device now has at least one command,
            // so READY is the right phase.
            _phase.value = Phase.READY
        } else {
            appendLog("No IR signal detected")
            // Capture failed — fall back to whatever the discovery state
            // was before we entered LEARNING_CAMERA. If we have a device
            // with no commands, it's still UNKNOWN; with commands, READY.
            val hasCommands = device?.irProfile?.commands?.isNotEmpty() == true
            _phase.value = when {
                device == null -> Phase.IDLE
                hasCommands -> Phase.READY
                else -> Phase.DEVICE_UNKNOWN
            }
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
            // Restore the pre-brute-force phase so the UI doesn't get
            // stuck on LEARNING_BRUTE forever. Same logic as
            // stopCameraLearn miss path.
            val device = _discoveredDevice.value
            val hasCommands = device?.irProfile?.commands?.isNotEmpty() == true
            _phase.value = when {
                device == null -> Phase.IDLE
                hasCommands -> Phase.READY
                else -> Phase.DEVICE_UNKNOWN
            }
        }
    }

    // ── Device Matching ───────────────────────────────────────
    // Pure logic lives in Matching.kt for testability; this class just
    // takes a thread-safe snapshot of knownSignatures and delegates.

    private fun matchKnownDevice(candidate: DeviceProfile): DeviceProfile? {
        val snapshot = synchronized(knownSignatures) { knownSignatures.toList() }
        return matchKnownDevice(candidate, snapshot, SIMILARITY_THRESHOLD)
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

    /**
     * Append to the user-visible scan log. Synchronized because the
     * read-modify-write of `_log.value = _log.value + message` races when
     * several callers append concurrently — the brute-force onSkip
     * callback runs on whatever thread the IR transmit error path uses,
     * the per-module scans run on Dispatchers.IO via async, and the
     * orchestrator's own scanPassive coroutine runs on yet another thread.
     * Without the lock, two concurrent appends can lose a message.
     */
    private val logLock = Any()
    private fun appendLog(message: String) {
        Log.d(TAG, message)
        synchronized(logLock) {
            _log.value = _log.value + message
        }
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
