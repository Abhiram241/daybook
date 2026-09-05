package com.daybook.app.data.sync

import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.HabitDef
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.5 — the "Ongoing" (STREAK) wire additions to [HabitDef] must not churn the sync hash for a
 * user with no Ongoing habits:
 *  (a) a `streakLongest` / `streakStartedAt` edit DOES change `ofDefinitions` (parent re-push — correct);
 *  (b) `streakLongest = 0` AND `streakStartedAt = null` (the defaults) serialise IDENTICALLY to the
 *      fields being absent — so an existing user's `definitionsHash` is byte-identical before/after
 *      this build (mirrors JournalV2HashTest (b)).
 */
@OptIn(ExperimentalSerializationApi::class)
class StreakDefHashTest {

    // Same config as ContentHash's private `json`.
    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun habit(startedAt: Long? = null, longest: Int = 0) = HabitDef(
        id = "h1", name = "No smoking", iconKey = "task", colorTag = "MINT",
        times = emptyList(), activeDays = emptyList(), snoozeMinutes = 10,
        createdAt = "2026-01-01T00:00:00Z", archived = false, type = "STREAK",
        streakStartedAt = startedAt, streakLongest = longest
    )

    private fun defs(h: HabitDef) = Definitions(habits = listOf(h))

    // ---------------------------------------------------------------- (a)

    @Test fun a_definitionsDifferOnlyByStreakLongest_hashesDiffer() {
        assertNotEquals(
            ContentHash.ofDefinitions(defs(habit(longest = 0))),
            ContentHash.ofDefinitions(defs(habit(longest = 5)))
        )
    }

    @Test fun a_definitionsDifferOnlyByStreakStartedAt_hashesDiffer() {
        assertNotEquals(
            ContentHash.ofDefinitions(defs(habit(startedAt = null))),
            ContentHash.ofDefinitions(defs(habit(startedAt = 1_767_225_600_000L)))
        )
    }

    // ---------------------------------------------------------------- (b)

    @Test fun b_defaultStreakFields_areAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(HabitDef.serializer(), habit())
        assertFalse("streakLongest must NOT appear: $canonical", canonical.contains("streakLongest"))
        assertFalse("streakStartedAt must NOT appear: $canonical", canonical.contains("streakStartedAt"))

        val nonDefault = canonicalJson.encodeToString(HabitDef.serializer(), habit(startedAt = 1L, longest = 3))
        assertTrue(nonDefault.contains("streakLongest"))
        assertTrue(nonDefault.contains("streakStartedAt"))
    }

    @Test fun b_defaultStreakFields_hashSameAsFieldsAbsent() {
        // A pre-v0.5.5 parent doc / backup literally has no "streakStartedAt" / "streakLongest" key.
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absentJson = """{"habits":[{"id":"h1","name":"No smoking","iconKey":"task","colorTag":"MINT","times":[],"activeDays":[],"snoozeMinutes":10,"createdAt":"2026-01-01T00:00:00Z","archived":false,"type":"INDIVIDUAL"}]}"""
        val absent = lenient.decodeFromString(Definitions.serializer(), absentJson)
        assertEquals(null, absent.habits.first().streakStartedAt)
        assertEquals(0, absent.habits.first().streakLongest)

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
}
