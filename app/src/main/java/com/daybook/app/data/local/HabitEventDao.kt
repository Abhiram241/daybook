package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.HabitEvent

@Dao
interface HabitEventDao {
    /**
     * v0.5.2 §2 (Problem 2): recent **activity** events only — SHOWN / USER_SNOOZED. The terminal
     * COMPLETED / SKIPPED rows in the History timeline are now driven straight from the occurrence
     * list (uncapped), so these no-content rows no longer compete with logged history for the
     * same LIMIT budget and can no longer evict it. Capped separately.
     */
    // v0.5.3 Phase 2 (A4): straight indexed LIMIT off the denormalised `item_id` — no join to
    // habit_occurrences, no O(occurrences-for-item) probe + temp B-tree. Backed by
    // `index_habit_events_item_id_timestamp`.
    @Query(
        "SELECT * FROM habit_events " +
            "WHERE item_id = :habitId AND action IN ('SHOWN','USER_SNOOZED') " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getRecentActivityEventsForHabit(habitId: String, limit: Int): List<HabitEvent>

    /**
     * v0.5.1 §G/§H: has this occurrence already been surfaced once?
     *
     * `SHOWN` is inserted by [com.daybook.app.util.alarm.AlarmReceiver] on the first (non-refire)
     * fire only, so this is the "already fired" flag — carried by the events table rather than a
     * new occurrence column, which would mean a v8 migration. Two uses:
     *   1. [com.daybook.app.data.OccurrenceScheduler] refuses to re-arm a past occurrence that
     *      already has one (the refire chain is already carrying it);
     *   2. `AlarmReceiver` refuses to insert a second one, so the Detail screen's Activity list
     *      stays honest even if some future path re-arms wrongly.
     */
    @Query("SELECT COUNT(*) > 0 FROM habit_events WHERE occurrence_id = :occurrenceId AND action = 'SHOWN'")
    suspend fun hasShownEvent(occurrenceId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: HabitEvent): Long

    /**
     * v0.5.3 Phase 3 (A3 / A6): retention sweep. Deletes only no-content **activity** rows
     * (`SHOWN` / `USER_SNOOZED`) older than the cutoff. Terminal `REPLIED` / `COMPLETED` /
     * `SKIPPED` rows are logged history and are never touched, at any age. Called from
     * `CloudSyncRepository.runMaintenance()` (daily worker, signed-out users included).
     */
    @Query("DELETE FROM habit_events WHERE action IN ('SHOWN','USER_SNOOZED') AND timestamp < :cutoff")
    suspend fun pruneActivityBefore(cutoff: Long)

    /**
     * v0.5.3 Phase 3 (A3): drop every event belonging to an occurrence in the given local month
     * ("yyyy-MM" prefix of the occurrence's stored `local_date`). Used by
     * `ExportImportRepository.evictMonth` so an evicted month's events do not outlive it and grow
     * unbounded. Must run BEFORE the occurrence rows are deleted — the subquery resolves against
     * them.
     */
    @Query("DELETE FROM habit_events WHERE occurrence_id IN (SELECT id FROM habit_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix)")
    suspend fun deleteForLocalMonth(monthPrefix: String)

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 14 (S-12, Low): [deleteForLocalMonth]'s `local_date`
     * match can never catch a pre-`MIGRATION_12_13` occurrence whose `local_date` is NULL (the
     * column didn't exist when that row was created, and it is never backfilled) — such a row's
     * events survive every future `evictMonth` call for any month, forever, orphaned the moment
     * `HabitOccurrenceDao.deleteInRange`'s epoch-range delete removes the occurrence itself. This
     * `scheduled_for`-range fallback catches exactly that case; `ExportImportRepository.evictMonth`
     * calls it alongside `deleteForLocalMonth`, before the occurrence delete, same ordering
     * requirement.
     */
    @Query("DELETE FROM habit_events WHERE occurrence_id IN (SELECT id FROM habit_occurrences WHERE local_date IS NULL AND scheduled_for >= :start AND scheduled_for < :end)")
    suspend fun deleteForNullLocalDateInRange(start: Long, end: Long)

    /** Full wipe — used only by the backup restore path (L4), inside its transaction. */
    @Query("DELETE FROM habit_events")
    suspend fun deleteAll()
}
