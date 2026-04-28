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
                        getString(R.string.action_delete),
                    )) { _, which ->
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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.savedDevices.collect { devices ->
                    adapter.submitList(devices)
                    emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                    deviceList.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
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
                menu.add(0, MENU_BACKUP, 1, R.string.action_backup_library)
                menu.add(0, MENU_RESTORE, 2, R.string.action_restore_library)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_ALL_OFF -> { vm.runAllOff(); true }
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
        // Pre-fill the picker with a date-stamped filename so users
        // building a rolling history don't have to retype every time.
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        backupExportLauncher.launch(getString(R.string.backup_filename_format, stamp))
    }

    private fun startBackupImport() {
        // application/json is the canonical type but some file managers
        // hand spectra-backup-*.json files as octet-stream — accept both
        // so the picker doesn't filter out a valid backup.
        backupImportLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
    }

    private fun writeBackupTo(uri: Uri) {
        vm.exportLibrary { jsonText ->
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

    companion object {
        private const val MENU_ALL_OFF = 1
        private const val MENU_BACKUP = 2
        private const val MENU_RESTORE = 3
    }
}
