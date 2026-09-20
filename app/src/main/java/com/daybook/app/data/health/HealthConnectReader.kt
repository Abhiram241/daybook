package com.daybook.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.daybook.app.util.runCatchingCancellable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * B3 (§7.3) — the ONLY file (besides [HealthPermissions] and [HealthConnectAvailability]) that
 * imports `androidx.health.connect.*`. Aggregates only (C7/§6.1.9 — never a raw per-sample series)
 * except for session reads, which are inherently per-record. Every public function is
 * `runCatchingCancellable`-wrapped at the call site in [com.daybook.app.data.HealthRepository], not here —
 * this class is a thin, honest translation layer that lets exceptions propagate so the caller
 * decides how to classify/surface them (§6.2's ladder).
 *
 * Provided (not `@Inject constructor`-provided) by `DatabaseModule.provideHealthConnectReader` —
 * a plain `Context`-holding class, no Hilt qualifier needed.
 */
class HealthConnectReader(private val context: Context) {

    private fun client(): HealthConnectClient =
        HealthConnectAvailability.client(context) ?: error("Health Connect is not available")

    /**
     * `AggregationResult.get`'s Java signature is `<T> T get(AggregateMetric<? extends T>)` — a
     * wildcard-bounded type parameter with no other usage site for Kotlin to anchor inference on,
     * which resolves to a bare `Any!` platform type at the call site rather than the metric's real
     * value type (e.g. `Length`, `Energy`), and every `.inMeters`/`.inKilocalories` access on it then
     * fails to resolve. Routing every read through this Kotlin-generic wrapper (where `T` is bound
     * directly by the [metric] parameter's own declared type, not by a Java wildcard) restores
     * normal inference.
     */
    private fun <T : Any> AggregationResult.valueOrNull(metric: AggregateMetric<T>): T? =
        if (contains(metric)) get(metric) else null

    suspend fun grantedPermissions(): Set<String> = client().permissionController.getGrantedPermissions()

    /** One aggregate call per day, covering every MVP metric that is aggregate-shaped (everything
     *  except sessions and nutrition-source-app, handled by [dayAggregate]'s caller). */
    data class DayAggregate(
        val steps: Long?,
        val distanceMeters: Double?,
        val activeCalories: Double?,
        val totalCalories: Double?,
        val avgHeartRate: Long?,
        val minHeartRate: Long?,
        val maxHeartRate: Long?,
        val restingHeartRate: Long?,
        val spo2Percent: Double?,
        val spo2MinPercent: Double?,
        val spo2MaxPercent: Double?,
        val weightKg: Double?,
        val weightReadings: List<WeightReading>,
        val hydrationMl: Double?,
        val nutritionCalories: Double?,
        val nutritionProteinGrams: Double?,
        val nutritionCarbsGrams: Double?,
        val nutritionFatGrams: Double?,
        val nutritionSourceApp: String?,
        // UI fix (owner review of the Health tab screenshot, build 50) — "which app/device wrote
        // this" for every tile that isn't already source-attributed (session/nutrition/weight all
        // already carry `sourceApp`/`dataOrigin` per record). `AggregationResult.dataOrigins` is
        // the set of apps that contributed to the combined aggregate (steps/distance/calories/
        // heart rate/hydration), unioned with SpO2's own raw-record origins below — no extra
        // Health Connect call, both were already being read.
        val activitySourceApps: List<String>
    )

    /** One raw `WeightRecord` reading, HEALTH_VITALS_RICHNESS_PLAN.md §1/§3. */
    data class WeightReading(val id: String, val atMillis: Long, val weightKg: Double, val sourceApp: String?)

    /** Reads one calendar day's aggregate across every MVP metric. Owner feedback round 2
     *  (2026-09-20) — each data point (Steps/Distance/Calories/Heart rate/Hydration) can now have
     *  its own resolved source preference, so the old single combined [AggregateRequest] is no
     *  longer always possible. Metrics are grouped by their *resolved* source package and one
     *  `aggregate()` call is issued per distinct group — when every metric shares the same
     *  preference (Auto, or the user picked one app for everything, the common case) this is still
     *  exactly ONE call, matching the original design's battery-cost reasoning; only a user who
     *  deliberately splits sources across metrics pays for more than one. Metrics the OS has
     *  nothing for are simply absent from the result (`AggregationResult.contains`), which is
     *  exactly §7.4's per-metric hide rule's data source. */
    suspend fun dayAggregate(
        date: LocalDate,
        zoneId: ZoneId,
        granted: Set<String>,
        // Owner feedback round 3 (2026-09-20) — ranked, ordered candidate list per data point (rank
        // 0 = most preferred). Empty list = old "no restriction / Auto with nothing observed"
        // behavior. Candidates are tried in order, per metric, stopping at the first one that
        // actually has data for this specific day — see class KDoc.
        stepsRanked: List<String> = emptyList(),
        distanceRanked: List<String> = emptyList(),
        caloriesRanked: List<String> = emptyList(),
        heartRateRanked: List<String> = emptyList(),
        spo2Ranked: List<String> = emptyList(),
        hydrationRanked: List<String> = emptyList(),
        weightRanked: List<String> = emptyList(),
        nutritionRanked: List<String> = emptyList()
    ): DayAggregate {
        val start = date.atStartOfDay(zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val filter = TimeRangeFilter.between(start, end)
        // H2 fix — `aggregate()` requires read permission for EVERY record type in the metric set
        // or it throws `SecurityException` for the whole call, not a partial result. Build the
        // metric set from only what's actually granted so an ordinary partial grant degrades to
        // "that family of metrics is absent" instead of failing every metric. Owner feedback round
        // 3 — each spec now carries its RANKED candidate list; metrics sharing the exact same list
        // are still batched into the same calls (one call per rank tried, not per metric per rank).
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
        // Group metrics that share the exact same ranked list (by value) — same "everyone on Auto
        // shares one call" property the old `groupBy { it.source }` had, extended to ranked lists.
        val bySameRanking = grantedSpecs.groupBy { it.ranked }
        val resolvedResultFor = HashMap<AggregateMetric<*>, AggregationResult?>()
        for ((ranked, specsInGroup) in bySameRanking) {
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
                    resolvedNow.forEach { m -> resolvedResultFor[m] = result }
                    remaining -= resolvedNow.toSet()
                }
                // Metrics still in `remaining` after the LAST candidate stay unresolved (absent).
            }
        }
        fun <T : Any> valueFor(metric: AggregateMetric<T>): T? =
            resolvedResultFor[metric]?.let { if (it.contains(metric)) it.get(metric) else null }

        // SpO2 has no AggregateMetric (it's a single-instant reading, not interval-shaped) — read
        // records directly and average in Kotlin. Bounded to one day, so this is not the "raw
        // per-sample series" C7 forbids (§6.1.9) — it's a same-day average, the smallest possible
        // window, exactly like every other daily figure on this screen.
        val spo2 = firstNonEmpty(OxygenSaturationRecord::class, filter, spo2Ranked)
        val spo2Avg = if (spo2.isEmpty()) null else spo2.map { it.percentage.value }.average()
        // HEALTH_VITALS_RICHNESS_PLAN.md §3 — two more reductions over the same raw list already
        // read above for the average; no new Health Connect call.
        val spo2Min = spo2.minOfOrNull { it.percentage.value }
        val spo2Max = spo2.maxOfOrNull { it.percentage.value }

        // Weight, HEALTH_VITALS_RICHNESS_PLAN.md §3 — raw read (same idiom as OxygenSaturationRecord
        // and NutritionRecord just below) instead of WeightRecord.WEIGHT_AVG, so every reading is
        // available for [WeightReading] and the day's headline figure is the ACTUAL last reading.
        val weightRecords = firstNonEmpty(WeightRecord::class, filter, weightRanked)
        val weightReadings = weightRecords.map {
            WeightReading(
                id = it.metadata.id,
                atMillis = it.time.toEpochMilli(),
                weightKg = it.weight.inKilograms,
                sourceApp = it.metadata.dataOrigin.packageName
            )
        }
        val lastWeightKg = weightRecords.maxByOrNull { it.time }?.weight?.inKilograms

        // Nutrition read directly (not via the mixed-metric AggregateRequest above) so the day's
        // "From <app>" source label (§6.1.6) is attributed to the record that actually carries it,
        // rather than to whichever data origin happened to also write steps/heart-rate that day.
        val nutritionRecords = firstNonEmpty(NutritionRecord::class, filter, nutritionRanked)
        val nutritionCalories = nutritionRecords.sumOf { it.energy?.inKilocalories ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.energy != null } }
        val nutritionProtein = nutritionRecords.sumOf { it.protein?.inGrams ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.protein != null } }
        val nutritionCarbs = nutritionRecords.sumOf { it.totalCarbohydrate?.inGrams ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.totalCarbohydrate != null } }
        val nutritionFat = nutritionRecords.sumOf { it.totalFat?.inGrams ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.totalFat != null } }
        val nutritionSourceApp = nutritionRecords.firstOrNull()?.metadata?.dataOrigin?.packageName

        // UI fix (owner review of the Health tab screenshot, build 50) — union of every app that
        // contributed to any of this day's resolved aggregate results plus SpO2's own raw-record
        // origins. Session/nutrition/weight already carry their own per-record source
        // (`sourceApp`/`dataOrigin`, unchanged above) — this is the one new source signal for the
        // tiles that didn't have one at all.
        val activitySourceApps = (
            resolvedResultFor.values.filterNotNull().flatMap { it.dataOrigins.map { o -> o.packageName } } +
                spo2.map { it.metadata.dataOrigin.packageName }
            ).distinct()

        return DayAggregate(
            steps = valueFor(StepsRecord.COUNT_TOTAL),
            distanceMeters = valueFor(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            activeCalories = valueFor(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            totalCalories = valueFor(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            avgHeartRate = valueFor(HeartRateRecord.BPM_AVG),
            minHeartRate = valueFor(HeartRateRecord.BPM_MIN),
            maxHeartRate = valueFor(HeartRateRecord.BPM_MAX),
            restingHeartRate = valueFor(RestingHeartRateRecord.BPM_AVG),
            spo2Percent = spo2Avg,
            spo2MinPercent = spo2Min,
            spo2MaxPercent = spo2Max,
            weightKg = lastWeightKg,
            weightReadings = weightReadings,
            hydrationMl = valueFor(HydrationRecord.VOLUME_TOTAL)?.inMilliliters,
            nutritionCalories = nutritionCalories,
            nutritionProteinGrams = nutritionProtein,
            nutritionCarbsGrams = nutritionCarbs,
            nutritionFatGrams = nutritionFat,
            nutritionSourceApp = nutritionSourceApp,
            activitySourceApps = activitySourceApps
        )
    }

    /** Owner feedback round 3 (2026-09-20) — tries each ranked candidate's `dataOriginFilter` in
     *  order, stopping at the first that returns a non-empty record list (falls through to the
     *  next candidate on empty — same "no data that day" semantics as the aggregate path above).
     *  Empty [ranked] = old unfiltered-read behavior. */
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

    /** [owner feedback round 2, 2026-09-20] — one data point selectable in the "Data source
     *  priority" settings pickers. Deliberately narrower than [AggregateMetric] groupings above:
     *  discovery wants to know exactly which apps write THIS metric, not a combined family. */
    enum class SourceField { STEPS, DISTANCE, CALORIES, HEART_RATE, SPO2, HYDRATION, WEIGHT, NUTRITION }

    /** Root cause (owner report: "I don't see Mi Fitness. It is syncing... but not showing") — the
     *  settings picker's option list used to be derived from `activitySourceApps`/
     *  `mostRecentActivitySourceApps`, itself populated by [dayAggregate]'s own `dataOriginFilter`.
     *  Once a source preference resolves to one package (even "Auto" resolves deterministically,
     *  see `HealthSourcePriority`), every later read is scoped to just that package, so the stored
     *  candidate list can never again see any other source Health Connect actually still has data
     *  from — the app blinds itself to alternates. This issues a ONE-OFF UNFILTERED read (no
     *  `dataOriginFilter`) over the last [days] days for exactly [field]'s own record type(s), used
     *  ONLY to populate a picker sheet's option list — never to compute a displayed value. Cheap by
     *  design: only called when a picker sheet is actually opened, not on every sync. */
    suspend fun discoverSourcePackages(field: SourceField, granted: Set<String>, days: Long = 30): Set<String> {
        val end = Instant.now()
        val start = end.minus(java.time.Duration.ofDays(days))
        val filter = TimeRangeFilter.between(start, end)
        suspend fun aggregateOrigins(metrics: Set<AggregateMetric<*>>): Set<String> {
            if (metrics.isEmpty()) return emptySet()
            return runCatchingCancellable {
                client().aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = filter))
            }.getOrNull()?.dataOrigins?.map { it.packageName }?.toSet() ?: emptySet()
        }
        suspend fun <T : androidx.health.connect.client.records.Record> recordOrigins(
            recordType: kotlin.reflect.KClass<T>
        ): Set<String> {
            val records = runCatchingCancellable {
                client().readRecords(ReadRecordsRequest(recordType, filter)).records
            }.getOrDefault(emptyList())
            return records.map { it.metadata.dataOrigin.packageName }.toSet()
        }

        val stepsPerm = HealthPermission.getReadPermission(StepsRecord::class)
        val distancePerm = HealthPermission.getReadPermission(DistanceRecord::class)
        val activeCalPerm = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val totalCalPerm = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val heartRatePerm = HealthPermission.getReadPermission(HeartRateRecord::class)
        val restingHrPerm = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val hydrationPerm = HealthPermission.getReadPermission(HydrationRecord::class)

        return when (field) {
            SourceField.STEPS ->
                if (stepsPerm !in granted) emptySet() else aggregateOrigins(setOf(StepsRecord.COUNT_TOTAL))
            SourceField.DISTANCE ->
                if (distancePerm !in granted) emptySet() else aggregateOrigins(setOf(DistanceRecord.DISTANCE_TOTAL))
            SourceField.CALORIES -> {
                val metrics = buildSet {
                    if (activeCalPerm in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                    if (totalCalPerm in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
                }
                aggregateOrigins(metrics)
            }
            SourceField.HEART_RATE -> {
                val metrics = buildSet {
                    if (heartRatePerm in granted) {
                        add(HeartRateRecord.BPM_AVG); add(HeartRateRecord.BPM_MIN); add(HeartRateRecord.BPM_MAX)
                    }
                    if (restingHrPerm in granted) add(RestingHeartRateRecord.BPM_AVG)
                }
                aggregateOrigins(metrics)
            }
            SourceField.HYDRATION ->
                if (hydrationPerm !in granted) emptySet() else aggregateOrigins(setOf(HydrationRecord.VOLUME_TOTAL))
            SourceField.SPO2 -> recordOrigins(OxygenSaturationRecord::class)
            SourceField.WEIGHT -> recordOrigins(WeightRecord::class)
            SourceField.NUTRITION -> recordOrigins(NutritionRecord::class)
        }
    }

    /** The day's sleep, if the session ENDED on this local date (a session spanning midnight is
     *  attributed to the day the user WOKE UP, not the night they went to bed — matching how a
     *  user thinks about "today's sleep" showing up on the morning they actually see it). */
    data class SleepSummary(
        val totalMinutes: Int, val deepMinutes: Int, val lightMinutes: Int,
        val remMinutes: Int, val awakeMinutes: Int, val startMillis: Long, val endMillis: Long
    )

    suspend fun sleepForDay(date: LocalDate, zoneId: ZoneId): SleepSummary? {
        // Widen the read window a few hours either side of midnight so a session that started the
        // evening before (and thus ends, and is attributed to, this date) is still captured.
        val start = date.minusDays(1).atStartOfDay(zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val sessions = client().readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
        ).records
        val forThisDay = sessions.filter {
            it.endTime.atZone(zoneId).toLocalDate() == date
        }
        if (forThisDay.isEmpty()) return null
        if (forThisDay.size > 1) {
            // Breadcrumb (kept permanently, matches this file's existing Log.w convention) — cheap
            // (only fires when there's more than one raw session for the day, i.e. exactly the
            // cases the merge below has to reason about) and is the one way to tell, from a real
            // user's device, whether [clusterSleepSessions] is merging the right things.
            Log.w(
                TAG,
                "sleepForDay $date: ${forThisDay.size} raw sessions: " + forThisDay.sortedBy { it.startTime }.joinToString(" | ") {
                    "${it.startTime}..${it.endTime} mod=${it.metadata.lastModifiedTime} src=${it.metadata.dataOrigin.packageName}"
                }
            )
        }
        // User bug report — a band app (e.g. Mi Fitness) can duplicate one night's sleep beyond
        // simple time-range overlap: a re-sync can post what's meant to be a CONTINUATION as a
        // fresh, non-overlapping session right after the earlier one ends (11:01pm–2:02am, then a
        // second sync adds 2:02am–6:36am), or with a slightly shifted start. The original fix only
        // merged sessions whose time ranges literally intersected, which missed this back-to-back
        // pattern — see [clusterSleepSessions]'s KDoc for the full merge rule (same-source-app +
        // small gap, most-recently-modified session wins, never a blind sum). Conservative by
        // design: different source apps or a large gap (a real nap hours before bedtime) are never
        // merged, so a genuinely long sleep or a real nap+main-sleep pair can't be truncated.
        val spans = forThisDay.mapIndexed { i, s ->
            SleepSessionSpan(
                index = i,
                startMillis = s.startTime.toEpochMilli(),
                endMillis = s.endTime.toEpochMilli(),
                lastModifiedMillis = s.metadata.lastModifiedTime.toEpochMilli(),
                sourcePackage = s.metadata.dataOrigin.packageName
            )
        }
        val keptIndices = clusterSleepSessions(spans).map { it.index }.toSet()
        val kept = forThisDay.filterIndexed { i, _ -> i in keptIndices }
        var deep = 0; var light = 0; var rem = 0; var awake = 0; var total = 0
        var minStart = Long.MAX_VALUE; var maxEnd = Long.MIN_VALUE
        for (s in kept) {
            minStart = minOf(minStart, s.startTime.toEpochMilli())
            maxEnd = maxOf(maxEnd, s.endTime.toEpochMilli())
            if (s.stages.isEmpty()) {
                total += java.time.Duration.between(s.startTime, s.endTime).toMinutes().toInt()
            } else {
                for (stage in s.stages) {
                    val mins = java.time.Duration.between(stage.startTime, stage.endTime).toMinutes().toInt()
                    total += mins
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> deep += mins
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> light += mins
                        SleepSessionRecord.STAGE_TYPE_REM -> rem += mins
                        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> awake += mins
                        else -> { /* SLEEPING/OUT_OF_BED/UNKNOWN — counted in total, not a named bucket */ }
                    }
                }
            }
        }
        return SleepSummary(total, deep, light, rem, awake, minStart, maxEnd)
    }

    data class ExerciseSessionData(
        val id: String, val exerciseType: Int, val title: String?,
        val startMillis: Long, val endMillis: Long,
        val activeCalories: Double?, val distanceMeters: Double?, val avgHeartRate: Long?,
        val sourceApp: String?
    )

    /** Every band-recorded workout in [start, end) — §6.4, never merged with `workout_sessions`. */
    suspend fun exerciseSessions(start: Instant, end: Instant): List<ExerciseSessionData> {
        val sessions = client().readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))
        ).records
        return sessions.map { s ->
            // Per-session aggregates (calories/distance/avg HR) — one bounded aggregate call per
            // session, scoped to that session's own [start,end), not a raw sample read.
            val agg = runCatchingCancellable {
                client().aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            DistanceRecord.DISTANCE_TOTAL,
                            HeartRateRecord.BPM_AVG
                        ),
                        timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime)
                    )
                )
            }.getOrNull()
            ExerciseSessionData(
                id = s.metadata.id,
                exerciseType = s.exerciseType,
                title = s.title,
                startMillis = s.startTime.toEpochMilli(),
                endMillis = s.endTime.toEpochMilli(),
                activeCalories = agg?.valueOrNull(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
                distanceMeters = agg?.valueOrNull(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
                avgHeartRate = agg?.valueOrNull(HeartRateRecord.BPM_AVG),
                sourceApp = s.metadata.dataOrigin.packageName
            )
        }
    }

    // Bug fix — user report: refresh permanently fails ("Couldn't refresh your health data right
    // now") on a device that has NOT granted every one of the 12 MVP read permissions (e.g. "Total
    // calories burned" left off). Root cause: this used to unconditionally request a changes token
    // scoped to all 12 record types regardless of what was actually granted. Health Connect's
    // `getChanges()` requires every permission a token's scope covers to still be held at call
    // time or it throws `SecurityException` for the WHOLE call — the exact same "all or nothing"
    // constraint `dayAggregate`'s `metricPermissions` filtering (H2, above) already works around
    // for the aggregate path, but this mint call was never given the same treatment. Every
    // subsequent incremental pull then re-threw on `reader.changes(token)` forever, which is a
    // strictly worse failure than the "missing 1 permission" case ever needed to be — it should
    // just mean that one metric family is silently absent, matching every other metric here.
    suspend fun changesToken(granted: Set<String>): String {
        val recordTypePermissions: List<Pair<kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>, String>> = listOf(
            StepsRecord::class to HealthPermission.getReadPermission(StepsRecord::class),
            DistanceRecord::class to HealthPermission.getReadPermission(DistanceRecord::class),
            ActiveCaloriesBurnedRecord::class to HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            TotalCaloriesBurnedRecord::class to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            ExerciseSessionRecord::class to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            SleepSessionRecord::class to HealthPermission.getReadPermission(SleepSessionRecord::class),
            HeartRateRecord::class to HealthPermission.getReadPermission(HeartRateRecord::class),
            RestingHeartRateRecord::class to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            OxygenSaturationRecord::class to HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            WeightRecord::class to HealthPermission.getReadPermission(WeightRecord::class),
            HydrationRecord::class to HealthPermission.getReadPermission(HydrationRecord::class),
            NutritionRecord::class to HealthPermission.getReadPermission(NutritionRecord::class)
        )
        val recordTypes = recordTypePermissions.filter { it.second in granted }.map { it.first }.toSet()
        return client().getChangesToken(ChangesTokenRequest(recordTypes = recordTypes))
    }

    data class ChangesResult(val hasChanges: Boolean, val expired: Boolean, val nextToken: String?)

    /** Whether anything changed since [token] — does not itself re-derive `health_days`; the
     *  caller narrows its re-aggregation window instead of processing raw change records one by
     *  one (a pragmatic simplification: `HealthRepository` re-aggregates the affected date window
     *  via [dayAggregate]/[exerciseSessions] rather than patching individual entities from each
     *  `Change`, which keeps the mapping logic in exactly one place — the same aggregate path the
     *  full pull already uses — at the cost of not being a byte-for-byte "process only the changed
     *  record" diff). */
    suspend fun changes(token: String): ChangesResult {
        val response = client().getChanges(token)
        if (response.changesTokenExpired) return ChangesResult(hasChanges = true, expired = true, nextToken = null)
        return ChangesResult(
            hasChanges = response.changes.isNotEmpty(),
            expired = false,
            nextToken = response.nextChangesToken
        )
    }

    companion object {
        private const val TAG = "HealthConnectReader"
    }
}
