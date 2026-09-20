# Health tab "Range" mode + a fresh look at Health Connect sync (post-build-49)

Follow-up analysis round, 2026-09-20. Read-only — **nothing in this document has been implemented.**
Every claim below is anchored to `file:line` as of commit `311e496` (build 46) plus the working-tree
changes for build 49 (the `HealthSyncLogic.kt` merge/cap work, `HealthTabViewModel`'s month-hydration
fix). This is the follow-up to `HEALTH_SYNC_BATTERY_AND_IMPORT_AUDIT_2026-09-20.md` — read that first
for the map of the sync machinery; it is not repeated here except where directly relevant.

Two things were asked for:

1. Logical bugs specific to the Health tab's `Range` mode (`HealthTabScreen.kt`, `HealthTabViewModel.kt`).
2. A fresh correctness pass over Health Connect sync now that build 49's fixes (D-1/D-2/B-1/B-3, per
   the prior audit's numbering) are in place — do they compose correctly, and is anything still wrong
   that isn't a battery/performance concern.

**Headline finding:** build 49's own D-1 fix (per-screen month pin/hydrate) introduced a new bug of
the same shape it was fixing — see R-1. Range mode's date-range math, aggregation choices and
month-hydration-per-span logic are otherwise sound. The "duplicate Walking tiles" report is real but
is not a Range-mode bug — see R-2.

---

## R-1. `HealthTabViewModel` and `HomeViewModel` share one un-refcounted pin set (HIGH)

`CloudSyncRepository.pinnedMonths` is a single process-wide `MutableSet<String>`
(`CloudSyncRepository.kt:185-186`), and `pinMonth`/`unpinMonth` are bare add/remove with no owner
tracking or reference count:

```kotlin
fun pinMonth(month: String) { pinnedMonths.add(month) }
fun unpinMonth(month: String) { pinnedMonths.remove(month) }
```
(`CloudSyncRepository.kt:837-838`)

Two independent callers now pin/unpin into this same set:

- `HomeViewModel.init` (`HomeViewModel.kt:343-357`) — pins/unpins the single month the Today screen's
  `_selectedDate` is on.
- `HealthTabViewModel.init` (`HealthTabViewModel.kt:151-174`, build 49's D-1 fix) — pins/unpins every
  month `neededMonths` computes (Day mode's selected date + every month `Range` mode's `[start, end]`
  spans).

Neither tracks *who* asked for a pin. This produces two distinct bugs, both stemming from the same
root cause:

### (a) A still-visible Range-mode month can be evicted out from under the user

Repro:
1. User is on Daybook's Today screen in March (`HomeViewModel` pins `"2026-03"`).
2. User switches into Beast Mode → Health → Range mode → `LAST_3_MONTHS`, which spans Jan–Mar
   (`HealthTabViewModel` pins `"2026-01"`, `"2026-02"`, `"2026-03"` via its own `neededMonths` collector).
3. User goes back to Today and scrolls the week strip into April. `HomeViewModel`'s collector fires
   `cloudSync.unpinMonth("2026-03")` (`HomeViewModel.kt:349`) — unconditionally, because it only knows
   about its *own* previous/next month, not that Health's Range view still needs March.
4. `HealthTabViewModel`'s own `neededMonths` flow has not changed (the user never touched the Range
   picker), so its `distinctUntilChanged()` never re-fires and it never re-pins March
   (`HealthTabViewModel.kt:165-172` only calls `pinMonth` on a *change* to the needed-months set).
5. March is now unpinned. The next `runMaintenance()` → `evictStaleMonths()` pass
   (`CloudSyncRepository.kt:775-786`, still gated only on `it !in recent && it !in pinnedMonths &&
   it !in exportRangePins`) is free to delete March's `health_days`/`health_sessions` rows
   (`ExportImportRepository.evictMonth`, same mechanism the prior audit's D-1 finding described) —
   while the user may still have that Range view open, or navigates back to it a moment later and
   finds January–February populated but March gone.

This is exactly the class of bug D-1's fix (build 49) was written to close, reintroduced by giving a
second, independent owner write access to the same un-scoped set. Note that the codebase already knows
this pattern is dangerous — `exportRangePins` was deliberately split out from `pinnedMonths` for
precisely this reason:

> "Kept separate from `pinnedMonths` (the user's on-screen month) so releasing an export's pins can
> never unpin the month Home is showing." (`CloudSyncRepository.kt:191-193`)

`HealthTabViewModel` was not given the same isolation.

### (b) Pins leak when `HealthTabViewModel` is destroyed without transitioning through an empty range

Neither `HealthTabViewModel` nor `HomeViewModel` overrides `onCleared()` to release its pins. If the
Health tab's `ViewModel` is torn down (e.g. the user backs all the way out of Beast Mode, or the nav
destination is popped) while `Range` mode is showing a non-recent month, that month's pin is never
removed — the only unpin path is the `previousMonths - months` diff inside the `neededMonths` collector
(`HealthTabViewModel.kt:164-172`), which only runs while the ViewModel is alive. The month stays
pinned, and therefore un-evictable, for the rest of the process's life (or until some *other*
`neededMonths` value happens to include and then exclude it again). Lower severity than (a) — it is a
resource leak (unbounded resident health months), not data loss — but it is the mirror image of the
same missing-ownership problem.

**Suggested shape of a fix:** give `HealthTabViewModel` its own pin set, the same way `exportRangePins`
is already split from `pinnedMonths` (e.g. `healthTabPinnedMonths`), and make `evictStaleMonths` check
membership across all pin sets, not just the one `pinnedMonths` collection. Ref-counting
(`Map<String, Int>`) is the more general fix if a third caller is ever added. Either way, add
`onCleared()` overrides to both `HomeViewModel` and `HealthTabViewModel` that unpin whatever they were
last holding, so a destroyed screen doesn't leak a pin forever.

---

## R-2. "Duplicate Walking tiles" is real, but it's not a Range-mode bug — it's shared, unaggregated session rendering (MEDIUM)

The user's report ("duplicate/un-grouped 'Walking' tiles instead of one aggregated distance tile") was
checked against both modes. There is **no session grouping or merging logic anywhere in the app** —
neither Day nor Range mode has one, so this is not a Range-specific regression, but Range mode makes
the symptom much more visible.

- `HealthConnectReader.exerciseSessions` (`HealthConnectReader.kt:266-297`) maps every
  `ExerciseSessionRecord` Health Connect returns 1:1 into a `HealthSession` row — no de-dup, no
  same-type/same-day merge. Health Connect / most band companion apps are known to emit several short
  separate "Walking" segments across a day (auto-detected activity bursts) rather than one session, so
  this list is frequently already fragmented at the source.
- `HealthRepository.pullWindow` (`HealthRepository.kt:480-496`) upserts each one as its own row keyed
  by the Health Connect session id. Compare `SleepSessionMerge.kt`, which exists specifically to merge
  overlapping/adjacent *sleep* records from multiple sources — there is no equivalent for exercise
  sessions.
- `HealthTabScreen.kt:511-517` renders `sessions` (whichever of `daySessions`/`rangeSessions` the mode
  picked, `HealthTabScreen.kt:427`) as one `SessionRow` per raw row, unconditionally, in both modes:
  ```kotlin
  if (HealthCardKind.SESSIONS in visible) {
      items(sessions) { session ->
          Box(Modifier.padding(horizontal = 20.dp)) { SessionRow(session, onClick = { onOpenSessionDetail(session) }) }
      }
  }
  ```
  This is the exact same code path for Day and Range — so a day with 6 fragmented "Walking" segments
  shows 6 tiles in Day mode already; `Range` mode over a week can show 40+, because `observeSessionsInRange`
  (`HealthDao.kt:61-62`) returns every session across every day in the range with no per-type rollup at
  all. There is no "one aggregated distance tile" anywhere — the closest thing, the Activity/Steps card
  (`HealthCardKind.STEPS`), aggregates `distanceMeters` from `health_days`, which is a *separate* number
  from the individual exercise-session list; the two are never reconciled or deduplicated against each
  other, so a user can see both a Steps card claiming some total distance and a wall of "Walking"
  session tiles that don't obviously relate to it.

`sessionIdsStartingBetween`/`deleteSessionsByIds` (D-3 fix, wired at `HealthRepository.kt:501-505`)
correctly reconciles stale/duplicate-by-id sessions (an old id disappearing upstream), but that's a
different problem — it doesn't collapse multiple genuinely-distinct-id sessions of the same type on the
same day into one tile, which is what the user is describing.

**Suggested shape of a fix:** decide a grouping rule (e.g. same `exerciseType` + same `localDate` →
one merged tile summing `durationMinutes`/`activeCalories`/`distanceMeters`, listing the individual
segments in the detail sheet) and apply it once, at the same choke point `aggregateHealthDays` already
established for day-level metrics — probably a `groupSessionsForDisplay(sessions, mode)` next to
`visibleHealthCards`, called from `HealthContent` for both Day and Range so the fix isn't duplicated.
This is a product decision (what counts as "the same walk") as much as a code fix, so it needs one
input from the owner before a fix shape can be finalized.

---

## R-3. Range's "daily average" silently divides by days-with-data, not by the days in the range (LOW/MEDIUM — likely intentional, but undocumented and worth a decision)

`aggregateHealthDays` (`HealthAggregation.kt:45-87`) computes every per-metric average with:

```kotlin
fun doubles(sel: (HealthDay) -> Number?): List<Double> = days.mapNotNull { sel(it)?.toDouble() }
...
stepsAvg = avgOf(doubles { it.steps }),
```

`mapNotNull` drops days where that metric is `null` before averaging — so `stepsAvg` is
`sum(steps on days that have a steps value) / (count of days that have a steps value)`, not
`sum(steps) / totalDays`. The UI then labels this "Daily avg" (`rangeSubtitleFor`,
`HealthCardMetrics.kt:98`), with no qualifier that the denominator excludes no-data days.

The one place this is disclosed at all is the separate "5 of 7 days have data" caption
(`HealthTabScreen.kt:456-459`, driven by `daysWithAnyData`/`totalDays`) sitting above the cards — but
that caption is a single number for the *whole* range (any metric present), not per-card, so it doesn't
tell the user that, say, Steps' avg was computed over 5 days while Heart Rate's was computed over a
different 3. A user who didn't wear their band for 2 of 7 days in the range sees an "avg steps/day"
that is quietly the average of the days they *did* wear it, which reads as higher than a naive
"total ÷ 7" the subtitle's wording implies.

This may well be the intended behavior (an honest average that isn't dragged down by days with no
sensor coverage is arguably the more useful number) — flagging it because it's a real semantic choice
baked silently into `avgOf`'s `mapNotNull`, not something the last audit or this one saw documented as
a decision, and it's the kind of thing that produces a confused support ticket ("my average doesn't
match what I calculated by hand").

**Suggested shape of a fix:** no code change needed either way — just an explicit decision recorded
(e.g. a KDoc line on `aggregateHealthDays` saying "averaged over days with data, deliberately") and,
if the owner wants it disclosed, a "(N days)" suffix on the per-card subtitle using each metric's own
`doubles(...).size` rather than the range-wide `daysWithAnyData`.

---

## R-4. Range mode's own logic (date math, month-hydration-per-span, state) — checked, no bugs found

Went through each of the specific failure modes asked for; recording the "checked, clean" results so
this doesn't need re-auditing next round:

- **Off-by-one / inclusive-exclusive boundaries.** `HealthDao.observeDaysInRange`/`observeSessionsInRange`
  both use `local_date BETWEEN :startYmd AND :endYmd` (`HealthDao.kt:28-29,61-62`), which is inclusive
  on both ends in SQLite, matching every preset's construction (`LAST_7_DAYS` = `today.minusDays(6)` to
  `today`, i.e. 7 calendar days inclusive; `setCustomRange` swaps `start`/`end` if given backwards,
  `HealthTabViewModel.kt:321-325`). No fencepost error found.
- **Timezone / local-date vs epoch-millis mixups.** `Range` mode is local-date-keyed throughout
  (`LocalDate`/`yyyy-MM-dd` strings) — it never touches epoch millis except inside `sleepBedtimeDate`
  (`SleepAttribution.kt:37-40`), which explicitly converts via the injected `zoneId`. Consistent with
  Day mode.
- **Aggregation direction (sum vs. mean).** Deliberately different per metric and correctly applied:
  activity/calories/heart-rate/SpO₂/weight/hydration/nutrition are means (`HealthAggregate`'s `*Avg`
  fields), sleep is a sum (`sleepMinutesTotal` etc., `HealthAggregation.kt:30-35`, comment explains why).
  `metricsFor` (`HealthCardMetrics.kt:34-90`) branches correctly on `isRange` per card and never mixes
  the two up. See R-3 for a nuance in how the mean's denominator is chosen, not a sum/mean mixup.
- **Month hydration across every month a range spans.** `neededMonths`
  (`HealthTabViewModel.kt:140-149`) correctly walks `YearMonth.from(range.start)` to
  `YearMonth.from(range.end)` inclusive with a `while (!m.isAfter(endMonth))` loop — `LAST_3_MONTHS`
  (up to 4 distinct months) is covered, not just the first/last. This is exactly the fix the prior
  audit's D-1 asked for. The bug is in how the *pin* half of that fix shares state with
  `HomeViewModel` (R-1 above), not in the month-walking logic itself.
- **State/race bugs across navigation.** `_range`/`_mode` are plain `MutableStateFlow`s scoped to the
  `ViewModel`; `rangeDaysFlow`/`rangeSessionsFlow`/`paddedRangeDaysFlow` are all `flatMapLatest` off
  `_range` (`HealthTabViewModel.kt:210-228`), so a range change correctly cancels any in-flight query
  for the old range before starting the new one — no stale-overwrite race found. Whether the range
  selection itself "survives" a navigate-away/back depends on whether the Beast Mode nav graph
  saves/restores this destination's back-stack entry (not verified either way in this pass — the
  `ViewModel`'s own state has no bug in it regardless of which happens).
- **Loading/empty state resets.** `visible.isEmpty()` → `EmptyState` (`HealthTabScreen.kt:465-474`) is
  computed fresh from `state.rangeAggregate` every recomposition, not cached/sticky — switching to an
  empty range correctly clears any previously-shown cards rather than leaving stale ones on screen.

---

## R-5. Sync correctness, re-checked post build-49

- **`mergeHealthDay` field coverage** (`HealthSyncLogic.kt:32-63`): every nullable `HealthDay` column
  is covered by the `incoming.x ?: existing.x` pattern; cross-checked field-by-field against
  `isEmptyHealthDay`'s list (`HealthSyncLogic.kt:16-24`) and found no field present in one but missing
  from the other. **A legitimate `0` steps day is correctly preserved** — `?:` only falls back on
  `null`, never on `0`, and `isEmptyHealthDay` tests `== null` too, so a real zero is never mistaken for
  "nothing read" on either the merge side or the "did this window find real data" side (the case R-3's
  sibling item in the task worried about). No bug found here — build 49's fix is correct as shipped.
- **`automaticWindowFloor` cap** (`HealthSyncLogic.kt:76-89`): walked the arithmetic for the boundary
  cases (`hasHistoryAccess=false`, `backfillThrough=null`, `backfillThrough` deeper than `capDays`,
  `backfillThrough` shallower than `capDays`) — all four resolve to a sane floor, and the 90-day cap
  (`capDays = 90L`) is applied regardless of how deep a prior import went, closing the prior audit's B-1.
  No bug found.
- **Session/weight reconciliation (D-3)**: `sessionIdsStartingBetween`/`deleteSessionsByIds` are now
  wired at `HealthRepository.kt:501-505`, gated correctly on `!sessionsFailed` so a failed session read
  can't be misread as "everything in this window was deleted upstream." Same pattern, same correctness,
  for weight readings at `:510-515`. Both are call-site-verified fixed; no longer dead code as the prior
  audit found.
- **`WorkoutHistoryScreen` and health data**: re-checked — still zero references to `health_days`,
  `HealthDao`, or `HealthRepository` anywhere in `WorkoutHistoryScreen.kt` (the only hit is a comment
  about matching the Health tab's settings-gear button style, `WorkoutHistoryScreen.kt:144`). Correctly
  out of scope — Workout history is Beast Mode's own exercise-log data model, distinct from Health
  Connect band sessions (`HealthSession`), and nothing in build 49 or this pass suggests they should be
  merged. No action needed.
- **Changes-token / delta-path arithmetic** (`HealthRepository.kt:210-327`): re-read end to end for the
  `scopeGrew`/`forcedResync`/expired-token/`hasChanges` branches — all four still correctly gate token
  minting and `backfillThroughMillis`/`lastFullResyncAt` bookkeeping the way the prior audit described;
  no new correctness issue found beyond what's already documented there (B-2's 7-day floor is now
  `isDailyWorker`-gated, as recorded, and doesn't affect Range mode's own reads).

---

## Priority

| # | Issue | Severity | Effort |
|---|---|---|---|
| R-1(a) | `HealthTabViewModel`/`HomeViewModel` share one un-refcounted `pinnedMonths` set — a Home navigation can unpin a month Range mode is actively displaying, re-exposing build 49's own eviction bug | **High** | Small–Medium |
| R-2 | No session grouping anywhere (Day or Range) — raw Health Connect exercise segments render 1:1 as tiles; Range mode just shows far more of them | Medium | Medium (needs a product decision on the grouping rule) |
| R-1(b) | Pins leak (never released) when `HealthTabViewModel`/`HomeViewModel` are destroyed without an explicit unpin, from the same missing-ownership root cause as R-1(a) | Low | Small (`onCleared()` overrides) |
| R-3 | Range's "Daily avg" divides by days-with-data, not by the full range length; undisclosed to the user | Low/Medium | Trivial (doc) to Small (UI disclosure) |
| R-4 | Range date-math, sum-vs-mean choice, month-hydration span walk, and flow cancellation on range change — all checked, no bugs found | — | — |
| R-5 | `mergeHealthDay`, `automaticWindowFloor`, D-3 reconciliation, `WorkoutHistoryScreen` scope — all re-verified correct post build-49 | — | — |

R-1 is the one that matters: it's a real regression risk sitting directly on top of the fix build 49
just shipped, with the same user-visible symptom ("my imported/viewed history vanished") the prior
audit spent most of its report on. R-2 is the most likely explanation for the "duplicate Walking tiles"
report, but fixing it needs one product decision (what "one session" should mean) before a fix shape can
be written.
