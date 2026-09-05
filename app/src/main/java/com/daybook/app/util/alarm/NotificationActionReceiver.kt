package com.daybook.app.util.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.util.notification.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Handles the Skip / Snooze / Complete / Reply buttons on a reminder notification. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: OccurrenceScheduler
    @Inject lateinit var notificationUtils: NotificationUtils

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // v0.5.2: the BATCH check-in actions carry no occurrence id — handle them before that read.
        if (action == ACTION_BATCH_DONE || action == ACTION_BATCH_SNOOZE) {
            val pending = goAsync()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO +
                    CoroutineExceptionHandler { _, t -> Log.e(TAG, "batch action $action failed", t) }
            )
            scope.launch {
                try {
                    withTimeout(8_000) {
                        if (action == ACTION_BATCH_DONE) scheduler.completeAllBatchToday()
                        else scheduler.snoozeBatchCheckIn()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "batch action $action failed", t)
                } finally {
                    runCatching { notificationUtils.cancelNotification(NotificationUtils.BATCH_NOTIFICATION_ID) }
                    pending.finish()
                }
            }
            return
        }

        val occurrenceId = intent.getStringExtra(NotificationUtils.EXTRA_OCCURRENCE_ID) ?: return
        val isHabit = intent.getBooleanExtra(NotificationUtils.EXTRA_IS_HABIT, false)
        val notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, 0)
        val title = intent.getStringExtra(NotificationUtils.EXTRA_TITLE).orEmpty()
        val replyText = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY_KEY)?.toString()

        Log.i(TAG, "action=$action occ=$occurrenceId isHabit=$isHabit notifId=$notificationId")

        val pending = goAsync()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler { _, t -> Log.e(TAG, "action $action failed", t) }
        )
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-6): a food/med Reply is the one action whose
        // `finally` must NOT do the generic cancel — both outcomes below (postReplyAck on success,
        // postReplyFailed on rejection or on a caught timeout/exception) already post their own
        // replacement notification on this exact id, and a bare cancel afterward would just erase
        // whichever of those the user was supposed to see.
        val isFoodMedReply = action == ACTION_REPLY && !isHabit
        scope.launch {
            try {
                // goAsync() gives ~10s before the system considers the receiver hung; cap the work
                // below that so a stuck Room/Hilt call can't blow the budget and ANR.
                withTimeout(8_000) {
                    when (action) {
                        ACTION_COMPLETE -> if (isHabit) scheduler.completeHabit(occurrenceId)
                        ACTION_SKIP -> if (isHabit) scheduler.skipHabit(occurrenceId) else scheduler.skipFoodMed(occurrenceId)
                        ACTION_SNOOZE -> if (isHabit) scheduler.snoozeHabit(occurrenceId) else scheduler.snoozeFoodMed(occurrenceId)
                        ACTION_REPLY -> if (!isHabit) {
                            // N-6: log FIRST, ack only after it actually succeeds — the old order
                            // replaced the RemoteInput "sending" UI with a "Logged ✓" ack BEFORE the
                            // write happened, so a failure left the user believing an unsaved reply
                            // had been saved. N-7: logFoodMedFromNotificationReply (not the general
                            // logFoodMed) refuses to silently overwrite an already-resolved row.
                            when (val result = scheduler.logFoodMedFromNotificationReply(occurrenceId, replyText.orEmpty())) {
                                is com.daybook.app.data.LogResult.Success ->
                                    notificationUtils.postReplyAck(notificationId, title, replyText.orEmpty())
                                is com.daybook.app.data.LogResult.Rejected ->
                                    notificationUtils.postReplyFailed(occurrenceId, notificationId, title, result.reason)
                                is com.daybook.app.data.LogResult.AlreadyResolved ->
                                    notificationUtils.postAlreadyLoggedNotice(occurrenceId, notificationId, title)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "action $action failed", t)
                // A timeout/exception on the reply path must not silently vanish the notification
                // with the reply lost — post a distinct "couldn't save" notification.
                if (isFoodMedReply && notificationId != 0) {
                    runCatching { notificationUtils.postReplyFailed(occurrenceId, notificationId, title, null) }
                }
            } finally {
                // Dismiss unconditionally and outside the scheduler's mutex: guarantees the
                // notification goes away even if the occurrence was already resolved (so the
                // scheduler early-returned before its own cancel) or a syncAll() sweep holds the
                // lock. (Bug_Fixes item 6) Skipped for a food/med Reply — see the comment above
                // `isFoodMedReply`.
                if (notificationId != 0 && !isFoodMedReply) {
                    runCatching { notificationUtils.cancelNotification(notificationId) }
                }
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotifActionReceiver"

        const val ACTION_COMPLETE = "com.daybook.app.ACTION_COMPLETE"
        const val ACTION_SKIP = "com.daybook.app.ACTION_SKIP"
        const val ACTION_SNOOZE = "com.daybook.app.ACTION_SNOOZE"
        const val ACTION_REPLY = "com.daybook.app.ACTION_REPLY"
        const val REPLY_KEY = "reply_text"

        /** v0.5.2: the two actions on the combined BATCH check-in notification. These carry no
         *  occurrence id, so [onReceive] handles them before it reads EXTRA_OCCURRENCE_ID. */
        const val ACTION_BATCH_DONE = "com.daybook.app.ACTION_BATCH_DONE"
        const val ACTION_BATCH_SNOOZE = "com.daybook.app.ACTION_BATCH_SNOOZE"
    }
}
