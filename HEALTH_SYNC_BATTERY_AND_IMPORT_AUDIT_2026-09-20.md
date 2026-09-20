# Health Connect sync, battery cost, "Import my past data", and the settings gear

Analysis round, 2026-09-20. Read-only — **nothing in this document has been implemented.**
Every claim below is anchored to `file:line` as of commit `311e496` (build 46).

Three things were asked for:

1. Analyse the Health Connect sync logic and confirm the app is not battery-hungry.
2. Investigate "Import my past data" in Beast Mode appearing to discard existing data.
3. Put the settings gear (currently only on Beast Mode's Routines page) on every page.

Sections 1 and 2 turn out to be **the same defect seen from two angles**, so read them together.

---

## 0. Map of the sync machinery

| Piece | File | Job |
|---|---|---|
| `HealthConnectReader` | `app/src/main/java/com/daybook/app/data/health/HealthConnectReader.kt` | The only file that talks to `androidx.health.connect.*`. Aggregates + raw reads. |
| `HealthRepository` | `app/src/main/java/com/daybook/app/data/HealthRepository.kt` | Orchestration: decide a window, read it, upsert into Room, set a status line. |
| `HealthSyncStateStore` | `app/src/main/java/com/daybook/app/data/health/HealthSyncStateStore.kt` | SharedPreferences: changes token, `lastPullAt`, `backfillThroughMillis`, granted scope. |
| `HealthDao` | `app/src/main/java/com/daybook/app/data/local/HealthDao.kt` | `health_days` / `health_sessions` / `health_weight_readings`. |
| `WindowRefreshWorker` | `app/src/main/java/com/daybook/app/util/work/WindowRefreshWorker.kt` | Daily worker; `pullDaily()` is folded into it. |
| `CloudSyncRepository` | `app/src/main/java/com/daybook/app/data/sync/CloudSyncRepository.kt` | Firestore push/hydrate/evict. All three health tables are registered sync tables. |

### The four pull entry points

| Trigger | Call site | Throttle |
|---|---|---|
| App resume | `MainActivity.kt:459-461` → `pullOnResume()` | 15 min (`HealthRepository.kt:81-85`) |
| Daily worker | `WindowRefreshWorker.kt:46` → `pullDaily()` | ~24 h, 6 h flex |
| Pull-to-refresh / "Refresh now" | `HealthTabViewModel.kt:299`, `WorkoutSettingsViewModel.kt:137` | none (user-driven) |
| "Import my past data" | `WorkoutSettingsViewModel.kt:167` → `importPastData()` | none (user-driven) |

### Cost of one day of reading

`fetchDay` (`HealthRepository.kt:420`) issues, per calendar day:

- `dayAggregate` (`HealthConnectReader.kt:91`) = **4** binder round trips — one combined
  `aggregate()` plus separate `readRecords()` for SpO₂ (`:127`), weight (`:139`) and nutrition (`:152`).
- `sleepForDay` (`HealthConnectReader.kt:190`) = **1** more.

**≈5 Health Connect IPC calls per day of window**, plus one `exerciseSessions()` read per window
and one extra `aggregate()` per exercise session found (`HealthConnectReader.kt:270-281`).

---

## 1. Battery analysis

### 1.1 What is already right

These are genuinely well done and should not be touched:

- **No dedicated periodic Health job.** The daily pull rides on the existing `WindowRefreshWorker`
  (`WindowRefreshWorker.kt:46`) rather than adding a second `PeriodicWorkRequest`.
- **Resume throttle.** `pullOnResume` bails within 15 minutes of the last pull
  (`HealthRepository.kt:82-84`), so screen-on/app-switch churn costs nothing.
- **Delta-first.** A changes token means the common "nothing happened" case is a *single*
  `getChanges()` IPC and zero Room writes (`HealthRepository.kt:253-256`).
- **Bounded concurrency.** `DAY_FETCH_CONCURRENCY = 6` (`HealthRepository.kt:511`) — cuts wall clock
  without hammering the provider with 30 simultaneous binder calls.
- **Aggregates, never raw sample series.** The only raw reads are SpO₂ / weight / nutrition /
  sleep / sessions, each bounded to one day or one window.
- **Maintenance is throttled.** `runMaintenance()` (a full-DB export plus month re-hash) is capped
  at once per 6 h on the app-stop path (`CloudSyncRepository.kt:1368`, `MAINTENANCE_THROTTLE_MILLIS`
  at `:1512`). The comment at `SyncStateStore.kt:144-151` records that this was already fixed once.
- **Push debounce is 45 s** (`CloudSyncRepository.kt:1510`), and `HealthDayLog`
  (`BackupModel.kt:279-305`) deliberately omits `updatedAt`, so a re-read that finds identical data
  produces an identical month hash and **no Firestore write**. That was a good call.

### 1.2 B-1 — an unbounded automatic window after any backfill (HIGH, battery + data)

`automaticWindowFloor` (`HealthRepository.kt:62-68`) floors every *automatic* window at whichever is
earlier of "30 days ago" and `backfillThroughMillis`. There is **no cap**.

So after one "Import my past data" the floor becomes 365 days ago, permanently
(`recordBackfillDepth` only ever moves it deeper, `:74-78`). Every subsequent full re-read —
first-ever pull, expired changes token, `scopeGrew`, `sleepDedupResyncPending` — now covers
**365 days, not 30**:

```
365 days × 5 IPC calls = ~1,825 binder round trips, in batches of 6
```

Health Connect changes tokens expire (roughly 30 days), so this is not hypothetical: the
expired-token branch at `HealthRepository.kt:236-262` will re-enter it.

Three consequences:

- **No timeout on the automatic paths.** `HealthTabViewModel.refreshNow` has a 20 s
  `withTimeoutOrNull` (`:311`), but `pullOnResume`, `pullDaily` and `importPastData` have none. A
  1,825-call pass inside `WindowRefreshWorker` can plausibly exceed WorkManager's 10-minute
  execution ceiling → the worker is stopped → it retries with backoff → it runs the same 1,825
  calls again. That is a real "app ate my battery overnight" shape.
- **The re-read also rewrites ~365 Room rows**, which fires the `InvalidationTracker` for
  `health_days` (registered at `CloudSyncRepository.kt:1569`) → `pendingPush` → a 45 s debounce → a
  **full-database `exportBackup()` plus per-month hashing** (`CloudSyncRepository.kt:515,526-527`),
  even though the hashes will come back unchanged.
- It feeds the data-loss loop in §2.2.

**Suggested shape of a fix:** cap `automaticWindowFloor` at something like 60–90 days regardless of
`backfillThroughMillis`; leave the deep window to the explicit, user-triggered import only. Add a
timeout (or a resumable cursor) to `pull()` so no automatic path can run unbounded.

### 1.3 B-2 — the delta path always re-reads 8 days (MEDIUM)

`HealthRepository.kt:266-272`:

```kotlin
val since = minOf(sinceLastPull, today.minusDays(7))
    .coerceAtLeast(automaticWindowFloor(today))
```

This was added deliberately, to catch a band back-syncing a few days late — the reasoning at
`:263-272` is sound. But the floor is unconditional, so **every** delta pull where anything at all
changed re-reads at least 8 days:

```
8 days × 5 IPC = ~41 calls + 8 Room row writes, per pull
```

With the 15-minute resume throttle that is up to 4 × per hour of app use. A band syncing steps
every few minutes means `changes.hasChanges` is essentially always true, so this is the *normal*
path, not the exceptional one. Each pass also re-triggers the push pipeline's full-DB export (§1.2).

Note the self-inflicted version: Daybook writes hydration back into Health Connect
(`HealthConnectHydrationWriter.kt`, called from `HomeViewModel.kt:494`). Logging a glass of water
guarantees `hasChanges == true` on the next pull, and therefore a full 8-day re-read.

**Suggested shape of a fix:** keep the 7-day back-sync catch but run it on a slower cadence — e.g.
the daily worker does the 8-day sweep, the 15-minute resume pull only covers `lastPull → today`.

### 1.4 B-3 — every re-read rewrites rows even when nothing changed (MEDIUM)

`fetchDay` always stamps `updatedAt = now` (`HealthRepository.kt:454`) and `upsertDays` always
writes. There is no "is this row actually different from what's in Room?" check, so a no-change
8-day re-read still performs 8 row replacements.

The Firestore side is protected (`HealthDayLog` omits `updatedAt`, §1.1), so this costs no network.
It does cost: SQLite writes, an `InvalidationTracker` fan-out, a debounced full-DB `exportBackup()`,
and a Compose recomposition of the Health tab via `observeDaysInRange`.

**Suggested shape of a fix:** in `pullWindow`, read the existing rows for the window once and skip
the upsert for any day whose payload (ignoring `updatedAt`) is byte-identical.

### 1.5 B-4 — `WindowRefreshWorker` has no constraints (LOW)

`WindowRefreshWorker.kt:67-70` builds a `PeriodicWorkRequest` with **no `Constraints` at all** —
no `setRequiresBatteryNotLow`, no network requirement. It runs `scheduler.syncAll()`,
`cloudSync.runMaintenance()` (full-DB export), `healthRepository.pullDaily()` and a photo sweep.

Compare `SyncFlushWorker.kt:46-48`, which does set `NetworkType.CONNECTED`.

At 24 h/6 h flex this is not a large cost, but `setRequiresBatteryNotLow(true)` is free and would
stop the heaviest scheduled job in the app from firing at 3 %.

### 1.6 B-5 — `READ_HEALTH_DATA_IN_BACKGROUND` is requested but not needed (LOW)

`HealthPermissions.optionalExtras()` (`HealthPermissions.kt:89-90`) bundles
`READ_HEALTH_DATA_IN_BACKGROUND` together with `READ_HEALTH_DATA_HISTORY`, and both are declared in
the manifest (`AndroidManifest.xml:40-41`). The only background read the app performs is
`pullDaily()` once a day; everything else is foreground. Requesting background health access is
extra scope in the consent sheet with no corresponding feature, and it is the kind of thing Play's
health data review asks about.

### 1.7 Battery verdict

**The app is not gratuitously battery-hungry in its steady state** — the cadence design (no extra
periodic job, 15-minute resume throttle, delta-first, 6 h maintenance throttle, 45 s push debounce)
is careful and clearly the product of earlier audits.

The problems are all in the *window sizing*, not the cadence: B-1 turns every full resync into a
1,825-call, untimeboxed pass for the rest of the install's life after one import, and B-2/B-3 make
the routine path ~5× more expensive than it needs to be. Fixing B-1 is the one that matters.

---

## 2. "Import my past data" discarding existing data — **confirmed, two independent causes**

`importPastData()` is `HealthRepository.kt:95-139`, reached from Beast Mode settings
(`WorkoutSettingsScreen.kt:386-392` → `WorkoutSettingsViewModel.kt:167`).

There are two separate mechanisms that destroy data here. They can both fire on the same install.

### 2.1 D-1 — the cloud-sync eviction wipes the import within hours (HIGH, signed-in only)

This is almost certainly the one being seen.

The chain:

1. The import writes up to 365 days into `health_days` / `health_sessions` /
   `health_weight_readings` (`HealthRepository.kt:110`, `pullWindow` at `:350`). That spans ~13
   calendar months.
2. All three tables are registered sync tables (`CloudSyncRepository.kt:1569-1571`), so the writes
   set `pendingPush` and the month docs go to Firestore.
3. The next `runMaintenance()` (`CloudSyncRepository.kt:818`) — daily worker, or app-stop once the
   6 h throttle allows — calls `evictStaleMonths()` (`:771`).
4. `evictStaleMonths` evicts every month that is **not in `recentMonths()`**, not pinned, and whose
   local hash matches the cloud (`:777-780`).
5. **`MonthPartitioner.recentMonths()` is the current month and the previous month. That is all.**
   (`MonthPartitioner.kt:79-80`.)
6. `evictMonth` (`ExportImportRepository.kt:1315`) then runs, for each evicted month:

   ```kotlin
   database.healthDao().deleteSessionsInLocalDateRange(monthStartYmd, monthEndYmd)   // :1358
   database.healthDao().deleteDaysInLocalDateRange(monthStartYmd, monthEndYmd)       // :1359
   database.healthDao().deleteWeightReadingsBetween(monthStartYmd, monthEndYmd)      // :1361
   ```

So **~11 of the 13 imported months are deleted from the device within a day of importing them.**

That is by design *for the sync architecture* — evicted months live in the cloud and are supposed
to come back via `ensureMonthHydrated`. The bug is that **nothing ever hydrates them back for the
Health tab**:

```
$ grep -rn "ensureMonthHydrated\|pinMonth" app/src/main --include=*.kt
app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt:353   ensureMonthHydrated
app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt:354   pinMonth
app/src/main/java/com/daybook/app/ui/home/HomeViewModel.kt:560   ensureMonthHydrated
... (and CloudSyncRepository's own internals)
```

`HomeViewModel` is the **only** caller. `HealthTabViewModel` has zero cloud-sync interaction — it
reads `health_days` straight out of Room (`observeDaysInRange`), so an evicted month simply renders
as "no data", forever. Same for `WorkoutHistoryScreen`.

**Reproduction:** sign in → tap "Import my past data" → confirm the Health tab's Range mode shows
the old months → background the app, or wait for the daily worker → reopen → the imported history
older than last month is gone.

Then, because §1.2's `automaticWindowFloor` is still stamped at 365 days, the next full resync
re-reads the whole year, re-writes it, re-pushes it, and maintenance evicts it again. **Import →
push → evict → re-import** is a permanent loop that burns battery and never keeps the data.

Notes:
- **Signed-out users are not affected** — eviction is signed-in-only (`CloudSyncRepository.kt:829`,
  `if (currentUid == null) return`).
- The Beast Mode backup restore (`ExportImportRepository.importBeastModeBackup`, `:244`) *is*
  deliberately destructive (`deleteAllDays()` at `:278`) but it is a different feature, it is
  labelled "Import Beast Mode backup", and it has an explicit confirmation dialog
  (`WorkoutSettingsScreen.kt:119-138`). That one is working as intended.
- The Hevy import (`HevyImporter.kt`) is additive with dedupe (`:152-158`). Not implicated.

**Suggested shape of a fix** — pick one:
- Exempt health rows from `evictMonth` entirely (they are cheap, ~1 row/day), or
- Pin/hydrate the months the Health tab is showing, the way `HomeViewModel` does, or
- Widen `recentMonths()` for health specifically, or
- Make "Import my past data" pin the months it imported.

The second option is the architecturally correct one; the first is the smallest.

### 2.2 D-2 — `REPLACE` upserts overwrite good data with nulls (HIGH, hits everyone)

Independent of sign-in.

`upsertDays` is `@Insert(onConflict = OnConflictStrategy.REPLACE)` (`HealthDao.kt:41-42`). In
SQLite, `INSERT OR REPLACE` **deletes the existing row and inserts the new one** — it is not a
column-wise merge. `fetchDay` (`HealthRepository.kt:420-478`) builds the whole `HealthDay` from
whatever this read returned, so **any metric Health Connect does not return this time becomes
`NULL` and destroys what was stored.**

Two ways that happens in practice:

**(a) History permission goes away.** `importPastData` correctly checks `hasHistoryAccess` before
choosing a 365-day vs 30-day window (`:108-110`). **`automaticWindowFloor` does not.** It re-reads
back to `backfillThroughMillis` unconditionally (`:62-68`). Without
`READ_HEALTH_DATA_HISTORY`, Health Connect silently caps reads at 30 days — the call *succeeds* and
returns nothing. So:

- `dayAggregate` returns an all-null `DayAggregate` for days 31–365
- `fetchDay` returns `failed = false` (`:477`) — an empty read is indistinguishable from a
  successful one
- `pullWindow` counts them as `daysWritten` (`:389`), so `requireNotFullyFailed()` does not trip
- `upsertDays` **replaces a year of real history with empty rows**
- the status line says *"Last updated just now."*

Android auto-resets unused permissions, and `markHistoryExtrasRequested()` (`:502`) sets a
persistent flag so the extras consent sheet is **never shown again** — so once
`READ_HEALTH_DATA_HISTORY` lapses, the app will never re-request it and will quietly null out the
imported year on the next full resync.

**(b) A single read permission is revoked.** `dayAggregate` filters its metric set by what is
granted (`HealthConnectReader.kt:113`) — correct, and the reason a partial grant no longer hard-
fails. But the resulting nulls then go through `REPLACE`. Turning off "Heart rate" in Health
Connect therefore erases every stored heart-rate value for every day in the next window, not just
future days.

**Suggested shape of a fix:** never let a read that produced *no* data overwrite a row that has
data. Either add a merge-aware upsert (`@Update`-style, or read-then-merge in `pullWindow`) that
keeps the stored value when the incoming one is null, or skip the upsert entirely for a day whose
`DayAggregate` came back completely empty. Also make `automaticWindowFloor` respect
`hasHistoryAccess`.

### 2.3 D-3 — stale sessions and weight readings are never removed (LOW)

`pullWindow` upserts sessions and weight readings by Health Connect id (`:379`, `:370`) but never
deletes rows for records that disappeared upstream. `HealthDao.sessionIdsStartingBetween`
(`HealthDao.kt:86-90`) exists *precisely* for this reconciliation and is **dead code — zero call
sites**. A workout deleted in the band app stays in Daybook forever, and a re-synced session that
gets a new Health Connect id shows up as a duplicate.

### 2.4 D-4 — the import claims success for a window that read nothing (LOW)

The comment at `HealthRepository.kt:118-121` says `backfillThroughMillis` is "only stamped for a
window that actually wrote something", but the code at `:122` stamps it unconditionally in the
success branch, and `requireNotFullyFailed()` (`:406-410`) only trips when `daysWritten == 0` —
which, per D-2, an all-empty read never is. So a 365-day import that found no data at all reports
*"Imported your health history back to 21 Sep 2025."*, stamps the 365-day floor, and arms §1.2's
battery cost for nothing.

---

## 3. The settings gear across all pages

### 3.1 Where it is today

`WorkoutHomeScreen.kt:107-119` — Beast Mode's Routines landing:

```kotlin
ScreenHeader(
    title = "Beast Mode",
    subtitle = ...,
    actions = {
        CircleIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Beast Mode settings",
            onClick = onOpenWorkoutSettings,
            size = 40.dp
        )
    }
)
```

`onOpenWorkoutSettings` is plumbed to `WorkoutRoutes.SETTINGS` and is **passed to this one screen
only** — `WorkoutHomeScreen` is the sole route to Beast Mode settings anywhere in the app.

### 3.2 Current state of every top-level page

`ScreenHeader` (`app/src/main/java/com/daybook/app/ui/components/ScreenHeader.kt:29-54`) already
takes an `actions: @Composable RowScope.() -> Unit` slot, so this is a per-screen wiring job, not a
component change.

**Beast Mode nav** (`MainActivity.kt:760-770`):

| Page | Route | Header | Top-right today |
|---|---|---|---|
| Today | `beast_today` | — | Not a page; the nav item exits Beast Mode (`MainActivity.kt:714`) |
| Routines | `WorkoutRoutes.HOME` | `WorkoutHomeScreen.kt:107` | ⚙️ **gear → Beast settings** |
| History | `WorkoutRoutes.HISTORY` | `WorkoutHistoryScreen.kt:137` | **nothing** — `ScreenHeader(title, subtitle)`, no `actions` at all |
| Health | `WorkoutRoutes.LIBRARY` | `HealthTabScreen.kt:106` | filter icon only ("Choose cards to show") — **no gear** |

**Daybook nav** (`MainActivity.kt:580-587`):

| Page | Route | Header | Top-right today |
|---|---|---|---|
| Today | `home` | `HomeScreen.kt:142` (`HomeHeader`, custom) | Avatar → app Settings (`:436-441`) |
| Habits | `routines` | `RoutinesScreen.kt:65` | Avatar → app Settings |
| Intake | `foodmed` | `FoodMedScreen.kt:63` | Avatar → app Settings |
| Report | `report` | `DailyReportScreen.kt:121` | Avatar → app Settings |

### 3.3 What "the gear on all pages" should mean

The two modes have different destinations, so this needs one decision from you before it is built:

- **Beast Mode (History, Health):** unambiguous. Add the same
  `CircleIconButton(Icons.Filled.Settings, …, onClick = onOpenWorkoutSettings)` to both, plumb
  `onOpenWorkoutSettings` through from `MainActivity`. On the Health tab it sits **beside** the
  existing filter button (`HealthTabScreen.kt:114-135`), which should keep its badge behaviour.
  Low risk, ~3 files.

- **Daybook Mode (Today, Habits, Intake, Report):** these four already have a working
  top-right entry point to app Settings — the Avatar. So "add a gear" here means one of:
  1. **Leave them as-is** (the Avatar *is* the settings affordance, it is just not gear-shaped).
  2. **Replace the Avatar with a gear** — more legible as "settings", loses the profile photo.
  3. **Show both** — gear + Avatar side by side; two controls that go to the same screen, which is
     the worst of the three.

**Open question for you:** should Daybook mode's four pages get a literal gear, or is the Avatar
already doing that job? My recommendation is **(1) leave Daybook mode alone, and do Beast Mode's
History + Health only** — that fixes the real gap (two Beast pages with no settings route at all)
without putting two identical entry points in the main nav's header.

One consistency note: the Beast gear is `size = 40.dp` and untinted, while the Health tab's filter
button is `CircleStyle.Tonal` at `IconButtonSize.Lg.dp` (`HealthTabScreen.kt:117-120`). Whichever
way this goes, the two buttons sharing that header should match.

---

## 4. Priority

| # | Issue | Severity | Effort |
|---|---|---|---|
| D-1 | Eviction deletes imported health months; nothing rehydrates them for the Health tab | **High** | Medium |
| D-2 | `REPLACE` upsert nulls out stored metrics when a read comes back empty | **High** | Medium |
| B-1 | `automaticWindowFloor` uncapped → 1,825-call untimeboxed resync forever after an import | **High** | Small |
| B-2 | Delta path always re-reads 8 days on every 15-minute resume pull | Medium | Small |
| B-3 | No-change re-reads still rewrite rows → full-DB export on the push path | Medium | Medium |
| D-3 | Stale sessions / weight readings never reconciled (`sessionIdsStartingBetween` is dead code) | Low | Small |
| D-4 | Import reports success + stamps the 365-day floor for a window that read nothing | Low | Small |
| B-4 | `WindowRefreshWorker` has no `Constraints` (no battery-not-low) | Low | Trivial |
| B-5 | `READ_HEALTH_DATA_IN_BACKGROUND` requested but not needed | Low | Trivial |
| UI | Gear missing on Beast History + Health | — | Small |

B-1, D-1 and D-2 are one loop and should be fixed in one round: cap the automatic window, stop
eviction from eating health months (or hydrate them back), and make the upsert refuse to overwrite
real data with nulls.
