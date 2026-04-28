package com.andene.spectra.modules.rf

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * SSDP M-SEARCH (Simple Service Discovery Protocol) for finding
 * UPnP devices on the LAN that don't advertise via mDNS — most
 * notably Roku TVs / boxes which advertise as "roku:ecp" on the
 * standard SSDP multicast group.
 *
 * The existing [RfFingerprint] uses NsdManager (mDNS) only;
 * Roku predates Android's mDNS discovery and never registered for
 * `_roku-rcp._tcp` reliably, so an mDNS-only scan misses them.
 * This module's MSEARCH probe + LOCATION header parse closes the
 * gap and surfaces auto-discovered Roku endpoints.
 *
 * Wire format (RFC 2616-like UDP datagram, no formal RFC):
 *   M-SEARCH * HTTP/1.1
 *   HOST: 239.255.255.250:1900
 *   MAN: "ssdp:discover"
 *   MX: 2
 *   ST: roku:ecp
 *
 * Roku replies with HTTP/1.1 200 OK + LOCATION: http://<ip>:8060/
 * which is exactly the base URL [NetworkControl] needs for ECP
 * keypress.
 */
object SsdpDiscovery {

    private const val TAG = "SsdpDiscovery"
    private const val SSDP_GROUP = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val MX_SECONDS = 2
    private const val LISTEN_TIMEOUT_MS = (MX_SECONDS + 1) * 1000

    data class RokuEndpoint(
        /** Base URL for ECP, e.g. "http://192.168.1.42:8060/". The
         *  caller can pass this directly to NetworkControl as
         *  "roku:" + baseUrl with the trailing slash trimmed. */
        val baseUrl: String,
        /** USN identifier from the response, opaque but unique
         *  per-device — useful for de-duplicating discoveries
         *  across multiple M-SEARCH passes. */
        val usn: String?,
    )

    /**
     * Send one M-SEARCH for ST=roku:ecp and collect responses for
     * up to ~3 seconds. Returns a de-duplicated list of discovered
     * Roku base URLs.
     *
     * No INTERNET permission needed at runtime — UDP broadcasts on
     * the local network, but the manifest-declared INTERNET +
     * ACCESS_NETWORK_STATE (added in B-209) cover this.
     */
    suspend fun searchRoku(): List<RokuEndpoint> = withContext(Dispatchers.IO) {
        val request = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_GROUP:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: $MX_SECONDS\r\n")
            append("ST: roku:ecp\r\n")
            append("\r\n")
        }.toByteArray(Charsets.US_ASCII)

        val socket = try {
            DatagramSocket().apply {
                broadcast = true
                soTimeout = LISTEN_TIMEOUT_MS
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open UDP socket for SSDP", e)
            return@withContext emptyList<RokuEndpoint>()
        }

        try {
            val groupAddr = InetAddress.getByName(SSDP_GROUP)
            socket.send(DatagramPacket(request, request.size, groupAddr, SSDP_PORT))

            val responses = mutableListOf<RokuEndpoint>()
            val seenUsns = mutableSetOf<String>()
            val buf = ByteArray(2048)
            val deadline = System.currentTimeMillis() + LISTEN_TIMEOUT_MS

            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "SSDP receive error", e)
                    break
                }
                val text = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                val location = parseHeader(text, "LOCATION")
                val usn = parseHeader(text, "USN")
                if (location != null && (usn == null || seenUsns.add(usn))) {
                    // Strip the path off the LOCATION URL — Roku's ECP
                    // server lives at the root, not at the LOCATION's
                    // path (which points to the description XML).
                    val baseUrl = location.let {
                        val schemeEnd = it.indexOf("://")
                        val pathStart = if (schemeEnd >= 0) it.indexOf('/', schemeEnd + 3) else -1
                        if (pathStart > 0) it.substring(0, pathStart) else it
                    }.trimEnd('/')
                    responses.add(RokuEndpoint(baseUrl = baseUrl, usn = usn))
                }
            }
            responses
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun parseHeader(response: String, name: String): String? {
        val lines = response.lineSequence()
        for (line in lines) {
            val sep = line.indexOf(':')
            if (sep <= 0) continue
            if (line.substring(0, sep).trim().equals(name, ignoreCase = true)) {
                return line.substring(sep + 1).trim()
            }
        }
        return null
    }
}
