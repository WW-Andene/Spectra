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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andene.spectra.R
import com.andene.spectra.ui.MainViewModel
import com.andene.spectra.ui.components.DeviceAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnScan = view.findViewById<Button>(R.id.btnScan)
        val deviceList = view.findViewById<RecyclerView>(R.id.deviceList)
        val emptyState = view.findViewById<TextView>(R.id.emptyState)
        val statusIr = view.findViewById<TextView>(R.id.statusIr)
        val statusEm = view.findViewById<TextView>(R.id.statusEm)

        // Hardware status
        statusIr.alpha = if (vm.hasIrBlaster()) 1f else 0.3f
        statusEm.alpha = if (vm.hasMagnetometer()) 1f else 0.3f

        // Device list
        val adapter = DeviceAdapter(
            onDeviceClick = { vm.selectDevice(it) },
            onDeviceLongClick = { device ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(device.name ?: "Device")
                    .setItems(arrayOf("Open Remote", "Delete")) { _, which ->
                        when (which) {
                            0 -> vm.selectDevice(device)
                            1 -> vm.deleteDevice(device.id)
                        }
                    }
                    .show()
            }
        )

        deviceList.layoutManager = LinearLayoutManager(requireContext())
        deviceList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            vm.savedDevices.collect { devices ->
                adapter.submitList(devices)
                emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                deviceList.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        btnScan.setOnClickListener {
            vm.startPassiveScan()
        }
    }
}
