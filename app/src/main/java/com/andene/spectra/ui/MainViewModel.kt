package com.andene.spectra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andene.spectra.SpectraApp
import com.andene.spectra.core.SpectraOrchestrator
import com.andene.spectra.data.models.*
import com.andene.spectra.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpectraApp
    val orchestrator: SpectraOrchestrator = app.orchestrator
    private val repository: DeviceRepository = app.repository

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

    init {
        loadSavedDevices()
        // Mirror orchestrator log
        viewModelScope.launch {
            orchestrator.log.collect { _scanLog.value = it }
        }
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
            orchestrator.control.removeDevice(deviceId)
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

    fun startBruteForce() {
        viewModelScope.launch {
            _screen.value = Screen.LEARN
            orchestrator.startBruteForce { protocol, manufacturer, attempt ->
                _bruteForcePrompt.value = BruteForcePrompt(protocol, manufacturer, attempt)
                // Wait for user response — this is handled via confirmBruteForce/denyBruteForce
                // We use a simple polling approach here
                while (_bruteForcePrompt.value != null) {
                    kotlinx.coroutines.delay(100)
                }
                // Return whether user confirmed
                _bruteForceConfirmed
            }

            // After sweep completes
            val state = orchestrator.bruteForce.state.value
            if (state.foundProtocol != null) {
                val device = _activeDevice.value
                if (device != null) {
                    orchestrator.registerKnownDevice(device)
                    viewModelScope.launch { repository.save(device) }
                }
                _screen.value = Screen.REMOTE
            }
        }
    }

    private var _bruteForceConfirmed = false

    fun confirmBruteForce() {
        _bruteForceConfirmed = true
        _bruteForcePrompt.value = null
    }

    fun denyBruteForce() {
        _bruteForceConfirmed = false
        _bruteForcePrompt.value = null
    }

    fun stopBruteForce() {
        orchestrator.bruteForce.stop()
        _bruteForcePrompt.value = null
    }

    // ── IR Control ────────────────────────────────────────────

    fun sendCommand(commandName: String) {
        val deviceId = _activeDevice.value?.id ?: return
        orchestrator.control.sendCommand(deviceId, commandName)
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

    // ── Hardware checks ───────────────────────────────────────

    fun hasIrBlaster(): Boolean = orchestrator.bruteForce.isAvailable()
    fun hasMagnetometer(): Boolean = orchestrator.em.isAvailable()
}
