package com.andene.spectra.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.andene.spectra.SpectraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives a tap from a Spectra home-screen widget and fires the
 * configured IR command without opening the activity.
 *
 * Cold-process startup race: a widget tap can wake the app process
 * just to deliver this broadcast. SpectraApp.onCreate runs first, but
 * its appScope coroutine that calls `orchestrator.loadKnownDevices(...)`
 * is async and may not have completed by the time onReceive fires. We
 * reload the specific device from the repository inline (a single
 * cheap JSON read) and re-register it on `IrControl` before sending,
 * so the widget tap works even when the orchestrator's in-memory
 * registry isn't yet hydrated.
 */
class WidgetCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetCmdReceiver"
        const val ACTION_FIRE = "com.andene.spectra.widget.action.FIRE_COMMAND"
        const val EXTRA_DEVICE_ID = "deviceId"
        const val EXTRA_COMMAND_NAME = "commandName"
    }

    // Detached scope so the suspend transmit outlives the receiver's
    // brief onReceive window. goAsync() keeps the broadcast pending
    // until pendingResult.finish() — without it, Android can kill the
    // process before the IR burst finishes (transmit blocks ~50–200ms).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        val commandName = intent.getStringExtra(EXTRA_COMMAND_NAME) ?: return

        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as? SpectraApp
                if (app == null) {
                    Log.w(TAG, "Application not SpectraApp — widget tap ignored")
                    return@launch
                }
                // Re-hydrate this one device into IrControl. saveDevice is
                // idempotent on id, so if loadKnownDevices already ran this
                // is a no-op cost; if it hasn't, this is the cheapest way
                // to make sure sendCommand has the IR profile.
                val device = app.repository.load(deviceId)
                if (device == null) {
                    Log.w(TAG, "Device $deviceId not found — widget stale")
                    return@launch
                }
                app.orchestrator.control.saveDevice(device)
                app.orchestrator.control.sendCommand(deviceId, commandName)
            } catch (e: Exception) {
                Log.e(TAG, "Widget command failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
