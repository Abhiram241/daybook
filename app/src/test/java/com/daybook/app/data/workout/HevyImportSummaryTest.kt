package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Test

/** §3.9.9 — exact-string summary composition (S1/S1a/S2/S2a/S3/S4). */
class HevyImportSummaryTest {

    @Test fun s1_sessionsNothingSkipped() {
        assertEquals(
            "Imported 4 workouts and 33 sets.",
            summarise(HevyImportResult(sessions = 4, sets = 33, newExercises = 0, skippedDuplicates = 0, skippedRows = 0))
        )
    }

    @Test fun s1_singularWorkoutAndSet() {
        assertEquals(
            "Imported 1 workout and 1 set.",
            summarise(HevyImportResult(sessions = 1, sets = 1, newExercises = 0, skippedDuplicates = 0, skippedRows = 0))
        )
    }

    @Test fun s1a_newExercisesPresent() {
        assertEquals(
            "Imported 4 workouts, 33 sets and 9 new exercises.",
            summarise(HevyImportResult(sessions = 4, sets = 33, newExercises = 9, skippedDuplicates = 0, skippedRows = 0))
        )
    }

    @Test fun s1a_singularNewExercise() {
        assertEquals(
            "Imported 4 workouts, 33 sets and 1 new exercise.",
            summarise(HevyImportResult(sessions = 4, sets = 33, newExercises = 1, skippedDuplicates = 0, skippedRows = 0))
        )
    }

    @Test fun s2_duplicatesSkipped_appended() {
        assertEquals(
            "Imported 4 workouts and 33 sets. Skipped 2 already in Daybook.",
            summarise(HevyImportResult(sessions = 4, sets = 33, newExercises = 0, skippedDuplicates = 2, skippedRows = 0))
        )
    }

    @Test fun s2a_rowsSkipped_appended() {
        assertEquals(
            "Imported 4 workouts and 33 sets. Ignored 3 rows it couldn't read.",
            summarise(HevyImportResult(sessions = 4, sets = 33, newExercises = 0, skippedDuplicates = 0, skippedRows = 3))
        )
    }

    @Test fun s2andS2a_bothAppended_duplicatesThenRows() {
        assertEquals(
            "Imported 4 workouts and 33 sets. Skipped 2 already in Daybook. Ignored 3 rows it couldn't read.",
            summarise(HevyImportResult(sessions = 4, sets = 33, newExercises = 0, skippedDuplicates = 2, skippedRows = 3))
        )
    }

    @Test fun s3_allDuplicates_neitherSuccessNorFailureWording() {
        assertEquals(
            "Nothing new to import — all 4 workouts in that file are already in Daybook.",
            summarise(HevyImportResult(sessions = 0, sets = 0, newExercises = 0, skippedDuplicates = 4, skippedRows = 0))
        )
    }

    @Test fun s3_singularWorkout() {
        assertEquals(
            "Nothing new to import — all 1 workout in that file is already in Daybook.",
            summarise(HevyImportResult(sessions = 0, sets = 0, newExercises = 0, skippedDuplicates = 1, skippedRows = 0))
        )
    }

    // ------------------------------------------------------------------ new-exercise warning

    @Test fun newExerciseNames_appendsWarningListingThem() {
        assertEquals(
            "Imported 4 workouts, 33 sets and 2 new exercises. ⚠ These weren't in your exercise " +
                "library, so they were added as custom: Cycling, Hiking.",
            summarise(
                HevyImportResult(
                    sessions = 4, sets = 33, newExercises = 2, skippedDuplicates = 0, skippedRows = 0,
                    newExerciseNames = listOf("Cycling", "Hiking")
                )
            )
        )
    }

    @Test fun newExerciseNames_singular() {
        assertEquals(
            "Imported 4 workouts, 33 sets and 1 new exercise. ⚠ This wasn't in your exercise " +
                "library, so it was added as custom: Cycling.",
            summarise(
                HevyImportResult(
                    sessions = 4, sets = 33, newExercises = 1, skippedDuplicates = 0, skippedRows = 0,
                    newExerciseNames = listOf("Cycling")
                )
            )
        )
    }

    @Test fun newExerciseNames_truncatedAtFiveWithCountOfTheRest() {
        val names = listOf("A", "B", "C", "D", "E", "F", "G")
        assertEquals(
            "Imported 4 workouts, 33 sets and 7 new exercises. ⚠ These weren't in your exercise " +
                "library, so they were added as custom: A, B, C, D, E and 2 more.",
            summarise(
                HevyImportResult(
                    sessions = 4, sets = 33, newExercises = 7, skippedDuplicates = 0, skippedRows = 0,
                    newExerciseNames = names
                )
            )
        )
    }

    @Test fun s4_nothingImportedAndNoDuplicates() {
        assertEquals(
            "Couldn't import: no workouts were found in that file.",
            summarise(HevyImportResult(sessions = 0, sets = 0, newExercises = 0, skippedDuplicates = 0, skippedRows = 0))
        )
    }
}
