# Daybook — Full Bug Audit (14 Sep 2026)

**Scope:** the whole app as it is in the working tree today (v0.7.1, build 38, including the
uncommitted changes). I read the sync engine, backup/import, reminders and notifications, Health
Connect, the AI/Daily Report code, auth and app lock, Beast Mode, the main screens and ViewModels, the
theme colours, and the manifest, backup rules and build config.
**Nothing was changed.** This is a read-only report.

**Severity**
- **Critical**: the user's data is permanently lost or overwritten, often with no warning.
- **High**: something fails silently, or the app does the wrong thing and the user can't tell.
- **Medium**: a real bug with a workaround or limited impact, or a clear UX problem.
- **Low**: a minor or cosmetic issue, or a risk that is far away.

**Confidence**
- **Confirmed**: the failure path can be traced step by step in the code.
- **Likely**: depends on timing or device behaviour. Check on a device (steps included).

**Previously reported and still open:** several findings from `BUG_AUDIT_REPORT.md` (12 Sep) are
**still unfixed** in the current code. They are marked *(still open: BUG_AUDIT_REPORT §x)*.

---

## Summary

| Area | Critical | High | Medium | Low |
|---|---|---|---|---|
| 1. Data loss, backup & sync | 5 | 7 | 4 | 2 |
| 2. Silent failures (reminders, Today, journal) | 0 | 3 | 5 | 3 |
| 3. Health Connect | 0 | 3 | 5 | 2 |
| 4. AI / LLM integration | 0 | 2 | 5 | 5 |
| 5. Crashes, app lock, state bugs | 0 | 1 | 4 | 5 |
| 6. UI/UX: contrast, readability, touch targets | 0 | 2 | 4 | 2 |

### Fix these first (top 10)
1. **C1**: importing a Daybook backup deletes all Beast Mode routines, custom exercises, workouts and health data.
2. **C2**: importing a Beast Mode backup wipes workout and health history for *all* dates, not just the file's dates.
3. **C5**: "Keep my data on this phone" during account deletion still erases everything.
4. **C3**: another device's sync deletes workouts, including a workout in progress, and health data on this phone.
5. **H-S2**: signing out doesn't clear workouts, health or AI summaries, so the next account inherits them and uploads them to its own cloud.
6. **H-S1**: people who only use Beast Mode get no cloud backup at all, and their backups can't be restored.
7. **H-X1**: with app lock on, returning from the file picker, photo picker or Health Connect locks the app, and the picked file or permission result is silently lost.
8. **H-HC1/HC2**: Health Connect pulls can overwrite good data with blanks while reporting "Last updated just now".
9. **H-AI1**: switching off every AI category sends *everything* to the AI provider.
10. **H-R1**: logging, completing or skipping a past-day item from the Today card can fail silently.

---

## 1. Data loss, backup & sync

### C1. Importing a "Daybook" backup deletes all routines and custom exercises, and wipes workouts, health and AI summaries — CRITICAL · Confirmed
**What the user sees:** In Settings → Backup & data they export a date range, then later tap "Import
Daybook JSON" with that file. Afterwards every Beast Mode routine and custom exercise is gone (for all
time, not just the file's dates). Every workout, health day and AI summary inside the file's months is
gone too. Sync then uploads the deletions, so the cloud copy is lost as well.

**Why:**
- Every export from that screen is date-ranged (`exportDaybookRange`) and *strips* workouts, health,
  routines and custom exercises (`ExportImportRepository.kt:174-181`).
- A date-ranged file is imported through `importRange` (`:878-902`), which calls
  `applyRemoteDefinitions(backup.definitions)`. That function deletes every local exercise and routine
  whose ID isn't in the file (`:1242-1260`). The file's lists are empty, so it deletes **all** of them.
- Each month is then merged with `importMonth`, which delete-then-inserts workouts, health and AI
  summaries for the month (`:1103-1138`). The file has none, so the month is emptied.

**Fix:** When the file is a Daybook-kind file, never touch the exercise, routine, workout, health or AI
summary tables. Split `applyRemoteDefinitions` and `importMonth` into a Daybook part and a Beast Mode
part. Also fix the confirmation copy ("replaces all current data" is wrong for range files, see L-S2).

### C2. Importing a Beast Mode backup wipes workout and health history for every date — CRITICAL · Confirmed
**What the user sees:** They export Beast Mode data for, say, August, then import it. All workouts and
health data from every *other* month, plus all routines and custom exercises not in the file, are
deleted.

**Why:** Beast Mode exports are always date-ranged (`SettingsViewModel.kt:362-396`).
`importBeastModeBackup` ignores `meta.rangeStart`/`rangeEnd` and calls `deleteAll*` on all nine Beast
Mode tables before inserting the file (`ExportImportRepository.kt:219-270`).

**Fix:** Apply the same non-destructive per-month merge that Daybook range files use. Only replace the
months the file actually covers. Merge definitions without deleting.

### C3. A sync from another device deletes this phone's unsynced workouts and health data, including a workout in progress — CRITICAL · Confirmed *(still open: BUG_AUDIT_REPORT §1.1)*
**What the user sees:** Signed in on two devices (or after reinstalling). They log a workout on
phone A, and phone B pushes a change to the same month. Phone A's workout (even the live one they're in
the middle of) disappears. The same happens to health data: a phone without the fitness band pushes the
month and wipes the band phone's steps, sleep and heart rate.

**Why:** `importMonth` handles habits and intake with a careful merge, but for workouts, health and AI
summaries it deletes everything in the month and re-inserts only what the cloud has
(`ExportImportRepository.kt:1103-1138`). This path runs from the live months listener
(`CloudSyncRepository.kt:906-915`) and from `ensureMonthHydrated`.

**Fix:** Merge by ID, as habit occurrences already do: keep local sessions and health rows the cloud
doesn't mention, and never delete a session whose status is `ACTIVE`.

### C4. Hevy import can overwrite old cloud months with only the imported workouts — CRITICAL · Likely (race)
**What the user sees:** They import a Hevy CSV covering old months. Afterwards those months in the cloud
contain only the Hevy workouts, and their habit and intake history is gone on every device.

**Why:** `HevyImporter.doImport` fetches the old months from the cloud (`hydrateRange`), then **releases
the pins in `finally` before writing anything** (`HevyImporter.kt:112-125`). `endRangeExport()`
immediately launches `runMaintenance()` (`CloudSyncRepository.kt:811-820`), which evicts any
non-recent, unpinned month whose hash matches the cloud. That describes exactly the months just fetched.
The import then inserts sessions into the now-empty months. The next push sees those months as resident
and uploads a month document with only the imported workouts.

**Fix:** Call `endRangeExport()` only after the import transaction has committed. Better still, make
`endRangeExport` not trigger eviction.
**Verify:** import a Hevy file spanning 3+ months older than the last two, then check Firestore
`users/{uid}/months/{old month}` payload size before and after.

### C5. Account deletion erases local data even when "keep my data on this phone" is chosen — CRITICAL · Confirmed
**What the user sees:** Account → Delete account, leaving "also erase local data" **off**. The message
says "Account deleted. Your data on this phone is untouched." In fact every habit, reminder, history
entry and alarm on the phone is wiped.

**Why:** Deleting the Firebase user signs the user out, which emits `AuthState.SignedOut`.
`CloudSyncRepository.onAuthState` treats every genuine sign-out from a known account as a reason to run
`wipeLocalForSignOut()` (`CloudSyncRepository.kt:233-247`). The `alsoEraseLocal` flag
(`AccountViewModel.kt:180-181`) is never consulted on that path.

**Fix:** Set a "deleting account, keep local" flag before `deleteAccount()`, have `onAuthState` skip the
wipe when it's set, and still clear sync bookkeeping.

### H-S1. Beast Mode–only users never sync, and their backups can't be restored — HIGH · Confirmed
**What the user sees:** Someone who only tracks workouts and health (no habits or intake reminders) sees
"Synced", but nothing is ever uploaded. On a new phone nothing comes back. Importing their own full
backup says "This backup was made by an older version of Daybook."

**Why:** The "is there any data" checks only look at habits and intake:
- `isLocalEmpty()` (`CloudSyncRepository.kt:1327-1329`) makes `doPush` return early (`:500`).
- `applyRemoteParent` refuses definitions with no habits or reminders (`:965`).
- `importAllData` (`ExportImportRepository.kt:720`), `importRange` (`:879`) and
  `applyRemoteDefinitions` (`:1161`) all reject such files as "unsupported".

**Fix:** Count custom exercises, routines, workout sessions and health days as "not empty" in all five
places.

### H-S2. Signing out leaves the previous account's workouts, health and AI summaries on the phone, and uploads them to the next account — HIGH · Confirmed (privacy)
**What the user sees:** On a shared phone, person A signs out and person B signs in. B sees A's
workouts, weights, sleep and AI summaries, and they are pushed into B's cloud.

**Why:** `wipeAllLocalData()` clears only the 8 original tables (`CloudSyncRepository.kt:277-307`). The
six workout tables, three health tables and `daily_report_ai_summaries` are not cleared. The Health
Connect sync token and status (`HealthSyncStateStore`) are also kept. The cross-account push guard
(`refusePushLocalForForeignUid`) is skipped because `isLocalEmpty()` returns true.

**Fix:** Wipe all 18 data tables and reset `HealthSyncStateStore` in the same transaction.

### H-S3. Definitions are all-or-nothing, last writer wins: a new habit, reminder, routine or exercise can vanish — HIGH · Confirmed (multi-device)
**What the user sees:** They add a habit on phone B. Within a few seconds (or while B is offline), phone
A saves any definition change. When A's definitions reach B, B's new habit is deleted.

**Why:** `applyRemoteParent` → `applyRemoteDefinitions` deletes every local habit, task, exercise and
routine whose ID isn't in the remote list (`ExportImportRepository.kt:1237-1260`). It doesn't check for
local, not-yet-pushed definition changes (`CloudSyncRepository.kt:959-995`). The comment calls this the
"S15 last-writer-wins window".

**Fix:** Keep local definitions created or edited after the last push (for example an `updatedAt` per
row, or a "pending local IDs" set), and merge by ID instead of deleting.

### H-S4. "Share backup…" creates an incomplete full backup, and restoring it deletes history — HIGH · Confirmed
**What the user sees:** A signed-in user taps "Share backup…" and saves the file somewhere safe. The file
is missing every month older than the last two (those only live in the cloud). Restoring it later
("Import Daybook JSON") **replaces all data** with the partial file.

**Why:** `shareLatestExport` calls `exportAllData()` directly, without `hydrateRange`
(`SettingsViewModel.kt:399-411`). The file has no range stamp, so import takes the full-replace path
(`ExportImportRepository.kt:802-847`).

**Fix:** Fetch all cloud months first (as range export does), or stamp the file as a range. Also rename
the button: it doesn't share "the latest export", it builds a new one.

### H-S5. Account deletion wipes the cloud before the account is actually deleted — HIGH · Confirmed *(still open: BUG_AUDIT_REPORT §1.11 and §1.2)*
**What the user sees:** They start "Delete account", the cloud data is deleted, then the Google re-login
prompt appears and they cancel. The account still exists but its cloud backup is gone. The app keeps
showing "Synced" and never re-uploads, because `monthHashes` still match.
Separately, `AuthRepository.deleteAccount()` and `reauthenticateWithGoogle()` return **Success** when
`currentUser` is null (`AuthRepository.kt:133, 147`).

**Fix:** Re-authenticate first, then delete the cloud data, then the user. After any partial failure,
reset `syncState` so the next push re-uploads everything. Treat a null user as an error.

### H-S6. Offline, a push never finishes, which blocks all other sync work — HIGH · Likely
**What the user sees:** Offline, the account row says "Syncing…" forever (never "Offline"). "Sync now"
does nothing. Scrolling to an older month shows "Loading…" until the phone reconnects. Resolving a sync
conflict hangs.

**Why:** Firestore's `WriteBatch.commit()` Task only completes when the server acknowledges the write.
`doPush` awaits it while holding `pushMutex` (`CloudSyncRepository.kt:638`). Everything else takes the
same lock: `syncNow`, the remote listeners, `ensureMonthHydrated`, `runMaintenance` and
`resolveConflict`. `isOffline()` is never reached.

**Fix:** Don't await `commit()` inside the lock. Firestore already queues offline writes, so treat
"enqueued" as done and mark `pendingPush` cleared on the ack listener. Or add a timeout that reports
`Offline`.
**Verify:** turn on airplane mode, edit a habit, open Account and watch the status row.

### H-S7. Destructive database wipe when an older APK is installed — HIGH · Confirmed
**What the user sees:** Installing an older build over a newer one (common when side-loading test APKs
from this repo) silently deletes the entire local database. A signed-in user only gets the last two
months back; unsynced edits, reminder history and older months not yet resident are gone.

**Why:** `.fallbackToDestructiveMigrationOnDowngrade()` (`di/DatabaseModule.kt:65`).

**Fix:** Remove it. A downgrade should fail loudly (crash with a clear "please install the newer
version" message) instead of wiping data. At minimum, back up the DB file before Room opens it.

### M-S1. Unexpected "conflict" dialog on launch for the user's own unsynced changes — MEDIUM · Likely
**What the user sees:** They edit something and swipe the app away within ~3 seconds (the push delay),
or reminders pass without a notification firing (notifications blocked). On the next launch they get the
"This device vs cloud" conflict prompt and sync is paused.

**Why:** `bootstrap()` runs on every cold start. It compares a fresh local export with the cloud. Any
unsynced local change makes the hashes differ, including past reminder slots that now export as
"missed" (`ExportImportRepository.kt:321`). `promptShown` is only true if a conflict was resolved at
exactly the current remote revision (`SyncLogic.kt:135-140`). Nothing checks `syncState.pendingPush`.

**Fix:** If `pendingPush` is set and `lastSyncedUid == uid` and the remote revision equals
`lastKnownRevision`, push instead of prompting.

### M-S2. Android auto-backup and device transfer copy sync internals to the new phone — MEDIUM · Confirmed
**What the user sees:** After setting up a new phone from a Google backup or cable transfer, two phones
share the same sync device ID and one phone's changes can be silently ignored by the other. App lock
can turn itself off and saved AI keys disappear (restored encrypted-prefs keysets can't be decrypted on
the new device).

**Why:** `backup_rules.xml` and `data_extraction_rules.xml` include **all** shared prefs. The exclude
for `device_id.xml` does nothing because that file doesn't exist; the ID lives in `daybook_prefs`
(`SyncStateStore.kt:28-31`). The echo guard drops remote writes where
`deviceId == ours && revision <= lastKnownRevision` (`SyncLogic.kt:39`).

**Fix:** Exclude `daybook_prefs`, `daybook_lock*`, `daybook_ai_keys*` and `notification_id_seq` from
backup and transfer. Keep only theme/appearance prefs, moved into their own file.

### M-S3. ID-token listener may refresh the token in a loop — MEDIUM · Likely
**Why:** The `IdTokenListener` forces `user.getIdToken(true)` every time it fires
(`AuthRepository.kt:75-87`). A forced refresh issues a new token, which fires the listener again. That
means continuous token calls while online (battery, data, and possible Firebase rate limiting).
**Verify:** `adb logcat | grep -i "token"` while the app is open and online. The refresh count should
not keep climbing.
**Fix:** Check revocation only once per app start, or at most once an hour. Don't use the listener as
the trigger.

### M-S4. Deleting the account resumes a paused (conflicted) sync — MEDIUM · Confirmed
`deleteRemoteDoc()`'s `finally` sets `conflictPaused = false` (`CloudSyncRepository.kt:1269-1271`) even
if the user had dismissed an unresolved conflict earlier. Pushes then start again. Restore the previous
value instead.

### L-S1. The shared backup file stays in the cache forever — LOW · Confirmed (privacy)
`writeShareFile` writes the full history (medications, trigger foods, health data) as plain JSON to
`cache/exports/` and never deletes it (`util/StorageUtils.kt`). Delete old files before writing a new
one.

### L-S2. Misleading copy on the Backup screen — LOW · Confirmed
- The import confirmation says "replaces all current data" (`SettingsScreen.kt:1035`), but range files
  merge (and, per C1, silently delete the Beast Mode data).
- "Your data stays on this device" (`:1176`) is shown to signed-in users whose data syncs to the cloud.
- On Android 8–9 the export is written to the app's private folder and reported as a long absolute
  path. It is deleted if the app is uninstalled.

---

## 2. Silent failures: reminders, Today screen, journal

### H-R1. Today card: logging, completing or skipping a past-day item can fail silently — HIGH · Confirmed *(still open: BUG_AUDIT_REPORT §1.4)*
**What the user sees:** On a past day they type what they ate and tap send. The text box clears and the
card closes as if saved, but nothing was saved (the month isn't loaded yet, the item was archived, the
day isn't on the schedule, and so on).

**Why:** `HomeViewModel.replyToItem`, `completeItem` and `skipItem` ignore the `LogResult` from
`backfillFoodMed` and `logFoodMed` (`HomeViewModel.kt:742-809`). `backfillHabit` doesn't even return a
result and fails with only a log line (`OccurrenceScheduler.kt:728-761`). `HomeScreen` clears the
draft before the save runs (`HomeScreen.kt:740-750`).

**Fix:** Return `LogResult` from every path, show a snackbar on `Rejected`, and restore the draft.

### H-R2. Editing a journal-habit entry always says "saved" — HIGH · Confirmed
`HabitJournalEditViewModel.save()` wraps the save in `runCatching` and then sets `saved = true`
unconditionally, ignoring both exceptions and `LogResult.Rejected` (`HabitJournalEditViewModel.kt:75-83`).
The edit can be lost with no message.

### H-R3. Detail/Respond screen: Complete and Skip swallow errors and report success — HIGH · Confirmed *(still open: BUG_AUDIT_REPORT §1.3)*
`RespondViewModel.resolve {}` uses `runCatching { action() }` with no logging, then sets `done = true`
(`RespondViewModel.kt:222-229`). The screen closes as if the action worked.

### M-R1. "Snooze" is offered on rows where it can't work — MEDIUM · Confirmed
**What the user sees:**
- On a **past** day, Snooze on a missed item does nothing.
- On a **future** day (tomorrow's reminder), Snooze fires a notification in about 10 minutes, and the
  real reminder still fires tomorrow.
- On a reminder later today, Snooze makes it notify early.

**Why:** The overflow sheet shows Snooze for any unresolved row (`HomeScreen.kt:822-834`) and ignores
`HomeItem.canSnooze`. `snoozeItem` returns silently when there's no occurrence ID
(`HomeViewModel.kt:766-771`). `snoozeHabit`/`snoozeFoodMed` arm a re-fire at *now + interval*
regardless of the scheduled time (`OccurrenceScheduler.kt:604-613, 680-688`).

**Fix:** Only show Snooze when `canSnooze && scheduledEpoch <= now`.

### M-R2. The habit-journal chat is a dead end if the save is rejected — MEDIUM · Confirmed *(still open: BUG_AUDIT_REPORT §1.8)*
- After the last answer, a rejection shows a red bubble, but the input bar is gone (`!allAnswered`) and
  there is no retry button (`HabitJournalChatScreen.kt:98-107`). Leaving the screen loses every answer.
- While the save is still running, it already shows "✓ Entry saved" (`:98`).
- For past-day (backfill) entries nothing is drafted as the user goes (`HabitJournalChatViewModel.kt:214-217`),
  so leaving mid-chat loses all answers.

### M-R3. Notification buttons fail silently and dismiss the notification — MEDIUM · Confirmed *(still open: BUG_AUDIT_REPORT §1.5)*
If Skip, Snooze or Complete throws or times out, `NotificationActionReceiver` logs it and still cancels
the notification in `finally` (`NotificationActionReceiver.kt:292-309`). The reminder vanishes but
nothing was recorded. "Complete" on an intake notification is a silent no-op (`:272`).

### M-R4. One error permanently stops the evening batch check-in — MEDIUM · Confirmed *(still open: BUG_AUDIT_REPORT §1.12)*
`fireBatch()` re-arms tomorrow's alarm only at the end (`AlarmReceiver.kt:80-97`). Any exception before
that (a DB read or a notification post) means no check-in until the app is opened or the daily worker
runs. Put `armBatchCheckIn()` in a `finally`.

### M-R5. Missed reminders are recorded as "Skipped" by the user — MEDIUM · Confirmed (data meaning)
After 24 hours, unanswered slots are auto-set to `SKIPPED` (`OccurrenceScheduler.kt:207-213, 315-317`).
History, streaks, the Daily Report ("Skipped") and exports ("skipped") can't tell "I chose to skip"
from "I never saw it" (phone off, notifications blocked). That matters for medication tracking. Add a
separate `MISSED` status or a flag.

### L-R1. Deleting or archiving an item leaves other posted notifications in the shade — LOW · Confirmed
`cancelTaskInternal`/`cancelHabitInternal` cancel only the earliest pending occurrence's alarm and
notification (`OccurrenceScheduler.kt:262-269, 367-374`). Other already-posted notifications for that
item stay, and their buttons then do nothing.

### L-R2. Today's Overdue / Now / Later groups don't update while the screen is open — LOW · Confirmed
Grouping uses `System.currentTimeMillis()` read during composition (`HomeScreen.kt:129`). The ticker
only advances at 00:00/05:00/12:00/17:00/22:00. Items stay under "Later" after their time passes until
something else redraws the screen.

### L-R3. The notification-ID counter is used up on every refresh — LOW · Confirmed
`notificationIds.next()` runs for every slot on every `syncAll`, even when the insert is ignored
because the row already exists (`OccurrenceScheduler.kt:195-205, 303-313`). The counter wraps at 500M
and then reuses IDs, which brings back the "two reminders share one alarm" bug. It is years away, but
it's a one-line fix: only mint an ID when the row is new.

---

## 3. Health Connect

### H-HC1. Pulls overwrite good health data with blanks, and sync spreads the blanks — HIGH · Confirmed
**What the user sees:** Steps, sleep or weight for a day suddenly disappear. Examples: after revoking one
permission, after the band app drops older data, when Health Connect rate-limits a read, or when a
second phone without the band runs its pull.

**Why:** `pullWindow` builds a complete `HealthDay` for every day in the window and **upserts
(REPLACE)** it (`HealthRepository.kt:211-272`). Any metric not returned in this pass becomes `null`
over the stored value. Every day in the window gets a row even when nothing was read, so all-null
days are also exported and synced.

**Fix:** Merge field by field (keep the old value when the new read is null because of a failure or
missing permission). Skip writing a day when nothing was read.

### H-HC2. Health Connect failures are hidden and reported as success — HIGH · Confirmed
**What the user sees:** "Last updated just now" or "Imported your health history back to 15 Sep 2025",
while nothing was actually read.

**Why:** `dayAggregate` wraps every read in `runCatching { … }.getOrNull()` / `getOrDefault`
(`HealthConnectReader.kt:114-156`), so it almost never throws. `pullWindow` only reports failure when
**every** day failed **and** the session read failed (`HealthRepository.kt:306`). A security error or
rate limit on each call is counted as "no data".

**Fix:** Have `dayAggregate` return per-metric errors. Count a day as failed when any granted metric's
read threw, and surface "partially updated" in the status line.

### H-HC3. The Health privacy screen says data is "never sent anywhere else", but the AI features send it — HIGH · Confirmed (policy/privacy)
`HealthPermissionsRationaleActivity` (the screen Google requires for Health Connect) says the data
"stays on this device … it is never sent anywhere else". Daily Report AI summary and Chat send steps,
calories, heart rate, sleep, SpO₂, weight and hydration to the chosen third-party AI provider
(`DailyReportPrompt.kt:108-135`). That breaks Health Connect's data-use disclosure rules and is
misleading to the user. Update the text, and add an explicit consent step before health data is first
sent to an AI provider (see M-AI1).

### M-HC1. Late-arriving band data is never picked up — MEDIUM · Confirmed
When the changes token reports changes, the re-read only covers from the **date of the last pull** to
today (`HealthRepository.kt:144-155`). If the band syncs older days later (phone was off, band app
synced late, nutrition entered for yesterday after today's pull), those days are never re-read. Fix:
re-read from the earliest changed record's date (the `Change` records carry it), capped at 30 days.

### M-HC2. Data deleted in Health Connect never disappears in Daybook — MEDIUM · Confirmed
Sessions and weight readings are upsert-only, and deletion changes are ignored (`HealthConnectReader.kt:287-295`).
A workout or weight entry deleted in Mi Fitness or Samsung Health stays in Daybook and the cloud
forever. For each re-read window, delete local rows in that window that weren't returned.

### M-HC3. Sleep totals include awake time and the day assignment is inconsistent — MEDIUM · Confirmed
- `total += mins` runs for **every** stage, including `AWAKE` and `AWAKE_IN_BED`
  (`HealthConnectReader.kt:210-219`), so "Sleep 8h" can include an hour awake.
- A night that starts at 23:30 Monday counts as Monday; one that starts at 00:30 counts as Tuesday. On
  the Tuesday report, "last night" is sometimes there and sometimes not.

Exclude awake stages from the total, and attribute sleep to the day it **ends**.

### M-HC4. "Import my past data" is a huge, unthrottled, silent batch — MEDIUM · Likely
365 days × (1 aggregate + 3 record reads + 1 sleep read) is about 1,800 Health Connect calls in one
coroutine, with no progress indicator (`HealthRepository.kt:54-89`). Health Connect rate-limits
foreground apps, so later days silently come back empty (see H-HC2). It runs in a ViewModel scope, so
leaving the screen cancels it partway without telling the user. Batch by month, back off on rate-limit
errors, run it in WorkManager, and show progress.

### M-HC5. Health pulls run for users who never connected Health Connect — MEDIUM · Confirmed
`MainActivity.onResume` calls `pullOnResume()` for everyone (`MainActivity.kt:458-460`). On devices
without Health Connect this logs a Crashlytics error on every resume (the 15-minute throttle only moves
forward on success). On devices with Health Connect it writes "Daybook no longer has access to your
health data" as the status line for someone who never connected (`HealthRepository.kt:102-105`). Skip
the pull unless the user has connected at least once.

### L-HC1. Overlapping pulls can race on the changes token — LOW · Confirmed
The resume pull, the daily worker, "Refresh now" and "Import past data" have no lock between them. Two
pulls can read the same token and overwrite each other's `changesToken`/`lastPullAt`. Add a `Mutex`.

### L-HC2. Only the first page of records is read — LOW · Confirmed
`readRecords` ignores `pageToken` (`HealthConnectReader.kt:121-156, 193-195, 235-237`). A watch that
records SpO₂ every minute overnight can exceed a page, so min/max/average use partial data.

---

## 4. AI / LLM integration

### H-AI1. Switching off every category sends all data — HIGH · Confirmed (privacy)
In Settings → Daily Report AI, turning off Workout, Health, Intake and Habits saves an empty list, and
`parseReportCategories` treats empty as "everything on" (`DailyReportContext.kt:13-18`). The user
thinks nothing is shared, but the full report (including medication and trigger-food notes) is sent.
Store an explicit "none" value, and disable Generate/Chat when no category is on.

### H-AI2. No consent or warning before personal health and medical data goes to a third party — HIGH · Confirmed
Generate and Chat send intake logs, trigger-food flags, journal answers and health vitals to
OpenAI/Anthropic/Google/OpenRouter/NVIDIA/OpenCode (`DailyReportPrompt.kt`). No screen says so, except
a small note about the meta-prompt (`DailyReportAiSettingsScreen.kt:106`). Add a one-time consent
dialog per provider that lists what's sent, and link it from the Health privacy screen (H-HC3).

### M-AI1. Tapping Generate before the report loads caches a "nothing happened" summary — MEDIUM · Confirmed
`generate()` uses `uiState.value.report`, which is `null` until the flows emit
(`DailyReportViewModel.kt:175-209`). The prompt then says "No workout logged / No food/med entries
logged / Nothing scheduled", and the resulting wrong summary is **saved** for that day. Disable
Generate until `report != null`.

### M-AI2. A failed chat message stays in the transcript and the draft is lost — MEDIUM · Confirmed
`sendChatMessage()` appends the user message and clears the draft *before* the call
(`DailyReportViewModel.kt:401-419`). On failure the message looks sent, the draft is gone (the comment
says it's "preserved"), and retrying creates duplicate user turns. Remove the message on failure and
put the text back in the draft.

### M-AI3. Truncated or blocked AI answers aren't detected — MEDIUM · Confirmed
- Anthropic is hard-capped at `max_tokens = 1024` (`AnthropicProvider.kt:66`), so longer chat answers
  or multi-day summaries are cut off mid-sentence. `stop_reason` isn't checked.
- OpenAI-compatible providers don't check `finish_reason` ("length"). Reasoning models can return
  `content = null` after spending tokens on reasoning, which shows as "returned an empty response".
- Gemini safety blocks return no candidates plus `promptFeedback`, also shown as "empty response, try
  again", so retrying never helps.

### M-AI4. Misleading timeout messages, and requests that can't be cancelled — MEDIUM · Confirmed
The read timeout is 60 s (`OpenAiCompatibleProvider.kt:171-177`). Slower or reasoning models time out,
and a `SocketTimeoutException` is an `IOException`, shown as "Couldn't reach X. Check your
connection." `execute()` is blocking, so leaving the screen doesn't cancel the request (the user may
still be billed). Use `client.newCall().await()` with cancellation, show a separate "took too long"
message, and raise the timeout for chat.

### M-AI5. Best-set labels in the report and AI prompt are always in kg — MEDIUM · Confirmed
`DailyReportRepository.kt:265` formats the best set with `WeightUnit.KG` regardless of the user's unit,
while total volume uses their unit. An lb user sees "10 reps @ 40 kg" next to "880 lb total".

### L-AI1. The Gemini API key is sent in the URL — LOW · Confirmed
`?key=$apiKey` (`GoogleAiStudioProvider.kt:76-77, 119`) can show up in proxy and HTTP logs. The model
name is also put into the URL without encoding. Use the `x-goog-api-key` header.

### L-AI2. AI responses and error bodies are written to logcat in release builds — LOW · Confirmed
`Log.e(TAG, "empty/unparseable response body: $text")` and `friendlyHttpError` log full bodies (the
generated health summary). Remove these or restrict them to debug builds.

### L-AI3. Outdated model hints — LOW · Confirmed
The placeholder models `claude-3-5-haiku-20241022`, `gemini-2.0-flash` and `gpt-4o-mini`
(`AiProvider.kt:63-69`) are retired or old. Users who copy them get "didn't recognise that model name".

### L-AI4. Model picker filters are wrong — LOW · Confirmed
OpenRouter hides every paid model (`onlyFree = true`), so users with credits can't pick one from the
list. Gemini labels any model with "flash" in its name as free (`FREE_TIER_PATTERN = Regex("flash")`),
which mislabels paid image/TTS flash models.

### L-AI5. Small report and prompt gaps — LOW · Confirmed
- A day with only band workout sessions (no day summary) is sent as "No health data logged"
  (`DailyReportPrompt.kt:110`).
- "Calories" means active calories on some days and total on others (`:116`, and the same on screen).
- "OpenCode" uses the OpenCode Zen URL as an unverified guess (`AiProviderRegistry.kt:30`).
- The "Test key" spinner can stay stuck if the coroutine is cancelled, because it has no `finally`
  (`AiProvidersViewModel.kt:81-103`).

---

## 5. Crashes, app lock, state bugs

### H-X1. With app lock on, coming back from a system screen locks the app and silently drops the result — HIGH · Confirmed
**What the user sees:** With app lock set to "Immediately" (the default), they tap Import → choose a
file. They come back to the PIN screen, unlock, and land on **Today**. The import never happened. The
same happens with the profile photo picker, the Health Connect permission sheet, "Share backup" and the
notification settings screen.

**Why:** Any trip to another activity triggers `onStop` → `onAppBackgrounded()`, then `onResume` → locked
(`MainActivity.kt:417-433`, `AppLockRepository.kt:141-156`). When locked, `MainActivity` replaces the
**whole** `MainApp()` with `LockScreen` (`MainActivity.kt:353-406`). That disposes the NavController,
every `rememberLauncherForActivityResult` registration (so the picked file or permission result has
nowhere to go) and the screen-scoped ViewModels.

**Fix:** Draw the lock screen *on top of* `MainApp()` (in a Box) instead of replacing it, and/or add a
short grace period (for example 5 s) or a "launching system picker" flag.

### M-X1. A keystore problem silently turns app lock off — MEDIUM · Confirmed (security) *(still open: BUG_AUDIT_REPORT §1.13)*
If `EncryptedSharedPreferences` can't open (keystore reset, or restored from backup, see M-S2),
`AppLockRepository` falls back to an empty plain file where `enabled = false`
(`AppLockRepository.kt:175-189`). The lock just disappears with no message. Show "App lock was reset —
set a new PIN."

### M-X2. Multi-device exercise catalog clean-up can orphan logged sets — MEDIUM · Likely
`refreshExerciseCatalog` creates the "added" exercises with a **new random ID on each device**
(`WorkoutRepository.kt:220-231`). Those sync as custom exercises. When the other device's definitions
arrive, `applyRemoteDefinitions` deletes this device's copies (`ExportImportRepository.kt:1243-1247`).
Sets logged against the deleted IDs then show as "Exercise" or "Unknown exercise". Use fixed IDs for
built-in additions (for example `builtin-add:<slug>`).

### M-X3. The rest timer only exists while the screen is open — MEDIUM · Confirmed (UX)
`_restEndsAt` is in-memory ViewModel state (`WorkoutSessionViewModel.kt:148-181`). With the phone
locked or the app in the background there's no sound, vibration or notification when rest ends. It's
also lost if Android kills the process mid-workout. Schedule an alarm or notification when rest starts.

### M-X4. Load errors on the item Detail screen are never shown — MEDIUM · Confirmed *(still open: BUG_AUDIT_REPORT §1.9)*
`DetailViewModel.errorMessage` is set (`DetailViewModel.kt:166-201`), but `DetailScreen.kt` never reads
it. A failed load shows an empty screen.

### L-X1. Some coroutines can crash the app — LOW · Confirmed
These use a raw `viewModelScope.launch`/`launchIn` without error handling, so a DB error crashes the
process instead of going through `safeLaunch`:
- `HealthTabViewModel.kt:109-112`
- `WorkoutSessionViewModel.kt:111-125` (`base.onEach … launchIn`) and `:133-141`

### L-X2. The Health Connect privacy screen ignores the user's theme — LOW · Confirmed
`HealthPermissionsRationaleActivity` uses `DaybookTheme()` with defaults (dark, Charcoal, Lavender)
instead of the user's appearance settings.

### L-X3. Release build quietly falls back to debug signing — LOW · Confirmed
If `keystore.properties` is missing, release is signed with the debug key
(`app/build.gradle.kts:69-73`). Installing that over a real release fails with "App not installed". Fail
the build instead.

### L-X4. The crash log drops all older crashes once it reaches 256 KB — LOW · Confirmed
`CrashHandler.appendCrash` discards the whole existing file when it's over the cap
(`util/CrashHandler.kt`). Trim the oldest part instead.

### L-X5. The health status line on the Health tab can be stale — LOW · Confirmed
`statusLine()` and `statusIsFailure()` are read from SharedPreferences inside the `combine` lambda
(`HealthTabViewModel.kt:190-191`). They only refresh when another source emits, so a background pull
failure while the tab is open isn't shown. This was fixed in `WorkoutSettingsViewModel` but not here.

---

## 6. UI/UX: contrast, readability, touch targets

WCAG AA needs **4.5:1** for normal text and **3:1** for large text (≥18sp regular, ≥14sp bold) and
icons. Ratios below were calculated from the exact hex values in `ui/theme/ThemeStyle.kt`,
`Tokens.kt`, `Accent.kt` and `BeastTheme.kt`.

### H-U1. "Faint" text is too light in every theme — HIGH · Confirmed
`textFaint` is used for help captions (`HealthDetailSheet.kt:234`), disabled labels, settings chevrons
and some metadata.

| Style | on background | on card | on raised card |
|---|---|---|---|
| Charcoal (dark) | 3.95 | 3.61 | 3.28 |
| True black | 4.26 | 3.94 | 3.61 |
| Espresso | 3.68 | 3.42 | **3.15** |
| Midnight | 3.88 | 3.56 | 3.20 |
| Paper (light) | 3.10 | 3.22 | **2.87** |
| Pure white | 3.34 | 3.34 | 3.07 |
| Warm cream | 3.45 | 3.65 | 3.13 |
| Sepia | 3.30 | 3.60 | **3.00** |

The light pastel cards' `onFillFaint` (`#8A9099`) is worse: **2.48–2.86:1** on every tint.
**Fix:** Keep `textFaint` for decoration only. Darken it in light mode (about `#6E747C` gives ≥4.5 on
Paper) and lighten it in dark mode (about `#868C93`). Never use it for help text.

### H-U2. Placeholder text in input fields is nearly invisible — HIGH · Confirmed
Placeholders are `TextMuted` at 55% opacity (`components/Forms.kt:152`, `WorkoutSessionScreen.kt:979`,
the set-table "kg/reps" hints):

| Theme | on card | on raised card |
|---|---|---|
| Dark (Charcoal) | 2.94 | 2.83 |
| Light (Paper) | **2.39** | **2.29** |
| Sepia | – | **2.21** |

In light mode, empty fields read as blank boxes. Use full `TextMuted` at a lighter font weight or
smaller size instead of transparency (that still meets ≥4.5).

### M-U1. Coloured text on light pastel cards fails — MEDIUM · Confirmed
Cards use `tint.accent` as a *text* colour for metadata, "Start", next-time labels and quotes:

| Light tint | accent on card | on raised card |
|---|---|---|
| Butter | **3.23** | **2.98** |
| Mint | 3.32 | 3.04 |
| Peach | 3.47 | 3.13 |
| Rose | 3.61 | 3.26 |
| Lavender | 4.06 | 3.63 |
| Slate blue | 4.51 | 4.10 |

Sites: `RoutinesScreen.kt:314, 332, 361`, `FoodMedScreen.kt:259`, `DetailScreen.kt:200` (quote),
`DetailScreen.kt:358-380` (big stat numbers: Butter fails even the 3:1 large-text rule),
`WorkoutSessionScreen.kt:542`. All of these are 12sp `Metadata`/`Caption`. Add a darker `accentText`
per light tint (for example Butter `#8A5A12`, Mint `#0B6E65`) and use it for text only. (Dark-mode tints
are fine at 5.4–8.3:1.)

### M-U2. Success, warning and error text fails on the Cream and Sepia light styles — MEDIUM · Confirmed
Light signal colours on raised cards: Paper 4.31–4.48, Cream **4.07–4.23**, Sepia **3.65–3.80**. These
are used for 12sp error and warning captions: sync errors, "Suspected trigger", AI errors, export
results, lock PIN errors (`DailyReportScreen.kt:319, 340, 392, 551, 604, 694`,
`SettingsScreen.kt:1140, 1161`, `AppLockSettingsScreen.kt:168, 282`, `JournalScreen.kt:156`,
`RespondScreen.kt:215`). Darken them per style (for example Sepia danger `#A51C1C`, warning `#8A3F06`,
success `#0F6A31`).

### M-U3. The chosen accent colour used as text is too light on light backgrounds — MEDIUM · Confirmed
Light accents as text on Paper / Sepia: Mint **3.61 / 3.11**, Amber **3.51 / 3.03**, Coral **4.01 / 3.46**,
Lavender 4.54 / **3.91**, Sky 4.99 / **4.30**. Used for `LocalAccent` text and links
(`SettingsScreen.kt:1110`, `WorkoutSessionScreen.kt:230, 282`, `TextLink`). Button *labels* on accent
fills are fine (`onAccentInk` picks black or white correctly). Add an `accentText` token that darkens
the accent until it reaches ≥4.5 against the current background.

### M-U4. Touch targets are too small — MEDIUM · Confirmed (accessibility)
- The "⋮ More" buttons on every Today, Habits and Intake card, and the History button on habit cards,
  are **32dp** (`IconButtonSize.Sm`: `HomeScreen.kt:657-695`, `FoodMedScreen.kt:263`,
  `RoutinesScreen.kt:377, 384`). The minimum is 48dp.
- The duration "edit" badge in a live workout is **24dp** (`WorkoutSessionScreen.kt:241`).
- `CircleIconButton` sets its tap area to exactly its drawn size, with no
  `minimumInteractiveComponentSize()`. When disabled it is still clickable and focusable, so TalkBack
  announces it as a working button (`components/Components.kt:174-193`).

Add `Modifier.minimumInteractiveComponentSize()`, use `clickable(enabled = enabled, role = Role.Button)`,
and keep the smaller visual circle.

### L-U1. Important information is shown at 12sp in muted colours — LOW · Confirmed
`Caption` and `Metadata` are both 12sp (`theme/Type.kt:179-182, 210-213`) and carry errors, sync
status, reminder times and "Suspected trigger" warnings. Combined with M-U2 and M-U3 this is hard to
read, especially for users with larger system font settings. Use at least 13–14sp for error and warning
text.

### L-U2. Beast Mode "Violet" accent text on dark cards is borderline — LOW · Confirmed
Violet `#8B5CF6` on the dark card surface is **4.20:1** (fails for small text). Crimson is 4.84. Most
Beast text uses the brightened "hot" accent, which passes, but the session header uses the raw
`LocalAccent`.

---

## Checked and found OK
- Muted text (`TextMuted`) passes everywhere: 5.0–8.0:1 in all eight styles.
- Button labels on accent fills: `onAccentInk` gives ≥4.5:1 for all 5 app accents and 9 Beast accents
  in both modes.
- Dark-mode pastel card text (`onFillMuted` 5.9–7.0:1) and accents (5.4–8.3:1).
- Screens with text fields all handle the keyboard (`imePadding` or `StickySaveBar`).
- Firestore rules: every path is limited to the owner's `uid`, with shape and size validation.
- R8/ProGuard rules keep kotlinx-serialization and Room classes.
- The exact-alarm fallback to an inexact alarm, notification channel versioning, and quiet-hours
  deferral on every arm path are in place.
- AI keys are never included in exports or sync.

## How to verify the "Likely" items
| ID | Quick check |
|---|---|
| C4 | Import a Hevy CSV spanning months older than the last two, then compare those months' Firestore payload size before and after. |
| H-S6 | Turn on airplane mode, edit a habit, open Account. The status stays "Syncing…" instead of "Offline". |
| M-S1 | Edit a habit and swipe the app away within 2 s (while online), then reopen. A conflict dialog appears. |
| M-S3 | `adb logcat -s FirebaseAuth` while idle and online. Token refreshes should not repeat. |
| M-HC4 | Tap "Import my past data" with a band holding a year of data, then check the Crashlytics non-fatals and the number of days with data. |
| M-X2 | Two signed-in phones on a fresh install: log a set with an "added" exercise on phone B, let phone A sync, then look at B's history. |
