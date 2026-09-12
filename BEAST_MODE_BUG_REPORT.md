# Beast Mode (Workout) — Bug & Silent-Failure Audit

Scope: `ui/workout/**`, `ui/workout/beast/**`, `data/WorkoutRepository.kt`, `data/workout/**`.
Method: full read of every file in the workout feature (ViewModels, screens, repository, pure
logic, Hevy importer, migrations). General app-wide `catch` sites were also scanned; nothing
outside workout rose to the same severity, so this report stays scoped to Beast Mode as asked.

The codebase is in good shape overall — `safeLaunch` (see `util/ViewModelExt.kt`) already turns
"crash the app" into "log + Crashlytics", which is the right instinct. The bugs below are the next
layer down: cases where an action quietly does nothing, or does the wrong thing, without crashing
and without telling the user.

---

## 1. Confirmed bugs

### 1.1 `WorkoutHomeViewModel.errorMessage` is produced but never shown — HIGH
**Files:** `WorkoutHomeViewModel.kt:109-141`, `WorkoutHomeScreen.kt`

`startRoutine()` and `deleteRoutine()` both set `_errorMessage.value = "Couldn't start/delete..."`
on failure. `WorkoutHomeScreen` never collects `viewModel.errorMessage` anywhere — `grep` confirms
zero references to `errorMessage` in the screen file. Compare with `RoutineEditScreen`, which
*does* render its ViewModel's `rejectedMessage` (`RoutineEditScreen.kt:115-116`) — that's the
established pattern elsewhere in this feature, so this is a real oversight, not a deliberate
omission.

**Effect:** if starting a routine or deleting a routine throws (DB error, routine already deleted
on another device, etc.), the user sees nothing. The routine list just doesn't update and there is
no error, no retry, no explanation.

**Fix:** collect `errorMessage` in `WorkoutHomeScreen` and show it (e.g. via `UndoSnack`/a toast,
mirroring `deletedToken`), same as `RoutineEditScreen` does for `rejectedMessage`.

### 1.2 "Start an empty workout" has no failure path at all — MEDIUM
**File:** `WorkoutHomeViewModel.kt:112`

```kotlin
fun startEmptyWorkout() = safeLaunch { _newSessionId.value = repo.startEmptySession() }
```
Unlike `startRoutine`, this isn't wrapped in `runCatching`, so any exception falls through to
`safeLaunch`'s default `onError` — which only logs to Crashlytics. `_newSessionId` is never set, so
`LaunchedEffect(newSessionId)` in the screen never fires and nothing happens. Tapping the button
just... does nothing, with zero on-screen feedback.

**Fix:** same `runCatching` + `_errorMessage` treatment as `startRoutine`.

### 1.3 `duplicateRoutine()` failure is recorded but never surfaced — LOW/MEDIUM
**File:** `WorkoutHomeViewModel.kt:131-134`

```kotlin
fun duplicateRoutine(routineId: String) = safeLaunch {
    runCatching { repo.duplicateRoutine(routineId) }
        .onFailure { com.daybook.app.util.recordUnhandledException(it) }
}
```
On failure this only reports to Crashlytics — no `_errorMessage` set, no toast. "Duplicate" from
the overflow menu can silently fail with the user never knowing it didn't work.

### 1.4 `addSet` prefill can target the wrong set number after a mid-session delete — MEDIUM
**File:** `WorkoutSessionViewModel.kt:170-174`

```kotlin
fun addSet(blockId: String, exerciseId: String) = safeLaunch {
    val prevMatch = state.value.blocks.firstOrNull { it.block.id == blockId }
        ?.let { it.previous[it.sets.size + 1] }
    repo.addSet(blockId, sessionId, exerciseId, prevMatch)
}
```
This predicts the new row's set number as `sets.size + 1` to look up the PREVIOUS-session value to
prefill with. But `WorkoutRepository.addSet` independently computes the *real* set number as
`maxSetNumber(workoutExerciseId) + 1` (`WorkoutRepository.kt:308`). These two only agree when set
numbers are contiguous from 1. If a set in the middle of the block was ever deleted (`deleteSet`
does not renumber the rest), `sets.size` undercounts the true max, so the prefill is pulled from
the wrong `PREVIOUS` set number — e.g. after deleting set 2 of a 3-set block (`sets = [1, 3]`,
`size = 2`), the next add computes prefill from `previous[3]` but the repository actually inserts
it as set 4. The new row silently gets the wrong previous weight/reps pre-filled. No crash, no
error — just quietly wrong data shown to the user.

**Fix:** have the ViewModel ask the repository for the next set number (or have
`addSet`/`previousSetsForExercise` be keyed consistently), rather than re-deriving it from
`sets.size` on the UI side.

### 1.5 `finish()` reports success even if the session row is already gone — LOW
**File:** `WorkoutRepository.kt:164-167`, `WorkoutSessionViewModel.kt:183-186`

```kotlin
suspend fun finishSession(id: String) {
    val session = database.workoutDao().getSession(id) ?: return   // silent no-op
    ...
}
...
fun finish() = safeLaunch {
    repo.finishSession(sessionId)
    _finished.value = true   // always "succeeds" from the UI's point of view
}
```
If the session has already been deleted (race with a concurrent discard, or a sync-driven
eviction), `finishSession` silently no-ops, but the ViewModel still flips `_finished = true` and
the screen navigates away as if the workout was saved. The same shape applies to
`renameSession`/`setSessionElapsedSeconds` (`WorkoutRepository.kt:185-198`) — both silently no-op
on a missing id with no signal back to the caller. Low real-world likelihood, but it's a genuine
"reports success when nothing happened" path.

### 1.6 Most session actions have no user-visible failure path — MEDIUM (systemic)
**File:** `WorkoutSessionViewModel.kt` (`addSet`, `updateSet`, `deleteSet`, `toggleSetComplete`,
`removeExercise`, `setExerciseNotes`, `setExerciseRest`, `reorderExercises`, `discard`)

All of these are plain `safeLaunch { repo.xyz(...) }` with no `runCatching`/error state — which is
fine for a best-effort write, except nothing in the screen ever reflects "this didn't save." If any
of these throws (a constraint violation, a disk-full Room write, a null coercion), the action is
logged to Crashlytics and otherwise vanishes: the checkbox the user tapped stays unchecked, the
weight they typed doesn't persist, and there is no toast, no retry affordance, nothing to tell them
the tap didn't take. This is the exact shape of "silent failure" the prompt asked about — it's
pervasive by design (every workout mutation goes through `safeLaunch`'s default `onError`), not a
one-off bug, so it's worth a conscious decision rather than a fix-in-place: either add a shared
"couldn't save that change" snackbar wired to a `safeLaunch` error callback, or explicitly accept
that these are fire-and-forget local DB writes where failures are expected to be vanishingly rare.

---

## 2. Smaller / lower-confidence observations

- **`HevyImporter.doImport`** (`HevyImporter.kt:204`): `val exerciseId = nameToId[block.exerciseTitle] ?: return@forEachIndexed` skips a block (and its sets) entirely if name resolution ever misses — shouldn't happen since `nameToId` is built from the same `toImport` sessions, but if it ever does, the session is still counted as "imported" with fewer sets than the file actually had, and the summary text doesn't mention it.
- **`WorkoutSettingsViewModel`** (all setters): plain `safeLaunch`, no error feedback — consistent with §1.6, lower stakes since these are simple preference writes.
- **`EditDurationDialog`** (`WorkoutSessionScreen.kt:392-394`): hours field allows up to 3 digits (`v.length <= 3`) with no upper clamp, so a session duration of e.g. 999h is accepted without validation. Cosmetic only (`setElapsedSeconds` just re-anchors `startedAt` into the far past).

---

## 3. What's *not* a bug (checked and ruled out)

- `safeLaunch`'s own exception handling (`ViewModelExt.kt`) is solid — it reports to Crashlytics and guards its own logging/reporting calls so a failure there can't cascade into a second crash.
- The v21→v22 Room migration that introduces all of Beast Mode's tables (`Migrations.kt:513-610`) is additive-only, correctly leaves routine/session target columns nullable (so "no target" isn't confused with "target of zero"), and is covered by `MigrationTest`.
- The elapsed-time ring's "fills past 60 minutes and just stays full" behavior (`BeastComponents.kt` `RingStat`, `WorkoutSessionScreen.kt:170`) is intentional per its own doc comment, not a clamping bug.
- `BeastAccentColor.fromKey` falls back to a default for any unrecognized/blank stored key, so a corrupt settings value can't crash the accent picker.
