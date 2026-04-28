# H5W Autonomous Run Report

Branch: `claude/understand-project-ATKHd`
Mode (current): `§AUTO-UNCHAINED + §BRAINSTORM + §BUILD-LOOP`
Gate: user-passed ("i accept full responsibility" + "this is my sandbox" + "ship features")

---

## §BUILD-LOOP run — feature shipping (skill v1.4.0)

Audit + queue published in `.h5w/H5W-BUILD.md` covering 11 build
targets across three tiers (user value → competitivity → innovation).
Six targets shipped at least one phase; remaining phases stay queued.

### B-001 — Library backup / restore (closed)

**Commits:** `74ee4df`, `3712eff`

Whole-library JSON envelope (devices + macros) export via SAF
CreateDocument with date-stamped filename; merge import via
OpenDocument, devices get fresh ids, macros merge by id with
replace-on-collision semantics. Per-entry parse failures count as
skipped, not propagated, so partial restores still succeed. UI
lives in a new overflow menu in the Home header (icon, popup
menu — first overflow affordance in the app).

### B-002 phase 1 — Home-screen widget (1×1 power tile)

**Commit:** `1dc241e`

AppWidgetProvider that renders the user's primary device (most-recent
with POWER bound) and a single tap target. Tap → BroadcastReceiver
that re-hydrates the device into IrControl (cold-process safe) and
fires POWER via the existing serialised transmit path. Refreshed
on every save/delete/import via `loadSavedDevices`.

### B-003 phase 1 — Quick Settings tile

**Commit:** `10a76b5`

TileService firing POWER on the same primary device the widget
targets — both routes through new `widget/QuickPowerKit` so they
agree on selection. STATE_INACTIVE flicker during transmit gives
visual feedback even on a phone where the IR burst is silent.
STATE_UNAVAILABLE when no eligible device exists.

### B-008 — Universal "all off" (closed)

**Commit:** `0050d3d`

Overflow menu action that synthesizes an in-memory power-off macro
from every saved device with POWER bound and runs it via the
existing macro runner. 250 ms inter-step delay (small enough to
feel instant, large enough that close-together IR receivers don't
collapse bursts inside their internal debounce). Refactored
`runMacro` to share `executeMacro(macro)` so the synthetic path
inherits the existing stale-step validation, single-active-macro
guard, and progress surface.

### B-007 phase 1 — NFC tag triggers

**Commit:** `57178f4`

Three URI authorities under `spectra://`: `device/<id>`,
`macro/<id>`, `command/<deviceId>/<commandName>`. Manifest
NDEF_DISCOVERED filter scoped to scheme=spectra so taps on encoded
tags route to MainActivity with priority over apps registered for
broader URI scopes. New `MainViewModel.sendCommandTo` for the
command path so an NFC tap doesn't drag the user into the Remote
screen. Phase 2 (in-app foreground-dispatch tag write) deferred
in favour of a clipboard-write affordance: macro long-press →
"Copy NFC trigger URI" copies `spectra://macro/<id>` for paste
into any existing NFC writer app.

### B-002 phase 2 — Pinned-widget config activity

**Commit:** `e4c9b1b`

Each pinned widget now targets a specific device chosen at pin
time, persisted per-appWidgetId. Launcher invokes the new
configuration activity on widget drop. Auto-pick fallback
preserved for widgets pinned before this code shipped.

### B-002 phase 3 — Per-widget command picker

**Commit:** `addd26e`

After picking a device, the user picks which of its commands the
widget fires (VOL_UP / MUTE / INPUT / custom — not just POWER).
Device list filter relaxed from "has POWER" to "has any command".

### B-003 phase 2 — Per-tile target picker

**Commit:** `c5b1c62`

In-app picker (QS tiles don't get a system-invoked config screen)
that binds the tile to either a macro or a single (device, command)
pair. SpectraQuickTile reads the binding on tap; falls back to the
phase 1 primary-device auto-pick when nothing is bound.

### B-005 phase 2 — Boot reschedule + countdown banner

**Commit:** `5fcbcc3`

AlarmManager is wiped on reboot — the BootRescheduleReceiver now
re-arms a pending sleep timer from persisted SharedPreferences.
Home shows a card-elevated banner with "⏱ '<label>' in N min" + a
Cancel button while a timer is active, ticking once a minute via
a fragment-scoped coroutine inside repeatOnLifecycle.

### B-005 phase 1 — Sleep timer

**Commit:** `aa308f9`

Pick a saved macro + duration (15/30/45/60/90/120 min preset),
schedule via `AlarmManager.setAndAllowWhileIdle` — inexact, runs
through Doze, no SCHEDULE_EXACT_ALARM permission. New
`scheduling/ScheduledFireReceiver` re-hydrates the macro's devices
into IrControl and runs the macro from the persisted form.
`SleepTimer` facade keeps the active-timer record in
SharedPreferences for the home banner (queued for phase 2). Single-
slot semantics: re-scheduling replaces via FLAG_UPDATE_CURRENT.

### Remaining queue

10 phases still TODO across B-002 (config activity, multi-cell
macro chips), B-003 (per-tile macro picker), B-004 (smart-home
control over RF), B-005 (boot-survives reschedule, repeating
schedule), B-006 (custom remote layouts), B-007 (in-app tag
write), B-009 (community fingerprint share), B-010 (auto-discover-
and-control), B-011 (voice / Assistant). See `H5W-BUILD.md`.

---

## Cycle 11 — ScanService foreground service

**Closed:** Longest-deferred T2 architectural item.

**Files:** `services/ScanService.kt` (new), `AndroidManifest.xml`,
`MainActivity.kt`, `MainViewModel.kt`, `HomeFragment.kt`,
`strings.xml`

**Commit:** `2585667`

Hosts the passive-scan coroutine inside a foreground service with
`foregroundServiceType=connectedDevice`. Survives a brief
background → foreground transition; complies with Android 14+ BLE-from-
background rules. Notification offers a cancel action.

---

## Cycle 12 — Scan flow cancellation hardening

**Closed:** Two T1 bugs in cycle 11's scan flow.

**Commits:**
- `d25929e` — orchestrator: clear `_discoveredDevice` at scan start;
  finally{} resets `_phase` off SCANNING_PASSIVE on cancellation.
- `8faac44` — viewmodel: timeout-fallback dispatches stopIntent;
  watcher routes to HOME when discoveredDevice is null (cancel)
  vs RESULTS when set (completion).

Without these, a notification-cancel left phase stuck at
SCANNING_PASSIVE forever and the watcher hung; a slow scan startup
that timed out left the foreground service running unobserved; a
cancelled scan promoted a stale match from a prior scan.

---

## Cycle 13 — Truly atomic JSON file replace

**Closed:** T1 in MacroRepository + BruteForceCheckpointRepository.

**Commit:** `e5c7b68`

Both claimed atomic-write semantics in their docstrings but used
`delete-then-rename`, which has a window where neither file is on
disk. Replaced with `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`
backed by `rename(2)`; falls back to delete+rename only on filesystems
that reject ATOMIC_MOVE (which the app's private files dir doesn't).

---

## Cycle 14 — Serialize IR transmits

**Closed:** T1 race on shared IR hardware.

**Commit:** `c5d25cf`

`ConsumerIrManager.transmit()` isn't documented as thread-safe;
concurrent calls from rapid taps or button-while-macro produced
garbled bursts on some OEMs. Added a per-IrControl Mutex held across
single transmits and across the full repeat sequence. Logged
previously-swallowed transmit exceptions in `sendRepeated`.

T3-03 logged for the cross-module IrBruteForce case (separate
ConsumerIrManager handle; currently UI-blocked from racing but
worth refactoring to a shared IrHardware singleton).

---

## Cycle 16 — AudioRecord init-failure leak

**Closed:** T1 mic leak in AcousticFingerprint.

**Commit:** `d45497d`

`AudioRecord(...)` doesn't throw on init failure — returns an instance
with `state == STATE_UNINITIALIZED`. The previous code skipped the
state check so `startRecording()` threw IllegalStateException, and
because `release()` lived inside an inner try/finally that wrapped
only the capture loop, the throw from `startRecording()` (outside
that inner try) bypassed release entirely. The mic stayed held until
the native finalizer ran on GC — typically minutes-to-never on a
backgrounded app, wedging the next scan and any other recording app.

Fixed with an explicit state check that surfaces a clean ERROR
return, plus restructure to outer try/finally so release() always
runs on every exit path including cancellation.

---

## Cycle 15 — Don't gate scan on POST_NOTIFICATIONS denial

**Closed:** T2 UX gate introduced by cycle 11.

**Commit:** `1c6dd3f`

HomeFragment's `scanPermissionLauncher` blocked the scan whenever any
permission was denied — including POST_NOTIFICATIONS, which is
purely cosmetic (the foreground service still runs without a visible
notification). Filtered the result map to "essential and denied" so
the optional notification doesn't gate scanning.

T3-02 logged for a one-shot snackbar hint when notifications are
denied so the user knows the scan is running invisibly.

---

## T3 Queue Status

6 items logged in `.h5w/H5W-QUEUE.md`. None block the loop.

| ID | Area | Severity |
|----|------|----------|
| T3-01 | Notification small-icon silhouette | Cosmetic |
| T3-02 | POST_NOTIFICATIONS denial UX | UX |
| T3-03 | Cross-module IR transmit mutex | Latent |
| T3-04 | DeviceRepository irProfile null-conflation | Latent |
| T3-05 | IrCameraCapture cross-thread visibility | Latent |
| T3-06 | ScanService non-START/STOP intent handling | Cosmetic |

---

## Unresolved T2

- **Compose migration** — only remaining T2 architectural item.
  Requires user direction on full-vs-hybrid scope. Deferred since
  cycle 9.

---

## Loop continuation

Per CLAUDE.md "always loop when cycle end" + §AUTO Rule 1. Queue
non-empty doesn't pause; cycle 16+ continues with another §SIM.6
sweep on cycle 12-15 changes plus cross-cycle micro-H5W when
resumed.

Real termination triggers: red build + 3 self-correction failures,
context wall, user interrupt. None hit yet.
