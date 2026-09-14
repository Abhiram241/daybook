package com.daybook.app.data

/**
 * DAILY_REPORT_REDESIGN_PLAN.md §6/§7 — the four data categories a Daily Report AI feature (the
 * one-shot AI Summary, or Chat) can be told to include or leave out. Stored as a CSV string on
 * [com.daybook.app.data.model.AppSettings] (`ai_report_categories` / `ai_chat_categories`) — same
 * idiom as `nav_tabs`/`streak_rest_days` — so a fifth category later is additive with no migration.
 */
enum class ReportCategory { WORKOUT, HEALTH, INTAKE, TODO }

/** Mirrors `NavConfig.visibleRoutesFrom`'s "blank/unknown falls back to everything" rule: a blank
 *  or corrupt CSV (including a fresh install's stored default) is treated as "every category on". */
fun parseReportCategories(csv: String?): Set<ReportCategory> {
    val stored = csv.orEmpty().split(",").mapNotNull { s ->
        ReportCategory.entries.firstOrNull { it.name == s.trim() }
    }.toSet()
    return stored.ifEmpty { ReportCategory.entries.toSet() }
}

/** Inverse of [parseReportCategories] — used by the settings screen's toggle rows to persist a
 *  new set back as CSV. */
fun reportCategoriesToCsv(categories: Set<ReportCategory>): String =
    categories.joinToString(",") { it.name }

/** BEAST_HEALTH_REPORT_AUDIT.md M4 — a settings fingerprint recorded on every generated
 *  [com.daybook.app.data.model.DailyReportAiSummary] row (and recomputed live from current
 *  settings) so a cached summary can tell when the meta-prompt or category toggles that produced
 *  it have since changed. Deliberately a plain concatenation, not a real hash — both sides always
 *  read the same raw strings ([com.daybook.app.data.model.AppSettings.aiMetaPrompt] /
 *  `aiReportCategories`'s CSV), so any change to either value changes this string too, and there's
 *  no size/opacity reason to hash it.
 *
 *  AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.4 — [exclusionFingerprint] (from
 *  [AiExclusionSet.fingerprint]) is folded in too: hiding or un-hiding something for the Summary
 *  scope changes this string, so a cached summary generated before that change still shows the
 *  existing "settings changed since this summary" hint. Defaults to "" so a caller that doesn't
 *  care about exclusions (chat's staleness isn't tracked the same way) doesn't need to pass one. */
fun settingsFingerprint(metaPrompt: String, categoriesCsv: String, exclusionFingerprint: String = ""): String =
    "$metaPrompt|$categoriesCsv|$exclusionFingerprint"

/** BEAST_HEALTH_REPORT_AUDIT.md M3 — the single shared parser for `ai_chat_range_start`/`_end`,
 *  a plain `TEXT NOT NULL DEFAULT ''` column. Three call sites used to each parse it themselves
 *  and disagree on trust: one guarded with `runCatching`, one let a malformed value silently
 *  no-op a settings write, and one parsed bare DURING COMPOSITION, crashing the settings screen
 *  on every open. Not reachable today (only `date.toString()`/`""` are ever written, and
 *  `app_settings` is excluded from sync/backup) but a single null-returning parser removes the
 *  crash-on-open trap regardless. Returns null for a blank or malformed value. */
fun parseChatRangeDate(raw: String?): java.time.LocalDate? =
    raw?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
