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
}
