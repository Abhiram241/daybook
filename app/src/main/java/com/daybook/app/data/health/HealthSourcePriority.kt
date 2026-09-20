package com.daybook.app.data.health

import androidx.health.connect.client.records.metadata.DataOrigin

/**
 * [owner feedback, 2026-09-20] — resolves which Health Connect `DataOrigin`(s) an aggregate read
 * should be restricted to for one metric family, given the user's stored preference (a package name,
 * or null for "auto").
 *
 * - A non-null, non-blank preference restricts the read to exactly that source
 *   (`AggregateRequest.dataOriginFilter = setOf(DataOrigin(pref))`), so Health Connect's own
 *   `aggregate()` never combines it with any other app's records — this is what actually fixes the
 *   double-count, not a client-side re-computation after the fact (Health Connect does not expose a
 *   per-origin breakdown of an already-combined `AggregationResult`).
 * - "Auto" (null preference) is NOT "no filter" (the old, buggy default) — see [resolveAuto]. It
 *   still restricts to exactly one source, chosen deterministically, so the "wrong to add them" bug
 *   is fixed even for a user who never opens Settings. The `dataOrigins` a *previous* unrestricted
 *   read observed (already collected today via `activitySourceApps`/per-record `sourceApp`) is the
 *   candidate list `resolveAuto` picks from.
 */
object HealthSourcePriority {

    /** Package-name sort is deliberately simple/deterministic (not "most records" or "most recent")
     *  so the same install always picks the same source without needing a scan of historical data
     *  first — a user who cares which one wins can always override it in Settings. */
    fun resolveAuto(candidates: Collection<String>): String? = candidates.filter { it.isNotBlank() }.minOrNull()

    /** [preference] wins outright if set; otherwise [resolveAuto] over [observedCandidates]
     *  (e.g. the previous day's `activitySourceApps`/per-record sources, passed in by the caller —
     *  this object does no I/O of its own). Returns null when there is nothing to restrict to (no
     *  preference AND no candidates observed yet), in which case the caller should pass an empty
     *  `dataOriginFilter` (today's behavior — the very first read, before any source is known, must
     *  still return data). */
    fun resolve(preference: String?, observedCandidates: Collection<String>): String? =
        preference?.takeIf { it.isNotBlank() } ?: resolveAuto(observedCandidates)

    fun toDataOriginFilter(resolvedPackage: String?): Set<DataOrigin> =
        resolvedPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()

    /** Owner feedback round 3 (2026-09-20) — parses a comma-joined, rank-ordered package list (rank
     *  0 first), same CSV convention as `health_days.activity_source_apps`. */
    fun parseRanked(csv: String?): List<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    /**
     * Owner feedback round 3 (2026-09-20) — [ranked] is the user's ordered list of preferred packages
     * for one data point (rank 0 first). Falls back to [resolveAuto] over [observedCandidates] when
     * [ranked] is empty (pure Auto, same as today). Returns the ordered list of candidates a caller
     * should try IN ORDER, stopping at the first one that actually has data for the day in question —
     * this object does no I/O, so it cannot itself know which candidate "has data"; that per-day check
     * happens in HealthConnectReader.dayAggregate (see its KDoc).
     */
    fun candidateOrder(ranked: List<String>, observedCandidates: Collection<String>): List<String> {
        if (ranked.isNotEmpty()) return ranked
        val auto = resolveAuto(observedCandidates)
        return listOfNotNull(auto)
    }
}
