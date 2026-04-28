package com.andene.spectra.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.andene.spectra.R
import com.andene.spectra.SpectraApp
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.modules.control.IrControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pinned-widget configuration screen (B-002 phase 2).
 *
 * Android invokes this activity when the user drops the Spectra widget
 * onto their launcher. The user picks which saved device should be
 * targeted, the binding is written to [WidgetConfigStore] keyed by the
 * widget's appWidgetId, and the activity finishes with RESULT_OK +
 * EXTRA_APPWIDGET_ID so the launcher actually completes the placement.
 *
 * Cancel / back-press finishes with RESULT_CANCELED, which on most
 * launchers cleans up the half-placed widget. Activities marked as
 * widget configurators must explicitly setResult before finish or the
 * widget is left in a broken half-placed state.
 *
 * Phase 3 will extend this to pick a per-widget command (POWER vs
 * MUTE vs custom). Phase 1 / 2 hardcode POWER since that's the
 * dominant single-tap use case.
 */
class SpectraWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        // Default to RESULT_CANCELED before super.onCreate — if the user
        // hits back at any point, the launcher's widget host knows to
        // tear down the half-placed instance.
        setResult(Activity.RESULT_CANCELED)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        // Apply window insets so the title isn't tucked under the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.widgetConfigRoot)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Launched outside a widget-pin flow — nothing to do.
            finish()
            return
        }

        val title = findViewById<TextView>(R.id.widgetConfigTitle)
        val list = findViewById<ListView>(R.id.deviceList)
        val emptyState = findViewById<TextView>(R.id.emptyState)
        val cancelButton = findViewById<Button>(R.id.btnCancel)

        title.text = getString(R.string.widget_config_title)
        cancelButton.setOnClickListener { finish() }

        lifecycleScope.launch {
            val app = applicationContext as SpectraApp
            val devices = withContext(Dispatchers.IO) {
                // Phase 3: relax the POWER-only filter — any device with
                // at least one command is now eligible because the
                // command picker downstream lets the user pick whatever
                // command they want for this specific widget instance.
                app.repository.loadAll().filter {
                    it.irProfile?.commands?.isNotEmpty() == true
                }
            }

            if (devices.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                emptyState.text = getString(R.string.widget_config_no_devices)
                list.visibility = View.GONE
                return@launch
            }

            list.adapter = ArrayAdapter(
                this@SpectraWidgetConfigActivity,
                android.R.layout.simple_list_item_1,
                devices.map { it.name ?: getString(R.string.device_default_label) },
            )
            list.setOnItemClickListener { _, _, position, _ ->
                pickCommandFor(devices[position])
            }
        }
    }

    /**
     * Phase 3: after a device is picked, surface its command list so the
     * user can pin a command other than POWER. Single-command devices
     * skip this step and complete with the only available command.
     */
    private fun pickCommandFor(device: DeviceProfile) {
        val commands = device.irProfile?.commands?.keys?.toList()?.sorted().orEmpty()
        if (commands.isEmpty()) {
            // Shouldn't reach here because the device list pre-filters
            // to "has POWER", but handle for completeness.
            completeConfiguration(device, IrControl.Commands.POWER)
            return
        }
        if (commands.size == 1) {
            completeConfiguration(device, commands[0])
            return
        }
        // Default selection: POWER if present (matches phase 2's
        // hardcoded default), else the first command alphabetically.
        val defaultIndex = commands.indexOf(IrControl.Commands.POWER).coerceAtLeast(0)
        var picked = defaultIndex
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.widget_config_pick_command)
            .setSingleChoiceItems(commands.toTypedArray(), defaultIndex) { _, which -> picked = which }
            .setPositiveButton(R.string.action_save_button) { _, _ ->
                completeConfiguration(device, commands[picked])
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    private fun completeConfiguration(device: DeviceProfile, commandName: String) {
        WidgetConfigStore.set(
            this,
            appWidgetId,
            WidgetConfigStore.Binding(
                deviceId = device.id,
                commandName = commandName,
            ),
        )

        // Force an immediate widget update so the freshly-pinned tile
        // shows the user's pick on first render rather than waiting for
        // the periodic refresh.
        val updateIntent = Intent(this, SpectraQuickWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(updateIntent)

        // Setting RESULT_OK + the appWidgetId tells the launcher we
        // accepted the pin; without this the widget is rejected.
        val result = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
