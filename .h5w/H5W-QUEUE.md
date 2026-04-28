# H5W T3 Queue

T3 (deferred) items surfaced during autonomous H5W cycles. These are
not blocking — they're logged for future attention with full context
and a recommendation. The autonomous loop continues without acting on
them per CLAUDE.md §AUTO-FULL.

---

## T3-01 — Notification small icon: not a single-color silhouette

**Surfaced in:** cycle 12 cross-cycle micro-H5W on cycle 11
**File:** `app/src/main/java/com/andene/spectra/services/ScanService.kt:176`
**Line:** `.setSmallIcon(R.drawable.ic_launcher_foreground)`

**Issue:** Android 12+ requires notification small icons to be a
single-color silhouette (white-only with transparency). The current
`ic_launcher_foreground` is a multi-colour vector — on API 31+ it
renders as a solid white square, losing the brand mark.

**Recommendation:** Add a dedicated `ic_notification_scan.xml`
vector drawable with all paths set to `?attr/colorOnPrimary` (or
white) and reference it in `setSmallIcon`. Cosmetic only; the
notification still appears and the cancel action still works.

---

## T3-02 — POST_NOTIFICATIONS denial UX hint

**Surfaced in:** cycle 12 cross-cycle micro-H5W on cycle 11
**File:** `app/src/main/java/com/andene/spectra/ui/screens/HomeFragment.kt:33`

**Issue:** If the user denies POST_NOTIFICATIONS on Android 13+, the
scan now still starts (cycle 15 fix) but they have no visible
indication that a foreground service is using the radios + battery.
The cancel-from-notification path is also unavailable to them — they
have to find their way to the Scanning screen and tap Cancel.

**Recommendation:** When POST_NOTIFICATIONS is denied at scan-start
time on Android 13+, surface a one-shot snackbar: "Scan running in
background — enable notifications in Settings to see status and
cancel from anywhere." Not blocking; informational.

---

## T3-03 — Cross-module IR transmit serialization

**Surfaced in:** cycle 14
**Files:** `IrControl.kt` (mutex added cycle 14), `IrBruteForce.kt:216`
(separate ConsumerIrManager instance, no mutex)

**Issue:** `IrBruteForce` holds its own `ConsumerIrManager` and
transmits independently. Cycle 14 added a mutex inside `IrControl`
but it doesn't cover brute-force transmits. In the current UI flow
the brute-force YES/NO dialog blocks all other interaction so a
concurrent `IrControl.sendCommand` is impossible. If we ever add a
side-by-side flow (e.g. brute-force in background, normal remote
visible), the two would race on the IR hardware.

**Recommendation:** Refactor to a shared `IrHardware` singleton on
`SpectraApp` that owns the single `ConsumerIrManager` and exposes a
mutex-guarded `transmit(carrier, timings)` method. Both `IrControl`
and `IrBruteForce` route through it. ~30 lines, no behaviour change
in the current UI.

---

## T3-04 — DeviceRepository.toModel always populates irProfile

**Surfaced in:** cycle 13 sweep
**File:** `app/src/main/java/com/andene/spectra/data/repository/DeviceRepository.kt:220`

**Issue:** `toModel()` always returns `irProfile = IrProfile(...)` —
never null. A device saved with `irProfile = null` (RF-only
fingerprint, no IR yet) round-trips to a non-null IrProfile with an
empty commands map. This conflates "no IR profile" with "IR profile
but zero commands", breaking any downstream code that uses
`irProfile == null` as the "no IR yet" signal.

**Recommendation:** In `toModel`, set `irProfile = null` when the
serialized form has empty commands AND default carrier (38000) AND
default protocol (RAW). Or, more robustly, add an explicit
`hasIrProfile: Boolean` field to the serialized form (default false,
written true on save when irProfile is non-null). Currently no
caller uses this distinction critically, so low priority.

---

## T3-05 — IrCameraCapture captureStartNanos cross-thread visibility

**Surfaced in:** cycle 14 sweep
**File:** `app/src/main/java/com/andene/spectra/modules/ir/IrCameraCapture.kt:160`
**Line:** `private var captureStartNanos = 0L`

**Issue:** `captureStartNanos` is written by the Main thread in
`startCapture()` and read by the cameraExecutor thread in
`analyzeFrame()`. Without `@Volatile` or atomic semantics, the
cameraExecutor may observe a stale value during the brief Main →
analyze handoff window. The result is wrong relative timestamps for
the first few frames, which can produce a bad decode and surface
ERROR state. User just retries.

**Recommendation:** Mark `captureStartNanos` as `@Volatile` (zero
runtime cost on this access pattern, fixes the JMM gap). Same for
`frames` mutability — already gated by `synchronized(frames)` so
it's actually fine, but the `var` declarations of executor /
imageAnalysis could be reviewed too.

---

## T3-06 — ScanService accepts non-START/STOP intents as start

**Surfaced in:** cycle 12 cross-cycle micro-H5W on cycle 11
**File:** `app/src/main/java/com/andene/spectra/services/ScanService.kt:78`

**Issue:** `onStartCommand`'s `when` block defaults to
`startForegroundIfNeeded()` for any intent action that isn't
`ACTION_STOP`. A null-action Intent or a typo would launch a scan.
Service is unexported so this isn't a security issue, but it's
slightly noisy.

**Recommendation:** Tighten the `when` to exhaustively handle
ACTION_START and ACTION_STOP, log + ignore everything else. One-line
change.

---

*Loop continues per CLAUDE.md §AUTO Rule 1 + §SIM.6 — queue
non-empty does not pause execution.*
