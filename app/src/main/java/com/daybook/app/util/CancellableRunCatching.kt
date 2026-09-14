package com.daybook.app.util

import kotlinx.coroutines.CancellationException

/**
 * Investigation finding (user report: Health tab pull-to-refresh "spinner sometimes never goes
 * away") — plain `kotlin.runCatching` catches `Throwable`, which includes `CancellationException`
 * (and its subtype `TimeoutCancellationException`, thrown by `withTimeoutOrNull`/`withTimeout`).
 * The whole health-sync path (`HealthRepository`, `HealthConnectReader`) wraps essentially every
 * suspend Health Connect call in `runCatching` for a different, legitimate reason (C6: a Health
 * Connect read must never crash the app) — but that means when `HealthTabViewModel.refreshNow`'s
 * `withTimeoutOrNull(20_000)` fires, the cancellation it throws gets silently absorbed by the
 * nearest `runCatching` instead of propagating, so the coroutine does NOT actually stop: it keeps
 * making Health Connect calls (each one immediately re-cancelling and getting re-swallowed) well
 * past the point the ViewModel already gave up and reset `_isRefreshing`. On a device/provider
 * where Health Connect serializes requests from one client, that leftover "zombie" work can keep
 * contending for the same binder connection a NEW manual refresh needs, compounding the stall on
 * the next swipe — this is the leading candidate for "sometimes never goes away".
 *
 * Drop-in replacement for `runCatching` at every one of those call sites: identical shape/behavior
 * for a genuine failure, but a cancellation is rethrown instead of boxed into a `Result.failure`,
 * so structured concurrency's cancellation actually takes effect.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Result.failure(t)
}
