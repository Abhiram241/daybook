# BATTERY_FIX_PLAN.md — implementation plan for a Sonnet implementer

You are implementing two approved battery fixes in the Daybook Android app
(`/home/abhiram/Downloads/app-for-food`, Kotlin / Jetpack Compose / Room / Hilt, package
`com.daybook.app`). A prior audit (`BATTERY_BUG_AUDIT.md`) identified them. The user has decided
the approach for both. **Do exactly what is below. Do not redesign, do not expand scope.**

There is **no emulator or device** on this machine (`adb devices` is empty) and no Compose UI
test / Robolectric / coroutines-test infra. Verification is compile + the JVM unit-test suite
only (details at the end).

---

## Goal

Reduce continuous power draw / heat while the app is open:

1. **Change 1 — display refresh rate:** stop hard-pinning the panel to its peak refresh mode for
   the whole foreground session; use a *soft* hint instead so the OS can idle the panel down for
   static frames.
2. **Change 2 — lifecycle-aware Flow collection:** replace `collectAsState()` with
   `collectAsStateWithLifecycle()` across the remaining UI so ViewModel Flow pipelines (minute
   tickers, Room queries, boundary loops) suspend while the app is backgrounded instead of
   running forever under `SharingStarted.WhileSubscribed`.

**Already done (do NOT touch, already built green):** the 3 tab screens
`ui/home/HomeScreen.kt`, `ui/routines/RoutinesScreen.kt`, `ui/foodmed/FoodMedScreen.kt` were
already converted in a prior pass — each already has
`import androidx.lifecycle.compose.collectAsStateWithLifecycle` and uses
`.collectAsStateWithLifecycle()` throughout. Leave them exactly as they are.

---

## Change 1 — refresh rate: hard mode-lock → soft hint

### File
`app/src/main/java/com/daybook/app/ui/MainActivity.kt`

### Exact current code (in `onCreate`, currently lines 109–115)

```kotlin
        // Opt in to the display's highest refresh rate, but only when it's actually worth it.
        runCatching {
            val best = display?.supportedModes?.maxByOrNull { it.refreshRate }
            if (best != null && best.refreshRate > 90f) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
            }
        }
```

### Replace it with

```kotlin
        // Ask for the display's highest refresh rate as a SOFT hint, but only when it's worth it.
        // `preferredRefreshRate` (unlike the old `preferredDisplayModeId`) does not pin the panel
        // to one mode for the whole session — the system stays free to drop the panel to a lower
        // rate for static / idle frames (LTPO down-clocking), which is what keeps the device from
        // running warm while the app is merely foregrounded and not animating. Compose still
        // requests the panel's high rate on its own during scroll / fling / animation.
        runCatching {
            val best = display?.supportedModes?.maxByOrNull { it.refreshRate }
            if (best != null && best.refreshRate > 90f) {
                window.attributes = window.attributes.apply { preferredRefreshRate = best.refreshRate }
            }
        }
```

### Notes / API details
- **Only the assignment line changes.** Keep the `runCatching { }` wrapper, keep the
  `display?.supportedModes?.maxByOrNull { it.refreshRate }` lookup, keep the `best != null &&
  best.refreshRate > 90f` guard, keep `window.attributes = window.attributes.apply { … }`.
- `WindowManager.LayoutParams.preferredRefreshRate` is a `Float`, available since API 21
  (minSdk here is 26 — fine). `Display.Mode.getRefreshRate()` already returns a `Float`, so
  `best.refreshRate` is the correct RHS. No cast needed.
- `preferredDisplayModeId` was the *only* reference to `best.modeId`; after this change `modeId`
  is unused but `best` is still used for `refreshRate`, so nothing else needs editing.
- **No import changes.** Both properties live on `android.view.WindowManager.LayoutParams`,
  already reached through `window.attributes`.
- Behaviour difference to expect: `preferredDisplayModeId` forced SurfaceFlinger to hold that
  exact mode while the window was focused; `preferredRefreshRate` only expresses a preference —
  the compositor picks a mode whose refresh rate matches when it makes sense and is free to
  switch to a lower rate for static content. This is the intended power win.

---

## Change 2 — `collectAsState()` → `collectAsStateWithLifecycle()` sweep

### What / why
`collectAsState()` keeps collecting as long as the composition is alive, which outlives the
Activity being stopped (app backgrounded, not killed). `collectAsStateWithLifecycle()` stops
collection below `Lifecycle.State.STARTED` and resumes it on return; for `StateFlow` the latest
value is retained and replayed instantly on resume (no flicker, no visible change).

### The import to add (every file below)
```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```
- This artifact (`androidx.lifecycle:lifecycle-runtime-compose:2.7.0`) is **already a project
  dependency** — see `app/build.gradle.kts`. No dependency change. Do not add or bump anything.
- Place it with the other `androidx.lifecycle.*` / `androidx.compose.*` imports; the codebase
  does not enforce strict import ordering.

### The mechanical edit (every call site)
Replace the literal string:
```
.collectAsState()
```
with:
```
.collectAsStateWithLifecycle()
```

**Safety of a blind find/replace:** every `collectAsState()` in the repo is the **zero-argument
`StateFlow<T>` overload** (verified: there are no `collectAsState(<initial>)` Flow-overload call
sites anywhere in `app/src/main`). So there are no nullable-initial-value concerns and the
1:1 textual replacement is correct at every site. Do **not** touch `collectIsPressedAsState()`,
`animate*AsState()`, or any other `*AsState` — only the exact token `.collectAsState()`.

### Unused-import cleanup
9 of the files below use an **explicit narrow import** `import androidx.compose.runtime.collectAsState`
(not the wildcard). After converting every call site in those files, that import is unused —
**delete that line** in those 9 files. (Unused import is only a warning, not a build failure, but
remove it for cleanliness.) The other 8 files import `androidx.compose.runtime.*` (wildcard) —
nothing to remove there.

Explicit-import files (delete `import androidx.compose.runtime.collectAsState` after converting):
`AccountScreen.kt`, `LockScreen.kt`, `AppLockSettingsScreen.kt`, `NavigationSettingsScreen.kt`,
`SignInGate.kt`, `JournalScreen.kt`, `HabitJournalEditScreen.kt`, `HabitJournalChatScreen.kt`,
`RespondScreen.kt`.

### Complete file list — 95 call sites across 17 files

All paths are under `app/src/main/java/com/daybook/app/`. Counts are the number of
`.collectAsState()` occurrences to convert in that file. The edit is purely mechanical in every
file; specific line numbers are given only for the handful that need care (Tier B).

#### Tier A — straightforward, convert all sites + add the import (+ remove the narrow import where noted)

| File | sites | import style | narrow import to delete? |
|---|---|---|---|
| `ui/settings/SettingsScreen.kt` | 33 | wildcard | no |
| `ui/detail/DetailScreen.kt` | 14 | wildcard | no |
| `ui/foodmed/AddFoodMedScreen.kt` | 6 | wildcard | no |
| `ui/foodmed/EditFoodMedScreen.kt` | 4 | wildcard | no |
| `ui/account/AccountScreen.kt` | 6 | explicit | **yes** |
| `ui/lock/LockScreen.kt` | 4 | explicit | **yes** |
| `ui/routines/AddHabitScreen.kt` | 4 | wildcard | no |
| `ui/onboarding/OnboardingScreen.kt` | 3 | wildcard | no |
| `ui/lock/AppLockSettingsScreen.kt` | 2 | explicit | **yes** |
| `ui/settings/NavigationSettingsScreen.kt` | 2 | explicit | **yes** |
| `ui/account/SignInGate.kt` | 2 | explicit | **yes** |
| `ui/routines/EditHabitScreen.kt` | 2 | wildcard | no |
| `ui/journal/JournalScreen.kt` | 1 | explicit | **yes** |
| `ui/journal/HabitJournalEditScreen.kt` | 1 | explicit | **yes** |
| `ui/journal/HabitJournalChatScreen.kt` | 1 | explicit | **yes** |
| `ui/respond/RespondScreen.kt` | 1 | explicit | **yes** |

Notes on Tier A specifics:
- `SettingsScreen.kt`: the 33 sites are spread across several `@Composable` functions in the file
  (the hub plus sub-screens — e.g. around lines 93–99, 382–384, 539–548, 715, 809, 848–850, 884,
  901, 926, 951–956). They read from `viewModel`, `accountViewModel`, and `lockViewModel`. All
  are the same mechanical `StateFlow` conversion. Add the import once at the top of the file.
- `AccountScreen.kt`, `SignInGate.kt`: sites read from an `AccountViewModel` /
  `SignInGateViewModel` — same `StateFlow` shape, mechanical.
- `DetailScreen.kt`: 14 sites, all `viewModel.<x>.collectAsState()`, mechanical.
- All Tier A screens are transient stack routes (pushed/popped) — behaviour is unchanged for the
  user; they simply stop recollecting while not visible.

#### Tier B — `ui/MainActivity.kt` (9 sites) — CONVERT, but read this first

These 9 sites are all inside `setContent { … }` / `MainApp()`, so `collectAsStateWithLifecycle()`
is valid (a `LocalLifecycleOwner` is always present under `setContent`). Convert them the same
way (mechanical `.collectAsState()` → `.collectAsStateWithLifecycle()`; the file uses the
`androidx.compose.runtime.*` wildcard, so just add the lifecycle import).

The 9 sites:

| line | code | role |
|---|---|---|
| 254 | `val accent by onboardingViewModel.accentColor.collectAsState()` | theme |
| 255 | `val fontChoice by onboardingViewModel.fontChoice.collectAsState()` | theme |
| 256 | `val reduceMotion by onboardingViewModel.reduceMotion.collectAsState()` | theme |
| 258 | `val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()` | launch gate |
| 259 | `val locked by appLockRepository.isLocked.collectAsState()` | **app-lock gate (security-sensitive)** |
| 260 | `val authState by authRepository.state.collectAsState()` | **auth gate (session-sensitive)** |
| 373 | `val navTabsCsv by onboardingViewModel.navTabs.collectAsState()` | nav config |
| 374 | `val defaultLandingTab by onboardingViewModel.defaultLandingTab.collectAsState()` | nav config |
| 384 | `val pendingDeepLink by deepLinkOccurrence.collectAsState()` | notification deep-link routing |

Why this is still correct after conversion (reason it through — you cannot run it here):
- All 9 sources are `StateFlow`s (or a `MutableStateFlow` field, line 384) with a current value.
  On `Lifecycle.Event.ON_START` the collector restarts and immediately reads the current value.
- `onResume()` in `MainActivity` calls `appLockRepository.onAppForegrounded()` (which may set
  `isLocked = true` after a background-timeout) and `appLockRepository` state is a `StateFlow`.
  `ON_START` fires **before** `ON_RESUME`, so by the time `onAppForegrounded()` flips the flag the
  lifecycle collector for line 259 is already active and receives the emission → the `when {}`
  launch gate recomposes → `LockScreen` shows. The lock still engages on return from background.
- Line 384: a notification tap delivers `onNewIntent()` → `readDeepLink()` sets
  `deepLinkOccurrence.value`, then `onResume()` runs. The lifecycle collector has restarted by
  then and the existing `LaunchedEffect(pendingDeepLink)` fires as before.

**Risk acknowledgement:** there is no device to smoke-test the lock / auth / deep-link gates.
The reasoning above says the conversion is safe, and the user approved "all remaining call
sites". Convert all 9. In your final report, explicitly list these three as
"converted, reasoned-safe, not device-verified" so a human tester can spot-check:
  1. background the app, wait past the app-lock timeout, reopen → lock screen must appear;
  2. sign out in another way / token expiry → sign-in gate must appear on return;
  3. tap a reminder notification while the app is backgrounded → correct screen opens.

If any of those three cannot be accepted without a device test, the fallback is to leave lines
**259, 260, 384** as `collectAsState()` and convert only the other 6 in this file — but the
default per the user's decision is convert all 9.

### After the sweep — sanity check
Run:
```
grep -rn "\.collectAsState()" app/src/main/java/com/daybook/app/
```
Expected result: **no matches** (every occurrence converted). If anything remains, convert it
(unless it is a deliberate Tier-B fallback you documented).
Also confirm no stray `import androidx.compose.runtime.collectAsState` remains in the 9
explicit-import files:
```
grep -rn "import androidx.compose.runtime.collectAsState$" app/src/main/java/com/daybook/app/
```
Expected: no matches.

---

## Verification gate (run all; all must pass)

From the repo root. If the system `java` cannot run Gradle, prefix with the JDK/SDK env the
project uses:

```
JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
  ./gradlew testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

Run Gradle in the **foreground / blocking** (Bash tool timeout up to 600000 ms). Do **not**
background it with a `while pgrep` watch loop.

What green looks like:
- `testDebugUnitTest` — **BUILD SUCCESSFUL**, ~446 JVM unit tests pass, 0 failures (the sweep
  changes no logic, so the count and pass state must be unchanged).
- `assembleDebug` — **BUILD SUCCESSFUL**, produces `app/build/outputs/apk/debug/app-debug.apk`.
- `assembleRelease` — **BUILD SUCCESSFUL**, R8 + `lintVitalRelease` clean, signed APK at
  `app/build/outputs/apk/release/app-release.apk`.
- `compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL**.

A Kotlin *warning* about `kapt` language version ("Kapt currently doesn't support language
version 2.0+. Falling back to 1.9.") is pre-existing and expected — not a failure.

If `assembleRelease` fails on an **unused import** lint (it should not — unused imports are
warnings), remove the offending `import androidx.compose.runtime.collectAsState` line you missed.

---

## Do NOT

- **No** schema / migration changes; **no** Room DB version bump (DB stays **v19**).
- **No** Firestore changes of any kind: no security-rules edit, no index change, no
  document-shape / collection-layout change, no change to the sync wire/payload format
  (`data/sync/**`, `data/backup/**`, `firestore.rules`, `firestore.indexes.json` are all
  **untouched** by this plan).
- **No** dependency additions, removals, or version bumps (`lifecycle-runtime-compose` is
  already present — use it as-is).
- **No** SDK / AGP / Gradle / Kotlin / compose-bom version changes.
- **No** `versionCode` / `versionName` change in `app/build.gradle.kts` (stays 22 / "0.5.6").
- **NO push anywhere.** No `./gradlew appDistributionUploadRelease`, no Firebase / App
  Distribution upload, no `git commit`, no `git push`, no branch creation, no tag. The
  deliverable **stops at a locally-built signed release APK sitting on disk**
  (`app/build/outputs/apk/release/app-release.apk`).
- **No** touching `ui/home/HomeScreen.kt`, `ui/routines/RoutinesScreen.kt`,
  `ui/foodmed/FoodMedScreen.kt` (already converted).
- **No** other refactors, restyle, "while I'm here" cleanups, or converting non-`collectAsState`
  `*AsState` calls. Scope is exactly Change 1 + Change 2.

If while implementing you discover any edit that would touch a Room schema, a migration, the
sync payload/wire format, Firestore rules/indexes, or any code that reads/writes/clears stored
data — **STOP and report it upward. Do not include it.** Nothing in this plan requires that;
if you think it does, you have misread the plan.

---

## Safety / reversibility

**Both changes are client-side, in-memory-only, and fully reversible.**

- **Zero backend changes.** No Firestore schema, rules, indexes, or document shape is touched.
  No Room schema or migration is touched — the database stays version **19**. The sync payload
  / backup wire format is not touched (`CloudSyncRepository`, `SyncLogic`, `MonthPartitioner`,
  `PayloadCodec`, `BackupModel` are **not edited**).
- **Zero stored-data access changes.** Neither change reads, writes, migrates, deletes, or
  clears any Room row, any `SharedPreferences` / `EncryptedSharedPreferences` value, any file in
  `filesDir`, or any Firestore document. Change 1 sets one `WindowManager.LayoutParams` field.
  Change 2 only changes **when** an in-memory `StateFlow` collector is subscribed (active while
  the screen is visible, suspended while it isn't) — the ViewModels still run the exact same
  Room queries with the exact same SQL when active, and write nothing they didn't write before.
- **Existing user data is preserved untouched** — the local Room DB and the user's Firestore
  documents are unaffected by installing a build with these changes. There is no first-run
  migration, no data reset, no re-sync forced.
- **How to revert.** Nothing here is committed. To undo *all* battery work (this plan's 17 files
  **plus** the 3 already-converted tab screens) back to the current `HEAD` (commit `46a714d`):

  ```
  git checkout -- app/src/main/java/com/daybook/app/ui/
  ```

  (No `.kt` files are *created* by this plan — every edit is a modification of a tracked file —
  so a `checkout` fully restores them. The two audit `.md` files at the repo root are the only
  new files; delete them if unwanted.) To undo only this plan's changes and keep the 3 tab
  screens, `git checkout --` just the 17 files listed below.

**Files this plan modifies (17):**
`ui/MainActivity.kt` (Change 1 + Change 2), `ui/settings/SettingsScreen.kt`,
`ui/detail/DetailScreen.kt`, `ui/foodmed/AddFoodMedScreen.kt`,
`ui/foodmed/EditFoodMedScreen.kt`, `ui/account/AccountScreen.kt`, `ui/lock/LockScreen.kt`,
`ui/routines/AddHabitScreen.kt`, `ui/onboarding/OnboardingScreen.kt`,
`ui/lock/AppLockSettingsScreen.kt`, `ui/settings/NavigationSettingsScreen.kt`,
`ui/account/SignInGate.kt`, `ui/routines/EditHabitScreen.kt`, `ui/journal/JournalScreen.kt`,
`ui/journal/HabitJournalEditScreen.kt`, `ui/journal/HabitJournalChatScreen.kt`,
`ui/respond/RespondScreen.kt`
(all under `app/src/main/java/com/daybook/app/`). Already-modified-in-a-prior-pass (do not
touch, listed for the revert command's completeness): `ui/home/HomeScreen.kt`,
`ui/routines/RoutinesScreen.kt`, `ui/foodmed/FoodMedScreen.kt`.

---

## Path to release APK

The user's end goal is a **locally-built signed release APK** containing both fixes.

### Steps (after the plan's code edits are applied)

1. Run the full verification gate (see above) — the `assembleRelease` task in it **is** the APK
   build; there is no separate step.

   ```
   JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk \
     ./gradlew testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
   ```

2. On success the signed release APK is at:

   ```
   app/build/outputs/apk/release/app-release.apk
   ```

3. Signing: `assembleRelease` signs with the real release key **iff** `keystore.properties`
   exists at the repo root (it points at `app/daybook-release.jks`; both are gitignored and
   present on this machine). If `keystore.properties` were absent the build falls back to the
   debug key (see `README.md` / `RELEASE_SIGNING.md`) — it still produces an APK, just
   debug-signed. Confirm the file exists before building if a properly-signed APK is required;
   do **not** create or modify it.

4. Hand back the path. **Stop there.** The Firebase App Distribution push
   (`./gradlew assembleRelease appDistributionUploadRelease`, per `HOW_TO_PUSH_UPDATES.md`) is
   **user-triggered only and is NOT part of this task or the estimate.**

### Wall-clock estimate for a Sonnet implementer (this machine)

| Phase | Estimate | Basis |
|---|---|---|
| Change 1 (1-line edit in `MainActivity.kt`) | ~2 min | trivial, incl. reading context |
| Change 2 (17 files: `.collectAsState()`→lifecycle, +1 import each, −1 import in 9, post-grep checks) | ~20–35 min | ~2 Edit calls/file + verification greps; tool round-trips dominate, not thinking |
| Verification gate — `testDebugUnitTest` + `assembleDebug` | ~1–2 min | this session's warm run of these two = **39 s** with most tasks up-to-date; changing 17 files re-runs `compileDebugKotlin` (+ possibly kapt stubs), so budget a bit more |
| Verification gate — `assembleRelease` + `compileDebugAndroidTestKotlin` | ~3.5–5 min | this session's run of these two = **3 m 13 s**; R8 `minifyReleaseWithR8` + `lintVitalRelease` always re-run on any code change and dominate |
| Buffer for one "fix a typo / missed site, re-run gate" cycle | ~5–7 min | realistic |
| **Total (implementation + one full gate + signed APK on disk)** | **~35–50 min** | of which **build/test is ~7–12 min**, the rest is the mechanical edit pass |

Notes:
- Run Gradle **once** as the single combined invocation above rather than task-by-task — the
  four tasks share compilation and the daemon stays warm, so the combined run is roughly the
  union of the two measured runs (~6–9 min) plus up to ~1 min if the daemon is cold on first
  invocation.
- A `./gradlew clean` first is **not** required by the gate and would add ~3–4 min; skip it.
- The estimate excludes any Firebase/App-Distribution push and any git operation — those are
  out of scope.

---

## Report back

State: which files you edited and how many call sites converted (expect 95 across 17 files for
Change 2, plus the 1-line Change 1), the full verification-gate result (all four tasks + unit
test count), and the explicit "converted but not device-verified" note for MainActivity lines
259 / 260 / 384.
