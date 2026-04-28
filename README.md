# Spectra

> Universal IR remote + smart-device discovery for Android.

Spectra fingerprints nearby electronic devices using the phone's
sensors, then learns and replays IR commands to control them. WiFi /
BLE / mDNS discovery works on any Android phone (API 26+); the
universal-remote half requires an IR blaster.

---

## What it does

| Half | Works on |
|---|---|
| **Smart-device discovery** — WiFi APs, BLE peripherals, Chromecast/AirPlay/Sonos via mDNS, OUI → manufacturer lookup | Any Android phone |
| **Universal IR remote** — bundled code DB, brand-narrowed brute-force, camera capture (rolling-shutter), per-device button grid, share/import device profiles | Phones with `ConsumerIrManager` (Xiaomi flagships are the realistic target) |
| **Macros** — cross-device command sequences with per-step delays | Same as universal remote |
| **RF re-identification** — recognise a previously-saved device on subsequent scans via WiFi BSSID / BLE address match | Any Android phone |

---

## Building from source

### Requirements

- **JDK 17** on your `PATH`
- **Android SDK** with platform 34 + build-tools installed (or let
  Android Studio install them on first sync)
- Internet for the first Gradle sync (downloads AGP 8.2.2 + dependencies)

### Build

```bash
./gradlew :app:assembleDebug
```

The unsigned debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Install on a device

```bash
./gradlew :app:installDebug
```

Or sideload the APK manually via `adb install <path>`.

### Run unit tests

```bash
./gradlew :app:testDebugUnitTest
```

Currently **30 host-JVM tests** covering IR encoders (NEC / Samsung /
SIRC bit layouts), OUI lookup, JSON-database schema, rolling-shutter
timeline reconstruction, and brand-token matching. All run on the host
JVM — no emulator required.

### Release build

`./gradlew :app:assembleRelease` produces a minified APK with R8 +
resource shrinking. Release signing is **not configured** — fork and
add your own keystore via Gradle's `signingConfigs` block before
distributing.

---

## Project layout

```
app/src/main/
├── java/com/andene/spectra/
│   ├── core/
│   │   └── SpectraOrchestrator.kt    Coordinates the six sensor modules
│   ├── modules/
│   │   ├── acoustic/                  Mic-based fingerprinting (advisory)
│   │   ├── em/                        Magnetometer + EMI-mic fingerprinting (advisory)
│   │   ├── rf/                        WiFi + BLE + mDNS discovery (primary)
│   │   ├── ir/                        Camera-based IR decode (rolling-shutter)
│   │   ├── bruteforce/                IR protocol sweep + encoders
│   │   └── control/                   IR transmission via ConsumerIrManager
│   ├── data/
│   │   ├── codedb/                    Bundled IR remote database
│   │   ├── models/                    DeviceProfile, Macro, etc.
│   │   └── repository/                JSON-on-disk persistence
│   └── ui/
│       ├── MainActivity.kt            Single-activity host
│       ├── MainViewModel.kt           UI state + facades
│       └── screens/                   Six fragments — Home, Scanning,
│                                      Results, Learn, Remote, MacroEdit
├── res/                               Layouts, themes, colors, strings
└── assets/
    └── ir_codes.json                  11 brands × 16 remotes starter DB

app/src/test/                          Host-JVM unit tests
.h5w/                                  H5W audit working docs
h5w-unified/                           Skill bundle (not part of the app)
```

The architecture is **MVVM with Navigation Component**. State flows
upward through `MainViewModel`'s `StateFlow`s; module internals are
not exposed — only narrow facades like `orchestratorPhase`,
`captureState`, `lastTransmitResult`. RF is the only cross-session
identifier (acoustic/EM are too unstable).

---

## Working with the codebase

The project carries a `.h5w/` directory with audit working documents
(`H5W-CONTEXT.md`, `H5W-LOG.md`, `H5W-QUEUE.md`, `H5W-FINDINGS.md`,
`H5W-REPORT.md`) produced by autonomous H5W cycles. New work picks up
from `H5W-QUEUE.md` and appends to `H5W-LOG.md`.

`CLAUDE.md` at the repo root captures the autonomy directive — H5W
runs T0/T1/T2 fixes without confirmation, queues T3 items, and loops
until termination triggers fire.

---

## Hardware caveats

- **No IR blaster on most modern phones.** Pixel, recent Galaxy S, and
  every iPhone have no IR transmitter. The Remote screen shows a clear
  banner when no blaster is detected; transmissions return false and
  flash the button red.
- **Rolling-shutter IR decode** lifts the effective sample rate from
  ~30 Hz (frame rate) to ~22 kHz (rows × frame rate), enough to see
  NEC bit timings. Quality still depends on lighting and aim.
- **WiFi scan throttling** on Android 9+ caps fresh scans at 4 per 2
  minutes; the app falls back to cached results when throttled.
- **Location services system toggle** affects WiFi scans separately
  from the runtime permission — both must be on for `scanResults` to
  populate.

---

## License

Not yet declared. Treat as all-rights-reserved until a license is
added.
