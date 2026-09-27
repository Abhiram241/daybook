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
import com.daybook.app.data.UploadPendingOutcome
import com.daybook.app.data.WorkoutPhotoCloudSync
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Fallback upload sweep for pending workout session photos (Firebase Storage backup round,
 * 2026-09-20 — see `WorkoutPhotoCloudSync`'s KDoc). Mirrors [SyncFlushWorker]'s exact shape:
 * enqueued as a belt-and-braces fallback alongside the repository's own fire-and-forget upload
 * launch (covers a process kill mid-upload), and again on sign-in (a backlog captured while
 * signed out gets uploaded once there's a uid to attach it to).
 */
@HiltWorker
class WorkoutPhotoUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val photoCloudSync: WorkoutPhotoCloudSync
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        // Audit finding #18 fix — NoAccount is not retry-worthy: nobody is signed in, and sign-in
        // itself already explicitly re-enqueues this worker (CloudSyncRepository.onAuthState).
        // Retrying every ~30s while signed out achieved nothing and never stopped.
        when (photoCloudSync.uploadPending()) {
            UploadPendingOutcome.NoAccount, UploadPendingOutcome.AllUploaded -> Result.success()
            UploadPendingOutcome.PartialFailure -> Result.retry()
        }
    } catch (t: Throwable) {
        Log.e(TAG, "workout photo upload failed", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "WorkoutPhotoUploadWorker"
        private const val UNIQUE_NAME = "daybook-workout-photo-upload"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WorkoutPhotoUploadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            // Battery fix (finding #18) — every caller here is routine (a photo was just added, or
            // an account just signed in); an upload already actually in flight from a previous
            // enqueue must not be cancelled and restarted by a later one landing on top of it.
            // KEEP lets an in-flight/backoff-waiting attempt run its own course.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
