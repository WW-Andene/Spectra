package com.andene.spectra.modules.rf

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.andene.spectra.data.models.BleDeviceInfo
import com.andene.spectra.data.models.RfSignature
import com.andene.spectra.data.models.WifiDeviceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Module 3 — RF Fingerprint
 *
 * Scans WiFi, BLE, and NFC to identify nearby electronic devices.
 * Many smart TVs, ACs, and IoT devices broadcast identifiable
 * information even without active pairing:
 * - WiFi: SSID patterns ("LG_TV_xxxx"), MAC prefix → manufacturer
 * - BLE: Device name, service UUIDs, manufacturer-specific data
 * - mDNS/Bonjour: Service types (_googlecast._tcp, _airplay._tcp)
 *
 * MAC prefix database maps first 3 octets to manufacturer (IEEE OUI).
 */
class RfFingerprint(private val context: Context) {

    companion object {
        private const val TAG = "RfFingerprint"
        private const val BLE_SCAN_DURATION_MS = 5000L
        private const val MDNS_SCAN_DURATION_MS = 4000L

        // Common OUI prefixes → manufacturer
        // Full database: https://standards-oui.ieee.org/
        val OUI_MAP = mapOf(
            "00:E0:91" to "LG Electronics",
            "A8:23:FE" to "LG Electronics",
            "00:1E:75" to "LG Electronics",
            "F8:0D:60" to "Samsung",
            "8C:71:F8" to "Samsung",
            "BC:72:B1" to "Samsung",
            "78:BD:BC" to "Samsung",
            "10:F1:F2" to "Sony",
            "04:5D:4B" to "Sony",
            "04:E5:36" to "TCL",
            "00:1A:11" to "Google (Chromecast)",
            "D8:6C:63" to "Google",
            "58:FD:B1" to "Hisense",
            "00:09:B0" to "Panasonic",
            "00:D0:B8" to "Panasonic",
            "AC:3A:7A" to "Roku",
            "B8:3E:59" to "Roku",
            "D0:73:D5" to "Apple (AirPlay)",
            "7C:D1:C3" to "Apple",
            "78:28:CA" to "Sonos",
            "B8:E9:37" to "Sonos",
            "A0:18:28" to "Xiaomi",
            "64:CE:D1" to "Xiaomi",
        )

        // mDNS service → device category hints
        val MDNS_HINTS = mapOf(
            "_googlecast._tcp" to "Chromecast / Android TV",
            "_airplay._tcp" to "Apple AirPlay device",
            "_raop._tcp" to "AirPlay audio",
            "_spotify-connect._tcp" to "Spotify speaker",
            "_sonos._tcp" to "Sonos speaker",
            "_hap._tcp" to "HomeKit device",
            "_amzn-wplay._tcp" to "Fire TV / Echo",
        )
    }

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _signature = MutableStateFlow<RfSignature?>(null)
    val signature: StateFlow<RfSignature?> = _signature

    private val discoveredBle = mutableListOf<BleDeviceInfo>()
    private val discoveredMdns = mutableMapOf<String, String>() // service name → type

    enum class State {
        IDLE, SCANNING, COMPLETE, ERROR
    }

    /**
     * Run all RF scans concurrently and combine results.
     */
    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ])
    suspend fun scan(): RfSignature = withContext(Dispatchers.IO) {
        _state.value = State.SCANNING
        discoveredBle.clear()
        discoveredMdns.clear()

        try {
            // Run WiFi, BLE, and mDNS scans concurrently
            val wifiJob = async { scanWifi() }
            val bleJob = async { scanBle() }
            val mdnsJob = async { scanMdns() }

            val wifiDevices = wifiJob.await()
            bleJob.await()
            mdnsJob.await()

            // Enrich WiFi devices with mDNS hints
            val enrichedWifi = wifiDevices.map { device ->
                val mdnsHint = discoveredMdns.entries
                    .firstOrNull { it.key.contains(device.bssid.takeLast(5), ignoreCase = true) }
                    ?.value
                device.copy(modelHint = mdnsHint ?: device.modelHint)
            }

            val sig = RfSignature(
                wifiDevices = enrichedWifi,
                bleDevices = discoveredBle.toList(),
                nfcTags = emptyList() // NFC requires user tap — handled separately
            )

            _signature.value = sig
            _state.value = State.COMPLETE
            sig
        } catch (e: Exception) {
            Log.e(TAG, "RF scan failed", e)
            _state.value = State.ERROR
            RfSignature()
        }
    }

    /**
     * Scan WiFi networks, extract manufacturer from MAC prefix.
     */
    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE
    ])
    private fun scanWifi(): List<WifiDeviceInfo> {
        val results = wifiManager.scanResults ?: return emptyList()
        return results.map { result ->
            val macPrefix = result.BSSID?.take(8)?.uppercase() ?: ""
            val manufacturer = OUI_MAP[macPrefix]

            WifiDeviceInfo(
                ssid = result.SSID,
                bssid = result.BSSID ?: "",
                macPrefix = macPrefix,
                signalStrength = result.level,
                modelHint = manufacturer
            )
        }.sortedByDescending { it.signalStrength } // Closest first
    }

    /**
     * BLE scan to discover smart TVs, speakers, IoT devices.
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    private suspend fun scanBle() {
        val scanner: BluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val existing = discoveredBle.indexOfFirst { it.address == device.address }

                val info = BleDeviceInfo(
                    name = try { device.name } catch (_: SecurityException) { null },
                    address = device.address,
                    serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList(),
                    manufacturerData = result.scanRecord?.bytes,
                    rssi = result.rssi
                )

                synchronized(discoveredBle) {
                    if (existing >= 0) discoveredBle[existing] = info
                    else discoveredBle.add(info)
                }
            }
        }

        scanner.startScan(null, settings, callback)
        delay(BLE_SCAN_DURATION_MS)
        scanner.stopScan(callback)
    }

    /**
     * mDNS/Bonjour discovery for Chromecast, AirPlay, etc.
     */
    private suspend fun scanMdns() {
        val serviceTypes = MDNS_HINTS.keys.toList()

        val listeners = serviceTypes.map { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    val hint = MDNS_HINTS[serviceType] ?: serviceType
                    synchronized(discoveredMdns) {
                        discoveredMdns[info.serviceName] = hint
                    }
                    Log.d(TAG, "mDNS found: ${info.serviceName} → $hint")
                }
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w(TAG, "mDNS scan failed for $serviceType", e)
            }
            listener
        }

        delay(MDNS_SCAN_DURATION_MS)

        listeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
    }

    /**
     * Lookup manufacturer from MAC prefix (OUI).
     */
    fun lookupManufacturer(macAddress: String): String? {
        val prefix = macAddress.take(8).uppercase()
        return OUI_MAP[prefix]
    }
}
