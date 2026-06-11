<p align="center">
  <img src="docs/logo.svg" alt="GlucoseSync Logo" width="128" height="128">
</p>

<h1 align="center">GlucoseSync</h1>

<p align="center">
  <strong>Wear OS + Android companion app for continuous glucose monitoring</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/platform-Android%20%7C%20Wear%20OS-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/kotlin-2.1-purple.svg" alt="Kotlin">
</p>

---

## Features

- **Multi-provider support** — Medtrum and LibreLinkUp, with Nightscout, Dexcom, and xDrip+ planned
- **Real-time glucose** — Current value, trend arrows, and configurable delta (1/5/10/15 min window)
- **24-hour history chart** — Color-coded steepness segments, round time labels, data point dots
- **Wear OS app** — Glucose, sparkline, stats, pump info, and sensor battery on your wrist
- **Watch face complications** — Glucose, Glucose+Delta, IOB, Delta, and Sensor Battery
- **Pull-to-refresh** — Swipe down on the watch to request fresh data from the phone
- **Pump data** — IOB, basal rate, last bolus, reservoir for Medtrum pump users
- **Alert system** — Configurable high/low thresholds, sound, vibration duration, DND override
- **Alarm overview** — Recent sensor and pump alarms from Medtrum API
- **Auto-refresh** — 60s foreground, 5 min background polling
- **Encrypted storage** — Credentials and settings via AES256 EncryptedSharedPreferences
- **Carer support** — Monitor multiple patients with patient picker
- **Theme selector** — System / Light / Dark
- **Boot persistence** — Restarts polling service after device reboot

## Screenshots

*Phone dashboard with glucose chart, stats, pump info, and alarm history*

*Wear OS dashboard with glucose hero, sparkline, stats, and pump card*

## Supported CGM Providers

| Provider | Status | Notes |
|----------|--------|-------|
| **Medtrum** | Supported | CGM + pump data, carer monitoring |
| **LibreLinkUp** | Supported | Multi-patient, region-aware |
| **Nightscout** | Planned | |
| **Dexcom** | Planned | |
| **xDrip+** | Planned | |

## Architecture

```
├── app/                    # Phone app (Android)
│   ├── service/            # Foreground polling + alert manager
│   ├── ui/                 # Compose Material 3 screens
│   ├── viewmodel/          # State management
│   └── receiver/           # Boot receiver
├── wear/                   # Wear OS app
│   ├── complication/       # Watch face complications
│   ├── receiver/           # DataLayer listener
│   ├── repository/         # Persistent state
│   └── ui/                 # Wear Compose Material screens
├── shared/                 # Shared domain layer
│   ├── domain/             # Models, trend arrows, providers
│   ├── provider/           # Provider implementations
│   ├── api/                # Retrofit interfaces + models
│   └── data/               # Credential + settings stores
├── docs/                   # Documentation assets
└── scripts/                # Release automation
```

## Tech Stack

- **Kotlin** 2.1, **Jetpack Compose** (Material 3), **Wear Compose** 1.4
- **Retrofit** + **OkHttp** with cookie-based auth (Medtrum)
- **EncryptedSharedPreferences** for credential storage
- **Wear OS Data Layer API** (DataClient + MessageClient)
- **Foreground Service** with `SPECIAL_USE` type
- **Coroutines** + **StateFlow** for reactive state
- **Kotlin Serialization** for JSON parsing

## Building

### Prerequisites

- Android Studio Ladybug or later
- JDK 17+
- Android SDK with API 36

### Debug Build

```bash
./gradlew assembleDebug
```

APKs are output to `app/build/outputs/apk/debug/` and `wear/build/outputs/apk/debug/`.

### Release Build

Release builds require signing credentials. Create a `.env` file:

```bash
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=your_alias
KEY_PASSWORD=your_key_password
```

Then:

```bash
source .env
set -a
./gradlew assembleRelease --no-daemon
```

## CI/CD

- **CI** (`ci.yml`) — Builds all modules and runs tests on every push/PR to `main`
- **Release** (`release.yml`) — Builds signed AABs, generates release notes, creates GitHub Release

### Creating a Release

```bash
./scripts/release.sh v1.4.0 "Add new provider support"
```

This bumps version codes, commits, tags, and pushes. The release workflow handles building and publishing.

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Glucose unit | mmol/L | Switch between mmol/L and mg/dL |
| Delta window | 5 min | Calculation window for glucose change |
| High alert | 10.0 mmol/L | Upper threshold |
| Low alert | 3.9 mmol/L | Lower threshold |
| Alert sound | On | Notification sound |
| Vibration | On, 3s | Haptic feedback with configurable duration |
| Override DND | Off | Bypass Do Not Disturb for alerts |
| Alert repeat | 5 min | Minimum interval between same alerts |
| Theme | System | System / Light / Dark |

## License

[MIT](LICENSE) &copy; NimbleFlux
