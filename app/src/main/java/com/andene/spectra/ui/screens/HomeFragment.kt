package com.andene.spectra.ui.screens

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andene.spectra.R
import com.andene.spectra.data.models.Macro
import com.andene.spectra.ui.MainViewModel
import com.andene.spectra.ui.components.DeviceAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    private val scanPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            vm.startPassiveScan()
        } else {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.scan_permissions_required),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnScan = view.findViewById<Button>(R.id.btnScan)
        val deviceList = view.findViewById<RecyclerView>(R.id.deviceList)
        val emptyState = view.findViewById<TextView>(R.id.emptyState)
        val statusIr = view.findViewById<TextView>(R.id.statusIr)
        val statusEm = view.findViewById<TextView>(R.id.statusEm)
        val statusMic = view.findViewById<TextView>(R.id.statusMic)
        val statusRf = view.findViewById<TextView>(R.id.statusRf)

        // Hardware / permission status — dim the dot when its capability isn't
        // available. Mic and RF dim based on runtime grant; IR and EM dim based
        // on hardware presence. Long-press for an explanation.
        statusIr.alpha = if (vm.hasIrBlaster()) 1f else 0.3f
        statusEm.alpha = if (vm.hasMagnetometer()) 1f else 0.3f
        statusMic.alpha = if (hasPermission(Manifest.permission.RECORD_AUDIO)) 1f else 0.3f
        statusRf.alpha = if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) 1f else 0.3f
        statusIr.setOnLongClickListener { explainCapability(R.string.cap_ir); true }
        statusEm.setOnLongClickListener { explainCapability(R.string.cap_em); true }
        statusMic.setOnLongClickListener { explainCapability(R.string.cap_mic); true }
        statusRf.setOnLongClickListener { explainCapability(R.string.cap_rf); true }

        // Device list
        val adapter = DeviceAdapter(
            onDeviceClick = { vm.selectDevice(it) },
            onDeviceLongClick = { device ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(device.name ?: getString(R.string.device_default_label))
                    .setItems(arrayOf(
                        getString(R.string.action_open_remote_short),
                        getString(R.string.action_delete),
                    )) { _, which ->
                        when (which) {
                            0 -> vm.selectDevice(device)
                            1 -> vm.deleteDevice(device.id)
                        }
                    }
                    .show()
            }
        )

        deviceList.layoutManager = LinearLayoutManager(requireContext())
        deviceList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            vm.savedDevices.collect { devices ->
                adapter.submitList(devices)
                emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                deviceList.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        btnScan.setOnClickListener {
            val missing = scanPermissionsToRequest()
            if (missing.isEmpty()) vm.startPassiveScan()
            else scanPermissionLauncher.launch(missing.toTypedArray())
        }

        view.findViewById<Button>(R.id.btnImportClipboard).setOnClickListener {
            importFromClipboard()
        }

        // Surface viewmodel-emitted toasts (save failures, etc.). Home is
        // always the surviving root destination so collecting here gives us
        // user feedback for every persistence error regardless of which
        // screen the user was on when the save fired.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.toasts.collect { msg ->
                android.widget.Toast.makeText(
                    requireContext(), msg, android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }

        // Undo affordance for delete actions. The viewmodel snapshots the
        // deleted item before removing it; this collector shows a Snackbar
        // with UNDO that restores the item if tapped.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.undoActions.collect { action ->
                val label = when (action) {
                    is MainViewModel.UndoAction.Device ->
                        getString(R.string.undo_deleted_device, action.profile.name ?: "device")
                    is MainViewModel.UndoAction.Macro ->
                        getString(R.string.undo_deleted_macro, action.macro.name)
                }
                com.google.android.material.snackbar.Snackbar
                    .make(requireView(), label, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) { vm.undoDelete(action) }
                    .show()
            }
        }

        // ── Macros ────────────────────────────────────────────
        val macroChips = view.findViewById<LinearLayout>(R.id.macroChips)
        val macroRunning = view.findViewById<TextView>(R.id.macroRunning)
        view.findViewById<Button>(R.id.btnNewMacro).setOnClickListener {
            vm.openMacroEditor(null)
        }

        // Re-render chips whenever macros OR savedDevices change, so the
        // stale-step-count badge stays accurate after a device delete.
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(vm.macros, vm.savedDevices) { m, d -> m to d }
                .collect { (macros, devices) ->
                    renderMacroChips(macroChips, macros, devices.map { it.id }.toSet())
                }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.runningMacro.collect { running ->
                if (running != null) {
                    macroRunning.visibility = View.VISIBLE
                    macroRunning.text = getString(
                        R.string.macro_running_format,
                        running.name, running.currentStep, running.totalSteps, running.currentLabel,
                    )
                } else {
                    macroRunning.visibility = View.GONE
                }
            }
        }
    }

    private fun hasPermission(name: String) =
        ContextCompat.checkSelfPermission(requireContext(), name) == PackageManager.PERMISSION_GRANTED

    private fun explainCapability(stringRes: Int) {
        android.widget.Toast.makeText(
            requireContext(),
            getString(stringRes),
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * The runtime permissions a passive scan needs that aren't yet granted.
     * Mirrors what the orchestrator's @RequiresPermission annotations declare:
     * audio for acoustic + EM, fine location + BLE for RF.
     */
    private fun scanPermissionsToRequest(): List<String> {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return needed.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun importFromClipboard() {
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val text = cm.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(requireContext())
            ?.toString()
            ?.trim()
        if (text.isNullOrEmpty() || !text.startsWith("{")) {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.clipboard_not_json),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        vm.importDeviceFromJson(text) { imported ->
            val msg = if (imported != null) {
                "Imported ${imported.name ?: "device"} with ${imported.irProfile?.commands?.size ?: 0} commands"
            } else getString(R.string.clipboard_parse_failed)
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun renderMacroChips(
        container: LinearLayout,
        macros: List<Macro>,
        knownDeviceIds: Set<String>,
    ) {
        container.removeAllViews()
        if (macros.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.empty_macros)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
                textSize = 12f
                setPadding(0, 0, 0, 0)
            }
            container.addView(empty)
            return
        }
        val pad = (12 * resources.displayMetrics.density).toInt()
        val chipBg = ContextCompat.getColor(requireContext(), R.color.bg_card_elevated)
        val chipText = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val warnText = ContextCompat.getColor(requireContext(), R.color.accent_warning)
        for (macro in macros) {
            val staleCount = macro.steps.count { it.deviceId !in knownDeviceIds }
            val chip = TextView(requireContext()).apply {
                // Append a "(N stale)" suffix in the warning colour so the
                // user notices that some steps point to deleted devices
                // before they tap the chip and get a half-running macro.
                text = if (staleCount == 0) macro.name
                       else "${macro.name} ⚠"
                if (staleCount > 0) setTextColor(warnText) else setTextColor(chipText)
                textSize = 13f
                setPadding(pad, pad / 2, pad, pad / 2)
                // Solid card-elevated colour for the chip with built-in press
                // feedback. selectableItemBackground gets the platform ripple
                // overlaid on top of the colour.
                setBackgroundColor(chipBg)
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true
                )
                foreground = ContextCompat.getDrawable(requireContext(), outValue.resourceId)
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { rightMargin = pad / 2 }
                layoutParams = lp
                setOnClickListener { vm.runMacro(macro.id) }
                setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(macro.name)
                        .setItems(arrayOf(
                            getString(R.string.action_run),
                            getString(R.string.action_edit),
                            getString(R.string.action_delete),
                        )) { _, which ->
                            when (which) {
                                0 -> vm.runMacro(macro.id)
                                1 -> vm.openMacroEditor(macro)
                                2 -> AlertDialog.Builder(requireContext())
                                    .setMessage(getString(R.string.confirm_delete_macro, macro.name))
                                    .setPositiveButton(R.string.action_delete) { _, _ -> vm.deleteMacro(macro.id) }
                                    .setNegativeButton(R.string.action_cancel_dialog, null)
                                    .show()
                            }
                        }
                        .show()
                    true
                }
            }
            container.addView(chip)
        }
    }
}
