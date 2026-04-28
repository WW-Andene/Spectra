package com.andene.spectra.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.andene.spectra.SpectraApp
import com.andene.spectra.modules.control.IrControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives an AlarmManager-fired intent and runs either a macro or a
 * one-off IR command. Used by the sleep-timer feature (B-005 phase 1).
 *
 * Two flavours via the [EXTRA_KIND] extra:
 *  - KIND_MACRO with [EXTRA_MACRO_ID]
 *  - KIND_COMMAND with [EXTRA_DEVICE_ID] + [EXTRA_COMMAND_NAME]
 *
 * Cold-process safety: uses goAsync so the IR transmit (which can be
 * 50-200ms per burst, plus per-step delays for macros) finishes
 * before Android can kill the process. Devices are inline-loaded
 * from the repository so a cold-start fire works even when
 * SpectraApp.appScope's loadKnownDevices coroutine hasn't completed.
 */
class ScheduledFireReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledFireReceiver"
        const val ACTION_FIRE = "com.andene.spectra.scheduling.action.FIRE_SCHEDULED"
        const val EXTRA_KIND = "kind"
        const val EXTRA_MACRO_ID = "macroId"
        const val EXTRA_DEVICE_ID = "deviceId"
        const val EXTRA_COMMAND_NAME = "commandName"
        const val KIND_MACRO = "macro"
        const val KIND_COMMAND = "command"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val app = context.applicationContext as? SpectraApp ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return

        val pending = goAsync()
        scope.launch {
            try {
                when (kind) {
                    KIND_COMMAND -> {
                        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return@launch
                        val commandName = intent.getStringExtra(EXTRA_COMMAND_NAME) ?: return@launch
                        val device = app.repository.load(deviceId) ?: run {
                            Log.w(TAG, "Scheduled fire: device $deviceId not found")
                            return@launch
                        }
                        app.orchestrator.control.saveDevice(device)
                        app.orchestrator.control.sendCommand(deviceId, commandName)
                    }
                    KIND_MACRO -> {
                        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return@launch
                        runMacroFromPersistence(app, macroId)
                    }
                    else -> Log.w(TAG, "Unknown kind: $kind")
                }
                // Clear the persisted timer record now that it fired so
                // the home banner doesn't keep advertising a past timer.
                SleepTimer.clearActive(context)
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled fire failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun runMacroFromPersistence(app: SpectraApp, macroId: String) {
        val macros = app.macroRepository.loadAll()
        val macro = macros.firstOrNull { it.id == macroId } ?: run {
            Log.w(TAG, "Scheduled macro $macroId no longer exists")
            return
        }
        // Hydrate every device referenced by the macro so a cold-start
        // fire doesn't silently no-op steps that haven't been loaded
        // into IrControl's registry yet.
        val needed = macro.steps.map { it.deviceId }.distinct()
        for (deviceId in needed) {
            if (app.orchestrator.control.devices.value[deviceId] == null) {
                app.repository.load(deviceId)?.let { app.orchestrator.control.saveDevice(it) }
            }
        }
        for (step in macro.steps) {
            if (step.delayBeforeMs > 0) kotlinx.coroutines.delay(step.delayBeforeMs.toLong())
            app.orchestrator.control.sendCommand(step.deviceId, step.commandName)
        }
    }
}
