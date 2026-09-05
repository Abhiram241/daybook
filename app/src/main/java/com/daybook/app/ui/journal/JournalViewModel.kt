package com.daybook.app.ui.journal

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.JournalQa
import com.daybook.app.data.LogResult
import com.daybook.app.data.MAX_JOURNAL_CHARS
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.model.Event
import com.daybook.app.data.model.Occurrence
import com.daybook.app.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** v0.5.4 Phase 2 (D3): what a journal save writes — the newline-joined non-blank answers plus the
 *  full ordered `[{"q":…,"a":…}]` snapshot. */
data class JournalSave(val responseText: String, val qaJson: String)

/**
 * v0.5.4 Phase 2 (§2.6) — pure. Pairs each configured question with the caller's answer for that
 * index (a missing answer is treated as blank), trims each answer, and:
 *  - returns **null** when every aligned answer is blank (nothing to save);
 *  - `responseText` = the non-blank answers joined by "\n" (trailing/interior blanks dropped);
 *  - `qaJson` = every question snapshotted in ask order, even ones with a blank answer, so the
 *    entry's snapshot is complete;
 *  - the blob (and the response text) are capped at [MAX_JOURNAL_CHARS].
 */
fun journalQaPayload(questions: List<String>, answers: List<String>): JournalSave? {
    val aligned = questions.indices.map { answers.getOrElse(it) { "" }.trim() }
    if (aligned.all { it.isEmpty() }) return null
    val responseText = aligned.filter { it.isNotEmpty() }.joinToString("\n").take(MAX_JOURNAL_CHARS)
    val qaJson = JournalQa.encode(questions.zip(aligned)).take(MAX_JOURNAL_CHARS)
    return JournalSave(responseText = responseText, qaJson = qaJson)
}

/**
 * Journal Mode edit-in-place — pure: true when the loaded occurrence was already resolved, so a
 * save updates it in place (keep responded_at, no duplicate event) rather than logging fresh.
 */
internal fun journalIsEdit(loadedStatus: Occurrence.Status?): Boolean =
    loadedStatus != null && loadedStatus != Occurrence.Status.PENDING

/**
 * v0.5.4 Phase 4 (§4.4) — pure step-index maths for the conversational entry flow. [clampIndex]
 * keeps `next()` / `back()` inside `0..size-1` (and returns 0 for an empty step list, before the
 * VM's init has resolved the question snapshot). [isLastStep] gates the Save-vs-Next primary button.
 */
internal fun clampIndex(i: Int, size: Int): Int {
    if (size <= 0) return 0
    return i.coerceIn(0, size - 1)
}

internal fun isLastStep(i: Int, size: Int): Boolean = size > 0 && i >= size - 1

/**
 * v0.5.2 §3 / 5B.5 — the dedicated journal page.
 *
 * v0.5.4 Phase 4: a step-by-step (question, answer) conversational flow.
 *
 * Journal-as-habit round: Journal is retired as an Intake (`TaskType`) concept — the Type picker no
 * longer offers it, and the migration purges every existing JOURNAL-typed `FoodMedTask` outright, so
 * this screen is unreachable from live UI going forward (kept, unmodified in spirit, only so a
 * genuinely-ancient still-PENDING deep link or notification cannot crash; B3's TaskType.JOURNAL
 * import-remap means no *newly imported* row can reach it either). The global per-question-set
 * repository this screen used to read from is gone (retired with `journal_questions`); the fallback
 * single default question below is now the only source, matching this screen's belt-and-braces D6
 * fallback that already existed before.
 *
 * Two entry shapes, one route (`journal/{arg0}/{slotMillis}`):
 *  - `slotMillis <= 0` → `arg0` is an existing occurrence id (notification tap / a real PENDING row).
 *    Save resolves through [OccurrenceScheduler.logJournal].
 *  - `slotMillis > 0`  → `arg0` is a task id and this is a §9 backfill of a synthetic past slot with
 *    no row yet. Save resolves through [OccurrenceScheduler.backfillFoodMed].
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val foodMedRepository: FoodMedRepository,
    private val occurrenceScheduler: OccurrenceScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object { const val DEFAULT_QUESTION = "What's on your mind?" }

    private val arg0: String = savedStateHandle.get<String>("arg0") ?: ""
    private val slotMillis: Long = savedStateHandle.get<String>("slotMillis")?.toLongOrNull() ?: 0L
    private val isBackfill: Boolean = slotMillis > 0L

    /** One question and the answer typed so far. */
    data class QaStep(val question: String, val answer: String)

    data class UiState(
        val title: String = "",
        val scheduledTime: String = "",
        val steps: List<QaStep> = emptyList(),
        val index: Int = 0,
        val busy: Boolean = false,
        val saved: Boolean = false,
        val missing: Boolean = false,
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): non-null when the last save() call was
        // rejected (canBackfill/month-residency/missing-row) — `saved` stays false in that case,
        // where it previously and silently became true.
        val rejectedMessage: String? = null
    ) {
        /** "3 / 7" step indicator. */
        val progressLabel: String get() = "${index + 1} / ${steps.size}"
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Resolved lazily in init: the occurrence id + task id + slot instant to operate on.
    private var taskId: String = ""
    private var occurrenceId: String? = null
    private var slotInstant: Long = 0L
    // Journal Mode edit-in-place: true when the loaded occurrence was already resolved.
    private var wasResolved: Boolean = false

    /** History-link target — matches DetailViewModel's `"food_med"`; the id is the owning task. */
    var itemType: String = "food_med"
        private set
    var itemId: String = ""
        private set

    init {
        safeLaunch {
            // Journal-as-habit round: the global question set is gone; this screen is unreachable
            // from live UI (see class doc) so a single fixed fallback question is sufficient.
            val snapshot = listOf(DEFAULT_QUESTION)

            if (isBackfill) {
                val task = foodMedRepository.getTaskById(arg0)
                if (task == null) { _state.update { it.copy(missing = true) }; return@safeLaunch }
                taskId = task.id
                itemId = task.id
                slotInstant = slotMillis
                _state.update {
                    it.copy(
                        title = task.label,
                        scheduledTime = DateTimeUtils.formatTime(DateTimeUtils.timestampToLocalTime(slotMillis)),
                        steps = snapshot.map { q -> QaStep(q, "") },
                        index = 0
                    )
                }
            } else {
                val occ = foodMedRepository.database.foodMedOccurrenceDao().getOccurrenceById(arg0)
                if (occ == null) { _state.update { it.copy(missing = true) }; return@safeLaunch }
                occurrenceId = occ.id
                taskId = occ.taskId
                itemId = occ.taskId
                slotInstant = occ.scheduledFor
                wasResolved = journalIsEdit(occ.status)
                val task = foodMedRepository.getTaskById(occ.taskId)
                // v0.5.4 Phase 4 (D3): an already-resolved entry's questions come from ITS OWN
                // decoded snapshot, not the current global set — renaming a global question later
                // must not rewrite this entry. A blank/empty snapshot (fresh PENDING row, or a
                // pre-v0.5.4 row that survived the D5 wipe) falls back to the global set.
                val decoded = if (wasResolved) JournalQa.decode(occ.qaJson) else emptyList()
                val steps = if (decoded.isNotEmpty()) {
                    decoded.map { QaStep(it.first, it.second) }
                } else {
                    snapshot.map { q -> QaStep(q, "") }
                }
                _state.update {
                    it.copy(
                        title = task?.label ?: "Entry",   // v0.5.3 Phase 7 (#37) — matches JournalScreen's fallback
                        scheduledTime = DateTimeUtils.formatTime(DateTimeUtils.timestampToLocalTime(occ.scheduledFor)),
                        steps = steps,
                        index = 0
                    )
                }
            }
        }
    }

    /** Update the answer for the step currently on screen. */
    fun onAnswerChange(v: String) = _state.update { s ->
        if (s.index !in s.steps.indices) return@update s
        s.copy(steps = s.steps.toMutableList().also { it[s.index] = it[s.index].copy(answer = v) })
    }

    /** Advance to the next question; clamps to the last step (the primary button is Save there). */
    fun next() = _state.update { it.copy(index = clampIndex(it.index + 1, it.steps.size)) }

    /** Step back to the previous question, re-showing its typed answer; clamps to step 0. */
    fun back() = _state.update { it.copy(index = clampIndex(it.index - 1, it.steps.size)) }

    fun save() {
        val s = _state.value
        if (s.busy) return
        // All-blank -> null -> Save stays disabled; never write an empty LOGGED row.
        val payload = journalQaPayload(s.steps.map { it.question }, s.steps.map { it.answer }) ?: return
        _state.update { it.copy(busy = true, rejectedMessage = null) }
        safeLaunch {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 9 (C-4): `runCatching { }.getOrNull()` used to
            // discard the scheduler's result entirely and unconditionally report `saved = true`.
            // Now: a thrown exception OR an explicit LogResult.Rejected both surface as a rejection
            // instead of a false "Saved".
            val result = runCatching {
                val id = occurrenceId
                if (isBackfill || id == null) {
                    occurrenceScheduler.backfillFoodMed(
                        taskId, slotInstant, Occurrence.Status.LOGGED,
                        payload.responseText, payload.qaJson, Event.Action.REPLIED
                    )
                } else {
                    occurrenceScheduler.logJournal(id, payload.responseText, payload.qaJson, isEdit = wasResolved)
                }
            }.getOrElse { LogResult.Rejected("Couldn't save: ${it.message}") }
            _state.update {
                when (result) {
                    LogResult.Success -> it.copy(busy = false, saved = true)
                    is LogResult.Rejected -> it.copy(busy = false, rejectedMessage = result.reason)
                    // AlreadyResolved is only ever returned by the notification-Reply-specific
                    // logFoodMedFromNotificationReply (Phase 12, N-7) — unreachable from this
                    // screen's own backfillFoodMed/logJournal calls. Handled defensively anyway.
                    LogResult.AlreadyResolved -> it.copy(busy = false, rejectedMessage = "Already logged.")
                }
            }
        }
    }
}
