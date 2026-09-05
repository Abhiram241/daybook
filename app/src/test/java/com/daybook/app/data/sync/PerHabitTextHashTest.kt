package com.daybook.app.data.sync

import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.HabitDef
import com.daybook.app.data.backup.IntakeReminderDef
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Customization round (rec 8) — the per-habit / per-intake text additions to the wire model must
 * NOT churn `definitionsHash` for a user who sets none:
 *  (a) an actual edit to `HabitDef.promptMessage` / `HabitDef.motivation` /
 *      `IntakeReminderDef.motivation` DOES change `ofDefinitions` (a real parent-doc push — correct);
 *  (b) the null defaults serialise IDENTICALLY to the fields being ABSENT — so an existing user's
 *      hash is byte-identical before/after this build (mirrors StreakDefHashTest / JournalV2HashTest).
 */
@OptIn(ExperimentalSerializationApi::class)
class PerHabitTextHashTest {

    // Same config as ContentHash's private `json`.
    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }
    private val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun habit(prompt: String? = null, motivation: String? = null) = HabitDef(
        id = "h1", name = "Stretch", iconKey = "task", colorTag = "MINT",
        times = listOf("07:00"), activeDays = emptyList(), snoozeMinutes = 10,
        createdAt = "2026-01-01T00:00:00Z", archived = false, type = "INDIVIDUAL",
        promptMessage = prompt, motivation = motivation
    )

    private fun intake(motivation: String? = null) = IntakeReminderDef(
        id = "t1", name = "Lunch", type = "FOOD", iconKey = "restaurant", colorTag = "PEACH",
        times = listOf("12:00"), activeDays = emptyList(), snoozeMinutes = 10,
        createdAt = "2026-01-01T00:00:00Z", archived = false, motivation = motivation
    )

    // ---------------------------------------------------------------- (a) real edits churn

    @Test fun a_habitPromptMessageEdit_changesTheHash() {
        assertNotEquals(
            ContentHash.ofDefinitions(Definitions(habits = listOf(habit(prompt = null)))),
            ContentHash.ofDefinitions(Definitions(habits = listOf(habit(prompt = "Roll it out"))))
        )
    }

    @Test fun a_habitMotivationEdit_changesTheHash() {
        assertNotEquals(
            ContentHash.ofDefinitions(Definitions(habits = listOf(habit(motivation = null)))),
            ContentHash.ofDefinitions(Definitions(habits = listOf(habit(motivation = "For your back"))))
        )
    }

    @Test fun a_intakeMotivationEdit_changesTheHash() {
        assertNotEquals(
            ContentHash.ofDefinitions(Definitions(intakeReminders = listOf(intake(motivation = null)))),
            ContentHash.ofDefinitions(Definitions(intakeReminders = listOf(intake(motivation = "Fuel"))))
        )
    }

    // ---------------------------------------------------------------- (b) null == absent

    @Test fun b_nullFields_areAbsentInCanonicalHabitBytes() {
        val canonical = canonicalJson.encodeToString(HabitDef.serializer(), habit())
        assertFalse("promptMessage must NOT appear: $canonical", canonical.contains("promptMessage"))
        assertFalse("motivation must NOT appear: $canonical", canonical.contains("motivation"))

        val set = canonicalJson.encodeToString(HabitDef.serializer(), habit(prompt = "x", motivation = "y"))
        assertTrue(set.contains("promptMessage"))
        assertTrue(set.contains("motivation"))
    }

    @Test fun b_nullFields_areAbsentInCanonicalIntakeBytes() {
        val canonical = canonicalJson.encodeToString(IntakeReminderDef.serializer(), intake())
        // The pre-existing `promptMessage` is Optional-without-@EncodeDefault, so `explicitNulls=false`
        // still drops its null. `motivation` is @EncodeDefault(NEVER), so it is fully absent.
        assertFalse("motivation must NOT appear: $canonical", canonical.contains("\"motivation\""))
    }

    @Test fun b_habitDefaults_hashSameAsAPreRoundParentDoc() {
        // A pre-round parent doc literally has no promptMessage / motivation key on the habit.
        val absentJson = """{"habits":[{"id":"h1","name":"Stretch","iconKey":"task","colorTag":"MINT","times":["07:00"],"activeDays":[],"snoozeMinutes":10,"createdAt":"2026-01-01T00:00:00Z","archived":false,"type":"INDIVIDUAL"}]}"""
        val absent = lenient.decodeFromString(Definitions.serializer(), absentJson)
        assertEquals(null, absent.habits.first().promptMessage)
        assertEquals(null, absent.habits.first().motivation)
        assertEquals(
            ContentHash.ofDefinitions(absent),
            ContentHash.ofDefinitions(Definitions(habits = listOf(habit())))
        )
    }

    @Test fun setText_survivesAJsonRoundTrip_bothSides() {
        val defs = Definitions(
            habits = listOf(habit(prompt = "Roll it out", motivation = "For your back")),
            intakeReminders = listOf(intake(motivation = "Fuel"))
        )
        val encoded = canonicalJson.encodeToString(Definitions.serializer(), defs)
        val back = lenient.decodeFromString(Definitions.serializer(), encoded)
        assertEquals("Roll it out", back.habits.first().promptMessage)
        assertEquals("For your back", back.habits.first().motivation)
        assertEquals("Fuel", back.intakeReminders.first().motivation)
        assertEquals(ContentHash.ofDefinitions(defs), ContentHash.ofDefinitions(back))
    }

    @Test fun b_intakeDefault_hashSameAsAPreRoundParentDoc() {
        val absentJson = """{"intakeReminders":[{"id":"t1","name":"Lunch","type":"FOOD","iconKey":"restaurant","colorTag":"PEACH","times":["12:00"],"activeDays":[],"snoozeMinutes":10,"createdAt":"2026-01-01T00:00:00Z","archived":false}]}"""
        val absent = lenient.decodeFromString(Definitions.serializer(), absentJson)
        assertEquals(null, absent.intakeReminders.first().motivation)
        assertEquals(
            ContentHash.ofDefinitions(absent),
            ContentHash.ofDefinitions(Definitions(intakeReminders = listOf(intake())))
        )
    }
}
