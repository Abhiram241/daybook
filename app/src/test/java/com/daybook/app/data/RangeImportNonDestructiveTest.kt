package com.daybook.app.data

import com.daybook.app.data.backup.BackupMeta
import com.daybook.app.data.backup.DayEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 6 (S5): guards the pure decisions that keep a **range** import non-destructive —
 * re-importing a "Jan–Mar" file must not cost the user Apr–Dec, locally or (via the next push's
 * `changedMonths` diff) in the cloud.
 *
 *  - [isRangeScoped]       — picks `importRange` (merge) over `importAllData` (whole replace);
 *  - [coveredMonths]       — the months the file names;
 *  - [residentAfterImport] — what `onLocalDataReplaced` pins `hydratedMonths` to, so an
 *                            out-of-range month is never seen as "emptied locally";
 *  - [pushDeletesAllowed]  — a diff-driven month delete only proceeds when user-initiated.
 */
class RangeImportNonDestructiveTest {

    private fun day(d: String) = DayEntry(date = d)

    @Test fun `isRangeScoped is true when either range bound is set`() {
        assertFalse(isRangeScoped(BackupMeta(exportedAt = "x", appVersionName = "1")))
        assertTrue(
            isRangeScoped(
                BackupMeta(exportedAt = "x", appVersionName = "1", rangeStart = "2026-03-01", rangeEnd = "2026-03-31")
            )
        )
    }

    @Test fun `coveredMonths collapses days to their month keys`() {
        val covered = coveredMonths(
            listOf(day("2026-03-01"), day("2026-03-31"), day("2026-02-10"), day("bad"))
        )
        assertEquals(setOf("2026-03", "2026-02"), covered)
    }

    /** A March-only file leaves Aug/Sep out of the resident set, so `changedMonths` can't mark them deleted. */
    @Test fun `residentAfterImport of a March file does not make August or September resident`() {
        val covered = coveredMonths(listOf(day("2026-03-04"), day("2026-03-20")))
        val recent = setOf("2026-09", "2026-08")   // pretend "now" is Sept
        val resident = residentAfterImport(covered, recent)
        assertEquals(setOf("2026-03", "2026-09", "2026-08"), resident)
        // The cloud still holds e.g. 2026-05 and 2026-06 — they are simply not resident, so the
        // push-side diff (which only considers resident months) can never emit a delete for them.
        assertFalse("2026-05" in resident)
        assertFalse("2026-06" in resident)
    }

    @Test fun `pushDeletesAllowed - a diff with no deletions is always fine`() {
        val changed = mapOf<String, List<DayEntry>?>(
            "2026-03" to listOf(day("2026-03-01")),
            "2026-04" to listOf(day("2026-04-01"))
        )
        assertTrue(pushDeletesAllowed(changed, userInitiated = false))
        assertTrue(pushDeletesAllowed(changed, userInitiated = true))
    }

    @Test fun `pushDeletesAllowed - a diff containing a null (delete) is blocked unless user-initiated`() {
        val changed = mapOf<String, List<DayEntry>?>(
            "2026-03" to listOf(day("2026-03-01")),
            "2026-08" to null   // month "emptied locally" — could just be evicted or trimmed
        )
        assertFalse(pushDeletesAllowed(changed, userInitiated = false))
        assertTrue(pushDeletesAllowed(changed, userInitiated = true))
    }
}
