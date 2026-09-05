# Daybook

Daybook is an offline-first personal tracker for habits and for what you eat, take, or want to
journal about. It reminds you at times you set, and each reminder *asks a question* ("Did you do
this?", "What did you have?") and keeps the answer — so over time you get a log you never had to
sit down and write, plus streaks and simple stats. Built for one person with a chronic condition
who wanted low-friction food-and-symptom and habit history without opening an app every time.

## Status

| | |
|---|---|
| `versionName` | `0.5.6` |
| `versionCode` | `20` |
| Room DB schema | `v19` |
| Platform | Android only — `minSdk 26` (Android 8.0), `compileSdk` / `targetSdk 34` |
| Theme | Dark only (no light theme, by design) |
| Data | Offline-first: on-device Room is the source of truth; cloud sync is an optional mirror |
| Package | `com.daybook.app` |
| Repo | https://github.com/Abhiram241/daybook |

## Tech stack

- **Kotlin 2.0.21** (K2), **Jetpack Compose** (Material 3, `compose-bom:2024.12.01`), Compose
  compiler Gradle plugin
- **Room 2.6.1** (SQLite) for local storage — the source of truth
- **Hilt 2.51** (via kapt) for dependency injection
- **Kotlin coroutines / Flow** for async work and reactive streams
- **AlarmManager** + notifications for exact-time reminders; **WorkManager 2.9.1** for the daily
  reminder-window top-up and sync flush
- **kotlinx.serialization** for JSON (export/import + the sync wire format)
- **Firebase** (BOM `33.1.2`): Auth (Google sign-in via Credential Manager) + Cloud Firestore
  (month-partitioned, gzipped mirror of Room) + Crashlytics; **Firebase App Distribution**
  (`16.0.0-beta14`) for over-the-air test builds and the in-app update check
- **Coil 2.6.0** for images (profile photo)
- **androidx.biometric 1.1.0** + **security-crypto 1.1.0-alpha06** for the optional app lock
  (PIN + biometric)
- Build: **AGP 8.3.2**, **Gradle 8.6**, JDK 17

## Project layout

```
app/src/main/java/com/daybook/app/
  DaybookApplication.kt   @HiltAndroidApp; one-time startup (notif channels, worker, sync.start(), crash handler)
  data/
    model/                every @Entity + every enum (DataModel.kt) — start here
    local/                Room: AppDatabase, the DAOs, Migrations.kt
    auth/                 Google sign-in (AuthRepository, Credential Manager, avatar fetch)
    backup/               BackupModel.kt — the v2 JSON backup / sync wire model
    sync/                 CloudSyncRepository + SyncLogic, MonthPartitioner, ContentHash, PayloadCodec, SyncStateStore
    lock/                 app lock: AppLockRepository, PinHasher, BiometricGate
    *Repository.kt        the thin middle layer (Habit, FoodMed, AppSettings, CustomCategory, CustomPrompt, ExportImport)
    OccurrenceScheduler.kt   the reminder engine: windows, arm / re-nag, resolve, backfill, batch check-in
    QuietHours.kt, RetentionPolicy.kt, ProfilePhotoStore.kt   small data helpers
  di/                     Hilt modules: DatabaseModule, FirebaseModule
  ui/
    MainActivity.kt       the one Activity: launch gate + NavHost + swipe pager
    NavConfig.kt          pure logic for the configurable bottom-nav
    theme/                Theme.kt, Tokens.kt (colours/spacing/shapes/motion), Type.kt (5 fonts), Accent.kt (5 accents)
    components/            shared composables: SoftCard, forms, sheets, SortSheet, WeekStrip, headers, dialogs
    home/                 Today screen + HomeViewModel (greeting, hero line, week strip, progress cards)
    routines/             Habits list + Add/Edit forms + HabitForm + ViewModels
    foodmed/              Intake list + Add/Edit + FoodMedForm + ViewModels
    journal/              HabitJournalChat/Edit screens + ViewModels (live); JournalScreen/ViewModel (retired legacy stepper)
    detail/              Detail screen (History / Stats tabs) + paging + ViewModel
    respond/             the reply/edit screen a notification tap opens
    settings/            Settings hub + sub-screens + SettingsViewModel
    account/             sign-in gate, account screen, sync status, delete-account
    lock/                LockScreen, AppLockSettingsScreen
    onboarding/          the first-run name screen
    icons/              curated icon set + resolver
  util/
    alarm/               AlarmReceiver, BootCompletedReceiver, NotificationActionReceiver
    notification/        NotificationUtils (channels, posting, PendingIntents), NotificationIdSequence
    work/                WindowRefreshWorker (daily window top-up), SyncFlushWorker
    streak/              StreakCalculator (habit/intake streaks), OngoingStreak (day count)
    update/              InAppUpdateChecker (Firebase App Distribution)
    DateTimeUtils.kt, JsonUtils.kt, StorageUtils.kt, TimeTicker.kt, CrashHandler.kt, enums/Converters.kt
app/schemas/…/<n>.json   Room's exported schema per version (3.json … 19.json) — committed source, not build output
app/src/test/            ~446 JVM unit tests (pure functions), ~74 files
app/src/androidTest/     MigrationTest, ChunkedDeleteTest, NavIconInflateTest (device-only)
```

## Build & run

```bash
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9
export ANDROID_HOME=/home/abhiram/android-sdk

./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk  (signed if keystore.properties is present)
```

Run on a connected device/emulator with `./gradlew installDebug` (then launch from the launcher),
or press ▶ in Android Studio. First launch shows a mandatory Google sign-in screen — use a real
device or add a Google account to the emulator.

`local.properties` (gitignored) points Gradle at the SDK: `sdk.dir=/home/abhiram/android-sdk`.

## The 4-gate

Nothing ships unless all four pass from a clean state:

```bash
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

`assembleRelease` must also be R8- and `lintVitalRelease`-clean.

## Release & OTA

- **Signing** — the `release` type is signed from `keystore.properties` (repo root, gitignored) +
  `app/daybook-release.jks` (gitignored). If `keystore.properties` is absent the build falls back
  to the debug key. Details: `RELEASE_SIGNING.md`.
- **Over-the-air** — `./gradlew assembleRelease appDistributionUploadRelease` builds and uploads
  the signed APK to the Firebase App Distribution `testers` group. **Bump `versionCode` first** —
  a repeated `versionCode` is treated by Firebase as a re-upload of the existing release. Full
  workflow: `HOW_TO_PUSH_UPDATES.md`.

## More docs

- **`HANDOVER.md`** — learn this codebase (and Android) from scratch: a module-by-module course
  with hands-on exercises.
- **`FEATURES.md`** — the full feature inventory, area by area.
- **`RELEASE_SIGNING.md`** — release keystore details.
- **`HOW_TO_PUSH_UPDATES.md`** — the Firebase App Distribution push workflow and troubleshooting.

## Notes

- `app/google-services.json` (Firebase config; committed) is required for any build touching
  Firebase — Auth, Firestore, Crashlytics, App Distribution. `keystore.properties` +
  `app/daybook-release.jks` are required for a properly signed release build; both are
  **gitignored** — without them the release build falls back to the debug key.
- Device-local settings (accent, font, week-start, quiet hours, etc.) live only in
  `app_settings` on the device — they are not synced and not in the JSON backup.
