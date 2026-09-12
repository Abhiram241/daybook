package com.daybook.app.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A5 (§3.6.6) — the two load-bearing invariants, made explicit instead of incidental. */
class WorkoutRoutesTest {

    @Test fun navFirstIsHome() {
        assertEquals(WorkoutRoutes.HOME, WorkoutRoutes.NAV.first())
    }

    @Test fun everyNavRouteIsInAll() {
        assertTrue(WorkoutRoutes.NAV.all { it in WorkoutRoutes.ALL })
    }

    @Test fun allHasNoDuplicates() {
        assertEquals(WorkoutRoutes.ALL.size, WorkoutRoutes.ALL.toList().distinct().size)
    }

    @Test fun helperBuildersProduceParsableRoutes() {
        assertEquals("workout_session/abc", WorkoutRoutes.session("abc"))
        assertEquals("workout_detail/abc", WorkoutRoutes.detail("abc"))
        assertEquals("routine_edit?routineId=r1", WorkoutRoutes.routineEdit("r1"))
        assertEquals("routine_edit", WorkoutRoutes.routineEdit(null))
        assertEquals("edit_exercise/ex1", WorkoutRoutes.editExercise("ex1"))
    }
}
