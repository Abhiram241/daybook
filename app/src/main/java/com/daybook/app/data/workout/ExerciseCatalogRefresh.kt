package com.daybook.app.data.workout

/**
 * User request — three things, all driven by the user's own exported exercise spreadsheet
 * (`exercises_updated_v2.csv`, cross-checked against their real Hevy import history):
 *
 * 1. Rename 39 builtin exercises so the catalog reads with the same naming convention the user's
 *    own Hevy history already uses — the exact convention [HEVY_ALIASES] in ExerciseCatalog.kt
 *    encodes for import-matching, just not previously reflected in the catalog's own display
 *    names.
 * 2. Add 17 exercises that convention names but the builtin catalog has no entry for — several
 *    of these are genuinely distinct from an existing builtin even though [HEVY_ALIASES]
 *    currently resolves both Hevy titles to the same import target (e.g. "Lat Pulldown
 *    (Machine)" is a different rig from the cable "Lat Pulldown (Cable)" builtin, even though
 *    both Hevy CSV titles alias to the same `lat-pulldown` id for import purposes).
 * 3. Clean up any duplicate custom-exercise row an earlier Hevy import auto-created for a title
 *    that didn't resolve via [HEVY_ALIASES] at the time, and that now collides with one of these
 *    renames/additions — see `WorkoutRepository.refreshExerciseCatalog`, which merges (reassigns
 *    every block/set/routine-slot reference, never bare-deletes) rather than just deleting.
 */
data class ExerciseAdditionDraft(
    val name: String,
    val muscle: MuscleGroup,
    val equipment: Equipment,
    val trackingMode: String
)

/** Old builtin catalog name -> the name to rename it to. Matched against
 *  [ExerciseCatalog.builtins]'s STATIC (never-overridden) name, so this stays idempotent no
 *  matter how many times [WorkoutRepository] re-applies it across app runs. */
val EXERCISE_RENAME_MAP: Map<String, String> = mapOf(
    "Treadmill Running" to "Treadmill",
    "Machine Seated Crunch" to "Crunch (Machine)",
    "Barbell Preacher Curl" to "Preacher Curl (Barbell)",
    "Cable Curl" to "Bicep Curl (Cable)",
    "Dumbbell Bicep Curl" to "Bicep Curl (Dumbbell)",
    "Incline Dumbbell Curl" to "Seated Incline Curl (Dumbbell)",
    "Machine Preacher Curl" to "Preacher Curl (Machine)",
    "Reverse Grip Lat Pulldown" to "Reverse Grip Lat Pulldown (Cable)",
    "Smith Machine Calf Raise" to "Standing Calf Raise (Smith)",
    "Cable Fly" to "Seated Chest Flys (Cable)",
    "Dumbbell Bench Press" to "Bench Press (Dumbbell)",
    "Incline Dumbbell Press" to "Incline Bench Press (Dumbbell)",
    "Machine Chest Fly" to "Chest Fly (Machine)",
    "Machine Chest Press" to "Chest Press (Machine)",
    "Cable Wrist Curl" to "Wrist Curl ( Cable )",
    "Barbell Back Squat" to "Squat (Barbell)",
    "Barbell Hip Thrust" to "Hip Thrust (Barbell)",
    "Bodyweight Squat" to "Squat (Bodyweight)",
    "Dumbbell Romanian Deadlift" to "Romanian Deadlift (Dumbbell)",
    "Dumbbell Squat" to "Squat (Dumbbell)",
    "Leg Press" to "Leg Press (Machine)",
    "Smith Machine Squat" to "Squat (Smith Machine)",
    "Lying Leg Curl" to "Lying Leg Curl (Machine)",
    "Seated Leg Curl" to "Seated Leg Curl (Machine)",
    "Lat Pulldown" to "Lat Pulldown (Cable)",
    "Pull-Up" to "Pull Up",
    "Seated Cable Row" to "Seated Cable Row - V Grip (Cable)",
    "Leg Extension" to "Leg Extension (Machine)",
    "Cable Lateral Raise" to "Lateral Raise (Cable)",
    "Dumbbell Lateral Raise" to "Lateral Raise (Dumbbell)",
    "Machine Shoulder Press" to "Seated Shoulder Press (Machine)",
    "Seated Dumbbell Lateral Raise" to "Seated Lateral Raise (Dumbbell)",
    "Smith Machine Incline Bench Press" to "Incline Bench Press (Smith Machine)",
    "Dumbbell Shrug" to "Shrug (Dumbbell)",
    "Smith Machine Shrug" to "Shrug (Smith Machine)",
    "Cable Tricep Kickback" to "Triceps Kickback (Cable)",
    "Cable Tricep Pushdown" to "Triceps Pushdown",
    "Dumbbell Tricep Extension" to "Triceps Extension (Dumbbell)",
    "Single-Arm Dumbbell Overhead Tricep Extension" to "Single Arm Tricep Extension (Dumbbell)",
)

/** Exercises added as genuine custom rows (source USER) — resolved by current display name, so
 *  re-running after [EXERCISE_RENAME_MAP] has already applied never double-adds one. */
val EXERCISE_ADDITIONS: List<ExerciseAdditionDraft> = listOf(
    ExerciseAdditionDraft("Cycling", MuscleGroup.CARDIO, Equipment.OTHER, "DISTANCE_DURATION"),
    ExerciseAdditionDraft("Hiking", MuscleGroup.CARDIO, Equipment.NONE, "DISTANCE_DURATION"),
    ExerciseAdditionDraft("Decline Crunch (Weighted)", MuscleGroup.ABDOMINALS, Equipment.PLATE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Hanging Leg Raises ( Weighted )", MuscleGroup.ABDOMINALS, Equipment.NONE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Lying Leg Raises (Weighted)", MuscleGroup.ABDOMINALS, Equipment.PLATE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Tricep Supported Bicep Curls ( Dumbbell )", MuscleGroup.BICEPS, Equipment.DUMBBELL, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Reverse Wrist Curl ( Cable )", MuscleGroup.FOREARMS, Equipment.CABLE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Seated Palms Up Wrist Curl", MuscleGroup.FOREARMS, Equipment.BARBELL, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Seated Wrist Extension (Barbell)", MuscleGroup.FOREARMS, Equipment.BARBELL, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Lat Pulldown (Machine)", MuscleGroup.LATS, Equipment.MACHINE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Seated Row (Machine)", MuscleGroup.LATS, Equipment.MACHINE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Iso-Lateral Row (Machine)", MuscleGroup.LATS, Equipment.MACHINE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Single Arm Cable Row", MuscleGroup.LATS, Equipment.CABLE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Deadlift (Smith Machine)", MuscleGroup.LOWER_BACK, Equipment.MACHINE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Rear Delt Reverse Fly (Machine)", MuscleGroup.SHOULDERS, Equipment.MACHINE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Single Arm Lateral Raise (Cable)", MuscleGroup.SHOULDERS, Equipment.CABLE, "WEIGHT_REPS"),
    ExerciseAdditionDraft("Shrug (Cable)", MuscleGroup.TRAPS, Equipment.CABLE, "WEIGHT_REPS"),
)
