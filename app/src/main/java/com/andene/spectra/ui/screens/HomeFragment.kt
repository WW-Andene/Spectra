package com.andene.spectra.ui.screens

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnScan = view.findViewById<Button>(R.id.btnScan)
        val deviceList = view.findViewById<RecyclerView>(R.id.deviceList)
        val emptyState = view.findViewById<TextView>(R.id.emptyState)
        val statusIr = view.findViewById<TextView>(R.id.statusIr)
        val statusEm = view.findViewById<TextView>(R.id.statusEm)

        // Hardware status
        statusIr.alpha = if (vm.hasIrBlaster()) 1f else 0.3f
        statusEm.alpha = if (vm.hasMagnetometer()) 1f else 0.3f

        // Device list
        val adapter = DeviceAdapter(
            onDeviceClick = { vm.selectDevice(it) },
            onDeviceLongClick = { device ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(device.name ?: "Device")
                    .setItems(arrayOf("Open Remote", "Delete")) { _, which ->
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
            vm.startPassiveScan()
        }

        view.findViewById<Button>(R.id.btnImportClipboard).setOnClickListener {
            importFromClipboard()
        }

        // ── Macros ────────────────────────────────────────────
        val macroChips = view.findViewById<LinearLayout>(R.id.macroChips)
        val macroRunning = view.findViewById<TextView>(R.id.macroRunning)
        view.findViewById<Button>(R.id.btnNewMacro).setOnClickListener {
            vm.openMacroEditor(null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.macros.collect { macros -> renderMacroChips(macroChips, macros) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.runningMacro.collect { running ->
                if (running != null) {
                    macroRunning.visibility = View.VISIBLE
                    macroRunning.text = "Running '${running.name}': step ${running.currentStep}/${running.totalSteps} — ${running.currentLabel}"
                } else {
                    macroRunning.visibility = View.GONE
                }
            }
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
                "Clipboard doesn't look like a Spectra profile (expected JSON).",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        vm.importDeviceFromJson(text) { imported ->
            val msg = if (imported != null) {
                "Imported ${imported.name ?: "device"} with ${imported.irProfile?.commands?.size ?: 0} commands"
            } else "Could not parse clipboard contents"
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun renderMacroChips(container: LinearLayout, macros: List<Macro>) {
        container.removeAllViews()
        if (macros.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No macros yet."
                setTextColor(resources.getColor(R.color.text_tertiary, null))
                textSize = 12f
                setPadding(0, 0, 0, 0)
            }
            container.addView(empty)
            return
        }
        val pad = (12 * resources.displayMetrics.density).toInt()
        for (macro in macros) {
            val chip = TextView(requireContext()).apply {
                text = macro.name
                setTextColor(resources.getColor(R.color.text_primary, null))
                textSize = 13f
                setPadding(pad, pad / 2, pad, pad / 2)
                setBackgroundResource(android.R.drawable.list_selector_background)
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
                        .setItems(arrayOf("Run", "Edit", "Delete")) { _, which ->
                            when (which) {
                                0 -> vm.runMacro(macro.id)
                                1 -> vm.openMacroEditor(macro)
                                2 -> AlertDialog.Builder(requireContext())
                                    .setMessage("Delete macro '${macro.name}'?")
                                    .setPositiveButton("Delete") { _, _ -> vm.deleteMacro(macro.id) }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                        .show()
                    true
                }
            }
            // Wrap chip in a card-style background
            chip.setBackgroundColor(resources.getColor(R.color.bg_card_elevated, null))
            container.addView(chip)
        }
    }
}
