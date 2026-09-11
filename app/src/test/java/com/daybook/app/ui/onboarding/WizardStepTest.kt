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

    // UX refinement round (LD7) — was five Teach steps, now seven (streaks and Journal each
    // get their own step).
    @Test fun `the tour is seven Teach steps then PermissionPrimer then Ready`() {
        assertEquals(7, OnboardingTeachSteps.size)
        assertEquals(OnboardingTeachSteps.size + 2, OnboardingTourSteps.size)
        assertTrue(OnboardingTourSteps.take(7).all { it is WizardStep.Teach })
        assertSame(WizardStep.PermissionPrimer, OnboardingTourSteps[7])
        assertSame(WizardStep.Ready, OnboardingTourSteps[8])
    }

    // LD8 — Journalling (step 4) deliberately reuses TeachIllustration.INTAKE (no new JOURNAL
    // mock), so illustrations are no longer required to be pairwise distinct; every OTHER step
    // still gets its own illustration.
    @Test fun `every Teach step has non-blank copy, and only the Journalling step reuses an illustration`() {
        val illustrations = OnboardingTeachSteps.map { it.illustration }
        assertEquals(7, illustrations.size)
        assertEquals(6, illustrations.toSet().size)
        val duplicated = illustrations.groupBy { it }.filterValues { it.size > 1 }
        assertEquals(mapOf(TeachIllustration.INTAKE to listOf(TeachIllustration.INTAKE, TeachIllustration.INTAKE)), duplicated)
        OnboardingTeachSteps.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.body.length > 20)
        }
    }

    @Test fun `STREAKS and PRIVACY illustrations are used exactly once each`() {
        val illustrations = OnboardingTeachSteps.map { it.illustration }
        assertEquals(1, illustrations.count { it == TeachIllustration.STREAKS })
        assertEquals(1, illustrations.count { it == TeachIllustration.PRIVACY })
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
