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
        // Async load + render. onUpdate is allowed to return before all
        // widgets are populated; the manager picks up the latest views
        // when we call updateAppWidget below.
        scope.launch {
            val app = context.applicationContext as? SpectraApp ?: return@launch
            val devices = app.repository.loadAll()
            // QuickPowerKit centralises the "primary device" definition so
            // the widget label and the QS tile target the same device.
            val primary = QuickPowerKit.selectPrimary(devices) ?: devices.firstOrNull()

            for (id in appWidgetIds) {
                val views = buildViews(context, primary)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun buildViews(
        context: Context,
        primary: com.andene.spectra.data.models.DeviceProfile?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_command)

        if (primary == null || primary.irProfile?.commands?.containsKey(IrControl.Commands.POWER) != true) {
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
            primary.name ?: context.getString(R.string.device_default_label),
        )
        views.setTextViewText(R.id.widgetActionLabel, context.getString(R.string.btn_power))

        // Tap = fire POWER. Use the device id as the requestCode so two
        // widgets pinned to different devices don't clobber each other's
        // PendingIntent extras.
        val fireIntent = Intent(context, WidgetCommandReceiver::class.java).apply {
            action = WidgetCommandReceiver.ACTION_FIRE
            putExtra(WidgetCommandReceiver.EXTRA_DEVICE_ID, primary.id)
            putExtra(WidgetCommandReceiver.EXTRA_COMMAND_NAME, IrControl.Commands.POWER)
            // Set a unique data URI so PendingIntent.filterEquals doesn't
            // collapse two pending intents that differ only in extras.
            data = android.net.Uri.parse("spectra-widget://${primary.id}/power")
        }
        val firePi = PendingIntent.getBroadcast(
            context,
            primary.id.hashCode(),
            fireIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, firePi)
        return views
    }
}
