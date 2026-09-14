package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §1.4/§5. */
class ChatSystemMessageTest {

    @Test fun `blank chat instructions produce no user-instructions paragraph`() {
        val msg = buildChatSystemMessage("", "", "DATA")
        assertFalse(msg.contains("The user's own instructions"))
        assertTrue(msg.startsWith(CHAT_BUILT_IN_RULES))
        assertTrue(msg.endsWith("DATA"))
    }

    @Test fun `non-blank chat instructions appear after the rules and before the data`() {
        val msg = buildChatSystemMessage("Be concise.", "", "DATA")
        val rulesIdx = msg.indexOf(CHAT_BUILT_IN_RULES)
        val instrIdx = msg.indexOf("Be concise.")
        val dataIdx = msg.indexOf("DATA")
        assertTrue(rulesIdx == 0)
        assertTrue(instrIdx > rulesIdx)
        assertTrue(dataIdx > instrIdx)
        assertTrue(msg.contains("The user's own instructions for this chat"))
    }

    @Test fun `the clamp note sits between instructions and data`() {
        val msg = buildChatSystemMessage("Be concise.", "(clamped)", "DATA")
        val instrIdx = msg.indexOf("Be concise.")
        val clampIdx = msg.indexOf("(clamped)")
        val dataIdx = msg.indexOf("DATA")
        assertTrue(clampIdx > instrIdx)
        assertTrue(dataIdx > clampIdx)
    }

    @Test fun `summary instructions never appear in chat`() {
        // buildChatSystemMessage only ever receives aiChatMetaPrompt — a summary-only prompt
        // string passed here would show up as chat instructions, which is exactly what must never
        // happen at the call site (DailyReportViewModel.openChat uses settings.aiChatMetaPrompt,
        // never settings.aiMetaPrompt). This test locks the function's own contract: whatever is
        // passed as chatMetaPrompt is what shows, nothing else is pulled in implicitly.
        val msg = buildChatSystemMessage("chat-only text", "", "DATA")
        assertTrue(msg.contains("chat-only text"))
        assertEquals(1, Regex("The user's own instructions").findAll(msg).count())
    }
}
