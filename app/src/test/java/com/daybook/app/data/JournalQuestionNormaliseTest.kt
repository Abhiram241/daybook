package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.5.4 Phase 2 (D1) — pins [normaliseQuestionText]: trim only, case-preserving, case-sensitive
 * (same rule as [normalisePrompt]).
 */
class JournalQuestionNormaliseTest {

    @Test fun trimsSurroundingWhitespace() {
        assertEquals("How did you sleep?", normaliseQuestionText("  How did you sleep?  "))
    }

    @Test fun blankIsNull() {
        assertNull(normaliseQuestionText(""))
        assertNull(normaliseQuestionText("   "))
        assertNull(normaliseQuestionText("\n\t "))
    }

    @Test fun caseIsSignificant() {
        assertEquals("What's on your mind?", normaliseQuestionText("What's on your mind?"))
        assert(normaliseQuestionText("mood") != normaliseQuestionText("Mood"))
    }

    @Test fun innerWhitespaceIsUntouched() {
        assertEquals("a  b\tc", normaliseQuestionText("  a  b\tc "))
    }
}
