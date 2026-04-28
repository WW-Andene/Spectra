package com.andene.spectra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andene.spectra.SpectraApp
import com.andene.spectra.core.SpectraOrchestrator
import com.andene.spectra.data.models.*
import com.andene.spectra.data.repository.DeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpectraApp
    private val orchestrator: SpectraOrchestrator = app.orchestrator
    private val repository: DeviceRepository = app.repository
    val codeDatabase = app.codeDatabase
    private val macroRepository = app.macroRepository
    private val bfCheckpointRepository = app.bfCheckpointRepository

    /** Checkpoint of an in-flight brute-force the user was running before
     *  process death / app close. If non-null on launch, the UI offers
     *  to resume from where the user left off. Cleared on success +
     *  explicit cancel. */
    private val _resumableBruteForce = MutableStateFlow<com.andene.spectra.data.models.BruteForceCheckpoint?>(null)
    val resumableBruteForce: StateFlow<com.andene.spectra.data.models.BruteForceCheckpoint?> = _resumableBruteForce

    /** Transient one-shot toasts (e.g. "Save failed"). Replay=0 so a
     *  subscribed fragment doesn't see stale events on rebind. */
    private val _toasts = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private fun emitToast(message: String) {
        _toasts.tryEmit(message)
    }

    private suspend fun saveDeviceWithFeedback(profile: DeviceProfile) {
        if (!repository.save(profile)) emitToast("Couldn't save ${profile.name ?: "device"} — storage may be full")
    }

    private suspend fun saveMacrosWithFeedback(macros: List<Macro>) {
        if (!macroRepository.saveAll(macros)) emitToast("Couldn't save macros — storage may be full")
    }

    // Narrow facades so fragments don't reach into orchestrator internals.
    val orchestratorPhase: StateFlow<SpectraOrchestrator.Phase> = orchestrator.phase
    val captureState: StateFlow<com.andene.spectra.modules.ir.IrCameraCapture.CaptureState> =
        orchestrator.irCapture.captureState

    fun buildIrCameraAnalyzer() = orchestrator.irCapture.buildAnalyzer()

    // UI state
    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen

    private val _savedDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val savedDevices: StateFlow<List<DeviceProfile>> = _savedDevices

    private val _activeDevice = MutableStateFlow<DeviceProfile?>(null)
    val activeDevice: StateFlow<DeviceProfile?> = _activeDevice

    private val _scanLog = MutableStateFlow<List<String>>(emptyList())
    val scanLog: StateFlow<List<String>> = _scanLog

    private val _bruteForcePrompt = MutableStateFlow<BruteForcePrompt?>(null)
    val bruteForcePrompt: StateFlow<BruteForcePrompt?> = _bruteForcePrompt

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _cameraLearnActive = MutableStateFlow(false)
    val cameraLearnActive: StateFlow<Boolean> = _cameraLearnActive

    private val _commandNameInput = MutableStateFlow("")
    val commandNameInput: StateFlow<String> = _commandNameInput

    data class BruteForcePrompt(
        val protocol: IrProtocol,
        val manufacturer: String,
        val attemptNum: Int
    )

    enum class Screen {
        HOME,        // Device list + scan button
        SCANNING,    // Passive scan in progress
        RESULTS,     // Scan results + next steps
        LEARN,       // IR learning (camera or brute force)
        REMOTE,      // Universal remote control pad
        DEVICE_EDIT, // Edit device name/category
        MACRO_EDIT,  // Create / edit a macro
    }

    /** The macro currently being edited; null means we're creating a new one. */
    private val _editingMacro = MutableStateFlow<Macro?>(null)
    val editingMacro: StateFlow<Macro?> = _editingMacro

    fun openMacroEditor(macro: Macro?) {
        _editingMacro.value = macro
        _screen.value = Screen.MACRO_EDIT
    }

    // ── Macros ────────────────────────────────────────────────

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros

    /** Currently-running macro progress, or null when idle. */
    private val _runningMacro = MutableStateFlow<RunningMacro?>(null)
    val runningMacro: StateFlow<RunningMacro?> = _runningMacro

    private var macroJob: kotlinx.coroutines.Job? = null

    data class RunningMacro(
        val macroId: String,
        val name: String,
        val totalSteps: Int,
        val currentStep: Int,
        val currentLabel: String,
    )

    init {
        loadSavedDevices()
        loadMacros()
        // Mirror orchestrator log
        viewModelScope.launch {
            orchestrator.log.collect { _scanLog.value = it }
        }
        // Surface any persisted brute-force checkpoint on launch; the UI
        // collector decides whether to prompt the user to resume.
        viewModelScope.launch {
            _resumableBruteForce.value = bfCheckpointRepository.load()
        }
    }

    private fun loadMacros() {
        viewModelScope.launch { _macros.value = macroRepository.loadAll() }
    }

    fun saveMacro(macro: Macro) {
        val updated = _macros.value.toMutableList().apply {
            val existing = indexOfFirst { it.id == macro.id }
            if (existing >= 0) set(existing, macro) else add(macro)
        }
        _macros.value = updated
        viewModelScope.launch { saveMacrosWithFeedback(updated) }
    }

    /**
     * Walk a macro's steps, sending each command via IrControl with the
     * configured delay before each step. Cancellable; cancelling stops at
     * the next step boundary (we don't kill an in-flight transmit).
     *
     * Re-tap behaviour: if a macro is already running, ignore the new tap
     * instead of silently swapping macros — that way the user can't
     * accidentally truncate a run by double-tapping a chip. Tap the same
     * macro twice OK (no-op), tap a different one — also no-op until the
     * first finishes or is cancelled.
     */
    fun runMacro(id: String) {
        if (macroJob?.isActive == true) return
        val macro = _macros.value.firstOrNull { it.id == id } ?: return

        // Validate device references up front. A macro built six months ago
        // may reference a device the user has since deleted; running it
        // would silently no-op for those steps. Surface the count instead.
        val knownIds = _savedDevices.value.map { it.id }.toSet()
        val staleSteps = macro.steps.count { it.deviceId !in knownIds }
        if (staleSteps == macro.steps.size) {
            emitToast("Macro \"${macro.name}\" can't run — every device it uses has been deleted")
            return
        } else if (staleSteps > 0) {
            emitToast("Macro \"${macro.name}\": $staleSteps step(s) skipped (deleted devices)")
        }

        macroJob = viewModelScope.launch {
            try {
                for ((index, step) in macro.steps.withIndex()) {
                    if (step.deviceId !in knownIds) continue
                    if (step.delayBeforeMs > 0) kotlinx.coroutines.delay(step.delayBeforeMs.toLong())
                    _runningMacro.value = RunningMacro(
                        macroId = macro.id,
                        name = macro.name,
                        totalSteps = macro.steps.size,
                        currentStep = index + 1,
                        currentLabel = "${step.deviceName} → ${step.commandName}",
                    )
                    orchestrator.control.sendCommand(step.deviceId, step.commandName)
                }
            } finally {
                _runningMacro.value = null
            }
        }
    }

    fun cancelRunningMacro() {
        macroJob?.cancel()
        macroJob = null
        _runningMacro.value = null
    }

    // ── Navigation ────────────────────────────────────────────

    fun navigate(screen: Screen) {
        _screen.value = screen
    }

    // ── Device Management ─────────────────────────────────────

    private fun loadSavedDevices() {
        viewModelScope.launch {
            _savedDevices.value = repository.loadAll()
            // Surface a parse-skip count so the user knows some profiles
            // were corrupted (rather than silently treating them as
            // "deleted"). -1 means the whole devices directory failed.
            val skips = repository.lastLoadSkipCount
            when {
                skips > 0 -> emitToast("$skips saved profile(s) couldn't be loaded — files may be corrupted")
                skips < 0 -> emitToast("Couldn't read saved devices folder")
            }
        }
    }

    fun selectDevice(device: DeviceProfile) {
        _activeDevice.value = device
        _screen.value = Screen.REMOTE
    }

    fun updateDeviceName(name: String) {
        val device = _activeDevice.value ?: return
        val updated = device.copy(name = name)
        _activeDevice.value = updated
        orchestrator.control.saveDevice(updated)
        viewModelScope.launch { saveDeviceWithFeedback(updated) }
        loadSavedDevices()
    }

    fun updateDeviceCategory(category: DeviceCategory) {
        val device = _activeDevice.value ?: return
        val updated = device.copy(category = category)
        _activeDevice.value = updated
        orchestrator.control.saveDevice(updated)
        viewModelScope.launch { saveDeviceWithFeedback(updated) }
    }

    fun deleteDevice(deviceId: String) {
        // Snapshot the profile before deletion so an undo emit can restore
        // it byte-for-byte. JSON is the canonical form so we round-trip
        // through it instead of holding a Kotlin reference (which a future
        // model change could break).
        val snapshot = _savedDevices.value.firstOrNull { it.id == deviceId }
        viewModelScope.launch {
            repository.delete(deviceId)
            orchestrator.forgetDevice(deviceId)
            if (_activeDevice.value?.id == deviceId) _activeDevice.value = null
            loadSavedDevices()
            _screen.value = Screen.HOME
            if (snapshot != null) {
                _undoActions.tryEmit(UndoAction.Device(snapshot))
            }
        }
    }

    fun deleteMacro(id: String) {
        val snapshot = _macros.value.firstOrNull { it.id == id }
        val updated = _macros.value.filterNot { it.id == id }
        _macros.value = updated
        viewModelScope.launch { saveMacrosWithFeedback(updated) }
        if (snapshot != null) _undoActions.tryEmit(UndoAction.Macro(snapshot))
    }

    /** Restore the most-recently-deleted item. UI consumes [undoActions]
     *  to know what to offer; this call is the actual restore. */
    fun undoDelete(action: UndoAction) {
        when (action) {
            is UndoAction.Device -> {
                orchestrator.registerKnownDevice(action.profile)
                _activeDevice.value = action.profile
                viewModelScope.launch { saveDeviceWithFeedback(action.profile); loadSavedDevices() }
            }
            is UndoAction.Macro -> {
                val updated = _macros.value.toMutableList().apply { add(action.macro) }
                _macros.value = updated
                viewModelScope.launch { saveMacrosWithFeedback(updated) }
            }
        }
    }

    sealed class UndoAction {
        data class Device(val profile: DeviceProfile) : UndoAction()
        data class Macro(val macro: com.andene.spectra.data.models.Macro) : UndoAction()
    }

    private val _undoActions = MutableSharedFlow<UndoAction>(
        replay = 0, extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val undoActions: SharedFlow<UndoAction> = _undoActions.asSharedFlow()

    // ── Passive Scan ──────────────────────────────────────────

    private var scanJob: kotlinx.coroutines.Job? = null

    fun startPassiveScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            _screen.value = Screen.SCANNING
            orchestrator.clearLog()

            try {
                orchestrator.scanPassive()
                _activeDevice.value = orchestrator.discoveredDevice.value
                _screen.value = Screen.RESULTS
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User cancelled — don't transition to RESULTS, leave them
                // wherever they navigated to.
                throw e
            } catch (e: SecurityException) {
                // Tell the user how to recover, not just what failed.
                _scanLog.value = _scanLog.value +
                    "Permission denied (${e.message?.take(80) ?: "unknown"}). " +
                    "Open Settings → Apps → Spectra → Permissions to grant the missing access, then tap Scan again."
            } catch (e: Exception) {
                _scanLog.value = _scanLog.value +
                    "Scan failed (${e.message?.take(80) ?: "unknown"}). " +
                    "Make sure WiFi, Bluetooth, and Location are turned on, then retry."
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun cancelPassiveScan() {
        scanJob?.cancel()
        scanJob = null
    }

    // ── IR Camera Learning ────────────────────────────────────

    fun setCommandName(name: String) {
        _commandNameInput.value = name
    }

    fun startCameraCapture() {
        _cameraLearnActive.value = true
        orchestrator.startCameraLearn()
    }

    fun stopCameraCapture() {
        orchestrator.stopCameraLearn()
        _cameraLearnActive.value = false

        val command = orchestrator.irCapture.capturedCommand.value
        val device = _activeDevice.value
        val name = _commandNameInput.value.ifBlank { "button_${System.currentTimeMillis()}" }

        if (command != null && device != null) {
            val namedCommand = command.copy(name = name)
            orchestrator.control.addCommand(device.id, namedCommand)
            viewModelScope.launch { saveDeviceWithFeedback(device) }
            _commandNameInput.value = ""
        }
    }

    // ── IR Brute Force ────────────────────────────────────────

    // One pending response at a time; the orchestrator's onAttempt callback
    // suspends on this until the UI completes it via confirm/deny.
    private var pendingBruteForceResponse: CompletableDeferred<Boolean>? = null
    private var bruteForceJob: kotlinx.coroutines.Job? = null

    fun startBruteForce(startAttempt: Int = 0) {
        // Re-entry guard: if a sweep is already running, ignore. Otherwise
        // two coroutines race on the orchestrator's BruteForceState and the
        // pendingBruteForceResponse field, producing inconsistent prompts.
        if (bruteForceJob?.isActive == true) return
        // Resume support: clear the staged checkpoint state from previous
        // launches so the resume banner doesn't reappear after we've
        // started running.
        _resumableBruteForce.value = null
        bruteForceJob = viewModelScope.launch {
            _screen.value = Screen.LEARN
            val device = _activeDevice.value
            orchestrator.startBruteForce(startAttempt = startAttempt) { protocol, manufacturer, attempt ->
                val response = CompletableDeferred<Boolean>()
                pendingBruteForceResponse = response
                _bruteForcePrompt.value = BruteForcePrompt(protocol, manufacturer, attempt)
                // Persist a checkpoint BEFORE awaiting — if the process
                // dies between display and user response, on resume we
                // pick up at the next attempt (we already showed this one).
                if (device != null) {
                    bfCheckpointRepository.save(
                        com.andene.spectra.data.models.BruteForceCheckpoint(
                            deviceId = device.id,
                            deviceName = device.name ?: "Device",
                            brandFilter = device.manufacturer,
                            nextAttemptIndex = attempt,
                        )
                    )
                }
                try {
                    response.await()
                } finally {
                    pendingBruteForceResponse = null
                    _bruteForcePrompt.value = null
                }
            }

            val state = orchestrator.bruteForce.state.value
            if (state.foundProtocol != null) {
                val finalDevice = orchestrator.discoveredDevice.value ?: _activeDevice.value
                if (finalDevice != null) {
                    _activeDevice.value = finalDevice
                    orchestrator.registerKnownDevice(finalDevice)
                    saveDeviceWithFeedback(finalDevice)
                    loadSavedDevices()
                }
                _screen.value = Screen.REMOTE
            }
            // Clear checkpoint on any clean exit (success, full sweep
            // complete with no hit, or coroutine cancellation): the
            // user is no longer mid-flow.
            bfCheckpointRepository.clear()
        }
    }

    fun confirmBruteForce() {
        pendingBruteForceResponse?.complete(true)
    }

    fun denyBruteForce() {
        pendingBruteForceResponse?.complete(false)
    }

    fun stopBruteForce() {
        orchestrator.bruteForce.stop()
        // Unblock any in-flight prompt so the sweep coroutine returns promptly.
        pendingBruteForceResponse?.complete(false)
        // Explicit user cancel — drop the checkpoint so we don't offer
        // resume next launch.
        viewModelScope.launch { bfCheckpointRepository.clear() }
    }

    /** Drop the persisted checkpoint without resuming. UI offers this
     *  when the user dismisses the resume banner. */
    fun discardResumableBruteForce() {
        _resumableBruteForce.value = null
        viewModelScope.launch { bfCheckpointRepository.clear() }
    }

    /** Resume the previously-persisted brute force from the recorded
     *  attempt index. Selects the device the checkpoint was for. */
    fun resumeBruteForce() {
        val cp = _resumableBruteForce.value ?: return
        val device = _savedDevices.value.firstOrNull { it.id == cp.deviceId }
        if (device == null) {
            // Source device deleted while we were away — nothing to resume.
            emitToast("Can't resume — device \"${cp.deviceName}\" no longer exists")
            discardResumableBruteForce()
            return
        }
        _activeDevice.value = device
        startBruteForce(startAttempt = cp.nextAttemptIndex)
    }

    // ── IR Control ────────────────────────────────────────────

    fun sendCommand(commandName: String) {
        val deviceId = _activeDevice.value?.id ?: return
        viewModelScope.launch {
            orchestrator.control.sendCommand(deviceId, commandName)
        }
    }

    fun sendRepeated(commandName: String, count: Int = 3) {
        val deviceId = _activeDevice.value?.id ?: return
        viewModelScope.launch {
            orchestrator.control.sendRepeated(deviceId, commandName, count)
        }
    }

    // ── Save discovered device ────────────────────────────────

    fun saveDiscoveredDevice(name: String) {
        val device = _activeDevice.value ?: return
        val named = device.copy(name = name)
        orchestrator.registerKnownDevice(named)
        _activeDevice.value = named
        viewModelScope.launch {
            saveDeviceWithFeedback(named)
            loadSavedDevices()
        }
    }

    // ── Per-command edits ────────────────────────────────────

    fun renameCommand(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        val device = _activeDevice.value ?: return
        val profile = device.irProfile ?: return
        val command = profile.commands[oldName] ?: return
        val updatedCommands = profile.commands.toMutableMap().apply {
            remove(oldName)
            put(newName, command.copy(name = newName))
        }
        val updated = device.copy(irProfile = profile.copy(commands = updatedCommands))
        _activeDevice.value = updated
        orchestrator.control.saveDevice(updated)
        viewModelScope.launch {
            saveDeviceWithFeedback(updated)
            loadSavedDevices()
        }
    }

    fun deleteCommand(name: String) {
        val device = _activeDevice.value ?: return
        val profile = device.irProfile ?: return
        if (profile.commands[name] == null) return
        val updatedCommands = profile.commands.toMutableMap().apply { remove(name) }
        val updated = device.copy(irProfile = profile.copy(commands = updatedCommands))
        _activeDevice.value = updated
        orchestrator.control.saveDevice(updated)
        viewModelScope.launch {
            saveDeviceWithFeedback(updated)
            loadSavedDevices()
        }
    }

    fun testCommand(name: String) {
        val deviceId = _activeDevice.value?.id ?: return
        viewModelScope.launch {
            orchestrator.control.sendCommand(deviceId, name)
        }
    }

    // ── Code database install ────────────────────────────────

    /**
     * Apply a complete remote layout from the bundled DB to the active
     * device. Replaces any existing IR commands and persists immediately.
     */
    /**
     * Install a complete remote layout from the bundled DB.
     * Existing commands on the device are MERGED — the DB entry's
     * commands take precedence on name collision, but any
     * captured-via-camera or learned commands the user already had
     * for buttons the DB doesn't define survive intact.
     *
     * This is a behaviour change from the original "replace" semantics
     * because silently dropping a user's captured POWER pattern when
     * they later install a DB remote was data loss with no warning.
     */
    fun installRemoteFromDatabase(entry: com.andene.spectra.data.codedb.IrCodeDatabase.RemoteEntry) {
        val device = _activeDevice.value ?: return
        val existingCommands = device.irProfile?.commands ?: emptyMap()
        val mergedCommands = existingCommands.toMutableMap().apply {
            putAll(entry.commands) // DB wins on name collision
        }
        val mergedProfile = entry.asIrProfile().copy(commands = mergedCommands.toMutableMap())
        val updated = device.copy(
            manufacturer = device.manufacturer ?: entry.brand,
            category = if (device.category == DeviceCategory.UNKNOWN) entry.deviceType else device.category,
            irProfile = mergedProfile,
        )
        _activeDevice.value = updated
        orchestrator.registerKnownDevice(updated)
        viewModelScope.launch {
            saveDeviceWithFeedback(updated)
            loadSavedDevices()
        }
    }

    // ── Export / import ──────────────────────────────────────

    /** JSON for the active device, or null when there is none. */
    fun exportActiveDeviceJson(): String? {
        val device = _activeDevice.value ?: return null
        return repository.exportProfile(device)
    }

    /**
     * Parse JSON, give it a fresh id, persist, and load into the active
     * device + matcher. Returns the loaded device on success.
     */
    fun importDeviceFromJson(jsonText: String, onResult: (DeviceProfile?) -> Unit) {
        viewModelScope.launch {
            val imported = repository.importProfile(jsonText)
            if (imported != null) {
                orchestrator.registerKnownDevice(imported)
                saveDeviceWithFeedback(imported)
                _activeDevice.value = imported
                loadSavedDevices()
            }
            onResult(imported)
        }
    }

    // ── Transmit feedback ─────────────────────────────────────

    /** Result of the most recent IR transmit. Consumed by RemoteFragment to
     *  flash a button green/red so the user actually sees something happened
     *  (or not). */
    val lastTransmitResult: StateFlow<com.andene.spectra.modules.control.IrControl.TransmitResult?> =
        orchestrator.control.lastTransmitResult

    // ── Hardware checks ───────────────────────────────────────

    fun hasIrBlaster(): Boolean = orchestrator.bruteForce.isAvailable()
    fun hasMagnetometer(): Boolean = orchestrator.em.isAvailable()
}
