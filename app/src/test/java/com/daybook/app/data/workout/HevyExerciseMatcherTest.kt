package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §3.9.4 — parametrised over all 62 distinct `exercise_title` values from the user's real
 * 4,734-row export. Every one must resolve to SOME real catalog id (existing, aliased, or a new
 * custom-exercise instruction) — never an exception, never a silently dropped row.
 */
class HevyExerciseMatcherTest {

    private fun repdbFile(): File {
        val rel = "src/main/assets/exercises/repdb.json"
        val candidates = listOf(
            File(rel), File("app/$rel"),
            File(System.getProperty("user.dir"), rel),
            File(System.getProperty("user.dir"), "app/$rel")
        )
        return candidates.firstOrNull { it.isFile } ?: error("Could not locate repdb.json")
    }

    private val catalog by lazy { parseBuiltinCatalog(repdbFile().readText()) }
    private val candidates by lazy { catalog.map { MatchCandidate(it.id, it.name, it.equipment.name) } }
    private val catalogIds by lazy { catalog.map { it.id }.toSet() }

    private val allNamesFromFullExport by lazy {
        val rows = CsvReader.parse(HevyFixtures.fullText())
        rows.mapNotNull { it["exercise_title"]?.takeIf { t -> t.isNotBlank() } }.distinct().sorted()
    }

    @Test fun exactly62DistinctNamesInTheRealExport() {
        assertEquals(62, allNamesFromFullExport.size)
    }

    @Test fun everyNameResolvesToSomethingReal_neverThrows() {
        allNamesFromFullExport.forEach { name ->
            val result = HevyExerciseMatcher.resolve(name, candidates)
            when (result) {
                is HevyMatchResult.Existing ->
                    assertTrue("'$name' resolved to unknown id ${result.exerciseId}", result.exerciseId in catalogIds)
                is HevyMatchResult.CreateCustom ->
                    assertEquals(name, result.hevyNameVerbatim)
            }
        }
    }

    @Test fun fiftyFourNamesResolveToExistingCatalogRows_eightFallThroughToCustom() {
        val (existing, custom) = allNamesFromFullExport.partition {
            HevyExerciseMatcher.resolve(it, candidates) is HevyMatchResult.Existing
        }
        assertEquals("existing matches: $existing", 54, existing.size)
        assertEquals("custom fallbacks: $custom", 8, custom.size)
    }

    @Test fun theEightDesignedFallbacks_areExactlyTheseNames() {
        val expectedFallbacks = setOf(
            "Deadlift (Smith Machine)", "Hiking", "Iso-Lateral Row (Machine)",
            "Reverse Wrist Curl ( Cable )", "Seated Row (Machine)",
            "Seated Wrist Extension (Barbell)", "Shrug (Cable)", "Single Arm Cable Row"
        )
        val actualFallbacks = allNamesFromFullExport.filter {
            HevyExerciseMatcher.resolve(it, candidates) is HevyMatchResult.CreateCustom
        }.toSet()
        assertEquals(expectedFallbacks, actualFallbacks)
    }

    @Test fun straySpacesInsideParens_stillHitTheAliasTable() {
        // The real export spells this "Tricep Supported Bicep Curls ( Dumbbell )" (extra spaces
        // just inside the parens) — §3.9.4's alias table must still match it (canonicalised).
        val result = HevyExerciseMatcher.resolve("Tricep Supported Bicep Curls ( Dumbbell )", candidates)
        assertTrue(result is HevyMatchResult.Existing)
    }

    @Test fun normalise_stripsParentheticalAsEquipmentHint() {
        val (name, hint) = HevyExerciseMatcher.normalise("Squat (Barbell)")
        assertEquals("squat", name)
        assertEquals("BARBELL", hint)
    }

    @Test fun normalise_dropsLeadingThe() {
        val (name, _) = HevyExerciseMatcher.normalise("The Plank")
        assertEquals("plank", name)
    }

    @Test fun inferTrackingMode_perColumnPattern() {
        assertEquals("WEIGHT_REPS", HevyExerciseMatcher.inferTrackingMode(listOf(ParsedSet(1, reps = 10, weightKg = 20f))))
        assertEquals("REPS_ONLY", HevyExerciseMatcher.inferTrackingMode(listOf(ParsedSet(1, reps = 10))))
        assertEquals("DURATION", HevyExerciseMatcher.inferTrackingMode(listOf(ParsedSet(1, durationSeconds = 30))))
        assertEquals("DISTANCE_DURATION", HevyExerciseMatcher.inferTrackingMode(listOf(ParsedSet(1, distanceMeters = 500f, durationSeconds = 60))))
    }

    // ------------------------------------------------------------------ Hevy CSV reconciliation

    @Test fun renamedBuiltins_resolveByExactMatch_notAlias() {
        // Now that HEVY_NAME_OVERRIDES relabels these 3 catalog entries, the CSV's own wording
        // exact-matches the catalog directly — no HEVY_ALIASES entry needed for them.
        val expected = mapOf(
            "Squat (Barbell)" to BUILTIN_ID_PREFIX + "squat",
            "Bench Press (Dumbbell)" to BUILTIN_ID_PREFIX + "db-bench-press",
            "Single Arm Tricep Extension (Dumbbell)" to BUILTIN_ID_PREFIX + "single-arm-dumbbell-overhead-tricep-extension"
        )
        expected.forEach { (hevyName, expectedId) ->
            assertFalse("'$hevyName' should not need an alias entry", hevyName in HEVY_ALIASES)
            val result = HevyExerciseMatcher.resolve(hevyName, candidates)
            assertEquals(HevyMatchResult.Existing(expectedId), result)
        }
    }

    @Test fun knownCustomNames_returnCreateCustomWithExpectedHints() {
        val expected = mapOf(
            "Cycling" to (MuscleGroup.CARDIO to null),
            "Hiking" to (MuscleGroup.CARDIO to null),
            "Deadlift (Smith Machine)" to (MuscleGroup.LOWER_BACK to "SMITH_MACHINE"),
            "Iso-Lateral Row (Machine)" to (MuscleGroup.LATS to "MACHINE"),
            "Reverse Wrist Curl (Cable)" to (MuscleGroup.FOREARMS to "CABLE"),
            "Seated Row (Machine)" to (MuscleGroup.LATS to "MACHINE"),
            "Seated Wrist Extension (Barbell)" to (MuscleGroup.FOREARMS to "BARBELL"),
            "Shrug (Cable)" to (MuscleGroup.TRAPS to "CABLE"),
            "Single Arm Cable Row" to (MuscleGroup.LATS to null)
        )
        expected.forEach { (hevyName, hints) ->
            val (expectedMuscle, expectedEquipmentHint) = hints
            val result = HevyExerciseMatcher.resolve(hevyName, candidates)
            assertTrue("'$hevyName' should fall through to CreateCustom", result is HevyMatchResult.CreateCustom)
            result as HevyMatchResult.CreateCustom
            assertEquals(expectedMuscle, result.muscleHint)
            assertEquals(expectedEquipmentHint, result.equipmentHint)
        }
    }

    @Test fun strayParenSpaces_stillHitKnownCustomsTable() {
        // Mirrors `straySpacesInsideParens_stillHitTheAliasTable` — HEVY_KNOWN_CUSTOMS is keyed
        // the same post-canonicalisation way HEVY_ALIASES is.
        val result = HevyExerciseMatcher.resolve("Reverse Wrist Curl ( Cable )", candidates)
        assertTrue(result is HevyMatchResult.CreateCustom)
        assertEquals(MuscleGroup.FOREARMS, (result as HevyMatchResult.CreateCustom).muscleHint)
    }

    @Test fun exactNormalisedMatch_prefersEquipmentHintOnTie() {
        val dumbbellSquat = MatchCandidate("builtin:db-squat", "Squat", "DUMBBELL")
        val barbellSquat = MatchCandidate("builtin:barbell-squat", "Squat", "BARBELL")
        val result = HevyExerciseMatcher.resolve("Squat (Dumbbell)", listOf(barbellSquat, dumbbellSquat))
        assertEquals(HevyMatchResult.Existing("builtin:db-squat"), result)
    }
}
