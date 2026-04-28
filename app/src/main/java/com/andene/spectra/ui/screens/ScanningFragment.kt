package com.andene.spectra.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andene.spectra.R
import com.andene.spectra.ui.MainViewModel
import kotlinx.coroutines.launch

class ScanningFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_scanning, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val logText = view.findViewById<TextView>(R.id.logText)
        val logScroll = view.findViewById<ScrollView>(R.id.logScroll)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.scanLog.collect { lines ->
                    logText.text = lines.joinToString("\n")
                    logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }

        btnCancel.setOnClickListener {
            vm.cancelPassiveScan()
            vm.navigate(MainViewModel.Screen.HOME)
        }
    }
}
