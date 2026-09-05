package com.daybook.app.ui.routines

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.ColorTag
import com.daybook.app.data.model.DayOfWeek
import com.daybook.app.data.model.Habit
import com.daybook.app.data.model.HabitType
import com.daybook.app.ui.icons.Icons
import com.daybook.app.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * v0.5.5 — pure: should [AddHabitViewModel.saveHabit] carry `streak_started_at` / `streak_longest`
 * forward, or reset them to null / 0? True ONLY for a STREAK->STREAK edit (rename / icon / colour).
 * A brand-new habit ([isEdit] == false), a switch INTO STREAK, or a switch OUT OF STREAK all reset.
 * See `AddHabitViewModelStreakCarryTest`.
 */
fun keepStreakColumns(isEdit: Boolean, newType: HabitType, oldType: HabitType?): Boolean =
    isEdit && newType == HabitType.STREAK && oldType == HabitType.STREAK

/**
 * rec 3 (N2) — pure: the snooze a form should START at. An EDIT hydrates the item's own saved
 * value; a fresh ADD starts at the app-wide default. See `DefaultSnoozeSeedTest`.
 */
fun seedSnooze(isEdit: Boolean, savedValue: Int?, default: Int): Int =
    if (isEdit) savedValue ?: default else default

@HiltViewModel
class AddHabitViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {
    var title = ""
    var description = ""
    var iconKey = Icons.TASK
    var colorTag = ColorTag.AUTO
    var type = HabitType.INDIVIDUAL
    var times = mutableListOf<LocalTime>()
    var activeDays = mutableListOf<DayOfWeek>()
    var snoozeIntervalMinutes = 10
    var isArchived = false
    // Customization round (rec 8): per-habit notification text + "why" note. Blank => stored NULL.
    var promptMessage = ""
    var motivation = ""
    // Journal-as-habit round: the per-habit ordered question list. A type switch AWAY from JOURNAL
    // deliberately leaves this in place (harmless dead data on a non-Journal habit, matches how
    // promptMessage/motivation already survive type switches) rather than force-clearing it.
    var journalQuestions = mutableListOf<String>()

    /** rec 3 (N2) — the app-wide default snooze, for seeding a NEW habit form. */
    private val _defaultSnooze = MutableStateFlow(10)
    val defaultSnooze: StateFlow<Int> = _defaultSnooze.asStateFlow()
    // v0.5.5: STREAK ("Ongoing") passthrough — see [HabitFormState]. Carried forward on a
    // STREAK->STREAK edit, cleared on any type switch away from STREAK (see saveHabit).
    var streakStartedAt: Long? = null
    var streakLongest: Int = 0

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _nextReminderPreview = MutableStateFlow("")
    val nextReminderPreview: StateFlow<String> = _nextReminderPreview.asStateFlow()

    init {
        updateNextReminderPreview()
        safeLaunch {
            val d = appSettingsRepository.getSettings().defaultSnoozeMinutes
            _defaultSnooze.value = d
            // Only nudge the field if it is still at the hard-coded default (a NEW form). An Edit
            // form overwrites `snoozeIntervalMinutes` from the saved habit before save anyway.
            if (snoozeIntervalMinutes == 10) snoozeIntervalMinutes = d
        }
    }

    fun saveHabit(habitId: String? = null) {
        safeLaunch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null
            try {
                // v0.5.5: carry the running count forward ONLY on a STREAK->STREAK edit. A new
                // STREAK habit, or a switch INTO or OUT OF STREAK, resets to null / 0.
                val keepStreak = keepStreakColumns(
                    isEdit = habitId != null,
                    newType = type,
                    oldType = if (habitId != null) habitRepository.getHabitById(habitId)?.type else null
                )
                val newStartedAt = if (keepStreak) streakStartedAt else null
                val newLongest = if (keepStreak) streakLongest else 0
                val habit = Habit(
                    id = habitId ?: UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    iconKey = iconKey,
                    colorTag = colorTag,
                    // BATCH habits carry no per-habit times — the scheduler substitutes the app-wide
                    // check-in time. STREAK habits have no times at all. JOURNAL schedules exactly
                    // like INDIVIDUAL (B4), own times. Persisting a stray value on a type that
                    // doesn't use it would churn the definitions hash on every change.
                    timesJson = if (type == HabitType.INDIVIDUAL || type == HabitType.JOURNAL)
                        DateTimeUtils.timesToJson(times) else "",
                    activeDaysJson = DateTimeUtils.daysToJson(activeDays),
                    isArchived = isArchived,
                    snoozeIntervalMinutes = snoozeIntervalMinutes,
                    notificationId = 0,
                    type = type,
                    streakStartedAt = newStartedAt,
                    streakLongest = newLongest,
                    // rec 8: STREAK/JOURNAL never show a custom reminder text field (Phase 2's fixed
                    // "Tap to write today's entry" body for JOURNAL), so drop any stale value on that switch.
                    promptMessage = promptMessage.trim()
                        .takeIf { it.isNotBlank() && type != HabitType.STREAK && type != HabitType.JOURNAL },
                    motivation = motivation.trim().takeIf { it.isNotBlank() },
                    journalQuestionsJson = DateTimeUtils.journalQuestionsToJson(journalQuestions)
                )

                habitRepository.save(habit)
                occurrenceScheduler.syncHabit(habit.id)
                _successMessage.value = if (habitId == null) "Habit added successfully" else "Habit updated successfully"
                clearForm()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save habit: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteHabit(habitId: String) {
        safeLaunch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val habit = habitRepository.getHabitById(habitId)
                habit?.let { h ->
                    occurrenceScheduler.cancelHabit(h.id)
                    habitRepository.delete(h)
                    _successMessage.value = "Habit deleted successfully"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete habit: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearForm() {
        title = ""
        description = ""
        iconKey = Icons.TASK
        colorTag = ColorTag.AUTO
        type = HabitType.INDIVIDUAL
        times.clear()
        activeDays.clear()
        // rec 3 (N2) — reset to the stored default, not a hard 10.
        snoozeIntervalMinutes = _defaultSnooze.value
        isArchived = false
        promptMessage = ""
        motivation = ""
        streakStartedAt = null
        streakLongest = 0
        journalQuestions = mutableListOf()
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