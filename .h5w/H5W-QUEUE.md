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

### T3-Blocked findings (require user decision)

| # | ID | Decision Needed | Recommendation | Why T3 |
|---|----|-----------------|----------------|--------|
| - | F-004 | Persist mid-flow brute-force state across process death? | **Defer** — uncommon, complex (would require persisting a coroutine waypoint). Adds complexity for marginal gain. | Schema change to persist mid-flow state |
