package com.daybook.app.data

import android.util.Log
import androidx.room.withTransaction
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.Event
import com.daybook.app.data.model.FoodMedEvent
import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitEvent
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.HabitType
import com.daybook.app.data.model.Occurrence
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.notification.NotificationIdSequence
import com.daybook.app.util.notification.NotificationUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a Habit / FoodMedTask (fixed clock times x active weekdays) into concrete
 * occurrence rows and keeps exactly one "next" AlarmManager alarm armed per item.
 *
 * Re-nagging while an occurrence is unresolved is handled by [AlarmReceiver] scheduling
 * an "is-refire" alarm every snooze interval; the *next* occurrence is only armed once
 * the current one is resolved (logged / skipped / completed) or on a full [syncAll].
 *
 * Concurrency: the public entry points ([syncTask], [syncHabit], [cancelTask], [cancelHabit],
 * the resolve actions) run under [syncMutex]. Each per-item row rewrite is additionally wrapped
 * in a DB transaction so a concurrent sync can never observe the deleted-but-not-yet-reinserted
 * window and arm an alarm for a row that no longer exists (REV-15). Alarms are armed *after* the
 * transaction commits, since AlarmManager is not part of the transaction.
 */
/**
 * v0.5.5 — pure: is this a "no-schedule" habit type? STREAK ("Ongoing") generates zero
 * occurrences and arms zero alarms; [OccurrenceScheduler.syncHabitInternal] early-returns via
 * the cancel path (which also tears down any prior INDIVIDUAL/BATCH schedule). See
 * `OngoingSchedulerDecisionTest`.
 */
fun isNoScheduleHabit(type: HabitType): Boolean = type == HabitType.STREAK

/**
 * Journal-as-habit round (B4) — pure: which habit types arm their OWN per-time "next" alarm
 * (as opposed to BATCH, surfaced only by the single app-wide check-in alarm, or STREAK, which
 * arms nothing at all). Used by [OccurrenceScheduler.syncHabitInternal]'s post-sync arm call.
 * See `HabitJournalSchedulerTest`.
 */
fun armsOwnAlarm(type: HabitType): Boolean = type == HabitType.INDIVIDUAL || type == HabitType.JOURNAL

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4, High): the outcome of a journal/backfill save.
 * [OccurrenceScheduler.logFoodMed], [OccurrenceScheduler.logJournal],
 * [OccurrenceScheduler.logHabitJournal], [OccurrenceScheduler.backfillFoodMed] and
 * [OccurrenceScheduler.backfillHabitJournal] used to early-return silently (a bare `Log.w` or a
 * no-op `return@withLock`) on a `canBackfill` rejection, a not-yet-resident month, or a missing
 * occurrence row — every caller then unconditionally set `saved`/`done = true`, so the UI showed
 * "Saved" over data that was actually dropped. Every call site now threads this back to an
 * explicit UI state instead, modelled on `SettingsViewModel.exportRange`'s existing
 * `HydrateResult.Offline` pattern.
 */
sealed class LogResult {
    data object Success : LogResult()
    data class Rejected(val reason: String) : LogResult()
    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-7): distinct from [Rejected] — the occurrence
     * exists and is fine, it's just already answered. Returned only by
     * [OccurrenceScheduler.logFoodMedFromNotificationReply].
     */
    data object AlreadyResolved : LogResult()
}

@Singleton
class OccurrenceScheduler @Inject constructor(
    private val db: AppDatabase,
    private val notificationUtils: NotificationUtils,
    private val notificationIds: NotificationIdSequence,
    // Provider, not a direct dependency: CloudSyncRepository depends on OccurrenceScheduler, so a
    // direct injection here would be a Hilt cycle. Used only by the §9 backfill month-residency guard.
    private val cloudSyncProvider: javax.inject.Provider<com.daybook.app.data.sync.CloudSyncRepository>,
    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-3): persists `batchSnoozeUntil` — no Hilt cycle
    // risk, SyncStateStore only depends on Context.
    private val syncState: com.daybook.app.data.sync.SyncStateStore
) {
    private companion object {
        const val TAG = "OccurrenceScheduler"
        const val WINDOW_DAYS = 7L
        const val STALE_AFTER_MS = 24L * 60 * 60 * 1000  // auto-skip prompts older than this
        const val CATCHUP_DELAY_MS = 5_000L
        /** Fallback when app_settings has no row / an unparseable "HH:mm". */
        const val DEFAULT_CHECKIN = "21:00"

        /**
         * How far into the past `armNext*` will still look. Anything older is a genuine miss and
         * must never be armed, or it head-blocks the queue and today's real reminder never fires.
         */
        const val CATCHUP_WINDOW_MS = 60L * 60 * 1000
    }

    /** Serialises every mutation so delete+reinsert+arm for one item can't interleave with another. */
    private val syncMutex = Mutex()

    private fun occId(itemId: String, epochMillis: Long) = "$itemId:$epochMillis"

    /**
     * rec 5 (N1 / SD-3) — push a would-fire-now trigger past the quiet-hours window if one is
     * enabled and the instant falls inside it. Identity no-op when quiet hours is off, so every
     * existing arm/snooze/batch path is byte-for-byte unchanged for a user who never enables it.
     */
    private suspend fun quietDefer(triggerAtMillis: Long): Long {
        val s = db.appSettingsDao().getSettings() ?: return triggerAtMillis
        return deferIfInsideQuietHours(
            triggerAtMillis, s.quietHoursEnabled, s.quietStart, s.quietEnd
        )
    }

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-1): a public entry point onto [quietDefer] so
     * `AlarmReceiver`'s refire arm — the one arm path that used to bypass quiet hours entirely —
     * can apply the exact same defer every other arm path already gets. Identity no-op when quiet
     * hours is off, so every existing arm/snooze/batch path stays byte-for-byte unchanged.
     */
    suspend fun deferForQuietHours(triggerAtMillis: Long): Long = quietDefer(triggerAtMillis)

    /**
     * The times-json a habit's occurrence rows are generated from. INDIVIDUAL habits carry their
     * own; a BATCH habit carries none and borrows the single app-wide check-in time, so its
     * per-habit occurrence rows (streaks, the Today card) work exactly as an INDIVIDUAL's do.
     * Shared by [syncHabitInternal] and the §9 backfill path so the two cannot diverge.
     */
    private fun effectiveTimesJson(habit: Habit, checkinTime: String): String =
        if (habit.type == HabitType.BATCH) checkinTime else habit.timesJson

    /** All wall-clock instants for the next [WINDOW_DAYS] days matching the schedule. */
    private fun slots(timesJson: String, activeDaysJson: String): List<Long> {
        val times = DateTimeUtils.jsonToTimes(timesJson)
        if (times.isEmpty()) return emptyList()
        val activeDays = DateTimeUtils.jsonToDays(activeDaysJson) // empty => every day
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val out = ArrayList<Long>()
        for (d in 0 until WINDOW_DAYS) {
            val date = today.plusDays(d)
            if (activeDays.isNotEmpty() && activeDays.none { it.name == date.dayOfWeek.name }) continue
            for (t in times) {
                out += date.atTime(t).atZone(zone).toInstant().toEpochMilli()
            }
        }
        return out
    }

    // ------------------------------------------------------------------ Food / Med

    suspend fun syncTask(taskId: String) = syncMutex.withLock { syncTaskInternal(taskId) }

    private suspend fun syncTaskInternal(taskId: String) {
        val task = db.foodMedTaskDao().getTaskById(taskId)
        val dao = db.foodMedOccurrenceDao()
        if (task == null || task.isArchived) {
            cancelTaskInternal(taskId)
            return
        }
        val now = System.currentTimeMillis()
        val taskSlots = slots(task.timesJson, task.activeDaysJson)
        Log.i(TAG, "syncTask($taskId) label='${task.label}' times='${task.timesJson}' days='${task.activeDaysJson}' slots=${taskSlots.size}")
        // v0.5.3 Phase 3 (A2): the occurrence id ("$taskId:$millis") is deterministic, but the
        // notification_id was minted fresh on every sweep and the previously-armed PendingIntent
        // (keyed on the old id's request code) was never cancelled — a leaked armed alarm per item
        // per sweep, after every remote apply / boot / tz change / permission grant / daily worker.
        // Fix: keep the rows for slots still in the schedule (their notification_id, and therefore
        // their armed alarm, survive), delete only genuinely removed slots. `dao.insert` is
        // OnConflictStrategy.IGNORE, so a surviving row is never overwritten by the loop below.
        val wantedIds = taskSlots.filter { it >= now - 60_000L }.map { occId(taskId, it) }
        // One transaction: the window is never observed half-rewritten by a concurrent reader.
        val orphaned = db.withTransaction {
            dao.deleteFuturePendingExcept(taskId, now, wantedIds)
            for (millis in taskSlots) {
                if (millis < now - 60_000L) continue
                val id = occId(taskId, millis)
                dao.insert(
                    FoodMedOccurrence(
                        id = id,
                        taskId = taskId,
                        scheduledFor = millis,
                        status = Occurrence.Status.PENDING,
                        notificationId = notificationIds.next(),
                        // v0.5.3 Phase 2 (S17): persist the local calendar date at insert time.
                        localDate = DateTimeUtils.timestampToLocalDate(millis).toString()
                    )
                )
            }
            // Auto-skip *every* long-missed prompt in one statement so we don't burst-notify after a
            // long gap and — more importantly — so a backlog can't head-block the queue.
            // Grab their notification ids first: once retired, nothing else would ever clear the
            // notifications they left in the shade (L3).
            val stale = dao.staleNotificationIdsForTask(taskId, now - STALE_AFTER_MS)
            dao.skipStaleForTask(taskId, now - STALE_AFTER_MS)
            stale
        }
        orphaned.forEach { notificationUtils.cancelNotification(it) }
        armNextTaskInternal(taskId)
    }

    /**
     * Arms the next pending occurrence for [taskId].
     *
     * v0.5.1 §G/§H. [allowCatchup] controls whether an occurrence that is already *in the past*
     * may be selected and re-fired at `now + CATCHUP_DELAY_MS`:
     *
     *  - `true` (the sweep paths — [syncTaskInternal] and therefore [syncAll], boot, the
     *    WorkManager window refresh) — a genuine miss from the last hour still deserves a prompt.
     *  - `false` (the resolve paths — [finishFoodMed]) — resolving one occurrence must arm the
     *    *next scheduled* one, never immediately re-fire a different overdue one. That re-fire is
     *    what made an answered reminder look like it "never cleared": five seconds after the user
     *    replied in-app, the next overdue slot posted its own notification.
     *
     * Independently, an occurrence already in the past that has a `SHOWN` event is never re-armed:
     * it fired once already and [com.daybook.app.util.alarm.AlarmReceiver]'s own refire chain is
     * still carrying it. Without this, any `syncAll()` (MainActivity.onResume, WindowRefreshWorker)
     * re-armed a still-`PENDING` row as a *non-refire* alarm, producing a second `SHOWN` event row
     * and a second notification post.
     *
     * v0.5.3 Phase 3 (A2): no logic change here. Arming the same `next` row now re-uses the same
     * `notification_id` — hence the same request code, so `FLAG_UPDATE_CURRENT` replaces rather
     * than stacks — because [syncTaskInternal]'s window rewrite spares (rather than deletes and
     * re-mints) the rows for slots still in the schedule.
     */
    private suspend fun armNextTaskInternal(taskId: String, allowCatchup: Boolean = true) {
        val now = System.currentTimeMillis()
        val floor = if (allowCatchup) now - CATCHUP_WINDOW_MS else now
        val next = db.foodMedOccurrenceDao().getNextPendingForTask(taskId, floor)
        if (next == null) {
            Log.w(TAG, "armNextTask($taskId, catchup=$allowCatchup): no pending occurrence in window — nothing armed")
            return
        }
        if (next.scheduledFor <= now && db.foodMedEventDao().hasShownEvent(next.id)) {
            Log.i(TAG, "armNextTask($taskId): occ=${next.id} already SHOWN and overdue — leaving it to the refire chain")
            return
        }
        val triggerAt = quietDefer(if (next.scheduledFor > now) next.scheduledFor else now + CATCHUP_DELAY_MS)
        Log.i(TAG, "armNextTask($taskId): occ=${next.id} scheduledFor=${next.scheduledFor} triggerAt=$triggerAt catchup=$allowCatchup")
        notificationUtils.scheduleReminderAlarm(next.id, next.notificationId, isHabit = false, triggerAtMillis = triggerAt)
    }

    suspend fun cancelTask(taskId: String) = syncMutex.withLock { cancelTaskInternal(taskId) }

    private suspend fun cancelTaskInternal(taskId: String) {
        val dao = db.foodMedOccurrenceDao()
        dao.getNextPendingForTask(taskId, 0L)?.let {
            notificationUtils.cancelReminderAlarm(it.id, it.notificationId, isHabit = false)
            notificationUtils.cancelNotification(it.notificationId)
        }
        dao.deleteFuturePendingForTask(taskId, 0L)
    }

    // ------------------------------------------------------------------ Habits

    suspend fun syncHabit(habitId: String) = syncMutex.withLock { syncHabitInternal(habitId) }

    private suspend fun syncHabitInternal(habitId: String) {
        val habit = db.habitDao().getHabitById(habitId)
        val dao = db.habitOccurrenceDao()
        if (habit == null || habit.isArchived) {
            cancelHabitInternal(habitId)
            return
        }
        // v0.5.5: STREAK ("Ongoing") is a no-schedule type — it generates no occurrences and arms
        // no alarms. If this row was previously INDIVIDUAL/BATCH, tear its schedule down: the
        // existing cancel path cancels the next armed alarm + shade notification and deletes every
        // future PENDING occurrence row.
        if (isNoScheduleHabit(habit.type)) {
            cancelHabitInternal(habitId)
            return
        }
        val now = System.currentTimeMillis()
        val checkinTime = db.appSettingsDao().getSettings()?.habitCheckinTime ?: DEFAULT_CHECKIN
        val timesJson = effectiveTimesJson(habit, checkinTime)
        val habitSlots = slots(timesJson, habit.activeDaysJson)
        Log.i(TAG, "syncHabit($habitId) title='${habit.title}' type=${habit.type} times='$timesJson' days='${habit.activeDaysJson}' slots=${habitSlots.size}")
        // v0.5.3 Phase 3 (A2) — see [syncTaskInternal]: spare the rows for slots still in the
        // schedule so their notification_id (and armed alarm) is reused, not leaked and re-minted.
        val wantedIds = habitSlots.filter { it >= now - 60_000L }.map { occId(habitId, it) }
        val orphaned = db.withTransaction {
            dao.deleteFuturePendingExcept(habitId, now, wantedIds)
            for (millis in habitSlots) {
                if (millis < now - 60_000L) continue
                val id = occId(habitId, millis)
                dao.insert(
                    HabitOccurrence(
                        id = id,
                        habitId = habitId,
                        scheduledFor = millis,
                        status = Occurrence.Status.PENDING,
                        notificationId = notificationIds.next(),
                        // v0.5.3 Phase 2 (S17): persist the local calendar date at insert time.
                        localDate = DateTimeUtils.timestampToLocalDate(millis).toString()
                    )
                )
            }
            val stale = dao.staleNotificationIdsForHabit(habitId, now - STALE_AFTER_MS)
            dao.skipStaleForHabit(habitId, now - STALE_AFTER_MS)
            stale
        }
        orphaned.forEach { notificationUtils.cancelNotification(it) }
        // Journal-as-habit round (B4): JOURNAL schedules exactly like INDIVIDUAL — own per-time
        // alarms, no BATCH substitution (already excluded above via effectiveTimesJson).
        if (armsOwnAlarm(habit.type)) armNextHabitInternal(habitId)
        // BATCH habits are surfaced by the single app-wide check-in alarm, armed by armBatchCheckIn().
    }

    /** See [armNextTaskInternal] — identical contract, habit side (v0.5.1 §G/§H). v0.5.3 Phase 3
     *  (A2): re-arming the same row re-uses its surviving `notification_id`, so no alarm is leaked. */
    private suspend fun armNextHabitInternal(habitId: String, allowCatchup: Boolean = true) {
        val now = System.currentTimeMillis()
        val floor = if (allowCatchup) now - CATCHUP_WINDOW_MS else now
        val next = db.habitOccurrenceDao().getNextPendingForHabit(habitId, floor)
        if (next == null) {
            Log.w(TAG, "armNextHabit($habitId, catchup=$allowCatchup): no pending occurrence in window — nothing armed")
            return
        }
        if (next.scheduledFor <= now && db.habitEventDao().hasShownEvent(next.id)) {
            Log.i(TAG, "armNextHabit($habitId): occ=${next.id} already SHOWN and overdue — leaving it to the refire chain")
            return
        }
        val triggerAt = quietDefer(if (next.scheduledFor > now) next.scheduledFor else now + CATCHUP_DELAY_MS)
        Log.i(TAG, "armNextHabit($habitId): occ=${next.id} scheduledFor=${next.scheduledFor} triggerAt=$triggerAt catchup=$allowCatchup")
        notificationUtils.scheduleReminderAlarm(next.id, next.notificationId, isHabit = true, triggerAtMillis = triggerAt)
    }

    suspend fun cancelHabit(habitId: String) = syncMutex.withLock { cancelHabitInternal(habitId) }

    /**
     * v0.5.3 Phase 1 (D3): cancel every armed reminder alarm and shade notification. Called by
     * [com.daybook.app.data.sync.CloudSyncRepository.wipeLocalForSignOut] **before** the Room
     * tables are wiped — the cancel path needs the occurrence rows to find the armed alarms.
     */
    suspend fun cancelAllReminders() = syncMutex.withLock {
        db.foodMedTaskDao().observeAllTasks().first().forEach { cancelTaskInternal(it.id) }
        db.habitDao().observeAllHabits().first().forEach { cancelHabitInternal(it.id) }
        notificationUtils.cancelBatchCheckInAlarm()
        notificationUtils.cancelNotification(NotificationUtils.BATCH_NOTIFICATION_ID)
    }

    private suspend fun cancelHabitInternal(habitId: String) {
        val dao = db.habitOccurrenceDao()
        dao.getNextPendingForHabit(habitId, 0L)?.let {
            notificationUtils.cancelReminderAlarm(it.id, it.notificationId, isHabit = true)
            notificationUtils.cancelNotification(it.notificationId)
        }
        dao.deleteFuturePendingForHabit(habitId, 0L)
    }

    // ------------------------------------------------------------------ bulk

    suspend fun syncAll() {
        // Per-item locking (not one lock for the whole sweep) so a notification action can still
        // slip between items instead of waiting out the entire pass.
        db.foodMedTaskDao().getActiveTasks().first().forEach { syncTask(it.id) }
        db.habitDao().getActiveHabits().first().forEach { syncHabit(it.id) }
        // v0.5.2: one app-wide alarm covers every BATCH habit. Every existing re-arm trigger
        // (launch, boot, package replace, tz change, WindowRefreshWorker) reaches syncAll(), so
        // no new WorkManager worker is needed.
        armBatchCheckIn()
    }

    // ------------------------------------------------------------------ BATCH habit check-in (v0.5.2)

    /**
     * Arms the single app-wide BATCH check-in alarm for the next occurrence of the configured
     * check-in time (today if it is still ahead, else tomorrow). Idempotent — the PendingIntent is
     * keyed on a fixed reserved id, so re-arming replaces rather than stacks.
     */
    suspend fun armBatchCheckIn() = syncMutex.withLock { armBatchCheckInInternal() }

    private suspend fun armBatchCheckInInternal() {
        val hhmm = db.appSettingsDao().getSettings()?.habitCheckinTime ?: DEFAULT_CHECKIN
        val time = runCatching { java.time.LocalTime.parse(hhmm) }.getOrDefault(java.time.LocalTime.of(21, 0))
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var trigger = LocalDate.now().atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (trigger <= now) trigger = LocalDate.now().plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-3): an in-flight snooze (persisted, survives
        // this call) must win over the next scheduled check-in — otherwise any `syncAll()` sweep
        // that re-arms this alarm (a routine sweep, not just a user action) would silently discard
        // it. A snooze already in the past is stale and ignored (max() with `trigger` handles that
        // for free — the next real check-in wins once the snooze has expired).
        trigger = maxOf(trigger, syncState.batchSnoozeUntil)
        notificationUtils.scheduleBatchCheckInAlarm(quietDefer(trigger))
    }

    /** Every BATCH-habit occurrence still PENDING for the local day containing [atMillis]. */
    suspend fun unresolvedBatchOccurrencesFor(atMillis: Long): List<HabitOccurrence> {
        val date = DateTimeUtils.timestampToLocalDate(atMillis)
        val dayStart = DateTimeUtils.startOfDay(date)
        val dayEnd = DateTimeUtils.startOfDay(date.plusDays(1))
        val habits = db.habitDao().getActiveHabits().first()
        if (habits.none { it.type == HabitType.BATCH }) return emptyList()
        val occs = db.habitOccurrenceDao()
            .getAllOccurrencesInTimeRange(dayStart, dayEnd - 1)
            .first()
        return unresolvedBatch(habits, occs, dayStart, dayEnd)
    }

    /** "Done" on the combined notification: resolve every unresolved BATCH occurrence for today. */
    suspend fun completeAllBatchToday() = syncMutex.withLock {
        val now = System.currentTimeMillis()
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-8): the whole-day mutation now runs in one
        // transaction — previously each occurrence's status update + event insert were separate
        // writes, so a mid-loop crash/kill could leave the day half-resolved (some occurrences
        // COMPLETED, others still PENDING with no matching event). The notification is only
        // cancelled after the transaction commits, so a failed/aborted batch never clears the
        // combined notification while leaving unresolved occurrences behind.
        db.withTransaction {
            for (occ in unresolvedBatchOccurrencesFor(now)) {
                db.habitOccurrenceDao().updateStatusResponded(occ.id, Occurrence.Status.COMPLETED.name, now)
                db.habitEventDao().insert(HabitEvent(occurrenceId = occ.id, action = Event.Action.COMPLETED, itemId = occ.habitId))  // v0.5.3 Phase 2 (A4)
            }
        }
        notificationUtils.cancelNotification(NotificationUtils.BATCH_NOTIFICATION_ID)
    }

    /** Snooze on the combined notification: re-post after the global default interval. */
    suspend fun snoozeBatchCheckIn() = syncMutex.withLock {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-3): take syncMutex for symmetry with
        // completeAllBatchToday (both mutate the same batch-check-in state) and PERSIST the snooze
        // so a later armBatchCheckIn() call (from any syncAll() sweep) doesn't silently discard it.
        val minutes = (db.appSettingsDao().getSettings()?.defaultSnoozeMinutes ?: 10).coerceAtLeast(10)
        val snoozeUntil = System.currentTimeMillis() + minutes * 60_000L
        syncState.batchSnoozeUntil = snoozeUntil
        notificationUtils.cancelNotification(NotificationUtils.BATCH_NOTIFICATION_ID)
        notificationUtils.scheduleBatchCheckInAlarm(quietDefer(snoozeUntil))
    }

    /**
     * The detail-screen nav target `(itemType, itemId)` for a tapped notification's occurrence,
     * or null if the row is gone. `itemType` matches DetailViewModel's `"habit"` / `"food_med"`.
     */
    suspend fun detailTargetFor(occurrenceId: String, isHabit: Boolean): Pair<String, String>? =
        if (isHabit) {
            db.habitOccurrenceDao().getOccurrenceById(occurrenceId)?.let { "habit" to it.habitId }
        } else {
            db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId)?.let { "food_med" to it.taskId }
        }

    /** v0.5.2 §3: true when this food/med occurrence belongs to a JOURNAL task — the notification
     *  tap and the Today card both route to the journal page instead of the detail screen. */
    suspend fun isJournalOccurrence(occurrenceId: String): Boolean =
        db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId)
            ?.let { db.foodMedTaskDao().getTaskById(it.taskId)?.type == com.daybook.app.data.model.TaskType.JOURNAL } == true

    /** Journal-as-habit round: true when this HABIT occurrence belongs to a JOURNAL-type habit —
     *  the notification tap and Home/Detail both route to the chat/edit-form instead of a generic
     *  Complete/Skip card. Habit-side counterpart of [isJournalOccurrence]. */
    suspend fun isHabitJournalOccurrence(occurrenceId: String): Boolean =
        db.habitOccurrenceDao().getOccurrenceById(occurrenceId)
            ?.let { db.habitDao().getHabitById(it.habitId)?.type == HabitType.JOURNAL } == true

    // ------------------------------------------------------------------ resolve actions
    // Shared by the notification action receiver and the in-app "Today" screen so both
    // paths write the same rows / events and advance to the next occurrence identically.
    //
    // v0.5.3 Phase 2 (S17): the resolve/edit paths below never touch `scheduled_for`, and
    // `local_date` was written from it at insert time, so it stays correct without a rewrite here.
    // v0.5.3 Phase 2 (A4): every event insert below carries `itemId` = the owning habit/task id.

    suspend fun logFoodMed(
        occurrenceId: String,
        text: String,
        redFlag: com.daybook.app.data.model.RedFlag? = null,
        suspectedFood: String? = null,
        outsideFood: Boolean? = null,
        // Journal Mode edit-in-place: true when re-saving an already-resolved log. Belt-and-braces
        // — the status check below also catches a caller that forgets to pass it.
        isEdit: Boolean = false
    ): LogResult = syncMutex.withLock {
        val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId)
        // L3: dismiss the posted reminder *first*, ahead of every early-return and every DB write.
        // A row that is already resolved (answered in-app, double-tapped, stale-swept) must still
        // clear its shade notification; the old order left it posted forever.
        occ?.let { notificationUtils.cancelNotification(it.notificationId) }
        if (occ == null) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): was a silent return@withLock — the
            // caller then unconditionally reported "saved".
            Log.w(TAG, "logFoodMed: no occurrence row for $occurrenceId")
            return@withLock LogResult.Rejected("That reminder is no longer available.")
        }
        // v0.5.4: FOOD logs carry an optional red-flag + suspected-trigger note. NONE is stored as
        // NULL so an unflagged log is indistinguishable from a pre-v0.5.4 row.
        val flagName = redFlag?.takeIf { it != com.daybook.app.data.model.RedFlag.NONE }?.name
        val suspected = suspectedFood?.trim()?.takeIf { it.isNotBlank() }
        // v0.5.2 build 8: true -> true; false/null -> null (unset == pre-build-8 row).
        val outside = outsideFood?.takeIf { it }

        // Journal Mode edit-in-place: an already-resolved occurrence is being re-saved. Update the
        // payload columns only; leave responded_at / status alone; append NO event; do NOT re-run
        // the finish/arm dance (this slot's alarm is long gone, the next slot is already armed).
        if (isFoodMedEdit(occ.status, isEdit)) {
            db.foodMedOccurrenceDao().editFoodResponse(occurrenceId, text, flagName, suspected, outside)
            return@withLock LogResult.Success
        }

        db.foodMedOccurrenceDao()
            .logFoodResponse(occurrenceId, text, flagName, suspected, outside, System.currentTimeMillis())
        db.foodMedEventDao().insert(FoodMedEvent(occurrenceId = occurrenceId, action = Event.Action.REPLIED, itemId = occ.taskId))  // v0.5.3 Phase 2 (A4)
        finishFoodMed(occ.id, occ.taskId, occ.notificationId)
        LogResult.Success
    }

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-7): the notification-Reply-specific entry point.
     * `isFoodMedEdit(status, callerSaysEdit) = callerSaysEdit || status != PENDING` is correctly
     * generic for every in-app caller (an already-resolved row reopened in the editable form IS an
     * edit) — but the reply action always passes `isEdit=false`, and that same generic predicate
     * still routes it into the edit-in-place branch the instant `status` is no longer PENDING,
     * silently overwriting an already-answered reminder with whatever text arrives from a stale/
     * duplicate notification action. This checks PENDING status *before* calling [logFoodMed] at
     * all, so a reply on an already-resolved occurrence is a deliberate no-op instead — the general
     * [isFoodMedEdit] predicate itself is unchanged for every other caller.
     */
    suspend fun logFoodMedFromNotificationReply(occurrenceId: String, text: String): LogResult {
        val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId)
            ?: return LogResult.Rejected("That reminder is no longer available.")
        if (occ.status != Occurrence.Status.PENDING) return LogResult.AlreadyResolved
        return logFoodMed(occurrenceId, text, isEdit = false)
    }

    /** v0.5.4 Phase 2 (D3): JOURNAL reply — modelled on [logFoodMed]. [text] is the newline-joined
     *  non-blank answers; [qaJson] is the ordered `[{"q":…,"a":…}]` snapshot (now the unbounded
     *  field). `description` is retired for journals — the DAO sets it NULL. */
    suspend fun logJournal(
        occurrenceId: String,
        text: String,
        qaJson: String?,
        isEdit: Boolean = false   // Journal Mode edit-in-place — see [logFoodMed].
    ): LogResult = syncMutex.withLock {
        val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId)
        occ?.let { notificationUtils.cancelNotification(it.notificationId) }   // L3: cancel FIRST
        if (occ == null) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4) — see logFoodMed's identical guard.
            Log.w(TAG, "logJournal: no occurrence row for $occurrenceId")
            return@withLock LogResult.Rejected("That reminder is no longer available.")
        }
        // v0.5.4 Phase 2 (S9): cap the only unbounded field at the input layer (pasted, not typed).
        val qa = qaJson?.take(MAX_JOURNAL_CHARS)?.takeIf { it.isNotBlank() }

        if (isFoodMedEdit(occ.status, isEdit)) {
            db.foodMedOccurrenceDao().editJournalResponse(occurrenceId, text, qa)
            return@withLock LogResult.Success
        }

        db.foodMedOccurrenceDao().logJournalResponse(
            occurrenceId, text, qa, System.currentTimeMillis()
        )
        db.foodMedEventDao().insert(FoodMedEvent(occurrenceId = occurrenceId, action = Event.Action.REPLIED, itemId = occ.taskId))  // v0.5.3 Phase 2 (A4)
        finishFoodMed(occ.id, occ.taskId, occ.notificationId)
        LogResult.Success
    }

    suspend fun skipFoodMed(occurrenceId: String) = syncMutex.withLock {
        val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId)
        occ?.let { notificationUtils.cancelNotification(it.notificationId) }
        if (occ == null) return@withLock
        db.foodMedOccurrenceDao().updateStatusResponded(occurrenceId, Occurrence.Status.SKIPPED.name, System.currentTimeMillis())
        db.foodMedEventDao().insert(FoodMedEvent(occurrenceId = occurrenceId, action = Event.Action.SKIPPED, itemId = occ.taskId))  // v0.5.3 Phase 2 (A4)
        finishFoodMed(occ.id, occ.taskId, occ.notificationId)
    }

    suspend fun snoozeFoodMed(occurrenceId: String) = syncMutex.withLock {
        val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId) ?: return@withLock
        db.foodMedOccurrenceDao().bumpSnooze(occurrenceId)
        db.foodMedEventDao().insert(FoodMedEvent(occurrenceId = occurrenceId, action = Event.Action.USER_SNOOZED, itemId = occ.taskId))  // v0.5.3 Phase 2 (A4)
        val task = db.foodMedTaskDao().getTaskById(occ.taskId)
        // Floor at 10 min: Doze rate-limits setExactAndAllowWhileIdle to ~one per app per ~10 min.
        val snoozeMs = (task?.snoozeIntervalMinutes ?: 10).coerceAtLeast(10) * 60_000L
        notificationUtils.cancelNotification(occ.notificationId)
        notificationUtils.scheduleReminderAlarm(occ.id, occ.notificationId, isHabit = false, triggerAtMillis = quietDefer(System.currentTimeMillis() + snoozeMs), isRefire = true)
    }

    private suspend fun finishFoodMed(occurrenceId: String, taskId: String, notificationId: Int) {
        notificationUtils.cancelNotification(notificationId)
        notificationUtils.cancelReminderAlarm(occurrenceId, notificationId, isHabit = false)
        // v0.5.1 §G: allowCatchup = false. Resolving this occurrence must arm the *next scheduled*
        // one — not immediately re-fire a different overdue slot 5 s from now, which is what made
        // an answered reminder look like it never cleared.
        armNextTaskInternal(taskId, allowCatchup = false)
        // Belt and braces: cancel again after the arm, so no ordering can leave a post-after-cancel.
        notificationUtils.cancelNotification(notificationId)
    }

    suspend fun completeHabit(occurrenceId: String) = resolveHabit(occurrenceId, Occurrence.Status.COMPLETED, Event.Action.COMPLETED)
    suspend fun skipHabit(occurrenceId: String) = resolveHabit(occurrenceId, Occurrence.Status.SKIPPED, Event.Action.SKIPPED)

    private suspend fun resolveHabit(occurrenceId: String, status: Occurrence.Status, event: Event.Action) = syncMutex.withLock {
        val occ = db.habitOccurrenceDao().getOccurrenceById(occurrenceId)
        // L3: cancel ahead of the early-return / the writes — see logFoodMed.
        occ?.let { notificationUtils.cancelNotification(it.notificationId) }
        if (occ == null) return@withLock
        db.habitOccurrenceDao().updateStatusResponded(occurrenceId, status.name, System.currentTimeMillis())
        db.habitEventDao().insert(HabitEvent(occurrenceId = occurrenceId, action = event, itemId = occ.habitId))  // v0.5.3 Phase 2 (A4)
        notificationUtils.cancelReminderAlarm(occ.id, occ.notificationId, isHabit = true)
        // v0.5.1 §G — see finishFoodMed.
        armNextHabitInternal(occ.habitId, allowCatchup = false)
        notificationUtils.cancelNotification(occ.notificationId)
    }

    /**
     * Journal-as-habit round: a Journal habit's chat/edit-form reply — modelled on [logJournal] but
     * on the habit side, using the Phase 1 `HabitOccurrenceDao` methods. [qaJson] is the ordered
     * `[{"q":…,"a":…}]` snapshot; there is no separate `response_text` column on `HabitOccurrence`
     * (see Phase 1's DataModel note) — any "preview text" a caller wants is derived from
     * `JournalQa.decode(qaJson)` at render time, not persisted a second time.
     */
    suspend fun logHabitJournal(occurrenceId: String, qaJson: String?, isEdit: Boolean = false): LogResult = syncMutex.withLock {
        val occ = db.habitOccurrenceDao().getOccurrenceById(occurrenceId)
        occ?.let { notificationUtils.cancelNotification(it.notificationId) }   // L3: cancel FIRST
        if (occ == null) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4) — see logFoodMed's identical guard.
            Log.w(TAG, "logHabitJournal: no occurrence row for $occurrenceId")
            return@withLock LogResult.Rejected("That reminder is no longer available.")
        }
        val qa = qaJson?.take(MAX_JOURNAL_CHARS)?.takeIf { it.isNotBlank() }

        if (isFoodMedEdit(occ.status, isEdit)) {
            db.habitOccurrenceDao().editJournalResponse(occurrenceId, qa)
            return@withLock LogResult.Success
        }

        db.habitOccurrenceDao().logJournalResponse(occurrenceId, qa, System.currentTimeMillis())
        db.habitEventDao().insert(HabitEvent(occurrenceId = occurrenceId, action = Event.Action.REPLIED, itemId = occ.habitId))
        // finish: mirrors finishFoodMed but on the habit side (cancel, cancel-alarm, arm-next-no-catchup)
        notificationUtils.cancelNotification(occ.notificationId)
        notificationUtils.cancelReminderAlarm(occ.id, occ.notificationId, isHabit = true)
        armNextHabitInternal(occ.habitId, allowCatchup = false)
        notificationUtils.cancelNotification(occ.notificationId)
        LogResult.Success
    }

    /** Draft auto-save (B1) — persists the in-progress chat answer snapshot without resolving the
     *  slot; a no-op if the occurrence has moved on (e.g. auto-skipped while the chat was open). */
    suspend fun saveHabitJournalDraft(occurrenceId: String, qaJson: String) {
        db.habitOccurrenceDao().saveJournalDraft(occurrenceId, qaJson.take(MAX_JOURNAL_CHARS))
    }

    suspend fun snoozeHabit(occurrenceId: String) = syncMutex.withLock {
        val occ = db.habitOccurrenceDao().getOccurrenceById(occurrenceId) ?: return@withLock
        db.habitOccurrenceDao().incrementSnooze(occurrenceId, System.currentTimeMillis())
        db.habitEventDao().insert(HabitEvent(occurrenceId = occurrenceId, action = Event.Action.USER_SNOOZED, itemId = occ.habitId))  // v0.5.3 Phase 2 (A4)
        val habit = db.habitDao().getHabitById(occ.habitId)
        val snoozeMs = (habit?.snoozeIntervalMinutes ?: 10).coerceAtLeast(10) * 60_000L
        notificationUtils.cancelNotification(occ.notificationId)
        notificationUtils.scheduleReminderAlarm(occ.id, occ.notificationId, isHabit = true, triggerAtMillis = quietDefer(System.currentTimeMillis() + snoozeMs), isRefire = true)
    }

    // ------------------------------------------------------------------ undo / revert (v0.5.3)

    /**
     * v0.5.3 undo. Reverts one occurrence to a blank PENDING state from the UI. Idempotent.
     *  - status -> PENDING, responded_at -> null, snooze_count -> 0 (habit)
     *    plus response_text -> "", description -> null (food/med)
     *  - the original COMPLETED/SKIPPED/REPLIED event row is LEFT in place (append-only history)
     *  - re-arm: only when the slot is today or in the future. A past slot must not acquire an
     *    alarm (it becomes "Missed"/backfillable again) — same rule as backfill.
     */
    suspend fun revertHabit(occurrenceId: String) = syncMutex.withLock {
        val occ = db.habitOccurrenceDao().getOccurrenceById(occurrenceId) ?: return@withLock
        notificationUtils.cancelNotification(occ.notificationId)
        db.habitOccurrenceDao().revertToPending(occurrenceId)
        if (revertShouldRearm(occ.scheduledFor, startOfTodayMillis())) syncHabitInternal(occ.habitId)
    }

    suspend fun revertFoodMed(occurrenceId: String) = syncMutex.withLock {
        val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId) ?: return@withLock
        notificationUtils.cancelNotification(occ.notificationId)
        db.foodMedOccurrenceDao().revertToPending(occurrenceId)
        if (revertShouldRearm(occ.scheduledFor, startOfTodayMillis())) syncTaskInternal(occ.taskId)
    }

    private fun startOfTodayMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ------------------------------------------------------------------ §9 past-day backfill (v0.5.2)

    private fun monthResident(date: LocalDate): Boolean =
        cloudSyncProvider.get().isMonthResident(YearMonth.from(date).toString())

    /**
     * Resolves an arbitrary past slot for [habitId], creating the occurrence row on demand when
     * none exists. Never arms, cancels, or posts anything — a past slot has no alarm and must not
     * acquire one. Written terminal-in-one-transaction: a row that was briefly PENDING could be
     * caught by `skipStaleForHabit` and silently overwritten.
     */
    suspend fun backfillHabit(
        habitId: String,
        slotMillis: Long,
        status: Occurrence.Status,
        action: Event.Action
    ) = syncMutex.withLock {
        val habit = db.habitDao().getHabitById(habitId) ?: return@withLock
        val date = DateTimeUtils.timestampToLocalDate(slotMillis)
        if (!canBackfill(date, LocalDate.now(), habit.createdAt, habit.activeDaysJson, habit.isArchived)) {
            Log.w(TAG, "backfillHabit($habitId, $slotMillis): rejected by canBackfill"); return@withLock
        }
        if (!monthResident(date)) {
            Log.w(TAG, "backfillHabit($habitId, $slotMillis): month not resident — refusing"); return@withLock
        }
        val id = occId(habitId, slotMillis)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = db.habitOccurrenceDao().getOccurrenceById(id)
            if (existing == null) {
                db.habitOccurrenceDao().insertAll(
                    HabitOccurrence(
                        id = id, habitId = habitId, scheduledFor = slotMillis,
                        status = status, respondedAt = now,
                        notificationId = notificationIds.next(),
                        localDate = date.toString()   // v0.5.3 Phase 2 (S17) — `date` computed above
                    )
                )
            } else {
                db.habitOccurrenceDao().updateStatusResponded(id, status.name, now)
            }
            // v0.5.3 Phase 2 (A4): denormalise the owning habit id onto the event.
            db.habitEventDao().insert(HabitEvent(occurrenceId = id, action = action, itemId = habitId))
        }
    }

    /**
     * Journal-as-habit round: habit-side counterpart of [backfillHabit] combined with
     * [backfillFoodMed]'s LOGGED branch — resolves a missed past Journal-habit slot, creating the
     * occurrence row on demand. Never arms/cancels anything (past slots have no alarm), exactly
     * like every other backfill path. Re-opening an ALREADY-backfilled (LOGGED) slot is an edit —
     * routes through [HabitOccurrenceDao.editJournalResponse], appending no duplicate event.
     */
    suspend fun backfillHabitJournal(habitId: String, slotMillis: Long, qaJson: String?): LogResult = syncMutex.withLock {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): every early return below used to be a
        // silent no-op — the caller then unconditionally reported "saved" over data that was
        // actually dropped.
        val habit = db.habitDao().getHabitById(habitId)
            ?: return@withLock LogResult.Rejected("That habit no longer exists.")
        val date = DateTimeUtils.timestampToLocalDate(slotMillis)
        if (!canBackfill(date, LocalDate.now(), habit.createdAt, habit.activeDaysJson, habit.isArchived)) {
            Log.w(TAG, "backfillHabitJournal($habitId, $slotMillis): rejected by canBackfill")
            return@withLock LogResult.Rejected("That date can't be logged for this habit.")
        }
        if (!monthResident(date)) {
            Log.w(TAG, "backfillHabitJournal($habitId, $slotMillis): month not resident — refusing")
            return@withLock LogResult.Rejected("That month isn't loaded yet — connect and retry.")
        }
        val id = occId(habitId, slotMillis)
        val now = System.currentTimeMillis()
        val qa = qaJson?.take(MAX_JOURNAL_CHARS)?.takeIf { it.isNotBlank() }
        db.withTransaction {
            val existing = db.habitOccurrenceDao().getOccurrenceById(id)
            val isBackfillEdit = existing != null && existing.status != Occurrence.Status.PENDING
            if (existing == null) {
                db.habitOccurrenceDao().insertAll(
                    HabitOccurrence(
                        id = id, habitId = habitId, scheduledFor = slotMillis,
                        status = Occurrence.Status.LOGGED, qaJson = qa, respondedAt = now,
                        notificationId = notificationIds.next(), localDate = date.toString()
                    )
                )
            } else if (isBackfillEdit) {
                db.habitOccurrenceDao().editJournalResponse(id, qa)
            } else {
                db.habitOccurrenceDao().logJournalResponse(id, qa, now)
            }
            if (!isBackfillEdit) {
                db.habitEventDao().insert(HabitEvent(occurrenceId = id, action = Event.Action.REPLIED, itemId = habitId))
            }
        }
        LogResult.Success
    }

    /** Intake / journal counterpart. [qaJson] is non-null only for a JOURNAL task (the ordered
     *  `[{"q":…,"a":…}]` snapshot — v0.5.4 Phase 2); [redFlag]/[suspectedFood] are only set for a
     *  FOOD task's retroactive inline log (v0.5.4). */
    suspend fun backfillFoodMed(
        taskId: String,
        slotMillis: Long,
        status: Occurrence.Status,
        text: String,
        qaJson: String? = null,
        action: Event.Action,
        redFlag: com.daybook.app.data.model.RedFlag? = null,
        suspectedFood: String? = null,
        outsideFood: Boolean? = null
    ): LogResult = syncMutex.withLock {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4) — see backfillHabitJournal's identical note.
        val task = db.foodMedTaskDao().getTaskById(taskId)
            ?: return@withLock LogResult.Rejected("That reminder no longer exists.")
        val date = DateTimeUtils.timestampToLocalDate(slotMillis)
        if (!canBackfill(date, LocalDate.now(), task.createdAt, task.activeDaysJson, task.isArchived)) {
            Log.w(TAG, "backfillFoodMed($taskId, $slotMillis): rejected by canBackfill")
            return@withLock LogResult.Rejected("That date can't be logged for this reminder.")
        }
        if (!monthResident(date)) {
            Log.w(TAG, "backfillFoodMed($taskId, $slotMillis): month not resident — refusing")
            return@withLock LogResult.Rejected("That month isn't loaded yet — connect and retry.")
        }
        val id = occId(taskId, slotMillis)
        val now = System.currentTimeMillis()
        val flagName = redFlag?.takeIf { it != com.daybook.app.data.model.RedFlag.NONE }?.name
        val suspected = suspectedFood?.trim()?.takeIf { it.isNotBlank() }
        val outside = outsideFood?.takeIf { it }
        // v0.5.4 Phase 2 (S9): cap the qa_json blob at the input layer. `description` is retired.
        val qa = qaJson?.take(MAX_JOURNAL_CHARS)?.takeIf { it.isNotBlank() }
        db.withTransaction {
            val existing = db.foodMedOccurrenceDao().getOccurrenceById(id)
            // Journal Mode edit-in-place: a backfilled slot that is already resolved is being
            // re-saved. Update the payload columns only — keep the original responded_at, append
            // NO event (the append-only history already holds the first REPLIED row).
            val isBackfillEdit = existing != null &&
                existing.status != Occurrence.Status.PENDING &&
                status == Occurrence.Status.LOGGED
            if (existing == null) {
                db.foodMedOccurrenceDao().insertAll(
                    FoodMedOccurrence(
                        id = id, taskId = taskId, scheduledFor = slotMillis,
                        status = status,
                        responseText = text,
                        description = null,   // v0.5.4 Phase 2 (D3): retired for journals
                        qaJson = qa,
                        redFlag = redFlag?.takeIf { it != com.daybook.app.data.model.RedFlag.NONE },
                        suspectedFood = suspected,
                        outsideFood = outside,
                        respondedAt = now,
                        notificationId = notificationIds.next(),
                        localDate = date.toString()   // v0.5.3 Phase 2 (S17) — `date` computed above
                    )
                )
            } else if (isBackfillEdit) {
                if (flagName != null || suspected != null || outside != null) {
                    db.foodMedOccurrenceDao().editFoodResponse(id, text, flagName, suspected, outside)
                } else {
                    db.foodMedOccurrenceDao().editJournalResponse(id, text, qa)
                }
            } else {
                if (status == Occurrence.Status.LOGGED) {
                    db.foodMedOccurrenceDao().logJournalResponse(id, text, qa, now)
                    if (flagName != null || suspected != null || outside != null) {
                        db.foodMedOccurrenceDao().logFoodResponse(id, text, flagName, suspected, outside, now)
                    }
                } else {
                    db.foodMedOccurrenceDao().updateStatusResponded(id, status.name, now)
                }
            }
            if (!isBackfillEdit) {
                // v0.5.3 Phase 2 (A4): denormalise the owning task id onto the event.
                db.foodMedEventDao().insert(FoodMedEvent(occurrenceId = id, action = action, itemId = taskId))
            }
        }
        LogResult.Success
    }
}

/**
 * v0.5.3 Phase 1 (S9): hard cap for the unbounded journal string in a month payload. Not reachable
 * by typing; is by pasting. Enforced at every input site ([OccurrenceScheduler.logJournal],
 * [OccurrenceScheduler.backfillFoodMed], [com.daybook.app.ui.journal.journalQaPayload]).
 *
 * v0.5.4 Phase 2 (S9): this now caps the `qa_json` blob total, not the retired `description` field.
 * Value unchanged (32_000).
 */
internal const val MAX_JOURNAL_CHARS = 32_000

/**
 * v0.5.2 §9 / SD-i. A past date may be backfilled for an item when all three hold:
 *   - it is not in the future (today counts as normal, not backfill);
 *   - it is on or after the item's creation date (you cannot log what did not exist);
 *   - the item's schedule covers that weekday (empty activeDaysJson = every day);
 *   - and the item is not archived.
 */
internal fun canBackfill(
    date: LocalDate,
    today: LocalDate,
    createdAtMillis: Long,
    activeDaysJson: String,
    isArchived: Boolean
): Boolean {
    if (isArchived) return false
    if (date.isAfter(today)) return false
    if (date.isBefore(DateTimeUtils.timestampToLocalDate(createdAtMillis))) return false
    return DateTimeUtils.isDateActive(date, activeDaysJson)
}

/**
 * v0.5.3 undo — pure decision: after reverting an occurrence to PENDING, may it re-acquire an
 * alarm? Only when the slot is today or in the future. A past slot must stay alarm-less (it
 * becomes "Missed"/backfillable again) — the same rule the backfill path uses.
 */
internal fun revertShouldRearm(scheduledFor: Long, startOfTodayMillis: Long): Boolean =
    scheduledFor >= startOfTodayMillis

/**
 * Journal Mode edit-in-place — pure decision: re-saving a food/med or journal occurrence updates it
 * in place (no new event, keep responded_at, no alarm re-arm) exactly when it is already resolved
 * OR the caller explicitly says so.
 */
internal fun isFoodMedEdit(status: Occurrence.Status, callerSaysEdit: Boolean): Boolean =
    callerSaysEdit || status != Occurrence.Status.PENDING

/**
 * v0.5.2 — pure decision: "which BATCH occurrences are still unresolved within [dayStart, dayEnd)".
 * Top-level so it is unit-testable without a Room instance. This is the pre-completion suppression
 * rule and SD-e ("post nothing when empty") expressed as a function:
 *  - only BATCH, non-archived habits' occurrences count;
 *  - only PENDING (anything already ticked on the Today card is excluded);
 *  - only the given local day's window.
 */
internal fun unresolvedBatch(
    habits: List<Habit>,
    occurrences: List<HabitOccurrence>,
    dayStart: Long,
    dayEnd: Long
): List<HabitOccurrence> {
    val batchIds = habits.filter { it.type == HabitType.BATCH && !it.isArchived }.mapTo(HashSet()) { it.id }
    return occurrences.filter {
        it.habitId in batchIds && it.status == Occurrence.Status.PENDING &&
            it.scheduledFor >= dayStart && it.scheduledFor < dayEnd
    }
}
