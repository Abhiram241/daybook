package com.daybook.app.data.hydration

import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.TaskType
import com.daybook.app.ui.icons.Icons
import com.daybook.app.util.DateTimeUtils
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
        val timesJson = hydrationTimesJson(timeHHmm)
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

    companion object {
        /**
         * Builds the `food_med_tasks.times_json` value for the reminder time.
         *
         * Build 56 fix — despite the column name, `times_json` is NOT JSON: every other writer
         * (`AddFoodMedViewModel`, `AddHabitViewModel`, `ExportImportRepository`) stores a
         * COMMA-SEPARATED list of bare "HH:mm" via [DateTimeUtils.timesToJson], and the only
         * reader, [DateTimeUtils.jsonToTimes], splits on "," and `LocalTime.parse`s each element.
         * Builds 53-55 wrote a JSON-array literal (`["21:00"]`) here instead, which that reader
         * silently drops per-element — so the Hydration task ended up with ZERO parsed times,
         * `OccurrenceScheduler.slots()` returned an empty list, and no `food_med_occurrences` row
         * was ever generated. Hence no Hydration card on Today, nothing in Intake, and no alarm,
         * with no crash or logged failure to show for it (which is why both self-heal attempts in
         * builds 54/55 appeared to succeed while changing nothing). Writing the format the reader
         * actually expects is the fix; because [sync] rewrites `times_json` on every call and is
         * invoked on each cold start and each Today load, already-broken installs repair
         * themselves on the next launch without the user re-toggling anything.
         */
        internal fun hydrationTimesJson(timeHHmm: String): String {
            val parsed = runCatching { java.time.LocalTime.parse(timeHHmm.trim(), FORMAT) }
                .getOrElse { java.time.LocalTime.of(21, 0) }
            return DateTimeUtils.timesToJson(listOf(parsed))
        }

        private val FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}
