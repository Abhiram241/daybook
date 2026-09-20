package com.daybook.app.ui.workout.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daybook.app.data.health.ExerciseTypeLabels
import com.daybook.app.data.health.HealthCardKind
import com.daybook.app.data.health.SourceAppLabels
import com.daybook.app.data.model.HealthSession
import com.daybook.app.data.model.HealthWeightReading
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.util.formatHealthCalories
import com.daybook.app.util.formatHealthCount
import com.daybook.app.util.formatHealthDistanceKm
import com.daybook.app.util.formatHealthDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private fun millisToTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FMT)

/**
 * User request — "Richer data must be shown when I click on that item." Each half-width tile on
 * [HealthTabScreen] shows the headline figures; this sheet shows what didn't fit — a min/max
 * breakdown for heart rate, sleep start/end times (already stored in [com.daybook.app.data.model.HealthDay]
 * but never rendered anywhere before this), a macro % split for nutrition, and an honest caption
 * on metrics ([HealthCardKind.SPO2], [HealthCardKind.WEIGHT]) where Health Connect only ever gives
 * Daybook one number for the day, rather than fabricating precision the data doesn't have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDetailSheet(
    kind: HealthCardKind?,
    state: HealthTabUiState,
    onDismiss: () -> Unit
) {
    if (kind == null || kind == HealthCardKind.SESSIONS) return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(detailTitle(kind), style = DaybookText.SectionTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.mode == HealthTabMode.DAY) {
                    "For ${state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))}"
                } else {
                    // M6 fix — this was unconditionally "Averaged over …", but
                    // `HealthAggregate` deliberately makes sleep a TOTAL, not a mean ("Sums, not
                    // means" — HealthAggregation.kt), and this sheet renders `sleepMinutesTotal`
                    // etc. directly below. A 632-hour "average" read as obviously wrong; the
                    // subtitle is now kind-aware so Sleep's Range-mode sheet says "Totalled".
                    val verb = if (kind == HealthCardKind.SLEEP) "Totalled" else "Averaged"
                    "$verb over ${state.range.start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${state.range.end.format(DateTimeFormatter.ofPattern("d MMM"))}"
                },
                style = DaybookText.Caption, color = DaybookColors.TextMuted
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DaybookColors.Hairline)
            Spacer(Modifier.height(8.dp))
            DetailRows(kind, state)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailRows(kind: HealthCardKind, state: HealthTabUiState) {
    val day = state.day
    val agg = state.rangeAggregate
    val isDay = state.mode == HealthTabMode.DAY
    // UI fix (owner review of the Health tab screenshot, build 50) — "tapping ANY health tile must
    // surface the data source". Steps/Calories/Heart rate/Oxygen/Hydration all come from the same
    // combined Health Connect aggregate read (`HealthConnectReader.dayAggregate`), so they share
    // one source line; Session/Nutrition/Weight already had their own per-record source before
    // this fix (`HealthSessionDetailSheet`'s "Source" row, this file's own NUTRITION/WEIGHT branch).
    //
    // [owner feedback, 2026-09-20] — a historical day whose row was written before
    // `activity_source_apps` existed (MIGRATION_32_33, nullable, no backfill at ALTER time) has
    // this null forever unless re-synced — `HealthRepository.backfillActivitySourceApps` now fills
    // most of those in, but the raw csv can still be null for a day that genuinely has zero
    // recorded activity data, or before that one-time backfill has run. [activityRawCsv] is looked
    // up per-branch through [sourceRowValue] so the "Source unknown" fallback only ever appears
    // alongside a kind that actually rendered SOME value — a day with nothing to show for a kind
    // still shows nothing for it.
    val activityRawCsv = if (isDay) day?.activitySourceApps else agg.activitySourceApps
    when (kind) {
        HealthCardKind.STEPS -> {
            val steps = if (isDay) day?.steps else agg.stepsAvg
            val distance = if (isDay) day?.distanceMeters else agg.distanceMetersAvg
            DetailRow("Steps", if (isDay) day?.steps?.let { formatHealthCount(it) } else agg.stepsAvg?.let { "${formatHealthCount(it.toLong())} avg/day" })
            DetailRow("Distance", if (isDay) day?.distanceMeters?.let { formatHealthDistanceKm(it) } else agg.distanceMetersAvg?.let { "${formatHealthDistanceKm(it.toFloat())} avg/day" })
            DetailRow("Source", sourceRowValue(activityRawCsv, hasData = steps != null || distance != null))
        }
        HealthCardKind.CALORIES -> {
            val active = if (isDay) day?.activeCalories else agg.activeCaloriesAvg?.toFloat()
            val total = if (isDay) day?.totalCalories else agg.totalCaloriesAvg?.toFloat()
            DetailRow("Active", active?.let { formatHealthCalories(it) })
            DetailRow("Total", total?.let { formatHealthCalories(it) })
            if (active != null && total != null && total >= active) {
                DetailRow("Estimated resting", formatHealthCalories(total - active))
            }
            DetailRow("Source", sourceRowValue(activityRawCsv, hasData = active != null || total != null))
        }
        HealthCardKind.HEART_RATE -> {
            val avg = if (isDay) day?.avgHeartRate else agg.avgHeartRateAvg
            val resting = if (isDay) day?.restingHeartRate else agg.restingHeartRateAvg
            val min = if (isDay) day?.minHeartRate else agg.minHeartRateAvg
            val max = if (isDay) day?.maxHeartRate else agg.maxHeartRateAvg
            if (isDay) {
                DetailRow("Average", day?.avgHeartRate?.let { "$it bpm" })
                DetailRow("Resting", day?.restingHeartRate?.let { "$it bpm" })
                DetailRow("Lowest", day?.minHeartRate?.let { "$it bpm" })
                DetailRow("Highest", day?.maxHeartRate?.let { "$it bpm" })
            } else {
                DetailRow("Average", agg.avgHeartRateAvg?.let { "${it.roundToInt()} bpm avg/day" })
                DetailRow("Resting", agg.restingHeartRateAvg?.let { "${it.roundToInt()} bpm avg/day" })
                DetailRow("Lowest", agg.minHeartRateAvg?.let { "${it.roundToInt()} bpm avg/day" })
                DetailRow("Highest", agg.maxHeartRateAvg?.let { "${it.roundToInt()} bpm avg/day" })
            }
            DetailRow("Source", sourceRowValue(activityRawCsv, hasData = avg != null || resting != null || min != null || max != null))
            HelpCaption("Heart rate is read as a daily average, resting rate and range — never a raw per-minute feed, to keep Health Connect reads light on battery.")
        }
        HealthCardKind.SLEEP -> {
            if (isDay) {
                // User request — every sleep touching this day gets its own block, morning first.
                state.sleepEntries.forEachIndexed { i, entry ->
                    val row = entry.row
                    if (i > 0) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DaybookColors.Hairline)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(entry.label, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    DetailRow("Went to bed", row.sleepStartMillis?.let { millisToTime(it) })
                    DetailRow("Woke up", row.sleepEndMillis?.let { millisToTime(it) })
                    DetailRow("Total", row.sleepMinutes?.let { formatHealthDuration(it) })
                    DetailRow("Deep", row.sleepDeepMinutes?.let { formatHealthDuration(it) })
                    DetailRow("Light", row.sleepLightMinutes?.let { formatHealthDuration(it) })
                    DetailRow("REM", row.sleepRemMinutes?.let { formatHealthDuration(it) })
                    DetailRow("Awake", row.sleepAwakeMinutes?.let { formatHealthDuration(it) })
                }
            } else {
                DetailRow("Total", formatHealthDuration(agg.sleepMinutesTotal))
                DetailRow("Deep", formatHealthDuration(agg.sleepDeepMinutesTotal))
                DetailRow("Light", formatHealthDuration(agg.sleepLightMinutesTotal))
                DetailRow("REM", formatHealthDuration(agg.sleepRemMinutesTotal))
                DetailRow("Awake", formatHealthDuration(agg.sleepAwakeMinutesTotal))
                HelpCaption("A night is counted on the ${state.sleepCountDay.label.lowercase()}. Change this in Beast Mode settings.")
            }
        }
        HealthCardKind.SPO2 -> {
            val v = if (isDay) day?.spo2Percent else agg.spo2PercentAvg?.toFloat()
            val min = if (isDay) day?.spo2MinPercent else agg.spo2MinPercentAvg?.toFloat()
            val max = if (isDay) day?.spo2MaxPercent else agg.spo2MaxPercentAvg?.toFloat()
            DetailRow("Average", v?.let { "${"%.0f".format(it)}%" })
            DetailRow("Lowest", min?.let { "${"%.0f".format(it)}%" })
            DetailRow("Highest", max?.let { "${"%.0f".format(it)}%" })
            DetailRow("Source", sourceRowValue(activityRawCsv, hasData = v != null || min != null || max != null))
            HelpCaption("Health Connect gives Daybook one averaged oxygen-saturation reading per day, not each individual sample your band took.")
        }
        HealthCardKind.WEIGHT -> {
            val v = if (isDay) day?.weightKg else agg.weightKgAvg?.toFloat()
            DetailRow(if (isDay) "Weight" else "Average weight", v?.let { "${"%.1f".format(it)} kg" })
            if (isDay) {
                // Newest-first (`observeWeightReadingsForDay`'s own ordering) — the latest
                // reading is what `weightKg` above actually shows, so its source is the
                // relevant one to attribute. `weight_readings`/`sourceApp` predates this round's
                // backfill gap (always populated whenever there was any reading at all), so this
                // fallback only ever fires for the (rarer) case of a reading with no `sourceApp`.
                val weightSourceApp = state.dayWeightReadings.firstOrNull()?.sourceApp
                DetailRow(
                    "Source",
                    SourceAppLabels.labelFor(weightSourceApp)?.let { "From $it" }
                        ?: state.dayWeightReadings.firstOrNull()?.let { "Source unknown (synced before source tracking was added)" }
                )
                HelpCaption("This is the last reading Health Connect had for the day, if you weighed in more than once.")
                WeightReadingsList(state.dayWeightReadings)
            }
        }
        HealthCardKind.HYDRATION -> {
            val v = if (isDay) day?.hydrationMl else agg.hydrationMlAvg?.toFloat()
            DetailRow("Hydration", v?.let { "${"%.0f".format(it)} ml" })
            DetailRow("In litres", v?.let { "${"%.2f".format(it / 1000f)} L" })
            DetailRow("Source", sourceRowValue(activityRawCsv, hasData = v != null))
        }
        HealthCardKind.NUTRITION -> {
            val cal = if (isDay) day?.nutritionCalories else agg.nutritionCaloriesAvg?.toFloat()
            val protein = if (isDay) day?.nutritionProteinGrams else agg.nutritionProteinGramsAvg?.toFloat()
            val carbs = if (isDay) day?.nutritionCarbsGrams else agg.nutritionCarbsGramsAvg?.toFloat()
            val fat = if (isDay) day?.nutritionFatGrams else agg.nutritionFatGramsAvg?.toFloat()
            val sourceApp = if (isDay) day?.nutritionSourceApp else agg.nutritionSourceApp
            DetailRow("Calories", cal?.let { formatHealthCalories(it) })
            DetailRow("Protein", protein?.let { "${"%.0f".format(it)} g" })
            DetailRow("Carbs", carbs?.let { "${"%.0f".format(it)} g" })
            DetailRow("Fat", fat?.let { "${"%.0f".format(it)} g" })
            val proteinKcal = (protein ?: 0f) * 4f
            val carbsKcal = (carbs ?: 0f) * 4f
            val fatKcal = (fat ?: 0f) * 9f
            val macroKcal = proteinKcal + carbsKcal + fatKcal
            if (macroKcal > 0f) {
                DetailRow(
                    "Split",
                    "${(proteinKcal / macroKcal * 100).roundToInt()}% protein · " +
                        "${(carbsKcal / macroKcal * 100).roundToInt()}% carbs · " +
                        "${(fatKcal / macroKcal * 100).roundToInt()}% fat"
                )
            }
            DetailRow("Source", SourceAppLabels.labelFor(sourceApp) ?: "Another app")
        }
        HealthCardKind.SESSIONS -> Unit
    }
}

/** HEALTH_VITALS_RICHNESS_PLAN.md §6 — "list, not chart" idiom, same as per-exercise history and
 *  band session lists elsewhere in Beast Mode. A day with exactly one reading (or none) renders
 *  nothing here — there is nothing to expand into. [readings] is already newest-first
 *  ([com.daybook.app.data.local.HealthDao.observeWeightReadingsForDay]'s own ordering). */
@Composable
private fun WeightReadingsList(readings: List<HealthWeightReading>) {
    if (readings.size < 2) return
    var expanded by remember(readings) { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${readings.size} readings today", style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
        Text(if (expanded) "Hide" else "Show", style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
    }
    if (expanded) {
        Column {
            for (reading in readings) {
                DetailRow(millisToTime(reading.atMillis), "${"%.1f".format(reading.weightKg)} kg")
            }
        }
    }
}

/** [owner feedback, 2026-09-20] — resolves a "Source" row's value from a raw
 *  `activitySourceApps` CSV, falling back to an explicit "unknown" caption (instead of silently
 *  omitting the row) whenever [hasData] is true but the CSV has nothing to attribute — the
 *  `activity_source_apps` backfill gap (see [DetailRows]'s own KDoc). When [hasData] is false,
 *  returns null so the Source row disappears entirely, same as before: a day with nothing to show
 *  for this kind still shows nothing. */
private fun sourceRowValue(rawCsv: String?, hasData: Boolean): String? {
    val labeled = SourceAppLabels.labelForCsv(rawCsv)?.let { "From $it" }
    return labeled ?: if (hasData) "Source unknown (synced before source tracking was added)" else null
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value == null) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
        Text(value, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
    }
}

@Composable
private fun HelpCaption(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = DaybookText.Caption, color = DaybookColors.TextFaint)
}

private fun detailTitle(kind: HealthCardKind): String = when (kind) {
    HealthCardKind.STEPS -> "Steps & distance"
    HealthCardKind.CALORIES -> "Calories"
    HealthCardKind.HEART_RATE -> "Heart rate"
    HealthCardKind.SLEEP -> "Sleep"
    HealthCardKind.SPO2 -> "Oxygen saturation"
    HealthCardKind.WEIGHT -> "Weight"
    HealthCardKind.HYDRATION -> "Hydration"
    HealthCardKind.NUTRITION -> "Nutrition"
    HealthCardKind.SESSIONS -> "Workout"
}

/** The band-recorded-session equivalent of [HealthDetailSheet] — the fields [HealthTabScreen]'s
 *  compact `SessionRow` doesn't have room for (distance, average heart rate, source app, start/end
 *  time), tapped open per row rather than picked via [HealthCardKind]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSessionDetailSheet(
    session: HealthSession?,
    onDismiss: () -> Unit
) {
    if (session == null) return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(
                session.title?.takeIf { it.isNotBlank() } ?: ExerciseTypeLabels.labelFor(session.exerciseType),
                style = DaybookText.SectionTitle, color = DaybookColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${millisToTime(session.startMillis)} – ${millisToTime(session.endMillis)}",
                style = DaybookText.Caption, color = DaybookColors.TextMuted
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DaybookColors.Hairline)
            Spacer(Modifier.height(8.dp))
            DetailRow("Duration", formatHealthDuration(session.durationMinutes))
            DetailRow("Calories", session.activeCalories?.let { formatHealthCalories(it) })
            DetailRow("Distance", session.distanceMeters?.let { formatHealthDistanceKm(it) })
            DetailRow("Average heart rate", session.avgHeartRate?.let { "$it bpm" })
            DetailRow("Source", SourceAppLabels.labelFor(session.sourceApp) ?: "Another app")
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** R-2 fix (HEALTH_RANGE_TAB_AND_SYNC_AUDIT_2026-09-20.md) — the owner's "tapping it must show the
 *  split" ask: the individual Health Connect segments a [com.daybook.app.data.health
 *  .HealthSessionGroup] tile summed, each with its own time/duration/distance at a glance; tapping
 *  one opens [HealthSessionDetailSheet] (via [onOpenSession]) for the fuller per-record detail —
 *  duration, calories, average heart rate and source — this sheet doesn't have room for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSessionGroupDetailSheet(
    group: com.daybook.app.data.health.HealthSessionGroup?,
    onDismiss: () -> Unit,
    onOpenSession: (HealthSession) -> Unit
) {
    if (group == null) return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(group.label, style = DaybookText.SectionTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "${group.sessions.size} segments · ${formatHealthDuration(group.totalDurationMinutes)}" +
                    (group.totalDistanceMeters?.let { " · " + formatHealthDistanceKm(it) } ?: ""),
                style = DaybookText.Caption, color = DaybookColors.TextMuted
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DaybookColors.Hairline)
            for (session in group.sessions) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable { onOpenSession(session) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(millisToTime(session.startMillis), style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
                        Text(
                            formatHealthDuration(session.durationMinutes) +
                                (session.distanceMeters?.let { " · " + formatHealthDistanceKm(it) } ?: ""),
                            style = DaybookText.Caption, color = DaybookColors.TextMuted
                        )
                    }
                }
                HorizontalDivider(color = DaybookColors.Hairline)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
