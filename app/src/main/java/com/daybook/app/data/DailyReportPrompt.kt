package com.daybook.app.data

import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.formatWeight
import com.daybook.app.util.formatHealthCalories
import com.daybook.app.util.formatHealthCount
import com.daybook.app.util.formatHealthDistanceKm
import com.daybook.app.util.formatHealthDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * DAILY_REPORT_PLAN.md §3.4 — a pure function, no I/O, deterministic: assembles the same four
 * sections §1 renders into one plain-text prompt. A section missing (§1.5) is called out with a
 * one-line note ("No workout logged") instead of just vanishing — the model is told what's absent
 * instead of guessing from silence.
 *
 * DAILY_REPORT_REDESIGN_PLAN.md §2/§6/§7 — [metaPrompt] (when non-blank) is prepended as its own
 * paragraph ahead of the "You are summarising…" preamble; [categories] gates which of the four
 * sections are appended at all (a disabled category is omitted from the prompt text entirely, not
 * sent as "No X logged" — that would be a lie about the data). The per-day section body itself is
 * shared with [buildMultiDayReportPrompt] via [appendDaySections] so both stay in sync.
 */
fun buildDailyReportPrompt(
    workout: WorkoutSectionData?,
    health: HealthSectionData?,
    intake: List<IntakeEntryRow>,
    todo: List<TodoEntryRow>,
    date: LocalDate,
    weightUnit: WeightUnit,
    metaPrompt: String = "",
    categories: Set<ReportCategory> = ReportCategory.entries.toSet()
): String {
    val sb = StringBuilder()
    if (metaPrompt.isNotBlank()) {
        sb.append(metaPrompt.trim()).append("\n\n")
    }
    sb.append("You are summarising one person's day from their personal tracking app, Daybook.\n")
    sb.append("Write a short, warm, plain-language summary of ${dateLabel(date)} using only the facts below.\n")
    sb.append("Do not invent details that are not present. Keep it to a few sentences per section.\n\n")

    appendDaySections(sb, workout, health, intake, todo, weightUnit, categories)

    return sb.toString()
}

/**
 * DAILY_REPORT_REDESIGN_PLAN.md §7.2 — chat's configurable multi-day context. [days] must already
 * be sorted oldest-first (see `DailyReportRepository.buildChatContext`). Each day gets a `## <date>`
 * header so multi-day answers can be attributed correctly ("on Tuesday you logged X"). Passing a
 * single-day list produces byte-for-byte the same per-day body [buildDailyReportPrompt] does (both
 * share [appendDaySections]) — a one-day call is indistinguishable from today's existing behaviour.
 */
fun buildMultiDayReportPrompt(
    days: List<DailyReportData>,
    categories: Set<ReportCategory>,
    weightUnit: WeightUnit,
    metaPrompt: String = ""
): String {
    val sb = StringBuilder()
    if (metaPrompt.isNotBlank()) {
        sb.append(metaPrompt.trim()).append("\n\n")
    }
    sb.append("You are helping one person review their personal tracking app, Daybook, across ")
    sb.append("multiple days. Write plainly and only use the facts below; do not invent details.\n\n")
    days.forEach { day ->
        sb.append("# ${dateLabel(day.date)}\n")
        appendDaySections(sb, day.workout, day.health, day.intake, day.todo, weightUnit, categories)
    }
    return sb.toString()
}

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §1.4 — Chat's built-in system-message rules,
 *  unchanged from the wording `DailyReportViewModel.openChat()` used inline before this. Kept as
 *  its own constant so [buildChatSystemMessage] is a pure, unit-testable function. */
const val CHAT_BUILT_IN_RULES = "You are a helpful, casual assistant inside Daybook, a personal " +
    "tracking app. Below is the user's data, provided ONLY as background context you " +
    "may draw on IF it's relevant to what the user actually asks or says.\n\n" +
    "For a greeting or small talk (e.g. \"hi\", \"hello\", \"how are you\"), just reply " +
    "naturally and briefly — do not recite or summarise the data below unprompted. Only " +
    "bring up specific facts from the data when the user's message is actually asking " +
    "about their day, a habit, a workout, food/med intake, or health metrics. If asked " +
    "about something not covered in the data, say so plainly rather than guessing."

/**
 * AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §1.4 — Chat's system message, in the exact,
 * decided order: [CHAT_BUILT_IN_RULES] first, then (only when non-blank) the user's own Chat
 * instructions as a labelled paragraph, then [clampNote] (only when non-blank), then [data].
 * [chatMetaPrompt] is [com.daybook.app.data.model.AppSettings.aiChatMetaPrompt] — a field
 * SEPARATE from the one-shot AI Summary's `aiMetaPrompt`, which never reaches chat at all.
 */
fun buildChatSystemMessage(chatMetaPrompt: String, clampNote: String, data: String): String {
    val sb = StringBuilder(CHAT_BUILT_IN_RULES)
    if (chatMetaPrompt.isNotBlank()) {
        sb.append("\n\nThe user's own instructions for this chat (follow them unless they conflict with the rules above):\n")
        sb.append(chatMetaPrompt.trim())
    }
    if (clampNote.isNotBlank()) sb.append("\n\n").append(clampNote)
    sb.append("\n\n").append(data)
    return sb.toString()
}

private fun dateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.US))

/** Shared per-day section body for both [buildDailyReportPrompt] and [buildMultiDayReportPrompt].
 *  A category not in [categories] is skipped entirely — omitted from the AI's input, not sent as
 *  a "no data" placeholder — per DAILY_REPORT_REDESIGN_PLAN.md §6.2. */
private fun appendDaySections(
    sb: StringBuilder,
    workout: WorkoutSectionData?,
    health: HealthSectionData?,
    intake: List<IntakeEntryRow>,
    todo: List<TodoEntryRow>,
    weightUnit: WeightUnit,
    categories: Set<ReportCategory>
) {
    if (ReportCategory.WORKOUT in categories) {
        sb.append("## Workout\n")
        if (workout == null || workout.sessions.isEmpty()) {
            sb.append("No workout logged.\n\n")
        } else {
            workout.sessions.forEach { s ->
                sb.append("- ${s.title}")
                s.durationMinutes?.let { sb.append(", ${formatHealthDuration(it)}") }
                sb.append(", ${formatWeight(s.totalVolumeKg, weightUnit)} total volume, ${s.setCount} sets\n")
                s.exercises.forEach { ex ->
                    sb.append("  - ${ex.name}: ${ex.setCount} sets")
                    ex.bestSetLabel?.let { sb.append(", best set $it") }
                    sb.append("\n")
                }
            }
            sb.append("\n")
        }
    }

    if (ReportCategory.HEALTH in categories) {
        sb.append("## Health\n")
        if (health == null || health.day == null) {
            sb.append("No health data logged.\n\n")
        } else {
            val d = health.day
            val lines = buildList {
                d.steps?.let { add("Steps: ${formatHealthCount(it)}") }
                (d.activeCalories ?: d.totalCalories)?.let { add("Calories: ${formatHealthCalories(it)}") }
                d.distanceMeters?.let { add("Distance: ${formatHealthDistanceKm(it)}") }
                if (d.avgHeartRate != null || d.minHeartRate != null || d.maxHeartRate != null) {
                    add("Heart rate: avg ${d.avgHeartRate ?: "?"}, range ${d.minHeartRate ?: "?"}-${d.maxHeartRate ?: "?"} bpm")
                }
                d.sleepMinutes?.let { add("Sleep: ${formatHealthDuration(it)}") }
                d.spo2Percent?.let { add("SpO2: ${it}%") }
                d.weightKg?.let { add("Weight: ${formatWeight(it, weightUnit)}") }
                d.hydrationMl?.let { add("Hydration: ${it.toInt()} ml") }
            }
            if (lines.isEmpty() && health.sessions.isEmpty()) {
                sb.append("No health data logged.\n\n")
            } else {
                lines.forEach { sb.append("- $it\n") }
                if (health.sessions.isNotEmpty()) {
                    sb.append("- Tracked sessions: ${health.sessions.size}\n")
                }
                sb.append("\n")
            }
        }
    }

    if (ReportCategory.INTAKE in categories) {
        sb.append("## Intake\n")
        if (intake.isEmpty()) {
            sb.append("No food/med entries logged.\n\n")
        } else {
            // Round 2 (Feature 4 context): the actual logged text travels too, not just the label —
            // so the AI can answer "what did I eat today" instead of just naming the reminder.
            intake.forEach { row ->
                sb.append("- ${row.timeLabel}: ${row.label}")
                row.flagLabel?.let { sb.append(" ($it)") }
                if (row.outsideFood) sb.append(" [outside food]")
                row.suspectedFood?.let { sb.append(" — suspected food: $it") }
                sb.append("\n")
                row.responseText.takeIf { it.isNotBlank() }?.let { sb.append("  Logged: $it\n") }
                row.description?.let { sb.append("  Note: $it\n") }
                row.qaPairs.forEach { (q, a) -> sb.append("  Q: $q\n  A: $a\n") }
            }
            sb.append("\n")
        }
    }

    if (ReportCategory.TODO in categories) {
        sb.append("## Habits / to-do\n")
        if (todo.isEmpty()) {
            sb.append("Nothing scheduled.\n\n")
        } else {
            // Round 2 (Feature 4 context): a STREAK habit reports its streak length as of this date
            // instead of a plain done/missed status, so "how's my streak on X" is answerable.
            todo.forEach { row ->
                val statusText = row.streakDays?.let { "current streak: $it day(s)" } ?: row.statusLabel
                sb.append("- ${row.timeLabel}: ${row.label} — $statusText\n")
                row.qaPairs.forEach { (q, a) -> sb.append("  Q: $q\n  A: $a\n") }
            }
            sb.append("\n")
        }
    }
}
