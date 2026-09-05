package com.daybook.app.util.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.daybook.app.R
import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.TaskType
import com.daybook.app.util.alarm.AlarmReceiver
import com.daybook.app.util.alarm.NotificationActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "NotificationUtils"

        /** v0.5.3: the single home of the default intake prompt literal. All four call sites
         *  (inline reply, RespondScreen, JournalScreen, this notification) fall back through here. */
        internal fun resolvePrompt(promptMessage: String?): String =
            promptMessage?.takeIf { it.isNotBlank() } ?: "What did you have?"

        /** Customization round (rec 8 / IJ3): the per-habit notification text, or the default. */
        internal fun resolveHabitPrompt(promptMessage: String?): String =
            promptMessage?.takeIf { it.isNotBlank() } ?: "Time to complete this habit"

        /**
         * Channel IDs are versioned. A NotificationChannel is immutable once created: after the
         * first `createNotificationChannel` the system ignores every later importance change, and a
         * user "turn off notifications" on the channel survives app *updates* (only a full uninstall
         * clears it). Builds installed over the top therefore inherit whatever state the very first
         * build left behind — a blocked or IMPORTANCE_NONE channel silently swallows every notify()
         * while the app-level `areNotificationsEnabled()` still reports true. Bumping the suffix
         * hands this build a guaranteed-clean channel. Bump again if channel state is ever suspect.
         */
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 15 (N-11, documentation only — the versioning
        // scheme itself is already correct): bumping either suffix again (e.g. to `_v3`) MUST add
        // the id it replaces ("habits_v2" / "food_med_v2") to LEGACY_CHANNEL_IDS below in the SAME
        // change, or that channel is never cleaned up on startup and can linger blocked in system
        // Settings exactly like the pre-v2 ids this list already exists to sweep.
        const val CHANNEL_HABITS = "habits_v2"
        const val CHANNEL_FOOD_MED = "food_med_v2"

        /** Pre-v2 IDs, deleted on startup so stale/blocked copies can't linger in Settings. */
        private val LEGACY_CHANNEL_IDS = listOf("habits", "food_med")

        const val ACTION_FIRE = "com.daybook.app.ACTION_FIRE"
        /** v0.5.2: the app-wide BATCH habit check-in alarm. */
        const val ACTION_FIRE_BATCH = "com.daybook.app.ACTION_FIRE_BATCH"

        /**
         * v0.5.2: combined BATCH habit check-in notification id. Fixed, below
         * [NotificationIdSequence.START] (1_000), so it can never collide with a per-occurrence id
         * or its id*4+n request codes (999*4+3 = 3999 < 1000*4 = 4000).
         */
        const val BATCH_NOTIFICATION_ID = 999

        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_IS_HABIT = "is_habit"
        const val EXTRA_IS_REFIRE = "is_refire"

        /**
         * The reminder's stored notification id, carried on every action intent so
         * [NotificationActionReceiver] can dismiss the notification unconditionally — even when the
         * scheduler no-ops because the occurrence was already resolved (Bug_Fixes item 6).
         */
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        /** Reminder title, carried on the Reply action so the "Logged: …" ack can echo it. */
        const val EXTRA_TITLE = "notif_title"

        /** Set on the content-intent so MainActivity can open the tapped reminder's detail screen. */
        const val EXTRA_OPEN_OCCURRENCE_ID = "open_occurrence_id"
        const val EXTRA_OPEN_IS_HABIT = "open_is_habit"

        private const val TEST_NOTIFICATION_ID = 424242

        // A reminder's stored notification id is a small monotonic int (NotificationIdSequence).
        // Its four PendingIntent request codes are carved out as id*4 + slot so they never collide
        // across occurrences, and re-arm / cancel for the same occurrence always resolve to the
        // same PendingIntent.
        private const val RC_FIRE = 0
        private const val RC_REFIRE = 1
        private const val RC_OPEN = 2
        private const val RC_ACTION = 3
    }

    /**
     * Called once from [com.daybook.app.DaybookApplication.onCreate], which runs before any
     * `@AndroidEntryPoint` receiver's injection completes — so channels always exist before the
     * first notify(). (Was also created from an `init {}` block here; that duplicate is gone.)
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LEGACY_CHANNEL_IDS.forEach { runCatching { notificationManager.deleteNotificationChannel(it) } }
            val habitChannel = NotificationChannel(
                CHANNEL_HABITS, "Habit reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders to do your habits" }
            val foodMedChannel = NotificationChannel(
                CHANNEL_FOOD_MED, "Intake prompts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Prompts to log what you had" }
            notificationManager.createNotificationChannels(listOf(habitChannel, foodMedChannel))
        }
    }

    /**
     * Why a notification would not appear, or null if nothing is blocking it.
     *
     * [NotificationManagerCompat.areNotificationsEnabled] is an *app-level* check only. A channel
     * the user has turned off reports `IMPORTANCE_NONE` and drops every post while the app-level
     * check still says "enabled" — the exact shape of a "permission is granted but nothing appears"
     * report, so both are checked here and surfaced in Settings.
     */
    fun notificationBlockReason(): String? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "Notifications are turned off for Daybook"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(CHANNEL_HABITS to "Habit reminders", CHANNEL_FOOD_MED to "Intake prompts")
                .forEach { (id, label) ->
                    val ch = notificationManager.getNotificationChannel(id)
                        ?: return "The \"$label\" channel is missing"
                    if (ch.importance == NotificationManager.IMPORTANCE_NONE) {
                        return "The \"$label\" channel is turned off"
                    }
                }
        }
        return null
    }

    /** Posts a notification immediately, isolating `notify()` from the whole alarm pipeline. */
    /**
     * @return null on success, or the reason it was suppressed (Phase 15, N-10 — the Settings
     *   "send test notification" button used to silently no-op on a blocked channel/permission,
     *   with only a bare `Log.w` nobody but a developer would ever see).
     */
    fun postTestNotification(): String? {
        val blocked = notificationBlockReason()
        if (blocked != null) return blocked
        val n = NotificationCompat.Builder(context, CHANNEL_FOOD_MED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daybook test")
            .setContentText("If you can see this, notifications work.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        return if (notify(TEST_NOTIFICATION_ID, n)) null else "Couldn't post the test notification"
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    // ---------------------------------------------------------------------
    // Alarm scheduling
    // ---------------------------------------------------------------------

    private fun firePendingIntent(
        occurrenceId: String,
        notificationId: Int,
        isHabit: Boolean,
        isRefire: Boolean
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_IS_HABIT, isHabit)
            putExtra(EXTRA_IS_REFIRE, isRefire)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId * 4 + (if (isRefire) RC_REFIRE else RC_FIRE),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedules the alarm that will surface [occurrenceId] at [triggerAtMillis]. */
    fun scheduleReminderAlarm(
        occurrenceId: String,
        notificationId: Int,
        isHabit: Boolean,
        triggerAtMillis: Long,
        isRefire: Boolean = false
    ) {
        val pi = firePendingIntent(occurrenceId, notificationId, isHabit, isRefire)
        val exact = canScheduleExactAlarms()
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            Log.i(TAG, "armed ${if (exact) "exact" else "inexact"} alarm occ=$occurrenceId " +
                "refire=$isRefire at=$triggerAtMillis (in ${(triggerAtMillis - System.currentTimeMillis()) / 1000}s)")
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm refused for occ=$occurrenceId, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancelReminderAlarm(occurrenceId: String, notificationId: Int, isHabit: Boolean) {
        alarmManager.cancel(firePendingIntent(occurrenceId, notificationId, isHabit, isRefire = false))
        alarmManager.cancel(firePendingIntent(occurrenceId, notificationId, isHabit, isRefire = true))
    }

    // -------------------------------------------------------------- BATCH habit check-in (v0.5.2)

    private fun batchFirePendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_FIRE_BATCH }
        return PendingIntent.getBroadcast(
            context, BATCH_NOTIFICATION_ID * 4 + RC_FIRE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleBatchCheckInAlarm(triggerAtMillis: Long) {
        val pi = batchFirePendingIntent()
        val exact = canScheduleExactAlarms()
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            Log.i(TAG, "armed ${if (exact) "exact" else "inexact"} BATCH check-in at=$triggerAtMillis " +
                "(in ${(triggerAtMillis - System.currentTimeMillis()) / 1000}s)")
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm refused for BATCH check-in, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancelBatchCheckInAlarm() { alarmManager.cancel(batchFirePendingIntent()) }

    /**
     * One notification covering every unresolved BATCH habit for today. Posted on CHANNEL_HABITS
     * (same importance / user-facing category as individual habit reminders). "Open app" is the
     * body tap (setContentIntent + setAutoCancel), so only Snooze / Done are explicit actions.
     */
    fun showBatchHabitNotification(count: Int, titles: List<String>) {
        val body = titles.take(4).joinToString(", ") + if (titles.size > 4) " +${titles.size - 4} more" else ""
        val n = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_notif_habit)
            .setContentTitle("Fill today's habits")
            .setContentText(if (count == 1) "1 habit left · $body" else "$count habits left · $body")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .addAction(R.drawable.ic_snooze, "Snooze", batchActionIntent(NotificationActionReceiver.ACTION_BATCH_SNOOZE))
            .addAction(R.drawable.ic_complete, "Done", batchActionIntent(NotificationActionReceiver.ACTION_BATCH_DONE))
            .build()
        notify(BATCH_NOTIFICATION_ID, n)
    }

    private fun batchActionIntent(action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_NOTIFICATION_ID, BATCH_NOTIFICATION_ID)
        }
        return PendingIntent.getBroadcast(
            context, BATCH_NOTIFICATION_ID * 4 + RC_ACTION, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------

    /**
     * v0.5.1 §F: status-bar small icon by category.
     *
     * The three `ic_notif_*` drawables currently ship as placeholder copies of `ic_notification`;
     * the UI agent replaces their path data (§F-drawables) without renaming them. `ic_notification`
     * stays the fallback and is still used directly by the two category-less posts,
     * [postTestNotification] and [postReplyAck].
     */
    private fun smallIconFor(type: TaskType?): Int = when (type) {
        TaskType.MED -> R.drawable.ic_notif_med
        TaskType.FOOD, TaskType.CUSTOM, TaskType.JOURNAL -> R.drawable.ic_notif_food
        null -> R.drawable.ic_notification
    }

    /** @return whether the notification was actually posted (Phase 7, N-2) — see [notify]. */
    fun showHabitNotification(occurrence: HabitOccurrence, habitTitle: String, promptMessage: String? = null): Boolean {
        val n = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_notif_habit)
            .setContentTitle(habitTitle)
            // Customization round (rec 8): per-habit text; the batch check-in notification keeps its
            // combined copy (per-habit text on one combined notification isn't meaningful).
            .setContentText(resolveHabitPrompt(promptMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent(occurrence.id, occurrence.notificationId, isHabit = true))
            .setAutoCancel(true)
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-1): a refire re-posts under the SAME
            // notification_id — without this, every refire re-triggers the full sound/vibrate alert
            // even if the original notification is still sitting there un-acted-upon, which is what
            // let an unanswered reminder buzz all night through quiet hours.
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_skip, "Skip",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_SKIP, isHabit = true))
            .addAction(R.drawable.ic_snooze, "Snooze",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_SNOOZE, isHabit = true))
            .addAction(R.drawable.ic_complete, "Complete",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_COMPLETE, isHabit = true))
            .build()
        return notify(occurrence.notificationId, n)
    }

    /**
     * Journal-as-habit round: a JOURNAL-type habit's reminder. Modelled on [showFoodMedNotification]'s
     * `isJournal` branch but posted on the existing `CHANNEL_HABITS` (a Journal habit is still
     * fundamentally "a habit reminder", just with a different action set — no new channel needed,
     * see the plan's risk register), with `ic_notif_habit` (matches [showHabitNotification]) and
     * `isHabit = true` action intents. **Skip + Snooze only — no Complete, no RemoteInput**: there is
     * no "just tick it off" for a chat-answered entry.
     */
    /** @return whether the notification was actually posted (Phase 7, N-2) — see [notify]. */
    fun showHabitJournalNotification(occurrence: HabitOccurrence, habitTitle: String): Boolean {
        val body = "Tap to write today's entry"
        val n = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_notif_habit)
            .setContentTitle(habitTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent(occurrence.id, occurrence.notificationId, isHabit = true))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)   // Phase 7 (N-1) — see showHabitNotification's comment.
            .addAction(R.drawable.ic_skip, "Skip",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_SKIP, isHabit = true))
            .addAction(R.drawable.ic_snooze, "Snooze",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_SNOOZE, isHabit = true))
            .build()
        return notify(occurrence.notificationId, n)
    }

    /**
     * @param type the task's category, used only to pick the small icon (§F). Nullable so a caller
     *   without the row still gets the neutral fallback rather than a wrong glyph.
     * @param promptMessage v0.5.3: the per-reminder custom prompt, or null for the default.
     */
    /** @return whether the notification was actually posted (Phase 7, N-2) — see [notify]. */
    fun showFoodMedNotification(
        occurrence: FoodMedOccurrence,
        taskLabel: String,
        type: TaskType?,
        promptMessage: String? = null
    ): Boolean {
        val isJournal = type == TaskType.JOURNAL
        val prompt = resolvePrompt(promptMessage)
        // v0.5.2 §3 / SD-f: a JOURNAL prompt has no inline RemoteInput — the multi-line description
        // can only be collected on the journal page. The body tap opens it.
        val body = if (isJournal) "Tap to write today's entry" else "$prompt Tap Reply to log it."

        val builder = NotificationCompat.Builder(context, CHANNEL_FOOD_MED)
            .setSmallIcon(smallIconFor(type))
            .setContentTitle(taskLabel)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent(occurrence.id, occurrence.notificationId, isHabit = false))
            .setAutoCancel(true)
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-1) — see showHabitNotification's identical
            // comment; same refire-re-posts-under-the-same-id reasoning applies here.
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_skip, "Skip",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_SKIP, isHabit = false))
            .addAction(R.drawable.ic_snooze, "Snooze",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_SNOOZE, isHabit = false))

        if (!isJournal) {
            val replyInput = RemoteInput.Builder(NotificationActionReceiver.REPLY_KEY)
                .setLabel(prompt)
                .build()
            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_reply, "Reply",
                actionIntent(occurrence.id, occurrence.notificationId, NotificationActionReceiver.ACTION_REPLY, isHabit = false, title = taskLabel)
            ).addRemoteInput(replyInput).setAllowGeneratedReplies(false).build()
            builder.addAction(replyAction)
        }
        return notify(occurrence.notificationId, builder.build())
    }

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-2): returns whether the notification was actually
     * posted. Previously this silently no-op'd (`Log.w` only) on a blocked channel/permission, but
     * every caller in `AlarmReceiver` inserted a SHOWN event and armed the refire regardless — a
     * reminder the user never saw became a phantom "SHOWN → SKIPPED" history entry, and the app
     * stopped re-arming it thinking it had succeeded. Callers that need this gate (`fireHabit`/
     * `fireFoodMed`) now check it; callers that don't (the test notification, the batch summary,
     * the inline-reply ack) simply ignore the return value — unchanged behavior for them.
     */
    private fun notify(id: Int, notification: android.app.Notification): Boolean {
        val blocked = notificationBlockReason()
        if (blocked != null) {
            Log.w(TAG, "notify(id=$id) suppressed: $blocked")
            return false
        }
        return try {
            notificationManager.notify(id, notification)
            Log.i(TAG, "notify(id=$id) posted on channel ${notification.channelId}")
            true
        } catch (t: Throwable) {
            // Never let a bad notification take the process down from a receiver's coroutine.
            Log.e(TAG, "notify(id=$id) threw", t)
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-18) — N-2's whole point was to stop this
            // failure mode from being invisible; Crashlytics is the other half of that.
            com.daybook.app.util.recordUnhandledException(t)
            false
        }
    }

    /**
     * Tapping the notification body. When [occurrenceId] is given, the launch carries it so
     * MainActivity can open that reminder's detail screen (REV-07); otherwise it just opens the
     * app. Also what makes `setAutoCancel(true)` actually dismiss the notification on tap.
     */
    private fun contentIntent(
        occurrenceId: String? = null,
        notificationId: Int = 0,
        isHabit: Boolean = false
    ): PendingIntent {
        val intent = Intent().apply {
            setClassName(context, "com.daybook.app.ui.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (occurrenceId != null) {
                putExtra(EXTRA_OPEN_OCCURRENCE_ID, occurrenceId)
                putExtra(EXTRA_OPEN_IS_HABIT, isHabit)
            }
        }
        return PendingIntent.getActivity(
            context,
            if (occurrenceId != null) notificationId * 4 + RC_OPEN else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * v0.5.1 §G: logs the id it cancels. `notify()` already logs the id it posts (see [notify]),
     * so a single logcat capture now shows post/cancel pairs and the "stale notificationId"
     * hypothesis is falsifiable without a debugger.
     */
    fun cancelNotification(notificationId: Int) {
        Log.i(TAG, "cancelNotification(id=$notificationId)")
        notificationManager.cancel(notificationId)
    }

    /**
     * Posts a lightweight confirmation on the reminder's own notification id after an inline Reply.
     * Submitting RemoteInput text leaves Android holding the notification in a "sending" state that a
     * bare `cancel()` from a background receiver does not reliably clear (notably on Motorola). This
     * replaces that stuck UI with a normal notification the follow-up `cancel()` can remove, and
     * `setTimeoutAfter` guarantees it disappears within 3s even if the cancel is swallowed.
     */
    fun postReplyAck(notificationId: Int, title: String, replyText: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_FOOD_MED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Daybook" })
            .setContentText(if (replyText.isBlank()) "Logged ✓" else "Logged: $replyText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(3_000)
            .build()
        notify(notificationId, n)
    }

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-6): the honest counterpart to [postReplyAck] — an
     * inline-Reply that was rejected (or timed out) must not leave the user believing it saved.
     * Tapping it reopens the app at this occurrence so the reply isn't lost, just re-typed.
     */
    fun postReplyFailed(occurrenceId: String, notificationId: Int, title: String, reason: String?) {
        val body = "Couldn't save${reason?.let { ": $it" } ?: ""} — tap to retry"
        val n = NotificationCompat.Builder(context, CHANNEL_FOOD_MED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Daybook" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(occurrenceId, notificationId, isHabit = false))
            .setAutoCancel(true)
            .build()
        notify(notificationId, n)
    }

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-7): a stale/duplicate notification-Reply action
     * landed on an occurrence that's already resolved — distinct copy from [postReplyFailed] since
     * nothing actually failed here, the reply is just no longer applicable to this slot.
     */
    fun postAlreadyLoggedNotice(occurrenceId: String, notificationId: Int, title: String) {
        val body = "Already logged — open the app to edit"
        val n = NotificationCompat.Builder(context, CHANNEL_FOOD_MED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Daybook" })
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(occurrenceId, notificationId, isHabit = false))
            .setAutoCancel(true)
            .setTimeoutAfter(5_000)
            .build()
        notify(notificationId, n)
    }

    private fun actionIntent(
        occurrenceId: String,
        notificationId: Int,
        action: String,
        isHabit: Boolean,
        title: String? = null
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_IS_HABIT, isHabit)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            if (title != null) putExtra(EXTRA_TITLE, title)
        }
        val flags = if (action == NotificationActionReceiver.ACTION_REPLY) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        // All of an occurrence's action buttons share the id*4+3 request code but differ by
        // Intent.action, which PendingIntent.filterEquals() distinguishes; different occurrences
        // have different notification ids, so no cross-occurrence collision.
        return PendingIntent.getBroadcast(context, notificationId * 4 + RC_ACTION, intent, flags)
    }
}
