package com.andene.spectra.modules.control

import android.util.Log
import com.andene.spectra.data.models.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Non-IR control fan-out (B-209/B-210/B-211).
 *
 * DeviceProfile.controlEndpoint is a free-form scheme-prefixed string
 * that lets us add new control protocols without migrating the data
 * model. Currently understood:
 *
 *   roku:http://<ip>:8060               → Roku ECP keypress (HTTP POST)
 *   bridge:http://<ip>:8080/ir          → Spectra LAN IR-blaster bridge
 *                                          (B-210; sends our raw timings
 *                                          + carrier as JSON to a relay)
 *
 * Future:
 *   ble:<mac>/<service-uuid>            → BLE GATT write
 *   homeassistant:http://<ip>:8123/...  → HA REST API
 *
 * Protocol-specific impl methods live in this same module to keep
 * the dispatch facade self-contained. Each returns true on a clean
 * HTTP response, false on transport / 4xx / 5xx errors.
 */
object NetworkControl {

    private const val TAG = "NetworkControl"

    /** True iff we have a network handler for the device's endpoint. */
    fun isNetworkControlled(profile: DeviceProfile): Boolean {
        val endpoint = profile.controlEndpoint ?: return false
        return endpoint.startsWith("roku:") || endpoint.startsWith("bridge:")
    }

    /**
     * Send a logical command name to the device via its configured
     * network endpoint. Returns true when the remote endpoint accepted
     * the request. Caller stays on a coroutine — this is suspending
     * because IO.
     */
    suspend fun send(profile: DeviceProfile, commandName: String): Boolean {
        val endpoint = profile.controlEndpoint ?: return false
        return when {
            endpoint.startsWith("roku:") -> sendRoku(endpoint.removePrefix("roku:"), commandName)
            endpoint.startsWith("bridge:") -> {
                // B-210 IR bridge: re-encode + post the raw IR timings
                // to the bridge's HTTP endpoint. Implemented in
                // [sendBridge] which needs the IR command, not just
                // the name — pulled from the profile here.
                val cmd = profile.irProfile?.commands?.get(commandName) ?: return false
                val carrier = profile.irProfile.carrierFrequency
                sendBridge(endpoint.removePrefix("bridge:"), commandName, cmd.rawTimings, carrier)
            }
            else -> {
                Log.w(TAG, "Unknown control endpoint scheme: $endpoint")
                false
            }
        }
    }

    /**
     * Roku ECP: HTTP POST to /keypress/<key>. Maps Spectra's logical
     * command names to ECP key strings. Logical commands without a
     * mapping (e.g. captured camera-decode buttons) get sent as the
     * raw command name; ECP rejects unknown keys with 400 so the
     * function returns false and the caller can fall back to IR.
     *
     * Spec: https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md
     */
    private suspend fun sendRoku(baseUrl: String, commandName: String): Boolean = withContext(Dispatchers.IO) {
        val key = ROKU_KEY_MAP[commandName] ?: commandName
        try {
            val url = URL("${baseUrl.trimEnd('/')}/keypress/$key")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 2_000
                conn.readTimeout = 2_000
                conn.doOutput = false  // ECP keypress takes empty body
                val ok = conn.responseCode in 200..299
                if (!ok) Log.w(TAG, "Roku ECP $key → ${conn.responseCode}")
                ok
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Roku ECP send failed", e)
            false
        }
    }

    /**
     * B-210 LAN IR-blaster bridge: POST raw timings + carrier as
     * JSON to a known bridge endpoint. The bridge runs on a phone
     * with an IR LED (or a Broadlink-style hardware blaster running
     * a thin HTTP proxy) and emits the IR. Lets blaster-less phones
     * still control IR devices when there's a bridge on the LAN.
     *
     * Body: { "command": "power", "carrier": 38000, "timings": [...] }
     * Bridge spec is part of the Spectra repo (companion bridge app
     * shipped separately).
     */
    private suspend fun sendBridge(
        baseUrl: String,
        commandName: String,
        timings: IntArray,
        carrier: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(baseUrl.trimEnd('/'))
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 2_000
                conn.readTimeout = 4_000  // bridge does the IR transmit synchronously
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = buildString {
                    append("{\"command\":\"")
                    append(commandName)
                    append("\",\"carrier\":")
                    append(carrier)
                    append(",\"timings\":[")
                    timings.forEachIndexed { i, t ->
                        if (i > 0) append(",")
                        append(t)
                    }
                    append("]}")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val ok = conn.responseCode in 200..299
                if (!ok) Log.w(TAG, "IR bridge $commandName → ${conn.responseCode}")
                ok
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "IR bridge send failed", e)
            false
        }
    }

    /** Spectra logical command name → Roku ECP key string. */
    private val ROKU_KEY_MAP = mapOf(
        IrControl.Commands.POWER to "Power",
        IrControl.Commands.VOL_UP to "VolumeUp",
        IrControl.Commands.VOL_DOWN to "VolumeDown",
        IrControl.Commands.MUTE to "VolumeMute",
        IrControl.Commands.UP to "Up",
        IrControl.Commands.DOWN to "Down",
        IrControl.Commands.LEFT to "Left",
        IrControl.Commands.RIGHT to "Right",
        IrControl.Commands.OK to "Select",
        IrControl.Commands.BACK to "Back",
        IrControl.Commands.HOME to "Home",
        IrControl.Commands.MENU to "Info",
        IrControl.Commands.PLAY to "Play",
        IrControl.Commands.PAUSE to "Play",  // Roku Play toggles
        IrControl.Commands.STOP to "Back",   // Closest semantic
        IrControl.Commands.INPUT to "InputAV1",
        IrControl.Commands.CH_UP to "ChannelUp",
        IrControl.Commands.CH_DOWN to "ChannelDown",
    )
}
