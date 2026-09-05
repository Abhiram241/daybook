package com.daybook.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * rec 5 (N1) — quiet hours. A due alarm that would fire inside the user's quiet window is
 * **deferred to the window's end** (`quiet_end` on the same or next local day), never silently
 * dropped (SD-3). Pure functions — exhaustively covered by `QuietHoursTest`.
 *
 * When quiet hours is disabled [deferIfInsideQuietHours] is the identity function, so every
 * arm / snooze / batch path is byte-for-byte unchanged for a user who never turns it on
 * (§2 DO-NOT-TOUCH invariant #5).
 */

/** Is [t] inside `[start, end)`, handling a window that wraps past midnight? */
internal fun isInsideQuietWindow(t: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (start < end) t >= start && t < end
    else if (start > end) t >= start || t < end
    else false // start == end → zero-width window, never "inside"

/**
 * Returns [triggerAtMillis] unchanged when quiet hours is off, the window is degenerate, or the
 * instant is outside the window; otherwise the epoch-millis of [end] at the first local
 * occurrence at/after the trigger.
 */
internal fun deferIfInsideQuietHours(
    triggerAtMillis: Long,
    enabled: Boolean,
    start: String,
    end: String,
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    if (!enabled) return triggerAtMillis
    val s = runCatching { LocalTime.parse(start) }.getOrDefault(LocalTime.of(22, 0))
    val e = runCatching { LocalTime.parse(end) }.getOrDefault(LocalTime.of(7, 0))
    if (s == e) return triggerAtMillis

    val local = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(triggerAtMillis), zone)
    val time = local.toLocalTime()
    if (!isInsideQuietWindow(time, s, e)) return triggerAtMillis

    // The window's end lands today if the trigger's time-of-day is before `end` (a non-wrap
    // window, or the early-morning tail of a wrap window); otherwise it's tomorrow.
    val endDate: LocalDate = if (time < e) local.toLocalDate() else local.toLocalDate().plusDays(1)
    return endDate.atTime(e).atZone(zone).toInstant().toEpochMilli()
}
