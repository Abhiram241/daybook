package com.daybook.app.data

import com.daybook.app.data.backup.BackupMeta
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.ExerciseDef
import com.daybook.app.data.backup.HealthDayLog
import com.daybook.app.data.backup.RoutineDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for AUDIT_2026-09-16.md's two Critical data-loss findings, S-1 and S-2.
 * Both fixes hinge on small pure functions extracted specifically so they're testable without Room
 * — see each function's own KDoc in `ExportImportRepository.kt` for the full trace of the bug.
 */
class ExportImportMergeSafetyTest {

    // ---------------------------------------------------------------------------------- S-2

    private fun meta(kind: String = BackupMeta.KIND_DAYBOOK) =
        BackupMeta(exportedAt = "2026-09-16T00:00:00Z", appVersionName = "0.8.0", kind = kind)

    private fun exercise(id: String) = ExerciseDef(
        id = id, name = "Custom $id", primaryMuscle = "BICEPS", equipment = "CABLE",
        trackingMode = "WEIGHT_REPS", createdAt = "2026-08-01T00:00:00Z"
    )

    private fun routine(id: String) = RoutineDef(
        id = id, name = "Routine $id", orderIndex = 0,
        createdAt = "2026-08-01T00:00:00Z", updatedAt = "2026-08-01T00:00:00Z"
    )

    @Test fun daybookKindFile_isNeverAuthoritativeForExercisesAndRoutines() {
        // S-2 — a "Daybook" range file always zeroes these fields; restoring one must never be
        // read as "the remote set is empty, so delete every local custom exercise and routine".
        val defs = Definitions(customExercises = emptyList(), routines = emptyList())
        assertFalse(exercisesAndRoutinesAuthoritative(meta(BackupMeta.KIND_DAYBOOK), defs))
    }

    @Test fun daybookKindFile_isNeverAuthoritative_evenIfItSomehowCarriesExercises() {
        // Belt-and-braces: kind alone is disqualifying, regardless of what the file contains.
        val defs = Definitions(customExercises = listOf(exercise("ex1")), routines = emptyList())
        assertFalse(exercisesAndRoutinesAuthoritative(meta(BackupMeta.KIND_DAYBOOK), defs))
    }

    @Test fun beastModeKindFile_withExercises_isAuthoritative() {
        // A genuine Beast Mode file carrying real definitions is exactly the case that must still
        // prune a locally-deleted exercise or routine.
        val defs = Definitions(customExercises = listOf(exercise("ex1")), routines = emptyList())
        assertTrue(exercisesAndRoutinesAuthoritative(meta(BackupMeta.KIND_BEAST_MODE), defs))
    }

    @Test fun beastModeKindFile_withRoutinesOnly_isAuthoritative() {
        val defs = Definitions(customExercises = emptyList(), routines = listOf(routine("r1")))
        assertTrue(exercisesAndRoutinesAuthoritative(meta(BackupMeta.KIND_BEAST_MODE), defs))
    }

    @Test fun nonDaybookKindFile_withBothEmpty_isNotAuthoritative() {
        // An empty Beast Mode export (e.g. a brand-new account) must not be read as "delete
        // everything" either — emptiness alone, regardless of kind, is never authoritative.
        val defs = Definitions(customExercises = emptyList(), routines = emptyList())
        assertFalse(exercisesAndRoutinesAuthoritative(meta(BackupMeta.KIND_BEAST_MODE), defs))
    }

    // ---------------------------------------------------------------------------------- S-1

    private fun healthLog(steps: Int = 1000) = HealthDayLog(steps = steps)

    @Test fun mapDaysToHealth_skipsDaysWithNoHealthPayload() {
        // The second-device bug: a device with no band pushes a month whose DayEntries all have
        // `health == null`. Those dates must never be treated as "named" for health merge purposes.
        val days = listOf(
            DayEntry(date = "2026-09-01", health = healthLog()),
            DayEntry(date = "2026-09-02", health = null),
            DayEntry(date = "2026-09-03")
        )
        val (healthDays, _, _) = mapDaysToHealth(days)
        assertEquals(listOf("2026-09-01"), healthDays.map { it.localDate })
    }

    @Test fun mapDaysToHydration_skipsDaysWithNoOrZeroHydration() {
        val days = listOf(
            DayEntry(date = "2026-09-01", hydrationMl = 1500),
            DayEntry(date = "2026-09-02", hydrationMl = null),
            DayEntry(date = "2026-09-03", hydrationMl = 0)
        )
        val hydration = mapDaysToHydration(days)
        assertEquals(listOf("2026-09-01"), hydration.map { it.localDate })
    }

    @Test fun mapDaysToAiSummary_skipsDaysWithNoSummary() {
        val days = listOf(
            DayEntry(date = "2026-09-01"),
            DayEntry(date = "2026-09-02")
        )
        assertTrue(mapDaysToAiSummary(days).isEmpty())
    }

    @Test fun emptyIncomingSet_meansNoDatesAreClearedByAnyTable() {
        // The whole point of S-1: when a payload carries no health/hydration/AI data at all for a
        // month, the caller (`importMonth`) computes its "dates to clear" purely from these
        // functions' output — an entirely health-free push must produce an empty date set, i.e.
        // ExportImportRepository's `deleteDaysByLocalDates` etc. are never even called.
        val days = listOf(
            DayEntry(date = "2026-09-01"),
            DayEntry(date = "2026-09-02"),
            DayEntry(date = "2026-09-03")
        )
        val (healthDays, healthSessions, weightReadings) = mapDaysToHealth(days)
        assertTrue(healthDays.isEmpty() && healthSessions.isEmpty() && weightReadings.isEmpty())
        assertTrue(mapDaysToHydration(days).isEmpty())
        assertTrue(mapDaysToAiSummary(days).isEmpty())
    }
}
