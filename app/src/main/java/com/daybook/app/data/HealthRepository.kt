package com.daybook.app.data

import android.util.Log
import com.daybook.app.data.health.HealthConnectReader
import com.daybook.app.data.health.HealthPermissions
import com.daybook.app.data.health.HealthSyncStateStore
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.model.HealthSession
import com.daybook.app.data.model.HealthWeightReading
import com.daybook.app.util.recordUnhandledException
import com.daybook.app.util.runCatchingCancellable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * B3/B4 (§6.3, §7.3) — orchestration only: pull from [HealthConnectReader], map, upsert into Room.
 * Every public function is `runCatchingCancellable`-wrapped end to end (C6) — a Health Connect failure never
 * throws out of this class, never crashes, and never blocks the caller; it is always reported back
 * as a [PullOutcome] the caller renders per §6.2's ladder / §6.3's exact copy.
 */
@Singleton
class HealthRepository(
    private val database: AppDatabase,
    private val reader: HealthConnectReader,
    private val stateStore: HealthSyncStateStore
) {
    private val zoneId: ZoneId get() = ZoneId.systemDefault()
    private val ymd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    sealed class PullOutcome {
        data class Success(val hasNewData: Boolean) : PullOutcome()
        data class Failure(val message: String) : PullOutcome()
        object PermissionsMissing : PullOutcome()
    }

    /** §6.3 cadence 1 — on app resume, throttled to at most once every 15 minutes. */
    suspend fun pullOnResume(): PullOutcome? {
        val now = System.currentTimeMillis()
        if (now - stateStore.lastPullAt < RESUME_THROTTLE_MILLIS) return null
        return pull(isManual = false)
    }

    /** §6.3 cadence 2 — folded into the existing `WindowRefreshWorker`, no new periodic job. */
    suspend fun pullDaily(): PullOutcome = pull(isManual = false)

    /** §6.3's manual "Refresh now" row. */
    suspend fun refreshNow(): PullOutcome = pull(isManual = true)

    /** §6.3's "Import my past data" — assumes the caller has already been granted
     *  `READ_HEALTH_DATA_HISTORY` (or accepts the shorter window if it hasn't). 365-day cap. */
    suspend fun importPastData(): PullOutcome {
        val granted = runCatchingCancellable { reader.grantedPermissions() }.getOrElse {
            Log.e(TAG, "grantedPermissions failed", it); recordUnhandledException(it)
            return PullOutcome.Failure("Couldn't import your past data. Try again.")
        }
        if (granted.isEmpty()) return PullOutcome.PermissionsMissing
        // H6 fix — without `READ_HEALTH_DATA_HISTORY`, Health Connect silently caps every read at
        // the last 30 days: `pullWindow` below still "succeeds" for days 31-365, it just returns
        // nothing for them, so the old unconditional success copy ("Imported your health history
        // back to <~1 year ago date>") was simply false whenever this permission wasn't granted —
        // which was always, since nothing in the app ever requested it (see
        // `HealthPermissions.optionalExtras()`'s callers, now wired up from the Beast Mode
        // settings screen). The claim is now conditional on what was actually granted.
        val hasHistoryAccess = HealthPermissions.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        val today = LocalDate.now(zoneId)
        val from = if (hasHistoryAccess) today.minusDays(BACKFILL_CAP_DAYS - 1) else today.minusDays(29)
        val result = runCatchingCancellable { pullWindow(from, today, granted) }
        return result.fold(
            onSuccess = {
                stateStore.backfillThroughMillis = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
                stateStore.lastPullAt = System.currentTimeMillis()
                val msg = if (hasHistoryAccess) {
                    "Imported your health history back to ${from.format(DISPLAY_DATE)}."
                } else {
                    "Imported the last 30 days. Daybook needs access to your history to go further back."
                }
                setStatus(msg, failed = false)
                PullOutcome.Success(hasNewData = true)
            },
            onFailure = { t ->
                Log.e(TAG, "importPastData failed", t); recordUnhandledException(t)
                setStatus("Couldn't import your past data. Try again.", failed = true)
                PullOutcome.Failure("Couldn't import your past data. Try again.")
            }
        )
    }

    private suspend fun pull(isManual: Boolean): PullOutcome {
        val granted = runCatchingCancellable { reader.grantedPermissions() }.getOrElse {
            Log.e(TAG, "grantedPermissions failed", it); recordUnhandledException(it)
            val msg = "Couldn't refresh your health data. Check that Health Connect is still installed, then try again."
            if (isManual) setStatus(msg, failed = true)
            return PullOutcome.Failure(msg)
        }
        // H5 fix — this used to return without calling `setStatus(...)`, so the persisted status
        // line kept showing whatever the LAST successful pull said, days after access was
        // revoked — the one real, actionable, entirely-recoverable failure state produced no
        // message at all. Now it always overwrites the status line, matching every other branch.
        if (granted.isEmpty()) {
            setStatus("Daybook no longer has access to your health data. Tap Connect to share it again.", failed = true)
            return PullOutcome.PermissionsMissing
        }

        val today = LocalDate.now(zoneId)
        // User bug report — force exactly one full 30-day re-aggregate after the sleep-dedup fix
        // shipped, the same way a first-ever pull or an expired token already does below, so a day
        // synced BEFORE the fix (with the old, inflated sleep total) actually gets recomputed
        // instead of sitting stale in Room forever (see `sleepDedupResyncPending`'s KDoc).
        val forcedResync = stateStore.sleepDedupResyncPending
        val token = if (forcedResync) null else stateStore.changesToken
        val outcome: Result<Boolean> = if (token == null) {
            // First pull ever, or a forced one-time resync — the automatic window is the last 30
            // days (§6.3).
            runCatchingCancellable {
                pullWindow(today.minusDays(29), today, granted)
                // H1 fix — this branch never minted a token, so `stateStore.changesToken` stayed
                // null forever and every subsequent pull re-entered this same 30-day full-resync
                // branch for the life of the install. Mint it now, exactly like the expired-token
                // branch below already does, WITHOUT clearing anything on failure (`getOrNull()`
                // just leaves it null, same as it already was).
                stateStore.changesToken = runCatchingCancellable { reader.changesToken() }.getOrNull()
                if (forcedResync) stateStore.sleepDedupResyncPending = false
                true
            }
        } else {
            runCatchingCancellable {
                val changes = reader.changes(token)
                when {
                    changes.expired -> {
                        // Silent-but-visible fallback (§6.3): re-read the last 30 days, mint a new
                        // token, and record when this happened WITHOUT clearing the old token
                        // first — if the re-read below throws, the old (still-expired) token stays
                        // in place rather than being nulled out, which would otherwise force a
                        // full re-read on every subsequent pull forever (a silent battery
                        // regression, §6.3).
                        pullWindow(today.minusDays(29), today, granted)
                        stateStore.lastFullResyncAt = System.currentTimeMillis()
                        // L2 fix — `.getOrNull()` used to clobber the token to null on a transient
                        // mint failure, contradicting this very comment's "without clearing the
                        // old token first" — that's the one way the system could re-enter H1's
                        // permanent-full-resync state after being fixed. Only overwrite on an
                        // actual new token; a mint failure now leaves the expired-but-non-null
                        // token in place (still forces one more full re-read next time via
                        // `changes.expired`, but doesn't null it out for good).
                        runCatchingCancellable { reader.changesToken() }.getOrNull()?.let { stateStore.changesToken = it }
                        true
                    }
                    changes.hasChanges -> {
                        // Narrow the re-read to since the last successful pull (capped at 30 days)
                        // rather than replaying every changed record individually — see
                        // HealthConnectReader.changes' KDoc for why.
                        val lastPull = stateStore.lastPullAt
                        val since = if (lastPull <= 0) today.minusDays(1)
                        else Instant.ofEpochMilli(lastPull).atZone(zoneId).toLocalDate()
                            .coerceAtLeast(today.minusDays(29))
                        pullWindow(since, today, granted)
                        stateStore.changesToken = changes.nextToken ?: token
                        true
                    }
                    else -> {
                        stateStore.changesToken = changes.nextToken ?: token
                        false
                    }
                }
            }
        }

        return outcome.fold(
            onSuccess = { hasNewData ->
                stateStore.lastPullAt = System.currentTimeMillis()
                if (isManual) {
                    setStatus(
                        if (hasNewData) "Last updated just now." else "Up to date — nothing new from your band.",
                        failed = false
                    )
                } else {
                    setStatus("Last updated just now.", failed = false)
                }
                PullOutcome.Success(hasNewData)
            },
            onFailure = { t ->
                Log.e(TAG, "pull failed", t); recordUnhandledException(t)
                // §6.2 "A read threw" — keep the last-good data on screen, just report the failure.
                // H2 fix — this used to blame "Health Connect is still installed" unconditionally,
                // which was almost always misdiagnosing an ordinary partial permission grant (Steps
                // granted, Heart rate denied) as a missing-app problem. Now that `dayAggregate`
                // only requests granted metrics and every Health Connect call in `pullWindow` is
                // individually guarded (H2/H3), reaching this branch means the WHOLE window failed
                // for some other reason, so the copy no longer points at a specific, usually-wrong
                // cause.
                val msg = if (isManual) {
                    "Couldn't refresh your health data right now. Try again in a moment."
                } else {
                    "Couldn't refresh your health data. Last updated ${lastUpdatedRelative()}."
                }
                setStatus(msg, failed = true)
                PullOutcome.Failure(msg)
            }
        )
    }

    private fun LocalDate.coerceAtLeast(min: LocalDate): LocalDate = if (this.isBefore(min)) min else this

    /** Re-aggregates every day in `[from, to]` inclusive plus every band session in that window,
     *  and upserts into Room. The single choke point every pull path (first pull, expired-token
     *  full re-read, delta re-read, manual import) funnels through.
     *
     *  H3 fix — this used to accumulate into local lists and only write once at the very end, so
     *  one bad day (an aggregate throw — see H2) unwound past both `upsertDays` and
     *  `upsertWeightReadings` and discarded every day already aggregated in that pass, and an
     *  `exerciseSessions` failure discarded the whole window's sessions. Each day is now
     *  `runCatchingCancellable`-guarded individually and partial progress is upserted regardless of later
     *  failures; the call only throws (surfacing §6.2's "keep the last-good data, report the
     *  failure" copy) when EVERY day in the window failed to aggregate. */
    /** Investigation finding (user report: pull-to-refresh spinner "stuck forever") — this used to
     *  walk `[from, to]` one day at a time, fully sequentially: `dayAggregate` alone issues up to 5
     *  separate Health Connect IPC round trips per day (one combined `aggregate()` covering 9
     *  metrics, plus separate `readRecords()` calls for SpO2, weight and nutrition — see
     *  `HealthConnectReader.dayAggregate`), and `sleepForDay` is a 6th. A first-ever pull (or any
     *  expired-token full resync) covers 30 days, so that's up to ~180 sequential binder round
     *  trips with NOTHING running concurrently and (before this investigation) no bound on how long
     *  the whole thing could take. On a slow device, or Health Connect's provider being momentarily
     *  busy syncing with the band app, this genuinely could take well past what a user will wait
     *  for — that's what actually produced the "stuck forever" spinner. It was never a Compose /
     *  `PullToRefreshBox` wiring bug; that part already followed the standard, documented pattern.
     *
     *  Fixed two ways: (1) days are fetched CONCURRENTLY in small batches
     *  ([DAY_FETCH_CONCURRENCY] at a time) instead of one at a time, cutting real wall-clock time
     *  roughly by the batch size; (2) each batch is upserted into Room as soon as it's fetched, so
     *  a caller-side timeout (`HealthTabViewModel.refreshNow`'s `withTimeoutOrNull`) cancelling a
     *  slow pull mid-flight no longer discards every day already fetched — only whichever batches
     *  hadn't completed yet are lost, and the next pull picks up from there. */
    private suspend fun pullWindow(from: LocalDate, to: LocalDate, granted: Set<String>) {
        val now = System.currentTimeMillis()
        val allDates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        var totalDays = 0
        var failedDays = 0

        for (batch in allDates.chunked(DAY_FETCH_CONCURRENCY)) {
            val results = coroutineScope {
                batch.map { d -> async { fetchDay(d, granted, now) } }.awaitAll()
            }
            totalDays += results.size
            failedDays += results.count { it.failed }
            val batchDays = results.mapNotNull { it.day }
            val batchWeightReadings = results.flatMap { it.weightReadings }
            if (batchDays.isNotEmpty()) database.healthDao().upsertDays(batchDays)
            if (batchWeightReadings.isNotEmpty()) database.healthDao().upsertWeightReadings(batchWeightReadings)
        }

        val startInstant = from.atStartOfDay(zoneId).toInstant()
        val endInstant = to.plusDays(1).atStartOfDay(zoneId).toInstant()
        // H2/H3 — was unguarded, so a session-read failure discarded the whole window's sessions
        // even though the days above (which upsert first) had already succeeded independently.
        var sessionsFailed = false
        val exerciseSessions = runCatchingCancellable { reader.exerciseSessions(startInstant, endInstant) }.getOrElse { t ->
            Log.e(TAG, "exerciseSessions failed", t); recordUnhandledException(t)
            sessionsFailed = true
            emptyList()
        }
        val sessions = exerciseSessions.map { s ->
            HealthSession(
                id = s.id,
                localDate = Instant.ofEpochMilli(s.startMillis).atZone(zoneId).toLocalDate().format(ymd),
                exerciseType = s.exerciseType,
                title = s.title,
                startMillis = s.startMillis,
                endMillis = s.endMillis,
                durationMinutes = ((s.endMillis - s.startMillis) / 60000L).toInt(),
                activeCalories = s.activeCalories?.toFloat(),
                distanceMeters = s.distanceMeters?.toFloat(),
                avgHeartRate = s.avgHeartRate?.toInt(),
                sourceApp = s.sourceApp,
                updatedAt = now
            )
        }
        if (sessions.isNotEmpty()) database.healthDao().upsertSessions(sessions)

        // H3 — only surface an actual failure (so the caller's `onFailure` branch reports it and
        // does NOT advance `lastPullAt`/the changes token, per H1) when nothing at all came back
        // from this window; a partial failure has already had its good days/sessions committed
        // above, which is the whole point of moving the upserts inside this guard.
        if (totalDays > 0 && failedDays == totalDays && sessionsFailed) {
            throw IllegalStateException("Every day in the requested window failed to aggregate")
        }
    }

    /** One day's worth of [pullWindow] work, extracted so it can run concurrently with its
     *  siblings inside a batch — see [pullWindow]'s KDoc for why. Same per-day error handling as
     *  before (H2/H3): an aggregate failure degrades this one day to "absent", never throws out. */
    private data class DayFetchResult(val day: HealthDay?, val weightReadings: List<HealthWeightReading>, val failed: Boolean)

    private suspend fun fetchDay(d: LocalDate, granted: Set<String>, now: Long): DayFetchResult {
        val aggResult = runCatchingCancellable { reader.dayAggregate(d, zoneId, granted) }
        val agg = aggResult.getOrNull()
        if (agg == null) {
            aggResult.exceptionOrNull()?.let { t ->
                Log.e(TAG, "dayAggregate failed for $d", t); recordUnhandledException(t)
            }
            return DayFetchResult(day = null, weightReadings = emptyList(), failed = true)
        }
        val sleep = runCatchingCancellable { reader.sleepForDay(d, zoneId) }.getOrNull()
        val healthDay = HealthDay(
            localDate = d.format(ymd),
            steps = agg.steps?.toInt(),
            distanceMeters = agg.distanceMeters?.toFloat(),
            activeCalories = agg.activeCalories?.toFloat(),
            totalCalories = agg.totalCalories?.toFloat(),
            restingHeartRate = agg.restingHeartRate?.toInt(),
            avgHeartRate = agg.avgHeartRate?.toInt(),
            minHeartRate = agg.minHeartRate?.toInt(),
            maxHeartRate = agg.maxHeartRate?.toInt(),
            sleepMinutes = sleep?.totalMinutes,
            sleepDeepMinutes = sleep?.deepMinutes,
            sleepLightMinutes = sleep?.lightMinutes,
            sleepRemMinutes = sleep?.remMinutes,
            sleepAwakeMinutes = sleep?.awakeMinutes,
            sleepStartMillis = sleep?.startMillis,
            sleepEndMillis = sleep?.endMillis,
            spo2Percent = agg.spo2Percent?.toFloat(),
            spo2MinPercent = agg.spo2MinPercent?.toFloat(),
            spo2MaxPercent = agg.spo2MaxPercent?.toFloat(),
            weightKg = agg.weightKg?.toFloat(),
            hydrationMl = agg.hydrationMl?.toFloat(),
            nutritionCalories = agg.nutritionCalories?.toFloat(),
            nutritionProteinGrams = agg.nutritionProteinGrams?.toFloat(),
            nutritionCarbsGrams = agg.nutritionCarbsGrams?.toFloat(),
            nutritionFatGrams = agg.nutritionFatGrams?.toFloat(),
            nutritionSourceApp = agg.nutritionSourceApp,
            updatedAt = now
        )
        val weightReadings = agg.weightReadings.map { w ->
            HealthWeightReading(
                id = w.id,
                localDate = d.format(ymd),
                atMillis = w.atMillis,
                weightKg = w.weightKg.toFloat(),
                sourceApp = w.sourceApp
            )
        }
        return DayFetchResult(healthDay, weightReadings, failed = false)
    }

    private fun setStatus(message: String, failed: Boolean) {
        stateStore.lastStatusMessage = message
        stateStore.lastStatusIsFailure = failed
    }

    fun statusLine(): String? = stateStore.lastStatusMessage
    fun statusIsFailure(): Boolean = stateStore.lastStatusIsFailure
    fun lastPullAtMillis(): Long = stateStore.lastPullAt

    private fun lastUpdatedRelative(): String {
        val at = stateStore.lastPullAt
        if (at <= 0) return "never"
        val minutes = (System.currentTimeMillis() - at) / 60000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
    }

    suspend fun grantedPermissions(): Set<String> =
        runCatchingCancellable { reader.grantedPermissions() }.getOrDefault(emptySet())

    fun missingPermissions(granted: Set<String>): Set<String> = HealthPermissions.missingPermissions(granted)

    /** H6 fix — whether the optional extras (`READ_HEALTH_DATA_HISTORY` /
     *  `READ_HEALTH_DATA_IN_BACKGROUND`) have already been asked for once, so the caller can
     *  launch that OS consent sheet at most once per §6.3's "never nag" rule rather than
     *  re-prompting on every "Import my past data" tap. */
    fun historyExtrasAlreadyRequested(): Boolean = stateStore.backgroundPermissionDenied
    fun markHistoryExtrasRequested() { stateStore.backgroundPermissionDenied = true }

    private companion object {
        const val TAG = "HealthRepository"
        const val RESUME_THROTTLE_MILLIS = 15 * 60 * 1000L
        const val BACKFILL_CAP_DAYS = 365L
        // See pullWindow's KDoc — bounds how many days' worth of Health Connect calls run
        // concurrently at once. High enough to meaningfully cut wall-clock time on a 30-day full
        // resync, low enough not to hammer the Health Connect provider with 30 simultaneous calls.
        const val DAY_FETCH_CONCURRENCY = 6
        val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    }
}
