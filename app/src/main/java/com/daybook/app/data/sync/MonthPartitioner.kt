package com.daybook.app.data.sync

import com.daybook.app.data.backup.DayEntry
import com.daybook.app.data.backup.Definitions
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Splits a v2 backup's `days` array into one bucket per **local** calendar month (v0.5.1 §N,
 * decision 3), and diffs those buckets' hashes against what the cloud already holds.
 *
 * Pure JVM — no Firestore, no Room, no Android — so `MonthPartitionerTest` / `MonthHashDiffTest`
 * need no mocks, the same shape as `ContentHash` / `PayloadCodec`.
 *
 * There is deliberately **no timezone maths here.** `DayEntry.date` is already a local
 * `"yyyy-MM-dd"` string, produced from `DateTimeUtils.timestampToLocalDate` at export time, so the
 * month key is a string property of the day the user actually lived. That is the whole of
 * decision 3's guarantee.
 */
object MonthPartitioner {

    private val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    /** Exactly `yyyy-MM-dd`, zero-padded — which is all `ExportImportRepository` ever writes. */
    private val STRICT_YMD = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** `"2026-08-30"` -> `"2026-08"`. Null for anything that is not a valid ISO local date. */
    fun monthKeyOf(isoDate: String): String? {
        val trimmed = isoDate.trim()
        // Both checks are load-bearing. The regex rejects "2026-1-5", which `DateTimeFormatter`
        // parses happily but whose take(7) is the nonsense key "2026-1-"; the parse then rejects
        // shapes that are well-formed but not real dates ("2026-13-01", "2026-02-30").
        if (!STRICT_YMD.matches(trimmed)) return null
        runCatching { LocalDate.parse(trimmed, YMD) }.getOrNull() ?: return null
        return trimmed.take(7)
    }

    /** Groups by local month key, preserving each month's input (ascending-date) order. */
    fun partition(days: List<DayEntry>): Map<String, List<DayEntry>> {
        val out = LinkedHashMap<String, MutableList<DayEntry>>()
        for (day in days) {
            val key = monthKeyOf(day.date) ?: continue
            out.getOrPut(key) { mutableListOf() } += day
        }
        return out
    }

    /** Per-month content hash over the canonical JSON of that month's days. */
    fun hashes(partitioned: Map<String, List<DayEntry>>): Map<String, String> =
        partitioned.mapValues { (_, days) -> ContentHash.ofDays(days) }

    /**
     * Months whose cloud doc must be written or removed.
     *
     * - present in [current] with a different (or absent) hash in [known] -> needs a `set()`
     * - present in [known] but absent from [current] -> the month emptied locally, needs a `delete()`
     *
     * **Caller contract:** [known] must be pre-filtered to months that are actually *resident* on
     * this device. A month that was evicted (§N eviction: dropped locally because it is old and its
     * hash matches the cloud) is absent from [current] but is emphatically NOT a deletion — passing
     * it in would delete the user's cloud history. `CloudSyncRepository.doPush` does that filtering.
     */
    fun changedMonths(current: Map<String, String>, known: Map<String, String>): Set<String> {
        val out = LinkedHashSet<String>()
        for ((month, hash) in current) if (known[month] != hash) out += month
        for (month in known.keys) if (month !in current) out += month
        return out
    }

    /** The months kept hydrated locally: this month and the previous one (SD-3a). */
    fun recentMonths(now: YearMonth = YearMonth.now()): Set<String> =
        setOf(now.toString(), now.minusMonths(1).toString())

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 11 (S-8): Firestore's `whereIn` caps at 30 values.
     * `resident.toList().take(cap)` on a `Set` took whatever arbitrary iteration order the `Set`
     * happened to produce — able to silently drop a RECENT month while keeping a stale one live in
     * `CloudSyncRepository.scopedMonthsListener`'s query. Month keys are `"yyyy-MM"` strings, so a
     * lexicographic descending sort is chronological descending; taking the first [cap] of that
     * guarantees any months dropped are the oldest ones. Pure — see `MonthPartitionerTest`.
     */
    fun cappedMostRecentMonths(resident: Set<String>, cap: Int = 30): List<String> =
        resident.toList().sortedByDescending { it }.take(cap)

    /**
     * v0.5.3 Phase 6 (D2): the ascending list of `"yyyy-MM"` keys from [startMonth] to [endMonth]
     * **inclusive** — the set of month docs a date-range export must hydrate before writing the
     * file. Empty when either key is malformed or `start` is after `end`. Pure — [MonthRangeTest].
     */
    fun monthKeysInRange(startMonth: String, endMonth: String): List<String> {
        val a = runCatching { YearMonth.parse(startMonth.trim()) }.getOrNull() ?: return emptyList()
        val b = runCatching { YearMonth.parse(endMonth.trim()) }.getOrNull() ?: return emptyList()
        if (a.isAfter(b)) return emptyList()
        val out = ArrayList<String>()
        var m = a
        while (!m.isAfter(b)) { out += m.toString(); m = m.plusMonths(1) }
        return out
    }

    /** Serialises one month's days for the cloud blob. */
    fun encodeDays(days: List<DayEntry>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DayEntry.serializer()), days)

    /**
     * Inverse of [encodeDays]. LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 8 (C-3, High): returns
     * `null` on decode failure — was `emptyList()`, which is indistinguishable from a genuinely
     * empty month and let a truncated/corrupt gzip blob silently wipe every local PENDING row in
     * that month (`CloudSyncRepository.applyRemoteMonth`'s merge treats "no incoming ids" as
     * "nothing pending survives") and then store the remote hash as if the merge had succeeded —
     * permanent, silent data loss with no retry path. Mirrors [decodeDefinitionsJson] exactly,
     * which already got this right.
     */
    fun decodeDays(text: String): List<DayEntry>? = runCatching {
        Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
            .decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(DayEntry.serializer()),
                text
            )
    }.onFailure {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-18): a corrupt remote month payload (C-3,
        // Phase 8) is exactly the kind of "silently" the whole audit set out to turn into a
        // dashboard entry.
        com.daybook.app.util.recordUnhandledException(it)
    }.getOrNull()

    /**
     * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 8 (C-3): true when a month that [decodeDays] resolved
     * to zero days is genuinely empty — its stored content hash matches
     * `ContentHash.ofDays(emptyList())`, or there is no stored hash at all (an older/never-hashed
     * doc, or a brand-new empty month) — rather than corrupt: a write truncated to a syntactically
     * valid but wrong bare `[]` would decode successfully yet carry the ORIGINAL (non-empty)
     * content's hash, which this catches. Pure — see `MonthPartitionerTest`.
     */
    fun isGenuinelyEmptyMonth(storedHash: String?): Boolean =
        storedHash == null || storedHash == ContentHash.ofDays(emptyList())

    /** Serialises the parent doc's definitions blob. */
    fun encodeDefinitionsJson(defs: Definitions): String =
        json.encodeToString(Definitions.serializer(), defs)

    /** Inverse of [encodeDefinitionsJson]; null when the blob is unreadable. */
    fun decodeDefinitionsJson(text: String): Definitions? = runCatching {
        Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
            .decodeFromString(Definitions.serializer(), text)
    }.getOrNull()

    /**
     * Local epoch-millis half-open range `[startOfMonth, startOfNextMonth)` for a `"yyyy-MM"` key,
     * in the device timezone. Null for a malformed key.
     */
    fun epochRangeOf(monthKey: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Pair<Long, Long>? {
        val ym = runCatching { YearMonth.parse(monthKey) }.getOrNull() ?: return null
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}
