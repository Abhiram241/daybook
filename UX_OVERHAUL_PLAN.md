# Daybook UX Overhaul — Implementation Plan (LOCKED)

**Status:** APPROVED. The user has reviewed and locked every decision. This plan is ready for a fresh implementer to execute with no further input. There is no open-questions section — see **Decisions (locked)** below.

**Baseline:** branch `main`, versionCode 22 / versionName 0.5.6, Room DB **v19**, compose-bom 2024.12.01, minSdk 26 / targetSdk 34. Dark-theme-only today. This plan sits on top of the CURRENT working tree (the build-21/22 keyboard/font batch + the finished `collectAsStateWithLifecycle` battery sweep + the `preferredRefreshRate` change in `MainActivity.kt`). Do **not** revert or replan any of that; the two untracked docs `BATTERY_BUG_AUDIT.md` / `BATTERY_FIX_PLAN.md` stay untouched.

**Scope:** 7 work items (item 6 "Personalisation" was dropped by the user). One additive Room migration, `MIGRATION_19_20`, adding a single column.

---

## Decisions (locked)

1. **Onboarding (item 1):** proceed with the teaching-step rewrite. Final step ends with a **skippable** "Create your first habit" `TextLink` — it does **not** force-open Add Habit. The permission-primer step **explains** notifications / exact alarms / battery with **optional** inline "Allow" buttons; the existing post-onboarding `MainActivity` permission requests stay as the backstop. The tour shows **once only** — no per-version re-show, **no `onboarding_tour_version` column**. A "Replay the tour" entry lives in the new **About & help** screen.
2. **Alarm/notification permission dialog contrast (item 2):** proceed. Add `textContentColor` (full-contrast primary) to `DaybookAlertDialog`, `tonalElevation = 0.dp`, `surfaceTint = Color.Transparent` in both schemes, fuller body copy on the two `MainActivity` dialogs. Must be theme-aware for the new light theme.
3. **Batch notifications (item 3) — NARROW BUGFIX ONLY.** A `HabitType.BATCH` habit currently fires **both** its one consolidated notification (`showBatchHabitNotification`) **and** separate per-item notifications. Fix: find the path by which BATCH occurrences also post per-item notifications and **suppress the per-item post for `HabitType.BATCH` only**. `INDIVIDUAL` / `JOURNAL` / Intake notification behaviour is left **completely untouched**. **No** Android notification group/summary work (the earlier Options A/B/C are dropped). Add a focused JUnit test on the pure predicate that decides "post an individual notification for this occurrence".
4. **Light/dark theme (item 4):** proceed. Toggle offers **Dark / Light / System**; **default = Dark** for every existing and fresh install (nothing changes until the user opts in). Dark palette byte-identical to today. Use a **`@Composable`-getter shim** over `LocalDaybookColors` for the 332 refs (minimise call-site churn). Mirror `theme_mode` to `SharedPreferences` and read it **synchronously before `setContent`** — zero theme flash. Keep US spelling `color`. Per-theme `AccentColor` + `CardTint` variants per the tables below (dark values unchanged).
5. **Settings reorg (item 5):** proceed fully — renames, moves, sentence case, new **About & help** hub row + screen, fold the Navigation sub-screen into **Appearance → Layout**.
6. **Personalisation (item 6): DROPPED ENTIRELY.** None of 6.1–6.8. Item removed from this plan. No personalisation columns.
7. **Today + calendar (item 7):** proceed. Group the Today reminders list into labelled sections **Overdue / Now / Later / Done** with counts, via a pure JUnit-testable `groupHomeItems` transform. Add a **"Group by type"** option to the **existing** filter sheet, backed by **session state / `SharedPreferences`** — **not** an `app_settings` column. Lighten `ReminderCard` meta rows. Reserve height for the "Back to today" link so the calendar doesn't jump. Detailed visual styling (spacing/weight/colour balance) is **pending user screenshots**; the implementer does a best-effort code restyle and the user refines on device.
8. **Bloat (item 8):** proceed as proposed. **Hide UI / keep column inert** for 8.1, 8.2, 8.3, 8.11. **Move (not delete)** 8.4 + 8.5 into About & help. **Fold** 8.6 into Appearance → Layout. **8.7 is OUT OF SCOPE** — the per-habit / per-intake `prompt_message` + `motivation` form fields are left **exactly as they are today** (not demoted, not moved, not restyled). **Do NOT delete** the retired Intake-Journal files (8.10) — a live nav route still reaches them (confirmed by grep; see item 8). **No column drops, no table-rebuild migration.** Keep 8.8 (food diary), app lock, quiet hours, export/import, sync.
9. **Migration:** exactly **one** additive `MIGRATION_19_20` on `app_settings` adding **one** column, `theme_mode TEXT NOT NULL DEFAULT 'DARK'`. `AppDatabase.version` 19 → 20, commit `20.json`. Additive only, no table rebuild, not in `BackupModel`, not in sync / `ContentHash`.

---

## Do NOT (hard constraints for the implementer)

- **No `git commit` / `git push`.** No branch-merge, no tag.
- **No Firebase App Distribution push** (`appDistributionUploadRelease` etc.). No other Firebase writes.
- **Every change must be revertable.** No irreversible data operations.
- **Preserve all existing user data** — local Room *and* Firestore documents. Nothing may delete, rewrite in place, or re-key stored rows.
- **The existing DARK theme and all current layouts must stay visually recognisable.** "Do not mess up the current UI." All changes are additive / corrective, never a redesign. Dark palette values are byte-identical to today.
- **Do not change** `versionCode` / `versionName`, any dependency version, `compose-bom`, AGP/Gradle, or `minSdk` / `compileSdk` / `targetSdk`.
- **Schema:** the ONLY permitted schema change is `MIGRATION_19_20` (one `ALTER TABLE app_settings ADD COLUMN theme_mode ...`). **STOP and ask** before any edit that would touch: another migration, any table rebuild, a column drop, the sync payload, `data/sync/ContentHash.kt`, or `data/backup/BackupModel.kt`.
- **STOP and ask** if deleting the retired Intake-Journal files turns out to be reachable from any live route or call site (it currently is — leave them).
- No new runtime permissions in `AndroidManifest.xml`. No new notification channels.

---

## Deliverable & verification gate

**Deliverable:** a locally-built, **real-signed release APK** at `app/build/outputs/apk/release/app-release.apk` (signed via `keystore.properties` + `app/daybook-release.jks` — both gitignored, already present on this machine).

**Verification gate — all green, from a clean state:**
```
export JAVA_HOME=/home/abhiram/jdk/jdk-17.0.11+9 ANDROID_HOME=/home/abhiram/android-sdk
./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin
```
- `assembleRelease` must be R8-clean and `lintVitalRelease`-clean.
- The ~446 existing JVM unit tests must all keep passing; new tests listed per item must pass.
- `app/schemas/com.daybook.app.data.local.AppDatabase/20.json` must be regenerated by the build and committed as source.
- No device/emulator on this machine: instrumented `MigrationTest` is compiled (`compileDebugAndroidTestKotlin`) but not run here; write it anyway following the existing pattern.

---

## Cross-cutting fact base (read before touching any item)

### Theme architecture as it stands
- `ui/theme/Tokens.kt` → `object DaybookColors` — a data-less `object` with 13 dark colour `val`s (`Bg`, `Surface`, `SurfaceElevated`, `Outline`, `Hairline`, `Border`, `TextPrimary`, `TextMuted`, `TextFaint`, `Success`, `Warning`, `Danger`, `OnSolid`). **332 static references across 40 files**, every one `DaybookColors.X` property access (not a CompositionLocal).
- `ui/theme/Tokens.kt` → `object CardTints` — `CardTint` data class (`fill`, `fillRaised`, `onFill`, `onFillMuted`, `onFillFaint`, `accent`) × 6 pastels (`Lavender`, `Peach`, `Mint`, `Butter`, `SlateBlue`, `Rose`) + `Neutral`. Hardcoded dark. ~15 files read `CardTints.*`; `.onFill` / `.onFillMuted` / `.onFillFaint` / `.accent` are read on card content widely. Helpers `byIndex` / `byId` / `resolve(overrideName, positionalIndex)` are pure.
- `ui/theme/Accent.kt` → `enum class AccentColor(storageKey, color: Color)` — 5 fixed colours (`MINT #2DD4BF`, `LAVENDER #A78BFA`, `CORAL #FB7185`, `SKY #60A5FA`, `AMBER #FBBF24`), `DEFAULT = LAVENDER`, one value per accent regardless of theme. `LocalAccent = staticCompositionLocalOf { AccentColor.DEFAULT.color }`. `LocalReduceMotion` alongside it.
- `ui/theme/Theme.kt` → `DarkScheme = darkColorScheme(...)` mapping `DaybookColors` onto M3. `DaybookTheme(accent, fontChoice, reduceMotion, content)` does `remember(accent) { DarkScheme.copy(primary = accent.color, secondary = ..., tertiary = ...) }`, provides `LocalAccent` + `LocalReduceMotion`, wraps `MaterialTheme`. **No `isSystemInDarkTheme` anywhere in the codebase.** `surfaceTint` is never set → M3's default light-purple tint leaks onto any tonally-elevated M3 surface (`AlertDialog` at its default 6dp).
- `DaybookTheme` is invoked once in `MainActivity.setContent`, fed by `onboardingViewModel.accentColor` / `.fontChoice` / `.reduceMotion` StateFlows, each `stateIn` with a non-null initial (first frame renders on defaults, then flips if the stored value differs).

### Data / sync facts that de-risk the migration
- `data/backup/BackupModel.kt` carries **no `AppSettings`, no `userName`, no accent/theme** — only `meta` + `definitions` (HabitDef / IntakeReminderDef / customCategories / customPrompts) + `days`. Every `app_settings` column added since v16 (the "customization round") is therefore device-local, not synced, not in any export. `theme_mode` inherits that for free.
- `AppSettings` writes are per-column `UPDATE ... WHERE id = 1` (`AppSettingsDao`) — REV-25/REV-04 discipline. A new setting = one appended `@ColumnInfo` field + one `@Query` update fn + one `set*` in `AppSettingsRepository` + one `col(...)` flow + setter in the ViewModel(s) that need it.
- Migrations: `data/local/Migrations.kt` (`MIGRATION_2_3` … `MIGRATION_18_19`), registered in `di/DatabaseModule.kt`'s `addMigrations(...)`, with `fallbackToDestructiveMigrationFrom(1)` + `fallbackToDestructiveMigrationOnDowngrade()`. Exported schema JSON in `app/schemas/com.daybook.app.data.local.AppDatabase/` (`3.json` … `19.json`), committed source. A build regenerates the next JSON.
- `app/src/androidTest/.../data/local/MigrationTest.kt` — pattern: `helper.createDatabase(TEST_DB, N).close()` then `helper.runMigrationsAndValidate(TEST_DB, N+1, true, MIGRATION_N_Np1)`, plus a `preservesRows` variant where data matters.

### Notification / reminder facts
- `data/model/DataModel.kt` → `enum class HabitType { INDIVIDUAL, BATCH, STREAK, JOURNAL }`.
  - `armsOwnAlarm(type)` = `type == INDIVIDUAL || type == JOURNAL` → one `AlarmManager` alarm + one notification **per occurrence** (`AlarmReceiver.fireHabit` → `NotificationUtils.showHabitNotification` / `showHabitJournalNotification`).
  - `BATCH` is meant to have **zero per-time alarms** — surfaced only by the single app-wide check-in: `OccurrenceScheduler.armBatchCheckIn` at `AppSettings.habitCheckinTime` → `AlarmReceiver.fireBatch` → `NotificationUtils.showBatchHabitNotification(count, titles)` (one notification, `BigTextStyle`, up to 4 titles + "+N more", Snooze / Done). `unresolvedBatch(...)` / `unresolvedBatchOccurrencesFor(...)` are the selectors; `BatchCheckInTest` covers them.
  - `STREAK` ("Ongoing"): zero occurrences / alarms / notifications.
  - Intake (`FoodMedTask`): one alarm + one notification per occurrence (`showFoodMedNotification`).
- `OccurrenceScheduler.effectiveTimesJson(habit, checkinTime)` returns `checkinTime` for `BATCH` and `habit.timesJson` otherwise — i.e. **BATCH habits still get occurrence rows generated** (at the check-in time), so their streak / Today card work like an Individual's. See item 3 for why this is the likely source of the double-notification.
- Channels `habits_v2` / `food_med_v2`, both `IMPORTANCE_HIGH` (`NotificationUtils.createNotificationChannels`). No new channels in this overhaul.

---

## ITEM 1 — Real first-run onboarding

### What's there now (file evidence)
- `ui/onboarding/OnboardingScreen.kt` (145 lines) + `ui/onboarding/OnboardingViewModel.kt` (233 lines).
- Flow = `buildWizardSteps(hasAutoDerivedName)`: an **optional** `WizardStep.NameAsk` (only when no name derivable from the Google profile) then a fixed 5-item `OnboardingTourSteps` list of `WizardStep.FeatureTip` (title + body + icon + `CardTint`). UI: `StepDots`, one `SoftCard` per tip, `StickySaveBar` with `Skip` + `Next` / `Get started`.
- The 5 tips are pure marketing copy. Nothing is interactive, nothing shows the real UI, nothing is created, and none of these are explained: creating a habit / intake reminder / journal; what Skip/Snooze/Complete/Reply do and that you can act from the shade; the Today list + week-strip↔month-calendar; past-day backfill; app lock; Google sync + the conflict/restore dialog; permissions and why they matter.
- `MainActivity` ONBOARDING branch: `deriveOnboardingName(...)` → `onboardingViewModel.configure(derived)` → `OnboardingScreen`. `completeOnboarding` fires once (Skip, or Next on the last step) and sets `AppSettings.onboardingCompleted = true`.

### Proposed change
Rebuild the tour as a **teaching** flow inside the existing `WizardStep` / `buildWizardSteps` / `StickySaveBar` shell — keep the shell, do not reimplement pinning or scaffolding.

New `WizardStep` subtypes (sealed class in `OnboardingViewModel.kt`):
- `WizardStep.NameAsk` — unchanged, same trigger.
- `WizardStep.Teach(title, body, illustration: TeachIllustration, tint)` — replaces `FeatureTip`. `body` = 2–4 short sentences that explain one loop. `TeachIllustration` is an enum the screen maps to a small **in-app-rendered mock** (not a drawable asset): a `Column` of 2–3 real primitives (`SoftCard`, `IconTile`, `CircleIconButton` with `CircleStyle.Success` / `.Tonal`, a fake `ReminderCard`-shaped row, a mini week-strip-shaped row) so the picture always matches the live theme/accent/font. Keep each illustration cheap and static.
- `WizardStep.PermissionPrimer` — one step explaining the three permissions in plain language (notifications = reminders can alert you; exact alarms = they fire at the minute you set; battery unrestricted = the 7-day window isn't killed), each with an **optional** inline "Allow" that triggers the existing request path. `Next` / `Skip` are always enabled. This does **not** replace the post-onboarding `MainActivity` permission `LaunchedEffect`s — leave those as the backstop (they mostly no-op once the primer has asked).

Proposed step order:
1. `NameAsk` (only when no derivable name).
2. `Teach` — **Today**: greeting + "N left today" hero + week strip + progress cards; tapping a past day logs retroactively; the month chevron expands the calendar.
3. `Teach` — **Make a habit**: 4 types concisely (Individual = a reminder per time; Batch = one combined daily check-in for a group of small habits; Ongoing = a passive day-count, no reminders; Journal = a chat-style Q&A per reminder). Days, times, snooze.
4. `Teach` — **Food / meds / anything else** (Intake): reminders that ask "what did you have?", inline reply from the card or the notification, plus the red-flag food diary.
5. `Teach` — **Reminders work from the shade**: Skip / Snooze / Complete / Reply without opening the app; unlimited re-nag until you respond; quiet hours defer, never drop.
6. `Teach` — **Yours & private**: accent + font + light/dark; which tabs show; everything lives on-device first and mirrors once you sign in; optional PIN / biometric app lock.
7. `PermissionPrimer`.
8. Final step: `Get started` primary button, plus a secondary `TextLink` **"Create your first habit"** that calls a new `onOpenAddHabit` callback **after** `completeOnboarding` — the user may ignore it. `MainActivity` wires it to `navController.navigate("add_habit")` post-completion.

**Replay:** add a "Replay the tour" row in the new **About & help** screen (item 5). It navigates to a new nav route `onboarding_review` in `MainActivity`, which shows `OnboardingScreen` in a review mode: `OnboardingViewModel.configureReview()` builds the tour-only step list (skips `NameAsk`), the final button reads `Done` and just pops back, and **`onboardingCompleted` is never touched**.

### Files to touch
- `ui/onboarding/OnboardingViewModel.kt` — new `WizardStep` subtypes, `TeachIllustration` enum, rewritten `OnboardingTourSteps`, `configureReview()`; keep `buildWizardSteps` pure.
- `ui/onboarding/OnboardingScreen.kt` — render `Teach` (illustration mapper) + `PermissionPrimer` + final CTA; keep the `Column` / `weight(1f)` / `verticalScroll` / `StickySaveBar` shell.
- New file: `ui/onboarding/OnboardingIllustrations.kt` — the small mock composables.
- `ui/MainActivity.kt` — pass `onOpenAddHabit`; add the `onboarding_review` composable route + its entry from About.
- `ui/settings/AboutSettingsScreen.kt` (new, item 5) — "Replay the tour" row.
- Tests: `app/src/test/.../onboarding/WizardStepTest.kt` (extend) — assert the step list shape for `hasAutoDerivedName` true/false and for review mode.

### SCHEMA IMPACT
**None.** Reuses `AppSettings.onboardingCompleted`; replay is stateless; no `onboarding_tour_version` column (locked decision).

### Verification
Gate green + `WizardStepTest`. Manual (user, device): fresh install shows the teaching flow; "Create your first habit" opens Add Habit only if tapped; About → "Replay the tour" re-shows steps 2–8 and returns without changing anything.

---

## ITEM 2 — Alarm/notification permission dialog contrast

### What's wrong now (file evidence)
- The "Allow exact alarms" prompt is `MainActivity.kt` `showExactAlarmDialog` → `DaybookAlertDialog(title = "Allow exact alarms", text = { Text("Daybook fires reminders at exact times and needs the Alarms & reminders permission.") }, ...)`. The sibling notification-rationale dialog (`showRationale`) has the same shape.
- `ui/components/DaybookAlertDialog.kt` wraps M3 `AlertDialog`: sets `containerColor = DaybookColors.Surface`, styles the **title** explicitly (`color = DaybookColors.TextPrimary`), but passes the caller's `text` slot straight through with **no `textContentColor` and no style override**. M3 then colours it with `colorScheme.onSurfaceVariant` → `DaybookColors.TextMuted` `#9AA0A6` at `bodyMedium` — the app's *caption* tier — on the near-black `Surface` `#16181B`. That is the low-contrast body the user sees. Every other bespoke dialog (`SettingsScreen` sign-out, `DataSettingsScreen` import) passes an explicit `color`; these two do not.
- Secondary: `AlertDialog` default `tonalElevation` (6dp) + `surfaceTint` never set in either scheme → M3's default light-purple wash over the dialog container.
- The `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` screen `onConfirm` opens is the OS's own and is not stylable — out of scope.

### Proposed change
1. `DaybookAlertDialog.kt` — add `textContentColor: Color = DaybookColors.TextPrimary` param; pass to `AlertDialog(textContentColor = textContentColor)`. Add `tonalElevation = 0.dp`. Backward-compatible for the ~6 existing callers (they get a higher-contrast body — spot-check each in the same pass).
2. `MainActivity.kt` — for both permission dialogs, wrap the body text on the app's body style with explicit `TextPrimary` (don't rely on the M3 default). Fuller exact-alarm copy: *"Without this, reminders can still arrive but the system may batch them and fire them late. Tap Allow, then turn on 'Alarms & reminders' for Daybook."*
3. `ui/theme/Theme.kt` — set `surfaceTint = Color.Transparent` in `DarkScheme` **and** in the new `LightScheme` (item 4) so no tonal wash appears on any dialog / menu / sheet. Verify nothing relied on the tint (nothing should — the app paints its own surfaces).
4. Theme-aware: after item 4, `DaybookColors.Surface` / `.TextPrimary` resolve per theme via `LocalDaybookColors`, so this fix reads correctly in light automatically. Do item 4 first (see suggested order); if item 2 lands first, use the current static tokens and re-verify when item 4 lands.

### Files to touch
- `ui/components/DaybookAlertDialog.kt`
- `ui/MainActivity.kt` (two dialog bodies)
- `ui/theme/Theme.kt` (`surfaceTint` in both schemes)

### SCHEMA IMPACT
**None.**

### Verification
Gate green. Manual (device): with exact-alarm permission off, cold-launch → rationale dialog body clearly legible in dark, then in light after item 4; no purple cast.

---

## ITEM 3 — BATCH habit posts a duplicate per-item notification (narrow bugfix)

### The bug (user report)
A `HabitType.BATCH` habit fires **both**:
- the intended single consolidated notification (`NotificationUtils.showBatchHabitNotification`, id `BATCH_NOTIFICATION_ID = 999`, from `AlarmReceiver.fireBatch`), **and**
- a separate per-item notification for each batch item, on the normal per-occurrence path.

The user wants **only the consolidated one**. `INDIVIDUAL` / `JOURNAL` / Intake behaviour must be left **byte-for-byte unchanged**. No notification-group / summary work.

### Suspected root cause (implementer to confirm first)
BATCH habits still get **occurrence rows** generated: `OccurrenceScheduler.effectiveTimesJson(habit, checkinTime)` returns the check-in time for `BATCH` (so the Today card + streak work), which means `habit_occurrences` rows exist for BATCH habits at the check-in instant. The guard that keeps BATCH off the per-item alarm path is `armsOwnAlarm(type) == false` for `BATCH` — so `OccurrenceScheduler.armNextHabitInternal` should skip arming a per-occurrence alarm for them. The double-notification most likely comes from one of:
1. **A stale/rogue per-occurrence alarm** still armed for a habit that was created as `INDIVIDUAL` and later switched to `BATCH` (or armed before the type check), whose `AlarmReceiver.fireHabit` path has **no `HabitType.BATCH` check** and posts `showHabitNotification` regardless.
2. **`AlarmReceiver.fireHabit` / `fireBatch` overlap** — `fireBatch` iterates unresolved BATCH occurrences and, on some path (re-nag, catch-up sweep, boot re-arm in `BootCompletedReceiver`, or `WindowRefreshWorker`), a per-occurrence fire is also scheduled for the same rows.
3. **`syncAll()` / `armNextHabitInternal`** not filtering `HabitType.BATCH` in every arm path (there are `armNextHabitInternal`, the `syncAll` sweep, boot re-arm, and `WindowRefreshWorker` — confirm the `BATCH` skip is applied in all of them, not just the primary one at `OccurrenceScheduler.kt:~313`).

Trace concretely: `AlarmReceiver.kt` `fireHabit(occurrenceId, isRefire)` — read whether it loads the owning `Habit` and short-circuits when `habit.type == HabitType.BATCH`. It currently loads `habit` (used for `habit.title` / `habit.promptMessage`) but the read shows **no `type` check** before `showHabitNotification` / `showHabitJournalNotification`. That is the most likely single fix point.

### Proposed change
1. **Introduce one pure predicate** — e.g. `fun shouldPostIndividualHabitNotification(type: HabitType): Boolean = type != HabitType.BATCH` (place it next to `armsOwnAlarm` in `OccurrenceScheduler.kt`, or in a small `util/notification` helper if that's cleaner for the test). It must be a plain function with no Android deps.
2. **`AlarmReceiver.fireHabit`** — after loading the owning `Habit`, if `!shouldPostIndividualHabitNotification(habit.type)`: do **not** call `showHabitNotification` / `showHabitJournalNotification`, do **not** insert the `SHOWN` event, do **not** arm the re-nag — return early (a BATCH slot is surfaced by the check-in notification only). Log one line so the suppression is visible in logcat.
3. **Belt-and-braces:** in whichever arm paths schedule per-occurrence fire alarms, ensure `HabitType.BATCH` is filtered (confirm `armNextHabitInternal`, the `syncAll` sweep, `BootCompletedReceiver`, `WindowRefreshWorker`). If a stale alarm can already be armed for a since-converted habit, have `syncAll()` cancel any per-occurrence alarm for a habit whose `type` is now `BATCH` (`NotificationUtils.cancelReminderAlarm(occ.id, occ.notificationId, isHabit = true)` for each such occurrence).
4. Do **not** touch `showBatchHabitNotification`, `fireBatch`, `armBatchCheckIn`, or anything on the `INDIVIDUAL` / `JOURNAL` / Intake path.

### Files to touch
- `data/OccurrenceScheduler.kt` — the pure predicate; BATCH filtering in every arm path; the stale-alarm cleanup in `syncAll()`.
- `util/alarm/AlarmReceiver.kt` — early-return in `fireHabit` for `HabitType.BATCH`.
- (Only if the trace shows they schedule per-occurrence habit fires) `util/alarm/BootCompletedReceiver.kt`, `util/work/WindowRefreshWorker.kt` — apply the same filter.
- Tests: `app/src/test/.../` — a focused JUnit test on `shouldPostIndividualHabitNotification` (`INDIVIDUAL` / `JOURNAL` / `STREAK` → true where applicable; `BATCH` → false). If a pure "which occurrences should have a per-item alarm armed" selector is factored out, add an input→output test for it too (mirror `BatchCheckInTest`).

### SCHEMA IMPACT
**None.** No column, no migration.

### Verification
Gate green + the new predicate test. Manual (user, device): create a BATCH habit with ≥2 items, wait for the check-in time (or "Re-arm all reminders" then trigger) → exactly **one** notification (the consolidated "Fill today's habits"), zero per-item notifications; an INDIVIDUAL habit at the same time still posts its own notification unchanged; convert an existing INDIVIDUAL habit to BATCH → its stale per-item notification stops after the next `syncAll()`.

---

## ITEM 4 — Light / dark theme toggle

**Contains the one approved migration: `MIGRATION_19_20` (one column, `theme_mode`). No other schema change is permitted anywhere in this overhaul.**

### What's missing now (file evidence)
Dark-only by construction: only `DarkScheme`; no `LightScheme`; no `isSystemInDarkTheme`. `DaybookColors` is a static `object` of dark values (332 refs / 40 files). `CardTints` is a static `object` of dark pastels. `AccentColor` gives one `Color` per accent regardless of theme. `surfaceTint` never set.

### Token architecture

**4.1 — turn `DaybookColors` into a theme-resolved scheme with minimal call-site churn (LOCKED: `@Composable`-getter shim).**
- In `Tokens.kt`:
  ```
  @Immutable data class DaybookColorScheme(
      val bg: Color, val surface: Color, val surfaceElevated: Color, val outline: Color,
      val hairline: Color, val border: Color, val textPrimary: Color, val textMuted: Color,
      val textFaint: Color, val success: Color, val warning: Color, val danger: Color, val onSolid: Color
  )
  val DaybookColorsDark = DaybookColorScheme( /* the exact current constants — byte-identical */ )
  val DaybookColorsLight = DaybookColorScheme( /* new, see 4.2 */ )
  val LocalDaybookColors = staticCompositionLocalOf { DaybookColorsDark }
  ```
- Keep the public name `DaybookColors` working: convert it to an `object` whose members are `@Composable get()` delegating to the local, e.g.
  ```
  object DaybookColors {
      val Bg: Color        @Composable get() = LocalDaybookColors.current.bg
      val Surface: Color    @Composable get() = LocalDaybookColors.current.surface
      val TextPrimary: Color @Composable get() = LocalDaybookColors.current.textPrimary
      /* …13 total… */
  }
  ```
  This makes ~all 332 references compile unchanged **where they sit inside a `@Composable`**.
- **Sweep the non-composable references** (compiler will flag them): grep `DaybookColors\.` inside `private fun` bodies that are not `@Composable`, inside `remember { }` lambdas, and inside `object` initialisers. Known cases to fix:
  - `Tokens.kt` itself — `CardTints.Neutral` is built from `DaybookColors.*` at `object` init. Move `Neutral` (and any pastel that references `DaybookColors`) into the per-theme resolution in 4.4 so it no longer reads the shim at init time.
  - `Theme.kt` — `DarkScheme` reads `DaybookColors.*` at file scope; keep it reading the raw `DaybookColorsDark` fields directly, not the shim.
  - A handful of `ui/components/*` / `ui/home/*` fallbacks (e.g. `Color.Transparent` next to `DaybookColors.X` in `WeekStrip.DayCell`) — pass the colour in as a param or hoist the read into the composable body.
  Estimate 10–25 trivial, compiler-caught fixes.
- `DaybookTheme` provides `LocalDaybookColors` next to `LocalAccent` / `LocalReduceMotion`.

**4.2 — `LightScheme` + selection in `Theme.kt`.**
- Add `LightScheme = lightColorScheme(...)` mapping `DaybookColorsLight` roles onto M3 exactly as `DarkScheme` does for dark.
- `DaybookColorsLight` starting values (implementer tunes on device; keep it calm/paper, not stark white):
  | role | dark (unchanged) | light (proposed) |
  |---|---|---|
  | `bg` | `#0B0D0F` | `#FBFBF9` |
  | `surface` | `#16181B` | `#FFFFFF` |
  | `surfaceElevated` | `#1E2124` | `#F2F2EF` |
  | `outline` | `#2A2D31` | `#E2E2DE` |
  | `hairline` (`Border`) | `#14FFFFFF` (white 8%) | `#14000000` (black 8%) |
  | `textPrimary` | `#F2F3F5` | `#1B1D20` |
  | `textMuted` | `#9AA0A6` | `#5B6068` |
  | `textFaint` | `#6B7178` | `#8A9099` |
  | `success` | `#4ADE80` | `#15803D` |
  | `warning` | `#FACC15` | `#B45309` |
  | `danger` | `#F87171` | `#DC2626` |
  | `onSolid` | `#0B0D0F` | `#FFFFFF` (text/icon on a filled accent control) |
- `DaybookTheme` gains `themeMode: ThemeMode`:
  ```
  enum class ThemeMode(val storageKey: String) { DARK("DARK"), LIGHT("LIGHT"), SYSTEM("SYSTEM");
      companion object { val DEFAULT = DARK; fun fromKeyOrDefault(k: String?) = entries.firstOrNull { it.storageKey == k } ?: DEFAULT } }
  ```
  ```
  val dark = when (themeMode) { DARK -> true; LIGHT -> false; SYSTEM -> isSystemInDarkTheme() }
  val base = if (dark) DarkScheme else LightScheme
  val colors = if (dark) DaybookColorsDark else DaybookColorsLight
  val scheme = remember(themeMode, accent, dark) {
      base.copy(primary = accent.colorFor(dark), secondary = accent.colorFor(dark),
                tertiary = accent.colorFor(dark), surfaceTint = Color.Transparent) }
  CompositionLocalProvider(
      LocalDaybookColors provides colors,
      LocalAccent provides accent.colorFor(dark),
      LocalReduceMotion provides reduce
  ) { MaterialTheme(colorScheme = scheme, typography = typography, shapes = DaybookShapes, content = content) }
  ```

**4.3 — per-theme accent (`Accent.kt`).**
- Change `AccentColor` from `val color: Color` to `val dark: Color` + `val light: Color`, plus `fun colorFor(dark: Boolean) = if (dark) this.dark else this.light`. `storageKey` unchanged (serialisation by name — exported backups unaffected).
  | Accent | `dark` (unchanged) | `light` (proposed) |
  |---|---|---|
  | MINT | `#2DD4BF` | `#0F9488` |
  | LAVENDER | `#A78BFA` | `#7C5CE0` |
  | CORAL | `#FB7185` | `#E23D5B` |
  | SKY | `#60A5FA` | `#2563EB` |
  | AMBER | `#FBBF24` | `#B7791F` |
- `LocalAccent` still carries a single resolved `Color`; `DaybookTheme` resolves `colorFor(dark)` before providing it — most consumers (`LocalAccent.current`) need no change. Direct `AccentColor.color` reads (grep `.color` on an `AccentColor`: `Theme.kt`, `AppearanceSettingsScreen` swatch row, `OnboardingViewModel`, `OnboardingScreen` `StepDots`) switch to `colorFor(dark)` or read `LocalAccent`.
- `AppearanceSettingsScreen` swatch row shows each accent's **current-theme** colour; `checkColor = DaybookColors.OnSolid` becomes theme-aware automatically.

**4.4 — `CardTint` light variants (`Tokens.kt`).**
- Add a light `CardTint` per pastel (`LavenderLight`, `PeachLight`, `MintLight`, `ButterLight`, `SlateBlueLight`, `RoseLight`) + `NeutralLight` — same 6-field shape, values for a paper ground (light-tinted `fill`, near-black `onFill`, mid-grey `onFillMuted` / `onFillFaint`, saturated `accent`).
- Resolve per theme with the same shim style: `object CardTints` members become `@Composable get()` returning the dark or light `CardTint` based on `LocalDaybookColors.current === DaybookColorsDark` (or a dedicated `LocalCardTints` provided by `DaybookTheme` — implementer's call; ~15 files read `CardTints.*`, all inside composables, so the `@Composable get()` works with no call-site edits).
- `byIndex` / `byId` / `resolve(overrideName, positionalIndex)` stay pure — add `@Composable` overloads (or a `@Composable fun currentTints(): List<CardTint>` the callers map through) that operate on the resolved list. Grep for `CardTints.resolve` / `CardTints.byIndex` / `CardTints.ALL` and route each through the resolved variant.
- Dark `CardTint` values stay byte-identical.

**4.5 — state + zero flash (LOCKED: `SharedPreferences` mirror read before `setContent`).**
- Setting: `AppSettings.theme_mode TEXT NOT NULL DEFAULT 'DARK'` (values `DARK` / `LIGHT` / `SYSTEM`). Kotlin: `@ColumnInfo(name = "theme_mode", defaultValue = "DARK") val themeMode: String = ThemeMode.DEFAULT.storageKey` (String column, no Room converter — mirrors `font_choice` / `week_start`). Appended last, never reordered.
- Plumb like every other setting: `AppSettingsDao.updateThemeMode`, `AppSettingsRepository.setThemeMode`, `SettingsViewModel` + `OnboardingViewModel` `col({ it.themeMode }, "DARK")` flow + setter, feed `DaybookTheme(themeMode = ThemeMode.fromKeyOrDefault(...))` in `MainActivity.setContent` next to `accent` / `fontChoice`.
- **Zero-flash mechanism:**
  1. `AppSettingsRepository.setThemeMode(v)` writes Room **and then** `context`-backed `SharedPreferences("daybook_prefs", MODE_PRIVATE).edit().putString("theme_mode", v).apply()` (the same prefs file `MainActivity` already uses for `alarm_permission_asked`). Inject `@ApplicationContext` into the repository, or route the mirror write through a tiny `ThemeModePrefs` helper — do not add a Room read on the UI thread.
  2. `MainActivity.onCreate`, **before `setContent`**: read `daybook_prefs.getString("theme_mode", "DARK")`, map to `ThemeMode`, and use it as the **initial value** of the `themeMode` StateFlow (`stateIn(..., initialValue = <read>)`) so the very first composition is already correct. Also `setTheme(...)` a matching Activity theme (a `values/themes.xml` + `values-night` or two explicit styles) so the pre-inflate `android:windowBackground` matches — a light `#FBFBF9` window for `LIGHT`, the current dark for `DARK` / (SYSTEM→follow `-night`).
  3. Keep the existing neutral-splash gate (`MainActivity` paints `Box(background = DaybookColors.Bg)` while `authState is Loading` / `onboardingCompleted == null`) — with 4.1 done, `DaybookColors.Bg` now resolves to the correct theme's background, so the splash is already right.

### Settings placement
`AppearanceSettingsScreen`: add a **"Theme"** `SectionHeader` above "Accent color", with a `SegmentedControl` — `Dark` / `Light` / `System`. Keep Accent + Font sections exactly where they are. (The new **"Layout"** section from item 5 also lands on this screen.)

### Files to touch
- `ui/theme/Tokens.kt` — `DaybookColorScheme` + `LocalDaybookColors` + dark/light instances; `DaybookColors` `@Composable`-getter shim; `CardTint` light variants + per-theme `CardTints` resolution; move `Neutral` out of `object` init.
- `ui/theme/Theme.kt` — `LightScheme`, `ThemeMode` enum, `themeMode` param + scheme/colour selection, `surfaceTint = Color.Transparent` in both schemes, provide new locals.
- `ui/theme/Accent.kt` — `dark` / `light` + `colorFor(dark)`.
- `data/model/DataModel.kt` — appended `AppSettings.themeMode` field.
- `data/local/AppSettingsDao.kt` — `updateThemeMode`.
- `data/AppSettingsRepository.kt` — `setThemeMode` + SharedPreferences mirror write.
- `data/local/Migrations.kt` — `MIGRATION_19_20`.
- `di/DatabaseModule.kt` — register `MIGRATION_19_20` in `addMigrations(...)`.
- `ui/settings/SettingsViewModel.kt` + `ui/onboarding/OnboardingViewModel.kt` — `themeMode` flow + setter.
- `ui/MainActivity.kt` — synchronous prefs read before `setContent`; `setTheme(...)`; pass `themeMode` to `DaybookTheme`.
- `app/src/main/res/values/themes.xml` (+ `values-night` or explicit light/dark styles) — window background per mode. Do **not** change the app theme parent or any other attribute.
- `ui/settings/SettingsScreen.kt` (`AppearanceSettingsScreen`) — Theme section.
- The 10–25 non-composable `DaybookColors.` / `CardTints.` spot fixes from 4.1 / 4.4.
- `app/schemas/com.daybook.app.data.local.AppDatabase/20.json` — regenerated by the build, committed.
- Tests:
  - `app/src/test/.../theme/` — `ThemeMode.fromKeyOrDefault` pure test (mirror `FontChoice`); a pure test asserting every `AccentColor.dark` equals the current constant (guards "dark unchanged") and every `AccentColor.light` differs; optionally a contrast-ratio assertion for `AccentColor.light` vs `DaybookColorsLight.surface`.
  - `app/src/androidTest/.../data/local/MigrationTest.kt` — `migrate19To20_addsThemeModeColumn` + `migrate19To20_preservesRowsAndDefaultsDark` (create at 19 with an `app_settings` row, run `MIGRATION_19_20`, assert `theme_mode = 'DARK'` and all other columns intact).

### SCHEMA IMPACT — Room migration v19 → v20 (the ONLY one in this overhaul)
```kotlin
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN theme_mode TEXT NOT NULL DEFAULT 'DARK'")
    }
}
```
- Additive, single column, `NOT NULL DEFAULT 'DARK'` byte-matching `@ColumnInfo(defaultValue = "DARK")`. No table rebuild, no row rewrite.
- Device-local: not in `BackupModel`, not in any export, not in `ContentHash` / the sync loop — same as every `app_settings` column since v16.
- `AppDatabase.version` → `20`. `di/DatabaseModule.kt` `addMigrations(... , MIGRATION_18_19, MIGRATION_19_20)`.
- Downgrade v20→v19 is covered by the existing `fallbackToDestructiveMigrationOnDowngrade()` (wipes) — unchanged policy; acceptable per the user.
- **This is the entire permitted schema surface. Any further schema edit → STOP and ask.**

### Verification
Gate green; `20.json` regenerated + committed; `MigrationTest.migrate19To20*` compiles (and passes on a device if the user runs it). `ThemeMode` / accent tests green. Manual (device): Appearance → Theme → Light restyles the whole app; accent picker + card pastels + dialogs (item 2) legible on the paper ground; back to Dark → visually identical to pre-change (diff screenshots if possible); kill + relaunch under Light → **no** dark flash; fresh install → still Dark; `System` follows the OS toggle.

---

## ITEM 5 — Settings reorganisation

### Full current inventory (file evidence)

**Hub — `SettingsScreen.kt`**, one `SettingsGroup` of `SettingsRow`s:
1. **Account & sync** → `AccountScreen` (subtitle = `accountSubtitle`).
2. **Appearance** → `AppearanceSettingsScreen` (subtitle `"$accentName · ${fontChoice.label}"`).
3. **Today & calendar** → `TodayCalendarSettingsScreen` (`"Week start, clock, greeting, streaks"`).
4. **Navigation** → `NavigationSettingsScreen` (`"Default tab and which tabs show"`).
5. **Notifications & alarms** → `NotificationSettingsScreen` (`"All set"` / `"Action needed"`).
6. **App lock** → `AppLockSettingsScreen` (`"On"` / `"Off"`).
7. **Export & import** → `DataSettingsScreen` (`"Back up or restore your data"`).
- Separate group: **Sign out** (confirm dialog).
- Footer: launcher glyph, `"Daybook"`, `"Version 0.5.6 (22)"`, conditional **Copy crash log** `TextLink`.
- Top: `ProfileHeader` (avatar, name, "Your Daybook", "Edit Profile" pill) + inline name field + "Remove photo" + photo error.

**Appearance:** `Accent color` (5 swatches) · `Font` (5-row list) · `Accessibility` (Reduce motion toggle).
**Today & calendar:** `Calendar` (Week starts on / 24-hour time / Default calendar view) · `Greeting` (Greeting style / Show time-of-day word / Hero line 4-way) · `Reminders` (Hide resolved reminders by default) · `Streak display` (Streak counting / Rest days / Show streak flames).
**Navigation:** `Default tab` (radio of visible routes) · `Tabs` (Today locked / Show tab: Habits / Show tab: Intake).
**Notifications & alarms:** `Permissions` (Notifications / Exact alarms / Battery + Fix) · `Habit check-in` (Check-in time) · `Quiet hours` (toggle / Start / End) · `Snooze` (SnoozeStepper) · `Updates` (Check for updates) · `Diagnostics` (Send test notification / Re-arm all reminders).
**App lock:** `App lock` toggle · `Change PIN` · `Lock after` (Immediately / 1 / 5 / 15 min).
**Account & sync:** `Signed in` (identity + Copy) · `Sync` (status + Sync now) · `From your Google account` (Use Google photo / Use "$name" as your name) · `Sign out` · `Danger zone` (Delete account).

### Diagnosis
- Casing is inconsistent (Title Case headers vs sentence-case row labels; `24-hour time` vs `Show time-of-day word`; `Use "$name" as your name`). Android standard is **sentence case** everywhere (proper nouns — *Google*, *Daybook*, *PIN* — stay capitalised).
- **Navigation** is a whole hub row + screen for 3 toggles + a radio.
- **Diagnostics** + **Updates** are power-user and inflate the Notifications screen.
- No **About** destination — version / crash log / (new) replay-tour squat in the hub footer.
- **Greeting** (3 sub-controls for one line) is over-weighted — see item 8.1.

### Proposed information architecture

**New hub (7 rows → 7 rows, renamed + regrouped):**

| # | Hub row (after) | Sub-screen | Contains |
|---|---|---|---|
| 1 | **Account & sync** | `AccountScreen` (unchanged) | identity, sync, Google photo/name, sign out, delete account |
| 2 | **Appearance** | `AppearanceSettingsScreen` | **Theme** (new, item 4), Accent color, Font, **Layout** (folded-in Navigation), Motion (Reduce motion) |
| 3 | **Today & calendar** | `TodayCalendarSettingsScreen` | Calendar, Greeting, Reminders list, Streaks |
| 4 | **Reminders & notifications** | `NotificationSettingsScreen` (renamed) | Permissions, Quiet hours, Default snooze, Batch check-in |
| 5 | **Privacy & lock** | `AppLockSettingsScreen` (renamed) | App lock, PIN, lock timeout |
| 6 | **Backup & data** | `DataSettingsScreen` (renamed) | export range, import, share |
| 7 | **About & help** | **new `AboutSettingsScreen`** | version; Replay the tour (item 1); Check for updates (moved from Notifications); Send test notification + Re-arm all reminders (moved from Notifications); Copy crash log; open-source licences (optional) |

- **Navigation** stops being a hub row: its two sections (`Default tab`, `Show Habits tab` / `Show Intake tab`) become a **"Layout"** `SectionHeader` inside `AppearanceSettingsScreen`. Keep the composable sections in `NavigationSettingsScreen.kt` but export them as reusable `@Composable` fns called from Appearance; remove the `settings_navigation` hub destination + its `onOpenNavigation` wiring (the route may be deleted or left dead — prefer deleting).
- **Diagnostics** + **Check for updates** → About & help (feature-move for item 8.4 / 8.5 too).
- **Habit check-in** → stays under Reminders & notifications, retitled **"Batch check-in"**, subtitle rewritten to point at the Batch habit type.
- Hub footer keeps just the centred wordmark + version (tap opens About); everything else in it moves to About.

### Before → after label / casing map (renames OK)

| Location | Before | After (sentence case) |
|---|---|---|
| Hub row | `Notifications & alarms` | `Reminders & notifications` |
| Hub row | `App lock` | `Privacy & lock` |
| Hub row | `Export & import` | `Backup & data` |
| Hub row | `Navigation` (standalone) | *removed → Appearance → Layout* |
| Hub row | *(none)* | `About & help` (new) |
| Hub row | `Account & sync` / `Appearance` / `Today & calendar` | unchanged |
| Appearance | *(new)* | `Theme` header + `Dark` / `Light` / `System` segmented |
| Appearance | *(new)* | `Layout` header + `Default tab`, `Show Habits tab`, `Show Intake tab` |
| Appearance hdr | `Accessibility` | `Motion` |
| Appearance hdr | `Accent color` / `Font` | unchanged (keep US `color`) |
| Today&cal hdr | `Reminders` | `Reminders list` |
| Today&cal hdr | `Streak display` | `Streaks` |
| Today&cal rows | `Week starts on` / `24-hour time` / `Default calendar view` / `Greeting style` / `Show time-of-day word` / `Hero line` / `Hide resolved reminders by default` / `Streak counting` / `Rest days` / `Show streak flames` | unchanged wording, sentence case confirmed |
| Notifs hdr | `Habit check-in` | `Batch check-in` |
| Notifs hdr | `Snooze` | `Default snooze` |
| Notifs hdr | `Updates` / `Diagnostics` | *moved to About & help* |
| Notifs rows | `Notifications` / `Exact alarms` / `Battery` / `Check-in time` / `Quiet hours` | unchanged |
| App lock screen | title `App lock` | title `Privacy & lock` |
| App lock rows | `App lock` / `Change PIN` / `Lock after` | unchanged |
| Data screen | title `Export & import` | title `Backup & data` |
| Data hdrs | `Export a date range` / `Restore & share` | unchanged |
| Account row | `Use "$name" as your name` | `Set name to "$name"` |
| About screen | *(new)* | `Version`, `Replay the tour`, `Check for updates`, `Send test notification`, `Re-arm all reminders`, `Copy crash log`, `Open-source licences` |

### Files to touch
- `ui/settings/SettingsScreen.kt` — hub rows (set/order/labels); move footer content to About; sentence-case sweep across `AppearanceSettingsScreen` / `TodayCalendarSettingsScreen` / `NotificationSettingsScreen` / `DataSettingsScreen` headers + row titles; add `Theme` + `Layout` + `Motion` sections to Appearance; retitle "Habit check-in" → "Batch check-in".
- New file: `ui/settings/AboutSettingsScreen.kt` — version block, Replay-the-tour row (item 1), Check-for-updates toggle (from `SettingsViewModel.checkForUpdatesEnabled`), diagnostics buttons (`sendTestNotification` / `resyncReminders`), Copy-crash-log (`hasCrashLog` / `crashLogText`), optional licences.
- `ui/settings/NavigationSettingsScreen.kt` — extract the two sections as reusable `@Composable` fns for Appearance; drop the `SettingsSubScreen` wrapper / route.
- `ui/MainActivity.kt` — remove `settings_navigation` hub wiring (`onOpenNavigation`); add `settings_about` composable route + `onOpenAbout`; `settings_appearance` now also hosts Layout + Motion.
- `ui/settings/SettingsViewModel.kt` — no new state (existing `checkForUpdatesEnabled` / `sendTestNotification` / `resyncReminders` re-surfaced from About).
- Tests: none required (pure IA/label). Update any test that asserts a hub-row label string.

### SCHEMA IMPACT
**None.**

### Verification
Gate green. Manual: every setting still reachable; casing consistent (sentence case); About has version + replay tour + diagnostics + updates + crash log; Navigation no longer a top-level row; Layout section works in Appearance.

---

## ITEM 6 — Personalisation

**DROPPED by the user.** No changes. No personalisation columns. This item is intentionally empty.

---

## ITEM 7 — Today + calendar UI

### What can be assessed from code
- `ui/home/HomeScreen.kt`: pinned `HomeHeader` (greeting + date + `Avatar` + hero `BigHeadline`), then a `LazyColumn`: optional sync/notif banners → `WeekStrip` → `"Your progress"` `SectionHeader` + a `Row` of two fixed-144dp `ProgressCard`s (Habits/Mint, Intake/Peach) → `"Reminders"` `SectionHeader` + filter button → `EmptyState` or `itemsIndexed(visibleItems)` of `ReminderCard`.
- **The reminders list is one flat list sorted only by `scheduledEpoch`** (`HomeViewModel.kt`: `return items.sortedBy { it.scheduledEpoch }`). No grouping by time-of-day, domain, or state. This is the "categorization is bad".
- `ReminderCard` carries a lot: title, subtitle, red-flag dot + text, "outside food" line, alarm-clock icon + time (own `Row`), then a trailing zone (backfill-loading / interactive status `TextLink` + `MoreVert` / Complete + `MoreVert` / journal-comment + `MoreVert` / reply-send + `MoreVert` / "Upcoming"), plus an expanding inline reply block (text field + red-flag picker + suspected-food field + "Outside food" chip). Dense.
- `WeekStrip.kt`: chevron header + centred month/range label; **"Back to today" `TextLink`** inside an `AnimatedVisibility(visible = selectedDate != today)` that occupies zero height when hidden → the calendar body **jumps** when it springs in; `AnimatedContent` week-row ↔ `MonthGrid` with a `SizeTransform`; centred expand/collapse chevron handle. `DayCell`: weekday initial, 38×40 rounded box (accent fill when selected), 4dp "today" dot. Future days dimmed + non-tappable.
- `ProgressCard`: fixed 144dp, icon tile + title, `PastelProgressBar` (tint.accent), `%` + optional streak `StatPill`. Two side-by-side, equal size.

### Proposed change
1. **Group the reminders list into labelled sections** — replace the flat `sortedBy` with a pure transform in `HomeViewModel`:
   - `fun groupHomeItems(items: List<HomeItem>, selectedDate: LocalDate, today: LocalDate, nowMillis: Long): List<HomeSection>` where `HomeSection(label: String, count: Int, items: List<HomeItem>)`.
   - For **today**: `Overdue` (`scheduledEpoch < now` and still actionable) → `Now` (within the next ~90 min, or currently due) → `Later` (rest of today, still pending) → `Done` (resolved; shown only when resolved items are visible per the existing "show resolved" state).
   - For a **past** selected day: `Missed` / `Logged` / `Skipped` / `Done` as applicable (reuse `statusLabelFor` semantics).
   - Within each section keep `scheduledEpoch` order.
   - Render each section label as a lightweight muted line (`DaybookText.Metadata` / `labelMedium`, `TextMuted`) with the count, e.g. `NOW · 2`. No new card chrome, no new colours.
2. **"Group by type" option in the EXISTING filter sheet** (`SortSheet` already open from the Reminders `SectionHeader` filter button in `HomeScreen`). Add one toggle "Group by type" that switches the section axis to `Habits` / `Intake` / `Journal` instead of state. **Back it with session state (`rememberSaveable` in `HomeScreen`) or a `SharedPreferences` flag** (`daybook_prefs`, key `home_group_by_type`) — **NOT an `app_settings` column** (locked decision). `groupHomeItems` takes a `byType: Boolean` param and stays pure.
3. **Lighten `ReminderCard`**: fold the red-flag / suspected-food / outside-food detail into a single truncated meta line (they already use `onFillFaint`); put the alarm-time on the title's second line rather than its own `Row`. **Do not remove any action.** Spacing / hierarchy only.
4. **`WeekStrip`**: give the "Back to today" link a **reserved height** (fixed-height `Box`, content shown/hidden inside) so the calendar body doesn't jump when it appears — the pattern `ProgressCard`'s `StreakSlotHeight` already uses. Optionally move it inline as a small trailing link next to the month label.
5. **"Your progress"**: route the greeting→date→hero vertical rhythm through `Spacing` tokens; optionally make the section collapsible via `rememberSaveable`. No colour change (keep Mint/Peach identity).

**Visual styling caveat (in-plan):** detailed spacing / weight / colour-balance decisions are **pending user screenshots** (Today with several reminders; week strip collapsed; expanded month grid; the two progress cards; Today on a past day). The implementer does a **best-effort code restyle** per the above and the user refines on device.

### Files to touch
- `ui/home/HomeViewModel.kt` — pure `groupHomeItems(...)` + `HomeSection` data class; keep `ratio` / `heroLine` pure helpers; feed `visibleItems` through grouping.
- `ui/home/HomeScreen.kt` — render sections (label + `itemsIndexed` per section); lighten `ReminderCard` meta rows; reserved-height "Your progress" collapse; wire the filter-sheet "Group by type" toggle to session/prefs state.
- `ui/components/SortSheet.kt` — add the "Group by type" toggle row (reuse the existing archived-row toggle pattern).
- `ui/components/WeekStrip.kt` — reserved height for "Back to today".
- Tests: `app/src/test/.../home/GroupHomeItemsTest.kt` — pure: given items with mixed epochs / statuses, a selected day, a `now`, and `byType` on/off → assert section labels, membership, counts, and order.

### SCHEMA IMPACT
**None.** Grouping is a pure view transform; the "group by type" preference is session state / `SharedPreferences`, not Room.

### Verification
Gate green + `GroupHomeItemsTest`. Manual (user, device, after screenshots): reminders show `Overdue / Now / Later / Done` labels with counts; filter sheet "Group by type" switches to `Habits / Intake / Journal` and the choice survives rotation; week strip doesn't jump when "Back to today" appears; dark theme still recognisable.

---

## ITEM 8 — Remove bloat (as approved)

Rule for 8.1 / 8.2 / 8.3 / 8.11: **hide the UI, keep the `app_settings` column live and readable** (the established `habits_accent_color` pattern). No column drop, no table rebuild, fully revertable. Every code path that reads the column keeps working.

| # | Action | What / why | How |
|---|---|---|---|
| 8.1 | **Hide UI** | Greeting has 3 controls (`greeting_tone` Warm/Plain/Minimal + `greeting_time_word` + `hero_style` 4-way) to tune one header line. | In `TodayCalendarSettingsScreen`, replace the `Greeting` section's three controls with one `SegmentedControl` (`Full` = WARM+timeWord / `Simple` = PLAIN / `Off` = MINIMAL) that sets `greeting_tone` + `greeting_time_word` together; stop rendering the `Hero line` radio and always write `hero_style = "COUNT_LEFT"`. `renderGreeting` / `heroLine` unchanged — they still read the columns. |
| 8.2 | **Hide UI** | Streak has 3 controls (`streak_mode` Strict/Lenient + `streak_rest_days` + `show_streaks`). Most users want on/off. | Keep the `Show streak flames` toggle. Remove the `Streak counting` `SegmentedControl` + `Rest days` `DayOfWeekSelector` from `TodayCalendarSettingsScreen` (columns stay `STRICT` / `""`; `StreakCalculator` + its tests untouched). |
| 8.3 | **Hide UI** | `reduce_motion` toggle — the OS already has "Remove animations" and the app OR-s with `ANIMATOR_DURATION_SCALE == 0`. | Remove the `Motion` (was `Accessibility`) toggle row from Appearance; keep the column + the `effectiveReduceMotion` OS path. |
| 8.4 | **Move** | `Check for updates` toggle — inert for non-testers, clutters Notifications. | Move the toggle row from `NotificationSettingsScreen` to `AboutSettingsScreen`. Column + `MainActivity.onResume` auto-flip logic unchanged. |
| 8.5 | **Move** | `Diagnostics` (Send test notification, Re-arm all reminders) — power-user tools in a primary screen; genuinely useful for support. | Move both `GhostButton`s (+ the test-result caption) from `NotificationSettingsScreen` to `AboutSettingsScreen`. |
| 8.6 | **Fold** | `Navigation` sub-screen — a hub row for 3 toggles + a radio. | Item 5: sections move into `AppearanceSettingsScreen` → "Layout"; hub row + route removed. |
| 8.7 | **OUT OF SCOPE** | Per-habit / per-intake `prompt_message` + `motivation` free-text fields. | **No change.** Leave the form fields exactly where they are today — not demoted, not moved, not restyled. Removed from the bloat scope by user amendment. |
| 8.8 | **Keep** | Red-flag food diary / outside-food / suspected-food — the app's founding use case. | No change. (Optional future: an Intake "Food diary" master toggle — not in this pass.) |
| 8.10 | **KEEP — do NOT delete** | Retired Intake-Journal files `ui/journal/JournalScreen.kt` + `JournalViewModel.kt`. **A live nav route still reaches them:** `MainActivity` `composable("journal/{arg0}/{slotMillis}")` → `JournalScreen`, wired via `goJournal` / `goJournalBackfill`, called by `HomeScreen.onNavigateToJournal` (branch on `item.isJournal`), `DetailScreen.onOpenJournal`, and the notification deep-link path (`OccurrenceScheduler.isJournalOccurrence`). Deleting them breaks those routes. | Leave the files and all wiring untouched. Flag remains: they can only be removed after the `journal/` route + `isJournalOccurrence` path are themselves retired — out of scope here. |
| 8.11 | **Hide UI** | `Default calendar view` (Week/Month cold-start) — the calendar remembers in-session anyway. | Remove the `SegmentedControl` from `TodayCalendarSettingsScreen`'s `Calendar` section; column (`calendar_default_expanded`) stays and is still read by `HomeScreen`'s seed `LaunchedEffect`. |

**Explicitly kept:** app lock, quiet hours, export/import, sync, `habits_accent_color` / `intake_accent_color` dead columns (leave inert — item 4 must NOT revive them).

### Files to touch
- `ui/settings/SettingsScreen.kt` — `TodayCalendarSettingsScreen` (8.1, 8.2, 8.11), `AppearanceSettingsScreen` (8.3), `NotificationSettingsScreen` (8.4, 8.5 removed / relocated).
- `ui/settings/AboutSettingsScreen.kt` (new) — receives 8.4 + 8.5.
- `ui/home/HomeScreen.kt` / `ui/home/HomeViewModel.kt` — only if 8.1's "always COUNT_LEFT" simplification touches `heroLine` usage (it shouldn't — just stop writing other values).
- **Not touched for item 8:** `ui/routines/HabitForm.kt`, `ui/foodmed/FoodMedForm.kt` (8.7 is out of scope).
- Tests: update any settings test asserting a removed row's presence. No new tests required.

### SCHEMA IMPACT
**None.** No column drops, no migration. Every hidden setting's column stays live.

### Verification
Gate green. Manual: hidden rows gone; 8.4 / 8.5 present in About; 8.6 in Appearance → Layout; the per-habit / per-intake prompt + motivation fields are unchanged from today (8.7 untouched); every still-present feature works; no crash from a code path reading a now-hidden column (they all still read it).

---

## Test strategy (JVM, pure — runnable on this machine)
- `WizardStepTest` (extend) — item 1 step lists (auto-derived name true/false, review mode).
- notification predicate — item 3: `shouldPostIndividualHabitNotification(HabitType)` (`BATCH` → false, others → true).
- `ThemeMode.fromKeyOrDefault` + `AccentColor.dark` unchanged / `.light` differs [+ optional contrast] — item 4.
- `groupHomeItems(...)` — item 7: section labels / membership / counts / order, `byType` on & off, today vs past day.
- `MigrationTest.migrate19To20_addsThemeModeColumn` + `migrate19To20_preservesRowsAndDefaultsDark` — instrumented (compiled here, run on device by the user).
- The ~446 existing unit tests must stay green.

## Suggested implementation order
1. **Item 2** — tiny; improves the dialog immediately.
2. **Item 4** — theme architecture (`LocalDaybookColors` shim, `LightScheme`, per-theme accent/tints) + `MIGRATION_19_20`. Everything visual rides on this.
3. **Item 5** — IA: needs item 4's `Theme` section; creates `AboutSettingsScreen` that items 1 and 8 depend on.
4. **Item 1** — onboarding rewrite; uses About for "Replay the tour".
5. **Item 3** — BATCH duplicate-notification bugfix (independent; no schema).
6. **Item 7** — Today grouping + `ReminderCard` / `WeekStrip` restyle (best-effort; user refines from screenshots).
7. **Item 8** — bloat: hide / move / fold last, once the new homes (About, Appearance → Layout) exist. (8.7 is out of scope; 8.10 stays — grep confirms it is still reachable.)

Then build the signed release APK and run the full verification gate.
