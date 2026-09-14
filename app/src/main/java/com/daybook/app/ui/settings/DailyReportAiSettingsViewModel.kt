package com.daybook.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.ReportCategory
import com.daybook.app.data.model.AppSettings
import com.daybook.app.data.parseChatRangeDate
import com.daybook.app.data.parseReportCategories
import com.daybook.app.data.reportCategoriesToCsv
import com.daybook.app.data.sync.CloudSyncRepository
import com.daybook.app.data.sync.SyncStatus
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DAILY_REPORT_REDESIGN_PLAN.md §2/§6/§7 — the new "Daily Report AI" settings screen's backing
 * state: the meta-prompt, the AI-Summary category toggles (§6, independent of §7's Chat toggles),
 * and Chat's custom date-range + its own category toggles.
 *
 * Everything here lives in `app_settings` / `ai_exclusions`, both of which `CloudSyncRepository`
 * tracks: any write below is pushed to the signed-in user's Firestore doc (`aiSettings` /
 * `aiExclusions` fields) by the debounced push loop, and pulled onto other devices.
 */
@HiltViewModel
class DailyReportAiSettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val database: com.daybook.app.data.local.AppDatabase,
    cloudSync: CloudSyncRepository
) : ViewModel() {

    private fun <T> col(sel: (AppSettings) -> T, initial: T): StateFlow<T> =
        settingsRepository.observeSettings().map(sel)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // §2 — Summary's meta-prompt. `null` until the settings row has actually been read: the
    // screen's text-field draft must not be seeded from a placeholder "" (it used to be, which
    // showed an empty box over a saved prompt and let a focus-loss commit wipe it).
    val metaPrompt: StateFlow<String?> = col({ it.aiMetaPrompt }, null)

    // §1 — Chat's OWN, separate instructions box. Fully independent of [metaPrompt].
    val chatMetaPrompt: StateFlow<String?> = col({ it.aiChatMetaPrompt }, null)

    /** When each box was last saved on this screen (millis), or null — drives the "Saved · synced"
     *  caption; cleared when that box is edited again. */
    private val _summarySavedAt = MutableStateFlow<Long?>(null)
    val summarySavedAt = _summarySavedAt.asStateFlow()
    private val _chatSavedAt = MutableStateFlow<Long?>(null)
    val chatSavedAt = _chatSavedAt.asStateFlow()

    fun saveMetaPrompt(v: String) {
        val at = System.currentTimeMillis()
        safeLaunch {
            settingsRepository.setAiMetaPrompt(v.trim())
            _summarySavedAt.value = at
        }
    }

    fun saveChatMetaPrompt(v: String) {
        val at = System.currentTimeMillis()
        safeLaunch {
            settingsRepository.setAiChatMetaPrompt(v.trim())
            _chatSavedAt.value = at
        }
    }

    fun clearSummarySaved() { _summarySavedAt.value = null }
    fun clearChatSaved() { _chatSavedAt.value = null }

    /** Surfaced under the Save buttons so the user can see the change actually reach the cloud. */
    val syncStatus: StateFlow<SyncStatus> = cloudSync.status

    // §6 / §7 — the two INDEPENDENT category toggle sets. Both toggles read-modify-write the same
    // `app_settings` row, so they're serialized: two quick taps used to read the same "current"
    // set concurrently and the second write dropped the first tap.
    private val toggleMutex = Mutex()

    val reportCategories: StateFlow<Set<ReportCategory>> =
        col({ parseReportCategories(it.aiReportCategories) }, ReportCategory.entries.toSet())

    fun toggleReportCategory(category: ReportCategory, enabled: Boolean) {
        safeLaunch {
            toggleMutex.withLock {
                val current = parseReportCategories(settingsRepository.getSettings().aiReportCategories)
                val next = nextCategorySet(current, category, enabled) ?: return@withLock
                settingsRepository.setAiReportCategories(reportCategoriesToCsv(next))
            }
        }
    }

    val chatCategories: StateFlow<Set<ReportCategory>> =
        col({ parseReportCategories(it.aiChatCategories) }, ReportCategory.entries.toSet())

    fun toggleChatCategory(category: ReportCategory, enabled: Boolean) {
        safeLaunch {
            toggleMutex.withLock {
                val current = parseReportCategories(settingsRepository.getSettings().aiChatCategories)
                val next = nextCategorySet(current, category, enabled) ?: return@withLock
                settingsRepository.setAiChatCategories(reportCategoriesToCsv(next))
            }
        }
    }

    // §7.1 — Chat's custom context date range. "" means "not set" (falls back to today/whatever
    // day the Report tab has open).
    val chatRangeStart: StateFlow<String> = col({ it.aiChatRangeStart }, "")
    val chatRangeEnd: StateFlow<String> = col({ it.aiChatRangeEnd }, "")

    fun setChatRangeStart(date: LocalDate) {
        safeLaunch {
            val settings = settingsRepository.getSettings()
            // §7.4 — "start can't be after end", auto-clamped. A malformed stored value degrades to
            // null via [parseChatRangeDate] instead of throwing.
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

    // §2.4 — live counts for the "Privacy" section's two rows.
    val hiddenFromSummaryCount: StateFlow<Int> = database.aiExclusionDao().observe("SUMMARY")
        .map { it.distinctBy { row -> row.targetId }.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val hiddenFromChatCount: StateFlow<Int> = database.aiExclusionDao().observe("CHAT")
        .map { it.distinctBy { row -> row.targetId }.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/**
 * The next toggle set, or `null` when the change must be refused. Turning the LAST category off is
 * refused: the stored CSV would be "", which [parseReportCategories] deliberately reads as "all on"
 * — so the switch used to snap back on and silently re-enable every category.
 */
internal fun nextCategorySet(
    current: Set<ReportCategory>,
    category: ReportCategory,
    enabled: Boolean
): Set<ReportCategory>? {
    val next = if (enabled) current + category else current - category
    return next.takeIf { it.isNotEmpty() }
}
