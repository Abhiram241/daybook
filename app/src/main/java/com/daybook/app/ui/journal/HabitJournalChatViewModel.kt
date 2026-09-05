package com.daybook.app.ui.journal

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.HabitRepository
import com.daybook.app.data.JournalQa
import com.daybook.app.data.LogResult
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.Occurrence
import com.daybook.app.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Journal-as-habit round (Phase 4) — one bubble in the chat transcript. The "live" question (asked,
 * not yet answered) is pushed into [ChatUiState.messages] immediately, per the plan's simpler
 * mental model: the whole conversation so far, including the just-asked question, is always in the
 * transcript; the compose box below is just "type your reply to the last bubble".
 */
sealed class ChatMessage {
    data class Question(val text: String) : ChatMessage()
    data class Answer(val text: String) : ChatMessage()
}

data class ChatUiState(
    val title: String = "",
    val scheduledTime: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val totalQuestions: Int = 0,
    val answeredCount: Int = 0,
    val draftAnswer: String = "",
    val allAnswered: Boolean = false,
    val busy: Boolean = false,
    val saved: Boolean = false,
    val missing: Boolean = false,
    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): non-null when the last sendAnswer() save was
    // rejected (canBackfill/month-residency/missing-row) — `saved` stays false in that case.
    val rejectedMessage: String? = null
)

/**
 * Journal-as-habit round (Phase 4) — pure chat-advance step, unit-testable without a ViewModel.
 * Appends the sent [answer] right after the live (last) Question bubble, then pushes the NEXT
 * question's bubble immediately if one remains (per the plan's "whole conversation so far,
 * including the just-asked question" model). Returns the new transcript and whether every
 * question is now answered.
 */
internal fun advanceChat(
    messages: List<ChatMessage>,
    questions: List<String>,
    answeredCount: Int,
    answer: String
): Pair<List<ChatMessage>, Boolean> {
    if (answeredCount !in questions.indices) return messages to true
    val trimmed = answer.trim()
    val withAnswer = messages + ChatMessage.Answer(trimmed)
    val nextIndex = answeredCount + 1
    return if (nextIndex in questions.indices) {
        (withAnswer + ChatMessage.Question(questions[nextIndex])) to false
    } else {
        withAnswer to true
    }
}

/**
 * Journal-as-habit round (Phase 4) — the habit-side chat screen's ViewModel. Modeled on
 * [JournalViewModel] for occurrence/backfill resolution, but the UI shape is a transcript, not a
 * stepper (see [ChatUiState]).
 *
 * Two entry shapes, one route (`habit_journal_chat/{arg0}/{slotMillis}`):
 *  - `slotMillis <= 0` -> `arg0` is an existing PENDING occurrence id.
 *  - `slotMillis > 0`  -> `arg0` is a habit id and this is a §9-style backfill of a past slot.
 */
@HiltViewModel
class HabitJournalChatViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object { const val DEFAULT_QUESTION = "What's on your mind?" }

    private val arg0: String = savedStateHandle.get<String>("arg0") ?: ""
    private val slotMillis: Long = savedStateHandle.get<String>("slotMillis")?.toLongOrNull() ?: 0L
    private val isBackfill: Boolean = slotMillis > 0L

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var habitId: String = ""
    private var occurrenceId: String? = null
    private var slotInstant: Long = 0L
    private var questions: List<String> = listOf(DEFAULT_QUESTION)
    /** Only ever holds NON-blank answers — Send is disabled on a blank draft, so no chat entry can
     *  ever contain a blank-answer pair (unlike the old stepper, which allowed skipping). */
    private val answeredPairs = mutableListOf<Pair<String, String>>()

    var itemType: String = "habit"
        private set
    var itemId: String = ""
        private set

    init {
        safeLaunch {
            if (isBackfill) {
                val habit = habitRepository.getHabitById(arg0)
                if (habit == null) { _state.update { it.copy(missing = true) }; return@safeLaunch }
                habitId = habit.id
                itemId = habit.id
                slotInstant = slotMillis
                questions = DateTimeUtils.jsonToJournalQuestions(habit.journalQuestionsJson)
                    .ifEmpty { listOf(DEFAULT_QUESTION) }
                _state.update {
                    it.copy(
                        title = habit.title,
                        scheduledTime = DateTimeUtils.formatTime(DateTimeUtils.timestampToLocalTime(slotMillis)),
                        messages = listOf(ChatMessage.Question(questions[0])),
                        totalQuestions = questions.size,
                        answeredCount = 0
                    )
                }
            } else {
                val occ = habitRepository.database.habitOccurrenceDao().getOccurrenceById(arg0)
                if (occ == null) { _state.update { it.copy(missing = true) }; return@safeLaunch }
                // B8 defense-in-depth: the caller (Home/Detail/notification-tap) should already
                // route an already-resolved occurrence to the edit-form, never here. If somehow
                // reached anyway, don't render a chat over already-answered data.
                if (occ.status != Occurrence.Status.PENDING) {
                    _state.update { it.copy(missing = true) }
                    return@safeLaunch
                }
                occurrenceId = occ.id
                habitId = occ.habitId
                itemId = occ.habitId
                slotInstant = occ.scheduledFor
                val habit = habitRepository.getHabitById(occ.habitId)
                questions = habit?.journalQuestionsJson?.let(DateTimeUtils::jsonToJournalQuestions)
                    ?.ifEmpty { listOf(DEFAULT_QUESTION) } ?: listOf(DEFAULT_QUESTION)

                // B1 draft-resume: replay whatever was already sent (a prior auto-saved draft).
                answeredPairs.addAll(JournalQa.decode(occ.qaJson))
                val startIndex = answeredPairs.size.coerceAtMost(questions.size)
                val messages = mutableListOf<ChatMessage>()
                answeredPairs.take(startIndex).forEach { (q, a) ->
                    messages += ChatMessage.Question(q); messages += ChatMessage.Answer(a)
                }
                val allAnswered = startIndex >= questions.size
                if (!allAnswered) messages += ChatMessage.Question(questions[startIndex])

                _state.update {
                    it.copy(
                        title = habit?.title ?: "Entry",
                        scheduledTime = DateTimeUtils.formatTime(DateTimeUtils.timestampToLocalTime(occ.scheduledFor)),
                        messages = messages,
                        totalQuestions = questions.size,
                        answeredCount = startIndex,
                        allAnswered = allAnswered
                    )
                }
            }
        }
    }

    fun onDraftChange(v: String) = _state.update { it.copy(draftAnswer = v) }

    fun sendAnswer() {
        val s = _state.value
        if (s.busy || s.draftAnswer.isBlank() || s.allAnswered) return
        val answer = s.draftAnswer.trim()
        val (newMessages, allAnswered) = advanceChat(s.messages, questions, s.answeredCount, answer)
        val question = questions.getOrElse(s.answeredCount) { "" }
        answeredPairs.add(question to answer)
        _state.update {
            it.copy(
                messages = newMessages,
                answeredCount = it.answeredCount + 1,
                draftAnswer = "",
                allAnswered = allAnswered
            )
        }
        safeLaunch {
            val qaJson = JournalQa.encode(answeredPairs)
            if (allAnswered) {
                _state.update { it.copy(busy = true, rejectedMessage = null) }
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): was `runCatching { }` with the
                // result discarded and `saved = true` set unconditionally.
                val result = runCatching {
                    val id = occurrenceId
                    if (isBackfill || id == null) {
                        occurrenceScheduler.backfillHabitJournal(habitId, slotInstant, qaJson)
                    } else {
                        occurrenceScheduler.logHabitJournal(id, qaJson, isEdit = false)
                    }
                }.getOrElse { LogResult.Rejected("Couldn't save: ${it.message}") }
                _state.update {
                    when (result) {
                        LogResult.Success -> it.copy(busy = false, saved = true)
                        is LogResult.Rejected -> it.copy(busy = false, rejectedMessage = result.reason)
                        // AlreadyResolved is only returned by the notification-Reply-specific
                        // logFoodMedFromNotificationReply (Phase 12, N-7) — unreachable from this
                        // screen's own backfillHabitJournal/logHabitJournal calls. Handled
                        // defensively anyway.
                        LogResult.AlreadyResolved -> it.copy(busy = false, rejectedMessage = "Already logged.")
                    }
                }
            } else {
                val id = occurrenceId
                if (id != null) occurrenceScheduler.saveHabitJournalDraft(id, qaJson)
            }
        }
    }
}
