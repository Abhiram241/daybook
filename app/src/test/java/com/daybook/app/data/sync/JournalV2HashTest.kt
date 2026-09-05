package com.daybook.app.data.sync

import com.daybook.app.data.backup.BackupStatus
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.IntakeLog
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * v0.5.4 Phase 2 (§2.7) — the Journal-v2 wire additions must not churn the sync hash for
 * non-journal data:
 *  (c) a `qaJson` edit DOES change `ofDays` (that one month re-push — correct);
 *  (d) `qaJson = null` serialises IDENTICALLY to the field being absent — no month-hash churn.
 *
 * Journal-as-habit round: parts (a)/(b), which pinned `Definitions.journalQuestions` (the GLOBAL
 * question set), are retired along with that field — it is replaced entirely by the per-habit
 * `HabitDef.journalQuestions`, whose own hash-safety assertions live in `HabitJournalHashTest`.
 * `IntakeLog.qaJson` is untouched by this round (FoodMed-side field, pre-existing) so (c)/(d) stay.
 */
@OptIn(ExperimentalSerializationApi::class)
class JournalV2HashTest {

    // Same config as ContentHash's private `json` — used only to inspect the canonical bytes.
    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun day(qaJson: String?) = DayEntry(
        date = "2026-02-10",
        intakeLogs = listOf(
            IntakeLog(
                reminderId = "t1", scheduledTime = "21:00", status = BackupStatus.LOGGED,
                answer = "slept well", resolvedAt = "2026-02-10T21:05:00Z", qaJson = qaJson
            )
        )
    )

    // ---------------------------------------------------------------- (c)

    @Test fun c_daysDifferOnlyByQaJson_hashesDiffer() {
        assertNotEquals(
            ContentHash.ofDays(listOf(day(null))),
            ContentHash.ofDays(listOf(day("[{\"q\":\"How did you sleep?\",\"a\":\"well\"}]")))
        )
        assertNotEquals(
            ContentHash.ofDays(listOf(day("[{\"q\":\"Q\",\"a\":\"A1\"}]"))),
            ContentHash.ofDays(listOf(day("[{\"q\":\"Q\",\"a\":\"A2\"}]")))
        )
    }

    // ---------------------------------------------------------------- (d)

    @Test fun d_nullQaJson_isAbsentInCanonicalBytes() {
        val canonical =
            canonicalJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(DayEntry.serializer()), listOf(day(null)))
        assertFalse(
            "a null qaJson must NOT appear in the canonical JSON: $canonical",
            canonical.contains("qaJson")
        )
    }

    @Test fun d_nullQaJson_hashesSameAsFieldAbsent() {
        // A pre-v0.5.4 month doc's IntakeLog has no "qaJson" key.
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absentJson = """[{"date":"2026-02-10","habitLogs":[],"intakeLogs":[{"reminderId":"t1","scheduledTime":"21:00","status":"logged","answer":"slept well","resolvedAt":"2026-02-10T21:05:00Z"}]}]"""
        val absent = lenient.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(DayEntry.serializer()), absentJson
        )
        assertEquals(ContentHash.ofDays(absent), ContentHash.ofDays(listOf(day(null))))
    }
}
