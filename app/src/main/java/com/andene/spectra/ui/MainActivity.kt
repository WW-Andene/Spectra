package com.andene.spectra.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.andene.spectra.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    private lateinit var navController: NavController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        if (!allGranted) {
            val deniedLabels = results.filter { !it.value }.keys
                .map { humanizePermission(it) }
                .distinct()
            android.widget.Toast.makeText(
                this,
                getString(R.string.permission_denial_message, deniedLabels.joinToString(", ")),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun humanizePermission(name: String): String = when (name) {
        Manifest.permission.CAMERA -> getString(R.string.perm_label_camera)
        Manifest.permission.RECORD_AUDIO -> getString(R.string.perm_label_microphone)
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES -> getString(R.string.perm_label_location)
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT -> getString(R.string.perm_label_bluetooth)
        Manifest.permission.TRANSMIT_IR -> getString(R.string.perm_label_ir)
        else -> name.substringAfterLast('.').lowercase().replace('_', ' ')
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the SplashScreen compat shim BEFORE super.onCreate so the
        // platform splash takes over during the cold-start window.
        // androidx.core.splashscreen handles Android 12+ natively and
        // back-fills earlier API levels with the same theme attributes.
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this)

        // Enable edge-to-edge BEFORE super.onCreate per the androidx.activity
        // contract. Android 15 (API 35) enforces edge-to-edge for apps
        // targeting that API; calling this from API 26 onward gives us a
        // forward-compatible setup that draws under system bars.
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Apply window insets to the nav host so fragment content doesn't
        // sit behind the status / nav bars. Each fragment's outer layout
        // already pads itself; this just keeps the host honest about
        // where the safe-area boundary is.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

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

        // Handle a deep-link intent that started this activity, if any.
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // launchMode=singleTask reuses the existing activity for new
        // deep-link intents — route them through the same handler.
        handleDeepLink(intent)
    }

    /**
     * spectra://device/<id> → open Remote with that device active.
     * The URI authority is "device" (manifest-pinned); the first path
     * segment is the device id. Defers to MainViewModel.openDeviceById
     * which surfaces a toast when the id isn't known.
     */
    private fun handleDeepLink(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "spectra") return
        if (uri.host != "device") return
        val deviceId = uri.pathSegments.firstOrNull() ?: return

        // Saved devices load asynchronously on cold-start (SpectraApp's
        // appScope coroutine). Wait up to 2 s for the first non-empty
        // emission then attempt the open. Empty after timeout falls
        // through to openDeviceById's "Device not found" toast.
        lifecycleScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(2_000) {
                vm.savedDevices.first { it.isNotEmpty() }
            }
            vm.openDeviceById(deviceId)
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
