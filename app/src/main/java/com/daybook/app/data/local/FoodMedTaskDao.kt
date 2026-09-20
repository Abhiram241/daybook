package com.daybook.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.daybook.app.data.model.FoodMedTask
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface FoodMedTaskDao {
    @Query("SELECT * FROM food_med_tasks WHERE is_archived = 0 ORDER BY created_at DESC")
    fun getActiveTasks(): Flow<List<FoodMedTask>>

    @Query("SELECT * FROM food_med_tasks")
    fun observeAllTasks(): Flow<List<FoodMedTask>>

    @Query("SELECT * FROM food_med_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): FoodMedTask?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: FoodMedTask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg tasks: FoodMedTask): LongArray

    @Update
    suspend fun update(task: FoodMedTask)

    @Delete
    suspend fun delete(task: FoodMedTask)

    @Query("UPDATE food_med_tasks SET is_archived = 1 WHERE id = :taskId") 
    suspend fun archiveTask(taskId: String)

    @Query("UPDATE food_med_tasks SET is_archived = 0 WHERE id = :taskId")
    suspend fun unarchiveTask(taskId: String)

    /** Full wipe — used only by the backup restore path (L4), inside its transaction. */
    @Query("DELETE FROM food_med_tasks")
    suspend fun deleteAll()

    /** v0.5.3 Phase 1 (S6): every task id, to diff against a remote definitions set. */
    @Query("SELECT id FROM food_med_tasks")
    suspend fun allIds(): List<String>

    /** v0.5.3 Phase 1 (S6): delete only the definition rows absent from the remote set. */
    @Query("DELETE FROM food_med_tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Build 57 — every system-managed Hydration row, oldest first.
     *
     * `HydrationIntakeSync` used to find its row ONLY through the `app_settings.hydration_task_id`
     * pointer, which is what let two concurrent bootstraps (Application.onCreate and
     * HomeViewModel.init, added in builds 54/55) both read a null pointer and both insert a row —
     * the duplicate Hydration in Intake. Adopting whatever is already in the table makes creation
     * idempotent regardless of the pointer, and lets the extras be cleaned up.
     * The type is matched as a literal because Room stores the enum by name.
     */
    @Query("SELECT * FROM food_med_tasks WHERE type = 'HYDRATION' ORDER BY created_at ASC, id ASC")
    suspend fun getHydrationTasks(): List<FoodMedTask>

    /** Build 57 — ids only, for [com.daybook.app.data.ExportImportRepository]'s prune guard. */
    @Query("SELECT id FROM food_med_tasks WHERE type = 'HYDRATION'")
    suspend fun hydrationTaskIds(): List<String>

    /** Customization round (SD-6): per-intake "why this matters" note. Single-column write. */
    @Query("UPDATE food_med_tasks SET motivation = :v WHERE id = :taskId")
    suspend fun updateMotivation(taskId: String, v: String?)
}
