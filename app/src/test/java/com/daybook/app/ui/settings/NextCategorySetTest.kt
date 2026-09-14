package com.daybook.app.ui.settings

import com.daybook.app.data.ReportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextCategorySetTest {
    @Test fun `turning off the last category is refused`() {
        assertNull(nextCategorySet(setOf(ReportCategory.HEALTH), ReportCategory.HEALTH, enabled = false))
    }

    @Test fun `normal toggles add and remove`() {
        assertEquals(setOf(ReportCategory.HEALTH), nextCategorySet(setOf(ReportCategory.HEALTH, ReportCategory.TODO), ReportCategory.TODO, false))
        assertEquals(setOf(ReportCategory.HEALTH, ReportCategory.TODO), nextCategorySet(setOf(ReportCategory.HEALTH), ReportCategory.TODO, true))
    }
}
