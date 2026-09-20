package com.daybook.app.data

import android.net.Uri
import com.daybook.app.data.auth.AuthRepository
import com.daybook.app.data.auth.AuthState
import com.daybook.app.data.auth.awaitCompat
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.WorkoutSessionPhoto
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Firebase Storage backup round (2026-09-20) — uploads each locally-downscaled workout session
 * photo (already ≤1024px/~150-300KB via `WorkoutPhotoStore`, re-uploaded AS-IS, never
 * re-compressed) to `users/{uid}/workout_photos/{sessionId}/{filename}`, once per photo,
 * best-effort. Room's `workout_session_photos.cloud_uploaded_at IS NULL` IS the durable upload
 * queue — no separate queue needed (mirrors how `SyncFlushWorker`/`syncState.pendingPush` track a
 * pending Firestore push). A signed-out user or no connectivity simply leaves rows pending;
 * [com.daybook.app.util.work.WorkoutPhotoUploadWorker] (network-constrained, WorkManager) retries
 * later — never fails the local save, matching this app's "local Room write always wins, cloud is
 * best-effort" convention throughout `CloudSyncRepository`.
 *
 * Uses `Task.awaitCompat()` (`data.auth.TaskAwait`) rather than
 * `kotlinx-coroutines-play-services`, matching the precedent `CloudSyncRepository`'s own Firestore
 * `Task` calls already follow — this app deliberately keeps that dependency off the classpath.
 */
@Singleton
class WorkoutPhotoCloudSync @Inject constructor(
    private val database: AppDatabase,
    private val auth: AuthRepository,
    private val storage: FirebaseStorage
) {
    /** Called after a photo is persisted locally (`WorkoutRepository`'s session-save path) AND by
     *  `WorkoutPhotoUploadWorker`'s sweep. Uploads every currently-pending photo; a per-photo
     *  failure (offline, denied, etc.) just leaves that row pending for the next attempt — never
     *  throws out of this function. */
    suspend fun uploadPending(): Boolean {
        val uid = (auth.state.first { it !is AuthState.Loading } as? AuthState.SignedIn)?.uid ?: return false
        val pending = database.workoutPhotoDao().getPendingUpload()
        var allSucceeded = true
        for (photo in pending) {
            val file = File(photo.filePath)
            if (!file.exists()) continue // local file already gone — nothing to back up; leave row as-is
            val cloudPath = "users/$uid/workout_photos/${photo.sessionId}/${file.name}"
            val ok = runCatching {
                storage.reference.child(cloudPath).putFile(Uri.fromFile(file)).awaitCompat()
                database.workoutPhotoDao().markUploaded(photo.id, cloudPath, System.currentTimeMillis())
            }.isSuccess
            if (!ok) allSucceeded = false
        }
        return allSucceeded
    }

    /** §3.5 rehydrate path — called lazily from the workout session DETAIL screen only (never the
     *  history list, never on app start): if the local file is already there, returns it as-is
     *  (the common case, a plain `File.exists()` stat). Only on a miss (reinstall / second device)
     *  does this touch the network, downloading the backed-up copy back into the same
     *  `filesDir/workout_photos/` path. Returns null (never throws) if there is nothing to
     *  rehydrate from or the download fails — callers fall back to `photo.filePath` unchanged. */
    suspend fun rehydrateIfMissing(photo: WorkoutSessionPhoto): String? {
        if (File(photo.filePath).exists()) return photo.filePath
        val cloudPath = photo.cloudPath ?: return null
        return runCatching {
            val target = File(photo.filePath)
            target.parentFile?.mkdirs()
            storage.reference.child(cloudPath).getFile(target).awaitCompat()
            target.absolutePath
        }.getOrNull()
    }
}
