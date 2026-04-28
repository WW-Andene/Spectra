# H5W §0 — Unified Context for Spectra

## Identity

| Field | Value |
|---|---|
| App Name | Spectra |
| Version | 0.1.0 (`app/build.gradle.kts:16`) |
| Domain | Universal IR remote + smart-device discovery (utility / hobby) |
| Audience | Owners of IR-controlled appliances (TVs, AVRs, ACs); Android users |
| Stakes | **LOW** — consumer hobby utility, no money/health/critical data |

## Tech Stack

| Field | Value |
|---|---|
| Framework | Android (Kotlin, XML Views, Material Design 3) |
| Language | Kotlin 1.9.22 |
| Styling | Material 3, XML themes (`themes.xml`, `colors.xml`) |
| State | ViewModel + StateFlow; navigation via NavController |
| Persistence | Filesystem JSON: `DeviceRepository`, `MacroRepository` (atomic writes) |
| Build | Gradle 8.7 (wrapper); AGP 8.2.2; Kotlin 1.9.22 |
| Linting | None configured (no ktlint / detekt) |
| Testing | JUnit 4.13.2 host-JVM; 4 test files, 24 unit tests |
| External APIs | None (offline) |
| AI/LLM | None |
| Workers | None (sensor capture on coroutine dispatchers) |
| Visualization | None |

## Mobile

| Field | Value |
|---|---|
| Platform | Android |
| Min SDK | 26 (Android 8.0) — required for `ConsumerIrManager` + CameraX baseline |
| Target SDK | 34 (Android 14) |
| Primary Locale | en-US (only English in `strings.xml`) |
| RTL Support | No |
| Deployment | None configured (no Play Store config, no signing config) |

## Entry Points (NavController destinations)

- `dest_home` (HomeFragment) — start destination, scan + macros + saved devices
- `dest_scanning` (ScanningFragment) — passive scan progress + log
- `dest_results` (ResultsFragment) — scan results + next-step picker (also DEVICE_EDIT alias)
- `dest_learn` (LearnFragment) — IR learning: DB picker, camera capture, brute force
- `dest_remote` (RemoteFragment) — universal remote button grid + share
- `dest_macro_edit` (MacroEditFragment) — macro create/edit

## State Stores

- `MainViewModel`: `screen, savedDevices, activeDevice, scanLog, bruteForcePrompt, isScanning, cameraLearnActive, commandNameInput, macros, runningMacro, editingMacro` — all `StateFlow`
- `SpectraOrchestrator`: `phase: StateFlow<Phase>`, `discoveredDevice: StateFlow<DeviceProfile?>`, `log: StateFlow<List<String>>`; private synchronized `knownSignatures: MutableList`
- `IrControl`: `devices: StateFlow<Map<String,DeviceProfile>>`, `lastTransmitResult: StateFlow<TransmitResult?>`
- `IrCameraCapture`: `captureState`, `capturedCommand`; private `frames: MutableList<Pair<Long, IntArray>>`
- `IrBruteForce`: `state: StateFlow<BruteForceState>` plus `lastFoundPattern/Manufacturer/Carrier`
- `AcousticFingerprint`, `RfFingerprint`, `EmFingerprint`: each has its own `state` and `signature` StateFlows

## Constraints

| Field | Value |
|---|---|
| Primary Device | Android phone with IR blaster (Xiaomi flagships are the realistic intersection) |
| Target Viewports | Portrait phones, 360–440 dp |
| Performance Budget | Cold start < 1s (target, not enforced); scan ≈ 6s max (gated by BLE) |
| Known Limitations | No backend; IR features dead on non-IR phones; acoustic/EM identification advisory only |
| Architecture | Feature-based modules under `com.andene.spectra.{core,modules,data,ui}` |

## Design Identity

| Field | Value |
|---|---|
| Color System | Custom dark "signal-inspired" palette (`bg_primary #0D0D12`, accents purple/cyan/violet) |
| Typography | System default (no custom font families declared) |
| Motion | Minimal |
| Visual Source | Custom signal/spectrum motif; concentric-wave launcher icon |
| Aesthetic Role | UTILITY |
| Personality | Technical, atmospheric |
| Protected Elements | Adaptive launcher icon (concentric signal waves over `#0D0D12`); per-module accent colors (`module_ir`, `module_acoustic`, `module_rf`, `module_em`) |

### Five-Axis Quick Profile

- **A1** Commercial: **Non-revenue** — open hobby app
- **A2** Intensity: **Occasional / transactional** — open, point at device, control, close
- **A3** Audience: **Mixed** — power users (theorycrafters running brute force) + general
  remote users (DB picker)
- **A4** Subject ID: **Weak / none** — no IP, no licensed brand
- **A5** Aesthetic: **Utility** — invisible design, max clarity, min decoration

## Conventions (extracted)

| Field | Value |
|---|---|
| Naming Style | PascalCase classes, camelCase fns/vars, `ALL_CAPS` const, `_underscored` for private MutableStateFlow |
| Import Order | `android.* → androidx.* → com.andene.* → kotlinx.*` (mostly) |
| Error Pattern | `try/catch (Exception)` with `Log.{e,w}`, fall back to ERROR state in module enum |
| State Pattern | private `MutableStateFlow` + public read-only `StateFlow` exposure |
| File Org | Feature-based — `core/`, `modules/{acoustic,bruteforce,control,em,ir,rf}`, `ui/{screens,components}`, `data/{models,repository,codedb}` |

## Codebase Metrics

| Field | Value |
|---|---|
| Total Files | 21 Kotlin + 12 XML layouts + nav graph + 8 values |
| Total LOC | 4,399 Kotlin + 1,485 XML = **5,884 source LOC** |
| Source Dirs | `app/src/main/{java,res,assets}` |
| Test Files | 4 (`IrEncodersTest`, `OuiLookupTest`, `IrCodeDatabaseSchemaTest`, `RollingShutterTimelineTest`) |
| Test Coverage | Pure-function only — no Robolectric or instrumented tests |
| Config Files | `build.gradle.kts` (root + app), `settings.gradle.kts`, `gradle.properties`, `AndroidManifest.xml`, `nav_graph.xml` |

## Current Structure

| Field | Value |
|---|---|
| Org Style | Feature-based |
| Top-Level Tree | `app/src/main/{java,res,assets}` |
| Route Structure | `nav_graph.xml` with 6 destinations |
| Shared Code | None — each module is self-contained |
| State Locations | `MainViewModel` for UI state; orchestrator + per-module StateFlows for domain |
| Type Definitions | `data/models/Models.kt` |

## Domain Rules (from code)

- **`SIMILARITY_THRESHOLD = 0.75f`** [CODE: `SpectraOrchestrator.kt:58`] — matching cutoff for re-identification
- **`PROTOCOL_CARRIER`** map [CODE: `IrBruteForce.kt:46`] — per-protocol IR carrier (NEC=38k, SIRC=40k, RC5/6=36k, SAMSUNG/LG/PANASONIC/SHARP=38k)
- **`OUI_MAP`** [CODE: `RfFingerprint.kt:44`] — 22 entries mapping MAC prefix → manufacturer
- **`MDNS_HINTS`** [CODE: `RfFingerprint.kt:71`] — 7 service-type → category hints
- **NEC encoder**: 9000µs leader mark, 4500µs leader space, 562µs bit mark, 562µs/1687µs bit space, stop bit [CODE: `IrBruteForce.kt:40`]
- **Samsung encoder**: 4500/4500 leader, 560µs mark, 560/1690µs space [CODE: `IrBruteForce.kt:71`]
- **SIRC 12-bit encoder**: 2400/600 leader, variable mark + 600µs space [CODE: `IrBruteForce.kt:60`]
- **`ON_FRACTION = 0.5f`** [CODE: `IrCameraCapture.kt:38`] — rolling-shutter threshold position between min/max

## Critical Workflows (5)

1. **First-time scan and learn**: Home → Scan → Results (new device) → Learn → DB picker / brute force / camera → Save → Remote
2. **Recognize known device**: Home → Scan → orchestrator matches RF → Results (known) → Remote (auto)
3. **Brute-force flow**: Results → Brute Force button → onAttempt prompts (Yes/No) → on hit, persist power command → Remote
4. **Macro execution**: Home → tap macro chip → orchestrator runs steps with delays
5. **Profile share/import**: Remote → Share (intent) ; Home → Import from clipboard

## Known Issues (carried into this audit)

- DataStore dependency declared (`app/build.gradle.kts:62`) but never used.
- `MainViewModel` exposes `orchestrator` as public property — fragments reach into module internals directly.
- `IrCameraCapture.synchronized(frames)` write but read via `synchronized` toList — single-writer/single-reader OK in practice but contract is implicit.
- `cameraExecutor.shutdown()` in `IrCameraCapture.release()` but never re-initialized; if `release` is called, a subsequent `buildAnalyzer()` will use a shutdown executor (latent bug — no caller currently triggers it).
- `MainViewModel.permissionLauncher` and runtime checks gate scan, but `orchestrator.scanPassive()` declares `@RequiresPermission` and throws `SecurityException` if any is missing — handled in viewmodel, but the per-module camera permission check in `LearnFragment.setupCamera` uses deprecated `requestPermissions(arrayOf(...), 100)`.

## Audit Scope

- **Files to Audit**: All `app/src/main/`. Skip `gradle/wrapper/`, `gradlew*`, `h5w-unified/`.
- **Out of Scope**: Generated R, Gradle wrapper jar, the h5w bundle itself.

## Growth

| Field | Value |
|---|---|
| Users | None deployed yet |
| Data Volume | Per-user: ≤20 device profiles + ≤20 macros |
| Feature Plans | Carry-over bench items: macro Quick Settings tile, larger IR DB, instrumented tests |
| Scale Target | Hobby distribution; not Play Store yet |
