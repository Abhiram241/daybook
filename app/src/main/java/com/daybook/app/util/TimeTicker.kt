package com.daybook.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * v0.5.3 Phase 3 (A8): a coarse wall-clock ticker.
 *
 * The next-pending queries in `RoutinesViewModel` / `FoodMedViewModel` used to bind
 * `System.currentTimeMillis()` once, at ViewModel construction — so "Next: …" drifted further into
 * the past the longer the screen stayed alive, and a slot that had already fired still showed as
 * upcoming. Re-collecting the query off this flow re-evaluates `now` every minute.
 *
 * Emits immediately, then once per [periodMs]. Cold — one timer per active collector, cancelled
 * with the collector.
 */
fun minuteTicker(periodMs: Long = 60_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMs)
    }
}
