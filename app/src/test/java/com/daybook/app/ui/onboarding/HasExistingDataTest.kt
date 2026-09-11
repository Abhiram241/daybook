package com.daybook.app.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D4 / LD9 — the pure predicate behind [OnboardingViewModel.hasExistingData]: the closing
 * onboarding step's "Create your first habit" link must not render for an account that already
 * has habit or intake rows.
 */
class HasExistingDataTest {

    @Test fun `false when both counts are zero`() {
        assertFalse(hasExistingData(0, 0))
    }

    @Test fun `true when only habit count is non-zero`() {
        assertTrue(hasExistingData(1, 0))
    }

    @Test fun `true when only intake count is non-zero`() {
        assertTrue(hasExistingData(0, 3))
    }

    @Test fun `true when both counts are non-zero`() {
        assertTrue(hasExistingData(2, 5))
    }
}
