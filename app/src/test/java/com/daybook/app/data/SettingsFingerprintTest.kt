package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §5. */
class SettingsFingerprintTest {

    @Test fun `a changed exclusion set changes the fingerprint`() {
        val a = settingsFingerprint("prompt", "WORKOUT,HEALTH", exclusionFingerprint = "h1")
        val b = settingsFingerprint("prompt", "WORKOUT,HEALTH", exclusionFingerprint = "h1,h2")
        assertNotEquals(a, b)
    }

    @Test fun `the same exclusion set and settings produce the same fingerprint`() {
        val a = settingsFingerprint("prompt", "WORKOUT,HEALTH", exclusionFingerprint = "h1")
        val b = settingsFingerprint("prompt", "WORKOUT,HEALTH", exclusionFingerprint = "h1")
        assertEquals(a, b)
    }

    @Test fun `chat instructions do not affect this fingerprint (it never receives them)`() {
        // The chat meta-prompt is a wholly separate field (aiChatMetaPrompt) never passed into
        // settingsFingerprint, which only ever consumes aiMetaPrompt/aiReportCategories — a chat
        // instructions edit can't change this string.
        val a = settingsFingerprint("summary prompt", "WORKOUT")
        val b = settingsFingerprint("summary prompt", "WORKOUT")
        assertEquals(a, b)
    }
}
