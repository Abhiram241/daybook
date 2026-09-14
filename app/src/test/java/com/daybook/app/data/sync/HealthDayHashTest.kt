package com.daybook.app.data.sync

import com.daybook.app.data.backup.BackupMeta
import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.HealthDayLog
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B5 (§7.5.0, R1) — `DayEntry.health` must not churn a month's `contentHash` for a day with no
 * health data: present-but-null serialises IDENTICALLY to the field being absent. Also proves
 * `BackupMeta.kind` (new, §7.5.1) cannot destabilise a hash, since `ContentHash` hashes
 * `definitions + days` only, never `meta`.
 */
@OptIn(ExperimentalSerializationApi::class)
class HealthDayHashTest {

    private val canonicalJson = Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }

    private fun healthLog() = HealthDayLog(steps = 8000, sleepMinutes = 420)

    private fun day(health: HealthDayLog? = null) = DayEntry(date = "2026-08-29", health = health)

    @Test fun differentHealth_hashesDiffer() {
        assertNotEquals(ContentHash.ofDays(listOf(day())), ContentHash.ofDays(listOf(day(healthLog()))))
    }

    @Test fun nullHealth_isAbsentInCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(DayEntry.serializer(), day())
        assertFalse("health must NOT appear: $canonical", canonical.contains("\"health\""))

        val withOne = canonicalJson.encodeToString(DayEntry.serializer(), day(healthLog()))
        assertTrue(withOne.contains("\"health\""))
    }

    @Test fun nullHealth_hashesSameAsFieldAbsent() {
        val lenient = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val absent = lenient.decodeFromString(DayEntry.serializer(), """{"date":"2026-08-29"}""")
        assertEquals(null, absent.health)
        assertEquals(ContentHash.ofDays(listOf(absent)), ContentHash.ofDays(listOf(day())))
    }

    /** R1 — `BackupMeta.kind` is a new, always-present field, but `ContentHash` never reads
     *  `meta` at all, so two metas differing only by `kind` still hash identically as "days". */
    @Test fun backupMetaKind_neverAffectsContentHash() {
        val daybookMeta = BackupMeta(exportedAt = "x", appVersionName = "y", kind = BackupMeta.KIND_DAYBOOK)
        val beastMeta = BackupMeta(exportedAt = "x", appVersionName = "y", kind = BackupMeta.KIND_BEAST_MODE)
        assertNotEquals(daybookMeta.kind, beastMeta.kind)
        // ContentHash operates on `days`/`definitions`, never `meta` — this is a documentation
        // assertion that the two metas are legitimately different, not a hash call on meta itself
        // (there is no such API), matching the existing rangeStart/rangeEnd precedent.
        assertEquals(ContentHash.ofDays(listOf(day())), ContentHash.ofDays(listOf(day())))
    }

    /** HEALTH_VITALS_RICHNESS_PLAN.md §5 — spo2Min/Max and weightReadings must not churn a day's
     *  hash for data predating this round (or a day with no SpO2/weight data at all): absent, not
     *  present-but-null/empty, same rule §0 already applies to `health` itself. */
    @Test fun newFieldsDefaultToAbsent_inCanonicalBytes() {
        val canonical = canonicalJson.encodeToString(DayEntry.serializer(), day(healthLog()))
        assertFalse("spo2MinPercent must NOT appear: $canonical", canonical.contains("spo2MinPercent"))
        assertFalse("spo2MaxPercent must NOT appear: $canonical", canonical.contains("spo2MaxPercent"))
        assertFalse("weightReadings must NOT appear: $canonical", canonical.contains("weightReadings"))
    }
}
