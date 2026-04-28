package com.andene.spectra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andene.spectra.SpectraApp
import com.andene.spectra.core.SpectraOrchestrator
import com.andene.spectra.data.models.*
import com.andene.spectra.data.repository.BackupRepository
import com.andene.spectra.data.repository.DeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Cast safely so a non-Spectra Application (Robolectric default,
    // future test harness, etc.) gives a clear error instead of a
    // ClassCastException buried in Android's ViewModelProvider stack.
    private val app: SpectraApp = (application as? SpectraApp)
        ?: error(
            "MainViewModel requires SpectraApp as the Application. " +
                "Got ${application.javaClass.name}. If you're testing, run " +
                "your test under @Config(application = SpectraApp::class)."
        )
    private val orchestrator: SpectraOrchestrator = app.orchestrator
    private val repository: DeviceRepository = app.repository
    val codeDatabase = app.codeDatabase
    private val macroRepository = app.macroRepository
    private val bfCheckpointRepository = app.bfCheckpointRepository
    private val backupRepository = app.backupRepository

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

    /** Quality summary for the most recent successful capture
     *  (B-100 phase 6) — surfaced beside the capture status text so
     *  the user knows whether their multiple presses agreed cleanly
     *  or only a subset decoded. */
    val lastCaptureQuality: StateFlow<com.andene.spectra.modules.ir.IrCameraCapture.CaptureQuality?> =
        orchestrator.irCapture.lastCaptureQuality

    /** Other plausible matches the most recent scan turned up. The
     *  Results screen renders these so the user can correct an
     *  ambiguous auto-pick (B-102). */
    val alternateMatches: StateFlow<List<DeviceProfile>> = orchestrator.alternateMatches

    /** Switch the chosen device to one of the alternates. Updates
     *  activeDevice; the orchestrator's discoveredDevice stays at the
     *  scan's top-pick so re-running matchers doesn't reset the user's
     *  manual override. */
    fun chooseAlternateMatch(profile: DeviceProfile) {
        _activeDevice.value = profile
    }

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
        executeMacro(macro)
    }

    /**
     * Synthesize and run an "all off" macro — fire POWER on every saved
     * device that has a POWER command bound. The macro isn't persisted;
     * users who want a customized version can build a regular macro
     * themselves. Inter-step delay is small (250 ms) so close-together
     * IR receivers don't drop bursts that arrive within their internal
     * debounce window.
     *
     * No-op when no devices have POWER bound (toast surfaces that).
     */
    fun runAllOff() {
        if (macroJob?.isActive == true) return
        val poweredDevices = _savedDevices.value.filter {
            it.irProfile?.commands?.containsKey(
                com.andene.spectra.modules.control.IrControl.Commands.POWER
            ) == true
        }
        if (poweredDevices.isEmpty()) {
            emitToast("No devices with POWER configured")
            return
        }
        val synthesized = Macro(
            id = "spectra-synth-all-off",
            name = "All Off",
            steps = poweredDevices.map { device ->
                MacroStep(
                    deviceId = device.id,
                    deviceName = device.name ?: "Device",
                    commandName = com.andene.spectra.modules.control.IrControl.Commands.POWER,
                    delayBeforeMs = 250,
                )
            },
        )
        executeMacro(synthesized)
    }

    private fun executeMacro(macro: Macro) {
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
            // Tell any pinned widgets the library changed so the primary
            // device label (and target) is refreshed. Cheap broadcast —
            // fans out to onUpdate which short-circuits when no widgets
            // are pinned.
            com.andene.spectra.widget.SpectraQuickWidget.requestRefresh(getApplication())
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

    /** B-209/B-210: set or clear the device's non-IR control endpoint.
     *  Pass null to clear and revert to IR-only. */
    /** B-215: set or clear a device's room label. */
    fun setRoom(deviceId: String, room: String?) {
        val device = _savedDevices.value.firstOrNull { it.id == deviceId } ?: return
        val updated = device.copy(room = room?.takeIf { it.isNotBlank() })
        orchestrator.control.saveDevice(updated)
        viewModelScope.launch {
            saveDeviceWithFeedback(updated)
            loadSavedDevices()
        }
    }

    /** Currently-applied room filter on the home device list (B-215).
     *  Null = "All rooms". */
    private val _roomFilter = MutableStateFlow<String?>(null)
    val roomFilter: StateFlow<String?> = _roomFilter

    fun selectRoomFilter(room: String?) {
        _roomFilter.value = room
    }

    fun setControlEndpoint(deviceId: String, endpoint: String?) {
        val device = _savedDevices.value.firstOrNull { it.id == deviceId } ?: return
        val updated = device.copy(controlEndpoint = endpoint?.takeIf { it.isNotBlank() })
        orchestrator.control.saveDevice(updated)
        viewModelScope.launch {
            saveDeviceWithFeedback(updated)
            loadSavedDevices()
        }
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
    //
    // The scan now runs inside ScanService (foreground service) so it
    // outlives a brief background → foreground transition and is compliant
    // with Android 14+ BLE-from-background rules. The viewmodel still
    // observes orchestrator state directly (it's a singleton on
    // SpectraApp, so the service writes through to the same flows the
    // viewmodel reads). The viewmodel itself just dispatches start/stop
    // intents and watches for completion to flip the screen.

    private var scanWatchJob: kotlinx.coroutines.Job? = null

    fun startPassiveScan() {
        _isScanning.value = true
        _screen.value = Screen.SCANNING
        val ctx = getApplication<android.app.Application>()
        androidx.core.content.ContextCompat.startForegroundService(
            ctx,
            com.andene.spectra.services.ScanService.startIntent(ctx),
        )
        // Watch the orchestrator's phase so when scanPassive finishes we
        // navigate to Results. Replaces the old coroutine's straight-line
        // post-await transition. Cancels any prior watcher to keep the
        // single-active-scan invariant.
        scanWatchJob?.cancel()
        scanWatchJob = viewModelScope.launch {
            try {
                // Two-step wait: the service kicks scanPassive() asynchronously
                // so phase is still pre-scan when this coroutine starts. First
                // wait for it to ENTER SCANNING_PASSIVE, then for it to LEAVE.
                // Bound the entry wait so a service-failed-to-start scenario
                // doesn't hang the screen forever.
                kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    orchestrator.phase.first { it == SpectraOrchestrator.Phase.SCANNING_PASSIVE }
                } ?: run {
                    // Service never entered SCANNING_PASSIVE — tell the
                    // service to stop (in case it eventually did spin up)
                    // and drop back to Home with a recoverable hint. Without
                    // the stop dispatch the foreground service would leak:
                    // a slow orchestrator startup that produces SCANNING_PASSIVE
                    // a moment after our timeout fires would otherwise run
                    // unobserved with no UI tracking it.
                    ctx.startService(com.andene.spectra.services.ScanService.stopIntent(ctx))
                    emitToast("Scan didn't start. Try again, or check that Bluetooth and Location are on.")
                    _screen.value = Screen.HOME
                    return@launch
                }
                orchestrator.phase.first { it != SpectraOrchestrator.Phase.SCANNING_PASSIVE }
                // Distinguish a natural completion from a cancellation: the
                // orchestrator now clears _discoveredDevice at scan start
                // and only writes it on the success branches, so a null here
                // means the user (or the OS) cancelled the scan before any
                // result was produced. Pushing them to RESULTS in that case
                // would show an empty / stale-data screen.
                val discovered = orchestrator.discoveredDevice.value
                if (discovered == null) {
                    _screen.value = Screen.HOME
                } else {
                    _activeDevice.value = discovered
                    _screen.value = Screen.RESULTS
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun cancelPassiveScan() {
        scanWatchJob?.cancel()
        scanWatchJob = null
        val ctx = getApplication<android.app.Application>()
        ctx.startService(com.andene.spectra.services.ScanService.stopIntent(ctx))
        _isScanning.value = false
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

    /**
     * Open a saved device by id. Used by both the device list tap path
     * and the spectra://device/<id> deep link from MainActivity.
     * Returns true when the device exists; false (with toast) otherwise.
     */
    fun openDeviceById(deviceId: String): Boolean {
        // Saved devices may not be loaded yet on cold-start deep-link;
        // fall back to the orchestrator's matcher snapshot which seeds
        // from the same source on app init.
        val device = _savedDevices.value.firstOrNull { it.id == deviceId }
        if (device == null) {
            emitToast("Device not found")
            return false
        }
        _activeDevice.value = device
        _screen.value = Screen.REMOTE
        return true
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

    /**
     * Stateless variant of [sendCommand] — fire a named command on a
     * specific device id without changing UI state. Used by the
     * spectra://command/<deviceId>/<commandName> deep link (NFC
     * triggers, tasker plugins) where the user wants the IR burst
     * but doesn't want to be navigated into the Remote screen.
     *
     * Cold-process safety: ensures the IR profile is registered with
     * IrControl by reloading from the repository if needed. Same
     * pattern as WidgetCommandReceiver and the QS tile.
     */
    fun sendCommandTo(deviceId: String, commandName: String) {
        viewModelScope.launch {
            if (orchestrator.control.devices.value[deviceId] == null) {
                val device = repository.load(deviceId) ?: run {
                    emitToast("Device not found")
                    return@launch
                }
                orchestrator.control.saveDevice(device)
            }
            orchestrator.control.sendCommand(deviceId, commandName)
        }
    }

    fun sendRepeated(commandName: String, count: Int = 3) {
        val deviceId = _activeDevice.value?.id ?: return
        viewModelScope.launch {
            orchestrator.control.sendRepeated(deviceId, commandName, count)
        }
    }

    private var holdJob: kotlinx.coroutines.Job? = null

    /** B-202: start a hold-to-repeat transmit. Cancel any prior hold so
     *  rapid down/up events don't pile up overlapping IR streams. */
    fun startHold(commandName: String) {
        val deviceId = _activeDevice.value?.id ?: return
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            orchestrator.control.sendHeld(deviceId, commandName)
        }
    }

    /** Cancel the active hold (ACTION_UP / ACTION_CANCEL). */
    fun stopHold() {
        holdJob?.cancel()
        holdJob = null
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

    // ── Library backup / restore ────────────────────────────────

    /**
     * Build a JSON string holding every saved device + macro. Suspends
     * because both repositories load from disk; the caller (typically
     * a SAF createDocument result handler) is on a coroutine scope.
     * Returns null if the export fails.
     */
    fun exportLibrary(anonymize: Boolean = false, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val text = try {
                backupRepository.exportLibrary(anonymize = anonymize)
            } catch (e: Exception) {
                emitToast("Couldn't build backup")
                null
            }
            onResult(text)
        }
    }

    /**
     * Parse and merge a backup envelope into the existing library.
     * Imported devices get fresh ids; macros are merged by id (same id
     * replaces in place, new id appends). Reloads the active device
     * + matcher state on success so the home screen reflects the new
     * library without a manual refresh.
     */
    fun importLibrary(jsonText: String, onResult: (BackupRepository.ImportResult?) -> Unit) {
        viewModelScope.launch {
            val result = backupRepository.importLibrary(jsonText)
            if (result != null && (result.devicesImported > 0 || result.macrosImported > 0)) {
                // Re-seed orchestrator's matcher with whatever's now on disk so
                // the next scan can re-identify imported devices.
                val all = repository.loadAll()
                orchestrator.loadKnownDevices(all)
                loadSavedDevices()
                loadMacros()
            }
            onResult(result)
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
