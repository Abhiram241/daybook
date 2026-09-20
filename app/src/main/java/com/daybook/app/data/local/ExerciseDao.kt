package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.daybook.app.data.model.Exercise
import kotlinx.coroutines.flow.Flow

/**
 * A1 — USER-created exercises only (the ~525-exercise built-in catalog is a bundled RepDB asset,
 * never a Room row — see data/workout/ExerciseCatalog.kt). Same shape as every other definition
 * DAO (HabitDao, CustomCategoryDao): a sync-diff `allIds()` / `deleteByIds()` pair, and no FK.
 */
@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE is_archived = 0 ORDER BY name ASC")
    fun observeCustom(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): Exercise?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Exercise>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<Exercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Update
    suspend fun update(exercise: Exercise)

    @Query("UPDATE exercises SET is_archived = :archived WHERE id = :id")
    suspend fun archive(id: String, archived: Boolean)

    /** Full wipe — used only by the backup restore path, inside its transaction. */
    @Query("DELETE FROM exercises")
    suspend fun deleteAll()

    /** The sync-diff path (§4.4 item 5). */
    @Query("SELECT id FROM exercises")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM exercises WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Items 1/2 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — every non-archived custom/imported
     *  exercise still missing a real muscle group or equipment. The predicate item 2's prompt and
     *  item 1's post-import review queue both use — a builtin can never match (the Room `exercises`
     *  table only ever holds user rows + rename overrides, and every builtin carries a real
     *  muscle/equipment from RepDB). Stable across process death for free: nothing is persisted,
     *  the query itself "remembers" what was skipped since a skipped row still matches next time. */
    @Query("SELECT * FROM exercises WHERE is_archived = 0 AND (primary_muscle = 'OTHER' OR equipment = 'OTHER') ORDER BY created_at DESC")
    suspend fun exercisesNeedingDetails(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises WHERE is_archived = 0 AND (primary_muscle = 'OTHER' OR equipment = 'OTHER')")
    fun observeExercisesNeedingDetailsCount(): Flow<Int>
}
