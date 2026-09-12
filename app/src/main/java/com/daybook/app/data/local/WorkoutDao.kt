package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

/**
 * A1 (§3.4) — sessions, blocks and sets in one DAO, mirroring the plan's grouping. No foreign
 * keys (§3.2): `WorkoutRepository.deleteSession` deletes sets, then blocks, then the session
 * inside one `withTransaction`.
 */
@Dao
interface WorkoutDao {

    // ------------------------------------------------------------------------------ sessions
    @Query("SELECT * FROM workout_sessions WHERE started_at BETWEEN :startMillis AND :endMillis ORDER BY started_at DESC")
    fun observeSessionsBetween(startMillis: Long, endMillis: Long): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' OR status = 'ACTIVE' ORDER BY started_at DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSession?

    /** Full export path (§4.4 item 1) — every session, regardless of month. */
    @Query("SELECT * FROM workout_sessions")
    suspend fun getAllSessions(): List<WorkoutSession>

    /** Export path (§4.4 item 1) — inclusive on both ends, "yyyy-MM-dd" strings sort chronologically. */
    @Query("SELECT * FROM workout_sessions WHERE local_date BETWEEN :startYmd AND :endYmd ORDER BY started_at ASC")
    suspend fun getSessionsInLocalDateRange(startYmd: String, endYmd: String): List<WorkoutSession>

    /** §3.9.5 — the Hevy-import duplicate probe: dedupe key is (startedAt, endedAt). */
    @Query("SELECT * FROM workout_sessions WHERE started_at BETWEEN :fromMillis AND :toMillis")
    suspend fun sessionsStartingBetween(fromMillis: Long, toMillis: Long): List<WorkoutSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<WorkoutSession>)

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    /** Month eviction / month import (§4.4 item 6). */
    @Query("DELETE FROM workout_sessions WHERE local_date BETWEEN :startYmd AND :endYmd")
    suspend fun deleteSessionsInLocalDateRange(startYmd: String, endYmd: String)

    /** Full wipe — the backup restore path only. */
    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    // ---------------------------------------------------------------------------- blocks
    @Query("SELECT * FROM workout_exercises WHERE session_id = :sessionId ORDER BY order_index ASC")
    fun observeExercisesForSession(sessionId: String): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE session_id = :sessionId ORDER BY order_index ASC")
    suspend fun getExercisesForSession(sessionId: String): List<WorkoutExercise>

    /** Export path — chunked at 900 bound vars by the caller (§3.4). */
    @Query("SELECT * FROM workout_exercises WHERE session_id IN (:sessionIds) ORDER BY session_id, order_index ASC")
    suspend fun getExercisesForSessions(sessionIds: List<String>): List<WorkoutExercise>

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun getExercise(id: String): WorkoutExercise?

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM workout_exercises WHERE session_id = :sessionId")
    suspend fun maxExerciseOrderIndex(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: WorkoutExercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<WorkoutExercise>)

    @Update
    suspend fun updateExercise(exercise: WorkoutExercise)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    @Query("DELETE FROM workout_exercises WHERE session_id = :sessionId")
    suspend fun deleteExercisesForSession(sessionId: String)

    @Query("DELETE FROM workout_exercises WHERE session_id IN (:sessionIds)")
    suspend fun deleteExercisesForSessions(sessionIds: List<String>)

    @Query("DELETE FROM workout_exercises")
    suspend fun deleteAllExercises()

    @Query("UPDATE workout_exercises SET notes = :notes WHERE id = :id")
    suspend fun updateExerciseNotes(id: String, notes: String?)

    @Query("UPDATE workout_exercises SET rest_seconds = :restSeconds WHERE id = :id")
    suspend fun updateExerciseRest(id: String, restSeconds: Int?)

    // ------------------------------------------------------------------------------ sets
    @Query("SELECT * FROM workout_sets WHERE session_id = :sessionId ORDER BY workout_exercise_id, set_number ASC")
    fun observeSetsForSession(sessionId: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE workout_exercise_id = :workoutExerciseId ORDER BY set_number ASC")
    fun observeSetsForExercise(workoutExerciseId: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE session_id = :sessionId")
    suspend fun getSetsForSession(sessionId: String): List<WorkoutSet>

    /** Export path — chunked at 900 bound vars by the caller (§3.4). */
    @Query("SELECT * FROM workout_sets WHERE session_id IN (:sessionIds)")
    suspend fun getSetsForSessions(sessionIds: List<String>): List<WorkoutSet>

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: String): WorkoutSet?

    @Query("SELECT COALESCE(MAX(set_number), 0) FROM workout_sets WHERE workout_exercise_id = :workoutExerciseId")
    suspend fun maxSetNumber(workoutExerciseId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: WorkoutSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSet>)

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("DELETE FROM workout_sets WHERE session_id = :sessionId")
    suspend fun deleteSetsForSession(sessionId: String)

    @Query("DELETE FROM workout_sets WHERE session_id IN (:sessionIds)")
    suspend fun deleteSetsForSessions(sessionIds: List<String>)

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAllSets()

    @Query("UPDATE workout_sets SET completed_at = :completedAt WHERE id = :id")
    suspend fun setCompletedAt(id: String, completedAt: Long?)

    // ------------------------------------------------------------------ derived-value queries (§3.4)
    /**
     * Every set of the most recent PRIOR completed session containing this exercise. The
     * ViewModel keeps only the rows belonging to the newest session_id in the result and indexes
     * them by set_number — a pure `previousBySetNumber` — because a single SQL query cannot
     * itself express "only the newest session's rows" without a second round trip.
     */
    @Query(
        "SELECT s.* FROM workout_sets s " +
            "JOIN workout_sessions ws ON ws.id = s.session_id " +
            "WHERE s.exercise_id = :exerciseId " +
            "AND s.session_id != :excludeSessionId " +
            "AND s.completed_at IS NOT NULL " +
            "AND ws.status = 'COMPLETED' " +
            "ORDER BY ws.started_at DESC " +
            "LIMIT 50"
    )
    suspend fun previousSetsForExercise(exerciseId: String, excludeSessionId: String): List<WorkoutSet>

    /** The pre-session personal best — read once when the exercise block is opened (§3.4, P3). */
    @Query(
        "SELECT s.* FROM workout_sets s " +
            "WHERE s.exercise_id = :exerciseId " +
            "AND s.session_id != :excludeSessionId " +
            "AND s.completed_at IS NOT NULL " +
            "AND s.set_type = 'NORMAL' " +
            "ORDER BY COALESCE(s.weight_kg, 0) DESC, " +
            "COALESCE(s.reps, 0) DESC, " +
            "COALESCE(s.duration_seconds, 0) DESC, " +
            "COALESCE(s.distance_meters, 0) DESC " +
            "LIMIT 1"
    )
    suspend fun bestSetForExercise(exerciseId: String, excludeSessionId: String): WorkoutSet?

    /** Beast Mode home "This week" stat grid — the pre-week personal best, so a set logged THIS
     *  week can be checked against what stood before the week started (§3.3, item 3). */
    @Query(
        "SELECT s.* FROM workout_sets s " +
            "JOIN workout_sessions ws ON ws.id = s.session_id " +
            "WHERE s.exercise_id = :exerciseId " +
            "AND ws.started_at < :beforeMillis " +
            "AND s.completed_at IS NOT NULL " +
            "AND s.set_type = 'NORMAL' " +
            "ORDER BY COALESCE(s.weight_kg, 0) DESC, " +
            "COALESCE(s.reps, 0) DESC, " +
            "COALESCE(s.duration_seconds, 0) DESC, " +
            "COALESCE(s.distance_meters, 0) DESC " +
            "LIMIT 1"
    )
    suspend fun bestSetForExerciseBefore(exerciseId: String, beforeMillis: Long): WorkoutSet?

    /** The most recent block's rest_seconds for this exercise, so the timer pre-fills (§3.4). */
    @Query(
        "SELECT we.rest_seconds FROM workout_exercises we " +
            "JOIN workout_sessions ws ON ws.id = we.session_id " +
            "WHERE we.exercise_id = :exerciseId " +
            "ORDER BY ws.started_at DESC LIMIT 1"
    )
    suspend fun lastRestSecondsForExercise(exerciseId: String): Int?

    /** ExerciseHistorySheet (§3.7.3) — every completed set of this exercise, newest session
     *  first, then set_number. Not paged: a single exercise's lifetime history is small. */
    @Query(
        "SELECT s.* FROM workout_sets s " +
            "JOIN workout_sessions ws ON ws.id = s.session_id " +
            "WHERE s.exercise_id = :exerciseId AND s.completed_at IS NOT NULL " +
            "ORDER BY ws.started_at DESC, s.set_number ASC"
    )
    suspend fun setsHistoryForExercise(exerciseId: String): List<WorkoutSet>

    /** Add-Exercise screen's "Recent" section (§3.7.3). */
    @Query(
        "SELECT DISTINCT we.exercise_id FROM workout_exercises we " +
            "JOIN workout_sessions ws ON ws.id = we.session_id " +
            "ORDER BY ws.started_at DESC LIMIT 12"
    )
    suspend fun recentExerciseIds(): List<String>

    /**
     * Feature addition — Add-Exercise's default "Frequently logged" view: total COMPLETED sets
     * ever logged per exercise, all-time, most-logged first. One aggregate query (not N+1);
     * [limit] is deliberately generous (the caller resolves each id to its real name — not stored
     * in Room for a builtin — and re-sorts with an alphabetical tie-break before truncating to the
     * final ~10, since SQL's `ORDER BY` alone can't break a count tie by an asset-catalog name).
     */
    @Query(
        "SELECT exercise_id AS exerciseId, COUNT(*) AS setCount FROM workout_sets " +
            "WHERE completed_at IS NOT NULL " +
            "GROUP BY exercise_id ORDER BY setCount DESC LIMIT :limit"
    )
    suspend fun mostLoggedExercises(limit: Int): List<ExerciseSetCount>

    // -------------------------------------------------------- History tab redesign (§2.4, §5)
    /**
     * The History row's condensed "4,200 kg · 18 sets · 5 exercises" line — one `GROUP BY` query
     * for the whole visible page of session ids (mirrors `RoutineDao.observeRoutineSummaries` /
     * `mostLoggedExercises`'s shape), never one query per row. `setCount`/`totalVolumeKg` mirror
     * `sessionStats`'s "completed sets only" semantics; `exerciseCount` is the distinct exercises
     * that have at least one set in the session.
     */
    @Query(
        "SELECT session_id AS sessionId, " +
            "COUNT(DISTINCT exercise_id) AS exerciseCount, " +
            "SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END) AS setCount, " +
            "COALESCE(SUM(CASE WHEN completed_at IS NOT NULL THEN COALESCE(weight_kg, 0) * COALESCE(reps, 0) ELSE 0 END), 0) AS totalVolumeKg " +
            "FROM workout_sets WHERE session_id IN (:sessionIds) GROUP BY session_id"
    )
    suspend fun sessionAggregates(sessionIds: List<String>): List<SessionAggregate>

    /**
     * The History row's single thumbnail (open question 5) — each session's first block (lowest
     * `order_index`), one query for the whole visible page rather than per-row.
     */
    @Query(
        "SELECT we.session_id AS sessionId, we.exercise_id AS exerciseId FROM workout_exercises we " +
            "WHERE we.session_id IN (:sessionIds) AND we.order_index = " +
            "(SELECT MIN(order_index) FROM workout_exercises we2 WHERE we2.session_id = we.session_id)"
    )
    suspend fun firstExercisePerSession(sessionIds: List<String>): List<SessionFirstExercise>

    /** History's exercise filter (open question 3) — every exercise id ever logged in a session,
     *  for the filter-picker sheet; the repository resolves each id to its real name. */
    @Query("SELECT DISTINCT exercise_id FROM workout_exercises")
    suspend fun allLoggedExerciseIds(): List<String>

    /** History's exercise filter (open question 3) — every session that logged this exercise. */
    @Query("SELECT DISTINCT session_id FROM workout_exercises WHERE exercise_id = :exerciseId")
    suspend fun sessionIdsForExercise(exerciseId: String): List<String>
}

/** Plain Room POJO for [WorkoutDao.mostLoggedExercises] — not an @Entity. */
data class ExerciseSetCount(val exerciseId: String, val setCount: Int)

/** Plain Room POJO for [WorkoutDao.sessionAggregates] — not an @Entity. */
data class SessionAggregate(val sessionId: String, val exerciseCount: Int, val setCount: Int, val totalVolumeKg: Double)

/** Plain Room POJO for [WorkoutDao.firstExercisePerSession] — not an @Entity. */
data class SessionFirstExercise(val sessionId: String, val exerciseId: String)
