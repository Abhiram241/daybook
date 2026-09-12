package com.daybook.app.data.workout

/** One catalog row the matcher can resolve a Hevy name onto — builtin or existing custom. */
data class MatchCandidate(val id: String, val name: String, val equipment: String)

sealed interface HevyMatchResult {
    data class Existing(val exerciseId: String) : HevyMatchResult
    /** Once per distinct Hevy name per import — the caller creates a custom `Exercise` row.
     *  [muscleHint] comes from [HEVY_KNOWN_CUSTOMS] (a name-specific known movement), not from
     *  the raw parenthetical hint that [equipmentHint] is derived from. */
    data class CreateCustom(
        val hevyNameVerbatim: String,
        val equipmentHint: String?,
        val muscleHint: MuscleGroup? = null
    ) : HevyMatchResult
}

/**
 * A8 (§3.9.4) — matches a Hevy `exercise_title` to Daybook's catalog. A HEURISTIC: it will not be
 * perfect, and the design's job is to make its failures harmless and visible (an auto-created
 * custom exercise) rather than pretend they won't happen. Deliberately NO fuzzy/edit-distance
 * matching — a wrong match silently merges two different movements' histories, which is worse
 * than no match. May contain only a normaliser and the alias table, nothing else (§3.9.4).
 */
object HevyExerciseMatcher {

    /** Step 1 — lowercase; strip the trailing parenthetical as an equipment hint; replace
     *  `-`, `–`, `/` and punctuation with spaces; collapse whitespace; drop a leading "the". The
     *  same normaliser powers the Add-Exercise search field (§3.7.3) — see [normaliseForSearch],
     *  which this delegates the base normalisation to. */
    fun normalise(rawName: String): Pair<String, String?> {
        val parenMatch = Regex("""\(([^)]*)\)\s*$""").find(rawName)
        val hint = parenMatch?.groupValues?.get(1)?.trim()?.uppercase()?.replace(Regex("\\s+"), "_")
        val withoutParen = if (parenMatch != null) rawName.substring(0, parenMatch.range.first) else rawName
        var normalised = normaliseForSearch(withoutParen)
        if (normalised.startsWith("the ")) normalised = normalised.removePrefix("the ").trim()
        return normalised to hint?.takeIf { it.isNotBlank() }
    }

    /** Strips the same trailing "(Equipment)" parenthetical [normalise] strips off a raw Hevy
     *  name, but off a CATALOG name instead — needed because [HEVY_NAME_OVERRIDES] gives a
     *  handful of catalog entries a name that itself ends in "(Equipment)" (to match the CSV's own
     *  wording verbatim), which would otherwise never exact-match a query whose own parenthetical
     *  was already stripped by [normalise]. */
    private fun stripTrailingParenthetical(rawName: String): String {
        val parenMatch = Regex("""\(([^)]*)\)\s*$""").find(rawName)
        return if (parenMatch != null) rawName.substring(0, parenMatch.range.first) else rawName
    }

    /**
     * Steps 2–4, in order: exact normalised match (ties broken by the equipment hint) -> the
     * hand-written alias table -> create a custom exercise. [candidates] is `builtins + existing
     * customs`; [aliasResolver] maps a raw Hevy name to a bare catalog slug (no "builtin:"
     * prefix) — the caller supplies [HEVY_ALIASES] in production and a smaller fixture in tests.
     */
    fun resolve(
        hevyName: String,
        candidates: List<MatchCandidate>,
        aliasResolver: Map<String, String> = HEVY_ALIASES
    ): HevyMatchResult {
        val (normalisedName, hint) = normalise(hevyName)
        val exactMatches = candidates.filter { normaliseForSearch(stripTrailingParenthetical(it.name)) == normalisedName }
        if (exactMatches.isNotEmpty()) {
            val best = if (hint != null) exactMatches.firstOrNull { it.equipment == hint } ?: exactMatches.first()
            else exactMatches.first()
            return HevyMatchResult.Existing(best.id)
        }
        // The real export has been seen to insert stray spaces just inside a parenthetical
        // ("Tricep Supported Bicep Curls ( Dumbbell )") — canonicalise before the alias lookup so
        // the table's clean-spelled keys still match, rather than silently falling through to an
        // unnecessary custom-exercise creation.
        val aliasSlug = aliasResolver[canonicaliseHevyRawName(hevyName)]
        if (aliasSlug != null) {
            return HevyMatchResult.Existing(BUILTIN_ID_PREFIX + aliasSlug)
        }
        val muscleHint = HEVY_KNOWN_CUSTOMS[canonicaliseHevyRawName(hevyName)]?.first
        return HevyMatchResult.CreateCustom(hevyName, hint, muscleHint)
    }

    /** Collapses stray whitespace just inside a parenthetical ("( Cable )" -> "(Cable)") and
     *  runs of internal whitespace, without touching casing or punctuation otherwise — a
     *  defensive normalisation for the alias-table lookup only (§3.9.4). */
    internal fun canonicaliseHevyRawName(raw: String): String =
        raw.trim()
            .replace(Regex("\\(\\s+"), "(")
            .replace(Regex("\\s+\\)"), ")")
            .replace(Regex("\\s+"), " ")

    /**
     * §3.9.4 step 4 — `trackingMode` inferred from which columns are populated across ALL of a
     * name's rows in the file: weight+reps -> WEIGHT_REPS; reps only -> REPS_ONLY; duration only
     * -> DURATION; distance present -> DISTANCE_DURATION.
     */
    fun inferTrackingMode(sets: List<ParsedSet>): String {
        val hasDistance = sets.any { it.distanceMeters != null }
        val hasWeight = sets.any { it.weightKg != null }
        val hasReps = sets.any { it.reps != null }
        val hasDuration = sets.any { it.durationSeconds != null }
        return when {
            hasDistance -> "DISTANCE_DURATION"
            hasWeight && hasReps -> "WEIGHT_REPS"
            hasReps -> "REPS_ONLY"
            hasDuration -> "DURATION"
            else -> "REPS_ONLY"
        }
    }
}
