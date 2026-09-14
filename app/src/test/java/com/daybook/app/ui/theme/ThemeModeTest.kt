package com.daybook.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** UX overhaul item 4 — [ThemeMode] key round-trip + default. Mirrors `FontChoice`. */
class ThemeModeTest {

    @Test
    fun `default is LIGHT`() {
        assertSame(ThemeMode.LIGHT, ThemeMode.DEFAULT)
    }

    @Test
    fun `every storage key round-trips`() {
        ThemeMode.entries.forEach { mode ->
            assertSame(mode, ThemeMode.fromKeyOrDefault(mode.storageKey))
        }
    }

    @Test
    fun `null, blank and unknown keys fall back to LIGHT`() {
        assertSame(ThemeMode.LIGHT, ThemeMode.fromKeyOrDefault(null))
        assertSame(ThemeMode.LIGHT, ThemeMode.fromKeyOrDefault(""))
        assertSame(ThemeMode.LIGHT, ThemeMode.fromKeyOrDefault("dark"))
        assertSame(ThemeMode.LIGHT, ThemeMode.fromKeyOrDefault("SEPIA"))
    }

    @Test
    fun `storage keys are the enum names`() {
        assertEquals("DARK", ThemeMode.DARK.storageKey)
        assertEquals("LIGHT", ThemeMode.LIGHT.storageKey)
        assertEquals("SYSTEM", ThemeMode.SYSTEM.storageKey)
    }
}
