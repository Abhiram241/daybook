# Daybook Bug & Silent-Failure Audit — Beast Mode / Health / Report — 2026-09-13

Read-only audit. Scope, per request: (1) **Beast Mode** (`ui/workout/`, `data/WorkoutRepository.kt`,
`data/local/WorkoutDao.kt`, `data/workout/`), (2) the **Health** tab (`ui/workout/health/`,
`data/HealthRepository.kt`, `data/health/`, `data/local/HealthDao.kt`, `util/HealthFormat.kt`),
(3) what the **Daily Report redesign round** newly introduced (`ui/report/`,
`data/DailyReportRepository.kt`, `DailyReportPrompt.kt`, `DailyReportContext.kt`,
`ui/settings/DailyReportAiSettings*`, `MIGRATION_26_27`), and (4) **Health Connect sync**
(`data/health/HealthConnectReader.kt`, `HealthPermissions.kt`, `HealthConnectAvailability.kt`,
`HealthSyncStateStore.kt`).

Companion to `BUG_AUDIT.md` (same format, same tiers, same citation style). Every finding from that
audit was re-verified against the current tree before writing this one: **C1, C2, H2, H3 and M3 are
all confirmed fixed and are not re-reported** (`DailyReportRepository.kt:159-197,227` now carries
per-section + outer `.catch{}`; `DailyReportViewModel.kt:143` has the `_isGenerating` guard;
`:230-233` the `KEY_CHAT_WAS_OPEN` notice; `:312-315` the blank-model check). Recent cosmetic
round-trips on `WorkoutSessionScreen.kt` (checkbox/icon/cell sizing, notes restyling, PREV wrap) were
treated as intentional and are not flagged; only functional defects found in that same logic are.

---

## Critical (data loss / crash)

### C1. Beast Mode's weight **entry** surfaces are hard-coded to kg while every weight **display** converts by `app_settings.weight_unit` — an "lb" user's entire logged history is stored, and then read back, wrong
**Files:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutSessionScreen.kt:727-733` (the WEIGHT
cell), `:976` (`columnLabel` → `"KG"`), `:988,993` (`formatPrevious` → `"${w}kg"`),
`app/src/main/java/com/daybook/app/ui/workout/RoutineTargetSheet.kt:67,84` (`NumField("Weight (kg)"…)`
→ `targetWeightKg = weight.toFloatOrNull()`); contrast
`app/src/main/java/com/daybook/app/data/workout/WorkoutLogic.kt:337-340,357-362`
(`formatWeight`/`formatVolume`, which both convert kg→lb) and
`WorkoutSessionScreen.kt:150,256` (the same screen collects `weightUnit` and uses it for the Volume tile).

The codebase has exactly one converter, `kgToLb` (`WorkoutLogic.kt:23`). There is **no `lbToKg`
anywhere in the repo** — confirmed by grep. Every write path treats the typed number as kilograms
verbatim: `onCommit = { text -> onUpdateWeight(s.id, text.toFloatOrNull()) }` →
`WorkoutRepository.setSetWeight` → `workout_sets.weight_kg`. Every read path
(`WorkoutDetailScreen.kt:91,174`, `WorkoutHistoryScreen.kt:342,434`, `ExerciseHistorySheet.kt:138`,
`WorkoutHomeScreen.kt:112`, `WorkoutSessionScreen.kt:256`, and the AI prompt at
`DailyReportPrompt.kt:97,123`) passes that stored number through `formatWeight`/`formatVolume`, which
multiply by 2.2046 when the unit is LB.

**Trigger:** Beast Mode settings → Weight unit → **lb** (`WorkoutSettingsScreen.kt:180-188`). Log a
135 lb bench press: the set cell's header still reads `KG`, its placeholder still reads `kg`, and
`135` is persisted as `weight_kg = 135`. The Volume tile *on the same card, two inches away*
(`:256`) then renders that set as **297 lb**, History's per-session volume renders it as 297 lb, the
PREVIOUS column next session renders it as `135kg`, and the Daily Report's AI prompt tells the model
the user lifted 297 lb. The stored history is now permanently ambiguous — nothing records which unit
the user believed they were typing — so this cannot be corrected later by a migration. The PR check
(`isPersonalRecord`, `WorkoutLogic.kt:57-72`) compares raw `weightKg` values, so it still works
*within* one unit setting but silently reclassifies every historical PR the moment the setting is
flipped.

**Fix direction:** thread `weightUnit` into `SetTable`/`EditableSetCell` (it is already collected at
`WorkoutSessionScreen.kt:150`) and into `RoutineTargetSheet`: label the column/placeholder from the
unit, and convert on commit (`lbToKg`, the missing inverse of `kgToLb`) and on seed
(`formatEditableNumber(kgToLb(s.weightKg))`) so the DB column keeps meaning kilograms. `formatPrevious`
should take the unit and call `formatWeight` rather than re-implementing a kg-only string.

### C2. `HealthTabViewModel.uiState`'s combine chain has **no `.catch{}`** — the same defect `BUG_AUDIT.md` C1 fixed in the Report tab, still present in the file that the fixed code cites as its own template
**File:** `app/src/main/java/com/daybook/app/ui/workout/health/HealthTabViewModel.kt:147-176`

Fourteen cold Flows (five Room DAO flows via `flatMapLatest` at `:125-143`, the settings flow at
`:145`, the `ladder` flow at `:108-123`, plus seven `MutableStateFlow`s) are folded into three nested
`combine`s and `stateIn`'d with no `.catch{}` at any level — confirmed: zero `catch` occurrences in
the file. This is precisely what `util/ViewModelExt.kt`'s KDoc calls out as the one thing
`safeLaunch` does **not** cover ("a cold `Flow` pipeline's `stateIn` … see `HomeViewModel`'s
`.catch{}` additions"), and `HomeViewModel` wraps ~10 equivalent flows.

The irony is load-bearing: `DailyReportRepository.kt:44-47`'s own KDoc says it copied "the same
pattern `HealthTabViewModel.kt:139-167` already established for combining several flows into one
`uiState`." The Report side subsequently got per-section `.catch{}` guards as C1's fix; the original
template never did.

**Trigger:** any throw out of `healthDao().observeDay/observeSessionsForDay/observeDaysInRange/
observeSessionsInRange/observeWeightReadingsForDay` (a DB corruption/disk-IO error, a Room
invalidation failure), out of `appSettingsRepository.observeSettings()`, or out of the `ladder` flow's
`healthRepository.grantedPermissions()` (`:116` — this one calls into Health Connect's
`permissionController` and is only `runCatching`-guarded one layer down, at `HealthRepository.kt:253`,
so that specific case is covered, but the five DAO flows are not). It propagates uncaught through
`combine` → `stateIn` → `collectAsStateWithLifecycle` → the process's default handler, and the app
crashes while the user is just looking at the Health tab.

**Fix direction:** identical to C1's accepted fix — a `.catch { recordUnhandledException(it); emit(<neutral>) }`
on each of `dayFlow`, `daySessionsFlow`, `dayWeightReadingsFlow`, `rangeDaysFlow`, `rangeSessionsFlow`
and `weekStartFlow`, plus an outer one on the final combine.

---

## High (silent failure / incorrect behavior user can't detect)

### H1. The Health Connect **changes-token is never minted on the first pull**, so every pull for the life of the install is a full 30-day re-read and the entire delta path is dead code
**File:** `app/src/main/java/com/daybook/app/data/HealthRepository.kt:88-126`

```kotlin
val token = stateStore.changesToken
val outcome: Result<Boolean> = if (token == null) {
    runCatching { pullWindow(today.minusDays(29), today); true }   // <-- never assigns changesToken
} else { … }
```

`stateStore.changesToken` is assigned in exactly three places — `:105`, `:117`, `:121` — **all three
inside the `token != null` branch** (grep-confirmed: those are the only writes in the repo outside the
property declaration itself). On a fresh install the token is null, the first-pull branch runs,
succeeds, and leaves the token null. The next pull reads null again, and so on forever.

**Trigger:** install the app and connect Health Connect. Every subsequent `pullOnResume()`
(`MainActivity.kt:457`, up to once per 15 min), every `pullDaily()` from `WindowRefreshWorker`, and
every "Refresh now" tap re-aggregates **30 days** — that is 30 × (1 mixed-metric `aggregate()` + 3
`readRecords()` for SpO₂/weight/nutrition + 1 `readRecords()` for sleep) = ~150 IPC round trips to
the Health Connect provider, plus one `aggregate()` per band session in the window
(`HealthConnectReader.kt:231-242`), and a 30-row `upsertDays` + `upsertWeightReadings` +
`upsertSessions` on every single one. `reader.changes()` is never called, so §6.3's whole delta
design is unreachable. It also makes the outcome always `hasNewData = true`, which means
`WorkoutSettingsViewModel.kt:90`'s and `HealthTabViewModel.kt:217`'s *"Up to date — nothing new from
your band."* branch can never fire — "Refresh now" always claims new data arrived, whether or not any
did. None of this surfaces as an error; it just quietly burns battery and lies in the status line.

**Fix direction:** in the `token == null` branch, after a successful `pullWindow`, do what the
expired-token branch already does at `:105` — `stateStore.changesToken = runCatching { reader.changesToken() }.getOrNull()`.
(And see L2: that line's `getOrNull()` also clobbers a good token to null on a transient mint failure,
which is the one way the system can *re-enter* this permanently-full-resync state after being fixed.)

### H2. A **partial permission grant** makes every Health Connect pull fail entirely — and blames it on Health Connect being uninstalled
**Files:** `app/src/main/java/com/daybook/app/data/HealthRepository.kt:85` and `:166`;
`app/src/main/java/com/daybook/app/data/health/HealthConnectReader.kt:89-106`

`pull()` gates only on `if (granted.isEmpty()) return PullOutcome.PermissionsMissing` (`:85`) — any
non-empty grant is treated as "connected". But `dayAggregate` then issues **one**
`AggregateRequest` covering nine metrics across eight record types (`HealthConnectReader.kt:94-105`:
Steps, Distance, ActiveCalories, TotalCalories, HeartRate ×3, RestingHeartRate, Hydration), and
Health Connect's `aggregate()` requires read permission for *every* record type in the metric set —
it throws `SecurityException` for the whole call otherwise, not a partial result. That call is the
one Health Connect read in `pullWindow` that is **not** `runCatching`-wrapped (`:166`; contrast the
SpO₂/weight/nutrition reads at `HealthConnectReader.kt:111,123,139`, each of which correctly degrades
to an empty list, and `sleepForDay` at `HealthRepository.kt:167`, wrapped by its caller).

**Trigger:** in the OS consent sheet the user ticks Steps and Sleep but leaves Heart rate (or
Hydration, or Total calories) unticked — an entirely ordinary choice that the app's own
"$missingCount type(s) not shared" copy (`WorkoutSettingsScreen.kt:254`) explicitly anticipates. Every
pull from then on throws on day 1 of the window, the `runCatching` at `:91` catches it, and the user
gets `"Couldn't refresh your health data. Check that Health Connect is still installed, then try
again."` — pointing them at a non-problem — while the Health tab shows the generic "No data yet …
turn on Steps, Sleep, Heart rate and Workouts" empty state. The steps and sleep they *did* grant
never appear, and nothing anywhere says why.

**Fix direction:** build the `AggregateRequest`'s metric set from the *granted* permission set (pass
`granted` down into `dayAggregate`, filter each metric by
`HealthPermission.getReadPermission(<Record>::class) in granted`), and wrap the aggregate call in its
own `runCatching` so an unexpected `SecurityException` degrades to "that family of metrics is absent"
rather than "nothing today worked". Same treatment for `exerciseSessions` (`:213`).

### H3. `pullWindow` is all-or-nothing: one bad day throws away up to 29 days of work that had already succeeded
**File:** `app/src/main/java/com/daybook/app/data/HealthRepository.kt:160-230`

The day loop (`:164-207`) accumulates into local `ArrayList`s and only writes at `:208-209`, *after*
the loop. `reader.dayAggregate(d, zoneId)` at `:166` is unguarded (see H2), so a throw on any single
day aborts the loop, unwinds past both `upsertDays` and `upsertWeightReadings`, and discards every
day already aggregated in that pass. `exerciseSessions(...)` at `:213` is likewise unguarded, so a
session-read failure discards the sessions for the whole window (the days themselves survive, since
their upsert already ran — an inconsistent halfway state the `onFailure` branch at `:141-151` doesn't
distinguish from a total failure). And because the token was never advanced (H1) and
`stateStore.lastPullAt` is only set on success (`:130`), the *identical* 30-day window is retried from
scratch on the next pull and fails at the same day again — an unbounded, silent retry loop.

**Trigger:** one day in the last 30 has a record Health Connect chokes on, or (per H2) one record type
isn't granted. Result: the Health tab shows nothing at all, forever, for every day — not just the bad
one — and the only visible artefact is a status line the Health tab never renders (H4).

**Fix direction:** `runCatching` each day inside the loop (recording the failure and skipping that
date), and upsert incrementally — or at minimum inside a `try/finally` so partial progress is
committed. §6.2's "keep the last-good data on screen, just report the failure" intent is already
written in the comment at `:143`; the code just doesn't preserve *partial* good data.

### H4. The Health tab computes `statusLine`, `statusIsFailure`, `actionResult`, `isRefreshing` and `isImportingPast` — and **renders none of them**
**Files:** `app/src/main/java/com/daybook/app/ui/workout/health/HealthTabViewModel.kt:67-71,167-174,210-243`
vs. `app/src/main/java/com/daybook/app/ui/workout/health/HealthTabScreen.kt` (whole file)

`HealthTabUiState` carries all five fields and the ViewModel populates them carefully — `refreshNow()`
(`:210-226`) sets `_actionResult` to the failure message, flips `_isRefreshing`, and bumps
`_ladderRefresh`. Grep-confirmed: **`HealthTabScreen.kt` reads `state.statusLine`,
`state.statusIsFailure`, `state.actionResult`, `state.isRefreshing` and `state.isImportingPast`
exactly zero times.** There is also no "Refresh now" control on the tab at all, so the only path into
`refreshNow()` from this screen is `onPermissionFlowFinished()` (`:205-208`).

**Trigger:** the user taps **Connect** on the Health tab (`HealthTabScreen.kt:141`), grants
everything, and the OS sheet returns. `onPermissionFlowFinished()` fires `refreshNow()`. If that pull
fails for any reason — H2's partial grant, H3's bad day, a provider timeout — `_actionResult` is set
to a perfectly good plain-language message that **is never drawn**, `_isRefreshing` toggles with no
spinner, and the screen simply re-renders the "No data yet / open Mi Fitness and turn on Steps…"
empty state. The user has no way to distinguish "Health Connect has nothing for you" from "the read
failed". The same information *is* rendered on the Beast Mode settings screen
(`WorkoutSettingsScreen.kt:284-289,310-319`), which the user has no reason to visit. This is exactly
the C9.4 "a background failure must be readable somewhere the user can check" rule the state store's
own KDoc cites (`HealthSyncStateStore.kt:47-48`), defeated on the one screen the feature lives on.

**Fix direction:** render `state.actionResult` / `state.statusLine` as a caption under the segmented
control (coloured by `state.statusIsFailure`), and add the "Refresh now" affordance the state fields
were clearly written for.

### H5. `PullOutcome.PermissionsMissing` is mapped to **`null`** in both ViewModels — tapping "Refresh now" after revoking access does nothing and says nothing
**Files:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsViewModel.kt:92` and
`app/src/main/java/com/daybook/app/ui/workout/health/HealthTabViewModel.kt:219`

```kotlin
HealthRepository.PullOutcome.PermissionsMissing -> _healthActionResult.value = null
```

`HealthRepository.pull()` returns `PermissionsMissing` whenever `grantedPermissions()` comes back
empty (`HealthRepository.kt:85`) — and, critically, it does **not** call `setStatus(...)` on that path
either, so the persisted status line isn't updated. Both call sites then explicitly blank the message.

**Trigger:** the user revokes Daybook's access in the Health Connect app (the exact flow the
"Disconnect" button at `WorkoutSettingsScreen.kt:260-272` sends them to), comes back, and taps
**Refresh now**. The row title flips to "Refreshing…" and back to "Refresh now"; the result caption is
blank; the Status row still shows whatever it said from the last successful pull days ago, so it reads
as if everything is fine. The one real, actionable, entirely-recoverable failure state in the whole
feature is the one state that produces no message at all. (The `importPastData` path at
`WorkoutSettingsViewModel.kt:109-110` / `HealthTabViewModel.kt:236-237` handles the same outcome
correctly with a real sentence — the inconsistency is within the same `when` blocks.)

**Fix direction:** map `PermissionsMissing` to a message ("Daybook no longer has access to your health
data. Tap Connect to share it again."), and have `pull()` `setStatus(...)` it too so the persisted
status line stops implying success.

### H6. `READ_HEALTH_DATA_HISTORY` is **never requested**, but "Import my past data" reports success back to a date a year ago regardless
**Files:** `app/src/main/java/com/daybook/app/data/health/HealthPermissions.kt:71-73`
(`optionalExtras()`), `app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsViewModel.kt:81`
(`optionalHealthExtras()`), `app/src/main/java/com/daybook/app/data/HealthRepository.kt:54-76`

Grep-confirmed: `optionalExtras()` / `optionalHealthExtras()` is defined and exposed on the ViewModel
but has **no call site anywhere in `app/src/main`** — no `permissionLauncher.launch(...)` ever passes
it. Neither `READ_HEALTH_DATA_HISTORY` nor `READ_HEALTH_DATA_IN_BACKGROUND` is ever actually requested
from the OS, even though both are declared in `AndroidManifest.xml:38-39` and
`HealthSyncStateStore.backgroundPermissionDenied` (`:43-45`) exists to remember the refusal.

`importPastData()` reads a 365-day window (`BACKFILL_CAP_DAYS`, `:261`) and, on success, writes
`"Imported your health history back to ${from.format(DISPLAY_DATE)}."` (`:67`) — a concrete claim
about a specific date ~12 months ago. Without the history permission, Health Connect silently caps
reads at the last 30 days: the call **succeeds**, returns nothing for days 31–365, and the success
message is simply false. The KDoc at `:52-53` even names the assumption ("assumes the caller has
already been granted `READ_HEALTH_DATA_HISTORY`") — nobody grants it.

Same root cause for background reads: `WindowRefreshWorker` (`:44`) calls `pullDaily()` from a
WorkManager job, which on Android 14+ is a background read and needs
`READ_HEALTH_DATA_IN_BACKGROUND`. It is never requested, so the daily cadence quietly returns nothing
(or throws into H3's silent-retry loop) whenever the app isn't foregrounded.

**Fix direction:** launch `optionalExtras()` through the same `rememberLauncherForActivityResult`
contract before running the import (once, remembering a refusal in `backgroundPermissionDenied` per
§6.3's "never nag"), and make the success copy conditional on what was actually granted — "Imported
the last 30 days. Daybook needs access to your history to go further back." otherwise.

### H7. Chat's configurable context date range has **no upper bound** — `buildChatContext` re-derives the entire Daily Report, day by day, for every day in the range, with no loading state and no failure path
**Files:** `app/src/main/java/com/daybook/app/data/DailyReportRepository.kt:144-149`,
`app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:256-290`,
`app/src/main/java/com/daybook/app/ui/settings/DailyReportAiSettingsScreen.kt:55-76`

```kotlin
val days = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()
return days.map { d -> observeReport(d).first() }
```

Each `observeReport(d).first()` spins up and collects **eight** Room Flows plus a suspend
`buildWorkoutSection` that itself runs two queries per session and one `resolveExercise` per exercise
(`DailyReportRepository.kt:240-270`), plus `getScheduledStatusesForHabit` per STREAK habit
(`:327`). The date pickers impose only `maxDate` (`DailyReportAiSettingsScreen.kt:58,69`) — there is
no `minDate`, no span cap, and no warning. The resulting `List<DailyReportData>` is then flattened
into a single `StringBuilder` by `buildMultiDayReportPrompt` (`DailyReportPrompt.kt:55-72`) — which
emits full Q&A text for every intake and journal entry (`:151-153,169`) — and sent as **one system
message** (`DailyReportViewModel.kt:274-283`).

**Trigger:** set "From" to 1 January and "To" to today. Tapping **Chat**
(`DailyReportScreen.kt:534`, `onClick = viewModel::openChat`) runs ~250 × ~10 DB round trips inside
one `safeLaunch`, and `_chatOpen.value = true` is the **last** statement of that block
(`DailyReportViewModel.kt:286`) — so the UI does nothing at all, for many seconds, with no spinner and
no disabled state (compare `generate()`, which has `_isGenerating` driving `loading = state.isGenerating`
at `DailyReportScreen.kt:516-517`). There is also no re-entrancy guard, so each impatient re-tap
launches another full traversal. The prompt that finally goes out is likely megabytes of text, which
every provider rejects on token count — surfaced as a generic `AiResult.Failure` ("provider returned
an error"), never "your chat context is too large; shorten the date range."

**Fix direction:** cap the range (a `minDate` on the picker plus a hard clamp in `buildChatContext`,
with an explicit "showing the last N days" note in the prompt when clamped); add an
`_chatOpening`/`_chatSending`-style flag so the Chat button shows `loading` and guards re-entry; and
size-check the assembled context string before dispatch with a specific message.

### H8. The per-block **rest timer never starts** — completing a set does nothing, and the only way to run the timer is to re-open the picker sheet
**Files:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutSessionScreen.kt:374-381` (the sole
`startRest` call site), `:768-803` (the COMPLETE toggle), `WorkoutSessionViewModel.kt:161-162,217`

Grep-confirmed: `viewModel.startRest(...)` has exactly **one** call site in `app/src/main` —
`WorkoutSessionScreen.kt:379`, inside the rest-timer `SortSheet`'s `onSelectSort`. The COMPLETE cell's
`clickableImpl` (`:789-792`) fires a haptic and `onToggleComplete(s.id)` and nothing else;
`WorkoutSessionViewModel.toggleSetComplete` (`:217`) is a straight passthrough to
`WorkoutRepository.toggleSetComplete` (`WorkoutRepository.kt:513-516`), which only flips `completed_at`.

**Trigger:** set "Rest Timer: 90s" on a bench-press block (it even pre-fills from last session via
`lastRestSecondsForExercise`, `WorkoutRepository.kt:412-414`). Complete set 1. Nothing happens — no
countdown pill, no notification, nothing. The user waits, completes set 2: still nothing. The setting
is persisted, displayed on the card header (`WorkoutSessionScreen.kt:568`), carried forward between
sessions, and has no effect on anything. Its only observable behaviour is the one-shot countdown that
fires as a side effect of *changing* the setting, which reads as the feature working the first time
and then silently breaking.

**Fix direction:** call `startRest(blockUi.block.restSeconds ?: 0)` from the COMPLETE toggle when the
set is being marked done (and `cancelRest()` when un-marking), which is what the per-block
`restSeconds` column exists for.

---

## Medium (UX / state bug)

### M1. `observeReport`'s outer `.catch{}` doesn't `emit` — so a failure there makes `buildChatContext` throw `NoSuchElementException`, and makes the Report tab hang on a blank initial state
**File:** `app/src/main/java/com/daybook/app/data/DailyReportRepository.kt:224-228`, consumed at
`:148` and `DailyReportViewModel.kt:84,91-120`

C1's fix added `.catch { recordUnhandledException(it) }` as the outer net — correct in that it stops
the crash, but it swallows the exception *and emits nothing*, so the Flow simply completes. Two
consequences the fix didn't consider:

1. `buildChatContext`'s `observeReport(d).first()` (`:148`) on an empty Flow throws
   `NoSuchElementException: Expected at least one element`. That happens inside `openChat()`'s
   `safeLaunch`, whose default handler records to Crashlytics and ends the coroutine — so
   `_chatOpen` is never set, `_chatError` is never set, and **tapping "Chat" does nothing at all,
   silently, forever**.
2. On the Report tab itself, `reportFlow` is one arm of `uiState`'s `combine`
   (`DailyReportViewModel.kt:95`). `combine` emits nothing until *every* source has emitted once, so a
   completed-without-emitting `reportFlow` leaves `uiState` stuck on its `DailyReportUiState()`
   initial value — a blank report page with no error, rather than the "degrade to an absent section"
   behaviour R-DR1 promises.

**Fix direction:** make the outer catch emit a neutral `DailyReportData(date, null, null, emptyList(),
emptyList(), null, WeightUnit.KG)` so downstream `first()`/`combine` consumers always get a value, and
have `openChat()` set `_chatError` on failure rather than relying on `safeLaunch`'s silent default.

### M2. The meta-prompt field writes to Room on **every keystroke**, and its own `remember(metaPrompt)` key then races those writes back into the field
**File:** `app/src/main/java/com/daybook/app/ui/settings/DailyReportAiSettingsScreen.kt:46,86-97`

```kotlin
var metaPromptDraft by remember(metaPrompt) { mutableStateOf(metaPrompt) }
…
onValueChange = { metaPromptDraft = it.take(2000); viewModel.setMetaPrompt(metaPromptDraft) }
```

Each keystroke launches `safeLaunch { settingsRepository.setAiMetaPrompt(v) }`
(`DailyReportAiSettingsViewModel.kt:35`) → `ensureRow()` + an `UPDATE app_settings`. That write
invalidates the `app_settings` table, `observeSettings()` re-emits, `metaPrompt` changes, and
`remember(metaPrompt)` **re-keys and resets `metaPromptDraft`** to whatever the DB now holds. Typing
faster than the round trip therefore replays an older value into the field mid-type (dropped
characters / cursor jumps). It also violates this codebase's own stated convention — the block-notes
field two features over explicitly commits on focus-loss and says why
(`WorkoutSessionScreen.kt:503-506`: "this used to call `onNotesChange` (a DB write) on every
keystroke, contradicting this composable's own stated 'save on focus-loss, never per-keystroke'
convention"), and `EditableSetCell` is built around the same rule. Secondary cost: the
`InvalidationTracker` feedback loop that `HealthSyncStateStore.kt:7-10` warns about — a 2000-character
prompt means ~2000 settings-row writes.

**Fix direction:** commit on focus-loss (`onFocusChanged`, exactly as `ExerciseBlockCard` does) or
debounce, and drop `metaPrompt` from the `remember` key once the draft is locally owned.

### M3. Both chat-range consumers call `LocalDate.parse` unguarded on a free-text settings column — one crashes the settings screen in composition, the other fails silently
**Files:** `app/src/main/java/com/daybook/app/ui/settings/DailyReportAiSettingsScreen.kt:51-52` and
`app/src/main/java/com/daybook/app/ui/settings/DailyReportAiSettingsViewModel.kt:72,81`; compare the
correct handling at `app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:265-267`

`ai_chat_range_start`/`_end` are plain `TEXT NOT NULL DEFAULT ''` columns
(`Migrations.kt:806-807`). Three call sites read them, and they disagree on trust:
`DailyReportViewModel.openChat()` wraps the parse in `runCatching { … }.getOrElse { … }` (`:265-267`) —
correct; `DailyReportAiSettingsViewModel.setChatRangeStart/End` parse bare inside `safeLaunch`
(`:72,81`), so a malformed value means the date picker's Confirm silently does nothing, every time,
with no way to recover except spotting "Reset to today"; and `DailyReportAiSettingsScreen:51-52`
parses bare **during composition**, so a malformed value makes the Daily Report AI settings screen
throw `DateTimeParseException` on open, every time, permanently.

As with `BUG_AUDIT.md`'s M1, this is **not reachable today**: only `setChatRange(date.toString(), …)`
and `setChatRange("", "")` ever write these columns, `app_settings` is deliberately excluded from
both cloud sync and the backup format (`CloudSyncRepository.kt:295,1372`), and the column defaults are
well-formed. It is filed as Medium on the same reasoning that audit used — an unguarded parse of a
schema-unconstrained TEXT column, with the same file's own sibling call site demonstrating the guard,
is a latent trap whose failure mode is an unrecoverable screen crash.

**Fix direction:** one shared `parseChatRangeDate(raw: String): LocalDate?` returning null on garbage,
used by all three call sites.

### M4. A cached AI summary survives changes to the very settings that produced it, with nothing recording the mismatch
**Files:** `app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:168-192`,
`app/src/main/java/com/daybook/app/ui/report/DailyReportScreen.kt:561-594`,
`app/src/main/java/com/daybook/app/data/model/DailyReportModel.kt`

`DailyReportAiSummary` persists `provider`, `model`, `summaryText` and `generatedAt` — but not the
`aiMetaPrompt` or `aiReportCategories` the generation actually used, and there is no invalidation hook
on either setter (`AppSettingsRepository.kt:113-114`).

**Trigger:** generate a summary for today with all four categories on. Go to Settings → Daily Report
AI and turn **Health** off — the screen's own copy promises "Turn a category off to leave it out of
the report the AI generates" (`DailyReportAiSettingsScreen.kt:104-105`). Return to the Report tab. The
cached Lavender card still displays the full summary *including* the health paragraph, attributed
only as "Generated by … · 5 minutes ago" (`DailyReportScreen.kt:576-577`). The user's stated
preference and what's on screen now disagree, and nothing marks the summary as stale. Same for a
changed meta-prompt.

**Fix direction:** store a settings fingerprint (a hash of meta-prompt + categories CSV) on the row
and, when it differs from the current one, badge the card ("Settings changed since this was
generated — regenerate to apply") rather than silently serving the old text.

### M5. Beast Mode settings reads the health status line by **calling a suspend-free repository method directly in composition** — it is stale, off-thread-unsafe, and colours unrelated messages by it
**Files:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsScreen.kt:287,315`,
`app/src/main/java/com/daybook/app/ui/workout/WorkoutSettingsViewModel.kt:76-77`,
`app/src/main/java/com/daybook/app/data/HealthRepository.kt:237-238`

`subtitle = viewModel.healthStatusLine() ?: "Not connected yet"` (`:287`) is a plain function call
inside a composable — it reads `SharedPreferences` (`HealthSyncStateStore.kt:50`) on the composition
thread and, being no kind of observable state, **never triggers recomposition when it changes**. The
row only refreshes as a side effect of some *other* collected flow emitting. A background pull from
`WindowRefreshWorker` that fails while this screen is open leaves the Status row showing the previous
success indefinitely — the exact C9.4 guarantee the line above it claims to implement.

Worse, `:315` colours the *action result* caption by `viewModel.healthStatusIsFailure()` — the
repository's last **pull** outcome, not this action's. So `importHealthPastData()`'s
`PermissionsMissing` message ("Daybook can only see the last 30 days without access to your history",
`WorkoutSettingsViewModel.kt:110`) renders in `DaybookColors.Success` green whenever the last pull
happened to succeed, and a genuine success message renders in `Danger` red whenever it didn't.

**Fix direction:** expose the status as a `StateFlow` on the ViewModel (refreshed on the same ticks
`refreshHealthPermissionState()` already uses) and carry a per-action `isFailure` alongside
`_healthActionResult` instead of consulting the repository's global flag.

### M6. The Health detail sheet labels every Range-mode metric "Averaged over …" — but Sleep's rows are **sums**
**Files:** `app/src/main/java/com/daybook/app/ui/workout/health/HealthDetailSheet.kt:71-75,127-133`,
`app/src/main/java/com/daybook/app/data/health/HealthAggregation.kt:30-36,73-77`

The sheet's subtitle is unconditional: `"Averaged over ${range.start} – ${range.end}"` (`:73`). But
`HealthAggregate` deliberately makes sleep a total, not a mean (`HealthAggregation.kt:30-31`: "Sums,
not means"), and the sheet renders `agg.sleepMinutesTotal` etc. directly (`:128-132`).

**Trigger:** Range → Last 3 months → tap the Sleep card. The sheet reads "Averaged over 14 Jun –
13 Sep" above "Total: 632h 14m". A 632-hour nightly average is obviously wrong, but "Deep: 141h" is
not obviously anything, and the user has no way to know which rows in this sheet are means and which
are sums. (The main tab's cards do disambiguate — every other Range-mode value appends " avg"
explicitly, `HealthTabScreen.kt:264,266,304,343` — which makes the sheet the only place that gets it
wrong.)

**Fix direction:** make the subtitle kind-aware ("Totalled over …" for `SLEEP`), or append " total" /
" avg/day" per row as the other detail sections already do (`:92-93,111-114`).

### M7. The rest-timer pill sticks on "Rest over" permanently once the countdown reaches zero
**Files:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutSessionScreen.kt:268-282`,
`app/src/main/java/com/daybook/app/ui/workout/WorkoutSessionViewModel.kt:151-162`

The ticker computes `restRemaining = ((ends - now) / 1000).coerceAtLeast(0)` (`:155`) and never nulls
`_restEndsAt` when it hits zero. The pill's visibility condition is `restRemaining != null`
(`:268`), so once the timer expires the accent pill reading "Rest over" stays on the stats card for
the rest of the session — through every subsequent set — until the user opens the rest sheet and picks
"Off" (`:379`). There is also no sound, vibration or notification at expiry, so "Rest over" is the
entire signal, and it never goes away to mark the *next* rest.

**Fix direction:** in the ticker, set `_restEndsAt.value = null` (after firing whatever alert) once
the remaining time reaches 0, optionally holding the "Rest over" state for a few seconds first.

### M8. The Health tab computes `Connected(missingCount)` and then discards it — partial sharing is invisible on the screen it affects
**Files:** `app/src/main/java/com/daybook/app/ui/workout/health/HealthTabViewModel.kt:49,118` vs.
`app/src/main/java/com/daybook/app/ui/workout/health/HealthTabScreen.kt:143-153`

`HealthLadderState.Connected` carries `missingCount` specifically so the tab can say "3 types not
shared", and the `is HealthLadderState.Connected ->` branch ignores it entirely, rendering
`HealthContent` unconditionally. Combined with H2, a user who granted 9 of 12 types sees an empty tab
with a generic Mi Fitness hint and no indication that the three they withheld are why some cards are
missing. The Beast Mode settings screen does show this ("$missingCount type(s) not shared",
`WorkoutSettingsScreen.kt:254`) — the tab doesn't.

**Fix direction:** render a one-line caption when `missingCount > 0`, deep-linking to
`HealthConnectAvailability.settingsIntent()` the way the "Which data is shared" sheet already does.

---

## Low (code smell / minor risk)

### L1. `bestSetForExerciseBefore` omits the `ws.status = 'COMPLETED'` filter its sibling's KDoc claims it is aligned with
**File:** `app/src/main/java/com/daybook/app/data/local/WorkoutDao.kt:196-214` vs. `:216-231`

`bestSetForExercise`'s KDoc says it "excludes ACTIVE ones (aligned with `bestSetForExerciseBefore`'s
scoping)" — but `bestSetForExerciseBefore`'s query has no `status` predicate at all, only
`ws.started_at < :beforeMillis`. A stale never-finished ACTIVE session from a previous week therefore
counts toward the Beast Mode home "pre-week best", while the identical concept on the session screen
excludes it. Low because the two are used on different screens and a stale ACTIVE session is
uncommon — but the KDoc asserts an invariant the SQL doesn't hold.

### L2. Four pieces of Health Connect sync bookkeeping are written but never read (or read but never written)
**File:** `app/src/main/java/com/daybook/app/data/health/HealthSyncStateStore.kt:29-45`

Grep-confirmed across `app/src`: `backfillThroughMillis` is written at `HealthRepository.kt:65` and
**never read**; `lastFullResyncAt` is written at `:104` and never read, despite its KDoc promising it
is "surfaced as 'Rebuilt your health history <relative time>.'"; `backgroundPermissionDenied` is
neither read nor written (dead, pending H6); `optionalExtras()` is exposed and never launched (H6).
Relatedly, `HealthRepository.kt:105` — `stateStore.changesToken = runCatching { reader.changesToken() }.getOrNull()`
— **clobbers the existing token to null** if minting throws, which is the opposite of what the
carefully-written comment at `:97-102` says the code does ("without clearing the old token first");
combined with H1's never-mint bug, that's the one route back into a permanent full-resync state.

### L3. `WorkoutSessionViewModel` runs two unconditional `while (true)` 1 Hz tickers for the ViewModel's entire lifetime
**File:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutSessionViewModel.kt:131-141,151-159`

Neither loop checks whether anything is subscribed, whether the session is still ACTIVE, or whether a
rest timer is even running — the rest ticker recomputes `null` once a second forever when
`_restEndsAt` is null. They stop only when the ViewModel is cleared. Minor battery/wakeup cost;
`restRemainingSeconds` in particular could be a derived flow rather than a polled one.

### L4. `HealthTabUiState.rangeAggregate` is a computed `get()`, so the whole aggregate is recomputed on every read
**File:** `app/src/main/java/com/daybook/app/ui/workout/health/HealthTabViewModel.kt:73`

`aggregateHealthDays(rangeDays)` runs 17 `mapNotNull`+`average` passes over the list. It is read at
least six times per composition of `HealthTabScreen` (`:205,243,264,266,303,320,342…`) plus once per
`HealthDetailSheet` row. With `LAST_3_MONTHS` (92 days) that is ~1,500 list traversals per frame.
Cheap per pass, but it belongs in the combine that builds the state, not on a property getter.

### L5. `discardActiveSessionAndThen` is the one start-path action left without a `runCatching`/error message
**File:** `app/src/main/java/com/daybook/app/ui/workout/WorkoutHomeViewModel.kt:149-152`

`startEmptyWorkout`, `startRoutine`, `duplicateRoutine` and `deleteRoutine` (`:134-166`) were all
explicitly hardened with `runCatching { … }.onFailure { reportError(…) }` — with comments citing
`BEAST_MODE_BUG_REPORT.md` §1.2/§1.3 for exactly this reason. `discardActiveSessionAndThen` wasn't: a
throw from `repo.discardSession` means `startAction()` never runs and the user taps "Discard and
start" to no visible effect.

### L6. `LocalDate.now()` is evaluated inside combine lambdas, so `today` goes stale across midnight
**Files:** `app/src/main/java/com/daybook/app/ui/workout/health/HealthTabViewModel.kt:161`,
`app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:109`

`today = LocalDate.now(zoneId)` only refreshes when some upstream flow emits. A screen left open
across midnight keeps highlighting yesterday in the `WeekStrip` and keeps yesterday as the date
pickers' `maxDate` until something unrelated changes.

### L7. `_chatError` is never cleared on date change, unlike `_generationError`
**File:** `app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:122-125`

`selectDate()` clears `_generationError` but not `_chatError`, and the AI panel renders
`state.generationError ?: chatError` in one shared slot (`DailyReportScreen.kt:544`). A chat failure
from Monday therefore keeps showing in red under the Generate button after the user navigates to
Tuesday, attached to a day it has nothing to do with.

---

## Already-solid (checked, no issue found)

- **`SortSheet`'s `dismissOnSelect` ordering.** The rest-timer sheet's handler
  (`WorkoutSessionScreen.kt:374-381`) reads the `restSheetForBlock` state *inside* `onSelectSort`,
  which would silently no-op if the sheet nulled it first. Verified at
  `SortSheet.kt:150-153`: `onSelectSort(opt.key)` runs before `onDismiss()`, so the block id is still
  live. Correct as written — and the same ordering makes `ConfirmDeleteDialog.kt:31-32`'s deliberate
  *reverse* choice ("dismiss first, so the dialog is already gone if confirm triggers a navigation")
  safe for the Discard flow.
- **`EditableSetCell`'s commit path is genuinely double-guarded.** The `DisposableEffect`/`onDispose`
  commit-if-still-focused (`WorkoutSessionScreen.kt:862-867`, using `rememberUpdatedState` so the
  captured lambda and text are never stale) plus the screen-level `focusManager.clearFocus(force = true)`
  on Back/Finish/+Add Exercise (`:191,295,337,359`) means there is no reachable path where a typed
  set value is lost without being written. The IME-inset-driven `bringIntoView` loop (`:903-917`) is
  likewise correct — `collectLatest` over `snapshotFlow { imeInsets.getBottom(density) }` cancels
  stale scroll requests rather than racing a fixed delay.
- **Column-scoped set writes.** `setWeightKg`/`setReps`/`setDurationSeconds`/`setDistanceMeters`
  (`WorkoutDao.kt:142-152`) really are single-column `UPDATE`s, so a commit built from a
  composition-time snapshot cannot clobber a concurrent `setCompletedAt` — the §2.13 fix holds.
  Likewise the read-then-insert races: `maxExerciseOrderIndex` (`WorkoutRepository.kt:422-430`) and
  `maxSetNumber` (`:484-496`) are both inside `withTransaction`, and `addExercises`
  (`WorkoutSessionViewModel.kt:179-181`) serialises into one coroutine as its comment claims.
- **Exercise-catalog dedupe never orphans history.** `refreshExerciseCatalog`'s merge
  (`WorkoutRepository.kt:243-256`) reassigns every block, set and routine-exercise reference to the
  canonical id *inside the same `withTransaction`* as the duplicate row's deletion, and never deletes
  a `builtin:`-prefixed row — so the "Unknown exercise" orphan its own comment warns about is
  genuinely unreachable. The `AtomicBoolean.compareAndSet` once-per-process gate (`:180-189`) is
  correct, and the whole thing is `runCatching`-wrapped with `recordUnhandledException`.
- **Health Connect availability is fully failure-inert.** `HealthConnectAvailability.state()` and
  `.client()` (`:32-58`) are both `runCatching`-wrapped and return `UNAVAILABLE`/`null` rather than
  throwing, and `HealthConnectReader.client()` turns a null client into a plain `IllegalStateException`
  that every `HealthRepository` entry point catches. "Health Connect not installed" therefore
  degrades to the correct `HealthLadderState.Unavailable` empty state — the one degradation path in
  this feature that works end to end.
- **Inverted chat date ranges are clamped at write time, in both directions.**
  `DailyReportAiSettingsViewModel.setChatRangeStart` (`:67-76`) pushes `end` forward when the new
  start passes it, and `setChatRangeEnd` (`:78-85`) pulls `start` back — and
  `buildChatContext`'s `takeWhile { !it.isAfter(endDate) }` (`DailyReportRepository.kt:146`) is
  independently safe for an inverted pair (it yields an empty list rather than looping). A
  cross-month or cross-year range is handled correctly by `generateSequence { it.plusDays(1) }`; the
  problem with large ranges is size (H7), not correctness.
- **`parseReportCategories`' fallback.** `DailyReportContext.kt:13-18` treats blank, unknown and
  fully-corrupt CSV as "every category on" (mirroring `NavConfig.visibleRoutesFrom`), so a bad value
  can never silently strip the AI's entire context. `reportCategoriesToCsv` is a true inverse.
- **`MIGRATION_26_27`** (`Migrations.kt:802-810`) adds all five columns `NOT NULL` with defaults that
  exactly match the `@ColumnInfo` defaults on `AppSettings` (`DataModel.kt:327-339`), so an upgraded
  install and a fresh one agree — and `app_settings` is correctly excluded from both the cloud sync
  set and the v2 backup format (`CloudSyncRepository.kt:295,1372`), matching its "device-scoped
  preferences" intent.
- **`ExerciseTypeLabels` / `SourceAppLabels`** (`HealthLabels.kt:73,93-94`) both fall back on an
  unknown key rather than crashing or leaking a raw package id / int, exactly as their KDocs promise.
- **`AiKeyStore`'s C2 fix is present** — re-verified as part of confirming the prior audit's findings;
  not re-litigated here.

---

## Summary

| Severity | Count |
|---|---|
| Critical | 2 |
| High | 8 |
| Medium | 8 |
| Low | 7 |

**Total: 25 findings.**

**Single most important finding:** **C1** — Beast Mode's weight *input* surfaces (the live set
table's WEIGHT cell and the routine target sheet) are hard-coded to kilograms, while every weight
*display* in the app (`formatWeight`/`formatVolume`, History, Detail, the exercise history sheet, and
the Daily Report's AI prompt) converts by `app_settings.weight_unit`. There is no `lbToKg` anywhere in
the repository. A user who picks "lb" in Beast Mode settings types `135`, has `135` persisted as
kilograms, and is then shown `297 lb` for that same set — on the very same screen, in the Volume tile
two inches from the cell they typed into. Every set they log from that point is permanently ambiguous
(nothing records which unit they meant), so this corrupts real training history silently and
irreversibly, and it is the one finding in this audit that cannot be fully repaired after the fact.
