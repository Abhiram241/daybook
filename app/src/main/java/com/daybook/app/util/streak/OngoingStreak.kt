package com.daybook.app.util.streak

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * v0.5.5 — inclusive running-day count for an "Ongoing" (STREAK) habit. Pure, no DI, no
 * dependency on [StreakCalculator] / occurrence logic.
 *
 * Started today -> 1. Started yesterday -> 2. A future start (or a null-safe caller passing a
 * sentinel) -> 0.
 */
fun daysSince(startMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    if (now.isBefore(start)) return 0
    return (ChronoUnit.DAYS.between(start, now) + 1).toInt()
}
