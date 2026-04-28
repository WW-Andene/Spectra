package com.andene.spectra.core

import com.andene.spectra.data.models.BleDeviceInfo
import com.andene.spectra.data.models.DeviceCategory
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.data.models.RfSignature
import com.andene.spectra.data.models.WifiDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure RF-matching + identity-inference logic in Matching.kt.
 * These run on the host JVM; no Robolectric or Context needed.
 *
 * The matcher is the heart of Spectra's "recognise a device on next scan"
 * feature. A regression here breaks the headline use case.
 */
class MatchingTest {

    private fun wifi(bssid: String, modelHint: String? = null) =
        WifiDeviceInfo(ssid = "ap", bssid = bssid, macPrefix = bssid.take(8), signalStrength = -50, modelHint = modelHint)

    private fun ble(address: String, name: String? = null) =
        BleDeviceInfo(name = name, address = address, serviceUuids = emptyList(), rssi = -60)

    // ── compareRf ────────────────────────────────────────────────────

    @Test
    fun `BSSID hit scores 1`() {
        val a = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:11:22:33")))
        val b = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:11:22:33")))
        assertEquals(1f, compareRf(a, b), 0.001f)
    }

    @Test
    fun `BLE address hit scores 0_9`() {
        val a = RfSignature(bleDevices = listOf(ble("DE:AD:BE:EF:00:01")))
        val b = RfSignature(bleDevices = listOf(ble("DE:AD:BE:EF:00:01")))
        assertEquals(0.9f, compareRf(a, b), 0.001f)
    }

    @Test
    fun `manufacturer-only match scores 0_4`() {
        val a = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:00:00:01", "Samsung")))
        val b = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:00:00:02", "Samsung")))
        assertEquals(0.4f, compareRf(a, b), 0.001f)
    }

    @Test
    fun `no overlap scores 0`() {
        val a = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:00:00:01")))
        val b = RfSignature(wifiDevices = listOf(wifi("DD:EE:FF:00:00:01")))
        assertEquals(0f, compareRf(a, b), 0.001f)
    }

    @Test
    fun `BSSID hit dominates BLE manufacturer mismatch`() {
        val a = RfSignature(
            wifiDevices = listOf(wifi("AA:BB:CC:00:00:01", "Samsung")),
            bleDevices = listOf(ble("DE:AD:00:00:00:01")),
        )
        val b = RfSignature(
            wifiDevices = listOf(wifi("AA:BB:CC:00:00:01", "LG Electronics")),
            bleDevices = listOf(ble("BE:EF:00:00:00:02")),
        )
        // BSSID match is exact — manufacturer mismatch shouldn't drag it down.
        assertEquals(1f, compareRf(a, b), 0.001f)
    }

    // ── matchKnownDevice ─────────────────────────────────────────────

    @Test
    fun `match returns null when no candidate signature`() {
        val candidate = DeviceProfile()
        val known = listOf(DeviceProfile(rfSignature = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:00:00:01")))))
        assertNull(matchKnownDevice(candidate, known, 0.75f))
    }

    @Test
    fun `match returns null below threshold`() {
        val candidate = DeviceProfile(
            rfSignature = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:00:00:01", "Samsung"))),
        )
        val known = listOf(DeviceProfile(
            rfSignature = RfSignature(wifiDevices = listOf(wifi("DD:EE:FF:00:00:01", "Samsung"))),
        ))
        // manufacturer-only match scores 0.4 — below the standard 0.75 threshold.
        assertNull(matchKnownDevice(candidate, known, 0.75f))
    }

    @Test
    fun `match returns best score device when tied above threshold`() {
        val target = DeviceProfile(
            id = "winner",
            name = "Living Room TV",
            rfSignature = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:00:00:01"))),
        )
        val candidate = DeviceProfile(
            rfSignature = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:00:00:01"))),
        )
        val known = listOf(
            DeviceProfile(id = "loser-ble", rfSignature = RfSignature(bleDevices = listOf(ble("BE:EF:00:00:00:02")))),
            target,
        )
        val match = matchKnownDevice(candidate, known, 0.75f)
        assertNotNull(match)
        assertEquals("winner", match!!.id)
        assertEquals(1f, match.confidence, 0.001f)
    }

    @Test
    fun `match copies confidence into the returned profile`() {
        val target = DeviceProfile(
            id = "ble-target",
            rfSignature = RfSignature(bleDevices = listOf(ble("AA:BB:CC:DD:EE:FF"))),
        )
        val candidate = DeviceProfile(
            rfSignature = RfSignature(bleDevices = listOf(ble("AA:BB:CC:DD:EE:FF"))),
        )
        val match = matchKnownDevice(candidate, listOf(target), 0.75f)
        assertNotNull(match)
        assertEquals(0.9f, match!!.confidence, 0.001f)
    }

    // ── inferIdentity ────────────────────────────────────────────────

    @Test
    fun `wifi modelHint wins over BLE name for manufacturer`() {
        val rf = RfSignature(
            wifiDevices = listOf(wifi("00:00:00:00:00:01", "LG Electronics")),
            bleDevices = listOf(ble("AA:BB:CC:DD:EE:FF", "Samsung Galaxy Buds")),
        )
        val (mfg, _) = inferIdentity(rf)
        assertEquals("LG Electronics", mfg)
    }

    @Test
    fun `falls back to BLE keyword when wifi has no hint`() {
        val rf = RfSignature(
            wifiDevices = listOf(wifi("00:00:00:00:00:01", null)),
            bleDevices = listOf(ble("AA:BB:CC:DD:EE:FF", "Sony WH-1000XM5")),
        )
        val (mfg, _) = inferIdentity(rf)
        assertEquals("Sony", mfg)
    }

    @Test
    fun `inferIdentity returns null when no signal`() {
        val rf = RfSignature()
        val (mfg, cat) = inferIdentity(rf)
        assertNull(mfg)
        assertEquals(DeviceCategory.UNKNOWN, cat)
    }

    @Test
    fun `chromecast mdns hint maps to SET_TOP_BOX`() {
        val rf = RfSignature(
            wifiDevices = listOf(wifi("00:00:00:00:00:01", "Chromecast / Android TV")),
        )
        val (_, cat) = inferIdentity(rf)
        assertEquals(DeviceCategory.SET_TOP_BOX, cat)
    }

    @Test
    fun `airplay mdns hint maps to TV`() {
        val rf = RfSignature(
            wifiDevices = listOf(wifi("00:00:00:00:00:01", "Apple AirPlay device")),
        )
        val (_, cat) = inferIdentity(rf)
        assertEquals(DeviceCategory.TV, cat)
    }

    @Test
    fun `sonos mdns hint maps to SPEAKER`() {
        val rf = RfSignature(
            wifiDevices = listOf(wifi("00:00:00:00:00:01", "Sonos speaker")),
        )
        val (_, cat) = inferIdentity(rf)
        assertEquals(DeviceCategory.SPEAKER, cat)
    }

    // ── extractManufacturerFromBleName ───────────────────────────────

    @Test
    fun `BLE name keyword extraction is case-insensitive`() {
        assertEquals("Samsung", extractManufacturerFromBleName("SAMSUNG-Q60A"))
        assertEquals("Sony", extractManufacturerFromBleName("Sony WH-1000XM5"))
        assertEquals("Apple", extractManufacturerFromBleName("Joe's AirPods"))
    }

    @Test
    fun `unknown BLE name returns null`() {
        assertNull(extractManufacturerFromBleName("XYZ Brand"))
        assertNull(extractManufacturerFromBleName(null))
        assertNull(extractManufacturerFromBleName(""))
    }

    @Test
    fun `xiaomi 'Mi ' prefix recognised`() {
        assertEquals("Xiaomi", extractManufacturerFromBleName("Mi Box S"))
        assertEquals("Xiaomi", extractManufacturerFromBleName("xiaomi-router"))
    }
}
