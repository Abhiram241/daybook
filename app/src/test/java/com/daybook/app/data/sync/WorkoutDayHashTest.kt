package com.daybook.app.data.sync

import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.WorkoutExerciseLog
import com.daybook.app.data.backup.WorkoutLog
import com.daybook.app.data.backup.WorkoutSetLog
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4 (§4.2) — `DayEntry.workouts` must not churn a month's `contentHash` for a non-workout day:
 * present-but-empty serialises IDENTICALLY to the field being absent. Also covers the
 * `WorkoutLog.routineId = null` case (an ad-hoc / imported session) being byte-identical to a
 * `WorkoutLog` without the field at all.
 */
@OptIn(ExperimentalSerializationApi::class)
class WorkoutDayHashTest {

    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun log(routineId: String? = null) = WorkoutLog(
        id = "w1", startedAt = "2026-08-29T06:00:00Z", routineId = routineId,
        exercises = listOf(
            WorkoutExerciseLog(
                id = "we1", exerciseId = "builtin:bench-press", orderIndex = 0,
                sets = listOf(WorkoutSetLog(id = "s1", setNumber = 1, reps = 10, weightKg = 60f))
            )
        )
    )

    private fun day(workouts: List<WorkoutLog> = emptyList()) =
        DayEntry(date = "2026-08-29", workouts = workouts)

    @Test fun differentWorkouts_hashesDiffer() {
        assertNotEquals(ContentHash.ofDays(listOf(day())), ContentHash.ofDays(listOf(day(listOf(log())))))
    }

    @Test fun emptyWorkouts_isAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(DayEntry.serializer(), day())
        assertFalse("workouts must NOT appear: $canonical", canonical.contains("\"workouts\""))

        val withOne = canonicalJson.encodeToString(DayEntry.serializer(), day(listOf(log())))
        assertTrue(withOne.contains("\"workouts\""))
    }

    @Test fun emptyWorkouts_hashesSameAsFieldAbsent() {
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absent = lenient.decodeFromString(DayEntry.serializer(), """{"date":"2026-08-29"}""")
        assertTrue(absent.workouts.isEmpty())
        assertEquals(ContentHash.ofDays(listOf(absent)), ContentHash.ofDays(listOf(day())))
    }

    /** A dangling / never-set routineId round-trips exactly like the field being omitted. */
    @Test fun nullRoutineId_isByteIdenticalToFieldAbsent() {
        val withNull = canonicalJson.encodeToString(DayEntry.serializer(), day(listOf(log(routineId = null))))
        assertFalse("routineId must NOT appear when null: $withNull", withNull.contains("routineId"))

        val withRoutine = canonicalJson.encodeToString(DayEntry.serializer(), day(listOf(log(routineId = "r1"))))
        assertTrue(withRoutine.contains("routineId"))
    }
}
