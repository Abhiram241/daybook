package com.daybook.app.data.workout

import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutRoutine
import com.daybook.app.data.model.WorkoutRoutineExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.util.DateTimeUtils
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * A3 (§3.4) — every pure decision function behind the workout feature, Room-free and directly
 * unit-testable. Nothing in this file touches a DAO or a transaction; `WorkoutRepository` is the
 * only impure caller.
 */

/** Display-only — storage is always kg (§3.8.2). */
enum class WeightUnit { KG, LB }

/** kg -> lb, rounded to the nearest 0.5 lb (§3.8.2). */
fun kgToLb(kg: Float): Float = (Math.round(kg * 2.2046226f * 2f) / 2f)

/** The set-table's column set for one exercise block (§3.7.1). Order is display order. */
enum class SetColumn { SET, PREVIOUS, WEIGHT, REPS, DURATION, DISTANCE, COMPLETE }

/**
 * Ri2: this is the ONLY function outside §3.4's mapping functions that may branch on
 * `trackingMode`. One choke point means one function to replace if Round C ever generalises this.
 */
fun columnsFor(trackingMode: String): List<SetColumn> = when (trackingMode) {
    "WEIGHT_REPS" -> listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.WEIGHT, SetColumn.REPS, SetColumn.COMPLETE)
    "REPS_ONLY" -> listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.REPS, SetColumn.COMPLETE)
    "DURATION" -> listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.DURATION, SetColumn.COMPLETE)
    "DISTANCE_DURATION" -> listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.DISTANCE, SetColumn.DURATION, SetColumn.COMPLETE)
    else -> listOf(SetColumn.SET, SetColumn.PREVIOUS, SetColumn.REPS, SetColumn.COMPLETE)
}

/**
 * §3.4 — `WorkoutDao.previousSetsForExercise` returns every set of the most recent PRIOR
 * completed session (ordered by session recency, newest first). This keeps only the rows
 * belonging to that newest session and indexes them by `setNumber` — what makes PREVIOUS
 * per-set rather than per-exercise. Set 3 with no set 3 last time is simply absent from the map.
 */
fun previousBySetNumber(rows: List<WorkoutSet>): Map<Int, WorkoutSet> {
    val newestSessionId = rows.firstOrNull()?.sessionId ?: return emptyMap()
    return rows.filter { it.sessionId == newestSessionId }.associateBy { it.setNumber }
}

/**
 * §3.4 — heavier weight (equal weight with strictly more reps also counts) for WEIGHT_REPS, more
 * reps for REPS_ONLY, longer for DURATION, further for DISTANCE_DURATION. No history at all
 * (`best == null`) is NEVER a PR — marking every first-ever set with a medal makes the medal
 * meaningless.
 */
fun isPersonalRecord(candidate: WorkoutSet, best: WorkoutSet?, trackingMode: String): Boolean {
    if (best == null) return false
    return when (trackingMode) {
        "WEIGHT_REPS" -> {
            val cw = candidate.weightKg ?: 0f
            val bw = best.weightKg ?: 0f
            val cr = candidate.reps ?: 0
            val br = best.reps ?: 0
            cw > bw || (cw == bw && cr > br)
        }
        "REPS_ONLY" -> (candidate.reps ?: 0) > (best.reps ?: 0)
        "DURATION" -> (candidate.durationSeconds ?: 0) > (best.durationSeconds ?: 0)
        "DISTANCE_DURATION" -> (candidate.distanceMeters ?: 0f) > (best.distanceMeters ?: 0f)
        else -> false
    }
}

/** The live session header's Duration / Volume / Sets (§3.4). */
data class SessionStats(val totalVolumeKg: Float, val setCount: Int)

/** Beast Mode home "This week" stat grid (BEAST_MODE_REDESIGN_PLAN.md §3.3 item 3, §4.1). */
data class WeeklyStats(val workouts: Int, val volumeKg: Float, val streakDays: Int, val prs: Int)

/**
 * Consecutive-day training streak, walking backward from [today]. A day counts if its ISO date
 * string is in [completedDates]. Missing TODAY doesn't break the streak (you may not have trained
 * yet today) — the walk simply starts from yesterday in that case.
 */
fun currentStreakDays(completedDates: Set<String>, today: LocalDate): Int {
    var streak = 0
    var day = if (today.toString() in completedDates) today else today.minusDays(1)
    while (day.toString() in completedDates) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/**
 * Volume is Σ(weightKg × reps) over COMPLETED sets only (warm-ups included — Hevy counts them); a
 * bodyweight or duration set contributes 0 rather than being skipped, so the set count and volume
 * never disagree about which rows they looked at (§3.4, P3-bounded: fold over this session only).
 */
fun sessionStats(sets: List<WorkoutSet>): SessionStats {
    val completed = sets.filter { it.completedAt != null }
    val volume = completed.fold(0f) { acc, s -> acc + (s.weightKg ?: 0f) * (s.reps ?: 0) }
    return SessionStats(totalVolumeKg = volume, setCount = completed.size)
}

/**
 * §3.4 — the same idiom `CustomCategoryRepository.normaliseCategory` uses for category names:
 * trim only, blank -> null. Exercise names are not PK-deduplicated (the PK is a UUID), so this is
 * purely "is there anything here", not a collision check.
 */
fun normaliseExerciseName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

/**
 * §3.7.3/§3.9.4 — "the same normaliser powers the Add-Exercise search field and Hevy exercise
 * matching — one implementation, one test." Lowercases, strips punctuation, collapses whitespace.
 */
fun normaliseForSearch(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

/**
 * §3.7.2 — shared elapsed-time formatter: `WorkoutSessionScreen`'s live ticker and the Beast Mode
 * home card's frozen "up for" snapshot both format the same `startedAt`-derived seconds the same
 * way, so moved here rather than duplicated (was `private` inside `WorkoutSessionScreen.kt`).
 */
fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Feature addition (post-A6) — the Add-Exercise picker's muscle-group filter chip.
 * `selected == null` is the "All" chip: everything matches.
 */
fun matchesGroupFilter(exerciseGroup: MuscleGroup, selected: MuscleGroup?): Boolean =
    selected == null || exerciseGroup == selected

/**
 * Round 2 — the Add-Exercise search box's matching rule, replacing a single contiguous
 * `contains` check that had two gaps: word order ("bike air" didn't find "Air Bike") and space
 * sensitivity ("situps" didn't find "Sit Ups"). Both [query] and [exerciseName] are raw (not yet
 * run through [normaliseForSearch]) — this function normalises them itself. Matches if EITHER:
 *  - every whitespace-separated token of the normalised query appears somewhere in the
 *    normalised exercise name, in any order/position; or
 *  - the exercise name and query, with ALL whitespace stripped (not just collapsed), have one
 *    containing the other as a substring.
 * A blank query always matches (mirrors the old behaviour, where `needle.isBlank()` short-circuited
 * to true). Not fuzzy/typo-tolerant — just fixes order and spacing.
 */
fun matchesExerciseSearch(
    query: String,
    exerciseName: String,
    muscleLabel: String? = null,
    equipmentLabel: String? = null
): Boolean = exerciseSearchRank(query, exerciseName, muscleLabel, equipmentLabel) != null

/**
 * Round 3 (search enhancements, all three LOCKED items) — replaces the plain boolean match with a
 * ranked one so the Add-Exercise picker can sort by match quality instead of catalog order:
 *  - [RANK_EXACT_PREFIX]: the normalised name equals or starts with the normalised query;
 *  - [RANK_TOKEN]: the original Round 2 rule (word-order-independent token containment, or the
 *    space-stripped substring check either direction);
 *  - [RANK_MUSCLE_EQUIPMENT]: the query appears in the exercise's muscle-group or equipment label
 *    (new — "chest" or "dumbbell" now surfaces relevant exercises even when those words aren't in
 *    the name);
 *  - [RANK_FUZZY]: small edit-distance typo tolerance, per query token, capped to short tokens only
 *    (new — "curll" still finds "Curl").
 * Returns `null` for no match at all; a blank query always ranks [RANK_EXACT_PREFIX] for everyone
 * (mirrors the old "blank matches everything" behaviour, and keeps catalog order stable since every
 * row ties on the same rank).
 */
fun exerciseSearchRank(
    query: String,
    exerciseName: String,
    muscleLabel: String? = null,
    equipmentLabel: String? = null
): Int? {
    val normalisedQuery = normaliseForSearch(query)
    if (normalisedQuery.isBlank()) return RANK_EXACT_PREFIX
    val normalisedName = normaliseForSearch(exerciseName)

    if (normalisedName == normalisedQuery || normalisedName.startsWith(normalisedQuery)) {
        return RANK_EXACT_PREFIX
    }

    val tokens = normalisedQuery.split(" ").filter { it.isNotEmpty() }
    val tokensMatch = tokens.isNotEmpty() && tokens.all { normalisedName.contains(it) }

    val queryNoSpaces = normalisedQuery.replace(" ", "")
    val nameNoSpaces = normalisedName.replace(" ", "")
    val spacelessMatch = queryNoSpaces.isNotEmpty() &&
        (nameNoSpaces.contains(queryNoSpaces) || queryNoSpaces.contains(nameNoSpaces))

    if (tokensMatch || spacelessMatch) return RANK_TOKEN

    val muscleEquipmentMatch = listOfNotNull(muscleLabel, equipmentLabel).any {
        normaliseForSearch(it).contains(normalisedQuery)
    }
    if (muscleEquipmentMatch) return RANK_MUSCLE_EQUIPMENT

    val nameWords = normalisedName.split(" ").filter { it.isNotEmpty() }
    if (fuzzyTokensMatch(tokens, nameWords)) return RANK_FUZZY

    return null
}

const val RANK_EXACT_PREFIX = 0
const val RANK_TOKEN = 1
const val RANK_MUSCLE_EQUIPMENT = 2
const val RANK_FUZZY = 3

/** Every query token must fuzzy-match at least one word of the name within [maxEditDistanceFor]
 *  its own length's tolerance — "basic typo tolerance for short queries" (item 7b), not a general
 *  fuzzy search: a long/unrelated query still won't match (see [maxEditDistanceFor]). */
private fun fuzzyTokensMatch(queryTokens: List<String>, nameWords: List<String>): Boolean =
    queryTokens.isNotEmpty() && nameWords.isNotEmpty() && queryTokens.all { qt ->
        nameWords.any { nw -> levenshtein(qt, nw) <= maxEditDistanceFor(qt.length) }
    }

/** Short tokens tolerate a 1-character edit, medium ones 2; anything longer than 8 chars gets no
 *  fuzzy tolerance at all — "basic … for short queries" only. */
private fun maxEditDistanceFor(len: Int): Int = when {
    len <= 4 -> 1
    len <= 8 -> 2
    else -> 0
}

/** Plain iterative Levenshtein (insert/delete/substitute), O(n·m), no external dependency. */
internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        for (j in 0..b.length) prev[j] = curr[j]
    }
    return prev[b.length]
}

/**
 * Item 6 (Workout UI fixes plan, LOCKED) — the instant/no-routine "Start an empty workout"
 * session's default title, purely a function of the local hour-of-day (0-23) at creation time.
 * Room-free and directly unit-testable; `WorkoutRepository.startEmptySession` is the only caller,
 * passing `LocalTime.now().hour`. Stays user-renameable afterwards via the existing rename
 * feature (`WorkoutRepository.renameSession`) — this only sets the initial value.
 */
fun instantSessionTitle(hourOfDay: Int): String = when (hourOfDay) {
    in 5..11 -> "Morning Workout"
    in 12..16 -> "Afternoon Workout"
    in 17..20 -> "Evening Workout"
    else -> "Night Workout" // 21:00-23:59 and 00:00-04:59
}

/**
 * §3.4 — trim, collapse inner whitespace, case-insensitive compare against [existing], and on a
 * clash append " 2", " 3", ... A routine named identically to an existing one becomes "Push day
 * 2"; the third clash becomes "Push day 3".
 */
fun uniqueRoutineName(desired: String, existing: List<String>): String {
    val base = desired.trim().replace(Regex("\\s+"), " ")
    val existingNormalised = existing.map { it.trim().replace(Regex("\\s+"), " ").lowercase() }.toSet()
    if (base.lowercase() !in existingNormalised) return base
    var suffix = 2
    while ("$base $suffix".lowercase() in existingNormalised) suffix++
    return "$base $suffix"
}

/**
 * §3.7.5 — the routine editor's per-exercise targets summary line. `kg`/`lb` follows
 * `app_settings.weight_unit`, display-only (storage stays kg). Drops any clause whose value is
 * null; "No targets" when everything is null.
 */
fun targetSummary(
    targetSets: Int?,
    targetReps: Int?,
    targetWeightKg: Float?,
    targetDurationSeconds: Int?,
    targetDistanceMeters: Float?,
    trackingMode: String,
    unit: WeightUnit
): String {
    if (targetSets == null && targetReps == null && targetWeightKg == null &&
        targetDurationSeconds == null && targetDistanceMeters == null
    ) return "No targets"

    val setsPart = targetSets?.let { "$it" }
    return when (trackingMode) {
        "WEIGHT_REPS" -> {
            val repsAndWeight = buildString {
                if (targetReps != null) append("${formatCount(setsPart)}× $targetReps")
                else if (setsPart != null) append("$setsPart sets")
                if (targetWeightKg != null) {
                    if (isNotEmpty()) append(" · ")
                    append(formatWeight(targetWeightKg, unit))
                }
            }
            repsAndWeight.ifBlank { "No targets" }
        }
        "REPS_ONLY" -> when {
            targetReps != null -> "${formatCount(setsPart)}× $targetReps"
            setsPart != null -> "$setsPart sets"
            else -> "No targets"
        }
        "DURATION" -> when {
            targetDurationSeconds != null -> "${formatCount(setsPart)}× ${targetDurationSeconds}s"
            setsPart != null -> "$setsPart sets"
            else -> "No targets"
        }
        "DISTANCE_DURATION" -> when {
            targetDistanceMeters != null -> "${formatCount(setsPart)}× ${formatDistanceKm(targetDistanceMeters)}"
            targetDurationSeconds != null -> "${formatCount(setsPart)}× ${targetDurationSeconds}s"
            setsPart != null -> "$setsPart sets"
            else -> "No targets"
        }
        else -> setsPart?.let { "$it sets" } ?: "No targets"
    }
}

private fun formatCount(setsPart: String?): String = setsPart ?: "?"

private fun formatDistanceKm(meters: Float): String {
    val km = meters / 1000f
    val rounded = Math.round(km * 10f) / 10f
    return "${if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()} km"
}

/** Public (was `private` to this file) — §2.7/§3 reuse this for every other per-set/per-value
 *  weight display (`WorkoutDetailScreen.formatDetailSetLine`, `ExerciseHistorySheet.formatSetLine`)
 *  instead of each hardcoding "kg" or duplicating its own trim-trailing-zero helper. */
fun formatWeight(kg: Float, unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> "${trimTrailingZero(kg)} kg"
    WeightUnit.LB -> "${trimTrailingZero(kgToLb(kg))} lb"
}

fun trimTrailingZero(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()

/**
 * §3.8.2 — the same string -> [WeightUnit] mapping every settings read needs; anything
 * unrecognised (a stale/corrupt row) falls back to KG rather than crashing.
 */
fun parseWeightUnit(raw: String): WeightUnit = WeightUnit.entries.firstOrNull { it.name == raw } ?: WeightUnit.KG

/**
 * History redesign (§2.1/§2.4) and the Detail screen's own Volume stat — bug fix, both used to
 * hardcode kg regardless of `app_settings.weight_unit` (open question 2, now resolved: fix both).
 * Grouped with a thousands separator (e.g. "4,200 kg") since a session or a whole history's
 * volume commonly runs into 4+ digits, unlike a single set's weight.
 */
fun formatVolume(kg: Float, unit: WeightUnit): String {
    val displayValue = if (unit == WeightUnit.LB) kgToLb(kg) else kg
    val rounded = Math.round(displayValue)
    val grouped = String.format(Locale.US, "%,d", rounded)
    return "$grouped ${if (unit == WeightUnit.LB) "lb" else "kg"}"
}

/**
 * History redesign §2.2 — buckets an already newest-first list into "This week" / "Last week" /
 * "[Month name]" (year-suffixed only when not the current year) sections, in the same order the
 * items arrived in. Pure grouping, no re-sort: a caller that hands in a newest-first list gets
 * newest-first sections and newest-first rows within each section. [today]/[weekStart] are passed
 * in rather than read here so this stays a plain, directly-testable function (no `LocalDate.now()`
 * inside a "pure" helper). `weekStart` reuses `DateTimeUtils.startOfWeek` — the same device-local
 * week-boundary logic the streak/week-strip code already uses, no new date primitive.
 */
fun <T> bucketByHistorySection(
    items: List<T>,
    localDateOf: (T) -> String,
    today: LocalDate,
    weekStart: String
): List<Pair<String, List<T>>> {
    val thisWeekStart = DateTimeUtils.startOfWeek(today, weekStart)
    val lastWeekStart = thisWeekStart.minusWeeks(1)

    fun labelFor(rawDate: String): String {
        val date = runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: return "Earlier"
        return when {
            !date.isBefore(thisWeekStart) -> "This week"
            !date.isBefore(lastWeekStart) -> "Last week"
            else -> {
                val monthName = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                if (date.year == today.year) monthName else "$monthName ${date.year}"
            }
        }
    }

    val ordered = LinkedHashMap<String, MutableList<T>>()
    items.forEach { item ->
        ordered.getOrPut(labelFor(localDateOf(item))) { mutableListOf() }.add(item)
    }
    return ordered.map { it.key to it.value }
}

// ------------------------------------------------------------------ starting a session from a routine

/** The three built rows a `startSessionFromRoutine` call produces (§3.4), before persistence. */
data class RoutineStartResult(
    val session: WorkoutSession,
    val exercises: List<WorkoutExercise>,
    val sets: List<WorkoutSet>
)

/**
 * Precedence, stated once (§3.4): routine target -> last time's block value -> the app default ->
 * OFF. `appDefaultRestSeconds == 0` means OFF, so it resolves to `null`, never `0`.
 */
fun resolveRestSeconds(routineTarget: Int?, lastBlockValue: Int?, appDefaultRestSeconds: Int): Int? =
    routineTarget ?: lastBlockValue ?: appDefaultRestSeconds.takeIf { it > 0 }

/**
 * §3.4 — the whole of "pre-populate a session from a routine", as a pure builder.
 * `WorkoutRepository.startSessionFromRoutine` reads [lastRestSecondsByExercise] and the app
 * default from Room/AppSettings, calls this, then persists the three lists in one
 * `withTransaction`. `targetSets == null` produces a block with ZERO set rows — a target of "no
 * opinion", not "0 sets" (Ri3).
 */
fun buildSessionFromRoutine(
    routine: WorkoutRoutine,
    routineExercises: List<WorkoutRoutineExercise>,
    lastRestSecondsByExercise: Map<String, Int?>,
    appDefaultRestSeconds: Int,
    now: Long,
    today: String,
    sessionId: String,
    blockIdAt: (index: Int) -> String,
    setIdAt: (blockIndex: Int, setNumber: Int) -> String
): RoutineStartResult {
    val session = WorkoutSession(
        id = sessionId,
        localDate = today,
        startedAt = now,
        endedAt = null,
        title = routine.name,
        notes = null,
        status = "ACTIVE",
        source = "MANUAL",
        routineId = routine.id,
        createdAt = now
    )
    val exercises = ArrayList<WorkoutExercise>(routineExercises.size)
    val sets = ArrayList<WorkoutSet>()
    routineExercises.sortedBy { it.orderIndex }.forEachIndexed { index, re ->
        val blockId = blockIdAt(index)
        val restSeconds = resolveRestSeconds(
            re.restSeconds, lastRestSecondsByExercise[re.exerciseId], appDefaultRestSeconds
        )
        exercises += WorkoutExercise(
            id = blockId,
            sessionId = sessionId,
            exerciseId = re.exerciseId,
            orderIndex = re.orderIndex,
            notes = re.notes,
            supersetId = null,
            restSeconds = restSeconds,
            createdAt = now
        )
        val targetSetCount = re.targetSets ?: 0
        for (setNumber in 1..targetSetCount) {
            sets += WorkoutSet(
                id = setIdAt(index, setNumber),
                workoutExerciseId = blockId,
                sessionId = sessionId,
                exerciseId = re.exerciseId,
                setNumber = setNumber,
                reps = re.targetReps,
                weightKg = re.targetWeightKg,
                durationSeconds = re.targetDurationSeconds,
                distanceMeters = re.targetDistanceMeters,
                rpe = null,
                setType = "NORMAL",
                notes = null,
                completedAt = null
            )
        }
    }
    return RoutineStartResult(session, exercises, sets)
}
