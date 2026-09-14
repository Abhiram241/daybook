package com.daybook.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.ReportCategory
import com.daybook.app.data.model.AppSettings
import com.daybook.app.data.parseChatRangeDate
import com.daybook.app.data.parseReportCategories
import com.daybook.app.data.reportCategoriesToCsv
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * DAILY_REPORT_REDESIGN_PLAN.md §2/§6/§7 — the new "Daily Report AI" settings screen's backing
 * state: the meta-prompt, the AI-Summary category toggles (§6, independent of §7's Chat toggles),
 * and Chat's custom date-range + its own category toggles.
 */
@HiltViewModel
class DailyReportAiSettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val database: com.daybook.app.data.local.AppDatabase
) : ViewModel() {

    private fun <T> col(sel: (AppSettings) -> T, initial: T): StateFlow<T> =
        settingsRepository.observeSettings().map(sel)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // §2 — Summary's meta-prompt. AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §1 renames this
    // box "Summary instructions" — it now applies ONLY to the AI Summary.
    val metaPrompt: StateFlow<String> = col({ it.aiMetaPrompt }, "")
    fun setMetaPrompt(v: String) { safeLaunch { settingsRepository.setAiMetaPrompt(v) } }

    // §1 — Chat's OWN, separate instructions box. Fully independent of [metaPrompt]: editing one
    // never changes the other. Migration copies today's `ai_meta_prompt` into this new column so
    // nothing changes for the user on upgrade (S3).
    val chatMetaPrompt: StateFlow<String> = col({ it.aiChatMetaPrompt }, "")
    fun setChatMetaPrompt(v: String) { safeLaunch { settingsRepository.setAiChatMetaPrompt(v) } }

    // §6 — AI Summary category toggles. Independent of [chatCategories] — separate column,
    // separate UI section, never shared state.
    val reportCategories: StateFlow<Set<ReportCategory>> =
        col({ parseReportCategories(it.aiReportCategories) }, ReportCategory.entries.toSet())

    fun toggleReportCategory(category: ReportCategory, enabled: Boolean) {
        safeLaunch {
            val current = parseReportCategories(settingsRepository.getSettings().aiReportCategories)
            val next = if (enabled) current + category else current - category
            settingsRepository.setAiReportCategories(reportCategoriesToCsv(next))
        }
    }

    // §7 — Chat's SEPARATE category toggle set.
    val chatCategories: StateFlow<Set<ReportCategory>> =
        col({ parseReportCategories(it.aiChatCategories) }, ReportCategory.entries.toSet())

    fun toggleChatCategory(category: ReportCategory, enabled: Boolean) {
        safeLaunch {
            val current = parseReportCategories(settingsRepository.getSettings().aiChatCategories)
            val next = if (enabled) current + category else current - category
            settingsRepository.setAiChatCategories(reportCategoriesToCsv(next))
        }
    }

    // §7.1 — Chat's custom context date range. "" means "not set" (falls back to today/whatever
    // day the Report tab has open).
    val chatRangeStart: StateFlow<String> = col({ it.aiChatRangeStart }, "")
    val chatRangeEnd: StateFlow<String> = col({ it.aiChatRangeEnd }, "")

    fun setChatRangeStart(date: LocalDate) {
        safeLaunch {
            val settings = settingsRepository.getSettings()
            // §7.4 — "start can't be after end", auto-clamped, mirroring the export-range screen's
            // own onConfirm behaviour.
            // M3 fix — was a bare `LocalDate.parse`; a malformed stored value used to make this
            // silently no-op forever with no way to recover except "Reset to today". Shared
            // [parseChatRangeDate] degrades a malformed value to null instead of throwing.
            val end = parseChatRangeDate(settings.aiChatRangeEnd)
            val newEnd = if (end != null && end.isBefore(date)) date else end
            settingsRepository.setChatRange(date.toString(), (newEnd ?: date).toString())
        }
    }

    fun setChatRangeEnd(date: LocalDate) {
        safeLaunch {
            val settings = settingsRepository.getSettings()
            val start = parseChatRangeDate(settings.aiChatRangeStart)
            val newStart = if (start != null && start.isAfter(date)) date else start
            settingsRepository.setChatRange((newStart ?: date).toString(), date.toString())
        }
    }

    /** §7.4 — "Reset to today", one tap instead of re-picking today's date twice. */
    fun resetChatRange() { safeLaunch { settingsRepository.setChatRange("", "") } }

    // §2.4 — live counts for the "Privacy" section's two rows. Backed directly by the DAO (this
    // screen has no DailyReportRepository dependency and doesn't need one for a plain count).
    val hiddenFromSummaryCount: StateFlow<Int> = database.aiExclusionDao().observe("SUMMARY")
        .map { it.distinctBy { row -> row.targetId }.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val hiddenFromChatCount: StateFlow<Int> = database.aiExclusionDao().observe("CHAT")
        .map { it.distinctBy { row -> row.targetId }.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
