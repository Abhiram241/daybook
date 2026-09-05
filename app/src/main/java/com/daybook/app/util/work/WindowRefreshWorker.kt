package com.daybook.app.util.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.sync.CloudSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Runs ~once a day to regenerate the rolling occurrence window and re-arm alarms (REV-06).
 *
 * `MainActivity` and `BootCompletedReceiver` only refresh the window on launch / reboot, so a
 * user who answers reminders purely from the notification shade would hit silence once the
 * 7-day window drains. WorkManager survives reboot and app-standby buckets, so this keeps the
 * window topped up regardless of whether the app is ever opened.
 */
@HiltWorker
class WindowRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: OccurrenceScheduler,
    private val cloudSync: CloudSyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        scheduler.syncAll()
        // v0.5.3 Phase 1 (S3): month eviction now runs here (off the hot push path) — plus, in a
        // later phase, the retention sweep.
        runCatching { cloudSync.runMaintenance() }
        Result.success()
    } catch (t: Throwable) {
        Log.e(TAG, "window refresh failed", t)
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-18) — the second of the two named
        // "AlarmReceiver/worker catch(Throwable)" sites the plan calls out.
        com.daybook.app.util.recordUnhandledException(t)
        Result.retry()
    }

    companion object {
        private const val TAG = "WindowRefreshWorker"
        private const val UNIQUE_NAME = "daybook-window-refresh"

        /** Idempotent: safe to call from every process start / reboot. */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WindowRefreshWorker>(
                24, TimeUnit.HOURS,
                6, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
