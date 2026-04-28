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

    fun deleteMacro(id: String) {
        val updated = _macros.value.filterNot { it.id == id }
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
        macroJob = viewModelScope.launch {
            try {
                for ((index, step) in macro.steps.withIndex()) {
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
        viewModelScope.launch {
            repository.delete(deviceId)
            orchestrator.forgetDevice(deviceId)
            if (_activeDevice.value?.id == deviceId) _activeDevice.value = null
            loadSavedDevices()
            _screen.value = Screen.HOME
        }
    }

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
                _scanLog.value = _scanLog.value + "Permission denied: ${e.message}"
            } catch (e: Exception) {
                _scanLog.value = _scanLog.value + "Scan failed: ${e.message}"
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

    fun startBruteForce() {
        // Re-entry guard: if a sweep is already running, ignore. Otherwise
        // two coroutines race on the orchestrator's BruteForceState and the
        // pendingBruteForceResponse field, producing inconsistent prompts.
        if (bruteForceJob?.isActive == true) return
        bruteForceJob = viewModelScope.launch {
            _screen.value = Screen.LEARN
            orchestrator.startBruteForce { protocol, manufacturer, attempt ->
                val response = CompletableDeferred<Boolean>()
                pendingBruteForceResponse = response
                _bruteForcePrompt.value = BruteForcePrompt(protocol, manufacturer, attempt)
                try {
                    response.await()
                } finally {
                    pendingBruteForceResponse = null
                    _bruteForcePrompt.value = null
                }
            }

            val state = orchestrator.bruteForce.state.value
            if (state.foundProtocol != null) {
                // The orchestrator may have updated the discovered device
                // with the captured power pattern + manufacturer; pull that
                // back into the viewmodel so it survives navigation.
                val device = orchestrator.discoveredDevice.value ?: _activeDevice.value
                if (device != null) {
                    _activeDevice.value = device
                    orchestrator.registerKnownDevice(device)
                    saveDeviceWithFeedback(device)
                    loadSavedDevices()
                }
                _screen.value = Screen.REMOTE
            }
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
    fun installRemoteFromDatabase(entry: com.andene.spectra.data.codedb.IrCodeDatabase.RemoteEntry) {
        val device = _activeDevice.value ?: return
        val updated = device.copy(
            manufacturer = device.manufacturer ?: entry.brand,
            category = if (device.category == DeviceCategory.UNKNOWN) entry.deviceType else device.category,
            irProfile = entry.asIrProfile(),
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
