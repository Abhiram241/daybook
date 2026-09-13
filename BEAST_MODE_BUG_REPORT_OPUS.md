# Beast Mode — deep bug / silent-failure audit (second pass)

Scope: `ui/workout/**`, `ui/workout/beast/**`, `data/WorkoutRepository.kt`, `data/workout/**`,
`data/local/{WorkoutDao,ExerciseDao,RoutineDao}.kt`, `data/model/WorkoutModel.kt`,
`MIGRATION_21_22`, plus the Beast Mode nav wiring in `ui/MainActivity.kt` (needed to decide
whether several VM actions are reachable at all).

Method: full read of every file listed, then a cross-check of every public StateFlow against its
collectors, every VM action against its call sites, and every SQL query against the entity it
reads.

**Important context the first report did not have:** the working tree already contains
*uncommitted fixes* for prior findings 1–5 (`git diff` touches `WorkoutRepository.kt`,
`WorkoutHomeViewModel.kt`, `WorkoutHomeScreen.kt`, `WorkoutSessionViewModel.kt`,
`WorkoutSessionScreen.kt`). This report verifies against the **current working tree**, and says so
for each prior finding.

---

## 1. Verification of prior findings

### 1.1 `WorkoutHomeViewModel.errorMessage` never shown — **WAS CORRECT, NOW FIXED**
`WorkoutHomeScreen.kt:79-80` now collects `errorMessage`/`errorToken` and `:202` renders
`UndoSnack(token = errorToken, text = errorMessage ?: "")`. `WorkoutHomeViewModel.kt:114-118`
centralises this in `reportError`. Confirmed fixed.

Residual nit: `UndoSnack(token = errorToken, text = errorMessage ?: "")` and
`UndoSnack(token = deletedToken, text = "Routine deleted")` are two independent snackbars stacked
in the same `Box` with no z-ordering or mutual exclusion; a delete that fails increments
`errorToken` only, so they can't fire together — but a successful delete followed immediately by a
failed duplicate will draw both at once. Cosmetic. Confidence: medium.

### 1.2 `startEmptyWorkout()` has no failure path — **WAS CORRECT, NOW FIXED**
`WorkoutHomeViewModel.kt:124-128` now wraps in `runCatching` with `onSuccess`/`onFailure ->
reportError`. Confirmed fixed.

### 1.3 `duplicateRoutine()` failure only to Crashlytics — **WAS CORRECT, NOW FIXED**
`WorkoutHomeViewModel.kt:144-150` now calls `reportError(it, "Couldn't duplicate that routine.
Try again.")`. Confirmed fixed.

### 1.4 `addSet` prefill uses `sets.size + 1` — **WAS CORRECT AS WRITTEN, NOW FIXED, BUT THE
SEVERITY WAS OVERSTATED**
The fix is real and correct: `WorkoutSessionViewModel.kt:170-173` now passes the whole `previous`
map, and `WorkoutRepository.addSet` (`:308-327`) does the lookup *after* resolving
`maxSetNumber + 1` (`:315-316`). Good.

But the stated failure scenario ("after deleting set 2 of a 3-set block") **was not reachable from
the UI**, before or after the fix. `WorkoutSessionViewModel.deleteSet` (`:176`) has **zero call
sites** — a full grep of `ui/workout/**` finds no composable that invokes it; `SetTable`
(`WorkoutSessionScreen.kt:491-555`) renders only SET / PREVIOUS / value cells / COMPLETE, with no
delete affordance or swipe action. So the divergence could only ever have been produced by an
imported/synced row with non-contiguous set numbers. The fix is still right (Hevy import *can*
produce gaps if `set_index` is non-contiguous), but "a set in the middle of the block was ever
deleted" was not a user-reachable path. Confidence: high — see §2.9 for the missing-delete-UI
finding itself.

### 1.5 `finish()` reports success when the session row is gone — **WAS CORRECT, NOW FIXED (for
`finishSession` only)**
`WorkoutRepository.finishSession` (`:166-170`) now returns `Boolean`, and
`WorkoutSessionViewModel.finish()` (`:186-196`) only sets `_finished = true` on `true`, otherwise
raising `errorMessage`/`errorToken`, which the screen now collects (`WorkoutSessionScreen.kt:115-
116, 296`). Confirmed fixed.

**Still unfixed:** `renameSession` (`WorkoutRepository.kt:188-191`) and `setSessionElapsedSeconds`
(`:197-201`) both still `?: return` on a missing session with no signal to the caller.
`WorkoutHistoryViewModel.renameSession` (`:158-162`) reports `announce("Renamed")` on that silent
no-op, so the History overflow's "Edit" shows a "Renamed" toast even when nothing was written.
Confidence: high (low real-world likelihood).

### 1.6 Systemic: session mutations have no user-visible failure path — **CORRECT, STILL TRUE**
`WorkoutSessionViewModel.kt:158-161, 165-168, 170-177, 198-201` — `addExercise`, `removeExercise`,
`setExerciseNotes`, `setExerciseRest`, `setElapsedSeconds`, `addSet`, `updateSet`, `deleteSet`,
`toggleSetComplete`, `discard` are all bare `safeLaunch` with the default Crashlytics-only
`onError`. The `_errorMessage`/`_errorToken` plumbing now exists in this VM (added for `finish()`),
so wiring these in is cheap. Unchanged assessment.

Note one that is worse than "silently does nothing": `discard()` (`:198-201`) sets
`_finished = true` *after* `repo.discardSession(...)`, which is correct — but if the discard
throws, the confirm dialog has already been dismissed and the user is left on a session they
believe they deleted, with no message.

**Summary: none of the 6 were wrong. #1.4's failure scenario was overstated (unreachable path).**

---

## 2. New confirmed bugs, ranked by severity

### 2.1 Multi-select "Add (n)" races itself — every added exercise gets the same `orderIndex` — **HIGH**
`ui/MainActivity.kt:1086-1092` + `WorkoutSessionViewModel.kt:158` + `WorkoutRepository.kt:251-269`

```kotlin
// MainActivity.kt:1088-1092
pendingPick?.let { ids ->
    ids.forEach { sessionViewModel.addExercise(it) }   // N independent safeLaunch coroutines
    pickedExerciseId.value = null
}
// WorkoutSessionViewModel.kt:158
fun addExercise(exerciseId: String) = safeLaunch { repo.addExerciseToSession(sessionId, exerciseId) }
// WorkoutRepository.kt:253
val orderIndex = database.workoutDao().maxExerciseOrderIndex(sessionId) + 1
```

`addExercise` is `safeLaunch`, i.e. `viewModelScope.launch` — `ids.forEach` fires N coroutines that
all run concurrently on the same `viewModelScope`. Each independently reads
`maxExerciseOrderIndex` before any of them has inserted, so all N read the same value and all N
insert with the *same* `orderIndex`. There is no transaction around the read-modify-write
(`addExerciseToSession` is not inside `withTransaction`), and no FK/unique constraint to catch it.

**Failure scenario:** in a live session, tap "+ Add Exercise", check Bench Press, Incline Press and
Flyes, tap "Add (3)". All three blocks land with `order_index = 1`. `state` sorts by `orderIndex`
(`WorkoutSessionViewModel.kt:77`), so the three blocks appear in arbitrary, *non-deterministic*
order that can change on any re-emission of the Room flow. Downstream,
`WorkoutDao.firstExercisePerSession` (`:270-275`) matches on `order_index = (SELECT MIN(...))` and
returns **three rows for one session**; `firstExerciseIdsForSessions`'s `associate`
(`WorkoutRepository.kt:224`) silently keeps whichever came last, so the History row thumbnail/tint
for that session is arbitrary too.

**Should happen:** the whole add should be one serialised operation (a single repository call
taking `List<String>` inside one `withTransaction`, or the `maxOrderIndex+1` read-and-insert inside
a transaction).

Confidence: **high** (multi-select is a shipped, first-class UI affordance — `AddExerciseScreen.kt:
198-206`).

### 2.2 `WorkoutDetailScreen` bounces you straight back out when you return to it — **HIGH**
`WorkoutDetailScreen.kt:54`, `WorkoutDetailViewModel.kt:84-90`

```kotlin
LaunchedEffect(newSessionId) { newSessionId?.let(onSessionStarted) }   // never cleared
```

`WorkoutDetailViewModel` has no `clearNewSessionId()` — compare `WorkoutHomeViewModel.kt:105`,
which has exactly that and `WorkoutHomeScreen.kt:89` which calls it.

**Failure scenario:** open a finished session's detail → tap "Start this routine again" → you land
on the new live session → press Back. The DETAIL back-stack entry re-enters composition, its
`WorkoutDetailViewModel` is retained (same `NavBackStackEntry`), `newSessionId` is still non-null,
the `LaunchedEffect` re-launches and calls `onSessionStarted` again → you are immediately thrown
forward into the session again. The detail screen becomes unreachable until the back-stack entry is
destroyed.

**Should happen:** clear `_newSessionId` after navigating, exactly as `WorkoutHomeScreen` does.

Confidence: **high**.

### 2.3 Editing a builtin exercise, or archiving one, silently does nothing — **HIGH**
`ExerciseFormViewModel.kt:66-83`, `WorkoutRepository.kt:132-133`, `ExerciseDao.kt:39-43`,
`AddExerciseScreen.kt:216-224`

BROWSE mode's overflow offers **Edit** and **Archive** for *every* catalog row
(`AddExerciseScreen.kt:215-225`) — including the ~525 builtins, whose ids are `builtin:<slug>` and
which are **never Room rows** (`ExerciseDao`'s KDoc says so explicitly).

- **Edit:** `ExerciseFormViewModel.save()` calls `repo.resolveExercise(exerciseId)` (which *does*
  resolve builtins, via `ExerciseCatalog.byId` — `WorkoutRepository.kt:110-112`), so `existing !=
  null`, then calls `repo.updateExercise(...)` → `@Update` on the `exercises` table → **updates 0
  rows**. `runCatching` succeeds, `done = true`, the screen pops back, and nothing changed.
- **Archive:** `archiveExercise` → `UPDATE exercises SET is_archived = … WHERE id = :id` → 0 rows.
  The row stays in the library forever, with no error and no toast at all (the call isn't even
  `runCatching`'d — `AddExerciseScreen.kt:221` does `scope.launch { viewModel.archive(...) }`, an
  uncaught throw there would crash rather than report).

**Failure scenario:** open Beast Mode → Exercises → overflow on "Barbell Bench Press" → Edit →
rename to "Bench (comp grip)" → Save. The screen closes as if saved; the list still says "Barbell
Bench Press". Same for Archive.

**Should happen:** either hide Edit/Archive for `id.startsWith("builtin:")`, or fork the builtin
into a real custom `Exercise` row on first edit.

Confidence: **high** (the code path is unambiguous; only the product decision about builtins is
open).

### 2.4 History's week bucketing always uses Monday, regardless of the user's `week_start` — **HIGH**
`WorkoutHistoryViewModel.kt:84-86, 118-131`

```kotlin
private val weekStart = appSettingsRepository.observeSettings()
    .map { it.weekStart }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "MONDAY")
...
val sections = combine(rawSessions, _aggregatesBySession, _firstExerciseBySession,
                       _thumbnailByExerciseId, _filterSessionIds) { ... ->
    bucketByHistorySection(rows, { it.session.localDate }, LocalDate.now(), weekStart.value)
}
```

Two independent defects in the same expression:

1. `weekStart` is `SharingStarted.WhileSubscribed(5_000)` and is **private with zero collectors** —
   nothing ever subscribes to it, so the upstream `observeSettings()` is never collected and
   `weekStart.value` returns the initial `"MONDAY"` for the lifetime of the ViewModel. (Contrast
   `weightUnit` on line 80, which *is* collected by the screen at `WorkoutHistoryScreen.kt:83` and
   therefore works.)
2. Even if it did collect, `weekStart` is **not one of `combine`'s inputs** — it's read as `.value`
   inside the transform, so changing the setting would not re-bucket anything.

**Failure scenario:** a user whose `week_start` is Sunday opens History on a Sunday. That day's
workout is bucketed under "Last week" instead of "This week", and the whole Sunday→Saturday window
is off by one day. `DateTimeUtils.startOfWeek` is being fed a constant.

**Should happen:** `combine(..., weekStart) { ..., ws -> bucketByHistorySection(..., ws) }`.

Confidence: **high** (`WhileSubscribed` + zero subscribers is deterministic).

Secondary, same site: `LocalDate.now()` is evaluated inside the transform, so if the app is left
open across midnight the sections never re-label until something else re-emits. Low severity.

### 2.5 History row stats go permanently stale after a session is edited — **MEDIUM-HIGH**
`WorkoutHistoryViewModel.kt:62-78`

`_aggregatesBySession` / `_firstExerciseBySession` are refreshed only in `rawSessions.onEach { … }`,
and `rawSessions` is `observeRecentSessions` — a query over `workout_sessions` **only**. The
aggregates themselves are computed from `workout_sets`. Nothing observes `workout_sets`. Worse, the
merge is `update { it + agg }`, so an old value is only *overwritten* when a fresh query for that
same id runs — which requires the `workout_sessions` row itself to change.

**Failure scenario:** finish a workout (4,200 kg · 18 sets). Go to History — correct. Go back into
the session via History → Detail → Edit, add two more sets, finish. `workout_sessions.status` is
already `COMPLETED` and `updateSession` writes `status`/`endedAt`… `endedAt` *does* change on
re-finish, so this specific path recovers. But the reliable failure is: edit a **completed**
session's set values (weight/reps) via the session screen *without* pressing Finish again (back
arrow instead). No `workout_sessions` column changes, `rawSessions` never re-emits, and the History
row keeps showing the pre-edit volume/set count until the process restarts.

**Should happen:** derive aggregates from a flow that also observes sets, or at minimum re-query on
screen resume.

Confidence: **medium-high** (mechanism is certain; whether the user can reach "edit without
re-finishing" depends on the back-arrow path, which `WorkoutSessionScreen.kt:150-153` provides).

### 2.6 Rapid "+ Add Set" taps create two sets with the same set number — **MEDIUM-HIGH**
`WorkoutSessionViewModel.kt:170-173`, `WorkoutRepository.kt:308-327`, `WorkoutDao.kt:130-131`

Same shape as §2.1: `addSet` is `safeLaunch`, and `maxSetNumber(workoutExerciseId) + 1` is read
outside any transaction.

**Failure scenario:** with 3 sets logged, double-tap "+ Add Set" quickly. Both coroutines read
`maxSetNumber = 3`, both insert `set_number = 4`. The table renders "Set 4" twice (sorted by
`setNumber`, `WorkoutSessionViewModel.kt:87`), both rows prefill from `previous[4]`, and there is
no delete affordance to clean one up (§2.9). There is no unique index on
`(workout_exercise_id, set_number)` — `WorkoutModel.kt:88-92` declares that pair as a plain
`Index`, not `Index(unique = true)` — so SQLite accepts both.

**Should happen:** wrap the read+insert in `withTransaction`, or make the pair a unique index.

Confidence: **medium-high**.

### 2.7 Weight-unit setting is ignored on four of six weight displays — **MEDIUM**

`app_settings.weight_unit` (KG/LB) is honoured by `WorkoutDetailViewModel`/`WorkoutHistoryViewModel`
for *volume*, and by nothing else in Beast Mode:

| site | code | effect |
|---|---|---|
| `WorkoutHomeScreen.kt:112` | `formatVolume(weeklyStats.volumeKg, parseWeightUnit("KG"))` | the literal string `"KG"` is passed to the parser. The "This week · Volume" tile is **hardcoded to kg** for an LB user. |
| `WorkoutSessionScreen.kt:218` | `value = "${state.stats.totalVolumeKg.toInt()} kg"` | live session Volume tile hardcoded kg, and doesn't even use `formatVolume` (so no thousands separator either). |
| `WorkoutSessionScreen.kt:519-524, 628` | `EditableSetCell(... placeholder = "kg")`, `columnLabel(WEIGHT) = "KG"` | the set table is always kg, entry and header. |
| `WorkoutDetailScreen.kt:163` | `parts += "${s.weightKg} kg"` | per-set lines are kg even though the Volume tile two rows above is in lb. |
| `ExerciseHistorySheet.kt:105` | `parts += "${trimZero(s.weightKg)} kg"` | exercise history sheet always kg. |
| `RoutineEditScreen.kt:164-167` | `targetSummary(..., WeightUnit.KG)` | routine targets always kg, despite `targetSummary`'s own KDoc (`WorkoutLogic.kt:276-278`) saying "`kg`/`lb` follows `app_settings.weight_unit`". |

**Concrete wrong numbers:** a week with 4,200 kg of volume shows `4,200 kg` on the home tile for an
LB user; the same 4,200 kg on the *History* row (which does respect the setting) shows
`9,259 lb` — two screens, same figure, two units, no label mismatch warning. A routine target of
`targetWeightKg = 100f` shows `100 kg` in the editor and `220.5 lb` nowhere.

**Should happen:** thread `weightUnit` through `WorkoutHomeViewModel`, `WorkoutSessionViewModel`,
`RoutineEditViewModel` and `ExerciseHistorySheet` the way `WorkoutDetailViewModel.kt:41-43` already
does.

Confidence: **high** for the mechanism; severity medium because the storage is always correct — it's
a display bug only.

### 2.8 Editing a custom exercise wipes `createdAt` and silently un-archives it — **MEDIUM**
`ExerciseFormViewModel.kt:70-78`

```kotlin
repo.updateExercise(Exercise(
    id = exerciseId, name = s.name.trim(),
    primaryMuscle = …, equipment = …, trackingMode = s.trackingMode,
    isArchived = false,                                // ← always false
    source = existing.source,
    createdAt = System.currentTimeMillis(),            // ← "now", not the original
    notes = existing.notes
))
```

`@Update` replaces the whole row. `CatalogExercise` doesn't carry `isArchived`/`createdAt`, so the
form reconstructs them as constants.

**Failure scenarios:** (a) archive a custom exercise, then edit it from anywhere that can still
reach it — it silently comes back un-archived. (b) any edit rewrites `created_at` to now, which is
the column the definition-sync diff and any future "sort by date added" would read.

**Should happen:** read the `Exercise` row (`exerciseDao().getById`) and `copy()` the changed fields
rather than rebuilding the entity from a `CatalogExercise` projection.

Confidence: **high** on the code; medium on user impact (archive is the only reachable trigger).

### 2.9 There is no way to delete a set, remove a routine exercise mid-list, or reorder anything — **MEDIUM**
Three reorder/delete capabilities exist end-to-end in the data layer but have **zero UI call sites**
(verified by grep over the whole `com/daybook/app` tree):

- `WorkoutSessionViewModel.deleteSet` (`:176`) → `WorkoutRepository.deleteSet` (`:330`) — never
  called. A mistyped or accidentally-added set is permanent for the life of the session.
- `WorkoutRepository.reorderExercises` (`:284-292`) — never called by any ViewModel at all.
- `RoutineEditViewModel.moveExercise` (`:100-106`) — never called by `RoutineEditScreen`; the
  routine editor's rows have no drag handle and no move action, only "Set targets" / "Remove"
  (`RoutineEditScreen.kt:131-134`). Routine exercise order is fixed at insertion order forever.

Also dead: `WorkoutDao.recentExerciseIds` (`:228-233`, documented as powering the picker's "Recent"
section), `WorkoutDao.observeSetsForExercise` (`:117-118`),
`WorkoutRepository.frequentlyLoggedExercises` (`:361-368`, documented as the Add-Exercise default
"Frequently logged" view — the picker shows a flat alphabetical list instead),
`WorkoutSessionViewModel.restEndsAt` (`:133`, exposed but never collected), and
`WorkoutDetailViewModel.DetailState.routineStillExists` / `DetailState.newSessionId` (`:57-58`,
never written and never read).

Confidence: **high** on the grep result; I've classed it MEDIUM because "missing feature" vs "bug"
is a product call — but the KDoc in all three places describes behaviour that does not exist, which
is a documentation defect at minimum.

### 2.10 Typing a decimal into a Sets/Reps/Time target silently discards it — **MEDIUM**
`RoutineTargetSheet.kt:96-102` + `:79-82`

`NumField`'s validator accepts digits **and `.`** for every field:
```kotlin
onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) onChange(v) }
```
but Sets / Reps / Time are parsed with `toIntOrNull()`:
```kotlin
targetSets = sets.toIntOrNull(),
targetReps = reps.toIntOrNull(),
targetDurationSeconds = duration.toIntOrNull(),
```

**Failure scenario:** the user types `3` in Sets, then taps Reps, fat-fingers the `.` key so Reps
reads `12.`, and taps Done. `"12.".toIntOrNull()` is `null`, so the reps target is stored as
**NULL — "no target"** — which is semantically load-bearing in this codebase (Ri3: null ≠ 0), and
`buildSessionFromRoutine` (`WorkoutLogic.kt:461-478`) will create set rows with `reps = null`. The
sheet closes with no complaint; the routine row silently reads "3 sets" instead of "3× 12".
`"1.2.3"` in the Weight field does the same via `toFloatOrNull()`.

**Should happen:** integer fields should reject `.`; any field should reject rather than silently
null on an unparseable value.

Confidence: **high**.

### 2.11 Navigation performed directly in composition, not in an effect — **MEDIUM**
`RoutineEditScreen.kt:68` and `ExerciseFormScreen.kt:60`

```kotlin
if (state.done) onNavigateBack()
```

This calls `navController.popBackStack()` from the composition phase, and re-runs on **every**
recomposition while `state.done` is true. Compose makes no guarantee about how many times a
composable body runs; two recompositions between the state flip and the frame where the screen
leaves the back stack will pop **two** entries.

**Failure scenario:** Home → New routine → Save. Instead of landing back on Beast Mode home, you can
land one screen further back (out of Beast Mode entirely, or on whatever was under it), depending on
timing. Intermittent and hard to reproduce, which is exactly what makes it worth fixing.

**Should happen:** `LaunchedEffect(state.done) { if (state.done) onNavigateBack() }` — the pattern
`WorkoutSessionScreen.kt:125` and `WorkoutHomeScreen.kt:88` already use.

Confidence: **medium-high**.

### 2.12 A typed-but-uncommitted set value is lost when you tap Finish — **MEDIUM**
`WorkoutSessionScreen.kt:566-611` (`EditableSetCell`), `:293` (`FinishFab`)

`EditableSetCell` commits **only** on focus-loss (`:593-596`). Finish is a
`BeastPrimaryButton` (`:409-411`), which uses `clickableImpl` on a `Box` — a plain clickable, not a
focusable/focus-stealing control.

**Failure scenario:** type `82.5` into the last set's KG cell, then (without tapping elsewhere) tap
Finish. If the click does not move focus away from the `BasicTextField`, `onFocusChanged` never
fires, `onCommit` never runs, and the session is finished with `weight_kg = NULL` for that set — the
number the user just typed is gone, with no indication. The same risk applies to the back arrow and
to "+ Add Exercise".

**Should happen:** commit pending cell edits on `DisposableEffect` teardown as well as on
focus-loss, or clear focus explicitly before `viewModel.finish()`.

Confidence: **medium** — depends on whether `clickableImpl` requests focus, which I did not verify
in `ui/components`. Worth a 2-minute on-device check; if it reproduces it is the highest-impact
data-loss bug in the feature.

### 2.13 Whole-row `updateSet` can clobber a concurrent completion toggle — **MEDIUM**
`WorkoutSessionScreen.kt:519-544` + `WorkoutSessionViewModel.kt:175, 177` +
`WorkoutRepository.kt:329, 333-336`

`onUpdateSet(s.copy(weightKg = …))` writes the **entire** `WorkoutSet` row via `@Update`, from a
snapshot `s` captured at composition time. `toggleSetComplete` writes a **single column** via
`UPDATE workout_sets SET completed_at = …`. Both are independent `safeLaunch` coroutines.

**Failure scenario:** with a cell focused and edited, tap that row's green check. The check's click
both (a) moves focus, firing `onCommit` → `updateSet(s.copy(...))` with `s.completedAt == null`, and
(b) launches `toggleSetComplete`. If the toggle lands first, the full-row write immediately
overwrites `completed_at` back to `null` — the check visibly ticks and then un-ticks, and the set
isn't counted in volume/sets.

**Should happen:** make the set-cell commits column-scoped (`UPDATE workout_sets SET weight_kg = …`)
like `setCompletedAt` already is.

Confidence: **medium** (ordering-dependent, but both coroutines are dispatched from the same tap).

### 2.14 The History empty state's action button does nothing — **LOW-MEDIUM**
`WorkoutHistoryScreen.kt:99-107`

```kotlin
actionLabel = "Start an empty workout",
onAction = { /* landing owns starting a session */ },
```

A new user's very first visit to History shows a prominent, fully-styled CTA that is a no-op.
Tapping it produces no navigation, no toast, nothing.

**Should happen:** remove the `actionLabel`, or navigate to the Routines page / call the start path.

Confidence: **high** (trivially verifiable from the source).

### 2.15 The rest-timer sheet always shows "Off" as the current value — **LOW-MEDIUM**
`WorkoutSessionScreen.kt:299-315`

```kotlin
SortSheet(
    ...
    selectedSortKey = "0",   // hardcoded
```

`"0"` is the `Off` option. The block's actual `restSeconds` (`blockUi.block.restSeconds`, which the
card header renders correctly on `:458`) is never passed.

**Failure scenario:** set a block's rest to 90s, reopen the rest sheet — the tick sits on "Off". The
user cannot tell what the current value is from the picker, and "Off" looks selected on a block that
is clearly counting down.

Confidence: **high**.

### 2.16 The session screen's sticky bar gets double IME padding — **LOW-MEDIUM**
`WorkoutSessionScreen.kt:134` + `beast/BeastComponents.kt:244`

The root `Box` already has `.imePadding()` (`:134`, added deliberately per the comment there), and
`BeastStickyBar` — aligned `BottomCenter` *inside* that already-shrunk Box — applies
`.imePadding()` again (`BeastComponents.kt:244`).

**Failure scenario:** focus any set cell. The Discard/Finish bar floats roughly one keyboard-height
above the keyboard instead of sitting on top of it, leaving a large empty gap and hiding the list
rows the user was trying to reach.

**Should happen:** drop the `imePadding()` from one of the two. (`StickySaveBar`, the shared
component `BeastStickyBar` is modelled on, is used at `RoutineEditScreen.kt:114` and
`ExerciseFormScreen.kt:84` with an explicit `Modifier.imePadding()` from the *caller* — so
`BeastStickyBar` owning it internally is the inconsistency.)

Confidence: **medium-high**.

### 2.17 `ExerciseHistorySheet` can award a spurious PR badge — **LOW**
`ExerciseHistorySheet.kt:74-84` vs `WorkoutDao.kt:219-225`

The query orders `ws.started_at DESC, s.set_number ASC`. The sheet does `rows.asReversed()` to walk
"oldest → newest", but reversing that ordering gives `started_at ASC, set_number **DESC**` — sets
*within* a session are walked backwards.

**Failure scenario:** previous best 90 kg. In one session you do Set 1 = 100 kg, Set 2 = 95 kg. The
walk sees Set 2 first (95 > 90 → PR, best = 95), then Set 1 (100 > 95 → PR). **Both** get a PR
badge. Correct behaviour: only Set 1.

**Should happen:** sort explicitly (`rows.sortedWith(compareBy({ sessionStart }, { setNumber }))`)
rather than relying on `asReversed()` of a two-key ordering.

Confidence: **high** on the logic; low severity (cosmetic badge).

### 2.18 `duplicateRoutine`'s shadowed `newId` — **LOW (not a bug, but one rename away from one)**
`WorkoutRepository.kt:420, 433`

```kotlin
val newId = newId()                                     // :420 — shadows the fun newId()
...
originalExercises.map { it.copy(id = newId(), routineId = newId, createdAt = now) }   // :433
```

`newId()` (the function) and `newId` (the val) coexist on line 433. It resolves correctly today —
Kotlin picks the function for the invocation and the property for the bare reference — but it reads
as a bug and any refactor that turns `newId()` into a property makes every duplicated routine
exercise share one id (and `REPLACE` would then collapse them to a single row). Worth renaming.

Confidence: high that it currently works; flagged as a latent hazard.

---

## 3. Smaller observations

- **`ExerciseHistorySheet` shows the previous exercise's history for a frame.**
  `ExerciseHistorySheet.kt:55` — `var rows by remember { … }` has no key, while the reload is
  `LaunchedEffect(exerciseId)`. Opening history for exercise B right after A briefly renders A's
  sets. Also, `viewModel.load()` is called bare inside the `LaunchedEffect` — a DB throw there is
  *uncaught* (composition coroutine scope), which crashes the process rather than going through
  `safeLaunch`.
- **`HevyImporter.doImport:102-108`** — the `if (parsed.skippedRows > 0) … else …` branches return
  the identical string. Dead branch / copy-paste.
- **Hevy: rows with a blank `exercise_title` vanish without being counted.**
  `HevyCsvParser.kt:109` — `?: continue` skips the row but does not increment `skipped`, so the
  import summary under-reports what it ignored. Similarly `HevyImporter.kt:204`'s
  `return@forEachIndexed` (already noted by the first report) also leaves a **gap in `orderIndex`**,
  since `index` comes from `forEachIndexed` over the unfiltered list.
- **Hevy session grouping is contiguity-based** (`HevyCsvParser.kt:140-151`). A file whose rows for
  one session are not adjacent yields two `ParsedSession`s with the *same* `(startedAt, endedAt)`;
  the first imports, the second is then counted as a duplicate by `partitionDuplicates`
  (`HevyImporter.kt:244-254`) and **its sets are lost** while the summary reports "skipped 1 already
  in Daybook". Hevy's own export is sorted, so this needs a hand-edited file to trigger.
- **`CsvReader` drops a final row consisting only of empty quoted fields.** `CsvReader.kt:93` —
  `if (field.isNotEmpty() || row.isNotEmpty())`. A last line of `""` leaves both empty, so the row
  is discarded. Genuinely unreachable for a Hevy export (10 required columns), noted for
  completeness.
- **`CsvReader.parse` silently collapses duplicate header names** (`:28`, `associate` keeps the
  last). A Hevy export with two `notes` columns would lose one.
- **`WorkoutDao.recentExerciseIds` (`:228-233`) is `SELECT DISTINCT we.exercise_id … ORDER BY
  ws.started_at DESC`** — ordering by a column not in the DISTINCT projection makes the result
  order undefined. Moot today since the query is dead code (§2.9).
- **`sessionAggregates` counts exercises and sets on different bases** (`WorkoutDao.kt:257-264`):
  `COUNT(DISTINCT exercise_id)` over *all* sets, `setCount`/`totalVolumeKg` over *completed* sets
  only. A session where nothing was ticked renders "0 sets · 5 exercises" — though
  `WorkoutHistoryScreen.kt:313` gates the whole line on `setCount > 0`, so it's currently invisible.
- **`getSetsForSessions` / `sessionAggregates` / `getExercisesForSessions` are not chunked** despite
  their KDoc saying "chunked at 900 bound vars by the caller"
  (`WorkoutRepository.kt:208-209, 217-219`, `WorkoutDao.kt:76, 123`). `HISTORY_LIMIT = 200` keeps
  History safe, and the weekly stat grid is a week's worth — so no reachable overflow today, but the
  documented invariant is not actually implemented anywhere.
- **`bestSetForExercise` (`WorkoutDao.kt:177-189`) does not join `workout_sessions`,** so a set
  ticked in a *different, still-ACTIVE* session counts as the pre-session personal best.
  `bestSetForExerciseBefore` (`:193-206`) does join and does filter. Minor inconsistency.
- **`currentStreakDays` is capped at ~60 days** by its caller: `WorkoutHomeViewModel.kt:73-76` only
  fetches 60 days of sessions. A 70-day streak displays as 60.
- **`prCelebration` uses positional `remember`** (`BeastComponents.kt:213-214`) inside
  `blockUi.sets.forEach` (`WorkoutSessionScreen.kt:491`), which is a plain loop, not a keyed
  `items`. Set rows shifting position would mis-attribute the pulse animation. Unreachable today
  because sets can't be deleted (§2.9).
- **`EditDurationDialog` hour field has no upper clamp** (`WorkoutSessionScreen.kt:381`,
  `v.length <= 3`) — already noted by the first report; still true, still cosmetic.
- **Per-keystroke DB write for block notes.** `WorkoutSessionScreen.kt:449` —
  `onValueChange = { notesText = it; onNotesChange(it) }` fires `updateExerciseNotes` on **every
  character**, contradicting the "save on focus-loss, never per-keystroke" rule the same file's
  `EditableSetCell` KDoc states (`:559-565`).
- **`WorkoutHistoryViewModel.availableExercises` is a public `MutableStateFlow`** (`:90`) — any
  caller can write to it. Every other flow in the feature is `asStateFlow()`-wrapped.
- **`WorkoutDetailViewModel.startRoutineAgain` swallows the failure entirely** (`:87-90`,
  `.getOrNull()`) — not even a Crashlytics report, unlike every sibling. "Start this routine again"
  can do nothing at all, silently.
- **`WorkoutDetailScreen.formatDetailSetLine` prints raw `Float.toString()`** (`:163`): a 60 kg set
  renders as `60.0 kg`. Every other display path trims the trailing `.0`
  (`ExerciseHistorySheet.trimZero`, `WorkoutSessionScreen.formatEditableNumber`).
- **`formatPrevious` drops a weight-only PREVIOUS value.** `WorkoutSessionScreen.kt:635-646` — the
  `when` tests `w != null && r != null`, then `r != null`, then duration, then distance. A previous
  set with `weightKg = 100f, reps = null` (perfectly producible by a Hevy import, or by filling only
  the KG cell) falls through every branch and renders as `–`. Low, but it's a real "the data is
  there and we show a dash".
- **`WorkoutDetailViewModel.sessionId` defaults to `""`** (`:45`) rather than `checkNotNull` as
  `WorkoutSessionViewModel.kt:61` does; a missing arg yields a permanently blank detail screen
  instead of a loud failure.

---

## 4. Checked and ruled out

- **`MIGRATION_21_22` vs `WorkoutModel.kt` — clean.** I diffed every column, type, nullability,
  `DEFAULT` and index by hand across all six tables. Every `@ColumnInfo(defaultValue = …)` has a
  byte-identical `DEFAULT` in the SQL (`is_archived 0`, `source 'USER'`/`'MANUAL'`,
  `status 'ACTIVE'`, `set_type 'NORMAL'`); every column *without* a Kotlin `defaultValue` correctly
  has no SQL `DEFAULT`; every nullable Kotlin property maps to a column with no `NOT NULL`
  (including all five `workout_routine_exercises` target columns and all six `workout_sets` value
  columns, per Ri3/R18); `Exercise.notes`, `WorkoutExercise.superset_id`, `WorkoutSession.routine_id`
  and `rpe` are all present and nullable; all eight indices match the `@Entity(indices = …)`
  declarations by name and column order. The `app_settings` additions are all `NOT NULL DEFAULT`
  except `default_exercise_group`, which is deliberately nullable with no default. **No mismatch
  found** — Room's identity hash would fail at open otherwise, so this is consistent with the app
  running.
- **`safeLaunch` / `recordUnhandledException` (`util/ViewModelExt.kt`)** — correct, and both the
  `Log.e` and the Crashlytics call are individually `runCatching`'d so the reporter can't become a
  second crash. Confirms the first report.
- **`previousBySetNumber` (`WorkoutLogic.kt:46-49`)** — correct given the DAO's `ORDER BY
  ws.started_at DESC` (`WorkoutDao.kt:164-174`): the first row is guaranteed to belong to the newest
  qualifying session, and the filter is by `sessionId`, not by position. The `LIMIT 50` can only
  truncate if one prior session logged >50 sets of one exercise.
- **`resolveRestSeconds` (`WorkoutLogic.kt:411-412`)** — `appDefaultRestSeconds.takeIf { it > 0 }`
  correctly turns a `0` app default into `null`/OFF rather than a zero-second timer.
- **`buildSessionFromRoutine` (`:421-481`)** — `targetSets == null → 0` set rows is the documented
  Ri3 behaviour, not an off-by-one. Block `orderIndex` is copied from the routine row's own
  `orderIndex` (not the loop index), which is the right choice for preserving gaps.
- **`uniqueRoutineName` (`:266-273`)** — the `while` loop correctly skips past an existing
  "Push day 2" to produce "Push day 3"; case- and whitespace-normalised on both sides.
- **`kgToLb` (`:23`)** — `Math.round(kg * 2.2046226f * 2f) / 2f` correctly rounds to the nearest
  0.5 lb; `Math.round(Float)` returns `Int`, and `Int / 2f` is `Float`, so no integer-division trap.
- **`BeastAccentColor.fromKey` (`BeastTheme.kt:45`)** — falls back to `CORAL` for any
  unrecognised/null key. Confirms the first report.
- **`RingStat` clamps `progress` to 0..1** (`BeastComponents.kt:81`) and the "fills and stays full
  past 60 min" behaviour is documented intent, not a clamping bug. `durationRingProgress`
  (`WorkoutDetailScreen.kt:150-154`) uses the same `/3600f` baseline — consistent.
- **`CsvReader`'s CR / CRLF / lone-CR handling (`:80-86`)** — I traced the index arithmetic for all
  three cases; each advances correctly and emits exactly one row. Quoted fields containing commas,
  `""` escapes and embedded newlines are all handled correctly.
- **`HevyCsvValidator`** — correctly case- and whitespace-insensitive, and correctly does *not*
  require the optional columns.
- **`exerciseSearchRank` / `levenshtein` (`WorkoutLogic.kt:173-245`)** — the DP is correct (the
  `prev`/`curr` row copy at `:241` is a real copy, not an aliasing bug), and
  `maxEditDistanceFor(len > 8) = 0` correctly disables fuzzy matching for long queries rather than
  matching everything.
- **`observeRoutineSummaries` (`RoutineDao.kt:24-33`)** — `COUNT(re.id)` (not `COUNT(*)`) over a
  LEFT JOIN correctly yields `0` for an exercise-less routine, and `COALESCE(SUM(target_sets), 0)`
  correctly yields `0` rather than NULL. The `WHERE r.is_archived = 0` is applied to the routine,
  not post-aggregation, which is correct.
- **`RoutineEditViewModel.save()`'s `busy` guard (`:112-115`)** — correctly prevents a double-save;
  `moveExercise`'s `from/to !in list.indices` guard (`:102`) is correct.
- **`deleteSession` / `removeExerciseFromSession` / `updateRoutine`** — all correctly wrapped in
  `withTransaction` with children deleted before parents (`WorkoutRepository.kt:174-180, 271-282,
  399-415`). This is the FK-free "cascade" done right.
- **`WorkoutRoutes`** — every constant is used consistently; `ALL` and `NAV` are complete, and the
  `session()`/`detail()`/`routineEdit()` builders match their route patterns exactly.
- **`ExerciseThumbnail`'s failure path (`:40-47`)** — `remember(imageId)` correctly resets `failed`
  when the image changes, and `onError` falls back to the icon tile rather than a broken glyph.
- **`isPersonalRecord` (`WorkoutLogic.kt:57-72`)** — `best == null → false` is deliberate and
  documented; the WEIGHT_REPS tie-break (`cw == bw && cr > br`) is correct.
