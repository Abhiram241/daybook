# Daybook — App-Wide Bug & Silent-Failure Audit

Scope: everything **outside** the Beast Mode workout feature, which `BEAST_MODE_BUG_REPORT.md`
already covers — `ui/foodmed/**`, `ui/routines/**`, `ui/journal/**`, `ui/home/**`, `ui/detail/**`,
`ui/respond/**`, `ui/account/**`, `ui/lock/**`, `data/sync/**`, `data/ExportImportRepository.kt`,
`data/backup/**`, `data/auth/**`, `data/lock/**`, `util/alarm/**`, `util/notification/**`,
`util/work/**`, `data/OccurrenceScheduler.kt`, `util/streak/**`, `ui/MainActivity.kt`,
`ui/settings/**`. Method: full read of each file, plus a mechanical sweep for (a) raw
`viewModelScope.launch`, (b) `MutableStateFlow`s whose public `StateFlow` no Composable collects,
(c) `catch`/`runCatching` sites that discard both the error and the user-visible outcome.

The same overall verdict as the workout report holds: this codebase is unusually well-hardened, and
most of what a generic audit would flag has already been found and fixed by name (the
`LOGIN_REDESIGN_RISK_FIX_PLAN` phases). The findings below are the layer those passes did not
reach — three of them are **silent data destruction**, not just missing feedback.

One structural note that recurs below: several fixes from the C-4 ("reports saved over data that
was dropped") and C-18 ("stop swallowing exceptions") phases were applied to *some* call sites of a
pattern and not others. Where that's the case it is called out explicitly, because the fix already
exists in-tree and the gap is a missed call site rather than a design question.

---

## 1. Confirmed bugs

### 1.1 A remote month apply silently destroys unpushed local **workout sessions** — CRITICAL
**Files:** `data/ExportImportRepository.kt:816-834`, `data/sync/CloudSyncRepository.kt:1017`

`importMonth` is the merge that every remote month doc lands through
(`applyRemoteMonth` → `exportImport.importMonth(month, days)`). For *occurrences* it is a careful
per-log merge — `mergeMonth` (`ExportImportRepository.kt:1169`) deliberately **keeps** a local
resolved row the cloud does not name, because that row is this device's unpushed offline edit. That
is the whole point of the S15 work.

The workout tables get the opposite treatment in the same transaction:

```kotlin
// A4 (§4.4 item 4): workout sessions merge by full delete-then-insert over this
// month's local_date range — a session is simply present or not (no PENDING/
// resolved concept like an occurrence has), so there is nothing to diff.
val staleSessionIds = database.workoutDao()
    .getSessionsInLocalDateRange(monthStartYmd, monthEndYmd).map { it.id }
staleSessionIds.chunked(SQLITE_MAX_VARS).forEach {
    database.workoutDao().deleteSetsForSessions(it)
    database.workoutDao().deleteExercisesForSessions(it)
}
database.workoutDao().deleteSessionsInLocalDateRange(monthStartYmd, monthEndYmd)
if (incomingSessions.isNotEmpty()) database.workoutDao().insertSessions(incomingSessions)
```

Every workout session in the month is deleted and replaced by exactly what the incoming payload
carries. "Nothing to diff" is true for a session that both sides know about; it is *not* true for a
session only this device has.

**Failure scenario.** Phone finishes a workout at 10:00. The push is debounced 3 s, or the phone is
offline, or the app is killed before `SyncFlushWorker` runs. Meanwhile the tablet (or the same
account on any other device) ticks a habit and pushes that month. The month doc arrives on the
phone via `scopedMonthsListener` → `shouldApplyMonthDoc` is true (different `deviceId`, higher
`revision`) → `applyRemoteMonth` → `importMonth` → **every workout session for that month,
including this morning's, is deleted**, and only the tablet's payload is reinserted. The workout is
gone from Room. Worse, `applyRemoteMonth` then stores the remote hash and the S4 re-export
comparison pushes the *post-delete* state back up, so the deletion propagates to the cloud and to
every other device. No exception, no log line about it, nothing on screen.

The same path is reached deterministically by `syncNow()` (`CloudSyncRepository.kt:456-469`), which
by design **pulls before it pushes** — so a user who logs a workout offline, comes back online and
taps "Sync now" loses it in the pull, before their own push ever runs.

An **in-progress** session is destroyed the same way. The live `WorkoutSessionViewModel` then keeps
writing sets against a session id that no longer exists, which is precisely the "reports success
when nothing happened" no-op class the Beast Mode report documented at its §1.5 — so the user sees
a workout screen that accepts taps and saves nothing.

**Fix direction:** give workouts the same keep-local-if-not-named rule the occurrence merge already
has — only delete a session id the incoming payload *also* knows about (or that has already been
pushed, i.e. is covered by `syncState.monthHashes`), never one that exists solely locally. At
minimum, never delete a session whose `status` is not terminal.

### 1.2 "Account deleted" (and an optional full local wipe) when nothing was deleted — HIGH
**Files:** `data/auth/AuthRepository.kt:133`, `:147`, `ui/account/AccountViewModel.kt:155-189`

Both auth mutators use a null-safe call and then return `Success` unconditionally:

```kotlin
suspend fun reauthenticateWithGoogle(activityContext: Context): AuthOutcome {
    ...
    auth.currentUser?.reauthenticate(GoogleAuthProvider.getCredential(idToken, null))?.awaitCompat()
    AuthOutcome.Success          // reached when currentUser == null — nothing happened
}

suspend fun deleteAccount(): AuthOutcome = try {
    auth.currentUser?.delete()?.awaitCompat()
    ...
    AuthOutcome.Success          // same shape
}
```

When `auth.currentUser` is null the safe-call short-circuits, no exception is thrown, and the
caller is told the operation succeeded. `AccountViewModel.deleteAccount` then takes the
`AuthOutcome.Success` branch:

```kotlin
if (alsoEraseLocal) runCatching { cloudSync.wipeAllLocalData() }
_form.update { AccountForm(message = "Account deleted." + ...) }
```

**Failure scenario.** The user opens Account → Delete account with "also erase local data" ticked.
`deleteRemoteDoc()` runs first and **permanently deletes all their Firestore data** (by design —
S-2). Between that and `deleteAccount()`, the session goes away: `AuthRepository`'s own
`IdTokenListener` calls `auth.signOut()` on a revoked token (`AuthRepository.kt:82`), or the token
refresh races the delete. `currentUser` is now null, `deleteAccount()` returns `Success`,
`wipeAllLocalData()` erases all eight Room tables and cancels every alarm, and the UI says "Account
deleted. Local data erased." The Firebase account still exists. The cloud doc is gone, the local
data is gone, and the user was told this was the intended outcome.

**Fix direction:** `val user = auth.currentUser ?: return AuthOutcome.Error(...)` in both methods —
never let a null user resolve to `Success`.

### 1.3 `RespondViewModel.resolve {}` swallows every exception with no log and reports success — HIGH
**File:** `ui/respond/RespondViewModel.kt:222-229`

```kotlin
private inline fun resolve(crossinline action: suspend () -> Unit) {
    if (_state.value.busy) return
    _state.update { it.copy(busy = true) }
    safeLaunch {
        runCatching { action() }                       // result discarded entirely
        _state.update { it.copy(busy = false, done = true) }
    }
}
```

`complete()` and `skip()` both go through this. The `runCatching` has no `onFailure`, so a thrown
exception reaches neither the user, logcat, nor Crashlytics — it is strictly worse than the
`safeLaunch`-default sites the Beast Mode report catalogued, which at least record. `done = true`
fires regardless, and `RespondScreen` navigates away on `done`.

The in-file comment justifies this with "those never reject" — true of `LogResult`, but these
actions can still (a) throw a `SQLiteException` on a disk-full/locked write, and (b) *silently
no-op*: `OccurrenceScheduler.resolveHabit` (`OccurrenceScheduler.kt:633`) and `skipFoodMed`
(`:598`) both `return@withLock` on a missing occurrence row with no signal to the caller. Either
way, the user taps Complete on a reminder opened from a notification, the screen closes as if it
worked, and the occurrence is still PENDING. The refire chain then re-nags them for something they
believe they answered.

Note `undo()` and `log()` in the same file were both *specifically* fixed for exactly this (ROUND 0
C9 and Phase 9 C-4 respectively). `resolve {}` is the one left behind.

### 1.4 Every Today-screen backfill discards its `LogResult` — the C-4 fix never reached Home — HIGH
**Files:** `ui/home/HomeViewModel.kt:742-764`, `:789-809`; `data/OccurrenceScheduler.kt:728-761`

Phase 9 (C-4) threaded `LogResult` out of `backfillFoodMed` / `backfillHabitJournal` / `logFoodMed`
so a rejection ("that month isn't loaded yet", "that date can't be logged") would stop the caller
saying "saved". `RespondViewModel` and `HabitJournalChatViewModel` consume it. `HomeViewModel` does
not — in three places:

```kotlin
fun skipItem(item: HomeItem) = safeLaunch {
    when {
        item.isBackfill && item.isHabit -> occurrenceScheduler.backfillHabit(...)   // returns Unit
        item.isBackfill -> occurrenceScheduler.backfillFoodMed(...)                 // LogResult dropped
        ...
    }
}

fun replyToItem(...) = safeLaunch {
    ...
    item.isBackfill -> occurrenceScheduler.backfillFoodMed(...)                     // LogResult dropped
    item.occurrenceId != null -> occurrenceScheduler.logFoodMed(...)                // LogResult dropped
}
```

and `backfillHabit` (`OccurrenceScheduler.kt:728`) — used by `completeItem` and `skipItem` — still
returns `Unit`, so it *cannot* report. It has three silent `return@withLock` early exits (habit
gone, `canBackfill` rejection, month not resident), each with only a `Log.w`.

**Failure scenario.** The user scrubs the week strip back to a past day in an evicted month (a
month `evictStaleMonths` dropped, which is normal), types a reply into an intake card and taps
send. `monthResident(date)` is false, `backfillFoodMed` returns
`Rejected("That month isn't loaded yet — connect and retry.")`, `HomeViewModel` throws the result
away, nothing is written, and the card simply stays as it was. There is no toast, no error, no
`monthReady` gate on the reply path — the user has no way to know the entry was dropped, and
re-typing it produces the same nothing.

`HomeViewModel` already owns the machinery for this (`_undoFeedback` + `UndoSnack`); the rejection
message just needs routing into it, and `backfillHabit` needs the same `LogResult` return its two
siblings got.

### 1.5 Notification Skip / Snooze / Complete fail silently *and* dismiss the notification — HIGH
**File:** `util/alarm/NotificationActionReceiver.kt:59-114`

```kotlin
} catch (t: Throwable) {
    Log.e(TAG, "action $action failed", t)      // logcat only — no recordUnhandledException
    if (isFoodMedReply && notificationId != 0) { ... }
} finally {
    if (notificationId != 0 && !isFoodMedReply) {
        runCatching { notificationUtils.cancelNotification(notificationId) }   // always
    }
    pending.finish()
}
```

Two problems, compounding:

1. The catch does **not** call `com.daybook.app.util.recordUnhandledException(t)`. `AlarmReceiver`
   (`AlarmReceiver.kt:63`, `:73`) and `WindowRefreshWorker` (`WindowRefreshWorker.kt:44`) were both
   given that call by name in Phase 10 (C-18) as "the two named AlarmReceiver/worker
   `catch(Throwable)` sites"; this third receiver — which handles *every* user action on a
   notification — was missed. A failing Skip/Snooze/Complete never reaches the dashboard.
2. The `finally` cancels the notification **unconditionally**, including on the failure path. The
   food/med Reply case was deliberately excepted (N-6, with `postReplyFailed` as the honest
   counterpart); Skip / Snooze / Complete were not. So on failure the reminder vanishes from the
   shade exactly as it does on success.

**Failure scenario.** The user taps Snooze during a heavy sync. `snoozeFoodMed` contends on
`syncMutex` past the 8 s `withTimeout` and throws `TimeoutCancellationException`. The occurrence is
never snoozed and no new alarm is armed. The notification disappears anyway. The reminder is now
silently dead for the day, with no Crashlytics event and nothing the user could have noticed.

The `ACTION_BATCH_DONE` / `ACTION_BATCH_SNOOZE` branch above it (`:29-49`) has the identical shape:
logcat-only catch, and a `finally` that cancels `BATCH_NOTIFICATION_ID` whether
`completeAllBatchToday()` succeeded or not.

### 1.6 A confirmed-empty cloud month gets stranded "not loaded" for the rest of the session — MEDIUM
**Files:** `data/sync/CloudSyncRepository.kt:649`, `:1067-1105`, `ui/home/HomeViewModel.kt:428-447`

`doPush` ends by **replacing** the resident set, rather than growing it:

```kotlin
syncState.hydratedMonths = curHashes.keys + MonthPartitioner.recentMonths()
```

`curHashes` only contains months that have `DayEntry` rows in the current export. A month that
hydrated successfully but is *genuinely empty* on the server has no entry, so it is dropped from
`hydratedMonths` by the first subsequent push. (`onLocalDataReplaced` was explicitly changed to a
union for exactly this hazard — see its S-13 comment at `:830-844` — but `doPush` still narrows.)

`ensureMonthHydrated` cannot repair it:

```kotlin
if (month in syncState.hydratedMonths) return
if (!hydrationAttempted.add(month)) return        // already attempted this session → give up
```

`hydrationAttempted` still holds the month from the successful first pass, and that set is only
cleared on eviction, sign-out or auth change.

**Failure scenario.** The user scrubs back to a past month they have no data for. `ensureMonthHydrated`
fetches, the server confirms `!snap.exists()`, the month is marked hydrated and pinned — correct.
The user then ticks any habit; the debounced push fires and drops that month from `hydratedMonths`.
Now `isMonthResident` is false, so `monthReady` (`HomeViewModel.kt:433`) reports false and its
recovery `safeLaunch { ensureMonthHydrated(month) }` returns immediately without doing anything,
so `_hydrationTick` never bumps and `monthReady` never flips back. Every backfill into that month
is refused (`OccurrenceScheduler.monthResident`), silently, per §1.4. It self-heals only on the
next app restart.

### 1.7 Streak maths recomputes the local date from the epoch, ignoring the stored `local_date` — MEDIUM
**File:** `util/streak/StreakCalculator.kt:37-39`, `:112-119`

```kotlin
private fun localDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
```

`fullyCompletedDates` buckets by that recomputed date. The whole S17 phase exists because
recomputing a day from `scheduled_for` in the *current* zone is not stable — which is why
occurrences carry a `local_date` column and why `exportDateFor`
(`ExportImportRepository.kt:1127`) prefers it everywhere else in the tree. The streak fold is the
one consumer that still recomputes, and it is the one where a day either counts or doesn't.

**Failure scenario.** A user with two daily slots (01:00 and 22:00) travels from IST (UTC+5:30) to
UTC. Every historical 01:00 IST slot re-buckets into the *previous* local day. Days that were
"every occurrence done" now have a third, differently-sourced occurrence folded into them, and days
that were complete now look incomplete. `fullyCompletedDates`' rule is "one unsatisfied item
disqualifies the whole day", so a run that was 40 days reads as broken — retroactively, from a
timezone change alone, with no way for the user to tell why. `longestStreak` shifts too. The same
applies on any DST transition for slots near midnight.

**Fix direction:** thread `localDate` through the projections the streak functions consume
(`getScheduledStatusesForHabit` already returns a projection — add the column) and bucket on the
stored string, as everything else does.

### 1.8 The habit-journal chat is a dead end on rejection, and never drafts on the backfill path — MEDIUM
**Files:** `ui/journal/HabitJournalChatViewModel.kt:167-215`, `ui/journal/HabitJournalChatScreen.kt:96-122`

Two distinct bugs in the same method.

**(a) No retry after a rejection.** `sendAnswer()` optimistically sets `allAnswered = true` before
the save, then handles the result:

```kotlin
is LogResult.Rejected -> it.copy(busy = false, rejectedMessage = result.reason)
```

but the screen gates the whole compose box on it:

```kotlin
if (!state.allAnswered) { ... compose box + Send button ... }
```

and `sendAnswer()` itself starts with `if (s.busy || s.draftAnswer.isBlank() || s.allAnswered) return`.
So once the final answer is rejected — the normal outcome for a backfill into a non-resident month,
which returns `Rejected("That month isn't loaded yet — connect and retry.")` — the user is shown a
"connect and retry" message next to a screen that has **no retry affordance and no input**. Backing
out discards the whole conversation. The message tells the user to do something the UI does not let
them do.

**(b) Nothing is drafted on the backfill path.** The intermediate (not-yet-last-question) branch is:

```kotlin
val id = occurrenceId
if (id != null) occurrenceScheduler.saveHabitJournalDraft(id, qaJson)
```

On a backfill, `occurrenceId` is null by construction (the row does not exist yet), so this is a
no-op for every answer before the last one. The draft-resume feature (B1) that protects the
live-occurrence path does not exist for backfills: navigate away, or lose the process, after
answering four of five questions and all four are gone with no warning.

### 1.9 `DetailViewModel.errorMessage` is produced but never rendered — MEDIUM
**Files:** `ui/detail/DetailViewModel.kt:166-167`, `:200-201`; `ui/detail/DetailScreen.kt`

Identical shape to the workout report's §1.1:

```kotlin
private val _errorMessage = MutableStateFlow<String?>(null)
val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
...
} catch (e: Exception) {
    _errorMessage.value = "Failed to load item details: ${e.message}"
}
```

`grep` finds zero references to `errorMessage` (or `isLoading`, same file) anywhere in
`ui/detail/`. This is the *only* error surface on the detail screen, and `loadItemDetails` is what
loads the title, stats, streaks and the whole timeline. If it throws — a `SQLiteException`, a
corrupt `qa_json` — the screen renders blank or, on a `refresh()` after a toggle, silently keeps
the stale previous item's data. `isLoading` being uncollected means there is no spinner either, so
a slow load is indistinguishable from an empty item.

`itemColorTag` (`:107`) is likewise written on every load and read by nothing — dead state.

### 1.10 The undo toast is raced and can show the previous attempt's result — MEDIUM
**Files:** `ui/home/HomeScreen.kt:121`, `:268`, `:318`; `ui/home/HomeViewModel.kt:774-787`

```kotlin
onUndo = { viewModel.revertItem(item); undoToken++ }        // token bumps synchronously
...
UndoSnack(token = undoToken, text = undoFeedback ?: "Undone")
```

`UndoSnack` shows for 2.6 s keyed on `token`. But `_undoFeedback` is only set *after* the suspend
revert completes, and it is never cleared. Three consequences:

- The first undo always shows the literal "Undone" (the `?:` default) regardless of outcome; a
  failure that resolves after the 2.6 s window is never seen at all.
- The second undo shows the **first** attempt's stale text until the new value lands — so a failed
  undo followed by a successful one can read "Couldn't undo that. Try again."
- There is no `clearUndoFeedback()`, so the last message persists in the ViewModel indefinitely.

Compounding it, the success determination is itself unreliable:
`runCatching { occurrenceScheduler.revertHabit(occ) }.isSuccess` is `true` even when `revertHabit`
took its `?: return@withLock` on a missing occurrence row (`OccurrenceScheduler.kt:701`) — "Undone"
over a no-op. ROUND 0 (C9) set out to make "revert must not fail silently" true; the plumbing is
there but the wiring is off by one async hop.

### 1.11 Account deletion erases the cloud data even when the account delete fails, and never says so — MEDIUM
**File:** `ui/account/AccountViewModel.kt:155-189`

Ordering is deliberate (delete the Firestore doc while the token is valid) and the S-2 fix
correctly aborts the *auth* delete when the *remote* delete fails. The reverse case is unhandled:

```kotlin
val remoteDeleted = runCatching { cloudSync.deleteRemoteDoc() }.getOrDefault(false)
if (!remoteDeleted) { ...abort... }
var outcome = authRepository.deleteAccount()
...
AuthOutcome.NeedsReauth ->
    _form.update { it.copy(busy = false, message = "Please sign in again, then retry deletion.") }
is AuthOutcome.Error ->
    _form.update { it.copy(busy = false, message = o.message) }
```

By the time either of those branches runs, `deleteRemoteDoc()` has already enumerated and deleted
every month document and the parent doc. The user is told "Please sign in again, then retry" — as
if nothing happened — while their entire cloud backup is already gone. They are still signed in and
`conflictPaused` was reset in `deleteRemoteDoc`'s `finally`, so the next debounced push re-uploads
from Room; that masks it *if* Room still holds everything and the device gets back online. If the
user cancels the Google re-auth sheet and signs out (or reinstalls) before that push lands, the
data is unrecoverable. At minimum the message is wrong: it must say the cloud copy has been erased.

### 1.12 A throw in `fireBatch` permanently breaks the batch check-in chain — MEDIUM
**File:** `util/alarm/AlarmReceiver.kt:80-97`

```kotlin
private suspend fun fireBatch() {
    val unresolved = scheduler.unresolvedBatchOccurrencesFor(System.currentTimeMillis())
    if (unresolved.isEmpty()) { ... } else {
        val titles = unresolved.mapNotNull { db.habitDao().getHabitById(it.habitId)?.title }
        notificationUtils.showBatchHabitNotification(unresolved.size, titles)
        unresolved.forEach { occ -> ...insert SHOWN event... }
    }
    // Re-arm for tomorrow either way, so the chain never breaks.
    scheduler.armBatchCheckIn()
}
```

The comment states the invariant the code doesn't enforce: `armBatchCheckIn()` is the *last*
statement, not a `finally`. Any throw above it — a `SQLiteException` on the event inserts, or the
8 s `withTimeout` in `runAsync` expiring on a `syncMutex`-contended
`unresolvedBatchOccurrencesFor` — skips the re-arm. The app-wide BATCH check-in is a single
self-rearming alarm chain, so missing one link means **no BATCH habit ever gets a check-in
notification again** until something else calls `syncAll()` (app launch, boot, the daily
`WindowRefreshWorker`). For a user who answers everything from the shade and rarely opens the app,
that can be a day or more of silence with no indication anything is wrong. Wrapping the body so
`armBatchCheckIn()` runs in a `finally` restores the stated invariant.

### 1.13 A keystore failure silently turns the app lock off — MEDIUM (security)
**File:** `data/lock/AppLockRepository.kt:167-190`, `:52-58`

```kotlin
fun openPrefs(context: Context): SharedPreferences = runCatching {
    EncryptedSharedPreferences.create(context, FILE, masterKey, ...) as SharedPreferences
}.getOrElse {
    Log.w(TAG, "EncryptedSharedPreferences unavailable — falling back to plain prefs", it)
    context.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)
}
```

then, in the field initializers that read it:

```kotlin
private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
private val _isLocked  = MutableStateFlow(_isEnabled.value)
```

The fallback file (`daybook_lock_plain`) is a **different, empty** file. The documented rationale
covers a device that never had a working keystore — but the common real case is a keystore master
key that was working and is later *invalidated*, which Android does when the user changes or
removes their device lock-screen credential. On the next launch `EncryptedSharedPreferences.create`
(or the first decrypting read) fails, the fallback returns an empty file, `KEY_ENABLED` defaults to
`false`, and `_isLocked` seeds to `false`.

**Failure scenario.** A user with the app lock on changes their phone PIN. Next launch, Daybook
opens straight to Today with no lock screen, the App Lock settings switch reads off, and nothing
anywhere says the lock was disabled. A second-factor the user believes is protecting their health
journal is gone, silently. (Symmetrically, if the read throws rather than `create`, it throws from
a field initializer during Hilt construction — an unrecoverable crash on launch, since
`AppLockRepository` is a `@Singleton` injected into `MainActivity`.)

This needs a deliberate decision rather than a mechanical fix: either persist a plaintext "lock was
enabled" tripwire outside the encrypted file so the app can tell "off" from "unreadable" and prompt
the user to re-set their PIN, or accept the failure loudly. Silently defaulting to unlocked is the
one option that shouldn't stand.

---

## 2. Smaller / lower-confidence observations

- **`NotificationIdSequence.next()` is called even when the insert is ignored.**
  `syncTaskInternal` / `syncHabitInternal` (`OccurrenceScheduler.kt:196`, `:309`) mint a fresh id
  for every slot in the 7-day window on every sweep, but `dao.insert` is `OnConflictStrategy.IGNORE`
  so surviving rows keep their old id and the minted one is thrown away. With ~20 items × ~20 slots
  and `syncAll()` on every `onResume`, that burns a few hundred ids per foreground. It cannot
  realistically reach `MAX` (500 M), so this is waste rather than a bug — but it does mean the
  counter is not a useful diagnostic, and the wrap behaviour (`if (it >= MAX) START`) would
  collide with live ids if it ever got there.
- **`completeAllBatchToday` collects Room `Flow`s inside `withTransaction`.**
  `OccurrenceScheduler.kt:449` wraps `unresolvedBatchOccurrencesFor(now)`, which does
  `getActiveHabits().first()` and `getAllOccurrencesInTimeRange(...).first()` — cold Room flows
  dispatched off the transaction's thread. WAL mode should let those reads through, so I could not
  confirm a deadlock, but it's a known Room footgun and the query results don't need to be inside
  the transaction at all (hoisting them above it is free).
- **`importMonth`'s `keepIfShownPending` is an N+1.** `ExportImportRepository.kt:745-756` calls
  `hasShownEvent(it.id)` once per live occurrence in the month — a query per row on every remote
  month apply. Correct, just costly on a heavy month.
- **`importHevyFromUri` discards the throwable entirely** (`SettingsViewModel.kt:435-437`): no
  `Log`, no `recordUnhandledException`, just a generic user message. The two paths beside it
  (`importFromUri`, `exportRange`) at least surface `t.message`.
- **`applyGooglePhotoIfEligible` has five silent `?: return`s** (`AccountViewModel.kt:113-120`) —
  a failed avatar fetch or save after sign-in is invisible even in logcat. Cosmetic, but it's the
  reason "my Google photo didn't come across" would be unreportable.
- **The three remaining raw `viewModelScope.launch` calls** are all one-second UI tickers
  (`WorkoutHomeViewModel.kt:47`, `WorkoutSessionViewModel.kt:120`, `:138`). They read only
  in-memory `StateFlow` values and can't realistically throw, so the `safeLaunch` migration
  skipping them is defensible — noted only because they are the complete remaining set, and the
  elapsed/rest tickers are the files currently modified in the working tree.
- **Four public `isLoading` flows are never collected** (`DetailViewModel`, `AddFoodMedViewModel`,
  `AddHabitViewModel`, `OnboardingViewModel`) — no spinner anywhere those were intended.
- **`FoodMedViewModel` / `RoutinesViewModel` mutators are all bare `safeLaunch { repo.x() }`** with
  no error state (`add/update/delete/archive/unarchiveItem`, `add/update/delete/archive/
  unarchiveHabit`). Same systemic shape as the workout report's §1.6, and the same conclusion
  applies: a failed "Delete habit" leaves the row on screen with no explanation. Worth one shared
  snackbar rather than 10 individual fixes.
- **Habit-journal status erodes on a file round trip.** `exportBackup` maps a habit's `LOGGED` to
  `BackupStatus.DONE` (`ExportImportRepository.kt:188`); the import maps `DONE` back to `LOGGED`
  only when `qaJson` is non-blank (`:656`). A Journal habit answered with all-blank text (so
  `qa = null`) therefore comes back as `COMPLETED`, not `LOGGED`. Harmless for streaks
  (`daySatisfies` accepts both) but it does change what the Detail timeline renders.
- **`EditDurationDialog`-style unbounded numeric input** has a counterpart here:
  `setDefaultSnoozeMinutes` coerces to 5..120 (`SettingsViewModel.kt:267`) but
  `setCornerScale`/`setStreakRestDays`/`setNavTabs` take arbitrary strings from the UI and are only
  validated on read. Not exploitable, just asymmetric.

---

## 3. Checked and ruled out

- **`simpleName` string comparisons on exception types** —
  `AuthRepository.kt:80` (`FirebaseAuthInvalidUserException`), `:151`
  (`FirebaseAuthRecentLoginRequiredException`) and `CloudSyncRepository.isOffline` (`:1337`) all
  match exception classes by `javaClass.simpleName`, which would break under obfuscation. It
  doesn't: `app/proguard-rules.pro` opens with `-dontobfuscate`. Safe as written, but this is a
  real tripwire if that line is ever removed.
- **`SyncLogic`'s three-layer echo guard and `decideBootstrap`** — re-read against the S-1/S-10
  changes. `residentMonthHashes` correctly stops an evicted month wedging bootstrap into permanent
  `CONFLICT`, and `conflictAlreadyResolved` correctly re-opens prompting on a genuinely new remote
  revision. No off-by-one in `chunkByBytes` (the single-oversized-entry case gets its own chunk, as
  its KDoc claims).
- **`MonthPartitioner.monthKeyOf`'s double validation** — the regex plus the parse really are both
  load-bearing (`"2026-1-5"` and `"2026-02-30"` are rejected by different halves), and
  `cappedMostRecentMonths` sorts descending before taking 30, so the `whereIn` cap drops the oldest
  months rather than arbitrary ones.
- **`decodeDays` returning `null` on failure** plus the `isGenuinelyEmptyMonth` hash check
  (`CloudSyncRepository.kt:1006-1014`) genuinely does close the corrupt-payload-wipes-a-month hole.
  The occurrence side of `importMonth` is sound; only the workout side (§1.1) is not.
- **`pushDeletesAllowed` / `onLocalDataReplaced`** — a range-scoped import cannot phantom-delete a
  cloud month. I tried to construct a path where an evicted month is pushed as a deletion and
  couldn't: the diff-driven delete is gated on `allowMonthDeletions`, which only
  `resolveConflict(restoreFromCloud = false)` sets.
- **`canBackfill` / `revertShouldRearm`** — no off-by-one. "Today" is deliberately not a backfill,
  the creation-date floor uses `isBefore` (so the creation day itself is allowed), and a past slot
  correctly refuses to re-acquire an alarm on undo.
- **`armNextTaskInternal` / `armNextHabitInternal`'s `allowCatchup` split** and the
  "already SHOWN and overdue → leave it to the refire chain" guard — both correct, and they do
  prevent the duplicate-SHOWN-row corruption their comments describe.
- **`PinHasher`** — 120k-iteration PBKDF2-HMAC-SHA256, per-install random salt, constant-time
  `MessageDigest.isEqual` over decoded bytes (not hex `String.equals`), `clearPassword()` in a
  `finally`, and the one `!!` that used to exist is gone. No attempt counter is a documented,
  defensible decision (decision 8), not an oversight.
- **`BiometricGate`** — `BIOMETRIC_STRONG` only, `DEVICE_CREDENTIAL` never in the allowed set, and
  every error path (`onAuthenticationError`, an unavailable sensor, a prompt that fails to show)
  routes to the PIN pad. There is genuinely no path where an error resolves to "unlocked".
- **App-lock timing** — `onAppBackgrounded` is stamped in `onStop` and persisted (so a process
  death between background and foreground still locks), `onAppForegrounded` in `onResume`, and the
  lock gate is the outermost branch in `MainActivity`'s launch `when` (`:350-351`), ahead of the
  sign-in form. The recents-thumbnail exposure between `onPause` and `onStop` is real but is the
  documented consequence of decision 9 ("no `FLAG_SECURE` anywhere"), not a bug.
- **`AlarmReceiver`'s `goAsync()` + `withTimeout(8_000)` + `finally { pending.finish() }`** — the
  budget is correctly under the ~10 s foreground-broadcast limit, `BootCompletedReceiver` correctly
  uses 20 s for the longer background budget, and both enqueue `WindowRefreshWorker` in the
  `finally` so a timed-out sweep still leaves the daily worker registered.
- **PendingIntent request-code allocation** — `id * 4 + {0..3}` with fire/refire/open/action slots,
  and the four action buttons sharing slot 3 but differing by `Intent.action` (which
  `filterEquals` distinguishes, unlike extras). `BATCH_NOTIFICATION_ID = 999` is correctly below
  `NotificationIdSequence.START = 1000` so `999*4+3 < 1000*4`. No collision path found.
- **Notification channel versioning** (`habits_v2` / `food_med_v2` + `LEGACY_CHANNEL_IDS` swept on
  startup) and the N-2 "did it actually post?" return value threaded into `fireHabit`/`fireFoodMed`
  — both correct; a blocked channel no longer produces a phantom SHOWN event.
- **`SyncFlushWorker.flushOutcome`** — a pending push with no resolved uid correctly
  `Result.retry()`s rather than reporting success and consuming the unique work.
- **Room migrations** — nothing after v22 exists (`Migrations.kt` ends at `MIGRATION_21_22`), so
  per the brief this was not re-reviewed.
