package com.daybook.app.util.streak

import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.Occurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * rec 6 (S4) — how strictly a day must be handled to count toward a streak.
 *  - [STRICT]  (default, byte-identical to pre-round behaviour): every occurrence that day reached
 *    its done terminal state (COMPLETED / LOGGED).
 *  - [LENIENT]: a day counts when every occurrence is done OR deliberately `SKIPPED`.
 */
enum class StreakMode { STRICT, LENIENT }

/** rec 6 (S1) — parse a CSV of `java.time.DayOfWeek` names (case-insensitive) into a set. */
fun parseRestDays(csv: String?): Set<DayOfWeek> =
    csv.orEmpty().split(",").mapNotNull { tok ->
        runCatching { DayOfWeek.valueOf(tok.trim().uppercase()) }.getOrNull()
    }.toSet()

/**
 * Streak maths for habits and food/med logs. Pure functions — no state, no DI (was a
 * `@Singleton` class whose every method forwarded to a companion and needed `@JvmName`
 * hacks for the two `List` overloads; REV-36).
 */

data class StreakResult(val currentStreak: Int, val longestStreak: Int)

private val zone: ZoneId get() = ZoneId.systemDefault()

/** Local calendar date a scheduled instant falls on (device timezone, e.g. IST). */
private fun localDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/**
 * @param asOf the day the "current" streak is measured from. Defaults to today, which is what
 *   [com.daybook.app.ui.detail.DetailViewModel]'s call sites want. The Today screen passes its
 *   selected date instead (v0.5.1 §I / SD-2 option B) so the flame reflects the day on screen
 *   rather than a single global number that stayed lit beside a past day's 0% ring.
 */
fun calculateHabitStreaks(
    occurrences: List<HabitOccurrence>,
    asOf: LocalDate = LocalDate.now(),
    mode: StreakMode = StreakMode.STRICT,
    restDays: Set<DayOfWeek> = emptySet()
): StreakResult =
    computeStreaks(
        fullyCompletedDates(
            occurrences.map { it.scheduledFor to daySatisfies(it.status, Occurrence.Status.COMPLETED, mode) }
        ),
        asOf, restDays
    )

/** See [calculateHabitStreaks] — same contract, food/med side. */
fun calculateFoodMedStreaks(
    occurrences: List<FoodMedOccurrence>,
    asOf: LocalDate = LocalDate.now(),
    mode: StreakMode = StreakMode.STRICT,
    restDays: Set<DayOfWeek> = emptySet()
): StreakResult =
    computeStreaks(
        fullyCompletedDates(
            occurrences.map { it.scheduledFor to daySatisfies(it.status, Occurrence.Status.LOGGED, mode) }
        ),
        asOf, restDays
    )

/**
 * v0.5.3 Phase 3 (A4): streaks from a `(scheduled_for, status)` projection rather than full
 * occurrence rows. The Detail screen pages the timeline but still needs the whole history for the
 * streak / completion numbers — this lets the stats fold read the lightweight projection.
 * [doneStatus] is `COMPLETED` for habits, `LOGGED` for intake.
 */
fun streaksFromScheduledStatuses(
    scheduledStatuses: List<Pair<Long, Occurrence.Status>>,
    doneStatus: Occurrence.Status,
    asOf: LocalDate = LocalDate.now(),
    mode: StreakMode = StreakMode.STRICT,
    restDays: Set<DayOfWeek> = emptySet()
): StreakResult =
    computeStreaks(
        fullyCompletedDates(scheduledStatuses.map { it.first to daySatisfies(it.second, doneStatus, mode) }),
        asOf, restDays
    )

/**
 * rec 6 (S4) — does this occurrence's status count for the day? STRICT requires the exact done
 * terminal state; LENIENT also accepts a deliberate `SKIPPED`. Pure — the whole point is that
 * `mode == STRICT` is byte-identical to the pre-round `status == doneStatus`.
 *
 * Journal-as-habit round: `LOGGED` also always satisfies, regardless of [doneStatus] — this is a
 * no-op for the FoodMed caller (its own [doneStatus] IS `LOGGED`) and additive for the habit caller
 * (`doneStatus == COMPLETED`), since no habit occurrence could ever be `LOGGED` before this round.
 * Without this, a Journal-type habit's answered day never counts toward `calculateHabitStreaks` /
 * `streaksFromScheduledStatuses`'s habit-side fold — a silent, crash-free 0-day streak bug.
 */
internal fun daySatisfies(status: Occurrence.Status, doneStatus: Occurrence.Status, mode: StreakMode): Boolean =
    status == doneStatus || status == Occurrence.Status.LOGGED ||
        (mode == StreakMode.LENIENT && status == Occurrence.Status.SKIPPED)

/**
 * A local date counts toward a streak **only when every occurrence scheduled that day satisfied
 * the rule** (see [daySatisfies]). One unsatisfied item disqualifies the whole day. A date with no
 * occurrence rows at all is simply absent (it breaks the run — unless it is a rest weekday, see
 * [computeStreaks]).
 *
 * @param scheduledAndDone (scheduledFor epoch millis, satisfies) for each occurrence considered.
 */
private fun fullyCompletedDates(scheduledAndDone: List<Pair<Long, Boolean>>): List<LocalDate> =
    scheduledAndDone
        .groupBy({ localDate(it.first) }, { it.second })
        .filterValues { doneFlags -> doneFlags.isNotEmpty() && doneFlags.all { it } }
        .keys
        .sorted()

private fun computeStreaks(
    completionDates: List<LocalDate>,
    asOf: LocalDate,
    restDays: Set<DayOfWeek> = emptySet()
): StreakResult {
    if (completionDates.isEmpty()) return StreakResult(0, 0)

    // A rest-day set covering every weekday is degenerate — treat it as "no rest days".
    val rest = if (restDays.size >= 7) emptySet() else restDays

    // v0.5.1 §I: was `LocalDate.now()`, hardcoded — there was no way to ask for the streak as of
    // another date. `longestStreak` below is unaffected by asOf; it scans the whole history.
    val today = asOf
    val dateSet = completionDates.toHashSet()
    // The current streak runs backwards from today. Today not being done yet must NOT
    // zero it — start from yesterday in that case; a completion today just extends it.
    var currentStreak = 0
    var checkDate = if (dateSet.contains(today)) today else today.minusDays(1)
    // rec 6 (S1): a rest weekday that is NOT in the set is neither required nor a break — the walk
    // steps over it without incrementing and without breaking. A guard caps the walk so an
    // all-rest-day tail with nothing done can't loop forever.
    var guard = 0
    while (guard++ < 4000) {
        if (dateSet.contains(checkDate)) {
            currentStreak++
            checkDate = checkDate.minusDays(1)
        } else if (checkDate.dayOfWeek in rest) {
            checkDate = checkDate.minusDays(1)
        } else break
    }

    var longestStreak = 0
    var currentRun = 1
    for (i in 1 until completionDates.size) {
        val prev = completionDates[i - 1]
        val gap = ChronoUnit.DAYS.between(prev, completionDates[i])
        when {
            gap == 1L -> currentRun++
            // rec 6 (S1): bridge a single rest-weekday gap only.
            gap == 2L && prev.plusDays(1).dayOfWeek in rest -> currentRun++
            else -> {
                longestStreak = maxOf(longestStreak, currentRun)
                currentRun = 1
            }
        }
    }
    longestStreak = maxOf(longestStreak, currentRun)

    return StreakResult(currentStreak, longestStreak)
}
