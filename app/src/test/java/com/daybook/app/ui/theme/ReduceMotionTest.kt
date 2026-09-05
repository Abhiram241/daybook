package com.daybook.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** rec 4 — the OR of the `reduce_motion` preference and the OS ANIMATOR_DURATION_SCALE == 0. */
class ReduceMotionTest {

    @Test
    fun `off when neither the preference nor the OS asks for it`() {
        assertFalse(effectiveReduceMotion(pref = false, animatorScale = 1f))
        assertFalse(effectiveReduceMotion(pref = false, animatorScale = 0.5f))
    }

    @Test
    fun `on when the preference is set`() {
        assertTrue(effectiveReduceMotion(pref = true, animatorScale = 1f))
    }

    @Test
    fun `on when the OS animator scale is exactly zero`() {
        assertTrue(effectiveReduceMotion(pref = false, animatorScale = 0f))
    }

    @Test
    fun `on when both`() {
        assertTrue(effectiveReduceMotion(pref = true, animatorScale = 0f))
    }
}
