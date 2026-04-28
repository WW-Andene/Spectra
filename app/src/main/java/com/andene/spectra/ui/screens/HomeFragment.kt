package com.andene.spectra.ui.screens

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andene.spectra.R
import com.andene.spectra.data.models.Macro
import com.andene.spectra.ui.MainViewModel
import com.andene.spectra.ui.components.DeviceAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private val vm: MainViewModel by activityViewModels()

    private val scanPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Essential permissions block the scan. Optional ones (currently just
        // POST_NOTIFICATIONS — the foreground notification is nice-to-have,
        // not required for scanning) don't block: a user who denied notifs
        // can still run a perfectly functional scan, they just won't see the
        // ongoing-scan banner. The previous all-or-nothing check refused to
        // start scanning whenever any permission was denied which gated
        // scanning behind cosmetic notification approval on Android 13+.
        val essentialDenied = results.filter { (perm, granted) ->
            !granted && perm != Manifest.permission.POST_NOTIFICATIONS
        }
        if (essentialDenied.isEmpty()) {
            vm.startPassiveScan()
        } else {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.scan_permissions_required),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    // SAF: user picks where to write the backup. We use CreateDocument so
    // the system file-picker takes care of the directory choice + filename
    // confirmation; no WRITE_EXTERNAL_STORAGE permission needed because the
    // resulting URI grants persistent write to that one document. The
    // pre-filled filename is set via launcher.launch(initialName).
    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        writeBackupTo(uri)
    }

    // SAF: user picks an existing backup file. Pulled bytes off the IO
    // dispatcher so a large library doesn't jank the UI.
    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        confirmAndRestoreFrom(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, c, false)

    /**
     * B-214 onboarding: show a welcome dialog on the first launch
     * explaining what the app does and how to use it. Stored under
     * a SharedPreferences flag so subsequent launches stay clean.
     * Triggered once after onViewCreated runs, deferred via post()
     * so the home screen's status dots and device list have rendered
     * underneath the dialog.
     */
    private fun maybeShowOnboarding(view: View) {
        val prefs = requireContext().getSharedPreferences(PREFS_ONBOARDING, android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDED, false)) return
        view.post {
            // Re-check inside post() — fragment may have been torn down
            // between onViewCreated and the post running.
            if (!isAdded) return@post
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.onboarding_title)
                .setMessage(R.string.onboarding_message)
                .setPositiveButton(R.string.onboarding_got_it) { _, _ ->
                    prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                }
                .setNegativeButton(R.string.onboarding_show_later, null)
                .setCancelable(false)
                .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnScan = view.findViewById<Button>(R.id.btnScan)
        val deviceList = view.findViewById<RecyclerView>(R.id.deviceList)
        val emptyState = view.findViewById<TextView>(R.id.emptyState)
        val statusIr = view.findViewById<TextView>(R.id.statusIr)
        val statusEm = view.findViewById<TextView>(R.id.statusEm)
        val statusMic = view.findViewById<TextView>(R.id.statusMic)
        val statusRf = view.findViewById<TextView>(R.id.statusRf)

        // Hardware / permission status — dim the dot when its capability isn't
        // available. Mic and RF dim based on runtime grant; IR and EM dim based
        // on hardware presence. Long-press for an explanation.
        statusIr.alpha = if (vm.hasIrBlaster()) 1f else 0.3f
        statusEm.alpha = if (vm.hasMagnetometer()) 1f else 0.3f
        statusMic.alpha = if (hasPermission(Manifest.permission.RECORD_AUDIO)) 1f else 0.3f
        statusRf.alpha = if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) 1f else 0.3f
        statusIr.setOnLongClickListener { explainCapability(R.string.cap_ir); true }
        statusEm.setOnLongClickListener { explainCapability(R.string.cap_em); true }
        statusMic.setOnLongClickListener { explainCapability(R.string.cap_mic); true }
        statusRf.setOnLongClickListener { explainCapability(R.string.cap_rf); true }

        // Device list
        val adapter = DeviceAdapter(
            onDeviceClick = { vm.selectDevice(it) },
            onDeviceLongClick = { device ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(device.name ?: getString(R.string.device_default_label))
                    .setItems(arrayOf(
                        getString(R.string.action_open_remote_short),
                        getString(R.string.action_set_room),
                        getString(R.string.action_set_network_endpoint),
                        getString(R.string.action_delete),
                    )) { _, which ->
                        when (which) {
                            0 -> vm.selectDevice(device)
                            1 -> showRoomDialog(device)
                            2 -> showNetworkEndpointDialog(device)
                            3 -> vm.deleteDevice(device.id)
                        }
                    }
                    .show()
            }
        )

        deviceList.layoutManager = LinearLayoutManager(requireContext())
        deviceList.adapter = adapter

        // B-215: combine devices + room filter so the list re-renders
        // when either changes. Filter chips above the list let the user
        // limit display to one room.
        val roomFilterScroll = view.findViewById<View>(R.id.roomFilterScroll)
        val roomFilterChips = view.findViewById<LinearLayout>(R.id.roomFilterChips)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(vm.savedDevices, vm.roomFilter) { d, r -> d to r }
                    .collect { (devices, roomFilter) ->
                        val rooms = devices.mapNotNull { it.room }.distinct().sorted()
                        renderRoomFilterChips(roomFilterChips, rooms, roomFilter)
                        roomFilterScroll.visibility =
                            if (rooms.size >= 2) View.VISIBLE else View.GONE
                        val filtered = if (roomFilter == null) devices
                            else devices.filter { it.room == roomFilter }
                        adapter.submitList(filtered)
                        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                        deviceList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        btnScan.setOnClickListener {
            val missing = scanPermissionsToRequest()
            if (missing.isEmpty()) vm.startPassiveScan()
            else scanPermissionLauncher.launch(missing.toTypedArray())
        }

        view.findViewById<Button>(R.id.btnImportClipboard).setOnClickListener {
            importFromClipboard()
        }

        // Overflow menu: backup / restore. Hosted in the header next to
        // the title rather than buried in a settings screen because the
        // app has no settings screen yet and these two actions are the
        // primary "library management" affordances.
        view.findViewById<ImageButton>(R.id.btnOverflow).setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                menu.add(0, MENU_ALL_OFF, 0, R.string.action_all_off)
                menu.add(0, MENU_SLEEP_TIMER, 1, R.string.action_sleep_timer)
                menu.add(0, MENU_QS_TILE_TARGET, 2, R.string.action_qs_tile_target)
                menu.add(0, MENU_BACKUP, 3, R.string.action_backup_library)
                menu.add(0, MENU_RESTORE, 4, R.string.action_restore_library)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_ALL_OFF -> { vm.runAllOff(); true }
                        MENU_SLEEP_TIMER -> { showSleepTimerDialog(); true }
                        MENU_QS_TILE_TARGET -> { showQuickTileTargetDialog(); true }
                        MENU_BACKUP -> { startBackupExport(); true }
                        MENU_RESTORE -> { startBackupImport(); true }
                        else -> false
                    }
                }
                show()
            }
        }

        // Resumable brute-force banner: shows when a previous sweep was
        // interrupted. Resume picks up from the saved attempt index;
        // Dismiss drops the checkpoint without acting.
        val resumeBanner = view.findViewById<LinearLayout>(R.id.resumeBanner)
        val resumeBannerText = view.findViewById<TextView>(R.id.resumeBannerText)
        view.findViewById<Button>(R.id.btnResumeBruteForce).setOnClickListener {
            vm.resumeBruteForce()
        }
        view.findViewById<Button>(R.id.btnDismissResume).setOnClickListener {
            vm.discardResumableBruteForce()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.resumableBruteForce.collect { checkpoint ->
                    if (checkpoint != null) {
                        resumeBanner.visibility = View.VISIBLE
                        resumeBannerText.text = getString(
                            R.string.resume_brute_force_format,
                            checkpoint.deviceName,
                            checkpoint.nextAttemptIndex,
                        )
                    } else {
                        resumeBanner.visibility = View.GONE
                    }
                }
            }
        }

        // Sleep-timer countdown banner (B-005 phase 2). Tick once a minute
        // while active; render "X minute(s) left for '<label>'". Cancel
        // button kills the alarm and clears the persisted record. We use
        // a fragment-scoped coroutine instead of a viewmodel flow because
        // SleepTimer's source of truth is SharedPreferences (so it can be
        // read by BroadcastReceivers in any process state) — adding a
        // viewmodel flow would just be a less-reliable cache of the same
        // state.
        val sleepTimerBanner = view.findViewById<LinearLayout>(R.id.sleepTimerBanner)
        val sleepTimerText = view.findViewById<TextView>(R.id.sleepTimerText)
        view.findViewById<Button>(R.id.btnCancelSleepTimer).setOnClickListener {
            com.andene.spectra.scheduling.SleepTimer.cancel(requireContext())
            sleepTimerBanner.visibility = View.GONE
            toast(getString(R.string.sleep_timer_cancelled))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val active = com.andene.spectra.scheduling.SleepTimer.active(requireContext())
                    if (active != null) {
                        // Round up to the next minute so a 0:30-remaining
                        // doesn't render as "0 minutes" — the user expects
                        // to see "1 minute" until it actually fires.
                        val mins = (active.remainingMs + 59_000L) / 60_000L
                        sleepTimerText.text = getString(
                            R.string.sleep_timer_banner_format, active.label, mins,
                        )
                        sleepTimerBanner.visibility = View.VISIBLE
                    } else {
                        sleepTimerBanner.visibility = View.GONE
                    }
                    kotlinx.coroutines.delay(60_000L)
                }
            }
        }

        // Surface viewmodel-emitted toasts (save failures, etc.). Home is
        // always the surviving root destination so collecting here gives us
        // user feedback for every persistence error regardless of which
        // screen the user was on when the save fired.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.toasts.collect { msg ->
                    android.widget.Toast.makeText(
                        requireContext(), msg, android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        // Undo affordance for delete actions. The viewmodel snapshots the
        // deleted item before removing it; this collector shows a Snackbar
        // with UNDO that restores the item if tapped.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.undoActions.collect { action ->
                    val label = when (action) {
                        is MainViewModel.UndoAction.Device ->
                            getString(R.string.undo_deleted_device, action.profile.name ?: "device")
                        is MainViewModel.UndoAction.Macro ->
                            getString(R.string.undo_deleted_macro, action.macro.name)
                    }
                    com.google.android.material.snackbar.Snackbar
                        .make(requireView(), label, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo) { vm.undoDelete(action) }
                        .show()
                }
            }
        }

        // ── Macros ────────────────────────────────────────────
        val macroChips = view.findViewById<LinearLayout>(R.id.macroChips)
        val macroRunning = view.findViewById<TextView>(R.id.macroRunning)
        view.findViewById<Button>(R.id.btnNewMacro).setOnClickListener {
            vm.openMacroEditor(null)
        }

        // Re-render chips whenever macros OR savedDevices change, so the
        // stale-step-count badge stays accurate after a device delete.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(vm.macros, vm.savedDevices) { m, d -> m to d }
                    .collect { (macros, devices) ->
                        renderMacroChips(macroChips, macros, devices.map { it.id }.toSet())
                    }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.runningMacro.collect { running ->
                    if (running != null) {
                        macroRunning.visibility = View.VISIBLE
                        macroRunning.text = getString(
                            R.string.macro_running_format,
                            running.name, running.currentStep, running.totalSteps, running.currentLabel,
                        )
                    } else {
                        macroRunning.visibility = View.GONE
                    }
                }
            }
        }

        maybeShowOnboarding(view)
    }

    private fun hasPermission(name: String) =
        ContextCompat.checkSelfPermission(requireContext(), name) == PackageManager.PERMISSION_GRANTED

    private fun explainCapability(stringRes: Int) {
        android.widget.Toast.makeText(
            requireContext(),
            getString(stringRes),
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * The runtime permissions a passive scan needs that aren't yet granted.
     * Mirrors what the orchestrator's @RequiresPermission annotations declare:
     * audio for acoustic + EM, fine location + BLE for RF.
     */
    private fun scanPermissionsToRequest(): List<String> {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            // ScanService's foreground notification needs this on API 33+.
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return needed.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun importFromClipboard() {
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val text = cm.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(requireContext())
            ?.toString()
            ?.trim()
        if (text.isNullOrEmpty() || !text.startsWith("{")) {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.clipboard_not_json),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        vm.importDeviceFromJson(text) { imported ->
            val msg = if (imported != null) {
                "Imported ${imported.name ?: "device"} with ${imported.irProfile?.commands?.size ?: 0} commands"
            } else getString(R.string.clipboard_parse_failed)
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun renderMacroChips(
        container: LinearLayout,
        macros: List<Macro>,
        knownDeviceIds: Set<String>,
    ) {
        container.removeAllViews()
        if (macros.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.empty_macros)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
                textSize = 12f
                setPadding(0, 0, 0, 0)
            }
            container.addView(empty)
            return
        }
        val pad = (12 * resources.displayMetrics.density).toInt()
        val chipBg = ContextCompat.getColor(requireContext(), R.color.bg_card_elevated)
        val chipText = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val warnText = ContextCompat.getColor(requireContext(), R.color.accent_warning)
        for (macro in macros) {
            val staleCount = macro.steps.count { it.deviceId !in knownDeviceIds }
            val chip = TextView(requireContext()).apply {
                // Append a "(N stale)" suffix in the warning colour so the
                // user notices that some steps point to deleted devices
                // before they tap the chip and get a half-running macro.
                text = if (staleCount == 0) macro.name
                       else "${macro.name} ⚠"
                if (staleCount > 0) setTextColor(warnText) else setTextColor(chipText)
                textSize = 13f
                setPadding(pad, pad / 2, pad, pad / 2)
                // Solid card-elevated colour for the chip with built-in press
                // feedback. selectableItemBackground gets the platform ripple
                // overlaid on top of the colour.
                setBackgroundColor(chipBg)
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true
                )
                foreground = ContextCompat.getDrawable(requireContext(), outValue.resourceId)
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { rightMargin = pad / 2 }
                layoutParams = lp
                setOnClickListener { vm.runMacro(macro.id) }
                setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(macro.name)
                        .setItems(arrayOf(
                            getString(R.string.action_run),
                            getString(R.string.action_edit),
                            getString(R.string.action_copy_nfc_uri),
                            getString(R.string.action_delete),
                        )) { _, which ->
                            when (which) {
                                0 -> vm.runMacro(macro.id)
                                1 -> vm.openMacroEditor(macro)
                                2 -> copyMacroNfcUri(macro)
                                3 -> AlertDialog.Builder(requireContext())
                                    .setMessage(getString(R.string.confirm_delete_macro, macro.name))
                                    .setPositiveButton(R.string.action_delete) { _, _ -> vm.deleteMacro(macro.id) }
                                    .setNegativeButton(R.string.action_cancel_dialog, null)
                                    .show()
                            }
                        }
                        .show()
                    true
                }
            }
            container.addView(chip)
        }
    }

    // ── Library backup / restore ──────────────────────────────────

    private fun startBackupExport() {
        // B-203: ask up-front whether to anonymise. Public-share
        // backups (forum posts, shared groups) should strip BSSID +
        // BLE addresses; backups for the user's own re-import keep
        // them so cross-session matching still works.
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.backup_export_anonymise_title)
            .setMessage(R.string.backup_export_anonymise_message)
            .setPositiveButton(R.string.backup_export_anonymise_yes) { _, _ ->
                pendingExportAnonymize = true
                launchExportPicker()
            }
            .setNegativeButton(R.string.backup_export_anonymise_no) { _, _ ->
                pendingExportAnonymize = false
                launchExportPicker()
            }
            .show()
    }

    private var pendingExportAnonymize = false

    private fun launchExportPicker() {
        // Pre-fill the picker with a date-stamped filename so users
        // building a rolling history don't have to retype every time.
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val suffix = if (pendingExportAnonymize) "-anon" else ""
        backupExportLauncher.launch(
            getString(R.string.backup_filename_format, "$stamp$suffix"),
        )
    }

    private fun startBackupImport() {
        // application/json is the canonical type but some file managers
        // hand spectra-backup-*.json files as octet-stream — accept both
        // so the picker doesn't filter out a valid backup.
        backupImportLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    private fun writeBackupTo(uri: Uri) {
        vm.exportLibrary(anonymize = pendingExportAnonymize) { jsonText ->
            if (jsonText == null) {
                toast(getString(R.string.backup_export_failed))
                return@exportLibrary
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val (devices, macros) = withContext(Dispatchers.IO) {
                    try {
                        // Open with "wt" — truncate-then-write — so re-using
                        // an existing backup filename doesn't append garbage
                        // onto the end of the prior file.
                        requireContext().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            out.write(jsonText.toByteArray(Charsets.UTF_8))
                        } ?: return@withContext null
                        // Quick parse-back to count what we just wrote, so
                        // the success toast actually reflects the saved
                        // contents instead of advertising "0 device(s)" if
                        // the export round-tripped through an empty library.
                        val deviceCount = countTopLevelArray(jsonText, "devices")
                        val macroCount = countTopLevelArray(jsonText, "macros")
                        deviceCount to macroCount
                    } catch (_: Exception) { null }
                } ?: run {
                    toast(getString(R.string.backup_export_failed))
                    return@launch
                }
                toast(getString(R.string.backup_export_success_format, devices, macros))
            }
        }
    }

    private fun confirmAndRestoreFrom(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restore_confirm_title)
            .setMessage(R.string.restore_confirm_message)
            .setPositiveButton(R.string.action_restore_library) { _, _ -> performRestore(uri) }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    private fun performRestore(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                } catch (_: Exception) { null }
            }
            if (text.isNullOrBlank()) {
                toast(getString(R.string.restore_failed_invalid))
                return@launch
            }
            vm.importLibrary(text) { result ->
                if (result == null) {
                    toast(getString(R.string.restore_failed_invalid))
                    return@importLibrary
                }
                val skips = result.devicesSkipped + result.macrosSkipped
                val msg = when {
                    result.devicesImported == 0 && result.macrosImported == 0 ->
                        getString(R.string.restore_nothing_imported)
                    skips > 0 -> getString(
                        R.string.restore_summary_with_skips_format,
                        result.devicesImported, result.macrosImported, skips,
                    )
                    else -> getString(
                        R.string.restore_summary_format,
                        result.devicesImported, result.macrosImported,
                    )
                }
                toast(msg)
            }
        }
    }

    /**
     * B-209: dialog for setting a device's non-IR control endpoint.
     * Two presets (Roku ECP, IR bridge) with the right scheme prefix
     * pre-filled, plus a free-form text field for the IP. Saves on OK,
     * clears on the explicit clear button.
     */
    private fun showNetworkEndpointDialog(device: com.andene.spectra.data.models.DeviceProfile) {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = getString(R.string.endpoint_hint)
            setText(device.controlEndpoint ?: "")
        }
        container.addView(input, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        val help = TextView(ctx).apply {
            text = getString(R.string.endpoint_help)
            textSize = 12f
            setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_tertiary))
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, 0)
        }
        container.addView(help)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.endpoint_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.action_save_button) { _, _ ->
                vm.setControlEndpoint(device.id, input.text?.toString())
            }
            .setNeutralButton(R.string.endpoint_clear) { _, _ ->
                vm.setControlEndpoint(device.id, null)
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    /**
     * B-215: render room filter chips. Always includes an "All" chip
     * that clears the filter; one chip per distinct non-null room.
     * Selected chip gets the accent fill; others stay subdued.
     */
    private fun renderRoomFilterChips(
        container: LinearLayout,
        rooms: List<String>,
        selected: String?,
    ) {
        container.removeAllViews()
        val pad = (12 * resources.displayMetrics.density).toInt()
        val gap = (4 * resources.displayMetrics.density).toInt()
        val ctx = requireContext()
        val accentBg = androidx.core.content.ContextCompat.getColor(ctx, R.color.accent_primary)
        val mutedBg = androidx.core.content.ContextCompat.getColor(ctx, R.color.bg_card_elevated)
        val textOn = androidx.core.content.ContextCompat.getColor(ctx, R.color.bg_primary)
        val textOff = androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary)

        fun addChip(label: String, isSelected: Boolean, onTap: () -> Unit) {
            val chip = TextView(ctx).apply {
                text = label
                textSize = 12f
                setPadding(pad, pad / 2, pad, pad / 2)
                setBackgroundColor(if (isSelected) accentBg else mutedBg)
                setTextColor(if (isSelected) textOn else textOff)
                setOnClickListener { onTap() }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = gap }
            chip.layoutParams = lp
            container.addView(chip)
        }

        addChip(getString(R.string.room_filter_all), selected == null) {
            vm.selectRoomFilter(null)
        }
        for (room in rooms) {
            addChip(room, selected == room) { vm.selectRoomFilter(room) }
        }
    }

    /**
     * B-215: dialog for assigning a device to a room (or clearing).
     * Free-form text input; existing rooms surface as
     * suggestions via setSingleChoiceItems on a future enhancement.
     */
    private fun showRoomDialog(device: com.andene.spectra.data.models.DeviceProfile) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = getString(R.string.room_hint)
            setText(device.room ?: "")
        }
        container.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        AlertDialog.Builder(ctx)
            .setTitle(R.string.room_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.action_save_button) { _, _ ->
                vm.setRoom(device.id, input.text?.toString())
            }
            .setNeutralButton(R.string.endpoint_clear) { _, _ ->
                vm.setRoom(device.id, null)
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
    }

    /**
     * B-007 phase 2 (lite): copy the macro's NFC trigger URI to the
     * clipboard so the user can write it to a tag with any existing
     * NFC writer app (NFC Tools, TagWriter, etc.). A native in-app
     * tag-write flow is queued for later — this gets users 90% of
     * the value with zero new code paths.
     */
    private fun copyMacroNfcUri(macro: Macro) {
        val uri = "spectra://macro/${macro.id}"
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Spectra macro URI", uri))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.nfc_uri_dialog_title)
            .setMessage(getString(R.string.nfc_uri_dialog_message_format, uri))
            .setPositiveButton(R.string.action_ok_dialog, null)
            .show()
    }

    /**
     * Lightweight count of the entries in a top-level JSON array named
     * [key]. We only need a coarse number for the success toast — full
     * deserialization would parse the whole library twice. Looks for
     * `"<key>": [` then counts `{` at brace-depth 1 inside that array.
     * Returns 0 when the key isn't found.
     */
    private fun countTopLevelArray(jsonText: String, key: String): Int {
        val anchor = "\"$key\""
        val anchorIdx = jsonText.indexOf(anchor)
        if (anchorIdx < 0) return 0
        val openIdx = jsonText.indexOf('[', startIndex = anchorIdx)
        if (openIdx < 0) return 0
        var depth = 0
        var count = 0
        var i = openIdx
        while (i < jsonText.length) {
            when (jsonText[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return count }
                '{' -> if (depth == 1) count++
            }
            i++
        }
        return count
    }

    /**
     * Sleep-timer dialog (B-005). Pick an existing macro + duration.
     * Schedules an inexact AlarmManager fire (setAndAllowWhileIdle)
     * that lands within a few minutes of the requested time, runs
     * through Doze, and doesn't need SCHEDULE_EXACT_ALARM.
     *
     * Design decision: only persisted macros are scheduleable. A
     * synthesized "All off at fire-time" target was considered and
     * dropped — it would have either required permanently parking a
     * library-internal macro on disk for the receiver to load, or a
     * delete-after-fire dance with edge cases when the user cancelled
     * partway. Users who want a scheduled all-off can save an All Off
     * macro first via the regular macro editor, then pick it here.
     */
    private fun showSleepTimerDialog() {
        val active = com.andene.spectra.scheduling.SleepTimer.active(requireContext())
        if (active != null) {
            // A timer is already pending — surface a cancel affordance
            // first; the user can re-enter to schedule a new one.
            val remainingMinutes = (active.remainingMs / 60_000L).toInt()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.sleep_timer_active_title)
                .setMessage(getString(R.string.sleep_timer_active_format, active.label, remainingMinutes))
                .setPositiveButton(R.string.action_cancel_timer) { _, _ ->
                    com.andene.spectra.scheduling.SleepTimer.cancel(requireContext())
                    toast(getString(R.string.sleep_timer_cancelled))
                }
                .setNegativeButton(R.string.action_keep_editing, null)
                .show()
            return
        }

        val macros = vm.macros.value
        if (macros.isEmpty()) {
            toast(getString(R.string.sleep_timer_no_macros))
            return
        }

        val labels = macros.map { it.name }.toTypedArray()
        var pickedIndex = 0

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sleep_timer_pick_macro_title)
            .setSingleChoiceItems(labels, 0) { _, which -> pickedIndex = which }
            .setPositiveButton(R.string.action_next) { _, _ ->
                pickSleepDuration(macros[pickedIndex])
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    private fun pickSleepDuration(macro: Macro) {
        val minutes = intArrayOf(15, 30, 45, 60, 90, 120)
        val labels = minutes.map { getString(R.string.sleep_timer_minutes_format, it) }.toTypedArray()
        var pickedIndex = 1  // default 30 min
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sleep_timer_pick_duration_title)
            .setSingleChoiceItems(labels, pickedIndex) { _, which -> pickedIndex = which }
            .setPositiveButton(R.string.action_schedule) { _, _ ->
                com.andene.spectra.scheduling.SleepTimer.scheduleMacro(
                    requireContext(), macro.id, macro.name, minutes[pickedIndex],
                )
                toast(getString(R.string.sleep_timer_scheduled_format, macro.name, minutes[pickedIndex]))
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    /**
     * B-003 phase 2: pick which target the Quick Settings tile fires.
     *
     * Tiles don't get a system-invoked configuration activity (only
     * widgets do), so the picker lives here. Two-step dialog to keep
     * the flat list of (every macro) ⊕ (every device with POWER) from
     * cluttering: first choose macro vs single command, then pick the
     * specific entry. "Reset to default (auto-pick)" clears the
     * binding so the tile falls back to the phase 1 primary-device
     * auto-pick.
     */
    private fun showQuickTileTargetDialog() {
        val ctx = requireContext()
        val current = com.andene.spectra.widget.QuickTileConfigStore.get(ctx)
        val items = arrayOf(
            getString(R.string.qs_tile_target_pick_macro),
            getString(R.string.qs_tile_target_pick_command),
            getString(R.string.qs_tile_target_reset),
        )
        AlertDialog.Builder(ctx)
            .setTitle(R.string.qs_tile_target_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickQsTileMacro()
                    1 -> pickQsTileCommand()
                    2 -> {
                        com.andene.spectra.widget.QuickTileConfigStore.clear(ctx)
                        toast(getString(R.string.qs_tile_target_reset_done))
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
        // Show the current binding (if any) as a hint above the picker.
        if (current != null) toast(getCurrentBindingLabel(current))
    }

    private fun getCurrentBindingLabel(b: com.andene.spectra.widget.QuickTileConfigStore.Binding): String =
        when (b) {
            is com.andene.spectra.widget.QuickTileConfigStore.Binding.Macro ->
                getString(R.string.qs_tile_target_current_macro_format,
                    vm.macros.value.firstOrNull { it.id == b.macroId }?.name ?: "?")
            is com.andene.spectra.widget.QuickTileConfigStore.Binding.Command ->
                getString(R.string.qs_tile_target_current_command_format,
                    vm.savedDevices.value.firstOrNull { it.id == b.deviceId }?.name ?: "?",
                    b.commandName)
        }

    private fun pickQsTileMacro() {
        val macros = vm.macros.value
        if (macros.isEmpty()) {
            toast(getString(R.string.sleep_timer_no_macros))
            return
        }
        val labels = macros.map { it.name }.toTypedArray()
        var picked = 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.qs_tile_target_pick_macro)
            .setSingleChoiceItems(labels, 0) { _, which -> picked = which }
            .setPositiveButton(R.string.action_save_button) { _, _ ->
                com.andene.spectra.widget.QuickTileConfigStore.set(
                    requireContext(),
                    com.andene.spectra.widget.QuickTileConfigStore.Binding.Macro(macros[picked].id),
                )
                toast(getString(R.string.qs_tile_target_set_format, macros[picked].name))
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    private fun pickQsTileCommand() {
        val devices = vm.savedDevices.value
        if (devices.isEmpty()) {
            toast(getString(R.string.qs_tile_target_no_devices))
            return
        }
        val labels = devices.map { it.name ?: getString(R.string.device_default_label) }.toTypedArray()
        var pickedDeviceIndex = 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.qs_tile_target_pick_device)
            .setSingleChoiceItems(labels, 0) { _, which -> pickedDeviceIndex = which }
            .setPositiveButton(R.string.action_next) { _, _ ->
                pickQsTileCommandFor(devices[pickedDeviceIndex])
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    private fun pickQsTileCommandFor(device: com.andene.spectra.data.models.DeviceProfile) {
        val commands = device.irProfile?.commands?.keys?.toList()?.sorted().orEmpty()
        if (commands.isEmpty()) {
            toast(getString(R.string.qs_tile_target_no_commands))
            return
        }
        val labels = commands.toTypedArray()
        var picked = 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.qs_tile_target_pick_command_title)
            .setSingleChoiceItems(labels, 0) { _, which -> picked = which }
            .setPositiveButton(R.string.action_save_button) { _, _ ->
                com.andene.spectra.widget.QuickTileConfigStore.set(
                    requireContext(),
                    com.andene.spectra.widget.QuickTileConfigStore.Binding.Command(device.id, commands[picked]),
                )
                toast(getString(
                    R.string.qs_tile_target_set_format,
                    "${device.name ?: getString(R.string.device_default_label)} · ${commands[picked]}",
                ))
            }
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .show()
    }

    companion object {
        private const val MENU_ALL_OFF = 1
        private const val MENU_SLEEP_TIMER = 2
        private const val MENU_QS_TILE_TARGET = 3
        private const val MENU_BACKUP = 4
        private const val MENU_RESTORE = 5

        // B-214 onboarding state.
        private const val PREFS_ONBOARDING = "spectra_onboarding"
        private const val KEY_ONBOARDED = "completed"
    }
}
