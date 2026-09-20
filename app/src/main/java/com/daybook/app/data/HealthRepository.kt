package com.daybook.app.data

import android.util.Log
import com.daybook.app.data.health.HealthConnectReader
import com.daybook.app.data.health.HealthPermissions
import com.daybook.app.data.health.HealthSourcePriority
import com.daybook.app.data.health.HealthSyncStateStore
import com.daybook.app.data.health.automaticWindowFloor as computeAutomaticWindowFloor
import com.daybook.app.data.health.isEmptyHealthDay
import com.daybook.app.data.health.mergeHealthDay
import com.daybook.app.data.health.sameHealthDayPayload
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
import java.util.concurrent.TimeoutException
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val stateStore: HealthSyncStateStore,
    private val appSettingsRepository: AppSettingsRepository
) {
    private val zoneId: ZoneId get() = ZoneId.systemDefault()
    private val ymd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    sealed class PullOutcome {
        data class Success(val hasNewData: Boolean) : PullOutcome()
        data class Failure(val message: String) : PullOutcome()
        object PermissionsMissing : PullOutcome()
    }

    /** HC-3 fix — carries a [pullWindow] outcome's counts through `pull()`'s branching so the
     *  final status line can distinguish "everything worked" from "some days failed" instead of
     *  collapsing both into the same success message. */
    private data class PullAttempt(val hasNewData: Boolean, val daysFailed: Int = 0, val totalDays: Int = 0)

    /** HC-2 fix — a stable, order-independent encoding of a granted-permission set for comparing
     *  against [HealthSyncStateStore.grantedScope]. */
    private fun encodeScope(granted: Set<String>): String = granted.sorted().joinToString(",")

    /**
     * Item 5 stage 1 fix (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — every automatic pull window used
     * to hard-floor at exactly `today.minusDays(29)`, so once a deeper backfill had happened (via
     * [importPastData]) the very next ordinary pull re-narrowed straight back to 30 days — nothing
     * about the deeper history was ever remembered. This is the floor every automatic window
     * (first pull, expired/forced-resync, and the delta branch's trailing re-read) now uses
     * instead: whichever is EARLIER of the flat 30-day default or [HealthSyncStateStore
     * .backfillThroughMillis] (0 before any backfill has ever happened, in which case this is
     * simply the flat default) —
     *
     * B-1 fix (AUDIT_2026-09-20.md) — but with NO cap, that "earlier of" made one deep
     * [importPastData] permanently turn every later automatic resync (an expired changes token,
     * a scope grow, day 1's very first pull) into a ~365-day, ~1,825-Health-Connect-call pass for
     * the rest of the install's life. [com.daybook.app.data.health.automaticWindowFloor] now caps
     * how deep an AUTOMATIC window can go at [AUTOMATIC_WINDOW_CAP_DAYS] regardless of how deep an
     * explicit import previously reached — only `importPastData` itself reads past that cap.
     *
     * D-2 fix (the other half) — also collapses to the flat 30-day default whenever
     * `READ_HEALTH_DATA_HISTORY` isn't currently held: Health Connect silently caps every read at
     * ~30 days on its own without that permission, so asking for more only burned Health Connect
     * IPC calls and Room writes for days that always come back empty.
     */
    private fun automaticWindowFloor(today: LocalDate, hasHistoryAccess: Boolean): LocalDate {
        val backfillThrough = stateStore.backfillThroughMillis
        val backfillDate = if (backfillThrough > 0) {
            Instant.ofEpochMilli(backfillThrough).atZone(zoneId).toLocalDate()
        } else null
        return computeAutomaticWindowFloor(
            today = today,
            backfillThrough = backfillDate,
            hasHistoryAccess = hasHistoryAccess,
            capDays = AUTOMATIC_WINDOW_CAP_DAYS
        )
    }

    /** Item 5 stage 1 fix — records the deepest date any pull has actually reached, so
     *  [automaticWindowFloor] can use it later. Only ever moves earlier/deeper, never forward —
     *  a narrow delta re-read must not make the app "forget" a deeper backfill that already
     *  happened. */
    private fun recordBackfillDepth(from: LocalDate) {
        val newMillis = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val existing = stateStore.backfillThroughMillis
        if (existing <= 0 || newMillis < existing) stateStore.backfillThroughMillis = newMillis
    }

    /** Repository-scope coroutine for fire-and-forget background work ([backfillActivitySourceApps])
     *  that must not block or compete with a foreground pull — a `SupervisorJob` so one failure
     *  can't cancel anything else launched on it, and this repository is itself a `@Singleton`. */
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** §6.3 cadence 1 — on app resume, throttled to at most once every 15 minutes. */
    suspend fun pullOnResume(): PullOutcome? {
        val now = System.currentTimeMillis()
        if (now - stateStore.lastPullAt < RESUME_THROTTLE_MILLIS) return null
        val outcome = pullAutomatic(isDailyWorker = false)
        // [owner feedback, 2026-09-20] — launched AFTER the normal resume pull completes, and never
        // awaited, so the one-time historical `activity_source_apps` backfill never competes with
        // or blocks the foreground sync (see `backfillActivitySourceApps`'s own KDoc).
        repoScope.launch { runCatchingCancellable { backfillActivitySourceApps() } }
        return outcome
    }

    /** §6.3 cadence 2 — folded into the existing `WindowRefreshWorker`, no new periodic job. */
    suspend fun pullDaily(): PullOutcome = pullAutomatic(isDailyWorker = true)

    /** B-1 fix — the shared entry point for both automatic cadences: a hard timeout, matching
     *  `HealthTabViewModel.refreshNow`'s own 20 s `withTimeoutOrNull` for the manual path. Without
     *  it, an unbounded window (a first pull, an expired token, a scope grow) could plausibly run
     *  past WorkManager's 10-minute execution ceiling, get stopped mid-pass, and retry into the
     *  same expensive pass again — the "app ate my battery overnight" shape the audit called out. */
    private suspend fun pullAutomatic(isDailyWorker: Boolean): PullOutcome {
        val result = withTimeoutOrNull(AUTOMATIC_PULL_TIMEOUT_MILLIS) {
            pull(isManual = false, isDailyWorker = isDailyWorker)
        }
        if (result != null) return result
        Log.e(TAG, "automatic pull timed out after ${AUTOMATIC_PULL_TIMEOUT_MILLIS}ms")
        recordUnhandledException(TimeoutException("HealthRepository automatic pull timed out"))
        val msg = "Couldn't refresh your health data. Last updated ${lastUpdatedRelative()}."
        setStatus(msg, failed = true)
        return PullOutcome.Failure(msg)
    }

    /** §6.3's manual "Refresh now" row. */
    suspend fun refreshNow(): PullOutcome = pull(isManual = true, isDailyWorker = false)

    /** [owner feedback, 2026-09-20] — one-time backfill so every already-synced day (not just days
     *  touched by a future resync) gets `activity_source_apps` populated, without reopening B-1's
     *  unbounded-window bug (`HEALTH_SYNC_BATTERY_AND_IMPORT_AUDIT_2026-09-20.md` §1.2). Bounded to
     *  exactly the local date range `health_days` already has rows for (`healthDao.earliestLocalDate()`
     *  to today), i.e. it never reads further back than the user's own history already covers, and it
     *  reuses the same `pullWindow` merge-aware upsert (`mergeHealthDay`) so it can only ever fill in
     *  a null `activitySourceApps`, never touch/overwrite any other field. Runs once, gated by
     *  `app_settings.activity_source_backfill_done` so it is never repeated on every app start. */
    suspend fun backfillActivitySourceApps() {
        val settings = appSettingsRepository.getSettings()
        if (settings.activitySourceBackfillDone) return
        val earliest = database.healthDao().earliestLocalDate()
        if (earliest == null) {
            appSettingsRepository.setActivitySourceBackfillDone(true)
            return
        }
        val start = LocalDate.parse(earliest)
        val today = LocalDate.now(zoneId)
        val granted = runCatchingCancellable { reader.grantedPermissions() }.getOrElse { emptySet() }
        if (granted.isNotEmpty()) {
            runCatchingCancellable { pullWindow(start, today, granted) }
        }
        appSettingsRepository.setActivitySourceBackfillDone(true)
    }

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
        val result = runCatchingCancellable {
            val r = pullWindow(from, today, granted)
            r.requireNotFullyFailed()
            r
        }
        return result.fold(
            onSuccess = { r ->
                // HC-3 fix — only stamp `backfillThroughMillis` (which `importPastData`'s own
                // success copy and, per item 5, the automatic window's floor both rely on) for a
                // window that actually wrote something.
                //
                // D-4 fix — this used to run unconditionally: `daysWritten` counted a day as
                // "written" whenever its Health Connect read merely SUCCEEDED, even if it came back
                // completely empty (D-2's bug), so a 365-day import that found nothing at all still
                // stamped the 365-day floor and reported "Imported your health history back to …".
                // `daysWithRealData` (HealthSyncLogic.isEmptyHealthDay) only counts a day that
                // actually carried at least one real value.
                val wroteRealData = r.daysWithRealData > 0
                if (wroteRealData) {
                    stateStore.backfillThroughMillis = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
                stateStore.lastPullAt = System.currentTimeMillis()
                val partial = r.daysFailed > 0
                val msg = when {
                    partial -> "Imported partially — ${r.daysFailed} of ${r.totalDays} days couldn't be read. Try again later to fill in the rest."
                    !wroteRealData -> "Nothing new to import — Health Connect has no data further back than what Daybook already has."
                    hasHistoryAccess -> "Imported your health history back to ${from.format(DISPLAY_DATE)}."
                    else -> "Imported the last 30 days. Daybook needs access to your history to go further back."
                }
                setStatus(msg, failed = partial)
                PullOutcome.Success(hasNewData = wroteRealData)
            },
            onFailure = { t ->
                Log.e(TAG, "importPastData failed", t); recordUnhandledException(t)
                setStatus("Couldn't import your past data. Try again.", failed = true)
                PullOutcome.Failure("Couldn't import your past data. Try again.")
            }
        )
    }

    /** B-2 fix — [isDailyWorker] gates the delta branch's trailing 7-day back-sync sweep (only the
     *  once-a-day worker pays for it now; the 15-minute resume pull and manual "Refresh now" both
     *  cover exactly `lastPull → today`, no artificial floor). */
    private suspend fun pull(isManual: Boolean, isDailyWorker: Boolean): PullOutcome {
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
        // D-2 fix — the automatic window floor now respects this too (previously only
        // `importPastData` checked it); see `automaticWindowFloor`'s KDoc.
        val hasHistoryAccess = HealthPermissions.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        // User bug report — force exactly one full 30-day re-aggregate after the sleep-dedup fix
        // shipped, the same way a first-ever pull or an expired token already does below, so a day
        // synced BEFORE the fix (with the old, inflated sleep total) actually gets recomputed
        // instead of sitting stale in Room forever (see `sleepDedupResyncPending`'s KDoc).
        val forcedResync = stateStore.sleepDedupResyncPending
        val token = if (forcedResync) null else stateStore.changesToken
        // HC-2 fix — a token minted with a narrower granted set than we hold right now is stale in
        // scope even if Health Connect doesn't consider it expired: `reader.changes(token)` only
        // ever reports changes within the scope it was minted for, so a newly-shared data type
        // would otherwise produce nothing until the token happens to expire on its own. Treated
        // exactly like an expired token below. See `grantedScope`'s KDoc for why a null stored
        // scope does NOT force this (avoids double-forcing a resync on top of `forcedResync`).
        val scopeGrew = !forcedResync && token != null &&
            stateStore.grantedScope != null && stateStore.grantedScope != encodeScope(granted)
        val outcome: Result<PullAttempt> = if (token == null) {
            // First pull ever, or a forced one-time resync — the automatic window floors at
            // whichever is deeper of 30 days or a prior backfill (item 5 stage 1 fix).
            runCatchingCancellable {
                val from = automaticWindowFloor(today, hasHistoryAccess)
                val r = pullWindow(from, today, granted)
                r.requireNotFullyFailed()
                recordBackfillDepth(from)
                // H1 fix — this branch never minted a token, so `stateStore.changesToken` stayed
                // null forever and every subsequent pull re-entered this same 30-day full-resync
                // branch for the life of the install. Mint it now, exactly like the expired-token
                // branch below already does, WITHOUT clearing anything on failure (`getOrNull()`
                // just leaves it null, same as it already was).
                val minted = runCatchingCancellable { reader.changesToken(granted) }.getOrNull()
                stateStore.changesToken = minted
                if (minted != null) stateStore.grantedScope = encodeScope(granted)
                if (forcedResync) stateStore.sleepDedupResyncPending = false
                PullAttempt(hasNewData = r.anyChanged, daysFailed = r.daysFailed, totalDays = r.totalDays)
            }
        } else {
            runCatchingCancellable {
                // Bug fix — user report: a device with even one of the 12 MVP read permissions
                // NOT granted (e.g. "Total calories burned" left off) hard-failed every refresh
                // ("Couldn't refresh your health data right now") forever, despite `dayAggregate`
                // already correctly degrading to "that metric is absent" for a partial grant.
                // Root cause: `reader.changes(token)` throws `SecurityException` if the token's
                // scope covers a record type whose read permission isn't currently held — and the
                // token used to always be minted covering all 12 types regardless of what was
                // granted at mint time (see `HealthConnectReader.changesToken`'s KDoc for the mint
                // side of this fix). Guard the call itself and treat a throw exactly like an
                // expired token — this is self-healing even for a token minted before this fix
                // shipped: the very next pull falls back to a full re-read and mints a fresh,
                // correctly-scoped token instead of re-throwing forever.
                val changesResult = if (scopeGrew) Result.success(null) else runCatchingCancellable { reader.changes(token) }
                changesResult.exceptionOrNull()?.let { t -> Log.w(TAG, "reader.changes(token) threw, treating as expired", t) }
                val changes = changesResult.getOrNull()
                when {
                    changes == null || changes.expired -> {
                        // Silent-but-visible fallback (§6.3): re-read the last 30 days (or deeper,
                        // per item 5 stage 1's `automaticWindowFloor`), mint a new token, and
                        // record when this happened WITHOUT clearing the old token first — if the
                        // re-read below throws, the old (still-bad) token stays in place rather
                        // than being nulled out, which would otherwise force a full re-read on
                        // every subsequent pull forever (a silent battery regression, §6.3).
                        val from = automaticWindowFloor(today, hasHistoryAccess)
                        val r = pullWindow(from, today, granted)
                        r.requireNotFullyFailed()
                        recordBackfillDepth(from)
                        stateStore.lastFullResyncAt = System.currentTimeMillis()
                        // L2 fix — `.getOrNull()` used to clobber the token to null on a transient
                        // mint failure, contradicting this very comment's "without clearing the
                        // old token first" — that's the one way the system could re-enter H1's
                        // permanent-full-resync state after being fixed. Only overwrite on an
                        // actual new token; a mint failure now leaves the old, still-bad token in
                        // place (still forces one more full re-read next time, but doesn't null it
                        // out for good).
                        runCatchingCancellable { reader.changesToken(granted) }.getOrNull()?.let {
                            stateStore.changesToken = it
                            stateStore.grantedScope = encodeScope(granted)
                        }
                        PullAttempt(hasNewData = r.anyChanged, daysFailed = r.daysFailed, totalDays = r.totalDays)
                    }
                    changes.hasChanges -> {
                        // Narrow the re-read to since the last successful pull rather than
                        // replaying every changed record individually — see
                        // HealthConnectReader.changes' KDoc for why.
                        //
                        // Item 5 stage 1 fix (bonus bug found during the plan's own investigation,
                        // confidence: high) — `since` used to only ever move FORWARD from the last
                        // pull date, so a backdated write (a band back-syncing sleep/steps for a
                        // few days ago after being offline, or on first pairing) landing more than
                        // a day behind that boundary was silently never re-read, and never would
                        // be, because `since` only advances. The cheap stop-gap the plan calls out
                        // (over the fuller fix of Health Connect surfacing exactly which records
                        // changed): always re-read AT LEAST the trailing 7 days regardless of how
                        // recent the last pull was, which catches the common back-sync case.
                        //
                        // B-2 fix — that 7-day floor used to apply unconditionally, so it fired on
                        // EVERY delta pull that found any change at all — including the 15-minute
                        // resume pull, up to 4×/hour of app use, and Daybook's own hydration writes
                        // guarantee `hasChanges == true` on the very next pull. Now only the daily
                        // worker (`isDailyWorker`) pays for the wider back-sync sweep; resume/manual
                        // pulls cover exactly `lastPull → today`.
                        val lastPull = stateStore.lastPullAt
                        val sinceLastPull = if (lastPull <= 0) today.minusDays(1)
                        else Instant.ofEpochMilli(lastPull).atZone(zoneId).toLocalDate()
                        val since = (if (isDailyWorker) minOf(sinceLastPull, today.minusDays(7)) else sinceLastPull)
                            .coerceAtLeast(automaticWindowFloor(today, hasHistoryAccess))
                        val r = pullWindow(since, today, granted)
                        r.requireNotFullyFailed()
                        stateStore.changesToken = changes.nextToken ?: token
                        PullAttempt(hasNewData = r.anyChanged, daysFailed = r.daysFailed, totalDays = r.totalDays)
                    }
                    else -> {
                        stateStore.changesToken = changes.nextToken ?: token
                        PullAttempt(hasNewData = false)
                    }
                }
            }
        }

        return outcome.fold(
            onSuccess = { attempt ->
                stateStore.lastPullAt = System.currentTimeMillis()
                val partial = attempt.daysFailed > 0
                when {
                    // HC-3 fix — this used to be unreachable: a window where every day failed but
                    // the (usually-trivial) session read succeeded reported success unconditionally.
                    // Now a genuine partial failure is surfaced instead of a blanket "Last updated
                    // just now.", on both the manual and automatic paths.
                    partial -> setStatus(
                        "Partially updated — ${attempt.daysFailed} of ${attempt.totalDays} days couldn't be read.",
                        failed = true
                    )
                    isManual -> setStatus(
                        if (attempt.hasNewData) "Last updated just now." else "Up to date — nothing new from your band.",
                        failed = false
                    )
                    else -> setStatus("Last updated just now.", failed = false)
                }
                PullOutcome.Success(attempt.hasNewData)
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
    /** HC-3 fix (AUDIT_2026-09-16.md) — counts, not a boolean/throw. This used to throw only when
     *  `totalDays > 0 && failedDays == totalDays && sessionsFailed` — requiring the (usually
     *  trivially-true, since an empty list is "success") session read to *also* fail — so a window
     *  where every single day's aggregate failed but the session read happened to return
     *  normally (empty) was reported as a clean success: `pull()` advanced `lastPullAt`, rolled the
     *  changes token forward, and showed "Last updated just now." with zero rows written, and the
     *  failed days were never retried because the token had already moved past them. The caller now
     *  decides what a given count means (hard failure vs a partial success worth telling the user
     *  about) instead of this function silently declaring success or unconditionally throwing. */
    /** D-4 fix — [daysWithRealData] is [totalDays]'s "actually carried a value" subset (a day
     *  whose read merely succeeded-but-empty, per D-2, doesn't count) — the correct signal for
     *  "did this window's import genuinely reach further back than before", as opposed to
     *  [daysWritten]'s "did this day's Health Connect call not throw" (still the right signal for
     *  [requireNotFullyFailed]'s hard-failure check).
     *  B-3 fix — [anyChanged] is true only if something in Room actually changed: a day whose
     *  merged payload came out byte-identical to what was already stored (ignoring `updatedAt`) is
     *  not upserted at all and does not count. */
    private data class PullWindowResult(
        val totalDays: Int,
        val daysWritten: Int,
        val daysFailed: Int,
        val daysWithRealData: Int,
        val anyChanged: Boolean,
        val sessionsFailed: Boolean
    )

    /** Owner feedback round 2 (2026-09-20) — the resolved package(s) to restrict each individual
     *  data point's Health Connect read to for this pull pass. Null = no restriction (only possible
     *  for "auto" with nothing observed yet, e.g. the very first-ever sync). */
    private data class SourcePreferences(
        val steps: List<String>, val distance: List<String>, val calories: List<String>, val heartRate: List<String>,
        val spo2: List<String>, val hydration: List<String>, val weight: List<String>, val nutrition: List<String>
    )

    /** Auto-resolution needs a candidate list before any preference exists — reuses the *previous*
     *  stored day's `activitySourceApps` CSV (already persisted by a prior unrestricted read) as
     *  the candidate pool, since by the time a second sync happens there is already a stored day to
     *  look at. For the very first-ever sync (nothing stored yet), this resolves to null for every
     *  family, which `dayAggregate` treats as "no restriction" — a one-time bootstrap read, not the
     *  steady state the owner is reporting on; that first unrestricted read's `activitySourceApps`
     *  then seeds every subsequent auto-resolution. */
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

    /** Owner feedback round 3 (2026-09-20) — "the data source and priority I choose must change
     *  config for now and for prev days also". Re-aggregates every already-synced `health_days` row
     *  (bounded to `healthDao.earliestLocalDate()..today`, exactly like `backfillActivitySourceApps`)
     *  using whatever source preferences are CURRENTLY stored — i.e. call this AFTER saving the new
     *  ranked list. Recomputes every metric for the window for simplicity/correctness — safe because
     *  `mergeHealthDay` + `sameHealthDayPayload` make an unchanged metric a no-op write. Touches ONLY
     *  `health_days`/`health_sessions`/`health_weight_readings`. */
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

    private suspend fun pullWindow(from: LocalDate, to: LocalDate, granted: Set<String>): PullWindowResult {
        val now = System.currentTimeMillis()
        val allDates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        // Resolved once per pullWindow pass, not once per day — the user's source preference (or
        // the auto-picked default) doesn't change within one sync pass.
        val prefs = resolvedSourcePreferences()
        var totalDays = 0
        var failedDays = 0
        var daysWithRealData = 0
        var daysChanged = 0
        var weightReadingsWritten = false
        val fetchedWeightIds = HashSet<String>()

        // B-3/D-2 fix — the window's existing rows are read once, up front, instead of a query per
        // day: (1) lets each incoming day be merge-aware-upserted (D-2 — a metric this read didn't
        // return keeps whatever was already stored, instead of REPLACE nulling it out), and (2)
        // lets a day whose merged payload is byte-identical to storage skip the upsert entirely
        // (B-3 — no SQLite write, no InvalidationTracker fan-out, no debounced full-DB export for a
        // re-read that changed nothing). Kept up to date as each batch below writes, so a date
        // can't appear twice in `allDates` and race its own prior batch.
        val existingByDate = database.healthDao()
            .getDaysInRange(from.format(ymd), to.format(ymd))
            .associateByTo(HashMap()) { it.localDate }

        for (batch in allDates.chunked(DAY_FETCH_CONCURRENCY)) {
            val results = coroutineScope {
                batch.map { d -> async { fetchDay(d, granted, now, prefs) } }.awaitAll()
            }
            totalDays += results.size
            failedDays += results.count { it.failed }
            daysWithRealData += results.count { r -> r.day?.let { !isEmptyHealthDay(it) } == true }
            val batchDays = results.mapNotNull { it.day }
                .map { day -> mergeHealthDay(existingByDate[day.localDate], day) }
                .filterNot { merged -> existingByDate[merged.localDate]?.let { sameHealthDayPayload(it, merged) } == true }
            batchDays.forEach { existingByDate[it.localDate] = it }
            daysChanged += batchDays.size
            val batchWeightReadings = results.flatMap { it.weightReadings }
            if (batchDays.isNotEmpty()) database.healthDao().upsertDays(batchDays)
            if (batchWeightReadings.isNotEmpty()) {
                database.healthDao().upsertWeightReadings(batchWeightReadings)
                weightReadingsWritten = true
                batchWeightReadings.forEach { fetchedWeightIds += it.id }
            }
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
        // D-3 fix — `sessionIdsStartingBetween` used to have zero call sites: a session deleted
        // upstream (in the band app) or re-synced under a new Health Connect id stayed in Room
        // forever. Only safe to conclude "gone" when this window's own session read actually
        // succeeded — a failed read must not be read as "nothing here anymore".
        if (!sessionsFailed) {
            val existingSessionIds = database.healthDao()
                .sessionIdsStartingBetween(startInstant.toEpochMilli(), endInstant.toEpochMilli())
            val staleSessionIds = existingSessionIds - sessions.map { it.id }.toSet()
            if (staleSessionIds.isNotEmpty()) database.healthDao().deleteSessionsByIds(staleSessionIds)
        }
        // D-3 fix — same reconciliation for weight readings, gated on the whole window's day reads
        // having succeeded (a per-day aggregate failure means that day's readings were never
        // re-observed this pass, so its ids must not be treated as "gone").
        if (failedDays == 0) {
            val existingWeightIds = database.healthDao()
                .weightReadingIdsBetween(startInstant.toEpochMilli(), endInstant.toEpochMilli())
            val staleWeightIds = existingWeightIds - fetchedWeightIds
            if (staleWeightIds.isNotEmpty()) database.healthDao().deleteWeightReadingsByIds(staleWeightIds)
        }

        return PullWindowResult(
            totalDays = totalDays,
            daysWritten = totalDays - failedDays,
            daysFailed = failedDays,
            daysWithRealData = daysWithRealData,
            anyChanged = daysChanged > 0 || sessions.isNotEmpty() || weightReadingsWritten,
            sessionsFailed = sessionsFailed
        )
    }

    /** HC-3 fix — the caller-side policy `pullWindow` no longer applies itself: a window where
     *  NOTHING was written is a hard failure (thrown, so the existing `runCatchingCancellable`
     *  wrapping's `onFailure` branch fires and neither `lastPullAt` nor the changes token move); a
     *  window where SOME days failed is a partial success worth telling the user about, not a
     *  silent "Last updated just now." */
    private fun PullWindowResult.requireNotFullyFailed() {
        if (totalDays > 0 && daysWritten == 0) {
            throw IllegalStateException("Every day in the requested window failed to aggregate")
        }
    }

    /** One day's worth of [pullWindow] work, extracted so it can run concurrently with its
     *  siblings inside a batch — see [pullWindow]'s KDoc for why. Same per-day error handling as
     *  before (H2/H3): an aggregate failure degrades this one day to "absent", never throws out. */
    private data class DayFetchResult(val day: HealthDay?, val weightReadings: List<HealthWeightReading>, val failed: Boolean)

    private suspend fun fetchDay(d: LocalDate, granted: Set<String>, now: Long, prefs: SourcePreferences): DayFetchResult {
        val aggResult = runCatchingCancellable {
            reader.dayAggregate(
                d, zoneId, granted,
                prefs.steps, prefs.distance, prefs.calories, prefs.heartRate, prefs.spo2, prefs.hydration,
                prefs.weight, prefs.nutrition
            )
        }
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
            activitySourceApps = agg.activitySourceApps.takeIf { it.isNotEmpty() }?.joinToString(","),
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

    /** H6 fix — whether the optional extras (`READ_HEALTH_DATA_HISTORY`, per B-5 the only one
     *  requested any more) have already been asked for once, so the caller can launch that OS
     *  consent sheet at most once per §6.3's "never nag" rule rather than re-prompting on every
     *  "Import my past data" tap. */
    fun historyExtrasAlreadyRequested(): Boolean = stateStore.backgroundPermissionDenied
    fun markHistoryExtrasRequested() { stateStore.backgroundPermissionDenied = true }

    /** Owner feedback round 2 (2026-09-20) — the distinct Health Connect source packages for ONE
     *  data point's "Data source priority" settings picker, discovered via an on-demand UNFILTERED
     *  read (see [HealthConnectReader.discoverSourcePackages]'s KDoc for why this replaced the old
     *  `recentSourcePackages()`, which derived its list from the same narrowed `dataOriginFilter`
     *  the resolved preference restricts — self-blinding the app to any other source Health Connect
     *  still actually has data for, which is exactly the "I don't see Mi Fitness" report's root
     *  cause). Cheap by design: only called when a picker sheet is opened. */
    suspend fun discoverSourcePackages(field: HealthConnectReader.SourceField): List<String> {
        val granted = grantedPermissions()
        return runCatchingCancellable { reader.discoverSourcePackages(field, granted) }
            .getOrElse { emptySet() }
            .sorted()
    }

    private companion object {
        const val TAG = "HealthRepository"
        const val RESUME_THROTTLE_MILLIS = 15 * 60 * 1000L
        const val BACKFILL_CAP_DAYS = 365L
        // B-1 fix — see `automaticWindowFloor`'s KDoc: how deep an AUTOMATIC (non-import) pull is
        // ever allowed to go, regardless of how deep a prior explicit import reached.
        const val AUTOMATIC_WINDOW_CAP_DAYS = 90L
        // B-1 fix — matches HealthTabViewModel.refreshNow's own manual-path timeout.
        const val AUTOMATIC_PULL_TIMEOUT_MILLIS = 20_000L
        // See pullWindow's KDoc — bounds how many days' worth of Health Connect calls run
        // concurrently at once. High enough to meaningfully cut wall-clock time on a 30-day full
        // resync, low enough not to hammer the Health Connect provider with 30 simultaneous calls.
        const val DAY_FETCH_CONCURRENCY = 6
        val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    }
}
