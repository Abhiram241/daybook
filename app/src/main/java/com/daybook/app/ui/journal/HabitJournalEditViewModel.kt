package com.daybook.app.ui.journal

import com.daybook.app.util.safeLaunch

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.JournalQa
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One editable field: a saved question label + its current answer text. Replaced (not mutated) in
 *  [fields] on every edit so the `SnapshotStateList` reports the change to Compose. */
data class QaField(val question: String, val answer: String)

data class EditUiState(
    val title: String = "",
    val scheduledTime: String = "",
    val busy: Boolean = false,
    val saved: Boolean = false,
    val missing: Boolean = false
)

/**
 * Journal-as-habit round (Phase 5) — the plain, non-chat edit-form for revisiting an already
 * answered Journal-habit entry (B8). Every question is shown at once, each with its saved answer
 * in an editable field; Save re-encodes the whole set and writes it in place (no new event, no
 * status/`responded_at` change) — mirrors [OccurrenceScheduler.logHabitJournal]'s `isEdit = true`
 * path, itself modeled on the pre-existing FoodMed `editJournalResponse`.
 */
@HiltViewModel
class HabitJournalEditViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val occurrenceId: String = savedStateHandle.get<String>("occurrenceId") ?: ""

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    val fields = mutableStateListOf<QaField>()

    init {
        safeLaunch {
            val occ = habitRepository.database.habitOccurrenceDao().getOccurrenceById(occurrenceId)
            if (occ == null) { _state.update { it.copy(missing = true) }; return@safeLaunch }
            val habit = habitRepository.getHabitById(occ.habitId)
            val decoded = JournalQa.decode(occ.qaJson)
            fields.clear()
            fields.addAll(decoded.map { QaField(it.first, it.second) })
            _state.update {
                it.copy(
                    title = habit?.title ?: "Entry",
                    scheduledTime = DateTimeUtils.formatTime(DateTimeUtils.timestampToLocalTime(occ.scheduledFor))
                )
            }
        }
    }

    fun onAnswerChange(index: Int, value: String) {
        if (index !in fields.indices) return
        fields[index] = fields[index].copy(answer = value)
    }

    fun save() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        safeLaunch {
            val qaJson = JournalQa.encode(fields.map { it.question to it.answer })
            runCatching { occurrenceScheduler.logHabitJournal(occurrenceId, qaJson, isEdit = true) }
            _state.update { it.copy(busy = false, saved = true) }
        }
    }
}
