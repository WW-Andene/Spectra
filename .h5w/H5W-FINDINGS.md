# H5W Findings — Spectra (full §FMT)

Compact priority view in `H5W-QUEUE.md`. This file holds the per-finding §FMT
detail for each entry. Findings are listed in queue order.

---

```
══════════════════════════════════════
FINDING: F-001
MODULE:  H5W
══════════════════════════════════════
SEVERITY:    high
CONFIDENCE:  confirmed
SOURCE:      [CODE: ui/MainActivity.kt:36-95, ui/MainViewModel.kt:201-219]
COMPOUNDS:   yes ⏱ → F-002

How:   MainActivity.requestPermissions() runs once in onCreate and registers a
       launcher. Tapping SCAN runs vm.startPassiveScan(), which sets _screen=
       SCANNING and immediately invokes orchestrator.scanPassive(). If any
       permission isn't yet granted, modules throw SecurityException, caught
       and logged into scanLog as "Permission denied: <message>".
Who:   P1 first-time (taps Scan before reading the permission dialogs);
       P3 hostile-env (denies a permission, then tries to scan).
Will:  User sees the Scanning screen briefly, then gets dropped into Results
       with a stale or empty discoveredDevice. They don't know what failed
       or why.
What:  Disable Scan until all required runtime permissions are granted.
       Trigger requestPermissions() from the Scan onClick if any are missing
       and only proceed when all are granted.
When:  First app launch + every cold start where permissions are revoked.
Where: ui/MainActivity.kt requestPermissions(); ui/screens/HomeFragment.kt
       btnScan onClick handler.

FIX:        Move permission request into the Scan flow. Add a hasAllPermissions()
            helper to MainActivity exposed via vm, gate startPassiveScan on it.
TIER:       T1
EXPANSION:  Other permission-gated entry points (camera in Learn) — same pattern.
══════════════════════════════════════
```

---

```
══════════════════════════════════════
FINDING: F-002
MODULE:  H5W
══════════════════════════════════════
SEVERITY:    high
CONFIDENCE:  confirmed
SOURCE:      [CODE: ui/screens/ScanningFragment.kt:36-38, ui/MainViewModel.kt:201-219]
COMPOUNDS:   yes ⏱ → F-001

How:   ScanningFragment's btnCancel calls vm.navigate(HOME) only. The
       startPassiveScan() coroutine launched in viewModelScope keeps running.
       When it eventually returns, line 210 sets _screen = RESULTS, yanking
       the user out of HOME.
Who:   P3 hostile-env (cancels mid-scan after realizing they're in the wrong
       room); any user who taps Cancel and expects "stop".
Will:  After ~6s of background work, user is suddenly on a Results screen
       they explicitly cancelled out of. Confusing, feels like a bug.
What:  Track the scan job, cancel it on btnCancel, and skip the result-screen
       transition when cancelled.
When:  Whenever Cancel is tapped during an active scan.
Where: ui/MainViewModel.kt startPassiveScan() — store the Job, expose
       cancelScan(); ui/screens/ScanningFragment.kt:36 btnCancel handler.

FIX:        Hold the scan Job in vm; cancelScan() cancels it; skip RESULTS
            transition when CancellationException bubbles up.
TIER:       T1
EXPANSION:  Same pattern: brute-force coroutine cancellation, macro coroutine.
══════════════════════════════════════
```

---

```
══════════════════════════════════════
FINDING: F-006
MODULE:  H5W
══════════════════════════════════════
SEVERITY:    high
CONFIDENCE:  confirmed
SOURCE:      [CODE: modules/control/IrControl.kt:147-168, ui/MainViewModel.kt:302-307]
COMPOUNDS:   yes ⏱ → F-016, F-015

How:   IrControl.transmit short-circuits to false when irManager is null or
       hasIrEmitter() is false. _lastTransmitResult is set, but nothing in the
       UI consumes it. vm.sendCommand swallows the boolean. User taps POWER
       and absolutely nothing observable happens.
Who:   P4 IR-less phone (Pixel/Galaxy S/iPhone-equivalent population — the
       majority of modern Android users).
Will:  User concludes the app is broken on first IR button press. Likely
       uninstalls.
What:  Show a one-time toast or banner on Remote screen entry when
       hasIrBlaster() == false, explaining IR transmission isn't available
       and the device profile is store-only.
When:  Any time a no-blaster phone navigates to Remote.
Where: ui/screens/RemoteFragment.kt onViewCreated; or HomeFragment when
       picking a device.

FIX:        Add a small notice TextView at the top of Remote screen that's
            visible only when !vm.hasIrBlaster(). Copy: "This phone has no IR
            blaster — buttons won't transmit. Profile is saved for sharing."
TIER:       T1
EXPANSION:  Same notice should appear on the Learn screen's brute-force
            section (already partially handled there).
══════════════════════════════════════
```

---

```
══════════════════════════════════════
FINDING: F-024
MODULE:  CODE
══════════════════════════════════════
SEVERITY:    high
CONFIDENCE:  confirmed
SOURCE:      [CODE: modules/ir/IrCameraCapture.kt:118-135, 158-196]
COMPOUNDS:   no

How:   analyzeFrame appends one IntArray of size `height` (typically 720)
       per camera frame to `frames`. At 30fps × 720 ints × 4 bytes ≈ 86KB
       per frame. A 30-second long-press accumulates ~78MB; a stuck "I forgot
       to press stop" minute is ~150MB — guaranteed OOM on most devices.
Who:   P2 power user (who might experiment with long captures); any user
       who walks away mid-capture.
Will:  App crashes with OOM, unsaved capture state lost, scary failure mode.
What:  Cap the in-memory buffer. Either drop oldest frames once a soft cap
       (~ 5 seconds × fps) is exceeded, or stop appending and surface a
       "buffer full — press stop" status.
When:  Long captures (>10s) or stuck captures.
Where: modules/ir/IrCameraCapture.kt analyzeFrame, around line 134.

FIX:        Add MAX_CAPTURE_FRAMES const (e.g. 256 = ~8.5s at 30fps); skip
            append once size exceeds it, set state to PROCESSING with a
            warning logged.
TIER:       T1
EXPANSION:  Other unbounded collections — orchestrator.log, scanLog,
            knownSignatures.
══════════════════════════════════════
```

---

```
══════════════════════════════════════
FINDING: F-016
MODULE:  H5W
══════════════════════════════════════
SEVERITY:    medium
CONFIDENCE:  confirmed
SOURCE:      [CODE: ui/screens/RemoteFragment.kt:77-81, modules/control/IrControl.kt]
COMPOUNDS:   yes ⏱ ← F-006

How:   Buttons fire vm.sendCommand which dispatches to IO. The button itself
       has default Material ripple (transient), but no app-level signal that
       the command was actually transmitted. Ripple ≠ action confirmation.
Who:   P1, P2 — any user pressing buttons.
Will:  When IR fails (no blaster, missing command, hardware reject) the user
       has no idea. Ambiguous between "did it send and the TV ignored it" vs
       "did it not send".
What:  Subscribe to IrControl.lastTransmitResult; flash the just-pressed
       button green on success / red on failure for ~150 ms.
When:  Every button press.
Where: ui/screens/RemoteFragment.kt onViewCreated — wrap the click into a
       lifecycleScope launch that awaits result and tints.

FIX:        Light-weight tint flash on button after sendCommand returns.
            Could also use the existing lastTransmitResult as a single
            short-lived StateFlow.
TIER:       T1
EXPANSION:  Same on macro execution — the running banner exists but doesn't
            distinguish success from skipped.
══════════════════════════════════════
```

---

```
══════════════════════════════════════
FINDING: F-010
MODULE:  H5W
══════════════════════════════════════
SEVERITY:    medium
CONFIDENCE:  confirmed
SOURCE:      [CODE: res/layout/fragment_home.xml:34-67, ui/screens/HomeFragment.kt:33-38]
COMPOUNDS:   no

How:   Four colored dots labelled "IR ●", "MIC ●", "RF ●", "EM ●" appear in
       a row. Only IR and EM dim themselves based on hardware availability.
       Mic and RF are always full opacity (also: never updated by code).
Who:   P1 (no idea what these mean); P4 IR-less (sees the dim IR dot but
       no explanation).
Will:  P1 reads "IR" / "EM" as jargon. Power users get hardware status, but
       only for half the modules.
What:  Either drop the dots entirely (utility-aesthetic A5 — invisible
       design) or attach tooltips/expandable hints explaining what each
       capability covers + why it's dimmed.
When:  First Home view.
Where: res/layout/fragment_home.xml status row.

FIX:        Add long-press tooltips: "IR — infrared blaster available".
            Update statusMic/statusRf in HomeFragment from
            ContextCompat.checkSelfPermission(...).
TIER:       T1
EXPANSION:  Hardware availability also affects ResultsFragment buttons.
══════════════════════════════════════
```

---

```
══════════════════════════════════════
FINDING: F-017
MODULE:  H5W
══════════════════════════════════════
SEVERITY:    medium
CONFIDENCE:  confirmed
SOURCE:      [CODE: res/layout/fragment_learn.xml:283-296, ui/screens/LearnFragment.kt]
COMPOUNDS:   no

How:   "OPEN REMOTE" is rendered as enabled regardless of whether the active
       device has any commands. Tap → navigate to Remote → all buttons fire
       sendCommand which silently fails (no command found).
Who:   P1 first-time who hasn't yet learned anything.
Will:  Empty Remote screen presented as if functional; user wastes time
       tapping buttons that can't work.
What:  Disable the button (and dim it) until the active device's IR profile
       has at least one command. Re-enable as commands are added.
When:  Throughout the Learn flow until first command is captured/installed.
Where: ui/screens/LearnFragment.kt — observe activeDevice and toggle
       btnOpenRemote.isEnabled.

FIX:        Add a collector on vm.activeDevice that sets btnOpenRemote.isEnabled
            = (commands.isNotEmpty()). Apply alpha when disabled.
TIER:       T1
EXPANSION:  Same idea for "Test" affordance on each command row already
            implemented. Apply to RemoteFragment too — show empty state
            inside Remote when no commands.
══════════════════════════════════════
```

---

(Findings F-003, F-005, F-007, F-008, F-009, F-011, F-013, F-014, F-015,
F-018, F-019, F-020, F-021, F-022, F-023, F-025, F-026, F-027 hold
similar §FMT detail; condensed in queue table. Each will be expanded
inline in the commit message of its fix.)
