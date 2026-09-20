package com.daybook.app.data.health

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure `clusterSleepSessions` — the dedup decision extracted out of
 *  `HealthConnectReader.sleepForDay` so it's testable without a real `HealthConnectClient`. */
class SleepSessionMergeTest {

    private val HOUR = 3_600_000L
    private val MIN = 60_000L
    private val MI_FITNESS = "com.xiaomi.wearable"
    private val OTHER_APP = "com.other.tracker"

    private fun span(index: Int, startMillis: Long, endMillis: Long, lastModifiedMillis: Long, source: String? = MI_FITNESS) =
        SleepSessionSpan(index, startMillis, endMillis, lastModifiedMillis, source)

    @Test
    fun singleSession_isReturnedUnchanged() {
        val s = span(0, 0, 8 * HOUR, 100)
        assertEquals(listOf(s), clusterSleepSessions(listOf(s)))
    }

    @Test
    fun overlappingSessions_keepOnlyMostRecentlyModified() {
        // 11:01pm-2:02am (an in-progress sync) and 11:01pm-6:36am (the final one) — same start,
        // genuinely overlapping, the final sync is the more recently modified.
        val partial = span(0, 0, 3 * HOUR + MIN, lastModifiedMillis = 100)
        val whole = span(1, 0, 7 * HOUR + 35 * MIN, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(partial, whole))
        assertEquals(listOf(whole), result)
    }

    @Test
    fun backToBackContinuation_sameSource_zeroGap_keepsBothAndSums() {
        // HC-1 fix (AUDIT_2026-09-16.md) — 11:01pm-2:02am then a later sync adds 2:02am-6:36am for
        // the same night: NOT overlapping, zero gap, same source app. This used to be wrongly
        // collapsed to whichever half `maxBy` picked (losing the other half's sleep time); both
        // halves are real, disjoint sleep time and must both survive. 3h01m + 4h34m = 7h35m, which
        // is what `HealthConnectReader.sleepForDay` computes by summing every kept session.
        val first = span(0, 0, 3 * HOUR + MIN, lastModifiedMillis = 100)
        val second = span(1, 3 * HOUR + MIN, 7 * HOUR + 35 * MIN, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(first, second))
        assertEquals(setOf(first, second), result.toSet())
        val totalMinutes = result.sumOf { (it.endMillis - it.startMillis) / MIN }
        assertEquals(7 * 60 + 35, totalMinutes)
    }

    @Test
    fun backToBackContinuation_sameSource_smallGap_keepsBoth() {
        // Same shape as above but with a few minutes' gap rather than exactly zero — still not
        // overlapping, so both must still survive (HC-1 fix removed the old "small gap" merge rule
        // entirely; only literal time-range overlap ever collapses two sessions now).
        val first = span(0, 0, 3 * HOUR, lastModifiedMillis = 100)
        val second = span(1, 3 * HOUR + 5 * MIN, 7 * HOUR + 30 * MIN, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(first, second))
        assertEquals(setOf(first, second), result.toSet())
    }

    @Test
    fun reSync_shiftedStart_thatStillOverlaps_dedupsToTheFresherRecord() {
        // A genuine duplicate re-post: the corrected/final record (11:03pm-6:36am) starts a couple
        // of minutes before the partial one (11:01pm-2:02am) ends, i.e. it literally overlaps —
        // this is the true "same episode, corrected boundary" case, which must still dedup to the
        // single freshest record's own (unmodified) boundaries, never sum.
        val partial = span(0, 0, 3 * HOUR + MIN, lastModifiedMillis = 100)
        val corrected = span(1, 3 * HOUR - 2 * MIN, 7 * HOUR + 33 * MIN, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(partial, corrected))
        assertEquals(listOf(corrected), result)
    }

    @Test
    fun largeGap_sameSource_isTreatedAsTwoEpisodes_notMerged() {
        // A real nap (2pm-3pm) hours before the main sleep (11pm-7am), same source app — the gap
        // (8 hours) is far beyond the re-sync tolerance, so both must survive as separate episodes.
        val nap = span(0, 14 * HOUR, 15 * HOUR, lastModifiedMillis = 100)
        val mainSleep = span(1, 23 * HOUR, 31 * HOUR, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(nap, mainSleep))
        assertEquals(setOf(nap, mainSleep), result.toSet())
    }

    @Test
    fun smallGap_differentSource_isNeverMerged() {
        // Two different tracker apps logging back-to-back sessions with a tiny gap — never
        // overlapping, so never merged, regardless of source (source no longer factors into the
        // decision at all post-HC-1 — only literal overlap does).
        val a = span(0, 0, 3 * HOUR, lastModifiedMillis = 100, source = MI_FITNESS)
        val b = span(1, 3 * HOUR + MIN, 7 * HOUR, lastModifiedMillis = 200, source = OTHER_APP)
        val result = clusterSleepSessions(listOf(a, b))
        assertEquals(setOf(a, b), result.toSet())
    }

    @Test
    fun gapSize_neverAffectsMerging_onlyLiteralOverlapDoes() {
        // Post-HC-1 there is no gap tolerance any more: a hairline non-overlapping gap and a large
        // one behave identically (both kept), and the only thing that ever collapses two sessions
        // is an actual time-range overlap (covered by the dedup tests above).
        val first = span(0, 0, HOUR, lastModifiedMillis = 100)
        val hairlineGap = span(1, HOUR + MIN, 2 * HOUR, lastModifiedMillis = 200)
        assertEquals(setOf(first, hairlineGap), clusterSleepSessions(listOf(first, hairlineGap)).toSet())

        val largeGap = span(1, 10 * HOUR, 11 * HOUR, lastModifiedMillis = 200)
        assertEquals(setOf(first, largeGap), clusterSleepSessions(listOf(first, largeGap)).toSet())
    }

    @Test
    fun threeOverlappingSessions_keepOnlyTheLatestOfAll() {
        val a = span(0, 0, 2 * HOUR, lastModifiedMillis = 100)
        val b = span(1, HOUR, 5 * HOUR, lastModifiedMillis = 300)
        val c = span(2, 4 * HOUR, 6 * HOUR, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(a, b, c))
        assertEquals(listOf(b), result)
    }

    @Test
    fun emptyList_isReturnedUnchanged() {
        assertEquals(emptyList<SleepSessionSpan>(), clusterSleepSessions(emptyList()))
    }
}
