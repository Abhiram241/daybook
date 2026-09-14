package com.daybook.app.data.health

import com.daybook.app.data.model.HealthDay

/**
 * B6 (§7.4 "Range mode") — the single pure, unit-tested choke point that decides mean-vs-sum per
 * metric, mirroring `columnsFor(trackingMode)`'s (§3.7.1) and Ri2's "one choke point" discipline
 * (R31). Nothing else in this codebase averages or sums a `HealthDay` column.
 */
data class HealthAggregate(
    val stepsAvg: Double?,
    val distanceMetersAvg: Double?,
    val activeCaloriesAvg: Double?,
    val totalCaloriesAvg: Double?,
    val avgHeartRateAvg: Double?,
    val minHeartRateAvg: Double?,
    val maxHeartRateAvg: Double?,
    val restingHeartRateAvg: Double?,
    val spo2PercentAvg: Double?,
    // HEALTH_VITALS_RICHNESS_PLAN.md §6 — same "Average"/"Range" pair as heart rate's own min/max.
    val spo2MinPercentAvg: Double?,
    val spo2MaxPercentAvg: Double?,
    val weightKgAvg: Double?,
    val hydrationMlAvg: Double?,
    val nutritionCaloriesAvg: Double?,
    val nutritionProteinGramsAvg: Double?,
    val nutritionCarbsGramsAvg: Double?,
    val nutritionFatGramsAvg: Double?,
    val nutritionSourceApp: String?,
    /** Sums, not means — §7.4: "a range's total... sleep hours" is the meaningful figure. */
    val sleepMinutesTotal: Int,
    val sleepDeepMinutesTotal: Int,
    val sleepLightMinutesTotal: Int,
    val sleepRemMinutesTotal: Int,
    val sleepAwakeMinutesTotal: Int,
    val hasAnySleep: Boolean,
    /** The aggregate sheet's "5 of 7 days have data" coverage line. */
    val daysWithAnyData: Int,
    val totalDays: Int
)

private fun avgOf(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()

/** Pure — no I/O, no Health Connect call. §7.4/R31. */
fun aggregateHealthDays(days: List<HealthDay>): HealthAggregate {
    fun doubles(sel: (HealthDay) -> Number?): List<Double> = days.mapNotNull { sel(it)?.toDouble() }

    val daysWithAnyData = days.count { d ->
        d.steps != null || d.distanceMeters != null || d.activeCalories != null || d.totalCalories != null ||
            d.avgHeartRate != null || d.restingHeartRate != null || d.sleepMinutes != null ||
            d.spo2Percent != null || d.weightKg != null || d.hydrationMl != null || d.nutritionCalories != null
    }

    return HealthAggregate(
        stepsAvg = avgOf(doubles { it.steps }),
        distanceMetersAvg = avgOf(doubles { it.distanceMeters }),
        activeCaloriesAvg = avgOf(doubles { it.activeCalories }),
        totalCaloriesAvg = avgOf(doubles { it.totalCalories }),
        avgHeartRateAvg = avgOf(doubles { it.avgHeartRate }),
        minHeartRateAvg = avgOf(doubles { it.minHeartRate }),
        maxHeartRateAvg = avgOf(doubles { it.maxHeartRate }),
        restingHeartRateAvg = avgOf(doubles { it.restingHeartRate }),
        spo2PercentAvg = avgOf(doubles { it.spo2Percent }),
        spo2MinPercentAvg = avgOf(doubles { it.spo2MinPercent }),
        spo2MaxPercentAvg = avgOf(doubles { it.spo2MaxPercent }),
        weightKgAvg = avgOf(doubles { it.weightKg }),
        hydrationMlAvg = avgOf(doubles { it.hydrationMl }),
        nutritionCaloriesAvg = avgOf(doubles { it.nutritionCalories }),
        nutritionProteinGramsAvg = avgOf(doubles { it.nutritionProteinGrams }),
        nutritionCarbsGramsAvg = avgOf(doubles { it.nutritionCarbsGrams }),
        nutritionFatGramsAvg = avgOf(doubles { it.nutritionFatGrams }),
        nutritionSourceApp = days.firstNotNullOfOrNull { it.nutritionSourceApp },
        sleepMinutesTotal = days.sumOf { it.sleepMinutes ?: 0 },
        sleepDeepMinutesTotal = days.sumOf { it.sleepDeepMinutes ?: 0 },
        sleepLightMinutesTotal = days.sumOf { it.sleepLightMinutes ?: 0 },
        sleepRemMinutesTotal = days.sumOf { it.sleepRemMinutes ?: 0 },
        sleepAwakeMinutesTotal = days.sumOf { it.sleepAwakeMinutes ?: 0 },
        hasAnySleep = days.any { it.sleepMinutes != null },
        daysWithAnyData = daysWithAnyData,
        totalDays = days.size
    )
}

/** §7.4's per-metric hide rule / R32 — the one choke point that decides which cards render, in
 *  both `Day` and `Range` mode. A card renders only if it has SOMETHING to show; never an empty
 *  or dashed placeholder. */
enum class HealthCardKind { STEPS, CALORIES, HEART_RATE, SLEEP, SPO2, WEIGHT, HYDRATION, NUTRITION, SESSIONS }

/** User-facing label for the Health tab's "hide cards" picker — mirrors the card titles used in
 *  `HealthTabScreen`'s `HealthCardSpec` list (Activity/Calories/Heart rate/Sleep/Oxygen/Weight/
 *  Hydration/Nutrition/band sessions). */
fun HealthCardKind.displayLabel(): String = when (this) {
    HealthCardKind.STEPS -> "Activity"
    HealthCardKind.CALORIES -> "Calories"
    HealthCardKind.HEART_RATE -> "Heart rate"
    HealthCardKind.SLEEP -> "Sleep"
    HealthCardKind.SPO2 -> "Oxygen"
    HealthCardKind.WEIGHT -> "Weight"
    HealthCardKind.HYDRATION -> "Hydration"
    HealthCardKind.NUTRITION -> "Nutrition"
    HealthCardKind.SESSIONS -> "Band sessions"
}

/** Parse a `health_hidden_cards` CSV column into a set of hidden kinds. Blank/unknown tokens are
 *  dropped — a corrupt or stale value degrades to "nothing hidden", never a crash. */
fun parseHiddenHealthCards(csv: String?): Set<HealthCardKind> =
    csv.orEmpty().split(",").mapNotNull { token ->
        runCatching { HealthCardKind.valueOf(token.trim()) }.getOrNull()
    }.toSet()

/** Serialize a hidden-card set back to the CSV form [parseHiddenHealthCards] reads. */
fun Set<HealthCardKind>.toHiddenCardsCsv(): String = joinToString(",") { it.name }

/** `Day` mode. */
fun visibleHealthCards(day: HealthDay?, hasSessions: Boolean): Set<HealthCardKind> {
    val out = LinkedHashSet<HealthCardKind>()
    if (day != null) {
        if (day.steps != null || day.distanceMeters != null) out += HealthCardKind.STEPS
        if (day.activeCalories != null || day.totalCalories != null) out += HealthCardKind.CALORIES
        if (day.avgHeartRate != null || day.restingHeartRate != null || day.minHeartRate != null || day.maxHeartRate != null) {
            out += HealthCardKind.HEART_RATE
        }
        if (day.sleepMinutes != null) out += HealthCardKind.SLEEP
        if (day.spo2Percent != null) out += HealthCardKind.SPO2
        if (day.weightKg != null) out += HealthCardKind.WEIGHT
        if (day.hydrationMl != null) out += HealthCardKind.HYDRATION
        if (day.nutritionCalories != null || day.nutritionProteinGrams != null ||
            day.nutritionCarbsGrams != null || day.nutritionFatGrams != null
        ) {
            out += HealthCardKind.NUTRITION
        }
    }
    if (hasSessions) out += HealthCardKind.SESSIONS
    return out
}

/** `Range` mode — a metric present on some days and absent on others still renders; only a metric
 *  absent on EVERY day in the range is hidden. */
fun visibleHealthCards(agg: HealthAggregate, hasSessions: Boolean): Set<HealthCardKind> {
    val out = LinkedHashSet<HealthCardKind>()
    if (agg.stepsAvg != null || agg.distanceMetersAvg != null) out += HealthCardKind.STEPS
    if (agg.activeCaloriesAvg != null || agg.totalCaloriesAvg != null) out += HealthCardKind.CALORIES
    if (agg.avgHeartRateAvg != null || agg.restingHeartRateAvg != null || agg.minHeartRateAvg != null || agg.maxHeartRateAvg != null) {
        out += HealthCardKind.HEART_RATE
    }
    if (agg.hasAnySleep) out += HealthCardKind.SLEEP
    if (agg.spo2PercentAvg != null) out += HealthCardKind.SPO2
    if (agg.weightKgAvg != null) out += HealthCardKind.WEIGHT
    if (agg.hydrationMlAvg != null) out += HealthCardKind.HYDRATION
    if (agg.nutritionCaloriesAvg != null || agg.nutritionProteinGramsAvg != null ||
        agg.nutritionCarbsGramsAvg != null || agg.nutritionFatGramsAvg != null
    ) {
        out += HealthCardKind.NUTRITION
    }
    if (hasSessions) out += HealthCardKind.SESSIONS
    return out
}
