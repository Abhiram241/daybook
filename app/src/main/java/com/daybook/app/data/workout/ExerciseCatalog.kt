package com.daybook.app.data.workout

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A2 (§3.3) — the built-in exercise catalog: a licensed RepDB free-tier asset
 * (`app/src/main/assets/exercises/repdb.json`, RepDB's own `free.en.json` shipped verbatim),
 * parsed and projected at runtime — never hand-typed Kotlin, never Room-seeded rows.
 *
 * `builtin:` ids are RepDB's own slugs and are permanently stable (Ri1): once written into a
 * `workout_sets` row, an id is never renamed.
 */
const val BUILTIN_ID_PREFIX = "builtin:"

data class BuiltinExercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val trackingMode: String,
    /** RepDB's id, or its `image_alias` when present. Build image paths from THIS, never from
     *  [id] with the prefix stripped naively, in case the two ever diverge (§3.3.3). */
    val imageId: String,
    /** true -> "<imageId>-start"/"-peak"; false -> "<imageId>-main". */
    val hasStartPeak: Boolean
)

// ------------------------------------------------------------------ RepDB's own wire schema
// Mirrors only the fields the derivation functions and the catalog need — kotlinx.serialization's
// `ignoreUnknownKeys = true` (set below) tolerates every other RepDB field (description,
// instructions, tips, synonyms, met, ...) without a matching property.

@Serializable
internal data class RepDbRoot(
    val exercises: List<RepDbExercise> = emptyList()
)

@Serializable
internal data class RepDbExercise(
    val id: String,
    val name: String,
    val category: String,
    val force_type: String? = null,
    val equipment: String? = null,
    val body_part: String? = null,
    val primary_muscles: List<String> = emptyList(),
    val is_bodyweight: Boolean = false,
    val image_alias: String? = null,
    val images: RepDbImages = RepDbImages()
)

@Serializable
internal data class RepDbImages(
    val flat: List<String> = emptyList()
)

// ------------------------------------------------------------------ derivation (§3.3.2)

/** Cardio equipment that yields a DISTANCE reading, not just a duration (§3.3.2). */
val DISTANCE_CAPABLE_CARDIO_EQUIPMENT: Set<String> = setOf(
    "treadmill", "stationary_bike", "elliptical", "stair_climber", "rower", "air_bike"
)

internal fun deriveMuscleGroup(ex: RepDbExercise): MuscleGroup = when {
    ex.category == "cardio" -> MuscleGroup.CARDIO
    ex.body_part == "full_body" -> MuscleGroup.FULL_BODY
    else -> REPDB_MUSCLE_MAP[ex.primary_muscles.firstOrNull()] ?: MuscleGroup.OTHER
}

internal fun deriveEquipment(ex: RepDbExercise): Equipment =
    ex.equipment?.let { REPDB_EQUIPMENT_MAP[it] } ?: Equipment.NONE   // absent == is_bodyweight

internal fun deriveTrackingMode(ex: RepDbExercise): String = when {
    ex.category == "cardio" ->
        if (ex.equipment in DISTANCE_CAPABLE_CARDIO_EQUIPMENT) "DISTANCE_DURATION" else "DURATION"
    ex.force_type == "static" && ex.images.flat == listOf("main") -> "DURATION"
    ex.is_bodyweight -> "REPS_ONLY"
    else -> "WEIGHT_REPS"
}

/**
 * Every RepDB muscle key the free tier uses, mapped to one Daybook [MuscleGroup] (§3.3.2).
 * `[key] ?: OTHER` at the call site, not an exhaustive `when` — a future RepDB catalog update
 * that adds a new muscle key degrades gracefully instead of crashing the build.
 *
 * A2 deviation from the plan's literal table (documented in HEALTH_AND_WORKOUT_PROGRESS.md):
 * `lateral_deltoid` is present in the real free-tier data (15 exercises use it as their primary
 * muscle) but is absent from the plan's printed REPDB_MUSCLE_MAP. Mapped to SHOULDERS — the
 * obviously-intended bucket, matching `anterior_deltoid` / `posterior_deltoid` immediately above
 * and below it — rather than left to silently fall through to OTHER.
 */
internal val REPDB_MUSCLE_MAP: Map<String, MuscleGroup> = mapOf(
    "abductors" to MuscleGroup.ABDUCTORS,
    "adductors" to MuscleGroup.ADDUCTORS,
    "anterior_deltoid" to MuscleGroup.SHOULDERS,
    "lateral_deltoid" to MuscleGroup.SHOULDERS,
    "biceps_brachii" to MuscleGroup.BICEPS,
    "brachialis" to MuscleGroup.BICEPS,
    "brachioradialis" to MuscleGroup.FOREARMS,
    "erector_spinae" to MuscleGroup.LOWER_BACK,
    "forearms" to MuscleGroup.FOREARMS,
    "forearm_extensors" to MuscleGroup.FOREARMS,
    "forearm_flexors" to MuscleGroup.FOREARMS,
    "gastrocnemius" to MuscleGroup.CALVES,
    "gluteus_maximus" to MuscleGroup.GLUTES,
    "gluteus_medius" to MuscleGroup.GLUTES,
    "hamstrings" to MuscleGroup.HAMSTRINGS,
    "hip_flexors" to MuscleGroup.ABDOMINALS,   // RepDB files it under `region: core`
    "latissimus_dorsi" to MuscleGroup.LATS,
    "quadratus_lumborum" to MuscleGroup.LOWER_BACK,
    "obliques" to MuscleGroup.ABDOMINALS,
    "pectoralis_major" to MuscleGroup.CHEST,
    "posterior_deltoid" to MuscleGroup.SHOULDERS,
    "quadriceps" to MuscleGroup.QUADRICEPS,
    "rectus_abdominis" to MuscleGroup.ABDOMINALS,
    "rhomboids" to MuscleGroup.UPPER_BACK,
    "serratus_anterior" to MuscleGroup.CHEST,
    "supraspinatus" to MuscleGroup.SHOULDERS,
    "soleus" to MuscleGroup.CALVES,
    "transverse_abdominis" to MuscleGroup.ABDOMINALS,
    "trapezius" to MuscleGroup.TRAPS,
    "triceps_brachii" to MuscleGroup.TRICEPS
)

/** Every RepDB equipment key used by an exercise in the free tier, mapped to one Daybook
 *  [Equipment] (§3.3.2). Same graceful-fallback rule as [REPDB_MUSCLE_MAP]. */
internal val REPDB_EQUIPMENT_MAP: Map<String, Equipment> = mapOf(
    "barbell" to Equipment.BARBELL,
    "ez_bar" to Equipment.BARBELL,
    "trap_bar" to Equipment.BARBELL,
    "landmine" to Equipment.BARBELL,
    "dumbbell" to Equipment.DUMBBELL,
    "kettlebell" to Equipment.KETTLEBELL,
    "cable" to Equipment.CABLE,
    "plates" to Equipment.PLATE,
    "resistance_band" to Equipment.RESISTANCE_BAND,
    "loop_band" to Equipment.RESISTANCE_BAND,
    "ab_crunch_machine" to Equipment.MACHINE,
    "assisted_pullup_machine" to Equipment.MACHINE,
    "back_extension_machine" to Equipment.MACHINE,
    "bicep_curl_machine" to Equipment.MACHINE,
    "chest_fly_machine" to Equipment.MACHINE,
    "chest_press_machine" to Equipment.MACHINE,
    "dip_machine" to Equipment.MACHINE,
    "donkey_calf_raise_machine" to Equipment.MACHINE,
    "glute_ham_developer" to Equipment.MACHINE,
    "elliptical" to Equipment.MACHINE,
    "stair_climber" to Equipment.MACHINE,
    "treadmill" to Equipment.MACHINE,
    "hack_squat" to Equipment.MACHINE,
    "hip_abduction_machine" to Equipment.MACHINE,
    "hip_adduction_machine" to Equipment.MACHINE,
    "hip_thrust_machine" to Equipment.MACHINE,
    "lat_pulldown_machine" to Equipment.MACHINE,
    "lateral_raise_machine" to Equipment.MACHINE,
    "leg_curl" to Equipment.MACHINE,
    "leg_extension" to Equipment.MACHINE,
    "leg_press" to Equipment.MACHINE,
    "pec_deck" to Equipment.MACHINE,
    "plate_loaded_lateral_raise_machine" to Equipment.MACHINE,
    "preacher_curl_machine" to Equipment.MACHINE,
    "seated_calf_raise_machine" to Equipment.MACHINE,
    "shoulder_press_machine" to Equipment.MACHINE,
    "shrug_machine" to Equipment.MACHINE,
    "smith_machine" to Equipment.MACHINE,
    "standing_calf_raise_machine" to Equipment.MACHINE,
    "tricep_extension_machine" to Equipment.MACHINE,
    "air_bike" to Equipment.MACHINE,
    "rower" to Equipment.MACHINE,
    "stationary_bike" to Equipment.MACHINE,
    "pull_up_bar" to Equipment.NONE,
    "dip_station" to Equipment.NONE,
    "rings" to Equipment.NONE,
    "suspension_trainer" to Equipment.NONE,
    "climbing_rope" to Equipment.NONE,
    "ab_wheel" to Equipment.OTHER,
    "medicine_ball" to Equipment.OTHER,
    "battle_rope" to Equipment.OTHER,
    "slam_ball" to Equipment.OTHER,
    "jump_rope" to Equipment.OTHER,
    "stability_ball" to Equipment.OTHER,
    "plyo_box" to Equipment.OTHER,
    "wrist_roller" to Equipment.OTHER,
    "sled" to Equipment.OTHER,
    "decline_bench" to Equipment.OTHER,
    "incline_bench" to Equipment.OTHER,
    "flat_bench" to Equipment.OTHER
)

/**
 * §3.9.4 — the hand-written alias table Hevy-name matching falls back to after an exact
 * normalised-name match fails. Checked against the user's real 4,734-row export: of its 62
 * distinct `exercise_title` values, 54 resolve via this table to a real RepDB-backed catalog id;
 * the remaining 8 fall through to an auto-created custom exercise (§3.9.4), which is the designed,
 * correct outcome for a name this table cannot confidently resolve — not a gap to patch. Keys are
 * Hevy's `exercise_title` verbatim; values are bare RepDB slugs (no "builtin:" prefix — the
 * caller adds it).
 */
val HEVY_ALIASES: Map<String, String> = mapOf(
    "Bicep Curl (Cable)" to "cable-curl",
    "Bicep Curl (Dumbbell)" to "bicep-curl",
    "Cable Crunch" to "cable-crunch",
    "Chest Fly (Machine)" to "machine-chest-fly",
    "Chest Press (Machine)" to "chest-press-machine",
    "Crunch (Machine)" to "machine-seated-crunch",
    "Dead Hang" to "dead-hang",
    "Decline Crunch" to "decline-crunch",
    "Decline Crunch (Weighted)" to "decline-crunch",
    "Hanging Leg Raise" to "hanging-leg-raise",
    "Hanging Leg Raises (Weighted)" to "hanging-leg-raise",
    "Hip Thrust (Barbell)" to "hip-thrust",
    "Incline Bench Press (Dumbbell)" to "incline-db-press",
    "Incline Bench Press (Smith Machine)" to "smith-machine-incline-bench-press",
    "Iso-Lateral Chest Press (Machine)" to "chest-press-machine",
    "Lat Pulldown (Cable)" to "lat-pulldown",
    "Lat Pulldown (Machine)" to "lat-pulldown",
    "Lateral Raise (Cable)" to "cable-lateral-raise",
    "Lateral Raise (Dumbbell)" to "lateral-raise",
    "Leg Extension (Machine)" to "leg-extension",
    "Leg Press (Machine)" to "leg-press",
    "Lying Leg Curl (Machine)" to "leg-curl",
    "Lying Leg Raise" to "lying-leg-raise",
    "Lying Leg Raises (Weighted)" to "lying-leg-raise",
    "Preacher Curl (Barbell)" to "barbell-preacher-curl",
    "Preacher Curl (Machine)" to "machine-preacher-curl",
    "Pull Up" to "pull-up",
    "Rear Delt Reverse Fly (Machine)" to "rear-delt-fly",
    "Romanian Deadlift (Dumbbell)" to "dumbbell-romanian-deadlift",
    "Rowing Machine" to "rowing-machine",
    "Seated Cable Row - V Grip (Cable)" to "seated-cable-row",
    "Seated Calf Raise" to "seated-calf-raise",
    "Seated Chest Flys (Cable)" to "cable-fly",
    "Seated Incline Curl (Dumbbell)" to "incline-db-curl",
    "Seated Lateral Raise (Dumbbell)" to "seated-dumbbell-lateral-raise",
    "Seated Leg Curl (Machine)" to "seated-leg-curl",
    "Seated Palms Up Wrist Curl" to "wrist-curl",
    "Seated Shoulder Press (Machine)" to "machine-shoulder-press",
    "Shrug (Dumbbell)" to "db-shrug",
    "Shrug (Smith Machine)" to "smith-machine-shrug",
    "Single Arm Lateral Raise (Cable)" to "cable-lateral-raise",
    "Squat (Bodyweight)" to "bodyweight-squat",
    "Squat (Dumbbell)" to "db-squat",
    "Squat (Smith Machine)" to "smith-machine-squat",
    "Standing Calf Raise" to "standing-calf-raise",
    "Standing Calf Raise (Smith)" to "standing-calf-raise",
    "Treadmill" to "treadmill-running",
    "Tricep Supported Bicep Curls (Dumbbell)" to "incline-db-curl",
    "Triceps Extension (Cable)" to "tricep-pushdown",
    "Triceps Extension (Dumbbell)" to "dumbbell-tricep-extension",
    "Triceps Kickback (Cable)" to "cable-tricep-kickback",
    "Triceps Pushdown" to "tricep-pushdown",
    "Triceps Rope Pushdown" to "tricep-pushdown",
    "Wrist Curl (Cable)" to "cable-wrist-curl"
)

/**
 * Hevy CSV reconciliation — the user's real 4,734-row Hevy export uses this display wording for
 * 3 RepDB exercises the app already has under different names (full metadata + real photo
 * unchanged, "CSV is the main guy" for wording). Keyed by the bare RepDB slug (no "builtin:"
 * prefix); applied as a projection-time relabel in [parseBuiltinCatalog] — `repdb.json` itself
 * stays a byte-identical upstream mirror. Because the new names now exact-match the CSV wording,
 * no [HEVY_ALIASES] entries are needed for these 3.
 */
val HEVY_NAME_OVERRIDES: Map<String, String> = mapOf(
    "squat" to "Squat (Barbell)",
    "db-bench-press" to "Bench Press (Dumbbell)",
    "single-arm-dumbbell-overhead-tricep-extension" to "Single Arm Tricep Extension (Dumbbell)"
)

/**
 * Hevy CSV reconciliation — 9 `exercise_title` values across the user's export with no RepDB
 * counterpart at all (genuinely new movements, no licensed photo exists for them — they keep the
 * app's generic fallback icon tile). Muscle group picked to match the app's own tagging of the
 * closest sibling RepDB exercise (e.g. Seated Cable Row / T-Bar Row are both LATS; Barbell
 * Deadlift is LOWER_BACK via its first primary muscle). Keyed the same way [HEVY_ALIASES] is
 * (post-[HevyExerciseMatcher.canonicaliseHevyRawName], so stray inner parenthetical spaces in the
 * raw export still match these clean keys). The `String` is the tracking mode these exercises
 * actually log as in the export — for reference/tests; `HevyImporter` still infers tracking mode
 * from the real per-row data, which is the more reliable source once a session is being imported.
 */
val HEVY_KNOWN_CUSTOMS: Map<String, Triple<MuscleGroup, Equipment, String>> = mapOf(
    "Cycling" to Triple(MuscleGroup.CARDIO, Equipment.NONE, "DISTANCE_DURATION"),
    "Hiking" to Triple(MuscleGroup.CARDIO, Equipment.NONE, "DISTANCE_DURATION"),
    "Deadlift (Smith Machine)" to Triple(MuscleGroup.LOWER_BACK, Equipment.MACHINE, "WEIGHT_REPS"),
    "Iso-Lateral Row (Machine)" to Triple(MuscleGroup.LATS, Equipment.MACHINE, "WEIGHT_REPS"),
    "Reverse Wrist Curl (Cable)" to Triple(MuscleGroup.FOREARMS, Equipment.CABLE, "WEIGHT_REPS"),
    "Seated Row (Machine)" to Triple(MuscleGroup.LATS, Equipment.MACHINE, "WEIGHT_REPS"),
    "Seated Wrist Extension (Barbell)" to Triple(MuscleGroup.FOREARMS, Equipment.BARBELL, "WEIGHT_REPS"),
    "Shrug (Cable)" to Triple(MuscleGroup.TRAPS, Equipment.CABLE, "WEIGHT_REPS"),
    "Single Arm Cable Row" to Triple(MuscleGroup.LATS, Equipment.CABLE, "WEIGHT_REPS")
)

/** RepDB categories excluded from the picker (§3.3.4) — a held stretch has no PR/volume fit. */
private const val EXCLUDED_CATEGORY = "stretching"

private val catalogJson = Json { ignoreUnknownKeys = true }

/**
 * Pure: RepDB JSON text -> the filtered, projected, sorted builtin catalog. No Room, no Context —
 * exercised directly by `ExerciseCatalogTest` against the real bundled `repdb.json`.
 */
internal fun parseBuiltinCatalog(jsonText: String): List<BuiltinExercise> {
    val root = catalogJson.decodeFromString(RepDbRoot.serializer(), jsonText)
    return root.exercises
        .asSequence()
        .filter { it.category != EXCLUDED_CATEGORY }
        .map { ex ->
            val imageId = ex.image_alias ?: ex.id
            BuiltinExercise(
                id = BUILTIN_ID_PREFIX + ex.id,
                name = HEVY_NAME_OVERRIDES[ex.id] ?: ex.name,
                primaryMuscle = deriveMuscleGroup(ex),
                equipment = deriveEquipment(ex),
                trackingMode = deriveTrackingMode(ex),
                imageId = imageId,
                hasStartPeak = ex.images.flat != listOf("main")
            )
        }
        .sortedBy { it.name }
        .toList()
}

/**
 * A2 (§3.3.3) — reads `assets/exercises/repdb.json` on first access, parses + projects it via
 * [parseBuiltinCatalog], and caches the result for the process lifetime (one parse, not one per
 * screen visit — §3.10 P4).
 */
class ExerciseCatalog(private val context: Context) {
    @Volatile private var cached: List<BuiltinExercise>? = null

    fun builtins(): List<BuiltinExercise> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.assets.open("exercises/repdb.json").bufferedReader().use { it.readText() }
            val parsed = parseBuiltinCatalog(text)
            cached = parsed
            return parsed
        }
    }

    fun byId(id: String): BuiltinExercise? = builtins().firstOrNull { it.id == id }
}
