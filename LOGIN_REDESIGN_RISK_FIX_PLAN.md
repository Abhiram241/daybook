# Login Redesign + Risk-Audit Fix Plan

Repo: `/home/abhiram/Downloads/app-for-food` (package `com.daybook.app`, not a git repo — no undo). Build env: `JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk ./gradlew <task>`. Keystore CN=Daybook SHA1 `39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c` (`RELEASE_SIGNING.md`).

**4-gate every phase, in the FOREGROUND**: `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, `compileDebugAndroidTestKotlin`. Never build a `pgrep`-based wait loop — a prior round left zombie shells this way. Poll a PID file or just block in the foreground.

**Ground state**: versionCode **13**, versionName **"0.5.5"** — **frozen, do NOT bump.** Investigated whether a Play-Store-style constraint forces a bump: none applies — this is a sideloaded APK signed with a private keystore, never distributed through Play, so there is no store-side reason to increment. The **only** real consequence of staying on versionCode 13 (documented in `JOURNAL_HABIT_REGRESSION.md`, and it recurs here) is that `Daybook-v0.5.5-login-redesign-release.apk` will **not** install over the currently-installed `Daybook-v0.5.5-journal-habit-release.apk` — same versionCode, so `PackageInstaller` refuses the "update." Document this again, loudly, in the Final Phase's regression doc: **uninstall before installing this build.** Room DB is at **v17** (`app/schemas/com.daybook.app.data.local.AppDatabase/17.json`) and **nothing in this round requires a schema bump** — confirmed while scoping every phase below (see each phase's "DB impact" line); where a finding needs new persisted state (N-3), it goes into `SyncStateStore`'s existing `SharedPreferences`-backed store, not a new Room column, specifically to avoid an unnecessary v17→v18 migration this round.

This plan covers four combined efforts, in the order they must ship:
- **Item 1 (Phase 0)** — investigate and mitigate the "crashes after login" report.
- **Item 2 (Phase 1)** — reuse the app's existing launcher icon as a tasteful brand mark elsewhere in the UI.
- **Item 3 (Phases 2–3)** — redesign the sign-in screen; turn the post-login name-ask into a multi-step feature-tour wizard.
- **Item 4 (Phases 4–16)** — implement all 41 findings from `RISK_AUDIT_SYNC_NOTIFICATIONS_ERRORS.md`, Critical → High → Medium → Low.
- **Final Phase (17)** — `LOGIN_REDESIGN_RISK_FIX_REGRESSION.md` + signed `Daybook-v0.5.5-login-redesign-release.apk`.

---

## §0 — Open decisions (the parent resolves these via AskUserQuestion before implementation)

### D1 — The crash's root cause is NOT definitively confirmed. Ship the stopgap now; the real fix follows the first captured trace.
A rigorous static audit (every file in both the journal-habit round's first and second pass, plus `Migrations.kt`, `AppDatabase.kt`, `DataModel.kt`, `BackupModel.kt`, `AuthRepository.kt`, `CloudSyncRepository.kt`'s bootstrap/pull/listener paths, `ExportImportRepository.kt`'s import functions, the new journal JSON codec, and `proguard-rules.pro`) found **every hypothesis in the brief either ruled out with a citation or already defensively guarded** — see Phase 0 for the full ruled-out list. No emulator reproduction was possible: `adb devices` is empty, there is no AVD, and even after confirming `sdkmanager`/`avdmanager`/KVM access are all actually available on this machine (see Phase 0's "emulator feasibility" note), the app has **zero** debug/test bypass for its Google-Sign-In-only auth gate (`grep` for `BuildConfig.DEBUG` near any sign-in code returned nothing) — so a headless emulator without a real Google account cannot reach the "after login" state at all, making the emulator path structurally unable to reproduce this specific report regardless of setup effort.

Given that, Phase 0 ships a two-part mitigation instead of a single cited fix:
- **Phase 0a**: a local crash handler (`Thread.setDefaultUncaughtExceptionHandler`) that writes every future crash's full stack trace to internal storage, so the *next* occurrence is instantly diagnosable without needing a connected device at crash time.
- **Phase 0b**: closes the one theoretical uncaught-crash gap the audit actually found structurally plausible — `CloudSyncRepository.kt`'s two Firestore snapshot-listener callbacks (`attachSnapshotListeners`, lines 789–798 and 810–842) launch `scope.launch { ... }` blocks with **no exception guard at all**, unlike every other `scope.launch` in the same file (lines 1089, 1094, both already `runCatching`-wrapped). These listeners are wired up via `cloudSyncRepository.start()` in `DaybookApplication.onCreate()` (`DaybookApplication.kt:34`) and become live the moment auth state flips to `SignedIn` — i.e., **exactly "after login."** Every `Exception` path inside what they call (`applyRemoteParent`/`applyRemoteMonth` → `ExportImportRepository.applyRemoteDefinitions`/`importMonth`) is independently try/caught, so this gap is provably closed for `Exception` — it remains open only for an `Error` (e.g. `OutOfMemoryError` while gunzip-inflating/decoding a large or corrupt remote payload). This is the single most plausible **structural** candidate found — real, cloud-pulled, post-login data hitting decode paths that can't be exercised by any local/offline test — even though no concrete `Error`-throwing line was found by inspection.

**Decision needed**: is shipping Phase 0a+0b (without a proven root-cause fix) an acceptable Phase 0 for this round, on the understanding that the plan explicitly commits to revisiting the crash the moment `crash_log.txt` captures a real trace from the user's device? (Recommended: yes — the alternative is delaying the entire round indefinitely on an unreproducible bug, which the task's own framing anticipates via the "stopgap" allowance.)

### D2 — Does the onboarding wizard show for the auto-derived-name (silent) path too, or only when a name must be typed?
`MainActivity.kt`'s ONBOARDING branch (~lines 262–312) has two paths: (a) `deriveOnboardingName(...)` returns non-null and `!autoCompleteFailed` → `completeOnboarding(derived)` fires immediately inside a `LaunchedEffect`, showing nothing but a blank splash `Box` before falling through to `MainApp()`; (b) it returns null → the full `OnboardingScreen` (name field) renders, and `completeOnboarding(name)` fires once the user taps "Get started."

**Recommendation (to implement unless overridden): show the tour in BOTH cases.** Path (a) currently wastes the one moment a brand-new sign-in has the user's full attention on a blank screen, and it completes onboarding *before* any tour could run. Fixing this means restructuring path (a) so it no longer calls `completeOnboarding` inside the silent `LaunchedEffect`; instead it skips straight to **Step 2** of the wizard (the feature-tour steps, not Step 1's name field, since the name was already derived) and `completeOnboarding(derived)` fires only when the tour ends (Next-through-last-step or Skip) — exactly one call, same as today, just later. Path (b) is unchanged in shape: Step 1 (name) → Steps 2..N (tour) → `completeOnboarding(name)` once at the end.

Flagging this because it changes *when* a real Firestore/Room write happens for the auto-derived-name path (later than today, gated behind the user seeing at least one tour screen) — a genuine product/timing fork, not a style choice.

### D3 — `CloudSyncRepository.kt` bootstrap's Critical false-CONFLICT bug (S-1): which of three fixes?
`bootstrap()` (`CloudSyncRepository.kt:342–349`) compares `remoteMonthHashes` (every month ever pushed) against `localMonthHashes` (resident months only, from `exportBackup()`). The moment `evictStaleMonths` drops anything, every subsequent cold start sees "remote has months local doesn't" and calls it a permanent `CONFLICT`, wedging push/pull for that account forever (compounded by S-10's separate "only prompt once" bug).

1. **Filter remote to the resident set before comparing** — `remoteMonthHashes.filterKeys { it in residentSet }` where `residentSet = syncState.hydratedMonths + localMonthHashes.keys + MonthPartitioner.recentMonths()`, mirroring `doPush`'s existing `knownResident` pattern (~line 468). Smallest diff, reuses an established pattern.
2. **Re-fetch evicted months on demand during bootstrap** instead of filtering — treat "remote has months we don't hold" as a trigger to re-hydrate exactly those months (ATTACH_ONLY + background hydrate), never as CONFLICT. More conceptually correct (no data gap is ever silently treated as "equal") but requires extending `SyncLogic.decideBootstrap`'s contract to say "attach AND schedule N hydrations" — more surface area on the highest-blast-radius code path in the app.
3. **Store a permanently-unpruned cloud-hash cache app-wide** (effectively fixing S-7 at the same time) and diff against that instead of a live Firestore read. Doesn't independently fix the bug unless combined with option 1's filter — this is really "S-1 + S-7 together," not a distinct third approach.

**Recommendation: option 1.** Smallest surface area for a Critical, cold-start-path fix; reuses `doPush`'s already-proven `knownResident` idiom instead of inventing new bootstrap semantics. Flagging per the task's explicit instruction to surface Critical/High design forks here rather than silently picking one.

---

## §1 — Scope summary + DO-NOT-TOUCH invariants

- **Item 1** touches `DaybookApplication.kt` (new handler install) and `CloudSyncRepository.kt` (two `runCatching` wraps) only — no other file changes for the crash mitigation itself.
- **Item 2** touches `ui/settings/SettingsScreen.kt` (About row) and `ui/account/SignInGate.kt`/new sign-in composables (hero reuse) — no new drawable assets, reuses `mipmap-*/ic_launcher_monochrome.webp` and the existing adaptive-icon layers.
- **Item 3** redesigns `ui/account/SignInGate.kt`/`SignInContent.kt` (visual only — the Google-only auth mechanism is unchanged) and turns `ui/onboarding/OnboardingScreen.kt` + `OnboardingViewModel.kt` into a multi-step wizard, plus the `MainActivity.kt` ONBOARDING branch per D2.
- **Item 4** is scoped file-by-file per finding in Phases 4–16.

### DO-NOT-TOUCH invariants (every phase)
- Google-Sign-In-only auth mechanism, `AuthRepository.kt`'s Credential-Manager flow, and Firebase Auth wiring — Item 3 is a **visual** redesign only; no email/password/signup UI is added (explicitly ruled out by the brief).
- `StickySaveBar`'s existing pinned-bottom-button shell (`ui/components/StickySaveBar.kt`, 44 lines) — already solves "one-hand friendly, no `weight(1f, fill=false)`-inside-`verticalScroll` bug." Every new screen in Phases 2–3 reuses it unchanged, never reimplements bottom-pinning.
- `HabitJournalChatViewModel`/`HabitJournalEditViewModel` and all journal-habit-round scheduling/streak logic — untouched by this round except where a specific audit finding (C-4) names a scheduler function they share.
- Real keystore/signing (`RELEASE_SIGNING.md`) — untouched.
- Existing `@EncodeDefault(EncodeDefault.Mode.NEVER)` / hash-stability discipline — any newly-serialized optional backup field this round (none currently anticipated, but if a Medium/Low fix needs one — e.g. none of the 41 findings actually add a new `@Serializable` field except possibly nothing; confirmed none do) must follow the `HabitDef.streakStartedAt`/`IntakeLog.qaJson` precedent and ship a mirrored hash test.
- Any destructive-delete work must delete children before parents. Confirmed (again, independently, via `grep -n "@ForeignKey"` across `data/model/DataModel.kt`, zero hits): **no entity in this schema has a `@ForeignKey`/cascade** — `food_med_events → food_med_occurrences → food_med_tasks` and `habit_events → habit_occurrences → habits` both require explicit ordered deletes. No phase in this round performs a bulk cross-entity delete except S-12's narrow orphan-cleanup (Phase 14), which touches only `habit_events` (single table, no ordering issue).

---

## §2 — Phases

### Phase 0 — Crash investigation + stopgap mitigation (do first, always)

**Emulator feasibility (investigated, not pursued further)**: `sdkmanager`/`avdmanager` exist under `$ANDROID_HOME/cmdline-tools/latest/bin/`, network access to `dl.google.com` works, `/dev/kvm` is present and read/write-accessible to the current user (ACL-granted despite not being in the `kvm` group), and 12GB of free disk is available — a headless AVD (`google_apis;x86_64` or `arm64-v8a`, API 30) is technically installable and bootable (`-no-window -no-audio -gpu swiftshader_indirect`). **Not pursued**: the app has zero debug/test bypass for its Google-Sign-In-only gate (confirmed by reading `AuthRepository.kt`, `SignInGate.kt`, `SignInContent.kt`, `MainActivity.kt` — no `BuildConfig.DEBUG` branch anywhere near sign-in), and a headless emulator has no way to complete a real Google Sign-In without a provisioned test account, which is not available on this machine. Since the report is specifically "crashes **after** login," an emulator that can't get past login can't reproduce it — so time was spent on the static audit instead, which is exhaustive (see below) rather than on a setup that cannot reach the failure state regardless of effort.

**Static audit — hypotheses checked and ruled out (with citation)**, covering the full second-pass file list (`StreakCalculator.kt`, `DetailViewModel.kt`, `DetailScreen.kt`, `HabitJournalChatViewModel.kt`, `HabitJournalChatScreen.kt`, `HabitJournalEditViewModel.kt`, `HabitJournalEditScreen.kt`, `MainActivity.kt`, `HomeScreen.kt`, `HomeViewModel.kt`, `TimePickerComponents.kt`, `RoutinesViewModel.kt`, `RoutinesScreen.kt`) and first-pass list (`OccurrenceScheduler.kt`, `NotificationUtils.kt`, `AlarmReceiver.kt`, `ExportImportRepository.kt`, `AddHabitViewModel.kt`, `HabitForm.kt`), plus `Migrations.kt`, `AppDatabase.kt`, `DataModel.kt`, `BackupModel.kt`:

1. **`MIGRATION_16_17`** (`Migrations.kt:413-435`) — every referenced table/column exists at v16, delete order is children-before-parents, registered correctly in `di/DatabaseModule.kt:45`, `17.json` present. **Also structurally unreachable for most real users**: `JOURNAL_HABIT_REGRESSION.md` documents that the journal-habit APK's versionCode is unchanged from the prior release, so `PackageInstaller` **refuses** an in-place upgrade — the regression doc tells users to uninstall first. An uninstall clears app data, so most real devices hit this round via a **fresh install** (Room creates the DB directly at v17, no migration ever runs), not an upgrade. Deprioritized.
2. **`StreakCalculator.daySatisfies`/`calculateHabitStreaks`** (`StreakCalculator.kt:102-170`) — explicit `if (completionDates.isEmpty()) return StreakResult(0,0)` guard; no unguarded `.first()`/`.max()` on an empty list.
3. **`DetailViewModel.computeStats`/`loadHabitDetails`** (`DetailViewModel.kt:206-310, 404-419`) — division guarded by an `isEmpty()` check; `habit.streakStartedAt?.let{...} ?: 0` is null-safe for a never-started Ongoing habit.
4. **`RoutinesViewModel`/`RoutinesScreen`'s Task-C card + backdated-start picker** (`RoutinesViewModel.kt:267-268`, `RoutinesScreen.kt:198-371`, `TimePickerComponents.kt:185-206`) — `streakDays: Int?` is nullable everywhere it's read; `clampStreakStart` and the date picker's own `maxDate` cap both defend against an invalid state.
5. **`HomeViewModel.buildItems`** (`HomeViewModel.kt:442-628`) — `STREAK` habits are explicitly excluded from backfill synthesis (line 557); an empty habit/task/occurrence set falls through to `sortedBy{}` on an empty list, which is a no-op, not a crash.
6. **Compose layout** (`RoutinesScreen.kt`'s card redesign, `DetailScreen.kt`'s timeline, `HabitJournalChatScreen.kt`'s transcript) — no `LazyColumn`/`LazyRow` nested inside another scrollable with unbounded constraints; the one `Modifier.weight(1f).fillMaxWidth()` (`DetailScreen.kt:126`) is a direct, bounded `ColumnScope` child matching a pre-existing tab-content pattern.
7. **Hilt graph** — every new `@HiltViewModel` this round (`HabitJournalChatViewModel`, `HabitJournalEditViewModel`) follows the same constructor-injection shape as every existing ViewModel; no `init{}` block does eager/blocking Room or JSON work outside a `viewModelScope.launch{}`.
8. **Legacy `TaskType.JOURNAL` remap on import** (`ExportImportRepository.kt:295-296, 633-634, 764`) — confirmed **wired in** at both `importAllData` and `applyRemoteDefinitions` call sites (read, not just grepped), not dead code.
9. **`journalQuestionsToJson`/`jsonToJournalQuestions`** — blank-string short-circuit, `runCatching{}.getOrDefault(emptyList())` on decode failure.
10. **Sync `bootstrap()`** (`CloudSyncRepository.kt:264, 331-383`) — wrapped in `runCatching`, so even S-1's Critical logic bug (Phase 4) cannot crash the process; also requires `localEmpty == false`, which a fresh install never satisfies.
11. **ProGuard** (`app/proguard-rules.pro`) — `-dontobfuscate` set; existing broad `-keep` rules already cover `kotlinx.serialization`, Room entities, Hilt, enum-by-name, Tink. No new reflection surface this round escapes them.

**What could not be ruled in or out statically**: (a) Google Sign-In/Credential Manager/Play-Services version skew on the specific real device — invisible to source review; (b) an `Error` (not `Exception`) thrown inside `CloudSyncRepository`'s two unguarded snapshot-listener `scope.launch` bodies (see D1) — the single most plausible **structural** candidate, real and cloud-driven and exercised for the first time right after login, but no concrete `Error`-throwing statement was found by inspection.

**Phase 0a — local crash handler (new)**:
- New file `util/CrashHandler.kt`:
```kotlin
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val previous = Thread.getDefaultUncaughtExceptionHandler()
    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching {
            val f = File(context.filesDir, "crash_log.txt")
            val text = "${Instant.now()} :: ${Log.getStackTraceString(e)}\n\n"
            // Cap the file at ~256KB, keeping the most recent entries, so it never grows unbounded.
            val existing = if (f.exists() && f.length() < 256_000) f.readText() else ""
            f.writeText((text + existing).take(256_000))
        }
        previous?.uncaughtException(t, e)   // preserve the OS's own crash dialog/ANR behavior
    }
}
```
- Install from `DaybookApplication.onCreate()` (`DaybookApplication.kt:25`), **first line**, before `notificationUtils.createNotificationChannels()`:
```kotlin
Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
```
- Retrieval: add a row to Settings' existing "About Daybook" footer (`SettingsScreen.kt:282-302`, extended in Phase 1 anyway) — a small `TextLink("Copy crash log")` visible only when `filesDir/crash_log.txt` exists, that copies its content to the clipboard (`ClipboardManager`) so a user can paste it into an email/message without needing a connected device; document `adb pull /data/data/com.daybook.app/files/crash_log.txt` as the developer-side alternative in the regression doc.
- **Files touched**: new `util/CrashHandler.kt`, `DaybookApplication.kt`, `ui/settings/SettingsScreen.kt` (crash-log row), `ui/settings/SettingsViewModel.kt` (expose "has a crash log" + its content).
- **Tests**: a small unit test constructing `CrashHandler` with a fake `Context`/temp dir, throwing a test exception, and asserting the file is written and capped correctly.
- **DB impact**: none.

**Phase 0b — close the one structurally-plausible crash gap**:
- Wrap both currently-unguarded listener bodies in `CloudSyncRepository.kt` in `runCatching`, matching every other `scope.launch` in the file:
```kotlin
// line 793
scope.launch {
    runCatching {
        pushMutex.withLock {
            if (applyRemoteParent(snap)) { requestResync(); markIdle() }
        }
    }.onFailure { Log.e(TAG, "parent listener apply failed", it) }
}
// line 832
scope.launch {
    runCatching {
        pushMutex.withLock {
            if (applyRemoteMonth(doc.id, doc)) { requestResync(); markIdle() }
        }
    }.onFailure { Log.e(TAG, "month listener apply failed", it) }
}
```
Note: `runCatching` only catches `Throwable` that is a `Exception`-or-`Error` subtype reachable through normal JVM semantics — it does catch `Error` too (Kotlin's `runCatching` catches `Throwable`, not just `Exception`), so this closes the gap identified in D1 even for an `OutOfMemoryError`-class failure, at the cost of swallowing an OOM that arguably should still crash the process elsewhere; accepted tradeoff since an OOM inside a background sync listener silently corrupting sync state is worse than a logged-and-continued failure here (the user's data is still on the server; the next successful listener callback recovers it).
- **Files touched**: `data/sync/CloudSyncRepository.kt` (lines ~793, ~832 only).
- **Tests**: extend whatever listener-callback test harness exists (or add one) asserting a thrown `Exception`/`Error` inside `applyRemoteParent`/`applyRemoteMonth` no longer propagates out of the listener callback.
- **DB impact**: none.
- **4-gate**: all four for Phase 0a+0b together (small, low-risk, ship as one phase).

---

### Phase 1 — App-icon branding reuse

**Asset inventory** (confirmed): a full adaptive icon at `mipmap-anydpi-v26/ic_launcher.xml`/`ic_launcher_round.xml`, referencing per-density `.webp` layers (`ic_launcher_background`, `ic_launcher_foreground`, `ic_launcher_monochrome`) from `mipmap-hdpi` through `mipmap-xxxhdpi`. The foreground art is a metallic/silver ring built from three arcs (pill-capsule / fork / checkmark motifs) around a dark center dot — already rendered in greyscale, not accent-tinted, so it re-themes cleanly regardless of the user's chosen `AccentColor`. **`ic_launcher_monochrome.webp` already exists** (the Android-13+ themed-icon silhouette) — this is the ready-made mono mark; no new asset needs to be produced.

**The "Image not Icon" pattern already established**: `SignInContent.kt:72-79`'s `GoogleSignInButton` passes `Image(painterResource(...))` (not a Material `Icon`) into `PrimaryButton`'s `leadingIcon: @Composable (() -> Unit)?` slot specifically so `LocalContentColor` doesn't flatten a multicolor asset to a single tint. Every place below reuses this exact mechanism.

1. **Settings "About Daybook" footer** (`SettingsScreen.kt:282-302`, confirmed to already show "Daybook" + "Version X (Y)" centered, no icon today): prepend a `Image(painterResource(R.mipmap.ic_launcher_foreground), modifier = Modifier.size(48.dp))` above the text — the raw foreground layer (not the full adaptive icon, which would double-render the background circle awkwardly outside a launcher's masking) reads correctly as a standalone mark at this size. This is also where Phase 0a's crash-log row lives, so this file gets touched once for both.
2. **Sign-in hero** (Phase 2): the redesigned sign-in screen's hero area centers the same `ic_launcher_foreground` (or `ic_launcher_monochrome` if the metallic foreground reads poorly against the new wave background — decide visually during implementation, default to `ic_launcher_monochrome` for guaranteed contrast against an accent-colored backdrop) as a static `Image`, sized ~72-96dp, layered above the wave shape.
3. **Onboarding wizard header** (Phase 3): each tour step's header row gets a small (24-32dp) `Image(ic_launcher_monochrome)` beside the step's feature icon, purely as a returning brand touch — optional polish, skip if it reads as visually busy once Phase 3 is built; not load-bearing.
4. **Notification icons — deliberately NOT changed.** `NotificationUtils.kt`'s `smallIconFor` (lines 287-291) already uses purpose-built single-color vector drawables (`ic_notif_habit`, `ic_notif_med`, `ic_notif_food`, `ic_notification` fallback) — Android's notification small-icon slot is silhouette-only by OS convention (rendered as a flat white/tinted glyph in the status bar regardless of source colors), so swapping in the multicolor launcher mark here would be actively wrong, not an improvement. Leave `NotificationUtils.kt` untouched by this phase. A notification **large icon** (the bigger avatar-style image shown in the expanded notification) could show the monochrome mark, but none of the app's notifications currently set one and adding it is cosmetic-only with no clear user value (Daybook's notifications are personal reminders, not from another "sender" needing a face) — recommend skipping this to keep the phase tight; note it here so it isn't silently forgotten if a future round wants it.

**Files touched**: `ui/settings/SettingsScreen.kt` (About row + crash-log row from Phase 0a), `ui/account/SignInGate.kt`/`SignInContent.kt` (hero, built out fully in Phase 2 — this phase just confirms the asset choice), `ui/onboarding/OnboardingScreen.kt` (optional step-header mark, Phase 3).
**Tests**: a Compose UI test asserting the About row renders the icon `Image` node (not an `Icon` node, to guard against a future accidental swap back to the tinting variant).
**DB impact**: none.
**4-gate**: all four.

---

### Phase 2 — Sign-in screen redesign

**Current structure** (`SignInGate.kt`, full 88 lines, confirmed): `SignInGateScreen` = outer `Column(fillMaxSize + background(Bg) + statusBarsPadding)`; child 1 = `Column(weight(1f) + fillMaxWidth + verticalScroll + padding + spacedBy(sectionGap))` holding `BigHeadline("Sign in")` + a blurb `Text`; child 2 = `StickySaveBar { GoogleSignInButton(form, vm) }` (not weighted, so it's pinned to the bottom for free — no `Box`+align, no `weight(1f, fill=false)`-inside-`verticalScroll` bug). `BackHandler(enabled=true){}` swallows back. **This shape is correct and must be preserved** — only what's *inside* the two children changes.

**Reference mockup, adapted (not copied)**: the screenshot's two frames (a "Welcome" cover with a big wave illustration + "Continue" button, then a "Sign in" frame with a smaller wave + form) collapse into **one screen** here, because Daybook has exactly one action (Google Sign-In) — there is no second, distinct "now actually sign in" step to justify a second frame, and inventing one would add a tap for no functional reason. The single screen adopts frame 2's proportions (hero occupies roughly the top 35-45% of the screen, not frame 1's 60%, since this screen has to do both jobs — first impression AND the actual action).

**New structure**:
```
Column(fillMaxSize, background = Bg, statusBarsPadding) {
    WaveHero(modifier = Modifier.fillMaxWidth().heightIn(min = ..., max = 0.42f of screen height))
        // Canvas-drawn layered wave/blob shape in LocalAccent.current, dark Bg beneath/around it,
        // the app's ic_launcher_monochrome centered near the bottom of the hero, ~88dp.
    Column(weight(1f) + fillMaxWidth + verticalScroll + padding + spacedBy) {
        // Rounded-top "sheet" look: apply a clip(RoundedCornerShape(topStart=28dp, topEnd=28dp))
        // + background(DaybookColors.Surface) to this Column so it visually reads as a sheet
        // rising over the hero, matching the mockup's white-sheet-over-illustration structure
        // adapted to dark theme (Surface, not white).
        BigHeadline("Welcome to Daybook")
        Text(bodyBlurb)   // one line, e.g. "Sign in to sync your habits and reminders across devices."
    }
    StickySaveBar { GoogleSignInButton(form, vm) }   // UNCHANGED component, same call site shape
}
```
**`WaveHero` — new Composable** (`ui/components/WaveHero.kt` or inline in `SignInContent.kt` if kept small): a `Canvas` drawing 2-3 overlapping wavy/blob paths (cubic Bezier curves forming a soft horizontal wave band, the "topographic" look from the mockup reinterpreted as 2-3 stacked translucent layers) filled with `LocalAccent.current.color` at decreasing alpha (e.g. 100%/60%/30%) over the `DaybookColors.Bg` background — this is themed Compose drawing, not a static asset, so it re-renders correctly the instant the user changes their accent color in Settings (`AccentColor` enum: MINT `#2DD4BF` default, LAVENDER `#A78BFA`, CORAL `#FB7185`, SKY `#60A5FA`, AMBER `#FBBF24` — confirmed the full token set via `ui/theme/Tokens.kt`/`Accent.kt`). Concretely: three `Path`s each built from `moveTo` + a handful of `cubicTo`/`quadraticTo` calls tracing a horizontal wave, offset vertically from each other, `drawPath(path, brush = Brush.verticalGradient(listOf(accent.copy(alpha=X), accent.copy(alpha=Y))))`. Overlay `Image(painterResource(R.mipmap.ic_launcher_monochrome))` positioned via `Modifier.align(Alignment.BottomCenter).offset(y = -24.dp)` so the mark sits partly over the wave/sheet boundary, echoing the mockup's icon-at-the-seam placement.

**Files touched (new)**: `ui/components/WaveHero.kt`.
**Files touched (existing)**: `ui/account/SignInGate.kt` (restructure body per above), `ui/account/SignInContent.kt` (no change to `GoogleSignInButton` itself — it's reused as-is inside the same `StickySaveBar`).
**Tests**: a Compose UI test asserting (a) the Google Sign-In button is still present and still pinned to the bottom (measure its `top` against screen height across two different screen-size test configs, matching whatever the existing `SignInGate` test — if one exists — already asserts), (b) `WaveHero` renders without throwing across all 5 `AccentColor` values (a simple parametrized Compose test), (c) `BackHandler` still swallows back (unchanged, but re-assert since the file is being restructured).
**DB impact**: none.
**4-gate**: all four.

---

### Phase 3 — Onboarding feature-tour wizard

**Current shape** (`OnboardingViewModel.kt`, full 119 lines; `OnboardingScreen.kt`, full 139 lines; confirmed): a single-step name field + three static `FeatureCard`s (Smart reminders / One-tap logging / See your streaks) + `StickySaveBar { PrimaryButton("Get started", enabled = name.isNotBlank(), onClick = onComplete(name)) }`. `completeOnboarding(name)` (`OnboardingViewModel.kt:99-118`) is guarded by `shouldSkipCompleteOnboarding(isLoading, completed)` for fire-once safety, wraps the persist in try/catch, and is called from exactly one site today (`MainActivity.kt`'s `OnboardingScreen(onComplete = { name -> completeOnboarding(name) })`, ~line 305-309).

**New shape — a `WizardStep` sealed structure**, extending (not replacing) the existing screen:
```kotlin
sealed class WizardStep {
    data object NameAsk : WizardStep()                       // existing Step 1, unchanged behavior
    data class FeatureTip(val title: String, val body: String, val icon: ...) : WizardStep()
}
```
`OnboardingViewModel` gains `val steps: List<WizardStep>` (NameAsk first when a name must be typed — omitted per D2 when a name was already auto-derived — followed by N `FeatureTip` steps) and `var currentStep by mutableIntStateOf(0)`, plus `fun next()` (advances `currentStep`, calling `completeOnboarding(name)` only when `currentStep` is already the last index) and `fun skip()` (jumps straight to `completeOnboarding(name)` — same end-state, exactly one call, per the brief's explicit requirement).

**Tour content — pulled from `FEATURES.md` §2-§9** (confirmed shipped, not aspirational — `FEATURES.md` has no separate "planned" section, everything documented is live):
1. **Today** — the daily hub: greeting, hero completion count, week strip, progress cards.
2. **Habits — four types**: Individual, Batch, Ongoing (streaks), and Journal (the new chat-style check-in).
3. **Intake (Food/Med/Custom)** — structured reminders plus the red-flag diary.
4. **Reminders & notifications** — Skip/Snooze/Complete/Reply actions, quiet hours, re-nag on missed slots.
5. **Streaks** — Strict vs. Lenient modes, rest days.
6. **Personalisation** — accent color, font, which tabs show in the bottom nav.
7. **Offline-first + cloud sync** — Room is the source of truth; a month-partitioned Firestore mirror syncs across devices when signed in.
8. **App Lock** — PIN + biometric, for anyone who wants a lock screen on top of Google Sign-In.

(8 feature-tip steps is a lot — recommend condensing to 4-5 screens by grouping related bullets, e.g. combine 2+3 into "Track anything: habits, food, meds" and 4+5 into "Reminders that adapt to you," to keep the tour skimmable; exact grouping is an implementation-time editorial call, not a decision needing sign-off.)

**Screen**: each `FeatureTip` step reuses the *exact* `OnboardingScreen` shell (header + centered content + `StickySaveBar`) with the content area swapped for a single large `FeatureCard`-style block (icon + title + body) instead of the three-card grid, and the bottom bar now holds **two** controls instead of one: a `TextLink("Skip", onClick = viewModel::skip)` and `PrimaryButton("Next" / "Get started" on the last step, onClick = viewModel::next)` side by side (mirrors the existing `StickySaveBar` content slot, which already accepts an arbitrary `@Composable` — no new pinning primitive needed, per the DO-NOT-TOUCH invariant). A small step-dots indicator (`Row` of small circles, filled for completed/current) sits above the card, reusing whatever dot/indicator primitive already exists in the codebase if one does (check `ui/components/`), else a trivial new one.

**`MainActivity.kt` wiring (D2's resolution, recommended default)**:
- Path (a) auto-derived name: **remove** the `LaunchedEffect(derived){ completeOnboarding(derived) }` + blank splash `Box` (current lines ~301-303). Replace with rendering `OnboardingScreen` directly, but constructed with `steps = tourStepsOnly` (no `NameAsk`) and `nameForCompletion = derived`, so the first thing the user sees is the first tour tip, not a name field, and `completeOnboarding(derived)` fires only when `skip()`/the last `next()` runs.
- Path (b) typed name: unchanged entry point, `OnboardingScreen(steps = listOf(NameAsk) + tourSteps)`; `completeOnboarding(name)` still fires exactly once, now at the end of the whole wizard instead of right after the name field.

**Files touched (new)**: none — extends existing files rather than adding new ones (a `WizardStep` sealed class and a step-dots indicator can live inside `OnboardingViewModel.kt`/`OnboardingScreen.kt`).
**Files touched (existing)**: `ui/onboarding/OnboardingViewModel.kt`, `ui/onboarding/OnboardingScreen.kt`, `ui/MainActivity.kt` (ONBOARDING branch, both paths).
**Tests**: extend `OnboardingViewModel`'s existing test (if any — check for one before assuming) to cover: `skip()` calls `completeOnboarding` exactly once regardless of `currentStep`; `next()` on a non-last step does NOT call `completeOnboarding`; `next()` on the last step does; the auto-derived-name path's step list excludes `NameAsk`; `shouldSkipCompleteOnboarding`'s fire-once guard still holds across the now-longer step sequence (a double-tap on "Get started" during a slow persist must not double-fire).
**DB impact**: none — `completeOnboarding` still writes the same existing `app_settings` columns it always has, just later in the session for path (a).
**4-gate**: all four.

---

### Phase 4 — CRITICAL: S-1, `CloudSyncRepository.bootstrap()`'s false-permanent-CONFLICT bug

Per D3, implement **option 1**: filter `remoteMonthHashes` to the resident set before comparing.

`CloudSyncRepository.kt:342-349` (`bootstrap()`):
```kotlin
val residentSet = syncState.hydratedMonths + localMonthHashes.keys + MonthPartitioner.recentMonths()
val comparableRemote = remoteMonthHashes.filterKeys { it in residentSet }
val hashesEqual = comparableRemote == localMonthHashes   // was: remoteMonthHashes == localMonthHashes
```
Also (same phase, same root cause):
- Surface `conflictPaused`/`SyncStatus` on a **Today** banner (`ui/home/HomeScreen.kt` + `HomeViewModel.kt`), not just buried in Account/Sign-in — today a wedged sync is invisible outside Settings/Account.
- Add a regression test alongside the existing `BootstrapDecisionTest`: "a month is present remotely but absent from the local resident set ⇒ `ATTACH_ONLY`, never `CONFLICT`."

**Files touched**: `data/sync/CloudSyncRepository.kt` (`bootstrap`, ~342-380), `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt` (new banner), extended `BootstrapDecisionTest`.
**DB impact**: none.
**4-gate**: all four — this is the highest-blast-radius fix in the whole round (every signed-in user hits `bootstrap()` on every cold start), so treat `assembleRelease` + a manual re-read of the diff as mandatory before moving on.

---

### Phase 5 — HIGH: S-2 (account-deletion orphan) + S-4 (sign-out identity leak)

**S-2** — `AccountViewModel.kt:154-155` + `CloudSyncRepository.kt:1137-1154`: `deleteAccount()` calls `runCatching { cloudSync.deleteRemoteDoc() }` and discards the `Boolean`/`Result`, then unconditionally proceeds to `authRepository.deleteAccount()` — a failed remote delete leaves an orphaned Firestore doc under a UID nobody can ever sign back in as.
**Fix**: capture `deleteRemoteDoc()`'s result; if it did not succeed, set an error message ("Couldn't reach the cloud — connect and try again") and **do not** call `authRepository.deleteAccount()`. A full server-side backstop (a Cloud Functions `onDelete` Auth trigger) is out of scope — `firebase.json` has no Functions config today and adding one is a much larger, separate effort; note it as a follow-up only.

**S-4** — `CloudSyncRepository.kt:280-292` (`wipeLocalForSignOut`): the 8-table transactional wipe (verified against `AppDatabase.kt`'s 9-entity list: `habit_events`, `food_med_events`, `habit_occurrences`, `food_med_occurrences`, `habits`, `food_med_tasks`, `custom_categories`, `custom_prompts` — the 9th, `app_settings`, is deliberately excluded) never resets `user_name`, `profile_photo_path`, `onboarding_completed`. A second account signing in on a shared device sees the first account's name/photo and skips onboarding.
**Fix**: after the existing transaction (`CloudSyncRepository.kt:280-293`), reset those three `AppSettingsRepository`-backed columns to their defaults and delete the cached photo file via `ProfilePhotoStore` (which already has a `clear()` used today only from account-deletion — reuse it here too). Leave every device-scoped preference (accent, font, quiet hours, nav-tab selection) untouched — that's a deliberate, already-correct choice.

**Files touched**: `ui/account/AccountViewModel.kt` (`deleteAccount`), `data/sync/CloudSyncRepository.kt` (`wipeLocalForSignOut`), `data/AppSettingsRepository.kt`, `data/ProfilePhotoStore.kt`.
**Tests**: a test asserting `deleteAccount()` does NOT call `authRepository.deleteAccount()` when the remote delete fails; a test asserting `wipeLocalForSignOut` resets all three identity columns and calls `ProfilePhotoStore.clear()`.
**DB impact**: none (existing columns, just now reset at an additional call site).
**4-gate**: all four.

---

### Phase 6 — HIGH: S-3, SQLite 999-variable limit on Android 8-11

`ExportImportRepository.kt`'s `importMonth` (now at line 508, `habitKeep`/`taskKeep` built ~559) passes an `IN (:keep)` list to `HabitOccurrenceDao`/`FoodMedOccurrenceDao`'s `deletePendingByLocalMonthBefore`-style queries (calls at ~568/570) that can exceed SQLite's 999-bound-variable limit for a power user's heavy month, throwing on `minSdk 26` devices running SQLite < 3.32.
**Fix**: chunk `habitKeep`/`taskKeep` into ≤900-id batches, calling the DAO method once per chunk inside the existing transaction (no schema change, purely a call-site loop). Also, in `CloudSyncRepository.kt`'s `ensureMonthHydrated` (~984-997): when `applyRemoteMonth` returns `false` for any reason (not just an unreachable-server exception), remove the month from `hydrationAttempted` so a retry is actually possible on the next attempt rather than being permanently marked "tried."

**Files touched**: `data/ExportImportRepository.kt` (`importMonth`), `data/sync/CloudSyncRepository.kt` (`ensureMonthHydrated`).
**Tests**: a `MigrationTest`/DAO test with a synthetic 1200-row month asserting the chunked delete completes without a `SQLiteException`; a test asserting a failed hydration attempt is retryable.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 7 — HIGH: N-1 (quiet hours bypassed by re-nag) + N-2 (suppressed notification still recorded SHOWN)

**N-1** — `AlarmReceiver.kt`'s refire/re-nag arm (habit branch ~127-130, foodmed branch ~157-160) bypasses `OccurrenceScheduler`'s quiet-hours defer entirely — every other arm path in the app calls it, this one doesn't — and the refire notification lacks `setOnlyAlertOnce(true)`, so an unanswered reminder can buzz at full alert through quiet hours all night.
**Fix**: route the refire scheduling through a new `OccurrenceScheduler` entry point that applies `deferIfInsideQuietHours` (the receiver already has `OccurrenceScheduler` injected), and add `.setOnlyAlertOnce(true)` to the refire notification builder call in `NotificationUtils.kt`'s `showHabitNotification`/`showFoodMedNotification`.

**N-2** — `NotificationUtils.kt:385` (`notify()`): silently no-ops (`Log.w` only) when `notificationBlockReason() != null` (channel disabled, permission revoked, etc.), but its callers in `AlarmReceiver.kt`'s `fireHabit`/`fireFoodMed` insert a `SHOWN` event and arm the refire regardless — a reminder the user never saw becomes a phantom "SHOWN → SKIPPED" history entry, and the app stops re-arming it thinking it succeeded.
**Fix**: change `notify()`'s signature to return `Boolean`; in `fireHabit`/`fireFoodMed`, only insert the `SHOWN` event and arm the refire when it returned `true`. Add a persistent Today-screen banner (small, conditional row — can share plumbing with Phase 4's sync banner) when `notificationBlockReason() != null`, deep-linking to `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (channel-scoped).

**Files touched**: `util/alarm/AlarmReceiver.kt` (`fireHabit`/`fireFoodMed`), `data/OccurrenceScheduler.kt` (new quiet-hours-aware refire entry point), `util/notification/NotificationUtils.kt` (`notify`, `showHabitNotification`, `showFoodMedNotification`), `ui/home/HomeViewModel.kt` + `HomeScreen.kt` (banner).
**Tests**: a scheduler test asserting a refire arm inside quiet hours is deferred, not fired immediately; a test asserting `notify()` returning `false` prevents the `SHOWN` event/refire arm.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 8 — HIGH: C-3, corrupt month payload silently treated as a valid empty month

`MonthPartitioner.kt:102`'s `decodeDays` returns `emptyList()` (not `null`) on decode failure — unlike its sibling `decodeDefinitionsJson`, which correctly returns `null` and is checked. `CloudSyncRepository.applyRemoteMonth` has no guard against this: a truncated/corrupt gzip blob silently wipes every local `PENDING` row in that month (via `mergeMonth`'s "no incoming ids ⇒ delete pending" logic) and then stores the remote hash as if the merge succeeded — permanent, silent data loss with no retry path.
**Fix**: change `decodeDays`'s return type to `List<DayEntry>?`, returning `null` on `runCatching` failure (mirror `decodeDefinitionsJson` exactly, same file). In `applyRemoteMonth`, treat `null` as a failed apply — return `false`, do not store the hash, do not touch Room — matching `applyRemoteParent`'s existing null-guard (~885-892). Add the audit's suggested sanity check: refuse a month whose decoded day count is zero when the doc's stored `contentHash` doesn't match `ContentHash.ofDays(emptyList())` (i.e. a real empty month has a specific expected hash; anything else claiming zero days is corrupt, not empty).

**Files touched**: `data/sync/MonthPartitioner.kt` (`decodeDays`), `data/sync/CloudSyncRepository.kt` (`applyRemoteMonth`).
**Tests**: a `MonthPartitioner` test asserting corrupt input returns `null`, not `emptyList()`; a `CloudSyncRepository`/`applyRemoteMonth` test asserting a `null` decode leaves local `PENDING` rows and the stored hash untouched.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 9 — HIGH: C-4, journal/backfill saves report success when silently rejected

`JournalViewModel.kt:189-207` (`save()`) and `RespondViewModel.kt` (`resolve()`, ~187-192) both wrap their scheduler call in `runCatching { }` (result discarded) then unconditionally set `saved`/`done = true`. The callee (`OccurrenceScheduler.backfillFoodMed`/`logFoodMed`, and — new scope this round, since they were modeled on the exact same shape per `JOURNAL_HABIT_PLAN.md` Phase 2 — the habit-side `backfillHabitJournal`/`logHabitJournal`) early-returns silently (`Log.w` only, or a bare `return@withLock`) on `canBackfill` rejection or a not-yet-resident month, so the UI shows "Saved" over data that was actually dropped.
**Fix**: introduce a small sealed result type:
```kotlin
sealed class LogResult { data object Success : LogResult(); data class Rejected(val reason: String) : LogResult() }
```
Thread it back from `backfillFoodMed`/`logFoodMed`/`backfillHabitJournal`/`logHabitJournal` through `JournalViewModel.save()`, `RespondViewModel.resolve()`, and `HabitJournalChatViewModel`'s equivalent send/save path, to an explicit UI state ("Couldn't save — that month isn't loaded yet, connect and retry") instead of a bare `saved = true`. Model this on `SettingsViewModel.exportRange`'s existing `HydrateResult.Offline` pattern, which already does this correctly elsewhere in the codebase.

**Files touched**: `data/OccurrenceScheduler.kt` (`backfillFoodMed`, `logFoodMed`, `backfillHabitJournal`, `logHabitJournal`), `ui/journal/JournalViewModel.kt`, `ui/respond/RespondViewModel.kt`, `ui/journal/HabitJournalChatViewModel.kt`.
**Tests**: a test per call site asserting a `canBackfill`-rejected or non-resident-month save surfaces `Rejected`, not `Success`, and the corresponding ViewModel does not pop/show a false confirmation.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 10 — HIGH: C-9 (no `CoroutineExceptionHandler` anywhere) + C-18 (no crash reporter) — combined, mechanical, largest single lift

These two are implemented together deliberately: C-9's handler needs somewhere to *report to*, and C-18's Crashlytics needs call sites to report *from* — doing them separately would mean touching the same 13 ViewModel files twice.

**C-18 setup**: add the Crashlytics Gradle plugin + dependency (the Firebase project already exists per `firebase.json`/`.firebaserc`/the existing `google-services.json`, and `com.google.gms.google-services` is already applied — Crashlytics reuses the same config file, only needs enabling in the Firebase console if not already, plus the plugin/dependency locally):
```kotlin
// root build.gradle.kts plugins block
id("com.google.firebase.crashlytics") version "3.0.2" apply false
// app/build.gradle.kts plugins block
id("com.google.firebase.crashlytics")
// app/build.gradle.kts dependencies
implementation("com.google.firebase:firebase-crashlytics")
```

**C-9 fix**: add a shared extension, e.g. new `util/ViewModelExt.kt`:
```kotlin
fun ViewModel.safeLaunch(
    onError: (Throwable) -> Unit = { FirebaseCrashlytics.getInstance().recordException(it); Log.e("ViewModel", "unhandled", it) },
    block: suspend CoroutineScope.() -> Unit
) = viewModelScope.launch(CoroutineExceptionHandler { _, t -> onError(t) }, block = block)
```
Mechanically replace `viewModelScope.launch { ... }` with `safeLaunch { ... }` across all 13 ViewModel files (`AccountViewModel`, `AddHabitViewModel`, `DetailViewModel`, `FoodMedViewModel`, `AddFoodMedViewModel`, `HabitJournalChatViewModel`, `HabitJournalEditViewModel`, `HomeViewModel`, `JournalViewModel`, `LockViewModel`, `OnboardingViewModel`, `RespondViewModel`, `RoutinesViewModel`, `SettingsViewModel` — confirm the full 13 by `grep -rn "viewModelScope.launch" ui/` before starting, the audit counted ~92 call sites). For `HomeViewModel`'s unguarded `stateIn` pipelines specifically, add `.catch { FirebaseCrashlytics.getInstance().recordException(it) }` upstream of `.stateIn(...)` — `safeLaunch` doesn't cover a cold `Flow` pipeline the same way a `launch` block does. Also call `FirebaseCrashlytics.getInstance().recordException(t)` at every previously-swallowed `runCatching`-failure site named across this whole audit (S-2's `deleteRemoteDoc`, C-3's `decodeDays`, N-1/N-2's notification paths, the two `AlarmReceiver`/worker `catch(Throwable)` blocks, Phase 0b's two listener wraps) — this is what turns every "silently" in `RISK_AUDIT_SYNC_NOTIFICATIONS_ERRORS.md` into a dashboard entry going forward.

Given this phase is a mechanical multi-file pass, split its own execution into two 4-gate sub-passes if it proves too large for one sitting: **10a** = the `safeLaunch` extension + the plugin/dependency wiring + migrating the 4-5 highest-risk ViewModels (`HomeViewModel`, `RespondViewModel`, `JournalViewModel`, `AccountViewModel`, `SettingsViewModel`); **10b** = the remaining 8-9 ViewModels + the `recordException` calls at the named swallowed-error sites. Both sub-passes still ship inside this Phase 10 slot — this is a scoping note for the implementer, not a separate numbered phase.

**Files touched**: `build.gradle.kts` (root), `app/build.gradle.kts`, new `util/ViewModelExt.kt`, all 13 `ui/*/**ViewModel.kt` files, plus the specific swallowed-error sites named above across `data/sync/CloudSyncRepository.kt`, `data/sync/MonthPartitioner.kt`, `util/alarm/AlarmReceiver.kt`, `util/notification/NotificationUtils.kt`.
**Tests**: a test on `safeLaunch` asserting a thrown exception inside the block invokes `onError` and does not crash the test process; spot-check 2-3 migrated ViewModels' existing tests still pass unchanged (the migration should be behavior-preserving on the happy path by construction).
**DB impact**: none.
**4-gate**: all four, run once at the end of 10a+10b combined (or after each sub-pass if split, per the note above).

---

### Phase 11 — MEDIUM: sync-state bookkeeping (S-5 through S-10)

- **S-5** (`AccountViewModel.kt:174-185`, `wipeLocalData`): a weaker, non-transactional duplicate of the sign-out wipe, missing `customCategoryDao().deleteAll()`, `customPromptDao().deleteAll()`, `scheduler.cancelAllReminders()`, `syncState.reset()` (its `journal_questions` gap is now moot — table gone). **Fix**: delete this method; extract `wipeLocalForSignOut`'s (Phase 5-hardened) transactional body into a shared internal function both call.
- **S-6** (`CloudSyncRepository.kt:167-177, 313-327`): the `hydrating` echo guard doesn't actually suppress anything (an async `InvalidationTracker` race). **Fix**: delete the guard entirely, rely on the existing hash diff (which already carries the correctness weight) — do not build a "write generation counter," that's new complexity for a guard whose only real cost today is one wasted `exportBackup()` + hash per remote change. Update the stale comment claiming it works.
- **S-7** (`CloudSyncRepository.kt:658-680`): `monthHashes` grows unboundedly, never pruned on eviction — directly feeds S-1/Phase 4. **Fix**: prune `monthHashes` inside `evictStaleMonths` alongside `hydratedMonths` (do not keep it as a permanent "shape of the cloud" cache — Phase 4's fix already gets that correctly from a live read).
- **S-8** (`CloudSyncRepository.kt:810-813`, inside `scopedMonthsListener` — same function touched by Phase 0b, coordinate the two edits): the scoped-months listener's 30-item cap drops months in arbitrary `Set` iteration order. **Fix**: `.toList().sortedByDescending { it }.take(30)` — month keys are `"yyyy-MM"` strings, so lexicographic descending sort is chronological descending, guaranteeing recent months are never the ones dropped. Also cap `hydratedMonths` growth after a range export so it can't silently exceed 30 in the first place.
- **S-9** (`CloudSyncRepository.kt:980-1018`, `HomeViewModel.kt:320-339`): a month stuck "loading" while offline never retries. **Fix**: a short bounded retry-with-backoff inside `ensureMonthHydrated` (2-3 attempts, capped at a few seconds total — not indefinite, since this runs from a UI-triggered suspend call), or a re-trigger on connectivity-regained.
- **S-10** (`SyncLogic.kt:80-87`, `CloudSyncRepository.kt:351, 1057`): `conflictPromptShownForUid` permanently disables conflict detection after the first resolve — compounded badly by S-1's bug forcing an early spurious resolve for every user today. **Fix**: scope the "already prompted" flag to `(uid, remoteRevisionAtPromptTime)` and only suppress if the remote hasn't changed since, or add a multi-day expiry. Touches `SyncLogic.decideBootstrap`'s signature and its existing decision test.

**Files touched**: `ui/account/AccountViewModel.kt`, `data/sync/CloudSyncRepository.kt`, `data/sync/SyncLogic.kt`, `ui/home/HomeViewModel.kt`, plus `SyncLogic`'s existing test file.
**Tests**: one targeted test per bullet above (six total), several extending existing `BootstrapDecisionTest`/sync test files rather than new ones.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 12 — MEDIUM: notification/alarm robustness (N-3 through N-8)

- **N-3** (`OccurrenceScheduler.kt:335-345, 370-375`, re-verify exact lines at implementation time — heavily touched by the journal-habit round): a batch check-in snooze is silently discarded by the next `syncAll()` sweep. **Fix**: persist a `batch_snooze_until` timestamp in `SyncStateStore`'s existing `SharedPreferences` (NOT a new Room column — avoids a DB version bump this round), have `armBatchCheckInInternal` arm to `max(nextCheckin, snoozeUntil)`; take `syncMutex` in `snoozeBatchCheckIn` for symmetry with `completeAllBatchToday`.
- **N-4** (`AndroidManifest.xml`, `BootCompletedReceiver.kt:36-43`): no receiver for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+). **Fix**: add the action to the manifest's receiver intent-filter (guard `Build.VERSION.SDK_INT >= S`) and to `REARM_ACTIONS` in `BootCompletedReceiver.kt` — the existing `syncAll()` body already does the right thing once triggered.
- **N-5** (`util/work/WindowRefreshWorker.kt:49-59`, `OccurrenceScheduler.kt`'s `WINDOW_DAYS = 7`): an OEM battery restriction can silently drain the 7-day reminder window with no signal to the user. **Fix**: surface `ActivityManager.isBackgroundRestricted()`/`PowerManager.isIgnoringBatteryOptimizations()` in a Settings → Notifications health row (next to the existing `notificationBlockReason()` display), with a one-tap `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent.
- **N-6** (`util/alarm/NotificationActionReceiver.kt:73-77`): the inline-Reply action acknowledges before it actually writes. **Fix**: reorder — call `scheduler.logFoodMed(...)` first, post `postReplyAck` only after it returns successfully; on a `withTimeout(8_000)` failure, post a distinct "Couldn't save — tap to retry" notification instead of silently cancelling.
- **N-7** (`OccurrenceScheduler.kt:411-437, 716-717`, re-verify exact lines): `isFoodMedEdit(status, callerSaysEdit) = callerSaysEdit || status != PENDING` means a notification-Reply on an *already-answered* reminder silently overwrites it (the reply path always passes `isEdit=false`, but the generic predicate still routes it to the edit-in-place branch once status is non-`PENDING`). **Fix**: at the notification-reply call site specifically (`NotificationActionReceiver.kt`), check `status == PENDING` before calling `logFoodMed`; if already resolved, no-op with a "already logged — open the app to edit" notification instead. Do NOT change the general `isFoodMedEdit` predicate — it's correctly generic for every other (in-app) caller.
- **N-8** (`OccurrenceScheduler.kt:361-368`, re-verify): `completeAllBatchToday` mutates a whole day's occurrences with no transaction. **Fix**: wrap the loop in `db.withTransaction { }`; only cancel the batch notification after the transaction commits.

**Files touched**: `data/OccurrenceScheduler.kt`, `data/sync/SyncStateStore.kt` (new pref key for N-3), `app/src/main/AndroidManifest.xml`, `util/alarm/BootCompletedReceiver.kt`, `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` (N-5's health row), `util/alarm/NotificationActionReceiver.kt`.
**Tests**: one test per bullet (six total) — a scheduler test for N-3's persisted snooze surviving a sync sweep, a manifest/receiver-registration check for N-4, a scheduler test for N-7's PENDING-only reply guard, a transaction-wrapped assertion for N-8.
**DB impact**: none (N-3 uses `SharedPreferences`, not Room).
**4-gate**: all four.

---

### Phase 13 — MEDIUM: error-handling misc (C-5, C-14) — C-6/C-10/C-17 need no separate work here

- **C-5** (`CloudSyncRepository.kt:334`, near `bootstrap()`'s start): `runCatching { exportImport.exportBackup() }.getOrNull()` returning `null` on a genuine SQLite failure gets folded into the same CONFLICT/PUSH_LOCAL decision path as a real hash mismatch. **Fix**: if `exportBackup()` throws, set `SyncStatus.Error` and return from `bootstrap` without running `decideBootstrap` at all — retry on the next trigger, don't force a user-facing conflict choice over an internal error.
- **C-14** (`util/StorageUtils.kt:76-77`, `SettingsViewModel.kt:287-315`): a large import file is an uncatchable OOM crash. **Fix**: stat the `Uri` via `ContentResolver.query(uri, arrayOf(OpenableColumns.SIZE), ...)` before reading and refuse anything over a threshold (10MB — a legitimate multi-year backup is well under 1MB, per the Firestore 1MiB-per-month-doc bound cited elsewhere in the audit) with a clear "That file is too large to be a Daybook backup" message; switch `readText` to a streaming `Json.decodeFromStream`; catch `Throwable` (not just `Exception`) at the top of the import/export handlers so a slipped-through `OutOfMemoryError` still surfaces as a message.
- **C-6** is the same code as S-2 (already fixed in Phase 5) — no separate work.
- **C-10** is **moot** — `DaybookApplication.kt` (read in full, 36 lines) has no `appScope`/`ensureSeeded()` call at all; `JournalQuestionRepository` was deleted outright by the journal-habit round. Nothing to do.
- **C-17**'s two named exceptions are S-5 (Phase 11) and N-8 (Phase 12) — already tracked there, no separate work.

**Files touched**: `data/sync/CloudSyncRepository.kt` (`bootstrap`), `util/StorageUtils.kt`, `ui/settings/SettingsViewModel.kt`.
**Tests**: a `bootstrap` test asserting an `exportBackup()` throw yields `SyncStatus.Error`, not a conflict decision; a `StorageUtils`/import test asserting an oversized file is rejected with a friendly message before any parse is attempted.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 14 — LOW: sync/rules hardening (S-11, S-12, S-13)

- **S-11** (`firestore.rules`, `firestore.indexes.json`): no shape/size validation on writes; the indexes file has invalid `//` comments that would break `firebase deploy --only firestore:indexes`. **Fix**: add `request.resource.data.keys().hasOnly([...])` + a size guard to the rules (every read/write path is already confirmed owner-uid-scoped — this is defense-in-depth, not a confidentiality fix); strip the comments from `firestore.indexes.json` (currently `"indexes": []`, no index actually needed, but the file as written isn't deployable as-is).
- **S-12** (`ExportImportRepository.kt:693-707`, re-verify — file touched this round; `HabitEventDao.kt:63-64`): `evictMonth` orphans events for pre-`MIGRATION_12_13` rows where `local_date IS NULL`. **Fix**: add a `scheduled_for`-range fallback in the delete-for-local-month query for null-`local_date` rows, or a one-shot cleanup query for events with no matching occurrence. This single-table fix (only `habit_events`) has no parent/child ordering concern.
- **S-13** (`ExportImportRepository.kt:916` (`pushDeletesAllowed`), `CloudSyncRepository.kt:776-781`): `onLocalDataReplaced` narrows `hydratedMonths` on a range import, causing a false "not resident" for months that are actually present. **Fix**: union rather than replace (`hydratedMonths = hydratedMonths + covered + recentMonths()`) — simpler and strictly safer than evicting rows to match a narrowed bookkeeping set.

**Files touched**: `firestore.rules`, `firestore.indexes.json`, `data/local/HabitEventDao.kt`, `data/ExportImportRepository.kt`, `data/sync/CloudSyncRepository.kt`.
**Tests**: a `firestore.rules` unit test (if the project has the Firebase emulator test harness wired — check before adding new infra) for the `hasOnly` guard; a DAO test for S-12's orphan cleanup; a test asserting S-13's union behavior.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 15 — LOW: notification/boot receiver cleanup (N-9, N-10, N-11, N-12)

- **N-9** (`AndroidManifest.xml`, `BootCompletedReceiver.kt:38`): `LOCKED_BOOT_COMPLETED` is declared but undeliverable (the app is not, and per the audit correctly should not be, direct-boot-aware — Room lives in credential-encrypted storage). **Fix**: remove the action from the manifest intent-filter and from `REARM_ACTIONS` — documentation-correctness fix, no behavior change.
- **N-10** (`NotificationUtils.kt:144-155`): the Settings "send test notification" button silently no-ops when blocked. **Fix**: return the block reason to the caller and show it in the Settings snackbar instead of a bare `Log.w`.
- **N-11**: channel-ID versioning is already correct — add a one-line code comment on `CHANNEL_HABITS`/`CHANNEL_FOOD_MED` warning that bumping the `_v2` suffix again requires adding the old id to `LEGACY_CHANNEL_IDS` in the same change. No functional fix.
- **N-12**: `PendingIntent` request codes are already sound — no action.

**Files touched**: `app/src/main/AndroidManifest.xml`, `util/alarm/BootCompletedReceiver.kt`, `util/notification/NotificationUtils.kt`, `ui/settings/SettingsScreen.kt`/`SettingsViewModel.kt` (test-notification snackbar).
**Tests**: none required beyond the existing suite (these are comment/UX-message-only changes plus a manifest edit) — a manifest-parsing sanity check is sufficient for N-9.
**DB impact**: none.
**4-gate**: all four.

---

### Phase 16 — LOW: error-message and parsing hardening (C-1, C-7, C-8, C-13) — C-2/C-11/C-12/C-15/C-16 need no work

- **C-1** (`data/lock/PinHasher.kt:58`): the only `!!` in the entire tree, on the App Lock PIN-verify path. **Fix**: `fromHex(hash(pin, salt)) ?: return false` instead of `!!`. Safe by construction today; costs nothing to harden on the one screen a user cannot escape from if it ever threw.
- **C-7** (`ExportImportRepository.kt:361-363, 400-402, 579-581, 683-685`, re-verify — file touched this round, expect small shifts): raw exception text surfaces to the user on import/export failure. **Fix**: map known exception types (`SQLiteException`, `IOException`, `SerializationException`) to friendly strings at these four sites; log the raw message via `Log.e` instead.
- **C-8** (`util/DateTimeUtils.kt:167-173`): `stringToTime` silently defaults to `LocalTime.MIN` on parse failure. **Fix (minimal, per audit's own lowest-priority framing)**: add a warning log when the fallback fires; do not change the fallback behavior itself — swapping to a thrown exception risks a *new* regression in place of an old, harmless one.
- **C-13** (`util/DateTimeUtils.kt:44-49`): `jsonToTimes` throws (unlike its sibling `jsonToDays`) on malformed `times_json`, reachable from `HomeViewModel.buildItems`'s `stateIn` pipeline. **Fix**: wrap `LocalTime.parse` in `runCatching`, dropping unparseable entries per-element exactly like `jsonToDays` already does. Currently unreachable in practice (every writer produces valid values) but cheap, and pairs with Phase 10's broader `stateIn`-guarding work.
- **C-2, C-11, C-12, C-15, C-16**: verified sound / already correct by the audit itself (Hilt/`lateinit` safety, receiver concurrency, `CoroutineWorker` error handling, JSON/backup resilience, `@EncodeDefault` discipline) — **no code change**. One note for whoever implements this phase: independently re-confirm `@EncodeDefault` discipline against the journal-habit round's two new fields — `Habit.journalQuestionsJson` (non-null, default `""`) and `HabitOccurrence.qaJson` (nullable) — the round's own plan already reasoned through this (Phase 7 of `JOURNAL_HABIT_PLAN.md`) and shipped `HabitJournalHashTest.kt`, so this is a re-confirmation, not new work; if that test is green, C-16 needs nothing further.

**Files touched**: `data/lock/PinHasher.kt`, `data/ExportImportRepository.kt`, `util/DateTimeUtils.kt`.
**Tests**: a `PinHasher` test for a hypothetical malformed-hex input (previously would have thrown, now returns `false`); a `jsonToTimes` test asserting malformed entries are dropped, not thrown.
**DB impact**: none.
**4-gate**: all four.

---

### Final Phase (17) — Regression doc + signed release APK

- **Do not touch `versionCode`/`versionName`** (stays `13`/`"0.5.5"`, per the ground-state header). Update only the inline comment at `app/build.gradle.kts:31-32` to append this round's summary (currently ends "...; journal-as-habit + button fix + ongoing-habit UI" — append "; login redesign + branding + full risk-audit fix pass").
- Write `LOGIN_REDESIGN_RISK_FIX_REGRESSION.md` at repo root, covering (mirroring `JOURNAL_HABIT_REGRESSION.md`'s shape): (1) the same-versionCode install-over warning, **repeated prominently** since this is the third consecutive round on versionCode 13; (2) a manual watch-list item for the crash: "install fresh, sign in, use the app normally for a full day — if it crashes, immediately retrieve `crash_log.txt` via the new Settings row or `adb pull` and attach it to the next round's kickoff," since Phase 0 could not produce a proven fix, only a stopgap; (3) the wave-hero sign-in screen across all 5 `AccentColor` values; (4) the onboarding wizard through both the auto-derived-name and typed-name paths (D2), confirming `completeOnboarding` fires exactly once each way; (5) the Settings About-row icon + crash-log row; (6) S-1's bootstrap fix — confirm on a real account that has previously evicted months that cold-start no longer wedges into `CONFLICT`; (7) a spot-check of a handful of the Medium/Low fixes most likely to have a visible on-device symptom (N-1's quiet-hours-respecting re-nag, N-5's battery-restriction Settings row, C-14's oversized-import rejection message).
- Build `Daybook-v0.5.5-login-redesign-release.apk` at repo root using the existing release signing config, once every phase's 4-gate is green.

**Files touched**: `app/build.gradle.kts` (comment only), new `LOGIN_REDESIGN_RISK_FIX_REGRESSION.md`, new `Daybook-v0.5.5-login-redesign-release.apk`.
**4-gate**: a final full run across the whole diff before signing, in addition to each phase's own gate.

---

## §3 — Risk register (round-level, beyond each phase's own notes)

1. **The crash may still be unfixed at ship time.** Phase 0 is explicitly a mitigation, not a cure — flagged in D1 and repeated in the Final Phase's regression doc. Do not let this round's size create pressure to claim the crash is "fixed" without a captured trace confirming it.
2. **Phase 4 (S-1) is the single highest-blast-radius change in the entire round** — every signed-in user's cold-start bootstrap path. Give it its own careful re-read pass, not just a green 4-gate, before moving to Phase 5.
3. **Phase 10's mechanical `safeLaunch` migration across 13 files is the most likely place to introduce a copy-paste error** (a mismatched brace, a dropped `viewModelScope` receiver) purely from volume — `compileDebugAndroidTestKotlin`/`testDebugUnitTest` will catch most of this, but budget real review time proportional to the file count, not just the conceptual simplicity of the change.
4. **Phases 6, 7, 8, 11, 12 all touch `CloudSyncRepository.kt`, `OccurrenceScheduler.kt`, and `AlarmReceiver.kt` repeatedly** (as does Phase 0b and Phase 4) — implement them in the given order (they're already sequenced to avoid overlapping the same line ranges in the same sitting) and re-diff each of these three files once after Phase 12 to confirm no earlier phase's edit was accidentally clobbered by a later one working from a stale mental model of the file.
5. **No phase in this round bumps the Room schema.** If any implementer discovers mid-phase that a fix genuinely needs a new persisted column (not currently anticipated for any of the 41 findings or the branding/onboarding work), stop and re-scope that phase explicitly as a schema-bump phase with its own `MigrationTest`, rather than smuggling a column into an otherwise no-migration round.

---

## §4 — File-touch index

**New files**: `util/CrashHandler.kt`, `ui/components/WaveHero.kt`, `util/ViewModelExt.kt`, `LOGIN_REDESIGN_RISK_FIX_REGRESSION.md`, `Daybook-v0.5.5-login-redesign-release.apk`.

**Modified — Item 1 (crash)**: `DaybookApplication.kt`, `data/sync/CloudSyncRepository.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`.

**Modified — Item 2 (branding)**: `ui/settings/SettingsScreen.kt` (shared with Item 1's crash-log row).

**Modified — Item 3 (sign-in + wizard)**: `ui/account/SignInGate.kt`, `ui/account/SignInContent.kt`, `ui/onboarding/OnboardingViewModel.kt`, `ui/onboarding/OnboardingScreen.kt`, `ui/MainActivity.kt`.

**Modified — Item 4 (audit fixes)**: `data/sync/CloudSyncRepository.kt`, `data/sync/SyncLogic.kt`, `data/sync/MonthPartitioner.kt`, `data/sync/SyncStateStore.kt`, `data/OccurrenceScheduler.kt`, `data/ExportImportRepository.kt`, `data/AppSettingsRepository.kt`, `data/ProfilePhotoStore.kt`, `data/lock/PinHasher.kt`, `data/local/HabitEventDao.kt`, `util/StorageUtils.kt`, `util/DateTimeUtils.kt`, `util/alarm/AlarmReceiver.kt`, `util/alarm/BootCompletedReceiver.kt`, `util/alarm/NotificationActionReceiver.kt`, `util/notification/NotificationUtils.kt`, `ui/account/AccountViewModel.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `ui/journal/JournalViewModel.kt`, `ui/journal/HabitJournalChatViewModel.kt`, `ui/respond/RespondViewModel.kt`, all remaining `ui/*/**ViewModel.kt` files (Phase 10), `app/src/main/AndroidManifest.xml`, `firestore.rules`, `firestore.indexes.json`, `build.gradle.kts` (root), `app/build.gradle.kts`.

**Modified — version/docs**: `app/build.gradle.kts` (comment only, not the version fields).
