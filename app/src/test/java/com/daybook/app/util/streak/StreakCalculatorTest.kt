package com.daybook.app.util.streak

import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.model.Occurrence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

class StreakCalculatorTest {

    private val kolkata = TimeZone.getTimeZone("Asia/Kolkata") // UTC+5:30, no DST
    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(kolkata)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    /** Epoch millis for [time] on [date] in the (test) default zone. */
    private fun epochMillis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun completed(date: LocalDate, time: LocalTime = LocalTime.of(9, 0)) =
        HabitOccurrence(
            id = "$date-$time",
            habitId = "h1",
            scheduledFor = epochMillis(date, time),
            status = Occurrence.Status.COMPLETED,
            notificationId = 0
        )

    private fun withStatus(date: LocalDate, status: Occurrence.Status, time: LocalTime = LocalTime.of(9, 0)) =
        HabitOccurrence(
            id = "$date-$time",
            habitId = "h1",
            scheduledFor = epochMillis(date, time),
            status = status,
            notificationId = 0
        )

    @Test
    fun `early-morning completion counts for the local date, not the UTC date (REV-08)`() {
        // 02:00 IST today is still "yesterday" in UTC. Integer-dividing epoch millis by a day
        // (the old bug) would attribute this completion to the wrong calendar day.
        val today = LocalDate.now(ZoneId.systemDefault())
        val result = calculateHabitStreaks(listOf(completed(today, LocalTime.of(2, 0))))

        assertEquals("today's 02:00 completion should give a current streak of 1", 1, result.currentStreak)
    }

    @Test
    fun `current streak counts a run through yesterday when today is not done yet (REV-09)`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = listOf(
            completed(today.minusDays(1)),
            completed(today.minusDays(2)),
            completed(today.minusDays(3))
        )

        val result = calculateHabitStreaks(occ)

        assertEquals("a 3-day run ending yesterday is still the current streak", 3, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    @Test
    fun `today plus yesterday extends the streak`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val result = calculateHabitStreaks(
            listOf(completed(today), completed(today.minusDays(1)))
        )
        assertEquals(2, result.currentStreak)
    }

    @Test
    fun `a gap before yesterday breaks the current streak`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val result = calculateHabitStreaks(
            listOf(
                completed(today.minusDays(1)),
                // gap on day-2
                completed(today.minusDays(3)),
                completed(today.minusDays(4))
            )
        )
        assertEquals(1, result.currentStreak)
        assertEquals(2, result.longestStreak)
    }

    @Test
    fun `no completions gives zero`() {
        assertEquals(StreakResult(0, 0), calculateHabitStreaks(emptyList()))
    }

    // ---- v0.5.4: the flame requires EVERY occurrence on a day to be completed --------------

    @Test
    fun `a day with a still-pending occurrence does not count toward the streak`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = listOf(
            completed(today.minusDays(1), LocalTime.of(8, 0)),
            withStatus(today.minusDays(1), Occurrence.Status.PENDING, LocalTime.of(20, 0)),
            completed(today.minusDays(2)),
            completed(today.minusDays(3))
        )
        // yesterday is half-done -> the current run is only the two fully-done days before it
        assertEquals(0, calculateHabitStreaks(occ).currentStreak)
        assertEquals(2, calculateHabitStreaks(occ, today.minusDays(2)).currentStreak)
    }

    @Test
    fun `a day with a skipped occurrence is not a full day`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = listOf(
            completed(today.minusDays(1), LocalTime.of(8, 0)),
            withStatus(today.minusDays(1), Occurrence.Status.SKIPPED, LocalTime.of(20, 0)),
            completed(today.minusDays(2))
        )
        assertEquals("skipping one of the day's items breaks the run", 0, calculateHabitStreaks(occ).currentStreak)
    }

    @Test
    fun `a fully-completed run lights the flame`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = listOf(
            completed(today.minusDays(1), LocalTime.of(8, 0)),
            completed(today.minusDays(1), LocalTime.of(20, 0)),
            completed(today.minusDays(2), LocalTime.of(8, 0)),
            completed(today.minusDays(2), LocalTime.of(20, 0))
        )
        assertEquals(2, calculateHabitStreaks(occ).currentStreak)
    }

    // ---- v0.5.1 §I (SD-2 option B): the `asOf` parameter --------------------------------

    /** A 5-day run ending 10 days ago, plus one completion today so the *global* streak is 1. */
    private fun runOfFive(today: LocalDate) = listOf(
        completed(today.minusDays(10)),
        completed(today.minusDays(11)),
        completed(today.minusDays(12)),
        completed(today.minusDays(13)),
        completed(today.minusDays(14)),
        completed(today)
    )

    @Test
    fun `asOf inside a past run reports that run, not the global current streak`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = runOfFive(today)

        assertEquals("global current streak is just today", 1, calculateHabitStreaks(occ).currentStreak)
        assertEquals(
            "as of the last day of the old run, the streak is 5",
            5,
            calculateHabitStreaks(occ, today.minusDays(10)).currentStreak
        )
        assertEquals(
            "as of the middle of the old run, the streak is 3",
            3,
            calculateHabitStreaks(occ, today.minusDays(12)).currentStreak
        )
    }

    @Test
    fun `asOf on an empty day with nothing the day before is zero`() {
        // The exact case that produced the bug: a past day with no data still lit the flame
        // beside a 0% ring, because the streak was a single global number.
        val today = LocalDate.now(ZoneId.systemDefault())
        val result = calculateHabitStreaks(runOfFive(today), today.minusDays(5))
        assertEquals(0, result.currentStreak)
    }

    @Test
    fun `asOf on an empty day whose predecessor was done reports the prior run`() {
        // The "today not done yet must not zero it" rule, generalised to any as-of date.
        val today = LocalDate.now(ZoneId.systemDefault())
        val result = calculateHabitStreaks(runOfFive(today), today.minusDays(9))
        assertEquals(5, result.currentStreak)
    }

    @Test
    fun `the default argument is today, so existing call sites are unchanged`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = runOfFive(today)
        assertEquals(calculateHabitStreaks(occ, today), calculateHabitStreaks(occ))
    }

    @Test
    fun `longestStreak is independent of asOf`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = runOfFive(today)
        val expected = calculateHabitStreaks(occ).longestStreak
        assertEquals(5, expected)
        assertEquals(expected, calculateHabitStreaks(occ, today.minusDays(12)).longestStreak)
        assertEquals(expected, calculateHabitStreaks(occ, today.minusDays(5)).longestStreak)
    }

    /**
     * v0.5.2 §2 regression pin: [calculateHabitStreaks] takes `List<HabitOccurrence>` and never
     * sees the habit, so a run of COMPLETED BATCH occurrences produces the same result as the same
     * run from an INDIVIDUAL habit. Fails loudly if someone later makes streaks type-aware.
     */
    @Test
    fun `batch and individual habits streak identically`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val run = listOf(completed(today.minusDays(1)), completed(today.minusDays(2)), completed(today.minusDays(3)))
        // Occurrences are identical regardless of the parent habit's type — that IS the point.
        assertEquals(calculateHabitStreaks(run), calculateHabitStreaks(run.toList()))
        assertEquals(3, calculateHabitStreaks(run).currentStreak)
    }

    // ---- rec 6 (S4/S1): streak mode + rest days -----------------------------------------

    @Test
    fun `LENIENT counts a day whose only non-done item was SKIPPED`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = listOf(
            completed(today.minusDays(1), LocalTime.of(8, 0)),
            withStatus(today.minusDays(1), Occurrence.Status.SKIPPED, LocalTime.of(20, 0)),
            completed(today.minusDays(2))
        )
        assertEquals(
            "STRICT: the skipped item breaks the run",
            0, calculateHabitStreaks(occ).currentStreak
        )
        assertEquals(
            "LENIENT: done-or-skipped is a full day",
            2, calculateHabitStreaks(occ, today, StreakMode.LENIENT).currentStreak
        )
    }

    @Test
    fun `LENIENT still breaks on a still-pending item`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = listOf(
            completed(today.minusDays(1), LocalTime.of(8, 0)),
            withStatus(today.minusDays(1), Occurrence.Status.PENDING, LocalTime.of(20, 0)),
            completed(today.minusDays(2))
        )
        assertEquals(0, calculateHabitStreaks(occ, today, StreakMode.LENIENT).currentStreak)
    }

    @Test
    fun `a rest weekday with no occurrences does not break the run`() {
        // Fixed asOf on a Wednesday; make Sunday a rest day. Completions Mon, Sat (no Sun row).
        val wed = LocalDate.of(2026, 6, 17) // Wednesday
        val sat = LocalDate.of(2026, 6, 13)
        val mon = LocalDate.of(2026, 6, 15)
        val tue = LocalDate.of(2026, 6, 16)
        val occ = listOf(completed(sat), completed(mon), completed(tue), completed(wed))
        val rest = setOf(java.time.DayOfWeek.SUNDAY)
        // Without rest days the Sunday gap breaks it -> run is Mon..Wed = 3.
        assertEquals(3, calculateHabitStreaks(occ, wed).currentStreak)
        // With Sunday a rest day the walk steps over 2026-06-14 (Sun) -> Sat..Wed = 4.
        assertEquals(4, calculateHabitStreaks(occ, wed, StreakMode.STRICT, rest).currentStreak)
    }

    @Test
    fun `a rest weekday that WAS completed still counts plus one`() {
        val wed = LocalDate.of(2026, 6, 17)
        val days = listOf(
            LocalDate.of(2026, 6, 14), // Sunday - completed
            LocalDate.of(2026, 6, 15),
            LocalDate.of(2026, 6, 16),
            wed
        )
        val occ = days.map { completed(it) }
        val rest = setOf(java.time.DayOfWeek.SUNDAY)
        assertEquals(4, calculateHabitStreaks(occ, wed, StreakMode.STRICT, rest).currentStreak)
    }

    @Test
    fun `empty restDays plus STRICT is byte-identical to the pre-round result`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = runOfFive(today) + listOf(
            withStatus(today.minusDays(1), Occurrence.Status.SKIPPED),
            completed(today.minusDays(2))
        )
        val legacy = calculateHabitStreaks(occ, today)   // defaults: STRICT, no rest days
        val explicit = calculateHabitStreaks(occ, today, StreakMode.STRICT, emptySet())
        assertEquals(legacy, explicit)
    }

    @Test
    fun `parseRestDays tolerates blanks, whitespace and garbage`() {
        assertEquals(emptySet<java.time.DayOfWeek>(), parseRestDays(""))
        assertEquals(emptySet<java.time.DayOfWeek>(), parseRestDays(null))
        assertEquals(
            setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.SUNDAY),
            parseRestDays(" MONDAY , sunday , FUNDAY ")
        )
    }

    /**
     * v0.5.2 §9 / 5B.7: a backfilled completion buckets by `scheduledFor`, not `respondedAt`, so
     * it bridges a gap the instant the row is written.
     */
    @Test
    fun `backfill bridges a gap because streaks bucket by scheduledFor`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val withGap = listOf(
            completed(today.minusDays(4)), completed(today.minusDays(3)),
            completed(today.minusDays(1)), completed(today)
        )
        assertEquals(2, calculateHabitStreaks(withGap).currentStreak)
        val backfilled = withGap + HabitOccurrence(
            id = "backfill",
            habitId = "h1",
            scheduledFor = epochMillis(today.minusDays(2), LocalTime.of(9, 0)),
            status = Occurrence.Status.COMPLETED,
            respondedAt = System.currentTimeMillis(),
            notificationId = 0
        )
        assertEquals(5, calculateHabitStreaks(backfilled).currentStreak)
    }

    /**
     * Journal-as-habit round (B5/B6, §3 risk 6): a `LOGGED` habit occurrence (a Journal habit's
     * answered day) must count toward `calculateHabitStreaks` exactly like `COMPLETED` — otherwise
     * a Journal habit answered every day silently shows a permanent 0-day streak.
     */
    @Test
    fun `a LOGGED habit occurrence counts toward the streak like COMPLETED`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val occ = listOf(
            withStatus(today, Occurrence.Status.LOGGED),
            withStatus(today.minusDays(1), Occurrence.Status.LOGGED),
            withStatus(today.minusDays(2), Occurrence.Status.COMPLETED)
        )
        assertEquals(3, calculateHabitStreaks(occ).currentStreak)
    }

    @Test
    fun `streaksFromScheduledStatuses treats LOGGED as done for the habit doneStatus COMPLETED`() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val scheduled = listOf(
            epochMillis(today, LocalTime.of(9, 0)) to Occurrence.Status.LOGGED,
            epochMillis(today.minusDays(1), LocalTime.of(9, 0)) to Occurrence.Status.LOGGED
        )
        val result = streaksFromScheduledStatuses(scheduled, Occurrence.Status.COMPLETED, today)
        assertEquals(2, result.currentStreak)
    }
}
