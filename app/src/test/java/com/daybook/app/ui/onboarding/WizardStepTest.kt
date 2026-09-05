package com.daybook.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 3 (D2) — [buildWizardSteps] (NameAsk omitted for an
 * auto-derived name) and [isLastWizardStep] (the next()-vs-complete boundary).
 */
class WizardStepTest {

    @Test fun `buildWizardSteps includes NameAsk when no name was auto-derived`() {
        val steps = buildWizardSteps(hasAutoDerivedName = false)
        assertTrue(steps.first() is WizardStep.NameAsk)
        assertEquals(1 + OnboardingTourSteps.size, steps.size)
    }

    @Test fun `buildWizardSteps excludes NameAsk when a name was auto-derived`() {
        val steps = buildWizardSteps(hasAutoDerivedName = true)
        assertTrue(steps.none { it is WizardStep.NameAsk })
        assertEquals(OnboardingTourSteps.size, steps.size)
        assertEquals(OnboardingTourSteps, steps)
    }

    @Test fun `isLastWizardStep is false before the final index`() {
        assertFalse(isLastWizardStep(currentStep = 0, stepCount = 5))
        assertFalse(isLastWizardStep(currentStep = 3, stepCount = 5))
    }

    @Test fun `isLastWizardStep is true on the final index`() {
        assertTrue(isLastWizardStep(currentStep = 4, stepCount = 5))
    }

    @Test fun `isLastWizardStep is true for a single-step wizard`() {
        assertTrue(isLastWizardStep(currentStep = 0, stepCount = 1))
    }
}
