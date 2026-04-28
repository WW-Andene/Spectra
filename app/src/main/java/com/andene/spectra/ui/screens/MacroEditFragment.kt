package com.andene.spectra.ui.screens

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.andene.spectra.R
import com.andene.spectra.data.models.DeviceProfile
import com.andene.spectra.data.models.Macro
import com.andene.spectra.data.models.MacroStep
import com.andene.spectra.ui.MainViewModel
import com.google.android.material.textfield.TextInputEditText

/**
 * Build / edit a macro. Working state lives locally in this fragment; only
 * the final Save commits back to the viewmodel + persistence.
 */
class MacroEditFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    private val workingSteps = mutableListOf<MacroStep>()
    private var editingId: String? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_macro_edit, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = view.findViewById<TextView>(R.id.macroEditTitle)
        val nameInput = view.findViewById<TextInputEditText>(R.id.inputMacroName)
        val stepList = view.findViewById<LinearLayout>(R.id.stepList)
        val btnAddStep = view.findViewById<Button>(R.id.btnAddStep)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelMacro)
        val btnSave = view.findViewById<Button>(R.id.btnSaveMacro)

        // Seed from the macro being edited (null => creating)
        val existing = vm.editingMacro.value
        if (existing != null) {
            title.text = getString(R.string.macro_edit_title)
            nameInput.setText(existing.name)
            workingSteps.clear()
            workingSteps.addAll(existing.steps)
            editingId = existing.id
        }
        renderSteps(stepList)

        btnAddStep.setOnClickListener { openStepBuilder(stepList) }

        btnCancel.setOnClickListener {
            vm.navigate(MainViewModel.Screen.HOME)
        }

        btnSave.setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                nameInput.error = "Name required"
                return@setOnClickListener
            }
            if (workingSteps.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setMessage("Add at least one step before saving.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            val macro = Macro(
                id = editingId ?: java.util.UUID.randomUUID().toString(),
                name = name,
                steps = workingSteps.toList(),
            )
            vm.saveMacro(macro)
            vm.navigate(MainViewModel.Screen.HOME)
        }
    }

    private fun renderSteps(container: LinearLayout) {
        container.removeAllViews()
        if (workingSteps.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.empty_steps)
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_tertiary))
                setPadding(16, 16, 16, 16)
            }
            container.addView(empty)
            return
        }
        val pad = (12 * resources.displayMetrics.density).toInt()
        for ((index, step) in workingSteps.withIndex()) {
            val row = TextView(requireContext()).apply {
                text = "${index + 1}. ${step.deviceName} → ${step.commandName}" +
                    if (step.delayBeforeMs > 0) "  (wait ${step.delayBeforeMs}ms)" else ""
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
                textSize = 13f
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Step ${index + 1}")
                        .setItems(arrayOf("Move up", "Move down", "Remove")) { _, which ->
                            when (which) {
                                0 -> if (index > 0) {
                                    val s = workingSteps.removeAt(index); workingSteps.add(index - 1, s)
                                }
                                1 -> if (index < workingSteps.size - 1) {
                                    val s = workingSteps.removeAt(index); workingSteps.add(index + 1, s)
                                }
                                2 -> workingSteps.removeAt(index)
                            }
                            renderSteps(container)
                        }
                        .show()
                    true
                }
            }
            container.addView(row)
        }
    }

    /** Three-step picker: device → command → optional delay. */
    private fun openStepBuilder(container: LinearLayout) {
        val devices = vm.savedDevices.value.filter { !it.irProfile?.commands.isNullOrEmpty() }
        if (devices.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.macro_no_devices_title)
                .setMessage(R.string.macro_no_devices_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val deviceLabels = devices.map { it.name ?: "Unnamed" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.macro_pick_device)
            .setItems(deviceLabels) { _, which ->
                pickCommand(devices[which], container)
            }
            .show()
    }

    private fun pickCommand(device: DeviceProfile, container: LinearLayout) {
        val commands = device.irProfile?.commands?.keys?.toList()?.sorted().orEmpty()
        if (commands.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.macro_pick_command)
            .setItems(commands.toTypedArray()) { _, which ->
                pickDelay(device, commands[which], container)
            }
            .show()
    }

    private fun pickDelay(device: DeviceProfile, commandName: String, container: LinearLayout) {
        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "0"
            setText("0")
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.macro_delay_title)
            .setMessage(R.string.macro_delay_message)
            .setView(input)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val delay = input.text.toString().toIntOrNull()?.coerceIn(0, 30_000) ?: 0
                workingSteps.add(
                    MacroStep(
                        deviceId = device.id,
                        deviceName = device.name ?: "Device",
                        commandName = commandName,
                        delayBeforeMs = delay,
                    )
                )
                renderSteps(container)
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }
}
