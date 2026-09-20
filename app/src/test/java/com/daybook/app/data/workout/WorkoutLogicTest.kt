package com.daybook.app.data.workout

import com.daybook.app.data.model.WorkoutRoutine
import com.daybook.app.data.model.WorkoutRoutineExercise
import com.daybook.app.data.model.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLogicTest {

    private fun set(
        id: String = "s1", workoutExerciseId: String = "we1", sessionId: String = "sess1",
        exerciseId: String = "ex1", setNumber: Int = 1, reps: Int? = null, weightKg: Float? = null,
        durationSeconds: Int? = null, distanceMeters: Float? = null, completedAt: Long? = 1L
    ) = WorkoutSet(
        id = id, workoutExerciseId = workoutExerciseId, sessionId = sessionId, exerciseId = exerciseId,
        setNumber = setNumber, reps = reps, weightKg = weightKg, durationSeconds = durationSeconds,
        distanceMeters = distanceMeters, completedAt = completedAt
    )

    // ------------------------------------------------------------------ columnsFor

    @Test fun columnsFor_everyMode() {
        assertEquals(listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.WEIGHT, SetColumn.REPS, SetColumn.COMPLETE), columnsFor("WEIGHT_REPS"))
        assertEquals(listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.REPS, SetColumn.COMPLETE), columnsFor("REPS_ONLY"))
        assertEquals(listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.DURATION, SetColumn.COMPLETE), columnsFor("DURATION"))
        assertEquals(listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.DISTANCE, SetColumn.DURATION, SetColumn.COMPLETE), columnsFor("DISTANCE_DURATION"))
    }

    // ------------------------------------------------------------------ hasTrackedValue (item 10)

    @Test fun hasTrackedValue_weightReps_falseWhenBothNull() {
        assertFalse(hasTrackedValue("WEIGHT_REPS", set(reps = null, weightKg = null)))
    }

    @Test fun hasTrackedValue_weightReps_trueWhenEitherSet() {
        assertTrue(hasTrackedValue("WEIGHT_REPS", set(reps = 10, weightKg = null)))
        assertTrue(hasTrackedValue("WEIGHT_REPS", set(reps = null, weightKg = 40f)))
        assertTrue(hasTrackedValue("WEIGHT_REPS", set(reps = 10, weightKg = 40f)))
    }

    @Test fun hasTrackedValue_repsOnly_ignoresWeightColumn() {
        // REPS_ONLY's columnsFor doesn't include WEIGHT — a stray weight value (there shouldn't be
        // one, but defensively) must not by itself satisfy the gate.
        assertFalse(hasTrackedValue("REPS_ONLY", set(reps = null, weightKg = 40f)))
        assertTrue(hasTrackedValue("REPS_ONLY", set(reps = 8, weightKg = null)))
    }

    @Test fun hasTrackedValue_duration_onlyDurationCounts() {
        assertFalse(hasTrackedValue("DURATION", set(durationSeconds = null)))
        assertTrue(hasTrackedValue("DURATION", set(durationSeconds = 45)))
    }

    @Test fun hasTrackedValue_distanceDuration_eitherCounts() {
        assertFalse(hasTrackedValue("DISTANCE_DURATION", set(durationSeconds = null, distanceMeters = null)))
        assertTrue(hasTrackedValue("DISTANCE_DURATION", set(durationSeconds = 600, distanceMeters = null)))
        assertTrue(hasTrackedValue("DISTANCE_DURATION", set(durationSeconds = null, distanceMeters = 5000f)))
    }

    // ------------------------------------------------------------------ previousBySetNumber

    @Test fun previousBySetNumber_keepsOnlyNewestSession() {
        val rows = listOf(
            set(id = "a", sessionId = "newest", setNumber = 1),
            set(id = "b", sessionId = "newest", setNumber = 2),
            set(id = "c", sessionId = "older", setNumber = 1)
        )
        val result = previousBySetNumber(rows)
        assertEquals(2, result.size)
        assertEquals("a", result[1]?.id)
        assertEquals("b", result[2]?.id)
    }

    @Test fun previousBySetNumber_emptyInputIsEmptyMap() {
        assertTrue(previousBySetNumber(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------------ isPersonalRecord

    @Test fun isPersonalRecord_noHistoryIsNeverAPr() {
        assertFalse(isPersonalRecord(set(weightKg = 100f, reps = 5), null, "WEIGHT_REPS"))
    }

    @Test fun isPersonalRecord_weightRepsHeavierWins() {
        val best = set(weightKg = 80f, reps = 5)
        assertTrue(isPersonalRecord(set(weightKg = 85f, reps = 5), best, "WEIGHT_REPS"))
        assertFalse(isPersonalRecord(set(weightKg = 75f, reps = 5), best, "WEIGHT_REPS"))
    }

    @Test fun isPersonalRecord_weightRepsEqualWeightMoreRepsWins() {
        val best = set(weightKg = 80f, reps = 5)
        assertTrue(isPersonalRecord(set(weightKg = 80f, reps = 6), best, "WEIGHT_REPS"))
        assertFalse(isPersonalRecord(set(weightKg = 80f, reps = 5), best, "WEIGHT_REPS"))
    }

    @Test fun isPersonalRecord_repsOnly() {
        val best = set(reps = 10)
        assertTrue(isPersonalRecord(set(reps = 11), best, "REPS_ONLY"))
        assertFalse(isPersonalRecord(set(reps = 9), best, "REPS_ONLY"))
    }

    @Test fun isPersonalRecord_duration() {
        val best = set(durationSeconds = 60)
        assertTrue(isPersonalRecord(set(durationSeconds = 61), best, "DURATION"))
        assertFalse(isPersonalRecord(set(durationSeconds = 60), best, "DURATION"))
    }

    @Test fun isPersonalRecord_distanceDuration() {
        val best = set(distanceMeters = 1000f)
        assertTrue(isPersonalRecord(set(distanceMeters = 1500f), best, "DISTANCE_DURATION"))
        assertFalse(isPersonalRecord(set(distanceMeters = 999f), best, "DISTANCE_DURATION"))
    }

    // ------------------------------------------------------------------ sessionStats

    @Test fun sessionStats_volumeOverCompletedOnly_warmupsCounted() {
        val sets = listOf(
            set(id = "1", weightKg = 100f, reps = 5, completedAt = 1L),
            set(id = "2", weightKg = 50f, reps = 10, completedAt = 1L),
            set(id = "3", weightKg = 999f, reps = 999, completedAt = null) // not completed - excluded
        )
        val stats = sessionStats(sets)
        assertEquals(1000f, stats.totalVolumeKg, 0.001f) // 100*5 + 50*10
        assertEquals(2, stats.setCount)
    }

    @Test fun sessionStats_bodyweightSetContributesZeroVolume() {
        val sets = listOf(set(id = "1", weightKg = null, reps = 20, completedAt = 1L))
        val stats = sessionStats(sets)
        assertEquals(0f, stats.totalVolumeKg, 0.001f)
        assertEquals(1, stats.setCount)
    }

    @Test fun sessionStats_emptyIsZero() {
        val stats = sessionStats(emptyList())
        assertEquals(0f, stats.totalVolumeKg, 0.001f)
        assertEquals(0, stats.setCount)
    }

    // ------------------------------------------------------------------ matchesGroupFilter (feature addition)

    @Test fun matchesGroupFilter_allChipMatchesEverything() {
        assertTrue(matchesGroupFilter(MuscleGroup.CHEST, null))
        assertTrue(matchesGroupFilter(MuscleGroup.CARDIO, null))
    }

    @Test fun matchesGroupFilter_specificChipMatchesOnlyItself() {
        assertTrue(matchesGroupFilter(MuscleGroup.CHEST, MuscleGroup.CHEST))
        assertFalse(matchesGroupFilter(MuscleGroup.CHEST, MuscleGroup.CARDIO))
    }

    // ------------------------------------------------------------------ matchesExerciseSearch (Round 2)

    @Test fun matchesExerciseSearch_blankQueryMatchesEverything() {
        assertTrue(matchesExerciseSearch("", "Air Bike"))
        assertTrue(matchesExerciseSearch("   ", "Air Bike"))
    }

    @Test fun matchesExerciseSearch_plainSubstringStillMatches() {
        assertTrue(matchesExerciseSearch("air", "Air Bike"))
        assertTrue(matchesExerciseSearch("air bike", "Air Bike"))
    }

    @Test fun matchesExerciseSearch_wordOrderIndependent() {
        assertTrue(matchesExerciseSearch("bike air", "Air Bike"))
    }

    @Test fun matchesExerciseSearch_tokensMustAllBePresent() {
        assertFalse(matchesExerciseSearch("bike rowing", "Air Bike"))
    }

    @Test fun matchesExerciseSearch_spaceInsensitiveFallback() {
        assertTrue(matchesExerciseSearch("situps", "Sit Ups"))
        assertTrue(matchesExerciseSearch("pullups", "Pull Ups"))
        assertTrue(matchesExerciseSearch("sit ups", "SitUps"))
    }

    @Test fun matchesExerciseSearch_noMatchWhenNeitherHeuristicFires() {
        assertFalse(matchesExerciseSearch("squat", "Air Bike"))
    }

    // ------------------------------------------------------------------ normaliseExerciseName

    @Test fun normaliseExerciseName_trimsAndRejectsBlank() {
        assertEquals("Bench Press", normaliseExerciseName("  Bench Press  "))
        assertNull(normaliseExerciseName("   "))
        assertNull(normaliseExerciseName(""))
    }

    // ------------------------------------------------------------------ uniqueRoutineName

    @Test fun uniqueRoutineName_noClashReturnsTrimmed() {
        assertEquals("Push day", uniqueRoutineName("  Push   day  ", emptyList()))
    }

    @Test fun uniqueRoutineName_clashAppendsSuffix() {
        assertEquals("Push day 2", uniqueRoutineName("Push day", listOf("Push day")))
        assertEquals("Push day 3", uniqueRoutineName("Push day", listOf("Push day", "Push day 2")))
    }

    @Test fun uniqueRoutineName_caseInsensitiveClash() {
        assertEquals("push day 2", uniqueRoutineName("push day", listOf("PUSH DAY")))
    }

    // ------------------------------------------------------------------ targetSummary

    @Test fun targetSummary_weightReps_allSet() {
        assertEquals(
            "3× 10 · 60 kg",
            targetSummary(3, 10, 60f, null, null, "WEIGHT_REPS", WeightUnit.KG)
        )
    }

    @Test fun targetSummary_weightReps_weightAbsent() {
        assertEquals("3× 10", targetSummary(3, 10, null, null, null, "WEIGHT_REPS", WeightUnit.KG))
    }

    @Test fun targetSummary_repsAbsent_setsOnly() {
        assertEquals("3 sets", targetSummary(3, null, null, null, null, "WEIGHT_REPS", WeightUnit.KG))
    }

    @Test fun targetSummary_duration() {
        assertEquals("3× 45s", targetSummary(3, null, null, 45, null, "DURATION", WeightUnit.KG))
    }

    @Test fun targetSummary_distanceDuration() {
        assertEquals("3× 5 km", targetSummary(3, null, null, null, 5000f, "DISTANCE_DURATION", WeightUnit.KG))
    }

    @Test fun targetSummary_everythingNull_noTargets() {
        assertEquals("No targets", targetSummary(null, null, null, null, null, "WEIGHT_REPS", WeightUnit.KG))
    }

    @Test fun targetSummary_lbUnit() {
        val s = targetSummary(3, 10, 100f, null, null, "WEIGHT_REPS", WeightUnit.LB)
        assertTrue(s.endsWith("lb"))
    }

    // ------------------------------------------------------------------ resolveRestSeconds

    @Test fun resolveRestSeconds_precedence() {
        assertEquals(90, resolveRestSeconds(90, 60, 30))   // routine target wins
        assertEquals(60, resolveRestSeconds(null, 60, 30)) // then last block value
        assertEquals(30, resolveRestSeconds(null, null, 30)) // then app default
        assertNull(resolveRestSeconds(null, null, 0))         // app default 0 == OFF -> null
    }

    // ------------------------------------------------------------------ buildSessionFromRoutine (StartFromRoutineTest)

    private fun routine(id: String = "r1") = WorkoutRoutine(
        id = id, name = "Push day", orderIndex = 0, createdAt = 1L, updatedAt = 1L
    )

    private fun routineExercise(
        id: String, exerciseId: String, orderIndex: Int, targetSets: Int? = 3,
        targetReps: Int? = 10, targetWeightKg: Float? = 60f, restSeconds: Int? = null
    ) = WorkoutRoutineExercise(
        id = id, routineId = "r1", exerciseId = exerciseId, orderIndex = orderIndex,
        targetSets = targetSets, targetReps = targetReps, targetWeightKg = targetWeightKg,
        restSeconds = restSeconds, createdAt = 1L
    )

    @Test fun startFromRoutine_threeExercisesWithTargets_producesRightBlocksAndSets() {
        val exercises = listOf(
            routineExercise("re1", "builtin:bench-press", 0),
            routineExercise("re2", "builtin:squat", 1),
            routineExercise("re3", "builtin:pull-up", 2)
        )
        val result = buildSessionFromRoutine(
            routine = routine(), routineExercises = exercises, lastRestSecondsByExercise = emptyMap(),
            appDefaultRestSeconds = 0, now = 1000L, today = "2026-09-11", sessionId = "sess1",
            blockIdAt = { i -> "block$i" }, setIdAt = { b, s -> "set-$b-$s" }
        )
        assertEquals("ACTIVE", result.session.status)
        assertEquals("MANUAL", result.session.source)
        assertEquals("r1", result.session.routineId)
        assertEquals(3, result.exercises.size)
        assertEquals(9, result.sets.size) // 3 blocks x 3 sets each
        result.sets.forEach { assertNull(it.completedAt) }
        assertTrue(result.sets.all { it.setType == "NORMAL" })
    }

    @Test fun startFromRoutine_targetSetsNullProducesZeroSetBlock() {
        val exercises = listOf(routineExercise("re1", "builtin:plank", 0, targetSets = null))
        val result = buildSessionFromRoutine(
            routine = routine(), routineExercises = exercises, lastRestSecondsByExercise = emptyMap(),
            appDefaultRestSeconds = 0, now = 1000L, today = "2026-09-11", sessionId = "sess1",
            blockIdAt = { i -> "block$i" }, setIdAt = { b, s -> "set-$b-$s" }
        )
        assertEquals(1, result.exercises.size)
        assertTrue(result.sets.isEmpty())
    }

    @Test fun startFromRoutine_restPrecedenceOverLastBlockValue() {
        val exercises = listOf(routineExercise("re1", "builtin:bench-press", 0, restSeconds = 90))
        val result = buildSessionFromRoutine(
            routine = routine(), routineExercises = exercises,
            lastRestSecondsByExercise = mapOf("builtin:bench-press" to 60),
            appDefaultRestSeconds = 30, now = 1000L, today = "2026-09-11", sessionId = "sess1",
            blockIdAt = { i -> "block$i" }, setIdAt = { b, s -> "set-$b-$s" }
        )
        assertEquals(90, result.exercises.first().restSeconds) // routine target wins
    }

    @Test fun startFromRoutine_freshSessionStatsAreZero() {
        val exercises = listOf(routineExercise("re1", "builtin:bench-press", 0))
        val result = buildSessionFromRoutine(
            routine = routine(), routineExercises = exercises, lastRestSecondsByExercise = emptyMap(),
            appDefaultRestSeconds = 0, now = 1000L, today = "2026-09-11", sessionId = "sess1",
            blockIdAt = { i -> "block$i" }, setIdAt = { b, s -> "set-$b-$s" }
        )
        val stats = sessionStats(result.sets)
        assertEquals(0f, stats.totalVolumeKg, 0.001f)
        assertEquals(0, stats.setCount) // none completed yet
    }

    // ------------------------------------------------------------ currentStreakDays (BEAST_MODE_REDESIGN_PLAN.md §3.3 item 3)

    @Test fun streak_trainedTodayAndYesterday_countsBoth() {
        val today = LocalDate.of(2026, 9, 12)
        val dates = setOf("2026-09-12", "2026-09-11")
        assertEquals(2, currentStreakDays(dates, today))
    }

    @Test fun streak_notTrainedTodayYet_startsFromYesterday() {
        val today = LocalDate.of(2026, 9, 12)
        val dates = setOf("2026-09-11", "2026-09-10")
        assertEquals(2, currentStreakDays(dates, today))
    }

    @Test fun streak_gapBreaksIt() {
        val today = LocalDate.of(2026, 9, 12)
        val dates = setOf("2026-09-12", "2026-09-10") // missing the 11th
        assertEquals(1, currentStreakDays(dates, today))
    }

    @Test fun streak_nothingLogged_isZero() {
        assertEquals(0, currentStreakDays(emptySet(), LocalDate.of(2026, 9, 12)))
    }

    // -------------------------------------------------------- pickThumbnailExerciseId (item 11)

    /** Exercises whose id starts with "art" are the ones with a real RepDB photo. */
    private val hasArt: (String) -> Boolean = { it.startsWith("art") }

    @Test fun thumbnail_prefersTheFirstExercise_whenItHasArt() {
        assertEquals("art1", pickThumbnailExerciseId("sess-1", listOf("art1", "art2", "custom"), hasArt))
    }

    @Test fun thumbnail_fallsBackToAnExerciseWithArt() {
        val picked = pickThumbnailExerciseId("sess-1", listOf("custom", "art1", "art2"), hasArt)
        assertTrue(picked == "art1" || picked == "art2")
    }

    /** The whole point of the item: the same session must resolve to the same picture every time,
     *  including from a freshly rebuilt list — never a `Random()` evaluated at render time. */
    @Test fun thumbnail_isStablePerSession() {
        val first = pickThumbnailExerciseId("sess-1", listOf("custom", "art1", "art2", "art3"), hasArt)
        repeat(20) {
            assertEquals(first, pickThumbnailExerciseId("sess-1", listOf("custom", "art1", "art2", "art3"), hasArt))
        }
        val rebuilt = listOf("custom", "art1", "art2", "art3").map { String(it.toCharArray()) }
        assertEquals(first, pickThumbnailExerciseId(String("sess-1".toCharArray()), rebuilt, hasArt))
    }

    @Test fun thumbnail_differentSessionsCanDiffer() {
        val pool = listOf("custom", "art1", "art2", "art3", "art4", "art5")
        val picks = (1..40).map { pickThumbnailExerciseId("sess-$it", pool, hasArt) }.toSet()
        assertTrue("seed must vary with the session id", picks.size > 1)
    }

    @Test fun thumbnail_nothingHasArt_keepsTheFirst() {
        assertEquals("custom1", pickThumbnailExerciseId("sess-1", listOf("custom1", "custom2"), hasArt))
    }

    @Test fun thumbnail_emptySession_isNull() {
        assertNull(pickThumbnailExerciseId("sess-1", emptyList(), hasArt))
    }

    // ------------------------------------------------------------------ dominantMuscleGroup / bucketByMuscleGroup (item 13)

    @Test fun dominantMuscleGroup_picksTheHeaviestEntry() {
        val breakdown = listOf(MuscleGroup.CHEST to 1800f, MuscleGroup.TRICEPS to 1100f)
        assertEquals(MuscleGroup.CHEST, dominantMuscleGroup(breakdown))
    }

    @Test fun dominantMuscleGroup_emptyBreakdown_isNull() {
        assertNull(dominantMuscleGroup(emptyList()))
    }

    @Test fun bucketByMuscleGroup_ordersByCountDescending() {
        // 3 chest sessions, 2 legs sessions, 1 back session.
        val rows = listOf("c1", "c2", "c3", "l1", "l2", "b1")
        val groupOf: (String) -> MuscleGroup? = {
            when {
                it.startsWith("c") -> MuscleGroup.CHEST
                it.startsWith("l") -> MuscleGroup.QUADRICEPS
                else -> MuscleGroup.UPPER_BACK
            }
        }
        val sections = bucketByMuscleGroup(rows, groupOf)
        assertEquals(3, sections.size)
        assertTrue("expected Chest first, got ${sections[0].first}", sections[0].first.startsWith("Chest"))
        assertEquals(3, sections[0].second.size)
        assertEquals(2, sections[1].second.size)
        assertEquals(1, sections[2].second.size)
    }

    @Test fun bucketByMuscleGroup_nullGroup_goesToOtherLast() {
        val rows = listOf("a", "b", "none1", "none2", "none3")
        val groupOf: (String) -> MuscleGroup? = { if (it.startsWith("none")) null else MuscleGroup.CHEST }
        val sections = bucketByMuscleGroup(rows, groupOf)
        assertEquals(2, sections.size)
        // "Other" has more rows (3) than "Chest" (2) but must still sort last.
        assertTrue(sections[0].first.startsWith("Chest"))
        assertEquals("Other · 3 workouts", sections[1].first)
    }

    @Test fun bucketByMuscleGroup_noOtherRows_omitsTheSection() {
        val sections = bucketByMuscleGroup(listOf("a")) { MuscleGroup.CHEST }
        assertEquals(1, sections.size)
        assertFalse(sections.any { it.first.startsWith("Other") })
    }

    @Test fun bucketByMuscleGroup_preservesArrivalOrderWithinASection() {
        val sections = bucketByMuscleGroup(listOf("first", "second", "third")) { MuscleGroup.CHEST }
        assertEquals(listOf("first", "second", "third"), sections.single().second)
    }

    // Owner report (2026-09-20) — reopening a COMPLETED session for editing must show its fixed
    // recorded duration, not wall-clock time since the original `startedAt`.
    @Test fun sessionElapsedSeconds_active_ticksAgainstNow() {
        val startedAt = 1_000_000L
        val now = startedAt + 90_000L
        assertEquals(90L, sessionElapsedSeconds(startedAt, null, now))
    }

    @Test fun sessionElapsedSeconds_completed_ignoresNowAndUsesEndedAt() {
        val startedAt = 1_000_000L
        val endedAt = startedAt + 45_000L
        val farFutureNow = endedAt + 7 * 24 * 60 * 60 * 1000L // a week later, e.g. edited long after
        assertEquals(45L, sessionElapsedSeconds(startedAt, endedAt, farFutureNow))
    }

    @Test fun sessionElapsedSeconds_neverNegative() {
        val startedAt = 1_000_000L
        assertEquals(0L, sessionElapsedSeconds(startedAt, null, startedAt - 5_000L))
        assertEquals(0L, sessionElapsedSeconds(startedAt, startedAt - 5_000L, startedAt))
    }
}
