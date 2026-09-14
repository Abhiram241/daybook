package com.daybook.app.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AiScope
import com.daybook.app.data.local.AppDatabase
import com.daybook.app.data.model.AiExclusion
import com.daybook.app.data.model.FoodMedOccurrence
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitOccurrence
import com.daybook.app.data.JournalQa
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val KIND_HABIT = "HABIT"
private const val KIND_TASK = "TASK"
private const val KIND_HABIT_ENTRY = "HABIT_ENTRY"
private const val KIND_TASK_ENTRY = "TASK_ENTRY"
private const val PAGE_SIZE = 30

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.1 — one row in the picker: a habit or an
 *  intake reminder, whether it's fully hidden, and (once expanded) its loaded entry page. */
data class AiExclusionItem(
    val id: String,
    val label: String,
    val isHabit: Boolean,
    val isArchived: Boolean,
    val hiddenAll: Boolean
)

data class AiExclusionEntry(
    val id: String,
    val timeLabel: String,
    val preview: String,
    val hidden: Boolean
)

data class AiExclusionsUiState(
    val scopeLabel: String = "",
    val query: String = "",
    val items: List<AiExclusionItem> = emptyList(),
    val expandedItemId: String? = null,
    val expandedEntries: List<AiExclusionEntry> = emptyList(),
    val expandedHasMore: Boolean = false,
    val loadingEntries: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AiExclusionsViewModel @Inject constructor(
    private val database: AppDatabase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scope: AiScope =
        if (savedStateHandle.get<String>("scope") == "CHAT") AiScope.CHAT else AiScope.SUMMARY
    private val otherScope: AiScope = if (scope == AiScope.CHAT) AiScope.SUMMARY else AiScope.CHAT

    private val _query = MutableStateFlow("")
    private val _expandedItemId = MutableStateFlow<String?>(null)
    private val _expandedEntries = MutableStateFlow<List<AiExclusionEntry>>(emptyList())
    private val _expandedOffset = MutableStateFlow(0)
    private val _expandedHasMore = MutableStateFlow(false)
    private val _loadingEntries = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val habitsFlow = database.habitDao().observeAllHabits()
    private val tasksFlow = database.foodMedTaskDao().observeAllTasks()
    private val exclusionsFlow = database.aiExclusionDao().observe(scope.name)

    val uiState: StateFlow<AiExclusionsUiState> = combine(
        combine(habitsFlow, tasksFlow, exclusionsFlow) { habits, tasks, exclusions ->
            val hiddenHabitIds = exclusions.filter { it.kind == KIND_HABIT }.mapTo(HashSet()) { it.targetId }
            val hiddenTaskIds = exclusions.filter { it.kind == KIND_TASK }.mapTo(HashSet()) { it.targetId }
            val habitItems = habits.map {
                AiExclusionItem(it.id, it.title, isHabit = true, isArchived = it.isArchived, hiddenAll = it.id in hiddenHabitIds)
            }
            val taskItems = tasks.map {
                AiExclusionItem(it.id, it.label, isHabit = false, isArchived = it.isArchived, hiddenAll = it.id in hiddenTaskIds)
            }
            (habitItems + taskItems).sortedWith(compareBy({ it.isArchived }, { it.label.lowercase() }))
        },
        _query,
        combine(_expandedItemId, _expandedEntries, _expandedHasMore, _loadingEntries) { id, entries, more, loading ->
            arrayOf(id, entries, more, loading)
        },
        _errorMessage
    ) { items, query, expanded, error ->
        @Suppress("UNCHECKED_CAST")
        AiExclusionsUiState(
            scopeLabel = if (scope == AiScope.CHAT) "Chat" else "AI Summary",
            query = query,
            items = if (query.isBlank()) items else items.filter { it.label.contains(query, ignoreCase = true) },
            expandedItemId = expanded[0] as String?,
            expandedEntries = expanded[1] as List<AiExclusionEntry>,
            expandedHasMore = expanded[2] as Boolean,
            loadingEntries = expanded[3] as Boolean,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiExclusionsUiState())

    fun setQuery(q: String) { _query.value = q }

    fun toggleItem(item: AiExclusionItem, hide: Boolean) {
        safeLaunch {
            runCatching {
                val kind = if (item.isHabit) KIND_HABIT else KIND_TASK
                if (hide) {
                    database.aiExclusionDao().upsert(AiExclusion(scope.name, kind, item.id, System.currentTimeMillis()))
                } else {
                    database.aiExclusionDao().remove(scope.name, kind, item.id)
                }
            }.onFailure { _errorMessage.value = "Couldn't update — try again." }
        }
    }

    fun toggleEntry(entryId: String, isHabit: Boolean, hide: Boolean) {
        safeLaunch {
            runCatching {
                val kind = if (isHabit) KIND_HABIT_ENTRY else KIND_TASK_ENTRY
                if (hide) {
                    database.aiExclusionDao().upsert(AiExclusion(scope.name, kind, entryId, System.currentTimeMillis()))
                } else {
                    database.aiExclusionDao().remove(scope.name, kind, entryId)
                }
                _expandedEntries.value = _expandedEntries.value.map {
                    if (it.id == entryId) it.copy(hidden = hide) else it
                }
            }.onFailure { _errorMessage.value = "Couldn't update — try again." }
        }
    }

    /** §2.1 — tap to expand an item's entries (loads the first page); tap again to collapse. */
    fun toggleExpanded(item: AiExclusionItem) {
        if (_expandedItemId.value == item.id) {
            _expandedItemId.value = null
            _expandedEntries.value = emptyList()
            return
        }
        _expandedItemId.value = item.id
        _expandedEntries.value = emptyList()
        _expandedOffset.value = 0
        loadEntryPage(item)
    }

    fun loadMoreEntries(item: AiExclusionItem) = loadEntryPage(item)

    private fun loadEntryPage(item: AiExclusionItem) {
        safeLaunch {
            _loadingEntries.value = true
            try {
                val offset = _expandedOffset.value
                val hiddenEntryIds = database.aiExclusionDao().get(scope.name)
                    .filter { it.kind == KIND_HABIT_ENTRY || it.kind == KIND_TASK_ENTRY }
                    .mapTo(HashSet()) { it.targetId }
                val page: List<AiExclusionEntry> = if (item.isHabit) {
                    database.habitOccurrenceDao().getTerminalPageForHabit(item.id, PAGE_SIZE, offset)
                        .map { toEntry(it, hiddenEntryIds) }
                } else {
                    database.foodMedOccurrenceDao().getTerminalPageForTask(item.id, PAGE_SIZE, offset)
                        .map { toEntry(it, hiddenEntryIds) }
                }
                _expandedEntries.value = _expandedEntries.value + page
                _expandedOffset.value = offset + page.size
                _expandedHasMore.value = page.size == PAGE_SIZE
            } catch (t: Throwable) {
                com.daybook.app.util.recordUnhandledException(t)
                _errorMessage.value = "Couldn't load entries."
            } finally {
                _loadingEntries.value = false
            }
        }
    }

    private fun toEntry(occ: HabitOccurrence, hiddenEntryIds: Set<String>): AiExclusionEntry {
        val preview = JournalQa.decode(occ.qaJson).firstOrNull { it.second.isNotBlank() }?.second
            ?: occ.status.name.lowercase().replaceFirstChar { it.uppercase() }
        return AiExclusionEntry(
            id = occ.id,
            timeLabel = com.daybook.app.util.DateTimeUtils.timestampToLocalDate(occ.scheduledFor).toString(),
            preview = preview.take(40),
            hidden = occ.id in hiddenEntryIds
        )
    }

    private fun toEntry(occ: FoodMedOccurrence, hiddenEntryIds: Set<String>): AiExclusionEntry {
        val preview = occ.responseText.takeIf { it.isNotBlank() }
            ?: JournalQa.decode(occ.qaJson).firstOrNull { it.second.isNotBlank() }?.second
            ?: occ.status.name.lowercase().replaceFirstChar { it.uppercase() }
        return AiExclusionEntry(
            id = occ.id,
            timeLabel = com.daybook.app.util.DateTimeUtils.timestampToLocalDate(occ.scheduledFor).toString(),
            preview = preview.take(40),
            hidden = occ.id in hiddenEntryIds
        )
    }

    fun copyFromOtherScope() {
        safeLaunch {
            runCatching {
                database.aiExclusionDao().copyScope(otherScope.name, scope.name, System.currentTimeMillis())
            }.onFailure { _errorMessage.value = "Couldn't copy — try again." }
        }
    }

    val otherScopeLabel: String get() = if (otherScope == AiScope.CHAT) "Chat" else "Summary"

    fun clearError() { _errorMessage.value = null }
}
