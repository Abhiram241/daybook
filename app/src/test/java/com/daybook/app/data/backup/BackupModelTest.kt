package com.daybook.app.data.backup

import com.daybook.app.util.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the v2 backup wire format (L4): the shape a future stats screen and any restore both
 * depend on. Room isn't involved — this is the serializer contract only.
 */
class BackupModelTest {

    private val jsonUtils = JsonUtils()

    private fun sample() = DaybookBackup(
        meta = BackupMeta(exportedAt = "2026-08-29T10:00:00Z", appVersionName = "1.0"),
        definitions = Definitions(
            habits = listOf(
                HabitDef(
                    id = "h1", name = "Stretch", iconKey = "run", colorTag = "MINT",
                    times = listOf("07:00", "21:00"), activeDays = listOf(1, 2, 3, 4, 5),
                    snoozeMinutes = 10, createdAt = "2026-08-01T00:00:00Z", archived = false
                ),
                HabitDef(
                    id = "h2", name = "Meds check", iconKey = "task", colorTag = "AUTO",
                    times = emptyList(), activeDays = emptyList(),
                    snoozeMinutes = 10, createdAt = "2026-08-01T00:00:00Z", archived = false,
                    type = "BATCH"
                ),
                HabitDef(
                    id = "h3", name = "No smoking", iconKey = "task", colorTag = "ROSE",
                    times = emptyList(), activeDays = emptyList(),
                    snoozeMinutes = 10, createdAt = "2026-08-01T00:00:00Z", archived = false,
                    type = "STREAK", streakStartedAt = 1_756_000_000_000L, streakLongest = 12
                )
            ),
            intakeReminders = listOf(
                IntakeReminderDef(
                    id = "t1", name = "Lunch", type = "FOOD", iconKey = "restaurant",
                    colorTag = "PEACH", times = listOf("13:00"), activeDays = emptyList(),
                    snoozeMinutes = 15, createdAt = "2026-08-01T00:00:00Z", archived = true,
                    customCategory = "Snacks", promptMessage = "What did you take?",
                    defaultOutsideFood = true
                )
            ),
            customCategories = listOf("Snacks", "Supplements"),
            customPrompts = listOf("What did you take?", "How much water?")
        ),
        days = listOf(
            DayEntry(
                date = "2026-08-28",
                habitLogs = listOf(
                    HabitLog("h1", "07:00", BackupStatus.DONE, "2026-08-28T07:04:00Z"),
                    HabitLog("h1", "21:00", BackupStatus.MISSED, null)
                ),
                intakeLogs = listOf(
                    IntakeLog(
                        "t1", "13:00", BackupStatus.LOGGED, "rice and dal", "2026-08-28T13:20:00Z", "felt fine",
                        outsideFood = true
                    )
                )
            )
        )
    )

    @Test
    fun roundTrip_preservesEveryField() {
        val decoded = jsonUtils.decode(jsonUtils.encode(sample()))
        assertEquals(sample(), decoded)
    }

    @Test
    fun encoded_carriesNoIdentityFields() {
        val text = jsonUtils.encode(sample())
        assertTrue(text.contains("\"formatVersion\": 2"))
        // meta must never leak the user's name or a device id.
        assertTrue(!text.contains("userName") && !text.contains("deviceId"))
    }

    @Test
    fun decode_toleratesUnknownAndMissingFields() {
        val text = """
            {
              "meta": { "formatVersion": 2, "exportedAt": "x", "appVersionName": "1.0", "extra": 7 },
              "definitions": { "habits": [] },
              "days": []
            }
        """.trimIndent()
        val decoded = jsonUtils.decode(text)
        assertEquals(2, decoded.meta.formatVersion)
        assertTrue(decoded.definitions.intakeReminders.isEmpty())
    }

    /** The single most important new test: the user's existing v2 backups still import. */
    @Test
    fun oldV2File_withoutNewKeys_stillDecodes() {
        val text = """
            {
              "meta": { "formatVersion": 2, "exportedAt": "x", "appVersionName": "0.5.1" },
              "definitions": {
                "habits": [ { "id": "h1", "name": "Stretch", "iconKey": "run", "colorTag": "MINT",
                  "createdAt": "2026-08-01T00:00:00Z" } ],
                "intakeReminders": [ { "id": "t1", "name": "Lunch", "type": "FOOD",
                  "iconKey": "restaurant", "colorTag": "PEACH", "createdAt": "2026-08-01T00:00:00Z" } ]
              },
              "days": [ { "date": "2026-08-28", "intakeLogs": [
                { "reminderId": "t1", "scheduledTime": "13:00", "status": "logged", "answer": "dal" } ] } ]
            }
        """.trimIndent()
        val decoded = jsonUtils.decode(text)
        assertEquals(2, decoded.meta.formatVersion)
        assertEquals("INDIVIDUAL", decoded.definitions.habits.first().type)
        assertNull(decoded.definitions.habits.first().streakStartedAt)
        assertEquals(0, decoded.definitions.habits.first().streakLongest)
        assertNull(decoded.definitions.intakeReminders.first().customCategory)
        assertNull(decoded.definitions.intakeReminders.first().promptMessage)
        assertNull(decoded.definitions.intakeReminders.first().defaultOutsideFood)
        assertNull(decoded.days.first().intakeLogs.first().description)
        assertNull(decoded.days.first().intakeLogs.first().outsideFood)
        assertTrue(decoded.definitions.customCategories.isEmpty())
        assertTrue(decoded.definitions.customPrompts.isEmpty())
    }

    @Test
    fun roundTrip_preservesOngoingStreakFields() {
        val decoded = jsonUtils.decode(jsonUtils.encode(sample()))
        val ongoing = decoded.definitions.habits.first { it.id == "h3" }
        assertEquals("STREAK", ongoing.type)
        assertEquals(1_756_000_000_000L, ongoing.streakStartedAt)
        assertEquals(12, ongoing.streakLongest)
        // A non-Ongoing habit keeps the field defaults.
        val plain = decoded.definitions.habits.first { it.id == "h1" }
        assertNull(plain.streakStartedAt)
        assertEquals(0, plain.streakLongest)
    }

    @Test
    fun roundTrip_preservesPromptFields() {
        val decoded = jsonUtils.decode(jsonUtils.encode(sample()))
        assertEquals("What did you take?", decoded.definitions.intakeReminders.first().promptMessage)
        assertEquals(listOf("What did you take?", "How much water?"), decoded.definitions.customPrompts)
    }

    @Test
    fun newFile_stillDeclaresFormatVersion2() {
        assertTrue(jsonUtils.encode(sample()).contains("\"formatVersion\": 2"))
    }

    // ---- v0.5.3 Phase 6 (D2): meta.rangeStart / meta.rangeEnd ----

    @Test
    fun fullExport_leavesRangeFieldsNull() {
        val decoded = jsonUtils.decode(jsonUtils.encode(sample()))
        assertNull(decoded.meta.rangeStart)
        assertNull(decoded.meta.rangeEnd)
    }

    @Test
    fun rangeScopedFile_roundTripsBothRangeFields() {
        val ranged = sample().let {
            it.copy(meta = it.meta.copy(rangeStart = "2026-03-01", rangeEnd = "2026-03-31"))
        }
        val decoded = jsonUtils.decode(jsonUtils.encode(ranged))
        assertEquals("2026-03-01", decoded.meta.rangeStart)
        assertEquals("2026-03-31", decoded.meta.rangeEnd)
        // Still v2 — a range file is not a new format.
        assertEquals(2, decoded.meta.formatVersion)
    }

    @Test
    fun oldV2File_withoutRangeKeys_decodesWithNullRange() {
        val text = """
            {
              "meta": { "formatVersion": 2, "exportedAt": "x", "appVersionName": "0.5.2" },
              "definitions": {
                "habits": [ { "id": "h1", "name": "Stretch", "iconKey": "run", "colorTag": "MINT",
                  "createdAt": "2026-08-01T00:00:00Z" } ]
              },
              "days": []
            }
        """.trimIndent()
        val decoded = jsonUtils.decode(text)
        assertNull(decoded.meta.rangeStart)
        assertNull(decoded.meta.rangeEnd)
    }

    @Test
    fun isoRoundTrip() {
        val millis = 1_756_468_800_000L
        assertEquals(millis, jsonUtils.fromIso(jsonUtils.toIso(millis)))
        assertNull(jsonUtils.fromIso("not a date"))
        assertNull(jsonUtils.fromIso(null))
    }
}
