package com.andene.spectra.modules.rf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The OUI table is just a Map<String, String> in the companion object,
 * but it's the bridge between raw BSSID bytes and the brand picker —
 * a typo in a key here means a Samsung TV gets shown as "unknown" and
 * never benefits from brand-narrowed brute force.
 */
class OuiLookupTest {

    @Test
    fun `known Samsung prefix resolves to Samsung`() {
        assertEquals("Samsung", RfFingerprint.OUI_MAP["F8:0D:60"])
    }

    @Test
    fun `known LG prefix resolves to LG`() {
        assertEquals("LG Electronics", RfFingerprint.OUI_MAP["00:E0:91"])
    }

    @Test
    fun `unknown prefix returns null`() {
        assertNull(RfFingerprint.OUI_MAP["DE:AD:BE"])
    }

    @Test
    fun `OUI keys are uppercase XX colon XX colon XX format`() {
        val pattern = Regex("^[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}$")
        for (key in RfFingerprint.OUI_MAP.keys) {
            assert(pattern.matches(key)) { "Bad OUI key format: $key" }
        }
    }

    @Test
    fun `OUI table covers the major TV brands`() {
        val brands = RfFingerprint.OUI_MAP.values.toSet()
        listOf("Samsung", "LG Electronics", "Sony").forEach {
            assert(brands.contains(it)) { "Expected '$it' to appear in OUI_MAP" }
        }
    }
}
