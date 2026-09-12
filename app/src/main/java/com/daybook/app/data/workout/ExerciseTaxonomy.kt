package com.daybook.app.data.workout

/**
 * A2 (§3.3.1) — the two picker axes. Persisted by `.name` on `Exercise.primaryMuscle` /
 * `.equipment` (plain String columns, no Room converter), so an unknown future value degrades to
 * OTHER rather than throwing. Both enums are exhaustive-by-design: `MuscleGroupLabels` /
 * `EquipmentLabels` are total maps, unit-tested for totality (`ExerciseCatalogTest`), so adding a
 * value cannot ship a screen reading `UPPER_BACK` at the user.
 */
enum class MuscleGroup {
    ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CARDIO, CHEST, FOREARMS,
    FULL_BODY, GLUTES, HAMSTRINGS, LATS, LOWER_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS,
    UPPER_BACK, OTHER
}

/**
 * `NONE` is the bodyweight case (plank, push-up, pull-up, and any "bodyweight-aid" apparatus like
 * a pull-up bar, dip station, gymnastic rings or suspension trainer) — deliberately not a separate
 * `BODYWEIGHT` value, because that would hide every bodyweight exercise from the `NONE` filter.
 */
enum class Equipment {
    NONE, BARBELL, DUMBBELL, KETTLEBELL, MACHINE, CABLE, PLATE, RESISTANCE_BAND, OTHER
}

/** Human-readable label per [MuscleGroup] — a total map (§3.3.1). */
val MuscleGroupLabels: Map<MuscleGroup, String> = mapOf(
    MuscleGroup.ABDOMINALS to "Abdominals",
    MuscleGroup.ABDUCTORS to "Abductors",
    MuscleGroup.ADDUCTORS to "Adductors",
    MuscleGroup.BICEPS to "Biceps",
    MuscleGroup.CALVES to "Calves",
    MuscleGroup.CARDIO to "Cardio",
    MuscleGroup.CHEST to "Chest",
    MuscleGroup.FOREARMS to "Forearms",
    MuscleGroup.FULL_BODY to "Full body",
    MuscleGroup.GLUTES to "Glutes",
    MuscleGroup.HAMSTRINGS to "Hamstrings",
    MuscleGroup.LATS to "Lats",
    MuscleGroup.LOWER_BACK to "Lower back",
    MuscleGroup.NECK to "Neck",
    MuscleGroup.QUADRICEPS to "Quadriceps",
    MuscleGroup.SHOULDERS to "Shoulders",
    MuscleGroup.TRAPS to "Traps",
    MuscleGroup.TRICEPS to "Triceps",
    MuscleGroup.UPPER_BACK to "Upper back",
    MuscleGroup.OTHER to "Other"
)

/** Human-readable label per [Equipment] — a total map (§3.3.1). */
val EquipmentLabels: Map<Equipment, String> = mapOf(
    Equipment.NONE to "No equipment",
    Equipment.BARBELL to "Barbell",
    Equipment.DUMBBELL to "Dumbbell",
    Equipment.KETTLEBELL to "Kettlebell",
    Equipment.MACHINE to "Machine",
    Equipment.CABLE to "Cable",
    Equipment.PLATE to "Plate",
    Equipment.RESISTANCE_BAND to "Resistance band",
    Equipment.OTHER to "Other"
)
