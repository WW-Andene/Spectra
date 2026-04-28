# H5W Finding Queue — Spectra

Sorted by: severity → cascade → tier → persona overlap → compounds.
Full §FMT detail in `H5W-FINDINGS.md`. This is the priority view.

| # | ID | Sev | Tier | Mod | Conf | Source | Summary |
|---|------|-----|------|-----|------|--------|---------|
| 1 | F-001 | high | T1 | H5W | confirmed | P1, P3 walkthrough | Scan startable before permissions granted; SecurityException-only feedback |
| 2 | F-002 | high | T1 | H5W | confirmed | P3 walkthrough | Cancel during scan only navigates — scan continues, screen flips to Results on completion |
| 3 | F-006 | high | T1 | H5W | confirmed | P4 walkthrough | Silent IR failure on no-blaster phones; UI gives zero feedback |
| 4 | F-024 | high | T1 | CODE | confirmed | code review | Rolling-shutter `frames` list grows unbounded; 30s hold ≈ 78 MB → OOM |
| 5 | F-016 | medium | T1 | H5W | confirmed | P1, P2 walkthrough | Remote button taps have zero visual feedback regardless of success |
| 6 | F-010 | medium | T1 | H5W | confirmed | P1 walkthrough | Hardware-status dots (`IR ●`, `EM ●`) on Home unlabeled; `statusMic`/`statusRf` never updated |
| 7 | F-017 | medium | T1 | H5W | confirmed | P1 walkthrough | "OPEN REMOTE" enabled even with zero commands learned |
| 8 | F-027 | medium | T2 | CODE | high | code review | `startBruteForce` re-entry while sweep in flight — coroutine race |
| 9 | F-003 | medium | T1 | H5W | confirmed | P2 walkthrough | Macro re-tap silently cancels prior run — no acknowledgement |
| 10 | F-005 | medium | T1 | CODE | confirmed | P3 walkthrough | `LearnFragment.setupCamera` uses deprecated `requestPermissions(arr,100)` and ignores result |
| 11 | F-018 | medium | T1 | H5W | confirmed | P1 walkthrough | DB picker shows multiple TV layouts per brand with no hint how to choose |
| 12 | F-021 | medium | T1 | CODE | confirmed | code review | `IrCameraCapture.analyzeFrame` reads `_captureState` outside the `frames` lock — race on stop |
| 13 | F-013 | medium | T2 | CODE | confirmed | code review | `cameraExecutor.shutdown()` in `release()`; subsequent `buildAnalyzer()` would use a dead executor |
| 14 | F-022 | medium | T2 | CODE | confirmed | code review | `MainViewModel.orchestrator` exposed publicly; fragments reach into module internals |
| 15 | F-014 | medium | T1 | H5W | confirmed | code review | Permission denial Toast shows raw constant suffixes (`RECORD_AUDIO`) — not user-friendly |
| 16 | F-007 | low | T1 | DESG | confirmed | code review | Macro chip `setBackgroundResource(selector)` then `setBackgroundColor(...)` — selector overwritten, no press feedback |
| 17 | F-008 | low | T1 | CODE | confirmed | code review | `resources.getColor(..., null)` deprecated form used in HomeFragment, MacroEditFragment, LearnFragment |
| 18 | F-009 | low | T1 | H5W | confirmed | P1 walkthrough | "+ NEW" macro button label ambiguous; "+ NEW MACRO" clearer |
| 19 | F-020 | low | T1 | CODE | confirmed | code review | `RemoteFragment.setOnTouchListener` returns true without `performClick()` — accessibility |
| 20 | F-023 | low | T1 | CODE | confirmed | code review | `androidx.datastore` dependency declared but never used |
| 21 | F-011 | low | T2 | CODE | high | code review | ResultsFragment reads `phase.value` once inside collect; no reactive update on phase change |
| 22 | F-019 | low | T2 | CODE | confirmed | code review | `MacroEditFragment.workingSteps` lost on process death; no save-state |
| 23 | F-025 | low | T2 | APP | confirmed | code review | WiFi `scanResults` returns empty silently when location is system-disabled |
| 24 | F-026 | low | T0 | CODE | high | code review | Brand-filter substring matching could over-match in edge cases (no current victims) |
| 25 | F-015 | low | T1 | CODE | confirmed | code review | `_lastTransmitResult` written but no consumer; dead state |

### Cycle 3 (2026-04-28, anti-exhaustion 55Q sweep)

After cycle 2 declared queue empty, applied §SIM.6 to detect Premature
Completion. 11 new findings — every layer of the 55-question sweep
contributed. Premature completion confirmed: cycles 1+2 missed an
entire delivery-infrastructure dimension.

| # | ID | Sev | Tier | Mod | Source | Summary |
|---|------|-----|------|-----|--------|---------|
| 31 | F-030 | medium | T1 | CODE | §SIM.6 L1.Q2 | Brute-force silent transmit-failure skip — no scanLog entry on hardware reject |
| 32 | F-032 | medium | T1 | CODE | §SIM.6 L1.Q2 + L2.Q14 | DeviceRepository / MacroRepository swallow save errors; no UI feedback |
| 33 | F-035 | medium | T1 | APP | §SIM.6 L4.Q40 | No README.md — new dev can't onboard |
| 34 | F-031 | low | T2 | CODE | §SIM.6 L1.Q3 | IrCodeDatabase first-load on main thread (Json.decodeFromString blocking) |
| 35 | F-033 | medium | T2 | APP | §SIM.6 L3.Q26 | No R8/ProGuard config; debug Log statements ship in release |
| 36 | F-034 | low | T1 | H5W | §SIM.6 L4.Q39 | No undo on device/macro deletion — destructive without recovery |
| 37 | F-036 | low | T1 | H5W | §SIM.6 L5.Q49 | scanLog error messages don't tell user how to recover |
| 38 | F-040 | low | T0 | CODE | §SIM.6 L3.Q28 | Duplicate `kotlinx.coroutines.launch` import in RemoteFragment alongside wildcard |
| 39 | F-037 | low | T2 | APP | §SIM.6 L6.Q51,Q55 | Build/run/test instructions absent — fixed by F-035 README addition |

### T3-Blocked findings — RESOLVED

| # | ID | Closed | Resolution |
|---|----|--------|-----------|
| - | F-004 | ✓ | BruteForceCheckpoint model + repository (atomic, 24h stale cutoff) + IrBruteForce.startSweep(startAttempt) + resume banner on Home |
| - | F-038 | ✓ | (Closed in cycle 4) GitHub Actions workflow |
| - | F-039 | ✓ | keystore.properties at repo root (gitignored) + signingConfig in build.gradle.kts that degrades gracefully when absent + README + .example schema |

### Cycle 9 — T2 architectural batch closed

| Item | Status | Resolution |
|---|---|---|
| repeatOnLifecycle migration | ✓ | 13 collector launches wrapped across 5 fragments |
| Predictive back gesture | ✓ | `android:enableOnBackInvokedCallback="true"` + existing OnBackPressedDispatcher callbacks remain compatible |
| Edge-to-edge | ✓ | `enableEdgeToEdge()` + system-bars padding listener on nav_host |
| Deep linking | ✓ | `spectra://device/<id>` opens Remote; 2s cold-start wait on savedDevices |
| ViewModelFactory hardening | ✓ | Null-safe `Application as? SpectraApp` with helpful error |

### T2 still deferred (not minimum-footprint)

| Item | Why deferred |
|---|---|
| Compose migration | Full UI refactor across every fragment — needs a dedicated cycle and a decision on whether to keep XML hybrid or commit fully |
| Foreground service for long-running scans | Lifecycle architecture change (manifest service declaration + service binding + state hand-off). Worth doing when the scan duration grows, not as polish |

### Cycle 4 (2026-04-28, post-Premature-Completion correction)

User caught the cycle-3 termination as Premature Completion. Re-opened
the queue. F-038 / F-050 / F-051 / F-052 closed (none required
infrastructure decisions after all). Anti-exhaustion sweep produced
11 more findings — including F-062/063/064 cancellation leaks F-002
had introduced.

| # | ID | Status | Commit |
|---|------|--------|--------|
| 31 | F-038 | FIXED T2 | GitHub Actions workflow |
| 32 | F-050 | FIXED T2 | Robolectric + repo round-trip tests |
| 33 | F-051 | FIXED T2 | Matching.kt extraction + 16 unit tests |
| 34 | F-052 | FIXED T1 | D-pad row 320dp viewport fix |
| 35 | F-055 | FIXED T1 | Splash screen on cold start |
| 36 | F-057 | FIXED T1 | Lint runs in CI |
| 37 | F-058 | FIXED T1 | Test/Rename literal cleanup |
| 38 | F-059 | FIXED T1 | 36/32dp → 48dp touch targets |
| 39 | F-060 | FIXED T1 | Macro editor unsaved-changes confirm |
| 40 | F-061 | FIXED T1 | Camera bind failure toast |
| 41 | **F-062** | **FIXED T1** | **BLE scanner leak on cancellation** |
| 42 | **F-063** | **FIXED T1** | **AudioRecord leak on cancellation (×2)** |
| 43 | **F-064** | **FIXED T1** | **Magnetometer listener leak on cancellation** |
| 44 | F-065 | FIXED T1 | Removed bogus mDNS↔BSSID join |
| 45 | F-066 | FIXED T1 | Macro stale-device validation |

### Cycle 3 fixes applied (19)

F-030 BF-skip-surfaced · F-031 codeDB preload · F-032 save-feedback toast ·
F-033 R8/ProGuard · F-034 undo Snackbar · F-035 README · F-036 recovery copy ·
F-037 build instructions (closed by F-035) · F-040 dup import · F-041 module ERROR
surfaced · F-042 loadAll skip count · F-043 clipboard hint · F-044 ~30 Kotlin
literals → strings · F-045 forceDarkAllowed · F-046 maxLength · F-047 magic
numbers · F-048 dead public functions · F-049 dead sweepJob field

### Cycle 2 (2026-04-28, post-CLAUDE.md autonomy)

All previously-deferred enhancements resolved and pushed:

| # | ID | Status | Commit |
|---|------|--------|--------|
| 26 | F-019 | FIXED T2 | macro-editor save/restore via Bundle |
| 27 | F-022 | FIXED T2 | orchestrator privatized, three narrow facades added |
| 28 | F-026 | FIXED T0 | word-token brandTokens/matchesBrand helpers |
| 29 | F-028 | FIXED T1 | LearnFragment brand sorter migrated |
| 30 | F-029 | FIXED T1 | IrCodeDatabase.lookup migrated |

All clean, all tested where unit-testable. No new T3 surfaced.
