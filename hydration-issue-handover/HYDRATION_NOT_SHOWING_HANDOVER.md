# Hydration reminder still not appearing — handover, 2026-09-20

## Status: **RESOLVED in build 56** (2026-09-20). Root cause found and fixed — see
"## Resolution (build 56)" at the bottom. The investigation notes below are kept as written,
for the record of what was ruled in/out along the way.

## The symptom (owner's own words, across the round)

1. "today tab is not showing hydration. It is still not 9 [pm], but at least it should show in
   case I want to log early"
2. After build 54 (which was supposed to fix it): "still hydration is not fixed. Fix and share new
   apk. asap"
3. After build 55 (a second, independent self-heal attempt): "make a folder and put ur hydration
   related issue there. it is still not fixed."

So: two separate fix attempts have both failed to resolve it, and **no device logs have been
captured** at any point — every fix so far has been from static code reading, not a confirmed
root cause. Treat both prior "fixes" as unverified, not as ruled out.

## Background: how hydration got into this state

Hydration has existed as a feature since build 44-45 (v30→31 migration, `hydration_enabled` /
`hydration_goal_ml` / `hydration_unit` / `hydration_enabled_since` columns on `app_settings`, plus
a `hydration_days` table). Historically it rendered on Today via a **synthetic, non-database-backed
`HomeItem`** built directly in `HomeViewModel` — not a real scheduled reminder.

On 2026-09-20 (this round), a large refactor ("Hydration-as-Intake", see
`/home/abhiram/Downloads/app-for-food/SOURCE_PRIORITY_AND_HYDRATION_PLAN_2026-09-20.md` §2 at the
repo root) replaced that synthetic item with a **real system-managed `FoodMedTask`**
(`TaskType.HYDRATION`), flowing through the same `OccurrenceScheduler` / `FoodMedOccurrence` /
`AlarmReceiver` pipeline every other Intake reminder (food/med) already uses. This shipped in
**build 53**. The old `HydrationCard.kt` was deleted in the same round.

This is a bigger architectural change than it looks: hydration went from "always rendered
unconditionally by special-cased code" to "only rendered if a real `food_med_tasks` row exists and
`OccurrenceScheduler` has generated occurrence rows for it." If that row is never created, there is
**nothing** for Today or Intake to query — not a display bug, an absence-of-data situation.

## What's supposed to create the `FoodMedTask` row

`app/src/main/java/com/daybook/app/data/hydration/HydrationIntakeSync.kt` — `sync(enabled, timeHHmm)`:
- Reads `app_settings.hydration_task_id`. If null and `enabled == true`, inserts a new
  `FoodMedTask(type = TaskType.HYDRATION, ...)`, stores its id back into
  `app_settings.hydration_task_id`, then calls `OccurrenceScheduler.syncTask(task.id)` to generate
  the actual `food_med_occurrences` rows for the next 7 days.
- If a row already exists, it just updates `times_json` / archival state.
- **This function is only ever called from explicit UI actions**
  (`HydrationSettingsViewModel.setEnabled()` / `.setReminderTime()` in
  `app/src/main/java/com/daybook/app/ui/settings/HydrationSettingsSection.kt`) — i.e. only when a
  user actively flips the toggle or changes the time *after* build 53 was already installed.

## Attempt 1 (build 54) — Application-level cold-start bootstrap

Added to `app/src/main/java/com/daybook/app/DaybookApplication.kt`'s `onCreate()`:
```kotlin
appScope.launch {
    runCatching {
        val s = settingsRepository.getSettings()
        hydrationIntakeSync.sync(s.hydrationEnabled, s.hydrationReminderTime)
    }
}
```
Theory: an owner who already had `hydration_enabled = true` from before this round never gets
`sync()` called for them post-upgrade, so their `hydration_task_id` stays null forever. This should
self-heal it on the next cold start.

**Did not fix it**, per the owner's report after installing.

## Attempt 2 (build 55) — a second self-heal site, with visible failure logging

Same idempotent call, added a second time to `app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt`'s
`init {}` block (runs whenever the Today ViewModel is constructed — i.e. also independent of the
Application-level cold-start path), this time with `.onFailure { recordUnhandledException(it) }`
instead of a bare `runCatching` that silently eats errors.

**Also did not fix it.**

## What is NOT yet verified — this is the actual gap

Nobody has looked at:
- **Actual logcat / Crashlytics output from the owner's device.** Every fix so far has been a
  hypothesis from reading code, never confirmed against what's actually happening at runtime. This
  is the single highest-value next step — it would immediately show whether `sync()` is even being
  reached, whether it's throwing (and now, post-build-55, the exception would be visible in
  Crashlytics if `recordUnhandledException` reports there — check `CrashHandler.kt` /
  `recordUnhandledException`'s implementation to confirm it actually forwards to Crashlytics and
  isn't a no-op / log-only in release builds), or whether it completes successfully but the row
  still doesn't render (a different bug entirely — a query/filter problem, not a creation problem).
- **Whether `app_settings.hydration_enabled` is actually `true` for this owner right now.** This
  was assumed based on the owner's own statement ("I get to log the liters and it is logged in
  turn to the health connect") implying they use hydration logging — but that statement could
  describe *past* usage before this round, or describe the goal/expectation rather than confirmed
  current state. If `hydration_enabled` is actually `false`, **every fix shipped so far does
  nothing by design** (`sync()` explicitly no-ops when `enabled == false`), and the real problem is
  a UI/discoverability issue: the owner doesn't have a working, visible way to toggle it on in the
  post-reorg Settings layout, or toggling it silently fails. Check `HydrationSettingsSection.kt`'s
  "Track hydration" toggle is actually reachable in the reorganized Settings (this round also
  restructured Beast Mode Settings into sub-screens — confirm hydration's toggle, which lives in
  the *main app* Settings, not Beast Mode Settings, wasn't affected/hidden by that unrelated
  change) and that flipping it actually persists (`AppSettingsRepository.setHydrationEnabled`).
- **Whether the occurrence, once created, actually reaches the query `HomeViewModel` uses.**
  `OccurrenceScheduler.syncTaskInternal` generates rows into `food_med_occurrences` for a 7-day
  window; `HomeViewModel.baseHomeItems` queries
  `foodMedOccurrenceDao().getAllOccurrencesInTimeRange(startOfDay(date), endOfDay(date))` reactively.
  Nobody has confirmed via logs that a row for today's date + the hydration task actually lands in
  that table. It's possible `sync()`'s call to `scheduler.syncTask()` itself throws or no-ops for a
  reason specific to this task (e.g. `times_json` format, `active_days_json = ""` interpretation,
  or a race between the Application-level and HomeViewModel-level sync calls both trying to create
  the task simultaneously on the very first post-upgrade launch — check for a possible duplicate-row
  or lost-update race between those two call sites, now that both exist as of build 55).
- **Whether Intake shows it but Today doesn't, or neither does.** The owner's original report was
  specifically about the Today tab ("today tab is not showing hydration"). Nobody has asked or
  confirmed whether the Intake tab (`FoodMedScreen.kt`) shows it either. If Intake shows it and
  Today doesn't, the bug is in `HomeViewModel`/`HomeScreen.kt` specifically, not in task creation at
  all — a completely different investigation path than either fix attempted so far.

## Recommended first step for the next session

Don't guess a third time. Get one of:
1. A logcat capture from the owner's device around app launch (`adb logcat` filtered on tags
   `HydrationIntakeSync`, `OccurrenceScheduler`, or the app's package — note `OccurrenceScheduler`
   already logs via `Log.i(TAG, "syncTask($taskId) label=...")` on every sync, so if hydration's
   `syncTask` call is ever reached, it will show up there with `label='Hydration'`).
2. Or, ask the owner directly (plain language): "Open Settings → Hydration — is 'Track hydration'
   switched on right now?" This one question would immediately rule in/out the biggest unverified
   assumption above, and takes 10 seconds instead of another blind build.
3. If a debug build can be sideloaded, add a temporary visible Toast/Log at the very top of
   `HydrationIntakeSync.sync()` and in `HomeViewModel`'s new init block, confirming both are
   actually reached and what `enabled`/`existingId` are on the owner's real device — cheap,
   temporary, gives a definitive answer in one test cycle instead of another speculative fix.

## Relevant files

- `app/src/main/java/com/daybook/app/data/hydration/HydrationIntakeSync.kt` — the sync logic itself
- `app/src/main/java/com/daybook/app/DaybookApplication.kt` — attempt 1 (cold-start bootstrap)
- `app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt` — attempt 2 (init-block bootstrap),
  and `baseHomeItems`/`buildItems` (the Today query/render path)
- `app/src/main/java/com/daybook/app/ui/settings/HydrationSettingsSection.kt` — the actual
  user-facing toggle/reminder-time UI (main app Settings, not Beast Mode Settings)
- `app/src/main/java/com/daybook/app/ui/foodmed/FoodMedScreen.kt` — the Intake tab (unverified
  whether it shows hydration or not — check this to narrow the bug)
- `app/src/main/java/com/daybook/app/data/OccurrenceScheduler.kt` — `syncTask`/`syncTaskInternal`,
  where the actual `food_med_occurrences` rows get generated (and where the existing `Log.i` line
  would confirm whether hydration's sync is even reached)
- `/home/abhiram/Downloads/app-for-food/SOURCE_PRIORITY_AND_HYDRATION_PLAN_2026-09-20.md` — the
  original plan doc for the whole Hydration-as-Intake refactor (§2), for full context on the
  intended design
- Build history for this issue: **build 53** (shipped the Hydration-as-Intake refactor that
  introduced the regression), **build 54** (attempt 1, did not fix), **build 55** (attempt 2, did
  not fix)


---

## Resolution (build 56)

**Root cause: a data-format mismatch, not a missing-row or timing problem.**

`FoodMedTask.times_json` is misleadingly named — it is **not** JSON. Every other writer in the app
(`AddFoodMedViewModel`, `AddHabitViewModel`, `ExportImportRepository.joinTimes`) stores a
**comma-separated list of bare `HH:mm`** via `DateTimeUtils.timesToJson`, and the single reader,
`DateTimeUtils.jsonToTimes`, splits on `","` and `LocalTime.parse`s each element with an `HH:mm`
formatter.

`HydrationIntakeSync` — the only writer that never went through `timesToJson` — wrote a JSON-array
literal instead:

```kotlin
val timesJson = "[\"$timeHHmm\"]"   // -> ["21:00"]
```

`jsonToTimes("[\"21:00\"]")` splits on `,`, gets the single element `["21:00"]`, fails to parse it,
and — because of a deliberate per-element `mapNotNull` hardening added in an earlier round — **drops
it silently and returns an empty list** instead of throwing.

The consequences chain exactly onto the reported symptom:

1. `OccurrenceScheduler.slots()` starts with `if (times.isEmpty()) return emptyList()` → **zero
   slots**.
2. `syncTaskInternal` therefore inserts **zero `food_med_occurrences` rows**, and
   `armNextTaskInternal` logs `no pending occurrence in window — nothing armed`.
3. `HomeViewModel.baseHomeItems` queries occurrences in the day's range → nothing for hydration, so
   **Today shows nothing**. The past-day backfill path in `buildItems` also calls
   `jsonToTimes(t0.timesJson)`, so it synthesises nothing either. The Intake tab likewise had no
   times to render and no alarm ever fired.

**This also explains why builds 54 and 55 both "worked" and changed nothing.** Both self-heal
bootstraps were correct and *were* being reached: they created the `food_med_tasks` row, wrote
`hydration_task_id`, and called `scheduler.syncTask()` — all successfully, with no exception for
`recordUnhandledException` to report. The row existed the whole time; it just had no parseable
schedule attached, so the pipeline downstream of it had nothing to generate. Every hypothesis in
"What is NOT yet verified" above was chasing the wrong stage: task creation was never the problem.

### The fix

`HydrationIntakeSync.hydrationTimesJson()` now produces the format the reader actually expects, via
the same `DateTimeUtils.timesToJson` every other writer uses, with a fallback to 21:00 if the stored
`HH:mm` is somehow unparseable (a blank `times_json` would silently reproduce this exact bug).

**Already-broken installs repair themselves on the next launch, with no user action.** `sync()`'s
existing-row branch rewrites `times_json` on every call, and the builds 54/55 bootstraps call
`sync()` on every cold start *and* every time Today loads — so those two attempts, useless on their
own, are what makes this fix retroactive rather than "toggle hydration off and on again".

### Regression cover

`app/src/test/java/com/daybook/app/data/hydration/HydrationIntakeSyncTest.kt` pins the round-trip:
whatever `hydrationTimesJson` writes must parse back to the requested time through
`DateTimeUtils.jsonToTimes`, must be byte-identical to what `timesToJson` produces, and must never
be empty for a garbage input. One test asserts directly that the old `["21:00"]` shape parses to
nothing, so restoring it can't pass silently again.
