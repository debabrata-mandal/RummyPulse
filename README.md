# RummyPulse

Android app for running 10-round Rummy sessions: live scoring, multi-user sync, GST/contribution tracking, admin review, and monthly reports. Built with Java, Android Views, MVVM, and Firebase.

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Firestore-039BE5?style=flat-square&logo=Firebase&logoColor=white)

## Features

### Dashboard
- Create games with configurable point value and contribution (GST) percent
- Optional AI-suggested game titles via Groq at build time
- Join active games or reopen completed ones
- Real-time game list synced from Firestore

### Game view (`JoinGameActivity`)
- 10-round score tracking with standings, net amounts, and contribution totals
- View mode for spectators; edit mode with PIN for score entry and corrections
- QR code and game PIN for sharing access
- Real-time Firestore listener for multi-device updates
- Text-to-speech announcements for scores and game completion (configurable)
- **Show live amounts** (game default): when off, settlement amounts appear after round 6; when on, from round 1

### Review (admin)
- Approve completed games into the approved-games archive
- Bulk approve; edit economics (point value / contribution %) before approval
- GST summary per game

### Reports
- Expandable monthly summaries built from approved games
- Admins can rebuild a selected month on demand

### Game defaults
- Shared Firestore config: default point value, contribution %, mid-game player score increment, show-live-amounts toggle
- Contribution % and show-live-amounts are admin-only fields

### Users (admin)
- Manage app user roles (`admin_user` vs standard users)

### Updates & version gate
- In-app update checks against [GitHub Releases](https://github.com/debabrata-mandal/RummyPulse/releases)
- Firebase Remote Config minimum `versionCode` blocks outdated builds on release installs
- Debug builds skip the version gate for local development

## Requirements

| | |
|---|---|
| **Android** | 7.0+ (API 24), target SDK 34 |
| **Storage** | ~50 MB |
| **Network** | Required for Firebase sync |

## Install (end users)

RummyPulse is distributed as an APK via GitHub Releases (not on the Play Store).

1. Enable install from unknown sources on your device.
2. Download the latest APK from [Releases](https://github.com/debabrata-mandal/RummyPulse/releases).
3. Open the APK and follow the install prompt.

## Development setup

### Prerequisites

- Android Studio (recent stable; Ladybug or newer recommended)
- JDK 17
- Android SDK with API 34 (compile) / 24+ (min)
- Git and `adb` on PATH for device deploy

### Clone and open

```bash
git clone https://github.com/debabrata-mandal/RummyPulse.git
cd RummyPulse
```

Open the project in Android Studio and sync Gradle.

### Build & deploy

```bash
# Debug APK
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug

# Install on emulator/USB device and launch login screen
./gradlew deployDebug            # Windows: gradlew.bat deployDebug

# Release APK (local; uses release.keystore if present, else debug keystore)
./gradlew assembleRelease
```

After editing under `app/`, run `deployDebug` to install and launch on a connected device or emulator.

### Firebase

1. Create a project in the [Firebase Console](https://console.firebase.google.com).
2. Add an Android app with package `com.example.rummypulse`.
3. Download `google-services.json` into the `app/` directory (gitignored).
4. Enable **Firestore** and **Email/Password Authentication**.
5. Deploy [firestore.rules](firestore.rules) to your project.

**Firestore collections (v2):**

| Collection | Purpose |
|---|---|
| `games_v2` | Game metadata (status, creator, PIN hash) |
| `gameData_v2` | Players, scores, economics |
| `approvedGames_v2` | Archived games after admin approval |
| `approvedGamesReport_v2` | Pre-aggregated monthly report documents |
| `gameDefaults_v2` | Shared defaults (`config` document) |
| `appUser_v2` | User profiles and roles |

**Remote Config:** Add a **Number** parameter `min_supported_version_code` (not Text). Release builds below this `versionCode` are blocked until the user updates. Must match the same Firebase project as `google-services.json`.

### Optional: Groq game name suggestions

When a Groq API key is present at **build time**, the create-game dialog can suggest a short title. Without a key, the app builds and runs normally; suggestions are skipped.

Configure via the first match found:

1. Gradle property (`gradle.properties`, `~/.gradle/gradle.properties`, or `-P`)
2. Environment variable (CI / shell)
3. Root `local.properties` (gitignored)

```properties
GROQ_API_KEY=gsk_your_key_here
GROQ_MODEL_ID=llama-3.1-8b-instant   # optional
```

Get a free key from [console.groq.com](https://console.groq.com/). Keys embedded in the APK are extractable; a backend proxy is safer for wide distribution.

## CI/CD

Pushes to `main` trigger [.github/workflows/android-build.yml](.github/workflows/android-build.yml):

1. Bump `versionName` from the latest `v*.*.*` tag using conventional commit prefixes
2. Set `versionCode` to the GitHub Actions run number
3. Build a signed release APK
4. Publish a GitHub Release with the APK attached

### Commit → version bump

| Prefix | Bump |
|---|---|
| `feat:` / `feature:` | Minor (`1.1.0`) |
| `fix:` / `bugfix:` / `hotfix:` | Patch |
| `chore:` / `docs:` / `style:` / `refactor:` / `perf:` / `test:` | Patch |
| `BREAKING CHANGE:` / `!:` | Major |

### GitHub Actions secrets

| Secret | Purpose |
|---|---|
| `GOOGLE_SERVICES_JSON` | Full contents of `app/google-services.json` |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `GROQ_API_KEY` | Optional; enables AI name suggestions in CI builds |
| `GROQ_MODEL_ID` | Optional Groq model override |

## Architecture

| Layer | Stack |
|---|---|
| Language | Java |
| UI | Android Views, ViewBinding, Material Components |
| Pattern | MVVM (Fragments + ViewModels + LiveData) |
| Backend | Firebase Auth, Firestore, Remote Config |
| Navigation | Navigation Component + drawer |
| Build | Gradle Kotlin DSL, AGP 8.13 |

### Project layout

```
app/src/main/java/com/example/rummypulse/
├── data/              # Models, repositories, Firestore access
├── ui/
│   ├── dashboard/     # Game list & create
│   ├── home/          # Admin review & GST approval
│   ├── join/          # JoinGameViewModel
│   ├── reports/       # Monthly reports
│   ├── gamedefaults/  # Shared defaults editor
│   └── usermanagement/
├── utils/             # Updates, version gate, toasts, Groq client
├── JoinGameActivity.java
├── LoginActivity.java
└── MainActivity.java
```

### Notable components

- `GameRepository` / `GameDefaultsRepository` — Firestore reads and writes
- `JoinGameActivity` — Live game UI, scoring, announcements, amount visibility
- `ModernUpdateChecker` — GitHub release polling and in-app APK install flow
- `VersionGate` — Remote Config minimum version enforcement
- `AppUserRoleSession` — Cached admin vs non-admin role for navigation gating

## Contributing

1. Fork the repo and create a feature branch.
2. Use conventional commit prefixes (`feat:`, `fix:`, etc.) so CI versioning works.
3. Open a pull request against `main`.

## License

MIT — see [LICENSE](LICENSE).

## Author

**Debabrata Mandal**  
GitHub: [@debabrata-mandal](https://github.com/debabrata-mandal) · debabrata.developer@gmail.com
