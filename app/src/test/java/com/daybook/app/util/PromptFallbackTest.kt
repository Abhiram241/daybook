package com.daybook.app.util

import com.daybook.app.util.notification.NotificationUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.3 item 8 — the default intake prompt literal lives only in
 * [NotificationUtils.resolvePrompt]; every null/blank prompt falls back to "What did you have?".
 */
class PromptFallbackTest {

    @Test fun nullAndBlankFallBackToDefault() {
        assertEquals("What did you have?", NotificationUtils.resolvePrompt(null))
        assertEquals("What did you have?", NotificationUtils.resolvePrompt(""))
        assertEquals("What did you have?", NotificationUtils.resolvePrompt("   "))
    }

    @Test fun nonBlankPromptIsKept() {
        assertEquals("What meds?", NotificationUtils.resolvePrompt("What meds?"))
    }
}
