package com.daybook.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.ReportCategory
import com.daybook.app.ui.DaybookDatePickerDialog
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.FormGroup
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRowDivider
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * DAILY_REPORT_REDESIGN_PLAN.md §2/§6/§7 — one destination hosting all three Daily Report AI
 * settings groups: the free-text meta-prompt (§2), the AI Summary's category toggles (§6), and
 * Chat's SEPARATE category toggles + custom context date range (§7). Structured like
 * `AiProvidersSettingsScreen.kt` — `SettingsSubScreen` + `FormGroup`/`SectionHeader` idioms, no new
 * primitives invented.
 */
@Composable
fun DailyReportAiSettingsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenAiExclusions: (String) -> Unit = {},
    viewModel: DailyReportAiSettingsViewModel = hiltViewModel()
) {
    val metaPrompt by viewModel.metaPrompt.collectAsStateWithLifecycle()
    val chatMetaPrompt by viewModel.chatMetaPrompt.collectAsStateWithLifecycle()
    val reportCategories by viewModel.reportCategories.collectAsStateWithLifecycle()
    val chatCategories by viewModel.chatCategories.collectAsStateWithLifecycle()
    val chatRangeStart by viewModel.chatRangeStart.collectAsStateWithLifecycle()
    val chatRangeEnd by viewModel.chatRangeEnd.collectAsStateWithLifecycle()
    val hiddenFromSummaryCount by viewModel.hiddenFromSummaryCount.collectAsStateWithLifecycle()
    val hiddenFromChatCount by viewModel.hiddenFromChatCount.collectAsStateWithLifecycle()

    // M2 fix — this was `remember(metaPrompt)`, and every keystroke wrote straight to Room
    // (`viewModel.setMetaPrompt` per-character), which invalidated `app_settings`, re-emitted
    // `metaPrompt`, and re-keyed this `remember` back to whatever the DB round-trip returned —
    // typing faster than that write could replay an older value mid-type. Seeded once from the
    // first composition instead (this screen never leaves composition/re-enters with a different
    // stored value while open), and committed only on focus-loss below, matching this codebase's
    // established "save on focus-loss, never per-keystroke" convention (`EditableSetCell`,
    // `ExerciseBlockCard`'s notes field).
    var metaPromptDraft by remember { mutableStateOf(metaPrompt) }
    var metaPromptWasFocused by remember { mutableStateOf(false) }
    var chatPromptDraft by remember { mutableStateOf(chatMetaPrompt) }
    var chatPromptWasFocused by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    // M3 fix — this used to parse bare, DURING COMPOSITION; a malformed stored value made this
    // screen throw `DateTimeParseException` on every open, permanently. Shared
    // `parseChatRangeDate` (also used by the ViewModel's own setters) degrades to null instead.
    val startDate = com.daybook.app.data.parseChatRangeDate(chatRangeStart)
    val endDate = com.daybook.app.data.parseChatRangeDate(chatRangeEnd)
    val rangeSet = startDate != null && endDate != null

    if (showStartPicker) {
        DaybookDatePickerDialog(
            initial = startDate ?: LocalDate.now(),
            maxDate = endDate ?: LocalDate.now(),
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

    SettingsSubScreen("Daily Report AI", onNavigateBack) {
        // §2 — the Summary's own instructions box. AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md
        // §1: renamed and now applies ONLY to the AI Summary — Chat gets its own box below.
        SectionHeader(
            "Summary instructions",
            subtitle = "Added to every AI Summary request — e.g. \"Keep replies short and casual. " +
                "Don't lecture about missed habits.\""
        )
        FormGroup(title = null) {
            DaybookTextField(
                value = metaPromptDraft,
                onValueChange = { metaPromptDraft = it.take(2000) },
                label = null,
                placeholder = "e.g. Keep replies short and casual. Don't lecture about missed habits.",
                singleLine = false,
                minLines = 3,
                supportingText = "Sent with every AI Summary.",
                modifier = Modifier.onFocusChanged { state ->
                    if (metaPromptWasFocused && !state.isFocused) viewModel.setMetaPrompt(metaPromptDraft)
                    metaPromptWasFocused = state.isFocused
                }
            )
        }

        // §6 — AI Summary category toggles. Independent of §7's Chat toggles below.
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "What the AI Summary can see",
            subtitle = "Turn a category off to leave it out of the report the AI generates. The " +
                "report page itself is never affected."
        )
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                ReportCategory.entries.forEachIndexed { i, category ->
                    if (i > 0) SettingsRowDivider()
                    SettingsToggleRow(
                        label = category.displayLabel(),
                        checked = category in reportCategories,
                        onCheckedChange = { checked -> viewModel.toggleReportCategory(category, checked) }
                    )
                }
            }
        }

        // §7 — Chat context: date range + its own, separate category toggles.
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Chat context",
            subtitle = "By default, chat only sees the day you're viewing. Pick a date range to " +
                "let it see more history."
        )
        // §1 — Chat's own instructions box, fully independent of the Summary box above.
        FormGroup(title = null) {
            DaybookTextField(
                value = chatPromptDraft,
                onValueChange = { chatPromptDraft = it.take(2000) },
                label = null,
                placeholder = "e.g. Answer like a friendly coach. Keep it under 5 sentences.",
                singleLine = false,
                minLines = 3,
                supportingText = "Sent at the start of every chat.",
                modifier = Modifier.onFocusChanged { state ->
                    if (chatPromptWasFocused && !state.isFocused) viewModel.setChatMetaPrompt(chatPromptDraft)
                    chatPromptWasFocused = state.isFocused
                }
            )
        }
        Spacer(Modifier.height(Spacing.listGap))
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                com.daybook.app.ui.components.SettingsRow(
                    icon = com.daybook.app.ui.icons.DaybookIcons.Clock,
                    title = "From",
                    subtitle = startDate?.format(dateFmt) ?: "Not set — using today",
                    onClick = { showStartPicker = true }
                )
                SettingsRowDivider()
                com.daybook.app.ui.components.SettingsRow(
                    icon = com.daybook.app.ui.icons.DaybookIcons.Clock,
                    title = "To",
                    subtitle = endDate?.format(dateFmt) ?: "Not set — using today",
                    onClick = { showEndPicker = true }
                )
            }
        }
        if (rangeSet) {
            Spacer(Modifier.height(8.dp))
            TextLink(text = "Reset to today", onClick = { viewModel.resetChatRange() })
        }

        Spacer(Modifier.height(Spacing.listGap))
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                ReportCategory.entries.forEachIndexed { i, category ->
                    if (i > 0) SettingsRowDivider()
                    SettingsToggleRow(
                        label = category.displayLabel(),
                        checked = category in chatCategories,
                        onCheckedChange = { checked -> viewModel.toggleChatCategory(category, checked) }
                    )
                }
            }
        }

        // §2.4 — "Hide from AI": two rows opening the same picker screen for each scope.
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Privacy",
            subtitle = "Pick specific habits, reminders or individual entries to keep out of the AI."
        )
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                com.daybook.app.ui.components.SettingsRow(
                    icon = com.daybook.app.ui.icons.DaybookIcons.Lock,
                    title = "Hidden from AI Summary",
                    subtitle = if (hiddenFromSummaryCount > 0) "$hiddenFromSummaryCount item(s) hidden" else "Nothing hidden",
                    onClick = { onOpenAiExclusions("SUMMARY") }
                )
                SettingsRowDivider()
                com.daybook.app.ui.components.SettingsRow(
                    icon = com.daybook.app.ui.icons.DaybookIcons.Lock,
                    title = "Hidden from Chat",
                    subtitle = if (hiddenFromChatCount > 0) "$hiddenFromChatCount item(s) hidden" else "Nothing hidden",
                    onClick = { onOpenAiExclusions("CHAT") }
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
