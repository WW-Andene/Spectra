package com.andene.spectra.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.andene.spectra.R
import com.andene.spectra.SpectraApp
import com.andene.spectra.modules.control.IrControl
import com.andene.spectra.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 1×1 home-screen widget that fires a POWER command to the user's
 * primary saved device. "Primary" is the most-recently-discovered
 * device (highest `discoveredAt` timestamp) — a coarse but reasonable
 * proxy until the configuration activity in B-002 phase 2 lets the
 * user pick explicitly.
 *
 * If no devices are saved, the widget falls back to a "Open Spectra"
 * tile that launches MainActivity.
 *
 * Update cadence is intentionally low — onUpdate just refreshes labels,
 * so we let `updatePeriodMillis` in widget_info_quick.xml drive a
 * one-per-day refresh and rely on explicit refreshes (after a save /
 * delete) when the library actually changes. The activity broadcasts
 * ACTION_APPWIDGET_UPDATE through requestRefresh() to drive that.
 */
class SpectraQuickWidget : AppWidgetProvider() {

    companion object {
        /** Fire a one-shot widget refresh from app code (post-save,
         *  post-delete, post-restore). */
        fun requestRefresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpectraQuickWidget::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, SpectraQuickWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // goAsync keeps the broadcast pending until pendingResult.finish() —
        // without it, a fresh-process widget update launches the IO coroutine
        // but the system can kill the process the moment onReceive (which
        // dispatches to onUpdate) returns. The repository.loadAll() suspend
        // would never complete and the widget would render with the default
        // initialLayout's placeholder text.
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as? SpectraApp ?: return@launch
                val devices = app.repository.loadAll()
                // Auto-pick fallback for widgets pinned before the
                // configuration activity shipped (B-002 phase 1) — those
                // widgets have no per-id binding in WidgetConfigStore so
                // onUpdate uses QuickPowerKit's primary-device pick.
                val autoPrimary = QuickPowerKit.selectPrimary(devices) ?: devices.firstOrNull()

                for (id in appWidgetIds) {
                    val target = WidgetConfigStore.get(context, id)?.let { binding ->
                        // Look up the bound device. If it was deleted
                        // since the widget was configured, fall through
                        // to autoPrimary so the tile keeps working.
                        devices.firstOrNull { it.id == binding.deviceId }
                    } ?: autoPrimary
                    val commandName = WidgetConfigStore.get(context, id)?.commandName
                        ?: IrControl.Commands.POWER
                    val views = buildViews(context, target, commandName)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Drop per-widget bindings so the SharedPreferences file doesn't
        // grow unbounded over years of widget churn.
        WidgetConfigStore.clear(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    private fun buildViews(
        context: Context,
        target: com.andene.spectra.data.models.DeviceProfile?,
        commandName: String,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_command)

        if (target == null || target.irProfile?.commands?.containsKey(commandName) != true) {
            // No usable device → tile becomes a "launch app" affordance.
            views.setTextViewText(
                R.id.widgetDeviceName,
                context.getString(R.string.widget_no_device),
            )
            views.setTextViewText(R.id.widgetActionLabel, context.getString(R.string.widget_open_app))
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openApp)
            return views
        }

        views.setTextViewText(
            R.id.widgetDeviceName,
            target.name ?: context.getString(R.string.device_default_label),
        )
        // Render the command name as the action label. POWER stays
        // localized via R.string.btn_power; other commands fall back
        // to the raw name (UPPERCASED) until per-command label maps
        // exist.
        val actionLabel = if (commandName == IrControl.Commands.POWER) {
            context.getString(R.string.btn_power)
        } else {
            commandName.uppercase()
        }
        views.setTextViewText(R.id.widgetActionLabel, actionLabel)

        // Tap = fire the bound command. Use device-id+command as the
        // requestCode + URI so two widgets pinned to different
        // devices/commands don't clobber each other's PendingIntent
        // extras (PendingIntent.filterEquals doesn't compare extras —
        // distinct request codes + distinct data URIs disambiguate).
        val fireIntent = Intent(context, WidgetCommandReceiver::class.java).apply {
            action = WidgetCommandReceiver.ACTION_FIRE
            putExtra(WidgetCommandReceiver.EXTRA_DEVICE_ID, target.id)
            putExtra(WidgetCommandReceiver.EXTRA_COMMAND_NAME, commandName)
            data = android.net.Uri.parse("spectra-widget://${target.id}/$commandName")
        }
        val firePi = PendingIntent.getBroadcast(
            context,
            (target.id + commandName).hashCode(),
            fireIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, firePi)
        return views
    }
}
