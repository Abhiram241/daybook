package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.5.3 item 8 — pins [normalisePrompt]: trim only, case-preserving, case-sensitive uniqueness
 * (same rule as [normaliseCategory]).
 */
class CustomPromptNormaliseTest {

    @Test fun trimsSurroundingWhitespace() {
        assertEquals("hi", normalisePrompt("  hi "))
    }

    @Test fun blankIsNull() {
        assertNull(normalisePrompt(""))
        assertNull(normalisePrompt("   "))
    }

    @Test fun caseIsSignificant() {
        assertEquals("What meds?", normalisePrompt("What meds?"))
        assert(normalisePrompt("a") != normalisePrompt("A"))
    }
}
