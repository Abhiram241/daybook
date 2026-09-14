package com.daybook.app.data.health

import com.daybook.app.data.model.HealthDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * User request — sleep spanning midnight belongs to BOTH dates it touches.
 *
 * Storage is unchanged: a `health_days` row's sleep is the sleep that ENDED on that date (the
 * wake-up day). From that:
 *  - a day's list shows its own row's sleep (e.g. "13–14 Sept" on the 14th), then the NEXT day's
 *    row's sleep when that one started on this day (e.g. "14–15 Sept" also on the 14th);
 *  - [SleepCountDay] (Beast Mode settings) decides which date(s) a night's hours COUNT toward in
 *    totals — wake-up day, bedtime day, or both.
 */
enum class SleepCountDay(val storageKey: String, val label: String) {
    WAKE("WAKE", "Wake-up day"),
    BED("BED", "Bedtime day"),
    BOTH("BOTH", "Both days");

    companion object {
        val DEFAULT = WAKE
        fun fromKey(k: String?): SleepCountDay = entries.firstOrNull { it.storageKey == k } ?: DEFAULT
    }
}

/** One sleep shown on a day: the stored [row] it comes from, and its "13–14 Sept" [label]. */
data class SleepEntry(val row: HealthDay, val label: String)

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** The local date a row's sleep started on — its own date when no start time was recorded. */
fun sleepBedtimeDate(row: HealthDay, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
    val wake = LocalDate.parse(row.localDate)
    return row.sleepStartMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() } ?: wake
}

/** "14 Sept" for a sleep within one date, "13–14 Sept" across two, "30 Sept – 1 Oct" across months. */
fun sleepSpanLabel(bed: LocalDate, wake: LocalDate): String = when {
    bed == wake -> wake.format(DAY_MONTH)
    bed.month == wake.month && bed.year == wake.year -> "${bed.dayOfMonth}–${wake.format(DAY_MONTH)}"
    else -> "${bed.format(DAY_MONTH)} – ${wake.format(DAY_MONTH)}"
}

/** The sleeps to list on [date], morning one first. [day] is [date]'s row, [nextDay] the row after. */
fun sleepEntriesForDay(
    date: LocalDate,
    day: HealthDay?,
    nextDay: HealthDay?,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<SleepEntry> = buildList {
    if (day?.sleepMinutes != null) {
        add(SleepEntry(day, sleepSpanLabel(sleepBedtimeDate(day, zoneId), date)))
    }
    if (nextDay?.sleepMinutes != null && sleepBedtimeDate(nextDay, zoneId) == date) {
        add(SleepEntry(nextDay, sleepSpanLabel(date, LocalDate.parse(nextDay.localDate))))
    }
}

/**
 * The sleep rows whose hours count inside [start]..[end] under [mode]. [rows] must include the day
 * after [end] (and, for safety, the day before [start]) so a bedtime-day night that ends just past
 * the range is still found. A row may appear twice under [SleepCountDay.BOTH] when both of its
 * dates fall in range — that's the point of "both days".
 */
fun sleepRowsCountedInRange(
    rows: List<HealthDay>,
    start: LocalDate,
    end: LocalDate,
    mode: SleepCountDay,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<HealthDay> {
    fun inRange(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
    val out = ArrayList<HealthDay>()
    for (row in rows) {
        if (row.sleepMinutes == null) continue
        val wake = runCatching { LocalDate.parse(row.localDate) }.getOrNull() ?: continue
        val bed = sleepBedtimeDate(row, zoneId)
        when (mode) {
            SleepCountDay.WAKE -> if (inRange(wake)) out += row
            SleepCountDay.BED -> if (inRange(bed)) out += row
            SleepCountDay.BOTH -> {
                if (inRange(wake)) out += row
                if (bed != wake && inRange(bed)) out += row
            }
        }
    }
    return out
}
