package com.andene.spectra.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.activeDevice, vm.orchestratorPhase) { device, phase -> device to phase }
                    .collect { (device, phase) ->
                    if (device == null) return@collect

                    deviceStatus.text = when (phase) {
                        com.andene.spectra.core.SpectraOrchestrator.Phase.DEVICE_IDENTIFIED ->
                            getString(R.string.results_known_device_format,
                                device.name ?: getString(R.string.device_unnamed_label))
                        com.andene.spectra.core.SpectraOrchestrator.Phase.READY ->
                            getString(R.string.results_ready_to_control)
                        else -> getString(R.string.results_new_device_detected)
                    }

                    // Acoustic details
                    val acoustic = device.acousticSignature
                    detailAcoustic.text = if (acoustic != null) {
                        val peakCount = acoustic.dominantFrequencies.size
                        val topFreq = acoustic.dominantFrequencies.firstOrNull()?.frequencyHz?.toInt() ?: 0
                        getString(R.string.acoustic_detail_format, peakCount, topFreq)
                    } else getString(R.string.acoustic_no_data)

                    // RF details
                    val rf = device.rfSignature
                    detailRf.text = if (rf != null) {
                        val wifi = rf.wifiDevices.size
                        val ble = rf.bleDevices.size
                        val manufacturer = rf.wifiDevices.firstOrNull()?.modelHint
                        if (manufacturer != null) getString(R.string.rf_detail_with_manufacturer_format, wifi, ble, manufacturer)
                        else getString(R.string.rf_detail_format, wifi, ble)
                    } else getString(R.string.rf_no_data)

                    // EM details
                    val em = device.emSignature
                    detailEm.text = if (em != null) {
                        getString(R.string.em_detail_format, em.fieldStrength.toInt(), em.emiAudioFrequencies.size)
                    } else getString(R.string.em_no_data)

                    // Pre-fill name from RF if available
                    if (inputName.text.isNullOrEmpty()) {
                        val rfName = rf?.wifiDevices?.firstOrNull()?.ssid
                            ?: rf?.bleDevices?.firstOrNull()?.name
                        rfName?.let { inputName.setText(it) }
                    }
                }
            }
        }

        // Disable IR buttons if no blaster
        if (!vm.hasIrBlaster()) {
            btnBruteForce.isEnabled = false
            btnBruteForce.text = getString(R.string.brute_force_no_blaster)
        }

        // B-102 multi-device disambiguation. When the scan turned up
        // more than one plausible match for the room's RF, show a
        // banner with the alternate names; tap to open a picker that
        // re-targets activeDevice. Banner stays hidden when there's
        // only one match.
        val alternateMatchesHint = view.findViewById<TextView>(R.id.alternateMatchesHint)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.alternateMatches.collect { alternates ->
                    if (alternates.isEmpty()) {
                        alternateMatchesHint.visibility = View.GONE
                    } else {
                        alternateMatchesHint.visibility = View.VISIBLE
                        alternateMatchesHint.text = getString(
                            R.string.alternate_matches_hint_format,
                            alternates.size,
                            alternates.joinToString(", ") {
                                it.name ?: getString(R.string.device_unnamed_label)
                            },
                        )
                        alternateMatchesHint.setOnClickListener {
                            showAlternateMatchPicker(alternates)
                        }
                    }
                }
            }
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
            val fallback = getString(R.string.default_device_name)
            val name = inputName.text?.toString()?.ifBlank { fallback } ?: fallback
            vm.saveDiscoveredDevice(name)
            vm.navigate(MainViewModel.Screen.HOME)
        }

        btnBack.setOnClickListener {
            confirmDiscardOrLeave(inputName)
        }

        // Same dirty-state intercept on system back: don't drop a typed
        // device name silently. Symmetric with MacroEditFragment F-060.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmDiscardOrLeave(inputName)
                }
            },
        )
    }

    private fun confirmDiscardOrLeave(inputName: TextInputEditText) {
        val typed = inputName.text?.toString().orEmpty().trim()
        // Only prompt when there's typed content the user might lose.
        // Empty input → user hadn't typed anything yet, no dialog needed.
        if (typed.isEmpty()) {
            vm.navigate(MainViewModel.Screen.HOME)
            return
        }
        android.app.AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.results_discard_message))
            .setPositiveButton(R.string.action_discard) { _, _ ->
                vm.navigate(MainViewModel.Screen.HOME)
            }
            .setNegativeButton(R.string.action_keep_editing, null)
            .show()
    }

    private fun showAlternateMatchPicker(alternates: List<com.andene.spectra.data.models.DeviceProfile>) {
        // Each label includes the confidence percentage so the user can
        // sanity-check the matcher's ranking — e.g. "Living Room TV
        // (95%)" vs "Bedroom TV (76%)".
        val labels = alternates.map {
            getString(
                R.string.alternate_matches_picker_item_format,
                it.name ?: getString(R.string.device_unnamed_label),
                (it.confidence * 100).toInt(),
            )
        }.toTypedArray()
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.alternate_matches_picker_title)
            .setItems(labels) { _, which ->
                vm.chooseAlternateMatch(alternates[which])
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }
}
