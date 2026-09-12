package com.daybook.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A5 (§3.6.2) — [longPressRampMillis] must reach its end state at exactly the platform's own
 * long-press timeout, whatever that timeout is (stock 500 ms, or an Accessibility "Touch and hold
 * delay" of Medium/Long).
 */
class NavLongPressRampTest {

    @Test fun stockDefault_500ms() {
        assertEquals(380, longPressRampMillis(500L))
    }

    @Test fun accessibilitySetting_1500ms() {
        assertEquals(1380, longPressRampMillis(1500L))
    }

    @Test fun pathologicalSubDeadZoneValue_neverGoesNonPositive() {
        assertEquals(1, longPressRampMillis(50L))
        assertEquals(1, longPressRampMillis(0L))
    }

    @Test fun customDeadZone_isRespected() {
        assertEquals(400, longPressRampMillis(500L, deadZoneMillis = 100))
    }
}
