package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercised against BOTH real Hevy exports (§3.9/§10), not a hand-written approximation. */
class HevyCsvParserTest {

    @Test fun sampleFile_groupsIntoFourSessions() {
        val rows = CsvReader.parse(HevyFixtures.sampleText())
        val result = HevyCsvParser.parse(rows)
        assertEquals(0, result.skippedRows)
        assertEquals(4, result.sessions.size)
        val totalSets = result.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } }
        assertEquals(rows.size, totalSets)
    }

    @Test fun sampleFile_blankIsNotZero() {
        val result = HevyCsvParser.parse(CsvReader.parse(HevyFixtures.sampleText()))
        val plank = result.sessions.flatMap { it.exercises }.first { it.exerciseTitle == "Plank" }
        val plankSet = plank.sets.first()
        assertNull(plankSet.weightKg)
        assertNull(plankSet.reps)
        assertNotNull(plankSet.durationSeconds)

        val cycling = result.sessions.flatMap { it.exercises }.first { it.exerciseTitle == "Cycling" }
        val cyclingSet = cycling.sets.first()
        assertNull(cyclingSet.reps)
        assertNull(cyclingSet.weightKg)
        assertEquals(2400f, cyclingSet.distanceMeters!!, 0.01f) // 2.4 km -> 2400 m
        assertEquals(300, cyclingSet.durationSeconds)
    }

    @Test fun sampleFile_setIndexIsOneBased() {
        val result = HevyCsvParser.parse(CsvReader.parse(HevyFixtures.sampleText()))
        val cableCrunch = result.sessions.flatMap { it.exercises }.first { it.exerciseTitle == "Cable Crunch" }
        assertEquals(listOf(1, 2), cableCrunch.sets.map { it.setNumber })
    }

    @Test fun fullFile_everyRowAccountedFor() {
        val rows = CsvReader.parse(HevyFixtures.fullText())
        val result = HevyCsvParser.parse(rows)
        assertEquals(0, result.skippedRows)
        val totalSets = result.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } }
        assertEquals(rows.size, totalSets)
    }

    @Test fun fullFile_supersetIdPreservedLosslessly() {
        val result = HevyCsvParser.parse(CsvReader.parse(HevyFixtures.fullText()))
        val withSuperset = result.sessions.flatMap { it.exercises }.count { it.supersetId != null }
        // Verified against the real file: 903 of 4,734 rows carry a superset_id; a block only
        // needs one row to carry it for the parser to preserve it, so this is a lower bound.
        assertTrue("expected some superset blocks, found $withSuperset", withSuperset > 0)
    }

    @Test fun fullFile_titlesAreNotUniqueButSessionsAreStillDistinct() {
        // §3.9.0 — "titles are not unique and are useless as an identity key". Real file: 299
        // distinct title strings, but MORE actual sessions (title reused across different
        // start/end timestamps) — confirms the parser groups by the full (title, start, end)
        // key, not by title alone.
        val result = HevyCsvParser.parse(CsvReader.parse(HevyFixtures.fullText()))
        val distinctTitles = result.sessions.mapNotNull { it.title }.distinct().size
        assertTrue(result.sessions.size > distinctTitles)
    }

    @Test fun setTypeMapping() {
        assertEquals("NORMAL", HevyCsvParser.mapSetType("normal"))
        assertEquals("WARMUP", HevyCsvParser.mapSetType("warmup"))
        assertEquals("DROPSET", HevyCsvParser.mapSetType("dropset"))
        assertEquals("FAILURE", HevyCsvParser.mapSetType("failure"))
        assertEquals("NORMAL", HevyCsvParser.mapSetType("something_unrecognised"))
    }

    @Test fun unparseableDate_skipsWholeSessionAndCountsRows() {
        val rows = listOf(
            mapOf(
                "title" to "Bad", "start_time" to "not a date", "end_time" to "also not a date",
                "exercise_title" to "Squat", "set_index" to "0", "set_type" to "normal",
                "weight_kg" to "10", "reps" to "5", "distance_km" to "", "duration_seconds" to "",
                "description" to "", "superset_id" to "", "exercise_notes" to "", "rpe" to ""
            )
        )
        val result = HevyCsvParser.parse(rows)
        assertEquals(0, result.sessions.size)
        assertEquals(1, result.skippedRows)
    }
}
