package com.daybook.app.util.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.local.AppDatabase
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
            NotificationUtils.ACTION_REST_DONE -> {
                Log.i(TAG, "onReceive ACTION_REST_DONE")
                runAsync("fireRestDone") { fireRestDone() }
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
        // BUG_AUDIT_REPORT.md §1.12: armBatchCheckIn() used to be the last statement, not a
        // `finally` — a throw above it (a SQLiteException on the event inserts, an 8s timeout on a
        // syncMutex-contended unresolvedBatchOccurrencesFor) skipped the re-arm, and since this is
        // a single self-rearming alarm chain, missing one link meant no BATCH habit ever got
        // another check-in notification until something else called syncAll(). Wrapping the body
        // so the re-arm runs in `finally` restores the "either way" invariant the old comment here
        // only stated, rather than enforced.
        try {
            val unresolved = scheduler.unresolvedBatchOccurrencesFor(System.currentTimeMillis())
            if (unresolved.isEmpty()) {
                Log.i(TAG, "fireBatch: nothing unresolved — posting nothing")   // SD-e
            } else {
                val titles = unresolved.mapNotNull { db.habitDao().getHabitById(it.habitId)?.title }
                notificationUtils.showBatchHabitNotification(unresolved.size, titles)
                // One SHOWN event per occurrence, guarded exactly like fireHabit (v0.5.1 §H).
                // Audit finding #19 fix — mutex-guarded check-and-insert (see
                // OccurrenceScheduler.recordHabitShown's KDoc for the race this closes).
                unresolved.forEach { occ -> scheduler.recordHabitShown(occ.id, occ.habitId) }
            }
        } finally {
            // SB-1 fix (AUDIT_2026-09-16.md) — the alarm that just fired, whether the regular
            // check-in or a snoozed one, has consumed any pending snooze; clear it before
            // re-arming so it cannot leak into tomorrow's re-arm decision.
            scheduler.clearBatchSnooze()
            // Re-arm for tomorrow either way, so the chain never breaks.
            scheduler.armBatchCheckIn()
        }
    }

    /**
     * Item 4 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — the rest timer's alarm firing. Deliberately
     * the simplest handler in this file: post the notification and return. One-shot: never
     * re-arms itself, unlike `fireHabit`/`fireFoodMed`'s refire chain — a rest timer has exactly
     * one expiry, not an indefinitely-repeating nag.
     *
     * Build 50 item 2 — now reads the user's chosen tone (`app_settings.rest_timer_sound_uri`,
     * null = system default) so `showRestDoneNotification` can play it on the MEDIA stream — see
     * that function's KDoc for why the channel itself is silent.
     */
    private suspend fun fireRestDone() {
        val toneUri = db.appSettingsDao().getSettings()?.restTimerSoundUri
        notificationUtils.showRestDoneNotification(toneUri)
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

        // UX overhaul item 3 — a BATCH habit is surfaced ONLY by the single consolidated check-in
        // notification (fireBatch → showBatchHabitNotification). Its per-item occurrences must not
        // post their own notification — that was the duplicate. Return before the post, the SHOWN
        // event and the re-nag re-arm. INDIVIDUAL / JOURNAL are untouched.
        if (!com.daybook.app.data.shouldPostIndividualHabitNotification(habit.type)) {
            Log.i(TAG, "fireHabit: $occurrenceId is a ${habit.type} habit — suppressing the per-item notification")
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
        // Audit finding #19 fix — mutex-guarded check-and-insert, see recordHabitShown's KDoc.
        if (!isRefire) scheduler.recordHabitShown(occurrenceId, occ.habitId)
        // B-2 fix (AUDIT_2026-09-16.md) — this refire chain used to be unconditional: no cap, no
        // counter, re-arming forever until the user resolved the occurrence or the (unrelated)
        // 24h stale sweep happened to run. `shouldRefireHabit` bounds it and, when the chain must
        // stop, has already marked the occurrence SKIPPED — cancel the now-stale notification so
        // it doesn't linger with no alarm behind it.
        if (!scheduler.shouldRefireHabit(occ)) {
            Log.i(TAG, "fireHabit: $occurrenceId hit the refire cap — auto-skipped, chain stopped")
            notificationUtils.cancelNotification(occ.notificationId)
            return
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
        // v0.5.1 §H — see fireHabit. Audit finding #19 fix — mutex-guarded check-and-insert.
        if (!isRefire) scheduler.recordFoodMedShown(occurrenceId, occ.taskId)
        // B-2 fix (AUDIT_2026-09-16.md) — see fireHabit's identical guard.
        if (!scheduler.shouldRefireFoodMed(occ)) {
            Log.i(TAG, "fireFoodMed: $occurrenceId hit the refire cap — auto-skipped, chain stopped")
            notificationUtils.cancelNotification(occ.notificationId)
            return
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
