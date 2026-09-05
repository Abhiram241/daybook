package com.daybook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.5.4 Phase 2 (D3) — [JournalQa] encode/decode round trip + tolerant decode.
 */
class JournalQaCodecTest {

    @Test fun roundTrip_preservesOrderAndContent() {
        val pairs = listOf("Q1" to "A1", "Q2" to "A2", "Q3" to "A3")
        assertEquals(pairs, JournalQa.decode(JournalQa.encode(pairs)))
    }

    @Test fun decodeNull_isEmpty() {
        assertEquals(emptyList<Pair<String, String>>(), JournalQa.decode(null))
    }

    @Test fun decodeBlank_isEmpty() {
        assertEquals(emptyList<Pair<String, String>>(), JournalQa.decode("   "))
    }

    @Test fun decodeGarbage_isEmpty() {
        assertEquals(emptyList<Pair<String, String>>(), JournalQa.decode("garbage"))
        assertEquals(emptyList<Pair<String, String>>(), JournalQa.decode("{not an array}"))
        assertEquals(emptyList<Pair<String, String>>(), JournalQa.decode("[{\"q\":"))
    }

    @Test fun decodeUnknownKeys_areIgnored() {
        val json = "[{\"q\":\"Question\",\"a\":\"Answer\",\"extra\":\"ignored\"}]"
        assertEquals(listOf("Question" to "Answer"), JournalQa.decode(json))
    }

    @Test fun emptyAnswer_survivesRoundTrip() {
        val pairs = listOf("Answered" to "yes", "Skipped" to "", "AlsoAnswered" to "ok")
        assertEquals(pairs, JournalQa.decode(JournalQa.encode(pairs)))
    }

    @Test fun quotesNewlinesUnicode_surviveRoundTrip() {
        val pairs = listOf(
            "What did \"they\" say?" to "line one\nline two\ttabbed",
            "Émoji ✅ 你好" to "réponse — with dash & <html>"
        )
        assertEquals(pairs, JournalQa.decode(JournalQa.encode(pairs)))
    }

    @Test fun encodeEmptyList_decodesBackEmpty() {
        val encoded = JournalQa.encode(emptyList())
        assertTrue(JournalQa.decode(encoded).isEmpty())
    }
}
