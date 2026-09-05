package com.daybook.app.util.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.Event
import com.daybook.app.data.model.FoodMedEvent
import com.daybook.app.data.model.HabitEvent
import com.daybook.app.data.model.Occurrence
import com.daybook.app.util.notification.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Fires when a reminder's time arrives. Shows the notification and schedules the
 * next "re-nag" for the same occurrence one snooze-interval later. The *next*
 * occurrence is armed elsewhere (on user action or on app start), never here — that
 * keeps this from looping on a still-pending occurrence.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var notificationUtils: NotificationUtils
    @Inject lateinit var scheduler: OccurrenceScheduler

    private companion object { const val TAG = "AlarmReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationUtils.ACTION_FIRE -> {
                val occurrenceId = intent.getStringExtra(NotificationUtils.EXTRA_OCCURRENCE_ID) ?: return
                val isHabit = intent.getBooleanExtra(NotificationUtils.EXTRA_IS_HABIT, false)
                val isRefire = intent.getBooleanExtra(NotificationUtils.EXTRA_IS_REFIRE, false)
                Log.i(TAG, "onReceive occ=$occurrenceId isHabit=$isHabit isRefire=$isRefire")
                runAsync("fire occ=$occurrenceId") {
                    if (isHabit) fireHabit(occurrenceId, isRefire) else fireFoodMed(occurrenceId, isRefire)
                }
            }
            NotificationUtils.ACTION_FIRE_BATCH -> {
                Log.i(TAG, "onReceive ACTION_FIRE_BATCH")
                runAsync("fireBatch") { fireBatch() }
            }
            else -> return
        }
    }

    /** goAsync() + SupervisorJob + handler + 8s cap — shared by every branch of [onReceive]. */
    private fun runAsync(label: String, block: suspend () -> Unit) {
        val pending = goAsync()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler { _, t ->
                    Log.e(TAG, "$label failed", t)
                    com.daybook.app.util.recordUnhandledException(t)
                }
        )
        scope.launch {
            try {
                withTimeout(8_000) { block() }
            } catch (t: Throwable) {
                Log.e(TAG, "$label failed", t)
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-18) — this is the alarm-fire path, one
                // of the two named "AlarmReceiver/worker catch(Throwable)" sites the plan calls out.
                com.daybook.app.util.recordUnhandledException(t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fireBatch() {
        val unresolved = scheduler.unresolvedBatchOccurrencesFor(System.currentTimeMillis())
        if (unresolved.isEmpty()) {
            Log.i(TAG, "fireBatch: nothing unresolved — posting nothing")   // SD-e
        } else {
            val titles = unresolved.mapNotNull { db.habitDao().getHabitById(it.habitId)?.title }
            notificationUtils.showBatchHabitNotification(unresolved.size, titles)
            // One SHOWN event per occurrence, guarded exactly like fireHabit (v0.5.1 §H).
            unresolved.forEach { occ ->
                if (!db.habitEventDao().hasShownEvent(occ.id)) {
                    // v0.5.3 Phase 2 (A4): denormalise the owning habit id onto the SHOWN event.
                    db.habitEventDao().insert(HabitEvent(occurrenceId = occ.id, action = Event.Action.SHOWN, itemId = occ.habitId))
                }
            }
        }
        // Re-arm for tomorrow either way, so the chain never breaks.
        scheduler.armBatchCheckIn()
    }

    private suspend fun fireHabit(occurrenceId: String, isRefire: Boolean) {
        val occ = db.habitOccurrenceDao().getOccurrenceById(occurrenceId)
        if (occ == null) {
            Log.w(TAG, "fireHabit: no occurrence row for $occurrenceId — nothing to show")
            return
        }
        if (occ.status != Occurrence.Status.PENDING) {
            Log.i(TAG, "fireHabit: $occurrenceId already ${occ.status} — skipping")
            return
        }
        val habit = db.habitDao().getHabitById(occ.habitId)
        if (habit == null) {
            Log.w(TAG, "fireHabit: orphaned occurrence $occurrenceId (habit ${occ.habitId} gone)")
            return
        }

        // Journal-as-habit round: a JOURNAL habit gets its own notification shape (fixed body,
        // Skip+Snooze only, no Complete action) — mirrors how FoodMed-JOURNAL branches in
        // showFoodMedNotification, just routed at the call site since a habit-side JOURNAL
        // notification is a genuinely different builder, not a branch inside showHabitNotification.
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-2): capture whether it actually posted — a
        // blocked channel/permission previously still recorded SHOWN and armed the refire below,
        // turning a reminder the user never saw into a phantom "SHOWN → SKIPPED" history entry.
        val posted = if (habit.type == com.daybook.app.data.model.HabitType.JOURNAL) {
            notificationUtils.showHabitJournalNotification(occ, habit.title)
        } else {
            notificationUtils.showHabitNotification(occ, habit.title, habit.promptMessage)
        }
        if (!posted) {
            Log.w(TAG, "fireHabit: $occurrenceId notification suppressed — no SHOWN event, no refire")
            return
        }
        // v0.5.1 §H: `!isRefire` alone was not enough. Any non-refire re-arm of a still-PENDING
        // occurrence (a syncAll sweep used to do exactly that) produced a second SHOWN row and the
        // Detail screen's Activity list read "Shown, Shown, Completed". The scheduler no longer
        // re-arms such a row; this makes a duplicate *post* cost at most a duplicate notification,
        // never corrupted history.
        if (!isRefire && !db.habitEventDao().hasShownEvent(occurrenceId)) {
            // v0.5.3 Phase 2 (A4): denormalise the owning habit id onto the SHOWN event.
            db.habitEventDao().insert(HabitEvent(occurrenceId = occurrenceId, action = Event.Action.SHOWN, itemId = occ.habitId))
        }
        val snoozeMs = habit.snoozeIntervalMinutes.coerceAtLeast(10) * 60_000L
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-1): route the refire's trigger through the
        // same quiet-hours defer every other arm path already gets — this was the one arm that
        // bypassed it entirely.
        val rawTrigger = System.currentTimeMillis() + snoozeMs
        notificationUtils.scheduleReminderAlarm(
            occurrenceId, occ.notificationId, isHabit = true,
            triggerAtMillis = scheduler.deferForQuietHours(rawTrigger), isRefire = true
        )
    }

    private suspend fun fireFoodMed(occurrenceId: String, isRefire: Boolean) {
        val occ = db.foodMedOccurrenceDao().getOccurrenceById(occurrenceId)
        if (occ == null) {
            Log.w(TAG, "fireFoodMed: no occurrence row for $occurrenceId — nothing to show")
            return
        }
        if (occ.status != Occurrence.Status.PENDING) {
            Log.i(TAG, "fireFoodMed: $occurrenceId already ${occ.status} — skipping")
            return
        }
        val task = db.foodMedTaskDao().getTaskById(occ.taskId)
        if (task == null) {
            Log.w(TAG, "fireFoodMed: orphaned occurrence $occurrenceId (task ${occ.taskId} gone)")
            return
        }

        // v0.5.1 §F: the small icon is picked from the task's category; the caller already has the row.
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-2) — see fireHabit's identical guard.
        val posted = notificationUtils.showFoodMedNotification(occ, task.label, task.type, task.promptMessage)
        if (!posted) {
            Log.w(TAG, "fireFoodMed: $occurrenceId notification suppressed — no SHOWN event, no refire")
            return
        }
        // v0.5.1 §H — see fireHabit.
        if (!isRefire && !db.foodMedEventDao().hasShownEvent(occurrenceId)) {
            // v0.5.3 Phase 2 (A4): denormalise the owning task id onto the SHOWN event.
            db.foodMedEventDao().insert(FoodMedEvent(occurrenceId = occurrenceId, action = Event.Action.SHOWN, itemId = occ.taskId))
        }
        val snoozeMs = task.snoozeIntervalMinutes.coerceAtLeast(10) * 60_000L
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-1) — see fireHabit's identical quiet-hours defer.
        val rawTrigger = System.currentTimeMillis() + snoozeMs
        notificationUtils.scheduleReminderAlarm(
            occurrenceId, occ.notificationId, isHabit = false,
            triggerAtMillis = scheduler.deferForQuietHours(rawTrigger), isRefire = true
        )
    }
}
