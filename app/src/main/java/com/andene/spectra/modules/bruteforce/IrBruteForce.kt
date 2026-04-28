package com.andene.spectra.modules.bruteforce

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.andene.spectra.data.models.BruteForceState
import com.andene.spectra.data.models.IrProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Module 5 — IR Brute Force
 *
 * Systematically sweeps through IR protocol families sending
 * power toggle commands until the target device responds.
 *
 * Strategy:
 * 1. Start with most common protocols (NEC covers ~60% of devices)
 * 2. For each protocol, send known power-on codes for major manufacturers
 * 3. Pause between sends for user to confirm "did it react?"
 * 4. On confirmation → protocol locked, begin command discovery
 *
 * Total search space: ~6 protocols × ~50 manufacturer codes = ~300 attempts
 * At 500ms per attempt = ~2.5 minutes worst case.
 */
class IrBruteForce(private val context: Context) {

    companion object {
        private const val TAG = "IrBruteForce"
        private const val CARRIER_FREQ = 38000 // 38kHz standard
        private const val SEND_DELAY_MS = 600L // Pause between attempts

        /**
         * Encode an NEC command from address + command bytes.
         * NEC format: 9000µs mark, 4500µs space, then 32 bits
         * Bit 0: 562µs mark + 562µs space
         * Bit 1: 562µs mark + 1687µs space
         */
        fun encodeNEC(address: Int, command: Int): IntArray {
            val timings = mutableListOf(9000, 4500) // Header
            val data = (address and 0xFF) or
                    ((address.inv() and 0xFF) shl 8) or
                    ((command and 0xFF) shl 16) or
                    ((command.inv() and 0xFF) shl 24)

            for (bit in 0 until 32) {
                timings.add(562) // Mark
                timings.add(if ((data shr bit) and 1 == 1) 1687 else 562)
            }
            timings.add(562) // Stop bit
            return timings.toIntArray()
        }

        /**
         * Encode a Sony SIRC 12-bit command.
         * Format: 2400µs header, then 7 command + 5 address bits
         * Bit 0: 600µs mark + 600µs space
         * Bit 1: 1200µs mark + 600µs space
         */
        fun encodeSIRC(address: Int, command: Int): IntArray {
            val timings = mutableListOf(2400, 600) // Header
            val data = (command and 0x7F) or ((address and 0x1F) shl 7)

            for (bit in 0 until 12) {
                timings.add(if ((data shr bit) and 1 == 1) 1200 else 600)
                timings.add(600)
            }
            return timings.toIntArray()
        }

        /**
         * Encode Samsung protocol (similar to NEC but different header).
         */
        fun encodeSamsung(address: Int, command: Int): IntArray {
            val timings = mutableListOf(4500, 4500) // Samsung header
            val data = (address and 0xFF) or
                    ((address and 0xFF) shl 8) or // Samsung repeats address
                    ((command and 0xFF) shl 16) or
                    ((command.inv() and 0xFF) shl 24)

            for (bit in 0 until 32) {
                timings.add(560)
                timings.add(if ((data shr bit) and 1 == 1) 1690 else 560)
            }
            timings.add(560)
            return timings.toIntArray()
        }

        /**
         * Common power toggle codes organized by protocol → manufacturer.
         */
        val POWER_CODES: Map<IrProtocol, List<Pair<String, IntArray>>> = mapOf(
            // NEC protocol — most common
            IrProtocol.NEC to listOf(
                "Samsung TV" to encodeNEC(0x07, 0x02),
                "LG TV" to encodeNEC(0x04, 0x08),
                "Toshiba" to encodeNEC(0x02, 0x48),
                "Onkyo" to encodeNEC(0x04, 0xD3),
                "Yamaha" to encodeNEC(0x7A, 0x1E),
                "Hisense" to encodeNEC(0x00, 0x08),
                "TCL" to encodeNEC(0x00, 0x08),
                "Haier" to encodeNEC(0x01, 0x08),
                "Vizio" to encodeNEC(0x04, 0x08),
                "Insignia" to encodeNEC(0x04, 0x02),
                "Emerson" to encodeNEC(0x01, 0x00),
                "Sanyo" to encodeNEC(0x1C, 0x08),
                "Magnavox" to encodeNEC(0x01, 0x00),
                "Funai" to encodeNEC(0x18, 0x08),
                "Sylvania" to encodeNEC(0x01, 0x00),
                "Generic NEC TV 1" to encodeNEC(0x00, 0x02),
                "Generic NEC TV 2" to encodeNEC(0x00, 0x08),
                "Generic NEC TV 3" to encodeNEC(0x04, 0x02),
                "Generic AC" to encodeNEC(0x10, 0x00),
            ),

            // Samsung protocol
            IrProtocol.SAMSUNG to listOf(
                "Samsung TV (native)" to encodeSamsung(0x07, 0x02),
                "Samsung TV alt" to encodeSamsung(0xE0, 0x40),
                "Samsung Soundbar" to encodeSamsung(0x01, 0x02),
            ),

            // Sony SIRC
            IrProtocol.SIRC_12 to listOf(
                "Sony TV" to encodeSIRC(0x01, 0x15),
                "Sony TV alt" to encodeSIRC(0x01, 0x2E),
                "Sony BD Player" to encodeSIRC(0x1A, 0x15),
                "Sony AV Receiver" to encodeSIRC(0x10, 0x15),
                "Sony Soundbar" to encodeSIRC(0x13, 0x15),
            ),

            // LG specific
            IrProtocol.LG to listOf(
                "LG TV (native)" to encodeNEC(0x04, 0x08), // LG uses NEC variant
                "LG TV alt" to encodeNEC(0x04, 0x02),
                "LG Soundbar" to encodeNEC(0x04, 0xD3),
            ),

            // Panasonic (uses 48-bit Kaseikyo, simplified here)
            IrProtocol.PANASONIC to listOf(
                "Panasonic TV" to intArrayOf(
                    3500, 1750,
                    502, 390, 502, 390, 502, 1244, 502, 390,
                    502, 390, 502, 390, 502, 390, 502, 390,
                    502, 390, 502, 390, 502, 390, 502, 390,
                    502, 1244, 502, 390, 502, 390, 502, 390,
                    502, 390, 502, 390, 502, 1244, 502, 390,
                    502, 390, 502, 390, 502, 390, 502, 390,
                    502, 1244, 502, 1244, 502, 1244, 502, 1244,
                    502, 1244, 502, 1244, 502, 390, 502, 390,
                    502, 390, 502, 390, 502, 390, 502, 390,
                    502, 390, 502, 390, 502, 1244, 502, 1244,
                    502
                ),
            ),

            // Sharp
            IrProtocol.SHARP to listOf(
                "Sharp TV" to intArrayOf(
                    320, 680, 320, 1680, 320, 680, 320, 680,
                    320, 680, 320, 1680, 320, 680, 320, 1680,
                    320, 680, 320, 680, 320, 680, 320, 680,
                    320, 1680, 320, 680, 320, 43000,
                    320, 680, 320, 1680, 320, 680, 320, 680,
                    320, 680, 320, 1680, 320, 1680, 320, 680,
                    320, 1680, 320, 1680, 320, 1680, 320, 1680,
                    320, 680, 320, 1680, 320
                ),
            ),
        )
    }

    private val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    private val _state = MutableStateFlow(BruteForceState())
    val state: StateFlow<BruteForceState> = _state

    /**
     * Pattern that produced a confirmed reaction during the most recent
     * sweep. Captured so the orchestrator can save it as a real "power"
     * IrCommand on the device profile.
     */
    var lastFoundPattern: IntArray? = null
        private set
    var lastFoundManufacturer: String? = null
        private set
    var lastFoundCarrier: Int = CARRIER_FREQ
        private set

    private var sweepJob: Job? = null

    /**
     * Check if IR blaster is available.
     */
    fun isAvailable(): Boolean = irManager?.hasIrEmitter() == true

    /**
     * Get supported carrier frequency ranges.
     */
    fun getCarrierRanges(): List<ConsumerIrManager.CarrierFrequencyRange> {
        return irManager?.carrierFrequencies?.toList() ?: emptyList()
    }

    /**
     * Start brute force sweep across all protocol families.
     * Calls [onAttempt] after each transmission so UI can ask user for confirmation.
     */
    suspend fun startSweep(
        onAttempt: suspend (protocol: IrProtocol, manufacturer: String, attemptNum: Int) -> Boolean
    ) {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Log.e(TAG, "No IR emitter available")
            return
        }

        _state.value = BruteForceState(isRunning = true)
        lastFoundPattern = null
        lastFoundManufacturer = null
        var totalAttempts = 0

        for ((protocol, codes) in POWER_CODES) {
            for ((manufacturer, timings) in codes) {
                totalAttempts++
                _state.value = _state.value.copy(
                    currentProtocol = protocol,
                    totalAttempts = totalAttempts
                )

                // Transmit the code
                try {
                    irManager.transmit(CARRIER_FREQ, timings)
                } catch (e: Exception) {
                    Log.w(TAG, "Transmit failed for $manufacturer/$protocol", e)
                    continue
                }

                delay(SEND_DELAY_MS)

                // Ask user: did the device respond?
                val confirmed = onAttempt(protocol, manufacturer, totalAttempts)
                if (confirmed) {
                    lastFoundPattern = timings
                    lastFoundManufacturer = manufacturer
                    lastFoundCarrier = CARRIER_FREQ
                    _state.value = _state.value.copy(
                        isRunning = false,
                        foundProtocol = protocol,
                        foundCode = timings.first().toLong()
                    )
                    Log.d(TAG, "Found! $manufacturer using $protocol after $totalAttempts attempts")
                    return
                }
            }
        }

        _state.value = _state.value.copy(isRunning = false)
        Log.d(TAG, "Sweep complete, no device responded after $totalAttempts attempts")
    }

    /**
     * Send a single raw IR pattern.
     */
    fun transmitRaw(carrierFreq: Int, pattern: IntArray) {
        irManager?.transmit(carrierFreq, pattern)
    }

    fun stop() {
        sweepJob?.cancel()
        _state.value = _state.value.copy(isRunning = false)
    }

}
