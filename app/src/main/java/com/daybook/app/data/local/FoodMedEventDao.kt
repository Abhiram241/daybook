package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.daybook.app.data.model.FoodMedEvent

@Dao
interface FoodMedEventDao {
    /**
     * v0.5.2 §2 (Problem 2): recent **activity** events only — SHOWN / USER_SNOOZED. Terminal
     * REPLIED / SKIPPED rows in the History timeline are now driven straight from the occurrence
     * list (uncapped), so a twice-daily reminder's SHOWN rows can no longer evict the logs from
     * the LIMIT budget. Capped separately.
     */
    // v0.5.3 Phase 2 (A4): straight indexed LIMIT off the denormalised `item_id` — no join to
    // food_med_occurrences. Backed by `index_food_med_events_item_id_timestamp`.
    @Query(
        "SELECT * FROM food_med_events " +
            "WHERE item_id = :taskId AND action IN ('SHOWN','USER_SNOOZED') " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getRecentActivityEventsForTask(taskId: String, limit: Int): List<FoodMedEvent>

    /** v0.5.1 §G/§H — see [HabitEventDao.hasShownEvent]; same guard, food/med side. */
    @Query("SELECT COUNT(*) > 0 FROM food_med_events WHERE occurrence_id = :occurrenceId AND action = 'SHOWN'")
    suspend fun hasShownEvent(occurrenceId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: FoodMedEvent): Long

    /** v0.5.3 Phase 3 (A3 / A6) — see [HabitEventDao.pruneActivityBefore]; food/med side. */
    @Query("DELETE FROM food_med_events WHERE action IN ('SHOWN','USER_SNOOZED') AND timestamp < :cutoff")
    suspend fun pruneActivityBefore(cutoff: Long)

    /** v0.5.3 Phase 3 (A3) — see [HabitEventDao.deleteForLocalMonth]; food/med side. Run BEFORE the
     *  occurrence delete — the subquery resolves against `food_med_occurrences`. */
    @Query("DELETE FROM food_med_events WHERE occurrence_id IN (SELECT id FROM food_med_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix)")
    suspend fun deleteForLocalMonth(monthPrefix: String)

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 14 (S-12) — see
     * [HabitEventDao.deleteForNullLocalDateInRange]; food/med side. The plan names only the habit
     * side, but `food_med_occurrences` has the identical pre-`MIGRATION_12_13` null-`local_date`
     * history, so the same fix is applied here too rather than leaving a known-identical bug
     * unfixed on the sibling table.
     */
    @Query("DELETE FROM food_med_events WHERE occurrence_id IN (SELECT id FROM food_med_occurrences WHERE local_date IS NULL AND scheduled_for >= :start AND scheduled_for < :end)")
    suspend fun deleteForNullLocalDateInRange(start: Long, end: Long)

    /** Full wipe — used only by the backup restore path (L4), inside its transaction. */
    @Query("DELETE FROM food_med_events")
    suspend fun deleteAll()
}
