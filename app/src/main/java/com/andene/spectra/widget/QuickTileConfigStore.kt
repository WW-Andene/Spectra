package com.andene.spectra.widget

import android.content.Context

/**
 * Configuration for the Quick Settings tile (B-003 phase 2).
 *
 * QS tiles don't get a system-invoked configuration activity the way
 * widgets do — the user accesses tile settings exclusively through
 * Spectra's overflow menu, picks a target there, and the choice is
 * persisted globally (only one Spectra QS tile exists per device).
 *
 * Two binding kinds:
 *  - MACRO: tile fires the named macro
 *  - COMMAND: tile fires a single command on a specific device
 *
 * No binding ⇒ tile falls back to the primary-device POWER auto-pick
 * (the B-003 phase 1 behaviour) — preserving the original out-of-box
 * experience for users who never open the picker.
 */
internal object QuickTileConfigStore {

    private const val PREFS = "spectra_quick_tile_config"
    private const val KEY_KIND = "kind"
    private const val KEY_MACRO_ID = "macroId"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val KEY_COMMAND_NAME = "commandName"

    sealed class Binding {
        data class Macro(val macroId: String) : Binding()
        data class Command(val deviceId: String, val commandName: String) : Binding()
    }

    fun set(context: Context, binding: Binding) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        when (binding) {
            is Binding.Macro -> {
                editor.putString(KEY_KIND, "macro")
                editor.putString(KEY_MACRO_ID, binding.macroId)
            }
            is Binding.Command -> {
                editor.putString(KEY_KIND, "command")
                editor.putString(KEY_DEVICE_ID, binding.deviceId)
                editor.putString(KEY_COMMAND_NAME, binding.commandName)
            }
        }
        editor.apply()
    }

    fun get(context: Context): Binding? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_KIND, null)) {
            "macro" -> {
                val macroId = prefs.getString(KEY_MACRO_ID, null) ?: return null
                Binding.Macro(macroId)
            }
            "command" -> {
                val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: return null
                val commandName = prefs.getString(KEY_COMMAND_NAME, null) ?: return null
                Binding.Command(deviceId, commandName)
            }
            else -> null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
