package com.daybook.app.ui.foodmed

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.TaskType
import com.daybook.app.ui.components.ProfileUi
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.minuteTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class FoodMedItem(
    val id: String,
    val label: String,
    val iconKey: String,
    val colorTag: String,
    val type: String,
    val times: List<String>,
    val activeDays: List<String>,
    val snoozeInterval: Int,
    val isArchived: Boolean,
    val nextText: String?,
    val customCategory: String? = null,
    // v0.5.3 Phase 5 (§5.6) — "Type · N×/day" / "Category · N×/day", formatted here so the two
    // card variants keep identical density and the composable holds no presentation logic.
    val subtitle: String = ""
)

/** Intake list ordering, chosen from the top-right filter menu. */
enum class IntakeSort(val label: String) { ADDED("Added"), NAME("Name"), NEXT_REMINDER("Next reminder") }

/**
 * Pure ordering for the Intake list (v0.5.1 §A/§B). Extracted from the `combine` block so
 * `SortComparatorTest` can exercise it directly. `sortedBy` / `sortedWith` are stable, so equal
 * keys keep input order.
 */
fun sortIntake(rows: List<Pair<FoodMedTask, Long?>>, sort: IntakeSort): List<Pair<FoodMedTask, Long?>> =
    when (sort) {
        IntakeSort.ADDED -> rows.sortedBy { it.first.createdAt }
        IntakeSort.NAME -> rows.sortedBy { it.first.label.lowercase() }
        IntakeSort.NEXT_REMINDER -> rows.sortedWith(compareBy(nullsLast()) { it.second })
    }

@HiltViewModel
class FoodMedViewModel @Inject constructor(
    private val foodMedRepository: FoodMedRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    /** Corner-avatar profile (name + photo) for the tab header. */
    val profile: StateFlow<ProfileUi> =
        appSettingsRepository.observeSettings()
            .map { ProfileUi(it.userName.trim(), it.profilePhotoPath) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi("", null))

    // ---- Filter / sort state (top-right menu on the Intake screen) ----
    private val _typeFilter = MutableStateFlow<Set<TaskType>>(emptySet())   // empty = all types
    val typeFilter: StateFlow<Set<TaskType>> = _typeFilter.asStateFlow()

    private val _sort = MutableStateFlow(IntakeSort.ADDED)
    val sort: StateFlow<IntakeSort> = _sort.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    init {
        // rec 3 — remember the last sort + show-archived across restarts (no Settings row).
        safeLaunch {
            val s = appSettingsRepository.getSettings()
            _sort.value = runCatching { IntakeSort.valueOf(s.intakeSort) }.getOrDefault(IntakeSort.ADDED)
            _showArchived.value = s.intakeShowArchived
        }
    }

    /** True when the list is anything other than the default (all types, Added order, active only). */
    val filterActive: StateFlow<Boolean> =
        combine(_typeFilter, _sort, _showArchived) { types, sort, archived ->
            types.isNotEmpty() || sort != IntakeSort.ADDED || archived
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Hoisted once so the tasks query isn't re-run every time the filter changes (REV-19). */
    private val activeTasks: StateFlow<List<FoodMedTask>> =
        foodMedRepository.observeActiveTasks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All tasks incl. archived — only *used* when "Show archived" is on (the query is cheap and idle otherwise). */
    private val allTasks: StateFlow<List<FoodMedTask>> =
        foodMedRepository.observeAllTasks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * task id -> next pending reminder millis, one grouped query instead of one per row.
     *
     * v0.5.3 Phase 3 (A8): `now` re-derived from a minute ticker, not bound once at construction.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val nextMillisByTask: StateFlow<Map<String, Long>> =
        minuteTicker()
            .flatMapLatest { now ->
                foodMedRepository.observeNextPendingMillis(now)
                    .map { rows -> rows.associate { it.taskId to it.nextMillis } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private data class Filter(val types: Set<TaskType>, val sort: IntakeSort, val archived: Boolean)
    private val filter =
        combine(_typeFilter, _sort, _showArchived) { t, s, a -> Filter(t, s, a) }

    val typeCounts: StateFlow<Map<String, Int>> =
        activeTasks
            .map { tasks ->
                mapOf(
                    "All" to tasks.size,
                    "Food" to tasks.count { it.type == TaskType.FOOD },
                    "Med" to tasks.count { it.type == TaskType.MED },
                    "Custom" to tasks.count { it.type == TaskType.CUSTOM },
                    "Journal" to tasks.count { it.type == TaskType.JOURNAL }
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Reactive list — re-emits on any write to the tasks table or a filter/sort change. Filtering
     * and sorting are applied in memory via `combine`, so changing the menu does not hit the DB
     * (REV-19). Empty type set = all types.
     */
    val items: StateFlow<List<FoodMedItem>> =
        combine(activeTasks, allTasks, nextMillisByTask, filter) { active, all, nextMap, f ->
            val source = if (f.archived) all else active
            val filtered = if (f.types.isEmpty()) source else source.filter { it.type in f.types }
            // pair each task with its next-reminder millis (needed for both the card line and NEXT_REMINDER sort)
            val rows: List<Pair<FoodMedTask, Long?>> = filtered.map { task ->
                task to (if (task.isArchived) null else nextMap[task.id])
            }
            val sorted = sortIntake(rows, f.sort)
            sorted.map { (task, nextMillis) ->
                val times = DateTimeUtils.jsonToTimes(task.timesJson).map { DateTimeUtils.formatTime(it) }
                val head = task.customCategory
                    ?: task.type.name.lowercase().replaceFirstChar { it.uppercase() }
                FoodMedItem(
                    id = task.id,
                    label = task.label,
                    iconKey = task.iconKey,
                    colorTag = task.colorTag.name,
                    type = task.type.name,
                    times = times,
                    activeDays = DateTimeUtils.jsonToDays(task.activeDaysJson).map { DateTimeUtils.getDayName(it) },
                    snoozeInterval = task.snoozeIntervalMinutes,
                    isArchived = task.isArchived,
                    nextText = nextMillis?.let { "Next: ${DateTimeUtils.formatWhen(it)}" },
                    customCategory = task.customCategory,
                    subtitle = "$head · ${times.size}×/day"
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(item: FoodMedTask) {
        safeLaunch {
            foodMedRepository.save(item)
            occurrenceScheduler.syncTask(item.id)
        }
    }

    fun updateItem(item: FoodMedTask) {
        safeLaunch {
            foodMedRepository.save(item)
            occurrenceScheduler.syncTask(item.id)
        }
    }

    fun deleteItem(itemId: String) {
        safeLaunch {
            val item = foodMedRepository.getTaskById(itemId)
            item?.let { i ->
                occurrenceScheduler.cancelTask(i.id)
                foodMedRepository.delete(i)
            }
        }
    }

    fun archiveItem(itemId: String) {
        safeLaunch {
            foodMedRepository.archiveTask(itemId)
            occurrenceScheduler.cancelTask(itemId)
        }
    }

    fun unarchiveItem(itemId: String) {
        safeLaunch {
            foodMedRepository.unarchiveTask(itemId)
            occurrenceScheduler.syncTask(itemId)
        }
    }

    fun getItemById(itemId: String, onResult: (FoodMedTask?) -> Unit) {
        safeLaunch {
            onResult(foodMedRepository.getTaskById(itemId))
        }
    }

    fun toggleType(type: TaskType) {
        _typeFilter.value = _typeFilter.value.let { if (type in it) it - type else it + type }
    }

    fun setSort(newSort: IntakeSort) {
        _sort.value = newSort
        safeLaunch { appSettingsRepository.setIntakeSort(newSort.name) }
    }

    fun toggleArchived() {
        val v = !_showArchived.value
        _showArchived.value = v
        safeLaunch { appSettingsRepository.setIntakeShowArchived(v) }
    }

    fun resetFilter() {
        _typeFilter.value = emptySet()
        setSort(IntakeSort.ADDED)
        if (_showArchived.value) toggleArchived()
    }
}
