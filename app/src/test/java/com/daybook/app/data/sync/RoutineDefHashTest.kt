package com.daybook.app.data.sync

import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.RoutineDef
import com.daybook.app.data.backup.RoutineExerciseDef
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4 (§4.2) — `Definitions.routines` must not churn `definitionsHash` for a user with no
 * routines: present-but-empty serialises IDENTICALLY to the field being absent. Its own test
 * (separate from [WorkoutDefHashTest]) so a failure names which field broke, per §4.2's
 * instruction.
 */
@OptIn(ExperimentalSerializationApi::class)
class RoutineDefHashTest {

    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun routine(name: String = "Push day") = RoutineDef(
        id = "r1", name = name, orderIndex = 0,
        createdAt = "2026-08-01T00:00:00Z", updatedAt = "2026-08-01T00:00:00Z",
        exercises = listOf(
            RoutineExerciseDef(id = "re1", exerciseId = "builtin:bench-press", orderIndex = 0, targetSets = 3)
        )
    )

    @Test fun differentRoutineList_hashesDiffer() {
        assertNotEquals(
            ContentHash.ofDefinitions(Definitions(routines = emptyList())),
            ContentHash.ofDefinitions(Definitions(routines = listOf(routine())))
        )
    }

    @Test fun emptyRoutines_isAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(Definitions.serializer(), Definitions())
        assertFalse("routines must NOT appear: $canonical", canonical.contains("\"routines\""))

        val withOne = canonicalJson.encodeToString(Definitions.serializer(), Definitions(routines = listOf(routine())))
        assertTrue(withOne.contains("\"routines\""))
    }

    @Test fun emptyRoutines_hashesSameAsFieldAbsent() {
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absent = lenient.decodeFromString(Definitions.serializer(), """{"habits":[]}""")
        assertTrue(absent.routines.isEmpty())
        assertEquals(
            ContentHash.ofDefinitions(absent),
            ContentHash.ofDefinitions(Definitions(routines = emptyList()))
        )
    }

    /** Ri3/R18 guarded at the wire level too: a null target must serialise as absent, never `0`. */
    @Test fun nullTargets_areAbsentInCanonicalBytes() {
        val noTargets = RoutineExerciseDef(id = "re2", exerciseId = "builtin:plank", orderIndex = 1)
        val canonical = canonicalJson.encodeToString(
            Definitions.serializer(), Definitions(routines = listOf(routine().copy(exercises = listOf(noTargets))))
        )
        assertFalse(canonical.contains("targetSets"))
        assertFalse(canonical.contains("targetReps"))
        assertFalse(canonical.contains("targetWeightKg"))
    }
}
