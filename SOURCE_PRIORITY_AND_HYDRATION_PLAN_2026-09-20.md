# Source priority ranking, Hydration-as-Intake, and Workout-photo cloud backup — Implementation Plan
_2026-09-20 — builds on builds 51-52 (uncommitted, on disk). Do not revert any of that work._

This plan is precise enough to implement directly. It has three independent sections that can be
built in any order but ship in the same build:

1. §1 — Ranked, per-metric Health Connect source priority + retroactive re-resolution.
2. §2 — Hydration moves from a Beast-Mode/Home-only construct to a first-class Intake item.
3. §3 — Workout session photos backed up to Firebase Storage.

No versionCode bump, no git commits — both handled separately. Keep the existing visual theme
(SoftCard/DaybookText/CardTints/etc.) throughout; no new theme concepts.

---

## §1. Ranked per-metric Health Connect source priority

### 1.1 Current state (what's already shipped, builds 51-52)

- `AppSettings` (`app/src/main/java/com/daybook/app/data/model/DataModel.kt:196`) has nullable
  `String` columns `healthSourceSteps/Distance/Calories/HeartRate/Spo2/Hydration/Weight/Nutrition`
  (added by `MIGRATION_34_35`, `app/src/main/java/com/daybook/app/data/local/Migrations.kt:941`),
  each holding **at most one** package name, or `null` = Auto.
- `HealthSourcePriority.resolve(preference, observedCandidates)`
  (`app/src/main/java/com/daybook/app/data/health/HealthSourcePriority.kt`) picks `preference` if
  set, else `resolveAuto` (alphabetically-first of the previous day's observed sources).
- `HealthConnectReader.dayAggregate(...)` (`app/src/main/java/com/daybook/app/data/health/HealthConnectReader.kt:104`)
  takes one resolved package per data point, groups metrics by resolved source, and issues **one**
  `aggregate()`/`readRecords()` call per distinct source group with `dataOriginFilter` restricted to
  that one package (`HealthSourcePriority.toDataOriginFilter`).
- `HealthRepository.resolvedSourcePreferences()` (`app/src/main/java/com/daybook/app/data/HealthRepository.kt:484`)
  builds the `SourcePreferences` (8 fields) passed into `fetchDay`/`dayAggregate` every `pullWindow` pass.
- Settings UI: `WorkoutSettingsScreen.kt`'s "Data source priority" section (line ~515-812) has 8
  `SettingsRow`s, each opening a single-select `SortSheet` (`Auto` or one package). ViewModel setters
  `setHealthSourceSteps(pkg: String?)` etc. in `WorkoutSettingsViewModel.kt:135-166` save-then-call
  `refreshHealthNow()` — which is `healthRepository.refreshNow()`, a **delta** pull using the stored
  changes token (`HealthRepository.kt:147`,`235`). **This is why "for now and for prev days" is
  currently broken**: a changed preference is only applied to days a subsequent delta/full-resync
  touches, never to already-synced history.

### 1.2 Schema change — MIGRATION_35_36 (DB v35 → v36)

Reuse the CSV convention already used for `health_days.activity_source_apps`
(`HealthConnectReader.kt:88`, comma-joined `dataOrigin.packageName`s, no escaping needed since
Android package names cannot contain commas).

Add **8 new nullable `TEXT` columns** to `app_settings`, one per data point, each holding a
comma-joined, **rank-ordered** list of package names (rank 0 = most preferred). Do **not** repurpose
the existing 8 singular columns — following this file's own established precedent
(`MIGRATION_34_35`'s KDoc: "old column stays in place, unread from here on"), keep
`health_source_steps` etc. as-is, dead after this migration, and add new columns instead. This
keeps the migration mechanically identical to the last two (additive-only, seeded from the old
value, never read again from the old column):

In `app/src/main/java/com/daybook/app/data/local/Migrations.kt`, after `MIGRATION_34_35` (before the
`ALL_MIGRATIONS` array), add:

```kotlin
/**
 * Owner feedback round 3 (2026-09-20) — replaces the single-pick-per-metric preference with a
 * ranked, ordered list per data point (a user can now say "prefer Google Fit, but fall back to Mi
 * Fitness on days Google Fit has nothing"). New nullable `TEXT` columns hold a comma-joined,
 * rank-ordered list of package names — same CSV convention as `health_days.activity_source_apps`
 * (HealthConnectReader.kt). Seeded from the existing singular `health_source_*` columns (a
 * single-package preference becomes a one-element ranked list), so nobody's current choice is
 * silently reset. The old singular columns are left in place, unread from here on (matches
 * MIGRATION_34_35's own precedent for `health_source_activity`). Nothing existing is dropped.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_steps_ranked TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_distance_ranked TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_calories_ranked TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_heart_rate_ranked TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_spo2_ranked TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_hydration_ranked TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_weight_ranked TEXT")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN health_source_nutrition_ranked TEXT")
        db.execSQL(
            "UPDATE app_settings SET " +
                "health_source_steps_ranked = health_source_steps, " +
                "health_source_distance_ranked = health_source_distance, " +
                "health_source_calories_ranked = health_source_calories, " +
                "health_source_heart_rate_ranked = health_source_heart_rate, " +
                "health_source_spo2_ranked = health_source_spo2, " +
                "health_source_hydration_ranked = health_source_hydration, " +
                "health_source_weight_ranked = health_source_weight, " +
                "health_source_nutrition_ranked = health_source_nutrition"
        )
    }
}
```

Update `ALL_MIGRATIONS` (`Migrations.kt:977`) to append `MIGRATION_35_36`. Bump `AppDatabase.kt`'s
`@Database(..., version = 36, ...)` (currently `version = 35` at `AppDatabase.kt:75`) and update its
KDoc comment block the same way builds 51/52 did (one short paragraph noting what changed).

**`AppSettings` data class** (`DataModel.kt:196`) — add 8 new fields right after the existing 8
`healthSource*` fields (search for `health_source_nutrition` in that file to find the insertion
point), each `String? = null`, `@ColumnInfo(name = "health_source_steps_ranked")` etc. Do not remove
the 8 old fields.

**`AppSettingsDao`** (`app/src/main/java/com/daybook/app/data/local/AppSettingsDao.kt`, next to
the existing `updateHealthSourceSteps` block at line ~195-210) — add 8 new `@Query("UPDATE
app_settings SET health_source_steps_ranked = :v WHERE id = 1") suspend fun
updateHealthSourceStepsRanked(v: String?)` (and 7 siblings), where `v` is the pre-joined CSV string
(joining happens in the repository, matching how `activitySourceApps.joinToString(",")` is done in
`HealthRepository.fetchDay` today).

**`AppSettingsRepository`** (`app/src/main/java/com/daybook/app/data/AppSettingsRepository.kt:163-170`)
— add 8 new methods taking `List<String>` and joining to CSV before the DAO call, e.g.:

```kotlin
suspend fun setHealthSourceStepsRanked(ranked: List<String>) {
    ensureRow()
    database.appSettingsDao().updateHealthSourceStepsRanked(ranked.takeIf { it.isNotEmpty() }?.joinToString(","))
}
```
(and 7 siblings: Distance/Calories/HeartRate/Spo2/Hydration/Weight/Nutrition). An empty list stores
`null` (= pure Auto, same semantics as today's `null` preference).

Add a small shared parse helper (put it in `HealthSourcePriority.kt` since that's this concept's
existing home):

```kotlin
fun parseRanked(csv: String?): List<String> =
    csv?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
```

### 1.3 Resolution logic — `HealthSourcePriority`

Add a new function alongside `resolve`/`resolveAuto` (`HealthSourcePriority.kt`) — this is now the
one the rest of the app uses; the old single-preference `resolve` stays for reference/tests but has
no more call sites in `HealthRepository`:

```kotlin
/**
 * Owner feedback round 3 (2026-09-20) — [ranked] is the user's ordered list of preferred packages
 * for one data point (rank 0 first). Falls back to [resolveAuto] over [observedCandidates] when
 * [ranked] is empty (pure Auto, same as today). Returns the ordered list of candidates a caller
 * should try IN ORDER, stopping at the first one that actually has data for the day in question —
 * this object does no I/O, so it cannot itself know which candidate "has data"; that per-day check
 * happens in HealthConnectReader.dayAggregate (see its KDoc).
 */
fun candidateOrder(ranked: List<String>, observedCandidates: Collection<String>): List<String> {
    if (ranked.isNotEmpty()) return ranked
    val auto = resolveAuto(observedCandidates)
    return listOfNotNull(auto)
}
```

### 1.4 `HealthConnectReader.dayAggregate` — per-day, per-metric fallback

This is the core behavioral change. Replace the single-`source: String?`-per-metric design with an
**ordered candidate list per metric**, and try candidates in rank order until one yields data for
that specific metric on that specific day — this is what makes "day X falls back to Mi Fitness
because Google Fit has nothing that day, day Y uses Google Fit because it does" work.

**Design constraint to preserve**: keep the existing "one call per distinct group" battery-cost
property for the common case (everyone on Auto or one single preference for everything) — do not
regress to N calls per metric when ranked lists are trivial.

Change `dayAggregate`'s signature (`HealthConnectReader.kt:104-121`) from 8 `xSourcePackage:
String?` parameters to 8 `xRanked: List<String>` parameters (empty list = old "no restriction /
Auto with nothing observed" behavior when candidates are also empty):

```kotlin
suspend fun dayAggregate(
    date: LocalDate,
    zoneId: ZoneId,
    granted: Set<String>,
    stepsRanked: List<String> = emptyList(),
    distanceRanked: List<String> = emptyList(),
    caloriesRanked: List<String> = emptyList(),
    heartRateRanked: List<String> = emptyList(),
    spo2Ranked: List<String> = emptyList(),
    hydrationRanked: List<String> = emptyList(),
    weightRanked: List<String> = emptyList(),
    nutritionRanked: List<String> = emptyList()
): DayAggregate
```

Replace the `resultsBySource`/`groupBy{it.source}` block (`HealthConnectReader.kt:132-166`) with a
resolution loop keyed by the metric's ranked list (identity-grouped: metrics sharing the exact same
list — e.g. every metric with an empty/Auto list, or two metrics both ranked `[pkgA, pkgB]` — are
still batched into the SAME calls, one call per rank tried, not one call per metric per rank):

```kotlin
data class MetricSpec(val metric: AggregateMetric<*>, val permission: String, val ranked: List<String>)
val allMetricSpecs = listOf(
    MetricSpec(StepsRecord.COUNT_TOTAL, HealthPermission.getReadPermission(StepsRecord::class), stepsRanked),
    MetricSpec(DistanceRecord.DISTANCE_TOTAL, HealthPermission.getReadPermission(DistanceRecord::class), distanceRanked),
    MetricSpec(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), caloriesRanked),
    MetricSpec(TotalCaloriesBurnedRecord.ENERGY_TOTAL, HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class), caloriesRanked),
    MetricSpec(HeartRateRecord.BPM_AVG, HealthPermission.getReadPermission(HeartRateRecord::class), heartRateRanked),
    MetricSpec(HeartRateRecord.BPM_MIN, HealthPermission.getReadPermission(HeartRateRecord::class), heartRateRanked),
    MetricSpec(HeartRateRecord.BPM_MAX, HealthPermission.getReadPermission(HeartRateRecord::class), heartRateRanked),
    MetricSpec(RestingHeartRateRecord.BPM_AVG, HealthPermission.getReadPermission(RestingHeartRateRecord::class), heartRateRanked),
    MetricSpec(HydrationRecord.VOLUME_TOTAL, HealthPermission.getReadPermission(HydrationRecord::class), hydrationRanked)
)
val grantedSpecs = allMetricSpecs.filter { it.permission in granted }

// Group metrics that share the exact same ranked list (by value, not reference) — same "everyone
// on Auto shares one call" property the old `groupBy { it.source }` had, extended to ranked lists.
val bySameRanking = grantedSpecs.groupBy { it.ranked }
// Per-metric resolved AggregationResult + which rank/source it actually resolved to (for
// activitySourceApps / debugging), filled in as we walk ranks.
val resolvedResultFor = HashMap<AggregateMetric<*>, AggregationResult?>()
val resolvedSourceFor = HashMap<AggregateMetric<*>, String?>()

for ((ranked, specsInGroup) in bySameRanking) {
    // Candidates to try, in order. Empty ranked list -> old "no restriction" single call (Auto
    // with nothing observed, or the caller passed nothing — matches today's behavior exactly).
    val candidates: List<String?> = ranked.ifEmpty { listOf(null) }
    var remaining = specsInGroup.map { it.metric }.toMutableSet()
    for (candidate in candidates) {
        if (remaining.isEmpty()) break
        val result = runCatchingCancellable {
            client().aggregate(
                AggregateRequest(
                    metrics = remaining.toSet(),
                    timeRangeFilter = filter,
                    dataOriginFilter = HealthSourcePriority.toDataOriginFilter(candidate)
                )
            )
        }.getOrNull()
        if (result != null) {
            val resolvedNow = remaining.filter { result.contains(it) }
            resolvedNow.forEach { m -> resolvedResultFor[m] = result; resolvedSourceFor[m] = candidate }
            remaining -= resolvedNow.toSet()
        }
        // Metrics still in `remaining` after the LAST candidate simply stay unresolved (absent) —
        // exactly like today's "metric absent" degrade path.
    }
}
fun <T : Any> valueFor(metric: AggregateMetric<T>): T? =
    resolvedResultFor[metric]?.let { if (it.contains(metric)) it.get(metric) else null }
```

Update every `valueFor(X, xSourcePackage)` call at the bottom of `dayAggregate`
(`HealthConnectReader.kt:240-253`) to the new no-source-arg `valueFor(X)`.

Apply the **same per-candidate loop pattern** to the three `readRecords`-based reads (SpO2 at
`HealthConnectReader.kt:174-182`, weight at `192-200`, nutrition at `214-222`): try each ranked
candidate's `dataOriginFilter` in order, stop at the first that returns a non-empty record list
(fall through to the next candidate on empty, same "no data that day" semantics as the aggregate
path). Extract a small private suspend helper to avoid triplicating this:

```kotlin
private suspend fun <T : androidx.health.connect.client.records.Record> firstNonEmpty(
    recordType: kotlin.reflect.KClass<T>,
    filter: TimeRangeFilter,
    ranked: List<String>
): List<T> {
    val candidates: List<String?> = ranked.ifEmpty { listOf(null) }
    for (candidate in candidates) {
        val records = runCatchingCancellable {
            client().readRecords(
                ReadRecordsRequest(recordType, filter, dataOriginFilter = HealthSourcePriority.toDataOriginFilter(candidate))
            ).records
        }.getOrDefault(emptyList())
        if (records.isNotEmpty()) return records
    }
    return emptyList()
}
```
Call as `firstNonEmpty(OxygenSaturationRecord::class, filter, spo2Ranked)`, etc. (nutrition's
"no restriction" call when `nutritionRanked` is empty AND nothing observed yet still degrades to an
unfiltered read, matching today.)

`activitySourceApps` (`HealthConnectReader.kt:229-237`) — union of `resolvedResultFor.values`'s
`dataOrigins` plus the SpO2 records' origins, same as today, just reading from the new maps instead
of `resultsBySource`.

### 1.5 `HealthRepository` — threading the ranked lists through

`SourcePreferences` (`HealthRepository.kt:472-475`) — change each field from `String?` to
`List<String>`.

`resolvedSourcePreferences()` (`HealthRepository.kt:484-498`) — read the 8 new ranked columns
(parsed via `HealthSourcePriority.parseRanked`) instead of the 8 old singular columns, and pass
through `HealthSourcePriority.candidateOrder(ranked, recentCandidates)`:

```kotlin
private suspend fun resolvedSourcePreferences(): SourcePreferences {
    val settings = appSettingsRepository.getSettings()
    val recentCandidates = database.healthDao().mostRecentActivitySourceApps()
        ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    return SourcePreferences(
        steps = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceStepsRanked), recentCandidates),
        distance = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceDistanceRanked), recentCandidates),
        calories = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceCaloriesRanked), recentCandidates),
        heartRate = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceHeartRateRanked), recentCandidates),
        spo2 = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceSpo2Ranked), recentCandidates),
        hydration = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceHydrationRanked), recentCandidates),
        weight = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceWeightRanked), recentCandidates),
        nutrition = HealthSourcePriority.candidateOrder(HealthSourcePriority.parseRanked(settings.healthSourceNutritionRanked), recentCandidates)
    )
}
```

`fetchDay` (`HealthRepository.kt:618-624`) — pass `prefs.steps` etc. (now `List<String>`) straight
through to `reader.dayAggregate(...)`'s new `xRanked` parameters.

### 1.6 Retroactive re-resolution ("now and for prev days")

**Assumption (hardest call #1)**: reuse `backfillActivitySourceApps`'s exact pattern — call the
existing `pullWindow(from = earliest, to = today, granted)` again in full (recomputing **every**
metric for every already-synced day), not a metric-scoped partial recompute. Rationale: (a) the
owner explicitly pointed at this function as "the reference pattern" and it does exactly "walk
existing date range, re-run dayAggregate with a new filter, upsert"; (b) `dayAggregate` has no
partial-metric read mode and building one is a much larger, riskier change for one settings action;
(c) `mergeHealthDay` (`HealthSyncLogic.kt:35`) already makes this safe — a freshly-recomputed value
that happens to be unchanged for an untouched metric produces a byte-identical `HealthDay`
(`sameHealthDayPayload`, `HealthSyncLogic.kt:70`), so `pullWindow` silently skips the write for that
day (no-op, not a "reset"); (d) it correctly leaves every non-health table completely untouched —
`pullWindow` only ever writes `health_days`, `health_sessions` (session read — unaffected, no source
preference to change), and `health_weight_readings`. Cost accepted: extra Health Connect calls
(bounded to the ranked list's own length × days in range, capped by `AUTOMATIC_WINDOW_CAP_DAYS`
which does NOT apply here since this reuses `pullWindow` directly, not `pullAutomatic` — see below
for the explicit bound instead).

Add to `HealthRepository.kt`, near `backfillActivitySourceApps` (`HealthRepository.kt:157-172`):

```kotlin
/** Owner feedback round 3 (2026-09-20) — "the data source and priority I choose must change
 *  config for now and for prev days also". Re-aggregates every already-synced `health_days` row
 *  (bounded to `healthDao.earliestLocalDate()..today`, exactly like `backfillActivitySourceApps`)
 *  using whatever source preferences are CURRENTLY stored — i.e. call this AFTER saving the new
 *  ranked list, never before. Recomputes every metric for the window (not just the metric whose
 *  ranking changed) for simplicity/correctness — see this plan's §1.6 for why that's safe (merge +
 *  no-op-on-identical-payload). Touches ONLY `health_days`/`health_sessions`/
 *  `health_weight_readings` — no workout logs, no other settings, nothing else in Room.
 *  Exposed as a suspend fun (not fire-and-forget like backfillActivitySourceApps) so the caller can
 *  show a loading state and the result (success/partial/failure) directly. */
suspend fun reresolveSourcePreferences(): PullOutcome {
    val earliest = database.healthDao().earliestLocalDate()
        ?: return PullOutcome.Success(hasNewData = false)
    val start = LocalDate.parse(earliest)
    val today = LocalDate.now(zoneId)
    val granted = runCatchingCancellable { reader.grantedPermissions() }.getOrElse { emptySet() }
    if (granted.isEmpty()) return PullOutcome.PermissionsMissing
    val result = runCatchingCancellable {
        val r = pullWindow(start, today, granted)
        r.requireNotFullyFailed()
        r
    }
    return result.fold(
        onSuccess = { r ->
            val partial = r.daysFailed > 0
            val msg = if (partial) {
                "Updated — ${r.daysFailed} of ${r.totalDays} days couldn't be re-checked."
            } else {
                "Source priority applied to your whole history."
            }
            setStatus(msg, failed = partial)
            PullOutcome.Success(hasNewData = r.anyChanged)
        },
        onFailure = { t ->
            Log.e(TAG, "reresolveSourcePreferences failed", t); recordUnhandledException(t)
            setStatus("Couldn't re-apply your source priority. Try again.", failed = true)
            PullOutcome.Failure("Couldn't re-apply your source priority. Try again.")
        }
    )
}
```

No new gating flag (unlike `activitySourceBackfillDone`) — this is a deliberate, user-triggered
action every time a ranked list is saved, not a one-time migration step.

### 1.7 ViewModel — trigger + loading state

**Assumption (hardest call #2 — the trigger mechanism)**: fire synchronously, in-line, the moment a
ranked list is saved from Settings (not batched/debounced, not a background worker) — reusing
`_healthIsBusy`/`healthActionResult` exactly as `refreshHealthNow()` already does, so the same
"Refresh now" busy-row UI (`WorkoutSettingsScreen.kt:468`, disables the row while
`healthIsBusy`) covers this too. Rationale: the owner's own wording ("triggered right when a
priority list is saved... with a loading/progress state") matches this file's existing
`refreshHealthNow` pattern precisely; a WorkManager-based background job would need its own
progress-plumbing back to this screen for no benefit, since the user is looking at this exact
screen when they make the change.

In `WorkoutSettingsViewModel.kt`, replace the 8 `setHealthSourceX(pkg: String?)` methods
(lines 135-166) with 8 ranked-list setters that call the new repository setter then
`reresolveSourceNow()` (a new busy-tracked wrapper, NOT `refreshHealthNow()`):

```kotlin
fun setHealthSourceStepsRanked(ranked: List<String>) = safeLaunch {
    repo.setHealthSourceStepsRanked(ranked)
    reresolveSourceNow()
}
fun setHealthSourceDistanceRanked(ranked: List<String>) = safeLaunch { repo.setHealthSourceDistanceRanked(ranked); reresolveSourceNow() }
fun setHealthSourceCaloriesRanked(ranked: List<String>) = safeLaunch { repo.setHealthSourceCaloriesRanked(ranked); reresolveSourceNow() }
fun setHealthSourceHeartRateRanked(ranked: List<String>) = safeLaunch { repo.setHealthSourceHeartRateRanked(ranked); reresolveSourceNow() }
fun setHealthSourceSpo2Ranked(ranked: List<String>) = safeLaunch { repo.setHealthSourceSpo2Ranked(ranked); reresolveSourceNow() }
fun setHealthSourceHydrationRanked(ranked: List<String>) = safeLaunch { repo.setHealthSourceHydrationRanked(ranked); reresolveSourceNow() }
fun setHealthSourceWeightRanked(ranked: List<String>) = safeLaunch { repo.setHealthSourceWeightRanked(ranked); reresolveSourceNow() }
fun setHealthSourceNutritionRanked(ranked: List<String>) = safeLaunch { repo.setHealthSourceNutritionRanked(ranked); reresolveSourceNow() }

/** Same busy/result plumbing as [refreshHealthNow] (_healthIsBusy/_healthActionResult), but calls
 *  the full-history re-resolution instead of a delta pull — see HealthRepository
 *  .reresolveSourcePreferences's KDoc for why a delta pull alone isn't enough here. */
private suspend fun reresolveSourceNow() {
    _healthIsBusy.value = true
    _healthActionResult.value = null
    _healthActionIsFailure.value = false
    try {
        when (val r = healthRepository.reresolveSourcePreferences()) {
            is HealthRepository.PullOutcome.Success -> {
                _healthActionResult.value = "Source priority applied to your whole history."
                _healthActionIsFailure.value = false
            }
            is HealthRepository.PullOutcome.Failure -> {
                _healthActionResult.value = r.message
                _healthActionIsFailure.value = true
            }
            HealthRepository.PullOutcome.PermissionsMissing -> {
                _healthActionResult.value = "Daybook no longer has access to your health data. Tap Connect to share it again."
                _healthActionIsFailure.value = true
            }
        }
    } finally {
        _healthIsBusy.value = false
        refreshHealthPermissionState()
    }
}
```

`healthActionResult`/`healthIsBusy` are already `StateFlow`s the screen collects
(`WorkoutSettingsScreen.kt:504-513` renders `healthActionResult`) — no new UI plumbing needed there
beyond what §1.8 adds for the picker sheets themselves.

### 1.8 Settings UI — ranked-list picker

**Assumption**: no reorderable-list UI pattern exists anywhere else in this codebase (checked
`grep -rn "drag\|reorder\|Reorder" app/src/main/java` — the only "sort" UI is the existing
single-select `SortSheet`, used for read-only preference lists, not user-built ordered lists).
Building simple up/down-arrow rows (not drag-and-drop) inside a `BottomSheetMenu`-shaped custom
composable, as the owner's own prompt pre-authorizes.

Add a new composable `RankedSourceSheet` in `WorkoutSettingsScreen.kt` (or a new small file
`app/src/main/java/com/daybook/app/ui/workout/RankedSourceSheet.kt` if preferred for size) that:

- Takes `visible: Boolean`, `onDismiss: () -> Unit`, `title: String`, `available: List<String>`
  (candidate packages not yet ranked, from `discoverSourcePackages` unioned with any already-ranked
  package the discovery window no longer sees — same union rule `healthSourceOptionsFor` already
  uses at `WorkoutSettingsScreen.kt:723-732`), `ranked: List<String>`, `onRankedChange: (List<String>) -> Unit`.
- Renders (inside the app's existing bottom-sheet chrome, e.g. reuse `BottomSheetMenu`'s Compose
  `ModalBottomSheet` host, or the same host `SortSheet` uses — check `SortSheet.kt`'s own
  `ModalBottomSheet` wrapper and copy its chrome, not its single-select body):
  - A section "Priority order" listing `ranked` items in order, each row: `SourceAppLabels.labelFor(pkg) ?: pkg`,
    an up-arrow `IconButton` (disabled on the first row), a down-arrow `IconButton` (disabled on
    the last row), and a remove ("x") `IconButton` that moves the package back into "Available".
  - A section "Available" listing `available - ranked` (packages not yet ranked), each row tappable
    to append it to the end of `ranked`.
  - "Auto" is represented as the EMPTY ranked-list state — if `ranked.isEmpty()`, show a single
    "Auto (let Daybook pick)" row instead of the two sections, with a "Choose specific apps"
    `TextLink` that reveals the two sections (starting `ranked` empty, `available` populated).
  - `onRankedChange` fires on every reorder/add/remove — no separate "Save" button, matching this
    screen's existing pattern of the `SortSheet`'s `onSelectSort` firing immediately
    (`dismissOnSelect = true` elsewhere); here `dismissOnSelect` does NOT apply since this sheet
    stays open across multiple reorder taps — only `onDismiss` (tapping outside / a "Done" button)
    closes it.

Replace all 8 `SortSheet(... showHealthSourceXSheet ...)` blocks (`WorkoutSettingsScreen.kt:733-812`)
with 8 `RankedSourceSheet(...)` calls wired to the new `xRanked` settings fields and the new
ViewModel setters from §1.7. Replace the 8 `SettingsRow`s' `subtitle` (`WorkoutSettingsScreen.kt:528-587`)
to render the ranked list, e.g.:

```kotlin
subtitle = settings.healthSourceStepsRanked
    ?.split(",")?.filter { it.isNotBlank() }
    ?.joinToString(" › ") { com.daybook.app.data.health.SourceAppLabels.labelFor(it) ?: it }
    ?.ifBlank { "Auto" } ?: "Auto"
```

`loadSourceCandidates` (`WorkoutSettingsViewModel.kt:125-128`) is unchanged — it already populates
`sourceCandidates` per `SourceField` on demand; the new sheet consumes the same `sourceCandidates[field]`.

### 1.9 Data source priority page keeps Hydration listed

Per the owner's explicit carve-out ("except in here... it stays listed as a health parameter for
sync purposes"), the "Hydration" row in `WorkoutSettingsScreen.kt`'s "Data source priority" section
is **unchanged in kind** by §2 below — it keeps controlling which Health Connect app Daybook reads
hydration FROM when aggregating `health_days.hydrationMl` (used by the Health tab's own hydration
tile, unaffected by §2). §2 is entirely about the WRITE side (Daybook → Health Connect via
`HealthConnectHydrationWriter`, one direction) and the new Intake reminder UI — it does not touch
this read-side row at all.

---

## §2. Hydration moves under Intake

### 2.1 Naming correction

The tab titled "Intake" in the UI is `FoodMedScreen.kt` (`ScreenHeader(title = "Intake", ...)` at
`app/src/main/java/com/daybook/app/ui/foodmed/FoodMedScreen.kt:64`), backed by `FoodMedViewModel`/
`FoodMedTask`/`FoodMedOccurrence` (table names `food_med_tasks`/`food_med_occurrences` — the
`FoodMed` naming is internal/legacy, the user-facing name really is "Intake"). This plan uses
"Intake" throughout, matching the owner's own wording.

### 2.2 Current hydration architecture (what's being replaced)

- `AppSettings.hydrationEnabled/hydrationGoalMl/hydrationUnit/hydrationEnabledSince`
  (`MIGRATION_30_31`, `Migrations.kt:857`) — stays; §2.6 adds one more field.
- `HydrationDay` (`app/src/main/java/com/daybook/app/data/model/HydrationModel.kt`) / `hydration_days`
  table / `HydrationDao` — **stays exactly as-is**: still the source of truth for "how many ml did I
  log today", still written by `HydrationRepository.setDay` (`HydrationRepository.kt:29-37`), still
  mirrored to Health Connect via `HealthConnectHydrationWriter.upsertDay` (unchanged — per the
  owner's explicit "don't change the HC write path").
- Today: `HomeViewModel` synthesizes a **fake `HomeItem`** for hydration every day it's active
  (`hydrationHomeItem`, `HomeViewModel.kt:134-163`), injected into the `homeItems` list
  (`combine(baseHomeItems, hydrationItem)`, `HomeViewModel.kt:491`) and rendered by a
  **visually-separate** `HydrationCard` composable (`HomeScreen.kt:253-262`) instead of the standard
  `ReminderCard` every other Intake/Habit occurrence uses. It also synthesizes a **fake streak
  habit occurrence** (`hydrationStreakOccurrences`, `HYDRATION_HABIT_ID = "builtin-hydration"`,
  `HomeViewModel.kt:165-192`) so it counts toward the **Habits** progress ring — this is exactly the
  "Beast-Mode-Health-adjacent, not really Intake" framing the owner wants removed.
- Settings: `HydrationSettingsSection` (`app/src/main/java/com/daybook/app/ui/settings/HydrationSettingsSection.kt`)
  is already mounted in the MAIN app Settings screen, not Beast Mode Settings
  (`SettingsScreen.kt:967`, inside the "Reminders & notifications" area) — **no relocation needed**,
  this already satisfies "main app Settings, not Beast Mode Settings". It currently has no reminder
  time control at all ("No reminders — the habit is logged from its card on Today", line 79's KDoc).

### 2.3 Design decision (hardest call — HydrationCard replaced or kept parallel)

**Decision: replaced.** Hydration becomes a genuine `FoodMedTask` (new `TaskType.HYDRATION`) with a
real `FoodMedOccurrence` per day, scheduled/notified through the exact same
`OccurrenceScheduler`/`AlarmReceiver`/`FoodMedOccurrenceDao` pipeline every other Intake reminder
uses, rendered through the same `ReminderCard` on Today and the same `FoodMedCard` in the Intake
tab. Only the tap-to-log interaction is special-cased (opens the existing liters dialog instead of
the generic "what did you have" text field).

Justification: the owner's own three asks (configurable reminder time via the existing
picker/scheduling pattern, appear in the Intake tab's list, appear on Today "same way other intake
items appear") are each already exactly what `OccurrenceScheduler` + `ReminderCard` do for every
other Intake item — the current `HydrationCard`/synthetic-`HomeItem` machinery was built BEFORE
Intake integration was wanted and duplicates (badly) what the real pipeline already does for free:
real reminder notifications with snooze, real appearance in `FoodMedScreen`'s list, real "Intake"
progress-ring counting (`foodRatio`, `HomeScreen.kt:196`) instead of miscounting into the Habits
ring. Keeping `HydrationCard` "visually matching but parallel" would mean maintaining two scheduling
systems (real occurrences for reminders/Intake-tab-visibility, PLUS the old synthetic-item plumbing
for Today) for zero benefit — the liters-entry dialog is the only genuinely hydration-specific UI,
and that can hang off a real occurrence exactly as easily as off a synthetic one.

**Consequence to flag explicitly**: hydration's daily "done" no longer counts toward the **Habits**
progress ring (`hydrationStreakOccurrences`/`HYDRATION_HABIT_ID` removed) — it now counts toward the
**Intake** ring instead (a completed/skipped `FoodMedOccurrence`, same as every other Intake item).
This is a deliberate, correct consequence of "hydration is an Intake concept now", not an oversight.

### 2.4 Schema change — MIGRATION_36_37 (DB v36 → v37)

`TaskType` (`DataModel.kt:461`, `enum class TaskType { FOOD, MED, CUSTOM, JOURNAL }`) is stored by
Room as its enum NAME (kotlinx `@Serializable` + the existing `Converters` for other by-name enums
in this file, e.g. `RedFlag`) — safe to APPEND a new case. Add `HYDRATION` at the end:

```kotlin
@Serializable enum class TaskType { FOOD, MED, CUSTOM, JOURNAL, HYDRATION }
```

New `app_settings` columns (add to `AppSettings` data class near the existing `hydration*` fields,
`DataModel.kt` — search `hydration_enabled_since` to find them):

```kotlin
/** Owner feedback round 3 (2026-09-20) — the single reminder time ("HH:mm") for the system-managed
 *  Hydration Intake reminder. Default 21:00 IST is applied by MIGRATION_36_37's seed UPDATE and by
 *  this @ColumnInfo default for a fresh install; IST is a fixed default choice (the owner's own
 *  spec), NOT derived from the device's actual timezone — see this plan's §2.5 assumption. */
@ColumnInfo(name = "hydration_reminder_time", defaultValue = "21:00") val hydrationReminderTime: String = "21:00",
/** The `food_med_tasks.id` of the system-managed Hydration Intake reminder, once created (lazily,
 *  the first time hydration is enabled after this round ships — see HydrationIntakeSync). Null
 *  before that/if hydration has never been enabled since this round. Lets every later toggle find
 *  and update (not duplicate) the same row. */
@ColumnInfo(name = "hydration_task_id") val hydrationTaskId: String? = null,
```

`Migrations.kt`, after `MIGRATION_35_36`:

```kotlin
/**
 * Owner feedback round 3 (2026-09-20) — Hydration-as-Intake: two additive `app_settings` columns
 * (`hydration_reminder_time`, default "21:00" = 9pm IST per the owner's spec; `hydration_task_id`,
 * null until the system-managed Hydration `food_med_tasks` row is first created — see
 * HydrationIntakeSync's KDoc). `TaskType.HYDRATION` itself needs no SQL change (Room stores the
 * enum by name in the existing `food_med_tasks.type` TEXT column). Nothing existing is read or
 * rewritten.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN hydration_reminder_time TEXT NOT NULL DEFAULT '21:00'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN hydration_task_id TEXT")
    }
}
```

Append to `ALL_MIGRATIONS`; bump `AppDatabase.kt` to `version = 37`.

**Assumption**: "9pm IST" is stored as a plain local "21:00" wall-clock default (exactly like every
other reminder time in this app, e.g. `habit_checkin_time` default "21:00" at `DataModel.kt:195`,
and every `FoodMedTask.timesJson` entry) — i.e. it fires at 9pm in the device's OWN timezone, not a
literal Asia/Kolkata offset computed for other timezones. This matches how every other time-of-day
setting in this codebase already works (no existing precedent for a timezone-pinned reminder), and
"9pm IST" is almost certainly just how the owner (based in India) describes "9pm" — flagged here as
the one place this reading could be wrong.

### 2.5 `HydrationIntakeSync` — creating/updating the system-managed task

New file `app/src/main/java/com/daybook/app/data/hydration/HydrationIntakeSync.kt`:

```kotlin
package com.daybook.app.data.hydration

import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.TaskType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owner feedback round 3 (2026-09-20) — hydration's reminder is a real, system-managed
 * `food_med_tasks` row (TaskType.HYDRATION) so it flows through the exact same
 * OccurrenceScheduler/AlarmReceiver/Intake-tab pipeline as every other Intake reminder, instead of
 * the old synthetic-HomeItem-only approach (see SOURCE_PRIORITY_AND_HYDRATION_PLAN's §2.3).
 * Idempotent: `ensureTaskFor(enabled, timeHHmm)` creates the row once (recording its id in
 * `app_settings.hydration_task_id`) and thereafter only updates `times_json`/archival state — it
 * never creates a second row. The label/icon/color are fixed (not user-editable) since this is a
 * single always-the-same reminder, not a general-purpose Intake item.
 */
@Singleton
class HydrationIntakeSync @Inject constructor(
    private val database: AppDatabase,
    private val settingsRepository: AppSettingsRepository,
    private val scheduler: OccurrenceScheduler
) {
    /** Called whenever hydration is toggled on/off or its reminder time changes (see
     *  HydrationSettingsViewModel). [enabled]=false archives the task (stops reminders, keeps the
     *  row + its history for when re-enabled) rather than deleting it. */
    suspend fun sync(enabled: Boolean, timeHHmm: String) {
        val settings = settingsRepository.getSettings()
        val existingId = settings.hydrationTaskId
        val existing = existingId?.let { database.foodMedTaskDao().getTaskById(it) }
        val timesJson = "[\"$timeHHmm\"]" // matches FoodMedTask.timesJson's existing JSON-array-of-"HH:mm" shape
        if (existing == null) {
            if (!enabled) return // never created, and turning on is what creates it — nothing to do
            val task = FoodMedTask(
                label = "Hydration",
                type = TaskType.HYDRATION,
                iconKey = "water_drop", // pick the closest existing Icons key — verify against com.daybook.app.ui.icons.Icons's registry
                timesJson = timesJson,
                activeDaysJson = "", // "" = every day, matching every other daily FoodMedTask default
                isArchived = false
            )
            database.foodMedTaskDao().insert(task)
            settingsRepository.setHydrationTaskId(task.id)
            scheduler.syncTask(task.id)
        } else {
            val updated = existing.copy(timesJson = timesJson, isArchived = !enabled)
            database.foodMedTaskDao().update(updated)
            if (enabled) scheduler.syncTask(updated.id) else scheduler.cancelTask(updated.id)
        }
    }
}
```

(Verify exact `FoodMedTaskDao` method names — `getTaskById`/`insert`/`update` — against
`app/src/main/java/com/daybook/app/data/local/FoodMedTaskDao.kt`; adjust to match if named
differently, e.g. `upsert` instead of separate `insert`/`update`.)

Add `setHydrationTaskId(id: String)` and `setHydrationReminderTime(hhmm: String)` to
`AppSettingsRepository` (same shape as `setHydrationGoalMl` at `AppSettingsRepository.kt:147`) and
their `AppSettingsDao` `@Query("UPDATE app_settings SET hydration_task_id = :v WHERE id = 1")`
counterparts.

**Call sites**: `HydrationSettingsViewModel.setEnabled(enabled: Boolean)`
(`HydrationSettingsSection.kt:71`) and a new `setReminderTime(hhmm: String)` method both call
`hydrationIntakeSync.sync(...)` after saving the settings row (inject `HydrationIntakeSync` into
`HydrationSettingsViewModel`'s constructor).

### 2.6 Intake tab rendering — `FoodMedScreen`/`FoodMedViewModel`

`FoodMedItem` (the UI model `FoodMedCard` renders, distinct from the `FoodMedTask` entity — check
`FoodMedViewModel.kt` for its exact mapping function, likely named `toFoodMedItem`/similar) needs no
new fields for TaskType.HYDRATION to simply LIST correctly — it already derives from any
`FoodMedTask`/its occurrences generically. Two adjustments:

1. **Icon/label**: `TaskType.HYDRATION`'s task already carries `label = "Hydration"` and a water-drop
   `iconKey` (§2.5) — `FoodMedCard` renders these generically, no extra branching needed for the
   list view itself.
2. **Tap-to-log**: wherever `FoodMedScreen`/its detail flow opens the generic "what did you have"
   response entry (find this by tracing `onNavigateToDetail`/`onOpenDetail` from `FoodMedCard.kt:98`
   through to whatever screen calls `OccurrenceScheduler.logFoodMed`), branch on
   `item.type == TaskType.HYDRATION`(or the occurrence's task's type) to instead show the SAME
   liters-entry dialog `HydrationCard` uses today (`HydrationCard.kt:103-156`) — extract that dialog
   body into a shared composable `HydrationAmountDialog(unit, goalMl, currentMl, onSave, onDismiss)`
   reusable from both the Intake detail flow and Today's `ReminderCard` (§2.7), instead of copying
   the dialog code twice.
   - On save, call `HydrationRepository.setDay(date, amountMl)` (unchanged — still writes
     `hydration_days` + Health Connect) AND resolve the day's `FoodMedOccurrence` via
     `OccurrenceScheduler.logFoodMed(occurrenceId, text = HydrationUnits.format(amountMl, unit))` so
     the occurrence shows COMPLETED (same as any other logged Intake item) — `text` is a
     human-readable summary stored in `response_text`, not re-parsed for anything.
   - `FoodMedTask.type == HYDRATION` should also suppress the FOOD-only extra fields (red flag /
     suspected food / outside food) in whatever edit/log form currently conditions on
     `type == TaskType.FOOD` — grep that form for `TaskType.FOOD` checks and add
     `TaskType.HYDRATION` to any "hide these FOOD-only rows" condition, and to any "hide the
     Edit-task screen's schedule/label fields since this is system-managed" condition (a
     HYDRATION task's label/icon/times are set only via `HydrationIntakeSync`, not the general
     Add/Edit Intake form — hide or disable the Edit action for a HYDRATION `FoodMedItem`
     specifically, redirecting "Edit" to the Settings hydration section instead, or simply omitting
     the Edit action from its `BottomSheetMenu` in `FoodMedCard`).

### 2.7 Today (`HomeScreen`/`HomeViewModel`) — remove the synthetic item, let the real occurrence flow through

- Delete the synthetic-hydration machinery: `HydrationUi`, `hydrationActiveOn`,
  `hydrationHomeItem`, `hydrationStreakOccurrences`, `HYDRATION_HABIT_ID`
  (`HomeViewModel.kt:115-192`), the `hydrationItem`/`hydrationCfg` flows and their `combine(...)`
  into `baseHomeItems`/streak computation (`HomeViewModel.kt:470-491`, `591-602`), `setHydration`/
  `setHydrationUnit` (`HomeViewModel.kt:496-508`, now redundant with §2.6's dialog calling
  `HydrationRepository`/`OccurrenceScheduler` directly — but keep `HydrationRepository.setDay`
  itself, only the HomeViewModel wrapper goes), and the `_hydrationTick`
  cloud-sync-residency-retry plumbing (`HomeViewModel.kt:322`, `360`, `550-566`) UNLESS
  `hydration_days` residency-retry is still needed for the Health tab's own hydration tile display
  — check whether anything else in `HomeViewModel`/the Health tab still calls `hydrationRepository
  .observeDay`/`observeRange` before deleting the tick plumbing; if so, keep `_hydrationTick` and
  only delete the `HomeItem`-synthesis parts.
- `HomeScreen.kt:253-262`'s `if (item.hydration != null) { HydrationCard(...) }` branch is deleted —
  a HYDRATION occurrence now arrives as an ordinary item in `reminderSections`/`visibleItems`
  (sourced from the real `food_med_occurrences` table, same as every other Intake reminder) and
  renders through the existing `ReminderCard(...)` call right below it (`HomeScreen.kt:262` onward)
  with NO special-casing needed there — UNLESS `ReminderCard`'s tap action needs to open the liters
  dialog (§2.6's `HydrationAmountDialog`) instead of its default action. Check `ReminderCard.kt`'s
  tap handler: if it already delegates "what happens on tap" up to the caller via a callback,
  branch in `HomeScreen.kt` on `item.taskType == TaskType.HYDRATION`(add a `taskType: TaskType?`
  field to `HomeItem` if it doesn't already expose one) to open `HydrationAmountDialog` instead of
  the default log-entry sheet; if `ReminderCard` hardcodes its own navigation, add the same
  `TaskType.HYDRATION` branch there.
- `HydrationCard.kt` itself: delete the file (its dialog body is preserved as
  `HydrationAmountDialog`, extracted per §2.6, likely into a new shared file e.g.
  `app/src/main/java/com/daybook/app/ui/components/HydrationAmountDialog.kt` since it's now used
  from two screens).

### 2.8 Settings UI — reminder time control

In `HydrationSettingsSection.kt`, inside the `if (s.hydrationEnabled) { ... }` block (currently
starts at line 107), add a new row using this app's existing time-picker pattern (`TimePickerDialog`,
already used elsewhere in `SettingsScreen.kt` for quiet hours — see `showQhStart`/`TimePickerDialog`
at `SettingsScreen.kt:934-947` for the exact call shape to copy):

```kotlin
Spacer(Modifier.height(Spacing.md))
HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
Spacer(Modifier.height(Spacing.md))
var showTimePicker by rememberSaveable { mutableStateOf(false) }
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
        Text("Reminder time", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
        Text("Appears in Intake and Today at this time each day.", style = DaybookText.Metadata, color = DaybookColors.TextMuted)
    }
    TextLink(
        text = DateTimeUtils.formatTime(DateTimeUtils.stringToTime(s.hydrationReminderTime), clock24h),
        onClick = { showTimePicker = true }
    )
}
if (showTimePicker) {
    TimePickerDialog(
        initial = DateTimeUtils.stringToTime(s.hydrationReminderTime),
        onDismiss = { showTimePicker = false },
        onConfirm = { t -> viewModel.setReminderTime(DateTimeUtils.timeToString(t)); showTimePicker = false },
        force24h = clock24h
    )
}
```
(Confirm exact `DateTimeUtils` function names — `stringToTime`/`timeToString`/`formatTime` — against
usage at `SettingsScreen.kt:930-947`; `clock24h` needs to be threaded into
`HydrationSettingsSection`'s parameters or read from `viewModel.settings` the same way the rest of
that screen does — check how the enclosing `SettingsScreen.kt` scope already has `clock24h`
available and pass it down as a parameter, matching the existing `QuietTimeRow` call convention.)

`HydrationSettingsViewModel` — add:
```kotlin
fun setReminderTime(hhmm: String) = safeLaunch {
    settingsRepository.setHydrationReminderTime(hhmm)
    hydrationIntakeSync.sync(settingsRepository.getSettings().hydrationEnabled, hhmm)
}
```
and update `setEnabled` (`HydrationSettingsSection.kt:71`) to also call
`hydrationIntakeSync.sync(enabled, settingsRepository.getSettings().hydrationReminderTime)` after
`settingsRepository.setEnabled(enabled)`.

### 2.9 Reminder notification content / `AlarmReceiver`

No changes needed to `AlarmReceiver.kt` itself — it already renders a `FoodMedTask`'s
`label`/`promptMessage` generically for its notification content regardless of `type` (verify this
by checking whether `AlarmReceiver.kt` branches on `TaskType` anywhere; if it has FOOD/MED-specific
copy, add a HYDRATION case using copy like "Time to log your water"). The existing "Reply" inline
notification action (`logFoodMedFromNotificationReply`, `OccurrenceScheduler.kt:605-610`) should be
**disabled for HYDRATION** (a free-text reply doesn't make sense for a liters amount) — check
whatever builds the notification's action set (likely also in `AlarmReceiver.kt` or a
`NotificationUtils`) and skip the inline-reply action when `task.type == TaskType.HYDRATION`,
leaving just "Snooze"/tap-to-open (which opens the app to the Intake/Today card's own
`HydrationAmountDialog`).

---

## §3. Workout session photos → Firebase Storage backup

### 3.1 Current state

- `app/src/main/java/com/daybook/app/data/WorkoutPhotoStore.kt` — local-only, owns up to 3 photos
  per session under `filesDir/workout_photos/`, already downscaled to ≤1024px edge / JPEG quality 90
  (~150-300KB) by `persistCapturedFile`.
- `WorkoutSessionPhoto` (`app/src/main/java/com/daybook/app/data/model/WorkoutPhotoModel.kt:27-33`)
  — `id`, `session_id`, `file_path`, `order_index`, `created_at`. No cloud-related column exists yet.
- `WorkoutPhotoDao.kt` — full CRUD, cleanup hooks (`deleteForSessions`, `filePathsForSessions`,
  `sweepOrphans`'s caller).
- `CloudSyncRepository.kt` — the existing Firestore sync engine; `currentUid` (`CloudSyncRepository.kt:150`)
  populated from `AuthRepository.state`'s `AuthState.SignedIn`/`SignedOut` (collected at
  `CloudSyncRepository.kt:223`); `syncState.pendingPush` + `SyncFlushWorker`
  (`app/src/main/java/com/daybook/app/util/work/SyncFlushWorker.kt`) is the existing
  "queue-and-retry-on-reconnect" pattern for Firestore writes (`SyncFlushWorker.enqueue`, a
  `NetworkType.CONNECTED`-constrained, linearly-backed-off `WorkManager` `OneTimeWorkRequest`).
- `app/build.gradle.kts:196-201` already has `firebase-bom:33.1.2` + `firebase-auth` +
  `firebase-firestore` + `firebase-crashlytics`; **no `firebase-storage` dependency yet**.
- `firebase.json` (repo root) has `auth`/`firestore` blocks; **no `storage` block, no
  `storage.rules` file** anywhere in the repo (confirmed via `find`).

### 3.2 Gradle dependency

`app/build.gradle.kts`, right after the `firebase-firestore` line (`:199`):
```kotlin
implementation("com.google.firebase:firebase-storage")
```
(version comes from the existing `firebase-bom:33.1.2` platform BOM already applied at line 197 —
no separate version string needed, matching how `firebase-auth`/`firebase-firestore` are declared.)

### 3.3 Schema change — MIGRATION_37_38 (DB v37 → v38)

Add to `WorkoutSessionPhoto` (`WorkoutPhotoModel.kt`):
```kotlin
/** Firebase Storage backup round (2026-09-20) — the `users/{uid}/workout_photos/{sessionId}/
 *  {filename}` Storage path once uploaded, null until the upload succeeds. Used to (a) skip
 *  re-uploading a photo that's already backed up, (b) rehydrate the local file if it's ever
 *  missing (reinstall / second device) — see WorkoutPhotoCloudSync's KDoc. */
@ColumnInfo(name = "cloud_path") val cloudPath: String? = null,
/** Non-null once a Storage upload has actually succeeded; null means "not yet uploaded" —
 *  distinct from cloudPath so a partially-written row (rare, but Room upserts aren't atomic with
 *  the network call) can't be misread as "done". */
@ColumnInfo(name = "cloud_uploaded_at") val cloudUploadedAt: Long? = null
```

`Migrations.kt`, after `MIGRATION_36_37`:
```kotlin
/**
 * Firebase Storage backup round (2026-09-20) — two additive `workout_session_photos` columns:
 * `cloud_path` (the Storage object path once uploaded) and `cloud_uploaded_at` (upload timestamp,
 * null = not yet backed up). Every existing row starts as "not yet uploaded", which is correct —
 * WorkoutPhotoCloudSync's startup sweep (see its KDoc) picks up every pre-existing local photo and
 * queues it exactly like a brand-new one. Nothing existing is read or rewritten.
 */
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_session_photos ADD COLUMN cloud_path TEXT")
        db.execSQL("ALTER TABLE workout_session_photos ADD COLUMN cloud_uploaded_at INTEGER")
    }
}
```
Append to `ALL_MIGRATIONS`; bump `AppDatabase.kt` to `version = 38` (final version for this build,
after §1's 36 and §2's 37).

`WorkoutPhotoDao.kt` — add:
```kotlin
@Query("UPDATE workout_session_photos SET cloud_path = :cloudPath, cloud_uploaded_at = :uploadedAt WHERE id = :id")
suspend fun markUploaded(id: String, cloudPath: String, uploadedAt: Long)

@Query("SELECT * FROM workout_session_photos WHERE cloud_uploaded_at IS NULL")
suspend fun getPendingUpload(): List<WorkoutSessionPhoto>
```

### 3.4 Upload path — mirror `SyncFlushWorker`'s queue-and-retry pattern

**Assumption**: build a small, self-contained `WorkoutPhotoUploadWorker` mirroring
`SyncFlushWorker` exactly (same `NetworkType.CONNECTED` constraint, same linear backoff), rather
than folding Storage uploads into `CloudSyncRepository`'s own Firestore-specific state machine —
`CloudSyncRepository` is already a 1600+ line file whose `pendingPush`/dirty-counter machinery is
purpose-built for Firestore document diffing, not binary blob uploads; a parallel, equally-simple
worker scoped to exactly one job (upload photos not yet uploaded) is lower-risk and easier to
verify than threading a second concern through that file. `WorkoutPhotoDao.getPendingUpload()`
(§3.3) IS the queue — no separate in-memory/pref-backed queue needed, Room's `cloud_uploaded_at IS
NULL` already durably tracks "not yet done" across process death, matching what
`syncState.pendingPush` does for Firestore.

New file `app/src/main/java/com/daybook/app/data/WorkoutPhotoCloudSync.kt`:

```kotlin
package com.daybook.app.data

import android.net.Uri
import android.util.Log
import com.daybook.app.data.auth.AuthRepository
import com.daybook.app.data.auth.AuthState
import com.daybook.app.data.local.AppDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Firebase Storage backup round (2026-09-20) — uploads each locally-downscaled workout session
 * photo (already ≤1024px/~150-300KB via WorkoutPhotoStore, re-uploaded AS-IS, never re-compressed)
 * to `users/{uid}/workout_photos/{sessionId}/{filename}`, once per photo, best-effort. Room's
 * `workout_session_photos.cloud_uploaded_at IS NULL` IS the durable upload queue — no separate
 * queue needed (mirrors how `SyncFlushWorker`/`syncState.pendingPush` track a pending Firestore
 * push). A signed-out user or no connectivity simply leaves rows pending; WorkoutPhotoUploadWorker
 * (network-constrained, WorkManager) retries later — never fails the local save, matching this
 * app's "local Room write always wins, cloud is best-effort" convention throughout
 * CloudSyncRepository.
 */
@Singleton
class WorkoutPhotoCloudSync @Inject constructor(
    private val database: AppDatabase,
    private val auth: AuthRepository,
    private val storage: FirebaseStorage
) {
    /** Called after a photo is persisted locally (WorkoutRepository's session-save path — see
     *  this plan's §3.4 call-site note) AND by [WorkoutPhotoUploadWorker]'s sweep. Uploads every
     *  currently-pending photo; a per-photo failure (offline, denied, etc.) just leaves that row
     *  pending for the next attempt — never throws out of this function. */
    suspend fun uploadPending(): Boolean {
        val uid = (auth.state.first { it !is AuthState.Loading } as? AuthState.SignedIn)?.uid ?: return false
        val pending = database.workoutPhotoDao().getPendingUpload()
        var allSucceeded = true
        for (photo in pending) {
            val file = File(photo.filePath)
            if (!file.exists()) continue // local file already gone — nothing to back up; leave row as-is
            val cloudPath = "users/$uid/workout_photos/${photo.sessionId}/${file.name}"
            val ok = runCatching {
                storage.reference.child(cloudPath).putFile(Uri.fromFile(file)).await()
                database.workoutPhotoDao().markUploaded(photo.id, cloudPath, System.currentTimeMillis())
            }.isSuccess
            if (!ok) allSucceeded = false
        }
        return allSucceeded
    }

    /** §3.5 rehydrate path — see its own section. */
    suspend fun rehydrateIfMissing(photo: com.daybook.app.data.model.WorkoutSessionPhoto): String? {
        if (File(photo.filePath).exists()) return photo.filePath
        val cloudPath = photo.cloudPath ?: return null
        return runCatching {
            val target = File(photo.filePath)
            target.parentFile?.mkdirs()
            storage.reference.child(cloudPath).getFile(target).await()
            target.absolutePath
        }.getOrNull()
    }

    companion object { private const val TAG = "WorkoutPhotoCloudSync" }
}
```

(`.await()` needs `kotlinx-coroutines-play-services` — check `app/build.gradle.kts` for whether
that's already a dependency, e.g. via other Firebase Task-returning calls elsewhere in
`CloudSyncRepository.kt`; if not present, add
`implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:<version matching the
project's existing coroutines version>")`, or use `Tasks.await(task)` inside `withContext
(Dispatchers.IO)` instead if the project avoids that dependency elsewhere — check
`CloudSyncRepository.kt`'s own Firestore `Task` handling for the precedent it already follows and
match it exactly.)

Provide `FirebaseStorage` via whatever Hilt module already provides `FirebaseAuth`/
`FirebaseFirestore` (find it — likely `app/src/main/java/com/daybook/app/di/FirebaseModule.kt`) —
add a `@Provides @Singleton fun provideFirebaseStorage(): FirebaseStorage = Firebase.storage`.

**Call site**: `WorkoutRepository.kt` — wherever a session's photos are finalized/attached after
`WorkoutPhotoStore.persistCapturedFile` (`WorkoutPhotoStore.kt:88-115`) returns a path and the
`WorkoutSessionPhoto` row is inserted (grep `WorkoutPhotoDao().insert` / `.insertAll` call sites in
`WorkoutRepository.kt`), immediately after that insert, fire-and-forget launch
`workoutPhotoCloudSync.uploadPending()` on the repository's own background scope (mirror
`HealthRepository.repoScope`'s `SupervisorJob() + Dispatchers.IO` pattern, `HealthRepository.kt:112`,
if `WorkoutRepository` doesn't already have an equivalent scope) — this covers the common
"signed-in and online" case immediately; the Worker (below) is the fallback for offline/signed-out.

New file `app/src/main/java/com/daybook/app/util/work/WorkoutPhotoUploadWorker.kt`, mirroring
`SyncFlushWorker.kt` exactly:
```kotlin
@HiltWorker
class WorkoutPhotoUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val photoCloudSync: com.daybook.app.data.WorkoutPhotoCloudSync
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        if (photoCloudSync.uploadPending()) Result.success() else Result.retry()
    } catch (t: Throwable) {
        Log.e(TAG, "workout photo upload failed", t)
        Result.retry()
    }
    companion object {
        private const val TAG = "WorkoutPhotoUploadWorker"
        private const val UNIQUE_NAME = "daybook-workout-photo-upload"
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WorkoutPhotoUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
```
Enqueue it: (a) from the same session-save call site as the fire-and-forget launch above (belt and
braces — covers the case the in-process launch got killed mid-upload); (b) from `AuthRepository`'s
sign-in transition (a user who captured photos while signed out, then signs in, should have their
backlog uploaded — check how `CloudSyncRepository.onAuthState` reacts to a fresh sign-in,
`CloudSyncRepository.kt:247-262`, and add `WorkoutPhotoUploadWorker.enqueue(context)` alongside
whatever it already does there, OR call it from wherever `AuthRepository`'s sign-in success path
already lives if that's cleaner); (c) optionally on `ON_STOP`/app-background like `SyncFlushWorker`
if `WorkoutRepository` has an equivalent lifecycle hook — not required for correctness since the
Worker is idempotent and safe to enqueue redundantly (`ExistingWorkPolicy.REPLACE`).

### 3.5 Rehydrate path

**Decision**: check at **session load time** (a workout history detail screen reading a session's
photos), not a separate explicit "restore photos" action — simplest for the user (nothing to
discover/trigger manually) and cheap (`File.exists()` is a local stat call, only the miss case ever
touches the network).

Wherever the Workout History detail screen currently reads `WorkoutPhotoDao.getForSession`/
`getForSessions` and maps `file_path` straight to an `AsyncImage`/`Coil` request (grep for
`filePath` consumers in the workout history UI, likely `WorkoutHistoryViewModel.kt` or a session
detail screen under `app/src/main/java/com/daybook/app/ui/workout/`), change that mapping to call
`workoutPhotoCloudSync.rehydrateIfMissing(photo)` first and use ITS returned path (falling back to
the original `photo.filePath` if rehydration also fails/returns null, so a broken image is at worst
the same as today's behavior, never worse). Do this on a background dispatcher before emitting to
the UI state, not inline in Compose. **Per §3's own point (4)**: this must stay display-triggered
and lazy — do NOT eagerly pre-fetch every session's photos on app start or on the history list
screen (only the list) — only when a specific session's DETAIL view (where individual photos are
actually rendered) is opened.

### 3.6 Firebase Storage security rules

No `storage.rules` exists yet. Create `storage.rules` at the repo root (sibling to the existing
`firestore.rules`):

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{uid}/workout_photos/{sessionId}/{fileName} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

Add a `"storage"` block to `firebase.json` (sibling to the existing `"firestore"` block):
```json
"storage": {
  "rules": "storage.rules"
}
```

**Call-out (do not silently assume this is deployed)**: creating these two files does **NOT**
activate the rules — they must be pushed with `firebase deploy --only storage`, which requires
Firebase CLI login/deploy permissions the implementing agent may not have in this environment. Flag
this explicitly to whoever runs the build: until deployed, the default Firebase Storage rule set
(`allow read, write: if false` for a fresh bucket, or whatever the current default is) governs —
uploads will likely fail with a permission error until a human with deploy access runs that command.
This is a deploy step, separate from and not blocked by the Gradle app build.

### 3.7 Spark/free-tier note (non-blocking, informational only)

Firebase Storage's Spark (free) tier caps at 5GB total stored and 1GB/day downloaded. At ~150-300KB
per photo, ≤3 photos/session, this is roughly 6,000-11,000 sessions' worth of photos before hitting
the storage cap, and the rehydrate-on-miss design (§3.5, only touches network on a local cache miss)
keeps day-to-day download volume minimal. Not something to design around now — noted per the
coordinator's instruction, not a blocking constraint.

---

## Build/verify checklist

- [ ] `./gradlew :app:compileDebugKotlin` (or full `assembleDebug`) succeeds with the three new
      migrations (`MIGRATION_35_36`, `MIGRATION_36_37`, `MIGRATION_37_38`) all appended to
      `ALL_MIGRATIONS` and `AppDatabase`'s `version` bumped to `38`.
- [ ] Instrumented `MigrationTest.fullOpenAtLatestVersion()` (referenced in `Migrations.kt`'s own
      KDoc at line ~967) passes — this is the one test that catches a migration missing from
      `ALL_MIGRATIONS`.
- [ ] Manual: open Settings → Beast Mode → "Data source priority" → tap a metric row → the new
      ranked-list sheet opens, shows Auto/available/ranked sections, reorder up/down works, removing
      an entry works.
- [ ] Manual: set a two-item ranked list for one metric (e.g. Steps: [Google Fit, Mi Fitness]),
      confirm the busy indicator shows, confirm the status caption reads
      "Source priority applied to your whole history." (§1.6/1.7), and confirm a day in the Health
      tab that only had Mi Fitness data (no Google Fit that day) now shows the Mi Fitness figure
      instead of blank/zero.
- [ ] Manual: confirm `health_days`/`health_sessions`/`health_weight_readings` are the ONLY tables
      touched by the re-resolution pass (no workout logs, no other settings changed) — e.g. check a
      workout session's `updated_at`/row count is unaffected before/after triggering §1.6.
- [ ] Manual: toggle "Track hydration" on in Settings → confirm a "Hydration" reminder appears in
      the Intake tab's list and on Today at the default 21:00 slot; change the reminder time in
      Settings → confirm both the Intake-tab row and the next alarm reflect the new time.
- [ ] Manual: tap the Hydration reminder on Today and in Intake → confirm the SAME liters-entry
      dialog opens in both places, saving updates `hydration_days`, mirrors to Health Connect
      (unchanged write path — confirm via the existing Health Connect app), and marks the
      occurrence COMPLETED (shows up in Intake's completed state, contributes to the Intake progress
      ring, not the Habits ring).
- [ ] Manual: confirm `WorkoutSettingsScreen`'s "Data source priority" page still lists "Hydration"
      as a health-source row (§1.9 — unaffected by §2).
- [ ] Manual: confirm `HydrationCard.kt` is fully removed and nothing still references it (compile
      error would catch this, but also grep for `HydrationCard(` before considering this done).
- [ ] Manual: capture a workout session photo → confirm it appears in
      `workout_session_photos` with `cloud_uploaded_at` populated shortly after (while signed in and
      online) — check via `adb shell` Room inspection or a temporary log line.
- [ ] Manual: go offline, capture a photo, confirm the row stays `cloud_uploaded_at IS NULL`; go
      back online, confirm `WorkoutPhotoUploadWorker` (or the app-resume path) uploads it within a
      reasonable window without any explicit user action.
- [ ] Manual: delete a session's local photo file on disk (simulating a reinstall) while its Room
      row + `cloud_path` still exist, reopen that session's detail view, confirm the photo
      re-appears (rehydrated from Storage) instead of a broken image.
- [ ] Confirm `firebase-storage` resolves cleanly against the pinned `firebase-bom:33.1.2` (no
      version conflicts with `firebase-auth`/`firebase-firestore`/`firebase-appdistribution`'s own
      pinned transitive deps, per the existing comment block at `app/build.gradle.kts:203-207`).
- [ ] **Deploy call-out**: `storage.rules`/`firebase.json`'s new `"storage"` block are created by
      this plan but NOT deployed by it — a human with Firebase CLI deploy access must run
      `firebase deploy --only storage` before production uploads will actually be authorized; note
      this to whoever ships the build.
