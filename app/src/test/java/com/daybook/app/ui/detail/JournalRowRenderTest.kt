package com.daybook.app.ui.detail

import com.daybook.app.data.JournalQa
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.5.4 Phase 5 — [journalRowPairs] is the "decode qa_json -> pairs to stack on the Detail
 * history row" logic. Blank-answer rule under test: **skip the pair** (a Phase-2 interim entry
 * answers only Q1, so the rest must not render as a wall of "—").
 */
class JournalRowRenderTest {

    @Test fun `null qaJson yields no pairs`() {
        assertEquals(emptyList<Pair<String, String>>(), journalRowPairs(null))
    }

    @Test fun `blank qaJson yields no pairs`() {
        assertEquals(emptyList<Pair<String, String>>(), journalRowPairs("   "))
    }

    @Test fun `garbage qaJson yields no pairs`() {
        assertEquals(emptyList<Pair<String, String>>(), journalRowPairs("not json"))
    }

    @Test fun `valid blob yields ordered pairs`() {
        val json = JournalQa.encode(
            listOf("How do you feel?" to "Good", "What happened?" to "A walk", "Anything else?" to "No")
        )
        assertEquals(
            listOf("How do you feel?" to "Good", "What happened?" to "A walk", "Anything else?" to "No"),
            journalRowPairs(json)
        )
    }

    @Test fun `blank answers are skipped, order of the rest preserved`() {
        val json = JournalQa.encode(
            listOf("Q1" to "answered", "Q2" to "", "Q3" to "   ", "Q4" to "also answered")
        )
        assertEquals(listOf("Q1" to "answered", "Q4" to "also answered"), journalRowPairs(json))
    }

    @Test fun `Phase-2 interim entry - only Q1 answered - renders just Q1`() {
        val json = JournalQa.encode(
            listOf("What's on your mind?" to "Tired today", "Q2" to "", "Q3" to "")
        )
        assertEquals(listOf("What's on your mind?" to "Tired today"), journalRowPairs(json))
    }

    @Test fun `every answer blank yields no pairs`() {
        val json = JournalQa.encode(listOf("Q1" to "", "Q2" to "  "))
        assertEquals(emptyList<Pair<String, String>>(), journalRowPairs(json))
    }
}
