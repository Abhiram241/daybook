package com.daybook.app.data

import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.FoodMedTask
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodMedRepository @Inject constructor(
    val database: AppDatabase
) {
    suspend fun getActiveTasks(): List<FoodMedTask> =
        database.foodMedTaskDao().getActiveTasks().first()

    suspend fun getTaskById(id: String): FoodMedTask? =
        database.foodMedTaskDao().getTaskById(id)

    /** Earliest still-pending occurrence at or after now, for showing "Next: ..." on a card. */
    suspend fun nextOccurrenceMillis(taskId: String): Long? =
        database.foodMedOccurrenceDao()
            .getNextPendingForTask(taskId, System.currentTimeMillis())?.scheduledFor

    /**
     * Reactive "next reminder" millis for every task in one grouped query — replaces an
     * N+1 of [nextOccurrenceMillis] per task in the Intake list (re-emits on occurrence writes).
     */
    fun observeNextPendingMillis(now: Long) =
        database.foodMedOccurrenceDao().observeNextPendingMillis(now)

    suspend fun save(task: FoodMedTask) {
        database.foodMedTaskDao().insertAll(task)
    }

    suspend fun delete(task: FoodMedTask) {
        database.foodMedTaskDao().delete(task)
    }

    fun observeActiveTasks() = database.foodMedTaskDao().getActiveTasks()

    fun observeAllTasks() = database.foodMedTaskDao().observeAllTasks()

    suspend fun archiveTask(id: String) = database.foodMedTaskDao().archiveTask(id)
    suspend fun unarchiveTask(id: String) = database.foodMedTaskDao().unarchiveTask(id)

    /** Customization round (SD-6): per-intake "why this matters" note. */
    suspend fun setMotivation(id: String, v: String?) = database.foodMedTaskDao().updateMotivation(id, v)
}
