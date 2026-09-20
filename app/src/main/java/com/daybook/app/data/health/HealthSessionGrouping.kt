package com.daybook.app.data.health

import com.daybook.app.data.model.HealthSession

/**
 * HEALTH_RANGE_TAB_AND_SYNC_AUDIT_2026-09-20.md R-2 — owner review of the Health tab screenshot
 * (build 50): Day mode was listing several separate "Walking" tiles ("26m", "58m", "22m") instead
 * of one summary, because there was no session-grouping logic anywhere in the app —
 * `HealthConnectReader.exerciseSessions` maps every `ExerciseSessionRecord` Health Connect returns
 * 1:1 (bands/auto-detected-activity apps routinely split one walk into several short segments),
 * and `HealthTabScreen` rendered each one as its own `SessionRow`, unconditionally, in both Day and
 * Range mode.
 *
 * The chosen grouping rule (R-2's own suggestion): same [HealthSession.exerciseType] + same
 * [HealthSession.localDate] is "the same activity" and collapses into one tile — summing
 * duration/calories/distance, the individual segments still available in the tile's detail sheet.
 * In Day mode every session already shares one `localDate`, so this reduces to "group by type";
 * in Range mode it keeps each day's tile separate rather than summing a whole week's walks into
 * one number, which is what "total distance for THAT DAY's walking" (the owner's ask) means.
 *
 * Pure, no I/O — same reasoning as `aggregateHealthDays`/`visibleHealthCards`, this is the one
 * choke point [HealthTabScreen]'s `HealthContent` calls for both Day and Range mode so the fix
 * isn't duplicated per mode.
 */
data class HealthSessionGroup(
    val exerciseType: Int,
    val localDate: String,
    val label: String,
    val sessions: List<HealthSession>,
    val totalDurationMinutes: Int,
    val totalDistanceMeters: Float?,
    val totalActiveCalories: Float?,
    val earliestStartMillis: Long
)

fun groupSessionsForDisplay(sessions: List<HealthSession>): List<HealthSessionGroup> {
    if (sessions.isEmpty()) return emptyList()
    return sessions
        .groupBy { it.exerciseType to it.localDate }
        .map { (key, group) ->
            val sorted = group.sortedBy { it.startMillis }
            val distances = sorted.mapNotNull { it.distanceMeters }
            val calories = sorted.mapNotNull { it.activeCalories }
            HealthSessionGroup(
                exerciseType = key.first,
                localDate = key.second,
                label = ExerciseTypeLabels.labelFor(key.first),
                sessions = sorted,
                totalDurationMinutes = sorted.sumOf { it.durationMinutes },
                totalDistanceMeters = distances.takeIf { it.isNotEmpty() }?.sum(),
                totalActiveCalories = calories.takeIf { it.isNotEmpty() }?.sum(),
                earliestStartMillis = sorted.first().startMillis
            )
        }
        .sortedBy { it.earliestStartMillis }
}
