# HEALTH_AND_WORKOUT_PLAN.md

**Status: LOCKED — READY FOR IMPLEMENTATION.** Every product question is decided. PLAN ONLY — no
`.kt`, `.xml`, `.gradle.kts` or `.json` file has been created or modified to produce this
document; the only file any planning pass writes is this one.

---

## 0. Verified baseline

Read from the live repo, branch `main`, commit `66bca0c` plus the shipped Round 0 commit,
working tree clean (the only untracked file is this plan):

- **Room DB version 21** (`MIGRATION_2_3` … `MIGRATION_20_21`, `app/schemas/…/3.json`–`21.json`).
- **Month-partitioned Firestore sync is already shipped**, not in-flight: `formatVersion = 3`,
  `users/{uid}` parent + `users/{uid}/months/{YYYY-MM}`, gzipped blobs, `MonthPartitioner`, lazy
  hydration, eviction, pinning, the D2 conflict flow — all live in
  `data/sync/CloudSyncRepository.kt`.
- **versionName / versionCode: 0.5.7 / 25** (`app/build.gradle.kts:34–35`), post Round 0.
- **Tabs: Today / Habits / Intake**, in a `HorizontalPager`, tab set user-configurable
  (`app_settings.nav_tabs` CSV + `ui/NavConfig.kt`). This stays exactly three routes — Workout
  mode does not join it (§3.6).
- **Single Gradle module `:app`.** `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`, AGP
  **8.3.2**, Gradle wrapper **8.6**, Kotlin **2.0.21** (K2) + `kapt`, JDK 17 target.
- Every third-party pin in `app/build.gradle.kts` carries an explicit comment of the form *"X is
  the last release that builds against compileSdk 34"* — `firebase-bom:33.1.2`,
  `androidx.credentials:1.3.0`, `androidx.biometric:1.1.0`,
  `androidx.security:security-crypto:1.1.0-alpha06`. **compileSdk 34 is a deliberate, documented
  freeze, not an accident** (§5.1 is entirely about this).
- Hilt DI: `di/DatabaseModule.kt` (Room + every repository), `di/FirebaseModule.kt`.
  `DaybookApplication` is `Configuration.Provider` with a `HiltWorkerFactory`; WorkManager's own
  startup initializer is stripped in the manifest.
- WorkManager jobs today: `WindowRefreshWorker` (daily; also drives
  `CloudSyncRepository.runMaintenance()`, runs signed-out) and `SyncFlushWorker` (one-shot on
  `ON_STOP` when a push is pending).
- Sync bookkeeping deliberately lives in **SharedPreferences** (`SyncStateStore`, file
  `daybook_prefs`), never Room — a Room write would re-trigger the `InvalidationTracker` observer
  that is the local-change signal. Any new sync-adjacent cursor/token follows the same rule.
- `CloudSyncRepository.DATA_TABLES` is the tracked-table array, guarded by a tripwire unit test
  `data/sync/DataTablesSyncTest.kt` that asserts it matches `AppDatabase`'s entity list — **but
  that test is one-directional**: it catches a table present in the DB and missing from
  `DATA_TABLES`'s *shape*, not a table someone forgot to add to the literal list at all. Adding a
  new table to `DATA_TABLES` is a manual step every round must not skip (R12).
- Backup/sync wire model: `data/backup/BackupModel.kt` (`DaybookBackup` v2 = `meta` +
  `definitions` + `days`). `ContentHash` hashes **`definitions` + `days` only**, never `meta`.
  `ExportImportRepository` is the single translator between Room and that model.
- Design system: `ui/theme/Tokens.kt` (`DaybookColors`, `Spacing`, `DaybookText`, `Motion`,
  `CardTints`), `ui/theme/Shapes.kt` (`AppShapes` via `LocalDaybookShapes`, corner-scale aware),
  `ui/components/Components.kt` (`SoftCard`, `SectionHeader`, `PrimaryButton`, `GhostButton`,
  `CircleIconButton`, `EmptyState`), `ui/components/Navigation.kt` (`DaybookScaffold` +
  `FloatingPillNav`), `ScreenHeader`, `SegmentedControl`, `Sheets`, `SortSheet`,
  `ConfirmDeleteDialog`, `UndoSnack`, `StickySaveBar`.
- 87 unit tests + 3 instrumented tests already exist. The project's habit is **pure-function
  extraction + a unit test per decision**. New work matches it.
- **Daybook has exactly two confirmation/error UI patterns, and nothing in this document may
  introduce a third:**
  1. **`ui/components/UndoSnack.kt`** — `BoxScope.UndoSnack(token: Int, text: String = "Undone")`.
     A pill pinned `Alignment.BottomCenter`, `padding(bottom = 96.dp)`, `AppShapes.pill`,
     `DaybookColors.SurfaceElevated` + `DaybookColors.Hairline` border, `DaybookText.CardSubtitle`
     / `DaybookColors.TextPrimary`, visible ~2.6 s, honours `LocalReduceMotion`. No action button
     — it confirms, it does not offer.
  2. **The fixed-height inline result slot** in `ui/settings/SettingsScreen.kt` (~line 1044) —
     `Box(Modifier.fillMaxWidth().heightIn(min = 36.dp))` holding a `DaybookText.Caption` `Text`
     coloured `DaybookColors.Success` when `msg.startsWith("Exported ") ||
     msg.startsWith("Import successful")` and `DaybookColors.Danger` otherwise. Fixed height so
     feedback never shifts the layout. This is the pattern for anything the user must *read and
     act on*.
  Plus `ui/components/DaybookAlertDialog.kt` for confirm-before-acting, and `ConfirmDeleteDialog`
  for destructive confirmation. **`Toast` and `SnackbarHost` do not exist anywhere in the app and
  must not be introduced.**
- **Friendly error mapping already exists and must be reused, not re-invented:**
  `ExportImportRepository.friendlyImportError(e, fallback)` maps `SQLiteException` → *"Couldn't
  save the imported data — try again, or restart the app if it keeps happening."*, `IOException`
  → *"Couldn't read the file. Try picking it again."*, `SerializationException` → *"That file
  doesn't look like a Daybook backup."*, and **logs the raw throwable via `Log.e` rather than
  discarding it**. The old-format rejection constant is `ExportImportRepository.UNSUPPORTED` =
  *"This backup was made by an older version of Daybook and can't be restored."*
- **An occurrence-revert path already exists and is correct** — `OccurrenceScheduler.revertFoodMed`
  / `.revertHabit`, wired up by Round 0 (§2.4).
- **The Habits tab's route id is literally `"routines"`.** `NavConfig.kt:14` —
  `val ALL_ROUTES = listOf("home", "routines", "foodmed")`, and `MainActivity.kt:539` maps
  `"routines" to NavItemSpec("routines", habitsIcon, "Habits")`. **The word "routines" is already
  taken, by Habits, at the route level.** Beast Mode's Routine concept (§3.2.1) never uses
  `"routines"` as a route id, and its screen file is not called `RoutinesScreen.kt`
  (`ui/routines/RoutinesScreen.kt` already exists and is the **Habits** screen). §3.6.6 uses
  `"workout"` for the landing and `WorkoutHomeScreen.kt` for the file (R24).
- **`ScreenHeader` is the tab-screen header, and its trailing slot is a `RowScope`.**
  `ui/components/ScreenHeader.kt:29` — `ScreenHeader(title, subtitle, modifier, actions)`, a
  `Column` of a `Row(BigHeadline(weight 1f), actions())` over an optional muted subtitle. Both tab
  screens call it identically: `RoutinesScreen.kt:65` passes `title = "Habits", subtitle =
  "${habits.size} active"` and `actions = { Avatar(…, size = 40.dp, onClick =
  onNavigateToSettings) }`; `FoodMedScreen.kt:63` does the same for Intake. §3.7.4 mirrors this
  exactly: same composable, same 40 dp trailing control, same corner.
- **`CircleIconButton` already takes a size and is the app's icon-button.** `Components.kt:118` —
  `CircleIconButton(icon, contentDescription, onClick, modifier, style = CircleStyle.Ghost, size =
  44.dp, enabled = true)`. Passing `size = 40.dp` puts it in exactly the slot `Avatar(size =
  40.dp)` occupies on the other two tabs.
- **`Icons.Filled.Settings` is available without a new dependency** — the app aliases
  `androidx.compose.material.icons.Icons` as `MI` and already uses `MI.Filled.Person`,
  `MI.Filled.DateRange`, `MI.Filled.MoreVert`, `MI.Filled.Edit`. `Settings` is in the same
  material-icons-core set, so the gear in §3.7.4's header costs no new vector and no
  `material-icons-extended` dependency (deliberately removed from this project).
- **`DaybookIcons` are `ImageVector`s built in code, and `NavItemSpec.icon` is an
  `ImageVector`.** `Navigation.kt:37` — `data class NavItemSpec(val route: String, val icon:
  ImageVector, val label: String)`. The three existing nav icons happen to be inflated vectors,
  but nothing requires that — Beast Mode's second and third nav icons are `DaybookIcons.Clock` and
  `DaybookIcons.Category`, no new XML, no inflate risk (§3.6.7). Only `ic_workout.xml` is new.
- **`DaybookScaffold` hides the bottom nav for every stacked route, with no new plumbing.**
  `MainActivity.kt:608` calls it with `showNav = onMain`, where `onMain = backStackRoute == null
  || backStackRoute == "main"`. Every non-`main` `composable(...)` in the `NavHost` already
  renders full-screen with no pill nav. Every stacked workout route gets this for free — except
  Beast Mode's own three nav destinations, which want the opposite; `showNav` gains one disjunct
  for those (§3.6.7), the only edit to `DaybookScaffold`'s call site in the round.
- **`FloatingPillNav` currently has no long-press path.** `Navigation.kt:108` attaches
  `.clickableImpl(...)`. `clickableImpl` (`Components.kt:106`) has no `onLongClick` sibling;
  §3.6.1 adds one next to it.
- **There is no coach-mark, tooltip, spotlight or first-run-tip component anywhere in the app.**
  `app_settings.onboarding_completed` is the only "have they seen this yet" flag that exists, and
  it is about the onboarding wizard, not individual features. §3.6.3 builds a minimal one out of
  `UndoSnack`'s existing visual recipe.
- **The accent palette is exactly five values** (`ui/theme/Accent.kt:18`): `MINT`, `LAVENDER`
  (the default), `CORAL`, `SKY`, `AMBER`, each with a `dark` and a `light` value and a
  `colorFor(dark)` resolver. `LocalAccent` is a `staticCompositionLocalOf` provided once by
  `DaybookTheme` (`Theme.kt:153`) — re-providing it for a subtree is a supported, one-line move
  (§3.8.3).

### 0.1 Post-Round-A baseline — re-verified against the live repo for this revision

Round A ("Beast Mode") is no longer a plan — it is shipped, at **v0.6.2, build 34**, and has been
through one bug-fix pass beyond its original phase list. Everything below is read from the live
code, not the original A-phase description, and is what Round B's rewrite (§5–7) is now written
against:

- **`AppDatabase.version = 23`.** `MIGRATION_21_22` is Round A's six-table migration exactly as
  planned. **`MIGRATION_22_23` already exists and is NOT free for Round B** — it is a small,
  unrelated, already-shipped migration that added the `workout_accent_color` default plumbing for
  Beast Mode's own accent picker. **Round B's migration is therefore `MIGRATION_23_24`, taking
  `AppDatabase.version` 23 → 24**, and every reference to "`MIGRATION_22_23`" or "DB v23" for
  Round B elsewhere in this document (§7.2, §7.6, §8, §9, §10) is superseded by this fact and
  corrected in place below.
- **Beast Mode grew its own design system, not just its own accent.** `ui/workout/beast/
  BeastTheme.kt` defines `BeastAccentColor` — a **separate 9-value palette** (the original 5
  `AccentColor` values plus 4 new, more saturated "gym" colours: `CRIMSON`, `ELECTRIC`, `VOLT`,
  `VIOLET`), a `BeastPalette` (its own near-black dark-mode ground + accent vignette, distinct
  from `DaybookColors.Bg`), and `BeastText` (a bold tabular-figure numeral treatment for
  live-session numbers). `ui/workout/beast/BeastComponents.kt` supplies Beast-only composables on
  top of this. **This is a deliberate, already-shipped divergence from C5** (§1) inside Beast
  Mode's own UI — recorded as a standing carve-out in §1 rather than re-argued here, since it is
  now precedent, not a new proposal. Round B's health surface follows the same rule: it draws from
  `BeastTheme`/`BeastComponents`, never from `DaybookColors`/`AppShapes`/`DaybookText`.
- **Beast Mode's bottom nav is exactly as originally planned, three items** (`ui/MainActivity.kt`
  `beastNavItems`, ~line 729): `WorkoutRoutes.HOME` ("Routines"), `WorkoutRoutes.HISTORY`
  ("History"), `WorkoutRoutes.LIBRARY` ("Exercises") — the third currently renders
  `AddExerciseScreen` in `BROWSE` mode, i.e. the exercise catalog browser. **§7.4 repurposes this
  exact slot** — same route constant, same nav position, same icon slot — into the health data
  page. The exercise browser itself is not deleted from the codebase (still reachable from the
  live session's `+ Add Exercise` in `PICK` mode); it simply stops being a bottom-nav destination.
- **`WorkoutSettingsScreen.kt` / `WorkoutSettingsViewModel.kt` already exist and already hold more
  than §3.8.2 originally specified** — a Beast Mode accent-and-font group, a weight-unit /
  rest-timer / default-exercise-group / "Show Beast Mode on Today" group, an "Import from Hevy"
  group, and the "Leave Beast Mode" row. §7.4 adds Round B's Health Connect settings as one more
  `SettingsGroup` in this same file, using the same `SettingsGroup`/`SettingsRow` primitives —
  nothing new is invented.
- **`ui/settings/SettingsScreen.kt`'s "Backup & data" row opens `DataSettingsScreen`
  (`ui/settings/SettingsScreen.kt:934`)**, which today has exactly one export shape (`Export
  range` → `viewModel.exportRange`, full export via `shareLatestExport`) and one import shape
  (`Import JSON` → confirm dialog → `OpenDocument` → `viewModel.importFromUri`), plus the separate,
  non-destructive `Import from Hevy` CSV row. **§7.5.1's split model adds a second export button and
  a second import button to this same screen** — it does not add a second screen.
- **`CloudSyncRepository.DATA_TABLES`** (`data/sync/CloudSyncRepository.kt:1390`) today lists
  exactly the eight pre-existing tables plus Round A's six: `"exercises"`, `"workout_sessions"`,
  `"workout_exercises"`, `"workout_sets"`, `"workout_routines"`, `"workout_routine_exercises"`.
  Round B adds `"health_days"`, `"health_sessions"` to this same array (§7.5).
- **`BackupModel.kt` already carries Round A's shape exactly as planned** — `Definitions
  .customExercises` / `.routines` (both `@EncodeDefault(NEVER)`-empty) and `DayEntry.workouts`
  (`@EncodeDefault(NEVER)`-empty), with `ExerciseDef`, `RoutineDef`, `RoutineExerciseDef`,
  `WorkoutLog`, `WorkoutExerciseLog`, `WorkoutSetLog` all present and unchanged. **§7.5.1's split
  export/import is a change to how these fields are *serialised into files*, not a change to any
  of these Kotlin shapes** — no field is removed, renamed or moved between classes.

---

## 1. Standing constraints for both rounds (non-negotiable)

- **C1. No `git commit`, no `git push`, no tag, no Firebase App Distribution upload.** Someone
  else does that.
- **C2. Existing local Room data must survive.** Every migration is additive (`CREATE TABLE` /
  `ALTER TABLE … ADD COLUMN`). No table rebuild, no row deletion, no
  `fallbackToDestructiveMigration` beyond the two already registered
  (`fallbackToDestructiveMigrationFrom(1)`, `…OnDowngrade()`).
- **C3. Existing Firestore cloud data must survive.** `formatVersion` stays **3**. The parent-doc
  and month-doc field names (`definitions`, `definitionsHash`, `monthHashes`, `payload`,
  `contentHash`, `revision`, `deviceId`, `formatVersion`, `appVersion`) are untouched. All new
  payload content is *inside* the existing gzipped blobs as **optional, default-absent** fields
  (§4.3) — a device on an older build still decodes every month doc via `ignoreUnknownKeys =
  true`.
- **C4. Any Room migration and any change to `BackupModel.kt` / `ContentHash` input needs
  explicit, separate user sign-off before a line is written.** Both rounds need both. **Signed off
  for Round A**: the additive `MIGRATION_21_22` and the `BackupModel.kt`/`ContentHash` payload
  additions are approved, with the multi-device caveat noted (§4.3) — update every signed-in
  device in the same sitting, or an older build silently drops the new fields on its next save.
- **C5. No visual regression.** No new colour literal outside `ui/theme/`, no new shape outside
  `AppShapes`, no new type ramp outside `DaybookText`. Every new screen is built from the existing
  components listed in §0. New surfaces honour `LocalReduceMotion`, `LocalDaybookShapes` (corner
  scale), `LocalIsDark` and the accent locals.
  - **Superseding carve-out, already shipped, restated so it isn't mistaken for a new
    exception: everything inside Beast Mode is exempt from the "outside `ui/theme/`" clause and
    draws instead from `ui/workout/beast/BeastTheme.kt` and `BeastComponents.kt`** —
    `BeastAccentColor` (its own 9-value palette), `BeastPalette` (its own ground/vignette) and
    `BeastText` (its own numeral treatment) are the design system for every route in
    `WorkoutRoutes.ALL`, not `DaybookColors`/`AppShapes`/`DaybookText` (§0.1). This carve-out is
    scoped exactly to Beast Mode's own screens; **C5 governs the main app, unchanged** — nothing
    outside `WorkoutRoutes.ALL` may reach into `BeastTheme`/`BeastComponents`, and nothing inside
    Beast Mode may introduce a *third* design system on top of these two. Round B's health tab
    and its Beast-Mode-Settings additions are unconditionally inside this carve-out (§7.4).
- **C6. Offline-first stays true.** Nothing new may block app launch, and every Health Connect /
  Firestore call stays failure-inert (`runCatching` + a logged/Crashlytics-recorded failure, never
  a crash and never a blocking spinner on the launch path) — and never a silently-dropped failure:
  every user-triggered action that can fail must tell the user it failed, in plain language, even
  if the underlying cause is technical (see C9).
- **C7. Battery discipline.** This project has already had a battery/heat regression round
  (`BATTERY_BUG_AUDIT.md` / `BATTERY_FIX_PLAN.md`). New flows use `collectAsStateWithLifecycle`,
  `flowOn(Default)` + `WhileSubscribed(5s)`, and no new always-on ticker, foreground service or
  exact alarm.
- **C8. One round, one migration.** Each round bumps `AppDatabase.version` exactly once and
  exports exactly one new `app/schemas/…/NN.json`, and adds exactly one `MIGRATION_x_y`
  registered in `DatabaseModule` and covered by a new case in `androidTest/…/MigrationTest.kt`.
- **C9. No error is silently swallowed, anywhere in this plan.** A `runCatching` whose failure
  branch only logs is not acceptable for anything the user started. Concretely:
  1. **Every user-triggered action that can fail must surface its failure in the UI**, using one
     of the two patterns §0 records — `UndoSnack` for a transient "that didn't work" on an action
     the user can simply retry, and the **fixed-height inline result slot** (or an equivalent
     inline status row / `DaybookAlertDialog`) for anything the user must read, understand or act
     on. No new third pattern, no `Toast`, no `SnackbarHost` (C5).
  2. **Plain language, never a raw exception message.** Route through
     `ExportImportRepository.friendlyImportError`'s idiom: a mapped, human sentence for the user;
     the raw throwable to `Log.e` / Crashlytics, never discarded.
  3. **A failure must never be indistinguishable from a success.** The specific trap this
     constraint exists to prevent is the one already fixed once in this codebase
     (`LOGIN_REDESIGN_RISK_FIX_PLAN.md` Phase 9, C-4: `logFoodMed` used to `return` silently on a
     missing row while the caller reported "saved"). The `LogResult.Rejected(reason)` shape that
     fix introduced is the pattern to copy for any new suspend action that can be refused.
  4. **Background, non-user-triggered work is the one exception, and it is bounded.** A daily
     `WindowRefreshWorker` pass or an automatic sync pull may fail quietly *in the moment* —
     interrupting someone to say a background poll failed is worse than useless — but its outcome
     must still be readable somewhere the user can go and look: a status row in Settings (Round
     B: §6.2's status line, which lives in Beast Mode Settings per §7.4's placement decision, and
     must show the last failure, not just the last success). "Quiet" is allowed; "unknowable" is
     not.
  5. This constraint applies to every section of this document.

---

## 2. Sequencing

**Round 0 (intake reset fix) is shipped, as v0.5.7. Next: Feature 2 (Workout) as Round A, then
Feature 1 (Health) as Round B.** Round C (a universal activity-tracker vision) is backlog and not
scheduled (§2.2 explains why).

Why Workout before Health:

- Workout mode needs **zero new dependencies, zero new runtime permissions, zero
  external-device testing, and zero toolchain risk**. It is entirely in-repo work using patterns
  the codebase already has three examples of (Habit / FoodMedTask / CustomCategory → Room →
  `ExportImportRepository` → month blob → UI tab).
- Health sync is gated on a real toolchain decision (§5.1), a new Android permission model with an
  OS-owned consent UI, and can only be meaningfully verified with the actual Mi Band 10 + Mi
  Fitness on a physical phone. Bundling it with Workout means one bad variable contaminates both.
- Doing them as one round would put five new tables, two new wire-model fields, a new full-screen
  mode with a new entry gesture and a new dependency and permission flow behind one migration —
  which violates C8's spirit and makes the round un-revertable in pieces.

Version plan — updated for this revision. Round A shipped, then took one additional bug-fix pass
that consumed a migration slot the original version plan had reserved for Health:

| Round | Contents | DB | New schema JSON | versionCode / versionName |
|---|---|---|---|---|
| **0** | Intake reset fix (§2.4) | 21 — no migration | none | **25 / "0.5.7"** — SHIPPED |
| **A** | Workout mode | 21 → **22** | `22.json` | **26 / "0.6"** — SHIPPED |
| **A-fix** | Beast Mode bug-fix pass (own accent palette + font, session/routine error feedback, exercise picker, history, Hevy import) | 22 → **23** | `23.json` | **34 / "0.6.2"** — SHIPPED, current baseline |
| **B** | Health Connect, placed inside Beast Mode (this revision) | 23 → **24** | `24.json` | **35 / "0.6.5"** |

`MIGRATION_21_22` is the **workout** migration, `MIGRATION_22_23` (already shipped) was the
**Beast-Mode-accent-settings** migration, and **`MIGRATION_23_24` is Round B's health migration**
— every later reference in this document to "`MIGRATION_22_23`" as Round B's migration, or to "DB
v23" as Round B's target version, means `MIGRATION_23_24` / v24 (§0.1). versionCode jumped from 27
to 34 across the shipped A-fix pass, so Round B's version is **35 / "0.6.5"**, not the originally
planned 27 / "0.6.1" — bumped up along the 0.6.x line rather than to a new minor, keeping Round B
adjacent to the Beast Mode work it extends.

### 2.1 Why we are **not** adopting the PRD's backend architecture

An external product document (referred to throughout as "the PRD") proposes:

```
Android App → Room → Sync queue → WorkManager → Backend API → PostgreSQL   (Supabase suggested)
```

with every mutation carrying an `operation_id`, `entity_id`, `timestamp`, `version` and
`operation_type`, batched to a server that resolves conflicts deterministically.

**Rejected in full. Daybook keeps Room + Firestore exactly as it is today.** Not deferred —
rejected, because adopting it would mean building a second sync system next to a working one.

**What Daybook already has**, verified by reading the live code
(`data/sync/CloudSyncRepository.kt`, `SyncStateStore.kt`, `MonthPartitioner.kt`, `ContentHash.kt`,
`PayloadCodec.kt`, `SyncLogic.kt`):

| PRD requirement | Daybook's existing mechanism |
|---|---|
| "Every action writes locally first" | Room is the source of truth, unconditionally — every write is a Room write, nothing writes to the network on a user action |
| "Sync queue records the mutation" | `InvalidationTracker.Observer(DATA_TABLES)` — Room *itself* is the change queue; no hand-maintained queue table can drift out of step with the data |
| "Batch sync, not one request per action" | A debounced push (`changes.debounce(DEBOUNCE_MS)`) plus `SyncFlushWorker`, one-shot on `ON_STOP` when a push is pending |
| "WorkManager for deferrable background work" | Already exactly this: `WindowRefreshWorker` (daily) + `SyncFlushWorker` (one-shot). No polling service, no repeating alarm, no wake lock |
| "Conflict handling — never blindly overwrite" | The D2 conflict flow: `ConflictInfo` with concrete both-sides row counts, a user-facing prompt, `conflictPaused` halting sync for the session, `conflictAlreadyResolved` so the prompt isn't re-shown |
| "`version` per mutation" | `revision` on the parent doc plus `contentHash` per month and `definitionsHash` for definitions — content-addressed rather than counter-addressed: it cannot drift, and an unchanged month is provably unchanged |
| "Server-side access control" | Firestore security rules, `users/{uid}` owner-match — no server to run, patch, pay for or keep up |
| "Don't load everything into memory" | Month partitioning with lazy hydration, eviction and pinning |

**The specific reasons to reject it:**

1. **There is no server, and adding one is a category change, not a feature.** Daybook is a
   sideloaded, single-developer, offline-first journal app whose only remote component is
   Firestore used as per-user storage. A backend API means an operational surface that must be
   deployed, monitored, secured, migrated, backed up and paid for forever, and becomes a single
   point of failure for data that currently cannot be lost by any outage because it lives in Room.
2. **A hand-written mutation queue would be strictly worse than the `InvalidationTracker`.** A
   queue table is a second copy of "what changed" that can disagree with the first. This project
   has already been bitten once by a second copy of truth drifting; the lesson points the other
   way from the PRD.
3. **`operation_id` + per-mutation `version` solves a problem Daybook does not have.**
   Per-operation versioning exists to merge concurrent edits from many writers. Daybook has one
   human, on one or two devices, usually not simultaneously. The month-level content hash plus
   the D2 prompt is the correctly-sized answer.
4. **It would break C3 outright.** `formatVersion` is 3 and there is live user data in the
   existing shape. Migrating to Postgres means a data migration of the user's real history across
   two storage engines, with no rollback, for zero user-visible benefit.
5. **It contradicts the PRD's own stated priorities** — battery and offline reliability rank
   highly in it, and it warns against "network request on button tap" and "every-30-seconds API
   calls". Daybook's debounced, batched, `ON_STOP`-flushed model is already that shape.

**What this means for anyone implementing Round A, B or C:**

- Do not add a `sync_queue` / `mutations` / `pending_operations` table.
- Do not add `operation_id`, `entity_version`, `last_modified_by` or `dirty` columns to any entity.
- Do not add Supabase, Postgres, Ktor, Retrofit, or any HTTP client.
- Do add new tables to `CloudSyncRepository.DATA_TABLES` (§4.1) and to the six
  `ExportImportRepository` call sites (§4.4) — that is the whole of "wiring up sync" in this
  codebase, and it is why Round A's sync work is one phase (A4) rather than a project.
- Watch payload size against Firestore's 1 MiB per-document cap (§4.6 shows the headroom is
  comfortable).

### 2.2 Product scope: the concrete gym logger, not a universal activity engine

The PRD's architecture is wrong for this app; its product thinking mostly is not. Three things in
it are correct: the four-layer model Activity → Routine/Program → Workout → Progress is the right
way to think about training data; "the product should not say *we don't have that exercise*" — the
user creates it; and "less UI, more logging" during a live set, with a P0/P1/P2 discipline that
says don't build the AI coach before the logger is fast.

Round A already does much of this: Hevy-parity gym logging, custom exercises, previous/best
lookups, PRs, rest timers, notes, import/export, offline-first. A larger part of the PRD's vision —
Routines-as-Programs, periodization, deloads, substitution, a generic metrics engine, multi-sport
custom activities, training load, a fatigue dashboard — is a genuinely bigger system, captured in
**Round C**, explicitly not started or scheduled.

**Round A builds the concrete four-table schema of §3. The generic Activity/Metric engine is
Round C.**

| | Concrete Round A (chosen) | Generic Activity/Metric engine (→ Round C) |
|---|---|---|
| Schema | 4 tables, typed columns | ~6 tables, values in an EAV-shaped `metric_value` table |
| Queries (§3.4) | `ORDER BY weight_kg DESC` — one indexed read | a pivot/join per metric per set |
| Live set table (§3.7.1) | `columnsFor(trackingMode)` — 4 cases, unit-testable, ~30 lines | columns from the activity's metric definitions; every cell a dynamic typed editor |
| Hevy CSV import (§3.9) | direct column mapping | synthesise metric definitions, then write values as rows |
| Yoga / running / football / custom drills | not supported in Round A | supported by construction |

Three reasons for the concrete schema:

1. **The user asked for a gym logger.** Every artefact supplied — Hevy screenshots, a Hevy CSV
   export — is barbell-and-dumbbell gym logging.
2. **A generic metrics engine is meaningfully harder to implement correctly**, and this plan is
   written to be executable by a less-capable implementing model. An EAV schema moves
   type-safety from the compiler into runtime convention, turns every read into a pivot, and
   makes the wire model and the `ExportImportRepository` call sites substantially harder — and
   item 6 of §4.4 is already the single easiest place in this plan to silently lose cloud data.
   Scope ballooning here does not produce a worse workout tracker; it produces no workout
   tracker.
3. **This app is a habit tracker adding its first workout feature.** Build the engine, benchmark
   it on a real device, then layer on top.

**Does the concrete schema foreclose the generic engine later? No — three specific properties
keep the door open, checked rather than assumed:**

- **`Exercise.trackingMode`** (`"WEIGHT_REPS" / "REPS_ONLY" / "DURATION" / "DISTANCE_DURATION"`)
  is already a coarse metric-set descriptor. Each value names exactly which of `WorkoutSet`'s
  nullable columns are meaningful. Migrating means: for each `trackingMode`, emit the
  corresponding fixed set of `activity_metric` rows — a pure, total, unit-testable function over
  four cases.
- **`WorkoutSet`'s value columns are already all nullable**, and blank-is-not-zero (§3.9.3) is a
  hard rule. Converting one row to N `metric_value` rows is `listOfNotNull`-shaped.
- **`WorkoutExercise` already exists as a distinct block entity** (§3.2), precisely the PRD's
  `workout_block`.
- **`Exercise.source` and `WorkoutSession.source`** already distinguish provenance.

**Three requirements keep this reversible, and they are binding on every phase of Round A:**

- **Ri1.** `trackingMode` values are never reused for two different metric sets, and the string
  values are permanently stable — the same rule §3.3 applies to `builtin:` ids.
- **Ri2.** No code outside `columnsFor(trackingMode)` (§3.7.1) and the mapping functions in §3.4
  may branch on `trackingMode`. One choke point means one function to replace.
- **Ri3.** No value column may ever be given a non-null default (e.g. `weight_kg REAL NOT NULL
  DEFAULT 0`). This is the one change that would genuinely foreclose the generic engine — it
  destroys the distinction between "0 kg" and "this metric does not apply", breaks the
  blank-is-not-zero rule, and corrupts PR detection. R18 guards it with a migration test.

### 2.3 PRD engineering advice adopted into Round A

The PRD's non-architectural engineering guidance costs almost nothing and materially improves
quality, so it is folded into Round A as requirements — collected in §3.10 (Performance
discipline): per-set immediate writes, no whole-screen recomposition on a timer tick, no
recalculating history on every set, cold/warm start budgets, lazy lists with stable keys and
immutable UI models, and Baseline Profiles deferred to a future toolchain round. The rest-timer
battery rule and "ship no exercise media library" were already decided independently, for the same
reasons, and offline-first was already true.

### 2.4 Round 0 — already shipped

Round 0 fixed a real bug: a mistakenly-logged intake item had no way back to "not logged". The
data layer already existed (`OccurrenceScheduler.revertFoodMed` / `.revertHabit`) and was
correct; the defect was a three-place UI gap — the Today card's overflow sheet offered "Edit"
*instead of* "Undo" for a logged item, the Respond screen's intake form had no Undo control at
all, and the item's history screen routed straight into that same dead end. The fix added a
`Reset to not logged` action to the Respond screen's intake form (behind a confirm dialog, since
it discards typed text), turned the Today sheet's `else if` into two independent `if`s so a
logged row offers both Edit and Undo, and made both paths surface their failure instead of
swallowing it (C9). Resetting a past, no-longer-backfillable entry correctly shows "Missed" — the
entry genuinely was not logged — and can shorten a streak; that is correct, not a bug. No
migration, no new sync handling: `food_med_occurrences` was already tracked, and a reverted row
serialises through the existing wire shape.

**Shipped as v0.5.7 (build 25). Not a work item — history.**

---
# ROUND A — Workout mode

## 3. Feature 2: Workout mode ("Beast Mode", gym logging)

### 3.1 Scope

**What the reference screenshots (a Hevy export) actually show**, recorded because the rest of §3
is a direct response to it: an **Add Exercise** full-screen page — header `Cancel` / *Add
Exercise* / `Create`, a `Search exercise` field, two equal half-width filter buttons `All
Equipment` and `All Muscles` each opening its own bottom sheet, a `Recent Exercises` section of
rows (round illustration + name + muscle-group subtitle + a trailing trend-arrow button). A
**live session** screen — header with a collapse chevron, title that swaps to elapsed time on
scroll, an alarm-clock icon opening rest-timer defaults, and a blue `Finish`; a stats row
(`Duration` / `Volume` / `Sets`) with front/back body silhouettes shading worked muscles; each
exercise a card with a thumbnail, accent-coloured name, an `Add notes here…` field, a `Rest
Timer: OFF` row, and a set table whose **columns change with the exercise** (`SET · PREVIOUS · KG
· REPS · ✓` for a weighted exercise, `SET · PREVIOUS · REPS · ✓` for bodyweight); a completed row
turns solid green with a filled check; a set beating a personal record shows a gold medal instead
of its set number; `+ Add Set` per exercise; `+ Add Exercise` / `Settings` / `Discard Workout` at
the bottom.

**In scope for Round A:**

- **Entering and leaving the mode**: a long-press on the Today nav item opens Beast Mode as a
  full-screen mode with its own three-item bottom nav; a long-press on Beast Mode's own leftmost
  nav item leaves it again, mirroring the way in; the normal back gesture also leaves from the
  landing screen; a one-time coach-mark and an always-present Settings → `Open Workout` row make
  it findable; and a `Beast Mode` row on Today ships too — all three entry points, unconditional
  (§3.6).
- **Beast Mode has its own Settings screen** (§3.8.2), reachable only from inside it. Daybook's
  main Settings keeps one row: `Open Workout`.
- **Routines** — a named, reusable, ordered list of exercises with optional per-exercise target
  sets / reps / weight / time / distance / rest. Create, edit, duplicate, delete; start a session
  from one and the session comes pre-populated. Deliberately minimal — a *template*, not the
  PRD's Programs / periodization / weekly-cycle layer, which stays in Round C (§C.3.2). §3.2.1,
  §3.7.4, §3.7.5.
- Log a workout **session** (date, optional title, optional note, start/end).
- Add **exercises** to a session, from a built-in catalog and from user-created custom exercises,
  through a searchable Add-Exercise screen with muscle-group and equipment filters and a Recent
  section (§3.7).
- Log **sets** per exercise: reps + weight for strength; duration and/or distance for cardio or
  timed work; optional RPE; a set type (normal / warm-up / drop set / failure); optional per-set
  note.
- **Column set follows the exercise's tracking mode** — a bodyweight exercise never renders an
  empty KG column, a plank renders TIME, a cardio row renders DISTANCE + TIME.
- A **`PREVIOUS` column, matched per set number**, from the last session that contained the same
  exercise (§3.4).
- **Personal-record marking** on a set that beats the user's previous best for that exercise
  (§3.4) — computed live, never stored.
- A **live session header**: elapsed duration, total volume in kg, set count.
- A **rest timer between sets — in-app only.** Counts down while the session screen is open; no
  notification, no foreground service, no exact alarm. A countdown that only has to be visible on
  a screen the user is looking at needs none of the machinery that caused the prior battery
  regression: a `LaunchedEffect` keyed on the running timer, ticking a `StateFlow` once a second,
  cancelled automatically the moment the composable leaves composition. Leaving the screen or
  backgrounding the app does not kill the *set* — the timer's target end-time is a timestamp, so
  returning to the screen shows the correct remaining time — it kills the *ticking*. An
  alerts-while-closed version remains a separate future round with its own battery review.
- **Per-exercise notes** inside a session, and per-exercise ordering. If `Exercise.notes` is
  non-blank, it renders pinned under the exercise name (`DaybookText.Metadata` /
  `DaybookColors.TextMuted`), visually distinct from the per-block "notes for today" field —
  something like *"Seat 6, neutral grip"* that shows every time, versus a note about today.
- **Per-exercise history**: a sheet listing every past set of that exercise, newest first, with
  PRs marked — a list, not a chart.
- **Workout history**: past sessions listed newest-first, grouped by date, tappable into a
  session detail that can be edited or deleted.
- A **resume banner** when a session was started and never finished.
- **Importing workout history from a Hevy CSV export** (§3.9).
- Everything syncs to the user's account like the rest of their data.

**Explicitly deferred, with the reasoning that keeps each one a small later addition rather than a
rewrite:**

- **Alerts-while-closed rest timer** — needs a notification channel, a foreground service or an
  exact alarm; a separate round with its own battery review.
- **Programs, weekly cycles, periodization, deloads, progression engines, substitution, "suggest
  my next weight"** — Round C §C.3.3–C.3.6. A routine in Round A is a list you start from, with
  no opinion about when you should do it or what you should lift.
- **Importing routines** (from Hevy or anywhere) — a different file shape from workout history and
  a separate, unstarted feature. `workout_routines.source` exists as a forward-compatible
  provenance hook (§3.2.1) and nothing more.
- **Progression charts** — a per-exercise history **list** ships in Round A; a **chart** is Round
  C (§C.3.9). A line chart is new visual vocabulary (axes, gridlines, a colour ramp, a time-range
  selector) worth doing properly rather than rushed, and the list uses data already stored, so the
  chart lands on top later with no schema change.
- **Superset UI** — no grouping affordance, no coloured rail, no cross-group rest rule. But the
  Hevy CSV carries a `superset_id`, so `workout_exercises.superset_id` stores the grouping
  losslessly on import; Round A's UI reads and writes neither.
- **Plate calculator, 1RM estimator, body measurements** — none appears in any reference
  screenshot; each is its own logging domain with its own units and sync fields.
- **Automatic link to Health-Connect workout sessions** — stays separate; §6.4 argues it at
  length. The Hevy CSV import is a one-shot file transfer, not a live derived-data feed, so it is
  not a counter-example.
- **Exercise artwork and anatomical muscle diagrams** — see §3.3.5. **This one is no longer fully
  deferred**: Round A ships real per-exercise illustrations and real muscle diagrams, sourced from
  a licensed dataset (§3.3), which the Hevy screenshots' visual richness had originally been
  deferred for lack of. What stays deferred is the **live-session muscle-shaded silhouette** — a
  composited, session-wide view that the bundled stills don't supply for free — and a "collapse
  the session into a persistent bar" affordance, for which Daybook's resume banner is the cheaper
  equivalent.

### 3.2 New Room entities (`data/model/WorkoutModel.kt` — new file)

Kept in a new file, not appended to the already-362-line `DataModel.kt`. Room does not care which
file an `@Entity` lives in; `AppDatabase`'s `entities = [...]` array is the only registration
point.

```
@Serializable @Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey id: String            // UUID for custom rows
    name: String
    primaryMuscle: String             // one of MuscleGroup's ~20 values (§3.3.1)
    equipment: String                 // one of Equipment's ~9 values (§3.3.1)
    trackingMode: String              // "WEIGHT_REPS"/"REPS_ONLY"/"DURATION"/"DISTANCE_DURATION"
    isArchived: Boolean = false       // hidden from the picker, never deleted (history keeps referencing it)
    source: String = "USER"           // "USER" | "IMPORTED_HEVY" (§3.9.4). Lets the picker badge
                                      // an auto-created import row, and lets a future "tidy up my
                                      // imported exercises" tool find them. One TEXT column.
    createdAt: Long
    notes: String? = null
)
```

**This table holds ONLY user-created exercises.** The built-in catalog (~600 exercises) is a
bundled asset parsed at runtime (§3.3), not seeded rows and not hand-typed Kotlin — see §3.3 for
why.

```
@Serializable @Entity(tableName = "workout_sessions",
    indices = [Index("local_date"), Index("started_at")])
data class WorkoutSession(
    @PrimaryKey id: String            // UUID
    localDate: String                 // "yyyy-MM-dd" — the SAME timezone-stable convention as
                                      // HabitOccurrence.local_date / FoodMedOccurrence.local_date.
                                      // This is what MonthPartitioner buckets on. Load-bearing.
    startedAt: Long                   // epoch millis
    endedAt: Long? = null             // null == still in progress
    title: String? = null
    notes: String? = null
    status: String = "ACTIVE"         // "ACTIVE" / "COMPLETED"
    source: String = "MANUAL"         // "MANUAL" | "IMPORTED_HEVY". Still reserved for
                                      // "HEALTH_CONNECT" if §6.4 is ever un-deferred.
    routineId: String? = null         // the routine this session was started from, or null for an
                                      // empty/ad-hoc session and for every imported one. NULLABLE,
                                      // and deliberately NOT a foreign key: deleting a routine
                                      // must never delete or orphan the workouts done with it.
    createdAt: Long
)
```

```
// One row per exercise block inside a session.
@Serializable @Entity(tableName = "workout_exercises",
    indices = [Index(value = ["session_id", "order_index"]), Index("exercise_id")])
data class WorkoutExercise(
    @PrimaryKey id: String            // UUID
    sessionId: String
    exerciseId: String                // "builtin:<repdb-slug>" or a custom Exercise.id UUID (§3.3)
    orderIndex: Int                   // 0-based position of this block within the session
    notes: String? = null             // the "Add notes here…" line. Per BLOCK.
    supersetId: String? = null        // preserved losslessly from a Hevy import; nothing in Round
                                      // A's UI reads or writes it.
    restSeconds: Int? = null          // null == "Rest Timer: OFF" for this block. Per block, per
                                      // session — which is what makes "carry my rest setting over
                                      // from last time" a query, not a pref store.
    createdAt: Long
)
```

```
@Serializable @Entity(tableName = "workout_sets",
    indices = [Index(value = ["workout_exercise_id", "set_number"]),
               Index("session_id"), Index("exercise_id")])
data class WorkoutSet(
    @PrimaryKey id: String             // UUID (NOT autoincrement — autoincrement rowids do not
                                       // survive the export/import round trip)
    workoutExerciseId: String          // the owning block
    sessionId: String                  // DENORMALISED ON PURPOSE. Immutable for the row's
                                       // lifetime — what makes month eviction and the chunked
                                       // range export one indexed query instead of a join.
    exerciseId: String                 // DENORMALISED ON PURPOSE, also immutable — what makes the
                                       // PREVIOUS and personal-record lookups (§3.4) index-only on
                                       // the hottest screen in the feature.
    setNumber: Int                     // 1-based within its block; also the PREVIOUS join key
    reps: Int? = null
    weightKg: Float? = null            // ALWAYS stored in kg; lb is a display conversion only
    durationSeconds: Int? = null
    distanceMeters: Float? = null
    rpe: Int? = null                   // 1..10
    setType: String = "NORMAL"         // "NORMAL" | "WARMUP" | "DROPSET" | "FAILURE" — exactly
                                       // Hevy's four CSV values (§3.9.3). Round A's UI offers
                                       // NORMAL and WARMUP; the other two render as a badge and
                                       // round-trip untouched.
    notes: String? = null
    completedAt: Long? = null          // non-null == the green check is ticked
)
```

**No foreign keys with `onDelete = CASCADE`.** The rest of this schema uses plain indexed id
columns with repository-level cleanup, and a real FK would fight the import path, which inserts
children before parents in some orderings. `WorkoutRepository.deleteSession` deletes sets, then
blocks, then the session inside one `database.withTransaction { }`.

**No stored `isPersonalRecord` flag and no stored `previousSet` column.** Both are derived from
other rows, so editing or deleting a historical session would silently invalidate every cached
value downstream of it, and both would have to ride the wire model and be re-derived on every
import anyway. They are queries (§3.4).

#### 3.2.1 Routines — two more tables

**What a Routine is, stated narrowly so it cannot grow:** a named, reusable, ordered list of
exercises, each with optional targets. It is a template you start a session from. It has no
schedule, no week, no cycle, no progression rule and no opinion about what you should lift — all
of which stay in Round C (§C.3.3–C.3.6). A routine is to a session what `food_med_tasks` is to
`food_med_occurrences`: the definition, not the event.

```
// The template itself.
@Serializable @Entity(tableName = "workout_routines",
    indices = [Index("order_index")])
data class WorkoutRoutine(
    @PrimaryKey id: String            // UUID
    name: String                      // "Push day". Normalised + deduped exactly like
                                      // CustomCategoryRepository does category names (§3.4).
    notes: String? = null             // optional, shown under the name on the routine card
    orderIndex: Int                   // 0-based position in "My routines"
    isArchived: Boolean = false       // hidden from the list, never hard-deleted by archiving —
                                      // WorkoutSession.routineId keeps pointing at it.
    source: String = "USER"           // provenance hook mirroring Exercise.source and
                                      // WorkoutSession.source, character for character. Bookkeeping,
                                      // not a permission — an imported routine would be fully
                                      // editable like any other. Importing routines is out of
                                      // scope for this round (see §3.1's deferred list).
    createdAt: Long
    updatedAt: Long                   // bumped on every edit
)
```

```
// One row per exercise slot inside a routine, with its optional targets.
@Serializable @Entity(tableName = "workout_routine_exercises",
    indices = [Index(value = ["routine_id", "order_index"]), Index("exercise_id")])
data class WorkoutRoutineExercise(
    @PrimaryKey id: String            // UUID
    routineId: String
    exerciseId: String                // "builtin:<repdb-slug>" or a custom Exercise.id UUID
    orderIndex: Int                   // 0-based position within the routine
    targetSets: Int? = null           // null == "no target" -> start-from-routine creates NO set
                                      // rows for this exercise and the user taps "+ Add Set"
    targetReps: Int? = null
    targetWeightKg: Float? = null     // ALWAYS kg, like WorkoutSet.weightKg. lb is display only.
    targetDurationSeconds: Int? = null
    targetDistanceMeters: Float? = null
    restSeconds: Int? = null          // null == no routine-level rest opinion; the block then
                                      // falls back to lastRestSecondsForExercise, then to
                                      // app_settings.rest_timer_default_seconds (§3.4)
    notes: String? = null             // copied into WorkoutExercise.notes when a session starts —
                                      // "seat 6, neutral grip" written once, seen every time
    createdAt: Long
)
```

**Every target column is nullable.** `target_reps INTEGER NOT NULL DEFAULT 0` would destroy the
distinction between "3 sets of nothing in particular" and "3 sets of 0 reps", exactly as it would
on `WorkoutSet` — and §3.7.5's editor depends on blank meaning blank (Ri3, R18).

**No foreign keys**, same as §3.2. `WorkoutRepository.deleteRoutine` deletes the
`workout_routine_exercises` rows, then the `workout_routines` row, in one `withTransaction`.
Deleting a routine never touches `workout_sessions` — a session started from a deleted routine
keeps its now-dangling `routine_id`, and §3.7.4's "Start again" affordance simply does not render
when the id resolves to nothing.

**No `exercise_count` / `set_count` column** — both are counts over other rows, the same argument
as `isPersonalRecord`. The routine card's *"5 exercises · 15 sets"* subtitle comes from a `GROUP
BY` query (§3.4 `observeRoutineSummaries`).

---
### 3.3 Built-in exercise catalog

The built-in catalog is sourced from **RepDB's free tier** (repdb.co), a licensed exercise
dataset the user downloaded and cleared: 601 exercises, each with a name, category, equipment,
primary/secondary muscles, instructions, tips, a MET value, and image references — flat-style
WebP illustrations (512×512, a `start`/`peak` pair per exercise or a single `main` for static
holds), plus 27 anatomical muscle diagrams and 56 equipment icons. **Free for personal and
commercial use inside an application, with one required attribution line** — no per-call cost, no
API key, no backend, no network dependency. The whole dataset is a static asset bundled at build
time, exactly like every other Daybook asset.

#### 3.3.1 Taxonomy

Two axes, unchanged from the app's own picker design: **`MuscleGroup`** (~20 values) and
**`Equipment`** (~9 values), both Kotlin `enum class`es in `data/workout/ExerciseTaxonomy.kt`,
persisted as their `name` strings so an unknown future value degrades to `OTHER` rather than
throwing:

`MuscleGroup`: `ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CARDIO, CHEST, FOREARMS,
FULL_BODY, GLUTES, HAMSTRINGS, LATS, LOWER_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS,
UPPER_BACK, OTHER`.

`Equipment`: `NONE, BARBELL, DUMBBELL, KETTLEBELL, MACHINE, CABLE, PLATE, RESISTANCE_BAND,
OTHER`. `NONE` is the bodyweight case (plank, push-up, pull-up, and — per §3.3.2's mapping — any
"bodyweight-aid" apparatus like a pull-up bar, dip station, gymnastic rings or suspension
trainer); `BODYWEIGHT` is deliberately not its own value, because a pull-up is "no equipment" and
making it its own equipment type would hide every bodyweight exercise from the `NONE` filter.

The picker filters on both axes independently (two half-width buttons, two bottom sheets), because
the muscle axis is finer than a body region and the equipment axis is a separate question — a user
looking for "something for biceps with a cable" filters both.

Both are displayed through a pure `MuscleGroupLabels` / `EquipmentLabels` map, unit-tested for
totality, so adding a value cannot ship a screen reading `UPPER_BACK` at the user. **Neither enum
changes for RepDB** — every RepDB muscle and equipment value maps onto an existing entry (§3.3.2).

#### 3.3.2 Deriving muscle group, equipment and tracking mode from RepDB's data

RepDB's own taxonomy is finer than Daybook's by design (45 muscle regions across its full catalog,
56 equipment types) — the right response is not to adopt it wholesale (it would blow up the
picker's two-filter design) but to **project it down algorithmically**, once, at catalog-build
time. No exercise is hand-annotated; three pure functions derive everything from RepDB's own
fields:

```kotlin
fun deriveMuscleGroup(ex: RepDbExercise): MuscleGroup = when {
    ex.category == "cardio" -> MuscleGroup.CARDIO
    ex.bodyPart == "full_body" -> MuscleGroup.FULL_BODY
    else -> REPDB_MUSCLE_MAP[ex.primaryMuscles.firstOrNull()] ?: MuscleGroup.OTHER
}

fun deriveEquipment(ex: RepDbExercise): Equipment =
    ex.equipment?.let { REPDB_EQUIPMENT_MAP[it] } ?: Equipment.NONE   // absent == is_bodyweight

fun deriveTrackingMode(ex: RepDbExercise): String = when {
    ex.category == "cardio" ->
        if (ex.equipment in DISTANCE_CAPABLE_CARDIO_EQUIPMENT) "DISTANCE_DURATION" else "DURATION"
    ex.forceType == "static" && ex.images.flat == listOf("main") -> "DURATION"
    ex.isBodyweight -> "REPS_ONLY"
    else -> "WEIGHT_REPS"
}

val DISTANCE_CAPABLE_CARDIO_EQUIPMENT = setOf(
    "treadmill", "stationary_bike", "elliptical", "stair_climber", "rower", "air_bike"
)
```

Each has a unit test per branch (`ExerciseDerivationTest`), and `deriveTrackingMode` is verified
against real file data, not guessed: RepDB's `force_type == "static"` combined with a single
`main` image variant is exactly the plank/dead-hang/wall-sit shape (§3.1.1's DURATION case);
`is_bodyweight == true` outside that (push-ups, bodyweight squats, lying leg raises) is REPS_ONLY,
not WEIGHT_REPS — getting this wrong would render an empty KG column, which §3.1.1 already
forbids.

**`REPDB_MUSCLE_MAP`** — every RepDB muscle key RepDB's free tier uses, mapped to one Daybook
`MuscleGroup`:

| RepDB muscle | → `MuscleGroup` | RepDB muscle | → `MuscleGroup` |
|---|---|---|---|
| `abductors` | ABDUCTORS | `latissimus_dorsi` | LATS |
| `adductors` | ADDUCTORS | `quadratus_lumborum` | LOWER_BACK |
| `anterior_deltoid` | SHOULDERS | `obliques` | ABDOMINALS |
| `biceps_brachii` | BICEPS | `pectoralis_major` | CHEST |
| `brachialis` | BICEPS | `posterior_deltoid` | SHOULDERS |
| `brachioradialis` | FOREARMS | `quadriceps` | QUADRICEPS |
| `erector_spinae` | LOWER_BACK | `rectus_abdominis` | ABDOMINALS |
| `forearms` | FOREARMS | `rhomboids` | UPPER_BACK |
| `forearm_extensors` | FOREARMS | `serratus_anterior` | CHEST |
| `forearm_flexors` | FOREARMS | `supraspinatus` | SHOULDERS |
| `gastrocnemius` | CALVES | `soleus` | CALVES |
| `gluteus_maximus` | GLUTES | `transverse_abdominis` | ABDOMINALS |
| `gluteus_medius` | GLUTES | `trapezius` | TRAPS |
| `hamstrings` | HAMSTRINGS | `triceps_brachii` | TRICEPS |
| `hip_flexors` | ABDOMINALS *(RepDB files it under `region: core`)* | | |

Unmapped values (there are none in the free-tier set today) fall through to `OTHER` rather than
throwing — the map lookup is `[key] ?: OTHER`, not an exhaustive `when`, so a future RepDB catalog
update that adds a 29th muscle key degrades gracefully instead of crashing the build.

**`REPDB_EQUIPMENT_MAP`** — every RepDB equipment key used by an exercise in the free tier, mapped
to one Daybook `Equipment`. The grouping logic: **free weights** (barbell/dumbbell/kettlebell/EZ
bar/trap bar/landmine/plates) map to their obvious counterpart; **every `*_machine` key and every
cardio machine** (treadmill, stationary bike, elliptical, stair climber, rower, air bike, Smith
Machine) maps to `MACHINE`; **bodyweight-aid apparatus** (pull-up bar, dip station, gymnastic
rings, suspension trainer, climbing rope) maps to `NONE`, matching the app's existing "a pull-up
is no equipment" rule; **conditioning tools with no dedicated Daybook value** (ab wheel, battle
rope, jump rope, medicine ball, slam ball, stability ball, plyo box, wrist roller, sled) map to
`OTHER` — the same treatment `Russian Twist [PLATE]`'s neighbours already got before RepDB, now
generalised. No new `Equipment` value is added for "Smith Machine" or "Medicine Ball" — both
collapse into an existing bucket (`MACHINE`, `OTHER`) rather than growing the enum, keeping the
picker's two-filter UI exactly as designed.

| RepDB equipment | → `Equipment` | RepDB equipment | → `Equipment` |
|---|---|---|---|
| `barbell` | BARBELL | `hack_squat` | MACHINE |
| `ez_bar` | BARBELL | `hip_abduction_machine` | MACHINE |
| `trap_bar` | BARBELL | `hip_adduction_machine` | MACHINE |
| `landmine` | BARBELL | `hip_thrust_machine` | MACHINE |
| `dumbbell` | DUMBBELL | `lat_pulldown_machine` | MACHINE |
| `kettlebell` | KETTLEBELL | `lateral_raise_machine` | MACHINE |
| `cable` | CABLE | `leg_curl` | MACHINE |
| `plates` | PLATE | `leg_extension` | MACHINE |
| `resistance_band` | RESISTANCE_BAND | `leg_press` | MACHINE |
| `loop_band` | RESISTANCE_BAND | `pec_deck` | MACHINE |
| `ab_crunch_machine` | MACHINE | `plate_loaded_lateral_raise_machine` | MACHINE |
| `assisted_pullup_machine` | MACHINE | `preacher_curl_machine` | MACHINE |
| `back_extension_machine` | MACHINE | `seated_calf_raise_machine` | MACHINE |
| `bicep_curl_machine` | MACHINE | `shoulder_press_machine` | MACHINE |
| `chest_fly_machine` | MACHINE | `shrug_machine` | MACHINE |
| `chest_press_machine` | MACHINE | `smith_machine` | MACHINE |
| `dip_machine` | MACHINE | `standing_calf_raise_machine` | MACHINE |
| `donkey_calf_raise_machine` | MACHINE | `tricep_extension_machine` | MACHINE |
| `glute_ham_developer` | MACHINE | `air_bike` | MACHINE |
| `elliptical` | MACHINE | `rower` | MACHINE |
| `stair_climber` | MACHINE | `stationary_bike` | MACHINE |
| `treadmill` | MACHINE | | |
| `pull_up_bar` | NONE | `dip_station` | NONE |
| `rings` | NONE | `suspension_trainer` | NONE |
| `climbing_rope` | NONE | | |
| `ab_wheel` | OTHER | `medicine_ball` | OTHER |
| `battle_rope` | OTHER | `slam_ball` | OTHER |
| `jump_rope` | OTHER | `stability_ball` | OTHER |
| `plyo_box` | OTHER | `wrist_roller` | OTHER |
| `sled` | OTHER | `decline_bench` / `incline_bench` / `flat_bench` | OTHER *(unused by any free-tier exercise today; mapped defensively)* |
| *(absent — `is_bodyweight`)* | NONE | | |

#### 3.3.3 Bundling and the catalog reader

**Assets (`app/src/main/assets/exercises/`):**

- `repdb.json` — RepDB's `free.en.json`, shipped verbatim (no build-time transform: the simplest
  integration, and it preserves forward compatibility with future RepDB catalog updates — drop in
  a new file, nothing else changes).
- `images/flat/*.webp` — the per-exercise illustrations (~1,056 files, ~19 MB): a `<slug>-start` /
  `<slug>-peak` pair, or a single `<slug>-main` for a static hold. Exercises with an
  `image_alias` field build their path from the alias slug, not their own id — several exercises
  are visual variants of another and intentionally reuse its images.
- `images/muscles/*.webp` — 27 anatomical muscle diagrams (~1.3 MB), used in the muscle-group
  filter sheet (§3.7.3) — the anatomical diagram the reference screenshots show and which Daybook
  previously had no licensed source for.
- `images/equipment/*.webp` — 56 equipment icons (~4.8 MB), used in the equipment filter sheet.

**Total addition to the APK: ≈25 MB**, against a 7.2 MB baseline — roughly 4×. This is an install-
size cost, not a runtime-performance one: nothing here touches battery, network, or launch time
(§3.10 P4 still holds — the catalog is a compiled-in in-memory list once parsed, and parsing a
bundled JSON asset on first access is a one-time, off-main-thread cost like any other local read).

`ExerciseCatalog` (`data/workout/ExerciseCatalog.kt`) reads `repdb.json` from
`context.assets.open(...)` on first access, parses it with kotlinx.serialization into typed
classes mirroring RepDB's schema, filters out the `stretching` category (§3.3.4), and projects
each remaining entry through §3.3.2's three derivation functions into:

```kotlin
data class BuiltinExercise(
    val id: String,              // "builtin:" + RepDB's slug, e.g. "builtin:bench-press"
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val trackingMode: String,
    val imageId: String,         // RepDB's id, or its image_alias when present — build image
                                  // paths from this, never from `id` with "builtin:" stripped
                                  // naively, in case the two ever diverge
    val hasStartPeak: Boolean    // true -> "<imageId>-start"/"-peak"; false -> "<imageId>-main"
)
```

The parsed, filtered, projected list is cached for the process lifetime — one parse, not one per
screen visit. `WorkoutRepository.observeExerciseCatalog()` returns `builtins + customRows`,
merged and sorted, so the picker sees one flat list; an `exerciseId` that resolves to neither (an
old custom exercise deleted on another device before this one synced) renders as *"Unknown
exercise"* rather than crashing, the same defensive read the icon resolver already does.

**`builtin:` ids are RepDB's own slugs and are permanently stable** by the same rule the original
compiled-in catalog had: once written into a `workout_sets` row, an id is never renamed. Because
the slugs come from RepDB rather than being hand-picked, this is now also RepDB's own stability
guarantee to keep, not just Daybook's — acceptable, since a renamed upstream slug would only ever
affect *new* catalog entries, not historical data (§3.3's asset is versioned by whichever
`repdb.json` shipped with the installed build).

#### 3.3.4 Coverage: ~525 exercises, not 601

RepDB's free tier ships 601 exercises across 5 categories: `strength` (491), `stretching` (76),
`olympic` (14), `cardio` (16), `plyometrics` (4). **The 76 `stretching`-category exercises are
excluded from the picker** — `observeExerciseCatalog()` filters `category != "stretching"` — leaving
**≈525 usable rows**. Reasoning: Round A's live-session model (sets, reps/weight, PRs, volume) is
built around barbell-and-dumbbell gym logging, and a held stretch doesn't fit it ergonomically — no
PR, no volume, an odd fit for "log 3 sets of a hamstring stretch". The stretching data and its
images stay in the bundled asset untouched, so a future mobility/warm-up feature can turn them back
on with a one-line filter change and no re-bundling.

`olympic` and `plyometrics` are kept — cleans, snatches, box jumps and their kin fit the existing
`FULL_BODY` bucket exactly as the original catalog's hand-picked examples already did.

**Adding an exercise later is a RepDB catalog update** (a newer `repdb.json`), not a Daybook code
change — the reverse of the original design, where a new exercise was a line of Kotlin. Both are
"free" in the sense that matters: no Room migration, no Firestore blob change, since builtins
still never touch either.

#### 3.3.5 Attribution

**Required, permanent product requirement**, per RepDB's free-tier licence: one visible line —
**`Exercise data by RepDB (repdb.co)`** — in Daybook's existing `AboutSettingsScreen` (§3.8.1;
untouched otherwise by this round). That is the entire cost of the licence: free for commercial
in-app use, no redistribution of the dataset itself, images may be resized/cropped/recoloured for
in-app use.

---
### 3.4 DAOs and repository

New files:

- **`data/local/ExerciseDao.kt`** — `observeCustom(): Flow<List<Exercise>>`, `getById`,
  `insert`/`update`/`archive`, `allIds()`, `deleteByIds(ids)`, `deleteAll()` (the same shape every
  other definition DAO has for the sync diff path — see `HabitDao`).
- **`data/local/WorkoutDao.kt`** — sessions + blocks + sets in one DAO:
  - `observeSessionsBetween(startMillis, endMillis): Flow<List<WorkoutSession>>`
  - `observeRecentSessions(limit: Int): Flow<List<WorkoutSession>>`
  - `observeActiveSession(): Flow<WorkoutSession?>`
  - `observeExercisesForSession(sessionId): Flow<List<WorkoutExercise>>`
  - `observeSetsForSession(sessionId): Flow<List<WorkoutSet>>`
  - `getSessionsInLocalDateRange(startYmd, endYmd)` — for export
  - `getExercisesForSessions(ids)` / `getSetsForSessions(ids)` — both chunked at 900 bound vars
    (`SQLITE_MAX_VARS`, already used elsewhere; `ChunkedDeleteTest.kt` is the existing precedent)
  - `deleteSessionsInLocalDateRange(startYmd, endYmd)` + `deleteExercisesForSessions(ids)` +
    `deleteSetsForSessions(ids)` — for month eviction and month import
  - `sessionsStartingBetween(fromMillis, toMillis): List<WorkoutSession>` — the duplicate probe
    for §3.9.5

  **Two derived-value queries**, neither storing anything (§3.2):

  - **`previousSetsForExercise(exerciseId, excludeSessionId): List<WorkoutSet>`** — every set of
    the most recent prior session containing this exercise, in one query:

    ```sql
    SELECT s.* FROM workout_sets s
    JOIN workout_sessions ws ON ws.id = s.session_id
    WHERE s.exercise_id = :exerciseId
      AND s.session_id != :excludeSessionId
      AND s.completed_at IS NOT NULL
      AND ws.status = 'COMPLETED'
    ORDER BY ws.started_at DESC
    LIMIT 50
    ```

    The ViewModel keeps only the rows belonging to the newest `session_id` in the result and
    indexes them **by `set_number`** — what makes the `PREVIOUS` column per-set rather than
    per-exercise. Set 3 with no set 3 last time renders `–`. A pure function over the query
    result, `previousBySetNumber(rows): Map<Int, WorkoutSet>`, with its own unit test.

  - **`bestSetForExercise(exerciseId, excludeSessionId): WorkoutSet?`** — the pre-session personal
    best, read **once when the exercise block is opened**, not per keystroke:

    ```sql
    SELECT s.* FROM workout_sets s
    WHERE s.exercise_id = :exerciseId
      AND s.session_id != :excludeSessionId
      AND s.completed_at IS NOT NULL
      AND s.set_type = 'NORMAL'            -- a warm-up is never a PR
    ORDER BY COALESCE(s.weight_kg, 0) DESC,
             COALESCE(s.reps, 0) DESC,
             COALESCE(s.duration_seconds, 0) DESC,
             COALESCE(s.distance_meters, 0) DESC
    LIMIT 1
    ```

    plus a pure `isPersonalRecord(candidate, best, trackingMode): Boolean` — heavier weight
    (WEIGHT_REPS; equal weight with strictly more reps also counts), more reps (REPS_ONLY), longer
    (DURATION), further (DISTANCE_DURATION) — one unit test per mode plus a "no history at all is
    **not** a PR" case, because marking every first-ever set with a medal makes the medal
    meaningless.

    **Why a query and not a cached `is_pr` column:** a PR is a statement about *every other row*,
    so deleting or editing one historical session would silently leave stale medals scattered
    through the history with nothing to recompute them, and a stored flag would have to ride the
    wire model, where an older device (§4.3) could push back a version with the flag wrong. The
    query costs one indexed row read per exercise block opened.

  - `lastRestSecondsForExercise(exerciseId): Int?` — the most recent block's `rest_seconds`, so
    the rest timer the user set last time is pre-filled this time. One more indexed read; no
    preference table, no new `app_settings` column per exercise.

- **`data/local/RoutineDao.kt`** — its own DAO, not a fifth concern inside `WorkoutDao`, because
  routines are definitions and follow the definition DAOs' shape (`HabitDao`, `ExerciseDao`),
  including the three sync-diff functions:

  - `observeRoutineSummaries(): Flow<List<RoutineSummary>>` — everything the landing screen needs,
    in one query, so the list is not N+1 reads:

    ```sql
    SELECT r.id            AS id,
           r.name          AS name,
           r.notes         AS notes,
           COUNT(re.id)                 AS exerciseCount,
           COALESCE(SUM(re.target_sets), 0) AS targetSetCount
    FROM workout_routines r
    LEFT JOIN workout_routine_exercises re ON re.routine_id = r.id
    WHERE r.is_archived = 0
    GROUP BY r.id
    ORDER BY r.order_index ASC
    ```

    returning a plain Room POJO `RoutineSummary(id, name, notes, exerciseCount, targetSetCount)` —
    the `LEFT JOIN` is what makes a routine with no exercises still appear (*"No exercises yet"*).
  - `observeRoutine(routineId): Flow<WorkoutRoutine?>`
  - `observeRoutineExercises(routineId): Flow<List<WorkoutRoutineExercise>>` — `ORDER BY order_index`
  - `getRoutine(routineId): WorkoutRoutine?` / `getRoutineExercises(routineId): List<WorkoutRoutineExercise>`
    — the non-Flow reads `startSessionFromRoutine` uses inside its transaction
  - `maxOrderIndex(): Int?` — for "append the new routine at the end"
  - `upsertRoutine(routine)` / `upsertRoutineExercises(rows)` / `deleteRoutineExercises(routineId)` /
    `deleteRoutine(routineId)` / `archiveRoutine(routineId, archived)`
  - `allIds(): List<String>` / `deleteByIds(ids)` / `deleteAll()` — the definition-sync diff path
    (§4.4 item 5); `deleteByIds` chunked at 900 bound vars like every other bulk id call here.

- **`data/WorkoutRepository.kt`** — `@Singleton @Inject`, mirrors `HabitRepository`'s shape. Owns:
  - `startEmptySession(): String` (returns the new session id), `finishSession(id)`,
    `discardSession(id)`
  - `addExerciseToSession(...)`, `removeExerciseFromSession(...)`, `reorderExercises(...)`,
    `setExerciseNotes(...)`, `setExerciseRest(...)`
  - `addSet(...)`, `updateSet(...)`, `deleteSet(id)`, `toggleSetComplete(id)`
  - `createCustomExercise(name, primaryMuscle, equipment, trackingMode)` with the same
    normalise-and-dedupe treatment `CustomCategoryRepository` gives category names (there are
    already two unit tests for that idiom: `CustomCategoryNormaliseTest`, `CustomPromptNormaliseTest`)
  - `deleteSession(id)` inside `withTransaction` — sets, then blocks, then the session
  - `importHevyCsv(...)` (§3.9)
  - **Routines**, exact signatures:

    ```kotlin
    // The editor's in-memory row. Immutable, primitives only, no entity, no Flow.
    data class RoutineExerciseDraft(
        val id: String,                       // stable UUID, minted on add, kept across reorders
        val exerciseId: String,
        val targetSets: Int? = null,
        val targetReps: Int? = null,
        val targetWeightKg: Float? = null,
        val targetDurationSeconds: Int? = null,
        val targetDistanceMeters: Float? = null,
        val restSeconds: Int? = null,
        val notes: String? = null
    )

    fun observeRoutineSummaries(): Flow<List<RoutineSummary>>
    fun observeRoutineExercises(routineId: String): Flow<List<WorkoutRoutineExercise>>
    suspend fun createRoutine(name: String, notes: String?, exercises: List<RoutineExerciseDraft>): String
    suspend fun updateRoutine(routineId: String, name: String, notes: String?, exercises: List<RoutineExerciseDraft>)
    suspend fun duplicateRoutine(routineId: String): String
    suspend fun deleteRoutine(routineId: String)
    suspend fun startSessionFromRoutine(routineId: String): String
    ```

    - `createRoutine` / `updateRoutine` run in one `withTransaction`. `updateRoutine` is
      **delete-all-then-reinsert** for the child rows, not a diff: a routine is a handful of rows,
      `orderIndex` changes on every reorder, and a diff here is more code with more ways to leave
      the ordering inconsistent. It bumps `updatedAt`.
    - The name is normalised and deduped with the same idiom `CustomCategoryRepository` uses —
      trim, collapse inner whitespace, case-insensitive compare against existing non-archived
      routines, and on a clash append `" 2"`, `" 3"`. Extracted as a pure `fun uniqueRoutineName(desired:
      String, existing: List<String>): String` with its own `RoutineNameNormaliseTest`.
    - `duplicateRoutine` is that function doing its job: copy the routine and its rows with new
      UUIDs, name = `uniqueRoutineName(original.name, …)` → *"Push day 2"*, `source = "USER"` even
      if the original was imported — a copy you made is yours.
    - **`startSessionFromRoutine(routineId)` — the whole of "pre-populate", specified exactly.**
      One `withTransaction`:
      1. Insert a `WorkoutSession` with `status = "ACTIVE"`, `source = "MANUAL"`, `routineId =
         routineId`, `title = routine.name`, `startedAt = now`, `localDate = today`.
      2. For each `WorkoutRoutineExercise` in `orderIndex` order, insert a `WorkoutExercise` with
         the same `orderIndex`, `notes = routineExercise.notes`, and `restSeconds =
         routineExercise.restSeconds` — the routine's rest value wins over
         `lastRestSecondsForExercise` and over `app_settings.rest_timer_default_seconds`.
         Precedence, stated once: **routine target → last time's block value → the app default →
         OFF.**
      3. For each such block, insert `targetSets ?: 0` `WorkoutSet` rows, `setNumber = 1..n`, with
         `reps = targetReps`, `weightKg = targetWeightKg`, `durationSeconds =
         targetDurationSeconds`, `distanceMeters = targetDistanceMeters`, `setType = "NORMAL"`,
         and **`completedAt = null`**.
      4. Return the session id; the caller navigates to `workout_session/{id}`.

      **Why the target values are written into the set rows rather than shown as ghost
      placeholders:** a pre-filled row that the user ticks means *"I did exactly what I
      planned"*, the common case, costing one tap. `NULL` values with the target as placeholder
      text would make the green check either dangerous (tick it and store a set with no numbers,
      contributing 0 volume and invisible to PR detection) or conditional. `completedAt` already
      separates plan from performance: `previousSetsForExercise`, `bestSetForExercise` and
      `sessionStats` all filter `completed_at IS NOT NULL`, so an abandoned routine-started
      session cannot inflate Volume, invent a PR, or pollute PREVIOUS. No `is_target` /
      `is_planned` flag is added — the same stored-derived-state mistake §3.2 rejects for
      `isPersonalRecord`. `targetSets = null` creates no set rows at all.
      A `StartFromRoutineTest` covers: 3 exercises with targets → 3 blocks and the right set rows
      with the right values and `completedAt == null`; an exercise with `targetSets = null` → a
      block with zero sets; rest-precedence over a `lastRestSecondsForExercise` value; and that
      `sessionStats` on the fresh session reports 0 volume, 0 sets.

  Also pure, in `data/workout/`, unit-testable without Room: `sessionStats(sets): SessionStats`
  returning `(totalVolumeKg, setCount)` for the live header — volume is `Σ (weightKg × reps)` over
  **completed** sets only, warm-ups included (Hevy counts them), and a bodyweight or duration set
  contributes 0 rather than being skipped, so the set count and volume never disagree about which
  rows they looked at.

  Wired in `di/DatabaseModule.kt` as `@Provides @Singleton fun provideWorkoutRepository(db)`.

### 3.5 Migration — `MIGRATION_21_22`

Appended to `data/local/Migrations.kt`, registered in `DatabaseModule.addMigrations(…)`,
`AppDatabase.version` 21 → 22, entity list gains `Exercise::class`, `WorkoutSession::class`,
`WorkoutExercise::class`, `WorkoutSet::class`, `WorkoutRoutine::class`,
`WorkoutRoutineExercise::class`, and three new `abstract fun`s: `exerciseDao`, `workoutDao`,
`routineDao`.

**100% additive.** No `UPDATE`, no `DROP`, no table rebuild, no backfill — a user upgrading gets
six empty tables, one null column and five new settings columns at their declared defaults, and
nothing they already had is read or rewritten (C2).

```sql
CREATE TABLE IF NOT EXISTS `exercises` (
  `id` TEXT NOT NULL, `name` TEXT NOT NULL,
  `primary_muscle` TEXT NOT NULL, `equipment` TEXT NOT NULL,
  `tracking_mode` TEXT NOT NULL, `is_archived` INTEGER NOT NULL DEFAULT 0,
  `source` TEXT NOT NULL DEFAULT 'USER',
  `created_at` INTEGER NOT NULL, `notes` TEXT, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `workout_sessions` (
  `id` TEXT NOT NULL, `local_date` TEXT NOT NULL, `started_at` INTEGER NOT NULL,
  `ended_at` INTEGER, `title` TEXT, `notes` TEXT,
  `status` TEXT NOT NULL DEFAULT 'ACTIVE', `source` TEXT NOT NULL DEFAULT 'MANUAL',
  `routine_id` TEXT,                                           -- NULLABLE, no FK.
  `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_sessions_local_date` ON `workout_sessions`(`local_date`);
CREATE INDEX IF NOT EXISTS `index_workout_sessions_started_at` ON `workout_sessions`(`started_at`);

-- Routines are DEFINITIONS, like habits and custom exercises — they carry no local_date, they
-- are never month-partitioned and never evicted (§4.4).
CREATE TABLE IF NOT EXISTS `workout_routines` (
  `id` TEXT NOT NULL, `name` TEXT NOT NULL, `notes` TEXT,
  `order_index` INTEGER NOT NULL,
  `is_archived` INTEGER NOT NULL DEFAULT 0,
  `source` TEXT NOT NULL DEFAULT 'USER',
  `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_routines_order_index` ON `workout_routines`(`order_index`);

CREATE TABLE IF NOT EXISTS `workout_routine_exercises` (
  `id` TEXT NOT NULL, `routine_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL,
  `order_index` INTEGER NOT NULL,
  -- Every target is NULLABLE. A NOT NULL DEFAULT 0 here destroys the difference between "no
  -- target" and "a target of zero", exactly as it would on workout_sets (Ri3, R18).
  `target_sets` INTEGER, `target_reps` INTEGER, `target_weight_kg` REAL,
  `target_duration_seconds` INTEGER, `target_distance_meters` REAL,
  `rest_seconds` INTEGER, `notes` TEXT,
  `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_routine_exercises_routine_id_order_index` ON `workout_routine_exercises`(`routine_id`,`order_index`);
CREATE INDEX IF NOT EXISTS `index_workout_routine_exercises_exercise_id` ON `workout_routine_exercises`(`exercise_id`);

CREATE TABLE IF NOT EXISTS `workout_exercises` (
  `id` TEXT NOT NULL, `session_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL,
  `order_index` INTEGER NOT NULL, `notes` TEXT, `superset_id` TEXT, `rest_seconds` INTEGER,
  `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_exercises_session_id_order_index` ON `workout_exercises`(`session_id`,`order_index`);
CREATE INDEX IF NOT EXISTS `index_workout_exercises_exercise_id` ON `workout_exercises`(`exercise_id`);

CREATE TABLE IF NOT EXISTS `workout_sets` (
  `id` TEXT NOT NULL, `workout_exercise_id` TEXT NOT NULL,
  `session_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL,
  `set_number` INTEGER NOT NULL,
  `reps` INTEGER, `weight_kg` REAL, `duration_seconds` INTEGER, `distance_meters` REAL,
  `rpe` INTEGER,
  `set_type` TEXT NOT NULL DEFAULT 'NORMAL',
  `notes` TEXT, `completed_at` INTEGER, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_workout_sets_workout_exercise_id_set_number` ON `workout_sets`(`workout_exercise_id`,`set_number`);
CREATE INDEX IF NOT EXISTS `index_workout_sets_session_id` ON `workout_sets`(`session_id`);
CREATE INDEX IF NOT EXISTS `index_workout_sets_exercise_id` ON `workout_sets`(`exercise_id`);

-- device-local settings, same treatment as every app_settings column since v16. All four
-- surfaced on BEAST MODE's OWN settings screen (§3.8.2), NOT on Daybook's main settings_*
-- screens.
ALTER TABLE app_settings ADD COLUMN weight_unit TEXT NOT NULL DEFAULT 'KG';
-- The BEAST MODE accent — the accent for the whole Workout mode subtree, not a tab tint. CORAL
-- is an existing AccentColor (Accent.kt:21), not a new colour literal (C5).
ALTER TABLE app_settings ADD COLUMN workout_accent_color TEXT NOT NULL DEFAULT 'CORAL';
-- The default pre-filled into a new exercise block's rest timer. 0 == OFF.
ALTER TABLE app_settings ADD COLUMN rest_timer_default_seconds INTEGER NOT NULL DEFAULT 0;
-- The long-press hint's lifecycle. Tri-state, one column, device-local:
--   0 = never shown          -> show the coach-mark AND the dot on the Today icon
--   1 = coach-mark dismissed -> hide the coach-mark, KEEP the dot (gesture still unused)
--   2 = gesture used at least once -> show neither, ever again
ALTER TABLE app_settings ADD COLUMN workout_hint_state INTEGER NOT NULL DEFAULT 0;
-- The visible fallback entry point on Today. Default ON.
ALTER TABLE app_settings ADD COLUMN workout_today_card_enabled INTEGER NOT NULL DEFAULT 1;
```

Every `DEFAULT` above must byte-match the corresponding `@ColumnInfo(defaultValue = …)` in the
entity, or Room's identity-hash check fails at open — this rule has bitten this repo before, and
the existing migrations all carry a comment saying so.

**`nav_tabs` is not read, written or mentioned by Round A.** Workout is not a tab (§3.6), so there
is nothing to make visible there — the migration has no non-additive statement anywhere.

**Tests:** `androidTest/…/MigrationTest.kt` gains a `migrate21To22` case: open a 21.json DB, run
the migration, assert the six new tables + five new `app_settings` columns +
`workout_sessions.routine_id` all exist. **R18's guard extends to the five
`workout_routine_exercises` target columns** — assert none of them is `NOT NULL`.
`app/schemas/…/22.json` regenerates on the next build — commit it.

---
### 3.6 Navigation — Beast Mode is a mode, not a tab

**Workout mode ("Beast Mode") is not a fourth item in the bottom nav.** It is reached by
press-and-holding the "Today" item in the bottom bar. A normal tap on Today still opens Today,
exactly as today. Holding it takes you into a full-screen workout mode with its own look and its
own three-item bottom nav, and there is a clear way back out — the same hold, mirrored, on its
own leftmost nav item.

#### 3.6.0 What does not change

| File | Behaviour |
|---|---|
| `ui/NavConfig.kt` | **No change.** Stays `listOf("home", "routines", "foodmed")`. |
| `ui/NavConfigTest.kt` | **No change** — and it is a tripwire: if a later round quietly re-adds `"workout"` to `ALL_ROUTES`, this test goes red. |
| `app_settings.nav_tabs` | Not read, not written, not mentioned by Round A. |
| `ui/settings/NavigationSettingsScreen.kt` | Not touched at all this round — Workout is not a tab-visibility setting. |
| `HorizontalPager` / `beyondViewportPageCount` / `pagerState` | No change. Three pages, same swipe behaviour, same cold-composition cost. |
| The pager's `when (visibleRoutes.getOrElse(page))` | No change — there is no `"workout"` page id, so `else -> FoodMedScreen(...)` stays exactly as it is. |
| `BackHandler(enabled = settledPage != 0) { goToPage(0) }` inside `composable("main")` | No change, and it is not involved — `NavHost` composes only the current destination, so while Beast Mode is on top this handler is not registered at all. |
| `DaybookScaffold`'s `showNav` logic | **Amended, not left alone.** Beast Mode has a bottom nav of its own (§3.6.7), so `showNav` gains one disjunct — `onMain || backStackRoute in WorkoutRoutes.NAV` — and the `navItems` / `onSelectRoute` / `currentRoute` arguments switch to Beast Mode's set on those three routes. Everything else about `DaybookScaffold` is untouched, and the *other* workout routes (the live session, the pickers, the forms, Beast Mode's own settings) still get full-screen-no-nav for free. |
| `res/drawable/ic_nav_workout.xml` | Renamed `ic_workout.xml` and repurposed: not a nav icon, but Beast Mode's header, the Today row (§3.6.4) and the Settings row need a dumbbell glyph. Same 24 dp viewport, same stroke weight, same `?attr` tinting as the `ic_nav_*` set. Must be added to `androidTest/…/NavIconInflateTest.kt` — that test exists to catch a vector that inflates on the authoring machine but not on API 26. |

**No fourth-nav-label clipping risk** — there is no fourth label. The pill nav ships this round
with the same three items, the same widths and the same text it has today.

#### 3.6.1 The gesture — exact Compose mechanics

Three small, additive changes. No new file.

**(a) `ui/components/Components.kt` — a long-press sibling for `clickableImpl`.** The existing
helper (`:106`):

```kotlin
internal fun Modifier.clickableImpl(
    interaction: MutableInteractionSource,
    onClick: () -> Unit
): Modifier = this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
```

Add directly beneath it, in the same style:

```kotlin
// The long-press-capable twin of clickableImpl. Same "no indication" rule: the app draws its own
// press feedback (scale/tint), it never uses the Material ripple. `onLongClickLabel` is NOT
// cosmetic — TalkBack reads it as "double tap and hold to <label>", and it is what puts the
// gesture in switch-access's actions menu. Never pass null here.
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.combinedClickableImpl(
    interaction: MutableInteractionSource,
    onLongClickLabel: String,
    onLongClick: () -> Unit,
    onClick: () -> Unit
): Modifier = this.combinedClickable(
    interactionSource = interaction,
    indication = null,
    onLongClickLabel = onLongClickLabel,
    onLongClick = onLongClick,
    onClick = onClick
)
```

`Modifier.combinedClickable` is `@ExperimentalFoundationApi` in the foundation version this
project pins; `MainActivity.MainApp()` is already annotated `@OptIn(ExperimentalFoundationApi::class)`
(for `HorizontalPager`), so this is a known, already-accepted opt-in, not a new kind of risk.

**(b) `ui/components/Navigation.kt` — `FloatingPillNav` gains parameters**, all defaulted so the
signature stays source-compatible:

```kotlin
fun FloatingPillNav(
    items: List<NavItemSpec>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongSelect: ((String) -> Unit)? = null,
    longPressRoute: String? = null,             // WHICH item accepts the hold
    longPressLabel: String? = null,             // TalkBack's "double tap and hold to …"
    hintDotRoutes: Set<String> = emptySet()
)
```

**Why `longPressRoute` / `longPressLabel` are parameters, not a hard-coded `item.route ==
"home"` check:** there are **two** long-pressable nav items in this design — hold Today to enter,
hold Beast Mode's leftmost item to leave (§3.6.8) — and hard-coding two route ids inside a generic
nav component is how a generic component stops being one. One implementation, two configurations:
Daybook's nav passes `longPressRoute = "home"`, `longPressLabel = "Start a workout"`; Beast Mode's
nav passes `longPressRoute = WorkoutRoutes.HOME`, `longPressLabel = "Leave Beast Mode"`. Nothing
else about the gesture differs — not the threshold, not the dead zone, not the ramp, not the
haptic, not the reduced-motion branch.

`DaybookScaffold` / `DaybookScaffoldNav` thread the same four through (plus the coach-mark slot of
§3.6.3). There is exactly one `DaybookScaffold` call site in the app (`MainActivity.kt:608`), so
this is a few-line change, not a migration.

Inside the `items.forEach { item }` loop (`:108`):

```kotlin
val longPressable = onLongSelect != null && longPressRoute != null && item.route == longPressRoute
…
.then(
    if (longPressable) Modifier.combinedClickableImpl(
        interaction = interaction,
        onLongClickLabel = longPressLabel ?: "",   // never null in practice — see (a)
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongSelect!!(item.route)
        },
        onClick = { onSelect(item.route) }
    ) else Modifier.clickableImpl(interaction) { onSelect(item.route) }
)
```

Four things about this that are deliberate and must not be "tidied":

1. **The gate is a route check, not an index check.** Today is guaranteed present and first by
   `NavConfig.visibleRoutesFrom`'s "index 0 == Today" invariant, but keying on the route id means
   the gesture cannot migrate to the wrong item if reordering is ever implemented. The same holds
   for Beast Mode's own nav, whose leftmost item is `WorkoutRoutes.HOME` by construction (§3.6.7).
2. **The `interaction: MutableInteractionSource` is hoisted out of the modifier chain**, because
   §3.6.2's press feedback reads `interaction.collectIsPressedAsState()`. It already is hoisted
   today.
3. **The long-press works from any tab**, not only when Today is the selected page. Holding Today
   from Habits or Intake enters Beast Mode directly; it does not first switch to Today.
4. **Long-pressing Today while Today is already selected is a normal long-press**, not a no-op —
   this is in fact where most users will do it.

**(c) `ui/MainActivity.kt` — one new callback**, `remember(navController)`-wrapped exactly like the
twelve that already exist:

```kotlin
val goWorkout: () -> Unit = remember(navController) {
    { navController.navigate("workout") { launchSingleTop = true } }
}
```

passed as `onLongSelectRoute = { goWorkout() }` on the `DaybookScaffold` call.

**The long-press duration is the platform's, and is not ours to pick: ~500 ms.**
`Modifier.combinedClickable` uses Compose's `viewConfiguration.longPressTimeoutMillis`
(`ViewConfiguration.getLongPressTimeout()`, 500 ms on stock Android, unchanged since API 1). Do
not hand-roll a timer and do not pass a custom duration — a user who has set Accessibility →
*Touch and hold delay* to Medium/Long (people with tremor routinely do) gets their own value
automatically from `combinedClickable`, and gets nothing at all from a hard-coded `delay(500)`.
The one place the number is needed explicitly is §3.6.2's feedback ramp, which reads it from
`LocalViewConfiguration.current.longPressTimeoutMillis` rather than typing `500`.

#### 3.6.2 Press-and-hold feedback

A long-press with no feedback feels broken. So the hold is visibly answered, and the answer
completes at exactly the moment the gesture fires. **One ramp, one haptic, and nothing else:**

- **During the hold — the Today icon grows and takes the accent.** Read `val pressed by
  interaction.collectIsPressedAsState()`. After a **120 ms** dead zone (so an ordinary tap, over
  in ~60–100 ms, never animates), the icon's scale ramps **1.00 → 1.18** and its tint lerps to
  `LocalAccent.current`, using `animateFloatAsState(target, tween(durationMillis = rampMillis,
  easing = LinearEasing))` where `rampMillis = LocalViewConfiguration.current.longPressTimeoutMillis.toInt() -
  120` (= 380 ms at the platform default). Linear easing deliberately — the ramp is a progress
  indicator, not a flourish, and must reach its end state at the instant the gesture triggers.
  Releasing early springs back to 1.00 via `Motion.pressSpring()`, the same spring
  `CircleIconButton` already uses for its press scale — no new motion token.
- **At the trigger — one haptic tick.** `LocalHapticFeedback.current.performHapticFeedback(
  HapticFeedbackType.LongPress)`, fired as the first statement inside `onLongClick`, before
  `navigate`, so the tick lands while the finger is still down. Needs no permission and no
  `VIBRATE` in the manifest.
- **Reduce-motion (`LocalReduceMotion.current == true`): the ramp is dropped, the signal is not.**
  No growth, no spring-back. The tint snaps to `LocalAccent.current` once the 120 ms dead zone
  passes and snaps back on release. The haptic still fires — a haptic is not motion, and removing
  the only remaining feedback channel from the users most likely to need it would be exactly
  backwards. This matches every other surface in the app (`UndoSnack`, `SegmentedControl`,
  `WeekStrip` all branch on `LocalReduceMotion` without becoming inert).
- **Nothing else.** No radial/arc fill (new visual vocabulary, C5), no scrim, no bottom-sheet
  peek, no sound.

**Testable part.** Extract the dead zone and ramp arithmetic as a pure function:
`internal fun longPressRampMillis(longPressTimeoutMillis: Long, deadZoneMillis: Int = 120): Int`
returning `(longPressTimeoutMillis - deadZoneMillis).coerceAtLeast(1).toInt()`, with a
`NavLongPressRampTest` covering the 500 ms default, a 1500 ms accessibility setting, and a
pathological sub-dead-zone value.

#### 3.6.3 Discoverability

A hidden gesture that nobody is told about does not exist. Answered in two places, both of which
retire themselves.

**(a) A one-time coach-mark**, the first time the user opens Daybook after this update. There is
no coach-mark component in the app; build a minimal one, `ui/components/CoachMark.kt`, out of
`UndoSnack.kt`'s existing recipe — the same surface, border, type ramp and reduce-motion branch,
differing only in that it persists until dismissed and carries an action:

```kotlin
@Composable
fun BoxScope.NavCoachMark(
    text: String,
    actionLabel: String,          // "Got it"
    bottomClearance: Dp,          // DaybookScaffold's navClearance, so it floats just above the pill
    onDismiss: () -> Unit
)
```

- **Surface:** `AppShapes.card` (not `pill` — two lines of text plus an action),
  `DaybookColors.SurfaceElevated`, 1 dp `DaybookColors.Hairline` border, `UndoSnack`'s elevation.
  Text `DaybookText.CardSubtitle` / `DaybookColors.TextPrimary`, `maxLines = 2`.
- **Placement:** `Modifier.align(Alignment.BottomStart)`, `padding(start = Spacing.screenH, bottom
  = bottomClearance + 12.dp)`, `widthIn(max = 280.dp)`. Left-aligned over the **Today** slot —
  Today is always leftmost. No pointer triangle: a drawn tail is new shape outside `AppShapes`
  (C5), and proximity plus the dot in (b) is unambiguous with three items.
- **Exact copy, final:** body **`Hold "Today" to start a workout.`**, action **`Got it`** (a
  `GhostButton`, right-aligned under the text).
- **Reduce-motion:** appears/disappears with no fade/slide when `LocalReduceMotion` is true;
  otherwise `UndoSnack`'s fade.
- **Accessibility:** one focusable node with `Modifier.semantics { liveRegion =
  LiveRegionMode.Polite }` so TalkBack announces it when it appears; `Got it` is a real button.

**When it shows:** on the `main` destination only, when `workout_hint_state == 0`, after a 600 ms
delay from first composition. Dismissed — and `workout_hint_state` advances — by any of: tapping
`Got it` (→ 1), performing the long-press (→ 2), or navigating away from `main` (→ 1). It appears
once, ever, and cannot reappear after a process death because the flag is in Room.

**(b) A persistent dot on the Today icon**, until the gesture is used once. A 4 dp filled circle
in `LocalAccent.current` at the icon's top-end corner (`Modifier.offset(x = 6.dp, y = (-2).dp)`),
rendered only while `workout_hint_state < 2`, passed as `hintDotRoutes = setOf("home")` rather
than hard-coded, so the nav component stays generic.

- Survives "Got it" (state 1) and dies only on the first successful long-press (state 2) —
  dismissing a tip is not the same as having learned the gesture.
- Not a badge — no count, no red, no "new" pill. Accent-coloured, 4 dp, permanently gone after
  one use.
- The Today icon's `contentDescription` stays `null` (the label `Text` below already carries the
  name). The gesture reaches TalkBack through `onLongClickLabel`; the dot is decorative.

**(c) And the visible entry points of §3.6.4**, the real answer for anyone who never sees, or
cannot perform, the gesture.

#### 3.6.4 Visible entry points

Two ship, both unconditional:

1. **Settings → Workout → `Open Workout`.** A plain navigation row with the `ic_workout` glyph,
   calling the same `goWorkout()`. One row, zero new concepts, and it means the feature can
   always be reached by a user who has forgotten the gesture, cannot perform it, or is driving
   TalkBack. **Not optional and not behind any toggle** — the guaranteed floor. It is the **only**
   Workout row in Daybook's main Settings, because every actual Workout *setting* lives in Beast
   Mode's own settings screen (§3.8) — which makes this row both the accessibility floor and the
   only pointer telling someone hunting for "weight unit" in Settings where it went.
2. **A `Beast Mode` row on the Today screen.** A single `SoftCard` row in Today's `LazyColumn` —
   leading `CardTints` tinted circle + `ic_workout`, title, subtitle, tap → `goWorkout()`:
   - **No active session:** title **`Beast Mode`**, subtitle **`Routines, workouts and
     history`**. (Not "Start workout" — tapping the row lands on Beast Mode's routines landing
     page, it does not start a workout, and a button that says one thing and does another is a
     small lie the user pays for every time.)
   - **A session is ACTIVE:** title **`Workout in progress`**, subtitle **`Tap to carry on`**,
     tinted with the Beast Mode accent. This doubles as the resume affordance outside Beast Mode
     (§3.6.5), from the **same** `observeActiveSession()` Flow the in-mode resume banner uses —
     one query, two surfaces, no second source of truth.
   - **Placement:** appended after the grouped reminder items and before Round B's Health card and
     the bottom clearance spacer — Today is *what you still have to do*, so nothing new pushes a
     pending reminder down the screen, but it sits above the Health card because starting a
     workout is still an action you can take today, while health data is a read-out of what
     already happened. `GroupHomeItemsTest`'s grouping is not modified — the row is appended
     after the grouped items, not inserted into them.
   - **Hideable** via `app_settings.workout_today_card_enabled`, surfaced as **`Show Beast Mode
     on Today`** on Beast Mode's own settings screen (§3.8.2). Safe to turn off: entry point 1 is
     unconditional and the hold still works. Safe to find again: you turn it back on from inside
     Beast Mode, which you can always get into.

#### 3.6.5 Getting out — the exit path, stated exhaustively

| From | Gesture | Result |
|---|---|---|
| Beast Mode landing (`"workout"`) | Press-and-hold the leftmost nav item (§3.6.8) | Leaves Beast Mode and lands on **Today**. The mirror of the way in. |
| Beast Mode landing | System back button / back gesture | Leaves via `NavHost`'s default pop → back on `"main"`, Daybook's own nav returns, and the pager is on whatever page the user left from (`pagerState` is `remember`ed in `MainApp`'s scope, not inside `composable("main")`). **Do not add a `BackHandler` to the workout landing** — there is nothing to intercept. |
| `"workout_history"` / `"workout_library"` | Tap the leftmost nav item | Back to the landing. No-op if already there (`launchSingleTop`). |
| `"workout_history"` / `"workout_library"` | System back | Back to the **landing**, not out of the mode — §3.6.7's `popUpTo("workout")` keeps the landing under them. You cannot fall out of Beast Mode by backing out of a tab inside it. |
| `"workout_history"` / `"workout_library"` | Press-and-hold the leftmost nav item | Leaves Beast Mode from anywhere in the nav, landing on Today, without first navigating to the landing. |
| `"workout_settings"` | Back, or the header's back arrow | Pops to the landing. Not a nav destination (§3.6.7), so it renders full-screen with no pill nav, like every other stacked route. |
| `"workout_settings"` | The **`Leave Beast Mode`** row at the bottom of the screen (§3.8.2) | Leaves Beast Mode and lands on Today — the same `exitBeastMode()` the hold calls. The accessibility floor for the exit, mirroring `Open Workout` for the entrance (R21 / R26). |
| Live session (`workout_session/{id}`) | Back, or the header chevron | Pops to the Beast Mode landing. No confirmation dialog. |
| Live session, then the landing | Back twice | Leaves the mode entirely with the session still `ACTIVE`. Allowed, no prompt. |
| Any deeper route (`add_exercise`, `new_exercise`, `edit_exercise`, `workout_detail`, `routine_edit`) | Back | Pops one level, as every other stacked route already does. |

**One deliberate asymmetry.** The **hold** lands you on **Today**; **system back** lands you on
**the page you came from**. The hold is the mirror of "hold Today to enter", so it returns you to
Today; back means "undo the navigation I just did", returning you where you were. Both leave
Beast Mode; they differ only in which of the three tabs you arrive on.

**Why there is no "you have unsaved sets" prompt.** §3.10 P1 already requires that every completed
set is written to Room immediately, in its own transaction — the live session is forbidden from
holding an in-memory `List<WorkoutSet>`. So "unsaved sets" is not a state this design can be in. A
confirm-on-exit dialog would be a dialog about a risk that has been designed out. Discarding a
session stays what §3.7.1 already made it: an explicit `Discard Workout` button behind
`ConfirmDeleteDialog`.

**An interrupted session, reconciled with the resume design.** The resume banner on the Workout
surface handles "the user wandered off mid-session"; leaving the whole mode is handled by the
Today row flipping to `Workout in progress` / `Tap to carry on` — the **same**
`observeActiveSession()` Flow, rendered where the user actually is. Do not write a second query for
the second surface.

**Re-entering while a session is ACTIVE — one predictable destination.** A long-press (or either
visible entry point) always lands on the Beast Mode landing, never jumping straight into the live
session. The landing shows the resume banner at the very top, so carrying on is one tap.
Deep-jumping into a session the user may have forgotten about, from a gesture, is disorienting;
one destination is worth one extra tap.

#### 3.6.6 The route graph

All ten are plain `composable(...)` destinations in the **existing** `NavHost`, siblings of
`"main"` — not children of the pager and not a nested `navigation(...)` graph (a nested graph
would buy scoped ViewModels this round does not need). Three show Beast Mode's own pill nav
(§3.6.7); the other seven render full-screen with no nav, which `showNav` already does for them.

```kotlin
// One source of truth for Beast Mode's route ids. MainActivity's showNav gate (§3.6.7), the
// accent gate (§3.8.3) and the nav item list all read from here rather than repeating string
// literals, because a typo'd literal in any of those three places fails SILENTLY: the nav just
// doesn't appear, or the accent just doesn't change.
object WorkoutRoutes {
    const val HOME       = "workout"            // the landing — "My routines" (§3.7.4)
    const val HISTORY    = "workout_history"    // past sessions (§3.7.6)
    const val LIBRARY    = "workout_library"    // browse/manage exercises (§3.7.3 BROWSE mode)
    const val SETTINGS   = "workout_settings"   // Beast Mode's own settings (§3.8.2)
    const val SESSION    = "workout_session/{sessionId}"
    const val DETAIL     = "workout_detail/{sessionId}"
    const val ROUTINE_EDIT = "routine_edit?routineId={routineId}"
    const val PICK_EXERCISE = "add_exercise"
    const val NEW_EXERCISE  = "new_exercise"
    const val EDIT_EXERCISE = "edit_exercise/{exerciseId}"

    /** The three that show Beast Mode's pill nav. ORDER IS LOAD-BEARING: [0] is the leftmost
     *  item and the one the exit hold is attached to (§3.6.8). */
    val NAV: List<String> = listOf(HOME, HISTORY, LIBRARY)

    /** Every route inside the mode — the gate for the Beast Mode accent (§3.8.3). */
    val ALL: Set<String> = setOf(HOME, HISTORY, LIBRARY, SETTINGS, SESSION, DETAIL,
        ROUTINE_EDIT, PICK_EXERCISE, NEW_EXERCISE, EDIT_EXERCISE)
}
```

A `WorkoutRoutesTest` asserts `NAV.first() == HOME`, `NAV.all { it in ALL }`, and that `ALL` has no
duplicates — three lines that make the two load-bearing invariants explicit instead of incidental.

| Route | Screen | Pill nav? | Reached from |
|---|---|---|---|
| `WorkoutRoutes.HOME` = `"workout"` | `WorkoutHomeScreen` (§3.7.4) — "My routines" | **Yes** (item 1, leftmost) | `goWorkout()`: the Today long-press, Settings → `Open Workout`, the Today row |
| `"workout_history"` | `WorkoutHistoryScreen` (§3.7.6) — the past-sessions list | **Yes** (item 2) | its nav item |
| `"workout_library"` | `AddExerciseScreen` in `BROWSE` mode (§3.7.3) | **Yes** (item 3) | its nav item |
| `"workout_settings"` | `WorkoutSettingsScreen` (§3.8.2) | no | **only** the gear in the landing header (§3.7.4) |
| `"workout_session/{sessionId}"` | `WorkoutSessionScreen` (§3.7.1) | no | the landing's `Start an empty workout`, a routine card, the resume banner; History's "in progress" row |
| `"workout_detail/{sessionId}"` | `WorkoutDetailScreen` | no | a past-session card on History |
| `"routine_edit?routineId={routineId}"` | `RoutineEditScreen` (§3.7.5). Arg optional — absent = create, present = edit | no | the landing's `+ New routine`; a routine card's overflow → `Edit` |
| `"add_exercise"` | `AddExerciseScreen` in `PICK` mode (§3.7.3) | no | the live session's `+ Add Exercise`; the routine editor's `+ Add exercise` |
| `"new_exercise"` | `ExerciseFormScreen` | no | Add-Exercise's header `New` action |
| `"edit_exercise/{exerciseId}"` | `ExerciseFormScreen` | no | an exercise row's overflow |

**The picker is a pure chooser — it never writes.** `add_exercise` carries no `?session=`
argument. In `PICK` mode it sets `"picked_exercise_id"` on the **previous back-stack entry's
`SavedStateHandle`** and pops; the caller decides what that means — the live session's VM calls
`addExerciseToSession`, the routine editor appends a `RoutineExerciseDraft`. One mechanism, one
code path, which is also what makes `BROWSE` mode (the `"workout_library"` nav destination) the
same screen rather than a second one.

The callbacks are `remember(navController)`-wrapped like every existing one: `goWorkout`,
`exitBeastMode` (§3.6.8), `goBeastRoute(route)` (§3.6.7), `goWorkoutSettings`,
`goWorkoutSession(id)`, `goWorkoutDetail(id)`, `goRoutineEdit(routineId: String?)`,
`goPickExercise`, `goNewExercise`, `goEditExercise(id)`.

#### 3.6.7 Beast Mode's own bottom nav

Three destinations, the same `FloatingPillNav` component with the same visual styling. Three, not
four or five, for the same reason Daybook's own nav has three: the pill divides its width
`SpaceEvenly` across `items`, every item carries a visible text label, and 360 dp is the
constraint. Matching the app's own rhythm also means a user who has just held Today feels the bar
*change contents*, not *change kind*.

| # | Route | Label | Icon | Why it earns a slot |
|---|---|---|---|---|
| **1** (leftmost) | `"workout"` | **`Routines`** | `ImageVector.vectorResource(R.drawable.ic_workout)` | It is the landing (§3.7.4) and Beast Mode's "home". **Leftmost is load-bearing**: §3.6.8 attaches the exit hold to it, mirroring Today's position in Daybook's own nav. |
| **2** | `"workout_history"` | **`History`** | `DaybookIcons.Clock` | Past sessions (§3.7.6). The second thing anyone opens a gym app for, and it has nowhere else to be now that the landing is routines. |
| **3** | `"workout_library"` | **`Exercises`** | `DaybookIcons.Category` | The exercise catalog is a manage-able surface: a labelled nav item is more discoverable than an overflow menu, needs no new affordance, and it is where you go to rename or archive an "Imported" row from a Hevy import (§3.9.4). Cost: zero new screens — it is `AddExerciseScreen` in `BROWSE` mode (§3.7.3). |

**Superseded by Round B (§7.4), recorded here rather than silently rewritten so this section stays
an honest record of what Round A actually shipped.** The user found the Exercises tab unused after
living with the shipped app. Round B repurposes this same third slot — same `WorkoutRoutes.LIBRARY`
route constant, same nav position, same icon slot — into the health data page, with a new label
and icon (§7.4). `AddExerciseScreen` in `BROWSE` mode does not disappear from the codebase; it
simply stops being reachable from the bottom nav, staying reachable only as `PICK` mode from inside
a session or routine editor (§3.6.6's table). Everything else in this §3.6 — the gesture, the
entry/exit points, `WorkoutRoutes.HOME`/`.HISTORY`, the accent provider — is unaffected.

**Deliberately not a nav destination:**

- **Beast Mode's Settings (`"workout_settings"`).** A settings screen is a place you visit, not a
  place you live, and Daybook's own three-tab nav does not carry Settings either — it hangs off
  the header (the `Avatar` on Habits and Intake). §3.7.4 puts the gear in exactly that slot.
- **The live session.** A stacked route on purpose (§3.6.5): you are *in* a workout, and a nav bar
  offering to leave is the wrong furniture.

**Exact change at the call site — `ui/MainActivity.kt` (~`:525` and `:608`).** This is the whole of
"Beast Mode has a nav bar"; there is no change inside `DaybookScaffold` itself:

```kotlin
// Beast Mode's three destinations show a pill nav of their own. Every OTHER workout route (the
// live session, the pickers, the forms, workout_settings) still renders full-screen with no nav,
// which `onMain` already gives us for free (§3.6.0).
val inBeastNav = backStackRoute in WorkoutRoutes.NAV
val showNav = onMain || inBeastNav

val workoutIcon: ImageVector = ImageVector.vectorResource(R.drawable.ic_workout)
val beastNavItems = remember(workoutIcon) {
    listOf(
        NavItemSpec(WorkoutRoutes.HOME,    workoutIcon,             "Routines"),
        NavItemSpec(WorkoutRoutes.HISTORY, DaybookIcons.Clock,      "History"),
        NavItemSpec(WorkoutRoutes.LIBRARY, DaybookIcons.Category,   "Exercises")  // Round B (§7.4)
                                                                                   // changes this
                                                                                   // item's icon
                                                                                   // and label to
                                                                                   // "Health" —
                                                                                   // the route
                                                                                   // constant and
                                                                                   // slot position
                                                                                   // do not change.
    )
}

// A flat three-destination switch, not a growing stack: HOME stays underneath, so system back
// from History or Exercises returns to the landing rather than out of the mode (§3.6.5).
val goBeastRoute: (String) -> Unit = remember(navController) {
    { route ->
        navController.navigate(route) {
            popUpTo(WorkoutRoutes.HOME) { inclusive = false; saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

DaybookScaffold(
    showNav = showNav,
    currentRoute = if (onMain) visibleRoutes.getOrElse(settledPage) { visibleRoutes.first() }
                   else backStackRoute,
    navItems = if (inBeastNav) beastNavItems else navItems,
    onSelectRoute = if (inBeastNav) goBeastRoute else onSelectRoute,
    onLongSelect = if (inBeastNav) { _ -> exitBeastMode() } else { _ -> goWorkout() },
    longPressRoute = if (inBeastNav) WorkoutRoutes.HOME else "home",
    longPressLabel = if (inBeastNav) "Leave Beast Mode" else "Start a workout",
    hintDotRoutes = if (!inBeastNav && hintState < 2) setOf("home") else emptySet(),
    …
)
```

Three things this must not become: **not** a second nav component (`FloatingPillNav` is passed
different `items`; it is not forked, subclassed or copied — its shadow, clip, hairline, insets
padding, icon-size swap and tint animation are all inherited unchanged, which is what makes Beast
Mode read as the same app in a different mood); **not** a `BackHandler` (the `popUpTo(HOME)` above
is what makes back behave); **not** a change to `NavConfig` (Beast Mode's nav items are a plain
`listOf(...)` in `MainActivity`, not user-configurable, not in `nav_tabs`, not in `ALL_ROUTES`).

**The coach-mark and the hint dot are Daybook-nav-only.** `hintDotRoutes` is passed `emptySet()`
while `inBeastNav` — a dot on the Routines icon would be telling the user about the gesture they
are already using.

#### 3.6.8 Leaving by the same gesture that got you in

**Press-and-hold Beast Mode's leftmost nav item (`Routines`) to leave Beast Mode and land on
Today — the exact same mechanism as holding `Today` to get in, applied symmetrically.** Literally
the same code, in `FloatingPillNav`'s `items.forEach` loop (§3.6.1 b), differing only in the two
parameters §3.6.1 added for this purpose:

| | Entering | Leaving |
|---|---|---|
| Gate | `longPressRoute = "home"` | `longPressRoute = WorkoutRoutes.HOME` |
| Threshold | the platform's `longPressTimeoutMillis` (~500 ms, or the user's own setting) | the same |
| Dead zone | 120 ms | the same |
| Ramp | scale 1.00 → 1.18 + tint lerp to `LocalAccent.current`, `LinearEasing` | the same function, the same numbers — and because the mode's accent is provided around the whole scaffold (§3.8.3), the ramp inside Beast Mode is **Coral** |
| Haptic | `HapticFeedbackType.LongPress`, first statement in `onLongClick` | the same |
| Reduced motion | ramp dropped, tint snaps, haptic still fires | the same |
| TalkBack / switch access | `onLongClickLabel = "Start a workout"` | `onLongClickLabel = "Leave Beast Mode"` |

**The only thing that differs is what happens at the end**, and the label that announces it.
There is no second `combinedClickable` anywhere in the app.

**The handler, in `ui/MainActivity.kt`:**

```kotlin
// popBackStack(to "main") clears the whole workout stack in one pop, however deep the user was:
// landing, history, library, a routine editor. goToPage(0) lands on TODAY specifically — the
// gesture began on Today, so it ends on Today. System back keeps its own behaviour (§3.6.5).
val exitBeastMode: () -> Unit = remember(navController, goToPage) {
    {
        navController.popBackStack(route = "main", inclusive = false)
        goToPage(0)
    }
}
```

**A normal tap on the same item is a normal tap** — `goBeastRoute(WorkoutRoutes.HOME)`, a no-op
when already there. No confirm dialog, no double-tap-to-exit: the exit is free to reverse (hold
Today again) and nothing is lost — P1 guarantees every completed set is already in Room.

**The accessible floor.** A hold-to-exit has the identical accessibility problem as a
hold-to-enter (R21): `onLongClickLabel` puts it in TalkBack's and switch access's actions menu as
a named action, system back leaves from the landing unconditionally, and §3.8.2's `Leave Beast
Mode` row is the always-visible, always-tappable floor (R26).

---
### 3.7 UI (new package `ui/workout/`)

All screens built from `DaybookScaffold`'s `contentPadding`, `ScreenHeader`, `SoftCard`,
`SectionHeader`, `PrimaryButton`, `GhostButton`, `CircleIconButton`, `EmptyState`,
`SegmentedControl`, `SortSheet`, `ConfirmDeleteDialog`, `UndoSnack`, `StickySaveBar`. Colours from
`DaybookColors` / `CardTints` / `LocalAccent` only. Shapes from `AppShapes`. Motion gated on
`LocalReduceMotion`. The one departure from "no new visual vocabulary" is deliberate and scoped:
the Add-Exercise picker (§3.7.3) now renders real RepDB illustrations, because that art is
licensed and bundled (§3.3) — everywhere else in Daybook, including every other Workout screen,
stays icon-and-tint only.

| File | Route | What it is |
|---|---|---|
| `WorkoutHomeScreen.kt` | `"workout"` — the Beast Mode landing | "My routines" + the empty-workout button, with Beast Mode's own nav bar under it and its settings gear in the header. §3.7.4. |
| `WorkoutHomeViewModel.kt` | — | `@HiltViewModel`. `observeRoutineSummaries()` + `observeActiveSession()`. Flows `flowOn(Default)` + `stateIn(WhileSubscribed(5_000))`. |
| `WorkoutHistoryScreen.kt` + `WorkoutHistoryViewModel.kt` | `"workout_history"` | The past-sessions list. §3.7.6. |
| `WorkoutSettingsScreen.kt` + `WorkoutSettingsViewModel.kt` | `"workout_settings"` | Beast Mode's own settings. §3.8.2. |
| `RoutineEditScreen.kt` + `RoutineEditViewModel.kt` | `"routine_edit?routineId={routineId}"` | Create / edit a routine. §3.7.5. |
| `RoutineTargetSheet.kt` | (sheet) | Per-exercise targets inside the routine editor, columns driven by the same `columnsFor(trackingMode)` the live set table uses. §3.7.5. |
| `WorkoutRoutes.kt` | — | The route-id constants + `NAV` / `ALL`. §3.6.6. |
| `WorkoutSessionScreen.kt` | `workout_session/{sessionId}` | The live log — the richest screen in Round A. §3.7.1. A routine-started session is an ordinary session whose blocks and sets happen to exist already. |
| `WorkoutSessionViewModel.kt` | — | Owns the elapsed-time ticker and the rest-timer ticker (§3.7.2), the per-block `PREVIOUS` map and pre-session bests (§3.4), `sessionStats`, and consumes `"picked_exercise_id"` from the picker's `SavedStateHandle` result. |
| `AddExerciseScreen.kt` | `"add_exercise"` (PICK) and `"workout_library"` (BROWSE) | §3.7.3. |
| `FilterSheet.kt` | (sheet) | One composable, `@Composable fun FilterSheet(title: String, options: List<FilterOption>, selectedId: String?, allLabel: String, onSelect: (String?) -> Unit, onDismiss: () -> Unit)` with `data class FilterOption(val id: String, val label: String, val imageId: String)`, powering both the muscle sheet (`title = "Muscle group"`, `allLabel = "All muscles"`) and the equipment sheet (`title = "Equipment"`, `allLabel = "All equipment"`) — the same list with different data, so one file is one place to fix one bug. |
| `ExerciseFormScreen.kt` | `new_exercise`, `edit_exercise/{id}` | Name, primary-muscle picker, equipment picker, tracking mode. Mirrors `AddHabitScreen`/`HabitForm`'s structure and `StickySaveBar`. Reached from the Add-Exercise screen's header action. |
| `ExerciseHistorySheet.kt` | (sheet) | What the trend button opens: reverse-chronological list of every past session containing this exercise, its sets, and a medal on the best one. A list, not a chart. |
| `WorkoutDetailScreen.kt` | `workout_detail/{sessionId}` | Read view of a finished session with an "Edit" affordance that reopens `workout_session/{id}`. Shows Duration / Volume / Sets and per-block notes. Reached from History; when `WorkoutSession.routineId` resolves to a live routine it also shows a `GhostButton` **`Start this routine again`** → `startSessionFromRoutine`; when it resolves to nothing (a deleted routine) the button is simply not rendered. |

#### 3.7.1 `WorkoutSessionScreen` — the live log

Built entirely from existing components, mapped from each thing in the reference screenshots to
the Daybook component that renders it:

| Reference element | Daybook rendering |
|---|---|
| Header: chevron, "Log Workout" → elapsed time on scroll, alarm icon, blue **Finish** | Existing `BackHeader` with a title that swaps to the elapsed time once the list is scrolled past the stats row (a `derivedStateOf` on the `LazyListState` — no new component). `CircleIconButton` for the rest-timer-defaults sheet in place of the alarm icon. `PrimaryButton` "Finish" as the header action. |
| Thin progress bar under the header | Dropped — encodes nothing Daybook tracks; there is no target to progress toward without a routine, and a routine-started session's target is the pre-filled rows themselves. |
| Stats row: `Duration` / `Volume` / `Sets` | One `SoftCard` with three label-over-value columns, `DaybookText.Caption` label + value, Duration in `LocalAccent.current` because it is live. Figures from `sessionStats` (§3.4). |
| Two muscle-shaded body silhouettes | Deferred (§3.1.2) — a live, session-wide composited view, not a still. |
| Per-exercise card: thumbnail, accent name, `⋮` | `SoftCard` + the exercise's **RepDB illustration** (`images/flat/<imageId>-start.webp`, §3.3) in a rounded thumbnail slot + the name in `LocalAccent.current` + the existing overflow affordance (remove exercise, reorder, open history). |
| `Add notes here…` | A borderless `Forms.kt` text field bound to `WorkoutExercise.notes`. |
| A pinned, always-visible note per exercise | If `Exercise.notes` is non-blank, rendered under the exercise name in `DaybookText.Metadata` / `DaybookColors.TextMuted`, visually distinct from the per-block "notes for today" field above. No schema cost — `Exercise.notes` already exists (§3.2); the edit affordance is the existing `ExerciseFormScreen`. |
| `Rest Timer: OFF` row | A tappable row opening a duration sheet (Off / 30s / 60s / 90s / 2m / 3m / 5m / custom), writing `WorkoutExercise.restSeconds`, pre-filled from `lastRestSecondsForExercise` then `app_settings.rest_timer_default_seconds` (§3.4). |
| The set table, columns varying by exercise | A header `Row` + one `Row` per set, built from `trackingMode`: `WEIGHT_REPS` → SET · PREVIOUS · KG* · REPS · ✓; `REPS_ONLY` → SET · PREVIOUS · REPS · ✓; `DURATION` → SET · PREVIOUS · TIME · ✓; `DISTANCE_DURATION` → SET · PREVIOUS · DISTANCE · TIME · ✓. A pure `columnsFor(trackingMode)` function, unit-tested per mode. (\* "KG" reads "LB" when `app_settings.weight_unit = LB`; storage stays kg, §3.8.) |
| `PREVIOUS` = "30kg x 15", `–` when absent | From `previousBySetNumber` (§3.4), formatted by a shared `util/` formatter. `DaybookColors.TextMuted`. |
| Completed row solid green + filled check | `DaybookColors.Success` at the existing container alpha, via `CardTints` — the same treatment a completed habit row already gets. Honours `LocalIsDark`. |
| Gold medal replacing the set number on a PR | A small existing-vocabulary icon tinted `DaybookColors.Warning`, not a new gold literal. `contentDescription = "Personal record"`. |
| `+ Add Set` per exercise | `GhostButton`. Pre-fills from that block's previous set. |
| `+ Add Exercise` / `Settings` / `Discard Workout` | `PrimaryButton` / `GhostButton` / `GhostButton` in `DaybookColors.Danger` with the existing `ConfirmDeleteDialog` in front of it. |

Weight and reps use plain numeric `TextField`s with the app's existing `Forms.kt` styling, not a
new picker component. The whole page is `imePadding()`-aware — the build-22 IME-overlap fix is
the precedent and must not be regressed; a set table with a focused field two-thirds down the
page is the single most likely place to regress it.

#### 3.7.2 The two tickers, and why they are C7-safe

Two things count on this screen: the session elapsed time and the rest timer. Both use the
identical shape:

- The source of truth is a **timestamp**, never a counter: `startedAt` for the session,
  `restEndsAt` for the rest timer. Elapsed/remaining is always recomputed from
  `System.currentTimeMillis()`, so nothing drifts and nothing has to survive anything.
- Ticking is a single `LaunchedEffect(isRunning) { while (true) { emit(); delay(1000) } }` in the
  ViewModel, collected with `collectAsStateWithLifecycle`. It stops when the screen leaves
  composition **and** when the app is backgrounded, both for free.
- **No `WorkManager`, no `AlarmManager`, no `setExactAndAllowWhileIdle`, no foreground service, no
  `WakeLock`, no notification channel, no `POST_NOTIFICATIONS`, no sound, no vibration.** Nothing
  new in the manifest for either timer.
- Coming back to the screen mid-rest shows the correct remaining time (or "Rest over"), because
  the end-time is stored, not the countdown.

A build that needs any of the forbidden list has misread this section and must stop and re-ask.

#### 3.7.3 `AddExerciseScreen`

A full screen, not a sheet — it needs room for a search field, two filter buttons, section
headers and a long list, and it is also reachable outside a session to manage the catalog.

**One screen, two modes, two routes:**

```kotlin
enum class ExercisePickerMode { PICK, BROWSE }

@Composable
fun AddExerciseScreen(
    mode: ExercisePickerMode,
    contentPadding: PaddingValues,          // from DaybookScaffold; carries the nav clearance
                                            // in BROWSE and the bare inset in PICK, for free
    onPick: (exerciseId: String) -> Unit,   // PICK: set the SavedStateHandle result and pop.
                                            // BROWSE: open ExerciseHistorySheet instead.
    onBack: (() -> Unit)?,                  // PICK only; null in BROWSE (it is a nav destination)
    onNewExercise: () -> Unit,
    onEditExercise: (exerciseId: String) -> Unit,
    onOpenHistory: (exerciseId: String) -> Unit
)
```

| | `PICK` — route `"add_exercise"` | `BROWSE` — route `"workout_library"` |
|---|---|---|
| Header | `BackHeader(title = "Add exercise", onBack)` + a trailing **`New`** action | `ScreenHeader(title = "Exercises", subtitle = "<n> exercises")` + a trailing 40 dp `CircleIconButton(MI.Filled.Add, "New exercise")` — the same header shape §3.7.4 uses |
| Pill nav | no | **yes** (item 3, §3.6.7) |
| Tapping a row | returns `picked_exercise_id` and pops (§3.6.6) | opens `ExerciseHistorySheet` — nothing to add it to in BROWSE |
| Row overflow | — | `Edit` → `edit_exercise/{id}`, `Archive` |

Everything else — the search field, the two filter buttons, the sheets, Recent, All exercises, the
"Imported" badge, the empty state — is identical in both modes and written once:

- `BackHeader` with a "New" action in the trailing slot → `new_exercise`.
- A search `TextField` (`Forms.kt`), matching on the normalised name (§3.9.4's normaliser — one
  implementation, used by both search and import).
- **Two half-width `GhostButton`s in a `Row`**: *All equipment* / *All muscles*, each showing the
  active filter's label when set, each opening its sheet — each sheet row now carries a leading
  round image (`images/muscles/*.webp` or `images/equipment/*.webp`, §3.3), the first row being
  the "All …" clear-filter option with a plain icon.
- `SectionHeader("Recent")` over the exercises used in the last ~10 sessions (a `DISTINCT
  exercise_id ORDER BY started_at DESC LIMIT 12` query), then `SectionHeader("All exercises")`
  over the merged `builtins + customs`, alphabetical.
- **Each row: a round exercise thumbnail from RepDB** (`images/flat/<imageId>-start.webp`, or
  `-main.webp` for a static hold; a custom exercise with no RepDB image falls back to the tinted
  icon), name, primary-muscle subtitle, and a trailing `CircleIconButton` trend affordance opening
  `ExerciseHistorySheet`. Tapping the row itself adds the exercise to the session.
- An imported exercise (`Exercise.source = "IMPORTED_HEVY"`) carries a small "Imported" label in
  the subtitle line.
- `EmptyState` when a filter combination matches nothing, offering "Create \"<search text>\"".

#### 3.7.4 `WorkoutHomeScreen` — the Beast Mode landing

**The header:**

```kotlin
ScreenHeader(
    title = "Beast Mode",
    subtitle = "$routineCount routines",          // "1 routine" / "No routines yet"
    actions = {
        CircleIconButton(
            icon = MI.Filled.Settings,             // material-icons-core; no new vector
            contentDescription = "Beast Mode settings",
            onClick = onOpenWorkoutSettings,       // -> "workout_settings" (§3.8.2)
            size = 40.dp                           // == Avatar(size = 40.dp) on the other tabs
        )
    }
)
```

The same composable, argument shape and trailing-slot size as `RoutinesScreen.kt:65` and
`FoodMedScreen.kt:63` — the trailing control lands in the identical corner position the `Avatar`
occupies on Today / Habits / Intake. The one difference is where it goes: Beast Mode's own
settings, never the app's.

- **No `Avatar` here** — a profile photo would say "this leads to your account", and to the wrong
  settings screen. `CircleIconButton` in `CircleStyle.Ghost` is the app's existing icon-button.
- **No leading control.** `ScreenHeader` has no leading slot; the non-gesture exit is the `Leave
  Beast Mode` row in §3.8.2. Do not add a leading slot to `ScreenHeader` for this — a shared
  component gaining a parameter for one caller is how a design system erodes.
- Beast Mode's pill nav sits underneath (§3.6.7), so the `LazyColumn` consumes
  `DaybookScaffold`'s `contentPadding`, which carries `navClearance` here.

**The body, top to bottom.** One `LazyColumn`, `contentPadding` straight from the scaffold,
`verticalArrangement = Arrangement.spacedBy(Spacing.listGap)`, `key = { it.id }`:

1. **Resume banner** — a `SoftCard`, rendered only while `observeActiveSession()` emits non-null.
   Title `Workout in progress`, subtitle `Tap to carry on`, tinted with the Beast accent, tap →
   `workout_session/{id}`. The same Flow the Today row reads (§3.6.4).
2. **The empty-workout button** — a full-width `PrimaryButton`, exact label **`Start an empty
   workout`**, `onClick = { startEmptySession() }` → `workout_session/{newId}`.
3. `SectionHeader("My routines")`.
4. **One `SoftCard` per routine**, from `observeRoutineSummaries()` (§3.4):
   - leading `CardTints` tinted circle + `ic_workout` — routines are template metadata, not a
     RepDB exercise, so they keep the tinted-circle vocabulary rather than an illustration;
   - title = `name`; subtitle = `"5 exercises · 15 sets"` from `exerciseCount` / `targetSetCount`,
     singular/plural correct, and `"5 exercises"` alone when `targetSetCount == 0` — never `"·
     0 sets"`. A routine with no exercises at all reads **`No exercises yet`**;
   - **tapping the card starts a session from that routine** → `startSessionFromRoutine(id)` →
     `workout_session/{newId}`;
   - trailing `CircleIconButton(MI.Filled.MoreVert, "More")` opening the existing `Sheets.kt`
     action sheet with three actions: **`Edit`** (→ `routine_edit?routineId=…`), **`Duplicate`**
     (→ `duplicateRoutine`), **`Delete`** (`destructive = true`, see the copy below).
5. **`+ New routine`** — a full-width `GhostButton` at the end of the routine list rather than a
   FAB, because the screen already has a `PrimaryButton` above and two competing primary actions
   is the thing to avoid. → `routine_edit` with no arg.
6. **`EmptyState`** when there are no routines (replacing 3–5, keeping 1 and 2): icon `ic_workout`
   tinted `CardTints.Neutral`, title **`No routines yet`**, body **`A routine is a list of
   exercises you do together — build one once and start it with a tap.`**, action **`+ New
   routine`**. The `Start an empty workout` button stays visible above it, so a brand-new user is
   never forced through routine-building to log a workout.

**Delete copy:** `ConfirmDeleteDialog` with title **`Delete this routine?`**, body **`The
workouts you've already done with it are kept.`**, confirm **`Delete`**; afterwards
`UndoSnack(token, text = "Routine deleted")`. On failure, the same snack reads **`Couldn't delete
that routine. Try again.`**

**Starting a workout while one is already running.** Tapping either the empty-workout button or a
routine card while `observeActiveSession()` is non-null opens `DaybookAlertDialog`: title **`A
workout is already running`**, body **`Finish or discard the workout you've got going before
starting another one.`**, confirm **`Carry on with it`** → `workout_session/{activeId}`, dismiss
**`Not now`**. Not a disabled button, not a silent second session, not an auto-discard.

**Why `Start an empty workout`.** With routines on the same screen, "Start workout" is ambiguous —
it could mean one of the routines below it. Rejected alternatives: `Quick Start` (Title Case,
marketing-flavoured, and "quick" promises speed, not the absence of a routine); `Freestyle
Workout` (jargon, reads as a *kind* of training); `Start Blank Workout` (right meaning, wrong
casing, needless second word next to `EmptyState` vocabulary); `Start without a routine`
(accurate but defines the button by what it lacks — the most-used button on the screen for
anyone without a routine yet). **Final: `Start an empty workout`** — it names the thing you get,
in Daybook's plain sentence-case voice, and cannot be misread as starting one of the routines
underneath it. It pairs with the empty state's `No routines yet` and History's `No workouts yet`
(§3.9.10a F).

#### 3.7.5 `RoutineEditScreen` — creating and editing a routine

Route `"routine_edit?routineId={routineId}"`. Argument optional: absent = create, present = edit —
the same shape `ExerciseFormScreen` uses for `new_exercise` / `edit_exercise/{id}`, and the same
shape `AddHabitScreen` / `EditHabitScreen` already use.

```kotlin
@Composable
fun RoutineEditScreen(
    routineId: String?,                 // null == create
    onNavigateBack: () -> Unit,
    onPickExercise: () -> Unit,         // -> "add_exercise" (PICK mode, §3.6.6)
    viewModel: RoutineEditViewModel = hiltViewModel()
)

@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    data class State(
        val isEdit: Boolean = false,
        val name: String = "",
        val notes: String = "",
        val exercises: List<RoutineExerciseDraft> = emptyList(),
        val busy: Boolean = false,
        val done: Boolean = false,
        val rejectedMessage: String? = null      // C9 — same field name RespondViewModel uses
    )
    val state: StateFlow<State>
    fun setName(v: String); fun setNotes(v: String)
    fun addExercise(exerciseId: String)          // fed by the picker's SavedStateHandle result
    fun removeExercise(draftId: String)
    fun moveExercise(from: Int, to: Int)
    fun setTargets(draftId: String, targets: RoutineExerciseDraft)
    fun save()
}
```

**Chrome:** `BackHeader(title = if (isEdit) "Edit routine" else "New routine", onBack)` — a
stacked route, no pill nav — plus the existing `StickySaveBar` carrying one `PrimaryButton(text =
"Save routine", enabled = name.isNotBlank() && !busy)`. `imePadding()` throughout.

**The fields:**

| # | Field | Component | Rules |
|---|---|---|---|
| 1 | **Routine name** | single-line `Forms.kt` field, label `Name`, placeholder **`e.g. Push day`** | Required (Save disabled while blank). Normalised + deduped by `uniqueRoutineName` (§3.4). |
| 2 | **Notes** (optional) | multi-line `Forms.kt` field, label `Notes`, placeholder **`Anything to remember about this routine`** | Free text → `workout_routines.notes`. |
| 3 | **Exercises** | `SectionHeader("Exercises")` over a `LazyColumn` of rows | Each row: `RepDB thumbnail` + name, a targets-summary line underneath, a trailing drag handle for reorder and a trailing `MoreVert` overflow with **`Set targets`** and **`Remove`**. Tapping the row body opens the target sheet. |
| — | `+ Add exercise` | full-width `GhostButton` under the list | → the picker (`PICK` mode); the returned `picked_exercise_id` becomes a new `RoutineExerciseDraft` appended at the end with every target null. |
| — | empty | inline `EmptyState`: title **`No exercises yet`**, body **`Add the exercises you do in this routine, in the order you do them.`** | A routine may be saved with no exercises — starting it then behaves exactly like `Start an empty workout`. |

**The targets summary line** — a pure `fun targetSummary(d: RoutineExerciseDraft, mode: String,
unit: WeightUnit): String`, with its own `RoutineTargetSummaryTest`: `3 × 10 · 60 kg`
(WEIGHT_REPS, all three set) · `3 × 10` (weight absent) · `3 sets` (reps absent) · `3 × 45s`
(DURATION) · `3 × 5.0 km` (DISTANCE_DURATION) · **`No targets`** (everything null). `kg`/`lb`
follows `app_settings.weight_unit`, display-only.

**`RoutineTargetSheet`** — a `Sheets.kt` bottom sheet (drag handle + title `Targets`), fields
chosen by the same `columnsFor(trackingMode)` the live set table uses:

| `trackingMode` | Fields shown |
|---|---|
| `WEIGHT_REPS` | Sets · Reps · Weight · Rest · Note |
| `REPS_ONLY` | Sets · Reps · Rest · Note |
| `DURATION` | Sets · Time · Rest · Note |
| `DISTANCE_DURATION` | Sets · Distance · Time · Rest · Note |

Every field is a plain numeric `Forms.kt` field, every one optional, and an empty field stores
`NULL`, never `0`. `Rest` reuses the live session's existing duration options. Confirm label
**`Done`**.

**Save (C9).** `save()` mirrors `RespondViewModel.log()`: set `busy`, call `createRoutine` /
`updateRoutine` inside `runCatching`, and on failure set `rejectedMessage = "Couldn't save this
routine. Try again."` — which `StickySaveBar` already renders. On success, `done = true` and the
screen pops.

#### 3.7.6 `WorkoutHistoryScreen`

- `ScreenHeader(title = "History", subtitle = "<n> workouts")` — no trailing action (the settings
  gear lives on the landing). Beast Mode's pill nav underneath, consume `contentPadding`.
- A `LazyColumn` of past sessions, newest first, grouped by date, `key = { it.id }`: one `SoftCard`
  per session — date, title, `"5 exercises · 18 sets · 4,200 kg"` — tap → `workout_detail/{id}`,
  overflow → edit / delete with the existing `UndoSnack`.
- An in-progress session appears as the first row, tinted with the Beast accent, reading `Workout
  in progress` / `Tap to carry on` → `workout_session/{id}`.
- `observeRecentSessions(limit)` (§3.4) — paged by limit, never the whole history.
- `EmptyState`: title **`No workouts yet`**, body **`Start a workout to log your first session, or
  bring your history over from Hevy.`**, primary action **`Start an empty workout`**, secondary
  `GhostButton` **`Import from Hevy`**.

**Why the Hevy import button lives here and not on the landing:** §3.9 imports workout **history**,
so its entry point belongs on the History screen. The symmetry is deliberate — a future routine
importer, if one is ever built (§3.2.1), would hang off the **routines** empty state instead.

### 3.8 Settings additions

#### 3.8.1 What stays in Daybook's main Settings: exactly one row

`ui/settings/SettingsScreen.kt` gains one `FormGroup` with one row, placed immediately after the
existing Appearance group and before "Backup & data", built from `SettingsComponents.kt`'s
existing `SettingsGroup` / `SettingsRow`:

```
SectionHeader("Workout", subtitle = "Beast Mode has its own settings, inside it.")
  └─ SettingsRow(icon = ic_workout, title = "Open Workout", onClick = goWorkout)
```

- **`Open Workout`.** A plain navigation row calling the same `goWorkout()` the long-press calls.
  It is an **entry point, not a setting** — moving it into Beast Mode would be self-defeating: a
  way *into* Beast Mode that can only be found *inside* Beast Mode is not a way in at all. The
  guaranteed, always-visible, always-reachable door for anyone who does not know the gesture,
  cannot perform it, or is driving TalkBack (R21, §3.6.4). Behind no toggle, ever.
- The group subtitle does real work: someone who remembers changing kg→lb and comes back to
  Settings looking for it must not conclude the setting vanished.
- **`AboutSettingsScreen`** gains the RepDB attribution line (§3.3.5): **`Exercise data by
  [RepDB](https://repdb.co)`**, in the existing credits list style. This is the only other touch
  to `ui/settings/` outside the one Workout row.
- `ui/settings/NavigationSettingsScreen.kt`, `AppearanceSettingsScreen`, `DataSettingsScreen` and
  every other `settings_*` route are otherwise untouched — except the Hevy import row
  `DataSettingsScreen` gains in §3.9.1, which is an import action sitting beside the JSON import
  it shares all its plumbing with, not a Workout setting.

#### 3.8.2 Beast Mode's own settings screen

**Route `"workout_settings"`. Files `ui/workout/WorkoutSettingsScreen.kt` +
`ui/workout/WorkoutSettingsViewModel.kt`.**

**Why `ui/workout/` and not `ui/settings/`:** the existing `ui/settings/` package is Daybook's
settings *hierarchy* — the `settings_*` routes reachable from the `Avatar`. This screen is
reachable from exactly one place, the gear on Beast Mode's landing header, and is scoped to the
mode. Filing it under `ui/workout/` keeps "everything Beast Mode" in one package. The naming
convention still follows exactly — `WorkoutSettingsScreen.kt` / `WorkoutSettingsViewModel.kt`,
matching `AboutSettingsScreen.kt` / `NavigationSettingsScreen.kt` — only the package differs, and
the route id deliberately does not use the `settings_` prefix, so a grep for Daybook's settings
hierarchy does not pick it up by accident.

**Chrome:** a stacked route, no pill nav — `BackHeader(title = "Beast Mode settings", onBack)` and
a `LazyColumn` of `SettingsGroup`s built from the existing `SettingsComponents.kt` composables. It
is wrapped in the Beast accent like every other workout route (§3.8.3), so its switches and
segmented controls are Coral.

| # | Row | Control | Backed by | Notes |
|---|---|---|---|---|
| 1 | **Weight unit** | `SegmentedControl` **kg / lb** | `app_settings.weight_unit` | Storage is always kg; lb is a render-time conversion (`kg * 2.2046226f`, rounded to 0.5 lb). A pure `WeightFormatTest` guards the round trip. Applies to the live set table, the routine target sheet and every Volume figure. |
| 2 | **Beast Mode accent** | the existing `StyleSwatchRow` accent picker — five swatches | `app_settings.workout_accent_color`, default `CORAL` | §3.8.3. Changing it re-tints the whole mode including its nav bar, live. |
| 3 | **Default rest timer** | Off / 30s / 60s / 90s / 2m / 3m / 5m | `app_settings.rest_timer_default_seconds` | Only the *starting* value for a new exercise block. Precedence: routine target → last time's block value → this → OFF. Same control the session header's rest-timer icon opens. |
| 4 | **`Show Beast Mode on Today`** | switch | `app_settings.workout_today_card_enabled` | Controls the Today row of §3.6.4. Safe to turn off because `Open Workout` in main Settings is unconditional and the hold still works; safe to find again because you turn it back on from inside Beast Mode, which is always reachable. |
| 5 | **`Leave Beast Mode`** | a `SettingsRow` in its own group at the bottom, no chevron | — | Calls the same `exitBeastMode()` the hold calls. The exit's accessibility floor, the exact counterpart of `Open Workout` being the entrance's. |

All of rows 1–4 are device-local — not in `BackupModel`, not in `ContentHash`, not synced, the
standing treatment for every `app_settings` column since v16. (`workout_hint_state` has no row
anywhere: a "reset the workout tip" control is clutter for a one-time hint.)

**What this screen must not grow:** a second `Open Workout`, a second Hevy-import row, a "reset my
workout data" button, or anything about habits, intake, theme, account or backup. Four preferences
and a door.

#### 3.8.3 Beast Mode's visual identity

The louder-accent identity applies to the **whole** Beast Mode experience — the landing screen,
the routine editor, the history list, the exercise library, Beast Mode's own settings screen, the
live session, **and its bottom nav bar**. Every route in `WorkoutRoutes.ALL` is inside it.

Habits' and Intake's accents tint surfaces that sit inside shared chrome — the same screen frame,
the same pill nav, often two of them visible in one glance on Today, so a fourth accent there has
to harmonise. **Beast Mode shares no chrome with the rest of the app**: its own header, its own
full-screen surfaces, its own bottom nav with its own destinations, entered deliberately and left
deliberately. So the accent gets to be louder, and that is the point of the mode. Default: **CORAL**,
the highest-intensity value in the existing five-colour palette (`Accent.kt:21` — `#FB7185` dark /
`#E23D5B` light).

**The constraint is unchanged and absolute (C5): no new colour literal.** Beast Mode picks one of
the five existing `AccentColor` values. No "gym red", no gradient, no new `DaybookColors` entry,
no second type ramp, no new shape.

**Mechanism — one provider, around the scaffold, gated on the route.** `LocalAccent` is a
`staticCompositionLocalOf` provided once by `DaybookTheme` (`Theme.kt:153`), so re-providing it
for a subtree is a supported, one-line move. **The subtree must be the `DaybookScaffold` call, not
each route's content**, or the nav bar is left behind — `FloatingPillNav` is drawn by
`DaybookScaffold`, which sits **outside** the `NavHost`. In `ui/MainActivity.kt`, immediately
around the existing `DaybookScaffold(...)`:

```kotlin
// The Beast Mode accent covers EVERY workout route AND the pill nav, because the nav is drawn by
// DaybookScaffold, outside the NavHost. Gated on the route, so `main` and every settings/detail/
// form route keep the app accent exactly as today.
val inBeast = backStackRoute in WorkoutRoutes.ALL
val modeAccent = if (inBeast) workoutAccent else appAccent          // both AccentColor values
val modeAccentColor = modeAccent.colorFor(LocalIsDark.current)
CompositionLocalProvider(
    LocalAccent provides modeAccentColor,
    LocalOnAccent provides onAccentInk(modeAccentColor)
) {
    DaybookScaffold(/* §3.6.7 */) { contentPadding -> NavHost(...) }
}
```

**`LocalOnAccent` must be re-provided alongside it** — on-accent ink is per-accent and computed
(Coral measures 4.16:1 against white); providing the accent without its ink is how the Finish
button ends up with unreadable text in light mode. Every component then picks the Beast accent up
for free, because they all already read `LocalAccent.current`: the live Duration figure, exercise
names, `+ Add Exercise`, the PR row, the resume banner and routine cards,
`FloatingPillNav`'s selected-item tint, and §3.6.2's press-and-hold ramp — so the exit hold inside
Beast Mode charges up in Coral while the entry hold on Today charges up in the app accent, a free,
correct signal that the two gestures go in opposite directions. **Use `WorkoutRoutes.ALL`, not a
hand-written set of literals** — a route missed out of an inline `setOf(...)` fails silently, that
one screen just renders in the wrong colour, and no test catches it.

**`LocalIsDark`, `LocalDaybookShapes` (corner scale) and `LocalReduceMotion` are not overridden.**
Beast Mode is a louder accent inside the user's chosen theme, not a theme of its own.

---
### 3.9 Hevy CSV import

The user's own Hevy export lives at `/home/abhiram/Downloads/workout_data.csv` (a 34-row sample)
and a second, real, full export at `/home/abhiram/Downloads/workout_datasum).csv` (4,734 rows,
299 sessions) — both are Hevy's standard CSV shape and both are used as test fixtures
(§3.9.10). Everything below was checked against both files, not assumed.

#### 3.9.0 The file, as actually read

```
"title","start_time","end_time","description","exercise_title","superset_id",
"exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
```

Facts confirmed by reading both samples, not assumed:

- **One row per set.** A session is the set of rows sharing `title` + `start_time` + `end_time`;
  an exercise block is a run of rows within it sharing `exercise_title`.
- **`start_time` / `end_time`** are e.g. `"8 Sep 2026, 07:29"` — a **local** wall-clock time, an
  **English** month abbreviation, and **no timezone at all**.
- **`set_index` is 0-based** per exercise block. Daybook's `set_number` is 1-based, so `+1`.
- **Quoting is inconsistent and both forms appear on the same line**: text fields are quoted
  (`"Morning workout ☀️"`, `""` for empty), numeric fields are bare (`25`, `10`, `20.5` — decimals
  appear in real exports), and an empty numeric field is nothing at all between two commas
  (`,,`). A parser that assumes every field is quoted, or that an empty field is `""`, fails on
  this exact file.
- **Non-ASCII is real**: a title can contain `☀️` (U+2600 U+FE0F). Encoding is not hypothetical.
- **Blank ≠ zero.** `weight_kg` is empty for a bodyweight exercise and for a plank; `reps` is
  empty for a plank (`duration_seconds` populated) and for cycling (`distance_km` and
  `duration_seconds` populated, `reps` and `weight_kg` empty). Writing `0` where the file says
  nothing invents data and corrupts both the volume total and PR detection.
- **`superset_id`** is a real Hevy column and a real Hevy feature — populated on 903 rows of the
  4,734-row real export.
- **`set_type`** is `"normal"` in both samples on hand, but Hevy's real export vocabulary also
  includes `warmup`, `dropset` and `failure` (§3.9.3).
- **`rpe`, `description` and `exercise_notes`** are empty in both samples on hand but are
  populated in real exports; all three map to columns Daybook already has.
- **Titles are not unique and are useless as an identity key** — the 34-row sample's four
  sessions all share one title; the 4,734-row export repeats titles like `"Day 197"` across many
  sessions. §3.9.5 depends on this.

#### 3.9.1 Entry point

**Primary: Settings → "Backup & data" → a new `SectionHeader("Import from another app")` with one
row, "Import from Hevy (CSV)".** Mirrors the existing JSON import exactly rather than inventing a
second import UX: the same confirm-first-then-pick order (`SettingsScreen.kt` ~line 964: a
`DaybookAlertDialog` whose confirm label is "Choose file", which then launches the picker), the
same `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` system picker
with MIME types `arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")`, the
same `_importResult` / `_isImporting` `StateFlow` pair and fixed-height result slot, the same
`MAX_IMPORT_BYTES = 10 MB` pre-read size guard.

**One deliberate difference in the dialog copy.** The JSON import's dialog is `destructive = true`
and says *"Importing replaces all current data on this phone."* The Hevy import replaces nothing —
it is a merge that skips duplicates (§3.9.5). Its dialog is `destructive = false`:
*"This adds your Hevy workouts to Daybook. Nothing already in Daybook is changed or removed, and
workouts you've already imported are skipped."*

**Secondary: the `EmptyState` on Beast Mode's History screen** gets an "Import from Hevy"
`GhostButton`. That navigation leaves Beast Mode for Settings — it calls `navigate("settings_data")`
directly rather than popping first, so the user lands on Backup & data with the Hevy row in view,
and `Back` from there returns them to the workout screen they came from.

#### 3.9.2 Parsing: hand-rolled, no new dependency

`app/build.gradle.kts` has no CSV library. Adding one (opencsv, commons-csv, kotlin-csv) would
break this file's own discipline, where every third-party pin carries a written justification
comment; add new R8/ProGuard surface to a release build already tracking its size; and cost a
compileSdk-34 compatibility question for a benefit measured in about eighty lines.

**A small hand-rolled RFC4180 reader, `data/workout/CsvReader.kt`.** It must handle: quoted and
unquoted fields on the same line; commas inside quotes; `""` as an escaped quote; a completely
empty field between two commas; `CRLF` and `LF`; a leading UTF-8 BOM (Hevy's export has been seen
to carry one, and an un-stripped BOM silently breaks the *first header name* only, a horrible bug
to find); and a trailing newline. Returns `List<Map<String, String>>` keyed by header name, so a
column Hevy adds or reorders later does not shift every field by one.

Encoding is already solved upstream: `StorageUtils.readText` decodes with explicit
`Charsets.UTF_8`. The reader must not re-decode. `CsvReaderTest` covers each bullet above plus
the real sample's first three lines verbatim.

#### 3.9.3 Column mapping

| Hevy column | Daybook | Notes |
|---|---|---|
| `title` | `WorkoutSession.title` | Kept verbatim, emoji and all. Never used as an identity key (§3.9.0). |
| `start_time` | `WorkoutSession.startedAt`, and `localDate` | See the date note below. |
| `end_time` | `WorkoutSession.endedAt` | |
| `description` | `WorkoutSession.notes` | |
| `exercise_title` | → an `exerciseId` via §3.9.4 | The hard part. |
| `superset_id` | `WorkoutExercise.supersetId` | Preserved losslessly, unread by Round A's UI. |
| `exercise_notes` | `WorkoutExercise.notes` | This is why `workout_exercises` exists (§3.2). |
| `set_index` | `WorkoutSet.setNumber` | `+ 1` — Hevy is 0-based, Daybook is 1-based. |
| `set_type` | `WorkoutSet.setType` | `normal`→`NORMAL`, `warmup`→`WARMUP`, `dropset`→`DROPSET`, `failure`→`FAILURE`; anything unrecognised → `NORMAL`, never a crash. |
| `weight_kg` | `WorkoutSet.weightKg` | Already kg. Blank → `null`, not `0`. |
| `reps` | `WorkoutSet.reps` | Blank → `null`. |
| `distance_km` | `WorkoutSet.distanceMeters` | × 1000. Blank → `null`. |
| `duration_seconds` | `WorkoutSet.durationSeconds` | Blank → `null`. |
| `rpe` | `WorkoutSet.rpe` | Blank → `null`. |
| — | `WorkoutSet.completedAt` | Set to the session's `endedAt`. Every row in an export is a set that happened; leaving `completedAt` null would make the whole import invisible to PREVIOUS and PR queries, which filter on `completed_at IS NOT NULL`. |
| — | `WorkoutSession.status` | `"COMPLETED"` — an exported session is finished by definition. |
| — | `WorkoutSession.source` | `"IMPORTED_HEVY"` (§3.9.6). |

**Why `set_type` gets a column, not a squeeze into `notes`.** `notes` is the user's own prose and
is shown to them as such. Writing `"dropset"` into it fabricates text the user never typed and
would collide with a real `exercise_notes` value.

**Dates.** `"8 Sep 2026, 07:29"` parses with `DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm",
Locale.ENGLISH)` — `Locale.ENGLISH` explicitly, because Hevy writes English month abbreviations
regardless of device locale, and a device set to, say, French would fail every row on a path
nobody tested locally. No zone, so interpreted in `ZoneId.systemDefault()` — the same convention
`ExportImportRepository.epochOf` already uses, and why `localDate` comes out timezone-stable and
buckets correctly in `MonthPartitioner`. The parser tries ISO-8601 first (Hevy's format has varied
across app versions), falling back to the `d MMM yyyy` pattern. A row whose date parses as neither
is skipped and counted, never guessed. `HevyDateParseTest` covers: the sample's exact format,
ISO-8601, a French default locale, and an unparseable string.

#### 3.9.4 Matching Hevy's exercise names to Daybook's catalog

Hevy's names do not match RepDB's slugs verbatim: `"Squat (Barbell)"`, `"Seated Cable Row - V Grip
(Cable)"`, `"Bicep Curl (Dumbbell)"`. **This is a heuristic. It will not be perfect, and the
design's job is to make its failures harmless and visible rather than pretend they won't happen.**

A pure `HevyExerciseMatcher` (no Room, fully unit-tested) runs four steps in order:

1. **Normalise.** Lowercase; strip the trailing parenthetical and keep it as an equipment hint
   (`"Squat (Barbell)"` → name `squat`, hint `BARBELL`); replace `-`, `–`, `/` and punctuation with
   spaces; collapse whitespace; drop a leading `the`. The same normaliser powers the Add-Exercise
   search field (§3.7.3) — one implementation, one test.
2. **Exact normalised match** against `builtins + existing customs`. Ties broken by preferring a
   candidate whose `equipment` equals the hint.
3. **Alias table** — `ExerciseCatalog.HEVY_ALIASES` (below), a hand-written map covering common
   Hevy names step 2 cannot reach.
4. **Fall back to creating a custom `Exercise`**, once per distinct name per import: `name` =
   Hevy's name verbatim (so the user recognises it), `equipment` = the hint or `OTHER`,
   `primaryMuscle` = `OTHER`, `source = "IMPORTED_HEVY"`, and `trackingMode` **inferred from which
   columns are populated across all that name's rows in the file** (weight+reps → `WEIGHT_REPS`;
   reps only → `REPS_ONLY`; duration only → `DURATION`; distance present → `DISTANCE_DURATION`).

**No fuzzy / edit-distance matching, deliberately.** A threshold loose enough to catch `"Bicep
Curl (Dumbbell)"` → `"Dumbbell Curl"` is also loose enough to catch `"Incline Bench Press"` →
`"Decline Bench Press"` or `"Front Squat"` → `"Back Squat"`. A wrong match is worse than no
match: it silently merges two different movements' histories, corrupting PREVIOUS and inventing or
destroying personal records with nothing visible to the user. An extra custom exercise is visible,
harmless, renameable and mergeable by hand. `HevyExerciseMatcher` may contain a normaliser and the
alias table, and nothing else — no edit distance, no token overlap, no "closest match" fallback.

**Recovery is a normal edit, not a special tool.** An auto-created exercise is an ordinary custom
`Exercise` row: the user can rename it, set its muscle and equipment, or archive it from
`ExerciseFormScreen`. The "Imported" label in the picker (§3.7.3) is how they find them.

**The alias table, checked against the user's real 4,734-row export.** Of its 62 distinct
`exercise_title` values, 54 resolve to a real RepDB-backed catalog id — either an exact
normalised match (needing no alias entry) or one of the aliases below. **8 have no faithful RepDB
equivalent in the free-tier catalog and fall through to step 4** — an auto-created custom
exercise, exactly the designed, correct behaviour for a name the matcher cannot confidently
resolve, not a gap to patch:

| Hevy `exercise_title` | Resolves to | Hevy `exercise_title` | Resolves to |
|---|---|---|---|
| Bicep Curl (Cable) | `builtin:cable-curl` | Preacher Curl (Barbell) | `builtin:barbell-preacher-curl` |
| Bicep Curl (Dumbbell) | `builtin:bicep-curl` | Preacher Curl (Machine) | `builtin:machine-preacher-curl` |
| Cable Crunch | `builtin:cable-crunch` | Pull Up | `builtin:pull-up` |
| Chest Fly (Machine) | `builtin:machine-chest-fly` | Rear Delt Reverse Fly (Machine) | `builtin:rear-delt-fly` *(best-effort — no machine variant in the free tier; dumbbell)* |
| Chest Press (Machine) | `builtin:chest-press-machine` | Romanian Deadlift (Dumbbell) | `builtin:dumbbell-romanian-deadlift` |
| Crunch (Machine) | `builtin:machine-seated-crunch` | Rowing Machine | `builtin:rowing-machine` |
| Dead Hang | `builtin:dead-hang` | Seated Cable Row - V Grip (Cable) | `builtin:seated-cable-row` |
| Decline Crunch | `builtin:decline-crunch` | Seated Calf Raise | `builtin:seated-calf-raise` |
| Decline Crunch (Weighted) | `builtin:decline-crunch` *(best-effort — weighted variant not separately modelled)* | Seated Chest Flys (Cable) | `builtin:cable-fly` *(best-effort)* |
| Hanging Leg Raise | `builtin:hanging-leg-raise` | Seated Incline Curl (Dumbbell) | `builtin:incline-db-curl` |
| Hanging Leg Raises (Weighted) | `builtin:hanging-leg-raise` *(best-effort)* | Seated Lateral Raise (Dumbbell) | `builtin:seated-dumbbell-lateral-raise` |
| Hip Thrust (Barbell) | `builtin:hip-thrust` | Seated Leg Curl (Machine) | `builtin:seated-leg-curl` |
| Incline Bench Press (Dumbbell) | `builtin:incline-db-press` | Seated Palms Up Wrist Curl | `builtin:wrist-curl` |
| Incline Bench Press (Smith Machine) | `builtin:smith-machine-incline-bench-press` | Seated Shoulder Press (Machine) | `builtin:machine-shoulder-press` |
| Iso-Lateral Chest Press (Machine) | `builtin:chest-press-machine` | Shrug (Dumbbell) | `builtin:db-shrug` |
| Lat Pulldown (Cable) | `builtin:lat-pulldown` | Shrug (Smith Machine) | `builtin:smith-machine-shrug` |
| Lat Pulldown (Machine) | `builtin:lat-pulldown` *(best-effort — no dedicated machine variant in the free tier)* | Single Arm Lateral Raise (Cable) | `builtin:cable-lateral-raise` |
| Lateral Raise (Cable) | `builtin:cable-lateral-raise` | Squat (Bodyweight) | `builtin:bodyweight-squat` |
| Lateral Raise (Dumbbell) | `builtin:lateral-raise` | Squat (Dumbbell) | `builtin:db-squat` |
| Leg Extension (Machine) | `builtin:leg-extension` | Squat (Smith Machine) | `builtin:smith-machine-squat` |
| Leg Press (Machine) | `builtin:leg-press` | Standing Calf Raise | `builtin:standing-calf-raise` |
| Lying Leg Curl (Machine) | `builtin:leg-curl` | Standing Calf Raise (Smith) | `builtin:standing-calf-raise` |
| Lying Leg Raise | `builtin:lying-leg-raise` | Treadmill | `builtin:treadmill-running` |
| Lying Leg Raises (Weighted) | `builtin:lying-leg-raise` *(best-effort)* | Tricep Supported Bicep Curls (Dumbbell) | `builtin:incline-db-curl` *(best-effort)* |
| | | Triceps Extension (Cable) | `builtin:tricep-pushdown` *(best-effort)* |
| | | Triceps Extension (Dumbbell) | `builtin:dumbbell-tricep-extension` |
| | | Triceps Kickback (Cable) | `builtin:cable-tricep-kickback` |
| | | Triceps Pushdown | `builtin:tricep-pushdown` |
| | | Triceps Rope Pushdown | `builtin:tricep-pushdown` *(best-effort)* |
| | | Wrist Curl (Cable) | `builtin:cable-wrist-curl` |

**Falls through to an auto-created custom exercise (no RepDB free-tier row matches both the
movement and the equipment):** Deadlift (Smith Machine), Hiking, Iso-Lateral Row (Machine),
Reverse Wrist Curl (Cable), Seated Row (Machine), Seated Wrist Extension (Barbell), Shrug (Cable),
Single Arm Cable Row. Each becomes an ordinary custom exercise named exactly as Hevy wrote it,
badged "Imported", editable like any other — the correct outcome per the no-fuzzy-matching rule
above, not an omission.

`HevyExerciseMatcherTest` is parametrised over all 62 names from the real export and asserts every
one resolves to *some* real catalog id (existing, aliased, or newly auto-created) — never an
exception, never a silently dropped row.

#### 3.9.5 Duplicate safety

Importing the same file twice, or two overlapping exports, must not double the user's history, nor
collide with sessions logged by hand.

**The dedupe key is `(startedAt, endedAt)` as exact epoch millis.** `title` is useless as a key
(§3.9.0). Set composition is useless too — the user may have edited an imported session
afterwards, and a composition hash would then fail to recognise it and re-import a second copy.
Start and end together are effectively unique for a real human. **No fuzzy time window** — a ±60s
tolerance would let a genuinely different session be eaten, and a missed duplicate is recoverable
(delete it) while a wrongly-skipped session is silently absent forever.

The rule applies against every existing `workout_sessions` row regardless of `source`, and within
the file — group rows into sessions first, then dedupe the groups.

**On a detected duplicate: skip the whole session, count it, and say so.** Never merge, never
partially insert, never overwrite. `HevyDedupeTest` covers: re-importing the identical file yields
0 new sessions; a file with one new session and three old ones yields 1; a hand-logged session at
the same timestamps blocks its Hevy twin.

The whole import runs inside one `database.withTransaction { }` — either all of it lands or none
of it does, matching `importAllData`'s existing posture.

#### 3.9.6 `source`, and whether imported rows are second-class

**`WorkoutSession.source = "IMPORTED_HEVY"`, not a generic `"IMPORTED"`** — a generic marker
cannot tell apart a future second importer.

**Imported sessions are fully editable, first-class rows.** Once they land they are
indistinguishable from hand-logged sessions in every way that matters: editable, deletable,
counted in Volume and Sets, and they participate in PREVIOUS and personal-record detection. The
`source` string is bookkeeping, not a permission.

**Why this is not inconsistent with §6.4's read-only Health-Connect sessions.** A Health Connect
session is a live view of an authoritative store Daybook does not own — re-read on every pull, so
read-only is the only honest state. A Hevy CSV is a one-shot transfer of ownership: nothing
re-reads the file, there is no authority left to disagree with. The dividing line is "does
something else keep updating this?", not "did the user type it?"

#### 3.9.7 Sync interaction — the sharp edge

An import writes sessions into past months. On a signed-in account, past months are partitioned,
hash-tracked and frequently evicted from the local DB: the device may hold no local rows at all
for `2026-07`, while the cloud holds a full month doc of habits and intake. If the import writes a
July workout into that empty local month and the normal push path then runs, `MonthPartitioner`
sees a changed `2026-07` and pushes what the device has — a month doc containing the imported
workout and nothing else, overwriting the cloud's habits and intake for July.

**Mitigation: hydrate before importing, using the path that already exists.** The date-range
export solves the identical problem today. The Hevy import does the same thing, in this order:

1. Parse and group the file first (a parse failure must cost no network and no writes).
2. Compute the set of `"yyyy-MM"` months the file touches.
3. `beginRangeExport()` → hydrate those months → on any failure, abort with nothing written and a
   message in the existing voice.
4. Import inside one transaction.
5. `endRangeExport()` in a `finally`.

Signed-out users skip steps 3 and 5 — there is nothing to hydrate — and the import is purely
local. A test asserts that importing into a month with existing habit data leaves that habit data
intact.

#### 3.9.8 Migration: none of its own

The importer writes into `exercises`, `workout_sessions`, `workout_exercises` and `workout_sets` —
four of the six tables Round A already creates in `MIGRATION_21_22` (§3.5), using the same
columns. It does not write to the two routine tables — routine import is out of scope (§3.1). No
extra table, no extra column, `AppDatabase.version` stays at 22 for the whole of Round A.

#### 3.9.9 The summary the user sees, and every visible outcome

Same voice and place as the existing import messages.

**Can a partial failure happen? No — all-or-nothing per file.** The whole import runs inside one
transaction, and hydration aborts with nothing written if any month can't be reached. So the only
outcomes are: nothing was written, or everything the importer decided to import was written. There
is no per-row failure report screen. What *does* exist, inside a successful import, are two kinds
of deliberate, successful skipping: duplicate sessions and rows with an unparseable date — both
counted in the success summary. **If every session is skipped, or the file parses to zero
sessions, that is not a silent success** (see S3 below) — a "success" message reporting nothing
imported, with no explanation, is exactly the silent failure C9 forbids.

**Pre-parse validation — five gates, in this order, before any parsing or write:**

| # | Gate | Check | Message |
|---|---|---|---|
| V1 | Size | file bytes > 10 MB | F1 |
| V2 | Non-empty | trimmed text empty, or fewer than 2 non-blank lines | F2 |
| V3 | Parses as CSV | `CsvReader` throws, or the header row yields zero columns | F3 |
| V4 | Is a Hevy export | the required header set is present | F4 |
| V5 | Has ≥1 usable row | at least one data row survives with a parseable `start_time` | F5 |

**Required header set — exactly these ten**, compared case-insensitively after trimming and
stripping a leading UTF-8 BOM: `title, start_time, end_time, exercise_title, set_index, set_type,
weight_kg, reps, distance_km, duration_seconds`. Deliberately *not* required, though mapped when
present: `description`, `superset_id`, `exercise_notes`, `rpe` — rejecting an otherwise-importable
file over a missing `rpe` column would be a self-inflicted failure. Extra/unknown columns are
ignored, never an error.

`data/workout/HevyCsvValidator.kt` → `fun validate(headers: List<String>): ValidationResult`,
`sealed interface ValidationResult { object Ok; data class MissingColumns(val names: List<String>) }`.

**Messages render in the existing fixed-height inline result slot**, never a toast — the user
needs to read and act on them. `SettingsScreen`'s success check is extended: `msg.startsWith("Exported
") || msg.startsWith("Import successful") || msg.startsWith("Imported ")`. Every failure string
begins with `Couldn't ` so it can never accidentally match a success prefix.

**Failure (nothing was imported), `DaybookColors.Danger`:**

| ID | When | Exact text |
|---|---|---|
| F1 | file over 10 MB | `Couldn't import: that file is too large (the limit is 10 MB).` |
| F2 | empty file / header only | `Couldn't import: that file is empty.` |
| F3 | not parseable as CSV | `Couldn't import: that file isn't a CSV. Export your data from Hevy as CSV and try again.` |
| F4 | required columns missing | `Couldn't import: that doesn't look like a Hevy export. It's missing: <names>.` — `<names>` joined `", "`, verbatim as Hevy spells them |
| F5 | no readable rows | `Couldn't import: none of the rows in that file could be read.` |
| F6 | a touched month couldn't be fetched | `Couldn't fetch <month> from your account. Check your connection and try again — nothing was imported.` — `<month>` as `MMMM yyyy` |
| F7 | file couldn't be read (`IOException`) | `Couldn't read the file. Try picking it again.` — reuse `friendlyImportError`'s wording verbatim |
| F8 | the transaction threw (`SQLiteException`) | `Couldn't save the imported workouts — try again, or restart the app if it keeps happening. Nothing was imported.` |
| F9 | anything else | `Couldn't import: something went wrong and nothing was changed.` |

In every F-case the raw throwable goes to `Log.e` and Crashlytics.

**Success**, `DaybookColors.Success`, built by a pure `fun summarise(result: HevyImportResult):
String` in `data/workout/HevyImporter.kt` from `data class HevyImportResult(val sessions: Int, val
sets: Int, val newExercises: Int, val skippedDuplicates: Int, val skippedRows: Int)`:

| ID | When | Exact text |
|---|---|---|
| S1 | `sessions > 0`, nothing skipped | `Imported 4 workouts and 33 sets.` |
| S1a | `sessions > 0`, `newExercises > 0` | `Imported 4 workouts, 33 sets and 9 new exercises.` |
| S2 | `sessions > 0`, duplicates skipped | append ` Skipped 2 already in Daybook.` |
| S2a | `sessions > 0`, rows skipped | append ` Ignored 3 rows it couldn't read.` |
| S3 | `sessions == 0` and `skippedDuplicates > 0` | `Nothing new to import — all 4 workouts in that file are already in Daybook.` — `DaybookColors.TextMuted`, neither Success nor Danger. Re-importing the same file is the single most likely thing the user will do, and a bare "Imported 0 workouts" would read as failure. |
| S4 | `sessions == 0` and `skippedDuplicates == 0` | `Couldn't import: no workouts were found in that file.` — Danger; V5 passed but grouping produced nothing. |

Composition rules: correct singular/plural throughout; drop any clause whose count is 0 (never
"Skipped 0"); the counts clause uses an Oxford-free list; skipped clauses are separate sentences
appended after a single space, duplicates-then-rows.

**In-progress state:** reuse the existing `_isImporting` `StateFlow` and button-label swap — label
reads **`Importing…`** while it runs, disabled. During the hydration step the label reads
**`Fetching your history…`**.

**History's empty-state entry point** shares every string above. `EmptyState` copy: title **`No
workouts yet`**, body **`Start a workout to log your first session, or bring your history over
from Hevy.`**, primary action **`Start an empty workout`**, secondary `GhostButton` **`Import from
Hevy`**.

#### 3.9.10 New files

| File | Role |
|---|---|
| `data/workout/CsvReader.kt` | RFC4180-ish reader. Pure. |
| `data/workout/HevyCsvValidator.kt` | `validate(headers): ValidationResult` — the V4 header gate. Pure. |
| `data/workout/HevyCsvParser.kt` | rows → `List<ParsedSession>`; date parsing, column mapping, grouping. Pure. |
| `data/workout/HevyExerciseMatcher.kt` | name → `exerciseId` or "create this custom row". Pure. |
| `data/workout/HevyImporter.kt` | the only impure piece: dedupe probe, hydration guard, one transaction, the summary string. Called from `WorkoutRepository`. |
| tests | `CsvReaderTest`, `HevyCsvValidatorTest`, `HevyDateParseTest`, `HevyCsvParserTest` (against both real samples), `HevyExerciseMatcherTest` (parametrised over all 62 names from the full export), `HevyDedupeTest`, `HevyImportSummaryTest` (exact-string, S1/S1a/S2/S2a/S3/S4) |

Five of the six files are pure and Room-free — the risky part is squeezed down to `HevyImporter`.

### 3.10 Performance discipline

Non-architectural engineering discipline, adopted as **Round A requirements**, not suggestions,
each checkable:

**P1 — A completed set is written to Room immediately, in its own transaction.** The live session
must never accumulate sets in memory and persist them at "Finish". Tapping the green check calls
`WorkoutRepository.toggleSetComplete(id)` → a Room write → the Flow re-emits → the row turns
green. A crash, process death or battery pull after set 18 must leave sets 1–18 in the database.
This also supplies "a workout must survive rotation, backgrounding, process death" for free: the
resume banner reads `observeActiveSession()` from Room, so there is no in-memory session state to
lose. **Corollary:** `WorkoutSessionViewModel` holds no `List<WorkoutSet>` of its own.

**P2 — Timer state is isolated; the session screen does not recompose on a tick.** Both tickers
emit once a second. Reading the elapsed time at the top of the screen's composable would recompose
every exercise card and every set row 60 times a minute while the user is trying to type into a
text field. The ticking value is read only inside the smallest composable that displays it — the
header's duration `Text` and the rest-timer row's `Text` — via a deferred read, never hoisted into
the parent's composition scope. The same rule covers the header title's "swap to elapsed time on
scroll" — its `derivedStateOf` on `LazyListState` is read inside the header, not at screen level.

**P3 — No analytics are recomputed on set completion.** Only three derived values are computed
during a live session: `sessionStats(sets)` (a fold over this session's sets only, bounded);
`previousSetsForExercise` (`LIMIT 50`, read once when the exercise block opens, cached for that
block's life); `bestSetForExercise` (`LIMIT 1`, same). **Forbidden:** any query over the user's
whole workout history on the live-session screen — the per-exercise history list is a separate
sheet, opened deliberately.

**P4 — Interaction budgets** (targets, not guarantees): tap "complete set" → visible green must be
absolute (no network, no main-thread disk read); Add-Exercise search under ~100 ms per keystroke,
achievable because the catalog is an in-memory list (§3.3) filtered, not queried; opening the
Workout screen must not read the whole history (`observeRecentSessions(limit)` is already paged);
**app cold start must not regress at all** — Beast Mode is a stacked route not composed until
entered, unlike a fourth pager page kept warm by `beyondViewportPageCount`. Nothing in §3 runs at
application start; every Beast Mode destination's flows are `WhileSubscribed(5_000)`. The
long-press itself costs nothing at rest — `combinedClickable` adds a gesture detector to one nav
item, and the press-feedback ramp only runs while a finger is down, read inside the Today icon's
own composable, never hoisted into `FloatingPillNav`'s scope.

**P5 — List and state discipline.** `LazyColumn` with stable `key = { it.id }` for the session
list, the set rows, the exercise picker and the history sheet. UI models are immutable `data
class`es of primitives — no `AppDatabase` entity, no Flow, no lambda captured into a list item's
state. All flows `flowOn(Default)` + `stateIn(WhileSubscribed(5_000))`. Any fold over history off
the main thread with `withContext(Dispatchers.Default)`.

**P6 — Baseline Profiles: deferred.** Generating one needs the `androidx.benchmark`
macrobenchmark plugin, a second Gradle module, and an AGP/Gradle version newer than the frozen
8.3.2 / 8.6 (§5.1). Adding a module to a deliberately single-module project, in the same round
that adds a feature, is the scope creep this plan avoids elsewhere too. Belongs with a future
toolchain round.

**P7 — No new background work.** Round A adds no worker, no alarm, no service, no notification
channel and no permission. The only new `WorkManager` interaction is the existing one
(`SyncFlushWorker` firing because `DATA_TABLES` gained six tables).

**P8 — GPS: not now, and not by accident.** Round A tracks distance as a typed number the user
enters (`distanceMeters`), never a measured route. No location permission, no
`FusedLocationProvider`, no `ACCESS_FINE_LOCATION` in the manifest.

---
## 4. Round A sync integration (the sharp edges)

### 4.1 `CloudSyncRepository.DATA_TABLES`

Gains six names: `"exercises"`, `"workout_sessions"`, `"workout_exercises"`, `"workout_sets"`,
`"workout_routines"`, `"workout_routine_exercises"`. **`DataTablesSyncTest` is one-directional** —
it catches a table present in the DB and absent from `DATA_TABLES`'s *shape*, not a table someone
simply forgot to add to the literal list. This is a manual step, plus a manual "log a set,
background the app, confirm a push" check (R12).

### 4.2 Wire model additions (`data/backup/BackupModel.kt`)

Two additions, both optional and default-absent:

```kotlin
// in Definitions — the user's CUSTOM exercises only, never the built-in catalog
@EncodeDefault(EncodeDefault.Mode.NEVER)
val customExercises: List<ExerciseDef> = emptyList()

@Serializable
data class ExerciseDef(
    val id: String, val name: String,
    val primaryMuscle: String, val equipment: String,
    val trackingMode: String, val createdAt: String,      // ISO-8601 UTC
    val archived: Boolean = false,
    val source: String = "USER",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val notes: String? = null
)
```

```kotlin
// in Definitions. Routines are DEFINITIONS, like habits and custom exercises: they carry no
// local_date, so they do NOT belong in DayEntry and are NOT month-partitioned.
@EncodeDefault(EncodeDefault.Mode.NEVER)
val routines: List<RoutineDef> = emptyList()

@Serializable
data class RoutineDef(
    val id: String, val name: String,
    val orderIndex: Int,
    val createdAt: String, val updatedAt: String,        // ISO-8601 UTC
    val archived: Boolean = false,
    val source: String = "USER",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val notes: String? = null,
    // Nested, not a second top-level list keyed by routineId — a child list beside its parent
    // can be imported half-way, and a routine with orphaned rows renders wrong.
    val exercises: List<RoutineExerciseDef> = emptyList()
)

@Serializable
data class RoutineExerciseDef(
    val id: String, val exerciseId: String,
    val orderIndex: Int,
    // Every target NULLABLE, omitted by explicitNulls = false.
    val targetSets: Int? = null, val targetReps: Int? = null, val targetWeightKg: Float? = null,
    val targetDurationSeconds: Int? = null, val targetDistanceMeters: Float? = null,
    val restSeconds: Int? = null,
    val notes: String? = null
)
```

**`RoutineDef.orderIndex`, `archived` and `source`** are non-null with a non-null default — the
one shape `explicitNulls = false` does not rescue. They are safe here for the same reason
`setType`/`source` below are: they live inside `routines`, itself `@EncodeDefault(NEVER)`-empty
for a user with no routines, so nothing is emitted at all. `orderIndex` is `Int` rather than
`Int?` because a routine without a position is not a meaningful state.

**`WorkoutRoutineExercise.routineId` and `.createdAt` are deliberately not on the wire** —
`routineId` is re-derived from the enclosing `RoutineDef` on import, and `createdAt` on a child
row carries no information the parent's `createdAt` does not.

```kotlin
// in DayEntry — that local date's workout sessions
@EncodeDefault(EncodeDefault.Mode.NEVER)
val workouts: List<WorkoutLog> = emptyList()

@Serializable
data class WorkoutLog(
    val id: String,
    val startedAt: String,                 // ISO-8601 UTC
    val endedAt: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val status: String = "COMPLETED",
    val source: String = "MANUAL",
    val routineId: String? = null,         // NULLABLE, so explicitNulls = false omits it entirely
                                           // for every session that did not come from a routine.
                                           // A dangling id (the routine was deleted) round-trips
                                           // untouched and is simply not resolved by the UI.
    // Sets nest inside their exercise block rather than sitting flat on the session, mirroring
    // the workout_exercises table. Nesting is what lets a block's note, rest timer and superset
    // id round-trip without being repeated on every set.
    val exercises: List<WorkoutExerciseLog> = emptyList()
)

@Serializable
data class WorkoutExerciseLog(
    val id: String, val exerciseId: String,
    val orderIndex: Int,
    val notes: String? = null,
    val supersetId: String? = null,
    val restSeconds: Int? = null,
    val sets: List<WorkoutSetLog> = emptyList()
)

@Serializable
data class WorkoutSetLog(
    val id: String,
    val setNumber: Int,
    val reps: Int? = null, val weightKg: Float? = null,
    val durationSeconds: Int? = null, val distanceMeters: Float? = null,
    val rpe: Int? = null,
    val setType: String = "NORMAL",
    val notes: String? = null, val completedAt: String? = null
)
```

**`setType` and `source` in the wire model** are non-null fields with a non-null default — the one
shape `explicitNulls = false` does not rescue; a defaulted `"NORMAL"` would be emitted into the
canonical bytes. They are safe here only because they live inside `workouts`/`exercises`,
themselves `@EncodeDefault(NEVER)` empty lists for a user with no gym data. **Do not promote either
field up to `DayEntry` or `HabitDef` level** without adding its own `@EncodeDefault(NEVER)` and
its own byte-identity test.

`WorkoutSet.sessionId` and `.exerciseId` are **not** on the wire — both re-derivable on import from
the enclosing `WorkoutLog` / `WorkoutExerciseLog`; a denormalised value on the wire is how the two
copies eventually disagree.

**The hash-neutrality guarantee, and why it protects existing cloud data.** `ContentHash` and
`MonthPartitioner` both serialise with `encodeDefaults = true` **and** `explicitNulls = false`: a
`null` field is omitted automatically, and a non-null default (an empty list, `false`, `0`) would
otherwise be emitted — which `@EncodeDefault(NEVER)` suppresses. So `customExercises =
emptyList()`, `routines = emptyList()` and `workouts = emptyList()` produce **byte-identical**
`definitionsHash` and per-month `contentHash` values to today. A user who never opens Workout mode
sees zero re-push, zero cloud writes, zero risk of the D2 conflict dialog. The same technique
already backs `HabitDef.streakLongest`, `HabitDef.journalQuestions`, `HabitDef.promptMessage`,
`IntakeLog.qaJson`, `IntakeLog.outsideFood`. Copy those, including their comments.

**Mandatory new tests** (mirroring `StreakDefHashTest` / `JournalV2HashTest` /
`HabitJournalHashTest`): `WorkoutDefHashTest` — byte-identical with and without `customExercises`
present-but-empty. `RoutineDefHashTest` — the same for `routines`, as its own test so a failure
names which field broke. `WorkoutDayHashTest` — byte-identical with and without `workouts`
present-but-empty, plus a case for a `WorkoutLog` with `routineId = null` being byte-identical to
one without the field. Extend `BackupModelTest` with a round-trip over a populated workout day and
a populated routine with nested exercises and a mix of null and non-null targets.

### 4.3 Mixed-version hazard

A device on an older build decodes a month blob containing `workouts` via `ignoreUnknownKeys =
true` — it will not crash, but it also will not know about the field. If that old device then
edits anything in that month, it re-exports the month **without** `workouts` and pushes it —
silently dropping the workout data from the cloud for that month, and from the new device on its
next pull.

**Mitigation: update every device the user signs in on, in the same sitting.** This app is
sideloaded via Firebase App Distribution, and `versionCode` is bumped so the in-app update check
surfaces the new build. Not a code problem — a rollout instruction.

### 4.4 `ExportImportRepository` — six call sites

Every one of these must be extended, or data will silently disappear:

1. **`exportBackup()`** — read custom exercises into `Definitions.customExercises`; read routines
   and their exercise rows into `Definitions.routines`; read sessions, their exercise blocks and
   their sets, group by `local_date`, attach as `DayEntry.workouts`. Both the block fetch and the
   set fetch go through the chunked `getExercisesForSessions` / `getSetsForSessions` (900-var
   cap); the routine-row fetch is chunked the same way.
2. **`exportRange(start, end)`** — no change needed; it clips `full.days`, so `workouts` rides
   along automatically. Confirm with a test extension to `ExportRangeTest`.
3. **`importAllData(json)`** — full-replace path: wipe `exercises` / `workout_sessions` /
   `workout_exercises` / `workout_sets` / `workout_routines` / `workout_routine_exercises` inside
   the existing `withTransaction`, then insert from the file, re-deriving each set's `sessionId` /
   `exerciseId` from its enclosing block and each routine row's `routineId` from its enclosing
   `RoutineDef`.
4. **`importRange(backup)`** / **`importMonth(monthKey, days)`** — the non-destructive merge
   paths. Follow what these already do for occurrences: delete only the rows whose `local_date`
   falls inside the incoming range, then insert the incoming ones. Do not wipe globally.
   `RangeImportNonDestructiveTest` and `MonthMergeTest` are the existing guards; extend both.
4b. **Routines and range/month import — explicitly no change.** Routines carry no `local_date`, so
   a month-scoped merge must not touch `workout_routines` or `workout_routine_exercises` at all.
   Stated because "the workout tables" invites treating all six the same, and deleting routines
   because a July month doc arrived would be a data-loss bug with no error message.
5. **`applyRemoteDefinitions(defs)`** — upsert incoming custom exercises and routines; delete
   local exercise rows and local routine rows whose ids are absent from the remote set, via
   `allIds()` / `deleteByIds(chunked)`. A routine's child rows are replaced wholesale with its
   parent (delete by `routine_id`, then insert) — the same delete-then-reinsert rule
   `updateRoutine` uses, since a diff over `orderIndex` is more code and more ways to end up
   inconsistent. Extend `DefinitionsUpsertTest` with a routine case.
6. **`evictMonth(monthKey)` — the easiest one to get wrong.** Eviction drops an old month's rows
   locally once its hash matches the cloud. If it deletes occurrences but leaves that month's
   `workout_sessions` behind, the next `exportBackup()` produces a month bucket the cloud doesn't
   have → an endless re-push loop, or a month doc that overwrites cloud history with a partial
   view. `evictMonth` must delete that month's `workout_sets`, then its `workout_exercises`, then
   its `workout_sessions`, all by session id, chunked, in the same transaction. **And the mistake
   in the opposite direction, just as bad: `evictMonth` must NOT touch `workout_routines` or
   `workout_routine_exercises`.** Routines are definitions, not month data — evicting them would
   delete the user's templates every time an old month was tidied up, with no way to notice until
   they opened Beast Mode. Six new tables, four of which evict and two of which never do. Extend
   the existing "evicting a month with workouts leaves `changedMonths` empty" test with an
   assertion that the routine count is unchanged.

**A seventh call site, outside `ExportImportRepository`:** `data/workout/HevyImporter.kt` (§3.9)
writes into three of these tables directly. It must obey §3.9.7's hydrate-or-abort rule before
writing a single row into a month the device may not hold — the same class of mistake as item 6,
approached from the other direction.

### 4.5 What does NOT change

`formatVersion` stays 3. No Firestore field names change. No `firestore.rules` change (the
parent-doc owner match already permits arbitrary fields; months are a subcollection under the
same match). No `firestore.indexes.json` change. No change to the push debounce, the listener
model, the echo guard, `SyncStateStore`, or the D2 conflict flow.

### 4.6 Blob-size sanity

`CloudSyncRepository`'s own note records ≈120 KB raw / ~15 KB gzipped per month doc today, against
Firestore's 1 MiB hard cap. A heavy lifter logging 5 sessions/week × 25 sets adds roughly 500 set
records/month ≈ 60 KB raw / ~6 KB gzipped. Comfortable. The existing `SOFT`/`HARD` warn thresholds
and the `oversizedMonths` skip path need no tuning.

Routines land in the **parent** doc's `definitions` blob, not a month doc: a name plus ~8 exercise
rows of 6 small numbers is roughly 400 bytes raw each. Even a user with 50 routines adds ~20 KB raw
/ ~3 KB gzipped to a `definitions` blob that already carries habits, intake tasks, categories and
prompts. No threshold moves. The figure worth watching is `customExercises` after a large Hevy
import, and §3.9.4's matcher is specifically designed to keep that list small.

## 4.7 Round A phase list

| Phase | Work | Gate |
|---|---|---|
| A1 | `data/model/WorkoutModel.kt`, 6 entities; `AppDatabase` v22 + 3 DAO accessors (`exerciseDao`, `workoutDao`, `routineDao`); `ExerciseDao`, `WorkoutDao`, `RoutineDao`; `MIGRATION_21_22` (six tables, four indices, the nullable `workout_sessions.routine_id`); register in `DatabaseModule`; `AppSettings` 5 new columns + `AppSettingsDao` setters + `AppSettingsRepository` mirrors | `assembleDebug` green, `22.json` generated |
| A2 | `data/workout/ExerciseTaxonomy.kt` (`MuscleGroup`, `Equipment`, label maps); `data/workout/ExerciseCatalog.kt` — the RepDB asset reader, the derivation functions (§3.3.2), `REPDB_MUSCLE_MAP` / `REPDB_EQUIPMENT_MAP`, the stretching-category filter; `HEVY_ALIASES` (§3.9.4); the bundled assets under `app/src/main/assets/exercises/`; `ExerciseCatalogTest` (ids unique, ids stable, every muscle group reachable, every alias value resolves to a real catalog id, label maps total, derivation functions correct per branch) | unit tests green, APK builds with the new assets, release size confirmed (~25 MB added) |
| A3 | `data/WorkoutRepository.kt` + DI provider + `ExerciseNormaliseTest`; the derived-value layer: `previousSetsForExercise`, `bestSetForExercise`, `lastRestSecondsForExercise`, and the pure `previousBySetNumber` / `isPersonalRecord` / `sessionStats` / `columnsFor` functions, each with a unit test; the routine layer: `observeRoutineSummaries`, `createRoutine` / `updateRoutine` / `duplicateRoutine` / `deleteRoutine`, `startEmptySession`, `startSessionFromRoutine`, and the pure `uniqueRoutineName` / `targetSummary` functions, with `RoutineNameNormaliseTest`, `RoutineTargetSummaryTest`, `StartFromRoutineTest` | unit tests green |
| A4 | **Sync**: `BackupModel` additions (`WorkoutExerciseLog`, `setType`, `RoutineDef` + `RoutineExerciseDef` + `WorkoutLog.routineId`), `DATA_TABLES` (six names — §4.1's caveat: no test catches a forgotten one), all six `ExportImportRepository` call sites plus §4.4's 4b and item 6's routine clause, `WorkoutDefHashTest`, `RoutineDefHashTest`, `WorkoutDayHashTest`, extend `MonthMergeTest` / `RangeImportNonDestructiveTest` / `DefinitionsUpsertTest` / `BackupModelTest` | unit tests green — do not proceed to UI until this is green |
| A5 | **The entry point, the exit, and the mode shell.** `Components.combinedClickableImpl` (§3.6.1 a); `FloatingPillNav` + `DaybookScaffold` + `DaybookScaffoldNav` gain `onLongSelect` / `longPressRoute` / `longPressLabel` / `hintDotRoutes` / the coach-mark slot; the nav-icon press ramp + haptic + `LocalReduceMotion` branch with `longPressRampMillis` extracted and `NavLongPressRampTest` written; `ui/components/CoachMark.kt` + the `workout_hint_state` 0→1→2 wiring; the dot on the Today icon; `ic_workout.xml` + `NavIconInflateTest`; `ui/workout/WorkoutRoutes.kt` + `WorkoutRoutesTest`; `MainActivity`'s `goWorkout` + `exitBeastMode` + `goBeastRoute` + the ten workout `composable(...)` destinations; Beast Mode's own three-item pill nav and the `showNav` / `navItems` / `onSelectRoute` switch; the Beast Mode accent provider around the `DaybookScaffold` call, not per-route. **Explicitly NOT in this phase, and not anywhere in this round:** `NavConfig`, `NavConfigTest`, `nav_tabs`, `NavigationSettingsScreen`, the pager `when`, `beyondViewportPageCount`, any `BackHandler`, any header `×`. | App launches with three tabs, unchanged. Tap Today → Today. Hold Today → the icon visibly ramps, a tick fires, Beast Mode opens with its own three-item nav bar in Coral. Tapping between Routines / History / Exercises works; system back from History or Exercises returns to Routines, not out of the mode. Hold the Routines icon → the same ramp in Coral, the same tick, and you land on Today with Daybook's nav back. System back from the landing also leaves. Coach-mark appears once and never again after "Got it". With TalkBack on, focusing Today announces "Start a workout" and focusing Routines announces "Leave Beast Mode". With *Touch and hold delay* set to Long, both ramps still finish exactly when the gesture fires. Every workout screen, including the pill nav, is Coral; `main` is unchanged. |
| A6a | `ui/workout/` part 1: `WorkoutHomeScreen` + `WorkoutHomeViewModel` — the routines landing, its header gear, the `Start an empty workout` button, the routine cards, the already-running dialog; `WorkoutHistoryScreen` + VM; `WorkoutDetailScreen`; `AddExerciseScreen` in both `PICK` and `BROWSE` modes with the `SavedStateHandle` result contract, RepDB thumbnails and image-backed filter sheets, `ExerciseFormScreen` (two pickers), `ExerciseHistorySheet` | manual pass: can browse, filter, search and create exercises; images render for every RepDB-sourced row; the landing header's gear opens Beast Mode settings; the Exercises nav destination shows the pill nav and the picker does not |
| A6b | `ui/workout/` part 2 — the live session: `WorkoutSessionScreen` + VM, per-block cards with RepDB thumbnails, notes, the pinned per-exercise note, mode-driven set table, PREVIOUS column, PR medal, the two tickers, rest-timer sheet, Finish / Discard. Re-verify the build-22 IME fix with a focused field low on the page. | manual pass |
| A6c | **Routines UI**: `RoutineEditScreen` + `RoutineEditViewModel`, `RoutineTargetSheet`, reorder, `+ Add exercise` via the picker result, `targetSummary` rendering, the `StickySaveBar` + C9 failure copy; the routine card overflow (Edit / Duplicate / Delete) + `ConfirmDeleteDialog` + the `Routine deleted` snack; `Start this routine again` on `WorkoutDetailScreen` | manual pass: build a routine with targets, start it, confirm the live session opens with the right blocks and pre-filled set rows, all unticked, Volume / Sets both reading 0 |
| A7 | **Settings.** Daybook's main Settings gets exactly one row: `Open Workout`, unconditional, behind no toggle, under `SectionHeader("Workout", subtitle = "Beast Mode has its own settings, inside it.")`; `AboutSettingsScreen` gains the RepDB attribution line. Everything else moves into `ui/workout/WorkoutSettingsScreen.kt` + `WorkoutSettingsViewModel.kt`, route `"workout_settings"`: weight unit, Beast Mode accent (default `CORAL`), default rest timer, the `Leave Beast Mode` row, `Show Beast Mode on Today` + the Today row itself; `WeightFormatTest` | unit tests green; manual: Settings → Workout → Open Workout reaches Beast Mode with the gesture never used; the gear reaches Beast Mode settings; changing the accent there re-tints the pill nav live; `Leave Beast Mode` exits to Today; the RepDB credit line is visible in About |
| A8 | **Hevy CSV import.** `CsvReader`, `HevyCsvValidator`, `HevyCsvParser`, `HevyExerciseMatcher`, `HevyImporter`; the Settings → Backup & data row + dialog + picker; the History screen's `EmptyState` button; the hydrate-or-abort guard; all of §3.9.9's validation gates, every F-string, every S-string, the `Importing…`/`Fetching your history…` labels, the `ok`-prefix extension; `CsvReaderTest`, `HevyCsvValidatorTest`, `HevyDateParseTest`, `HevyCsvParserTest` (against both real sample files), `HevyExerciseMatcherTest` (parametrised over all 62 names in the full export), `HevyDedupeTest`, `HevyImportSummaryTest` (exact-string, S1/S1a/S2/S2a/S3/S4) | unit tests green; manual: import `workout_data.csv`; import it again and confirm the S3 message appears, not "0 workouts"; import a Daybook JSON backup and confirm the F3/F4 message appears; import the full `workout_datasum).csv` end-to-end and confirm all 299 sessions land with zero unresolved rows |
| A9 | `MigrationTest.migrate21To22` (six tables + five `app_settings` columns + `workout_sessions.routine_id`, R18's not-null guard extended to the five `workout_routine_exercises` target columns, no `nav_tabs` assertion); full `./gradlew test` + `assembleRelease`; signed APK named per the repo's convention (`Daybook-v0.6-workout-release.apk`) | all tests green |

---
# ROUND B — Health sync (Mi Band 10 and any Health-Connect-compatible device)

## 5. Platform choice: Health Connect, not Google Fit

### 5.0 The recommendation and its justification

**Target Health Connect (`androidx.health.connect:connect-client`) exclusively. No Google Fit
path, not even as a fallback.**

1. **The Google Fit APIs are dead or dying.** Developer sign-ups for the Fit Android APIs and the
   `fitness.googleapis.com` REST API closed 1 May 2024, and Google's own published position is
   that the Fit APIs are "only supported until the end of 2026".
2. **The consumer Google Fit app is gone** as the hub it used to be; Google's own migration guide
   points Android apps at Health Connect.
3. **Health Connect is the right architecture for this app anyway.** An on-device, OS-mediated
   data store. No OAuth, no Google Cloud project, no scope verification, no network round trip, no
   server — matches Daybook's offline-first posture (C6) in a way the Fit REST API never could.
4. **Mi Fitness writes to it.** Xiaomi's Mi Fitness companion app (how a Mi Band 10 reaches a
   phone at all — the band has no open API and no direct app access) has a Health Connect
   integration sharing Steps, Sleep, Heart rate and Workouts. Enabling it is a user action inside
   Mi Fitness → Health Connect. Same story for Samsung Health, Fitbit, Garmin Connect,
   Amazfit/Zepp — which is what makes "any other manufacturer's band" work for free.

**Daybook never talks to the band.** It reads whatever Mi Fitness has already deposited in Health
Connect. If Mi Fitness has not synced (band out of range, app killed by battery optimisation),
Daybook sees nothing new — inherent to every Android app in this category.

### 5.1 The toolchain problem

Verified from the published artifacts' `aar-metadata.properties`:

| `connect-client` version | requires `compileSdk` | requires AGP |
|---|---|---|
| `1.1.0-alpha08` | **34** | any |
| `1.1.0-alpha09` … `1.1.0-alpha12` | 35 | any |
| `1.1.0-beta01` | 35 | 8.6.0 |
| `1.1.0-beta02` … `1.1.0-rc03` | 36 | 8.9.1 |
| `1.1.0` (stable, Oct 2025) | **36** | **8.9.1** |
| `1.2.0-alpha06` (latest) | 36 | 8.9.1 |

Daybook is on compileSdk 34, AGP 8.3.2, Gradle 8.6.

**Pin `androidx.health.connect:connect-client:1.1.0-alpha08`, change nothing about the toolchain.**
Zero risk to the existing build: compileSdk stays 34, AGP 8.3.2, Gradle 8.6, and every "last
version that builds against compileSdk 34" pin (`firebase-bom:33.1.2`, `credentials:1.3.0`,
`biometric:1.1.0`, `security-crypto:1.1.0-alpha06`) stays untouched. Transitive dependencies are
modest: `androidx.activity:1.2.0`, `androidx.annotation:1.8.1`, `androidx.core:core-ktx:1.12.0`,
`com.google.guava:guava:31.1-android` (+ `kotlinx-coroutines-guava:1.7.3`, already on coroutines
1.7.3). **Guava is the one to watch** — a large artifact under R8 shrinking; check for ProGuard
keeps and re-check the release APK size.

**Verified to contain everything the MVP needs**: `HealthConnectClient`, `PermissionController`,
`HealthPermission`, `StepsRecord`, `HeartRateRecord`, `RestingHeartRateRecord`,
`SleepSessionRecord`, `ExerciseSessionRecord`, `TotalCaloriesBurnedRecord`,
`ActiveCaloriesBurnedRecord`, `DistanceRecord`, `AggregateRequest`, `ReadRecordsRequest`,
`ChangesTokenRequest` and the `changes/` package. **What it lacks**: the constants
`PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND` (added alpha09) and
`PERMISSION_READ_HEALTH_DATA_HISTORY` (added alpha10) — workaround: the permission strings are
OS-level and stable, so declare them as literals in the manifest and pass the same literals to the
permission-request contract. **Verify on a real device early in Phase B1** — if the older client's
request contract rejects an unknown permission string, fall back to foreground-only reads and a
30-day window, still a complete, useful feature. **Phase B0's first task is an on-device
API-surface compile check**: reference `HealthConnectFeatures` and each of the two permission
constants and every one of the 12 MVP record classes, and record which resolve — the plan's §6.1
availability claims are evidence-based but not compiler-verified, and this settles them in one
build.

A toolchain upgrade to `1.1.0` stable (Gradle 8.6 → 8.11.1+, AGP 8.3.2 → 8.9.1+, compileSdk 34 →
36, and re-validating every frozen pin plus compose-bom plus google-services/Crashlytics/App
Distribution) is a genuine, multi-day, whole-build round with no user-visible benefit of its own
and a real chance of breaking sign-in, biometrics, or the release build — **its own separate round,
later**, at which point moving from alpha08 to 1.1.0 stable is a one-line version bump.

### 5.2 Distribution note

Apps distributed **on Google Play** that read Health Connect data must complete a Health Connect
data-type declaration form in Play Console and publish a privacy policy. Daybook is sideloaded via
Firebase App Distribution and has no Play listing, so this does not apply today — it becomes a
hard gate the day this app is listed on Play. Separately, and regardless of distribution: Health
Connect requires the app to declare a privacy-policy rationale activity in the manifest (§6.2).

## 6. Feature 1: Health sync design

### 6.1 Data types

The user asked for "as much data as possible, done right". Every real Health Connect record type
is enumerated below with an explicit MVP / LATER / NEVER decision and a specific reason, taken from
Google's published Health Connect data-type reference: 42 record types across 7 categories.

#### 6.1.0 A hard constraint that decides several rows

Under the `connect-client:1.1.0-alpha08` pin, these types do not exist in the pinned client and
are LATER-by-construction regardless of merit:

| Record | First available |
|---|---|
| `SkinTemperatureRecord` | `1.1.0-alpha10` |
| `PlannedExerciseSessionRecord` | `1.1.0-alpha10` |
| `MindfulnessSessionRecord` | `1.1.0-beta02` |
| `ActivityIntensityRecord` | `1.2.0-alpha01` |

If a later toolchain round (§5.1) moves off the pin, these become available as a one-line version
bump. Every other type in the table predates alpha08 and is available today, to be confirmed by
the B0 compile check.

#### 6.1.1 Activity (15 types)

| Record | Decision | Reason |
|---|---|---|
| `StepsRecord` | **MVP** | The single most-wanted number. Read as `AggregateRequest(StepsRecord.COUNT_TOTAL)` daily — never raw records. |
| `DistanceRecord` | **MVP** | Pairs with steps in one summary row; one more aggregate on a request already made. |
| `ActiveCaloriesBurnedRecord` | **MVP** | The "calories burned" figure users expect. |
| `TotalCaloriesBurnedRecord` | **MVP** | Read alongside active because different apps write different ones — showing a blank because the wrong field was picked would look like a bug. `HealthDay` stores both nullable and the UI prefers active. |
| `ExerciseSessionRecord` | **MVP** | The band's recorded workouts. |
| `FloorsClimbedRecord` | **LATER** | Mi Band 10 does not report floors reliably; would render as a permanently empty stat. |
| `ElevationGainedRecord` | **LATER** | Only meaningful for outdoor sessions Daybook shows no route or profile for. |
| `Vo2MaxRecord` | **LATER** | A single sporadic value that needs a trend line to mean anything; charts are deferred. |
| `SpeedRecord` | **NEVER (as a day metric)** | A per-sample series inside a session; no session-detail chart exists to show it. |
| `PowerRecord` | **NEVER** | Cycling-power-meter data; no cyclist-facing surface. |
| `StepsCadenceRecord` | **NEVER** | Per-sample cadence series, same objection as speed. |
| `CyclingPedalingCadenceRecord` | **NEVER** | As above, and needs a cadence sensor almost nobody has. |
| `WheelchairPushesRecord` | **NEVER** | Only meaningful as a full replacement for steps, i.e. a whole alternative activity model — a real accessibility gap, named rather than passed over in silence. Revisit if ever asked for. |
| `ActivityIntensityRecord` | **LATER (pin)** | Needs `1.2.0-alpha01`. |
| `PlannedExerciseSessionRecord` | **LATER (pin)** | Needs `1.1.0-alpha10`, and is *planned* training — Round C territory (Programs/Routines), not "what happened" reporting. |

#### 6.1.2 Vitals (9 types)

| Record | Decision | Reason |
|---|---|---|
| `HeartRateRecord` | **MVP** | Daily avg/min/max via `AggregateRequest`. Never the raw per-sample series. |
| `RestingHeartRateRecord` | **MVP** | One value per day, a well-established cardio signal. |
| `OxygenSaturationRecord` | **MVP** | SpO₂ is a Samsung Health headline metric and Mi Band 10 measures it — one nullable `Float`, one more permission. |
| `HeartRateVariabilityRmssdRecord` | **LATER** | Only interpretable as a deviation from your own baseline; genuinely useful in a fatigue dashboard, which is Round C (§C.6). |
| `RespiratoryRateRecord` | **LATER** | Sparsely written, usually only during sleep; no room for it in Round B's sleep card. |
| `BodyTemperatureRecord` | **LATER** | Manually-entered clinical data in practice; no band writes it automatically. |
| `BasalBodyTemperatureRecord` | **OUT** | Categorised as Cycle Tracking; §6.1.5 keeps that category out entirely. |
| `BloodPressureRecord` | **OUT of Round B** | Needs a BP cuff; nothing in the user's stated setup writes it. An empty stat is worse than no stat. Adding it later is one permission, one reader, two nullable columns — fully additive. |
| `BloodGlucoseRecord` | **LATER, with a specific note** | Daybook is already a Crohn's-oriented food diary, so glucose alongside food logs is a coherent idea — but it needs a CGM or manual entry, and presenting medical data next to a symptom diary raises a care question this plan does not answer unilaterally. Out of Round B for the same reason as blood pressure; if the user owns a CGM, it becomes its own small addition. |
| `SkinTemperatureRecord` | **LATER (pin)** | Needs `1.1.0-alpha10`. |

#### 6.1.3 Sleep (1 type)

| Record | Decision | Reason |
|---|---|---|
| `SleepSessionRecord` | **MVP** | Session read, sum the `stages` for deep/light/REM/awake. `HealthDay` has all five columns. |

#### 6.1.4 Body measurement (7 types)

| Record | Decision | Reason |
|---|---|---|
| `WeightRecord` | **MVP** | The most-requested body metric, written by every smart scale and by Mi Fitness manual entry — the only one of this group a plain band user actually populates. |
| `BodyFatRecord` | **LATER** | Smart-scale bioimpedance only, notoriously unreliable. |
| `BasalMetabolicRateRecord` | **LATER** | A derived estimate, useful only alongside nutrition targets. |
| `LeanBodyMassRecord` | **LATER** | Smart-scale-derived, same objection as body fat. |
| `BoneMassRecord` | **LATER** | Smart-scale-derived, smallest audience. |
| `BodyWaterMassRecord` | **LATER** | As above. |
| `HeightRecord` | **LATER** | Changes essentially never; only use is BMI, which Daybook does not show. |

#### 6.1.5 Cycle tracking (7 types) — none in Round B

`MenstruationFlowRecord`, `MenstruationPeriodRecord`, `OvulationTestRecord`,
`CervicalMucusRecord`, `SexualActivityRecord`, `IntermenstrualBleedingRecord`,
`BasalBodyTemperatureRecord`.

**None of the seven. Not requested, not in the manifest, no opt-in row, no `HealthDay` column, no
UI.** Three reasons, and the third is the one that makes it safe:

1. **An unrequested permission is invisible; a wrongly-requested one is alarming.** Putting
   `SexualActivityRecord` on the OS consent sheet of a habit tracker is a surprise nobody asked
   for.
2. **An opt-in row is not actually cheap** — a second permission flow, a second granted/denied
   state, seven more record readers, a second set of `HealthDay` columns, a second payload
   decision — for a category never requested.
3. **This is the one decision here that is free to reverse.** Adding a permission later is purely
   additive: a manifest line, a reader, an optional nullable field. Shipping it and then removing
   it means a user who granted sensitive permissions to an app that then stopped using them, plus
   data to decide what to do with. When one direction is reversible and the other is not, take the
   reversible one.

If the user later asks for it, it is its own small round with its own privacy copy, not a quiet
addition to `HealthPermissions.kt`.

#### 6.1.6 Nutrition (2 types) — and the Intake question

| Record | Decision | Reason |
|---|---|---|
| `HydrationRecord` | **MVP** | A volume with a time, written by MyFitnessPal and Samsung Health, maps to a single daily-total number. |
| `NutritionRecord` | **MVP, read-only, displayed separately** | MyFitnessPal writes it, and it is the one record type that collides with a feature Daybook already has — see below. |

Daybook already has a hand-logged **Intake** feature: `food_med_tasks` / `food_med_occurrences`,
free-text answers, a Crohn's trigger flag, a suspected-trigger-food field, an outside-food marker.
`NutritionRecord` carries something structurally different: calories and macros per meal, written
by another app.

**Decision: imported nutrition is shown as a read-only summary, in its own card, on the Health
surface — never merged into, never written into, and never displayed inside the Intake tab.**

- New nullable columns on `HealthDay` (§7.1): `nutritionCalories: Float?`,
  `nutritionProteinGrams: Float?`, `nutritionCarbsGrams: Float?`, `nutritionFatGrams: Float?`,
  `hydrationMl: Float?` — daily aggregates only, not per-meal rows (a second occurrence-shaped
  table would compete with `food_med_occurrences` for the same conceptual space).
- Rendered on `HealthTabScreen` (§7.4, inside Beast Mode — not a main-app screen) as one `SoftCard` titled **`Nutrition`**, subtitle **`From
  MyFitnessPal`** — resolved at runtime from `metadata.dataOrigin.packageName` through a small
  pure `SourceAppLabels` map, exact fallback string `From another app` for an unrecognised
  package. No icon scraping, no `PackageManager` label lookup.
- **Nothing about the Intake tab changes.** No badge, no merged row, no "you also ate" line.

**Why separate.** The dividing line is "does something else keep updating this?" A
`NutritionRecord` is re-read on every pull and owned by MyFitnessPal — a local edit would be
overwritten, a local delete would resurrect. Daybook's Intake entries are the user's own prose and
theirs to edit forever. A merged view would make the same lunch appear twice — once as the user's
typed note, once as MyFitnessPal's 620 kcal — with no honest way to dedupe them.

#### 6.1.7 Wellness (1 type)

| Record | Decision | Reason |
|---|---|---|
| `MindfulnessSessionRecord` | **LATER (pin)** | Needs `1.1.0-beta02`; unreachable under the Option A pin. |

#### 6.1.8 Summary of the MVP permission set

**12 record types, 12 read permissions:** `StepsRecord`, `DistanceRecord`,
`ActiveCaloriesBurnedRecord`, `TotalCaloriesBurnedRecord`, `ExerciseSessionRecord`,
`SleepSessionRecord`, `HeartRateRecord`, `RestingHeartRateRecord`, `OxygenSaturationRecord`,
`WeightRecord`, `HydrationRecord`, `NutritionRecord`.

**Request all twelve in one call**, not a six-then-six split with an `Add more health data` row.
Three reasons: Health Connect's consent sheet is itself a per-type picker, so splitting the
request doesn't give the user a choice they didn't already have; the user's stated goal was "as
much as possible", and withholding half the request optimises a metric never asked about; and the
partial-grant state is designed for anyway — §6.2's **"Partially granted"** ladder row means
six-of-twelve is a normal, fully-rendered state, which is the real mitigation for a lower grant
rate and ships either way. If grant rate turns out to be a real problem in practice, re-requesting
the missing types is one call to the same launcher — no schema change, no round.

#### 6.1.9 Never, at any point, for any version

- **Raw per-sample series of any kind** — heart rate, speed, cadence, power. Aggregates only.
  Reading a day of per-second HR to render one number would be a battery regression (C7).
- **GPS routes / `ExerciseRoute`.** Daybook draws no maps, has no map dependency (C5).
- **Writing anything back to Health Connect.** Read-only, permanently — no write permission is
  ever declared in the manifest.
- **Xiaomi "Stress", "Body Energy", "PAI" and similar.** Vendor-invented metrics with no Health
  Connect record type at all — they exist only inside Mi Fitness and are not published to any
  app. They will never appear in Daybook, no matter which permissions are granted.

### 6.2 Permissions, manifest and availability

`AndroidManifest.xml` additions:

```xml
<!-- the 12-type MVP set -->
<uses-permission android:name="android.permission.health.READ_STEPS"/>
<uses-permission android:name="android.permission.health.READ_SLEEP"/>
<uses-permission android:name="android.permission.health.READ_EXERCISE"/>
<uses-permission android:name="android.permission.health.READ_HEART_RATE"/>
<uses-permission android:name="android.permission.health.READ_RESTING_HEART_RATE"/>
<uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED"/>
<uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED"/>
<uses-permission android:name="android.permission.health.READ_DISTANCE"/>
<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION"/>
<uses-permission android:name="android.permission.health.READ_WEIGHT"/>
<uses-permission android:name="android.permission.health.READ_HYDRATION"/>
<uses-permission android:name="android.permission.health.READ_NUTRITION"/>
<!-- optional, requested separately, degrade gracefully if refused -->
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"/>
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY"/>

<!-- required so getSdkStatus() can see the provider on Android 13 and below -->
<queries><package android:name="com.google.android.apps.healthdata" /></queries>
```

and, inside `<application>`, the required privacy-policy rationale activity, in both its forms:

```xml
<activity android:name=".ui.health.HealthPermissionsRationaleActivity"
          android:exported="true" android:theme="@style/Theme.Daybook">
  <intent-filter>
    <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
  </intent-filter>
</activity>
<activity-alias android:name="ViewPermissionUsageActivity"
          android:exported="true"
          android:targetActivity=".ui.health.HealthPermissionsRationaleActivity"
          android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
  <intent-filter>
    <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
    <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
  </intent-filter>
</activity-alias>
```

`HealthPermissionsRationaleActivity` is a tiny `ComponentActivity` (does not need to be the
`FragmentActivity` `MainActivity` is, since it hosts no `BiometricPrompt`) rendering one Compose
screen inside `DaybookTheme`: what Daybook reads, why, that it never writes, and that the data
stays on the device except for the user's own encrypted account backup. This doubles as the app's
health privacy statement.

**Availability and degradation ladder** (`data/health/HealthConnectAvailability.kt`, a pure
mapping + a thin `getSdkStatus` caller, unit-tested). Every row carries exact final copy. The rule
for the whole table: **the Health tab may render an empty/help state silently, but Beast Mode
Settings must always be able to tell the user why.** Rendering an empty state is not an error; a
user asking "why is there nothing here?" and finding no answer is. **Placement note, per §7.4's
superseding decision: every "Today card" below means the `Health` tab's own body (there is no
separate summary card to hide — the tab itself renders whichever row applies), and every "Settings
→ Health" below means the `Health Connect` group inside Beast Mode Settings (§3.8.2, §7.4) — not
Daybook's main Settings.** The copy in each row is otherwise exact and unchanged from the original
design.

| State | Exact behaviour and copy |
|---|---|
| Android 8.0–8.1 (API 26–27) | Health Connect requires Android 9+. Treated as permanently unavailable — falls into `SDK_UNAVAILABLE` below. Never branch on `Build.VERSION` for this; `getSdkStatus()` reports it. |
| `SDK_UNAVAILABLE` | Today card never renders. Settings → Health shows one muted line: `Health Connect isn't available on this phone.` No Play link — nothing to install. No "Connect" button. |
| `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` | Settings → Health shows: `Health Connect needs updating before Daybook can read your health data.` with a `GhostButton` `Update Health Connect` deep-linking to `market://details?id=com.google.android.apps.healthdata`. If no app can handle that intent, show `Couldn't open the Play Store on this phone.` in `DaybookColors.Danger` instead of crashing or doing nothing. |
| `SDK_AVAILABLE`, permissions not granted | Today card hidden. Settings → Health shows `Connect`, launching the OS permission sheet. Sub-line: `Daybook only reads. It never writes anything to Health Connect.` |
| Permission sheet returned with nothing granted | Show, once, in the Settings result slot: `No health data was shared. You can tap Connect again any time, or choose which data to share in the Health Connect app.` Do not re-prompt automatically and do not nag (C7/§6.3). |
| Permission sheet threw / no handler | `ActivityNotFoundException` or any throwable from the request contract. Show `Couldn't open the Health Connect permission screen. Make sure Health Connect is installed and up to date.` in `DaybookColors.Danger`, and `Log.e` the throwable. **This must not be a silent catch** — it is also the branch that would catch the alpha08 client rejecting an unknown permission string (§5.1 R4). |
| Partially granted | Some of the 12 types granted, some not — the normal outcome, since the OS sheet has per-type checkboxes. Not an error. Read and show what was granted; show nothing for what wasn't (no empty stat with a dash). Settings → Health lists ungranted types under `Not shared:` followed by their display names, with a `Change what's shared` row opening the Health Connect settings screen. |
| Granted, but zero records | First-class help state, not a blank card: `No data yet. In Mi Fitness, open Profile → Settings → Health Connect and turn on Steps, Sleep, Heart rate and Workouts.` The most likely real-world failure. |
| A read threw | Any `HealthConnectClient` call failing (provider killed, `RemoteException`, an unknown record type). Keep the last-good data on screen — do not blank the card — and set the Settings → Health status line to `Couldn't refresh your health data. Last updated <relative time>.` |
| Granted, data present | Normal. Settings → Health status line reads `Last updated <relative time>.` |

Android 14+ (API 34) has Health Connect in the framework; Android 9–13 needs the Play Store app.
`HealthConnectClient.getSdkStatus()` abstracts both — never branch on `Build.VERSION` directly.
`minSdk` stays 26; it does not need to rise, the feature simply reports unavailable on 26–27.

### 6.3 Sync model

**Direction: read-only. Daybook never writes to Health Connect.** Writing steps or heart rate back
would be nonsense and risk feedback loops with Mi Fitness. The one arguable write — publishing a
Daybook gym session as an `ExerciseSessionRecord` — is deferred (§6.4).

**Cadence:**

1. **On app resume** — `MainActivity.onResume` triggers a pull, throttled to at most once every 15
   minutes via a timestamp in the health state store.
2. **Once a day in the background** — folded into the existing `WindowRefreshWorker`, not a new
   periodic worker. One fewer scheduled job is one fewer battery line-item, and this project has a
   battery-regression history (C7). **No `HealthPullWorker.kt`.**
3. **Delta reads via the changes API.** After the first full pull, store a changes token and use
   `getChanges(token)` so subsequent pulls process only what changed. If the token has expired
   (`changesTokenExpired`), fall back to a full re-read of the last 30 days and mint a new token.
   The expiry fallback is silent, and that is correct — a token expiring is normal — but it must
   not be invisible: `HealthSyncStateStore` records `health_last_full_resync_at`, surfaced as
   `Rebuilt your health history <relative time>.` when it happened within the last 24 hours. If the
   full re-read itself then fails, that is a real failure and takes the "A read threw" row of
   §6.2's ladder — and the token must **not** be cleared in that case, or every subsequent pull
   re-attempts a full 30-day read forever (a silent battery regression).
4. **No `registerForDataNotifications` push subscription** — provider-dependent, and would mean
   waking the app on someone else's write cadence, against C7.
5. **A manual "Refresh now" row** in Beast Mode Settings' `Health Connect` group (§7.4). User-triggered, so it reports both
   outcomes: label swaps to `Refreshing…` and disables while running; on success, `Last updated
   just now.`; on failure, `Couldn't refresh your health data. Check that Health Connect is still
   installed, then try again.` in `DaybookColors.Danger`; **on success with nothing new** (the
   common case), say so rather than looking inert: `Up to date — nothing new from your band.`

**Background-read permission:** the daily worker reads while backgrounded, which on newer Health
Connect providers requires `READ_HEALTH_DATA_IN_BACKGROUND`. Requested as an optional extra. If
refused or unsupported: drop to on-resume-only reads and say so — `Daybook will update your health
data when you open the app.`, recording `health_bg_permission_denied` so the request is not
repeated. Never nag. **If the request itself throws** (the alpha08 client rejecting an unknown
permission string), that is not the same as a refusal and must not be recorded as one: show
`Couldn't ask for background access on this phone. Daybook will update when you open the app.`,
`Log.e` the throwable, treat as unsupported-not-denied.

**"Import my past data"** is user-triggered and needs the same treatment: row label `Import my past
data` → `Importing…` while running. Success: `Imported your health history back to <date>.`
(`d MMM yyyy`). Permission refused: `Daybook can only see the last 30 days without access to your
history.` Failed: `Couldn't import your past data. Try again.` Nothing found: `No older health
data was available.`

**History window:** without `READ_HEALTH_DATA_HISTORY`, Health Connect only serves the 30 days
preceding the permission grant; reading a single older record errors. So: default first pull =
**last 30 days**; Beast Mode Settings' `Health Connect` group (§7.4) gets an "Import my past data" button that requests the history
permission and backfills further, with a **365-day cap**: a longer window means more month docs
hydrated and pushed, and older data the band has often does not have anyway. Uninstalling Daybook
revokes the history permission, and reinstalling resets the 30-day window from the new grant date
— the data already pulled into Daybook's own Room DB survives via the account sync (§7.5).

**State storage:** `data/health/HealthSyncStateStore.kt`, `@Singleton`, backed by the same
`daybook_prefs` SharedPreferences file `SyncStateStore` uses. Keys: `health_changes_token`,
`health_last_pull_at`, `health_backfill_through`, `health_bg_permission_denied`. Not a Room table —
a Room write here would re-trigger the `InvalidationTracker` observer and mark a cloud push
pending on every poll, the exact feedback loop `SyncStateStore`'s KDoc warns about.

### 6.4 Relationship to Round A's Workout mode — deliberately separate tables, same mode

**A workout recorded by the band (an `ExerciseSessionRecord`) does NOT appear in the Routines/
History surfaces (`WorkoutRoutes.HOME`/`.HISTORY`). It appears only on the `Health` tab
(§7.4).** This section's reasoning predates the placement decision in §7.4 and is unaffected by
it: both surfaces now happen to live inside the same Beast Mode shell, but that is a UI-placement
fact, not a data-model one — `health_sessions` and `workout_sessions` stay two separate tables with
no foreign key or merge between them, for exactly the reasons below. **Placing the Health tab
inside Beast Mode makes this distinction more visible, not less** — a user on the History tab sees
only what they logged; one swipe over on the Health tab, they see only what the band recorded; the
mode boundary that used to separate "Workout mode" from "the rest of the app" now does double duty
separating "hand-logged" from "band-recorded" within the same mode, which is arguably a clearer
home for this distinction than the original main-app/Health-screen split ever was.

- **Double-counting is the default failure.** Log a gym session in Daybook and wear the band, and
  there are now two records of one workout. Merging them needs overlap-window dedupe heuristics
  that are wrong often enough to be annoying.
- **An imported session is not editable the same way.** It has no sets, reps or weights. Putting
  it in Workout mode means inventing a read-only session state, an "imported" badge, a different
  detail screen, and a rule for what happens when the user edits one.
- **Ownership and sync get murky.** A Health-Connect-derived session is derived data that can be
  re-derived; a hand-logged one cannot. Storing them in the same table and syncing them identically
  means a deleted band workout can resurrect from the cloud.

Keeping them separate is honest: "this is what you logged" vs "this is what your band saw". The
`workout_sessions.source` column (§3.2) exists so that if the user later wants an "import this band
workout as a workout entry" **button** — an explicit, one-tap, user-driven action, not automatic
merging — it is a UI change and not a migration.

## 7. Round B technical breakdown

### 7.1 New Room entities (`data/model/HealthModel.kt` — new file)

```
@Serializable @Entity(tableName = "health_days")
data class HealthDay(
    @PrimaryKey localDate: String        // "yyyy-MM-dd" — one row per local calendar day
    steps: Int? = null
    distanceMeters: Float? = null
    activeCalories: Float? = null
    totalCalories: Float? = null
    restingHeartRate: Int? = null
    avgHeartRate: Int? = null
    minHeartRate: Int? = null
    maxHeartRate: Int? = null
    sleepMinutes: Int? = null
    sleepDeepMinutes: Int? = null
    sleepLightMinutes: Int? = null
    sleepRemMinutes: Int? = null
    sleepAwakeMinutes: Int? = null
    sleepStartMillis: Long? = null
    sleepEndMillis: Long? = null
    spo2Percent: Float? = null           // OxygenSaturationRecord, daily average
    weightKg: Float? = null              // WeightRecord, the day's last reading. ALWAYS kg —
                                         // same storage rule as WorkoutSet.weightKg.
    hydrationMl: Float? = null           // HydrationRecord, daily total
    nutritionCalories: Float? = null     // NutritionRecord, daily totals. Aggregates ONLY —
    nutritionProteinGrams: Float? = null //  per-meal rows would compete with food_med_occurrences
    nutritionCarbsGrams: Float? = null   //  for the same conceptual space (§6.1.6).
    nutritionFatGrams: Float? = null
    nutritionSourceApp: String? = null   // metadata.dataOrigin.packageName, for "From MyFitnessPal"
    updatedAt: Long
)
```

Every metric is nullable, and nullable means "no data", distinct from zero. A day with 0 steps
recorded is not the same as a day the band was on the charger — the same rule as Ri3, applied to
every column above; none may ever acquire a `NOT NULL DEFAULT 0`. All nine new columns land in the
same `MIGRATION_22_23` as the rest of Round B.

```
@Serializable @Entity(tableName = "health_sessions",
    indices = [Index("local_date"), Index("start_millis")])
data class HealthSession(
    @PrimaryKey id: String               // Health Connect Record.metadata.id — stable, so a
                                         // re-read upserts rather than duplicating
    localDate: String
    exerciseType: Int                    // ExerciseSessionRecord.EXERCISE_TYPE_* constant
    title: String? = null
    startMillis: Long
    endMillis: Long
    durationMinutes: Int
    activeCalories: Float? = null
    distanceMeters: Float? = null
    avgHeartRate: Int? = null
    sourceApp: String? = null            // metadata.dataOrigin.packageName, e.g. "com.mi.health"
    updatedAt: Long
)
```

`exerciseType` is stored as the raw int and mapped to a display label by a pure
`ExerciseTypeLabels` map (unit-tested), so an unknown/future type renders as "Workout" instead of
crashing. `ui/health/` needs a distance/duration formatter shared with Round A's set rows — put it
in `util/` so both use one implementation.

### 7.2 `MIGRATION_23_24`

Additive: `CREATE TABLE health_days`, `CREATE TABLE health_sessions` + its two indices, and one
device-local `ALTER TABLE app_settings ADD COLUMN health_tab_last_mode INTEGER NOT NULL DEFAULT 0`
(§7.4's day/aggregate toggle, remembered like every other Beast-Mode-local preference — 0 = day
view, 1 = aggregate view). **No `health_card_enabled` column** — that column belonged to the
now-dropped Today card (§7.4) and never ships. `AppDatabase.version` **23 → 24**, two entities + two
DAO accessors, registered in `DatabaseModule`, `24.json` exported, `MigrationTest.migrate23To24`
added.

### 7.3 New files

All health-specific UI lives under `ui/workout/health/`, inside Beast Mode's own package, for the
same reason `WorkoutSettingsScreen.kt` lives under `ui/workout/` and not `ui/settings/` (§3.8.2):
reachable from exactly one place, inside the mode.

| File | Role |
|---|---|
| `data/health/HealthConnectAvailability.kt` | `getSdkStatus` wrapper + pure state→UI mapping |
| `data/health/HealthPermissions.kt` | the permission string set, the optional extras, and a pure `missingPermissions(granted, required)` helper |
| `data/health/HealthConnectReader.kt` | all `HealthConnectClient` calls: aggregates, session reads, changes token. The only file that imports `androidx.health.connect.*`. |
| `data/health/HealthSyncStateStore.kt` | SharedPreferences token/cursor store (§6.3) |
| `data/HealthRepository.kt` | orchestration: pull → map → upsert into Room; `@Singleton`, Hilt-provided in `DatabaseModule` |
| `data/local/HealthDao.kt` | day + session queries, range reads for a single day, range reads for the aggregate mode (§7.4), range reads for export, range deletes for eviction |
| `ui/workout/health/HealthPermissionsRationaleActivity.kt` | the required manifest activity + privacy copy. Its manifest declaration is unconditional (an OS requirement, §6.2) even though nothing else about Health Connect appears in the main app — it is not itself a Beast-Mode screen, just a system-facing activity with no entry point of its own. |
| `ui/workout/health/HealthTabScreen.kt` + `HealthTabViewModel.kt` | the repurposed third nav destination (§7.4) — day/aggregate toggle, the `WeekStrip`-identical calendar, the metric cards, the band's workout sessions |
| `ui/workout/health/HealthAggregateSheet.kt` | the date-range picker for aggregate mode (§7.4) |
| `ui/workout/WorkoutSettingsScreen.kt` (existing file, extended) | gains one new `SettingsGroup` — connect / disconnect, permission status, refresh now, import past data, which types are shared (§7.4) |

**No `ui/health/` package, no `ui/settings/HealthSettingsScreen.kt`, no `HealthSummaryCard.kt`, no
stacked `"health"` route outside Beast Mode.** All three existed in the original version of this
plan and are dropped outright per the user's decision (see the top of §7 and §7.4) — none of them
were ever implemented, so nothing is migrated away from; they are simply not built.

**No `util/work/HealthPullWorker.kt`** — the daily pull is a call inside `WindowRefreshWorker`; the
other two cadences are an on-resume throttled pull and a manual "Refresh now" (§6.3, now living in
Beast Mode Settings rather than main Settings).

**Isolation rule:** every Health Connect type stays behind `HealthConnectReader` and
`HealthConnectAvailability`. Nothing in `ui/`, `HealthRepository` or the Room layer imports
`androidx.health.connect.*`. If the alpha pin later moves to 1.1.0 stable, that is then a two-file
change.

### 7.4 UI placement — inside Beast Mode only

**Superseding decision, replacing the original version of this plan in full.** The original
version of §7.4 put a health summary card on Today and a stacked "Health" screen behind it,
reachable from the main app, with its settings living in `ui/settings/HealthSettingsScreen.kt`.
**None of that was ever built.** The user's instruction for this revision is explicit and
absolute: **Health Connect data and every Health Connect setting live only inside Beast Mode —
never on Today, never in Daybook's main Settings, never as a standalone main-app route.** This is
not a compromise between the two designs; the old design is deleted from the plan, not merged with
the new one.

**Where it lives: the repurposed third Beast Mode nav slot.** Beast Mode's bottom nav keeps its
three items and their route constants (`WorkoutRoutes.HOME` / `.HISTORY` / `.LIBRARY`, §3.6.7,
already shipped) — only `.LIBRARY`'s destination changes:

| # | Route | Old label/icon (Round A, shipped) | New label/icon (Round B) | New destination |
|---|---|---|---|---|
| 3 | `WorkoutRoutes.LIBRARY` = `"workout_library"` | `Exercises` / `DaybookIcons.Category` | **`Health`** / `DaybookIcons.Heart` | `HealthTabScreen` (this section) |

**Icon choice.** `DaybookIcons` (`ui/icons/DaybookIcons.kt`, the same file `.Clock` and `.Category`
already come from) does not yet have a heart glyph; adding `DaybookIcons.Heart` as one more
`ImageVector` built in code is exactly the precedent §0 already records for `.Clock`/`.Category` —
no new vector asset, no `material-icons-extended` dependency. Rejected alternative:
`MI.Filled.Favorite` (material-icons-core, zero new code) — rejected only because every other Beast
Mode nav icon is a `DaybookIcons` entry and mixing a stock Material icon into that row for the one
item that happens to need a heart shape would be the visible seam; the one-`ImageVector` cost is
worth the consistency.

**The exercise browser does not disappear.** `AddExerciseScreen` in `BROWSE` mode is simply no
longer reachable from the bottom nav — it stays exactly where §3.6.6's route table already has it
reachable from, in `PICK` mode, from the live session's `+ Add Exercise` and the routine editor's
`+ Add exercise`. Managing (renaming/archiving) a custom or imported exercise outside of adding one
to something loses its dedicated nav slot; the pragmatic call, made here because the user
explicitly called the old Exercises tab purposeless and this plan does not invent a replacement
surface for a use nobody asked to keep: that management action moves to the exercise's row overflow
wherever `PICK` mode already renders it (§3.7.3's "Row overflow" column, already `Edit` /
`Archive`), which is unaffected by this change and needs no new work.

**`HealthTabScreen` — the page itself:**

```kotlin
@Composable
fun HealthTabScreen(
    contentPadding: PaddingValues,             // from DaybookScaffold, carries Beast Mode's nav
                                               // clearance, same as WorkoutHomeScreen (§3.7.4)
    onOpenSettings: () -> Unit,                // -> "workout_settings" (§3.8.2), NOT a local gear —
                                               // Beast Mode already has exactly one settings gear,
                                               // on the Routines landing (§3.7.4); this screen
                                               // reuses it rather than growing a second one
    viewModel: HealthTabViewModel = hiltViewModel()
)

enum class HealthTabMode { DAY, AGGREGATE }
```

- **Chrome**: `ScreenHeader(title = "Health", subtitle = <day: the selected date, formatted;
  aggregate: the range, formatted>)`, drawn with `BeastText`/`BeastPalette`, not `DaybookText`/
  `DaybookColors` (§0.1, C5's Beast-Mode carve-out) — the same rule every other Beast Mode screen
  already follows. Beast Mode's pill nav sits underneath, consuming `contentPadding` exactly like
  `WorkoutHistoryScreen` (§3.7.6).
- **A `SegmentedControl` right under the header**: **`Day` / `Range`** — the `HealthTabMode`
  switch. Chosen over two separate nav rows or a toggle button because `SegmentedControl` is
  already Daybook's existing two-state-switch component (§0) and the choice is mutually exclusive
  by construction. Persisted in `app_settings.health_tab_last_mode` (§7.2) so re-opening the tab
  remembers which mode was last used — this is the one piece of state that is genuinely "which
  view was I in", not "what date am I looking at", and it is device-local by the same rule every
  other Beast Mode preference already follows (§3.8.2).

**`Day` mode — the calendar, byte-for-byte the same UI and the same implementation as Today's.**
The user's instruction is exact: same UI, same behaviour as the Home/Today tab's existing calendar.
Today's calendar is `ui/components/WeekStrip.kt`'s `WeekStrip` composable — a week-strip ⇄
month-grid pager with the exact expand/collapse chevron, the "Back to today" fixed-height slot, and
the future-day-is-shown-but-not-selectable rule (`ui/components/WeekStrip.kt:63-303`). **`Day` mode
calls this same `WeekStrip` composable directly** — not a re-implementation, not a health-specific
fork:

```kotlin
WeekStrip(
    selectedDate = state.selectedDate,
    today = state.today,
    onSelect = viewModel::selectDate,
    expanded = state.calendarExpanded,
    onToggleExpanded = viewModel::toggleCalendarExpanded,
    weekStart = state.weekStart          // same app_settings-backed preference Today reads
)
```

This is not "the same look" achieved by copying `WeekStrip`'s code into a Beast-Mode-styled
duplicate — it is a call to the identical function Today already uses, so any future fix or change
to the week/month calendar behaviour applies to both call sites automatically and can never drift
between them. `WeekStrip`'s own internals (`DaybookColors`, `LocalAccent`) are untouched by this —
`LocalAccent` already resolves to `BeastAccentColor`'s current value inside Beast Mode via the
existing accent-provider gate (§3.8.3), so the selected-day pill and the "today" dot render in
Beast Mode's accent with zero code change to `WeekStrip` itself. Below the strip: the day's metric
cards (next bullet) for whichever `LocalDate` is selected.

**`Range` mode — the aggregate view.** A `GhostButton` row under the segmented control reading the
current range (e.g. `Last 7 days`), tapping it opens `HealthAggregateSheet` — a `Sheets.kt` bottom
sheet offering four preset ranges (**`Last 7 days`**, **`Last 30 days`**, **`This month`**, **`Last
3 months`**) plus a **`Custom range`** row opening the existing two-date-picker pattern
`DataSettingsScreen`'s "Export a date range" already uses (`DaybookDatePickerDialog` ×2 — the same
component, not a re-implementation). **Default range: `Last 7 days`** — chosen because it is the
shortest window that smooths day-to-day noise in steps/sleep/heart-rate without needing more than a
handful of records hydrated, and it matches the "last week" framing already familiar from
`WeekStrip`'s own default view. Below the range row: the same metric cards as `Day` mode, but each
value is the range's mean (steps, distance, calories, heart rate, SpO₂, weight, hydration,
nutrition) or sum where a sum is the meaningful figure (a range's total workouts, total sleep
hours) — a pure `aggregateHealthDays(days: List<HealthDay>): HealthAggregate` function,
unit-tested, decides per-metric whether it averages or sums, so this is not an ad-hoc per-card
decision made in the composable. The aggregate sheet separately counts and shows how many of the
range's days had *any* health data at all — e.g. `5 of 7 days have data` — so a mostly-empty range
is legible rather than looking like a bug; this coverage line is a summary, not a substitute for
the per-card hide rule below, which still applies to every individual card underneath it.

**Metric cards — the MVP record set, unchanged from §6.1's scoping, only relocated:** steps +
distance, calories (active/total), heart rate (avg/resting/min/max), sleep (with the
deep/light/REM/awake breakdown), SpO₂, weight, hydration, the separate Nutrition card with its
"From …" source-app subtitle (§6.1.6, unchanged), and the band's `HealthSession` rows for the
selected day or range. Every card is a `SoftCard` built from `BeastComponents`, not
`ui/components/Components.kt`'s plain `SoftCard` — Beast Mode's own visual identity (§0.1) applies
here exactly as it does to every other Beast Mode screen.

**Per-metric hide rule — new for this revision, binding on every card on this page, in both
modes.** §6.2's degradation ladder governs whether the **whole page** has anything to show at all
(unavailable / ungranted / a read failed); this rule is a separate, narrower one that applies
**underneath** that ladder, once the page has *something* to show. **If a given metric's data was
not actually fetched or is not available for the selected day (`Day` mode) or is absent for every
day in the selected range (`Range` mode), that metric's card is not rendered at all** — not an
empty card, not a card with a dash, not a "No data" placeholder card. This is the exact same rule
the original plan already stated for the whole page in one place — "**the card renders nothing at
all** when Health Connect is unavailable or ungranted… because [this] is not a settings screen"
(the dropped Today-card version of this rule) and "show nothing for what wasn't [granted] (no
empty stat with a dash)" (§6.2's "Partially granted" row) — now applied at the finer grain of one
card among many on an otherwise-populated page: a user who has granted Steps and Sleep but not
SpO₂, or whose band simply never reports Distance, sees a page with Steps and Sleep cards and *no
gap, no dash, no "SpO₂: —" row* where the SpO₂ card would have been — the layout is exactly as long
as the number of cards that have something to say. Concretely:
- `Day` mode: a card renders only if that metric's `HealthDay` column for the selected date is
  non-null (or, for `HealthSession` rows, only if at least one session exists that day).
- `Range` mode: a card renders only if `aggregateHealthDays` found at least one non-null day for
  that metric anywhere in the range; a metric present on some days and absent on others still
  renders (averaged/summed over the days that had it), only a metric absent on **every** day in
  the range is hidden.
- This is independent of, and checked separately from, §6.2's ladder: a fully-granted, fully-
  working connection can still legitimately hide individual cards simply because the band never
  writes that record type (Floors, VO2 Max and similar LATER-scoped types aside — for the MVP set
  itself, e.g. a Mi Band 10 that does not report Hydration on a given day) — this is normal, not a
  degraded state, and must not be confused with the ladder's "read threw" or "ungranted" rows.
- The Nutrition card follows the same rule using its own presence check (`nutritionCalories` et al.
  all null on every day in scope ⇒ hidden), independent of every other card's state.
- A pure `visibleHealthCards(day: HealthDay?, range: HealthAggregate?): Set<HealthCardKind>`
  function (unit-tested) is the one choke point that decides this, mirroring `columnsFor` (§3.7.1)
  and `aggregateHealthDays` in being a single, testable decision point rather than a scattered set
  of `if (x != null)` checks repeated per composable.

**Degradation ladder, relocated verbatim.** §6.2's entire availability/permission ladder table
still applies, unchanged in content — only its destination changes: every row that said "Settings
→ Health shows…" now means **Beast Mode Settings** (§3.8.2's screen, extended per this section),
and every row that said "Today card hidden" / "Today card never renders" now means **the `Health`
nav tab renders its empty/ungranted/help state on the tab itself**, since there is no separate
summary card to hide — the tab **is** the summary. Concretely: `SDK_UNAVAILABLE` → the `Health` tab
shows one muted `EmptyState` (`Health Connect isn't available on this phone.`); not-yet-granted →
`EmptyState` with a `PrimaryButton` `Connect`, which is the same action Beast Mode Settings' new
group also exposes — tapping either launches the same permission flow, so a user who lands on the
tab first is never dead-ended into a screen with no way forward. Granted-but-zero-records and
partially-granted keep their exact copy from §6.2, rendered on the tab instead of a card.

**Why this reuses `WorkoutSettingsScreen`'s existing gear rather than adding a second settings
entry point on this tab.** Beast Mode already has exactly one settings affordance, established by
Round A (§3.7.4) and never varied since: the gear in the Routines landing header. Giving the Health
tab its own second gear would mean two different-looking paths to the same settings screen and two
places to keep in sync about which one is "the" way in. `onOpenSettings` on `HealthTabScreen`
exists only for the `Connect` empty-state's button to jump straight to the right `SettingsGroup`
when permissions are missing — a convenience shortcut into the one existing settings screen, not a
second door.

**Beast Mode Settings gains one new group — the entirety of Round B's user-facing settings surface,
per the user's explicit "confined to that one tab plus Beast Mode's Settings screen" instruction:**

| Row | Control | Notes |
|---|---|---|
| **`Connect Health Connect`** / **`Disconnect`** | `SettingsRow` with a trailing `PrimaryButton`/`GhostButton` depending on state | Launches or clears the permission flow. Label and control swap per §6.2's ladder — "Connect" when ungranted, a status line + "Disconnect" (opens the OS Health Connect app's own per-app revoke screen, since Daybook cannot itself revoke a Health Connect grant) when granted. |
| **Status line** | plain text row, no control | `Last updated <relative time>.` / the last-failure copy from §6.2/§6.3, always present once connected — this is C9's "background failure must be readable somewhere" requirement (§1 C9.4), and this row is that somewhere. |
| **`Refresh now`** | `GhostButton` | §6.3's manual refresh, exact copy unchanged. |
| **`Import my past data`** | `GhostButton` | §6.3's history backfill, exact copy unchanged. |
| **`Which data is shared`** | `SettingsRow` opening a read-only sheet | Lists the 12 MVP types (§6.1.8) each with a granted/not-shared indicator, mirroring §6.2's "Not shared:" list — decided here as a **sheet**, not an inline expanding list, because 12 rows inline would roughly double the settings screen's scroll length for a state most users check once; a `Sheets.kt` bottom sheet is the existing pattern for "more detail, on demand" (already used by the exercise picker's filter sheets, §3.7.3). Each row also carries a `Change what's shared` action at the sheet's bottom, deep-linking into the Health Connect app's own settings, exactly as §6.2 already specifies. |

This group sits below the existing Hevy-import group and above `Leave Beast Mode`, in its own
`SettingsGroup` titled **`Health Connect`** — five rows, the same count-and-shape discipline
§3.8.2's original "four preferences and a door" rule set for the settings screen as a whole, now
extended rather than violated: this is Round B's entire addition to that screen, no more.

**Why not a card on Today, restated as the rejection it is.** The original plan's argument for a
Today card was discovery-by-glance. That argument is explicitly overridden by the user's
instruction, not re-litigated here — Beast Mode is already the mode you deliberately enter to see
training/health context (its own long-press gesture, its own nav, its own settings), and Health
Connect data belongs entirely inside that deliberate space. A user who does not open Beast Mode
does not see health data, exactly as a user who does not open Beast Mode does not see their gym
history — parity with how Round A already treats workout data, not a new asymmetry.

### 7.5 Sync integration for health data, and the split export/import model

#### 7.5.0 What changes and what does not

**Health data syncs to the user's cloud account via the existing Firestore mechanism, unchanged in
shape.** `DATA_TABLES`, month partitioning, the D2 conflict flow, `ContentHash` — none of it
branches on "is this a health table" vs "is this a workout table" vs "is this a habit table".
**What changes is the *manual JSON export/import* surface in Daybook's main Settings, per the
user's explicit instruction to split it into two independent files.** This is a deliberate,
justified asymmetry between the two mechanisms, argued in §7.5.2.

- `DATA_TABLES` gains `"health_days"`, `"health_sessions"`; `DataTablesSyncTest` updated.
- `BackupModel.Definitions` and `DayEntry` gain no *new top-level classes* — health data joins the
  same `DayEntry` shape workout data already uses (§0.1), as one more optional, default-absent
  field:
  ```kotlin
  // in DayEntry, alongside the existing `workouts` field (§0.1)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val health: HealthDayLog? = null        // null == this day has no health data

  @Serializable
  data class HealthDayLog(
      val steps: Int? = null, val distanceMeters: Float? = null,
      val activeCalories: Float? = null, val totalCalories: Float? = null,
      val restingHeartRate: Int? = null, val avgHeartRate: Int? = null,
      val minHeartRate: Int? = null, val maxHeartRate: Int? = null,
      val sleepMinutes: Int? = null, val sleepDeepMinutes: Int? = null,
      val sleepLightMinutes: Int? = null, val sleepRemMinutes: Int? = null,
      val sleepAwakeMinutes: Int? = null,
      val sleepStartMillis: Long? = null, val sleepEndMillis: Long? = null,
      val spo2Percent: Float? = null, val weightKg: Float? = null,
      val hydrationMl: Float? = null,
      val nutritionCalories: Float? = null, val nutritionProteinGrams: Float? = null,
      val nutritionCarbsGrams: Float? = null, val nutritionFatGrams: Float? = null,
      val nutritionSourceApp: String? = null,
      val sessions: List<HealthSessionLog> = emptyList()
  )

  @Serializable
  data class HealthSessionLog(
      val id: String, val exerciseType: Int, val title: String? = null,
      val startMillis: Long, val endMillis: Long, val durationMinutes: Int,
      val activeCalories: Float? = null, val distanceMeters: Float? = null,
      val avgHeartRate: Int? = null, val sourceApp: String? = null
  )
  ```
  Nullable, so `explicitNulls = false` omits it automatically — the hash-neutrality guarantee of
  §4.2 holds identically. New `HealthDayHashTest` proves it.
- `ExportImportRepository`: the same six call sites as §4.4, extended for `health_days` /
  `health_sessions` the same way they were already extended for the workout tables. `evictMonth` is
  again the trap — it must delete that month's `health_days` and `health_sessions` rows, or evicted
  months re-push forever.
- `Definitions` is not touched by health data — health has no definitions, same as before.

**Why sync health data to the cloud at all:** the Mi Band pairs with one phone, so a second device
would otherwise show an empty Health tab; Health Connect's own history is bounded and resets on
reinstall; and the user's health context sitting next to their journal is the whole point of
putting it in Daybook rather than opening Mi Fitness.

#### 7.5.1 The split export/import model

**Two independent manual export actions, two independent manual import actions, both living in
`ui/settings/SettingsScreen.kt`'s existing `DataSettingsScreen`** (§0.1) — not a second screen, not
a Beast-Mode-side export button. This placement is deliberate: exporting/importing files is a
device-storage action, not a Beast Mode concept, and `DataSettingsScreen` is where every existing
export/import action already lives; Beast Mode Settings (§7.4) stays confined to Health-Connect-
specific settings, not general file I/O, matching the user's own framing that data-shape decisions
belong with backup, not with the mode.

**Shape.** Both files are still a `DaybookBackup` — no new root Kotlin class, no new wire-model
shape to maintain in parallel with the existing one. The split is a **filter applied at
serialisation time**, and a new discriminator field makes the two shapes self-identifying:

```kotlin
@Serializable
data class BackupMeta(
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: String,
    val appVersionName: String,
    val rangeStart: String? = null,
    val rangeEnd: String? = null,
    // New. Always present (not @EncodeDefault(NEVER)) — this is the one field whose entire job is
    // to be readable at import time before anything else is parsed, so it must never be omittable.
    // Safe to add unconditionally: ContentHash hashes `definitions + days` ONLY, never `meta`
    // (§0's existing rule), so this field cannot destabilise a single existing contentHash or
    // definitionsHash value (R1's guarantee is untouched).
    val kind: String = KIND_DAYBOOK
) {
    companion object {
        const val FORMAT_VERSION = 2
        const val KIND_DAYBOOK = "daybook"
        const val KIND_BEAST_MODE = "beast_mode"
    }
}
```

- **`exportBackup()` (existing, and its `exportRange`/`exportAllData` callers) produces the
  "Daybook" file**: `meta.kind = KIND_DAYBOOK`; `definitions.customExercises = emptyList()`,
  `definitions.routines = emptyList()`; every `DayEntry.workouts = emptyList()` and
  `DayEntry.health = null`. Everything else — habits, intake reminders, custom categories/prompts,
  habit logs, intake logs — is exactly what it already exports today. Because `customExercises`,
  `routines`, `workouts` and `health` are all `@EncodeDefault(NEVER)`-empty/absent, zeroing them
  here produces **exactly the bytes today's export already produces for a user with no Beast Mode
  data**, and for a user *with* Beast Mode data, it now correctly omits it — this is not a new code
  path, it is the existing filtering behaviour these four fields were already built for (§0.1),
  applied deliberately instead of incidentally.
- **A new `exportBeastModeBackup()` produces the "Beast Mode" file**: `meta.kind =
  KIND_BEAST_MODE`; `definitions.habits = emptyList()`, `.intakeReminders = emptyList()`,
  `.customCategories = emptyList()`, `.customPrompts = emptyList()` (kept: `.customExercises`,
  `.routines`); `days` is filtered to only the entries that have non-empty `workouts` and/or
  non-null `health` — every such `DayEntry` keeps `habitLogs = emptyList()`, `intakeLogs =
  emptyList()`. A day with neither workouts nor health data is dropped from the list entirely
  (mirroring `exportRange`'s existing clip-then-keep-only-what's-there style), so the Beast Mode
  file's `days` list is exactly the days Beast Mode ever touched, nothing more.
- **`exportRange(start, end)`** gains the same treatment on both new export functions — clip
  `full.days` to the range first, then apply the Daybook or Beast Mode filter. No new date-range UI
  is needed beyond what §7.4's aggregate range picker and `DataSettingsScreen`'s existing "Export a
  date range" already offer; the **Daybook export button exports the picked range**, the **new
  Beast Mode export button exports the same picked range** — one shared start/end date pair, two
  filtered outputs, decided this way because asking the user to pick two separate ranges for two
  files they'll usually want as a matched pair is friction with no benefit.

**Import — two independent buttons, two independent validations:**

- **`importAllData(json)` / `importRange(backup)` (existing "Import JSON" button, unchanged
  location) — the Daybook import.** Reads `meta.kind`. **Accepts `KIND_DAYBOOK` or an absent/older
  field** (every file exported before this round has no `kind` field at all, and `ignoreUnknownKeys`
  / a missing-field default both make `kind` decode to `KIND_DAYBOOK` for them) — and imports
  **everything the file actually contains**, exactly as today: if a pre-split legacy file happens to
  carry `customExercises` / `routines` / `workouts` (every full export made before this round did),
  those import too, unchanged behaviour. **Rejects `KIND_BEAST_MODE`** with a new, specific message
  — `"This looks like a Beast Mode backup. Import it from Beast Mode Settings instead."` — reusing
  `ExportImportRepository.friendlyImportError`'s idiom (§0) rather than inventing a new copy voice.
  This choice — accept old-shaped files unconditionally, reject only the new Beast-Mode-only shape —
  is what keeps every backup a user made before this round fully restorable with zero data loss,
  which matters more than making the split airtight against a file that, by construction, cannot
  exist yet for any pre-Round-B user.
- **A new import action, `importBeastModeBackup(json)`, reached from a new button inside Beast
  Mode Settings' `Health Connect` group area — specifically, from a new `Backup & data` sub-group
  in `WorkoutSettingsScreen.kt`, not from Daybook's main Settings.** This is the one asymmetry
  between export and import placement, and it is deliberate: exporting is a single, undirected
  action a user does from "the place backups live" regardless of which file they're making, but
  *importing* the Beast Mode file is meaningfully a Beast-Mode action — restoring gym history and
  health history onto a phone is something a user thinks of as "getting my Beast Mode data back",
  discovered from inside Beast Mode's own settings, mirroring where its Hevy-import row already
  lives. **Requires `meta.kind == KIND_BEAST_MODE` exactly** — an absent or `KIND_DAYBOOK` value is
  rejected with `"This looks like a Daybook backup, not a Beast Mode one. Import it from Settings →
  Backup & data instead — your Beast Mode data will be restored from it too if the file has any."`
  (the parenthetical clause matters: it tells the user the Daybook import path is not a dead end
  for their workout/health data if that's the file they picked). On success, only `workout_*` and
  `health_*` tables are touched — the transaction never references `habits`, `food_med_*`,
  `custom_categories` or `custom_prompts` tables, so a Beast Mode restore can never clobber the
  user's journal even if the file were somehow malformed into carrying journal-shaped content (a
  defensive property, not just a documentation claim: `importBeastModeBackup` is implemented as its
  own function reading only the Beast-Mode-relevant fields off the decoded `DaybookBackup`, never
  calling into `importAllData`'s full-replace code path).

**Filenames**, since the two files must be distinguishable at a glance in a downloads folder or a
share sheet: **`Daybook-backup-<yyyyMMdd-HHmmss>.json`** (unchanged from today's existing
convention — check `SettingsViewModel`'s current `shareLatestExport` naming before implementing and
keep it byte-identical for this file) and **`Daybook-BeastMode-backup-<yyyyMMdd-HHmmss>.json`** for
the new one. `meta.kind` is the authoritative shape check on import regardless of filename — the
filename is a human convenience, not a validation input — so a renamed file still imports (or is
still correctly rejected) exactly the same way.

**`DataSettingsScreen` layout change**, minimal: the existing "Export a date range" `FormGroup`
gains a second `PrimaryButton`/`GhostButton` pair — **`Export range`** (existing, now explicitly the
Daybook file) and **`Export Beast Mode data`** (new) — sharing the same start/end date fields above
them; the existing "Restore & share" group's **`Import JSON`** button is unchanged and explicitly
re-labelled **`Import Daybook JSON`** in its own row for clarity now that a second import exists
elsewhere; no new button is added to this screen for the Beast Mode import, since that lives in
Beast Mode Settings per the placement decision above. The existing fixed-height result slot (§0)
is reused for both new actions, unchanged pattern, no third UI mechanism.

#### 7.5.2 Why Firestore cloud sync stays unified while the manual JSON split does not

**Explicit call, with reasoning, per the task's requirement to decide rather than leave open.**
Cloud sync (Firestore, via `CloudSyncRepository`) **does not split** — it stays exactly one
account, one `users/{uid}` document tree, one push/pull loop, covering habits, intake, workout and
health data together, unchanged from how Round A already added workout data to it. **Only the
manual "download a JSON file" export/import surface splits, per the user's explicit instruction.**
Three reasons this is the right split, not an inconsistency:

1. **`CloudSyncRepository` has no concept of "which feature a table belongs to" today, and adding
   one would be new architecture for a problem that does not exist.** Sync is keyed on `DATA_TABLES`
   membership and month partitioning by `local_date`, full stop — Round A already proved this by
   adding six workout tables to the same sync loop that already carried habits and intake, with no
   branching on feature identity anywhere in `CloudSyncRepository`, `MonthPartitioner` or the D2
   conflict flow (§4.5's "what does NOT change"). Splitting cloud sync into two independent
   Firestore document trees per user would mean two conflict-resolution flows, two sets of
   `contentHash`/`definitionsHash` bookkeeping, and two places `SyncStateStore` has to track
   pending-push state — for a single human on one or two devices, which §2.1 already established is
   the wrong-sized solution to a coordination problem Daybook does not have.
2. **A manual JSON export is a portability/backup artifact a user actively chooses to make and hand
   to themselves** (attach to an email, drop in a cloud drive folder, keep as an archive) —
   splitting it by feature is a legitimate, low-cost product choice about *what one file means to a
   human*, matching the user's own framing that Beast Mode's data should be a separable, self-
   contained unit they can back up or restore on its own. Cloud sync is the opposite kind of
   artifact — invisible infrastructure the user never directly inspects — so a distinction that
   helps a human reading file names on their phone has no analogous benefit inside a sync protocol
   nobody looks at.
3. **Splitting cloud sync would be strictly worse for exactly the failure mode §7.5.0 already
   guards against.** If workout+health lived in a second Firestore tree, a device that only ever
   opens the main app (never Beast Mode) would still need to run a second sync loop just to keep
   that second tree's month-eviction bookkeeping correct, or risk exactly R2/R11's silent-data-loss
   class of bug in a code path that gets no manual testing because nobody who avoids Beast Mode
   would ever notice it broke. One sync loop, exercised by every signed-in device regardless of
   which features they use, is the more-tested, more-honest design.

**What this means concretely for an implementer:** `CloudSyncRepository.DATA_TABLES`,
`MonthPartitioner`, `ContentHash`, `SyncStateStore` and the D2 conflict flow need zero changes
beyond the two new table names (§7.5.0) — health data rides the exact same push/pull/conflict
machinery workout data already rides. The split lives entirely inside
`data/ExportImportRepository.kt`'s two new export functions and one new import function (§7.5.1),
and nowhere else.

### 7.6 Round B phase list

| Phase | Work | Gate |
|---|---|---|
| B0 | Add the pinned dependency (§5.1) + the 12 manifest permissions of §6.1.8 in one request, none of §6.1.5's seven cycle-tracking permissions, `<queries>`, rationale activity. Build and install on the real phone before writing any feature code — confirm the Guava/R8 interaction and the APK size. First task: the API-surface compile check (§5.1) — reference `HealthConnectFeatures`, `PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND`, `PERMISSION_READ_HEALTH_DATA_HISTORY`, and every one of the 12 MVP record classes, and record which resolve. | `assembleRelease` green, app launches, a written list of which symbols resolved |
| B1 | `HealthConnectAvailability`, `HealthPermissions`, rationale activity + privacy screen. No UI beyond the rationale activity itself in this phase — the Connect button lands in Beast Mode Settings in B6, per §7.4's placement. Verify on-device (a throwaway debug entry point is fine for this phase only) that the OS consent sheet appears and that the two optional permission strings are accepted. | permission grant works end-to-end |
| B2 | `HealthModel.kt`, `HealthDao`, `MIGRATION_23_24`, `AppDatabase` v24, DI, `24.json`, `MigrationTest.migrate23To24` (including the new `health_tab_last_mode` column and the not-null-value-column guard extended to every `HealthDay` metric, per R18) | tests green |
| B3 | `HealthConnectReader` (aggregates + sessions + changes token), `HealthSyncStateStore`, `HealthRepository`; pure unit tests for the record→entity mappers, the changes-token fallback decision, and the new `aggregateHealthDays` function (§7.4's `Range` mode) | tests green |
| B4 | Cadence: on-resume pull throttled to once per 15 minutes, the daily call folded into the existing `WindowRefreshWorker`, a manual "Refresh now". No `HealthPullWorker`, no new periodic job (C7). "Import my past data": the automatic window is the last 30 days; this button requests `PERMISSION_READ_HEALTH_DATA_HISTORY` and pulls up to one year, and is the only thing that ever asks for that permission. | manual pass with the actual band |
| B5 | Sync: `DayEntry.health`, `DATA_TABLES`, the six `ExportImportRepository` call sites extended for the two new tables, `HealthDayHashTest`, eviction (§7.5.0) | tests green — before any UI |
| B5a | **The split export/import** (§7.5.1): `BackupMeta.kind`, `exportBeastModeBackup()`, `importBeastModeBackup(json)`, the Daybook-import acceptance/rejection rule keyed on `kind`, filename convention, `BackupModelTest` extended with a round-trip of each new function and a case proving a pre-split legacy file (no `kind` field) still imports its embedded workout/health content via the Daybook path | tests green: a Daybook export contains zero workout/health bytes; a Beast Mode export contains zero habit/intake bytes; a legacy full export still round-trips through the Daybook import unchanged |
| B6 | `HealthTabScreen` + `HealthTabViewModel` repurposing `WorkoutRoutes.LIBRARY`'s nav destination (§7.4) — the `Day`/`Range` `SegmentedControl`, the `WeekStrip` call for `Day` mode, `HealthAggregateSheet` + `aggregateHealthDays` for `Range` mode, the metric cards including the separate `Nutrition` / "From …" card, **the pure `visibleHealthCards` function and the per-metric hide rule it enforces (§7.4) — no empty/dashed card ever renders**, the relocated §6.2 degradation ladder rendered on the tab itself; the new `Health Connect` `SettingsGroup` in `WorkoutSettingsScreen.kt` (Connect/Disconnect, status line, Refresh now, Import my past data, Which data is shared sheet — no `Add more health data` row, no cycle-tracking opt-in row); `DaybookIcons.Heart`; the nav item relabel from `Exercises`/`Category` to `Health`/`Heart`; every row of §6.2's ladder including its exact copy, and §6.3's Refresh-now / Import-past-data outcome strings | manual pass, including with Health Connect uninstalled, with permissions fully denied, with permissions PARTIALLY granted, and with some MVP types granted but genuinely never written by the band (confirm those cards are absent, not dashed) — partial grant and partial data are the most likely real-world states and the easiest to leave untested; confirm the exercise picker (`PICK` mode) still works unchanged from the live session and routine editor even though its `BROWSE`-mode nav entry is gone |
| B7 | Full `./gradlew test` + `assembleRelease`; signed APK | all tests green |

---
# ROUND C — Universal Activity Tracker (backlog, not started, not scheduled)

**Status: this is a backlog map, not a plan.** Nothing here is scheduled, estimated, or approved.
No part of Round C may be built as part of Round 0, A or B. It exists because the external PRD is
a legitimate product vision that deserves recording rather than discarding. **Sequencing: strictly
after Round A and Round B.** Round A must be shipped, used for real workouts, and found wanting
before any of this is worth building.

## C.1 What Round C is

The PRD's central claim, and it is a good one: strength training, yoga, running, sports drills and
arbitrary custom activities are the same underlying model at different settings —

```
ACTIVITY → ROUTINE / PROGRAM → WORKOUT → PERFORMANCE / PROGRESS
```

Round A implements the third and fourth boxes for one activity type (gym). Round C generalises the
first two and widens the third.

## C.2 The schema shift, and what it costs

Round A's concrete tables become a generic core:

| PRD entity | Daybook table | Relationship to Round A |
|---|---|---|
| `activity` | `activities` | `exercises` renamed and widened — gains `category` (Strength/Cardio/Yoga/Mobility/Sport/Combat/Skill/Recovery/Custom), `aliases`, `instructions`, `tags` |
| `activity_metric` | `activity_metrics` | NEW. One row per metric an activity tracks. Replaces `trackingMode` per §2.2's migration function |
| `workout` | `workout_sessions` | unchanged |
| `workout_block` | `workout_exercises` | unchanged — already the right shape |
| `workout_item` | `workout_sets` minus its value columns | the row survives; `reps`/`weight_kg`/`duration_seconds`/`distance_meters`/`rpe` move out |
| `metric_value` | `metric_values` | NEW. One row per (item, metric) pair |

**The honest cost:** two Room migrations at least, and the second is **not additive** — it moves
data out of `workout_sets`' columns into `metric_values` rows, which needs an explicit,
separate amendment to C2 or a strategy that leaves the old columns in place, unused, forever. The
wire model (§4.2) changes shape, and the hash-neutrality guarantee must be re-proved for the new
shape against every existing user's cloud data — the single riskiest part of Round C, riskier than
anything in Rounds 0/A/B. Every query in §3.4 is rewritten: `bestSetForExercise`'s `ORDER BY
weight_kg DESC` becomes a join-and-pivot over `metric_values`. The live session UI becomes
dynamic: §3.7.1's `columnsFor(trackingMode)` becomes "render an editor per metric definition,
typed at runtime".

## C.3 What Round C would add, in PRD priority order

**C.3.1 Custom activities and custom metrics** — the five-screen "+ Create Activity" builder:
name → category → what to track (including "create custom metric") → record rules → progression
rules. This is the feature that makes the PRD's claim true, and the reason to do Round C at all.

**C.3.2 Routines, generalised.** The basic half of this has already moved into Round A on the
user's instruction — `workout_routines` + `workout_routine_exercises` (§3.2.1), `RoutineEditScreen`
with drag-reorder (§3.7.5), and `startSessionFromRoutine` (§3.4). What is left in Round C:
routines generalised to non-gym activities (needs C.3.1's activity model), routine-level notes and
metadata beyond a name, routine folders/tagging, and anything that arranges routines **over
time** (C.3.3). **Routine import — from Hevy or elsewhere — is a separate, unstarted feature**, not
part of Round A and not part of Round C's scope as written here; deferred pending its own
conversation. Round A leaves it a `source` column and nothing else.

**C.3.3 Programs, periodization, deloads** — routines arranged over weeks. Includes the PRD's good
instinct: never silently manipulate someone's program — always show the reason.

**C.3.4 Progression engines** — double progression, RPE/RIR, percentage-based, and the
generalisation that progression is not always "more weight" (plank → longer, running → faster,
basketball → more accurate). Depends entirely on C.3.1's metric definitions.

**C.3.5 Substitution** — planned activity vs actual activity vs reason, preserving historical
truth. Cheap only after Routines exist.

**C.3.6 Generalised PRs and analytics** — Round A's `isPersonalRecord` becomes per-metric with a
per-activity rule (highest / lowest / fastest / longest / best percentage).

**C.3.7 Training load and a fatigue dashboard** — and the PRD's best single piece of UX advice,
worth adopting verbatim if this is ever built: never show a mysterious "Fitness score: 73"; say
*"Training load increased 24% from your previous 4-week average."* And never say "you are
overtrained" — an unjustified medical claim. A fatigue dashboard is the one feature that would
justify Round B collecting `HeartRateVariabilityRmssdRecord` (§6.1.2).

**C.3.8 Richer streaks and rest-day intelligence** — multiple streak models (training-day,
weekly-goal, per-activity, distance-goal), and the rule that a planned rest day must not break a
consistency streak. Daybook already does the hard part correctly: its streaks are derived from
stored occurrence rows, never from a cached `current_streak` integer.

**C.3.9 Charts.** The trend button opens a list in Round A; the chart lands here, since a generic
metrics engine needs generic charting anyway.

## C.4 What stays rejected even in Round C

- **Everything in §2.1** — no backend, no Postgres, no mutation queue. Round C changes the
  *schema*, not the *sync architecture*. All of it still rides Room + Firestore.
- **Social features** — no feed, no followers, no leaderboards, ever.
- **AI coaching / AI-generated programs** — not at all without a separate conversation.
- **Wearable/Wear OS app** — a second app module and a second distribution channel.
- **A bundled media library of exercise GIFs or videos** — already resolved differently in Round A
  (§3.3): static, licensed, bundled illustrations, not a video library, and Round C does not
  change that.
- **GPS route tracking** — needs a maps dependency and a location permission; a separate decision,
  not a Round C freebie.

## C.5 One Round C idea already pulled forward

The PRD's four-level notes system (activity note, routine note, session note, set note) is mostly
already in Round A's schema (`Exercise.notes`, `WorkoutSession.notes`, `WorkoutSet.notes`,
`WorkoutExercise.notes`). The genuinely missing piece — a pinned, always-visible activity note
shown every time you do that exercise — is already **in Round A** (§3.7.1, phase A6b), at no
schema cost, since `Exercise.notes` already exists. This is the only Round C item pulled into
Round A; nothing else in §C.3 follows it.

---

## 8. Risk register

| # | Risk | Mitigation |
|---|---|---|
| R1 | A new synced field changes `contentHash` for users with no workout/health data → mass re-push, possible D2 conflict prompt | `@EncodeDefault(NEVER)` / nullable-with-`explicitNulls=false`, plus a dedicated byte-identity unit test per field (§4.2). Non-negotiable. |
| R2 | `evictMonth` forgets the new tables → infinite month re-push or partial cloud overwrite | §4.4 item 6 / §7.5. An explicit test that evicting a month with workouts leaves `changedMonths` empty. |
| R3 | An old build on a second device silently strips the new fields | Update every signed-in device in the same sitting (§4.3). |
| R4 | `connect-client` alpha08 rejects the two optional permission strings | Detect at Phase B1 on-device; fall back to foreground-only + 30-day window. Feature still ships. |
| R5 | Guava (via connect-client) breaks R8 / inflates the APK | Phase B0 builds release first, before any feature code. Add ProGuard keeps if needed. |
| R6 | *(withdrawn — no mechanism)* | Beast Mode is not a bottom-nav tab, so there is no fourth nav label to clip at 360dp. |
| R7 | Mi Fitness simply is not writing to Health Connect | First-class empty state with exact instructions (§6.2), not a blank card. |
| R8 | The daily health read becomes a battery line-item | Fold into the existing `WindowRefreshWorker`; use the changes token so the steady-state read is near-empty (C7). |
| R9 | Room identity-hash mismatch from a `DEFAULT` that does not byte-match `@ColumnInfo` | Stated in §3.5. The migration test catches it. |
| R10 | `workout_sets` and `workout_exercises` bulk reads blow SQLite's 999-variable ceiling | Chunk at the existing `SQLITE_MAX_VARS = 900`; `ChunkedDeleteTest` is the precedent. Both tables — they are read by the same session-id lists. |
| R11 | A Hevy import writes into an evicted past month and the next push overwrites that month's cloud habits/intake with workouts only. The worst outcome, and it is silent. | §3.9.7 — hydrate every touched month via the existing `beginRangeExport()` path before writing anything, abort with nothing written if any month can't be reached, skip the whole step when signed out. Plus a test that importing into a month with habit data leaves it intact. |
| R12 | `DATA_TABLES` is not updated for the six new tables, so workout edits never mark a push pending and the gym log silently never reaches the account. | §4.1 — `DataTablesSyncTest` is one-directional and will NOT catch this. A deliberate step in A4 plus a manual "log a set, background the app, confirm a push" check. |
| R13 | The Hevy name matcher silently merges two different exercises, corrupting PREVIOUS and inventing/destroying personal records with nothing visible to the user. | §3.9.4 — normalise + alias table only, no fuzzy/edit-distance matching; unmatched names become visible, renameable custom rows badged "Imported". |
| R14 | The rest timer creeps back toward a notification / foreground service / exact alarm and re-opens the battery regression. | §3.1 and §3.7.2 are explicit: timestamp-derived, `LaunchedEffect`-ticked, dies with the composition. The forbidden-API list is a hard gate — a build that needs any of it must stop and re-ask, not improvise. |
| R15 | A CSV date parsed with the device's default locale fails on every row for a non-English phone. | §3.9.3 — `Locale.ENGLISH` explicitly, ISO-8601 attempted first, unparseable rows skipped and counted; `HevyDateParseTest` includes a French-default-locale case. |
| R16 | A future round re-proposes the PRD's Postgres/Supabase/backend-API architecture. The cost is not a wasted discussion — it is a second sync system half-built alongside a working one. | §2.1 is written as a standing rejection with named reasons, not a passing remark, and lists the specific artefacts (`sync_queue` tables, `operation_id` columns, HTTP clients) that must never appear. |
| R17 | Round C's vision leaks into Round A's scope — a generic metrics engine, Routines or Programs get "just started" mid-round, Round A never ships. | Round C is a separate, lettered, explicitly unscheduled section. §2.2's Ri1–Ri3 keep the door open so the choice stays reversible, and R18's not-null migration assertion mechanically enforces Ri3. The one Round C item deliberately pulled forward is the pinned exercise note (§C.5); nothing else in §C.3 may follow it. |
| R18 | Ri3 is violated — a `NOT NULL DEFAULT 0` is added to a value column (`weight_kg`, `reps`, `duration_seconds`, `distance_meters`, the five `workout_routine_exercises.target_*` columns, or any of §7.1's nine new health columns) because "a nullable float is annoying in Kotlin". This silently destroys the difference between zero and no-data, breaks the blank-is-not-zero rule, corrupts PR detection and volume totals, and permanently forecloses the generic Activity/Metric engine. | §2.2, §3.2, §3.5 and §7.1 all specify nullable. A migration test asserts no value column is `NOT NULL` — the one Ri that is cheap to guard automatically, and the one most likely to be broken by an implementer optimising for convenience. |
| R19 | A failure is caught, logged and never shown — the single most likely way this plan degrades in practice, because `runCatching { … }.getOrNull()` is the path of least resistance and the app has no toast/snackbar habit to fall into. | C9, plus §0's exactly-two approved UI patterns, plus exact final copy for every branch throughout §2.4 / §3.9.9 / §6.2 / §6.3 — nothing left to invent. Round-review checklist item: grep the diff for `runCatching` and check every failure branch reaches the UI. |
| R20 | The 12-permission Health Connect consent sheet suppresses the grant rate, and the user ends up with less data than a smaller request would have delivered. | Real, and not fully mitigable. The mitigation is §6.2's "Partially granted" ladder row, which makes a partial grant a normal, well-rendered state rather than a broken one. That row is load-bearing — B6's manual pass must exercise it explicitly. |
| R21 | The feature is invisible. A long-press is not discoverable, the one-time coach-mark is seen once and forgotten, and a user who never performs the gesture concludes Daybook has no workout feature at all. This also covers the accessibility case: TalkBack, switch access and motor impairment all make the gesture harder or impossible. | All four mitigations ship, none conditional: the coach-mark + icon dot (§3.6.3), the unconditional Settings → Workout → `Open Workout` row (§3.8, behind no toggle), the `Beast Mode` row on Today (§3.6.4), and `onLongClickLabel = "Start a workout"` so TalkBack and switch access expose the gesture as a named action. Two visible doors plus a named accessibility action means the feature is reachable without ever performing the gesture. |
| R22 | The long-press fires when the user meant to tap, or the ramp animation flickers on every ordinary tap of Today. | `combinedClickable` uses the platform's own tap/long-press discrimination (§3.6.1); the nav item is not inside a scrollable, so there is no scroll-vs-hold ambiguity. The ramp's 120 ms dead zone is specifically there so a tap never animates. Manual check at A5: tap Today twenty times in a row from each of the three tabs and confirm nothing ever flashes or navigates wrongly. |
| R23 | A later round quietly re-adds `"workout"` to `NavConfig.ALL_ROUTES`. | `ui/NavConfigTest.kt` is left asserting three routes on purpose and becomes the tripwire. Beast Mode having its own pill nav (§3.6.7) makes this risk *more* live, not less — "it already has a nav bar" reads like an invitation. It does not: Beast Mode's nav is a plain `listOf(...)` in `MainActivity`, not user-configurable, never touches `nav_tabs`. |
| R24 | The word "routines" is already taken — by Habits (`NavConfig.ALL_ROUTES` includes `"routines"`, and `ui/routines/RoutinesScreen.kt` is the Habits screen). An implementer designing Beast Mode's routine landing will reach for `"routines"` and `RoutinesScreen.kt` by reflex, colliding with the Habits tab. | §0 records the collision as a verified fact and §3.6.6 fixes the names: the route is `"workout"`, the file is `WorkoutHomeScreen.kt`, and every Beast Mode route id is a `WorkoutRoutes` constant rather than a literal. The user-facing label is still "Routines" — the clash is internal only. |
| R25 | The Beast Mode accent tints the screens but not the nav bar, because `FloatingPillNav` is drawn by `DaybookScaffold` — outside the `NavHost` — so a `CompositionLocalProvider` inside each workout route never reaches it. Result: a Coral screen sitting on a Lavender pill. | §3.8.3 moves the provider around the `DaybookScaffold` call, gated on `backStackRoute in WorkoutRoutes.ALL` — a shared constant, not an inline `setOf(...)`. Manual check at A5: open every one of the ten workout routes and confirm the pill nav, the header and the primary button are all the same colour. |
| R26 | The way *out* of Beast Mode becomes as invisible as the way in — a press-and-hold has the identical accessibility problem R21 raises for the entrance. | Three independent floors, none a gesture: system back leaves from the landing unconditionally; `onLongClickLabel = "Leave Beast Mode"` puts the hold in TalkBack's and switch access's actions menu as a named action; the `Leave Beast Mode` row at the bottom of Beast Mode's own settings (§3.8.2), the exact counterpart of `Open Workout` in main Settings. All three are mandatory — dropping any one re-opens this risk in full. |
| R27 | RepDB's images balloon the APK (~25 MB added) without the user seeing the cost stated plainly, or a future catalog swap silently changes `builtin:` ids and orphans historical `workout_sets` rows. | §3.3.5 states the size cost in one place, not buried. `builtin:` ids are RepDB's own slugs and are treated with the same Ri1 stability rule as the original hand-picked ones — never renamed once shipped, regardless of what a newer `repdb.json` calls the same exercise. A catalog update adds rows; it does not rename or remove existing ones. |
| R28 | An implementer reads §5–§7's older cross-references literally and rebuilds the dropped Today card / main-Settings Health row / stacked `"health"` route, because those phrases still appear inside quoted ladder copy (§6.2) that this revision deliberately left byte-for-byte unchanged. | §7.4 states the superseding decision once, in full, at the top of the section, and the ladder table itself carries an explicit redirection note immediately above it mapping every "Today card" / "Settings → Health" phrase in its rows to their new meaning. §0.1 and §7.3 also state plainly that no `ui/health/` package, no `HealthSummaryCard.kt` and no `ui/settings/HealthSettingsScreen.kt` are ever built. |
| R29 | The split export/import (§7.5.1) is implemented as two genuinely separate wire-model class hierarchies instead of one filtered `DaybookBackup`, so a future field added to `DayEntry` has to be remembered in two places and silently drifts — the same class of bug §4.4/§7.5.0 already exists to prevent for the *table* list. | §7.5.1 is explicit that both files stay the same `DaybookBackup`/`DayEntry`/`Definitions` Kotlin shapes, distinguished only by `BackupMeta.kind` and by which fields the two export functions zero out — one shape, two filtered views, not two shapes. |
| R30 | A user imports a Beast Mode file through the main "Import JSON" button (or vice versa) and gets a confusing silent no-op, or worse, a partial import that looks successful but dropped half the file. | §7.5.1's two import functions validate `meta.kind` explicitly and reject the wrong shape with a specific, named-destination error message (`ExportImportRepository.friendlyImportError`'s idiom) rather than silently importing nothing or partially importing — satisfying C9 for this specific new failure mode. |
| R31 | The Health tab's `Range`/aggregate mode is implemented as one more ad-hoc per-card averaging decision inside the composable, so a future metric added to `HealthDay` has no defined aggregate behaviour and an implementer guesses. | §7.4 requires a single pure, unit-tested `aggregateHealthDays` function as the one place that decides mean-vs-sum per metric — the same "one choke point" discipline Ri2 already applies to `trackingMode`. |
| R32 | A card for a metric with no data renders as an empty shell or a dash instead of being hidden — easy to ship by accident, since every other list-of-cards screen in the app (e.g. §7.4's own Nutrition card copying the old "From …" pattern) is written assuming its data exists. | §7.4's explicit per-metric hide rule, checked through a single pure `visibleHealthCards` function rather than scattered per-composable null checks — the same choke-point discipline as R31. Manual pass at B6 must include a day/range with several MVP types granted but genuinely empty (e.g. no Hydration ever written) and confirm no gap or dash renders. |

---

## 9. Key product decisions — quick reference

Every decision below is final. This is a lookup table, not a discussion.

| Topic | Decision |
|---|---|
| Health library version | Pin `health-connect-client:1.1.0-alpha08` (§5.1). Toolchain upgrade is a separate later round. |
| Band-data refresh cadence | On-resume pull throttled to once per 15 min + the existing daily `WindowRefreshWorker` pass + a manual "Refresh now". No new worker. |
| First-connect history window | Last 30 days automatically; "Import my past data" backfills up to 365 days. |
| Where band data appears | Inside Beast Mode only — the repurposed third nav slot (`WorkoutRoutes.LIBRARY`, relabelled `Health`), with a day calendar (the same `WeekStrip` Today uses) and an aggregate/range mode (§7.4). No Today card, no main-Settings Health entry, no non-Beast-Mode route of any kind. |
| Health Connect settings placement | Entirely inside Beast Mode Settings' new `Health Connect` group — connect/disconnect, status, refresh now, import past data, which types are shared. Nothing in Daybook's main Settings (§7.4). |
| Sync band data to the cloud? | Yes, via the unchanged Firestore/`DATA_TABLES` mechanism (§7.5.0) — cloud sync stays a single unified account sync, not split by feature (§7.5.2). Manual JSON export is a separate question, answered next. |
| Manual JSON export/import | Split into two independent files — "Daybook" (habits/intake, no workout/health) and "Beast Mode" (workout + health together), distinguished by `BackupMeta.kind`, both exported from `DataSettingsScreen`; Daybook import accepts old unsplit files too, Beast Mode import is reached from Beast Mode Settings (§7.5.1). |
| Write back to Health Connect | Never. Read-only; no write permission is ever declared. |
| Band workouts inside Beast Mode's Routines/History | No. Band-recorded sessions live on the `Health` tab only, in their own `health_sessions` table, never merged with `workout_sessions` (§6.4). |
| How you enter Beast Mode | Long-press "Today" in the bottom nav; tap still opens Today. Full-screen mode with its own three-item nav. |
| Built-in exercise catalog | RepDB's free tier, ≈525 usable exercises after excluding `stretching`-category rows, with real illustrations (§3.3). |
| Rest timer | Yes, in-app only. No notification, no service, no alarm. Alerts-while-closed is a separate future round. |
| Units + Beast Mode colour | kg default with a kg/lb switch (on Beast Mode's own settings screen); accent = CORAL. |
| Database + cloud-format sign-off | Given — `MIGRATION_21_22` and the `BackupModel.kt`/`ContentHash` additions are approved (§1 C4). |
| Order of work | Workout (Round A) first, then Health (Round B). |
| Exercise artwork | Ships — RepDB illustrations, muscle diagrams and equipment icons in the picker (§3.3, §3.7.3), with required attribution (§3.3.5, §3.8.1). Everywhere else in the app stays icon-and-tint only. |
| Exercise taxonomy | Two axes — ~20 muscle groups and ~9 equipment types, two filters. RepDB's finer taxonomy is algorithmically projected onto these, not adopted wholesale (§3.3.2). |
| The trend button | A list in Round A (every past set, newest first, PRs marked). The chart is Round C (§C.3.9). |
| "Import from Hevy" placement | Both — Settings → Backup & data, and the empty History screen inside Beast Mode. |
| Unrecognised Hevy exercises | Become custom exercises with Hevy's exact name, badged "Imported". No fuzzy matching of any kind. |
| Supersets from Hevy | Store `superset_id`, show nothing. Lossless import, no superset UI in Round A. |
| Re-importing the same file | Dedupe on exact start + end time; duplicates are skipped, not merged, and counted in the summary. |
| Importing into evicted months | Hydrate every touched month first; abort with nothing written if any month can't be reached. |
| How ambitious is Round A? | The concrete gym logger of §3. The universal Activity/Metric engine stays in Round C. Ri1–Ri3 (§2.2) are binding. |
| Baseline Profiles | Skipped in Round A. Belongs to a future toolchain round. |
| Cycle-tracking data | Out entirely. Not requested, not in the manifest, no opt-in row. Adding it later is purely additive. |
| Blood pressure / glucose | Out of Round B. Nothing in the user's setup writes them. |
| Nutrition from other apps | Read it, show it in its own card labelled with the source app. Never merged into Intake. |
| 12 permissions or 6+6 | All twelve in one request. No `Add more health data` row. |
| Pinned per-exercise note | Yes — in Round A, phase A6b. No schema cost. |
| Version numbers | 25 / 0.5.7 (shipped) → 26 / 0.6 (Round A, shipped) → 34 / 0.6.2 (Beast Mode bug-fix pass, shipped, current baseline) → 35 / 0.6.5 (Round B, this revision — §2). |
| Round B's migration | `MIGRATION_23_24`, DB v23 → v24 — `MIGRATION_22_23` was already consumed by a shipped, unrelated Beast-Mode-accent-settings migration (§0.1). |
| C5 inside Beast Mode | Superseded by a shipped carve-out: Beast Mode draws from its own `BeastTheme.kt`/`BeastComponents.kt` (own accent palette, own ground, own numeral treatment), not `DaybookColors`/`AppShapes`/`DaybookText`. C5 still governs the main app (§1). |
| The old "Exercises" nav tab | Repurposed, not removed — same `WorkoutRoutes.LIBRARY` slot now shows the `Health` tab. The exercise browser (`AddExerciseScreen` `BROWSE` mode) survives only in `PICK` mode, reached from a session or routine editor (§7.4). |
| Per-metric cards with no data | Hidden entirely, not shown empty/dashed — checked per card, independent of §6.2's whole-page ladder, in both `Day` and `Range` mode (§7.4's per-metric hide rule). |
| Visible way into Beast Mode | The hold + a Settings row + a Today row. All three ship. |
| One-time tip + the dot | Keep both. Neither can ever come back once dismissed/used. |
| Size of Routines | A list you start from, no programme layer. Tap starts it immediately; deleting a routine never deletes its workouts. |
| The `×` for leaving Beast Mode | No `×`. The three exits of §3.6.5 stand. |

---

## 10. Notes for the implementing agent

- **Read §1 (constraints) and §9 (decisions) before touching anything.** Every product question
  is answered; there is nothing left to stop on.
- **§2.1 is a standing rejection, not a footnote.** If you are about to add a sync queue, an
  `operation_id`, a Postgres schema or an HTTP client, you have misread this plan.
- **§2.2's Ri1–Ri3 are binding on every phase of Round A.** `trackingMode` values are permanent and
  never branched on outside `columnsFor` and §3.4's mappers; no value column ever gets a non-null
  default. R18's migration test is how Ri3 is enforced, and it is not optional.
- **§3.6 before writing a single line of navigation code.** Workout mode is not a tab. If you are
  about to touch `NavConfig.ALL_ROUTES`, `NavConfigTest`, `app_settings.nav_tabs`,
  `NavigationSettingsScreen.kt`, the `HorizontalPager`'s `when`, or add a fourth `NavItemSpec` to
  Daybook's own nav, you have misread this plan — stop and re-read §3.6.0.
- **§3.3 before touching the exercise catalog.** The catalog is a bundled RepDB asset, parsed and
  projected through the derivation functions in §3.3.2 — not hand-typed Kotlin, not Room-seeded
  rows. Adding an exercise later means shipping a newer `repdb.json`, not writing a line of code.
  `builtin:` ids are permanent once shipped (Ri1's rule, applied to RepDB's own slugs).
- **C9 — no error is silently swallowed.** There are exactly two approved UI patterns (§0) and
  every failure string in this document is already written out. You should not have to invent a
  single user-facing sentence — if you find yourself writing one, that is a gap in the plan: stop
  and ask rather than improvising.
- **Three named traps, each silent if missed:** `DATA_TABLES` has no test guarding the *new*-table
  direction (§4.1 / R12); the Hevy import must hydrate evicted months before writing (§3.9.7 /
  R11); the rest timer's forbidden-API list (§3.7.2 / R14) is a hard stop, not a preference.
- **Phase A4 / B5 (sync) must be green before any UI work in that round.** The UI is the easy
  part; the sync integration is where data is lost.
- **The Hevy sample exports live at `/home/abhiram/Downloads/workout_data.csv`** (34 rows, the
  hand-checked reference) **and `/home/abhiram/Downloads/workout_datasum).csv`** (4,734 rows, the
  user's real export, used to validate catalog coverage and exercise matching at scale). Use both
  in `HevyCsvParserTest`, not a hand-written approximation.
- **The RepDB asset pack lives at `/home/abhiram/Downloads/Daybook-Exercise-Assets/repdb-free-USABLE/`**
  — `free.en.json` plus `images/flat/`, `images/muscles/`, `images/equipment/`. This is the source
  for `app/src/main/assets/exercises/` (§3.3.3). The sibling folders
  `repdb-premium-sample-NOT-LICENSED/` and `vital-animations-UNLICENSED/` in that same directory
  must **not** be bundled — neither is cleared for shipping (one is a paid-tier sales sample, the
  other has no confirmed licence).
- Every new pure decision function gets a unit test. That is this repo's culture and the reason it
  has 87 of them.
- Match the existing comment style: every non-obvious line carries a `// <round> (<id>):` comment
  explaining *why*, not *what*. Future rounds read these.
- Follow `HOW_TO_PUSH_UPDATES.md` for the `versionCode` bump so testers' installed apps detect the
  new build. **Do not commit or push** (C1) — hand back a signed release APK named in the repo's
  existing convention. Round A is `versionCode` **26** / `versionName` **"0.6"**
  (`Daybook-v0.6-workout-release.apk`); Round B is **27** / **"0.6.1"**. The baseline is build 25 /
  0.5.7, which Round 0 already shipped.
- Write a `HEALTH_AND_WORKOUT_PROGRESS.md` checkpoint every 2–3 phases, in the style of
  `UX_REFINEMENT_PROGRESS.md`, so the round is resumable.

---

## 11. Health UI revamp — post-launch pass (user-requested, PLAN ONLY, not yet implemented)

Written after Round B actually shipped and the user used the real `HealthTabScreen` on-device.
Three concrete complaints, from two screenshots of the shipped grid and one screenshot of Mi
Fitness's "Stress" detail screen (an inspiration reference, not a spec to copy verbatim — §11.2
explains where it diverges and why). Nothing in this section is implemented; it is the plan for
the next pass over `ui/workout/health/`.

### 11.1 Card size must not vary

**Symptom:** in the shipped 2-column grid (`HealthTabScreen.kt`'s `HealthContent`), each
`MetricCard` sizes to its own content — a card with two `MetricRow`s (Calories: Active + Total) is
taller than a card with one (Oxygen: SpO₂ only), so paired cards in the same grid row don't line
up and the whole page reads uneven, unlike the fixed-size tiles in the Mi Fitness reference.

**Decision:** every two-column metric tile (Steps, Calories, Heart rate, Sleep, Oxygen, Weight,
Hydration — everything except the full-span Nutrition card and session rows) gets one fixed
height constant, applied via `Modifier.height(...)` on the `SoftCard` itself rather than left to
wrap content. Width is already uniform for free (`GridCells.Fixed(2)` sizes both columns equally)
— the fixed height is the only real gap, but pin both explicitly on the same constant so a future
card with three rows of content can never re-introduce the mismatch by accident.

- New constant, e.g. `private val HealthTileHeight = 148.dp` (picked to fit: icon-badge + title
  row, two `MetricRow`s, comfortable padding — measure against the tallest existing card, Calories,
  once this is built, and adjust the one constant rather than hand-tuning per card).
- A card with only one `MetricRow` (Oxygen, Weight, Hydration) keeps the second row's vertical
  space as blank space (content top-aligned in the fixed-height card), not stretched or centered —
  matches how the reference screenshot's shorter cards still sit in a same-height tile.
- The per-metric hide rule (R32, §7.4) is unaffected — a card still either renders at the fixed
  size or doesn't render at all; there is no partial/collapsed state.

### 11.2 A real detail screen with a trend graph, not a rows-only sheet

**What the user asked for:** something closer to the Mi Fitness "Stress" screen — a back-arrow
header, a D/W/M range switcher, a big headline number, and a plotted graph — in place of the
current `HealthDetailSheet` bottom sheet, which is text rows only.

**What the reference screenshot actually shows, and why Daybook can't draw the same chart
honestly:** Mi Fitness's chart plots individual timestamped samples across one day (dots at
13:20, 13:37, …). Daybook's Health Connect reads are deliberately aggregate-only — `HeartRateRecord`
etc. are read via `AggregateRequest` (avg/min/max), never as raw per-sample records (§6.1.9's
"Never, at any point, for any version" list, and R31/R32's whole design). `HealthDay` stores one
row per calendar day, not one row per sample. Building the screenshot's literal chart would mean
reversing that rule — reading and storing every raw sample — which is a real battery-cost and
schema decision, not a UI tweak, and is called out separately below rather than silently assumed.

**Recommended shape — Option A, a trend-across-days chart (no schema change, no new Health
Connect reads, ships now):**

- Replace `HealthDetailSheet`'s bottom sheet with a real stacked screen,
  `ui/workout/health/HealthMetricDetailScreen.kt`, pushed via a new route,
  `WorkoutRoutes.HEALTH_METRIC_DETAIL = "health_metric_detail/{kind}"` (same
  `WorkoutRoutes.detail(...)`-style helper function pattern already used for `SESSION`/`DETAIL`),
  reached by tapping a card instead of opening a sheet. `HealthSessionDetailSheet` (band workout
  sessions) is untouched — a session is one point-in-time event, not a trending metric, so it
  keeps its current simple sheet.
- Screen layout, top to bottom: a back-arrow `ScreenHeader`-style bar with the metric's title; a
  `D` / `W` / `M` `SegmentedControl` (reusing the exact sliding-filled-pill component and visual
  language already in `ui/components/SegmentedControl.kt` — no new control); a headline number in
  `BeastText.BigNumber` with its date/time context underneath, mirroring the reference's "37
  Mild / 13:37" treatment; the trend chart; the same supporting rows `HealthDetailSheet` already
  renders today (avg/min/max, sleep stages, macro split, the SpO₂/weight honesty captions) below
  the chart, unchanged.
- **`D`** plots nothing new — a single day has one aggregate value, so `D` mode is the existing
  headline number with no chart, same information as today's sheet.
- **`W`** and **`M`** plot the metric's daily value across 7 or 30 calendar days —
  `HealthDao.observeDaysInRange` already returns exactly this list (it's what `Range` mode's
  aggregate already reads). One point per day, so a week is 7 points and a month is ~30 — well
  within what a small line/bar chart can render legibly without gridline crowding.
- **New chart component:** nothing in the codebase charts anything yet (Round A explicitly
  deferred progression charts to Round C for the same reason — "new visual vocabulary worth doing
  properly rather than rushed," §3.1's deferred list). This is small enough (single series, ≤31
  points, no zoom/pan/tooltip) not to justify a third-party charting dependency — build one
  reusable Compose `Canvas` line-chart composable, `ui/components/TrendChart.kt` — gridlines in
  `DaybookColors.Hairline`, line/dots in the calling card's `CardTint.accent`, axis labels in
  `DaybookColors.TextMuted`, no new colour literal (C5). This becomes the one general-purpose trend
  chart primitive the app has — nothing else needs to build its own.
- Empty state: a metric with fewer than 2 days of data in the selected window shows the headline
  number and a muted one-line caption ("Not enough history yet for a trend") instead of an empty
  or single-point chart — same "never a placeholder that looks broken" instinct as R32's per-card
  hide rule, applied to the chart specifically.

**Option B — true intraday chart (the literal screenshot), named but NOT recommended for this
pass:** capture and store raw per-sample Health Connect records (steps per bucket, HR samples,
SpO₂ samples) instead of just the daily `AggregateRequest` figure, so `D` mode could plot real
dots across 00:00–24:00 like the reference. This requires: new Health Connect read calls beyond
`AggregateRequest`, a new samples table per metric (or a shared `health_samples` table), another
migration, and materially more data pulled per sync — a direct reversal of §6.1.9's "raw
per-sample series... would be a battery regression (C7)" rule, decided on purpose earlier in this
document. If the user wants the literal per-sample intraday chart later, it is its own scoped
phase with its own explicit battery-cost sign-off — not bundled into this pass, and not assumed.

### 11.3 Selected bottom-nav item gets a filled pill, not just a colour change

**Current behaviour:** `ui/components/FloatingPillNav` (`Navigation.kt:71`) — the one shared nav
component both the main app's Today/Habits/Intake bar *and* Beast Mode's Routines/History/Health
bar already render through (`ui/workout/WorkoutRoutes.NAV` supplies Beast Mode's three items to
the same composable) — only tints the selected item's icon and label to the accent colour
(`tint`/`holdTint`, `Navigation.kt:134-176`). No background. Fixing it here automatically covers
"both modes," per the user's ask, with one change.

**Decision — reuse `SegmentedControl`'s exact sliding-pill idiom** (`SegmentedControl.kt:79-97`):
an `animateDpAsState`-driven filled `Box` that tracks the selected item's position and width,
clipped to a rounded shape, filled with `LocalAccent.current`, sitting behind that item's icon +
label column. Concretely:

- The pill is sized to hug one nav item's column width (not the full bar, unlike
  `SegmentedControl`'s track-width pill) with a small inset so it reads as a soft chip floating in
  the bar, not a full-height segment divider.
- Selected item's icon/label switch to `DaybookColors.OnAccent` (the same rule
  `SegmentedControl.kt:102` already uses for its selected segment's content colour) so text stays
  legible sitting on a solid accent fill; unselected items keep today's `DaybookColors.TextMuted`.
- The existing long-press-to-enter/exit-Beast-Mode gesture and its hold-scale/hold-tint animation
  (`holdActive`, `holdScale`, `holdTint`, `Navigation.kt:145-176`) are untouched — the pill is an
  added background layer under the existing icon/label, not a replacement for that logic. While
  `holdActive` is ramping (the long-press-in-progress state), the pill's position/visibility
  follows `currentRoute` exactly as it does today — a long-press never itself counts as "selecting"
  the item, so the pill doesn't move until the route actually changes.
- Respects `LocalReduceMotion` the same way `SegmentedControl`'s pill does — `snap()` instead of
  the placement spring.

### 11.4 What ships together

All three are pure UI-layer changes inside `ui/workout/health/` (11.1, 11.2) and
`ui/components/Navigation.kt` (11.3) — no migration, no new Room column, no new Health Connect
permission, no change to `DATA_TABLES` or the split export/import wire model from §7.5. 11.2 is
the only one with a real design fork (Option A vs. B); this document recommends A and treats B as
an explicitly separate, not-yet-approved future phase.
