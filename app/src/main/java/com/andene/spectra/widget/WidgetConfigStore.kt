package com.andene.spectra.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * Per-pinned-widget configuration for the home-screen widget.
 *
 * Each AppWidgetProvider instance can target a different device + command
 * (B-002 phase 2). The configuration activity writes the selection here
 * keyed by the widget's appWidgetId; the widget's onUpdate reads the
 * binding to figure out which device label to render and which extras to
 * stuff into the tap-fire PendingIntent.
 *
 * No deviceId / commandName ⇒ widget falls back to the "primary device,
 * POWER command" auto-pick from phase 1 — preserving the original
 * behaviour for widgets pinned before this code shipped.
 *
 * Cleared on AppWidgetProvider.onDeleted to prevent the prefs file from
 * growing unbounded over years of widget churn.
 */
internal object WidgetConfigStore {

    private const val PREFS = "spectra_widget_config"

    private fun keyDevice(id: Int) = "w${id}_deviceId"
    private fun keyCommand(id: Int) = "w${id}_commandName"

    data class Binding(val deviceId: String, val commandName: String)

    fun set(context: Context, appWidgetId: Int, binding: Binding) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(keyDevice(appWidgetId), binding.deviceId)
            .putString(keyCommand(appWidgetId), binding.commandName)
            .apply()
    }

    fun get(context: Context, appWidgetId: Int): Binding? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(keyDevice(appWidgetId), null) ?: return null
        val commandName = prefs.getString(keyCommand(appWidgetId), null) ?: return null
        return Binding(deviceId, commandName)
    }

    fun clear(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (id in appWidgetIds) {
            editor.remove(keyDevice(id))
            editor.remove(keyCommand(id))
        }
        editor.apply()
    }
}
