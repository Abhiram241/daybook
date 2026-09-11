package com.daybook.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX overhaul item 1 — [buildWizardSteps] (NameAsk omitted for an auto-derived name / review
 * mode), the teaching-flow step shape, and [isLastWizardStep] (the next()-vs-complete boundary).
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

    @Test fun `the tour is five Teach steps then PermissionPrimer then Ready`() {
        assertEquals(5, OnboardingTeachSteps.size)
        assertEquals(OnboardingTeachSteps.size + 2, OnboardingTourSteps.size)
        assertTrue(OnboardingTourSteps.take(5).all { it is WizardStep.Teach })
        assertSame(WizardStep.PermissionPrimer, OnboardingTourSteps[5])
        assertSame(WizardStep.Ready, OnboardingTourSteps[6])
    }

    @Test fun `every Teach step has a distinct illustration and non-blank copy`() {
        val illustrations = OnboardingTeachSteps.map { it.illustration }
        assertEquals(illustrations.toSet().size, illustrations.size)
        assertEquals(TeachIllustration.entries.toSet(), illustrations.toSet())
        OnboardingTeachSteps.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.body.length > 20)
        }
    }

    /** Review mode ("Replay the tour") uses exactly the tour list — no NameAsk. */
    @Test fun `review mode step list is the tour with no NameAsk`() {
        assertTrue(OnboardingTourSteps.none { it is WizardStep.NameAsk })
    }

    @Test fun `isLastWizardStep is false before the final index`() {
        assertFalse(isLastWizardStep(currentStep = 0, stepCount = 7))
        assertFalse(isLastWizardStep(currentStep = 5, stepCount = 7))
    }

    @Test fun `isLastWizardStep is true on the final index`() {
        assertTrue(isLastWizardStep(currentStep = 6, stepCount = 7))
    }

    @Test fun `isLastWizardStep is true for a single-step wizard`() {
        assertTrue(isLastWizardStep(currentStep = 0, stepCount = 1))
    }
}
