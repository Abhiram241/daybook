package com.daybook.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.5 Phase 7 — [deriveOnboardingName] (which name, if any, to persist silently on first
 * sign-in) and [shouldSkipCompleteOnboarding] (the fire-once guard).
 */
class DeriveOnboardingNameTest {

    // ---- deriveOnboardingName ----

    @Test fun displayNamePresent_returnedTrimmed() {
        assertEquals("Alex", deriveOnboardingName("  Alex  ", restoredUserName = null))
    }

    @Test fun displayNameBlankOrNull_returnsNull() {
        assertNull(deriveOnboardingName(null, null))
        assertNull(deriveOnboardingName("", null))
        assertNull(deriveOnboardingName("   ", null))
    }

    @Test fun restoredUserNameWins_whenBothPresent() {
        // Future-proofing: sub-decision (c) passes null today, but the precedence must hold.
        assertEquals("Custom", deriveOnboardingName("Google Name", restoredUserName = " Custom "))
    }

    @Test fun fallsBackToDisplayName_whenRestoredBlank() {
        assertEquals("Google Name", deriveOnboardingName("Google Name", restoredUserName = "  "))
    }

    @Test fun bothBlank_returnsNull() {
        assertNull(deriveOnboardingName("  ", "  "))
    }

    // ---- shouldSkipCompleteOnboarding ----

    @Test fun skip_whenLoading() {
        assertTrue(shouldSkipCompleteOnboarding(isLoading = true, completed = false))
    }

    @Test fun skip_whenAlreadyCompleted() {
        assertTrue(shouldSkipCompleteOnboarding(isLoading = false, completed = true))
    }

    @Test fun proceed_whenIdleAndNotCompleted() {
        assertFalse(shouldSkipCompleteOnboarding(isLoading = false, completed = false))
        assertFalse(shouldSkipCompleteOnboarding(isLoading = false, completed = null))
    }
}
