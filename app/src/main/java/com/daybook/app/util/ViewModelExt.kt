package com.daybook.app.util

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-9 + C-18, High, combined): every ViewModel in the
 * app called `viewModelScope.launch { ... }` directly — an unhandled exception inside one of those
 * ~92 call sites crashes the whole process, and nothing about the crash ever reached a dashboard.
 * `safeLaunch` is a drop-in replacement: same call shape, but a thrown exception is caught,
 * reported to Crashlytics, logged, and the coroutine simply ends — it does not propagate to the
 * process's default uncaught-exception handler.
 *
 * Deliberately NOT a fix for [CrashHandler] (Phase 0a) — that stays as the last-resort local
 * safety net for whatever this migration doesn't cover (a cold `Flow` pipeline's `stateIn`, for
 * instance — see `HomeViewModel`'s `.catch { }` additions, which use the same [onError] shape).
 */
fun ViewModel.safeLaunch(
    // Lets the ~3 existing `viewModelScope.launch(Dispatchers.IO) { ... }` call sites migrate
    // without losing their dispatcher — e.g. `safeLaunch(Dispatchers.IO) { ... }`.
    context: CoroutineContext = EmptyCoroutineContext,
    onError: (Throwable) -> Unit = { t -> recordUnhandledException(t) },
    block: suspend CoroutineScope.() -> Unit
): Job = viewModelScope.launch(context + CoroutineExceptionHandler { _, t -> onError(t) }, block = block)

/**
 * Shared with [safeLaunch]'s default `onError`, exposed separately so a cold `Flow` pipeline's
 * `.catch { }` (which isn't a `launch` block `safeLaunch` can wrap) reports through the exact same
 * path instead of inventing a second convention — and every other Phase 10 (C-18) call site that
 * records a previously-swallowed exception uses this too, for one reason: [FirebaseCrashlytics]
 * requires a `FirebaseApp` to have been initialized, which is true in the running app (the
 * `com.google.gms.google-services` plugin wires that up automatically at process start) but NOT in
 * a plain JVM unit test — and this function itself gets exercised by tests that deliberately drive
 * a failure path (e.g. `MonthPartitionerTest`'s garbage-JSON case). Swallowing a failure from the
 * reporting call itself (never from the real exception, which is still logged) means "Crashlytics
 * isn't ready yet" can never itself become a second, worse crash. `android.util.Log` is guarded
 * for the identical reason: this project's plain-JVM unit tests run against the unmocked Android
 * SDK stub jar (no Robolectric, no `unitTests.returnDefaultValues`), which makes ANY `Log.*` call
 * throw `RuntimeException: ... not mocked` — and several of the pure functions this is called from
 * (e.g. `MonthPartitioner.decodeDays`) are exercised directly by such tests.
 */
fun recordUnhandledException(t: Throwable) {
    runCatching { Log.e("ViewModel", "unhandled exception", t) }
    runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
}
