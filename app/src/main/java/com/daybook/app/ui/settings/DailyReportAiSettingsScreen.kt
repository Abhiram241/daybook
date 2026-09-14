package com.daybook.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.ReportCategory
import com.daybook.app.data.sync.SyncStatus
import com.daybook.app.ui.DaybookDatePickerDialog
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.FormGroup
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRow
import com.daybook.app.ui.components.SettingsRowDivider
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PROMPT_MAX_CHARS = 2000

/**
 * DAILY_REPORT_REDESIGN_PLAN.md §2/§6/§7 — one destination hosting all Daily Report AI settings.
 *
 * Restyled + fixed (build 44): the page is two clearly separated blocks — AI Summary (instructions,
 * categories) and Chat (instructions, date range, categories) — then Privacy. Each instructions box
 * has an explicit Save button (the old save-on-focus-loss never fired when leaving the screen
 * straight from the keyboard, and its draft was seeded from a placeholder "" before settings
 * loaded). Saved values sync to the account's Firestore doc via `CloudSyncRepository`.
 */
@Composable
fun DailyReportAiSettingsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenAiExclusions: (String) -> Unit = {},
    viewModel: DailyReportAiSettingsViewModel = hiltViewModel()
) {
    val metaPrompt by viewModel.metaPrompt.collectAsStateWithLifecycle()
    val chatMetaPrompt by viewModel.chatMetaPrompt.collectAsStateWithLifecycle()
    val summarySavedAt by viewModel.summarySavedAt.collectAsStateWithLifecycle()
    val chatSavedAt by viewModel.chatSavedAt.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val reportCategories by viewModel.reportCategories.collectAsStateWithLifecycle()
    val chatCategories by viewModel.chatCategories.collectAsStateWithLifecycle()
    val chatRangeStart by viewModel.chatRangeStart.collectAsStateWithLifecycle()
    val chatRangeEnd by viewModel.chatRangeEnd.collectAsStateWithLifecycle()
    val hiddenFromSummaryCount by viewModel.hiddenFromSummaryCount.collectAsStateWithLifecycle()
    val hiddenFromChatCount by viewModel.hiddenFromChatCount.collectAsStateWithLifecycle()

    val summaryDraft = rememberPromptDraft(metaPrompt)
    val chatDraft = rememberPromptDraft(chatMetaPrompt)
    val anyUnsaved = summaryDraft.isDirty || chatDraft.isDirty

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    // M3 fix — a malformed stored value degrades to null instead of throwing during composition.
    val startDate = com.daybook.app.data.parseChatRangeDate(chatRangeStart)
    val endDate = com.daybook.app.data.parseChatRangeDate(chatRangeEnd)
    val rangeSet = startDate != null && endDate != null

    val saveSummary = {
        summaryDraft.commitTrim()
        viewModel.saveMetaPrompt(summaryDraft.text)
    }
    val saveChat = {
        chatDraft.commitTrim()
        viewModel.saveChatMetaPrompt(chatDraft.text)
    }

    // Leaving with typed-but-unsaved instructions asks first instead of silently dropping them.
    val guardedBack = { if (anyUnsaved) showLeaveDialog = true else onNavigateBack() }
    BackHandler(enabled = anyUnsaved) { showLeaveDialog = true }
    if (showLeaveDialog) {
        DaybookAlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = "Save your changes?",
            text = {
                Text(
                    "You've edited AI instructions without saving them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DaybookColors.TextMuted
                )
            },
            confirmLabel = "Save",
            onConfirm = {
                if (summaryDraft.isDirty) saveSummary()
                if (chatDraft.isDirty) saveChat()
                showLeaveDialog = false
                onNavigateBack()
            },
            dismissLabel = "Discard",
            onDismiss = {
                showLeaveDialog = false
                onNavigateBack()
            }
        )
    }

    if (showStartPicker) {
        DaybookDatePickerDialog(
            initial = startDate ?: LocalDate.now(),
            maxDate = LocalDate.now(),
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                viewModel.setChatRangeStart(picked)
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        DaybookDatePickerDialog(
            initial = endDate ?: LocalDate.now(),
            maxDate = LocalDate.now(),
            onDismiss = { showEndPicker = false },
            onConfirm = { picked ->
                viewModel.setChatRangeEnd(picked)
                showEndPicker = false
            }
        )
    }

    SettingsSubScreen("Daily Report AI", guardedBack) {
        // ---------------------------------------------------------------- AI Summary
        SectionHeader(
            "Summary instructions",
            subtitle = "Added to every AI Summary request."
        )
        PromptCard(
            draft = summaryDraft,
            placeholder = "e.g. Keep replies short and casual. Don't lecture about missed habits.",
            savedAt = summarySavedAt,
            syncStatus = syncStatus,
            onEdited = viewModel::clearSummarySaved,
            onSave = saveSummary
        )

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "What the AI Summary can see",
            subtitle = "Turn a category off to leave it out of the AI Summary. The report page " +
                "itself is never affected."
        )
        CategoryToggleGroup(
            selected = reportCategories,
            onToggle = viewModel::toggleReportCategory
        )

        // ---------------------------------------------------------------- Chat
        Spacer(Modifier.height(Spacing.sectionGap))
        SectionHeader(
            "Chat instructions",
            subtitle = "Sent at the start of every chat. Separate from the Summary instructions."
        )
        PromptCard(
            draft = chatDraft,
            placeholder = "e.g. Answer like a friendly coach. Keep it under 5 sentences.",
            savedAt = chatSavedAt,
            syncStatus = syncStatus,
            onEdited = viewModel::clearChatSaved,
            onSave = saveChat
        )

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Chat date range",
            subtitle = "By default, chat only sees the day you're viewing. Pick a range to let it " +
                "see more history."
        )
        SettingsGroup {
            SettingsRow(
                icon = DaybookIcons.Clock,
                title = "From",
                subtitle = startDate?.format(dateFmt) ?: "Not set · using the day you're viewing",
                onClick = { showStartPicker = true }
            )
            SettingsRowDivider()
            SettingsRow(
                icon = DaybookIcons.Clock,
                title = "To",
                subtitle = endDate?.format(dateFmt) ?: "Not set · using the day you're viewing",
                onClick = { showEndPicker = true }
            )
            if (rangeSet) {
                SettingsRowDivider()
                SettingsRow(
                    icon = DaybookIcons.Remove,
                    title = "Clear date range",
                    subtitle = "Go back to only the day you're viewing",
                    onClick = { viewModel.resetChatRange() },
                    trailing = {}
                )
            }
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "What Chat can see",
            subtitle = "Separate from the AI Summary's categories above."
        )
        CategoryToggleGroup(
            selected = chatCategories,
            onToggle = viewModel::toggleChatCategory
        )

        // ---------------------------------------------------------------- Privacy
        Spacer(Modifier.height(Spacing.sectionGap))
        SectionHeader(
            "Privacy",
            subtitle = "Pick specific habits, reminders or individual entries to keep out of the AI."
        )
        SettingsGroup {
            SettingsRow(
                icon = DaybookIcons.Lock,
                title = "Hidden from AI Summary",
                subtitle = if (hiddenFromSummaryCount > 0) "$hiddenFromSummaryCount item(s) hidden" else "Nothing hidden",
                onClick = { onOpenAiExclusions("SUMMARY") }
            )
            SettingsRowDivider()
            SettingsRow(
                icon = DaybookIcons.Lock,
                title = "Hidden from Chat",
                subtitle = if (hiddenFromChatCount > 0) "$hiddenFromChatCount item(s) hidden" else "Nothing hidden",
                onClick = { onOpenAiExclusions("CHAT") }
            )
        }
    }
}

/**
 * A text draft for one instructions box. Seeded from the stored value only once it has actually
 * loaded (`stored == null` until then), and keeps following later stored changes — e.g. a value
 * pulled from another device — for as long as the user hasn't edited it.
 */
private class PromptDraft(
    private val textState: androidx.compose.runtime.MutableState<String?>,
    private val stored: String?
) {
    val loaded: Boolean get() = stored != null && textState.value != null
    val text: String get() = textState.value.orEmpty()
    val isDirty: Boolean get() = loaded && text.trim() != stored
    fun update(v: String) { textState.value = v.take(PROMPT_MAX_CHARS) }
    fun discard() { textState.value = stored }
    fun commitTrim() { textState.value = text.trim() }
}

@Composable
private fun rememberPromptDraft(stored: String?): PromptDraft {
    val text = rememberSaveable { mutableStateOf<String?>(null) }
    var lastSeen by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(stored) {
        if (stored != null) {
            if (text.value == null || text.value?.trim() == lastSeen) text.value = stored
            lastSeen = stored
        }
    }
    return PromptDraft(text, stored)
}

@Composable
private fun PromptCard(
    draft: PromptDraft,
    placeholder: String,
    savedAt: Long?,
    syncStatus: SyncStatus,
    onEdited: () -> Unit,
    onSave: () -> Unit
) {
    FormGroup(title = null) {
        DaybookTextField(
            value = draft.text,
            onValueChange = {
                draft.update(it)
                onEdited()
            },
            label = null,
            placeholder = if (draft.loaded) placeholder else "Loading…",
            singleLine = false,
            minLines = 4,
            supportingText = "${draft.text.length} / $PROMPT_MAX_CHARS"
        )
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            val caption = saveCaption(draft.isDirty, savedAt, syncStatus)
            Text(
                caption?.first.orEmpty(),
                style = DaybookText.Caption,
                color = when (caption?.second) {
                    CaptionTone.Warn -> DaybookColors.Warning
                    CaptionTone.Ok -> DaybookColors.Success
                    else -> DaybookColors.TextMuted
                },
                modifier = Modifier.weight(1f)
            )
            if (draft.isDirty) {
                GhostButton(
                    text = "Undo",
                    onClick = {
                        draft.discard()
                        onEdited()
                    },
                    modifier = Modifier.width(92.dp)
                )
            }
            PrimaryButton(
                text = "Save",
                onClick = onSave,
                enabled = draft.isDirty,
                modifier = Modifier.width(104.dp)
            )
        }
    }
}

private enum class CaptionTone { Muted, Ok, Warn }

/** The status line beside a Save button: unsaved → saved → reached the cloud (or why not). */
private fun saveCaption(dirty: Boolean, savedAt: Long?, status: SyncStatus): Pair<String, CaptionTone>? = when {
    dirty -> "Unsaved changes" to CaptionTone.Warn
    savedAt == null -> null
    else -> when (status) {
        is SyncStatus.Idle ->
            if (status.lastSyncedAtMillis >= savedAt) "Saved · synced to your account" to CaptionTone.Ok
            else "Saved · syncing…" to CaptionTone.Muted
        SyncStatus.Syncing -> "Saved · syncing…" to CaptionTone.Muted
        SyncStatus.Offline -> "Saved · will sync when online" to CaptionTone.Muted
        SyncStatus.Paused -> "Saved on this phone · sync paused" to CaptionTone.Warn
        SyncStatus.Disabled -> "Saved on this phone (not signed in)" to CaptionTone.Muted
        is SyncStatus.Error -> "Saved on this phone · sync failed, will retry" to CaptionTone.Warn
    }
}

/**
 * The four category switches. The last one still on can't be turned off (an empty set is stored
 * as "", which reads back as "all on" — the switch used to snap back and re-enable everything).
 */
@Composable
private fun CategoryToggleGroup(
    selected: Set<ReportCategory>,
    onToggle: (ReportCategory, Boolean) -> Unit
) {
    SettingsGroup {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
            ReportCategory.entries.forEachIndexed { i, category ->
                if (i > 0) HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                val checked = category in selected
                val isLastOn = checked && selected.size == 1
                SettingsToggleRow(
                    label = category.displayLabel(),
                    subtitle = if (isLastOn) "At least one category stays on" else null,
                    checked = checked,
                    enabled = !isLastOn,
                    onCheckedChange = { on -> onToggle(category, on) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

private fun ReportCategory.displayLabel(): String = when (this) {
    ReportCategory.WORKOUT -> "Workout"
    ReportCategory.HEALTH -> "Health"
    ReportCategory.INTAKE -> "Intake"
    ReportCategory.TODO -> "Habits"
}
