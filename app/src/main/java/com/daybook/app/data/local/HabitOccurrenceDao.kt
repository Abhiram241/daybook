package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.Occurrence
import kotlinx.coroutines.flow.Flow
import java.util.*

/** One row per habit: the earliest still-pending occurrence at or after a reference time. */
data class HabitNextMillis(val habitId: String, val nextMillis: Long)

/**
 * v0.5.3 Phase 3 (A4): a two-column projection of an occurrence — everything the Detail stats fold
 * (`computeStats` + the streak maths) needs, with no `TimelineEvent` allocation and no full-row
 * read. Lets the timeline itself be paged while the stats still see the whole history.
 */
data class HabitSchedStatus(val scheduledFor: Long, val status: Occurrence.Status)

@Dao
interface HabitOccurrenceDao {

    /**
     * Reactive "next reminder" millis for every habit in one grouped query — replaces the
     * per-habit [getNextPendingForHabit] N+1 in the Habits list (re-emits on occurrence writes).
     * Mirrors [FoodMedOccurrenceDao.observeNextPendingMillis].
     */
    @Query("SELECT habit_id AS habitId, MIN(scheduled_for) AS nextMillis FROM habit_occurrences WHERE status = 'PENDING' AND scheduled_for >= :now GROUP BY habit_id")
    fun observeNextPendingMillisByHabit(now: Long): Flow<List<HabitNextMillis>>

    @Query("SELECT * FROM habit_occurrences WHERE habit_id = :habitId AND scheduled_for >= :startTime AND scheduled_for < :endTime ORDER BY scheduled_for")
    fun getOccurrencesForHabitInTimeRange(habitId: String, startTime: Long, endTime: Long): Flow<List<HabitOccurrence>>

    @Query("SELECT * FROM habit_occurrences WHERE scheduled_for >= :startTime AND scheduled_for < :endTime ORDER BY scheduled_for")
    fun getAllOccurrencesInTimeRange(startTime: Long, endTime: Long): kotlinx.coroutines.flow.Flow<List<HabitOccurrence>>

    @Query("SELECT * FROM habit_occurrences WHERE id = :occurrenceId")
    suspend fun getOccurrenceById(occurrenceId: String): HabitOccurrence?

    @Query("SELECT * FROM habit_occurrences")
    fun getAllOccurrences(): Flow<List<HabitOccurrence>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(occurrence: HabitOccurrence): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg occurrences: HabitOccurrence): LongArray

    @Update
    suspend fun update(occurrence: HabitOccurrence)

    @Delete
    suspend fun delete(occurrence: HabitOccurrence)

    @Query("UPDATE habit_occurrences SET status = :status, responded_at = :timestamp WHERE id = :occurrenceId")
    suspend fun updateStatusResponded(occurrenceId: String, status: String, timestamp: Long)

    @Query("UPDATE habit_occurrences SET snooze_count = snooze_count + 1, responded_at = :timestamp WHERE id = :occurrenceId")
    suspend fun incrementSnooze(occurrenceId: String, timestamp: Long)

    /** v0.5.3 undo: back to a blank PENDING slot. Leaves events untouched (append-only history). */
    @Query("UPDATE habit_occurrences SET status = 'PENDING', responded_at = NULL, snooze_count = 0 WHERE id = :occurrenceId")
    suspend fun revertToPending(occurrenceId: String)

    // --- scheduling engine ---

    /**
     * Earliest PENDING occurrence at or after [floor]. The floor is what stops an old
     * unanswered row from head-blocking the queue forever (see NOTIFICATION_DEBUG.md).
     */
    @Query("SELECT * FROM habit_occurrences WHERE habit_id = :habitId AND status = 'PENDING' AND scheduled_for >= :floor ORDER BY scheduled_for LIMIT 1")
    suspend fun getNextPendingForHabit(habitId: String, floor: Long): HabitOccurrence?

    /** Batch auto-skip: retires *every* stale pending row in one statement, not one per sync. */
    @Query("UPDATE habit_occurrences SET status = 'SKIPPED' WHERE habit_id = :habitId AND status = 'PENDING' AND scheduled_for < :before")
    suspend fun skipStaleForHabit(habitId: String, before: Long)

    /** See FoodMedOccurrenceDao.staleNotificationIdsForTask — same orphaned-notification fix. */
    @Query("SELECT notification_id FROM habit_occurrences WHERE habit_id = :habitId AND status = 'PENDING' AND scheduled_for < :before")
    suspend fun staleNotificationIdsForHabit(habitId: String, before: Long): List<Int>

    @Query("DELETE FROM habit_occurrences WHERE habit_id = :habitId AND status = 'PENDING' AND scheduled_for >= :from")
    suspend fun deleteFuturePendingForHabit(habitId: String, from: Long)

    /**
     * v0.5.3 Phase 3 (A2): [deleteFuturePendingForHabit] but sparing the deterministic occurrence
     * ids the sweep is about to (re)generate ([keep] = `"$habitId:$millis"` for every wanted slot).
     * A slot still in the schedule is therefore left in place with its existing `notification_id`,
     * so the already-armed `PendingIntent` is reused (same request code, `FLAG_UPDATE_CURRENT`
     * replaces) instead of being minted fresh and leaking the previous alarm slot. Only genuinely
     * removed slots are deleted. An empty [keep] behaves like [deleteFuturePendingForHabit].
     */
    @Query("DELETE FROM habit_occurrences WHERE habit_id = :habitId AND status = 'PENDING' AND scheduled_for >= :from AND id NOT IN (:keep)")
    suspend fun deleteFuturePendingExcept(habitId: String, from: Long, keep: List<String>)

    /** v0.5.3 Phase 3 (S15): targeted delete of specific occurrence ids — the per-log merge in
     *  `importMonth` removes exactly the stale window slots it identified, nothing else. */
    @Query("DELETE FROM habit_occurrences WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * v0.5.1 §N: clears one month's window before `ExportImportRepository.importMonth` re-inserts
     * it. Half-open `[from, until)` in local epoch millis, mirroring
     * [MonthPartitioner.epochRangeOf]. Unlike [deleteAll] this touches nothing outside the range,
     * and unlike [deleteFuturePendingForHabit] it is status-blind — a hydrated month replaces the
     * whole of that month's history.
     */
    @Query("DELETE FROM habit_occurrences WHERE scheduled_for >= :from AND scheduled_for < :until")
    suspend fun deleteInRange(from: Long, until: Long)

    /**
     * v0.5.3 Phase 1 (S14): [deleteInRange] but sparing [keep] — the current month's still-PENDING
     * rows that already have a SHOWN event, so a mid-day remote merge cannot orphan a posted
     * notification and its refire chain. An empty [keep] behaves exactly like [deleteInRange].
     */
    @Query("DELETE FROM habit_occurrences WHERE scheduled_for >= :from AND scheduled_for < :until AND id NOT IN (:keep)")
    suspend fun deleteInRangeExcept(from: Long, until: Long, keep: List<String>)

    /**
     * v0.5.3 Phase 2 (S17): clears one month's still-PENDING rows keyed off the STORED `local_date`
     * ("yyyy-MM" prefix) rather than `scheduled_for`. Wired in by `importMonth` in Phase 3 as an
     * extra clear so a device-zone shift cannot strand a re-imported row near a month boundary.
     * Deliberately `status = 'PENDING'` only — resolved rows are handled by the Phase 3 S15 merge.
     */
    @Query("DELETE FROM habit_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix AND status = 'PENDING'")
    suspend fun deletePendingByLocalMonth(monthPrefix: String)

    /**
     * v0.5.3 Phase 3 (S17): the past-scoped, keep-list form of [deletePendingByLocalMonth], wired
     * into `importMonth`. Clears a still-PENDING row whose STORED `local_date` puts it in
     * [monthPrefix] even if its `scheduled_for` (recomputed in a changed zone) falls outside the
     * month's epoch range — but only when it is before [before] (so a currently-armed future slot
     * is never touched) and not named by [keep] (the cloud's incoming ids + live SHOWN-pending
     * rows). A subsequent `syncAll()` regenerates any window slot this removes.
     */
    @Query("DELETE FROM habit_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix AND status = 'PENDING' AND scheduled_for < :before AND id NOT IN (:keep)")
    suspend fun deletePendingByLocalMonthBefore(monthPrefix: String, before: Long, keep: List<String>)

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 6 (S-3): the candidate-id form of
     * [deletePendingByLocalMonthBefore], with NO `keep` exclusion — `keep` can hold more ids than
     * SQLite's 999-bound-variable limit for a power user's heavy month, so `importMonth` applies
     * the exclusion itself (a plain `Set` lookup, no bound-variable limit) and deletes the
     * remainder via chunked [deleteByIds] calls instead of passing the whole `keep` list into one
     * `NOT IN (:keep)` query. (A `NOT IN` clause can't safely be "chunked" the way an `IN` clause
     * can — splitting `keep` into pieces and running one `NOT IN (chunk)` delete per piece would
     * delete a genuinely-kept row the moment it's absent from any single chunk, which is *most*
     * chunks. Filtering in Kotlin against the full `keep` set, then deleting by explicit id list,
     * is what keeps this correct.)
     */
    @Query("SELECT id FROM habit_occurrences WHERE substr(local_date, 1, 7) = :monthPrefix AND status = 'PENDING' AND scheduled_for < :before")
    suspend fun pendingIdsByLocalMonthBefore(monthPrefix: String, before: Long): List<String>

    // --- Detail timeline paging (v0.5.3 Phase 3 A4) ---

    /**
     * v0.5.3 Phase 3 (A4): the newest [limit] terminal (resolved) occurrence rows for one habit,
     * paged by [offset]. Ordered by the effective event time (`responded_at`, falling back to
     * `scheduled_for`) so a page boundary is stable. Uses the leading `habit_id` index for the
     * range and the `(status, scheduled_for)` index for the `status != 'PENDING'` filter.
     */
    @Query(
        "SELECT * FROM habit_occurrences WHERE habit_id = :habitId AND status != 'PENDING' " +
            "ORDER BY COALESCE(responded_at, scheduled_for) DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getTerminalPageForHabit(habitId: String, limit: Int, offset: Int): List<HabitOccurrence>

    /**
     * v0.5.3 Phase 3 (A4): `(scheduled_for, status)` for the WHOLE history of one habit — the
     * projection the stats fold and streak maths consume, so paging the timeline never truncates
     * the completion-rate / longest-streak numbers.
     */
    @Query("SELECT scheduled_for AS scheduledFor, status AS status FROM habit_occurrences WHERE habit_id = :habitId")
    suspend fun getScheduledStatusesForHabit(habitId: String): List<HabitSchedStatus>

    /** Full wipe — used only by the backup restore path (L4), inside its transaction. */
    @Query("DELETE FROM habit_occurrences")
    suspend fun deleteAll()

    // --- Journal-as-habit round: mirrors FoodMedOccurrenceDao's logJournalResponse/editJournalResponse ---

    /** A fully-answered Journal habit slot: writes the qa_json snapshot and resolves the slot. */
    @Query("UPDATE habit_occurrences SET qa_json = :qaJson, status = 'LOGGED', responded_at = :timestamp WHERE id = :occurrenceId")
    suspend fun logJournalResponse(occurrenceId: String, qaJson: String?, timestamp: Long)

    /** Journal Mode edit-in-place — mirrors FoodMedOccurrenceDao.editJournalResponse. Does NOT
     *  touch responded_at/status/scheduled_for. */
    @Query("UPDATE habit_occurrences SET qa_json = :qaJson WHERE id = :occurrenceId")
    suspend fun editJournalResponse(occurrenceId: String, qaJson: String?)

    /** Draft auto-save (B1) — persists the in-progress answer snapshot without resolving the slot. */
    @Query("UPDATE habit_occurrences SET qa_json = :qaJson WHERE id = :occurrenceId AND status = 'PENDING'")
    suspend fun saveJournalDraft(occurrenceId: String, qaJson: String)
}
