package com.daybook.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Date/time helpers. Was a `@Singleton` class whose every method forwarded to a companion
 * of the same name; flattened to a plain `object` so it needs no DI graph entry and callers
 * use one call style (REV-36). Pure functions, no state.
 */
object DateTimeUtils {
    /** Storage/parse format — 24h, never shown to the user. Do not change. */
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    /** User-facing 12h format with AM/PM, e.g. "6:05 PM". */
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)
    /** User-facing 24h format, e.g. "18:05". Selected by the `clock_24h` device-local setting. */
    private val displayTimeFormatter24 = DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // ---------------------------------------------------------------- week-start helpers (rec 1)
    /** Maps the `week_start` setting string to a [DayOfWeek]. Anything unrecognised → MONDAY. */
    fun firstDayOfWeek(weekStart: String): DayOfWeek = when (weekStart.trim().uppercase()) {
        "SUNDAY" -> DayOfWeek.SUNDAY
        "SATURDAY" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.MONDAY
    }

    /** The date of the first day of [date]'s week, given the configured [weekStart]. */
    fun startOfWeek(date: LocalDate, weekStart: String): LocalDate =
        date.minusDays(indexInWeek(date, weekStart).toLong())

    /** 0-based offset of [date] within its week (0 = the configured first weekday .. 6). */
    fun indexInWeek(date: LocalDate, weekStart: String): Int =
        ((date.dayOfWeek.value - firstDayOfWeek(weekStart).value) + 7) % 7

    fun timesToJson(times: List<LocalTime>): String {
        return times.joinToString(",") { it.format(timeFormatter) }
    }

    fun jsonToTimes(jsonString: String): List<LocalTime> {
        if (jsonString.isBlank()) return emptyList()
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-13, Low): was a bare `.map { LocalTime.parse
        // }` — unlike its sibling jsonToDays (below), a single malformed entry threw and took down
        // the whole times_json, reachable from HomeViewModel.buildItems's stateIn pipeline.
        // Currently unreachable in practice (every writer produces valid values) but cheap, and
        // pairs with Phase 10's broader stateIn-guarding work. Drops unparseable entries
        // per-element, exactly like jsonToDays already does.
        return jsonString.split(",").filter { it.isNotBlank() }.mapNotNull {
            runCatching { LocalTime.parse(it.trim(), timeFormatter) }.getOrNull()
        }
    }

    fun daysToJson(days: List<com.daybook.app.data.model.DayOfWeek>): String {
        return days.joinToString(",") { it.name }
    }

    fun jsonToDays(jsonString: String): List<com.daybook.app.data.model.DayOfWeek> {
        if (jsonString.isBlank()) return emptyList()
        return jsonString.split(",").filter { it.isNotBlank() }.mapNotNull {
            try {
                com.daybook.app.data.model.DayOfWeek.valueOf(it.trim())
            } catch (e: Exception) {
                null
            }
        }
    }

    // ------------------------------------------------------------- journal questions (per-habit)
    // Journal-as-habit round: `Habit.journalQuestionsJson` is a real JSON string array (unlike
    // times/days' comma-join) since question text may itself contain commas. Trims, drops blanks;
    // order is preserved (list index == asked order).
    private val journalQuestionsSerializer: kotlinx.serialization.KSerializer<List<String>> =
        kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>())
    private val journalQuestionsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun journalQuestionsToJson(questions: List<String>): String {
        val cleaned = questions.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ""
        return journalQuestionsJson.encodeToString(journalQuestionsSerializer, cleaned)
    }

    fun jsonToJournalQuestions(jsonString: String): List<String> {
        if (jsonString.isBlank()) return emptyList()
        return runCatching {
            journalQuestionsJson.decodeFromString(journalQuestionsSerializer, jsonString)
        }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getDayName(dayOfWeek: com.daybook.app.data.model.DayOfWeek): String {
        return dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    }

    fun isDateActive(date: LocalDate, activeDaysJson: String): Boolean {
        val activeDays = jsonToDays(activeDaysJson)
        if (activeDays.isEmpty()) return true
        val javaDay = date.dayOfWeek // java.time.DayOfWeek
        return activeDays.any { it.name == javaDay.name }
    }

    fun calculateNextOccurrence(
        times: List<LocalTime>,
        activeDaysJson: String,
        fromDate: LocalDate = LocalDate.now()
    ): Pair<LocalDate, LocalTime> {
        if (times.isEmpty()) return fromDate to LocalTime.MIN
        val sortedTimes = times.sorted()

        for (i in 0L..30L) {
            val dateToCheck = fromDate.plusDays(i)
            if (isDateActive(dateToCheck, activeDaysJson)) {
                val now = LocalTime.now()
                if (i == 0L) {
                    val futureTimes = sortedTimes.filter { it.isAfter(now) }
                    if (futureTimes.isNotEmpty()) {
                        return dateToCheck to futureTimes.first()
                    }
                } else {
                    return dateToCheck to sortedTimes.first()
                }
            }
        }
        return fromDate to sortedTimes.first()
    }

    /** User-facing time string. [clock24h] selects "HH:mm" vs "h:mm a". Storage stays "HH:mm". */
    fun formatTime(time: LocalTime, clock24h: Boolean): String =
        time.format(if (clock24h) displayTimeFormatter24 else displayTimeFormatter)

    /** 12h shim — kept so callers not yet threading the `clock_24h` setting still compile. */
    fun formatTime(time: LocalTime): String = formatTime(time, false)

    fun formatDate(date: LocalDate): String {
        return date.format(dateFormatter)
    }

    fun formatDateTime(date: LocalDate, time: LocalTime): String {
        return "${formatDate(date)} ${formatTime(time)}"
    }

    fun getRelativeDayString(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)

        return when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            tomorrow -> "Tomorrow"
            else -> formatDate(date)
        }
    }

    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.US)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", java.util.Locale.US)

    /** Friendly day label: Today / Tomorrow / weekday (within a week) / "5 Sep". */
    fun formatDayLabel(date: LocalDate): String {
        val today = LocalDate.now()
        return when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date.isAfter(today) && date.isBefore(today.plusDays(7)) -> date.format(weekdayFormatter)
            else -> date.format(shortDateFormatter)
        }
    }

    /** "Today 6:05 PM" style, from an epoch-millis instant in the device zone. [clock24h] selects
     *  the time format; the no-arg overload keeps the 12h default for un-swept callers. */
    fun formatWhen(epochMillis: Long, clock24h: Boolean): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return "${formatDayLabel(zdt.toLocalDate())} ${formatTime(zdt.toLocalTime(), clock24h)}"
    }

    fun formatWhen(epochMillis: Long): String = formatWhen(epochMillis, false)

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    fun timestampToLocalDate(timestamp: Long): LocalDate {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    fun timestampToLocalTime(timestamp: Long): LocalTime {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
    }

    fun stringToTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString.trim(), timeFormatter)
        } catch (e: Exception) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 16 (C-8, Low) — minimal fix per the plan's own
            // lowest-priority framing: log when the fallback fires, but do NOT change the fallback
            // itself. Swapping the silent LocalTime.MIN default for a thrown exception risks a NEW
            // regression in place of an old, harmless one. The Log call itself is guarded: this
            // pure function is exercised directly by DateTimeUtilsTest with deliberately-malformed
            // input, and android.util.Log is unmocked in this project's plain-JVM unit tests (see
            // ViewModelExt.recordUnhandledException's identical note).
            runCatching {
                android.util.Log.w("DateTimeUtils", "stringToTime: couldn't parse '$timeString', defaulting to LocalTime.MIN", e)
            }
            LocalTime.MIN
        }
    }
}
