package com.daybook.app.data.sync

import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.ExerciseDef
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4 (§4.2) — `Definitions.customExercises` must not churn `definitionsHash` for a user with no
 * custom exercises: present-but-empty serialises IDENTICALLY to the field being absent. Mirrors
 * `StreakDefHashTest` exactly.
 */
@OptIn(ExperimentalSerializationApi::class)
class WorkoutDefHashTest {

    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun exercise(name: String = "Cable Curl") = ExerciseDef(
        id = "ex1", name = name, primaryMuscle = "BICEPS", equipment = "CABLE",
        trackingMode = "WEIGHT_REPS", createdAt = "2026-08-01T00:00:00Z"
    )

    @Test fun differentExerciseList_hashesDiffer() {
        assertNotEquals(
            ContentHash.ofDefinitions(Definitions(customExercises = emptyList())),
            ContentHash.ofDefinitions(Definitions(customExercises = listOf(exercise())))
        )
    }

    @Test fun emptyCustomExercises_isAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(Definitions.serializer(), Definitions())
        assertFalse("customExercises must NOT appear: $canonical", canonical.contains("customExercises"))

        val withOne = canonicalJson.encodeToString(
            Definitions.serializer(), Definitions(customExercises = listOf(exercise()))
        )
        assertTrue(withOne.contains("customExercises"))
    }

    @Test fun emptyCustomExercises_hashesSameAsFieldAbsent() {
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absent = lenient.decodeFromString(Definitions.serializer(), """{"habits":[]}""")
        assertTrue(absent.customExercises.isEmpty())
        assertEquals(
            ContentHash.ofDefinitions(absent),
            ContentHash.ofDefinitions(Definitions(customExercises = emptyList()))
        )
    }

    @Test fun exerciseNotes_absentWhenNull() {
        val canonical = canonicalJson.encodeToString(
            Definitions.serializer(), Definitions(customExercises = listOf(exercise()))
        )
        assertFalse("notes must NOT appear when null: $canonical", canonical.contains("\"notes\""))
    }
}
