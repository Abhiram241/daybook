package com.daybook.app.data.sync

import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.HabitDef
import com.daybook.app.data.backup.HabitLog
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journal-as-habit round — the wire additions `HabitDef.journalQuestions` and `HabitLog.qaJson`
 * must not churn the sync hash for a user with no Journal-type habits:
 *  (a) a `journalQuestions` edit DOES change `ofDefinitions` (parent re-push — correct);
 *  (b) `journalQuestions = emptyList()` (the default) serialises IDENTICALLY to the field being
 *      absent — an existing user's `definitionsHash` is byte-identical before/after this build
 *      (mirrors StreakDefHashTest (b));
 *  (c) a `qaJson` edit DOES change `ofDays` (one month re-push — correct);
 *  (d) `qaJson = null` (the default) serialises IDENTICALLY to the field being absent (mirrors
 *      JournalV2HashTest (d) / IntakeLog.qaJson's un-annotated-nullable-default precedent).
 */
@OptIn(ExperimentalSerializationApi::class)
class HabitJournalHashTest {

    // Same config as ContentHash's private `json`.
    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun habit(questions: List<String> = emptyList()) = HabitDef(
        id = "h1", name = "Evening reflection", iconKey = "task", colorTag = "MINT",
        times = listOf("21:00"), activeDays = emptyList(), snoozeMinutes = 10,
        createdAt = "2026-01-01T00:00:00Z", archived = false, type = "JOURNAL",
        journalQuestions = questions
    )

    private fun defs(h: HabitDef) = Definitions(habits = listOf(h))

    private fun day(qaJson: String?) = DayEntry(
        date = "2026-02-10",
        habitLogs = listOf(
            HabitLog(habitId = "h1", scheduledTime = "21:00", status = "logged", resolvedAt = "2026-02-10T21:05:00Z", qaJson = qaJson)
        )
    )

    // ---------------------------------------------------------------- (a)

    @Test fun a_definitionsDifferOnlyByJournalQuestions_hashesDiffer() {
        assertNotEquals(
            ContentHash.ofDefinitions(defs(habit(questions = emptyList()))),
            ContentHash.ofDefinitions(defs(habit(questions = listOf("What went well today?"))))
        )
    }

    // ---------------------------------------------------------------- (b)

    @Test fun b_defaultJournalQuestions_areAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(HabitDef.serializer(), habit())
        assertFalse("journalQuestions must NOT appear: $canonical", canonical.contains("journalQuestions"))

        val nonDefault = canonicalJson.encodeToString(HabitDef.serializer(), habit(questions = listOf("How do you feel?")))
        assertTrue(nonDefault.contains("journalQuestions"))
    }

    @Test fun b_defaultJournalQuestions_hashSameAsFieldAbsent() {
        // A pre-this-round parent doc / backup literally has no "journalQuestions" key.
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absentJson = """{"habits":[{"id":"h1","name":"No smoking","iconKey":"task","colorTag":"MINT","times":[],"activeDays":[],"snoozeMinutes":10,"createdAt":"2026-01-01T00:00:00Z","archived":false,"type":"INDIVIDUAL"}]}"""
        val absent = lenient.decodeFromString(Definitions.serializer(), absentJson)
        assertEquals(emptyList<String>(), absent.habits.first().journalQuestions)

        val withDefaults = Definitions(
            habits = listOf(
                HabitDef(
                    id = "h1", name = "No smoking", iconKey = "task", colorTag = "MINT",
                    times = emptyList(), activeDays = emptyList(), snoozeMinutes = 10,
                    createdAt = "2026-01-01T00:00:00Z", archived = false, type = "INDIVIDUAL"
                )
            )
        )
        assertEquals(ContentHash.ofDefinitions(absent), ContentHash.ofDefinitions(withDefaults))
    }

    // ---------------------------------------------------------------- (c)

    @Test fun c_dayDiffersOnlyByHabitQaJson_hashesDiffer() {
        assertNotEquals(
            ContentHash.ofDays(listOf(day(qaJson = null))),
            ContentHash.ofDays(listOf(day(qaJson = """[{"q":"How do you feel?","a":"Good"}]""")))
        )
    }

    // ---------------------------------------------------------------- (d)

    @Test fun d_nullHabitQaJson_isAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(HabitLog.serializer(), day(qaJson = null).habitLogs.first())
        assertFalse("a null qaJson must NOT appear in the canonical JSON: $canonical", canonical.contains("qaJson"))
    }

    @Test fun d_nullHabitQaJson_hashSameAsFieldAbsent() {
        // A pre-this-round month doc's HabitLog has no "qaJson" key at all.
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absentJson = """{"date":"2026-02-10","habitLogs":[{"habitId":"h1","scheduledTime":"21:00","status":"logged","resolvedAt":"2026-02-10T21:05:00Z"}]}"""
        val absent = lenient.decodeFromString(DayEntry.serializer(), absentJson)
        assertEquals(null, absent.habitLogs.first().qaJson)
        assertEquals(ContentHash.ofDays(listOf(absent)), ContentHash.ofDays(listOf(day(qaJson = null))))
    }
}
