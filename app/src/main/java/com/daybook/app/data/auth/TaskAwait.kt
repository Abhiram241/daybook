package com.daybook.app.data.auth

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal `Task` → coroutine bridge so the sync/auth layers don't need
 * `kotlinx-coroutines-play-services` on the classpath (FIREBASE_0.5_PLAN.md §1 pins deps
 * deliberately; nothing there adds it).
 */
internal suspend fun <T> Task<T>.awaitCompat(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val e = task.exception
        when {
            e != null -> cont.resumeWithException(e)
            task.isCanceled -> cont.cancel()
            else -> @Suppress("UNCHECKED_CAST") cont.resume(task.result as T)
        }
    }
}
