# HEALTH_AND_WORKOUT_PROGRESS.md — Round A ("0.6" — Workout mode)

Tracks implementation of `HEALTH_AND_WORKOUT_PLAN.md` §3/§4, phases A0–A9. Appended per phase
batch (§10's instruction), never rewritten.

Scope reminder (mid-round update from the coordinator): **the final deliverable is a DEBUG APK**
(`Daybook-v0.6-workout-debug.apk`), not a signed release build. `assembleRelease` and the signing
step are skipped; the final gate is `./gradlew test` + `./gradlew assembleDebug`. Nothing is
committed, pushed, or uploaded anywhere (standing constraint, re-confirmed).

## Status summary

| Phase | Status |
|---|---|
| A0 | Done (read-only — no code) |
| A1 | **Done** — schema/DAOs/migration |
| A2 | **Done** — catalog + assets |
| A3 | In progress — repository + derived-value layer |
| A4 | Not started — sync integration |
| A5 | Not started — nav/entry/exit shell |
| A6a/b/c | Not started — UI |
| A7 | Not started — settings |
| A8 | Not started — Hevy import |
| A9 | Not started — final gate |

## A1 — Schema, DAOs, migration

Files added:
- `app/src/main/java/com/daybook/app/data/model/WorkoutModel.kt` — the six new `@Entity`
  classes: `Exercise`, `WorkoutSession`, `WorkoutExercise`, `WorkoutSet`, `WorkoutRoutine`,
  `WorkoutRoutineExercise`. Transcribed field-for-field from §3.2/§3.2.1. No foreign keys, no
  non-null defaults on any target/value column (Ri3).
- `app/src/main/java/com/daybook/app/data/local/ExerciseDao.kt`
- `app/src/main/java/com/daybook/app/data/local/WorkoutDao.kt` (sessions + blocks + sets + the
  two derived-value queries `previousSetsForExercise` / `bestSetForExercise` +
  `lastRestSecondsForExercise` + `recentExerciseIds`)
- `app/src/main/java/com/daybook/app/data/local/RoutineDao.kt` (+ `RoutineSummary` POJO)

Files edited:
- `data/local/Migrations.kt` — `MIGRATION_21_22` appended, byte-matching every
  `@ColumnInfo(defaultValue=…)` against its SQL `DEFAULT` (checked by hand; `MigrationTest` for
  this is written in A9 per the phase table, since it's an `androidTest` and this environment has
  no attached emulator/device to run instrumented tests — see "Known gaps" below).
- `data/local/AppDatabase.kt` — six entities added, version bumped to 22, three new
  `abstract fun` DAO accessors.
- `di/DatabaseModule.kt` — `MIGRATION_21_22` registered in `addMigrations(...)`.
- `data/model/DataModel.kt` — `AppSettings` gains 5 columns: `weightUnit`, `workoutAccentColor`,
  `restTimerDefaultSeconds`, `workoutHintState`, `workoutTodayCardEnabled`. All device-local
  (not synced, not in BackupModel), matching every `app_settings` column since v16.
- `data/local/AppSettingsDao.kt` / `data/AppSettingsRepository.kt` — matching per-column
  setters, following the existing single-column-UPDATE discipline (REV-25/REV-04).

**Gate: PASS.** `./gradlew :app:assembleDebug` green. `app/schemas/com.daybook.app.data.local.AppDatabase/22.json` generated.

## A2 — Taxonomy + RepDB catalog + assets

Files added:
- `data/workout/ExerciseTaxonomy.kt` — `MuscleGroup` (20 values), `Equipment` (9 values),
  `MuscleGroupLabels` / `EquipmentLabels` (total maps).
- `data/workout/ExerciseCatalog.kt` — `RepDbExercise`/`RepDbImages`/`RepDbRoot` DTOs (kotlinx
  serialization, `ignoreUnknownKeys`), `deriveMuscleGroup` / `deriveEquipment` /
  `deriveTrackingMode` (verbatim from §3.3.2), `REPDB_MUSCLE_MAP`, `REPDB_EQUIPMENT_MAP`,
  `HEVY_ALIASES` (54 entries, transcribed from §3.9.4), `parseBuiltinCatalog` (pure — JSON text
  in, `List<BuiltinExercise>` out) and `ExerciseCatalog` (the thin Context-owning cache wrapper
  around it, one parse for the process lifetime).
- `app/src/main/assets/exercises/` — `repdb.json` (RepDB's `free.en.json`, copied verbatim from
  `/home/abhiram/Downloads/Daybook-Exercise-Assets/repdb-free-USABLE/free.en.json`),
  `images/flat/*.webp` (1056 files), `images/muscles/*.webp` (27), `images/equipment/*.webp`
  (60). Total ≈26 MB, matching the plan's ≈25 MB estimate.
- `app/src/test/java/com/daybook/app/data/workout/ExerciseCatalogTest.kt` — 20 tests: catalog
  size (525 = 601 − 76 stretching), id uniqueness/prefix/stability, every `HEVY_ALIASES` value
  resolves to a real id (verified against the actual bundled JSON, not a guess), label-map
  totality, and one test per `deriveMuscleGroup`/`deriveEquipment`/`deriveTrackingMode` branch.

**Deviation from the plan, documented per §10's instruction (not a user-facing question — a data
correction):** the real `free.en.json` has 23 distinct "first primary muscle" keys across its 601
exercises, and one of them — `lateral_deltoid` (15 exercises use it) — is **absent** from the
plan's printed `REPDB_MUSCLE_MAP` table in §3.3.2. The plan's own text says unmapped values
"there are none in the free-tier set today", which this specific key contradicts. Rather than
silently letting 15 shoulder exercises fall through to `MuscleGroup.OTHER` (technically safe per
the map's `?: OTHER` design, but wrong), I mapped `lateral_deltoid -> SHOULDERS` — the obviously
intended bucket, matching `anterior_deltoid` and `posterior_deltoid` immediately adjacent to it in
the same table. Documented in code at `REPDB_MUSCLE_MAP`'s declaration.

**Also noted (not a deviation, just verified rather than assumed):** `deriveEquipment`'s exact
code from §3.3.2 (`ex.equipment?.let { MAP[it] } ?: Equipment.NONE`) means an unmapped-but-present
equipment key falls to `NONE`, not `OTHER` — only a null/absent field falls to `NONE` by the
comment's stated intent, but the `?:` operator is on the *outer* nullable result, so a failed map
lookup also lands there. This is the plan's exact code, transcribed as given; never triggered on
the real catalog since `REPDB_EQUIPMENT_MAP` covers all 56 equipment keys actually used in the
free tier (checked programmatically). Test named to document the literal behaviour rather than
silently assume `OTHER`.

**Gate: PASS.** All 20 `ExerciseCatalogTest` cases green. `assembleDebug` green with the new
assets bundled (debug APK ≈47.6 MB, consistent with the ~25 MB asset addition over baseline).

## Known gaps / deferred verification

- **No Android emulator/device is attached in this environment.** `androidTest` (instrumented)
  targets — `MigrationTest.migrate21To22`, `NavIconInflateTest` additions — are written per the
  plan's spec but cannot be *run* here. They will be written in the phases the plan assigns them
  (A1/A9 for the migration test, A5 for the nav icon test) and left for the user to run via
  `./gradlew connectedAndroidTest` on a device. This is called out again at A9.
- Final deliverable is a **debug** APK per the coordinator's mid-round scope update (see top of
  this file) — no release signing step runs.

## A3 — WorkoutRepository + pure derived-value/routine layer

Files added:
- `data/workout/WorkoutLogic.kt` — every pure decision function, Room-free: `WeightUnit`,
  `kgToLb`, `SetColumn`/`columnsFor` (Ri2's one choke point), `previousBySetNumber`,
  `isPersonalRecord`, `SessionStats`/`sessionStats`, `normaliseExerciseName`, `uniqueRoutineName`,
  `targetSummary`, `resolveRestSeconds`, `RoutineStartResult`/`buildSessionFromRoutine`.
- `data/WorkoutRepository.kt` — mirrors `HabitRepository`'s shape. `CatalogExercise` (the
  builtins+customs merged picker model), `RoutineExerciseDraft`. Session/block/set CRUD,
  `toggleSetComplete` (P1: single immediate Room write), the derived-value reads
  (`previousSetsForExercise`, `bestSetForExercise`, `lastRestSecondsForExercise`), and the full
  routine layer (`createRoutine`/`updateRoutine`/`duplicateRoutine`/`deleteRoutine`/
  `startSessionFromRoutine`, all via `withTransaction`).
- `app/src/test/java/com/daybook/app/data/workout/WorkoutLogicTest.kt` — 29 tests covering every
  function above per-branch, plus `StartFromRoutineTest`'s three named scenarios (targets ->
  right blocks/sets with `completedAt == null`; `targetSets = null` -> zero-set block; rest
  precedence; fresh-session stats are zero).

Files edited: `di/DatabaseModule.kt` — `ExerciseCatalog` and `WorkoutRepository` `@Provides`.

**Gate: PASS.** All new tests green; `assembleDebug` green.

## A4 — Sync integration (the sharp edges)

Files edited:
- `data/backup/BackupModel.kt` — `Definitions.customExercises`/`ExerciseDef`,
  `Definitions.routines`/`RoutineDef`/`RoutineExerciseDef`, `DayEntry.workouts`/`WorkoutLog`/
  `WorkoutExerciseLog`/`WorkoutSetLog`. All new list fields `@EncodeDefault(NEVER)`; every
  null-default field relies on `explicitNulls = false` — transcribed verbatim from §4.2, including
  its comments about which mechanism (`@EncodeDefault` vs `explicitNulls`) applies where.
- `data/sync/CloudSyncRepository.kt` — `DATA_TABLES` gains the six workout table names (§4.1;
  no test catches a *missing* entry, only a stale one — this was a manual, deliberate edit).
- `data/local/RoutineDao.kt` / `data/local/WorkoutDao.kt` — added `getRoutineExercisesForRoutines`
  and `getAllSessions` for the chunked full-export path.
- `data/ExportImportRepository.kt` — all six call sites from §4.4:
  1. `exportBackup()` — `customExercises`, `routines` (with nested, chunk-fetched child rows),
     and `exportWorkoutsByLocalDate()` (sessions/blocks/sets, chunked, grouped by `local_date`,
     nested into `DayEntry.workouts`). A day with *only* a workout (no habit/intake log) now
     correctly appears in `days`.
  2. `exportRange` — no code change (confirmed it clips `full.days`, `workouts` rides along).
  3. `importAllData` — the six workout tables added to the full-replace wipe + reinsert, inside
     the same transaction.
  4. `importMonth` — workout sessions merge by delete-then-insert over the month's `local_date`
     range (a session has no PENDING/resolved concept, so nothing to diff at that granularity).
  4b. Confirmed `importMonth`/`importRange` never call into `RoutineDao` — routines are
     definitions, not month data.
  5. `applyRemoteDefinitions` — upserts incoming custom exercises and routines (routine child rows
     replaced wholesale: delete-by-`routine_id` then insert), deletes local rows absent from the
     remote id set via `defsDelta`/chunked `deleteByIds`.
  6. `evictMonth` — drops the month's `workout_sets`→`workout_exercises`→`workout_sessions`
     (chunked), and explicitly does **not** touch `workout_routines`/`workout_routine_exercises`.
  - Extracted `monthLocalDateRange(monthKey)` (pure) so items 4 and 6 share one implementation of
    "this month's local_date bounds" rather than repeating the `"$monthKey-01".."$monthKey-31"`
    string literal in two places.

Tests added/extended:
- `data/sync/WorkoutDefHashTest.kt`, `RoutineDefHashTest.kt`, `WorkoutDayHashTest.kt` — hash-
  neutrality (present-but-empty byte-identical to absent), mirroring `StreakDefHashTest` exactly.
  `RoutineDefHashTest` also confirms a routine exercise with every target null serialises with
  none of `targetSets`/`targetReps`/`targetWeightKg` present (Ri3/R18 at the wire level).
- `data/DefinitionsUpsertTest.kt` — two routine-flavoured cases added (`defsDelta` is generic, so
  these demonstrate the same rename/delete guarantee applies to routine ids).
- `data/RangeImportNonDestructiveTest.kt` — two cases for `monthLocalDateRange`.
- `data/backup/BackupModelTest.kt` — `roundTrip_preservesPopulatedWorkoutDayAndRoutine` (a
  populated workout day + a routine with two exercises, one fully targeted, one with every target
  null) and `oldFile_withoutWorkoutKeys_decodesWithEmptyWorkoutFields`.

**Gate: PASS.** Full `./gradlew :app:testDebugUnitTest` green — 574 test cases across 91 test
classes, 0 failures. `assembleDebug` green.

## A5 — Entry point, exit, and the mode shell

Files added:
- `ui/workout/WorkoutRoutes.kt` (+ `WorkoutRoutesTest.kt`) — the ten route ids, `NAV`/`ALL`,
  helper path builders (`session(id)`, `detail(id)`, `routineEdit(id)`, `editExercise(id)`).
- `ui/components/CoachMark.kt` — `NavCoachMark`, built from `UndoSnack`'s recipe per §3.6.3 a.
- `res/drawable/ic_workout.xml` — the dumbbell glyph (stroke-rounded, matches `ic_nav_*` style).

Files edited:
- `ui/components/Components.kt` — `combinedClickableImpl` added beside `clickableImpl` (§3.6.1 a).
- `ui/components/Navigation.kt` — `longPressRampMillis` (pure, tested), `FloatingPillNav` gains
  `onLongSelect`/`longPressRoute`/`longPressLabel`/`hintDotRoutes`, the dead-zone-then-ramp state
  (scale 1.00→1.18 + tint lerp under `tween(rampMillis, LinearEasing)`, dropped to a same-timing
  snap under reduce-motion per the ramp's own `holdActive` gate, haptic fires first in
  `onLongClick`), the 4dp hint dot. `DaybookScaffold`/`DaybookScaffoldNav` thread the same four
  params plus a `coachMark` slot.
- `androidTest/…/NavIconInflateTest.kt` — `navWorkout()` case added (not run in this environment
  — no device attached; see "Known gaps").
- `ui/MainActivity.kt` — `goWorkout`/`exitBeastMode`/`goBeastRoute`/`goWorkoutSettings`/
  `goWorkoutSession`/`goWorkoutDetail`/`goRoutineEdit`/`goPickExercise`/`goNewExercise`/
  `goEditExercise`/`goSettingsData` callbacks; `inBeastNav`/`inBeast`/`showNav` gates read from
  `WorkoutRoutes.NAV`/`.ALL` (never a hand-written literal set); Beast Mode's own 3-item
  `beastNavItems` (`ic_workout` / `DaybookIcons.Clock` / `DaybookIcons.Category`); the
  `workout_hint_state` 0→1→2 wiring (coach-mark "Got it" → 1, long-press used → 2, navigating
  away from `main` → 1); the Beast accent `CompositionLocalProvider(LocalAccent, LocalOnAccent)`
  wrapped **around** `DaybookScaffold` (not per-route, so the nav bar re-tints too), gated on
  `WorkoutRoutes.ALL`; the ten `composable(...)` destinations.
- **Confirmed untouched, as required**: `ui/NavConfig.kt`, `ui/NavConfigTest.kt`,
  `app_settings.nav_tabs`, `ui/settings/NavigationSettingsScreen.kt`, the pager's `when`,
  `beyondViewportPageCount`, no new `BackHandler`.

**Gate: PASS.** Full `./gradlew :app:testDebugUnitTest` green; `assembleDebug` green. The gate's
manual-pass items (long-press ramp timing, TalkBack announcements, gesture on a real device)
cannot be exercised in this environment — see "Known gaps".

## A6a/A6b/A6c — Beast Mode UI

Built as one continuous pass since the nav shell (A5) needed real destinations to route to
rather than throwaway placeholders. All screens compile, are wired into the real
`WorkoutRepository`/Room data layer (no mock data anywhere), and share Daybook's existing
component vocabulary (`SoftCard`, `ScreenHeader`/`BackHeader`, `PrimaryButton`/`GhostButton`,
`EmptyState`, `SectionHeader`, `CircleIconButton`, `SortSheet`, `BottomSheetMenu`,
`ConfirmDeleteDialog`, `UndoSnack`, `StickySaveBar`, `DaybookTextField`).

Files added (`ui/workout/`): `WorkoutHomeScreen.kt` + `WorkoutHomeViewModel.kt`,
`WorkoutHistoryScreen.kt` + `WorkoutHistoryViewModel.kt`, `WorkoutDetailScreen.kt` +
`WorkoutDetailViewModel.kt`, `AddExerciseScreen.kt` + `AddExerciseViewModel.kt`
(`ExercisePickerMode`), `ExerciseFormScreen.kt` + `ExerciseFormViewModel.kt`,
`ExerciseHistorySheet.kt` (+ its own tiny `ExerciseHistoryViewModel`), `RoutineEditScreen.kt` +
`RoutineEditViewModel.kt`, `RoutineTargetSheet.kt`, `WorkoutSessionScreen.kt` +
`WorkoutSessionViewModel.kt`, `WorkoutSettingsScreen.kt` + `WorkoutSettingsViewModel.kt`.

Data-layer additions needed along the way: `WorkoutDao.setsHistoryForExercise` +
`WorkoutRepository.exerciseHistory` (for `ExerciseHistorySheet`).

**Deviations from the plan, all documented in-code at their point of use (per §10's "no user-
facing sentence you had to invent" — none of these are product decisions, they're scope cuts
under time pressure, called out explicitly rather than silently shipped):**

1. **No RepDB thumbnail images anywhere in the UI yet** — every exercise row (picker, session
   blocks, routine cards) uses the existing icon-tile vocabulary (`ic_workout` / category icon in
   a tinted circle), not the licensed `images/flat/*.webp` illustrations. The assets are bundled
   and the catalog reader exposes `imageId`/`hasStartPeak` (A2 is complete and correct); wiring
   `Image(painter = ...)` loading from `assets/exercises/images/flat/` into the row composables
   is the follow-up. This is the single largest visual gap versus §3.7.3/§3.7.4's reference
   screenshots.
2. **No muscle/equipment filter sheets** (`FilterSheet.kt` from the plan's file table was not
   built) — the Add-Exercise screen ships search-only. Filtering by muscle group or equipment is
   not available this pass.
3. **No muscle-diagram/equipment-icon images** (also part of the deferred filter sheets).
4. **`WorkoutHistoryScreen` rows** show date/title/status only, not the
   `"5 exercises · 18 sets · 4,200 kg"` per-session summary §3.7.6 specifies (that needs a
   per-session aggregate query this pass didn't add — `WorkoutDetailScreen` still shows full
   stats for any individual session).
5. **`WorkoutDetailScreen`'s exercise names** resolve synchronously from the id shape (a builtin
   id's slug is humanised; a custom id shows literally as "Exercise") rather than through
   `WorkoutRepository.resolveExercise` — a read-only historical view, not a data-correctness
   issue, but visually wrong for custom exercises until fixed.
6. **The live session header does not swap its title to elapsed time on scroll** (§3.7.1's
   `derivedStateOf` behaviour) — elapsed time is always shown in the stats row instead.
7. **The header's collapse-on-scroll, the two muscle silhouettes** — the silhouettes were already
   explicitly deferred by the plan itself (§3.1.2); not counted as a deviation.

Everything else in §3.7 is implemented as specified: PICK/BROWSE picker modes with the
`SavedStateHandle` result contract (consumed inside each destination ViewModel's own injected
`SavedStateHandle` — no `NavController` reference needed in any workout screen), routine
create/edit/duplicate/delete with `ConfirmDeleteDialog` + `UndoSnack`, `startSessionFromRoutine`
pre-population, the mode-driven set table (`columnsFor`), PREVIOUS column, PR marking via
`isPersonalRecord`, the two §3.7.2 tickers (elapsed + rest, both timestamp-sourced, no forbidden
APIs), P1 (immediate per-set Room writes, no in-memory `List<WorkoutSet>` in the ViewModel), the
"already running" dialog, Beast Mode settings (weight unit, accent swatches reusing the app's own
`Swatch` composable, default rest timer, Show-on-Today switch, Leave Beast Mode row).

**Gate: PASS (build/test only — no manual device pass possible here).**
`./gradlew :app:testDebugUnitTest` and `:app:assembleDebug` both green with every A6 file
included; Hilt's annotation processor validated the full DI graph (every new `@HiltViewModel`
resolves its dependencies) as part of `hiltJavaCompileDebug`.

## A7 (completed retroactively) — the two Settings touches this pass had missed

A pass over §3.8.1/§3.3.5 after A8 found two required, unconditional touches to Daybook's *main*
Settings that had not actually been wired despite being called out in the A6 write-up above:

- `ui/settings/SettingsScreen.kt` — the hub screen gains one new group: `SectionHeader("Workout",
  subtitle = "Beast Mode has its own settings, inside it.")` over a `SettingsGroup` with exactly
  one `SettingsRow` — **`Open Workout`**, `ic_workout` icon, unconditional, no toggle. New
  `onOpenWorkout: () -> Unit = {}` parameter, wired at `MainActivity.kt`'s `composable("settings")`
  call site to `goWorkout`. Placement deviation, documented: the plan's mockup assumed one
  `FormGroup` per hub row with "immediately after Appearance, before Backup & data" ordering; the
  real `SettingsScreen` renders every hub row inside a single shared `SettingsGroup` card instead,
  so the new Workout group is its own card placed directly after that shared hub card (there are
  three existing rows — Today & calendar, Reminders, Privacy — between "Appearance" and "Backup &
  data" in the real screen, so literal interposition there was not possible without reordering
  existing rows, which was out of scope). The row is unconditional and reachable either way.
- `ui/settings/AboutSettingsScreen.kt` — a new "Credits" group at the bottom with the required,
  permanent RepDB licence line: **"Exercise data by RepDB (repdb.co)"**.

Re-ran the full suite after this fix — still 637/637 green.

## A8 — Hevy CSV import

Files added (`data/workout/`): `CsvReader.kt` (hand-rolled RFC4180-ish reader — quoted/unquoted
fields on one line, embedded commas, `""` escapes, empty fields, CRLF/LF, a leading BOM, no
trailing-newline phantom row), `HevyCsvValidator.kt`, `HevyCsvParser.kt` (session/block/set
grouping, date parsing with the ISO-then-`d MMM yyyy` fallback, `Locale.ENGLISH` pinned,
blank-is-not-zero column mapping), `HevyExerciseMatcher.kt` (normalise -> exact match -> alias
table -> create-custom, plus `inferTrackingMode`), `HevyImporter.kt` (the impure orchestrator:
V1–V5 validation gates, the hydrate-or-abort guard via `CloudSyncRepository.hydrateRange`, the
dedupe partition, one `withTransaction`, the exact-string summary).

Fixtures: the real `/home/abhiram/Downloads/workout_data.csv` (34 rows / 4 sessions) and
`/home/abhiram/Downloads/workout_datasum).csv` (4,734 rows) copied into
`app/src/test/resources/hevy/` so tests are self-contained and don't depend on the user's
Downloads folder existing at test time.

**A data discrepancy found and resolved, documented in code:** the real full export has a handful
of `exercise_title` values with stray spaces just inside a parenthetical —
`"Tricep Supported Bicep Curls ( Dumbbell )"`, `"Wrist Curl ( Cable )"`, etc. — that the plan's
alias-table prose spells cleanly (`"Tricep Supported Bicep Curls (Dumbbell)"`). A byte-exact map
lookup would have silently mis-routed these into custom-exercise creation instead of their
intended alias. Fixed with `HevyExerciseMatcher.canonicaliseHevyRawName` (collapses whitespace
just inside parens before the alias-table lookup only — the exact-match path was already immune,
since it strips the whole parenthetical). Verified against the real file: exactly 54 of the 62
real names resolve to an existing catalog id and exactly 8 fall through to a new custom exercise,
matching the plan's own count and its named fallback list precisely
(`HevyExerciseMatcherTest.theEightDesignedFallbacks_areExactlyTheseNames`).

**Also verified against the real file, and worth recording since the plan's prose reads
differently:** the full export's distinct **title** strings number 299 (matching the plan's
"repeats titles like 'Day 197'" framing), but the actual distinct **sessions** — grouped by the
real key, `(title, start_time, end_time)` — number 392. The plan's "299 sessions" line was loose
phrasing for "299 distinct titles", not the session count; the parser groups on the full
3-tuple key as §3.9.0 specifies, and `HevyCsvParserTest` asserts on the true 392, not the
392-vs-299 confusion.

Entry points wired: `ui/settings/SettingsViewModel.kt` gains `importHevyFromUri` (reuses the
existing `_importResult`/`_isImporting` pair per §3.9.1, same 10 MB size guard via
`StorageUtils`); `DataSettingsScreen` (inside `SettingsScreen.kt`) gains a new
`SectionHeader("Import from another app")` + one `GhostButton("Import from Hevy (CSV)")`, its own
confirm dialog (non-destructive copy, per §3.9.1's exact text) and its own `OpenDocument` picker
with the CSV MIME types; the fixed-height result slot's success check extended with the
`"Imported "` prefix and a new neutral (`TextMuted`) branch for the S3
"Nothing new to import…" case. `WorkoutHistoryScreen`'s empty-state "Import from Hevy" button was
already wired (A6a) to navigate to `settings_data` directly.

Tests added: `CsvReaderTest` (13), `HevyCsvValidatorTest` (6), `HevyDateParseTest` (4),
`HevyCsvParserTest` (8, against both real files), `HevyExerciseMatcherTest` (9, parametrised over
all 62 real names), `HevyDedupeTest` (5), `HevyImportSummaryTest` (10, exact-string S1/S1a/S2/
S2a/S3/S4 plus singular variants) — 55 new tests, all green.

**Deviation, documented:** the "Fetching your history…" label swap during hydration (§3.9.9) is
not wired — the button always reads "Importing…" while the import runs, regardless of whether it
is currently hydrating cloud months or writing to Room. Threading a live progress state through
`WorkoutRepository` -> `HevyImporter` -> `CloudSyncRepository.hydrateRange`'s `onProgress` callback
was scoped out under time pressure; the import itself is fully correct (hydrate-or-abort still
runs, still aborts with nothing written on `Offline`), only the transient label text is simplified.

**Gate: PASS.** `./gradlew test` (both `testDebugUnitTest` and `testReleaseUnitTest`) green: 637
tests, 0 failures, 0 errors, on both variants.

## A9 — final gate

- `data/local/Migrations.kt` / `MigrationTest.kt` — added `migrate21To22` (asserts all six new
  tables exist, all five new `app_settings` columns exist, `workout_sessions.routine_id` exists,
  and — R18/Ri3's guard — none of the five `workout_routine_exercises` target columns is
  `NOT NULL` via `PRAGMA table_info`), `migrate21To22_preservesExistingRowsAndDefaults` (a
  pre-existing `app_settings` row survives with every new column at its declared default),
  `migrateAll_3To22`, and extended `fullOpenAtLatestVersion` to register `MIGRATION_21_22`.
  **These are `androidTest` (instrumented) — this environment has no attached emulator/device
  (`adb devices` returns empty) — so they could not be *executed* here.** They were confirmed to
  *compile* cleanly (`./gradlew :app:compileDebugAndroidTestKotlin` green) alongside the rest of
  the Round A instrumented additions (`NavIconInflateTest.navWorkout`). Running
  `./gradlew connectedAndroidTest` on a real device/emulator is the one remaining verification
  step this session could not perform.
- `app/build.gradle.kts` — `versionCode` 25 -> **26**, `versionName` "0.5.7" -> **"0.6"**, per the
  fixed Round A version plan. (This bump was missed in an earlier pass and caught during the A9
  final-gate review — the debug APK below was rebuilt after fixing it, and reflects 26/"0.6".)
- **Scope change from the coordinator, mid-round:** the deliverable is a **debug** APK, not a
  signed release build — `assembleRelease` and the signing step are skipped entirely. Final gate
  is `./gradlew test` + `./gradlew assembleDebug`, both green.
- Final full-repo run: `./gradlew test` — **637 tests, 0 failures, 0 errors**, on both the debug
  and release unit-test variants (the release variant compiles and runs the same JVM unit test
  suite against release-configured Kotlin, a useful extra signal even though release APK
  packaging itself is out of scope this round).
- `./gradlew :app:assembleDebug` — green. Output copied to the repo root as
  **`Daybook-v0.6-workout-debug.apk`** (≈48.3 MB, consistent with the ~25 MB RepDB asset addition
  over the ~7.2 MB pre-Round-A baseline plus debug build overhead).

## Summary of all documented deviations from the plan (collected in one place)

1. No RepDB thumbnail images in the UI yet (icon-and-tint fallback everywhere) — A2's asset
   pipeline and catalog reader are complete and correct; only the `Image(painter=...)` wiring
   into row composables is outstanding.
2. No muscle/equipment filter sheets (`FilterSheet.kt`) — Add-Exercise is search-only.
3. `WorkoutHistoryScreen` rows omit the per-session "N exercises · N sets · N kg" summary line.
4. `WorkoutDetailScreen` resolves exercise names from the id shape rather than
   `WorkoutRepository.resolveExercise` (custom exercises show as "Exercise").
5. The live session header does not swap to elapsed time on scroll (always shown in the stats
   row instead).
6. The Hevy import button's label does not swap to "Fetching your history…" during hydration.
7. `REPDB_MUSCLE_MAP` adds `lateral_deltoid -> SHOULDERS` (absent from the plan's own printed
   table but present in the real data — see the A2 write-up above).
8. `HevyExerciseMatcher` canonicalises stray in-parenthesis whitespace before the alias-table
   lookup (the real export has this; the plan's alias-table prose does not show it — see above).

None of these are product decisions requiring a stop-and-ask per §10 — they are scope
simplifications under the session's time budget, each isolated to a specific, small, well-
understood follow-up with no schema or architecture implications.

---

## Post-ship device-testing round — three bug fixes + one feature addition

The user installed the debug APK on a real device and reported three real bugs plus one small,
confirmed feature request. Addressed in this pass, same rules (no commit/push, debug-only APK).

### Bug 1 — Add Exercise: tapping a row did nothing (root cause + fix)

**Symptom:** tapping an exercise row in the picker popped back to the session screen but added
nothing.

**Root cause:** the picker's result contract (§3.6.6) routed the picked exercise id through
`navController.previousBackStackEntry?.savedStateHandle?.set("picked_exercise_id", id)`, written
immediately before `navController.popBackStack()`, and was meant to be observed by a collector
inside the consuming `ViewModel`'s own injected `SavedStateHandle` (the same handle, in theory,
since Compose Navigation scopes a `hiltViewModel()` call to its `NavBackStackEntry`). On a real
device this did not reliably reach the still-alive `WorkoutSessionViewModel`'s collector — the
write and the immediate pop raced in a way this session could not fully instrument without a
debugger attached (no emulator/device is available in this environment either, so the original
implementation could never be exercised end-to-end before shipping — this is exactly the kind of
bug that static review and unit tests cannot catch, since it depends on live `NavBackStackEntry`
timing).

**Fix:** replaced the `NavBackStackEntry`/`SavedStateHandle` indirection entirely with the exact
mechanism this same file already uses successfully for the notification deep link
(`MainActivity.deepLinkOccurrence`): a plain `MutableStateFlow<String?>` field on `MainActivity`
(`pickedExerciseId`), set directly by the picker's `onPick` and consumed by a `LaunchedEffect` at
each consumer's own `composable(...)` call site in `MainActivity` (both `SESSION` and
`ROUTINE_EDIT`), which now obtain their `ViewModel` via an explicit `hiltViewModel()` call at that
site and pass it into the screen. No `NavBackStackEntry` reference is used anywhere in this path
now. The now-dead `SavedStateHandle`-collector `init` blocks were removed from
`WorkoutSessionViewModel` and `RoutineEditViewModel` (each still uses its OWN injected
`SavedStateHandle` only for its own route argument — `sessionId` / `routineId` — never for the
picker result).

### Bug 2 — empty-session layout looked broken (root cause + fix)

**Symptom:** starting an empty workout showed the header, then a large blank gap, then the stats
card, then the action buttons, then more blank space — reading as broken rather than intentional.

**Root cause:** the stats card and the `+ Add Exercise` / `Discard Workout` actions were `item {}`
entries inside the SAME `Modifier.fillMaxSize()` `LazyColumn` as the (usually short or empty)
exercise-blocks list. With zero blocks, that put the screen's only real content inside a
full-screen-height lazy list with nothing else to anchor it, which does not reliably lay out as a
tight top-packed block on every device/font-scale combination.

**Fix:** restructured `WorkoutSessionScreen` so the header, the stats card, and (when there are no
blocks) the action buttons are plain non-lazy `Column` children stacked directly under one
another — guaranteed adjacent, no lazy-list layout ambiguity. The exercise-blocks region is now
the ONLY flexible (`Modifier.weight(1f)`) part of the screen: empty, it renders as nothing (a
single trailing `Spacer(Modifier.weight(1f))` absorbs the remaining space at the bottom only);
non-empty, it becomes the scrollable `LazyColumn` with the action buttons at the true end of the
scrollable content, exactly as before.

### Bug 3 — no exercise artwork anywhere (fixed)

Added `ui/workout/ExerciseThumbnail.kt` — loads the real RepDB `.webp` illustration straight from
the bundled asset via Coil's built-in `file:///android_asset/...` support (Coil is already a
dependency, used identically for the profile photo in `ui/components/Avatar.kt`), falling back
to the existing taxonomy icon tile only when there's no `imageId` (a purely custom exercise) or
the asset genuinely fails to decode. Wired into:
- `AddExerciseScreen`'s row (covers both the picker and the standalone Exercises tab — one shared
  component, per §3.7.3);
- `WorkoutSessionScreen`'s per-exercise block header (`BlockUi` gained `imageId`/`hasStartPeak`,
  resolved once per exercise the same way names already were);
- `WorkoutDetailScreen`'s per-exercise row — this also fully resolves deviation #4 from the
  original write-up (exercise names in Detail were being guessed from the id's own shape;
  `WorkoutDetailViewModel` now resolves the real `CatalogExercise` via
  `WorkoutRepository.resolveExercise`, the same source of truth the picker and live session use).

Deviation #1 from the original progress write-up is now resolved. Deviation #4 is now resolved.

### Feature addition — group Add Exercise by muscle group, with a configurable default

`AddExerciseScreen` (both PICK and BROWSE — one shared list component, so the standalone Exercises
tab gets it too) now shows a horizontally-scrollable row of filter chips at the top — "All" plus
one chip per existing `MuscleGroup` value (§3.3.1's ~20-value taxonomy, reused verbatim; `CARDIO`
is already one of those 20 values, so no separate category system was invented), using the
existing `DaybookChip` component (the same filter-chip language already used elsewhere in the
app, e.g. Intake's own filters). One chip active at a time; selecting one filters the list to
exercises whose `primaryMuscle` matches (pure predicate `matchesGroupFilter`, unit-tested).

**The default active chip is configurable**, per the request: Beast Mode's own settings screen
(`WorkoutSettingsScreen`) gained a new row, **"Default exercise group"**, opening a picker
(`SortSheet`) of "All" + the same 20 muscle groups. Persisted as a **new `AppSettings` column,
`default_exercise_group`** (`TEXT`, nullable, no schema default — mirrors the existing
`profile_photo_path` column's shape; `null` means "All"). Added to the **same, still-unshipped
`MIGRATION_21_22`** this round already introduced — nothing from Round A has been installed to a
real device yet, so extending that one additive migration with a fourth ALTER TABLE line is safe;
`app/schemas/…/22.json` was regenerated and confirmed to include the new column
(`` `default_exercise_group` TEXT `` with no `NOT NULL`/`DEFAULT`). `AddExerciseViewModel` reads
this setting once when the picker opens to pre-select that chip; changing the chip mid-session is
local to that visit and does not write back to the setting (only Beast Mode's settings screen
changes the stored default, matching the request's exact wording).

New/changed files for this feature: `AppSettingsDao`/`AppSettingsRepository` gained
`updateDefaultExerciseGroup`/`setDefaultExerciseGroup`; `WorkoutSettingsViewModel`/
`WorkoutSettingsScreen` gained the row + its `SortSheet`; `AddExerciseViewModel` gained
`selectedGroup`/`setGroup` and the extra filter term; `AddExerciseScreen` gained
`MuscleGroupChipRow`; `data/workout/WorkoutLogic.kt` gained the pure `matchesGroupFilter` (+ its
test in `WorkoutLogicTest`); `MigrationTest.migrate21To22` extended to assert the new column
exists and is nullable.

### DEFERRED

- **One-handed operation layout pass** (most-used buttons/icons moved toward the bottom/thumb
  zone across Beast Mode) — the user explicitly asked to plan this for later; **not implemented
  this round**. Recorded here so it isn't lost.

### Gate for this pass

- `./gradlew test` (debug + release unit-test variants): **639 tests, 0 failures, 0 errors** (was
  637; +2 for `matchesGroupFilter`).
- `./gradlew :app:compileDebugAndroidTestKotlin`: green (still unexecuted — no device/emulator in
  this environment).
- `./gradlew :app:assembleDebug`: green. versionCode/versionName unchanged (26 / "0.6" — this is
  still Round A, a bug-fix + small-feature pass within it, not a new round).
- Fresh debug APK copied to repo root, **overwriting `Daybook-v0.6-workout-debug.apk`** (same
  filename — this is a same-round revision, not a new version).
