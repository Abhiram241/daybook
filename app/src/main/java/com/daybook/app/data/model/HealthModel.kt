package com.daybook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// Round B (Health Connect, §7.1 of HEALTH_AND_WORKOUT_PLAN.md). Two new tables, MIGRATION_23_24,
// AppDatabase v23 -> v24. Kept in their own file, same reasoning as WorkoutModel.kt (§3.2) — Room
// only cares about AppDatabase's entities list, not which file an @Entity lives in.
//
// Every metric column below is nullable and NONE may ever be given a NOT NULL DEFAULT (Ri3/R18,
// carried over from Round A) — null means "no data", not zero. A day with 0 steps recorded is not
// the same as a day the band was on the charger.

/** One row per local calendar day of aggregated Health Connect data. */
@Serializable
@Entity(tableName = "health_days")
data class HealthDay(
    @PrimaryKey @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "steps") val steps: Int? = null,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Float? = null,
    @ColumnInfo(name = "active_calories") val activeCalories: Float? = null,
    @ColumnInfo(name = "total_calories") val totalCalories: Float? = null,
    @ColumnInfo(name = "resting_heart_rate") val restingHeartRate: Int? = null,
    @ColumnInfo(name = "avg_heart_rate") val avgHeartRate: Int? = null,
    @ColumnInfo(name = "min_heart_rate") val minHeartRate: Int? = null,
    @ColumnInfo(name = "max_heart_rate") val maxHeartRate: Int? = null,
    @ColumnInfo(name = "sleep_minutes") val sleepMinutes: Int? = null,
    @ColumnInfo(name = "sleep_deep_minutes") val sleepDeepMinutes: Int? = null,
    @ColumnInfo(name = "sleep_light_minutes") val sleepLightMinutes: Int? = null,
    @ColumnInfo(name = "sleep_rem_minutes") val sleepRemMinutes: Int? = null,
    @ColumnInfo(name = "sleep_awake_minutes") val sleepAwakeMinutes: Int? = null,
    @ColumnInfo(name = "sleep_start_millis") val sleepStartMillis: Long? = null,
    @ColumnInfo(name = "sleep_end_millis") val sleepEndMillis: Long? = null,
    // OxygenSaturationRecord, daily average.
    @ColumnInfo(name = "spo2_percent") val spo2Percent: Float? = null,
    // HEALTH_VITALS_RICHNESS_PLAN.md V1/V2 — same shape as heart rate's min/max, reduced in
    // Kotlin from the same raw per-day record list the average above already comes from (no new
    // Health Connect read).
    @ColumnInfo(name = "spo2_min_percent") val spo2MinPercent: Float? = null,
    @ColumnInfo(name = "spo2_max_percent") val spo2MaxPercent: Float? = null,
    // WeightRecord, the day's actual last reading (by `time`). ALWAYS kg — same storage rule as
    // WorkoutSet.weightKg. HEALTH_VITALS_RICHNESS_PLAN.md V1: this used to be `WEIGHT_AVG` —
    // an average mislabelled as "last reading" — now genuinely the latest raw record's value,
    // with every reading that day also kept in [HealthWeightReading].
    @ColumnInfo(name = "weight_kg") val weightKg: Float? = null,
    // HydrationRecord, daily total.
    @ColumnInfo(name = "hydration_ml") val hydrationMl: Float? = null,
    // NutritionRecord, daily totals. Aggregates ONLY — per-meal rows would compete with
    // food_med_occurrences for the same conceptual space (§6.1.6).
    @ColumnInfo(name = "nutrition_calories") val nutritionCalories: Float? = null,
    @ColumnInfo(name = "nutrition_protein_grams") val nutritionProteinGrams: Float? = null,
    @ColumnInfo(name = "nutrition_carbs_grams") val nutritionCarbsGrams: Float? = null,
    @ColumnInfo(name = "nutrition_fat_grams") val nutritionFatGrams: Float? = null,
    // metadata.dataOrigin.packageName, for "From MyFitnessPal" (§6.1.6).
    @ColumnInfo(name = "nutrition_source_app") val nutritionSourceApp: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** One row per Health-Connect-recorded exercise session (a band workout). Never merged with
 *  [WorkoutSession] — §6.4 argues this at length. */
@Serializable
@Entity(
    tableName = "health_sessions",
    indices = [Index("local_date"), Index("start_millis")]
)
data class HealthSession(
    // Health Connect Record.metadata.id — stable, so a re-read upserts rather than duplicating.
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    // ExerciseSessionRecord.EXERCISE_TYPE_* constant, mapped to a label by ExerciseTypeLabels.
    @ColumnInfo(name = "exercise_type") val exerciseType: Int,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    @ColumnInfo(name = "active_calories") val activeCalories: Float? = null,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Float? = null,
    @ColumnInfo(name = "avg_heart_rate") val avgHeartRate: Int? = null,
    // metadata.dataOrigin.packageName, e.g. "com.mi.health".
    @ColumnInfo(name = "source_app") val sourceApp: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** One row per raw `WeightRecord` reading (HEALTH_VITALS_RICHNESS_PLAN.md §1/§2) — weight is a
 *  discrete, deliberate event (stepping on a scale), so it gets its own table like [HealthSession]
 *  rather than being squeezed into [HealthDay]'s columns. `HealthDay.weightKg` stays as a
 *  denormalised "last reading of the day" column so the fast common-path read (the grid card)
 *  still needs no join. */
@Serializable
@Entity(
    tableName = "health_weight_readings",
    indices = [Index("local_date")]
)
data class HealthWeightReading(
    // Health Connect Record.metadata.id — stable, so a re-read upserts rather than duplicating,
    // exactly like HealthSession.id.
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "at_millis") val atMillis: Long,
    // ALWAYS kg — same storage rule as HealthDay.weightKg / WorkoutSet.weightKg. NOT NULL here
    // (unlike HealthDay.weightKg) — a row's whole reason to exist is a reading that happened.
    @ColumnInfo(name = "weight_kg") val weightKg: Float,
    @ColumnInfo(name = "source_app") val sourceApp: String? = null
)
