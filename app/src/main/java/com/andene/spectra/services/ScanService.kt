package com.andene.spectra.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.andene.spectra.R
import com.andene.spectra.SpectraApp
import com.andene.spectra.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the passive-scan coroutine so it can outlive
 * a fragment / activity going through onStop. Three motivations:
 *
 * 1. Android 14+ (API 34) requires BLE-touching scans from background to run
 *    inside a foreground service with foregroundServiceType=connectedDevice
 *    — the orchestrator's existing in-VM coroutine path was technically
 *    foreground-only because the activity was alive, but on a real device
 *    the moment the user backgrounds the app the BLE callback budget gets
 *    revoked.
 * 2. The viewModelScope coroutine cancels when the activity is destroyed
 *    by the OS (true process kill, not just rotation). A user who hits
 *    home mid-scan and reopens within seconds previously saw nothing
 *    because the scan died with the activity. The service survives.
 * 3. The notification gives the user a clear "scanning is active" signal
 *    + a tap target to return to the scanning screen.
 *
 * The service does NOT introduce a second source of truth for orchestrator
 * state. It calls the existing [com.andene.spectra.core.SpectraOrchestrator.scanPassive]
 * which writes its own state through StateFlows on the singleton orchestrator;
 * the viewmodel already observes those flows. The service is purely a
 * lifecycle wrapper.
 */
class ScanService : Service() {

    companion object {
        private const val TAG = "ScanService"
        private const val NOTIFICATION_ID = 0xFEE5  // arbitrary stable id
        const val CHANNEL_ID = "spectra_scan_channel"
        const val ACTION_START = "com.andene.spectra.action.START_SCAN"
        const val ACTION_STOP = "com.andene.spectra.action.STOP_SCAN"

        /** Idempotent foreground-service start. Returns the intent that
         *  should be passed to [Context.startForegroundService]. */
        fun startIntent(context: Context): Intent =
            Intent(context, ScanService::class.java).setAction(ACTION_START)

        /** Stop the scan + the service. */
        fun stopIntent(context: Context): Intent =
            Intent(context, ScanService::class.java).setAction(ACTION_STOP)
    }

    // SupervisorJob so a child coroutine failure (the scan) doesn't sink
    // the whole service scope; we still observe the failure ourselves.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null  // started service, no binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopScanAndSelf()
                return START_NOT_STICKY
            }
            else -> startForegroundIfNeeded()
        }

        // Kick off the scan if one isn't already in flight. The orchestrator
        // is a singleton on SpectraApp so the existing scan-state flows
        // continue to drive the UI without any glue here.
        if (scanJob?.isActive == true) {
            Log.d(TAG, "scan already running, ignoring duplicate start")
            return START_NOT_STICKY
        }
        scanJob = serviceScope.launch {
            val orchestrator = (applicationContext as SpectraApp).orchestrator
            try {
                orchestrator.clearLog()
                runScan(orchestrator)
            } catch (e: CancellationException) {
                // User cancelled or service stopped — let the scope unwind.
                throw e
            } catch (e: SecurityException) {
                Log.w(TAG, "scan denied permission", e)
            } catch (e: Exception) {
                Log.e(TAG, "scan failed", e)
            } finally {
                stopScanAndSelf()
            }
        }
        // START_NOT_STICKY: if the OS kills the service it should NOT
        // restart with a null intent — that would re-trigger a scan the
        // user didn't ask for.
        return START_NOT_STICKY
    }

    @RequiresPermission(allOf = [
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_WIFI_STATE,
    ])
    private suspend fun runScan(orchestrator: com.andene.spectra.core.SpectraOrchestrator) {
        orchestrator.scanPassive()
    }

    private fun startForegroundIfNeeded() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires the type to be declared at startForeground.
            // CONNECTED_DEVICE matches the manifest declaration and is the
            // category for BLE / WiFi scans + IR transmission.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.scan_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.scan_channel_description)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Tap-back-to-app intent: opens the existing MainActivity task.
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopPi = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.scan_notification_title))
            .setContentText(getString(R.string.scan_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .addAction(0, getString(R.string.action_cancel), stopPi)
            .build()
    }

    private fun stopScanAndSelf() {
        scanJob?.cancel()
        scanJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Final guard — if the OS tears the service down for any reason,
        // make sure the scope's children don't outlive it.
        serviceScope.cancel()
    }
}
