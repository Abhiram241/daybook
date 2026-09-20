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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owner feedback round 3 (2026-09-20) — hydration's reminder is a real, system-managed
 * `food_med_tasks` row (TaskType.HYDRATION) so it flows through the exact same
 * OccurrenceScheduler/AlarmReceiver/Intake-tab pipeline as every other Intake reminder, instead of
 * the old synthetic-HomeItem-only approach (see SOURCE_PRIORITY_AND_HYDRATION_PLAN's §2.3).
 * Idempotent: `sync(enabled, timeHHmm)` converges on exactly ONE such row (recording its id in
 * `app_settings.hydration_task_id`) and thereafter only updates `times_json`/archival state. The
 * label/icon/color are fixed (not user-editable) since this is a single always-the-same reminder,
 * not a general-purpose Intake item.
 *
 * Build 57 — this class is now the single owner of the "there is exactly one Hydration task, it has
 * a parseable schedule, and today's slot exists" invariant, and re-establishes all of it on every
 * call. It is called from three places (Settings' toggle/time handlers, `DaybookApplication`'s cold
 * start, and `HomeViewModel.init`), so every app launch and every Today load repairs whatever the
 * previous builds left behind, with no user action.
 */
@Singleton
class HydrationIntakeSync @Inject constructor(
    private val database: AppDatabase,
    private val settingsRepository: AppSettingsRepository,
    private val scheduler: OccurrenceScheduler
) {
    /**
     * Build 57 — serialises the whole read-decide-write below.
     *
     * The duplicate Hydration in Intake came from this method having no mutual exclusion: builds
     * 54/55 added two independent bootstraps (`DaybookApplication.onCreate` on `Dispatchers.Default`
     * and `HomeViewModel.init` on `viewModelScope`) that both run on the very first launch after an
     * upgrade. Both read `hydration_task_id` before either had written it, both saw null, and both
     * inserted a task — a textbook lost update. @Singleton, so this lock genuinely covers every
     * caller in the process.
     */
    private val mutex = Mutex()

    /** Called whenever hydration is toggled on/off or its reminder time changes (see
     *  HydrationSettingsViewModel), and on every cold start / Today load as a self-heal.
     *  [enabled]=false archives the task (stops reminders, keeps the row + its history for when
     *  re-enabled) rather than deleting it. */
    suspend fun sync(enabled: Boolean, timeHHmm: String) = mutex.withLock {
        val taskDao = database.foodMedTaskDao()
        val timesJson = hydrationTimesJson(timeHHmm)

        // Adopt whatever is already in the table rather than trusting the settings pointer alone.
        // `hydration_task_id` can disagree with reality in both directions: it can be null while
        // rows exist (the lost-update race above), and it can point at a row that no longer exists
        // (a cloud definitions apply pruned it — see ExportImportRepository.applyRemoteDefinitions).
        // Oldest wins, so the row with the real logging history is the one kept.
        val existingRows = taskDao.getHydrationTasks()
        val pointerId = settingsRepository.getSettings().hydrationTaskId
        val (canonical, duplicates) = resolveCanonical(existingRows, pointerId)

        if (canonical == null) {
            if (!enabled) return@withLock // never created, and turning on is what creates it
            val task = FoodMedTask(
                label = "Hydration",
                type = TaskType.HYDRATION,
                iconKey = Icons.WATER,
                timesJson = timesJson,
                activeDaysJson = "", // "" = every day, matching every other daily FoodMedTask default
                isArchived = false
            )
            taskDao.insert(task)
            settingsRepository.setHydrationTaskId(task.id)
            scheduler.syncTask(task.id)
            scheduler.ensureTodaySlots(task.id)
            return@withLock
        }

        // Retire any extra rows, taking their occurrences with them. Without the occurrence delete
        // they would linger as rows no screen can render (every render path joins occurrence ->
        // task and drops the row when the task is gone) while still holding the unique
        // `(task_id, scheduled_for)` index.
        duplicates.forEach { dupe ->
            scheduler.cancelTask(dupe.id)
            database.foodMedOccurrenceDao().deleteAllForTask(dupe.id)
            taskDao.delete(dupe)
        }
        if (pointerId != canonical.id) settingsRepository.setHydrationTaskId(canonical.id)

        // Always rewrite label/icon/schedule: this is what repairs a row left with the unparseable
        // `["21:00"]` times_json that builds 53-55 wrote (see [hydrationTimesJson]), and what
        // restores the system-managed label/icon if a cloud round-trip altered them.
        val updated = canonical.copy(
            label = "Hydration",
            iconKey = Icons.WATER,
            timesJson = timesJson,
            isArchived = !enabled
        )
        if (updated != canonical) taskDao.update(updated)
        if (enabled) {
            scheduler.syncTask(updated.id)
            scheduler.ensureTodaySlots(updated.id)
        } else {
            scheduler.cancelTask(updated.id)
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
         * themselves on the next launch without the user having to re-toggle anything.
         */
        /**
         * Build 57 — picks the one Hydration row to keep and lists the rest for removal.
         *
         * Pure so the dedupe rule itself is testable without a database (this module has no Room
         * test harness). The pointer wins when it still resolves to a real row, so an install with
         * a consistent `hydration_task_id` keeps exactly the row it has always used; otherwise the
         * OLDEST row wins, since [FoodMedTaskDao.getHydrationTasks] orders by `created_at` and that
         * is the row carrying the real logging history. A null/dangling pointer is expected, not
         * exceptional: it is what a lost-update race or a pruned task leaves behind.
         */
        internal fun resolveCanonical(
            rows: List<FoodMedTask>,
            pointerId: String?
        ): Pair<FoodMedTask?, List<FoodMedTask>> {
            val canonical = rows.firstOrNull { it.id == pointerId } ?: rows.firstOrNull()
            return canonical to rows.filter { it.id != canonical?.id }
        }

        internal fun hydrationTimesJson(timeHHmm: String): String {
            val parsed = runCatching { java.time.LocalTime.parse(timeHHmm.trim(), FORMAT) }
                .getOrElse { java.time.LocalTime.of(21, 0) }
            return DateTimeUtils.timesToJson(listOf(parsed))
        }

        private val FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}
