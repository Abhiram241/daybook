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

/**
 * Item 3 (Beast Mode round 2026-09-16) — the bundled equipment illustration for an [Equipment]
 * value, as the `<slug>` of `assets/exercises/images/equipment/<slug>.webp` (the same asset tree
 * `ExerciseThumbnail` already loads the RepDB exercise photos from, so there is one loader and one
 * code path, not a second `R.drawable` set). Returns `null` for the two values that must NOT get a
 * picture:
 *  - `NONE` is bodyweight: every candidate asset is a piece of apparatus (pull-up bar, dip
 *    station, rings), so a push-up would end up illustrated with a pull-up bar. It falls through
 *    to the muscle illustration instead, which is genuinely informative for bodyweight work.
 *  - `OTHER` means "we don't know yet". Any picture would be a lie, and the plain icon doubles as
 *    the "this exercise still needs its details filled in" cue.
 *
 * `MACHINE` deliberately maps to the chest-press machine: seat + back pad + handles + a visible
 * weight stack is the universal shorthand for "gym machine", where leg-press/hack-squat read as
 * leg-specific and smith-machine would collide with `BARBELL`.
 *
 * Only these 7 files are referenced; the rest of the bundled `equipment/` tree is unused.
 */
fun equipmentImageAsset(equipment: Equipment): String? = when (equipment) {
    Equipment.BARBELL -> "barbell"
    Equipment.DUMBBELL -> "dumbbell"
    Equipment.KETTLEBELL -> "kettlebell"
    Equipment.CABLE -> "cable"
    Equipment.PLATE -> "plates"
    Equipment.RESISTANCE_BAND -> "resistance-band"
    Equipment.MACHINE -> "chest-press-machine"
    Equipment.NONE, Equipment.OTHER -> null
}
