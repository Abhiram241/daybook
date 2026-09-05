# Daybook — Risk Audit: Data Sync, Notifications/Alarms, Crash Surface

**Scope:** read-only deep-dive against the current tree (versionCode 13 / versionName 0.5.5 / Room DB v16).
**Method:** every file under `data/sync/`, `data/backup/`, `data/OccurrenceScheduler.kt`, `data/QuietHours.kt`,
`data/RetentionPolicy.kt`, `data/ExportImportRepository.kt`, `util/alarm/*`, `util/notification/*`,
`util/work/*`, `firestore.rules`, `AndroidManifest.xml`, `app/build.gradle.kts`, `proguard-rules.pro`,
`di/*`, `data/local/*Dao.kt`, `Migrations.kt`, plus the ViewModels that drive those paths, read in full.
Whole-tree greps for `!!`, `catch (`, `TODO`/`FIXME`/`HACK`, `runCatching`, `GlobalScope`, `lateinit`,
`CoroutineExceptionHandler`, `crashlytics`, `setDefaultUncaughtExceptionHandler`.
Prior docs (`DEVELOPER_HANDOVER.md`, `CUSTOMIZATION_BUILD_PLAN.md`, `CUSTOMIZATION_REGRESSION.md`)
were read for context and every claim re-verified against the current source; several fixes they
describe are genuinely still in place (see §4), and several gaps they never covered are new here.

**Nothing in this audit was changed.** No file under `app/src/` or any config was edited.

---

## 1. Executive summary — top 5 risks

Ranked by likelihood × impact.

| # | Risk | Where | Why it's top |
|---|------|-------|--------------|
| 1 | **Cloud sync silently stops for every long-term user.** `bootstrap` compares the cloud's *full* month-hash map against the device's *resident-only* hashes, so once any month has been evicted (i.e. as soon as history exceeds ~2 months) every cold start decides `CONFLICT`, sets `conflictPaused = true`, and halts all push/pull — while the conflict dialog exists only on the Account/Sign-in screens, which most users never open. | `CloudSyncRepository.kt:341-349`, `:363-379`, `:1028` | Near-certain for any user past their third month; total, silent loss of sync with a "Sync paused" string buried two screens deep. |
| 2 | **No crash reporting of any kind.** No Crashlytics dependency, no `Thread.setDefaultUncaughtExceptionHandler`, no `CoroutineExceptionHandler` anywhere in the UI layer, and 92 `viewModelScope.launch` sites of which most have no `try`/`runCatching`. Every production crash is invisible. | `app/build.gradle.kts` (no `firebase-crashlytics`), whole tree | Every other finding here is un-observable in the field without it. |
| 3 | **Quiet hours is bypassed by the re-nag chain** — `AlarmReceiver` arms the refire alarm at `now + snooze` with no `quietDefer`, and the notification has no `setOnlyAlertOnce`, so an unanswered 21:00 reminder buzzes at full IMPORTANCE_HIGH every 10 minutes all night. | `AlarmReceiver.kt:119-123`, `:149-153` | Directly contradicts the advertised feature; hits any user who enables quiet hours and misses one reminder. |
| 4 | **Journal / backfill saves are reported as successful when they were silently rejected.** `JournalViewModel.save()` and `RespondViewModel.resolve()` `runCatching { … }` and then unconditionally set `saved`/`done = true`; `backfillFoodMed` early-returns with only a `Log.w` when the month isn't resident or `canBackfill` fails. The entry is gone; the UI says it was saved. | `JournalViewModel.kt:191-204`, `RespondViewModel.kt:~195`, `OccurrenceScheduler.kt:612-617` | Silent user-data loss on the exact flow (backfilling an older day) where the month-residency guard is most likely to trip. |
| 5 | **Account deletion orphans cloud data permanently.** `AccountViewModel.deleteAccount` discards `deleteRemoteDoc()`'s boolean and deletes the Firebase user regardless; once the uid is gone, `firestore.rules` makes the leftover `users/{uid}` + `months/*` docs unreachable by anyone, forever. | `AccountViewModel.kt:154-155`, `CloudSyncRepository.kt:1137-1154` | A flaky connection during "Delete my account" leaves the user's data on Anthropic-side… i.e. on your Firestore bill and outside any deletion request you can honour. |

---

## 2. §1 — Data sync findings

Counts: **1 Critical, 3 High, 6 Medium, 3 Low.**

---

### S-1 · CRITICAL · Spurious `CONFLICT` on every cold start once a month has been evicted → sync permanently paused

**File:** `app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt:341-349` (and `:363-379`, `:1022-1052`)

```kotlin
val localMonthHashes =
    MonthPartitioner.hashes(MonthPartitioner.partition(localBackup?.days.orEmpty()))
val hashesEqual = remoteExists &&
    localDefsHash != null &&
    parent?.getString(F_DEFS_HASH) == localDefsHash &&
    remoteMonthHashes == localMonthHashes          // <-- full map vs resident-only map
```

`remoteMonthHashes` comes from the parent doc's `monthHashes` field (`fetchRemoteMonthHashes`,
`:1188-1200`) and holds **every** month the account has ever pushed. `localMonthHashes` is derived
from `exportBackup()`, i.e. only the rows actually in Room — which, after `evictStaleMonths`
(`:658-680`) has run, excludes every month outside `recentMonths()`. `doPush` already knows this and
filters (`:468-470`, `knownResident = syncState.monthHashes.filterKeys { it in resident }`);
`bootstrap` does not.

**Concrete failure scenario.** A user signs in in January and uses the app daily. By April,
`runMaintenance()` (daily `WindowRefreshWorker` + `onAppStop`) has evicted January and February —
their hashes match the cloud, so eviction is correct. The process is killed (normal Android
behaviour) and the user reopens the app: `currentUid` is null, so `onAuthState` runs `bootstrap`.
`localMonthHashes = {2026-03, 2026-04}`, `remoteMonthHashes = {2026-01 … 2026-04}` → `hashesEqual =
false`; local is not empty; `conflictPromptShownForUid != uid` → `decideBootstrap` returns
`CONFLICT` → `raiseConflict` sets `conflictPaused = true`. From that moment `doPush`,
`applyRemoteParent`, `applyRemoteMonth`, `ensureMonthHydrated` and `runMaintenance` all early-return
(`:450`, `:884`, `:923`, `:982`, `:700`). Sync is dead for the session. The `ConflictInfo` is only
consumed by `AccountScreen.kt:56-58` and `SignInGate.kt:45-47` — a signed-in user on the Today
screen sees nothing at all, not even the "Sync paused" row unless they walk to Settings → Account.

**Second-order damage.** If the user does find the dialog and picks either side,
`resolveConflict` sets `conflictPromptShownForUid = uid` permanently (`:1057`), and
`decideBootstrap`'s `promptShown -> ATTACH_ONLY` branch (`SyncLogic.kt:85`) means **the conflict
prompt is disabled for that account for good** — a genuine second-device conflict a year later is
silently resolved last-write-wins.

**Fix direction.** Filter the remote map the same way `doPush` filters `knownResident`: compare
`remoteMonthHashes.filterKeys { it in residentSet }` where
`residentSet = syncState.hydratedMonths + localMonthHashes.keys + MonthPartitioner.recentMonths()`,
and treat "remote has months we don't hold locally" as normal (that is what eviction means), not as
divergence. Separately: surface the conflict/paused state somewhere the user actually is (a Today
banner), and don't leave sync paused indefinitely with no visible affordance.

---

### S-2 · HIGH · Account deletion orphans Firestore data forever

**Files:** `app/src/main/java/com/daybook/app/ui/account/AccountViewModel.kt:154-155`;
`app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt:1137-1154`

```kotlin
runCatching { cloudSync.deleteRemoteDoc() }   // return value discarded
var outcome = authRepository.deleteAccount()
```

`deleteRemoteDoc` itself already collapses every failure into `false`
(`runCatching { … }.onFailure { Log.w(…) }.isSuccess`), and the only caller throws that `false`
away.

**Concrete failure scenario.** User taps *Delete account* on a train. The `months` enumeration
(`monthsRef(uid).get(Source.SERVER)`, `:1143`) times out, or the first delete batch commits and the
second doesn't. `deleteRemoteDoc` returns `false`. The flow proceeds; a moment later the network
recovers enough for `auth.currentUser.delete()` to succeed. The uid no longer exists, so
`firestore.rules`' `request.auth.uid == uid` can never again be satisfied by anyone — the
`users/{uid}` doc and its `months/*` subcollection are unreachable, undeletable from the client, and
billed indefinitely. There is also no server-side cleanup (no Cloud Function, `firebase.json` has
only auth + firestore config).

**Fix direction.** Make the boolean load-bearing: if `deleteRemoteDoc()` returns false, do **not**
call `authRepository.deleteAccount()` — show "Couldn't reach the cloud; connect and try again" and
leave the account intact. Belt-and-braces: an `onDelete` Auth-trigger Cloud Function that recursively
deletes `users/{uid}` is the only way to guarantee it even if the client dies mid-flow.

---

### S-3 · HIGH · `importMonth` can exceed SQLite's 999-variable limit on Android 8–11 → a month never hydrates, silently and permanently

**Files:** `app/src/main/java/com/daybook/app/data/ExportImportRepository.kt:556-577`;
`app/src/main/java/com/daybook/app/data/local/HabitOccurrenceDao.kt:97-98`, `:120-121`, `:140-141`;
same shape in `FoodMedOccurrenceDao.kt:131-132`, `:143-144`, `:151-152`

```kotlin
val habitKeep = (incomingHabitIds + keepIfShownPending).toList()
…
database.habitOccurrenceDao().deletePendingByLocalMonthBefore(monthKey, clearUntil, habitKeep)
```

`habitKeep` is **every occurrence id in the month**, bound one-per-SQL-parameter by Room's
`IN (:keep)` expansion. `minSdk = 26`, and Android 8.0–11 ship SQLite < 3.32, where
`SQLITE_MAX_VARIABLE_NUMBER` is **999**.

**Concrete failure scenario.** A user with 20 active reminders × 3 times/day produces ~60
occurrences/day → ~1,860 ids for a 31-day month. On a Pixel 3 running Android 11, `importMonth`
throws `SQLiteException: too many SQL variables`, which is caught by the broad
`catch (e: Exception)` at `:579-581` and turned into `ImportResult(success = false)`.
`applyRemoteMonth` (`CloudSyncRepository.kt:930-934`) logs `"month … merge failed"` and returns
false — so `syncState.monthHashes` is *not* updated and `hydratedMonths` is *not* extended. Because
`ensureMonthHydrated` added the month to `hydrationAttempted` before the fetch (`:984`) and only
removes it on an *unreachable server* or a thrown exception (`:995`, `:1016`) — not on a
`applyRemoteMonth` returning false — the month is never retried this session. The user's second
device shows that month permanently empty, with a log line as the only trace.

Note the same pattern also affects `deleteFuturePendingExcept` (called on every `syncTask`/`syncHabit`
sweep) — bounded there by 7 days × times-per-day, so far less likely, but the same failure mode
inside `db.withTransaction` in `syncTaskInternal`.

**Fix direction.** Chunk the `keep`/`ids` lists (~900 per statement) at every call site, or invert
the queries to avoid parameter lists entirely — e.g. write the incoming ids into a scratch table
inside the same transaction and use `NOT IN (SELECT id FROM scratch)`. Also: on
`applyRemoteMonth == false`, remove the month from `hydrationAttempted` so a retry is possible.

---

### S-4 · HIGH · Sign-out wipe leaves the previous account's journal questions, name, photo and onboarding state behind — and pushes them to the next account

**File:** `app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt:280-300`

```kotlin
database.withTransaction {
    database.habitEventDao().deleteAll();      database.foodMedEventDao().deleteAll()
    database.habitOccurrenceDao().deleteAll(); database.foodMedOccurrenceDao().deleteAll()
    database.habitDao().deleteAll();           database.foodMedTaskDao().deleteAll()
    database.customCategoryDao().deleteAll();  database.customPromptDao().deleteAll()
}   // journal_questions and app_settings are NOT touched
```

Eight tables, but the DB has ten (`AppDatabase.kt:18-29`). `journal_questions` is missing — despite
being listed in `DATA_TABLES` (`:1254-1258`) as sync-relevant *and* being part of `Definitions`
(`BackupModel.kt:67-68`) and therefore of `definitionsHash`.

**Concrete failure scenario (shared device / handover phone).** User A has customised their journal
prompts to things like "Did you talk to your therapist today?". A signs out. B signs in. `isLocalEmpty()`
(`:1209-1211`) only inspects `habits` and `food_med_tasks`, both now empty, so B's bootstrap is
`PULL_REMOTE` or `ATTACH_ONLY`. If B is a brand-new account (no cloud doc → `ATTACH_ONLY`), A's
journal questions are still in Room; B's very first habit edit triggers `doPush`, whose
`exportBackup()` reads `journalQuestionDao().getOrderedTexts()` (`ExportImportRepository.kt:120`) and
**uploads A's private prompts into B's cloud document**. B also sees them in the app.

Separately, `app_settings` is device-local by design, but it carries `user_name`,
`profile_photo_path` and `onboarding_completed`. After A signs out, B skips onboarding entirely
(`MainActivity.kt:292`), is greeted by name as A, and sees A's profile photo (the file under
`filesDir` is not deleted either — `ProfilePhotoStore.clear()` is only called from the *account
deletion* path).

**Fix direction.** One shared wipe routine that covers all ten tables (re-seeding
`journal_questions` afterwards via `JournalQuestionRepository.ensureSeeded()` to preserve the ≥1
invariant), plus resetting the identity columns of `app_settings` (`user_name`, `profile_photo_path`,
`onboarding_completed`) and deleting the photo file. Keep the genuinely device-scoped preferences
(accent, font, quiet hours, nav tabs) if that's the intent, but make the choice explicit.

---

### S-5 · MEDIUM · `AccountViewModel.wipeLocalData` is a weaker, non-transactional duplicate of the sign-out wipe

**File:** `app/src/main/java/com/daybook/app/ui/account/AccountViewModel.kt:174-185`

Six `deleteAll()` calls, **not** inside `database.withTransaction`, inside a single `runCatching`.
Missing versus `wipeLocalForSignOut`: `custom_categories`, `custom_prompts`, `journal_questions`,
`scheduler.cancelAllReminders()`, `syncState.reset()`.

**Concrete failure scenario.** User picks "Delete my account **and** erase local data". A disk error
after `habitDao().deleteAll()` but before `foodMedTaskDao().deleteAll()` leaves half the definitions
present with no rollback; the `runCatching` swallows it and the UI reports "Local data erased."
Armed AlarmManager alarms for the deleted habits survive (nothing cancelled them), so the process is
woken repeatedly for occurrences that no longer exist. In practice the subsequent `AuthState.SignedOut`
→ `wipeLocalForSignOut()` mostly papers over this, which is precisely why the duplicate is dangerous:
it looks like it does the job and doesn't.

**Fix direction.** Delete this method and call the (hardened) shared wipe from S-4.

---

### S-6 · MEDIUM · The `hydrating` echo guard does not actually suppress anything

**File:** `app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt:167-177`, `:313-327`

```kotlin
private inline fun <T> hydrating(block: () -> T): T {
    hydrating.incrementAndGet(); try { return block() } finally { hydrating.decrementAndGet() }
}
…
override fun onInvalidated(tables: Set<String>) {
    if (hydrating.get() > 0) return          // <-- almost always already 0 by now
    syncState.pendingPush = true; dirtyCounter.incrementAndGet(); changes.tryEmit(Unit)
}
```

Room's `InvalidationTracker` notifies observers **asynchronously**, from a refresh runnable posted
to the query executor after the transaction commits. By the time `onInvalidated` fires, the
`hydrating` counter has almost always been decremented. The v0.5.3 "S10 counter, not a flag" change
narrowed the window from "the whole hydration loop" to "the duration of one Room write", and in doing
so narrowed it past the point where it can win the race at all.

**Concrete failure scenario.** Every `applyRemoteMonth`, `applyRemoteParent`, `evictMonth` and the
sign-out wipe sets `pendingPush = true` and schedules a debounced push. For a normal remote apply
this is self-healing (the stored hash makes the diff empty and `doPush` clears `pendingPush` at
`:487-491`), so the cost is an extra full `exportBackup()` + partition + hash of the entire history
per remote change — measurable battery/CPU on a large account, and one wasted parent-doc `update()`
whenever the S13 re-export asymmetry makes the hashes differ. The genuinely unpleasant case is
`wipeLocalForSignOut`: `syncState.reset()` runs at `:293`, then the wipe's invalidation lands and
re-sets `pendingPush = true` with no uid, leaving a stale flag in `daybook_prefs` for the next
account.

**Fix direction.** Either make the guard correct (stamp a monotonically increasing "hydration write
generation" before the write and compare it at notification time against the generation of the last
completed hydration) or delete it and lean entirely on the hash diff — which is what already carries
the weight — and update the comments so the next reader doesn't trust a guard that isn't there.

---

### S-7 · MEDIUM · `syncState.monthHashes` grows without bound and is never pruned on eviction

**File:** `app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt:658-680`

`evictStaleMonths` removes months from `hydratedMonths` (`:676`) but leaves them in
`syncState.monthHashes`. That map is a single JSON string in `SharedPreferences`
(`SyncStateStore.kt:50-60`), is re-parsed on every read (including inside `doPush`'s hot path), and
is written verbatim onto the parent Firestore doc as `projectedHashes` (`:528-533`, `:649`) on every
push. After five years it holds 60 entries — small, but monotonic — and it is the direct cause of the
map inequality in S-1.

**Fix direction.** Prune `monthHashes` alongside `hydratedMonths` in `evictStaleMonths`, *or*
deliberately keep it (it is genuinely useful as the cloud's shape) and fix S-1's comparison instead.
Pick one and document which; today the code does neither consistently.

---

### S-8 · MEDIUM · The scoped months listener silently drops months past the 30th, in arbitrary order

**File:** `app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt:810-813`

```kotlin
val resident = (syncState.hydratedMonths + MonthPartitioner.recentMonths()).toList().take(30)
```

`hydratedMonths` is a `Set<String>` read back from a comma-joined pref (`SyncStateStore.kt:69-75`),
so `.toList()` has no defined ordering, and `hydrateRange` (`:737-763`) freely grows the resident set
past 30 — a three-year range export marks 36+ months resident.

**Concrete failure scenario.** User exports Jan 2024 – Dec 2026 to share with their doctor. 36 months
become resident. The listener is rebuilt (`reattachMonthsListener`, `:849-853`) with an arbitrary 30
of them. For the rest of the session, remote edits from a second device to any of the other 6 months
— **including the current month, if it happened to fall outside the 30** — are never applied live.
`isMonthResident` returns true for them, so `ensureMonthHydrated` also short-circuits (`:983`).

**Fix direction.** Sort descending by month key and take the newest 30 (so the current and previous
month are always in the window), and cap `hydratedMonths` growth after a range export, or split into
multiple listeners.

---

### S-9 · MEDIUM · A month that fails to hydrate while offline can stay "loading" until the user navigates away

**Files:** `CloudSyncRepository.kt:980-1018`; `ui/home/HomeViewModel.kt:320-339`

On an unreachable server, `ensureMonthHydrated` removes the month from `hydrationAttempted` and
returns without any retry of its own (`:992-997`). The only re-trigger is `monthReady`'s `map`, which
re-evaluates on `_selectedDate`, `_now` or `_hydrationTick`. `_now` only advances at the next
time-of-day/midnight boundary (`HomeViewModel.kt:171-182`), and `_hydrationTick` is only bumped when
hydration *succeeds*.

**Concrete failure scenario.** User in the Underground scrolls the week strip back into last March.
The fetch fails. `monthReady` is false; the day renders empty and backfill is refused
(`OccurrenceScheduler.kt:615-617`). They regain signal 30 seconds later — nothing re-fetches. They
must scrub to a different month and back before the data appears.

**Fix direction.** A short bounded retry with backoff inside `ensureMonthHydrated`, or re-trigger the
whole resident set on a connectivity callback / on `SyncStatus` leaving `Offline`.

---

### S-10 · MEDIUM · `conflictPromptShownForUid` permanently disables conflict detection after the first resolve

**Files:** `SyncLogic.kt:80-87`; `CloudSyncRepository.kt:351`, `:1057`

`decideBootstrap`'s `promptShown -> ATTACH_ONLY` is documented as "last-write-wins from here on",
but the flag is per-uid and never expires. Combined with S-1, which forces an early spurious resolve,
every user ends up with conflict detection permanently off after their first cold start past month
three.

**Concrete failure scenario.** Two-device user. Phone dies; they use a tablet offline for a week and
log 40 entries. Phone comes back, offline-edited too. On the phone's next cold start, `hashesEqual`
is false and `promptShown` is true → `ATTACH_ONLY`, no prompt. The remote listener then applies the
tablet's parent doc, and the per-month merge (`mergeMonth`, `ExportImportRepository.kt:866-873`)
saves resolved rows but not the phone's *definition* edits, which `applyRemoteDefinitions` overwrites
wholesale. The user is never told a merge happened.

**Fix direction.** Scope the "already prompted" state to something that can change —
`(uid, lastKnownRevision)` or a timestamp with an expiry — so a genuinely new divergence can prompt
again.

---

### S-11 · LOW · Firestore rules: no privilege gap found, but zero shape or size validation

**File:** `firestore.rules`

Both matches (`/users/{uid}` and `/users/{uid}/months/{month}`) require
`request.auth != null && request.auth.uid == uid`. Cross-checked against every read/write the app
performs:

| App call | Path | Allowed |
|---|---|---|
| `docRef(uid).get(SERVER/CACHE)` | `users/{uid}` | ✅ |
| `batch.set/update(docRef(uid))`, `docRef(uid).delete()` | `users/{uid}` | ✅ |
| `monthsRef(uid).get(SERVER/CACHE)` | `users/{uid}/months` | ✅ (collection read under the nested match) |
| `monthsRef(uid).whereIn(documentId(), …).addSnapshotListener` | `users/{uid}/months` | ✅ |
| `monthRef(uid, m).get/set/delete` | `users/{uid}/months/{m}` | ✅ |

No path escapes the owner check; there is no cross-user read; anything deeper than
`months/{month}` has no match and is denied by default. The residual gaps are: no
`request.resource.data.keys().hasOnly(...)`, no size constraint, no `create` vs `update`
distinction, so a compromised or modified client can write arbitrary fields and unbounded data
against its own uid (a billing/quota problem, not a confidentiality one). `firestore.indexes.json` is
the untouched CLI template (`"indexes": []`) with JS-style `//` comments that will fail
`firebase deploy --only firestore:indexes` — no index is currently needed, but the file is not
deployable as-is.

**Fix direction.** Optional hardening: constrain the parent doc's key set and add a payload-size
guard on month docs. Clean up `firestore.indexes.json` so a deploy of the whole `firestore` target
doesn't fail on the comments.

---

### S-12 · LOW · `evictMonth` orphans events for pre-migration rows forever

**File:** `app/src/main/java/com/daybook/app/data/ExportImportRepository.kt:693-707`;
`HabitEventDao.kt:63-64`

```kotlin
database.habitEventDao().deleteForLocalMonth(monthKey)     // matches substr(local_date,1,7)
database.habitOccurrenceDao().deleteInRange(start, end)    // matches scheduled_for
```

Rows created before `MIGRATION_12_13` have `local_date = NULL`; `substr(NULL, 1, 7)` is `NULL` and
never equals the month key, so their events are never matched. The occurrence rows *are* deleted
(that query keys off `scheduled_for`), so the events survive with a dangling `occurrence_id` and are
unreachable by every other prune path — `pruneActivityBefore` only touches `SHOWN`/`USER_SNOOZED`,
and terminal `REPLIED`/`COMPLETED`/`SKIPPED` events are explicitly never pruned (`RetentionPolicy.kt`).
Small, bounded by whatever history existed before v0.5.3, but permanent.

**Fix direction.** Add a `scheduled_for`-range fallback for `local_date IS NULL` in
`deleteForLocalMonth`, or a one-shot cleanup of events with no matching occurrence.

---

### S-13 · LOW · `pushDeletesAllowed` / `onLocalDataReplaced` are sound; one edge remains

`pushDeletesAllowed` (`ExportImportRepository.kt:920-923`) correctly refuses diff-driven month
deletions on the automatic path — verified. One residual: `onLocalDataReplaced`
(`CloudSyncRepository.kt:776-781`) *narrows* `hydratedMonths` to `covered + recentMonths()`, so
months that were resident before a range import are dropped from the bookkeeping while their rows
are still in Room. `isMonthResident` then returns false for them, and a backfill into one is refused
with only a `Log.w` (which lands in S-4 of §3: the UI still reports success). Low likelihood.

**Fix direction.** Union rather than replace, or evict the rows for the months being dropped so
bookkeeping and Room agree.

---

## 3. §2 — Notification / alarm findings

Counts: **0 Critical, 2 High, 6 Medium, 4 Low.**

---

### N-1 · HIGH · Quiet hours is bypassed by the re-nag chain — reminders buzz all night

**File:** `app/src/main/java/com/daybook/app/util/alarm/AlarmReceiver.kt:119-123` and `:149-153`

```kotlin
val snoozeMs = habit.snoozeIntervalMinutes.coerceAtLeast(10) * 60_000L
notificationUtils.scheduleReminderAlarm(
    occurrenceId, occ.notificationId, isHabit = true,
    triggerAtMillis = System.currentTimeMillis() + snoozeMs, isRefire = true
)
```

Every *other* arm path routes through `OccurrenceScheduler.quietDefer`: `armNextTaskInternal:204`,
`armNextHabitInternal:287`, `snoozeFoodMed:483`, `snoozeHabit:520`, `armBatchCheckInInternal:344`,
`snoozeBatchCheckIn:374`. The receiver's automatic refire is the one that doesn't, and the receiver
never reads `app_settings` at all. `showHabitNotification` / `showFoodMedNotification`
(`NotificationUtils.kt:293-356`) set `PRIORITY_HIGH` on an `IMPORTANCE_HIGH` channel and do **not**
set `setOnlyAlertOnce`, so every refire re-alerts with sound and heads-up.

**Concrete failure scenario.** Quiet hours 22:00–07:00, a habit reminder at 21:00 with the default
10-minute snooze. User doesn't answer and goes to bed. `fireHabit` runs at 21:00, posts, arms a
refire for 21:10 — with no quiet check. That chain continues at 21:10, 21:20 … 22:00, 22:10, …,
06:50, at full alert volume, straight through the quiet window. Nothing stops it: the 24-hour
`skipStaleForHabit` auto-skip (`OccurrenceScheduler.kt:264-265`) only runs inside a `syncAll()`
sweep, which happens on app open / boot / the daily worker — none of which occur while the user is
asleep. Toggling quiet hours on mid-nag doesn't help either: `setQuietHoursEnabled` → `syncAll()`,
and `armNextHabitInternal` explicitly declines to touch an overdue row that already has a `SHOWN`
event (`:283-286`), "leaving it to the refire chain".

**Fix direction.** Move the refire arm out of the receiver into a scheduler entry point that applies
`quietDefer` (the receiver already injects `OccurrenceScheduler`), or read settings in the receiver
and apply `deferIfInsideQuietHours` there. Consider also capping the chain (N re-nags, then stop) and
adding `setOnlyAlertOnce(true)` to the refire post so a long chain vibrates once, not fifty times.

---

### N-2 · HIGH · A suppressed notification is still recorded as `SHOWN`, blocks re-arm, and eventually becomes "Skipped" — with no user-visible signal

**Files:** `util/notification/NotificationUtils.kt:358-371`; `util/alarm/AlarmReceiver.kt:109-118`,
`:143-148`; `data/OccurrenceScheduler.kt:200-203`, `:283-286`

```kotlin
private fun notify(id: Int, notification: android.app.Notification) {
    val blocked = notificationBlockReason()
    if (blocked != null) { Log.w(TAG, "notify(id=$id) suppressed: $blocked"); return }
    …
}
```

`notify()` returns without posting, but `fireHabit`/`fireFoodMed` carry on: they insert the `SHOWN`
event and arm the refire. `armNext*` then refuses to re-arm the row because it has a `SHOWN` event.

**Concrete failure scenario.** The user (or an OEM battery manager, or a mis-tap in the system
notification settings) turns off just the "Habit reminders" channel. `NotificationManagerCompat
.areNotificationsEnabled()` still returns true, so nothing in the app-level permission flow
(`MainActivity.kt:152-181`) notices. Every reminder that day is suppressed, marked `SHOWN` in the
Activity timeline, re-nagged into the void every 10 minutes, and finally flipped to `SKIPPED` by the
24-hour stale sweep. The Detail screen shows a day of "Shown … Skipped" the user never saw and never
did. The only surfacing of `notificationBlockReason()` is a static "Action needed" caption on the
Settings hub row (`SettingsScreen.kt:121`, `:704`) — nothing tells the user at the moment it matters.

**Fix direction.** Have `notify()` return a boolean; skip the `SHOWN` insert and the refire arm when
the post was suppressed, so the occurrence stays cleanly un-fired and re-armable. Add a persistent
in-app banner on Today whenever `notificationBlockReason() != null`, with a deep link to the channel
settings.

---

### N-3 · MEDIUM · Batch check-in snooze is silently discarded by the next `syncAll()`

**Files:** `data/OccurrenceScheduler.kt:335-345` and `:370-375`;
`util/notification/NotificationUtils.kt:215-240`

`snoozeBatchCheckIn` and `armBatchCheckInInternal` both arm the *same* PendingIntent — a fixed
request code `BATCH_NOTIFICATION_ID * 4 + RC_FIRE` (`= 3996`) with `FLAG_UPDATE_CURRENT` — so the
later call unconditionally replaces the earlier one. `syncAll()` ends with `armBatchCheckIn()`
(`:325`) and runs on app resume (`MainActivity.kt:120-130`), boot, `MY_PACKAGE_REPLACED`, timezone
change, the daily worker, after every remote apply via `requestResync()`, and after
`setHabitCheckinTime` / `setDefaultSnoozeMinutes` / the three quiet-hours setters.

**Concrete failure scenario.** At 21:00 the combined check-in fires. The user taps *Snooze* from the
shade; the alarm is re-armed for 21:10. They then open the app to look at something — `onResume` →
`syncAll()` → `armBatchCheckIn()` → today's 21:00 has passed, so it arms **tomorrow** 21:00,
overwriting the snooze. The snoozed check-in never returns.

Secondary: `snoozeBatchCheckIn` is the only resolve action **not** taken under `syncMutex` (compare
`completeAllBatchToday`, `:361`), so it can also interleave with an in-flight `armBatchCheckInInternal`
even without a full sweep.

**Fix direction.** Persist a `batch_snooze_until` timestamp and have `armBatchCheckInInternal`
honour it (arm to `max(nextCheckin, snoozeUntil)`), or give the snooze its own request code so a
sweep can't clobber it. Take `syncMutex` in `snoozeBatchCheckIn` for symmetry.

---

### N-4 · MEDIUM · No receiver for exact-alarm permission changes → up to 30 h of silence after a revoke

**File:** `app/src/main/AndroidManifest.xml` (receiver block), `util/alarm/BootCompletedReceiver.kt:36-43`

`REARM_ACTIONS` covers boot, quickboot, `MY_PACKAGE_REPLACED`, timezone and time change — but not
`AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+).

**Concrete failure scenario.** On Android 12+, revoking *Alarms & reminders* causes the system to
kill the app's process **and cancel all of its exact alarms**. Nothing re-arms them: the app has to
be opened, rebooted, or wait for `WindowRefreshWorker`'s 24-hour period (+6 h flex). A user who
revokes the permission expecting "inexact instead of exact" gets "nothing at all" for up to 30 hours.
The mirror case is also unhandled: after *granting* the permission, already-armed inexact alarms stay
inexact until the next sweep.

**Fix direction.** Add the action to the manifest filter and to `REARM_ACTIONS` (guarded by
`Build.VERSION.SDK_INT >= S`); the existing `syncAll()` body already does the right thing.

---

### N-5 · MEDIUM · OEM process-kill / battery restriction drains the 7-day window with no signal

**Files:** `util/work/WindowRefreshWorker.kt:49-59`; `data/OccurrenceScheduler.kt:99-114` (`WINDOW_DAYS = 7`)

Occurrence rows only exist for the next 7 days, regenerated by `syncAll()`. `WindowRefreshWorker` is
the only thing that keeps the window topped up for a user who never opens the app — exactly the user
the worker exists for. On aggressive OEM ROMs (Xiaomi/Oppo/Vivo/Samsung), a force-stopped or
background-restricted app's WorkManager jobs never run.

**Concrete failure scenario.** User answers everything from the shade and never opens the app. Their
launcher's "battery saver" force-stops Daybook. Existing AlarmManager alarms still fire (they survive
a force-stop only until the *next* reboot, and a force-stop cancels them outright on most ROMs), but
even in the best case, after 7 days the window has drained: `armNextTaskInternal` logs
`"no pending occurrence in window — nothing armed"` (`:197`) and the app goes permanently silent. No
notification, no in-app warning. The app never checks
`ActivityManager.isBackgroundRestricted()` or `PowerManager.isIgnoringBatteryOptimizations()`, and
the manifest declares no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

**Fix direction.** Surface `isBackgroundRestricted()` / `isIgnoringBatteryOptimizations()` in the
Settings → Notifications health row alongside `notificationBlockReason()`, with a one-tap intent to
the battery-optimisation exemption dialog. Consider a "last reminder fired" heartbeat that warns in
the app if nothing has fired in N days despite active reminders.

---

### N-6 · MEDIUM · Inline Reply acknowledges before it writes

**File:** `app/src/main/java/com/daybook/app/util/alarm/NotificationActionReceiver.kt:73-77`

```kotlin
ACTION_REPLY -> if (!isHabit) {
    notificationUtils.postReplyAck(notificationId, title, replyText.orEmpty())
    scheduler.logFoodMed(occurrenceId, replyText.orEmpty())
}
```

Both are inside `withTimeout(8_000)`. `logFoodMed` takes `syncMutex`
(`OccurrenceScheduler.kt:411`), which a `syncAll()` sweep or a cold Hilt + Room initialisation can
hold.

**Concrete failure scenario.** User types "porridge and coffee" into the notification's inline reply
on a cold-started, low-end device. `postReplyAck` immediately replaces the RemoteInput spinner with
"Logged: porridge and coffee". `logFoodMed` blocks behind a `WindowRefreshWorker` sweep for >8 s;
`withTimeout` cancels the coroutine, Room rolls the transaction back, the `finally` block cancels the
notification, and `pending.finish()` returns. The user was told it was logged; nothing was written,
and nothing anywhere records the loss beyond a `Log.e`.

**Fix direction.** Write first, ack second; or keep the optimistic ack but, on timeout/failure,
re-post a distinct "Couldn't save — tap to retry" notification instead of cancelling silently.

---

### N-7 · MEDIUM · Reply on an already-answered reminder silently overwrites the answer

**Files:** `data/OccurrenceScheduler.kt:411-437`, `:716-717`;
`data/local/FoodMedOccurrenceDao.kt:86-94`

```kotlin
internal fun isFoodMedEdit(status: Occurrence.Status, callerSaysEdit: Boolean): Boolean =
    callerSaysEdit || status != Occurrence.Status.PENDING
```

The notification path calls `logFoodMed(occurrenceId, replyText)` with the default `isEdit = false`,
but the status check alone makes any non-PENDING occurrence take the edit-in-place branch →
`editFoodResponse`, which replaces `response_text` and appends **no** event.

**Concrete failure scenario.** User logs "eggs" on the Today card at 08:05. The reminder's shade
notification is still present (the cancel path is best-effort). At 09:00 they see it, assume they
forgot, tap Reply and type "coffee". "eggs" is silently overwritten with no trace in the Activity
timeline — the original `REPLIED` event stays, so the history says one reply happened and shows the
wrong text.

**Fix direction.** For the notification-reply path specifically, no-op (or append) when the
occurrence is already resolved, and let deliberate edits come only from the in-app editable form
which does pass `isEdit = true`.

---

### N-8 · MEDIUM · `completeAllBatchToday` mutates a whole day's occurrences without a transaction

**File:** `app/src/main/java/com/daybook/app/data/OccurrenceScheduler.kt:361-368`

```kotlin
for (occ in unresolvedBatchOccurrencesFor(now)) {
    db.habitOccurrenceDao().updateStatusResponded(occ.id, COMPLETED.name, now)
    db.habitEventDao().insert(HabitEvent(occurrenceId = occ.id, action = COMPLETED, itemId = occ.habitId))
}
notificationUtils.cancelNotification(BATCH_NOTIFICATION_ID)
```

Every other multi-row write in the scheduler uses `db.withTransaction` (`syncTaskInternal:139`,
`backfillHabit:577`, `backfillFoodMed:625`). This one doesn't, and it runs inside the receiver's
8-second budget.

**Concrete failure scenario.** User with 15 batch habits taps *Done* on the combined check-in. The
receiver's `withTimeout(8_000)` fires after 9 rows on a slow device. The coroutine is cancelled
mid-loop; 9 habits are complete, 6 are not — and the `finally` block cancels the notification anyway
(`NotificationActionReceiver.kt:44`), so the user has no way back to it. Their "all done" tap
half-applied.

**Fix direction.** Wrap the loop in `db.withTransaction` so it's all-or-nothing, and only cancel the
notification on success.

---

### N-9 · LOW · `LOCKED_BOOT_COMPLETED` is declared but can never be delivered

**File:** `AndroidManifest.xml` (BootCompletedReceiver intent-filter);
`util/alarm/BootCompletedReceiver.kt:38`

Neither the `<application>` nor the `<receiver>` is `android:directBootAware="true"`, so the system
will not deliver `ACTION_LOCKED_BOOT_COMPLETED` to it. (And it shouldn't: the Room DB lives in
credential-encrypted storage and would be inaccessible before first unlock.) Harmless, but it reads
as coverage the app does not have.

**Fix direction.** Remove the action from both the manifest and `REARM_ACTIONS`, or make the receiver
genuinely direct-boot-aware and have it only enqueue work rather than touch Room.

---

### N-10 · LOW · `postTestNotification` can pass while habit reminders are blocked

**File:** `util/notification/NotificationUtils.kt:144-155`

The test notification is built on `CHANNEL_FOOD_MED`. `notify()` does call `notificationBlockReason()`
first, which checks *both* channels, so a blocked habits channel does suppress the test — but the
suppression is silent (a `Log.w`), so "Send test notification" appears to do nothing at all rather
than explaining why.

**Fix direction.** Return the block reason to the caller and show it in the snackbar.

---

### N-11 · LOW · Channel versioning is correct; one note

`CHANNEL_HABITS = "habits_v2"` / `CHANNEL_FOOD_MED = "food_med_v2"`, with `LEGACY_CHANNEL_IDS =
["habits", "food_med"]` deleted on every `createNotificationChannels()` call
(`NotificationUtils.kt:105-116`), invoked from `DaybookApplication.onCreate` before any receiver can
post. The immutable-after-creation gotcha is handled and an old install cannot carry a poisoned
channel forward under the same id. The one gap: bumping the suffix again in a future build would
orphan the `_v2` channels in system settings unless they're added to `LEGACY_CHANNEL_IDS` at the same
time — worth a comment.

---

### N-12 · LOW · PendingIntent request codes and mutability — verified sound

Verified exhaustively, no collision found:

- `NotificationIdSequence` starts at `1_000` and never reuses (`NotificationIdSequence.kt:38-39`);
  `MAX = 500_000_000` keeps `id * 4 + 3 ≤ 2_000_000_003 < Int.MAX_VALUE`.
- Broadcast codes: fire `id*4+0`, refire `id*4+1`, actions `id*4+3`. Distinct occurrences have
  distinct ids, so the stride-4 carve-out never overlaps.
- `BATCH_NOTIFICATION_ID = 999` → `3996` / `3999`, strictly below the sequence's first code `4000`.
  The comment at `:64-69` is accurate.
- The four action buttons of one occurrence share `id*4+3` but differ by `Intent.action`, which
  `PendingIntent.filterEquals()` compares (extras are not compared, actions are) — correct.
- `contentIntent()` with no occurrence uses request code `0` on `getActivity`, a separate
  PendingIntent namespace from `getBroadcast` — no clash.
- `FLAG_MUTABLE` is used only for `ACTION_REPLY` (required by RemoteInput) on a non-exported
  receiver with an explicit component; everything else is `FLAG_IMMUTABLE`. Correct.

---

### N-13 · LOW · Re-arm races between boot, resume and the worker — verified fixed

The v0.5.3 "A2" fix is intact and correct in the current tree. `syncTaskInternal` /
`syncHabitInternal` spare in-schedule slots via `deleteFuturePendingExcept`
(`OccurrenceScheduler.kt:137-155`, `:246-263`; DAO `HabitOccurrenceDao.kt:97-98`), so a surviving
row keeps its `notification_id` and therefore its request code, and `FLAG_UPDATE_CURRENT` replaces
rather than stacks. `armNext*` refuses to re-arm an overdue row that already has a `SHOWN` event
(`:200-203`, `:283-286`), so a `syncAll()` sweep cannot produce a second `SHOWN` row or a second
post. `AlarmReceiver` additionally guards the `SHOWN` insert with `!isRefire && !hasShownEvent`.
`requestResync()` coalesces per-month-doc re-arms into one debounced sweep (`:230-239`). Every
mutation is serialised by `syncMutex`. I could not construct a duplicate-notification path; the fix
holds, including against the batch check-in added since.

---

## 4. §3 — Crash-surface findings

Counts: **0 Critical, 3 High, 6 Medium, 7 Low.** Grouped by category.

---

### 4.1 Force-unwraps and `lateinit`

**C-1 · LOW · The only `!!` in the tree is in the app-lock verify path.**
`data/lock/PinHasher.kt:58`:

```kotlin
return MessageDigest.isEqual(fromHex(hash(pin, salt))!!, expected)
```

Safe by construction — `hash()` returns `toHex(...)`, always an even-length lowercase hex string, so
`fromHex` cannot return null. But it is an unguarded assertion in the unlock path: if the KDF or
`toHex` ever changes, the failure mode is a crash on the lock screen (the one screen from which the
user cannot escape). One `?: return false` costs nothing.

**C-2 · verified clean.** All 15 `lateinit` sites are Hilt `@Inject` fields on `@AndroidEntryPoint`
receivers / `MainActivity` / `@HiltAndroidApp` — all initialised before `onReceive`/`onCreate`
bodies run. No `GlobalScope` anywhere. No `TODO`/`FIXME`/`HACK`/`XXX` in the whole `app/src/main`
tree.

---

### 4.2 Swallowed exceptions

**C-3 · HIGH · `MonthPartitioner.decodeDays` returns `emptyList()` on a corrupt payload, and
`applyRemoteMonth` treats that as a valid empty month.**
`data/sync/MonthPartitioner.kt:102-108`; `CloudSyncRepository.kt:925-934`

```kotlin
fun decodeDays(text: String): List<DayEntry> = runCatching { … }.getOrDefault(emptyList())
```

Compare `decodeDefinitionsJson` (`:115-118`) which returns `null`, and `applyRemoteParent` which
correctly refuses to apply a null (`:885-892`) *and* refuses an empty definitions set (`:889-892`).
The month path has neither guard.

**Concrete failure scenario.** A month blob is truncated (a gzip stream corrupted in the Firestore
cache, or a payload written by a future/buggy client). `gunzipToString` throws → caught by
`runCatching` in `applyRemoteMonth` → returns `emptyList()`. `importMonth(month, emptyList())` runs a
merge with no incoming ids: `mergeMonth` puts every local past PENDING row into `deletePending`
(`ExportImportRepository.kt:870`) and they are deleted, then the **remote hash is stored** as if the
merge succeeded (`CloudSyncRepository.kt:937-938`). The device now believes it holds that month
correctly, will never re-fetch it, and the next re-export/push may propagate the truncated view.

**Fix direction.** Make `decodeDays` return `List<DayEntry>?` and have `applyRemoteMonth` return
false on null, mirroring `applyRemoteParent`. Add a sanity check: refuse to apply a month whose
decoded day count is zero when the doc's `contentHash` differs from `ContentHash.ofDays(emptyList())`.

**C-4 · HIGH · `JournalViewModel.save()` and `RespondViewModel.resolve()` report success
unconditionally.**
`ui/journal/JournalViewModel.kt:191-204`; `ui/respond/RespondViewModel.kt:~195`

```kotlin
viewModelScope.launch {
    runCatching { occurrenceScheduler.backfillFoodMed(...) }   // result discarded
    _state.update { it.copy(busy = false, saved = true) }      // always "saved"
}
```

And the callee itself early-returns silently on two guards:
`OccurrenceScheduler.backfillFoodMed:612-617` (`canBackfill` rejected, and *month not resident*,
each with only a `Log.w`).

**Concrete failure scenario.** User backfills a journal entry for 3 March while that month is not
hydrated (offline, or S-9's stuck-hydration state). `monthResident(date)` returns false;
`backfillFoodMed` logs and returns. The ViewModel sets `saved = true`, the screen pops, and the
long-form entry the user just typed is gone with no error. Same shape for `RespondViewModel.log()`
→ `logFoodMed` on a deleted occurrence (`occ == null` → `return@withLock`).

**Fix direction.** Give the scheduler's write methods a result type (or throw) and thread it back to
the UI; show an explicit "Couldn't save — that month isn't loaded yet, connect and retry" instead of
a false success. Note that `SettingsViewModel.exportRange` already models this correctly with
`HydrateResult.Offline` — the same discipline is missing here.

**C-5 · MEDIUM · `bootstrap` cannot distinguish "export failed" from "data conflict".**
`CloudSyncRepository.kt:334`

```kotlin
val localBackup = runCatching { exportImport.exportBackup() }.getOrNull()
```

A null makes `localDefsHash` null → `hashesEqual` false. Combined with a non-empty local DB that
route is `CONFLICT` (or `PUSH_LOCAL` with `prefetched = null` if no remote doc, which then
immediately re-exports). An `SQLiteException` during export therefore surfaces to the user as
"your phone and the cloud disagree, pick one".

**Fix direction.** Treat a failed export as "cannot decide" — set `SyncStatus.Error`, don't run the
bootstrap decision at all, and retry.

**C-6 · MEDIUM · `deleteRemoteDoc`'s partial-failure signal is swallowed twice.** Covered as S-2;
noted here because the shape (`runCatching { … }.onFailure { Log.w } .isSuccess`, caller discards) is
the single most consequential silent catch in the tree.

**C-7 · LOW · `ExportImportRepository` leaks raw exception text to the user.**
`:361-363`, `:400-402`, `:579-581`, `:683-685` all do
`ImportResult(success = false, message = e.message ?: "…")`, and `SettingsViewModel` renders that
verbatim (`"Import failed: ${result.message}"`). For an `SQLiteException` (see S-3) the user sees raw
SQL. Map to friendly messages and log the raw text.

**C-8 · LOW · `DateTimeUtils.stringToTime` silently returns `LocalTime.MIN` on a parse failure**
(`:167-173`), so a malformed stored `"HH:mm"` becomes a midnight reminder rather than an error.
`jsonToDays` (`:55-64`) drops unparseable weekday names per-element — fine, but note that
`jsonToTimes` (`:44-49`) is the odd one out and **throws** (see C-13).

---

### 4.3 Coroutine safety

**C-9 · HIGH · No `CoroutineExceptionHandler` anywhere in the UI layer, and no crash reporter to
see the result.** 92 `viewModelScope.launch` sites across 13 ViewModels; `viewModelScope` uses a
`SupervisorJob`, which isolates *siblings* but does **not** swallow — an uncaught throw goes to the
thread's default uncaught handler, i.e. a process crash. Sites with no guard at all include
`RespondViewModel.init` (`:85-141`, the notification deep-link landing screen),
`JournalViewModel.init` (`:122-178`), `HomeViewModel`'s `stateIn` flows (no `.catch {}` anywhere),
`SettingsViewModel`'s ~20 one-line setters, and `AccountViewModel.useDisplayNameAsName`.

**Fix direction.** One shared `fun ViewModel.safeLaunch(block)` that installs a
`CoroutineExceptionHandler` (log + a user-visible error state), and `.catch {}` on the `stateIn`
pipelines that fold Room data.

**C-10 · MEDIUM · `DaybookApplication` launches an unguarded coroutine on a bare scope during
`onCreate`.** `DaybookApplication.kt:26`, `:46`:

```kotlin
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
…
appScope.launch { journalQuestionRepository.ensureSeeded() }
```

No `runCatching`, no handler. A throw here — the most likely cause being a Room open or migration
failure, which is exactly the state you least want to crash-loop in — kills the process on every
launch, with no way for the user to recover short of clearing app data.

**Fix direction.** Wrap in `runCatching` and log; the seed is idempotent and non-critical.

**C-11 · verified sound.** All three `BroadcastReceiver`s use the correct shape:
`goAsync()` + `CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler)` +
`withTimeout` (8 s foreground, 20 s for boot) + `finally { pending.finish() }`
(`AlarmReceiver.kt:56-72`, `NotificationActionReceiver.kt:30-48` and `:59-93`,
`BootCompletedReceiver.kt:51-73`). A throw inside the receiver's coroutine cannot take the process
down and cannot leak the broadcast token. This is genuinely well done.

**C-12 · verified sound.** Both `CoroutineWorker`s catch `Throwable` and return `Result.retry()`
(`WindowRefreshWorker.kt:33-42`, `SyncFlushWorker.kt:33-38`), and `SyncFlushWorker` backs off
linearly rather than hammering. `CloudSyncRepository`'s own `scope` is
`SupervisorJob() + Dispatchers.IO` with `runCatching` around every collected emission
(`:226-235`) — an exception in one debounced push cannot cancel the collector.

**C-13 · LOW · `DateTimeUtils.jsonToTimes` throws on a malformed `times_json`.** `:44-49` calls
`LocalTime.parse` with no guard, unlike its sibling `jsonToDays`. It is reached from
`HomeViewModel.buildItems` (`:459`, `:579`) inside a `flowOn(Dispatchers.Default) … stateIn`
pipeline with no `.catch {}` → a throw would crash the app on every Today-screen open. Currently
unreachable in practice: every writer (`DateTimeUtils.timesToJson`, `ExportImportRepository.joinTimes`
which filters via `runCatching`, and the form) produces valid values, so only DB corruption or a
future writer can trip it. Cheap to harden.

---

### 4.4 JSON / parsing / backup import

**C-14 · MEDIUM · A large file picked at Import is an uncatchable OOM crash.**
`util/StorageUtils.kt:76-77`; `ui/settings/SettingsViewModel.kt:287-315`

```kotlin
fun readText(uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
```

The whole file is materialised as a `ByteArray` and then again as a `String` (~3× the file size in
RAM). The caller's `catch (e: Exception)` does not catch `OutOfMemoryError`.

**Concrete failure scenario.** Settings → Restore → the system picker → the user mis-taps a video or
a large database dump instead of their backup JSON. The app dies instantly with no message. The same
shape exists on the export side (`json.toByteArray()` in `saveExport`, `:39`).

**Fix direction.** Stat the Uri (`OpenableColumns.SIZE`) and refuse anything over a few MB with a
clear message; stream the decode via `Json.decodeFromStream` rather than materialising the string;
and catch `Throwable` at the top of the import/export handlers.

**C-15 · verified sound.** The JSON layer is otherwise well-defended and a malformed or old backup
does **not** crash the app:

- `JsonUtils` (`util/JsonUtils.kt:22-31`) sets `ignoreUnknownKeys`, `coerceInputValues`,
  `explicitNulls = false` — an unknown enum name coerces to the declared default rather than
  throwing, and a missing field falls back.
- Every `decode` call site is guarded: `importAllData` wraps it in `runCatching { … } ?: UNSUPPORTED`
  (`ExportImportRepository.kt:250-251`), `ContentHash.ofJson` returns null on failure,
  `MonthPartitioner.decodeDefinitionsJson` returns null, `JournalQa.decode` returns an empty list,
  `SyncStateStore.monthHashes`'s getter defaults to an empty map.
- A v1 file is rejected explicitly by `meta.formatVersion` (`:252-254`) and an empty-definitions file
  by `:256-258`.
- `PayloadCodec.gunzip` reads into memory but is bounded by the 1 MiB Firestore doc limit.

The single hole is C-3 (`decodeDays`'s empty-list default).

**C-16 · verified sound.** `@EncodeDefault(EncodeDefault.Mode.NEVER)` re-checked at every site that
needs it, against every field added in the recent rounds:

| Field | Added | `@EncodeDefault(NEVER)` | Verdict |
|---|---|---|---|
| `Definitions.journalQuestions` | v0.5.4 | ✅ `BackupModel.kt:67` | correct |
| `HabitDef.streakStartedAt` | v0.5.5 | ✅ `:89` | correct |
| `HabitDef.streakLongest` | v0.5.5 | ✅ `:95` | correct |
| `HabitDef.promptMessage` | customization | ✅ `:100` | correct |
| `HabitDef.motivation` | customization | ✅ `:103` | correct |
| `IntakeReminderDef.motivation` | customization | ✅ `:133` | correct |
| `IntakeReminderDef.promptMessage` | v0.5.3 | ❌ (nullable, `explicitNulls = false`) | fine — a null is omitted anyway |
| `IntakeLog.qaJson`, `.redFlag`, `.suspectedFood`, `.outsideFood` | v0.5.2–4 | ❌ (all nullable) | fine — same reason |
| `BackupMeta.rangeStart/rangeEnd` | v0.5.3 | n/a | correctly excluded from `ContentHash` (which hashes `definitions + days` only) |

The rule that makes this work: `ContentHash`/`MonthPartitioner`/`JsonUtils` all set
`encodeDefaults = true` **and** `explicitNulls = false`, so a *nullable* field defaulting to null is
already omitted, and only a **non-null** field with a non-null default (`streakLongest = 0`,
`journalQuestions = emptyList()`) needs the annotation. Both such fields have it. **I found no
hash-churn field.** `IntakeReminderDef.promptMessage` is deliberately left un-annotated (documented
at `:130-132`) and that is correct, not an oversight.

---

### 4.5 Transaction safety

**C-17 · verified sound, with two exceptions.** `importAllData` (`:323-350`), `importMonth`
(`:561-577`), `applyRemoteDefinitions` (`:657-678`), `evictMonth` (`:697-705`),
`wipeLocalForSignOut` (`CloudSyncRepository.kt:283-292`), `runMaintenance`'s retention sweep
(`:704-707`), `syncTaskInternal`/`syncHabitInternal`'s window rewrite (`:139-163`, `:247-267`) and
both backfills (`:577-593`, `:625-669`) are all inside `database.withTransaction` — a malformed file
or a mid-write failure leaves the DB exactly as it was, as documented.

The two that are not:
- `AccountViewModel.wipeLocalData` (S-5) — six `deleteAll()`s outside a transaction.
- `OccurrenceScheduler.completeAllBatchToday` (N-8) — a per-occurrence loop outside a transaction,
  inside a receiver timeout.

**C-18 · MEDIUM · No crash reporter and no uncaught-exception handler at all.**
`app/build.gradle.kts` pulls `firebase-auth` and `firebase-firestore` from the BoM but **not**
`firebase-crashlytics` (and no `com.google.firebase.crashlytics` plugin); there is no
`Thread.setDefaultUncaughtExceptionHandler`, no ANR instrumentation, no StrictMode. Every finding
above that ends in "silently" is, today, literally invisible to you: you would learn about it only if
a user filed a report. `proguard-rules.pro` does set `-dontobfuscate` with readable
`SourceFile,LineNumberTable`, so traces *would* be readable — there is just nothing collecting them.

**Fix direction.** Add Crashlytics (the Firebase project already exists; it is a plugin + one
dependency + no code) and record the non-fatal cases explicitly:
`recordException` on the swallowed `runCatching` failures in `CloudSyncRepository`, the receivers'
`CoroutineExceptionHandler`s, and the workers' `catch (t: Throwable)`. That single change turns the
rest of this document from "theory" into "a dashboard".

---

## 5. §4 — What's already solid

Don't re-litigate these; they were checked against the current tree and hold.

1. **The receiver concurrency pattern is correct and consistent.** `goAsync()` + `SupervisorJob` +
   `CoroutineExceptionHandler` + `withTimeout` + `finally { pending.finish() }` in all three
   receivers, with a longer budget for boot. No path can leak the broadcast token or crash the
   process from a receiver.
2. **The A2 duplicate-alarm fix is genuinely still in place.** `deleteFuturePendingExcept` preserves
   in-schedule occurrence rows and therefore their `notification_id`/request codes; `armNext*` refuses
   to re-arm an overdue `SHOWN` row; `requestResync()` coalesces per-month re-arms. I could not
   construct a duplicate notification from boot / resume / worker / remote-apply interleaving.
3. **PendingIntent request-code arithmetic is airtight.** The `id*4+slot` carve-out, the
   `BATCH_NOTIFICATION_ID = 999 < START = 1000` reservation, the `MAX` bound keeping `id*4` inside
   `Int`, and `FLAG_MUTABLE` restricted to `ACTION_REPLY` on a non-exported receiver are all correct.
   The comments explaining them are accurate.
4. **Notification channels are versioned and legacy ids are actively deleted**, from
   `Application.onCreate`, before any receiver can post. The immutable-after-creation trap is handled.
5. **`@EncodeDefault` discipline is complete.** Every field added in the last several rounds that
   could churn `definitionsHash` for a non-user has the annotation, and the ones that don't need it
   genuinely don't. This was the most likely place for a regression and there isn't one.
6. **`ContentHash` excludes `meta`** (including the new `rangeStart`/`rangeEnd`), so an export can
   never look like a change. The canonical `Json` config is shared across `ContentHash`,
   `MonthPartitioner` and `JsonUtils`, so hashes are stable across devices and processes.
7. **The month-eviction / deletion guards are correct.** `changedMonths`' caller contract is honoured
   (`knownResident` filtering in `doPush`), `pushDeletesAllowed` blocks diff-driven deletions on the
   automatic path, `onLocalDataReplaced` pins the bookkeeping after an import, and
   `evictStaleMonths` never touches a pinned month or one whose hash doesn't match the cloud. The
   S5/S5c class of bug is closed.
8. **Firestore security rules have no privilege gap.** Every read/write path the client performs is
   inside what the rules allow, and nothing outside the owner's own subtree is reachable.
9. **`mergeMonth`'s per-log merge is the right design** — it keeps a local resolved row the cloud
   doesn't name (this device's unseen offline edit) rather than the old wholesale clear, which is
   what makes the two-device same-month case survivable.
10. **The `local_date` column (S17)** correctly makes history bucketing timezone-stable, and
    `BootCompletedReceiver` correctly *stopped* force-pushing on timezone change as a result. The
    reasoning in the comment is sound.
11. **`SyncStateStore` is deliberately outside Room**, avoiding an invalidation feedback loop — the
    right call, clearly explained.
12. **The JSON layer will not crash on a bad backup.** `coerceInputValues` + `ignoreUnknownKeys` +
    `explicitNulls = false` + `runCatching` at every decode site + explicit `formatVersion` and
    empty-definitions rejection.
13. **The pure/impure split is exemplary.** `SyncLogic`, `MonthPartitioner`, `ContentHash`,
    `PayloadCodec`, `QuietHours`, `RetentionPolicy`, `mergeMonth`, `chunkByBytes`, `canBackfill`,
    `unresolvedBatch` are all side-effect-free and unit-testable. This is why the audit above could
    be this specific.

---

## 6. §5 — Recommended next steps

Prioritised. Recommendation only — nothing here was implemented.

**Do first (a broken feature, in shipping builds):**

1. **S-1** — filter `remoteMonthHashes` to the resident set in `bootstrap`'s `hashesEqual`. This is
   one expression and it un-breaks cloud sync for every user past month three. Add a regression test
   alongside `BootstrapDecisionTest`: "evicted month present remotely, absent locally ⇒ ATTACH_ONLY,
   not CONFLICT."
2. **C-18** — add Crashlytics + `recordException` at the swallowed-failure sites. Cheap, and every
   other item on this list becomes observable.
3. **N-1** — route the `AlarmReceiver` refire through `quietDefer`, and add `setOnlyAlertOnce` to
   refire posts. Quiet hours is currently the opposite of what it says.

**Do next (silent data loss / wrong-account data):**

4. **C-4 + S-13** — make `backfillFoodMed` / `backfillHabit` / `logJournal` return a result and stop
   reporting a rejected save as `saved = true`.
5. **S-4** — one shared sign-out wipe covering `journal_questions` and the identity columns of
   `app_settings` (+ the photo file). Then delete `AccountViewModel.wipeLocalData` (**S-5**) and call
   the shared one.
6. **S-2** — make `deleteRemoteDoc()`'s boolean block the auth deletion; add a Cloud Function
   `onDelete` sweep as the backstop.
7. **C-3** — `decodeDays` returns null on failure; `applyRemoteMonth` refuses a null.

**Then (correctness under load / on older devices):**

8. **S-3** — chunk every `IN (:keep)` / `IN (:ids)` list to stay under 999 parameters, and let
   `applyRemoteMonth == false` clear `hydrationAttempted` so the month can retry.
9. **N-3** — persist a batch-snooze timestamp so `syncAll()` can't clobber it; take `syncMutex` in
   `snoozeBatchCheckIn`.
10. **N-2** — don't write `SHOWN` for a suppressed post; add a Today banner when
    `notificationBlockReason() != null`.
11. **N-8** — wrap `completeAllBatchToday` in a transaction.
12. **C-14** — size-check the import Uri and catch `Throwable`.

**Then (robustness / hygiene):**

13. **N-4** — handle `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
14. **N-5** — surface `isBackgroundRestricted()` / battery-optimisation state in the Settings health row.
15. **S-6** — either fix the `hydrating` guard properly or delete it and correct the comments.
16. **S-7 / S-8 / S-9 / S-10** — prune `monthHashes` on eviction; sort before `.take(30)`; bounded
    hydration retry; expire `conflictPromptShownForUid`.
17. **N-6 / N-7** — ack after write; don't let a notification reply overwrite an existing answer.
18. **C-9 / C-10 / C-13 / C-1 / N-9 / S-12 / C-7** — the small ones: a shared `safeLaunch`,
    `runCatching` around the app-scope seed, guard `jsonToTimes`, drop the `!!`, remove the dead
    `LOCKED_BOOT_COMPLETED` declaration, fix `deleteForLocalMonth` for NULL `local_date`, stop showing
    raw exception text.
