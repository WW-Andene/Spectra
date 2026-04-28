package com.andene.spectra.widget

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.andene.spectra.R
import com.andene.spectra.SpectraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings tile (B-003 phase 1) — fires POWER on the primary
 * saved device when the user taps it from the notification shade,
 * including from the lock screen.
 *
 * Same primary-device definition as the home-screen widget
 * (QuickPowerKit.selectPrimary). Phase 2 will add per-tile
 * configuration so a user can pin a specific macro to the tile
 * instead of the bare power command.
 *
 * State machine:
 *  - STATE_INACTIVE while a transmit is in flight (visual feedback)
 *  - STATE_UNAVAILABLE when no device with POWER is saved
 *  - STATE_ACTIVE otherwise
 *
 * onClick suspends through the IR transmit using a detached scope.
 * TileService.onClick is called on the main thread; we don't block
 * it. The state flip-flop happens around the suspend so the user
 * sees the tile briefly dim while transmitting.
 */
class SpectraQuickTile : TileService() {

    companion object { private const val TAG = "QuickTile" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        // Refresh state each time the shade opens — the user may have
        // added or removed their primary device since we last rendered.
        scope.launch { refreshTileState() }
    }

    override fun onClick() {
        val tile = qsTile ?: return
        val app = applicationContext as? SpectraApp
        if (app == null) {
            Log.w(TAG, "Application not SpectraApp — tile tap ignored")
            return
        }

        // Brief visual dim while transmitting. STATE_INACTIVE renders
        // the tile in the muted color so the user gets feedback that
        // the tap registered even on a phone where the IR burst itself
        // is silent.
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()

        scope.launch {
            val result = try {
                QuickPowerKit.firePrimaryPower(app)
            } catch (e: Exception) {
                Log.e(TAG, "Tile fire failed", e)
                QuickPowerKit.Result.TRANSMIT_FAILED
            }
            // Refresh state — onStartListening may have already returned
            // by the time we get here (tile out of foreground), so this
            // is a best-effort redraw rather than a guarantee.
            refreshTileState(lastResult = result)
        }
    }

    private suspend fun refreshTileState(lastResult: QuickPowerKit.Result? = null) {
        val tile = qsTile ?: return
        val app = applicationContext as? SpectraApp ?: return
        val primary = QuickPowerKit.selectPrimary(app.repository.loadAll())

        if (primary == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.qs_tile_label)
            tile.contentDescription = getString(R.string.widget_no_device)
            tile.subtitle = null
        } else {
            // Always ACTIVE when a primary device is available — the
            // INACTIVE flicker during a send is set in onClick before
            // the suspend, this is the post-send return-to-ready
            // redraw. lastResult is logged but doesn't gate the state
            // because a TRANSMIT_FAILED still leaves the tile
            // tappable (the user may want to retry).
            if (lastResult == QuickPowerKit.Result.TRANSMIT_FAILED) {
                Log.w(TAG, "Last transmit failed; tile returning to ACTIVE for retry")
            }
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.qs_tile_label)
            tile.subtitle = primary.name ?: getString(R.string.device_default_label)
            tile.contentDescription = getString(
                R.string.qs_tile_content_description_format,
                primary.name ?: getString(R.string.device_default_label),
            )
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_power)
        tile.updateTile()
    }
}
