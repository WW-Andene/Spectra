package com.andene.spectra.core

import com.andene.spectra.data.models.DeviceCategory
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.data.models.RfSignature

/**
 * Pure-Kotlin device-matching helpers split out of SpectraOrchestrator so
 * they're testable without an Android runtime. The orchestrator delegates
 * to these.
 *
 * RF is the primary identifier per the architectural decision in cycle 1
 * (acoustic/EM are too unstable cross-session). All three functions here
 * are deterministic on their inputs alone — no side effects, no Context.
 */

/** Score 0–1 that two RF signatures describe the same device.
 *  - 1.0  exact BSSID hit (WiFi MAC; locally-administered/randomized addresses
 *         can drift but typical AP MACs are stable)
 *  - 0.9  BLE address hit (slightly less reliable post-Android-11
 *         randomisation)
 *  - 0.4  manufacturer-only match — too weak to identify alone
 *  - 0.0  no overlap
 */
internal fun compareRf(a: RfSignature, b: RfSignature): Float {
    val commonBssids = a.wifiDevices.map { it.bssid }.intersect(b.wifiDevices.map { it.bssid }.toSet())
    if (commonBssids.isNotEmpty()) return 1f

    val commonBle = a.bleDevices.map { it.address }.intersect(b.bleDevices.map { it.address }.toSet())
    if (commonBle.isNotEmpty()) return 0.9f

    val aManufacturers = a.wifiDevices.mapNotNull { it.modelHint }.toSet()
    val bManufacturers = b.wifiDevices.mapNotNull { it.modelHint }.toSet()
    if (aManufacturers.intersect(bManufacturers).isNotEmpty()) return 0.4f

    return 0f
}

/** Best-effort manufacturer + category guess from the strongest RF hits. */
internal fun inferIdentity(rf: RfSignature): Pair<String?, DeviceCategory> {
    val topWifi = rf.wifiDevices.firstOrNull { !it.modelHint.isNullOrBlank() }
    val topBle = rf.bleDevices.firstOrNull { !it.name.isNullOrBlank() }

    val manufacturer = topWifi?.modelHint
        ?: extractManufacturerFromBleName(topBle?.name)

    return manufacturer to guessCategory(rf)
}

internal fun extractManufacturerFromBleName(name: String?): String? {
    if (name.isNullOrBlank()) return null
    val lower = name.lowercase()
    return when {
        "samsung" in lower -> "Samsung"
        "lg" in lower -> "LG Electronics"
        "sony" in lower -> "Sony"
        "roku" in lower -> "Roku"
        "sonos" in lower -> "Sonos"
        "bose" in lower -> "Bose"
        "apple" in lower || "airpods" in lower -> "Apple"
        "xiaomi" in lower || lower.startsWith("mi ") -> "Xiaomi"
        else -> null
    }
}

internal fun guessCategory(rf: RfSignature): DeviceCategory {
    // mDNS service hints (loaded into modelHint by RfFingerprint) are the
    // most reliable categoriser.
    val mdnsHints = rf.wifiDevices.mapNotNull { it.modelHint?.lowercase() }
    if (mdnsHints.any { "chromecast" in it || "android tv" in it || "fire tv" in it }) {
        return DeviceCategory.SET_TOP_BOX
    }
    if (mdnsHints.any { "airplay" in it || "tv" in it }) {
        return DeviceCategory.TV
    }
    if (mdnsHints.any { "spotify" in it || "sonos" in it || "airplay audio" in it }) {
        return DeviceCategory.SPEAKER
    }

    val bleNames = rf.bleDevices.mapNotNull { it.name?.lowercase() }
    if (bleNames.any { "tv" in it || "soundbar" in it && "tv" in it }) return DeviceCategory.TV
    if (bleNames.any { "speaker" in it || "soundbar" in it || "audio" in it }) {
        return DeviceCategory.SPEAKER
    }

    return DeviceCategory.UNKNOWN
}

/**
 * Find the best match for a candidate against a list of previously-saved
 * profiles, RF-only.
 *
 * @param threshold the minimum score the matcher will accept; below this it
 *   returns null.
 */
internal fun matchKnownDevice(
    candidate: DeviceProfile,
    known: List<DeviceProfile>,
    threshold: Float,
): DeviceProfile? = matchTopN(candidate, known, threshold, n = 1).firstOrNull()

/**
 * Find up to [n] top-scoring matches for a candidate, sorted by score
 * descending. Used by B-102 multi-device disambiguation: when a scan
 * lands on a room with multiple registered devices that share parts
 * of their RF signature (two TVs on the same WiFi AP, or a TV +
 * soundbar with overlapping BLE), the UI surfaces alternates so the
 * user can correct an ambiguous auto-pick.
 *
 * Returned profiles each have [DeviceProfile.confidence] set to their
 * computed score so the caller can render confidence indicators.
 * Profiles with score < [threshold] are filtered out — same gate as
 * the single-best [matchKnownDevice].
 */
internal fun matchTopN(
    candidate: DeviceProfile,
    known: List<DeviceProfile>,
    threshold: Float,
    n: Int = 3,
): List<DeviceProfile> {
    val candidateRf = candidate.rfSignature ?: return emptyList()
    return known
        .mapNotNull { k ->
            val score = k.rfSignature?.let { compareRf(candidateRf, it) } ?: return@mapNotNull null
            if (score < threshold) null else k.copy(confidence = score)
        }
        .sortedByDescending { it.confidence }
        .take(n)
}
