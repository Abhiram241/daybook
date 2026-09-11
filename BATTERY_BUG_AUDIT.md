# Daybook — battery / heat / silent-failure audit

Scope: find what makes the phone get hot and drain battery while the app is open and in active
use; hunt for swallowed exceptions and silent failures; make touched code efficient. No schema,
dependency, SDK, or version changes. `versionCode`/`versionName` untouched.

Verification gate (all green, from the current tree):

```
./gradlew testDebugUnitTest assembleDebug        BUILD SUCCESSFUL  (446-odd unit tests pass)
./gradlew assembleRelease                         BUILD SUCCESSFUL  (R8 + lintVitalRelease clean)
./gradlew compileDebugAndroidTestKotlin           BUILD SUCCESSFUL
```

---

## Summary

The codebase has been through many prior performance passes and is, on the whole, careful:
no perpetual animations, no busy-spin loops, arm-once alarms, single-registration Firestore
listeners, `@Immutable` list models with stable keys, `flowOn(Dispatchers.Default)` everywhere,
diligent error handling. Two things stood out as real continuous-power costs:

| id | issue | severity | status |
|----|-------|----------|--------|
| **B1** | Display panel pinned to peak refresh rate for the whole foreground session | High (heat, active use) | **NOT fixed — needs a product decision** |
| **B2** | `collectAsState()` (not lifecycle-aware) keeps every ViewModel pipeline hot while the app is backgrounded | Medium–High (drain while app "open") | **Fixed on the 3 tab screens; app-wide sweep pending a decision** |
| B3 | `WindowRefreshWorker` rewrites its WorkManager row on every launch (`UPDATE` vs `KEEP`) | Negligible | Noted, deliberately left as-is |
| B4 | `RoutinesViewModel` starts two independent minute tickers where one would do | Negligible | Noted, left as-is (scope) |

No silent-failure / swallowed-exception defects were found that need fixing (see "already clean").

---

## B1 — Forced peak display refresh rate, held for the entire session  ·  NEEDS DECISION

**File:** `app/src/main/java/com/daybook/app/ui/MainActivity.kt:109-115`

```kotlin
runCatching {
    val best = display?.supportedModes?.maxByOrNull { it.refreshRate }
    if (best != null && best.refreshRate > 90f) {
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }
}
```

**Root cause.** `preferredDisplayModeId` is a *hard* mode selection: while this window is focused,
SurfaceFlinger holds the display in that exact mode. On a 90/120/144 Hz phone (especially LTPO
panels that would otherwise idle down to 60 Hz or 1–10 Hz for static content) this keeps the
panel and the display pipeline in their high-power state the entire time Daybook is on screen —
including while the user is just reading a static list. That is a continuous, always-on power
draw and the most plausible cause of "the phone gets hot while I'm using the app".

This was a deliberate feature (the "120 Hz smoothness pass", handover round 3), so per the task
rules it is **not** changed here.

**Expected impact of removing/softening it:** on a 120 Hz device this is likely the single
largest continuous cost while the app is foregrounded and not animating. Jetpack Compose already
requests the panel's high rate *during* scroll/fling/animation via the frame clock, so the
perceptible smoothness loss from dropping the hard pin is small (fast scrolling still ramps up;
static screens drop back down).

**Recommended fix (pick one):**
1. Replace `preferredDisplayModeId = best.modeId` with the softer hint
   `window.attributes.preferredRefreshRate = best.refreshRate` — expresses "I'd like high refresh"
   without forbidding the system from idling the panel down for static frames.
2. Remove the block entirely and rely on the platform default + Compose's per-animation high-rate
   requests. Simplest; on most current devices scrolling still renders at the high rate.

---

## B2 — `collectAsState()` keeps ViewModel pipelines hot while backgrounded  ·  PARTIALLY FIXED

**Files:** every UI screen. The whole app uses `collectAsState()`; `collectAsStateWithLifecycle()`
was used 0 times before this pass.

**Root cause.** `collectAsState()` subscribes for as long as the *composition* is alive. When the
user backgrounds the app the Activity is `onStop()`ped but **not destroyed**, so the composition —
and every `collectAsState()` collector in it — stays alive and keeps collecting. Combined with
`SharingStarted.WhileSubscribed(5_000)` on every ViewModel `stateIn(...)`, the upstreams never get
to idle. What keeps running in the background, indefinitely, until the process is killed:

- `util/TimeTicker.kt` `minuteTicker()` — a `while(true){ emit(); delay(60_000) }` loop.
  `RoutinesViewModel` starts **two** (`nextMillisByHabit`, `nowTick`); `FoodMedViewModel` starts
  one. Each minute wakeup also re-runs a `GROUP BY` "next pending" Room query.
- `HomeViewModel._now` boundary loop (`HomeViewModel.kt:191-202`) — wakes at the next of
  {00:00, 05:00, 12:00, 17:00, 22:00}. Low frequency, but still an un-cancellable
  `viewModelScope` loop.
- `HomeViewModel` reactive pipelines: `homeItems` (`flatMapLatest` over two Room occurrence
  flows + `allHabits` + `allTasks`), the two 800-day-windowed streak queries, `monthReady`'s
  re-poll, `greeting`, `syncStatus`. All re-emit on any DB write.
- With `beyondViewportPageCount = 1` on the main `HorizontalPager`, two-to-three of the tab
  screens are composed at once, so on any given tab **2–3 tab ViewModels' pipelines are live
  simultaneously**, and stay live after backgrounding.

Per-minute cadence isn't enough to make a phone hot *in the foreground* on its own, but it is
squarely "drains battery while the app is open" for the very common case of the app sitting
backgrounded-but-not-killed for hours.

**Fix applied.** Swapped `collectAsState()` → `collectAsStateWithLifecycle()` in the three
always-composed tab screens — the ones whose ViewModels carry the tickers and the heavy
pipelines:

- `ui/home/HomeScreen.kt` (16 collectors)
- `ui/routines/RoutinesScreen.kt` (7 collectors)
- `ui/foodmed/FoodMedScreen.kt` (7 collectors)

`androidx.lifecycle:lifecycle-runtime-compose:2.7.0` is **already** a dependency, so no
dependency change. `collectAsStateWithLifecycle` stops collecting below `Lifecycle.State.STARTED`
(app not visible) and re-collects on return; because these are `StateFlow`s the latest value is
retained and replayed instantly on resume — no flicker, no functional change the user can see.

**Not done (pending a decision):** the transient stack screens — Detail, Settings + sub-screens,
Journal/HabitJournal, Respond, Account, Lock, Onboarding, Add/Edit Habit, Add/Edit Intake — still
use `collectAsState()`. Those are popped when the user leaves them and none of them own a ticker,
so their background exposure is far smaller. Extending the swap there is a mechanical follow-up
(~55 more call sites across ~14 files).

---

## B3 — `WindowRefreshWorker` uses `ExistingPeriodicWorkPolicy.UPDATE`  ·  NOTED, left as-is

**File:** `app/src/main/java/com/daybook/app/util/work/WindowRefreshWorker.kt:57-61`

`enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)` runs on
every `DaybookApplication.onCreate` and every `BootCompletedReceiver` fire. `UPDATE` rewrites the
persisted work row each call (where `KEEP` would no-op when it's already scheduled). But `UPDATE`
**preserves the next-run time**, so it does not cause an extra worker execution — the only cost is
one WorkManager DB transaction per cold start. That is negligible for battery.

Deliberately not changed: `UPDATE` is intentional (it lets a future build change the 24h interval
without requiring the user to clear data, which `KEEP` would silently prevent). Trading that away
buys no measurable battery improvement.

---

## B4 — Two minute tickers in `RoutinesViewModel`  ·  NOTED, left as-is

**File:** `app/src/main/java/com/daybook/app/ui/routines/RoutinesViewModel.kt:93-100` and `131-134`

`nextMillisByHabit` and `nowTick` each call `minuteTicker()`, i.e. two `delay(60_000)` loops
where one shared source would serve both. A *suspended* `delay` coroutine costs effectively zero
power (it is not a spin — it is a scheduled resume), and consolidating requires reordering the
property declarations (`nextMillisByHabit` is declared before `nowTick`). Left under scope
discipline; noted for a future tidy. B2's lifecycle fix already stops both while backgrounded.

---

## What was checked and is already efficient / clean

**No perpetual animation.** Zero `rememberInfiniteTransition`, `infiniteRepeatable`,
`withFrameNanos`, `withInfiniteAnimationFrameNanos` anywhere in the tree.
`ui/components/WaveHero.kt` is a one-shot static `Canvas` draw (Bezier contour lines, no
animation, sign-in screen only). `SoftCard`, `FloatingPillNav`, `WeekStrip` use only finite
`animate*AsState` that settle to a target and stop.

**No busy-spin loops.** Every `delay()` in the codebase is one of: the 60s `minuteTicker`;
`HomeViewModel.millisUntilNextBoundary()` (sleeps to the next time-of-day boundary, ≤5 wakeups/
day); `CloudSyncRepository`'s bounded `RETRY_BACKOFF_MS` (`listOf(500, 1_800)`); or a one-shot UI
cue (`UndoSnack` 2.6s, `SettingsScreen` 1.8s "saved" flash, `HomeScreen` calendar-animation
gate). `CloudSyncRepository`'s push path is `changes.debounce(3_000).collect { … }` — not a spin —
and the post-remote-apply alarm re-arm is coalesced through `resyncRequests.debounce(1_000)`.

**Firestore sync is not leaking listeners.** `attachSnapshotListeners()` removes any prior
registration and re-registers exactly once per auth-state transition; `teardown()` removes both
on sign-out. The months listener is scoped with
`whereIn(FieldPath.documentId(), residentMonths)` (capped at 30, ~3 in practice) so it is not a
full-history download. The `InvalidationTracker.Observer` is added once, guarded by a
`trackerObserver != null` check. Nothing re-registers on recomposition or on resume.

**Alarms arm exactly once.** `OccurrenceScheduler` keeps one "next" alarm per item; the A2 fix
keeps the `notification_id` deterministic so re-arming the same slot *replaces* the PendingIntent
(`FLAG_UPDATE_CURRENT`) instead of stacking, and `armNext*` explicitly refuses to re-arm an
overdue row that already has a `SHOWN` event — so `MainActivity.onResume`'s `syncAll()` and
`WindowRefreshWorker` cannot double-fire a still-pending occurrence. `AlarmReceiver`,
`BootCompletedReceiver`, `NotificationActionReceiver` all use `goAsync()` + `withTimeout(8–20s)`
+ `SupervisorJob` on `Dispatchers.IO`; none do long work on the main thread.

**WorkManager is reasonable.** `WindowRefreshWorker`: periodic 24h with 6h flex, no network
constraint, not expedited. `SyncFlushWorker`: one-shot, `NetworkType.CONNECTED`, linear 30s
backoff, `ExistingWorkPolicy.REPLACE`. No foreground-service / expedited misuse.

**Recomposition hygiene is good.** `HomeItem`, `RoutineItem`, `FoodMedItem`, `NavItemSpec` are
`@Immutable`. `LazyColumn` and every `HorizontalPager` carry stable `key`s. Derived list values
are wrapped in `remember(items)`. `MainActivity` reads the settled pager page through
`derivedStateOf` and hoists all pager/nav callbacks in `remember`, so the scaffold/nav recompose
once per swipe, not per drag frame. `Theme.kt` `remember`s the color scheme and typography and
reads the OS `ANIMATOR_DURATION_SCALE` once. `WeekStrip`'s `snapshotFlow { pagerState.settledPage }`
is followed by `.distinctUntilChanged()`.

**ViewModel work is off the main thread.** Every heavy mapping in HomeViewModel / RoutinesViewModel
/ FoodMedViewModel / DetailViewModel is `.flowOn(Dispatchers.Default)`. Streak queries are
windowed to 800 days (`STREAK_WINDOW_DAYS`) rather than full-history table scans.
`CloudSyncRepository` runs on its own `Dispatchers.IO` scope. `ProfilePhotoStore` and Coil decode
off-main.

**Error handling is diligent — no silent failures found.** No empty `catch {}` / `onFailure {}`
blocks anywhere. No flow `.catch {}` that swallows without emitting or logging — every one routes
through `util/ViewModelExt.kt::recordUnhandledException` (logs + Crashlytics, both guarded for the
plain-JVM test classpath). `safeLaunch` is used consistently across ViewModels and every
`return@safeLaunch` / `return@launchBusy` label is correct. `AlarmReceiver` /
`BootCompletedReceiver` / `NotificationActionReceiver` log every early-return; the notification
"suppressed" path deliberately skips the SHOWN event + refire (Phase 7 N-2). `OccurrenceScheduler`'s
`LogResult` sealed type replaced the old silent early-returns that let callers report "Saved" over
dropped data.
