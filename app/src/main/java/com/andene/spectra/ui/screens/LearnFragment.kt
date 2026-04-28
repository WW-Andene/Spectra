package com.andene.spectra.ui.screens

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.andene.spectra.R
import com.andene.spectra.modules.ir.IrCameraCapture
import com.andene.spectra.ui.MainViewModel
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LearnFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_learn, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val learnTitle = view.findViewById<TextView>(R.id.learnTitle)
        val cameraPreview = view.findViewById<PreviewView>(R.id.cameraPreview)
        val inputCommandName = view.findViewById<TextInputEditText>(R.id.inputCommandName)
        val btnStartCapture = view.findViewById<Button>(R.id.btnStartCapture)
        val btnStopCapture = view.findViewById<Button>(R.id.btnStopCapture)
        val captureStatus = view.findViewById<TextView>(R.id.captureStatus)
        val bruteForceStatus = view.findViewById<TextView>(R.id.bruteForceStatus)
        val bruteForcePrompt = view.findViewById<LinearLayout>(R.id.bruteForcePrompt)
        val promptText = view.findViewById<TextView>(R.id.promptText)
        val btnYes = view.findViewById<Button>(R.id.btnYes)
        val btnNo = view.findViewById<Button>(R.id.btnNo)
        val btnStartBrute = view.findViewById<Button>(R.id.btnStartBrute)
        val btnStopBrute = view.findViewById<Button>(R.id.btnStopBrute)
        val learnedCommands = view.findViewById<LinearLayout>(R.id.learnedCommands)
        val btnBackLearn = view.findViewById<Button>(R.id.btnBackLearn)
        val btnOpenRemote = view.findViewById<Button>(R.id.btnOpenRemote)

        val deviceName = vm.activeDevice.value?.name ?: "Device"
        learnTitle.text = "Learn: $deviceName"

        // Database picker — fastest path
        view.findViewById<Button>(R.id.btnPickFromDb).setOnClickListener {
            showBrandPicker()
        }

        // Setup camera
        setupCamera(cameraPreview)

        // Camera capture controls
        btnStartCapture.setOnClickListener {
            vm.setCommandName(inputCommandName.text?.toString() ?: "")
            vm.startCameraCapture()
            btnStartCapture.isEnabled = false
            btnStopCapture.isEnabled = true
            captureStatus.text = "Recording... point remote at camera"
        }

        btnStopCapture.setOnClickListener {
            vm.stopCameraCapture()
            btnStartCapture.isEnabled = true
            btnStopCapture.isEnabled = false
            inputCommandName.setText("")
            updateLearnedList(learnedCommands)
        }

        // Camera capture state
        viewLifecycleOwner.lifecycleScope.launch {
            vm.orchestrator.irCapture.captureState.collect { state ->
                captureStatus.text = when (state) {
                    IrCameraCapture.CaptureState.IDLE -> "Ready"
                    IrCameraCapture.CaptureState.CAPTURING -> "Recording..."
                    IrCameraCapture.CaptureState.PROCESSING -> "Analyzing..."
                    IrCameraCapture.CaptureState.DECODED -> "Command captured!"
                    IrCameraCapture.CaptureState.ERROR -> "No IR signal detected"
                }
            }
        }

        // Brute force controls
        if (!vm.hasIrBlaster()) {
            btnStartBrute.isEnabled = false
            bruteForceStatus.text = "IR blaster not available on this device"
        }

        btnStartBrute.setOnClickListener {
            btnStartBrute.isEnabled = false
            btnStopBrute.isEnabled = true
            vm.startBruteForce()
        }

        btnStopBrute.setOnClickListener {
            vm.stopBruteForce()
            btnStartBrute.isEnabled = true
            btnStopBrute.isEnabled = false
        }

        // Brute force prompt
        viewLifecycleOwner.lifecycleScope.launch {
            vm.bruteForcePrompt.collect { prompt ->
                if (prompt != null) {
                    bruteForcePrompt.visibility = View.VISIBLE
                    promptText.text = "Attempt #${prompt.attemptNum}: ${prompt.manufacturer} (${prompt.protocol})\nDid the device react?"
                } else {
                    bruteForcePrompt.visibility = View.GONE
                }
            }
        }

        btnYes.setOnClickListener { vm.confirmBruteForce() }
        btnNo.setOnClickListener { vm.denyBruteForce() }

        // Navigation
        btnBackLearn.setOnClickListener { vm.navigate(MainViewModel.Screen.RESULTS) }
        btnOpenRemote.setOnClickListener { vm.navigate(MainViewModel.Screen.REMOTE) }

        // Re-render the learned-commands list whenever the active device changes
        // (rename/delete/install all flow through activeDevice). Also disable
        // OPEN REMOTE until at least one command exists — clicking it before
        // installing any commands lands the user on a non-functional grid.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.activeDevice.collect { device ->
                updateLearnedList(learnedCommands)
                val hasCommands = device?.irProfile?.commands?.isNotEmpty() == true
                btnOpenRemote.isEnabled = hasCommands
                btnOpenRemote.alpha = if (hasCommands) 1f else 0.4f
            }
        }
    }

    private fun showBrandPicker() {
        val db = vm.codeDatabase
        val detected = vm.activeDevice.value?.manufacturer?.lowercase()
        val brands = db.brands().sortedByDescending { brand ->
            // Brands matching the detected manufacturer float to the top.
            detected != null && (brand.lowercase().contains(detected) || detected.contains(brand.lowercase()))
        }
        if (brands.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("No remotes available")
                .setMessage("The bundled IR database is empty.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Pick a brand")
            .setItems(brands.toTypedArray()) { _, which ->
                showRemotePicker(brands[which])
            }
            .show()
    }

    private fun showRemotePicker(brand: String) {
        val entries = vm.codeDatabase.lookup(brand)
            // List the most complete layouts first — coverage is the dominant
            // tiebreaker between similarly-named entries.
            .sortedByDescending { it.commands.size }
        if (entries.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(brand)
                .setMessage("No remotes available for this brand.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = entries.map { "${it.model} · ${it.commands.size} cmds" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("$brand — most-complete layout first")
            .setItems(labels) { _, which ->
                vm.installRemoteFromDatabase(entries[which])
                AlertDialog.Builder(requireContext())
                    .setTitle("Installed")
                    .setMessage("${entries[which].model} loaded with ${entries[which].commands.size} commands.")
                    .setPositiveButton("Open Remote") { _, _ ->
                        vm.navigate(com.andene.spectra.ui.MainViewModel.Screen.REMOTE)
                    }
                    .setNegativeButton("Stay", null)
                    .show()
            }
            .show()
    }

    private fun updateLearnedList(container: LinearLayout) {
        container.removeAllViews()
        val device = vm.activeDevice.value
        val commands = device?.irProfile?.commands

        if (commands.isNullOrEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No commands learned yet"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
                textSize = 12f
            }
            container.addView(empty)
            return
        }

        val padding = (8 * resources.displayMetrics.density).toInt()
        for ((name, cmd) in commands.entries.sortedBy { it.key }) {
            val row = TextView(requireContext()).apply {
                text = "$name  ·  ${cmd.protocol} (${cmd.rawTimings.size} pulses)"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                textSize = 13f
                setPadding(padding, padding, padding, padding)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    vm.testCommand(name)
                }
                setOnLongClickListener {
                    showCommandActions(name, container)
                    true
                }
            }
            container.addView(row)
        }
    }

    private fun showCommandActions(name: String, container: LinearLayout) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setItems(arrayOf("Test", "Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> vm.testCommand(name)
                    1 -> showRenameDialog(name, container)
                    2 -> {
                        AlertDialog.Builder(requireContext())
                            .setMessage("Delete '$name'?")
                            .setPositiveButton("Delete") { _, _ ->
                                vm.deleteCommand(name)
                                updateLearnedList(container)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(oldName: String, container: LinearLayout) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(oldName)
            setSelection(oldName.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename command")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    vm.renameCommand(oldName, newName)
                    updateLearnedList(container)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupCamera(previewView: PreviewView) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analyzer = vm.orchestrator.irCapture.buildAnalyzer()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                // Camera init failed — log but don't crash
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
}
