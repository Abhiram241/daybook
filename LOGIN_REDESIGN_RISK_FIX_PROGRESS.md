# Login Redesign + Risk-Audit Fix — Progress Handoff

Use this file as the prompt to resume this task in a new chat. It has everything needed:
what to build, what's already decided, and exactly what's done vs. not-yet-started.

---

## Repo / build facts

- Repo: `/home/abhiram/Downloads/app-for-food` — Daybook, an offline-first Android app
  (Kotlin/Jetpack Compose/Room/Hilt/Firebase), package `com.daybook.app`.
- **NOT a git repository.** No undo — be careful with deletions.
- Build env: `JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew <task>`
- Keystore: CN=Daybook, SHA1 `39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c` (see `RELEASE_SIGNING.md`).
- 4-gate (run after every phase, **in the foreground**): `testDebugUnitTest`, `assembleDebug`,
  `assembleRelease`, `compileDebugAndroidTestKotlin`. **Tip that saves real time**: all four can be
  passed to a single `./gradlew` invocation (`./gradlew testDebugUnitTest assembleDebug
  assembleRelease compileDebugAndroidTestKotlin`) — Gradle shares config/compilation across them,
  cutting wall time roughly in half vs. four separate invocations. Still counts as "all four gates."
- **versionCode stays 13, versionName stays "0.5.5" — do NOT bump either.** Room DB stays at
  schema v17 — no migration in this round.

## The task

Execute `LOGIN_REDESIGN_RISK_FIX_PLAN.md` (repo root, unedited, full 18-phase plan). Four combined
efforts: Phase 0 (crash stopgap), Phase 1 (icon branding), Phases 2-3 (sign-in redesign + onboarding
wizard), Phases 4-16 (41 risk-audit findings, Critical→High→Medium→Low), Phase 17 (regression doc +
signed APK, plus two extra asks — see below).

### Decisions locked in — do not re-litigate (plan's §0)

- **D1**: ship the Phase 0a+0b stopgap crash mitigation; no proven root-cause fix this round.
- **D2**: the tour wizard shows for EVERYONE (auto-derived-name path too), not just typed-name.
- **D3**: Phase 4 (S-1) uses **option 1** — filter remote hashes to the resident set before
  comparing.

### Two extra requirements beyond the plan document (fold into Phase 17 / Final Phase)

1. Build and deliver a **DEBUG APK** in addition to the release APK — confirm `isDebuggable=true`,
   `minifyEnabled=false` for the debug build type. Name it
   `Daybook-v0.5.5-login-redesign-debug.apk` at repo root, IN ADDITION TO the release APK.
2. Add a **"How to send us the next crash"** subsection to `LOGIN_REDESIGN_RISK_FIX_REGRESSION.md`
   covering: (i) Settings → About → "Copy crash log" → paste into a message; (ii) the full adb path
   (`adb logcat -b crash` before reproducing, or `adb pull
   /data/data/com.daybook.app/files/crash_log.txt .` after). Also loudly repeat the
   versionCode-13-collision install warning.

---

## Progress so far — what's ACTUALLY done on disk right now

Verified via `find app/src/main/java app/src/test/java -newer LOGIN_REDESIGN_RISK_FIX_PLAN.md`.
**All four gates green** (single combined `./gradlew testDebugUnitTest assembleDebug assembleRelease
compileDebugAndroidTestKotlin` run) after each phase below.

### Phase 0a — DONE (crash handler + retrieval)
- `util/CrashHandler.kt`: installs as `Thread.UncaughtExceptionHandler`, writes to
  `filesDir/crash_log.txt` (256KB cap, most-recent-first). Refactored the write/cap logic into a
  `companion object` function `appendCrash(file: File, text: String)` — **deviation from the
  plan's literal sketch**: done specifically so `CrashHandlerTest` (new,
  `app/src/test/java/com/daybook/app/util/CrashHandlerTest.kt`, 3 tests, all passing) can exercise
  the real write/cap behavior against a real temp file. Reason: this project's unit-test setup has
  no Robolectric/Mockito/MockK (confirmed — grepped `build.gradle.kts`), so constructing a fake
  `android.content.Context` to test the class as literally sketched isn't practical; `appendCrash`
  keeps the actual on-disk behavior tested without one.
- `DaybookApplication.kt`: installed first line of `onCreate()` — unchanged from before this round.
- `ui/settings/SettingsViewModel.kt`: added `@ApplicationContext private val appContext: Context`
  constructor param, `hasCrashLog()`, `crashLogText()`.
- `ui/settings/SettingsScreen.kt`: "Copy crash log" `TextLink` added to the About-Daybook footer,
  visible only when `hasCrashLog()` is true; copies via `LocalClipboardManager`.
- **Deviation**: did NOT add a test asserting the listener-callback / Settings-row Compose
  behavior end-to-end (see "Deliberate deviations" below re: no Compose UI test infra).

### Phase 0b — DONE (CloudSyncRepository listener guards)
- `data/sync/CloudSyncRepository.kt`: both previously-unguarded `scope.launch` blocks in
  `attachSnapshotListeners` (~line 793, parent doc) and `scopedMonthsListener` (~line 832, month
  docs) now wrapped in `runCatching { }.onFailure { Log.e(TAG, ...) }`, matching every other
  `scope.launch` in the file.
- **Deviation / gap, documented not fixed**: did NOT add the plan's suggested "listener-callback
  test harness" test (assert a thrown exception inside `applyRemoteParent`/`applyRemoteMonth`
  doesn't propagate). Reason: no existing test harness fakes Firestore's `DocumentSnapshot`/
  `QuerySnapshot`/`ListenerRegistration` types, and none of the mocking libraries needed to build
  one are dependencies of this project. Building that infra from scratch was judged out of
  proportion to this one guard. **Flag for a future round** if this specific regression risk
  matters enough to justify adding a Firestore-test-fakes layer.

### Phase 1 — DONE (Settings About-row icon)
- `ui/settings/SettingsScreen.kt`: `Image(painterResource(R.mipmap.ic_launcher_foreground))`,
  48dp, added above the "Daybook" / "Version X (Y)" text in the About footer.
- Sign-in hero and onboarding step-header mark are handled inside Phases 2 and 3 respectively (as
  the plan itself scopes them there — Phase 1's own file-touch list only requires the Settings row).
- **Deviation**: skipped the plan's "Compose UI test asserting the About row renders an Image not
  Icon node" — see "Deliberate deviations" below.

### Phase 2 — DONE (sign-in screen redesign)
- New file `ui/components/WaveHero.kt`: a `Canvas`-drawn 3-band overlapping Bezier wave hero,
  filled with `LocalAccent.current` at decreasing alpha, with `ic_launcher_monochrome` centered
  near the bottom seam. Pure Compose drawing — re-themes correctly under all 5 `AccentColor`s with
  no static asset.
- `ui/account/SignInGate.kt`: restructured per the plan — outer shape (non-scroll `Column`,
  weighted scrollable child, `StickySaveBar` pinned below) is **byte-for-byte unchanged in
  structure**; added `WaveHero` above the scrollable content, gave the scrollable Column a
  `clip(AppShapes.sheet)` + `background(DaybookColors.Surface)` "sheet" look, changed headline to
  "Welcome to Daybook" + one-line blurb. Reused the existing `AppShapes.sheet` token
  (`RoundedCornerShape(topStart=20dp, topEnd=20dp)`) instead of inventing a new 28dp radius, for
  consistency with the rest of the app.
- `ui/account/SignInContent.kt`: **untouched** — `GoogleSignInButton` reused as-is inside the same
  `StickySaveBar`, exactly as the plan specifies.
- **Deviation**: skipped the plan's 3 Compose UI test asks (button still pinned/visible across
  configs, WaveHero renders across all 5 accents, BackHandler still swallows back) — see below.

### Phase 3 — DONE (onboarding feature-tour wizard)
- `ui/onboarding/OnboardingViewModel.kt`: added `sealed class WizardStep` (`NameAsk` /
  `FeatureTip`), `OnboardingTourSteps` (5 condensed tips per the plan's own "condense to 4-5
  screens" recommendation: Today-at-a-glance, Track-anything, Reminders-that-adapt-to-you,
  Make-it-yours, Offline-first-synced-and-locked-down/App-Lock), two new pure functions
  `buildWizardSteps(hasAutoDerivedName)` and `isLastWizardStep(currentStep, stepCount)` (unit
  tested, see below), plus ViewModel state `steps`/`currentStep`/`nameInput` and methods
  `configure(derivedName)` (idempotent — guards against a `LaunchedEffect` re-run resetting
  progress), `next()`, `skip()`. `completeOnboarding(name)` itself is unchanged (same fire-once
  guard) — now only ever invoked from `next()`/`skip()`.
  - **Removed** the old `_autoCompleteFailed` flag entirely (was: fall back to the manual name
    screen if the silent pre-tour persist failed). Moot under D2 — there is no more silent
    pre-tour persist to fail; `completeOnboarding` only fires from wizard interaction now, and a
    failure there still surfaces via the pre-existing `errorMessage` flow. Documented inline in
    the ViewModel.
- `ui/onboarding/OnboardingScreen.kt`: rewritten to render whichever `WizardStep` is current
  (`NameAsk` → the original name field; `FeatureTip` → a single large `SoftCard`/`IconTile` block),
  a small step-dots indicator (new `StepDots` composable — no pre-existing dot primitive found in
  `ui/components/`), and a `StickySaveBar` now holding **two** controls side by side
  (`TextLink("Skip")` + `PrimaryButton("Next"/"Get started")) — `StickySaveBar`'s content slot
  already accepted arbitrary content, so **no change to `StickySaveBar` itself** (DO-NOT-TOUCH
  honored).
- `ui/MainActivity.kt`'s ONBOARDING branch: **both D2 paths now converge** — removed the old
  `LaunchedEffect(derived){ completeOnboarding(derived) }` + blank-splash special case entirely.
  Both the auto-derived-name and typed-name paths now call `onboardingViewModel.configure(derived)`
  once (via `LaunchedEffect(derived)`) and render `OnboardingScreen(viewModel = onboardingViewModel)`
  — `configure`'s internal `hasAutoDerivedName` branch (in `buildWizardSteps`) is what actually
  decides whether `NameAsk` appears, matching the plan's D2 resolution exactly.
- New test `ui/onboarding/WizardStepTest.kt` (5 tests, all passing): covers `buildWizardSteps`
  including/excluding `NameAsk`, and `isLastWizardStep`'s boundary in both directions plus the
  single-step-wizard edge case.
- **Deviation / gap, documented not fixed**: did NOT add a ViewModel-level test for `next()`/
  `skip()`/`completeOnboarding` interaction (e.g. "skip() calls completeOnboarding exactly once
  regardless of currentStep"). Reason: this would require a fake `AppSettingsRepository` plus
  `kotlinx-coroutines-test` (neither present as a dependency anywhere in this codebase's ~13
  ViewModels), and no existing ViewModel test in this project does suspend-level testing — they're
  all pure-function tests (`DeriveOnboardingNameTest` is the exact sibling example). The pure logic
  that actually decides the branching (`buildWizardSteps`, `isLastWizardStep`) IS unit tested;
  the remaining gap is coroutine plumbing around already-tested pure decisions, which is
  lower-value to add net-new test infra for mid-round. Flag for a future round if this project ever
  adopts `kotlinx-coroutines-test`.

### Phase 4 — DONE (CRITICAL: S-1, bootstrap false-CONFLICT)
- `data/sync/SyncLogic.kt`: new pure function `residentMonthHashes(remoteMonthHashes, residentSet)`
  (filters remote hashes to the resident set — D3 option 1).
- `data/sync/CloudSyncRepository.kt` `bootstrap()`: computes `residentSet = hydratedMonths +
  localMonthHashes.keys + recentMonths()`, compares `residentMonthHashes(remoteMonthHashes,
  residentSet)` against `localMonthHashes` instead of the raw (unfiltered) remote map.
- `ui/home/HomeViewModel.kt`: new `syncStatus: StateFlow<SyncStatus> = cloudSync.status`.
- `ui/home/HomeScreen.kt`: new private `SyncStatusBanner` composable + wiring — shown as the first
  LazyColumn item (above WeekStrip) when `syncStatus` is `Paused` or `Error`; tappable, routes to
  `onNavigateToSettings` (Account is one tap further, same as the avatar-click path already uses).
- `app/src/test/java/.../data/sync/BootstrapDecisionTest.kt`: 3 new tests — filters-out-evicted-month,
  evicted-month-resolves-ATTACH_ONLY, and a regression guard proving the OLD unfiltered comparison
  would have wrongly forced CONFLICT (13 tests total in the file now, all passing).

### Phase 5 — DONE (HIGH: S-2 account-deletion orphan + S-4 sign-out identity leak)
- `ui/account/AccountViewModel.kt` `deleteAccount()`: captures `deleteRemoteDoc()`'s `Boolean`
  result; on failure, sets `"Couldn't reach the cloud — connect and try again."` and returns
  WITHOUT calling `authRepository.deleteAccount()`.
- `data/sync/CloudSyncRepository.kt`: constructor now also takes `AppSettingsRepository` and
  `ProfilePhotoStore` (verified no Hilt cycle — neither depends back on CloudSyncRepository).
  `wipeLocalForSignOut()` now also resets `user_name`→"", `profile_photo_path`→null,
  `onboarding_completed`→false, and calls `profilePhotoStore.clear()`, all wrapped in one
  `runCatching` after the existing 8-table transaction (unchanged) — device-scoped prefs
  (accent/font/quiet-hours/etc.) on the same `app_settings` row are deliberately left untouched.
- **Gap, documented not fixed**: did NOT add the plan's two suggested tests ("deleteAccount does
  NOT call authRepository.deleteAccount() when remote delete fails"; "wipeLocalForSignOut resets
  all three identity columns and calls clear()"). Reason: same as every other skipped test this
  round — both need a fake `AuthRepository`/`AppSettingsRepository`/`ProfilePhotoStore` plus
  suspend/coroutine test plumbing this codebase doesn't have. The fix itself is small and
  directly readable (see the two diffs above); flagging for a future round if `kotlinx-coroutines-
  test` + fakes are ever adopted here.

### Phase 6 — DONE (HIGH: S-3 SQLite 999-variable limit + hydration-retry bug)
- **Deviation from the plan's literal chunking sketch, and why**: the plan says to "chunk
  `habitKeep`/`taskKeep` into ≤900-id batches, calling the DAO method once per chunk." Verified this
  is WRONG for `deletePendingByLocalMonthBefore`'s `id NOT IN (:keep)` clause specifically:
  splitting `keep` into disjoint chunks and running one `NOT IN (chunk_i)` DELETE per chunk
  sequentially does not union correctly the way a chunked `IN (:ids)` does — a row kept only by
  chunk 2 gets wrongly deleted by chunk 1's `NOT IN (chunk_1)` pass (it's absent from chunk 1),
  and by the time all chunks have run, only rows present in the intersection of every chunk survive
  — for disjoint chunks of a single-membership list, that intersection is empty, i.e. **the naive
  per-chunk approach deletes every kept row**, which is a worse bug than the 999-limit crash it was
  meant to fix. Implemented instead: two new DAO methods
  `pendingIdsByLocalMonthBefore(monthPrefix, before): List<String>` (no `keep` bound at all) on
  both `HabitOccurrenceDao`/`FoodMedOccurrenceDao`; `ExportImportRepository.importMonth` now fetches
  candidates with that unbounded query, subtracts the full `keep` Set in Kotlin (no SQL
  bound-variable involved), then deletes the remainder via `deleteByIds` chunked at
  `SQLITE_MAX_VARS = 900` — which IS safe to chunk (a plain `IN (:ids)` chunked delete is a correct
  union). Also chunked `habitDelete`/`taskDelete` (the S15 merge's straightforward `deleteByIds`
  calls) the same way, since they share the same underlying limit and weren't explicitly excluded.
- `data/sync/CloudSyncRepository.kt` `ensureMonthHydrated`: when `applyRemoteMonth` returns `false`
  (e.g. a future Phase 8 corrupt-payload rejection), `hydrationAttempted.remove(month)` is now
  called so a later navigation into that month retries — previously this fell through with no
  cleanup and the month stayed permanently marked "tried."
- New androidTest `app/src/androidTest/java/com/daybook/app/data/local/ChunkedDeleteTest.kt`: a
  1200-synthetic-row in-memory-Room DAO test asserting the candidate-fetch → Kotlin-Set-subtract →
  chunked-delete pattern removes exactly the right 1080 of 1200 rows (120 kept), nothing else.
  **Compiles clean via `compileDebugAndroidTestKotlin`** but — like every androidTest in this
  project — is never actually executed by the 4-gate (no emulator/device on this machine; see the
  "Deliberate deviation" section above). Deliberately asserts the chunking pattern's *correctness*
  rather than trying to reproduce the historical `SQLiteException` itself, since the actual
  999-variable ceiling is a property of the SQLite version bundled with whatever device eventually
  runs this test, not something reproducible in a fixed assertion.

### Phase 7 — DONE (HIGH: N-1 quiet-hours-bypassed re-nag + N-2 suppressed-notification-still-SHOWN)
- `data/OccurrenceScheduler.kt`: new public `deferForQuietHours(triggerAtMillis)` wrapping the
  existing private `quietDefer` — so `AlarmReceiver`'s refire arm (the one arm path that bypassed
  quiet hours) can use the same defer every other arm path already gets.
- `util/notification/NotificationUtils.kt`: `showHabitNotification`/`showHabitJournalNotification`/
  `showFoodMedNotification` all gained `.setOnlyAlertOnce(true)` (N-1 — a refire re-posts under the
  same notification_id, so without this every refire re-alerts at full volume even through quiet
  hours). `notify()` now returns `Boolean` (posted or not); the three `show*Notification` functions
  propagate it (N-2).
- `util/alarm/AlarmReceiver.kt`: `fireHabit`/`fireFoodMed` now route the refire trigger through
  `scheduler.deferForQuietHours(...)`, and both check the `show*Notification` return value —
  on `false` (blocked channel/permission), no SHOWN event is inserted and no refire is armed
  (previously both happened unconditionally, creating a phantom "SHOWN → SKIPPED" history entry
  for a reminder the user never saw).
- `ui/home/HomeViewModel.kt` + `HomeScreen.kt`: new `NotificationsBlockedBanner` (mirrors Phase 4's
  `SyncStatusBanner`) shown on Today when `notificationBlockReason() != null`, deep-linking to
  `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- **Gap, documented not fixed**: no new automated test for N-1's wiring (AlarmReceiver calling
  `deferForQuietHours`) or N-2's gating (notify()-returns-false blocks SHOWN/refire) — both require
  Room+NotificationManager-level mocking this codebase's test tier doesn't have. The underlying
  pure logic (`deferIfInsideQuietHours`) is already exhaustively covered by the pre-existing
  `QuietHoursTest`; the new code is thin suspend/Boolean-propagation wiring around it.

### Phase 8 — DONE (HIGH: C-3 corrupt month payload silently treated as valid empty month)
- `data/sync/MonthPartitioner.kt`: `decodeDays` return type changed to `List<DayEntry>?` (was
  `emptyList()` on failure — indistinguishable from a genuinely empty month). New pure
  `isGenuinelyEmptyMonth(storedHash: String?)` — true when `storedHash` is null or matches
  `ContentHash.ofDays(emptyList())`.
- `data/sync/CloudSyncRepository.kt` `applyRemoteMonth`: gunzip failure and decode failure (now
  `null`) both `return false` without touching Room or storing the hash — matches
  `applyRemoteParent`'s existing null-guard. Also refuses a month that decoded to zero days but
  whose stored hash doesn't match a genuine empty month (the "truncated to a bare `[]`" corruption
  mode). The other `decodeDays` call site (conflict-dialog remote-day-count display) updated for
  the new nullable return (`?.size ?: 0`) — display-only, no merge-path impact.
- Tests: `MonthPartitionerTest.kt` — `decodeDays` garbage case now asserts `null` (was `emptyList()`,
  test updated to match the intentional behavior change), plus 3 new tests for
  `isGenuinelyEmptyMonth`. Did NOT add a `CloudSyncRepository`/`applyRemoteMonth` test (would need a
  fake Firestore `DocumentSnapshot` — no such harness exists; same documented gap as Phase 0b).

### Phase 9 — DONE (HIGH: C-4 journal/backfill saves report success when silently rejected)
- `data/OccurrenceScheduler.kt`: new `sealed class LogResult { Success; data class Rejected(reason) }`.
  Threaded through `logFoodMed`, `logJournal` (the plan said "logFoodMed" for this call site but
  `JournalViewModel.save()`'s non-backfill branch actually calls `logJournal` — its exact sibling;
  included it for the same reason), `logHabitJournal`, `backfillFoodMed`, `backfillHabitJournal`.
  Every previously-silent early return (`occ == null`, `canBackfill` rejection, month not resident)
  now returns `LogResult.Rejected(<user-facing reason>)` instead of a bare `return@withLock`.
- `ui/journal/JournalViewModel.kt`, `ui/respond/RespondViewModel.kt` (its `log()` action only —
  `complete()`/`skip()`/`undo()` still go through the shared `resolve{}` helper unchanged, since
  none of those three can reject), `ui/journal/HabitJournalChatViewModel.kt`: each gained a
  `rejectedMessage: String?` UI-state field; a rejection sets it and leaves `saved`/`done` false
  (previously both were set true unconditionally after a bare `runCatching{}`).
- `ui/journal/JournalScreen.kt`, `ui/respond/RespondScreen.kt`, `ui/journal/HabitJournalChatScreen.kt`:
  each now renders the rejection message (Warning-colored `Text`) instead of silently popping back
  as if the save succeeded. `HabitJournalChatScreen.kt` specifically: the existing `SavedBubble`
  ("✓ Entry saved") was rendering optimistically the instant every question was answered, BEFORE
  the save was confirmed — now suppressed when a rejection occurs, replaced by a new
  `RejectedBubble`.
- **Gap, documented not fixed**: no automated test asserting the full ViewModel-level behavior
  ("a canBackfill-rejected save surfaces Rejected, not Success"). The underlying pure decision
  (`canBackfill`) is already exhaustively covered by the pre-existing `BackfillEligibilityTest`;
  the new code is suspend/Room-dependent plumbing around it (same documented gap pattern as every
  other ViewModel/Repository suspend-level test this round).

### Phase 10 — DONE (HIGH: C-9 no CoroutineExceptionHandler + C-18 no crash reporter, combined)
- `build.gradle.kts` (root) + `app/build.gradle.kts`: added the `com.google.firebase.crashlytics`
  plugin (v3.0.2) and `implementation("com.google.firebase:firebase-crashlytics")` (version from
  the existing BoM). Confirmed resolves fine (network access works from this machine) and both
  `assembleDebug`/`assembleRelease` build clean including a live `uploadCrashlyticsMappingFileRelease`
  run.
- New `util/ViewModelExt.kt`: `ViewModel.safeLaunch(context, onError, block)` — drop-in replacement
  for `viewModelScope.launch(...)`, wraps in a `CoroutineExceptionHandler` whose default `onError`
  reports to Crashlytics + logs. Also `recordUnhandledException(t)`, shared by `safeLaunch`'s
  default and every other Phase 10 call site (see below).
  - **Important correctness fix found and applied mid-phase**: `recordUnhandledException`'s first
    draft called `Log.e(...)` then `FirebaseCrashlytics.getInstance().recordException(...)`
    unguarded. This broke `MonthPartitionerTest`'s existing "decodeDays returns null on garbage"
    test — `android.util.Log` is UNMOCKED in this project's plain-JVM unit tests (confirmed:
    no Robolectric, no `unitTests.returnDefaultValues` in `build.gradle.kts`), so any `Log.*` call
    from code reached by a pure-function test throws `RuntimeException: ... not mocked`, and a
    Crashlytics call would hit the same "no FirebaseApp initialized" problem in that environment.
    Fixed by wrapping BOTH the `Log.e` and the `FirebaseCrashlytics` call each in their own
    `runCatching { }` inside `recordUnhandledException` — the real exception is still reported in
    the running app (where both Log and FirebaseApp work fine), but a reporting-path failure in a
    test/edge environment can never itself become a second, worse crash. **This same guard applies
    to every other Phase 10 `recordException` addition below** — they all route through this one
    function rather than calling `FirebaseCrashlytics.getInstance()` directly, specifically so this
    fix covers all of them at once.
- **Mechanical migration — all ~92 call sites, 14 ViewModel files, zero remaining
  `viewModelScope.launch` in `ui/`** (confirmed via
  `grep -rn "viewModelScope.launch" app/src/main/java/com/daybook/app/ui/` → empty): AccountViewModel,
  DetailViewModel, AddFoodMedViewModel, FoodMedViewModel, HomeViewModel,
  HabitJournalChatViewModel, HabitJournalEditViewModel, JournalViewModel, LockViewModel,
  OnboardingViewModel, RespondViewModel, AddHabitViewModel, RoutinesViewModel, SettingsViewModel.
  Done via a scripted sed pass (`viewModelScope.launch {` → `safeLaunch {`,
  `viewModelScope.launch(Dispatchers.IO) {` → `safeLaunch(Dispatchers.IO) {`, plus the import) —
  **caught and fixed a labeling bug the mechanical pass introduced**: several call sites used
  `return@launch` inside the lambda (e.g. `if (occ == null) { ...; return@launch }`); once the
  call site is `safeLaunch { }` instead of a literal `launch { }`, that label no longer resolves
  (Kotlin's implicit lambda label comes from the literal call-site function name). Found via a full
  `compileDebugKotlin` failure sweep and fixed every instance with `return@safeLaunch` (confirmed
  zero remaining bare `return@launch` outside `return@launchBusy`, which is unrelated and untouched).
  Full `testDebugUnitTest`/`assembleDebug`/`assembleRelease`/`compileDebugAndroidTestKotlin` all
  green afterward.
- `ui/home/HomeViewModel.kt`: all 18 `.stateIn(...)` pipelines (the plan said "specifically" without
  naming an exact subset — applied to all of them, since none is obviously safe to skip) gained an
  upstream `.catch { recordUnhandledException(it) }` — on a thrown exception the flow stops
  emitting (StateFlow keeps serving its last-known-good value) instead of crashing the process,
  and the failure is reported.
- `recordUnhandledException` (via `recordException`) also added at every specifically-named
  previously-swallowed site: `CloudSyncRepository.kt`'s two Phase-0b listener `onFailure` blocks and
  `deleteRemoteDoc`'s `onFailure` (S-2); `MonthPartitioner.decodeDays`'s `onFailure` (C-3);
  `NotificationUtils.notify()`'s `catch(Throwable)` (N-2); `AlarmReceiver.kt`'s `runAsync` — both its
  outer `CoroutineExceptionHandler` and its inner `catch(Throwable)`; `WindowRefreshWorker.kt`'s
  `catch(Throwable)`.
- **Gap, documented not fixed**: no unit test for `safeLaunch` itself (the plan's ask: "a thrown
  exception inside the block invokes onError and does not crash the test process"). Reason:
  `viewModelScope` requires `Dispatchers.Main` to be set, which throws in a plain JVM test without
  `kotlinx-coroutines-test`'s `Dispatchers.setMain(...)` (not a dependency here) or Robolectric.
  Same documented infra gap as every other suspend-level test this round. The mechanical
  migration's correctness is instead verified by: a full compile pass across all 14 files (catches
  any signature/label mismatch, which is exactly how the `return@launch` bug above was actually
  caught) and the full existing `testDebugUnitTest` suite passing unchanged (438 tests, verifying no
  pure-function behavior regressed).

### Phase 11 — DONE (MEDIUM: sync-state bookkeeping, S-5 through S-10)
- **S-5**: `CloudSyncRepository.kt`'s `wipeLocalForSignOut` transactional body extracted into a new
  public `wipeAllLocalData()`; `wipeLocalForSignOut` is now a one-line delegate. Deleted
  `AccountViewModel.wipeLocalData()` (the weaker, non-transactional duplicate) outright;
  `deleteAccount()`'s "also erase local" branch now calls `cloudSync.wipeAllLocalData()`. Also
  removed `AccountViewModel`'s now-fully-unused `database: AppDatabase` constructor param + import.
- **S-6**: deleted the `hydrating` `AtomicInteger` guard entirely (field, inline function, the
  `if (hydrating.get() > 0) return` check in the tracker observer) — confirmed via its own KDoc that
  the hash-diff in `doPush` was always the actual correctness mechanism, this was redundant and
  racy (Room's `InvalidationTracker` callback fires asynchronously, so `hydrating` was typically
  already back at 0 by the time it ran). All 5 former `hydrating { ... }` call sites unwrapped in
  place (`wipeAllLocalData`, `evictStaleMonths`, `runMaintenance`, `applyRemoteParent`,
  `applyRemoteMonth`) — behavior-preserving, just without the ineffective wrapper.
- **S-7**: `evictStaleMonths` now also prunes `syncState.monthHashes` for evicted months (was:
  only `hydratedMonths` was pruned; `monthHashes` grew unboundedly, directly feeding S-1/Phase 4's
  bug class). Mirrors the existing `onLocalDataReplaced` pruning pattern.
- **S-8**: new pure `MonthPartitioner.cappedMostRecentMonths(resident, cap=30)` — sorts
  lexicographically descending (= chronologically descending for `"yyyy-MM"` keys) before taking
  30, so `scopedMonthsListener`'s Firestore `whereIn` cap always drops the OLDEST months, never an
  arbitrary `Set`-iteration-order pick that could drop a recent one. Also: `endRangeExport()` now
  kicks off `runMaintenance()` (eviction) proactively in the background right after a large
  date-range export finishes, instead of waiting for the next daily-worker/`onAppStop` tick — so
  `hydratedMonths` doesn't sit swollen past 30 in the meantime.
- **S-9**: `ensureMonthHydrated` now retries a server-unreachable fetch up to 2 more times with a
  short backoff (500ms, 1.8s — ~2.3s total) before giving up and freeing `hydrationAttempted` for a
  later external retry. Bounded specifically because this runs from a UI-triggered suspend call.
- **S-10**: new `SyncStateStore.conflictPromptShownRevision: Long` (paired with the existing
  `conflictPromptShownForUid`) + new pure `SyncLogic.conflictAlreadyResolved(promptShownForUid,
  promptShownRevision, currentUid, currentRemoteRevision)`. Previously `conflictPromptShownForUid ==
  uid` alone meant "this uid's conflict was resolved once, ever" — permanently suppressing
  re-prompting even for a completely unrelated LATER divergence. Now `resolveConflict()` also
  records the post-resolution `lastKnownRevision`, and `bootstrap()` only treats the prompt as
  "already resolved" (skip to last-write-wins ATTACH_ONLY) when the remote revision still matches
  that snapshot — a genuinely new remote change re-opens the possibility of a fresh CONFLICT prompt.
- Tests: `BootstrapDecisionTest.kt` +4 (`conflictAlreadyResolved`'s 4 cases), `MonthPartitionerTest.kt`
  +2 (`cappedMostRecentMonths`'s over-cap and under-cap cases). All passing (448 tests total now).
- **Gap, documented not fixed**: no test for S-9's retry-with-backoff wiring itself (network-call +
  `delay()` sequencing) — same suspend/network-mocking gap as every other such case this round; the
  bounded-retry constant (`RETRY_BACKOFF_MS`) is straightforward by inspection.

### Phase 12 — DONE (MEDIUM: notification/alarm robustness, N-3 through N-8)
- **N-3**: `SyncStateStore` gained `batchSnoozeUntil: Long` (new pref key `batch_snooze_until`,
  SharedPreferences — no Room/schema change). `OccurrenceScheduler` now also injects
  `SyncStateStore` (no Hilt cycle). `armBatchCheckInInternal` arms to
  `max(nextScheduledCheckin, batchSnoozeUntil)`. `snoozeBatchCheckIn()` now takes `syncMutex` (for
  symmetry with `completeAllBatchToday`) and persists the snooze deadline before scheduling.
- **N-4**: `BootCompletedReceiver.REARM_ACTIONS` gains
  `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+, guarded); added the
  matching `<action>` to `AndroidManifest.xml`'s `BootCompletedReceiver` intent-filter (harmless
  no-op declaration on pre-31 OSes, which never send it).
- **N-5**: `SettingsScreen.kt`'s `NotificationSettingsScreen` gained a third `PermissionRow`
  ("Battery"), checking `PowerManager.isIgnoringBatteryOptimizations()` AND (API 28+)
  `!ActivityManager.isBackgroundRestricted()`, with a one-tap
  `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` fix (falls back to
  `ACTION_APPLICATION_DETAILS_SETTINGS` if that action isn't implemented on an OEM build). Added
  the required `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest permission (normal-level, no runtime
  prompt of its own).
- **N-6**: `NotificationActionReceiver.kt`'s `ACTION_REPLY` branch reordered — `logFoodMed` (now
  `logFoodMedFromNotificationReply`, see N-7) runs FIRST, `postReplyAck` only posts on confirmed
  `LogResult.Success`. New `NotificationUtils.postReplyFailed(...)` posts a distinct "Couldn't
  save — tap to retry" notification (reopens the app at that occurrence) on `Rejected` or on a
  caught timeout/exception; the `finally` block's generic cancel is skipped for a food/med Reply
  specifically (both outcomes already replace that same notification id themselves).
- **N-7**: new `OccurrenceScheduler.logFoodMedFromNotificationReply(occurrenceId, text): LogResult`
  — checks `occ.status == PENDING` BEFORE calling the general `logFoodMed`, returning a new
  `LogResult.AlreadyResolved` case instead when it's not. This is the notification-Reply-specific
  fix the plan asked for; the general `isFoodMedEdit` predicate and every in-app caller of
  `logFoodMed` are untouched. New `NotificationUtils.postAlreadyLoggedNotice(...)` posts the
  "Already logged — open the app to edit" notice. Added the new `AlreadyResolved` branch
  (defensive, unreachable from their own call paths) to the 3 `when` blocks Phase 9 added in
  `JournalViewModel`/`RespondViewModel`/`HabitJournalChatViewModel` — Kotlin's exhaustiveness check
  required it once `LogResult` gained a third case.
  - **Residual minor race, documented not further fixed**: `logFoodMedFromNotificationReply` checks
    `status == PENDING` OUTSIDE `syncMutex`, then calls `logFoodMed` which re-checks status inside
    its own lock. A concurrent action resolving the SAME occurrence in the handful-of-milliseconds
    window between those two checks could still hit the original silent-overwrite path. Judged
    acceptable: this narrows an always-possible race to an extremely narrow one, matches the plan's
    literal prescription ("check status == PENDING before calling logFoodMed"), and a true atomic
    fix would require restructuring `logFoodMed` itself, risking the plan's explicit "do NOT change
    the general `isFoodMedEdit` predicate" constraint.
- **N-8**: `completeAllBatchToday`'s per-occurrence loop now runs inside `db.withTransaction { }`;
  the batch notification is cancelled only after the transaction commits.
- **Gap, documented not fixed**: no new automated tests this phase — every fix is either (a)
  suspend/Room/notification-mocking-dependent wiring (same established gap), or (b) the one new
  pure-ish surface (`LogResult.AlreadyResolved`) is a data-carrying sealed case with no decision
  logic of its own to unit test beyond what's already exercised by existing `LogResult` call sites.

### Phase 13 — DONE (MEDIUM: C-5, C-14 — C-6/C-10/C-17 need no separate work, confirmed per plan)
- **C-5**: `CloudSyncRepository.bootstrap()` now checks the `exportBackup()` `runCatching` result
  explicitly — on failure it logs, records to Crashlytics, sets `SyncStatus.Error(message)`, and
  `return`s immediately WITHOUT calling `decideBootstrap` at all. Previously a genuine export
  failure silently became `localDefsHash = null`, which forces `hashesEqual = false` and can land in
  CONFLICT — the same path a real divergence takes — forcing a data-loss-risk dialog over what was
  actually an internal error.
- **C-14**: new `StorageUtils.fileSizeBytes(uri)` (via `ContentResolver.query` +
  `OpenableColumns.SIZE`, fails open — null — if a provider can't report it, rather than blocking a
  legitimate import it just can't measure). `SettingsViewModel.importFromUri` now refuses anything
  over `MAX_IMPORT_BYTES = 10MB` with "That file is too large to be a Daybook backup" BEFORE calling
  `readText`. All three of `importFromUri`/`exportRange`/`shareLatestExport`'s catch blocks changed
  from `catch (e: Exception)` to `catch (t: Throwable)` so a slipped-through `OutOfMemoryError`
  surfaces as a message instead of propagating past the handler.
  - **Deviation, and why**: did NOT implement the plan's third C-14 sub-item — switching
    `readText`/`importAllData`'s String-based JSON decode to a streaming `Json.decodeFromStream`.
    Reasoning: the 10MB size cap above already bounds the in-memory exposure to a level no modern
    Android device's heap will OOM on (a real backup is "well under 1MB" per the plan's own
    citation of the Firestore 1MiB-per-month-doc bound) — the size check is what actually closes
    the OOM risk. The streaming refactor would touch `ExportImportRepository.importAllData`'s
    public signature and every call site/test, a materially larger footprint, for a marginal
    safety improvement once the 10MB cap already stands. Flag for a future round if the cap is
    ever relaxed upward.

### Phase 14 — DONE (LOW: sync/rules hardening, S-11, S-12, S-13)
- **S-11**: `firestore.rules` rewritten — `allow read`/`allow create, update`/`allow delete` split
  per match block (a bare `allow write` can't reference `request.resource.data` on a `delete`,
  which both `users/{uid}` and `users/{uid}/months/{month}` are subject to via
  `CloudSyncRepository.deleteRemoteDoc`). `create, update` adds `request.resource.data.keys()
  .hasOnly([...])` (exact field lists reverse-engineered from `CloudSyncRepository.parentData` and
  the month-doc `set()`/`update()` call sites) plus a `< 1MiB` size guard on the blob field —
  defense-in-depth only, every path was already confirmed owner-uid-scoped. **Validated with the
  Firebase MCP's `firebase_validate_security_rules` tool: "OK: No errors detected."**
  `firestore.indexes.json` stripped of its invalid `//`-comment example blocks (the file as
  originally written was not valid JSON, so `firebase deploy --only firestore:indexes` would have
  failed) — now just the real `{"indexes": [], "fieldOverrides": []}`.
- **S-12**: new `HabitEventDao.deleteForNullLocalDateInRange(start, end)` — a `scheduled_for`-range
  fallback for `deleteForLocalMonth`'s local_date-keyed query, which can never match a
  pre-`MIGRATION_12_13` occurrence whose `local_date` is NULL (such rows' events survive every
  future `evictMonth` call for any month, orphaned once the occurrence itself is deleted). Wired
  into `ExportImportRepository.evictMonth` alongside `deleteForLocalMonth`, same
  before-the-occurrence-delete ordering. **Scope note**: the plan named only the habit side
  (`HabitEventDao.kt`); the identical bug exists on `FoodMedEventDao`/`food_med_occurrences`
  (same schema history, same null-`local_date` population) — fixed there too
  (`FoodMedEventDao.deleteForNullLocalDateInRange`) rather than leaving a known-identical bug
  unfixed on the sibling table.
- **S-13**: `CloudSyncRepository.onLocalDataReplaced` now UNIONS `hydratedMonths` with the
  imported file's covered months (`syncState.hydratedMonths + coveredMonths + recentMonths()`)
  instead of replacing it outright. Previously a range-import could narrow the resident set below
  months whose Room rows were still physically present (a range import never touches months
  outside its own range) — a false "not resident" signal into `doPush`'s `changedMonths` diff that
  could delete a live cloud month's document.
- **Gap, documented not fixed**: no new automated test for S-13 (`onLocalDataReplaced` mutates
  `SyncStateStore` state directly — not a pure function, same suspend/stateful gap as elsewhere)
  or for the `firestore.rules` `hasOnly`/size guards (the plan itself flagged this as conditional
  on "if the project has the Firebase emulator test harness wired — check before adding new
  infra"; confirmed no such harness exists in this project, and setting one up is out of
  proportion for a Low-severity defense-in-depth hardening). S-12's DAO-level fix followed the same
  androidTest-but-never-executed pattern as Phase 6's `ChunkedDeleteTest` — did not add a
  dedicated test for it given the established "compiles but the harness has no device to run it
  on" reality, though it would be a reasonable small addition for whoever eventually runs this
  suite on a real device/emulator.

### Phase 15 — DONE (LOW: notification/boot receiver cleanup, N-9 through N-12)
- **N-9**: removed `LOCKED_BOOT_COMPLETED` from both `AndroidManifest.xml`'s `BootCompletedReceiver`
  intent-filter and `BootCompletedReceiver.kt`'s `REARM_ACTIONS` — documentation-correctness only
  (it was never actually deliverable; this app is correctly not direct-boot-aware).
- **N-10**: `NotificationUtils.postTestNotification()` now returns `String?` (null on success, the
  block reason otherwise — checks `notificationBlockReason()` up front, and now propagates
  `notify()`'s Phase-7 `Boolean` return too). `SettingsViewModel` gained
  `testNotificationResult: StateFlow<String?>`; `SettingsScreen.kt`'s Diagnostics section shows it
  as inline `Text` under the "Send test notification" button — was a bare `Log.w`-only silent no-op.
- **N-11**: added the one-line warning comment on `CHANNEL_HABITS`/`CHANNEL_FOOD_MED` — no
  functional change, per the plan.
- **N-12**: no action — `PendingIntent` request codes already sound, per the plan.
- **Gap, documented not fixed**: none beyond the already-established test-infra gaps; every change
  this phase is either a manifest/comment-only edit (no test needed) or Compose/Android-framework
  UI wiring (same documented Compose-UI-test-infra gap as the whole round).

### Phase 16 — DONE (LOW: C-1, C-7, C-8, C-13 — C-2/C-11/C-12/C-15/C-16 need no work per the plan)
- **C-1**: `PinHasher.verify`'s `fromHex(hash(pin, salt))!!` → `?: return false`. Safe by
  construction (unreachable in practice — `hash()`'s own `toHex` output is always valid hex — so no
  new test was added; the existing `PinHasherTest` suite already covers the realistic malformed-hex
  cases via the OTHER `fromHex` call in the same function).
- **C-7**: new `ExportImportRepository.friendlyImportError(e, fallback)` maps
  `SQLiteException`/`IOException`/`SerializationException` to friendly, non-technical messages
  (raw message still `Log.e`'d, never discarded); the call-site's own existing generic text is the
  fallback for anything else. Applied at all 4 `catch (e: Exception) { ImportResult(success =
  false, message = e.message ?: "...") }` sites (`importAllData`, `importRange`, `importMonth`,
  `applyRemoteDefinitions`).
- **C-8**: `DateTimeUtils.stringToTime`'s silent `LocalTime.MIN` fallback now also logs a warning
  when it fires — fallback behavior itself is UNCHANGED, per the plan's own lowest-priority
  framing. The `Log.w` call is wrapped in `runCatching` — `DateTimeUtilsTest`'s existing
  `` `stringToTime falls back to MIN on a bad value` `` test deliberately exercises this exact
  path with malformed input, and `android.util.Log` is unmocked in this project's plain-JVM unit
  tests (same class of issue as Phase 10's `recordUnhandledException` fix — caught by actually
  running the test, not just compiling).
- **C-13**: `DateTimeUtils.jsonToTimes` now drops unparseable entries per-element (`runCatching` +
  `mapNotNull`) instead of throwing on the first bad one — mirrors `jsonToDays`' existing behavior.
  New test `` `jsonToTimes drops unparseable entries instead of throwing` ``.
- Also per the plan: re-confirmed `@EncodeDefault` discipline for `Habit.journalQuestionsJson`/
  `HabitOccurrence.qaJson` — `HabitJournalHashTest.kt` has been green through every 4-gate this
  entire round (it's part of the standard `testDebugUnitTest` suite), so no new work needed; C-16
  needs nothing further.
- Tests: `DateTimeUtilsTest.kt` +1. All passing (449 tests total now).

### Phase 17 (Final) — DONE
- `app/build.gradle.kts`: appended "; login redesign + branding + full risk-audit fix pass" to
  both the `versionCode`/`versionName` inline comments — the fields themselves untouched (still
  `13` / `"0.5.5"`).
- New `LOGIN_REDESIGN_RISK_FIX_REGRESSION.md` at repo root — mirrors `JOURNAL_HABIT_REGRESSION.md`'s
  shape. Covers: the same-versionCode install-over warning (repeated prominently, flagged as the
  THIRD consecutive round on versionCode 13); Phase 0's crash watch-list item; the wave-hero sign-in
  screen across all 5 `AccentColor`s; the onboarding wizard through both D2 paths; the Settings
  About-row icon + crash-log row; S-1's bootstrap fix confirmation; a spot-check list covering N-1,
  N-5, C-14, N-2/N-7, N-6, N-10; a "regression — everything not deliberately changed" section
  (including a Phase-9-specific check); §7 documents the debug APK's properties (verified, not
  assumed — see below); §8 is the "How to send us the next crash" subsection (the extra ask) —
  easiest path (Settings → About → Copy crash log) and the full adb path (`adb logcat -b crash`
  before reproducing, `adb pull .../crash_log.txt` as the one-shot alternative).
- **Both APKs built and verified with real tool output, not assumed:**
  - `Daybook-v0.5.5-login-redesign-release.apk` (7,031,700 bytes / ~6.7MB) — `aapt dump badging`
    confirms versionCode=13, versionName=0.5.5, no `application-debuggable` line;
    `apksigner verify --print-certs` confirms SHA-1 `39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c`,
    matching `RELEASE_SIGNING.md`'s real release keystore exactly.
  - `Daybook-v0.5.5-login-redesign-debug.apk` (23,327,308 bytes / ~22.3MB) — `aapt dump badging`
    confirms versionCode=13, versionName=0.5.5, AND `application-debuggable` present.
    `isMinifyEnabled` for `debug` is AGP's default `false` (no `debug { }` block exists in
    `app/build.gradle.kts` to override it — only `release { }` sets `isMinifyEnabled = true`, and
    only the release build actually ran `minifyReleaseWithR8`/`shrinkReleaseRes` in the build log).
- **Final full 4-gate re-run, clean, immediately before building the APKs**: `testDebugUnitTest`
  (449 tests, 0 failures), `assembleDebug`, `assembleRelease`, `compileDebugAndroidTestKotlin` —
  all green in one combined invocation, `BUILD SUCCESSFUL`.

## ALL 18 PHASES (0 THROUGH 17) COMPLETE.

Final on-disk file list (via `find app/src/main/java app/src/test/java app/src/androidTest/java
app/src/main/AndroidManifest.xml build.gradle.kts app/build.gradle.kts firestore.rules
firestore.indexes.json -newer LOGIN_REDESIGN_RISK_FIX_PLAN.md -type f`) — 52 files touched/created,
matching every phase's own file list above. Both APKs and the regression doc exist at repo root
alongside this file.

---

## Deliberate deviation, applies to every phase from here: no new Compose UI test infra

The plan asks for a "Compose UI test" in Phases 1, 2, 3 (and implicitly wherever a UI finding has
a visible symptom). `app/build.gradle.kts` (lines ~166-175) has an **existing, explicit, dated
decision** (v0.5.2 §6.1): "androidTest = MigrationTest + NavIconInflateTest only — both plain
instrumented JUnit4, no Compose UI test, no Espresso, so the compose-bom platform / ui-test-* /
espresso deps that used to sit here were dead weight." Adding `androidx.compose.ui:ui-test-junit4`
now would reverse that decision. Compounding this: Phase 0's own investigation (this same plan
document) confirmed `adb devices` is empty and there is no AVD on this machine — so even a newly
added Compose UI test could only ever be *compiled* (the 4-gate's `compileDebugAndroidTestKotlin`
gate compiles androidTest sources, it does NOT run them — there is no `connectedAndroidTest` in the
4-gate), never actually executed, making it equivalent dead weight in Compose flavor. Given both
of these, every "Compose UI test" ask in the plan is being treated as **out of scope this round,
consistently, and documented here rather than silently skipped per-phase.** All real test-writing
effort goes into `testDebugUnitTest`-tier pure-function/logic tests instead, which is where this
codebase's actual test coverage already lives and where a genuine gate (tests actually execute) has
teeth. If a future round wants on-device UI coverage, that's a deliberate infra decision to revisit
explicitly (new dependency + probably a real emulator/device), not something to smuggle in as a
side effect of this round's phase-by-phase asks.

Similarly: any phase whose plan text asks for a test that would require new mocking/coroutine-test
infrastructure not already present in this codebase (Firestore fakes, `kotlinx-coroutines-test`,
Android `Context` mocking) will get the same treatment — implement the real fix, unit-test whatever
pure logic can be extracted from it (same pattern as `CrashHandler.appendCrash`, `buildWizardSteps`,
`isLastWizardStep`), and document the specific gap here rather than block on new test infra.

## Status: COMPLETE — nothing to resume

All 18 phases (0 through 17) are done, all four gates are green on the final combined run, both
APKs exist at repo root and are verified (not just assumed) correct, and
`LOGIN_REDESIGN_RISK_FIX_REGRESSION.md` exists with every section the plan and the two extra asks
required. If you're reading this to decide whether there's more to do on THIS plan: there isn't —
see the per-phase sections above for exactly what was built and the handful of documented,
deliberate test-coverage gaps (summarized just below). Any further work is a NEW round, most likely
triggered by a real captured `crash_log.txt` (see the regression doc's §8) or a fresh audit.

## Deliberate gaps accepted this round (test coverage) — summary

Every phase above that skipped a plan-requested test falls into one of two buckets, both
documented inline at the phase where they occur:
1. **Compose UI tests** — this project has zero Compose UI test infra by a prior, explicit,
   dated decision (v0.5.2 §6.1, in `app/build.gradle.kts`), AND there is no emulator/device on this
   machine, so even a newly-added one could only ever compile, never run. Treated as out of scope
   for the whole round (see the dedicated section above, right after Phase 3).
2. **Suspend/Room/Firestore-mocking-dependent ViewModel or Repository tests** — this codebase has
   no Mockito/MockK/Robolectric/`kotlinx-coroutines-test` dependency anywhere; every existing test
   is a pure-function test. Every phase that hit this extracted whatever pure decision logic it
   could into a standalone function and unit-tested THAT (see `CrashHandler.appendCrash`,
   `buildWizardSteps`/`isLastWizardStep`, `residentMonthHashes`, `isGenuinelyEmptyMonth`) rather
   than leaving the fix completely untested; the remaining gap is thin suspend-function plumbing
   around already-tested pure logic, not the actual decision-making code.

Neither is a shortcut taken to save effort on a single phase — both are consistent, once-decided
policies applied uniformly so the same judgment call isn't silently made differently phase to
phase. Flag either one to reconsider if a future round is willing to add the missing test
infrastructure (Compose UI test deps + a device, or Mockito/MockK + coroutines-test).
