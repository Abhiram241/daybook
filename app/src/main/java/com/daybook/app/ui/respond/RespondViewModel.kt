package com.daybook.app.ui.respond

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.Occurrence
import com.daybook.app.data.model.RedFlag
import com.daybook.app.data.model.TaskType
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.util.notification.NotificationUtils
import com.daybook.app.util.streak.calculateHabitStreaks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * v0.5.3 item 6 — the combined "fill-in" page reached from a habit or FOOD/MED/CUSTOM reminder
 * notification tap (JOURNAL keeps its own [com.daybook.app.ui.journal.JournalScreen]).
 *
 * Route: `respond/{occId}?isHabit={isHabit}`. Journal Mode: an already-resolved INTAKE occurrence
 * opens directly in the editable form, pre-filled, and saving updates it in place (no duplicate,
 * no timeline reorder). A resolved HABIT occurrence still renders read-only with an "Undo".
 */
@HiltViewModel
class RespondViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val foodMedRepository: FoodMedRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val occId: String = savedStateHandle.get<String>("occId") ?: ""
    private val isHabit: Boolean =
        savedStateHandle.get<String>("isHabit")?.toBooleanStrictOrNull() ?: false

    enum class Kind { HABIT, INTAKE }

    data class UiState(
        val title: String = "",
        val scheduledTime: String = "",
        val kind: Kind = Kind.INTAKE,
        // The default literal lives only in NotificationUtils.resolvePrompt (v0.5.3 Part 5).
        val promptPlaceholder: String = NotificationUtils.resolvePrompt(null),
        val reply: String = "",
        // v0.5.4: FOOD-only red-flag capture. [isFood] gates the extra UI; the two value fields
        // pre-fill from the reminder's defaults and are editable per log.
        val isFood: Boolean = false,
        val redFlag: RedFlag = RedFlag.NONE,
        val suspectedFood: String = "",
        // v0.5.2 build 8: FOOD-only "outside food" marker; pre-fills from the reminder default.
        val outsideFood: Boolean = false,
        val streak: Int = 0,
        // HABIT-only: a resolved habit occurrence renders read-only with an "Undo". Journal Mode
        // removed this intermediate for INTAKE — a resolved intake log now opens editable.
        val readOnly: Boolean = false,
        // Journal Mode: true when this INTAKE occurrence was already resolved when the screen
        // opened. The form still opens editable + pre-filled; the button reads "Save" not "Log",
        // "Skip" is hidden, and log() re-saves via the edit-in-place path.
        val isEdit: Boolean = false,
        val statusLabel: String? = null,
        val busy: Boolean = false,
        val done: Boolean = false,
        val missing: Boolean = false,
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): non-null when the last log() call was
        // rejected — `done` stays false in that case, where it previously and silently became true.
        val rejectedMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState(kind = if (isHabit) Kind.HABIT else Kind.INTAKE))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** History link target — matches DetailViewModel's `"habit"` / `"food_med"`. */
    var itemType: String = if (isHabit) "habit" else "food_med"
        private set
    var itemId: String = ""
        private set

    init {
        safeLaunch {
            if (isHabit) {
                val occ = habitRepository.database.habitOccurrenceDao().getOccurrenceById(occId)
                if (occ == null) { _state.update { it.copy(missing = true) }; return@safeLaunch }
                itemId = occ.habitId
                val habit = habitRepository.getHabitById(occ.habitId)
                val resolved = occ.status != Occurrence.Status.PENDING
                val all = habitRepository.database.habitOccurrenceDao()
                    .getOccurrencesForHabitInTimeRange(occ.habitId, 0L, Long.MAX_VALUE).first()
                // Same treatment HomeViewModel's streak flows get: the O(n) day-bucketing pass over
                // the habit's whole history does not belong on the main thread.
                val streak = withContext(Dispatchers.Default) {
                    calculateHabitStreaks(all, LocalDate.now()).currentStreak
                }
                _state.update {
                    it.copy(
                        title = habit?.title ?: "Reminder",
                        scheduledTime = DateTimeUtils.formatTime(
                            DateTimeUtils.timestampToLocalTime(occ.scheduledFor)
                        ),
                        kind = Kind.HABIT,
                        streak = streak,
                        readOnly = resolved,
                        statusLabel = statusLabelFor(occ.status)
                    )
                }
            } else {
                val occ = foodMedRepository.database.foodMedOccurrenceDao().getOccurrenceById(occId)
                if (occ == null) { _state.update { it.copy(missing = true) }; return@safeLaunch }
                itemId = occ.taskId
                val task = foodMedRepository.getTaskById(occ.taskId)
                val resolved = occ.status != Occurrence.Status.PENDING
                val isFood = task?.type == TaskType.FOOD
                _state.update {
                    it.copy(
                        title = task?.label ?: "Reminder",
                        scheduledTime = DateTimeUtils.formatTime(
                            DateTimeUtils.timestampToLocalTime(occ.scheduledFor)
                        ),
                        kind = Kind.INTAKE,
                        promptPlaceholder = NotificationUtils.resolvePrompt(task?.promptMessage),
                        reply = if (resolved) occ.responseText else "",
                        isFood = isFood,
                        // Resolved -> show what was recorded. Fresh -> pre-fill from the reminder's
                        // defaults so a known trigger reminder starts flagged.
                        redFlag = if (resolved) (occ.redFlag ?: RedFlag.NONE)
                            else (task?.defaultRedFlag ?: RedFlag.NONE),
                        suspectedFood = if (resolved) occ.suspectedFood.orEmpty()
                            else task?.defaultSuspectedFood.orEmpty(),
                        outsideFood = if (resolved) (occ.outsideFood == true)
                            else (task?.defaultOutsideFood == true),
                        // Journal Mode: no read-only intermediate for INTAKE — open editable,
                        // pre-filled; isEdit drives the "Save" label and hides "Skip".
                        isEdit = resolved
                    )
                }
            }
        }
    }

    private fun statusLabelFor(status: Occurrence.Status): String? = when (status) {
        Occurrence.Status.COMPLETED -> "Done"
        Occurrence.Status.SKIPPED -> "Skipped"
        Occurrence.Status.LOGGED -> "Logged"
        Occurrence.Status.PENDING -> null
    }

    fun onReplyChange(v: String) = _state.update { it.copy(reply = v) }

    fun onRedFlagChange(f: RedFlag) = _state.update { it.copy(redFlag = f) }

    fun onSuspectedFoodChange(v: String) = _state.update { it.copy(suspectedFood = v) }

    fun onOutsideFoodChange(v: Boolean) = _state.update { it.copy(outsideFood = v) }

    fun complete() = resolve { occurrenceScheduler.completeHabit(occId) }

    fun skip() = resolve {
        if (isHabit) occurrenceScheduler.skipHabit(occId) else occurrenceScheduler.skipFoodMed(occId)
    }

    fun log() {
        val s = _state.value
        if (s.reply.isBlank() || s.busy) return
        _state.update { it.copy(busy = true, rejectedMessage = null) }
        safeLaunch {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): the shared `resolve {}` helper below
            // (complete/skip/undo) discards its action's result by design — those never reject.
            // log() is the one action that can, so it handles its own result instead of going
            // through `resolve`.
            val result = runCatching {
                occurrenceScheduler.logFoodMed(
                    occId,
                    s.reply.trim(),
                    redFlag = if (s.isFood) s.redFlag else null,
                    suspectedFood = if (s.isFood) s.suspectedFood else null,
                    outsideFood = if (s.isFood) s.outsideFood else null,
                    isEdit = s.isEdit
                )
            }.getOrElse { com.daybook.app.data.LogResult.Rejected("Couldn't save: ${it.message}") }
            _state.update {
                when (result) {
                    com.daybook.app.data.LogResult.Success -> it.copy(busy = false, done = true)
                    is com.daybook.app.data.LogResult.Rejected ->
                        it.copy(busy = false, rejectedMessage = result.reason)
                    // AlreadyResolved is only returned by the notification-Reply-specific
                    // logFoodMedFromNotificationReply (Phase 12, N-7) — unreachable from this
                    // screen's own logFoodMed call. Handled defensively anyway.
                    com.daybook.app.data.LogResult.AlreadyResolved ->
                        it.copy(busy = false, rejectedMessage = "Already logged.")
                }
            }
        }
    }

    fun undo() = resolve {
        if (isHabit) occurrenceScheduler.revertHabit(occId) else occurrenceScheduler.revertFoodMed(occId)
    }

    private inline fun resolve(crossinline action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        safeLaunch {
            runCatching { action() }
            _state.update { it.copy(busy = false, done = true) }
        }
    }
}
