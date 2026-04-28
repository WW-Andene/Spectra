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
         * Per-protocol carrier frequency. Most consumer IR uses 38 kHz, but
         * Sharp's TVs and many split-system air conditioners use 33–56 kHz.
         * The sweep tries the protocol's preferred carrier first.
         */
        private val PROTOCOL_CARRIER: Map<IrProtocol, Int> = mapOf(
            IrProtocol.NEC to 38000,
            IrProtocol.SAMSUNG to 38000,
            IrProtocol.LG to 38000,
            IrProtocol.SIRC_12 to 40000,
            IrProtocol.SIRC_15 to 40000,
            IrProtocol.SIRC_20 to 40000,
            IrProtocol.PANASONIC to 38000,
            IrProtocol.SHARP to 38000,
            IrProtocol.RC5 to 36000,
            IrProtocol.RC6 to 36000,
        )

        /**
         * Tokenise a brand string into lowercase word tokens of length ≥ 2.
         * Used by the brand-narrowing prefix; tokens shorter than 2 chars
         * (e.g. punctuation) produce no useful match.
         */
        fun String?.brandTokens(): Set<String> =
            this?.lowercase()
                ?.split(Regex("\\W+"))
                ?.filter { it.length >= 2 }
                ?.toSet()
                .orEmpty()

        /**
         * Whether a database manufacturer entry shares any word token with
         * the user's detected brand. Word-level intersection avoids the
         * false positives substring matching produced (e.g. detected
         * 'lginsignia' would substring-match 'lg' but doesn't share a token).
         */
        fun matchesBrand(manufacturer: String, brandTokens: Set<String>): Boolean {
            if (brandTokens.isEmpty()) return false
            val mfTokens = manufacturer.brandTokens()
            return mfTokens.any { it in brandTokens }
        }

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
        brandFilter: String? = null,
        onSkip: ((protocol: IrProtocol, manufacturer: String, reason: String) -> Unit)? = null,
        onAttempt: suspend (protocol: IrProtocol, manufacturer: String, attemptNum: Int) -> Boolean,
    ) {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Log.e(TAG, "No IR emitter available")
            return
        }

        _state.value = BruteForceState(isRunning = true)
        lastFoundPattern = null
        lastFoundManufacturer = null
        var totalAttempts = 0

        // When the caller already knows the brand (e.g. inferred from RF),
        // try those entries first before falling back to the full sweep.
        val brandTokens = brandFilter.brandTokens()
        val codeOrder: List<Pair<IrProtocol, List<Pair<String, IntArray>>>> = if (brandTokens.isNotEmpty()) {
            val (prefer, rest) = POWER_CODES.entries.map { it.toPair() }.partition { (_, codes) ->
                codes.any { (manufacturer, _) -> matchesBrand(manufacturer, brandTokens) }
            }
            // Within preferred protocols, also reorder so brand-matching entries fire first.
            val reorderedPrefer = prefer.map { (proto, codes) ->
                val (matching, others) = codes.partition { (manufacturer, _) ->
                    matchesBrand(manufacturer, brandTokens)
                }
                proto to (matching + others)
            }
            reorderedPrefer + rest
        } else {
            POWER_CODES.entries.map { it.toPair() }
        }

        // Pick the carrier matching what the receiver expects; fall back to
        // 38 kHz (the most common) plus the supported range from the hardware
        // so we cover AC remotes that diverge from the protocol default.
        val supportedCarriers = irManager.carrierFrequencies?.toList().orEmpty()
        for ((protocol, codes) in codeOrder) {
            val carriersToTry = buildList {
                add(PROTOCOL_CARRIER[protocol] ?: CARRIER_FREQ)
                if (CARRIER_FREQ !in this) add(CARRIER_FREQ)
            }.filter { freq ->
                // Skip carriers the blaster can't actually generate.
                supportedCarriers.isEmpty() ||
                    supportedCarriers.any { freq in it.minFrequency..it.maxFrequency }
            }

            for ((manufacturer, timings) in codes) {
                val carrier = carriersToTry.firstOrNull() ?: CARRIER_FREQ
                totalAttempts++
                _state.value = _state.value.copy(
                    currentProtocol = protocol,
                    totalAttempts = totalAttempts
                )

                // Transmit the code
                try {
                    irManager.transmit(carrier, timings)
                } catch (e: Exception) {
                    Log.w(TAG, "Transmit failed for $manufacturer/$protocol @ ${carrier}Hz", e)
                    onSkip?.invoke(protocol, manufacturer, e.message ?: "transmit error")
                    continue
                }

                delay(SEND_DELAY_MS)

                // Ask user: did the device respond?
                val confirmed = onAttempt(protocol, manufacturer, totalAttempts)
                if (confirmed) {
                    lastFoundPattern = timings
                    lastFoundManufacturer = manufacturer
                    lastFoundCarrier = carrier
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
