package com.daybook.app.data.health

import com.daybook.app.data.model.HealthDay
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.formatWeight
import com.daybook.app.util.formatHealthCalories
import com.daybook.app.util.formatHealthCount
import com.daybook.app.util.formatHealthDistanceKm
import com.daybook.app.util.formatHealthDuration

/** HEALTH_CARDS §3.4 — one metric line: [label] muted, [value] the number, [unit] shown smaller
 *  and muted next to it (e.g. value="62" unit="steps", value="95" unit="%"). [unit] is null when
 *  the value string already carries its own unit (e.g. "1,200 kcal", "45 min"). */
data class HealthMetric(val label: String, val value: String, val unit: String? = null)

enum class HealthCardDisplayMode { DAY, RANGE }

/**
 * AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §3.4 — the single place that decides a card's
 * labels, units and which values exist, shared by the Health tab and the Daily Report Health
 * section so the same numbers read identically on both screens (§3.2/§3.3). Pure, no I/O.
 * Returns an empty list when [kind] has nothing to show for the given day/aggregate — the caller
 * (`visibleHealthCards`) already gates on that, this just mirrors it defensively.
 */
fun metricsFor(
    kind: HealthCardKind,
    day: HealthDay?,
    agg: HealthAggregate?,
    mode: HealthCardDisplayMode,
    weightUnit: WeightUnit
): List<HealthMetric> {
    val isRange = mode == HealthCardDisplayMode.RANGE
    return when (kind) {
        HealthCardKind.STEPS -> buildList {
            val steps = if (isRange) agg?.stepsAvg?.let { formatHealthCount(it.toLong()) } else day?.steps?.let { formatHealthCount(it) }
            val dist = if (isRange) agg?.distanceMetersAvg?.let { formatHealthDistanceKm(it.toFloat()) } else day?.distanceMeters?.let { formatHealthDistanceKm(it) }
            steps?.let { add(HealthMetric("Steps", it)) }
            dist?.let { add(HealthMetric("Distance", it)) }
        }
        HealthCardKind.CALORIES -> buildList {
            val active = if (isRange) agg?.activeCaloriesAvg?.let { formatHealthCalories(it.toFloat()) } else day?.activeCalories?.let { formatHealthCalories(it) }
            val total = if (isRange) agg?.totalCaloriesAvg?.let { formatHealthCalories(it.toFloat()) } else day?.totalCalories?.let { formatHealthCalories(it) }
            active?.let { add(HealthMetric("Active", it)) }
            total?.let { add(HealthMetric("Total", it)) }
        }
        HealthCardKind.HEART_RATE -> buildList {
            val avg = if (isRange) agg?.avgHeartRateAvg?.let { "${it.toInt()} bpm" } else day?.avgHeartRate?.let { "$it bpm" }
            val range = if (isRange) {
                if (agg?.minHeartRateAvg != null && agg.maxHeartRateAvg != null) {
                    "${agg.minHeartRateAvg.toInt()}–${agg.maxHeartRateAvg.toInt()} bpm"
                } else null
            } else {
                if (day?.minHeartRate != null && day.maxHeartRate != null) "${day.minHeartRate}–${day.maxHeartRate} bpm" else null
            }
            avg?.let { add(HealthMetric("Average", it)) }
            range?.let { add(HealthMetric("Range", it)) }
        }
        HealthCardKind.SLEEP -> buildList {
            val total = if (isRange) agg?.sleepMinutesTotal?.takeIf { agg.hasAnySleep }?.let { formatHealthDuration(it) }
                else day?.sleepMinutes?.let { formatHealthDuration(it) }
            val deep = if (isRange) agg?.sleepDeepMinutesTotal?.takeIf { agg.hasAnySleep }?.let { formatHealthDuration(it) }
                else day?.sleepDeepMinutes?.let { formatHealthDuration(it) }
            total?.let { add(HealthMetric("Total", it)) }
            deep?.let { add(HealthMetric("Deep", it)) }
        }
        HealthCardKind.SPO2 -> buildList {
            val avg = if (isRange) agg?.spo2PercentAvg else day?.spo2Percent?.toDouble()
            val min = if (isRange) agg?.spo2MinPercentAvg else day?.spo2MinPercent?.toDouble()
            val max = if (isRange) agg?.spo2MaxPercentAvg else day?.spo2MaxPercent?.toDouble()
            avg?.let { add(HealthMetric("Average", "%.0f".format(it), "%")) }
            if (min != null && max != null) {
                add(HealthMetric("Range", "${"%.0f".format(min)}–${"%.0f".format(max)}%"))
            }
        }
        HealthCardKind.WEIGHT -> buildList {
            val v = if (isRange) agg?.weightKgAvg?.toFloat() else day?.weightKg
            v?.let { add(HealthMetric("Weight", formatWeight(it, weightUnit))) }
        }
        HealthCardKind.HYDRATION -> buildList {
            val v = if (isRange) agg?.hydrationMlAvg?.toFloat() else day?.hydrationMl
            v?.let { add(HealthMetric("Hydration", "%.1f".format(it / 1000f), "L")) }
        }
        HealthCardKind.NUTRITION -> buildList {
            val cal = if (isRange) agg?.nutritionCaloriesAvg?.let { formatHealthCalories(it.toFloat()) } else day?.nutritionCalories?.let { formatHealthCalories(it) }
            val protein = if (isRange) agg?.nutritionProteinGramsAvg else day?.nutritionProteinGrams?.toDouble()
            val carbs = if (isRange) agg?.nutritionCarbsGramsAvg else day?.nutritionCarbsGrams?.toDouble()
            val fat = if (isRange) agg?.nutritionFatGramsAvg else day?.nutritionFatGrams?.toDouble()
            cal?.let { add(HealthMetric("Calories", it)) }
            protein?.let { add(HealthMetric("Protein", "%.0f".format(it), "g")) }
            carbs?.let { add(HealthMetric("Carbs", "%.0f".format(it), "g")) }
            fat?.let { add(HealthMetric("Fat", "%.0f".format(it), "g")) }
        }
        HealthCardKind.SESSIONS -> emptyList()
    }
}

/** §3.2 — "Daily avg" for most range-mode cards, "Total" for Sleep (which already sums). */
fun rangeSubtitleFor(kind: HealthCardKind): String = if (kind == HealthCardKind.SLEEP) "Total" else "Daily avg"
