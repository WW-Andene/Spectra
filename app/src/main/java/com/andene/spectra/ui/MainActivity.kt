package com.andene.spectra.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.andene.spectra.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    private lateinit var navController: NavController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        if (!allGranted) {
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

        val host = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = host.navController

        // Mirror the ViewModel's desired screen onto the NavController. We
        // skip when we're already at the target so a re-emission (rotation,
        // process restart) doesn't push a duplicate destination onto the back
        // stack and the current fragment instance survives.
        lifecycleScope.launch {
            vm.screen.distinctUntilChanged().collect { screen ->
                val destId = screen.toDestId()
                if (navController.currentDestination?.id != destId) {
                    navController.navigate(
                        destId,
                        null,
                        androidx.navigation.navOptions {
                            // Going Home wipes the back stack — Home is the root.
                            if (screen == MainViewModel.Screen.HOME) {
                                popUpTo(R.id.dest_home) { inclusive = true }
                            }
                            launchSingleTop = true
                        },
                    )
                }
            }
        }

        // Keep the ViewModel's screen in sync when the user pops the back
        // stack via the system back button. Without this, vm.screen would
        // still claim we're on (say) RESULTS while we're already back at HOME.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            destination.id.toScreen()?.let { current ->
                if (vm.screen.value != current) vm.navigate(current)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!navController.navigateUp()) finish()
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()

        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.TRANSMIT_IR,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
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

    private fun MainViewModel.Screen.toDestId(): Int = when (this) {
        MainViewModel.Screen.HOME -> R.id.dest_home
        MainViewModel.Screen.SCANNING -> R.id.dest_scanning
        MainViewModel.Screen.RESULTS -> R.id.dest_results
        MainViewModel.Screen.LEARN -> R.id.dest_learn
        MainViewModel.Screen.REMOTE -> R.id.dest_remote
        MainViewModel.Screen.DEVICE_EDIT -> R.id.dest_results
        MainViewModel.Screen.MACRO_EDIT -> R.id.dest_macro_edit
    }

    private fun Int.toScreen(): MainViewModel.Screen? = when (this) {
        R.id.dest_home -> MainViewModel.Screen.HOME
        R.id.dest_scanning -> MainViewModel.Screen.SCANNING
        R.id.dest_results -> MainViewModel.Screen.RESULTS
        R.id.dest_learn -> MainViewModel.Screen.LEARN
        R.id.dest_remote -> MainViewModel.Screen.REMOTE
        R.id.dest_macro_edit -> MainViewModel.Screen.MACRO_EDIT
        else -> null
    }
}
