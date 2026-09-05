package com.daybook.app.ui.journal

import com.daybook.app.data.JournalQa
import com.daybook.app.data.MAX_JOURNAL_CHARS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.4 Phase 2 (§2.6) — pins [journalQaPayload]: all-blank -> null; responseText = non-blank
 * answers joined by "\n"; qaJson snapshots EVERY question even with a blank answer; cap at
 * [MAX_JOURNAL_CHARS].
 */
class JournalQaPayloadTest {

    private val questions = listOf("Q1", "Q2", "Q3")

    @Test fun allBlankAnswers_returnNull() {
        assertNull(journalQaPayload(questions, listOf("", "  ", "\t")))
        assertNull(journalQaPayload(questions, emptyList()))
        assertNull(journalQaPayload(questions, listOf("")))
    }

    @Test fun oneAnsweredQuestion_responseTextIsThatAnswer_snapshotHasEveryQuestion() {
        val save = journalQaPayload(questions, listOf("hello"))!!
        assertEquals("hello", save.responseText)
        assertEquals(
            listOf("Q1" to "hello", "Q2" to "", "Q3" to ""),
            JournalQa.decode(save.qaJson)
        )
    }

    @Test fun answersAreTrimmed() {
        val save = journalQaPayload(questions, listOf("  rice  ", "", "  fine "))!!
        assertEquals("rice\nfine", save.responseText)
        assertEquals(
            listOf("Q1" to "rice", "Q2" to "", "Q3" to "fine"),
            JournalQa.decode(save.qaJson)
        )
    }

    @Test fun trailingBlankAnswers_droppedFromResponseTextButPresentInQaJson() {
        val save = journalQaPayload(questions, listOf("only first", "", ""))!!
        assertEquals("only first", save.responseText)
        val decoded = JournalQa.decode(save.qaJson)
        assertEquals(3, decoded.size)
        assertEquals("Q3" to "", decoded[2])
    }

    @Test fun interiorBlankAnswer_isSkippedInResponseTextJoin() {
        val save = journalQaPayload(questions, listOf("a", "", "c"))!!
        assertEquals("a\nc", save.responseText)
    }

    @Test fun moreAnswersThanQuestions_extraAnswersIgnored() {
        val save = journalQaPayload(listOf("Q1"), listOf("kept", "dropped"))!!
        assertEquals("kept", save.responseText)
        assertEquals(listOf("Q1" to "kept"), JournalQa.decode(save.qaJson))
    }

    @Test fun overCap_isTruncated() {
        val huge = "x".repeat(MAX_JOURNAL_CHARS * 2)
        val save = journalQaPayload(listOf("Q1"), listOf(huge))!!
        assertTrue(save.responseText.length <= MAX_JOURNAL_CHARS)
        assertTrue(save.qaJson.length <= MAX_JOURNAL_CHARS)
    }

    // v0.5.4 Phase 4 (§4.4) — the conversational stepper hands a full per-question answer list.

    @Test fun fullMultiAnswerList_qaJsonOrderMatchesQuestionOrder() {
        val save = journalQaPayload(questions, listOf("one", "two", "three"))!!
        assertEquals("one\ntwo\nthree", save.responseText)
        assertEquals(
            listOf("Q1" to "one", "Q2" to "two", "Q3" to "three"),
            JournalQa.decode(save.qaJson)
        )
    }

    @Test fun midListBlankAnswer_keptInQaJsonAsEmptyString() {
        val save = journalQaPayload(questions, listOf("first", "", "third"))!!
        val decoded = JournalQa.decode(save.qaJson)
        assertEquals(3, decoded.size)
        assertEquals("Q2" to "", decoded[1])   // the "a":"" pair survives
        assertEquals("first\nthird", save.responseText)
    }

    @Test fun editFromDecodedSnapshot_roundTrips() {
        // An existing entry: its questions come from its own decoded qa_json (D3 snapshot), the
        // stepper pre-fills the answers, the user tweaks one, save re-encodes the same questions.
        val original = journalQaPayload(listOf("How do you feel?", "What did you eat?"), listOf("ok", "rice"))!!
        val snapshot = JournalQa.decode(original.qaJson)
        val snapQuestions = snapshot.map { it.first }
        val editedAnswers = snapshot.map { it.second }.toMutableList().also { it[0] = "great" }

        val edited = journalQaPayload(snapQuestions, editedAnswers)!!
        assertEquals("great\nrice", edited.responseText)
        assertEquals(
            listOf("How do you feel?" to "great", "What did you eat?" to "rice"),
            JournalQa.decode(edited.qaJson)
        )
    }
}
