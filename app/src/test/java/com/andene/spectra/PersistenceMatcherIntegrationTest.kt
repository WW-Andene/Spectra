package com.andene.spectra

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andene.spectra.core.matchKnownDevice
import com.andene.spectra.data.models.BleDeviceInfo
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.data.models.RfSignature
import com.andene.spectra.data.models.WifiDeviceInfo
import com.andene.spectra.data.repository.DeviceRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end test for cross-session device re-identification — the headline
 * "scan once, recognise next time" feature.
 *
 * The chain is: save a DeviceProfile with RF signature → reload from disk →
 * present a fresh scan candidate that shares one RF identifier (BSSID or
 * BLE address) → matchKnownDevice should return the saved profile with
 * confidence 1.0.
 *
 * Integration scope intentionally crosses repository + matcher because
 * cycle 1's F-002 fix combined them: RF fields had to land in
 * SerializableDeviceProfile AND survive reload AND be readable by the
 * matcher. If any link drops, the headline feature silently breaks.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 34])
class PersistenceMatcherIntegrationTest {

    private lateinit var repo: DeviceRepository

    @Before
    fun setUp() {
        repo = DeviceRepository(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .filesDir.resolve("devices").deleteRecursively()
    }

    private fun wifi(bssid: String, modelHint: String? = null) = WifiDeviceInfo(
        ssid = "TestNet",
        bssid = bssid,
        macPrefix = bssid.take(8),
        signalStrength = -50,
        modelHint = modelHint,
    )

    private fun ble(address: String, name: String? = null) = BleDeviceInfo(
        name = name,
        address = address,
        serviceUuids = emptyList(),
        rssi = -60,
    )

    @Test
    fun `BSSID match across save and reload identifies the device`() = runTest {
        val livingRoomTv = DeviceProfile(
            id = "living-tv",
            name = "Living Room TV",
            manufacturer = "Samsung",
            rfSignature = RfSignature(
                wifiDevices = listOf(wifi("F8:0D:60:11:22:33", "Samsung")),
            ),
        )
        repo.save(livingRoomTv)

        // Simulate a fresh app launch: load saved devices from disk.
        val known = repo.loadAll()
        assertEquals(1, known.size)

        // Now a new scan picks up the same WiFi AP (different SSID label
        // doesn't matter — BSSID is the radio MAC and is the canonical id).
        val freshCandidate = DeviceProfile(
            rfSignature = RfSignature(
                wifiDevices = listOf(wifi("F8:0D:60:11:22:33")),
            ),
        )

        val match = matchKnownDevice(freshCandidate, known, threshold = 0.75f)
        assertNotNull("the saved device should be re-identified", match)
        assertEquals("living-tv", match!!.id)
        assertEquals("Living Room TV", match.name)
        assertEquals(1f, match.confidence, 0.001f)
    }

    @Test
    fun `BLE address match survives the persistence round-trip`() = runTest {
        val soundbar = DeviceProfile(
            id = "soundbar",
            name = "Kitchen Soundbar",
            rfSignature = RfSignature(
                bleDevices = listOf(ble("AA:BB:CC:DD:EE:FF", "Bose Soundbar 700")),
            ),
        )
        repo.save(soundbar)

        val known = repo.loadAll()
        val candidate = DeviceProfile(
            rfSignature = RfSignature(
                bleDevices = listOf(ble("AA:BB:CC:DD:EE:FF")),
            ),
        )
        val match = matchKnownDevice(candidate, known, threshold = 0.75f)
        assertNotNull(match)
        assertEquals("soundbar", match!!.id)
        // BLE match scores 0.9 — passes the 0.75 threshold.
        assertEquals(0.9f, match.confidence, 0.001f)
    }

    @Test
    fun `manufacturer-only similarity does not falsely re-identify`() = runTest {
        // Two different Samsung devices in the same household. Manufacturer
        // alone (score 0.4) must NOT be enough to claim they're the same
        // — that would mis-identify the bedroom TV as the living-room TV.
        repo.save(DeviceProfile(
            id = "bedroom-tv",
            name = "Bedroom TV",
            rfSignature = RfSignature(
                wifiDevices = listOf(wifi("AA:11:11:11:11:11", "Samsung")),
            ),
        ))
        val known = repo.loadAll()
        val candidate = DeviceProfile(
            rfSignature = RfSignature(
                wifiDevices = listOf(wifi("BB:22:22:22:22:22", "Samsung")),
            ),
        )
        // Score 0.4 < 0.75 threshold → null match.
        assertEquals(null, matchKnownDevice(candidate, known, threshold = 0.75f))
    }

    @Test
    fun `device with empty RF signature does not match anything`() = runTest {
        // Defensive: a candidate built before RF scan completes shouldn't
        // accidentally match a saved profile via the empty-set intersection.
        repo.save(DeviceProfile(
            id = "any",
            rfSignature = RfSignature(wifiDevices = listOf(wifi("AA:BB:CC:DD:EE:FF"))),
        ))
        val known = repo.loadAll()
        val empty = DeviceProfile(rfSignature = RfSignature())
        assertEquals(null, matchKnownDevice(empty, known, threshold = 0.75f))
    }

    @Test
    fun `multiple known devices return the correct match`() = runTest {
        // Library of three; a candidate matching the second should retrieve
        // exactly that one — not the first or a fuzzy aggregate.
        listOf(
            DeviceProfile(id = "alpha", rfSignature = RfSignature(wifiDevices = listOf(wifi("AA:00:00:00:00:01")))),
            DeviceProfile(id = "beta",  rfSignature = RfSignature(wifiDevices = listOf(wifi("BB:00:00:00:00:02")))),
            DeviceProfile(id = "gamma", rfSignature = RfSignature(wifiDevices = listOf(wifi("CC:00:00:00:00:03")))),
        ).forEach { repo.save(it) }

        val known = repo.loadAll()
        val candidate = DeviceProfile(
            rfSignature = RfSignature(wifiDevices = listOf(wifi("BB:00:00:00:00:02"))),
        )
        val match = matchKnownDevice(candidate, known, threshold = 0.75f)
        assertEquals("beta", match!!.id)
    }
}
