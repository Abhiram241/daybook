package com.daybook.app.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.daybook.app.data.model.HealthSession
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthSessionGroupingTest {

    private fun session(
        id: String, exerciseType: Int = WALKING, localDate: String = "2026-09-20",
        startMillis: Long, endMillis: Long, durationMinutes: Int,
        activeCalories: Float? = null, distanceMeters: Float? = null
    ) = HealthSession(
        id = id, localDate = localDate, exerciseType = exerciseType, title = null,
        startMillis = startMillis, endMillis = endMillis, durationMinutes = durationMinutes,
        activeCalories = activeCalories, distanceMeters = distanceMeters, avgHeartRate = null,
        sourceApp = "com.mi.health", updatedAt = 1L
    )

    companion object {
        private val WALKING = ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        private val RUNNING = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
    }

    @Test fun groupSessionsForDisplay_emptyInput_emptyOutput() {
        assertEquals(emptyList<HealthSessionGroup>(), groupSessionsForDisplay(emptyList()))
    }

    @Test fun groupSessionsForDisplay_sameTypeSameDay_mergesIntoOneGroup() {
        // The owner's exact repro: three separate "Walking" segments the same day.
        val sessions = listOf(
            session("a", startMillis = 1_000L, endMillis = 2_000L, durationMinutes = 26, distanceMeters = 2_000f),
            session("b", startMillis = 3_000L, endMillis = 4_000L, durationMinutes = 58, distanceMeters = 4_500f),
            session("c", startMillis = 5_000L, endMillis = 6_000L, durationMinutes = 22, distanceMeters = 1_500f)
        )
        val groups = groupSessionsForDisplay(sessions)
        assertEquals(1, groups.size)
        val g = groups.first()
        assertEquals(3, g.sessions.size)
        assertEquals(106, g.totalDurationMinutes)
        assertEquals(8_000f, g.totalDistanceMeters)
        assertEquals("Walking", g.label)
    }

    @Test fun groupSessionsForDisplay_differentTypesSameDay_staySeparate() {
        val sessions = listOf(
            session("a", exerciseType = WALKING, startMillis = 1_000L, endMillis = 2_000L, durationMinutes = 26),
            session("b", exerciseType = RUNNING, startMillis = 3_000L, endMillis = 4_000L, durationMinutes = 30)
        )
        val groups = groupSessionsForDisplay(sessions)
        assertEquals(2, groups.size)
    }

    @Test fun groupSessionsForDisplay_sameTypeDifferentDay_staySeparate() {
        // Range mode: a walk on two different days must not be summed into one "total distance"
        // tile — that would misrepresent "today's walking" as a multi-day total.
        val sessions = listOf(
            session("a", localDate = "2026-09-19", startMillis = 1_000L, endMillis = 2_000L, durationMinutes = 26),
            session("b", localDate = "2026-09-20", startMillis = 90_000_000L, endMillis = 90_060_000L, durationMinutes = 30)
        )
        val groups = groupSessionsForDisplay(sessions)
        assertEquals(2, groups.size)
        assertEquals(setOf("2026-09-19", "2026-09-20"), groups.map { it.localDate }.toSet())
    }

    @Test fun groupSessionsForDisplay_singleSession_totalsMatchTheOneSession() {
        val sessions = listOf(session("a", startMillis = 1_000L, endMillis = 2_000L, durationMinutes = 45, distanceMeters = 3_000f, activeCalories = 120f))
        val groups = groupSessionsForDisplay(sessions)
        assertEquals(1, groups.size)
        assertEquals(1, groups.first().sessions.size)
        assertEquals(45, groups.first().totalDurationMinutes)
        assertEquals(3_000f, groups.first().totalDistanceMeters)
        assertEquals(120f, groups.first().totalActiveCalories)
    }

    @Test fun groupSessionsForDisplay_missingDistanceOnEverySegment_totalIsNull() {
        // A band/session type with no distance data at all (e.g. strength training) must not
        // report a fabricated "0m" total — null means "no distance data", not zero.
        val sessions = listOf(
            session("a", startMillis = 1_000L, endMillis = 2_000L, durationMinutes = 20),
            session("b", startMillis = 3_000L, endMillis = 4_000L, durationMinutes = 15)
        )
        assertEquals(null, groupSessionsForDisplay(sessions).first().totalDistanceMeters)
    }

    @Test fun groupSessionsForDisplay_groupsOrderedByEarliestSessionInGroup() {
        val sessions = listOf(
            session("later", exerciseType = RUNNING, startMillis = 5_000L, endMillis = 6_000L, durationMinutes = 10),
            session("earlier", exerciseType = WALKING, startMillis = 1_000L, endMillis = 2_000L, durationMinutes = 10)
        )
        val groups = groupSessionsForDisplay(sessions)
        assertEquals(listOf(WALKING, RUNNING), groups.map { it.exerciseType })
    }
}
