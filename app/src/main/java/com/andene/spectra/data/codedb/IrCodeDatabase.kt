package com.andene.spectra.data.codedb

import android.content.Context
import android.util.Log
import com.andene.spectra.data.models.CaptureMethod
import com.andene.spectra.data.models.DeviceCategory
import com.andene.spectra.data.models.IrCommand
import com.andene.spectra.data.models.IrProfile
import com.andene.spectra.data.models.IrProtocol
import com.andene.spectra.modules.bruteforce.IrBruteForce
import com.andene.spectra.modules.bruteforce.IrBruteForce.Companion.brandTokens
import com.andene.spectra.modules.bruteforce.IrBruteForce.Companion.matchesBrand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bundled IR remote database.
 *
 * Each entry is a complete remote layout — brand + device type + protocol +
 * a map of named commands. Compact entries (NEC / SAMSUNG / SIRC) store only
 * the address+command bytes and let the encoders in IrBruteForce build the
 * raw timing array on demand. Pre-built timing arrays are also supported for
 * protocols we don't have an encoder for.
 *
 * The DB is intentionally small at the moment; brand-narrowed lookup is the
 * point, not exhaustive coverage. Add entries to assets/ir_codes.json.
 */
class IrCodeDatabase(private val context: Context) {

    companion object {
        private const val TAG = "IrCodeDatabase"
        private const val ASSET_PATH = "ir_codes.json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private var cache: List<RemoteEntry>? = null

    /**
     * One installable remote layout — brand-scoped, ready to assign to a device.
     */
    data class RemoteEntry(
        val brand: String,
        val model: String,
        val deviceType: DeviceCategory,
        val protocol: IrProtocol,
        val carrierFrequency: Int,
        val commands: Map<String, IrCommand>,
    ) {
        fun asIrProfile(): IrProfile = IrProfile(
            protocol = protocol,
            carrierFrequency = carrierFrequency,
            commands = commands.toMutableMap(),
        )
    }

    /**
     * Load + parse the bundled JSON. Cached on first call.
     */
    fun all(): List<RemoteEntry> {
        cache?.let { return it }
        val parsed = try {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
                .let { json.decodeFromString<RawDb>(it) }
                .remotes
                .mapNotNull { it.toEntry() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $ASSET_PATH", e)
            emptyList()
        }
        cache = parsed
        return parsed
    }

    /**
     * Filter entries by brand (word-token intersection) and optional category.
     * Pass null to skip a filter. Uses the same matcher as IrBruteForce so a
     * detected brand routes consistently across the DB-install path and the
     * brute-force fallback.
     */
    fun lookup(brand: String?, category: DeviceCategory? = null): List<RemoteEntry> {
        val tokens = brand.brandTokens()
        return all().filter { entry ->
            (tokens.isEmpty() || matchesBrand(entry.brand, tokens)) &&
                (category == null || category == DeviceCategory.UNKNOWN || entry.deviceType == category)
        }
    }

    /** All distinct brands present in the database, sorted. */
    fun brands(): List<String> = all().map { it.brand }.distinct().sorted()

    // ── JSON DTOs ─────────────────────────────────────────────────

    @Serializable
    private data class RawDb(val remotes: List<RawEntry> = emptyList())

    @Serializable
    private data class RawEntry(
        val brand: String,
        val model: String = "",
        val deviceType: String = "UNKNOWN",
        val protocol: String = "RAW",
        val carrierFrequency: Int = 38000,
        val commands: Map<String, RawCommand> = emptyMap(),
    ) {
        fun toEntry(): RemoteEntry? {
            val protocolEnum = try { IrProtocol.valueOf(protocol) } catch (_: Exception) { return null }
            val cat = try { DeviceCategory.valueOf(deviceType) } catch (_: Exception) { DeviceCategory.UNKNOWN }
            val resolved = commands.mapNotNull { (name, raw) ->
                raw.toIrCommand(name, protocolEnum)?.let { name to it }
            }.toMap()
            if (resolved.isEmpty()) return null
            return RemoteEntry(
                brand = brand,
                model = model,
                deviceType = cat,
                protocol = protocolEnum,
                carrierFrequency = carrierFrequency,
                commands = resolved,
            )
        }
    }

    @Serializable
    private data class RawCommand(
        val address: Int? = null,
        val command: Int? = null,
        val timings: List<Int>? = null,
    ) {
        fun toIrCommand(name: String, protocol: IrProtocol): IrCommand? {
            // Pre-baked timings always win.
            timings?.let {
                return IrCommand(
                    name = name,
                    rawTimings = it.toIntArray(),
                    protocol = protocol,
                    capturedVia = CaptureMethod.LEARNED,
                )
            }
            // Compact form — encode now using the protocol-specific encoder.
            val a = address ?: return null
            val c = command ?: return null
            val encoded = when (protocol) {
                IrProtocol.NEC, IrProtocol.LG -> IrBruteForce.encodeNEC(a, c)
                IrProtocol.SAMSUNG -> IrBruteForce.encodeSamsung(a, c)
                IrProtocol.SIRC_12, IrProtocol.SIRC_15, IrProtocol.SIRC_20 ->
                    IrBruteForce.encodeSIRC(a, c)
                else -> return null
            }
            return IrCommand(
                name = name,
                rawTimings = encoded,
                protocol = protocol,
                code = ((a.toLong() and 0xFF) shl 8) or (c.toLong() and 0xFF),
                capturedVia = CaptureMethod.LEARNED,
            )
        }
    }
}
