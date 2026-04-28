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

    companion object {
        private const val KEY_EDITING_ID = "macroEdit.editingId"
        private const val KEY_NAME = "macroEdit.name"
        private const val KEY_STEPS = "macroEdit.steps"
        // Field separator inside a serialized step. Picked because it's not
        // valid in any of the four fields (device name, command name) the
        // user can populate via UI.
        private const val STEP_SEP = '\u001F'
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_macro_edit, c, false)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Preserve in-progress edit across rotation + process death. A user
        // halfway through building 'Movie Night' shouldn't lose their
        // 5-step list to an OS-level kill.
        outState.putString(KEY_EDITING_ID, editingId)
        view?.findViewById<TextInputEditText>(R.id.inputMacroName)?.text?.toString()?.let {
            outState.putString(KEY_NAME, it)
        }
        outState.putStringArray(
            KEY_STEPS,
            workingSteps.map { encodeStep(it) }.toTypedArray(),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = view.findViewById<TextView>(R.id.macroEditTitle)
        val nameInput = view.findViewById<TextInputEditText>(R.id.inputMacroName)
        val stepList = view.findViewById<LinearLayout>(R.id.stepList)
        val btnAddStep = view.findViewById<Button>(R.id.btnAddStep)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelMacro)
        val btnSave = view.findViewById<Button>(R.id.btnSaveMacro)

        // Saved-state restore takes precedence — that's the user's most
        // recent typing. Falling through, fall back to vm.editingMacro
        // (the macro being edited or null for new).
        val restoredFromSaved = savedInstanceState?.let { restoreFromSaved(it, nameInput, title) } == true
        if (!restoredFromSaved) {
            val existing = vm.editingMacro.value
            if (existing != null) {
                title.text = getString(R.string.macro_edit_title)
                nameInput.setText(existing.name)
                workingSteps.clear()
                workingSteps.addAll(existing.steps)
                editingId = existing.id
            }
        }
        renderSteps(stepList)

        btnAddStep.setOnClickListener { openStepBuilder(stepList) }

        btnCancel.setOnClickListener {
            vm.navigate(MainViewModel.Screen.HOME)
        }

        btnSave.setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                nameInput.error = getString(R.string.macro_name_required)
                return@setOnClickListener
            }
            if (workingSteps.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.add_step_first_message)
                    .setPositiveButton(android.R.string.ok, null)
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

    private fun restoreFromSaved(
        saved: Bundle,
        nameInput: TextInputEditText,
        title: TextView,
    ): Boolean {
        editingId = saved.getString(KEY_EDITING_ID)
        // editingId presence indicates an edit (vs. create); use the matching
        // title so the screen still reflects the right mode after restore.
        if (editingId != null) title.text = getString(R.string.macro_edit_title)
        saved.getString(KEY_NAME)?.let { nameInput.setText(it) }
        val steps = saved.getStringArray(KEY_STEPS)?.mapNotNull { decodeStep(it) }.orEmpty()
        workingSteps.clear()
        workingSteps.addAll(steps)
        return saved.getString(KEY_NAME) != null || steps.isNotEmpty()
    }

    private fun encodeStep(step: MacroStep): String =
        listOf(step.deviceId, step.deviceName, step.commandName, step.delayBeforeMs.toString())
            .joinToString(STEP_SEP.toString())

    private fun decodeStep(s: String): MacroStep? {
        val parts = s.split(STEP_SEP)
        if (parts.size != 4) return null
        return MacroStep(
            deviceId = parts[0],
            deviceName = parts[1],
            commandName = parts[2],
            delayBeforeMs = parts[3].toIntOrNull() ?: 0,
        )
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
                        .setTitle(getString(R.string.step_index_title_format, index + 1))
                        .setItems(arrayOf(
                            getString(R.string.action_move_up),
                            getString(R.string.action_move_down),
                            getString(R.string.action_remove),
                        )) { _, which ->
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
