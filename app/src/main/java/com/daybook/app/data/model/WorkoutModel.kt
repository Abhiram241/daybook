package com.daybook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// Round A (workout mode / "Beast Mode"). Six new tables, kept in their own file rather than
// appended to the already-large DataModel.kt (§3.2 of HEALTH_AND_WORKOUT_PLAN.md) — Room only
// cares about AppDatabase's entities list, not which file an @Entity lives in.

/**
 * A USER-CREATED exercise only. The ~525-exercise built-in catalog is a bundled RepDB asset read
 * at runtime by ExerciseCatalog (data/workout/ExerciseCatalog.kt) — never a seeded row here.
 */
@Serializable
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    // One of MuscleGroup.entries's name(), one of Equipment.entries's name() (§3.3.1). Plain
    // String, not a Room-converted enum — same treatment RepDB-derived builtins get.
    @ColumnInfo(name = "primary_muscle") val primaryMuscle: String,
    @ColumnInfo(name = "equipment") val equipment: String,
    // "WEIGHT_REPS" / "REPS_ONLY" / "DURATION" / "DISTANCE_DURATION" — Ri1: permanent, never reused.
    @ColumnInfo(name = "tracking_mode") val trackingMode: String,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    // "USER" | "IMPORTED_HEVY" (§3.9.4/§3.9.6).
    @ColumnInfo(name = "source", defaultValue = "USER") val source: String = "USER",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "notes") val notes: String? = null
)

/** A logged (or in-progress) gym session. */
@Serializable
@Entity(
    tableName = "workout_sessions",
    indices = [Index("local_date"), Index("started_at")]
)
data class WorkoutSession(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    // "yyyy-MM-dd" — the same timezone-stable convention as Habit/FoodMedOccurrence.local_date.
    // What MonthPartitioner buckets on. Load-bearing.
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    // "ACTIVE" / "COMPLETED"
    @ColumnInfo(name = "status", defaultValue = "ACTIVE") val status: String = "ACTIVE",
    // "MANUAL" | "IMPORTED_HEVY" (reserved: "HEALTH_CONNECT" if Round B's §6.4 is ever un-deferred)
    @ColumnInfo(name = "source", defaultValue = "MANUAL") val source: String = "MANUAL",
    // The routine this session was started from, or null. NULLABLE, deliberately NOT a foreign
    // key: deleting a routine must never delete or orphan the workouts done with it.
    @ColumnInfo(name = "routine_id") val routineId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** One row per exercise block inside a session. */
@Serializable
@Entity(
    tableName = "workout_exercises",
    indices = [Index(value = ["session_id", "order_index"]), Index("exercise_id")]
)
data class WorkoutExercise(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    // "builtin:<repdb-slug>" or a custom Exercise.id UUID (§3.3).
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    // The "Add notes here…" line. Per block.
    @ColumnInfo(name = "notes") val notes: String? = null,
    // Preserved losslessly from a Hevy import; unread/unwritten by Round A's UI.
    @ColumnInfo(name = "superset_id") val supersetId: String? = null,
    // null == "Rest Timer: OFF" for this block. Per block, per session.
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * One logged (or planned-but-not-yet-logged) set. NOT autoincrement — an autoincrement rowid
 * does not survive the export/import round trip.
 */
@Serializable
@Entity(
    tableName = "workout_sets",
    indices = [
        Index(value = ["workout_exercise_id", "set_number"]),
        Index("session_id"),
        Index("exercise_id")
    ]
)
data class WorkoutSet(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    // DENORMALISED ON PURPOSE, immutable for the row's lifetime — what makes month eviction and
    // the chunked range export one indexed query instead of a join.
    @ColumnInfo(name = "session_id") val sessionId: String,
    // DENORMALISED ON PURPOSE, also immutable — what makes PREVIOUS/PR lookups index-only.
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    // 1-based within its block; also the PREVIOUS join key.
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "reps") val reps: Int? = null,
    // ALWAYS stored in kg; lb is a display conversion only (Ri3: never a non-null default).
    @ColumnInfo(name = "weight_kg") val weightKg: Float? = null,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Float? = null,
    @ColumnInfo(name = "rpe") val rpe: Int? = null,
    // "NORMAL" | "WARMUP" | "DROPSET" | "FAILURE" — exactly Hevy's four CSV values (§3.9.3).
    @ColumnInfo(name = "set_type", defaultValue = "NORMAL") val setType: String = "NORMAL",
    @ColumnInfo(name = "notes") val notes: String? = null,
    // non-null == the green check is ticked.
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null
)

/** The routine template itself — a named, reusable, ordered list of exercises (§3.2.1). */
@Serializable
@Entity(tableName = "workout_routines", indices = [Index("order_index")])
data class WorkoutRoutine(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    // Hidden from the list, never hard-deleted — WorkoutSession.routineId keeps pointing at it.
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    // Provenance hook mirroring Exercise.source / WorkoutSession.source. Importing routines is
    // out of scope for Round A; this is bookkeeping, not a permission.
    @ColumnInfo(name = "source", defaultValue = "USER") val source: String = "USER",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** One exercise slot inside a routine, with its optional targets. */
@Serializable
@Entity(
    tableName = "workout_routine_exercises",
    indices = [Index(value = ["routine_id", "order_index"]), Index("exercise_id")]
)
data class WorkoutRoutineExercise(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    // Every target column is nullable (Ri3, R18) — null == "no target": starting the routine
    // creates NO set rows for this exercise and the user taps "+ Add Set" instead.
    @ColumnInfo(name = "target_sets") val targetSets: Int? = null,
    @ColumnInfo(name = "target_reps") val targetReps: Int? = null,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Float? = null,
    @ColumnInfo(name = "target_duration_seconds") val targetDurationSeconds: Int? = null,
    @ColumnInfo(name = "target_distance_meters") val targetDistanceMeters: Float? = null,
    // null == no routine-level rest opinion — falls back to lastRestSecondsForExercise, then to
    // app_settings.rest_timer_default_seconds (§3.4).
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int? = null,
    // Copied into WorkoutExercise.notes when a session starts.
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
