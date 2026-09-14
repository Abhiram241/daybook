package com.daybook.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** rec 7 (SD-2) — pure bottom-nav config resolution. Today is always present and always first.
 *  DAILY_REPORT_PLAN.md §2 — a fourth id, "report", was appended last (not inserted) to ALL_ROUTES. */
class NavConfigTest {

    @Test
    fun `the default CSV yields all four tabs in order`() {
        assertEquals(
            listOf("home", "routines", "foodmed", "report"),
            NavConfig.visibleRoutesFrom("home,routines,foodmed,report")
        )
    }

    @Test
    fun `an intake-only user still gets Today, forced first`() {
        assertEquals(listOf("home", "foodmed"), NavConfig.visibleRoutesFrom("foodmed"))
    }

    @Test
    fun `blank or garbage falls back to all four`() {
        assertEquals(listOf("home", "routines", "foodmed", "report"), NavConfig.visibleRoutesFrom(""))
        assertEquals(listOf("home", "routines", "foodmed", "report"), NavConfig.visibleRoutesFrom(null))
        assertEquals(listOf("home", "routines", "foodmed", "report"), NavConfig.visibleRoutesFrom("nope,also-nope"))
    }

    @Test
    fun `a stored order with Today not first is corrected to Today-first`() {
        assertEquals(
            listOf("home", "routines", "foodmed", "report"),
            NavConfig.visibleRoutesFrom("routines,home,foodmed,report")
        )
    }

    @Test
    fun `a pre-Daily-Report stored CSV simply omits the fourth tab`() {
        // A stored value is a known, non-blank set — it is NOT garbage, so it must NOT fall back
        // to all four. This is what MIGRATION_25_26's data backfill (appending ",report") exists
        // to avoid for real installs; this case documents what would happen without it.
        assertEquals(listOf("home", "routines", "foodmed"), NavConfig.visibleRoutesFrom("home,routines,foodmed"))
    }

    @Test
    fun `landingIndex resolves a visible route and falls back to 0 for a hidden one`() {
        assertEquals(1, NavConfig.landingIndex("foodmed", listOf("home", "foodmed")))
        assertEquals(0, NavConfig.landingIndex("routines", listOf("home", "foodmed")))
        assertEquals(0, NavConfig.landingIndex(null, listOf("home", "routines", "foodmed", "report")))
    }

    @Test
    fun `toggleRoute removes then re-inserts a tab in canonical order`() {
        assertEquals("home,foodmed,report", NavConfig.toggleRoute("home,routines,foodmed,report", "routines"))
        assertEquals("home,routines,foodmed", NavConfig.toggleRoute("home,foodmed", "routines"))
        assertEquals("home,routines", NavConfig.toggleRoute("home,routines,foodmed", "foodmed"))
    }

    @Test
    fun `toggleRoute can add the report tab in its canonical rightmost slot`() {
        assertEquals("home,routines,foodmed,report", NavConfig.toggleRoute("home,routines,foodmed", "report"))
    }

    @Test
    fun `toggleRoute can never remove Today`() {
        assertEquals(
            "home,routines,foodmed,report",
            NavConfig.toggleRoute("home,routines,foodmed,report", "home")
        )
    }
}
