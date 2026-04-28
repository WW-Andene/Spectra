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
        val btnBackToHome = view.findViewById<Button>(R.id.btnBackToHome)

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

        tapButtons.forEach { (id, command) ->
            view.findViewById<Button>(id)?.setOnClickListener {
                vm.sendCommand(command)
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
            view.findViewById<Button>(id)?.setOnTouchListener { _, event ->
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
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
    }

    override fun onDestroyView() {
        repeatJob?.cancel()
        super.onDestroyView()
    }
}
