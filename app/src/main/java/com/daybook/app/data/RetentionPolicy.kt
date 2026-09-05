package com.daybook.app.data

import com.daybook.app.data.model.Event

/**
 * v0.5.3 Phase 3 (A3 / A6): event-table retention.
 *
 * The two event tables (`habit_events`, `food_med_events`) are append-only and were never pruned —
 * an unbounded growth for a long-lived install, and worse for a signed-out user (A6) where no cloud
 * eviction ever runs. The retention sweep in `CloudSyncRepository.runMaintenance()` (driven by the
 * daily `WindowRefreshWorker`, which runs signed-out too) deletes only **activity** rows
 * (`SHOWN` / `USER_SNOOZED`) older than [RETENTION_DAYS].
 *
 * **Never** touched: terminal `REPLIED` / `COMPLETED` / `SKIPPED` events (that is logged history),
 * and no occurrence row of any kind. Events orphaned by a month eviction are cleaned separately, by
 * `ExportImportRepository.evictMonth`.
 */
const val RETENTION_DAYS: Int = 90

private const val DAY_MILLIS: Long = 86_400_000L

/** v0.5.3 Phase 3 (A3): the retention cutoff — activity rows with `timestamp` before this are prunable. */
internal fun retentionCutoffMillis(nowMillis: Long, retentionDays: Int = RETENTION_DAYS): Long =
    nowMillis - retentionDays * DAY_MILLIS

/**
 * v0.5.3 Phase 3 (A3): pure guard behind the retention sweep. An event row is prunable **only** when
 * it is a no-content activity row (`SHOWN` / `USER_SNOOZED`) **and** older than [retentionDays].
 * A terminal `REPLIED` / `COMPLETED` / `SKIPPED` row is never prunable, at any age. Pure —
 * [com.daybook.app.data.RetentionSweepTest].
 */
internal fun isPrunableActivity(action: Event.Action, ageMillis: Long, retentionDays: Int): Boolean {
    val isActivity = action == Event.Action.SHOWN || action == Event.Action.USER_SNOOZED
    return isActivity && ageMillis > retentionDays * DAY_MILLIS
}
