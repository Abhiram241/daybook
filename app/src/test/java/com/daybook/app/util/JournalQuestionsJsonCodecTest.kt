package com.daybook.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Journal-as-habit round — [DateTimeUtils.journalQuestionsToJson] / [DateTimeUtils.jsonToJournalQuestions]:
 * the per-habit `Habit.journalQuestionsJson` codec. Unlike `timesToJson`/`daysToJson`'s comma-join,
 * this is real JSON since question text may itself contain commas.
 */
class JournalQuestionsJsonCodecTest {

    @Test fun roundTrips_orderPreserved() {
        val questions = listOf("What's on your mind?", "How did you sleep?", "Anything, in particular?")
        val json = DateTimeUtils.journalQuestionsToJson(questions)
        assertEquals(questions, DateTimeUtils.jsonToJournalQuestions(json))
    }

    @Test fun emptyList_encodesToEmptyString() {
        assertEquals("", DateTimeUtils.journalQuestionsToJson(emptyList()))
    }

    @Test fun emptyString_decodesToEmptyList() {
        assertEquals(emptyList<String>(), DateTimeUtils.jsonToJournalQuestions(""))
        assertEquals(emptyList<String>(), DateTimeUtils.jsonToJournalQuestions("   "))
    }

    @Test fun blanksAreDropped_onEncode() {
        val json = DateTimeUtils.journalQuestionsToJson(listOf("Q1", "  ", "", "Q2"))
        assertEquals(listOf("Q1", "Q2"), DateTimeUtils.jsonToJournalQuestions(json))
    }

    @Test fun garbageJson_decodesToEmptyList() {
        assertEquals(emptyList<String>(), DateTimeUtils.jsonToJournalQuestions("not json"))
    }

    @Test fun questionTextWithCommasAndQuotes_survivesRoundTrip() {
        val questions = listOf("What, if anything, happened?", "Any \"trigger\" today?")
        val json = DateTimeUtils.journalQuestionsToJson(questions)
        assertEquals(questions, DateTimeUtils.jsonToJournalQuestions(json))
    }
}
