# H5W Autonomous Run Report

Branch: `claude/understand-project-ATKHd`
Mode: `§AUTO-UNCHAINED + §BRAINSTORM`
Gate: user-passed ("i accept full responsibility" + "this is my sandbox")

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
