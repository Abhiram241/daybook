package com.daybook.app.data.workout

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §3.9.5 — [partitionDuplicates]: the dedupe key is exact `(startedAt, endedAt)` epoch millis,
 * against every existing session regardless of source. No fuzzy time window.
 */
class HevyDedupeTest {

    private fun session(start: Long, end: Long?) = ParsedSession(
        title = "T", startedAt = start, endedAt = end,
        exercises = listOf(ParsedExerciseBlock("Squat", sets = listOf(ParsedSet(1, reps = 5, weightKg = 60f))))
    )

    @Test fun reimportingTheIdenticalFile_yieldsZeroNew() {
        val sessions = listOf(session(1000L, 2000L), session(3000L, 4000L))
        val existing = sessions.map { it.startedAt to it.endedAt }.toSet()
        val (toImport, duplicates) = partitionDuplicates(sessions, existing)
        assertEquals(0, toImport.size)
        assertEquals(2, duplicates)
    }

    @Test fun oneNewSessionAmongThreeOld_yieldsOne() {
        val sessions = listOf(session(1000L, 2000L), session(3000L, 4000L), session(5000L, 6000L), session(7000L, 8000L))
        val existing = setOf(1000L to 2000L, 3000L to 4000L, 5000L to 6000L)
        val (toImport, duplicates) = partitionDuplicates(sessions, existing)
        assertEquals(listOf(session(7000L, 8000L)), toImport)
        assertEquals(3, duplicates)
    }

    @Test fun handLoggedSessionAtSameTimestamps_blocksItsHevyTwin() {
        // The existing set is built the same way regardless of the existing row's `source` —
        // the dedupe check itself never looks at source.
        val incoming = session(1000L, 2000L)
        val existingFromManualEntry = setOf(1000L to 2000L)
        val (toImport, duplicates) = partitionDuplicates(listOf(incoming), existingFromManualEntry)
        assertEquals(0, toImport.size)
        assertEquals(1, duplicates)
    }

    @Test fun differentEndTime_isNotADuplicate() {
        val incoming = session(1000L, 2000L)
        val existing = setOf(1000L to 2500L) // same start, different end
        val (toImport, duplicates) = partitionDuplicates(listOf(incoming), existing)
        assertEquals(1, toImport.size)
        assertEquals(0, duplicates)
    }

    @Test fun nullEndedAt_matchesOnlyNullEndedAt() {
        val incoming = session(1000L, null)
        val existingWithEnd = setOf(1000L to 2000L)
        assertEquals(1, partitionDuplicates(listOf(incoming), existingWithEnd).first.size)
        val existingWithoutEnd = setOf(1000L to null)
        assertEquals(0, partitionDuplicates(listOf(incoming), existingWithoutEnd).first.size)
    }
}
