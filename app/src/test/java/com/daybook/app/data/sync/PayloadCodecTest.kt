package com.daybook.app.data.sync

import com.daybook.app.data.backup.BackupMeta
import com.daybook.app.data.backup.BackupStatus
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.DaybookBackup
import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.HabitDef
import com.daybook.app.data.backup.HabitLog
import com.daybook.app.data.backup.IntakeLog
import com.daybook.app.data.backup.IntakeReminderDef
import com.daybook.app.util.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** gzip round-trip + the D1 sizing claim (≥5× on a realistic multi-day export). */
class PayloadCodecTest {

    private fun realisticExport(days: Int = 90): String {
        val defs = Definitions(
            habits = (1..4).map { i ->
                HabitDef(
                    id = "h$i", name = "Habit $i", iconKey = "run", colorTag = "MINT",
                    times = listOf("07:00", "12:30", "21:00"), activeDays = listOf(1, 2, 3, 4, 5, 6, 7),
                    snoozeMinutes = 10, createdAt = "2026-01-01T00:00:00Z", archived = false
                )
            },
            intakeReminders = (1..3).map { i ->
                IntakeReminderDef(
                    id = "t$i", name = "Intake $i", type = "MED", iconKey = "medication", colorTag = "PEACH",
                    times = listOf("09:00", "22:00"), activeDays = emptyList(),
                    snoozeMinutes = 15, createdAt = "2026-01-01T00:00:00Z", archived = false
                )
            }
        )
        val start = LocalDate.of(2026, 1, 1)
        val dayEntries = (0 until days).map { d ->
            val date = start.plusDays(d.toLong()).toString()
            DayEntry(
                date = date,
                habitLogs = listOf(
                    HabitLog("h1", "07:00", BackupStatus.DONE, "${date}T07:04:00Z"),
                    HabitLog("h2", "12:30", BackupStatus.SKIPPED, "${date}T12:31:00Z"),
                    HabitLog("h3", "21:00", BackupStatus.MISSED, null)
                ),
                intakeLogs = listOf(
                    IntakeLog("t1", "09:00", BackupStatus.LOGGED, "500mg paracetamol", "${date}T09:10:00Z"),
                    IntakeLog("t2", "22:00", BackupStatus.MISSED, null, null)
                )
            )
        }
        return JsonUtils().encode(
            DaybookBackup(BackupMeta(exportedAt = "2026-04-01T00:00:00Z", appVersionName = "0.5"), defs, dayEntries)
        )
    }

    @Test fun roundTrip_identity() {
        val json = realisticExport()
        assertEquals(json, PayloadCodec.gunzipToString(PayloadCodec.gzipString(json)))
    }

    @Test fun compressionRatio_aboveFiveX() {
        val json = realisticExport()
        val raw = json.toByteArray(Charsets.UTF_8).size
        val gz = PayloadCodec.gzipString(json).size
        val ratio = raw.toDouble() / gz
        assertTrue("expected >5x, got ${"%.2f".format(ratio)}x ($raw -> $gz)", ratio > 5.0)
    }

    @Test fun emptyString_roundTrips() {
        assertEquals("", PayloadCodec.gunzipToString(PayloadCodec.gzipString("")))
    }
}
