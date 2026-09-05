package com.daybook.app.data

import com.daybook.app.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Journal-as-habit round (B3) — [remapLegacyJournalTaskType]: a legacy backup's `"type":"JOURNAL"`
 * FoodMedTask must land as CUSTOM on import (nothing can render/schedule/edit a JOURNAL-typed
 * FoodMedTask correctly after this round); every other type passes through unchanged.
 */
class RemapLegacyJournalTaskTypeTest {

    @Test fun journal_remapsToCustom() {
        assertEquals(TaskType.CUSTOM, remapLegacyJournalTaskType(TaskType.JOURNAL))
    }

    @Test fun everyOtherType_passesThroughUnchanged() {
        assertEquals(TaskType.FOOD, remapLegacyJournalTaskType(TaskType.FOOD))
        assertEquals(TaskType.MED, remapLegacyJournalTaskType(TaskType.MED))
        assertEquals(TaskType.CUSTOM, remapLegacyJournalTaskType(TaskType.CUSTOM))
    }
}
