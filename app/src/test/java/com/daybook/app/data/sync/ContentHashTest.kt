package com.daybook.app.data.sync

import com.daybook.app.data.backup.BackupMeta
import com.daybook.app.data.backup.BackupStatus
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.DaybookBackup
import com.daybook.app.data.backup.Definitions
import com.daybook.app.data.backup.HabitDef
import com.daybook.app.data.backup.HabitLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The D-note invariant: the echo-guard hash covers definitions+days ONLY, never `meta`.
 * Get this wrong and the sync loop never settles.
 */
class ContentHashTest {

    private fun backup(exportedAt: String, habitName: String = "Stretch") = DaybookBackup(
        meta = BackupMeta(exportedAt = exportedAt, appVersionName = "0.5"),
        definitions = Definitions(
            habits = listOf(
                HabitDef(
                    id = "h1", name = habitName, iconKey = "run", colorTag = "MINT",
                    times = listOf("07:00"), activeDays = listOf(1, 2, 3),
                    snoozeMinutes = 10, createdAt = "2026-08-01T00:00:00Z", archived = false
                )
            )
        ),
        days = listOf(
            DayEntry(
                date = "2026-08-28",
                habitLogs = listOf(HabitLog("h1", "07:00", BackupStatus.DONE, "2026-08-28T07:04:00Z"))
            )
        )
    )

    @Test fun differsOnlyByExportedAt_hashesIdentically() {
        val a = ContentHash.of(backup("2026-08-29T10:00:00Z"))
        val b = ContentHash.of(backup("2026-08-30T23:59:59Z"))
        assertEquals(a, b)
    }

    @Test fun differentContent_hashesDiffer() {
        val a = ContentHash.of(backup("2026-08-29T10:00:00Z", habitName = "Stretch"))
        val b = ContentHash.of(backup("2026-08-29T10:00:00Z", habitName = "Walk"))
        assertNotEquals(a, b)
    }

    @Test fun ofJson_matchesOfObject() {
        val obj = backup("2026-08-29T10:00:00Z")
        val json = com.daybook.app.util.JsonUtils().encode(obj)
        assertEquals(ContentHash.of(obj), ContentHash.ofJson(json))
    }

    @Test fun sha256Hex_is64Chars() {
        assertEquals(64, ContentHash.of(backup("x")).length)
    }
}
