package com.daybook.app.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journal-as-habit round (Phase 4) — [advanceChat]'s pure bubble-append / index-advance /
 * all-answered maths, independent of any ViewModel or Compose state.
 */
class ChatFlowTest {

    private val questions = listOf("How was today?", "Anything on your mind?", "One good thing?")

    @Test fun firstAnswer_appendsAnswerThenNextQuestion_notAllAnswered() {
        val start = listOf(ChatMessage.Question(questions[0]))
        val (messages, allAnswered) = advanceChat(start, questions, answeredCount = 0, answer = "Good")

        assertEquals(3, messages.size)
        assertEquals(ChatMessage.Question(questions[0]), messages[0])
        assertEquals(ChatMessage.Answer("Good"), messages[1])
        assertEquals(ChatMessage.Question(questions[1]), messages[2])
        assertFalse(allAnswered)
    }

    @Test fun lastAnswer_appendsAnswerOnly_allAnswered() {
        val start = listOf(
            ChatMessage.Question(questions[0]), ChatMessage.Answer("Good"),
            ChatMessage.Question(questions[1]), ChatMessage.Answer("Nope"),
            ChatMessage.Question(questions[2])
        )
        val (messages, allAnswered) = advanceChat(start, questions, answeredCount = 2, answer = "Coffee")

        assertEquals(6, messages.size)
        assertEquals(ChatMessage.Answer("Coffee"), messages.last())
        assertTrue(allAnswered)
    }

    @Test fun answerIsTrimmed() {
        val start = listOf(ChatMessage.Question(questions[0]))
        val (messages, _) = advanceChat(start, questions, answeredCount = 0, answer = "  spaced  ")
        assertEquals(ChatMessage.Answer("spaced"), messages[1])
    }

    @Test fun outOfRangeIndex_isANoOpAndReportsAllAnswered() {
        val start = listOf(ChatMessage.Question(questions[0]))
        val (messages, allAnswered) = advanceChat(start, questions, answeredCount = questions.size, answer = "x")
        assertEquals(start, messages)
        assertTrue(allAnswered)
    }
}
