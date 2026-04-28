package com.andene.spectra.modules.control

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.andene.spectra.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Module 6 — IR Replay & Control
 *
 * Stores learned/discovered IR commands per device profile
 * and replays them on demand via the IR blaster.
 *
 * This is the "universal remote" layer — all other modules feed into this.
 * - Module 1 (Camera Capture) → raw waveforms stored here
 * - Module 5 (Brute Force) → discovered protocol/codes stored here
 * - Modules 2/3/4 (Fingerprinting) → device identification auto-loads saved profile
 */
class IrControl(private val context: Context) {

    companion object {
        private const val TAG = "IrControl"
        private const val DEFAULT_CARRIER = 38000
        private const val REPEAT_DELAY_MS = 40L
    }

    private val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    private val _devices = MutableStateFlow<Map<String, DeviceProfile>>(emptyMap())
    val devices: StateFlow<Map<String, DeviceProfile>> = _devices

    private val _lastTransmitResult = MutableStateFlow<TransmitResult?>(null)
    val lastTransmitResult: StateFlow<TransmitResult?> = _lastTransmitResult

    data class TransmitResult(
        val success: Boolean,
        val deviceId: String,
        val commandName: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun isAvailable(): Boolean = irManager?.hasIrEmitter() == true

    // ── Device Management ─────────────────────────────────────

    /**
     * Register a new device profile or update an existing one.
     */
    fun saveDevice(profile: DeviceProfile) {
        val map = _devices.value.toMutableMap()
        map[profile.id] = profile
        _devices.value = map
        Log.d(TAG, "Saved device: ${profile.name ?: profile.id}, ${profile.irProfile?.commands?.size ?: 0} commands")
    }

    /**
     * Add a learned command to a device.
     */
    fun addCommand(deviceId: String, command: IrCommand) {
        val map = _devices.value.toMutableMap()
        val device = map[deviceId] ?: return
        val irProfile = device.irProfile ?: IrProfile()

        val updatedProfile = irProfile.copy(
            commands = irProfile.commands.toMutableMap().apply {
                put(command.name, command)
            }
        )

        map[deviceId] = device.copy(irProfile = updatedProfile)
        _devices.value = map
        Log.d(TAG, "Added command '${command.name}' to device $deviceId")
    }

    fun removeDevice(deviceId: String) {
        val map = _devices.value.toMutableMap()
        map.remove(deviceId)
        _devices.value = map
    }

    fun getDevice(deviceId: String): DeviceProfile? = _devices.value[deviceId]

    // ── IR Transmission ───────────────────────────────────────

    /**
     * Send a named command to a specific device.
     */
    fun sendCommand(deviceId: String, commandName: String): Boolean {
        val device = _devices.value[deviceId]
        val command = device?.irProfile?.commands?.get(commandName)

        if (command == null) {
            Log.w(TAG, "Command '$commandName' not found for device $deviceId")
            _lastTransmitResult.value = TransmitResult(false, deviceId, commandName)
            return false
        }

        return transmit(deviceId, commandName, command)
    }

    /**
     * Send a raw IR command directly (not from saved profile).
     */
    fun sendRaw(
        deviceId: String,
        commandName: String,
        carrierFreq: Int,
        pattern: IntArray
    ): Boolean {
        val command = IrCommand(
            name = commandName,
            rawTimings = pattern,
            capturedVia = CaptureMethod.MANUAL
        )
        return transmit(deviceId, commandName, command, carrierFreq)
    }

    /**
     * Send a command with repeat (for volume/channel hold).
     */
    fun sendRepeated(
        deviceId: String,
        commandName: String,
        repeatCount: Int = 3
    ): Boolean {
        val device = _devices.value[deviceId]
        val command = device?.irProfile?.commands?.get(commandName) ?: return false
        val carrier = device.irProfile?.carrierFrequency ?: DEFAULT_CARRIER

        var success = true
        for (i in 0 until repeatCount) {
            try {
                irManager?.transmit(carrier, command.rawTimings)
                Thread.sleep(REPEAT_DELAY_MS)
            } catch (e: Exception) {
                success = false
                break
            }
        }

        _lastTransmitResult.value = TransmitResult(success, deviceId, commandName)
        return success
    }

    private fun transmit(
        deviceId: String,
        commandName: String,
        command: IrCommand,
        carrierFreqOverride: Int? = null
    ): Boolean {
        if (irManager == null || !irManager.hasIrEmitter()) {
            _lastTransmitResult.value = TransmitResult(false, deviceId, commandName)
            return false
        }

        val carrier = carrierFreqOverride
            ?: _devices.value[deviceId]?.irProfile?.carrierFrequency
            ?: DEFAULT_CARRIER

        return try {
            irManager.transmit(carrier, command.rawTimings)
            _lastTransmitResult.value = TransmitResult(true, deviceId, commandName)
            Log.d(TAG, "Transmitted '$commandName' to $deviceId @ ${carrier}Hz")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Transmit failed", e)
            _lastTransmitResult.value = TransmitResult(false, deviceId, commandName)
            false
        }
    }

    // ── Standard Remote Layouts ───────────────────────────────

    /**
     * Standard command names used across the app.
     * UI maps these to buttons on the remote screen.
     */
    object Commands {
        const val POWER = "power"
        const val VOL_UP = "vol_up"
        const val VOL_DOWN = "vol_down"
        const val CH_UP = "ch_up"
        const val CH_DOWN = "ch_down"
        const val MUTE = "mute"
        const val INPUT = "input"
        const val OK = "ok"
        const val UP = "up"
        const val DOWN = "down"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val BACK = "back"
        const val HOME = "home"
        const val MENU = "menu"
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val STOP = "stop"
        const val NUM_0 = "num_0"
        const val NUM_1 = "num_1"
        const val NUM_2 = "num_2"
        const val NUM_3 = "num_3"
        const val NUM_4 = "num_4"
        const val NUM_5 = "num_5"
        const val NUM_6 = "num_6"
        const val NUM_7 = "num_7"
        const val NUM_8 = "num_8"
        const val NUM_9 = "num_9"
    }
}
