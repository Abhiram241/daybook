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
        val nutritionSourceApp: String?
    )

    /** One raw `WeightRecord` reading, HEALTH_VITALS_RICHNESS_PLAN.md §1/§3. */
    data class WeightReading(val id: String, val atMillis: Long, val weightKg: Double, val sourceApp: String?)

    /** Reads one calendar day's aggregate across every MVP metric with a single [AggregateRequest]
     *  per metric family. Health Connect's `aggregate()` call accepts a mixed metric set, so this
     *  is ONE call, not twelve. Metrics the OS has nothing for are simply absent from the result
     *  (`AggregationResult.contains`), which is exactly §7.4's per-metric hide rule's data source. */
    suspend fun dayAggregate(date: LocalDate, zoneId: ZoneId, granted: Set<String>): DayAggregate {
        val start = date.atStartOfDay(zoneId).toInstant()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val filter = TimeRangeFilter.between(start, end)
        // H2 fix — `aggregate()` requires read permission for EVERY record type in the metric set
        // or it throws `SecurityException` for the whole call, not a partial result. Build the
        // metric set from only what's actually granted so an ordinary partial grant (e.g. Steps
        // and Sleep but not Heart rate) degrades to "that family of metrics is absent" instead of
        // failing every metric, including the ones the user did share.
        val metricPermissions: List<Pair<AggregateMetric<*>, String>> = listOf(
            StepsRecord.COUNT_TOTAL to HealthPermission.getReadPermission(StepsRecord::class),
            DistanceRecord.DISTANCE_TOTAL to HealthPermission.getReadPermission(DistanceRecord::class),
            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL to HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            TotalCaloriesBurnedRecord.ENERGY_TOTAL to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HeartRateRecord.BPM_AVG to HealthPermission.getReadPermission(HeartRateRecord::class),
            HeartRateRecord.BPM_MIN to HealthPermission.getReadPermission(HeartRateRecord::class),
            HeartRateRecord.BPM_MAX to HealthPermission.getReadPermission(HeartRateRecord::class),
            RestingHeartRateRecord.BPM_AVG to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HydrationRecord.VOLUME_TOTAL to HealthPermission.getReadPermission(HydrationRecord::class)
        )
        val metrics = metricPermissions.filter { it.second in granted }.map { it.first }.toSet()
        // Also wrapped in its own `runCatchingCancellable` (H2) — an unexpected `SecurityException` (a
        // permission revoked mid-session, an OS quirk) degrades this one aggregate to "absent"
        // rather than aborting the whole day the way the previously-unguarded call did (H3).
        val result = if (metrics.isEmpty()) null else runCatchingCancellable {
            client().aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = filter))
        }.getOrNull()
        // SpO2 has no AggregateMetric (it's a single-instant reading, not interval-shaped) — read
        // records directly and average in Kotlin. Bounded to one day, so this is not the "raw
        // per-sample series" C7 forbids (§6.1.9) — it's a same-day average, the smallest possible
        // window, exactly like every other daily figure on this screen.
        val spo2 = runCatchingCancellable {
            client().readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, filter)).records
        }.getOrDefault(emptyList())
        val spo2Avg = if (spo2.isEmpty()) null else spo2.map { it.percentage.value }.average()
        // HEALTH_VITALS_RICHNESS_PLAN.md §3 — two more reductions over the same raw list already
        // read above for the average; no new Health Connect call.
        val spo2Min = spo2.minOfOrNull { it.percentage.value }
        val spo2Max = spo2.maxOfOrNull { it.percentage.value }

        // Weight, HEALTH_VITALS_RICHNESS_PLAN.md §3 — raw read (same idiom as OxygenSaturationRecord
        // and NutritionRecord just below) instead of WeightRecord.WEIGHT_AVG, so every reading is
        // available for [WeightReading] and the day's headline figure is the ACTUAL last reading.
        val weightRecords = runCatchingCancellable {
            client().readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records
        }.getOrDefault(emptyList())
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
        val nutritionRecords = runCatchingCancellable {
            client().readRecords(ReadRecordsRequest(NutritionRecord::class, filter)).records
        }.getOrDefault(emptyList())
        val nutritionCalories = nutritionRecords.sumOf { it.energy?.inKilocalories ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.energy != null } }
        val nutritionProtein = nutritionRecords.sumOf { it.protein?.inGrams ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.protein != null } }
        val nutritionCarbs = nutritionRecords.sumOf { it.totalCarbohydrate?.inGrams ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.totalCarbohydrate != null } }
        val nutritionFat = nutritionRecords.sumOf { it.totalFat?.inGrams ?: 0.0 }.takeIf { nutritionRecords.any { r -> r.totalFat != null } }
        val nutritionSourceApp = nutritionRecords.firstOrNull()?.metadata?.dataOrigin?.packageName

        return DayAggregate(
            steps = result?.valueOrNull(StepsRecord.COUNT_TOTAL),
            distanceMeters = result?.valueOrNull(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            activeCalories = result?.valueOrNull(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            totalCalories = result?.valueOrNull(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            avgHeartRate = result?.valueOrNull(HeartRateRecord.BPM_AVG),
            minHeartRate = result?.valueOrNull(HeartRateRecord.BPM_MIN),
            maxHeartRate = result?.valueOrNull(HeartRateRecord.BPM_MAX),
            restingHeartRate = result?.valueOrNull(RestingHeartRateRecord.BPM_AVG),
            spo2Percent = spo2Avg,
            spo2MinPercent = spo2Min,
            spo2MaxPercent = spo2Max,
            weightKg = lastWeightKg,
            weightReadings = weightReadings,
            hydrationMl = result?.valueOrNull(HydrationRecord.VOLUME_TOTAL)?.inMilliliters,
            nutritionCalories = nutritionCalories,
            nutritionProteinGrams = nutritionProtein,
            nutritionCarbsGrams = nutritionCarbs,
            nutritionFatGrams = nutritionFat,
            nutritionSourceApp = nutritionSourceApp
        )
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

    suspend fun changesToken(): String = client().getChangesToken(
        ChangesTokenRequest(
            recordTypes = setOf(
                StepsRecord::class, DistanceRecord::class, ActiveCaloriesBurnedRecord::class,
                TotalCaloriesBurnedRecord::class, ExerciseSessionRecord::class, SleepSessionRecord::class,
                HeartRateRecord::class, RestingHeartRateRecord::class, OxygenSaturationRecord::class,
                WeightRecord::class, HydrationRecord::class, NutritionRecord::class
            )
        )
    )

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
