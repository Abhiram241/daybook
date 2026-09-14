package com.daybook.app.ui.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.DailyReportData
import com.daybook.app.data.IntakeEntryRow
import com.daybook.app.data.TodoEntryRow
import com.daybook.app.data.WorkoutSectionData
import com.daybook.app.data.ai.AiChatMessage
import com.daybook.app.data.ai.AiChatRole
import com.daybook.app.data.ai.AiProviderId
import com.daybook.app.data.model.HabitType
import com.daybook.app.ui.components.Avatar
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.BottomSheetMenu
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.CircleStyle
import com.daybook.app.ui.components.DaybookTextField
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.NavContentHeight
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.components.ScreenHeader
import com.daybook.app.ui.components.SegmentSpec
import com.daybook.app.ui.components.SegmentedControl
import com.daybook.app.ui.components.SettingsRow
import com.daybook.app.ui.components.SheetAction
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.components.TypingDots
import com.daybook.app.ui.components.WeekStrip
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.data.workout.formatWeight
import com.mikepenz.markdown.m3.Markdown
import java.time.format.DateTimeFormatter

/**
 * DAILY_REPORT_PLAN.md — the fourth main-app tab. §0: the static half (Report panel) works with
 * zero network access, zero API key, zero AI provider configured, always. §3.1: the AI Summary
 * panel is a second, independent panel — switching to it never re-fetches or blocks the static
 * half.
 */
@Composable
fun DailyReportScreen(
    onNavigateToSettings: () -> Unit = {},
    onOpenAiExclusions: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: DailyReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val chatOpen by viewModel.chatOpen.collectAsStateWithLifecycle()

    // DAILY_REPORT_REDESIGN_PLAN.md §4 — mirrors JournalScreen.kt's nested BackHandler. Registered
    // here (not inside DailyReportChatScreen) so it's unambiguous that this is scoped to THIS
    // screen's chat state, and is composed/enabled only while chatOpen is true, taking priority
    // over MainActivity's outer `BackHandler(enabled = settledPage != 0) { goToPage(0) }` —
    // Compose's BackHandler dispatch is a stack, most-recently-composed-and-enabled wins.
    androidx.activity.compose.BackHandler(enabled = chatOpen) { viewModel.closeChat() }

    if (chatOpen) {
        // No `contentPadding` here: the chat screen is a full-bleed sub-view that reserves its own
        // pill-nav clearance on the input bar alone (see `DailyReportChatScreen`), rather than
        // consuming the scaffold's list padding, which also carries FAB + system-inset terms that
        // do not apply to it.
        DailyReportChatScreen(viewModel = viewModel)
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Daily Report",
            subtitle = state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            actions = {
                Avatar(
                    photoPath = profile.photoPath,
                    name = profile.name,
                    size = 40.dp,
                    onClick = onNavigateToSettings
                )
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
        ) {
            item {
                Column(Modifier.padding(horizontal = Spacing.screenH, vertical = 8.dp)) {
                    WeekStrip(
                        selectedDate = state.selectedDate,
                        today = state.today,
                        onSelect = viewModel::selectDate,
                        expanded = state.calendarExpanded,
                        onToggleExpanded = viewModel::toggleCalendarExpanded,
                        weekStart = state.weekStart
                    )
                    Spacer(Modifier.height(Spacing.listGap))
                    SegmentedControl(
                        options = listOf(
                            SegmentSpec("report", "Report"),
                            SegmentSpec("ai", "AI Summary")
                        ),
                        selectedKey = if (state.panel == ReportPanel.REPORT) "report" else "ai",
                        onSelect = { key ->
                            viewModel.setPanel(if (key == "report") ReportPanel.REPORT else ReportPanel.AI_SUMMARY)
                        }
                    )
                    // §5B.1 — extra breathing room above the first section card so the transition
                    // out of the WeekStrip/SegmentedControl block never reads as "headerless".
                    Spacer(Modifier.height(Spacing.listGap / 2))
                }
            }

            if (state.panel == ReportPanel.REPORT) {
                reportSections(state.report)
            } else {
                item {
                    Column(Modifier.padding(horizontal = Spacing.screenH)) {
                        AiSummaryPanel(state = state, viewModel = viewModel, onOpenAiExclusions = onOpenAiExclusions)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportSections(report: DailyReportData?) {
    if (report == null || report.isEmpty) {
        item {
            EmptyState(
                icon = DaybookIcons.BarChart,
                title = "Nothing logged for this day",
                modifier = Modifier.padding(horizontal = Spacing.screenH),
                tint = CardTints.Mint
            )
        }
        return
    }
    report.workout?.let { workout ->
        item { SectionCard(title = "Workout") { WorkoutSectionBody(workout) } }
    }
    report.health?.let { health ->
        item { SectionCard(title = "Health") { HealthSectionBody(health) } }
    }
    if (report.intake.isNotEmpty()) {
        item { SectionCard(title = "Intake") { IntakeSectionBody(report.intake) } }
    }
    if (report.todo.isNotEmpty()) {
        item { SectionCard(title = "Habits") { TodoSectionBody(report.todo) } }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = Spacing.screenH)) {
        SoftCard(tint = CardTints.Neutral, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun WorkoutSectionBody(workout: WorkoutSectionData) {
    workout.sessions.forEachIndexed { i, session ->
        if (i > 0) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
        }
        Text(session.title, style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
        val statsLine = buildList {
            session.durationMinutes?.let { add("${it} min") }
            add("${formatWeight(session.totalVolumeKg, com.daybook.app.data.workout.WeightUnit.KG)} volume")
            add("${session.setCount} sets")
        }.joinToString(" · ")
        Text(statsLine, style = DaybookText.Caption, color = DaybookColors.TextMuted)
        session.exercises.forEach { ex ->
            Spacer(Modifier.height(6.dp))
            // §5B.3 — an unweighted, unbounded Row let a long exercise name push the reps/weight
            // value into a 3-line wrap. Both sides are now explicitly bounded: the name gets
            // weight(fill = false) + ellipsis so it never crowds out the value, and the value gets
            // a capped max width + ellipsis as a last-resort safety net instead of ever wrapping.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ex.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DaybookColors.TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    ex.bestSetLabel ?: "${ex.setCount} sets",
                    style = DaybookText.Caption,
                    color = DaybookColors.TextMuted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.widthIn(max = 130.dp)
                )
            }
        }
    }
}

@Composable
private fun HealthSectionBody(health: com.daybook.app.data.HealthSectionData) {
    val d = health.day ?: com.daybook.app.data.model.HealthDay(localDate = "", updatedAt = 0L)
    if (health.day == null && health.sleepEntries.isEmpty()) {
        Text("Tracked sessions: ${health.sessions.size}", style = DaybookText.Caption, color = DaybookColors.TextMuted)
        return
    }
    val rows = buildList {
        d.steps?.let { add("Steps" to com.daybook.app.util.formatHealthCount(it)) }
        (d.activeCalories ?: d.totalCalories)?.let { add("Calories" to com.daybook.app.util.formatHealthCalories(it)) }
        if (d.avgHeartRate != null || d.minHeartRate != null || d.maxHeartRate != null) {
            add("Heart rate" to "avg ${d.avgHeartRate ?: "–"} (${d.minHeartRate ?: "–"}-${d.maxHeartRate ?: "–"})")
        }
        health.sleepEntries.forEach { e ->
            e.row.sleepMinutes?.let { add("Sleep ${e.label}" to com.daybook.app.util.formatHealthDuration(it)) }
        }
        d.spo2Percent?.let { add("SpO2" to "${it}%") }
        d.weightKg?.let { add("Weight" to formatWeight(it, com.daybook.app.data.workout.WeightUnit.KG)) }
        d.hydrationMl?.let { add("Hydration" to "${it.toInt()} ml") }
    }
    rows.forEachIndexed { i, (label, value) ->
        if (i > 0) Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextPrimary)
            Text(value, style = DaybookText.Caption, color = DaybookColors.TextMuted)
        }
    }
    if (health.sessions.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("Tracked sessions: ${health.sessions.size}", style = DaybookText.Caption, color = DaybookColors.TextMuted)
    }
}

/** Round 2 (Feature 2) — collapsed one-line row (time + label + flag badge) by default for BOTH
 *  food and med entries; tapping expands in place to the full logged content (responseText,
 *  description, qaJson Q&A pairs, redFlag, suspectedFood, outsideFood). Tapping again collapses. */
@Composable
private fun IntakeSectionBody(rows: List<IntakeEntryRow>) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    rows.forEachIndexed { i, row ->
        if (i > 0) HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
        val expanded = expandedId == row.id
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { expandedId = if (expanded) null else row.id }
                .padding(vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextPrimary)
                    Text(row.timeLabel, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    row.flagLabel?.let {
                        Text(it, style = DaybookText.Caption, color = DaybookColors.Warning)
                    }
                    if (row.outsideFood) {
                        Text("Outside food", style = DaybookText.Caption, color = DaybookColors.TextMuted)
                    }
                }
            }
            val rm = com.daybook.app.ui.theme.LocalReduceMotion.current
            AnimatedVisibility(
                visible = expanded,
                enter = if (rm) fadeIn() else fadeIn(com.daybook.app.ui.theme.Motion.fast()) + expandVertically(com.daybook.app.ui.theme.Motion.softSpring()),
                exit = if (rm) fadeOut() else fadeOut(com.daybook.app.ui.theme.Motion.fast()) + shrinkVertically(com.daybook.app.ui.theme.Motion.softSpring())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (row.responseText.isNotBlank()) {
                        Text(row.responseText, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextPrimary)
                    }
                    row.description?.let {
                        Text("Note: $it", style = DaybookText.Caption, color = DaybookColors.TextMuted)
                    }
                    row.suspectedFood?.let {
                        Text("Suspected trigger: $it", style = DaybookText.Caption, color = DaybookColors.Warning)
                    }
                    row.qaPairs.forEach { (q, a) ->
                        Spacer(Modifier.height(2.dp))
                        Text(q, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                        Text(a, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextPrimary)
                    }
                    if (row.responseText.isBlank() && row.description == null && row.qaPairs.isEmpty()) {
                        Text("No further detail logged.", style = DaybookText.Caption, color = DaybookColors.TextMuted)
                    }
                }
            }
        }
    }
}

/**
 * Round 2 (Feature 3) — one compact row per habit: a check/x glyph for done vs missed/skipped
 * (INDIVIDUAL/BATCH/JOURNAL), or the current streak length in days for STREAK ("ongoing") habits,
 * as of the report's selected date (not live-today) — [TodoEntryRow.streakDays] is already
 * computed that way by `DailyReportRepository`. A JOURNAL row with Q&A content expands on tap,
 * mirroring Feature 2's Intake pattern.
 */
@Composable
private fun TodoSectionBody(rows: List<TodoEntryRow>) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    rows.forEachIndexed { i, row ->
        if (i > 0) HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
        val canExpand = row.habitType == HabitType.JOURNAL && row.qaPairs.isNotEmpty()
        val expanded = canExpand && expandedId == row.id
        Column(
            Modifier
                .fillMaxWidth()
                .let { if (canExpand) it.clickable { expandedId = if (expanded) null else row.id } else it }
                .padding(vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextPrimary)
                    Text(row.timeLabel, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                }
                if (row.habitType == HabitType.STREAK) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(DaybookIcons.Flame, null, tint = DaybookColors.Warning, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${row.streakDays ?: 0} day" + if ((row.streakDays ?: 0) == 1) "" else "s",
                            style = DaybookText.Caption,
                            color = DaybookColors.Warning
                        )
                    }
                } else {
                    // §5B.2 — DaybookIcons.Remove (a flat "minus" bar) read as an unstyled dash
                    // placeholder next to the checked CheckBox glyph. CheckBoxBlank is this app's
                    // existing checked/unchecked icon pair (see SortSheet.kt). Decided: every
                    // not-done status (Skipped/Missed/Pending) renders identically — no per-status
                    // color/icon distinction; row.statusLabel still carries the distinction for
                    // screen readers via contentDescription.
                    Icon(
                        if (row.done) DaybookIcons.CheckBox else DaybookIcons.CheckBoxBlank,
                        contentDescription = if (row.done) "Done" else row.statusLabel,
                        tint = if (row.done) DaybookColors.Success else DaybookColors.TextMuted,
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
            val rm = com.daybook.app.ui.theme.LocalReduceMotion.current
            AnimatedVisibility(
                visible = expanded,
                enter = if (rm) fadeIn() else fadeIn(com.daybook.app.ui.theme.Motion.fast()) + expandVertically(com.daybook.app.ui.theme.Motion.softSpring()),
                exit = if (rm) fadeOut() else fadeOut(com.daybook.app.ui.theme.Motion.fast()) + shrinkVertically(com.daybook.app.ui.theme.Motion.softSpring())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    row.qaPairs.forEach { (q, a) ->
                        Text(q, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                        Text(a, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

/** DAILY_REPORT_REDESIGN_PLAN.md §5.2 — a millis-since-epoch "time ago" label. No shared "time
 *  ago" util exists in `util/` (checked `HealthRepository.kt`/`AccountScreen.kt`: each screen
 *  inlines its own small formatter rather than sharing one) — this follows that same convention. */
private fun relativeTimeLabel(epochMillis: Long): String {
    val minutes = (System.currentTimeMillis() - epochMillis) / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

/** §1 / §5.2-D — shared typography for both chat bubbles and the AI summary card's Markdown body:
 *  headings render as bold app-styled text (no library-default rule/underline decoration), body
 *  text matches the app's own type scale instead of the library's unstyled M3 defaults. */
@Composable
private fun dailyReportMarkdownTypography() = com.mikepenz.markdown.m3.markdownTypography(
    h1 = DaybookText.CardTitle,
    h2 = DaybookText.CardTitle,
    h3 = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    h4 = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    h5 = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    h6 = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    text = MaterialTheme.typography.bodyMedium.copy(color = DaybookColors.TextPrimary),
    paragraph = MaterialTheme.typography.bodyMedium.copy(color = DaybookColors.TextPrimary),
    ordered = MaterialTheme.typography.bodyMedium.copy(color = DaybookColors.TextPrimary),
    bullet = MaterialTheme.typography.bodyMedium.copy(color = DaybookColors.TextPrimary),
    list = MaterialTheme.typography.bodyMedium.copy(color = DaybookColors.TextPrimary),
    quote = MaterialTheme.typography.bodyMedium.copy(color = DaybookColors.TextMuted),
    code = MaterialTheme.typography.bodyMedium
)

/** §1 — a literal `---` in a reply renders as a thin on-brand [DaybookColors.Hairline] rule
 *  instead of the library's default (mismatched) divider color, which is what read as a
 *  "misplaced purple bar" under the chat header. */
@Composable
private fun dailyReportMarkdownColors() = com.mikepenz.markdown.m3.markdownColor(
    dividerColor = DaybookColors.Hairline
)

@Composable
private fun AiSummaryPanel(
    state: DailyReportUiState,
    viewModel: DailyReportViewModel,
    onOpenAiExclusions: (String) -> Unit
) {
    val summary = state.report?.aiSummary
    var showProviderSheet by remember { mutableStateOf(false) }

    if (state.availableProviders.isEmpty()) {
        EmptyState(
            icon = DaybookIcons.Bolt,
            title = "No AI provider set up yet",
            body = "Add an API key in Settings → AI Providers to generate a summary.",
            tint = CardTints.Mint
        )
        return
    }

    // §5.2.A — a compact SettingsRow-style provider picker (one ~60dp row) replaces the old
    // label + full-width GhostButton (~90dp).
    SoftCard(
        tint = CardTints.Neutral,
        elevation = 0.dp,
        contentPadding = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingsRow(
            icon = DaybookIcons.Bolt,
            title = state.selectedProvider?.label ?: "Choose a provider",
            subtitle = "Provider",
            onClick = { showProviderSheet = true }
        )
    }

    Spacer(Modifier.height(Spacing.listGap))

    // §5.2.B — Generate/Regenerate + Chat side by side, a primary/secondary action pair instead
    // of two independent stacked full-width blocks.
    val hasSummary = summary != null
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.listGap)
    ) {
        PrimaryButton(
            text = when {
                state.isGenerating -> "Generating…"
                hasSummary -> "Regenerate"
                else -> "Generate"
            },
            onClick = viewModel::generate,
            enabled = !state.isGenerating,
            loading = state.isGenerating,
            modifier = Modifier.weight(1f)
        )
        // Feature 4 — the Chat entry point. Reuses the same provider/model already picked for
        // Generate (only providers with a saved key are ever offered). Shortened from "Chat with
        // AI about today" now that it's a half-width button — the fuller phrase already lives in
        // the Chat screen's own empty-state copy.
        GhostButton(
            // H7 fix — opening chat traverses the whole configured date range (each day its own
            // set of Room flows + suspend queries) with no loading state before this; mirrors
            // Generate's own `loading`/`enabled` wiring above.
            text = if (state.chatOpening) "Opening…" else "Chat",
            leadingIcon = {
                Icon(
                    DaybookIcons.Send,
                    contentDescription = null,
                    tint = DaybookColors.TextPrimary,
                    modifier = Modifier.height(18.dp)
                )
            },
            onClick = viewModel::openChat,
            enabled = !state.chatOpening,
            loading = state.chatOpening,
            modifier = Modifier.weight(1f)
        )
    }

    // AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §2.1 — only shows when something hidden
    // actually falls in the day (or chat range) currently being sent; links to the matching list.
    if (state.hiddenFromSummaryCount > 0 || state.hiddenFromChatCount > 0) {
        Spacer(Modifier.height(4.dp))
        val label = when {
            state.hiddenFromSummaryCount > 0 && state.hiddenFromChatCount > 0 ->
                "${state.hiddenFromSummaryCount} hidden from Summary, ${state.hiddenFromChatCount} from Chat"
            state.hiddenFromSummaryCount > 0 -> "${state.hiddenFromSummaryCount} entries hidden from AI Summary"
            else -> "${state.hiddenFromChatCount} entries hidden from Chat"
        }
        TextLink(
            text = "$label · Manage",
            onClick = { onOpenAiExclusions(if (state.hiddenFromSummaryCount > 0) "SUMMARY" else "CHAT") }
        )
    }

    // §5.2.C — one consolidated error slot (Generate and Chat are never mid-action at the same
    // time in this panel) plus the process-death notice, which keeps its own muted styling.
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val chatEndedNotice by viewModel.chatEndedNotice.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxWidth().heightIn(min = 20.dp).padding(top = 6.dp)) {
        val message = state.generationError ?: chatError
        if (message != null) {
            Text(message, style = DaybookText.Caption, color = DaybookColors.Danger)
        }
    }
    // H3 — process death while the chat sheet was open loses the in-memory transcript; this at
    // least says so instead of silently reopening empty.
    if (chatEndedNotice) {
        Text(
            "Your chat session ended.",
            style = DaybookText.Caption,
            color = DaybookColors.TextMuted
        )
    }

    Spacer(Modifier.height(Spacing.listGap))

    if (summary != null) {
        // §5.2.D — a distinct "generated content" tint (matches the app's purple/AI identity)
        // instead of the same neutral tint every settings-style card uses, plus a real
        // attribution row + divider marking where metadata ends and the summary body begins.
        SoftCard(tint = CardTints.Lavender, modifier = Modifier.fillMaxWidth()) {
            val lavender = CardTints.Lavender
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    DaybookIcons.Bolt,
                    contentDescription = null,
                    tint = lavender.accent,
                    modifier = Modifier.height(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Generated by ${summary.provider} / ${summary.model} · " +
                        relativeTimeLabel(summary.generatedAt),
                    style = DaybookText.Caption,
                    color = lavender.onFillMuted
                )
            }
            // BEAST_HEALTH_REPORT_AUDIT.md M4 — a null `settingsFingerprint` (a row generated
            // before this field existed) is treated the same as a mismatch: never claim a summary
            // is fresh when we can't actually tell. Same warning-badge convention as the fallback
            // storage notice in `AiProvidersSettingsScreen.kt`, sized down to fit inside this card.
            if (summary.settingsFingerprint != state.currentSettingsFingerprint) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        DaybookIcons.Bolt,
                        contentDescription = null,
                        tint = DaybookColors.Warning,
                        modifier = Modifier.height(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Settings changed since this was generated — regenerate to apply.",
                        style = DaybookText.Caption,
                        color = DaybookColors.Warning
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            // Coordinator-flagged fix: the model's markdown (`**bold**`, `- bullets`) was being
            // dumped into a plain Text, showing literal asterisks/dashes. Rendered properly here,
            // with the app's own typography/colors (§5.2-D) instead of the library's M3 defaults.
            Markdown(
                content = summary.summaryText,
                colors = dailyReportMarkdownColors(),
                typography = dailyReportMarkdownTypography(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    BottomSheetMenu(
        visible = showProviderSheet,
        onDismiss = { showProviderSheet = false },
        actions = state.availableProviders.map { id ->
            SheetAction(
                icon = DaybookIcons.Bolt,
                label = id.label,
                onClick = { viewModel.selectProvider(id) }
            )
        }
    )
}

/**
 * Round 2 (Feature 4) — the chat surface, opened over the Daily Report screen (no new nav route:
 * this stays a same-tab overlay, toggled by `DailyReportViewModel.chatOpen`). Bubble layout mirrors
 * `ui/journal/HabitJournalChatScreen`'s existing chat pattern (left `SoftCard` bubble for the
 * "other side", right accent-filled bubble for the user) — reused rather than inventing a new
 * chat visual language, per the round's instruction to check for an existing pattern first.
 */
@Composable
private fun DailyReportChatScreen(
    viewModel: DailyReportViewModel
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val draft by viewModel.chatDraft.collectAsStateWithLifecycle()
    val sending by viewModel.chatSending.collectAsStateWithLifecycle()
    val error by viewModel.chatError.collectAsStateWithLifecycle()
    val visibleMessages = messages.filter { it.role != AiChatRole.SYSTEM }

    val listState = rememberLazyListState()
    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(DaybookColors.Bg)) {
        BackHeader(title = "Chat about today", onBack = viewModel::closeChat)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = Spacing.listTop,
                bottom = Spacing.listGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
        ) {
            if (visibleMessages.isEmpty()) {
                item {
                    Text(
                        "Ask anything about today's report — what you ate, how a habit's streak is " +
                            "going, your workout, and more.",
                        style = DaybookText.Caption,
                        color = DaybookColors.TextMuted
                    )
                }
            }
            itemsIndexed(visibleMessages) { _, message ->
                when (message.role) {
                    AiChatRole.ASSISTANT -> AssistantBubble(message.content)
                    else -> UserBubble(message.content)
                }
            }
            if (sending) {
                item { AssistantTypingBubble() }
            }
        }

        Box(Modifier.fillMaxWidth().heightIn(min = 20.dp).padding(horizontal = Spacing.screenH)) {
            error?.let { Text(it, style = DaybookText.Caption, color = DaybookColors.Danger) }
        }

        // The persistent bottom tab bar is drawn as a separate overlay above this screen's own
        // content (DaybookScaffold's Box, not a resizing Scaffold bottomBar), so this content must
        // reserve real clearance for it or the input row renders underneath the opaque pill and is
        // invisible (as an earlier pass here did, by dropping the reservation entirely).
        //
        // But `DaybookScaffold`'s own `contentPadding` bottom is the WRONG number to reserve here.
        // It is `NavContentHeight + 12.dp + navBarInset + fabClearance`, and two of those terms do
        // not apply to this bar: there is no FAB on the chat screen (`fabPresent = true` is passed
        // once, for Habits/Intake), and `StickySaveBar` already applies its own
        // `navigationBarsPadding()`, so consuming the scaffold's copy of the system inset
        // double-counted it. Together that pushed the input ~108dp higher than the pill nav
        // actually occupies — the oversized dead gap the user saw at rest.
        //
        // The pill is docked flush to the bottom edge (`DaybookScaffoldNav` aligns it to
        // BottomCenter and it carries the nav-bar inset internally), so its real footprint above
        // the system nav bar is exactly `NavContentHeight`. Reserving that, on top of
        // `StickySaveBar`'s own nav-bar padding and its trailing 12.dp, leaves a 12.dp gap between
        // the input and the top of the pill.
        //
        // That reservation is ONLY needed while the tab bar is actually visible: once the IME opens
        // it covers that same bottom strip anyway, and `StickySaveBar`'s `imePadding()` already
        // lifts the input above the keyboard — stacking the reservation on top of that
        // unconditionally is what produced the dead gap between the input field and the keyboard.
        // Reserve only what the pill needs beyond what the keyboard already covers, so it shrinks
        // to zero as the IME rises.
        val density = LocalDensity.current
        val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
        val extraBottom = (NavContentHeight - imeBottomDp).coerceAtLeast(0.dp)
        StickySaveBar(modifier = Modifier.padding(bottom = extraBottom)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.listGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DaybookTextField(
                    value = draft,
                    onValueChange = viewModel::onChatDraftChange,
                    label = null,
                    placeholder = "Ask about today…",
                    singleLine = false,
                    minLines = 1,
                    modifier = Modifier.weight(1f)
                )
                val haptics = com.daybook.app.ui.theme.rememberDaybookHaptics()
                CircleIconButton(
                    icon = DaybookIcons.Send,
                    contentDescription = "Send",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.sendChatMessage()
                    },
                    style = CircleStyle.Tonal,
                    enabled = draft.isNotBlank() && !sending
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        SoftCard(
            tint = CardTints.Neutral,
            contentPadding = 14.dp,
            elevation = 0.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Markdown(
                content = text,
                colors = dailyReportMarkdownColors(),
                typography = dailyReportMarkdownTypography()
            )
        }
    }
}

/** Polish pass — replaces the old static `AssistantBubble("…")` with a pulsing typing indicator. */
@Composable
private fun AssistantTypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        SoftCard(
            tint = CardTints.Neutral,
            contentPadding = 14.dp,
            elevation = 0.dp
        ) {
            TypingDots()
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .background(accent, AppShapes.card)
        ) {
            Text(
                text,
                style = DaybookText.CardTitle,
                color = DaybookColors.OnAccent,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

