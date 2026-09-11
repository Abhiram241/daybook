package com.daybook.app.ui.respond

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ROUND 0 (Z3) — drives [RespondViewModel.applyUndoResult], the state-transition half of
 * [RespondViewModel.undo]'s outcome. `undo()` itself makes a real suspend call into
 * OccurrenceScheduler (a final class backed by Room) via `safeLaunch`, which this project's plain
 * JUnit4 unit tests (no Robolectric, no mocking library — see build.gradle.kts's "Testing — JUnit
 * 4 only") cannot execute; `applyUndoResult` is the exact same `if (ok) … else …` undo() runs,
 * pulled out so the C9 fix (undo() no longer swallowing a rejected/thrown revert via the old
 * `resolve {}` helper) is verifiable.
 */
class RespondViewModelUndoTest {

    @Test fun thrownRevert_leavesDoneFalseAndSetsRejectedMessage() {
        // Mirrors undo()'s own `runCatching { action() }.isSuccess` around a revert call that
        // throws (e.g. the occurrence row vanished, or a Room write failed).
        val ok = runCatching<Unit> { throw RuntimeException("boom") }.isSuccess
        assertFalse(ok)

        val result = RespondViewModel.applyUndoResult(
            RespondViewModel.UiState(busy = true),
            ok
        )

        assertFalse(result.busy)
        assertFalse(result.done)
        assertEquals("Couldn't reset this entry. Try again.", result.rejectedMessage)
    }

    @Test fun successfulRevert_setsDoneTrueAndNoRejectedMessage() {
        val ok = runCatching { Unit }.isSuccess
        assertTrue(ok)

        // undo()'s first `_state.update` (before the safeLaunch) already clears rejectedMessage to
        // null, so that is the real precondition applyUndoResult ever sees on this path.
        val result = RespondViewModel.applyUndoResult(
            RespondViewModel.UiState(busy = true, rejectedMessage = null),
            ok
        )

        assertFalse(result.busy)
        assertTrue(result.done)
        assertNull(result.rejectedMessage)
    }
}
