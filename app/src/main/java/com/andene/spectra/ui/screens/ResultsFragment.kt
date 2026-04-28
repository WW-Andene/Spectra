package com.andene.spectra.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.andene.spectra.R
import com.andene.spectra.ui.MainViewModel
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ResultsFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_results, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val deviceStatus = view.findViewById<TextView>(R.id.deviceStatus)
        val detailAcoustic = view.findViewById<TextView>(R.id.detailAcoustic)
        val detailRf = view.findViewById<TextView>(R.id.detailRf)
        val detailEm = view.findViewById<TextView>(R.id.detailEm)
        val inputName = view.findViewById<TextInputEditText>(R.id.inputDeviceName)
        val btnLearnCamera = view.findViewById<Button>(R.id.btnLearnCamera)
        val btnBruteForce = view.findViewById<Button>(R.id.btnBruteForce)
        val btnSave = view.findViewById<Button>(R.id.btnSaveDevice)
        val btnBack = view.findViewById<Button>(R.id.btnBack)

        // Combine device + phase so the status text updates on either side
        // changing — particularly when phase flips from DEVICE_UNKNOWN to
        // READY after the brute force finishes from this screen.
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.activeDevice, vm.orchestrator.phase) { device, phase -> device to phase }
                .collect { (device, phase) ->
                if (device == null) return@collect

                deviceStatus.text = when (phase) {
                    com.andene.spectra.core.SpectraOrchestrator.Phase.DEVICE_IDENTIFIED ->
                        "Known device: ${device.name ?: "Unnamed"}"
                    com.andene.spectra.core.SpectraOrchestrator.Phase.READY ->
                        "Ready to control"
                    else -> "New device detected"
                }

                // Acoustic details
                val acoustic = device.acousticSignature
                detailAcoustic.text = if (acoustic != null) {
                    val peakCount = acoustic.dominantFrequencies.size
                    val topFreq = acoustic.dominantFrequencies.firstOrNull()?.frequencyHz?.toInt() ?: 0
                    "Acoustic: $peakCount peaks, dominant ${topFreq}Hz"
                } else "Acoustic: no data"

                // RF details
                val rf = device.rfSignature
                detailRf.text = if (rf != null) {
                    val wifi = rf.wifiDevices.size
                    val ble = rf.bleDevices.size
                    val manufacturer = rf.wifiDevices.firstOrNull()?.modelHint
                    "RF: $wifi WiFi, $ble BLE${manufacturer?.let { " ($it)" } ?: ""}"
                } else "RF: no data"

                // EM details
                val em = device.emSignature
                detailEm.text = if (em != null) {
                    "EM: ${em.fieldStrength.toInt()} µT, ${em.emiAudioFrequencies.size} EMI peaks"
                } else "EM: no data"

                // Pre-fill name from RF if available
                if (inputName.text.isNullOrEmpty()) {
                    val rfName = rf?.wifiDevices?.firstOrNull()?.ssid
                        ?: rf?.bleDevices?.firstOrNull()?.name
                    rfName?.let { inputName.setText(it) }
                }
            }
        }

        // Disable IR buttons if no blaster
        if (!vm.hasIrBlaster()) {
            btnBruteForce.isEnabled = false
            btnBruteForce.text = "NO IR BLASTER"
        }

        btnLearnCamera.setOnClickListener {
            val name = inputName.text?.toString() ?: ""
            if (name.isNotBlank()) vm.saveDiscoveredDevice(name)
            vm.navigate(MainViewModel.Screen.LEARN)
        }

        btnBruteForce.setOnClickListener {
            val name = inputName.text?.toString() ?: ""
            if (name.isNotBlank()) vm.saveDiscoveredDevice(name)
            vm.startBruteForce()
        }

        btnSave.setOnClickListener {
            val name = inputName.text?.toString()?.ifBlank { "Device" } ?: "Device"
            vm.saveDiscoveredDevice(name)
            vm.navigate(MainViewModel.Screen.HOME)
        }

        btnBack.setOnClickListener {
            vm.navigate(MainViewModel.Screen.HOME)
        }
    }
}
