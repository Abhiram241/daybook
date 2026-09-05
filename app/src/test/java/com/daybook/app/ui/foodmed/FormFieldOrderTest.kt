package com.daybook.app.ui.foodmed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.3 Phase 4 (§4.9 / UI Q4). Locks the canonical top-level section order that BOTH the
 * Habit and FoodMed add/edit forms must lay out. A change to [CANONICAL_FORM_FIELD_ORDER]
 * without updating both `HabitFormScaffold` and `FoodMedFormScaffold` should fail review here.
 */
class FormFieldOrderTest {

    @Test
    fun `canonical order is name, type, times, type-specific, advanced`() {
        assertEquals(
            listOf("name", "type", "times", "typeSpecificSections", "advanced"),
            CANONICAL_FORM_FIELD_ORDER
        )
    }

    @Test
    fun `name comes before type and type comes before times`() {
        val order = CANONICAL_FORM_FIELD_ORDER
        assertEquals(true, order.indexOf("name") < order.indexOf("type"))
        assertEquals(true, order.indexOf("type") < order.indexOf("times"))
        assertEquals(true, order.indexOf("times") < order.indexOf("advanced"))
    }
}
