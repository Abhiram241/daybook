package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.daybook.app.data.model.WorkoutRoutine
import com.daybook.app.data.model.WorkoutRoutineExercise
import kotlinx.coroutines.flow.Flow

/**
 * A1 (§3.4) — its own DAO, not a fifth concern inside WorkoutDao, because routines are
 * DEFINITIONS and follow the definition DAOs' shape (HabitDao, ExerciseDao), including the
 * sync-diff `allIds()` / `deleteByIds()` pair. No foreign keys (§3.2).
 */
@Dao
interface RoutineDao {

    /**
     * Everything the landing screen needs in one query — the LEFT JOIN is what makes a routine
     * with no exercises still appear ("No exercises yet").
     */
    @Query(
        "SELECT r.id AS id, r.name AS name, r.notes AS notes, " +
            "COUNT(re.id) AS exerciseCount, " +
            "COALESCE(SUM(re.target_sets), 0) AS targetSetCount " +
            "FROM workout_routines r " +
            "LEFT JOIN workout_routine_exercises re ON re.routine_id = r.id " +
            "WHERE r.is_archived = 0 " +
            "GROUP BY r.id " +
            "ORDER BY r.order_index ASC"
    )
    fun observeRoutineSummaries(): Flow<List<RoutineSummary>>

    @Query("SELECT * FROM workout_routines WHERE id = :routineId")
    fun observeRoutine(routineId: String): Flow<WorkoutRoutine?>

    @Query("SELECT * FROM workout_routine_exercises WHERE routine_id = :routineId ORDER BY order_index ASC")
    fun observeRoutineExercises(routineId: String): Flow<List<WorkoutRoutineExercise>>

    @Query("SELECT * FROM workout_routines WHERE id = :routineId")
    suspend fun getRoutine(routineId: String): WorkoutRoutine?

    @Query("SELECT * FROM workout_routine_exercises WHERE routine_id = :routineId ORDER BY order_index ASC")
    suspend fun getRoutineExercises(routineId: String): List<WorkoutRoutineExercise>

    /** Export path (§4.4 item 1) — chunked at 900 bound vars by the caller. */
    @Query("SELECT * FROM workout_routine_exercises WHERE routine_id IN (:routineIds) ORDER BY routine_id, order_index ASC")
    suspend fun getRoutineExercisesForRoutines(routineIds: List<String>): List<WorkoutRoutineExercise>

    @Query("SELECT * FROM workout_routines WHERE is_archived = 0 ORDER BY order_index ASC")
    suspend fun getAllRoutines(): List<WorkoutRoutine>

    @Query("SELECT * FROM workout_routines")
    suspend fun getAllRoutinesIncludingArchived(): List<WorkoutRoutine>

    /** "append the new routine at the end" (§3.4). */
    @Query("SELECT MAX(order_index) FROM workout_routines")
    suspend fun maxOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: WorkoutRoutine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutines(routines: List<WorkoutRoutine>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutineExercises(rows: List<WorkoutRoutineExercise>)

    @Update
    suspend fun updateRoutine(routine: WorkoutRoutine)

    @Query("DELETE FROM workout_routine_exercises WHERE routine_id = :routineId")
    suspend fun deleteRoutineExercises(routineId: String)

    @Query("DELETE FROM workout_routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: String)

    @Query("UPDATE workout_routines SET is_archived = :archived WHERE id = :routineId")
    suspend fun archiveRoutine(routineId: String, archived: Boolean)

    /** Full wipe — the backup restore path only. */
    @Query("DELETE FROM workout_routines")
    suspend fun deleteAllRoutines()

    @Query("DELETE FROM workout_routine_exercises")
    suspend fun deleteAllRoutineExercises()

    /** The definition-sync diff path (§4.4 item 5). */
    @Query("SELECT id FROM workout_routines")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM workout_routines WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

/** Plain Room POJO for [RoutineDao.observeRoutineSummaries] — not an @Entity. */
data class RoutineSummary(
    val id: String,
    val name: String,
    val notes: String?,
    val exerciseCount: Int,
    val targetSetCount: Int
)
