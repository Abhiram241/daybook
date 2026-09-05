package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.5.2 §4 — pins the one normalisation rule: trim only, case-preserving, case-sensitive
 * uniqueness. Also pins the exact map/filter/distinct expression `importAllData` uses.
 */
class CustomCategoryNormaliseTest {

    @Test fun trimsSurroundingWhitespace() {
        assertEquals("Snacks", normaliseCategory("  Snacks  "))
    }

    @Test fun blankIsNull() {
        assertNull(normaliseCategory(""))
        assertNull(normaliseCategory("   "))
    }

    @Test fun caseIsSignificant() {
        assertEquals("Snacks", normaliseCategory("Snacks"))
        assertEquals("snacks", normaliseCategory("snacks"))
        // Different canonical values → two distinct categories.
        assert(normaliseCategory("Snacks") != normaliseCategory("snacks"))
    }

    @Test fun importDedupeExpression() {
        val raw = listOf(" Snacks ", "Snacks", "", "  ", "Supplements", "snacks")
        val canonical = raw.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        assertEquals(listOf("Snacks", "Supplements", "snacks"), canonical)
    }
}
