package com.daybook.app.ui.foodmed

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.CustomCategoryRepository
import com.daybook.app.data.CustomPromptRepository
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.ColorTag
import com.daybook.app.data.model.DayOfWeek
import com.daybook.app.data.model.FoodMedTask
import com.daybook.app.data.model.RedFlag
import com.daybook.app.data.model.TaskType
import com.daybook.app.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddFoodMedViewModel @Inject constructor(
    private val foodMedRepository: FoodMedRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    private val customCategoryRepository: CustomCategoryRepository,
    private val customPromptRepository: CustomPromptRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {
    var label = ""
    var iconKey = "restaurant"
    var colorTag = ColorTag.AUTO
    var type = TaskType.FOOD
    var times = mutableListOf<LocalTime>()
    var activeDays = mutableListOf<DayOfWeek>()
    var snoozeIntervalMinutes = 10
    var isArchived = false
    var customCategory: String? = null
    var promptMessage: String? = null
    var motivation = ""   // SD-6
    var defaultRedFlag: RedFlag = RedFlag.NONE
    var defaultSuspectedFood: String = ""
    var defaultOutsideFood: Boolean = false

    val categories: StateFlow<List<String>> =
        customCategoryRepository.observeNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prompts: StateFlow<List<String>> =
        customPromptRepository.observeNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCategory(name: String) {
        safeLaunch { customCategoryRepository.addIfAbsent(name) }
    }

    fun removeCategory(name: String) {
        safeLaunch { customCategoryRepository.remove(name) }
    }

    fun addPrompt(name: String) {
        safeLaunch { customPromptRepository.addIfAbsent(name) }
    }

    fun removePrompt(name: String) {
        safeLaunch { customPromptRepository.remove(name) }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _nextReminderPreview = MutableStateFlow("")
    val nextReminderPreview: StateFlow<String> = _nextReminderPreview.asStateFlow()

    /** rec 3 (N2) — the app-wide default snooze, for seeding a NEW intake form. */
    private val _defaultSnooze = MutableStateFlow(10)
    val defaultSnooze: StateFlow<Int> = _defaultSnooze.asStateFlow()

    init {
        updateNextReminderPreview()
        safeLaunch {
            val d = appSettingsRepository.getSettings().defaultSnoozeMinutes
            _defaultSnooze.value = d
            if (snoozeIntervalMinutes == 10) snoozeIntervalMinutes = d
        }
    }

    fun saveItem(itemId: String? = null) {
        safeLaunch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                 val item = FoodMedTask(
                     id = itemId ?: UUID.randomUUID().toString(),
                     label = label,
                     type = type,
                     iconKey = iconKey,
                     colorTag = colorTag,
                     timesJson = DateTimeUtils.timesToJson(times),
                     activeDaysJson = DateTimeUtils.daysToJson(activeDays),
                     isArchived = isArchived,
                     snoozeIntervalMinutes = snoozeIntervalMinutes,
                     notificationId = 0,
                     // Switching the type away from CUSTOM/JOURNAL must not leave a stale category on the row.
                     customCategory = customCategory?.takeIf { type == TaskType.CUSTOM || type == TaskType.JOURNAL },
                     promptMessage = promptMessage?.trim()?.takeIf { it.isNotBlank() },
                     motivation = motivation.trim().takeIf { it.isNotBlank() },
                     // v0.5.4: red-flag defaults are FOOD-only — clear them if the type moved away.
                     defaultRedFlag = defaultRedFlag.takeIf { type == TaskType.FOOD && it != RedFlag.NONE },
                     defaultSuspectedFood = defaultSuspectedFood.trim()
                         .takeIf { type == TaskType.FOOD && it.isNotBlank() },
                     defaultOutsideFood = defaultOutsideFood.takeIf { type == TaskType.FOOD && it }
                 )

                foodMedRepository.save(item)
                occurrenceScheduler.syncTask(item.id)
                _successMessage.value = if (itemId == null) "Reminder added" else "Reminder updated"
                clearForm()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save item: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteItem(itemId: String) {
        safeLaunch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val item = foodMedRepository.getTaskById(itemId)
                item?.let { i ->
                    occurrenceScheduler.cancelTask(i.id)
                    foodMedRepository.delete(i)
                    _successMessage.value = "Reminder deleted"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete item: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearForm() {
        label = ""
        iconKey = "restaurant"
        colorTag = ColorTag.AUTO
        type = TaskType.FOOD
        customCategory = null
        promptMessage = null
        motivation = ""
        defaultRedFlag = RedFlag.NONE
        defaultSuspectedFood = ""
        defaultOutsideFood = false
        times.clear()
        activeDays.clear()
        // rec 3 (N2) — reset to the stored default, not a hard 10.
        snoozeIntervalMinutes = _defaultSnooze.value
        isArchived = false
        updateNextReminderPreview()
    }

    fun updateNextReminderPreview() {
        if (times.isNotEmpty()) {
            val nextOccurrence = DateTimeUtils.calculateNextOccurrence(
                times = times,
                activeDaysJson = DateTimeUtils.daysToJson(activeDays),
                fromDate = LocalDate.now()
            )
            val relativeDay = DateTimeUtils.getRelativeDayString(nextOccurrence.first)
            val timeString = DateTimeUtils.formatTime(nextOccurrence.second)
            _nextReminderPreview.value = "$relativeDay at $timeString"
        } else {
            _nextReminderPreview.value = "Set times and days to see preview"
        }
    }
}