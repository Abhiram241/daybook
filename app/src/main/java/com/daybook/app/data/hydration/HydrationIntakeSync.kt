package com.daybook.app.data.hydration

import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.TaskType
import com.daybook.app.ui.icons.Icons
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owner feedback round 3 (2026-09-20) — hydration's reminder is a real, system-managed
 * `food_med_tasks` row (TaskType.HYDRATION) so it flows through the exact same
 * OccurrenceScheduler/AlarmReceiver/Intake-tab pipeline as every other Intake reminder, instead of
 * the old synthetic-HomeItem-only approach (see SOURCE_PRIORITY_AND_HYDRATION_PLAN's §2.3).
 * Idempotent: `sync(enabled, timeHHmm)` creates the row once (recording its id in
 * `app_settings.hydration_task_id`) and thereafter only updates `times_json`/archival state — it
 * never creates a second row. The label/icon/color are fixed (not user-editable) since this is a
 * single always-the-same reminder, not a general-purpose Intake item.
 */
@Singleton
class HydrationIntakeSync @Inject constructor(
    private val database: AppDatabase,
    private val settingsRepository: AppSettingsRepository,
    private val scheduler: OccurrenceScheduler
) {
    /** Called whenever hydration is toggled on/off or its reminder time changes (see
     *  HydrationSettingsViewModel). [enabled]=false archives the task (stops reminders, keeps the
     *  row + its history for when re-enabled) rather than deleting it. */
    suspend fun sync(enabled: Boolean, timeHHmm: String) {
        val settings = settingsRepository.getSettings()
        val existingId = settings.hydrationTaskId
        val existing = existingId?.let { database.foodMedTaskDao().getTaskById(it) }
        val timesJson = "[\"$timeHHmm\"]" // matches FoodMedTask.timesJson's existing JSON-array-of-"HH:mm" shape
        if (existing == null) {
            if (!enabled) return // never created, and turning on is what creates it — nothing to do
            val task = FoodMedTask(
                label = "Hydration",
                type = TaskType.HYDRATION,
                iconKey = Icons.WATER,
                timesJson = timesJson,
                activeDaysJson = "", // "" = every day, matching every other daily FoodMedTask default
                isArchived = false
            )
            database.foodMedTaskDao().insert(task)
            settingsRepository.setHydrationTaskId(task.id)
            scheduler.syncTask(task.id)
        } else {
            val updated = existing.copy(timesJson = timesJson, isArchived = !enabled)
            database.foodMedTaskDao().update(updated)
            if (enabled) scheduler.syncTask(updated.id) else scheduler.cancelTask(updated.id)
        }
    }
}
