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
    when (kind) {
        HealthCardKind.STEPS -> {
            DetailRow("Steps", if (isDay) day?.steps?.let { formatHealthCount(it) } else agg.stepsAvg?.let { "${formatHealthCount(it.toLong())} avg/day" })
            DetailRow("Distance", if (isDay) day?.distanceMeters?.let { formatHealthDistanceKm(it) } else agg.distanceMetersAvg?.let { "${formatHealthDistanceKm(it.toFloat())} avg/day" })
        }
        HealthCardKind.CALORIES -> {
            val active = if (isDay) day?.activeCalories else agg.activeCaloriesAvg?.toFloat()
            val total = if (isDay) day?.totalCalories else agg.totalCaloriesAvg?.toFloat()
            DetailRow("Active", active?.let { formatHealthCalories(it) })
            DetailRow("Total", total?.let { formatHealthCalories(it) })
            if (active != null && total != null && total >= active) {
                DetailRow("Estimated resting", formatHealthCalories(total - active))
            }
        }
        HealthCardKind.HEART_RATE -> {
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
            HelpCaption("Health Connect gives Daybook one averaged oxygen-saturation reading per day, not each individual sample your band took.")
        }
        HealthCardKind.WEIGHT -> {
            val v = if (isDay) day?.weightKg else agg.weightKgAvg?.toFloat()
            DetailRow(if (isDay) "Weight" else "Average weight", v?.let { "${"%.1f".format(it)} kg" })
            if (isDay) {
                HelpCaption("This is the last reading Health Connect had for the day, if you weighed in more than once.")
                WeightReadingsList(state.dayWeightReadings)
            }
        }
        HealthCardKind.HYDRATION -> {
            val v = if (isDay) day?.hydrationMl else agg.hydrationMlAvg?.toFloat()
            DetailRow("Hydration", v?.let { "${"%.0f".format(it)} ml" })
            DetailRow("In litres", v?.let { "${"%.2f".format(it / 1000f)} L" })
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
