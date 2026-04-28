package com.andene.spectra.widget

import com.andene.spectra.SpectraApp
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.modules.control.IrControl

/**
 * Shared helper for the home-screen widget + Quick Settings tile.
 *
 * The "primary" device is the most-recently-discovered profile that
 * has a POWER command bound. This is a coarse default; the per-widget
 * configuration activity (B-002 phase 2) and per-tile config (B-003
 * phase 2) will let the user pin a specific device.
 *
 * The fire path is intentionally inline-load-then-send so a cold
 * widget/tile tap (process started just to deliver the broadcast)
 * works even when SpectraApp.appScope's loadKnownDevices hasn't
 * finished yet. saveDevice on IrControl is idempotent on id, so the
 * warm case pays a single extra hash-map write per tap — negligible.
 */
internal object QuickPowerKit {

    fun selectPrimary(devices: List<DeviceProfile>): DeviceProfile? =
        devices
            .filter { it.irProfile?.commands?.containsKey(IrControl.Commands.POWER) == true }
            .maxByOrNull { it.discoveredAt }

    suspend fun firePrimaryPower(app: SpectraApp): Result {
        val devices = app.repository.loadAll()
        val primary = selectPrimary(devices) ?: return Result.NO_DEVICE
        app.orchestrator.control.saveDevice(primary)
        val ok = app.orchestrator.control.sendCommand(primary.id, IrControl.Commands.POWER)
        return if (ok) Result.SENT else Result.TRANSMIT_FAILED
    }

    enum class Result { SENT, NO_DEVICE, TRANSMIT_FAILED }
}
