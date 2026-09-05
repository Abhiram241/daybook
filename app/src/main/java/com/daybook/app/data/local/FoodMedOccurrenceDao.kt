package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.Occurrence
import kotlinx.coroutines.flow.Flow
import java.util.*

/** One row per task: the earliest still-pending occurrence at or after a reference time. */
data class TaskNextMillis(val taskId: String, val nextMillis: Long)

/** v0.5.3 Phase 3 (A4) — see [com.daybook.app.data.local.HabitSchedStatus]; food/med side. */
data class FoodMedSchedStatus(val scheduledFor: Long, val status: Occurrence.Status)

@Dao
interface FoodMedOccurrenceDao {
    @Query("SELECT task_id AS taskId, MIN(scheduled_for) AS nextMillis FROM food_med_occurrences WHERE status = 'PENDING' AND scheduled_for >= :now GROUP BY task_id")
    fun observeNextPendingMillis(now: Long): Flow<List<TaskNextMillis>>

    @Query("SELECT * FROM food_med_occurrences WHERE task_id = :taskId AND scheduled_for >= :startTime AND scheduled_for < :endTime ORDER BY scheduled_for")
    fun getOccurrencesForTaskInTimeRange(taskId: String, startTime: Long, endTime: Long): Flow<List<FoodMedOccurrence>>

    @Query("SELECT * FROM food_med_occurrences WHERE scheduled_for >= :startTime AND scheduled_for < :endTime ORDER BY scheduled_for")
    fun getAllOccurrencesInTimeRange(startTime: Long, endTime: Long): kotlinx.coroutines.flow.Flow<List<FoodMedOccurrence>>

    @Query("SELECT * FROM food_med_occurrences WHERE id = :occurrenceId")
    suspend fun getOccurrenceById(occurrenceId: String): FoodMedOccurrence?

    @Query("SELECT * FROM food_med_occurrences")
    fun getAllOccurrences(): Flow<List<FoodMedOccurrence>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(occurrence: FoodMedOccurrence): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg occurrences: FoodMedOccurrence): LongArray

    @Update
    suspend fun update(occurrence: FoodMedOccurrence)

    @Delete
    suspend fun delete(occurrence: FoodMedOccurrence)

    @Query("UPDATE food_med_occurrences SET status = :status, responded_at = :timestamp WHERE id = :occurrenceId")
    suspend fun updateStatusResponded(occurrenceId: String, status: String, timestamp: Long)

    /** v0.5.4: FOOD reply — the answer plus this log's red-flag marker and suspected trigger food.
     *  [redFlag] is the RedFlag name or NULL; [suspectedFood] is free text or NULL. */
    @Query("UPDATE food_med_occurrences SET response_text = :responseText, red_flag = :redFlag, " +
        "suspected_food = :suspectedFood, outside_food = :outsideFood, responded_at = :timestamp, " +
        "status = 'LOGGED' WHERE id = :occurrenceId")
    suspend fun logFoodResponse(
        occurrenceId: String,
        responseText: String,
        redFlag: String?,
        suspectedFood: String?,
        outsideFood: Boolean?,
        timestamp: Long
    )

    /** v0.5.4 Phase 2 (D3): JOURNAL fresh reply — the newline-joined answers plus the ordered
     *  `[{"q":…,"a":…}]` snapshot. `description` is retired for journals: set NULL going forward. */
    @Query("UPDATE food_med_occurrences SET response_text = :responseText, qa_json = :qaJson, " +
        "description = NULL, responded_at = :timestamp, status = 'LOGGED' WHERE id = :occurrenceId")
    suspend fun logJournalResponse(occurrenceId: String, responseText: String, qaJson: String?, timestamp: Long)

    /**
     * Journal Mode edit-in-place: re-save an ALREADY-RESOLVED journal entry. Updates ONLY the text
     * and the Q&A snapshot. Deliberately does NOT touch `responded_at`, `status`, `snooze_count`
     * or `scheduled_for` — an edit must not reorder the History timeline (sorted by responded_at) or
     * bump the month content-hash beyond the text change itself. `description` is retired -> NULL.
     */
    @Query("UPDATE food_med_occurrences SET response_text = :responseText, qa_json = :qaJson, " +
        "description = NULL WHERE id = :occurrenceId")
    suspend fun editJournalResponse(occurrenceId: String, responseText: String, qaJson: String?)

    /**
     * Journal Mode edit-in-place: re-save an ALREADY-RESOLVED FOOD/MED/CUSTOM log. Updates ONLY the
     * answer text and the per-log red-flag fields. Does NOT touch `responded_at` or `status` (see above).
     */
    @Query("UPDATE food_med_occurrences SET response_text = :responseText, red_flag = :redFlag, " +
        "suspected_food = :suspectedFood, outside_food = :outsideFood WHERE id = :occurrenceId")
    suspend fun editFoodResponse(
        occurrenceId: String,
        responseText: String,
        redFlag: String?,
        suspectedFood: String?,
        outsideFood: Boolean?
    )

    @Query("UPDATE food_med_occurrences SET snooze_count = snooze_count + 1 WHERE id = :occurrenceId")
    suspend fun bumpSnooze(occurrenceId: String)

    /** v0.5.3 undo: back to a blank PENDING slot — status, timestamp, snooze, AND the entered
     *  reply/description/qa_json are cleared so the reminder is fully blank again. Events kept. */
    @Query("UPDATE food_med_occurrences SET status = 'PENDING', responded_at = NULL, snooze_count = 0, " +
        "response_text = '', description = NULL, qa_json = NULL, red_flag = NULL, suspected_food = NULL, " +
        "outside_food = NULL WHERE id = :occurrenceId")
    suspend fun revertToPending(occurrenceId: String)

    // --- scheduling engine ---

    /**
     * Earliest PENDING occurrence at or after [floor]. The floor is what stops an old
     * unanswered row from head-blocking the queue forever (see NOTIFICATION_DEBUG.md).
     */
    @Query("SELECT * FROM food_med_occurrences WHERE task_id = :taskId AND status = 'PENDING' AND scheduled_for >= :floor ORDER BY scheduled_for LIMIT 1")
    suspend fun getNextPendingForTask(taskId: String, floor: Long): FoodMedOccurrence?

    /** Batch auto-skip: retires *every* stale pending row in one statement, not one per sync. */
    @Query("UPDATE food_med_occurrences SET status = 'SKIPPED' WHERE task_id = :taskId AND status = 'PENDING' AND scheduled_for < :before")
    suspend fun skipStaleForTask(taskId: String, before: Long)

    /**
     * Notification ids of the rows [skipStaleForTask] is about to retire. Read *before* the
     * auto-skip so their still-posted shade notifications can be cancelled — otherwise they are
     * orphaned forever: the refire path bails on any non-PENDING row, so nothing ever clears them.
     */
    @Query("SELECT notification_id FROM food_med_occurrences WHERE task_id = :taskId AND status = 'PENDING' AND scheduled_for < :before")
    suspend fun staleNotificationIdsForTask(taskId: String, before: Long): List<Int>

    @Query("DELETE FROM food_med_occurrences WHERE task_id = :taskId AND status = 'PENDING' AND scheduled_for >= :from")
    suspend fun deleteFuturePendingForTask(taskId: String, from: Long)

    /** v0.5.3 Phase 3 (A2) — see [HabitOccurrenceDao.deleteFuturePendingExcept]; food/med side. */
    @Query("DELETE FROM food_med_occurrences WHERE task_id = :taskId AND status = 'PENDING' AND scheduled_for >= :from AND id NOT IN (:keep)")
    suspend fun deleteFuturePendingExcept(taskId: String, from: Long, keep: List<String>)

    /** v0.5.3 Phase 3 (S15) — see [HabitOccurrenceDao.deleteByIds]; food/med side. */
    @Query("DELETE FROM food_med_occurrences WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** v0.5.1 §N — see [HabitOccurrenceDao.deleteInRange]; same contract, food/med side. */
    @Query("DELETE FROM food_med_occurrences WHERE scheduled_for >= :from AND scheduled_for < :until")
    suspend fun deleteInRange(from: Long, until: Long)

    /** v0.5.3 Phase 1 (S14) — see [HabitOccurrenceDao.deleteInRangeExcept]; food/med side. */
    @Query("DELETE FROM food_med_occurrences WHERE scheduled_for >= :from AND scheduled_for < :until AND id NOT IN (:keep)")
    suspend fun deleteInRangeExcept(from: Long, until: Long, keep: List<String>)

    /** v0.5.3 Phase 2 (S17) — see [HabitOccurrenceDao.deletePendingByLocalMonth]; food/med side. */
    @Query("DELETE FROM food_med_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix AND status = 'PENDING'")
    suspend fun deletePendingByLocalMonth(monthPrefix: String)

    /** v0.5.3 Phase 3 (S17) — see [HabitOccurrenceDao.deletePendingByLocalMonthBefore]; food/med side. */
    @Query("DELETE FROM food_med_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix AND status = 'PENDING' AND scheduled_for < :before AND id NOT IN (:keep)")
    suspend fun deletePendingByLocalMonthBefore(monthPrefix: String, before: Long, keep: List<String>)

    /** LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 6 (S-3) — see
     *  [HabitOccurrenceDao.pendingIdsByLocalMonthBefore]; food/med side. */
    @Query("SELECT id FROM food_med_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix AND status = 'PENDING' AND scheduled_for < :before")
    suspend fun pendingIdsByLocalMonthBefore(monthPrefix: String, before: Long): List<String>

    // --- Detail timeline paging (v0.5.3 Phase 3 A4) ---

    /** v0.5.3 Phase 3 (A4) — see [HabitOccurrenceDao.getTerminalPageForHabit]; food/med side. */
    @Query(
        "SELECT * FROM food_med_occurrences WHERE task_id = :taskId AND status != 'PENDING' " +
            "ORDER BY COALESCE(responded_at, scheduled_for) DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getTerminalPageForTask(taskId: String, limit: Int, offset: Int): List<FoodMedOccurrence>

    /** v0.5.3 Phase 3 (A4) — see [HabitOccurrenceDao.getScheduledStatusesForHabit]; food/med side. */
    @Query("SELECT scheduled_for AS scheduledFor, status AS status FROM food_med_occurrences WHERE task_id = :taskId")
    suspend fun getScheduledStatusesForTask(taskId: String): List<FoodMedSchedStatus>

    /** Full wipe — used only by the backup restore path (L4), inside its transaction. */
    @Query("DELETE FROM food_med_occurrences")
    suspend fun deleteAll()
}
