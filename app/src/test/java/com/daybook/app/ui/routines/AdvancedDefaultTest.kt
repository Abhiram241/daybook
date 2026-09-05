package com.daybook.app.ui.routines

import com.daybook.app.data.model.DayOfWeek
import com.daybook.app.data.model.HabitType
import com.daybook.app.ui.icons.Icons
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.3 Phase 4 (§4.9). Pure cases for [anyAdvancedFieldNonDefault] — the helper that decides
 * whether the Edit form auto-expands its collapsed "Advanced" section.
 */
class AdvancedDefaultTest {

    private fun freshState() = HabitFormState().apply {
        // A brand-new form: every Advanced field at its default.
        title = "Drink water"
        iconKey = Icons.TASK
        tintName = "AUTO"
        snooze = 10
    }

    @Test
    fun `a pristine form has no non-default advanced field`() {
        assertFalse(anyAdvancedFieldNonDefault(freshState()))
    }

    @Test
    fun `a non-blank description trips it`() {
        val s = freshState().apply { description = "after lunch" }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a non-default icon trips it`() {
        val s = freshState().apply { iconKey = Icons.WATER }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a chosen card colour trips it`() {
        val s = freshState().apply { tintName = "MINT" }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a restricted active-day set trips it`() {
        val s = freshState().apply { activeDays.add(DayOfWeek.MONDAY) }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a changed snooze interval trips it`() {
        val s = freshState().apply { snooze = 15 }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `the default snooze of 10 does not trip it`() {
        val s = freshState().apply { snooze = 10 }
        assertFalse(anyAdvancedFieldNonDefault(s))
    }

    // ---- v0.5.5: STREAK ("Ongoing") — Active days / Snooze are not settable, so they must not
    //      force-expand Advanced; Description still does. ----

    @Test
    fun `a STREAK form with stale activeDays or snooze does not trip it`() {
        val s = freshState().apply {
            type = HabitType.STREAK
            activeDays.add(DayOfWeek.MONDAY)
            snooze = 15
        }
        assertFalse(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a STREAK form with a description still trips it`() {
        val s = freshState().apply { type = HabitType.STREAK; description = "since new year" }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    // ---- Journal-as-habit round: a fresh JOURNAL form's single seeded default question must not
    //      force-expand Advanced; any other Questions list state (more/fewer/different) must. ----

    @Test
    fun `a JOURNAL form with only the seeded default question does not trip it`() {
        val s = freshState().apply {
            type = HabitType.JOURNAL
            journalQuestions.add("What's on your mind?")
        }
        assertFalse(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a JOURNAL form with an extra question trips it`() {
        val s = freshState().apply {
            type = HabitType.JOURNAL
            journalQuestions.add("What's on your mind?")
            journalQuestions.add("How did you sleep?")
        }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a JOURNAL form with an edited default question trips it`() {
        val s = freshState().apply {
            type = HabitType.JOURNAL
            journalQuestions.add("How was your day?")
        }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }

    @Test
    fun `a JOURNAL form with stale activeDays or snooze but no questions still trips it via base rules`() {
        val s = freshState().apply {
            type = HabitType.JOURNAL
            journalQuestions.add("What's on your mind?")
            activeDays.add(DayOfWeek.MONDAY)
        }
        assertTrue(anyAdvancedFieldNonDefault(s))
    }
}
