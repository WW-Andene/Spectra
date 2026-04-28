package com.andene.spectra.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andene.spectra.data.models.BleDeviceInfo
import com.andene.spectra.data.models.CaptureMethod
import com.andene.spectra.data.models.DeviceCategory
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.data.models.IrCommand
import com.andene.spectra.data.models.IrProfile
import com.andene.spectra.data.models.IrProtocol
import com.andene.spectra.data.models.RfSignature
import com.andene.spectra.data.models.WifiDeviceInfo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Round-trip tests for the device-profile JSON layer. These exercise the
 * exact conversion logic the on-disk schema relies on; a regression here
 * silently corrupts user-saved profiles.
 *
 * Runs under Robolectric so we get a real Context (and thus a real filesDir
 * pointed at a temp location) without an emulator.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 34])
class DeviceRepositoryTest {

    private lateinit var repo: DeviceRepository

    @Before
    fun setUp() {
        repo = DeviceRepository(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        // Tests share the temp filesDir; wipe between runs.
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .filesDir.resolve("devices").deleteRecursively()
    }

    @Test
    fun `save then load reproduces full profile`() = runTest {
        val profile = DeviceProfile(
            id = "fixed-id",
            name = "Living Room TV",
            manufacturer = "Samsung",
            model = "Q60A",
            category = DeviceCategory.TV,
            irProfile = IrProfile(
                protocol = IrProtocol.NEC,
                carrierFrequency = 38000,
                commands = mutableMapOf(
                    "power" to IrCommand(
                        name = "power",
                        rawTimings = intArrayOf(9000, 4500, 562, 562),
                        protocol = IrProtocol.NEC,
                        code = 0x12345L,
                        capturedVia = CaptureMethod.LEARNED,
                    ),
                ),
            ),
            rfSignature = RfSignature(
                wifiDevices = listOf(
                    WifiDeviceInfo(
                        ssid = "MyHomeNet",
                        bssid = "F8:0D:60:11:22:33",
                        macPrefix = "F8:0D:60",
                        signalStrength = -42,
                        modelHint = "Samsung",
                    ),
                ),
                bleDevices = listOf(
                    BleDeviceInfo(
                        name = "Samsung Smart TV",
                        address = "AA:BB:CC:DD:EE:FF",
                        serviceUuids = listOf("0000180a-0000-1000-8000-00805f9b34fb"),
                        rssi = -55,
                    ),
                ),
            ),
        )

        assertTrue("save should succeed", repo.save(profile))

        val loaded = repo.loadAll()
        assertEquals("expected exactly the profile we just saved", 1, loaded.size)
        val r = loaded[0]
        assertEquals("fixed-id", r.id)
        assertEquals("Living Room TV", r.name)
        assertEquals("Samsung", r.manufacturer)
        assertEquals("Q60A", r.model)
        assertEquals(DeviceCategory.TV, r.category)
        // IR profile preserved
        assertEquals(IrProtocol.NEC, r.irProfile?.protocol)
        assertEquals(38000, r.irProfile?.carrierFrequency)
        val cmd = r.irProfile?.commands?.get("power")
        assertNotNull(cmd)
        assertEquals("power", cmd!!.name)
        assertEquals(IrProtocol.NEC, cmd.protocol)
        assertEquals(CaptureMethod.LEARNED, cmd.capturedVia)
        assertEquals(0x12345L, cmd.code)
        assertTrue(intArrayOf(9000, 4500, 562, 562).contentEquals(cmd.rawTimings))
        // RF preserved
        val rfWifi = r.rfSignature?.wifiDevices?.firstOrNull()
        assertNotNull(rfWifi)
        assertEquals("F8:0D:60:11:22:33", rfWifi!!.bssid)
        assertEquals("MyHomeNet", rfWifi.ssid)
        assertEquals("Samsung", rfWifi.modelHint)
        val rfBle = r.rfSignature?.bleDevices?.firstOrNull()
        assertNotNull(rfBle)
        assertEquals("AA:BB:CC:DD:EE:FF", rfBle!!.address)
        assertEquals("Samsung Smart TV", rfBle.name)
    }

    @Test
    fun `export then import preserves profile content`() = runTest {
        val original = DeviceProfile(
            id = "original-id",
            name = "TV",
            category = DeviceCategory.TV,
            irProfile = IrProfile(
                protocol = IrProtocol.SAMSUNG,
                commands = mutableMapOf(
                    "vol_up" to IrCommand(
                        name = "vol_up",
                        rawTimings = intArrayOf(4500, 4500, 560),
                        protocol = IrProtocol.SAMSUNG,
                    ),
                ),
            ),
        )
        val json = repo.exportProfile(original)
        val imported = repo.importProfile(json)
        assertNotNull(imported)
        // Import always assigns a fresh id so re-imports don't clash.
        assertTrue("import should produce a new id", imported!!.id != original.id)
        assertEquals(original.name, imported.name)
        assertEquals(original.category, imported.category)
        assertEquals(IrProtocol.SAMSUNG, imported.irProfile?.protocol)
        val cmd = imported.irProfile?.commands?.get("vol_up")
        assertNotNull(cmd)
        assertTrue(intArrayOf(4500, 4500, 560).contentEquals(cmd!!.rawTimings))
    }

    @Test
    fun `corrupted file does not poison subsequent loads`() = runTest {
        // Plant one valid profile and one syntactically invalid one.
        val good = DeviceProfile(id = "good", name = "OK")
        repo.save(good)
        val devicesDir = ApplicationProvider.getApplicationContext<android.content.Context>()
            .filesDir.resolve("devices")
        devicesDir.resolve("bad.json").writeText("{this is not valid json")

        val loaded = repo.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("good", loaded[0].id)
        assertEquals(1, repo.lastLoadSkipCount)
    }

    @Test
    fun `empty repository load returns empty list`() = runTest {
        val loaded = repo.loadAll()
        assertEquals(0, loaded.size)
        assertEquals(0, repo.lastLoadSkipCount)
    }

    @Test
    fun `delete removes a saved profile`() = runTest {
        val p = DeviceProfile(id = "to-delete", name = "Temp")
        repo.save(p)
        assertEquals(1, repo.loadAll().size)
        repo.delete("to-delete")
        assertEquals(0, repo.loadAll().size)
    }

    @Test
    fun `import returns null on garbage input`() {
        assertNull(repo.importProfile("not json at all"))
        assertNull(repo.importProfile(""))
    }
}
