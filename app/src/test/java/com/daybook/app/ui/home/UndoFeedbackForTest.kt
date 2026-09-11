package com.daybook.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ROUND 0 (Z3) — drives [undoFeedbackFor], the outcome-to-copy mapping [HomeViewModel.revertItem]
 * uses. revertItem() itself makes a real suspend call into OccurrenceScheduler (a final class
 * backed by Room) via `safeLaunch`, which this project's plain JUnit4 unit tests (no Robolectric,
 * no mocking library) cannot execute; `undoFeedbackFor` is the exact same `if (ok) … else …`
 * revertItem() evaluates, pulled out so the C9 fix (a failed revert surfacing through
 * `undoFeedback` instead of failing silently) is verifiable.
 */
class UndoFeedbackForTest {

    @Test fun failedRevert_emitsFailureString() {
        // Mirrors revertItem()'s own `runCatching { … }.isSuccess` around a revert call that
        // throws (e.g. the occurrence row vanished, or a Room write failed).
        val ok = runCatching<Unit> { throw RuntimeException("boom") }.isSuccess
        assertEquals("Couldn't undo that. Try again.", undoFeedbackFor(ok))
    }

    @Test fun successfulRevert_emitsUndone() {
        val ok = runCatching { Unit }.isSuccess
        assertEquals("Undone", undoFeedbackFor(ok))
    }
}
