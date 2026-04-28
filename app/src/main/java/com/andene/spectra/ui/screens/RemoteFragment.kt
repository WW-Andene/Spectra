package com.andene.spectra.ui.screens

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.andene.spectra.R
import com.andene.spectra.modules.control.IrControl
import com.andene.spectra.ui.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.launch

class RemoteFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()
    private var repeatJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_remote, c, false)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val deviceName = view.findViewById<TextView>(R.id.deviceName)
        val deviceInfo = view.findViewById<TextView>(R.id.deviceInfo)
        val btnLearnMore = view.findViewById<Button>(R.id.btnLearnMore)
        val btnShareDevice = view.findViewById<Button>(R.id.btnShareDevice)
        val btnBackToHome = view.findViewById<Button>(R.id.btnBackToHome)
        val noBlasterNotice = view.findViewById<TextView>(R.id.noBlasterNotice)

        // Show no-blaster notice once on entry. Static state so we don't
        // recompute on every emission of activeDevice.
        if (!vm.hasIrBlaster()) noBlasterNotice.visibility = View.VISIBLE

        btnShareDevice.setOnClickListener { shareActiveDevice() }

        // Device info
        viewLifecycleOwner.lifecycleScope.launch {
            vm.activeDevice.collect { device ->
                if (device == null) return@collect
                deviceName.text = device.name ?: "Device"
                val cmdCount = device.irProfile?.commands?.size ?: 0
                val protocol = device.irProfile?.protocol?.name ?: "Unknown"
                deviceInfo.text = "$protocol · $cmdCount commands"
            }
        }

        // Single-tap buttons
        val tapButtons = mapOf(
            R.id.btnPower to IrControl.Commands.POWER,
            R.id.btnInput to IrControl.Commands.INPUT,
            R.id.btnMute to IrControl.Commands.MUTE,
            R.id.btnOk to IrControl.Commands.OK,
            R.id.btnUp to IrControl.Commands.UP,
            R.id.btnDown to IrControl.Commands.DOWN,
            R.id.btnLeft to IrControl.Commands.LEFT,
            R.id.btnRight to IrControl.Commands.RIGHT,
            R.id.btnBack to IrControl.Commands.BACK,
            R.id.btnHome to IrControl.Commands.HOME,
            R.id.btnMenu to IrControl.Commands.MENU,
            R.id.btnPlay to IrControl.Commands.PLAY,
            R.id.btnPause to IrControl.Commands.PAUSE,
            R.id.btnStop to IrControl.Commands.STOP,
            R.id.btn0 to IrControl.Commands.NUM_0,
            R.id.btn1 to IrControl.Commands.NUM_1,
            R.id.btn2 to IrControl.Commands.NUM_2,
            R.id.btn3 to IrControl.Commands.NUM_3,
            R.id.btn4 to IrControl.Commands.NUM_4,
            R.id.btn5 to IrControl.Commands.NUM_5,
            R.id.btn6 to IrControl.Commands.NUM_6,
            R.id.btn7 to IrControl.Commands.NUM_7,
            R.id.btn8 to IrControl.Commands.NUM_8,
            R.id.btn9 to IrControl.Commands.NUM_9,
        )

        // Index buttons by command name so the transmit-result collector can
        // flash the right one. Includes both tap and repeat buttons.
        val buttonsByCommand = HashMap<String, Button>()
        tapButtons.forEach { (id, command) ->
            view.findViewById<Button>(id)?.let { btn ->
                buttonsByCommand[command] = btn
                btn.setOnClickListener { vm.sendCommand(command) }
            }
        }

        // Hold-to-repeat buttons (volume and channel)
        val repeatButtons = mapOf(
            R.id.btnVolUp to IrControl.Commands.VOL_UP,
            R.id.btnVolDown to IrControl.Commands.VOL_DOWN,
            R.id.btnChUp to IrControl.Commands.CH_UP,
            R.id.btnChDown to IrControl.Commands.CH_DOWN,
        )

        repeatButtons.forEach { (id, command) ->
            view.findViewById<Button>(id)?.also { btn ->
                buttonsByCommand[command] = btn
            }?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        vm.sendCommand(command)
                        repeatJob = viewLifecycleOwner.lifecycleScope.launch {
                            delay(400) // Initial delay before repeat
                            while (isActive) {
                                vm.sendCommand(command)
                                delay(150) // Repeat interval
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        repeatJob?.cancel()
                        repeatJob = null
                        // Synthesize a click for accessibility services (TalkBack)
                        // — without this, touch-explore users never get a click
                        // event for the volume/channel buttons.
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        repeatJob?.cancel()
                        repeatJob = null
                        true
                    }
                    else -> false
                }
            }
        }

        // Navigation
        btnLearnMore.setOnClickListener {
            vm.navigate(MainViewModel.Screen.LEARN)
        }

        btnBackToHome.setOnClickListener {
            vm.navigate(MainViewModel.Screen.HOME)
        }

        // Flash a button green on success / red on failure for ~150 ms so the
        // user can tell whether the press transmitted. Catches both
        // no-blaster failures and unknown-command misses.
        viewLifecycleOwner.lifecycleScope.launch {
            vm.lastTransmitResult.collect { result ->
                val btn = result?.let { buttonsByCommand[it.commandName] } ?: return@collect
                val tint = if (result.success) R.color.accent_success else R.color.accent_error
                val original = btn.backgroundTintList
                btn.backgroundTintList = androidx.core.content.ContextCompat
                    .getColorStateList(requireContext(), tint)
                btn.postDelayed({ btn.backgroundTintList = original }, 150)
            }
        }
    }

    override fun onDestroyView() {
        repeatJob?.cancel()
        super.onDestroyView()
    }

    private fun shareActiveDevice() {
        val json = vm.exportActiveDeviceJson() ?: return
        val deviceName = vm.activeDevice.value?.name ?: "Spectra device"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Spectra: $deviceName")
            putExtra(android.content.Intent.EXTRA_TEXT, json)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share device profile"))
    }
}
