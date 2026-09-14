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
    fun backToBackReSync_sameSource_smallGap_mergesToLatest() {
        // 11:01pm-2:02am then a later sync adds 2:02am-6:36am for the same night — NOT overlapping,
        // zero gap, same source app: this is the pattern the original overlap-only fix missed.
        val first = span(0, 0, 3 * HOUR + MIN, lastModifiedMillis = 100)
        val second = span(1, 3 * HOUR + MIN, 7 * HOUR + 36 * MIN, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(first, second))
        assertEquals(listOf(second), result)
    }

    @Test
    fun backToBackReSync_slightlyShiftedStart_stillMerges() {
        // A re-sync with a slightly different start (11:03pm vs 11:01pm) and a couple of minutes'
        // gap from the earlier session's end — still well within the tolerance.
        val first = span(0, 0, 3 * HOUR, lastModifiedMillis = 100)
        val second = span(1, 3 * HOUR + 5 * MIN, 7 * HOUR + 30 * MIN, lastModifiedMillis = 200)
        val result = clusterSleepSessions(listOf(first, second))
        assertEquals(listOf(second), result)
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
        // Two different tracker apps logging back-to-back sessions with a tiny gap — conservative
        // rule: never merge across different source apps, regardless of how small the gap is.
        val a = span(0, 0, 3 * HOUR, lastModifiedMillis = 100, source = MI_FITNESS)
        val b = span(1, 3 * HOUR + MIN, 7 * HOUR, lastModifiedMillis = 200, source = OTHER_APP)
        val result = clusterSleepSessions(listOf(a, b))
        assertEquals(setOf(a, b), result.toSet())
    }

    @Test
    fun gapExactlyAtTolerance_merges_andJustBeyond_doesNot() {
        val first = span(0, 0, HOUR, lastModifiedMillis = 100)
        val atLimit = span(1, HOUR + 30 * MIN, 2 * HOUR, lastModifiedMillis = 200)
        assertEquals(listOf(atLimit), clusterSleepSessions(listOf(first, atLimit)))

        val beyondLimit = span(1, HOUR + 30 * MIN + MIN, 2 * HOUR, lastModifiedMillis = 200)
        assertEquals(setOf(first, beyondLimit), clusterSleepSessions(listOf(first, beyondLimit)).toSet())
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
