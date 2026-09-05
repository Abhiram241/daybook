package com.daybook.app.ui.detail

import com.daybook.app.data.model.Event
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.3 Phase 5 (§5.7) — [TimelineEvent.displayLabel] prettifies the raw enum name for the
 * Detail history row (was done inline in the composable). Guards the "USER_SNOOZED" → "User
 * snoozed" transformation so a renamed enum constant can't silently change the UI copy.
 */
class TimelineDisplayLabelTest {

    private fun row(action: Event.Action) =
        TimelineEvent(id = "x", timestamp = "", rawTimestamp = 0L, action = action)

    @Test fun `single word actions are capitalised`() {
        assertEquals("Completed", row(Event.Action.COMPLETED).displayLabel)
        assertEquals("Skipped", row(Event.Action.SKIPPED).displayLabel)
        assertEquals("Replied", row(Event.Action.REPLIED).displayLabel)
        assertEquals("Shown", row(Event.Action.SHOWN).displayLabel)
    }

    @Test fun `underscored actions become a single spaced phrase, first letter only capitalised`() {
        assertEquals("User snoozed", row(Event.Action.USER_SNOOZED).displayLabel)
    }
}
