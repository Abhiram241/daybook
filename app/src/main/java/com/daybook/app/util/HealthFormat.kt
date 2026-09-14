package com.daybook.app.util

import java.util.Locale
import kotlin.math.roundToLong

/**
 * B6 (§7.3) — the distance/duration formatter shared between the Health tab and any future
 * session-detail surface, per the plan's instruction to put it in `util/` rather than duplicate
 * `WorkoutLogic`'s private `formatDistanceKm`.
 */
fun formatHealthDistanceKm(meters: Float): String {
    val km = meters / 1000f
    val rounded = Math.round(km * 10f) / 10f
    return "${if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()} km"
}

/** "7h 32m" / "48m" / "0m" — never a raw decimal-hours or bare-minutes-past-1000 number. */
fun formatHealthDuration(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun formatHealthCalories(kcal: Float): String = "${kcal.roundToLong()} kcal"

fun formatHealthCount(n: Number): String = String.format(Locale.US, "%,d", n.toLong())
