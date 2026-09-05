package com.daybook.app.data.sync

import com.daybook.app.data.backup.BackupStatus
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.IntakeLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Journal Mode edit-in-place — the hash-stability contract behind [FoodMedOccurrenceDao.editJournalResponse]
 * / [FoodMedOccurrenceDao.editFoodResponse]. Those UPDATEs write ONLY the text (+ description /
 * red-flag fields) and deliberately leave `responded_at` alone, so:
 *  - a no-op re-save produces a byte-identical month hash ⇒ `doPush` writes nothing;
 *  - a real text change changes exactly that one month's hash ⇒ it syncs;
 *  - a bumped `resolvedAt` (the anti-pattern the DAO avoids) would ALSO change the hash — which is
 *    why the edit path must not touch it.
 */
class EditInPlaceHashTest {

    private fun days(
        answer: String = "Oatmeal",
        resolvedAt: String = "2026-08-28T08:05:00Z"
    ): List<DayEntry> = listOf(
        DayEntry(
            date = "2026-08-28",
            intakeLogs = listOf(
                IntakeLog(
                    reminderId = "t1",
                    scheduledTime = "08:00",
                    status = BackupStatus.LOGGED,
                    answer = answer,
                    resolvedAt = resolvedAt
                )
            )
        )
    )

    @Test fun sameTextSameResolvedAt_hashesIdentically() {
        assertEquals(ContentHash.ofDays(days()), ContentHash.ofDays(days()))
    }

    @Test fun changedAnswer_changesMonthHash() {
        assertNotEquals(
            ContentHash.ofDays(days(answer = "Oatmeal")),
            ContentHash.ofDays(days(answer = "Toast"))
        )
    }

    @Test fun bumpedResolvedAt_changesMonthHash() {
        assertNotEquals(
            ContentHash.ofDays(days(resolvedAt = "2026-08-28T08:05:00Z")),
            ContentHash.ofDays(days(resolvedAt = "2026-09-01T12:00:00Z"))
        )
    }
}
