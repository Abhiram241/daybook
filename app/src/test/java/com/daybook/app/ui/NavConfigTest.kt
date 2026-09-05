package com.daybook.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** rec 7 (SD-2) — pure bottom-nav config resolution. Today is always present and always first. */
class NavConfigTest {

    @Test
    fun `the default CSV yields all three tabs in order`() {
        assertEquals(listOf("home", "routines", "foodmed"), NavConfig.visibleRoutesFrom("home,routines,foodmed"))
    }

    @Test
    fun `an intake-only user still gets Today, forced first`() {
        assertEquals(listOf("home", "foodmed"), NavConfig.visibleRoutesFrom("foodmed"))
    }

    @Test
    fun `blank or garbage falls back to all three`() {
        assertEquals(listOf("home", "routines", "foodmed"), NavConfig.visibleRoutesFrom(""))
        assertEquals(listOf("home", "routines", "foodmed"), NavConfig.visibleRoutesFrom(null))
        assertEquals(listOf("home", "routines", "foodmed"), NavConfig.visibleRoutesFrom("nope,also-nope"))
    }

    @Test
    fun `a stored order with Today not first is corrected to Today-first`() {
        assertEquals(
            listOf("home", "routines", "foodmed"),
            NavConfig.visibleRoutesFrom("routines,home,foodmed")
        )
    }

    @Test
    fun `landingIndex resolves a visible route and falls back to 0 for a hidden one`() {
        assertEquals(1, NavConfig.landingIndex("foodmed", listOf("home", "foodmed")))
        assertEquals(0, NavConfig.landingIndex("routines", listOf("home", "foodmed")))
        assertEquals(0, NavConfig.landingIndex(null, listOf("home", "routines", "foodmed")))
    }

    @Test
    fun `toggleRoute removes then re-inserts a tab in canonical order`() {
        assertEquals("home,foodmed", NavConfig.toggleRoute("home,routines,foodmed", "routines"))
        assertEquals("home,routines,foodmed", NavConfig.toggleRoute("home,foodmed", "routines"))
        assertEquals("home,routines", NavConfig.toggleRoute("home,routines,foodmed", "foodmed"))
    }

    @Test
    fun `toggleRoute can never remove Today`() {
        assertEquals("home,routines,foodmed", NavConfig.toggleRoute("home,routines,foodmed", "home"))
    }
}
