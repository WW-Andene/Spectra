package com.andene.spectra.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-schedules a pending sleep timer after device reboot.
 *
 * AlarmManager doesn't survive reboot — every pending alarm is wiped
 * when the system goes down. SleepTimer persists the active-timer
 * record (fire-at timestamp + payload-ish label) in SharedPreferences,
 * but the actual alarm slot is gone. On BOOT_COMPLETED we read that
 * persisted record and re-arm the alarm with the same fire-at time.
 *
 * Caveat: SleepTimer's prefs only store the label and fire-at time, not
 * the macro id / device id needed to fire. To survive reboot we'd
 * need to also store the dispatch payload. For now (B-005 phase 2)
 * this receiver is wired but a no-op when the persisted state is
 * insufficient — phase 2.5 will widen the persisted record to include
 * the dispatch extras so the reboot-survives loop is closed.
 */
class BootRescheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootRescheduleReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val active = SleepTimer.active(context)
        if (active == null) {
            Log.d(TAG, "no pending sleep timer to reschedule")
            return
        }

        // The dispatch payload (macro id or command extras) lives in
        // [SleepTimer]'s extended persisted state. If it's missing, we
        // can't rebuild the alarm and we just clear the stale record so
        // the home banner doesn't lie about a phantom timer.
        val payload = SleepTimer.persistedPayload(context)
        if (payload == null) {
            Log.w(TAG, "active timer record present but no payload — clearing")
            SleepTimer.clearActive(context)
            return
        }

        // Re-arm the alarm at the original fire-at. If that's already in
        // the past (reboot took longer than the timer), fire it
        // immediately by setting fireAt = now + 1s so the user still
        // gets the action they asked for, just delayed.
        val fireAt = if (active.fireAtMs <= System.currentTimeMillis())
            System.currentTimeMillis() + 1_000L else active.fireAtMs

        val intent2 = Intent(context, ScheduledFireReceiver::class.java).apply {
            action = ScheduledFireReceiver.ACTION_FIRE
            putExtra(ScheduledFireReceiver.EXTRA_KIND, payload.kind)
            payload.macroId?.let { putExtra(ScheduledFireReceiver.EXTRA_MACRO_ID, it) }
            payload.deviceId?.let { putExtra(ScheduledFireReceiver.EXTRA_DEVICE_ID, it) }
            payload.commandName?.let { putExtra(ScheduledFireReceiver.EXTRA_COMMAND_NAME, it) }
        }
        val pi = PendingIntent.getBroadcast(
            context,
            SleepTimer.PI_REQUEST_CODE,
            intent2,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        Log.i(TAG, "rescheduled ${payload.kind} timer for ${fireAt - System.currentTimeMillis()}ms from now")
    }
}
