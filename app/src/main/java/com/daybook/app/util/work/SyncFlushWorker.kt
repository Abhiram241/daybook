package com.daybook.app.util.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daybook.app.data.sync.CloudSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Fallback flush for a pending cloud push (FIREBASE_0.5_PLAN.md §4).
 *
 * Enqueued on `ON_STOP` when [com.daybook.app.data.sync.SyncStateStore.pendingPush] is true, to
 * cover a process kill that happens before Firestore's own local write queue even enqueues the
 * `set()`. Mirrors the `@HiltWorker` + `@AssistedInject` shape of [WindowRefreshWorker].
 */
@HiltWorker
class SyncFlushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cloudSync: CloudSyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        if (cloudSync.flushPendingPush()) Result.success() else Result.retry()
    } catch (t: Throwable) {
        Log.e(TAG, "sync flush failed", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "SyncFlushWorker"
        private const val UNIQUE_NAME = "daybook-sync-flush"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncFlushWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                // v0.5.3 Phase 1 (S7): a Result.retry() (uid unresolved after a process death) now
                // backs off linearly instead of hammering.
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
