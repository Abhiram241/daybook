package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A2 — exercised against the REAL bundled `repdb.json` asset (not a hand-written fixture), the
 * same multi-candidate path resolution `DataTablesSyncTest` already uses for the schema JSONs.
 */
class ExerciseCatalogTest {

    private fun repdbFile(): File {
        val rel = "src/main/assets/exercises/repdb.json"
        val candidates = listOf(
            File(rel),
            File("app/$rel"),
            File(System.getProperty("user.dir"), rel),
            File(System.getProperty("user.dir"), "app/$rel")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate repdb.json (tried: ${candidates.map { it.absolutePath }})")
    }

    private val catalog: List<BuiltinExercise> by lazy { parseBuiltinCatalog(repdbFile().readText()) }

    @Test fun catalogIsNonEmptyAndExcludesStretching() {
        assertTrue(catalog.isNotEmpty())
        // 601 total - 76 stretching = 525 usable (§3.3.4).
        assertEquals(525, catalog.size)
    }

    @Test fun idsAreUniqueAndPrefixed() {
        val ids = catalog.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith(BUILTIN_ID_PREFIX) })
    }

    @Test fun idsAreStable_knownSlugsPresent() {
        val ids = catalog.map { it.id }.toSet()
        // A spot check of ids referenced by HEVY_ALIASES and by the reference screenshots —
        // renaming any of these would silently break historical workout_sets rows (Ri1).
        listOf("bench-press", "pull-up", "cable-crunch", "hip-thrust", "treadmill-running")
            .forEach { slug -> assertTrue("missing $slug", (BUILTIN_ID_PREFIX + slug) in ids) }
    }

    @Test fun hevyNameOverrides_relabelTheRightIdsAndKeepOriginalIdsAndImages() {
        // Hevy CSV reconciliation — "CSV is the main guy": these 3 keep their RepDB id/photo,
        // only the display name changes to match the user's real export wording.
        val byId = catalog.associateBy { it.id }
        val expected = mapOf(
            "squat" to "Squat (Barbell)",
            "db-bench-press" to "Bench Press (Dumbbell)",
            "single-arm-dumbbell-overhead-tricep-extension" to "Single Arm Tricep Extension (Dumbbell)"
        )
        expected.forEach { (slug, newName) ->
            val ex = byId[BUILTIN_ID_PREFIX + slug] ?: error("missing catalog id for $slug")
            assertEquals(newName, ex.name)
            assertEquals(slug, ex.imageId)
            assertTrue(ex.hasStartPeak)
        }
        // No other catalog entry collides with any of the 3 new names.
        expected.values.forEach { newName ->
            assertEquals(1, catalog.count { it.name == newName })
        }
    }

    @Test fun everyAliasValueResolvesToARealCatalogId() {
        val ids = catalog.map { it.id }.toSet()
        HEVY_ALIASES.forEach { (hevyName, slug) ->
            assertTrue("alias '$hevyName' -> '$slug' has no matching catalog id", (BUILTIN_ID_PREFIX + slug) in ids)
        }
    }

    @Test fun labelMapsAreTotal() {
        MuscleGroup.entries.forEach { assertNotNull(MuscleGroupLabels[it]) }
        Equipment.entries.forEach { assertNotNull(EquipmentLabels[it]) }
        assertEquals(MuscleGroup.entries.size, MuscleGroupLabels.size)
        assertEquals(Equipment.entries.size, EquipmentLabels.size)
    }

    @Test fun multipleMuscleGroupsAreReachableFromTheRealCatalog() {
        val reached = catalog.map { it.primaryMuscle }.toSet()
        assertTrue("only reached: $reached", reached.size >= 8)
    }

    // ------------------------------------------------------------------ derivation, per branch (§3.3.2)

    private fun ex(
        category: String = "strength",
        forceType: String? = null,
        equipment: String? = null,
        bodyPart: String? = null,
        primaryMuscles: List<String> = emptyList(),
        isBodyweight: Boolean = false,
        imagesFlat: List<String> = listOf("start", "peak")
    ) = RepDbExercise(
        id = "x", name = "X", category = category, force_type = forceType, equipment = equipment,
        body_part = bodyPart, primary_muscles = primaryMuscles, is_bodyweight = isBodyweight,
        images = RepDbImages(flat = imagesFlat)
    )

    @Test fun deriveMuscleGroup_cardioAlwaysCardio() {
        assertEquals(MuscleGroup.CARDIO, deriveMuscleGroup(ex(category = "cardio", primaryMuscles = listOf("quadriceps"))))
    }

    @Test fun deriveMuscleGroup_fullBodyBodyPart() {
        assertEquals(MuscleGroup.FULL_BODY, deriveMuscleGroup(ex(bodyPart = "full_body", primaryMuscles = listOf("quadriceps"))))
    }

    @Test fun deriveMuscleGroup_mappedPrimary() {
        assertEquals(MuscleGroup.CHEST, deriveMuscleGroup(ex(primaryMuscles = listOf("pectoralis_major"))))
    }

    @Test fun deriveMuscleGroup_unknownFallsToOther() {
        assertEquals(MuscleGroup.OTHER, deriveMuscleGroup(ex(primaryMuscles = listOf("some_future_muscle"))))
    }

    @Test fun deriveMuscleGroup_emptyPrimaryFallsToOther() {
        assertEquals(MuscleGroup.OTHER, deriveMuscleGroup(ex(primaryMuscles = emptyList())))
    }

    @Test fun deriveEquipment_mapped() {
        assertEquals(Equipment.BARBELL, deriveEquipment(ex(equipment = "barbell")))
        assertEquals(Equipment.MACHINE, deriveEquipment(ex(equipment = "smith_machine")))
    }

    @Test fun deriveEquipment_absentIsNone() {
        assertEquals(Equipment.NONE, deriveEquipment(ex(equipment = null)))
    }

    @Test fun deriveEquipment_unmappedPresentValueFallsToNone() {
        // §3.3.2's exact code: `ex.equipment?.let { MAP[it] } ?: Equipment.NONE` only guards a
        // NULL equipment field (bodyweight) — an unmapped-but-present key also lands on NONE, not
        // OTHER. Never triggered by the real free-tier catalog (every used key is in the map);
        // documented here as the literal, intentional behaviour of the plan's own function.
        assertEquals(Equipment.NONE, deriveEquipment(ex(equipment = "some_future_gadget")))
    }

    @Test fun deriveTrackingMode_cardioWithDistanceEquipment() {
        assertEquals("DISTANCE_DURATION", deriveTrackingMode(ex(category = "cardio", equipment = "treadmill")))
    }

    @Test fun deriveTrackingMode_cardioWithoutDistanceEquipment() {
        assertEquals("DURATION", deriveTrackingMode(ex(category = "cardio", equipment = null)))
    }

    @Test fun deriveTrackingMode_staticHoldIsDuration() {
        assertEquals(
            "DURATION",
            deriveTrackingMode(ex(forceType = "static", imagesFlat = listOf("main"), isBodyweight = true))
        )
    }

    @Test fun deriveTrackingMode_staticButStartPeakIsNotDuration() {
        // A static-force exercise with a start/peak pair (not a single "main" hold still) falls
        // through to the bodyweight/weighted branches below, per §3.3.2's exact `&&` condition.
        assertEquals(
            "REPS_ONLY",
            deriveTrackingMode(ex(forceType = "static", imagesFlat = listOf("start", "peak"), isBodyweight = true))
        )
    }

    @Test fun deriveTrackingMode_bodyweightIsRepsOnly() {
        assertEquals("REPS_ONLY", deriveTrackingMode(ex(isBodyweight = true)))
    }

    @Test fun deriveTrackingMode_defaultIsWeightReps() {
        assertEquals("WEIGHT_REPS", deriveTrackingMode(ex(isBodyweight = false)))
    }
}
