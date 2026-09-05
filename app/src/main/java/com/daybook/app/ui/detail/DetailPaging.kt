package com.daybook.app.ui.detail

/**
 * v0.5.3 Phase 3 (A4): offset/limit for a zero-based timeline page. `TIMELINE_LIMIT` was only ever
 * applied to the activity events; the terminal (occurrence-derived) rows were uncapped by design,
 * so a multi-year 4×/day reminder (~7,300 rows) was a fold + sort + open-screen stall. The Detail
 * timeline now loads the newest page first and appends older pages on demand.
 *
 * Pure — [TerminalPagingTest].
 */
internal fun pageBounds(page: Int, size: Int): Pair<Int, Int> {
    val p = page.coerceAtLeast(0)
    val s = size.coerceAtLeast(0)
    return (p * s) to s
}
