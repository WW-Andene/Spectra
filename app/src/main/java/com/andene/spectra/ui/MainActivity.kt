package com.andene.spectra.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andene.spectra.R
import com.andene.spectra.ui.screens.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        if (!allGranted) {
            // Show which permissions are missing
            val denied = results.filter { !it.value }.keys
            android.widget.Toast.makeText(
                this,
                "Missing permissions: ${denied.joinToString(", ") { it.substringAfterLast('.') }}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        // Navigate based on ViewModel screen state
        lifecycleScope.launch {
            vm.screen.collect { screen ->
                val fragment: Fragment = when (screen) {
                    MainViewModel.Screen.HOME -> HomeFragment()
                    MainViewModel.Screen.SCANNING -> ScanningFragment()
                    MainViewModel.Screen.RESULTS -> ResultsFragment()
                    MainViewModel.Screen.LEARN -> LearnFragment()
                    MainViewModel.Screen.REMOTE -> RemoteFragment()
                    MainViewModel.Screen.DEVICE_EDIT -> ResultsFragment() // Reuse results for now
                    MainViewModel.Screen.MACRO_EDIT -> MacroEditFragment()
                }

                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit()
            }
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()

        // Core permissions
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.TRANSMIT_IR,
        )

        // Android 12+ BLE permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Android 13+ nearby wifi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        for (perm in permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm)
            }
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        when (vm.screen.value) {
            MainViewModel.Screen.HOME -> super.onBackPressed()
            MainViewModel.Screen.SCANNING -> vm.navigate(MainViewModel.Screen.HOME)
            MainViewModel.Screen.RESULTS -> vm.navigate(MainViewModel.Screen.HOME)
            MainViewModel.Screen.LEARN -> vm.navigate(MainViewModel.Screen.RESULTS)
            MainViewModel.Screen.REMOTE -> vm.navigate(MainViewModel.Screen.HOME)
            MainViewModel.Screen.DEVICE_EDIT -> vm.navigate(MainViewModel.Screen.HOME)
            MainViewModel.Screen.MACRO_EDIT -> vm.navigate(MainViewModel.Screen.HOME)
        }
    }
}
