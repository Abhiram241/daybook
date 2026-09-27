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
/** Audit finding #18 fix — [WorkoutPhotoUploadWorker] used to collapse every non-success into a
 *  single `Result.retry()`, including "signed out" — a permanent state, for this worker's
 *  purposes, that retrying every ~30s can never resolve (sign-in itself already explicitly
 *  re-enqueues this worker — see `CloudSyncRepository.onAuthState`). Distinguishing the two lets
 *  the worker report success (and go quiet) when there's genuinely nothing it can do right now,
 *  while still retrying a real per-photo upload failure. */
sealed interface UploadPendingOutcome {
    /** Nobody is signed in — retrying achieves nothing until sign-in re-enqueues this worker. */
    data object NoAccount : UploadPendingOutcome
    data object AllUploaded : UploadPendingOutcome
    data object PartialFailure : UploadPendingOutcome
}

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
    suspend fun uploadPending(): UploadPendingOutcome {
        val uid = (auth.state.first { it !is AuthState.Loading } as? AuthState.SignedIn)?.uid
            ?: return UploadPendingOutcome.NoAccount
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
        return if (allSucceeded) UploadPendingOutcome.AllUploaded else UploadPendingOutcome.PartialFailure
    }

    /**
     * Audit finding #17 fix — a photo's cloud copy (`putFile` above) was never deleted when the
     * local row/file was: not on "Remove" from the retake sheet, not on deleting a whole session,
     * not on account deletion. Every one of those left an orphaned blob in Firebase Storage
     * forever, unreachable from the app and billed indefinitely. Best-effort: a photo never
     * actually uploaded (`cloudPath == null`), already gone, or unreachable right now is not worth
     * failing the local delete over — matches this class's existing "never throws" convention.
     */
    suspend fun deleteCloudPhotos(cloudPaths: List<String>) {
        for (path in cloudPaths) {
            runCatching { storage.reference.child(path).delete().awaitCompat() }
        }
    }

    /**
     * Audit finding #17 fix — called from [com.daybook.app.data.sync.CloudSyncRepository
     * .deleteRemoteDoc] (account deletion). A Firestore parent-doc `delete()` never touches
     * Storage at all, so without this every deleted account left its workout photos orphaned in
     * Storage forever — unreachable from the app, and still billed. Best-effort: a listing/delete
     * failure here must not block the rest of account deletion (matches this class's existing
     * "never throws" convention) — returns whether it fully succeeded purely for logging.
     */
    suspend fun deleteAllCloudPhotos(uid: String): Boolean = runCatching {
        val root = storage.reference.child("users/$uid/workout_photos")
        val listing = root.listAll().awaitCompat()
        // Layout is users/{uid}/workout_photos/{sessionId}/{filename} — one folder per session.
        for (sessionFolder in listing.prefixes) {
            val files = runCatching { sessionFolder.listAll().awaitCompat() }.getOrNull() ?: continue
            for (item in files.items) runCatching { item.delete().awaitCompat() }
        }
        // Cheap insurance against any stray file directly under the root.
        for (item in listing.items) runCatching { item.delete().awaitCompat() }
    }.isSuccess

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
