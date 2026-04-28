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

### T3-Blocked findings (require user decision)

| # | ID | Decision Needed | Recommendation | Why T3 |
|---|----|-----------------|----------------|--------|
| - | F-004 | Persist mid-flow brute-force state across process death? | **Defer permanently** — uncommon, complex (would require persisting a coroutine waypoint). Adds complexity for marginal gain. | Schema change to persist mid-flow state |
| - | F-038 | Configure CI/CD pipeline? | **Defer** — requires choice of provider (GitHub Actions, GitLab, etc.) and a working secret store for signing. Out of scope without user infrastructure decisions. | Infrastructure / external account |
| - | F-039 | Configure release signing? | **Defer** — requires user keystore + a secret-management plan. Cannot generate a real signing key autonomously without leaking it. | Cryptographic key material |

### T2 enhancements deferred from cycle 3 (no current victims, infra-level)

| # | ID | Sev | Source | Rationale |
|---|----|-----|--------|-----------|
| - | F-050 | low | §SIM.6 L3.Q29 | Repository JSON round-trip tests — needs Robolectric or a Context-mock framework. Repo conversions exercised at runtime; failure modes would surface immediately on first save. |
| - | F-051 | low | §SIM.6 L5.Q43 | Tests for SpectraOrchestrator.matchKnownDevice / inferIdentity — same Robolectric requirement. The pure subset is testable but requires a refactor that's not minimum-footprint. |
| - | F-052 | low | §SIM.6 L1.Q6 | fragment_remote.xml D-pad row exceeds 320dp width. No real device under 360dp ships modern Android. Cosmetic. |
| - | F-053 | low | §SIM.6 L2.Q15 | Long BLE/passive scans should ideally run in a foreground service for Android 14+ background reliability. Current scans ≤6s and bound to UI session; refactor would touch lifecycle architecture. |

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
