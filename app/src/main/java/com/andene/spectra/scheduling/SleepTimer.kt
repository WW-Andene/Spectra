package com.andene.spectra.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Sleep-timer scheduling utilities (B-005 phase 1).
 *
 * Inexact alarm via AlarmManager.setAndAllowWhileIdle — fires within
 * a few-minute window of the requested time, runs even when the
 * phone is in Doze, and crucially does NOT need the
 * SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM permission Android 12+
 * tightened around exact alarms. For a "turn off TV at 11pm-ish"
 * use case the slack is fine; users who need second-precision
 * scheduling can use the macro editor's per-step delays instead.
 *
 * Persistence: the active-timer record (target macro/command +
 * fire-at timestamp) is kept in SharedPreferences so the home banner
 * can show a countdown across process death and the receiver can
 * clear it when the alarm actually fires. Reboots will lose
 * pending alarms — Android's AlarmManager doesn't survive reboot
 * without RECEIVE_BOOT_COMPLETED + a re-scheduling broadcast
 * receiver, queued for phase 2.
 */
object SleepTimer {

    private const val PREFS = "spectra_sleep_timer"
    private const val KEY_FIRE_AT = "fireAt"
    private const val KEY_LABEL = "label"
    private const val KEY_KIND = "kind"
    private const val KEY_MACRO_ID = "macroId"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val KEY_COMMAND_NAME = "commandName"
    internal const val PI_REQUEST_CODE = 0x5757  // arbitrary stable

    data class Active(val fireAtMs: Long, val label: String) {
        val remainingMs: Long get() = (fireAtMs - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /** Payload needed to re-build the dispatch Intent after a reboot wipes
     *  the AlarmManager slot. [BootRescheduleReceiver] reads this, the
     *  schedule* methods write it. */
    data class Payload(
        val kind: String,
        val macroId: String? = null,
        val deviceId: String? = null,
        val commandName: String? = null,
    )

    fun scheduleMacro(
        context: Context,
        macroId: String,
        macroLabel: String,
        delayMinutes: Int,
    ) {
        val fireAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val payload = Payload(kind = ScheduledFireReceiver.KIND_MACRO, macroId = macroId)
        scheduleInternal(context, payload, fireAt, macroLabel)
    }

    fun scheduleCommand(
        context: Context,
        deviceId: String,
        deviceLabel: String,
        commandName: String,
        delayMinutes: Int,
    ) {
        val fireAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val payload = Payload(
            kind = ScheduledFireReceiver.KIND_COMMAND,
            deviceId = deviceId,
            commandName = commandName,
        )
        scheduleInternal(context, payload, fireAt, "$deviceLabel · $commandName")
    }

    private fun scheduleInternal(context: Context, payload: Payload, fireAt: Long, label: String) {
        val intent = Intent(context, ScheduledFireReceiver::class.java).apply {
            action = ScheduledFireReceiver.ACTION_FIRE
            putExtra(ScheduledFireReceiver.EXTRA_KIND, payload.kind)
            payload.macroId?.let { putExtra(ScheduledFireReceiver.EXTRA_MACRO_ID, it) }
            payload.deviceId?.let { putExtra(ScheduledFireReceiver.EXTRA_DEVICE_ID, it) }
            payload.commandName?.let { putExtra(ScheduledFireReceiver.EXTRA_COMMAND_NAME, it) }
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            PI_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // setAndAllowWhileIdle: inexact, but allowed to fire during Doze.
        // The previous slot (if any) shares the same request code so
        // FLAG_UPDATE_CURRENT replaces it — we never have two timers
        // queued simultaneously, matching the user's mental model.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_FIRE_AT, fireAt)
            .putString(KEY_LABEL, label)
            .putString(KEY_KIND, payload.kind)
            .putString(KEY_MACRO_ID, payload.macroId)
            .putString(KEY_DEVICE_ID, payload.deviceId)
            .putString(KEY_COMMAND_NAME, payload.commandName)
            .apply()
    }

    /** Read the persisted payload (B-005 phase 2). Used by
     *  [BootRescheduleReceiver] to rebuild the dispatch Intent after a
     *  reboot wipes the AlarmManager slot. Returns null if the
     *  payload is incomplete (eg. timer was scheduled before this
     *  field was added to the persisted record). */
    fun persistedPayload(context: Context): Payload? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kind = prefs.getString(KEY_KIND, null) ?: return null
        return Payload(
            kind = kind,
            macroId = prefs.getString(KEY_MACRO_ID, null),
            deviceId = prefs.getString(KEY_DEVICE_ID, null),
            commandName = prefs.getString(KEY_COMMAND_NAME, null),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Recreate a matching PendingIntent (same intent action + request
        // code) and cancel via the AlarmManager. FLAG_NO_CREATE returns
        // null when no matching pending intent exists, which is fine —
        // we still clear the prefs record below.
        val intent = Intent(context, ScheduledFireReceiver::class.java).apply {
            action = ScheduledFireReceiver.ACTION_FIRE
        }
        val pi = PendingIntent.getBroadcast(
            context,
            PI_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
        clearActive(context)
    }

    fun active(context: Context): Active? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fireAt = prefs.getLong(KEY_FIRE_AT, 0L)
        if (fireAt <= 0L || fireAt < System.currentTimeMillis()) {
            // Stale or absent — clean up so subsequent reads return null.
            if (fireAt > 0L) clearActive(context)
            return null
        }
        val label = prefs.getString(KEY_LABEL, null) ?: return null
        return Active(fireAt, label)
    }

    fun clearActive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
