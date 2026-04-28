package com.andene.spectra.ui.screens

import android.Manifest
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
        val learnedCommands = view.findViewById<TextView>(R.id.learnedCommands)
        val btnBackLearn = view.findViewById<Button>(R.id.btnBackLearn)
        val btnOpenRemote = view.findViewById<Button>(R.id.btnOpenRemote)

        val deviceName = vm.activeDevice.value?.name ?: "Device"
        learnTitle.text = "Learn: $deviceName"

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

        // Initial learned list
        updateLearnedList(learnedCommands)
    }

    private fun updateLearnedList(textView: TextView) {
        val device = vm.activeDevice.value
        val commands = device?.irProfile?.commands
        if (commands.isNullOrEmpty()) {
            textView.text = "No commands learned yet"
        } else {
            textView.text = commands.entries.joinToString("\n") { (name, cmd) ->
                "$name — ${cmd.protocol} (${cmd.rawTimings.size} pulses)"
            }
        }
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
