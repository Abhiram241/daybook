package com.daybook.app.ui.workout

/**
 * A5 (§3.6.6) — one source of truth for Beast Mode's route ids. `MainActivity`'s `showNav` gate
 * (§3.6.7), the accent gate (§3.8.3) and the nav item list all read from here rather than
 * repeating string literals, because a typo'd literal in any of those three places fails
 * SILENTLY: the nav just doesn't appear, or the accent just doesn't change.
 */
object WorkoutRoutes {
    const val HOME = "workout"                 // the landing — "My routines" (§3.7.4)
    const val HISTORY = "workout_history"       // past sessions (§3.7.6)
    const val LIBRARY = "workout_library"       // browse/manage exercises (§3.7.3 BROWSE mode)
    const val SETTINGS = "workout_settings"     // Beast Mode's own settings (§3.8.2)
    const val SESSION = "workout_session/{sessionId}"
    const val DETAIL = "workout_detail/{sessionId}"
    const val ROUTINE_EDIT = "routine_edit?routineId={routineId}"
    const val PICK_EXERCISE = "add_exercise"
    const val NEW_EXERCISE = "new_exercise"
    const val EDIT_EXERCISE = "edit_exercise/{exerciseId}"
    // Items 1/2 — `ids` optional: absent means "review everything that needs details"
    // (item 2's "Fix them"); present (comma-joined) scopes the first pass to a Hevy import's own
    // new exercises (item 1).
    const val EXERCISE_REVIEW = "exercise_review?ids={ids}"

    /** The three that show Beast Mode's pill nav. ORDER IS LOAD-BEARING: [0] is the leftmost
     *  item and the one the exit hold is attached to (§3.6.8). */
    val NAV: List<String> = listOf(HOME, HISTORY, LIBRARY)

    /** Every route inside the mode — the gate for the Beast Mode accent (§3.8.3). */
    val ALL: Set<String> = setOf(
        HOME, HISTORY, LIBRARY, SETTINGS, SESSION, DETAIL, ROUTINE_EDIT,
        PICK_EXERCISE, NEW_EXERCISE, EDIT_EXERCISE, EXERCISE_REVIEW
    )

    fun session(sessionId: String) = "workout_session/$sessionId"
    fun detail(sessionId: String) = "workout_detail/$sessionId"
    fun routineEdit(routineId: String?) =
        if (routineId != null) "routine_edit?routineId=$routineId" else "routine_edit"
    fun editExercise(exerciseId: String) = "edit_exercise/$exerciseId"
    /** [ids] null/empty -> the whole queue; otherwise a specific scoped first pass (item 1). */
    fun exerciseReview(ids: List<String>? = null): String =
        if (ids.isNullOrEmpty()) "exercise_review" else "exercise_review?ids=${ids.joinToString(",")}"
}
