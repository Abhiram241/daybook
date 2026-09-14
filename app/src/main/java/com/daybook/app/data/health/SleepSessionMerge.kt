package com.daybook.app.data.health

/**
 * Pure, unit-tested (`SleepSessionMergeTest`) core of `HealthConnectReader.sleepForDay`'s dedup
 * decision — extracted so it's testable without a real `HealthConnectClient`.
 *
 * User bug report — a band app can duplicate one night's sleep beyond simple time-range overlap:
 * a re-sync sometimes posts what's meant to be a CONTINUATION as a fresh, non-overlapping session
 * right after the earlier one ends (e.g. 11:01pm–2:02am, then a second sync adds 2:02am–6:36am for
 * the same night), or with a slightly shifted start (11:03pm–6:36am). The original fix only merged
 * sessions whose time ranges literally intersected, which missed this "back-to-back" pattern
 * entirely — it still got summed as if it were two real episodes (a nap + the main sleep).
 */
internal data class SleepSessionSpan(
    val index: Int,
    val startMillis: Long,
    val endMillis: Long,
    val lastModifiedMillis: Long,
    val sourcePackage: String?
)

/**
 * Clusters raw sleep sessions for one night into distinct sleep EPISODES: two sessions are the
 * SAME episode when either
 *  - their time ranges overlap (a re-sync that shortened or lengthened the boundary while the two
 *    spans still intersect — the original rule), or
 *  - they come from the SAME source app and the gap between one ending and the next starting is at
 *    most [maxGapMinutes] (default 30) — the "back-to-back re-sync" pattern above.
 *
 * Deliberately conservative in both directions:
 *  - two sessions from DIFFERENT source apps are NEVER merged, even with a zero gap — a genuine nap
 *    logged by one tracker right before the main sleep starts on another is far more plausible than
 *    two different apps racing to log the exact same episode;
 *  - there's no cap on how many distinct episodes a night can have — a real nap hours before bedtime
 *    sits well outside [maxGapMinutes] of the main sleep and is correctly left as its own episode.
 *
 * Within one merged cluster, only the single most-recently-modified session survives (the same
 * freshness rule the original overlap-only dedup used) — never a sum of all of them. A one- or
 * zero-element input is returned unchanged.
 */
internal fun clusterSleepSessions(sessions: List<SleepSessionSpan>, maxGapMinutes: Long = 30): List<SleepSessionSpan> {
    if (sessions.size <= 1) return sessions
    val sorted = sessions.sortedBy { it.startMillis }
    val clusters = mutableListOf<MutableList<SleepSessionSpan>>()
    val maxGapMillis = maxGapMinutes * 60_000L
    for (s in sorted) {
        val cluster = clusters.lastOrNull()
        val clusterEnd = cluster?.maxOf { it.endMillis }
        val clusterSource = cluster?.last()?.sourcePackage
        val overlaps = clusterEnd != null && s.startMillis < clusterEnd
        val adjacentSameSource = clusterEnd != null && clusterSource == s.sourcePackage &&
            (s.startMillis - clusterEnd) in 0..maxGapMillis
        if (cluster != null && (overlaps || adjacentSameSource)) {
            cluster += s
        } else {
            clusters += mutableListOf(s)
        }
    }
    return clusters.map { cluster -> cluster.maxBy { it.lastModifiedMillis } }
}
