package com.andene.spectra.modules.control

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.andene.spectra.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    // ConsumerIrManager.transmit() isn't documented as thread-safe and on
    // some OEM stacks concurrent calls produce garbled bursts or silently
    // drop one. Serialize every transmit through this mutex so a rapid
    // double-tap, a button-tap-while-macro-running, or any other overlap
    // queues instead of racing on the IR hardware.
    private val txMutex = Mutex()

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

    // ── IR Transmission ───────────────────────────────────────

    /**
     * Send a named command to a specific device.
     * ConsumerIrManager.transmit blocks for the IR burst duration (50–200ms),
     * so this is suspending and runs on the IO dispatcher.
     */
    suspend fun sendCommand(deviceId: String, commandName: String): Boolean {
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
     * Hold-to-repeat: fire a command continuously until cancelled
     * (via coroutine cancellation). NEC commands transmit a single
     * full data frame then 11ms repeat frames every 110ms — same wire
     * shape a real NEC remote produces. Other protocols retransmit
     * the full frame at REPEAT_DELAY_MS intervals.
     *
     * Holds the IR mutex for the whole hold duration so the burst
     * train stays coherent. Cancellation through the cooperative
     * yield in `delay` releases promptly.
     *
     * Used by the remote-pad's volume / channel / cursor buttons —
     * ACTION_DOWN starts this, ACTION_UP cancels its job.
     */
    suspend fun sendHeld(deviceId: String, commandName: String) = withContext(Dispatchers.IO) {
        val device = _devices.value[deviceId]
        val command = device?.irProfile?.commands?.get(commandName) ?: return@withContext
        val carrier = nearestSupportedCarrier(
            protocolCarrier(command)
                ?: device.irProfile?.carrierFrequency
                ?: DEFAULT_CARRIER
        ) ?: DEFAULT_CARRIER

        val timings = synthesizeOrFallback(command)
        val useNecRepeat = command.protocol == IrProtocol.NEC && command.code != null
        val repeatTimings = if (useNecRepeat)
            com.andene.spectra.modules.ir.protocols.NecCodec.encodeRepeat() else null
        val gapMs = if (useNecRepeat) 99L else REPEAT_DELAY_MS

        txMutex.withLock {
            try {
                irManager?.transmit(carrier, timings)
                _lastTransmitResult.value = TransmitResult(true, deviceId, commandName)
                while (true) {
                    delay(gapMs)
                    val burst = repeatTimings ?: timings
                    irManager?.transmit(carrier, burst)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e  // Let the caller's job cancellation unwind cleanly.
            } catch (e: Exception) {
                Log.e(TAG, "Held transmit failed", e)
                _lastTransmitResult.value = TransmitResult(false, deviceId, commandName)
            }
        }
    }

    /**
     * Send a command with repeat (for volume/channel hold).
     */
    suspend fun sendRepeated(
        deviceId: String,
        commandName: String,
        repeatCount: Int = 3
    ): Boolean = withContext(Dispatchers.IO) {
        val device = _devices.value[deviceId]
        val command = device?.irProfile?.commands?.get(commandName)
            ?: return@withContext false
        val carrier = protocolCarrier(command)
            ?: device.irProfile?.carrierFrequency
            ?: DEFAULT_CARRIER

        val timings = synthesizeOrFallback(command)
        // For NEC with a decoded code, follow the protocol's actual
        // press-and-hold semantics: send the full data frame once,
        // then short repeat frames every ~110 ms thereafter. Repeat
        // frames are 11 ms vs the full frame's ~67 ms, so a held
        // button transmits ~6× faster on the wire and behaves like a
        // real remote — volume / channel / cursor hold is smooth
        // instead of stuttering. Other protocols don't have a compact
        // repeat shape so they fall back to full-frame retransmit.
        val useNecRepeat = command.protocol == IrProtocol.NEC && command.code != null
        val repeatTimings: IntArray? = if (useNecRepeat) {
            com.andene.spectra.modules.ir.protocols.NecCodec.encodeRepeat()
        } else null
        val NEC_REPEAT_GAP_MS = 99L  // 110 ms NEC period − 11 ms repeat duration

        // Resolve carrier once for the whole repeat sequence. If the
        // hardware doesn't support the requested carrier we drop down
        // to the nearest supported one (B-200) so a held button at
        // 40 kHz on a 38 kHz-only blaster doesn't fail every burst.
        val effectiveCarrier = nearestSupportedCarrier(carrier) ?: carrier
        val success = txMutex.withLock {
            var ok = true
            for (i in 0 until repeatCount) {
                try {
                    val burst = if (i == 0 || repeatTimings == null) timings else repeatTimings
                    irManager?.transmit(effectiveCarrier, burst)
                    delay(if (useNecRepeat) NEC_REPEAT_GAP_MS else REPEAT_DELAY_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Repeated transmit failed at iteration $i @ ${effectiveCarrier}Hz", e)
                    ok = false
                    break
                }
            }
            ok
        }

        _lastTransmitResult.value = TransmitResult(success, deviceId, commandName)
        success
    }

    private suspend fun transmit(
        deviceId: String,
        commandName: String,
        command: IrCommand,
        carrierFreqOverride: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (irManager == null || !irManager.hasIrEmitter()) {
            _lastTransmitResult.value = TransmitResult(false, deviceId, commandName)
            return@withContext false
        }

        val carrier = carrierFreqOverride
            ?: protocolCarrier(command)
            ?: _devices.value[deviceId]?.irProfile?.carrierFrequency
            ?: DEFAULT_CARRIER

        // Pick the best timings to actually send. When we have a
        // protocol-decoded code (NEC for now, more codecs queued in the
        // build plan), re-synthesize fresh canonical timings instead of
        // replaying the rolling-shutter-jittered raw capture. This
        // significantly improves transmit reliability on devices with
        // strict NEC tolerances and lets shared profiles fire correctly
        // on any blaster regardless of the original capturer's jitter.
        val timings = synthesizeOrFallback(command)

        // Serialize: ConsumerIrManager.transmit() is a blocking call on
        // shared hardware; concurrent invocations are not safe across
        // OEM implementations. A waiter just queues — pleasant from the
        // caller's perspective since suspendable.
        txMutex.withLock {
            transmitWithCarrierFallback(deviceId, commandName, carrier, timings)
        }
    }

    /**
     * Transmit a burst at the requested [carrier], falling back to the
     * blaster's nearest supported carrier if the hardware refuses the
     * requested one (B-200). Some OEM blasters only emit at 38 kHz —
     * a SIRC code synthesized at 40 kHz then transmitted gets a
     * silent rejection instead of a clean failure-with-recovery. The
     * fallback walks ConsumerIrManager.carrierFrequencies for the
     * closest supported value and retries once.
     *
     * Returns true iff a burst made it onto the wire (at any carrier).
     */
    private fun transmitWithCarrierFallback(
        deviceId: String,
        commandName: String,
        carrier: Int,
        timings: IntArray,
    ): Boolean {
        val mgr = irManager ?: return false
        try {
            mgr.transmit(carrier, timings)
            _lastTransmitResult.value = TransmitResult(true, deviceId, commandName)
            Log.d(TAG, "Transmitted '$commandName' to $deviceId @ ${carrier}Hz")
            return true
        } catch (e: Exception) {
            // Hardware rejected the carrier (unsupported frequency,
            // out-of-range timings array on this blaster, etc.). Pick
            // the nearest supported carrier and retry once. Any
            // failure on the retry is the final answer — we don't
            // unbounded-loop.
            Log.w(TAG, "Transmit failed at ${carrier}Hz: ${e.message} — trying fallback")
            val fallback = nearestSupportedCarrier(carrier)
            if (fallback != null && fallback != carrier) {
                try {
                    mgr.transmit(fallback, timings)
                    _lastTransmitResult.value = TransmitResult(true, deviceId, commandName)
                    Log.i(TAG, "Transmitted '$commandName' to $deviceId @ ${fallback}Hz (fallback from $carrier)")
                    return true
                } catch (e2: Exception) {
                    Log.e(TAG, "Transmit failed at fallback ${fallback}Hz too", e2)
                }
            }
            _lastTransmitResult.value = TransmitResult(false, deviceId, commandName)
            return false
        }
    }

    /**
     * Pick the carrier the hardware will accept that's closest to
     * [requested]. Walks ConsumerIrManager.carrierFrequencies (an
     * array of [min..max] ranges per supported band) and finds the
     * frequency inside the union of those ranges with the smallest
     * absolute distance to [requested]. Returns null when the blaster
     * doesn't expose a frequency table or can't accept anything close.
     */
    private fun nearestSupportedCarrier(requested: Int): Int? {
        val ranges = irManager?.carrierFrequencies ?: return null
        if (ranges.isEmpty()) return null
        // Already supported? No-op.
        for (range in ranges) {
            if (requested in range.minFrequency..range.maxFrequency) return requested
        }
        // Pick the boundary frequency of the closest range.
        var bestCarrier: Int? = null
        var bestDistance = Int.MAX_VALUE
        for (range in ranges) {
            val candidate = when {
                requested < range.minFrequency -> range.minFrequency
                requested > range.maxFrequency -> range.maxFrequency
                else -> requested
            }
            val distance = kotlin.math.abs(requested - candidate)
            if (distance < bestDistance) {
                bestDistance = distance
                bestCarrier = candidate
            }
        }
        return bestCarrier
    }

    // ── Standard Remote Layouts ───────────────────────────────

    /**
     * Standard command names used across the app.
     * UI maps these to buttons on the remote screen.
     */
    /**
     * Pick the timings array to actually transmit. When the IrCommand has
     * a protocol-decoded [IrCommand.code] for a protocol we can encode
     * (currently NEC), re-synthesize canonical timings — same address +
     * command, but emitted with nominal microsecond widths instead of
     * the rolling-shutter-jittered values captured by the camera.
     *
     * Falls back to [IrCommand.rawTimings] for any protocol we don't
     * have an encoder for, for codes that are null, or for commands
     * captured via brute-force / DB import where rawTimings is the
     * canonical form.
     */
    private fun synthesizeOrFallback(command: IrCommand): IntArray {
        val code = command.code
        if (code != null) {
            when (command.protocol) {
                IrProtocol.NEC ->
                    return com.andene.spectra.modules.ir.protocols.NecCodec.encodeFromPacked(code)
                IrProtocol.SAMSUNG ->
                    return com.andene.spectra.modules.ir.protocols.SamsungCodec.encodeFromPacked(code)
                IrProtocol.LG ->
                    return com.andene.spectra.modules.ir.protocols.LgCodec.encodeFromPacked(code)
                IrProtocol.SIRC_12 ->
                    return com.andene.spectra.modules.ir.protocols.SonyCodec.encodeFromPacked(code)
                IrProtocol.PANASONIC ->
                    return com.andene.spectra.modules.ir.protocols.PanasonicCodec.encode(code)
                else -> Unit
            }
        }
        return command.rawTimings
    }

    /**
     * Per-protocol carrier-frequency override. Sony SIRC remotes use
     * 40 kHz; everyone else in our codec set uses 38 kHz. The user's
     * IrProfile.carrierFrequency is kept as the primary hint, but for
     * protocols with a fixed standard we trust the spec — emitting a
     * 38 kHz Sony burst at the device's expected 40 kHz pin is the
     * single most-common reason "I learned the code but it doesn't
     * work" reports surface in IR remote apps.
     */
    private fun protocolCarrier(command: IrCommand): Int? = when (command.protocol) {
        IrProtocol.SIRC_12, IrProtocol.SIRC_15, IrProtocol.SIRC_20 -> 40000
        IrProtocol.PANASONIC -> 36700  // Kaseikyo / Panasonic standard
        else -> null
    }

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
