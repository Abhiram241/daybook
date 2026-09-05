package com.daybook.app.util.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.util.work.WindowRefreshWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Re-arms all reminder alarms after any event that clears or invalidates them: a reboot, an app
 * update (installing a new APK drops every pending alarm), or a timezone / clock change (occurrences
 * store absolute epoch millis computed in the old zone).
 *
 * v0.5.3 Phase 3 (S17): on `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` this keeps `syncAll()`
 * (which re-arms alarms in the new zone) but does **not** force a re-partition push. History is
 * bucketed by the local calendar date recorded on each occurrence when it was created
 * (`local_date`, added in Phase 2) — a timezone change does not re-bucket it. Only newly-created
 * occurrences adopt the new zone. The audit's earlier mitigation (`doPush(force = true)` on tz
 * change) is therefore no longer needed.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: OccurrenceScheduler

    private companion object {
        const val TAG = "BootCompletedReceiver"
        val REARM_ACTIONS = buildSet {
            add(Intent.ACTION_BOOT_COMPLETED)
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 15 (N-9): ACTION_LOCKED_BOOT_COMPLETED removed
            // — see the matching manifest comment. Never actually delivered to this receiver.
            add(Intent.ACTION_MY_PACKAGE_REPLACED)
            add(Intent.ACTION_TIMEZONE_CHANGED)
            add(Intent.ACTION_TIME_CHANGED)
            add("android.intent.action.QUICKBOOT_POWERON")
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-4): fires when the user grants/revokes
            // the exact-alarm permission from system Settings (API 31+) — previously nothing
            // re-armed alarms after a grant, so a user who fixed the permission had to force-stop
            // or reboot the app before reminders actually resumed. `syncAll()` (already what every
            // other REARM_ACTIONS entry triggers) does the right thing once triggered.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in REARM_ACTIONS) return
        Log.i(TAG, "re-arming all alarms after $action")

        val pending = goAsync()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler { _, t -> Log.e(TAG, "re-arm after $action failed", t) }
        )
        scope.launch {
            try {
                // syncAll() walks every active item; cap it so a stuck query can't hang the
                // receiver indefinitely. Background broadcasts get a longer budget than the ~10s
                // foreground one, so 20s leaves headroom for a cold Room + Hilt init.
                withTimeout(20_000) {
                    scheduler.syncAll()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "re-arm after $action failed", t)
            } finally {
                // Idempotent (UPDATE policy): register the daily background refresh even if the
                // app process has never started since install / update — and even if syncAll above
                // timed out.
                runCatching { WindowRefreshWorker.enqueue(context) }
                pending.finish()
            }
        }
    }
}
