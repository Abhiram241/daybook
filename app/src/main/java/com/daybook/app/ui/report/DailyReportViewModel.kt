package com.daybook.app.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.DailyReportRepository
import com.daybook.app.data.DailyReportData
import com.daybook.app.data.ai.AiChatMessage
import com.daybook.app.data.ai.AiChatRole
import com.daybook.app.data.ai.AiKeyStore
import com.daybook.app.data.ai.AiProviderId
import com.daybook.app.data.ai.AiProviderRegistry
import com.daybook.app.data.ai.AiResult
import com.daybook.app.data.buildChatSystemMessage
import com.daybook.app.data.buildDailyReportPrompt
import com.daybook.app.data.buildMultiDayReportPrompt
import com.daybook.app.data.parseReportCategories
import com.daybook.app.data.settingsFingerprint
import com.daybook.app.data.withoutExcluded
import com.daybook.app.data.fingerprint
import com.daybook.app.data.model.DailyReportAiSummary
import com.daybook.app.util.safeLaunch

import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** §3.1 — the two panels: the static report (always the default) and the AI summary. */
enum class ReportPanel { REPORT, AI_SUMMARY }

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.4 — one combine step's intermediate output. */
private data class ReportAndCounts(
    val report: DailyReportData?,
    val fingerprint: String,
    val hiddenSummary: Int,
    val hiddenChat: Int
)

data class DailyReportUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val calendarExpanded: Boolean = false,
    val weekStart: String = "MONDAY",
    val panel: ReportPanel = ReportPanel.REPORT,
    val report: DailyReportData? = null,
    /** §3.5 step 1 — only providers that currently have a saved key (per-metric-hide instinct:
     *  don't offer a provider you can't call). */
    val availableProviders: List<AiProviderId> = emptyList(),
    val selectedProvider: AiProviderId? = null,
    val selectedModel: String = "",
    val isGenerating: Boolean = false,
    val generationError: String? = null,
    // H7 fix — opening chat traverses the whole date range's Daily Report data (up to 8 Room
    // flows + several suspend queries PER DAY) with no loading state at all before this; the
    // Chat button now mirrors `isGenerating`'s `loading`/`enabled` wiring.
    val chatOpening: Boolean = false,
    // BEAST_HEALTH_REPORT_AUDIT.md M4 — the fingerprint of the meta-prompt + categories currently
    // configured, so `AiSummaryPanel` can tell a cached `report.aiSummary` apart from one
    // generated under different settings without re-reading settings itself.
    val currentSettingsFingerprint: String = "",
    // AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.4 — how many of the SELECTED day's rows
    // are currently hidden from each AI feature, for the "N entries hidden from AI · Manage"
    // caption. Zero means the caption doesn't show.
    val hiddenFromSummaryCount: Int = 0,
    val hiddenFromChatCount: Int = 0
)

@HiltViewModel
class DailyReportViewModel @Inject constructor(
    private val repository: DailyReportRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val aiKeyStore: AiKeyStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val _selectedDate = MutableStateFlow(LocalDate.now(zoneId))
    private val _calendarExpanded = MutableStateFlow(false)
    private val _panel = MutableStateFlow(ReportPanel.REPORT)
    private val _selectedProvider = MutableStateFlow<AiProviderId?>(null)
    private val _selectedModel = MutableStateFlow("")
    private val _isGenerating = MutableStateFlow(false)
    private val _generationError = MutableStateFlow<String?>(null)
    private val _chatOpening = MutableStateFlow(false)

    /** Corner-avatar profile (name + photo) for the tab header — same shape every other main tab uses. */
    val profile: kotlinx.coroutines.flow.StateFlow<com.daybook.app.ui.components.ProfileUi> =
        appSettingsRepository.observeSettings()
            .map { com.daybook.app.ui.components.ProfileUi(it.userName.trim(), it.profilePhotoPath) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.daybook.app.ui.components.ProfileUi("", null))

    private val weekStartFlow = appSettingsRepository.observeSettings().map { it.weekStart }.distinctUntilChanged()

    // §2.4 — the two exclusion lists, observed live so a toggle in AiExclusionsScreen updates
    // this screen's fingerprint/caption without needing it reopened.
    private val summaryExclusionsFlow = repository.observeAiExclusions(com.daybook.app.data.AiScope.SUMMARY)
    private val chatExclusionsFlow = repository.observeAiExclusions(com.daybook.app.data.AiScope.CHAT)

    // M4 — recomputed live off the same settings row `generate()` reads fresh each time, so a
    // change in Settings → Daily Report AI is reflected here without needing this screen reopened.
    // §2.4 — now also folds in the SUMMARY exclusion set's fingerprint (settingsFingerprint's new
    // third parameter), so hiding/un-hiding something re-triggers the "settings changed" hint.
    private val currentSettingsFingerprintFlow = combine(
        appSettingsRepository.observeSettings(), summaryExclusionsFlow
    ) { s, exclusions -> settingsFingerprint(s.aiMetaPrompt, s.aiReportCategories, exclusions.fingerprint()) }
        .distinctUntilChanged()

    private val reportFlow = _selectedDate.flatMapLatest { date -> repository.observeReport(date) }
        .flowOn(Dispatchers.Default)

    private val availableProvidersFlow = aiKeyStore.states.map { states ->
        AiProviderId.entries.filter { states[it]?.hasKey == true }
    }

    // L6 fix — `LocalDate.now(zoneId)` used to be evaluated straight inside the final combine
    // lambda below, only re-running when some OTHER source in this combine emitted; a screen left
    // open across midnight kept `today` (and so the date pickers' `maxDate`) on yesterday until
    // something unrelated changed. Folded in as its own combine source instead.
    private val todayFlow = com.daybook.app.util.currentDateFlow(zoneId)

    val uiState = combine(
        combine(_selectedDate, _calendarExpanded, weekStartFlow, _panel, todayFlow) { sel, exp, ws, panel, today ->
            arrayOf(sel, exp, ws, panel, today)
        },
        combine(
            reportFlow, currentSettingsFingerprintFlow, summaryExclusionsFlow, chatExclusionsFlow
        ) { report, fingerprint, summaryEx, chatEx ->
            val hiddenSummary = report?.let { it.todo.count(summaryEx::hidesTodo) + it.intake.count(summaryEx::hidesIntake) } ?: 0
            val hiddenChat = report?.let { it.todo.count(chatEx::hidesTodo) + it.intake.count(chatEx::hidesIntake) } ?: 0
            ReportAndCounts(report, fingerprint, hiddenSummary, hiddenChat)
        },
        combine(availableProvidersFlow, _selectedProvider, _selectedModel, aiKeyStore.states) { avail, sel, model, states ->
            arrayOf(avail, sel, model, states)
        },
        combine(_isGenerating, _generationError, _chatOpening) { gen, err, chatOpening -> Triple(gen, err, chatOpening) }
    ) { a, reportAndFingerprint, b, genErr ->
        @Suppress("UNCHECKED_CAST")
        val availableProviders = b[0] as List<AiProviderId>
        val selectedProvider = (b[1] as AiProviderId?) ?: availableProviders.firstOrNull()
        val states = b[3] as Map<AiProviderId, com.daybook.app.data.ai.AiProviderState>
        val selectedModelRaw = b[2] as String
        val selectedModel = selectedModelRaw.ifBlank { selectedProvider?.let { states[it]?.model } ?: "" }
        val (report, currentSettingsFingerprint, hiddenSummary, hiddenChat) = reportAndFingerprint
        DailyReportUiState(
            selectedDate = a[0] as LocalDate,
            today = a[4] as LocalDate,
            calendarExpanded = a[1] as Boolean,
            weekStart = a[2] as String,
            panel = a[3] as ReportPanel,
            report = report,
            availableProviders = availableProviders,
            selectedProvider = selectedProvider,
            selectedModel = selectedModel,
            isGenerating = genErr.first,
            generationError = genErr.second,
            chatOpening = genErr.third,
            currentSettingsFingerprint = currentSettingsFingerprint,
            hiddenFromSummaryCount = hiddenSummary,
            hiddenFromChatCount = hiddenChat
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyReportUiState())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _generationError.value = null
        // L7 fix — this cleared `_generationError` on date change but not `_chatError`, and the
        // AI panel renders `state.generationError ?: chatError` in one shared slot
        // (`DailyReportScreen.kt`) — a chat failure from a previous day kept showing in red under
        // the Generate button after navigating to a day it has nothing to do with.
        _chatError.value = null
    }

    fun toggleCalendarExpanded() { _calendarExpanded.value = !_calendarExpanded.value }

    fun setPanel(panel: ReportPanel) { _panel.value = panel }

    fun selectProvider(id: AiProviderId) {
        _selectedProvider.value = id
        _selectedModel.value = ""
    }

    fun setModel(model: String) { _selectedModel.value = model }

    /** §3.5 — synchronous, user-triggered suspend call only; no retry loop, no background worker. */
    fun generate() {
        // H2 — mirrors sendChatMessage()'s existing `_chatSending.value` re-entrancy guard: a
        // second rapid tap before recomposition disables the button must not launch a second
        // concurrent generation that races the first for `saveAiSummary`'s upsert.
        if (_isGenerating.value) return
        val state = uiState.value
        val provider = state.selectedProvider
        val model = state.selectedModel.trim()
        val report = state.report
        if (provider == null) {
            _generationError.value = "No AI provider has a saved key yet — add one in Settings → AI Providers."
            return
        }
        if (model.isBlank()) {
            _generationError.value = "Enter a model name for ${provider.label} in Settings → AI Providers."
            return
        }
        val apiKey = aiKeyStore.states.value[provider]?.apiKey
        if (apiKey.isNullOrBlank()) {
            _generationError.value = "That provider's key was removed. Add it again in Settings → AI Providers."
            return
        }
        safeLaunch {
            _isGenerating.value = true
            _generationError.value = null
            try {
                // DAILY_REPORT_REDESIGN_PLAN.md §2/§6 — the meta-prompt and the category toggles
                // are read fresh on every generate(), same as the theme/weekStart pattern already
                // used elsewhere in this ViewModel.
                val settings = appSettingsRepository.getSettings()
                // §2.4 — hide SUMMARY-scoped habits/reminders/entries before building the prompt.
                // Still sent even if everything in the enabled categories was hidden — the
                // sections just read "No … logged" (per §2.4's decided behaviour).
                val summaryExclusions = repository.getAiExclusions(com.daybook.app.data.AiScope.SUMMARY)
                val filteredReport = report?.withoutExcluded(summaryExclusions)?.first
                val prompt = buildDailyReportPrompt(
                    workout = filteredReport?.workout,
                    health = filteredReport?.health,
                    intake = filteredReport?.intake.orEmpty(),
                    todo = filteredReport?.todo.orEmpty(),
                    date = state.selectedDate,
                    weightUnit = filteredReport?.weightUnit ?: com.daybook.app.data.workout.WeightUnit.KG,
                    metaPrompt = settings.aiMetaPrompt,
                    categories = parseReportCategories(settings.aiReportCategories)
                )
                val result = withContext(Dispatchers.IO) {
                    AiProviderRegistry.forId(provider).complete(apiKey, model, prompt)
                }
                when (result) {
                    is AiResult.Success -> {
                        repository.saveAiSummary(
                            DailyReportAiSummary(
                                localDate = state.selectedDate.toString(),
                                provider = provider.name,
                                model = model,
                                summaryText = result.text,
                                generatedAt = System.currentTimeMillis(),
                                // M4 — the exact settings this generation used, so a later change
                                // to the meta-prompt/categories can be detected against this row.
                                settingsFingerprint = settingsFingerprint(
                                    settings.aiMetaPrompt,
                                    settings.aiReportCategories,
                                    summaryExclusions.fingerprint()
                                )
                            )
                        )
                    }
                    is AiResult.Failure -> _generationError.value = result.message
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Round 2 (Feature 4) — Chat with AI, full context of the day's report. Kept as independent
    // StateFlows (not folded into `uiState`'s combine) since chat is its own UI surface toggled on
    // top of the AI Summary panel, not a slice of the same derived report state. In-memory only
    // for the session (per the round's spec — no new persistence requirement for chat transcripts).
    // -------------------------------------------------------------------------------------------

    private val _chatOpen = MutableStateFlow(false)
    val chatOpen: StateFlow<Boolean> = _chatOpen.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

    private val _chatDraft = MutableStateFlow("")
    val chatDraft: StateFlow<String> = _chatDraft.asStateFlow()

    private val _chatSending = MutableStateFlow(false)
    val chatSending: StateFlow<Boolean> = _chatSending.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    /** H3 — a one-line notice shown once when this ViewModel is recreated after process death
     *  while the chat sheet had been left open: the transcript itself is still in-memory only
     *  (per the round's spec), but [KEY_CHAT_WAS_OPEN] survives process death via
     *  [SavedStateHandle], so this recreation can at least tell the difference between "chat was
     *  never opened" and "chat was open and the conversation is now gone" instead of silently
     *  reopening empty. */
    private val _chatEndedNotice = MutableStateFlow(
        savedStateHandle.get<Boolean>(KEY_CHAT_WAS_OPEN) == true
    )
    val chatEndedNotice: StateFlow<Boolean> = _chatEndedNotice.asStateFlow()

    fun dismissChatEndedNotice() {
        _chatEndedNotice.value = false
    }

    /** Opens the chat sheet, seeding the conversation with the user's configured multi-day context
     *  (DAILY_REPORT_REDESIGN_PLAN.md §7 — defaults to just [DailyReportUiState.selectedDate] when
     *  no custom range is set, byte-for-byte the same content the old single-day path produced).
     *  Reuses the Generate flow's already-selected provider/model (§3.5 step 1's picker: only
     *  providers with a saved key). */
    fun openChat() {
        // H7 fix — re-entrancy guard, same shape as generate()'s `_isGenerating` check: with no
        // loading state at all before this fix, an impatient re-tap while the first traversal was
        // still running launched a second full traversal concurrently.
        if (_chatOpening.value) return
        val state = uiState.value
        val provider = state.selectedProvider
        val model = state.selectedModel.trim()
        if (provider == null) {
            _chatError.value = "No AI provider has a saved key yet — add one in Settings → AI Providers."
            return
        }
        if (model.isBlank()) {
            _chatError.value = "Enter a model name for ${provider.label} in Settings → AI Providers."
            return
        }
        safeLaunch {
            _chatOpening.value = true
            try {
                val settings = appSettingsRepository.getSettings()
                val categories = parseReportCategories(settings.aiChatCategories)
                val startStr = settings.aiChatRangeStart
                val endStr = settings.aiChatRangeEnd
                // M3 fix — now goes through the same shared [parseChatRangeDate] the settings
                // screen/ViewModel use, rather than its own local `runCatching`.
                val parsedStart = com.daybook.app.data.parseChatRangeDate(startStr)
                val parsedEnd = com.daybook.app.data.parseChatRangeDate(endStr)
                val (rawStart, rawEnd) = if (parsedStart == null || parsedEnd == null) {
                    // §7.1 default: no custom range set yet — just whatever day the Report tab has open.
                    state.selectedDate to state.selectedDate
                } else {
                    parsedStart to parsedEnd
                }
                // H7 fix — the range pickers only impose a `maxDate`, so a "1 Jan – today" range
                // used to traverse ~250 days one at a time (8 Room flows + several suspend queries
                // EACH), with no upper bound at all. Clamp to the most recent
                // `MAX_CHAT_CONTEXT_DAYS` days of whatever range was configured, keeping the more
                // recent end (closer to "today", the common case) rather than the older one.
                val spanDays = java.time.temporal.ChronoUnit.DAYS.between(rawStart, rawEnd) + 1
                val clamped = spanDays > MAX_CHAT_CONTEXT_DAYS
                val rangeStart = if (clamped) rawEnd.minusDays(MAX_CHAT_CONTEXT_DAYS - 1) else rawStart
                val rangeEnd = rawEnd
                val days = repository.buildChatContext(rangeStart, rangeEnd)
                // AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.4 — filter the CHAT-scoped
                // hidden habits/reminders/entries out of each day BEFORE building the prompt.
                val chatExclusions = repository.getAiExclusions(com.daybook.app.data.AiScope.CHAT)
                val filteredDays = days.map { it.withoutExcluded(chatExclusions).first }
                val weightUnit = state.report?.weightUnit ?: com.daybook.app.data.workout.WeightUnit.KG
                // §1.4 — the chat meta-prompt is no longer prepended inside the data block; it now
                // lives in the system message itself (built below), between the built-in rules and
                // the data, so pass "" here.
                val context = buildMultiDayReportPrompt(filteredDays, categories, weightUnit, "")
                // H7 fix — a megabytes-long prompt was previously only ever discovered by the AI
                // provider rejecting it, surfaced as a generic "provider returned an error" with no
                // hint that the date range was the cause. Checked before dispatch so the message is
                // specific and actionable instead.
                if (context.length > MAX_CHAT_CONTEXT_CHARS) {
                    _chatError.value = "Your chat context is too large for that many days. Shorten the date range in Settings → Daily Report AI."
                    return@safeLaunch
                }
                // §3 — the "Hi" data-dump bug: a rewritten system message so a greeting/small talk gets
                // a normal conversational reply instead of an unsolicited full report. Fixed via prompt
                // wording only (decided — no message-content detection code).
                val clampNote = if (clamped) {
                    "(Note: your configured date range was longer than $MAX_CHAT_CONTEXT_DAYS days, " +
                        "so this only covers the most recent $MAX_CHAT_CONTEXT_DAYS days, " +
                        "${rangeStart.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
                        "${rangeEnd.format(DateTimeFormatter.ofPattern("d MMM"))}.)"
                } else ""
                // §1.4 — built via the shared pure helper (also unit-tested), in the decided order:
                // built-in rules, then the user's own Chat instructions (aiChatMetaPrompt — NOT
                // aiMetaPrompt, which is Summary-only), then the clamp note, then the data.
                val systemMessage = buildChatSystemMessage(settings.aiChatMetaPrompt, clampNote, context)
                _chatMessages.value = listOf(AiChatMessage(AiChatRole.SYSTEM, systemMessage))
                _chatDraft.value = ""
                _chatError.value = null
                _chatOpen.value = true
                _chatEndedNotice.value = false
                savedStateHandle[KEY_CHAT_WAS_OPEN] = true
            } catch (t: Throwable) {
                // M1 fix — `openChat()` used to rely on `safeLaunch`'s silent default handler for
                // ANY failure here (including `buildChatContext`'s `NoSuchElementException` if a
                // day's `observeReport` flow completed without emitting), which recorded to
                // Crashlytics and left the user with a Chat tap that did nothing at all, silently.
                com.daybook.app.util.recordUnhandledException(t)
                _chatError.value = "Couldn't open chat. Try again."
            } finally {
                _chatOpening.value = false
            }
        }
    }

    fun closeChat() {
        _chatOpen.value = false
        savedStateHandle[KEY_CHAT_WAS_OPEN] = false
    }

    fun onChatDraftChange(text: String) {
        _chatDraft.value = text
    }

    /** Same C9 idiom as [generate] — a synchronous, user-triggered suspend call, plain-language
     *  failure, no retry loop. The failed draft is preserved so the user can just tap send again. */
    fun sendChatMessage() {
        val draft = _chatDraft.value.trim()
        if (draft.isBlank() || _chatSending.value) return
        val state = uiState.value
        val provider = state.selectedProvider ?: return
        val model = state.selectedModel.trim()
        // M3 — same blank-model check generate() already has: `_selectedModel` isn't tied live
        // to the key store's model once manually touched, so it can go blank again after the
        // chat sheet is already open.
        if (model.isBlank()) {
            _chatError.value = "Enter a model name for ${provider.label} in Settings → AI Providers."
            return
        }
        val apiKey = aiKeyStore.states.value[provider]?.apiKey
        if (apiKey.isNullOrBlank()) {
            _chatError.value = "That provider's key was removed. Add it again in Settings → AI Providers."
            return
        }
        val history = _chatMessages.value + AiChatMessage(AiChatRole.USER, draft)
        _chatMessages.value = history
        _chatDraft.value = ""
        safeLaunch {
            _chatSending.value = true
            _chatError.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    AiProviderRegistry.forId(provider).chat(apiKey, model, history)
                }
                when (result) {
                    is AiResult.Success ->
                        _chatMessages.value = _chatMessages.value + AiChatMessage(AiChatRole.ASSISTANT, result.text)
                    is AiResult.Failure -> _chatError.value = result.message
                }
            } finally {
                _chatSending.value = false
            }
        }
    }

    private companion object {
        /** H3 — survives process death via [SavedStateHandle]; not the transcript itself, just
         *  enough to tell "chat was open when this process died" from "chat was never opened." */
        const val KEY_CHAT_WAS_OPEN = "daily_report_chat_was_open"

        /** BEAST_HEALTH_REPORT_AUDIT.md H7 — the configurable chat context range had no upper
         *  bound at all; one month matches the order of magnitude of every other "recent window"
         *  default already in this app (e.g. `HealthRangePreset.LAST_30_DAYS`). */
        const val MAX_CHAT_CONTEXT_DAYS = 31L

        /** H7 — a specific, actionable size check before dispatch instead of letting the provider
         *  reject an oversized prompt with a generic error. ~500k chars is a conservative
         *  multi-hundred-thousand-token ceiling most providers reject well before. */
        const val MAX_CHAT_CONTEXT_CHARS = 500_000
    }
}
