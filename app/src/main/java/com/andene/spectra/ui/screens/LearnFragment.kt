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
import com.andene.spectra.modules.bruteforce.IrBruteForce.Companion.brandTokens
import com.andene.spectra.modules.bruteforce.IrBruteForce.Companion.matchesBrand
import com.andene.spectra.modules.ir.IrCameraCapture
import com.andene.spectra.ui.MainViewModel
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LearnFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    private var pendingPreviewView: PreviewView? = null
    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pv = pendingPreviewView ?: return@registerForActivityResult
        pendingPreviewView = null
        if (granted) {
            bindCameraPreview(pv)
        } else {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.camera_permission_required),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

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

        val deviceName = vm.activeDevice.value?.name ?: getString(R.string.device_default_label)
        learnTitle.text = getString(R.string.learn_title_with_device, deviceName)

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
            captureStatus.text = getString(R.string.recording_hint)
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
            vm.captureState.collect { state ->
                captureStatus.text = getString(when (state) {
                    IrCameraCapture.CaptureState.IDLE -> R.string.device_capture_status_ready
                    IrCameraCapture.CaptureState.CAPTURING -> R.string.device_capture_status_recording
                    IrCameraCapture.CaptureState.PROCESSING -> R.string.device_capture_status_analyzing
                    IrCameraCapture.CaptureState.DECODED -> R.string.device_capture_status_decoded
                    IrCameraCapture.CaptureState.ERROR -> R.string.device_capture_status_no_signal
                })
            }
        }

        // Brute force controls
        if (!vm.hasIrBlaster()) {
            btnStartBrute.isEnabled = false
            bruteForceStatus.text = getString(R.string.ir_blaster_unavailable)
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
                    promptText.text = getString(
                        R.string.brute_force_attempt_format,
                        prompt.attemptNum, prompt.manufacturer, prompt.protocol.name,
                    )
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
        val detectedTokens = vm.activeDevice.value?.manufacturer.brandTokens()
        val brands = db.brands().sortedByDescending { brand ->
            // Brands sharing a word with the detected manufacturer float
            // to the top — same matcher the brute-force preorder uses, so
            // the two paths agree.
            matchesBrand(brand, detectedTokens)
        }
        if (brands.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.db_no_remotes_title)
                .setMessage(R.string.db_no_remotes_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.db_pick_brand)
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
                .setMessage(R.string.db_no_remotes_for_brand)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = entries.map { "${it.model} · ${it.commands.size} cmds" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.db_brand_layout_title_format, brand))
            .setItems(labels) { _, which ->
                vm.installRemoteFromDatabase(entries[which])
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.db_installed_title)
                    .setMessage(getString(
                        R.string.db_installed_message_format,
                        entries[which].model, entries[which].commands.size,
                    ))
                    .setPositiveButton(R.string.action_open_remote) { _, _ ->
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
                text = getString(R.string.learn_no_commands)
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
            .setItems(arrayOf(
                getString(R.string.action_test),
                getString(R.string.action_rename),
                getString(R.string.action_delete),
            )) { _, which ->
                when (which) {
                    0 -> vm.testCommand(name)
                    1 -> showRenameDialog(name, container)
                    2 -> {
                        AlertDialog.Builder(requireContext())
                            .setMessage(getString(R.string.confirm_delete_command_format, name))
                            .setPositiveButton(R.string.action_delete) { _, _ ->
                                vm.deleteCommand(name)
                                updateLearnedList(container)
                            }
                            .setNegativeButton(R.string.action_cancel_dialog, null)
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
            .setTitle(R.string.rename_command_title)
            .setView(input)
            .setPositiveButton(R.string.action_save_button) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    vm.renameCommand(oldName, newName)
                    updateLearnedList(container)
                }
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    private fun setupCamera(previewView: PreviewView) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Stash the preview view so the permission-result callback can
            // resume binding once the user grants. The callback owns the
            // single shared launcher.
            pendingPreviewView = previewView
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        bindCameraPreview(previewView)
    }

    private fun bindCameraPreview(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analyzer = vm.buildIrCameraAnalyzer()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                // Camera init failed (rare — usually means another app holds
                // the camera or the device is mid-screen-rotation). Surface
                // it instead of failing silently — user otherwise sees an
                // empty PreviewView and can't tell whether to wait or retry.
                android.util.Log.e("LearnFragment", "Camera bind failed", e)
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.camera_bind_failed),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
}
