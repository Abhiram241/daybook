package com.daybook.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.4 Phase 3 (§3.3) — [canDelete] pins the D6 ≥1-question rule that a journal-question list
 * editor's delete control consults. Relocated to `JournalQuestionListEdits.kt` (Journal-as-habit
 * round) when the global `JournalQuestionRepository` was retired; behaviour unchanged.
 */
class JournalQuestionDeleteGuardTest {

    @Test fun cannotDeleteWhenOneOrNoneRemain() {
        assertFalse(canDelete(0))
        assertFalse(canDelete(1))
    }

    @Test fun canDeleteWhenMoreThanOneRemain() {
        assertTrue(canDelete(2))
        assertTrue(canDelete(7))
    }
}
