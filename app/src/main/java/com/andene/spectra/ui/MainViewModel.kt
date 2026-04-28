package com.andene.spectra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andene.spectra.SpectraApp
import com.andene.spectra.core.SpectraOrchestrator
import com.andene.spectra.data.models.*
import com.andene.spectra.data.repository.DeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpectraApp
    val orchestrator: SpectraOrchestrator = app.orchestrator
    private val repository: DeviceRepository = app.repository
    val codeDatabase = app.codeDatabase
    private val macroRepository = app.macroRepository

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
        HOME,       // Device list + scan button
        SCANNING,   // Passive scan in progress
        RESULTS,    // Scan results + next steps
        LEARN,      // IR learning (camera or brute force)
        REMOTE,     // Universal remote control pad
        DEVICE_EDIT // Edit device name/category
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
        viewModelScope.launch { macroRepository.saveAll(updated) }
    }

    fun deleteMacro(id: String) {
        val updated = _macros.value.filterNot { it.id == id }
        _macros.value = updated
        viewModelScope.launch { macroRepository.saveAll(updated) }
    }

    /**
     * Walk a macro's steps, sending each command via IrControl with the
     * configured delay before each step. Cancellable; cancelling stops at
     * the next step boundary (we don't kill an in-flight transmit).
     */
    fun runMacro(id: String) {
        val macro = _macros.value.firstOrNull { it.id == id } ?: return
        macroJob?.cancel()
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
        viewModelScope.launch { repository.save(updated) }
        loadSavedDevices()
    }

    fun updateDeviceCategory(category: DeviceCategory) {
        val device = _activeDevice.value ?: return
        val updated = device.copy(category = category)
        _activeDevice.value = updated
        orchestrator.control.saveDevice(updated)
        viewModelScope.launch { repository.save(updated) }
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

    fun startPassiveScan() {
        viewModelScope.launch {
            _isScanning.value = true
            _screen.value = Screen.SCANNING
            orchestrator.clearLog()

            try {
                orchestrator.scanPassive()
                _activeDevice.value = orchestrator.discoveredDevice.value
                _screen.value = Screen.RESULTS
            } catch (e: SecurityException) {
                _scanLog.value = _scanLog.value + "Permission denied: ${e.message}"
            } catch (e: Exception) {
                _scanLog.value = _scanLog.value + "Scan failed: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
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
            viewModelScope.launch { repository.save(device) }
            _commandNameInput.value = ""
        }
    }

    // ── IR Brute Force ────────────────────────────────────────

    // One pending response at a time; the orchestrator's onAttempt callback
    // suspends on this until the UI completes it via confirm/deny.
    private var pendingBruteForceResponse: CompletableDeferred<Boolean>? = null

    fun startBruteForce() {
        viewModelScope.launch {
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
                    repository.save(device)
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
            repository.save(named)
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
            repository.save(updated)
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
            repository.save(updated)
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
            repository.save(updated)
            loadSavedDevices()
        }
    }

    // ── Hardware checks ───────────────────────────────────────

    fun hasIrBlaster(): Boolean = orchestrator.bruteForce.isAvailable()
    fun hasMagnetometer(): Boolean = orchestrator.em.isAvailable()
}
