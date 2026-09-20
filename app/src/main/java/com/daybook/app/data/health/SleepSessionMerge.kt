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
 * Clusters raw sleep sessions for one night, collapsing only genuine DUPLICATES down to a single
 * survivor. Two sessions are the same duplicated episode exactly when their time ranges literally
 * overlap (a re-sync that reposted the same episode, shortened, lengthened, or with a corrected
 * boundary that still intersects the earlier record) — clustering is transitive, so a chain of
 * pairwise-overlapping sessions collapses to one cluster even if the first and last don't directly
 * overlap each other. Within such a cluster, only the single most-recently-modified session
 * survives, using **that session's own, unmodified** start/end — never a synthesized union.
 *
 * HC-1 fix (AUDIT_2026-09-16.md) — a session that does NOT overlap any other is always kept, full
 * stop, regardless of source app or how small the gap to its neighbour is. This is deliberate:
 * a back-to-back CONTINUATION — e.g. 11:01pm–2:02am, then a second sync adds 2:02am–6:36am for the
 * same night, same source app, zero gap — used to be folded into the overlapping session's cluster
 * by an "adjacent same source" rule and wrongly reduced to whichever single half `maxBy` picked
 * (losing the other half's sleep time entirely). Both halves are real, disjoint sleep time; the
 * caller ([HealthConnectReader.sleepForDay]) already sums stage minutes and spans min(start)/
 * max(end) across every session this function keeps, so simply keeping both is the whole fix —
 * nothing needs to merge or sum here. A real nap hours before the main sleep, from the same or a
 * different source app, was already (correctly) kept as its own episode before this fix and still
 * is: it never overlapped anything to begin with.
 *
 * A one- or zero-element input is returned unchanged.
 */
internal fun clusterSleepSessions(sessions: List<SleepSessionSpan>): List<SleepSessionSpan> {
    if (sessions.size <= 1) return sessions
    val sorted = sessions.sortedBy { it.startMillis }
    val clusters = mutableListOf<MutableList<SleepSessionSpan>>()
    for (s in sorted) {
        val cluster = clusters.lastOrNull()
        val clusterEnd = cluster?.maxOf { it.endMillis }
        val overlaps = clusterEnd != null && s.startMillis < clusterEnd
        if (cluster != null && overlaps) {
            cluster += s
        } else {
            clusters += mutableListOf(s)
        }
    }
    return clusters.map { cluster -> cluster.maxBy { it.lastModifiedMillis } }
}
