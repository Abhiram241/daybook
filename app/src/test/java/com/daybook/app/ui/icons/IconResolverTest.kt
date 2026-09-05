package com.daybook.app.ui.icons

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 4 (§4.6 / D1). Guards "no silent fallback": an unknown key must resolve to the
 * deliberate [DaybookIcons.Unknown] placeholder, and every curated key must resolve to a real
 * (non-Unknown) vector.
 */
class IconResolverTest {

    @Test
    fun `an unknown key resolves to the Unknown placeholder`() {
        assertSame(DaybookIcons.Unknown, Icons.getIcon("definitely-not-a-key"))
        assertSame(DaybookIcons.Unknown, Icons.getIcon(""))
        assertSame(DaybookIcons.Unknown, Icons.getIcon("zzzz_made_up"))
    }

    @Test
    fun `every curated key resolves to a non-Unknown vector`() {
        Icons.getCuratedIconSet().forEach { key ->
            val v = Icons.getIcon(key)
            assertNotEquals("curated key '$key' fell through to Unknown", DaybookIcons.Unknown, v)
        }
    }

    @Test
    fun `named alias keys still resolve to real glyphs`() {
        listOf(
            "water", "pill", "medication", "restaurant", "directions_run", "menu_book",
            "sleep", "meditation", "task", "add", "check", "close", "settings",
            "notifications", "home", "delete", "edit", "calendar_today", "access_time",
            "archive", "unarchive", "filter_list", "error", "info"
        ).forEach { key ->
            assertNotEquals("alias key '$key' fell through to Unknown", DaybookIcons.Unknown, Icons.getIcon(key))
        }
    }

    @Test
    fun `resolution is case-insensitive`() {
        assertSame(Icons.getIcon("water"), Icons.getIcon("WATER"))
        assertTrue(Icons.getIcon("TASK") === Icons.getIcon("task"))
    }
}
