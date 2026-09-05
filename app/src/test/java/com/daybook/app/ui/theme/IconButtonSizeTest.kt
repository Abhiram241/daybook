package com.daybook.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 0 (§3.5 / backlog #19). Documents the [IconButtonSize] scale and locks its
 * ordering so a later edit cannot quietly reshuffle the roles.
 */
class IconButtonSizeTest {

    @Test
    fun `the scale is exactly 32, 40, 44, 56 dp in order`() {
        assertEquals(
            listOf(32.dp, 40.dp, 44.dp, 56.dp),
            IconButtonSize.entries.map { it.dp }
        )
    }

    @Test
    fun `the scale is strictly ascending`() {
        val values = IconButtonSize.entries.map { it.dp.value }
        assertTrue(values.zipWithNext().all { (a, b) -> a < b })
    }
}
