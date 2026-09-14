package com.daybook.app.data.sync

import com.daybook.app.data.backup.DailyReportAiSummaryLog
import com.daybook.app.data.backup.DayEntry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DAILY_REPORT_PLAN.md §3.6 — `DayEntry.aiSummary` must not churn a month's `contentHash` for a
 * day with no cached AI summary: present-but-null serialises IDENTICALLY to the field being
 * absent. Mirrors `HealthDayHashTest`'s exact pattern for `DayEntry.health`.
 */
@OptIn(ExperimentalSerializationApi::class)
class DailyReportAiSummaryHashTest {

    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun summary() = DailyReportAiSummaryLog(
        provider = "OPENAI", model = "gpt-4o-mini", summaryText = "A quiet, productive day.",
        generatedAt = 1_700_000_000_000L
    )

    private fun day(aiSummary: DailyReportAiSummaryLog? = null) =
        DayEntry(date = "2026-08-29", aiSummary = aiSummary)

    @Test fun differentSummary_hashesDiffer() {
        assertNotEquals(ContentHash.ofDays(listOf(day())), ContentHash.ofDays(listOf(day(summary()))))
    }

    @Test fun nullSummary_isAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(DayEntry.serializer(), day())
        assertFalse("aiSummary must NOT appear: $canonical", canonical.contains("\"aiSummary\""))

        val withOne = canonicalJson.encodeToString(DayEntry.serializer(), day(summary()))
        assertTrue(withOne.contains("\"aiSummary\""))
    }

    @Test fun nullSummary_hashesSameAsFieldAbsent() {
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absent = lenient.decodeFromString(DayEntry.serializer(), """{"date":"2026-08-29"}""")
        assertEquals(null, absent.aiSummary)
        assertEquals(ContentHash.ofDays(listOf(absent)), ContentHash.ofDays(listOf(day())))
    }

    /** §3.2/§3.6 — the API key itself is never part of this wire model at all (a `provider`/`model`
     *  pair is not a credential), so there is nothing to assert it's absent from here; this test
     *  only documents that the summary text/provenance fields DO round-trip. */
    @Test fun summaryFields_roundTrip() {
        val encoded = canonicalJson.encodeToString(DayEntry.serializer(), day(summary()))
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(DayEntry.serializer(), encoded)
        assertEquals(summary(), decoded.aiSummary)
    }
}
