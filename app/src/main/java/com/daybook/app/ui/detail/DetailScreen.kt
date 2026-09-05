package com.daybook.app.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.daybook.app.data.JournalQa
import com.daybook.app.data.model.Event
import com.daybook.app.ui.icons.DaybookIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.*
import com.daybook.app.ui.icons.Icons
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import androidx.compose.animation.core.snap
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing

/**
 * v0.5.3 Phase 5 (§5.7) — the two internal views of the detail screen.
 *
 * v0.5.5 — moved back to a docked bottom bar (was the in-flow [SegmentedControl] below
 * [BackHeader]): reuses [FloatingPillNav] as-is — the same icon-over-label, accent-tinted bar
 * the app's main tabs use — rather than inventing a second nav style for one screen.
 */
enum class DetailTab { History, Stats }

private const val KEY_HISTORY = "history"
private const val KEY_STATS = "stats"

/**
 * v0.5.3 Phase 5 (§5.7 / backlog #31) — palette rule for this screen: the Stats cards and every
 * [TimelineRow] (card + [InnerChip]) are all [CardTints.Neutral]; the item's identity colour
 * ([tint].accent) appears **only** on the accent number / replied glyph. No pastel card surfaces.
 */
@Composable
fun DetailScreen(
    itemType: String,
    itemId: String,
    onNavigateBack: () -> Unit = {},
    onOpenJournal: (String) -> Unit = {},
    onOpenRespond: (occId: String) -> Unit = {},
    onOpenHabitJournalEdit: (occId: String) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel()
) {
    // Journal Mode: DetailViewModel's load is a one-shot snapshot. Re-run it every time this
    // screen resumes so returning from the Journal / Respond editor shows the edited text
    // immediately (the edit path preserves responded_at, so History order does not change).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val itemTitle by viewModel.itemTitle.collectAsState()
    val itemSubtitle by viewModel.itemSubtitle.collectAsState()
    val itemMotivation by viewModel.itemMotivation.collectAsState()
    val itemCategory by viewModel.itemCategory.collectAsState()
    val itemIconKey by viewModel.itemIconKey.collectAsState()
    val timelineEvents by viewModel.timelineEvents.collectAsState()
    val filteredTimeline by viewModel.filteredTimeline.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isJournal by viewModel.isJournal.collectAsState()
    val isHabitJournal by viewModel.isHabitJournal.collectAsState()
    val isOngoing by viewModel.isOngoing.collectAsState()
    val showStreaks by viewModel.showStreaks.collectAsState()
    val query by viewModel.query.collectAsState()
    // v0.5.3 Phase 3 (A4): older terminal timeline rows are paged in on demand.
    val canLoadMoreTerminal by viewModel.canLoadMoreTerminal.collectAsState()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tint = remember(itemId) { CardTints.byId(itemId) }
    val isHabit = itemType == "habit"

    var tab by rememberSaveable { mutableStateOf(DetailTab.History) }

    // v0.5.5 — the History/Stats switch is a docked FloatingPillNav again (see the DetailTab
    // doc above), so list content needs clearance for its height too, not just the system inset.
    val bottomClearance = NavContentHeight + navBarInset + Spacing.sm

    Box(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        Column(Modifier.fillMaxSize()) {
            // v0.5.3 Phase 4 (§4.1) — pinned back header carrying the compact title; §5.7 moves the
            // 64dp identity block into each tab's list so it scrolls with the content (UI Q6).
            BackHeader(title = itemTitle.ifBlank { "Details" }, onBack = onNavigateBack)

            // v0.5.3 Phase 5 (§5.7 / backlog #21) — fade the History <-> Stats swap.
            val rmTab = LocalReduceMotion.current
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (rmTab) fadeIn(snap()) togetherWith fadeOut(snap())
                    else fadeIn(Motion.fast()) togetherWith fadeOut(Motion.fast())
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "detailTab"
            ) { current ->
                when (current) {
                    DetailTab.History -> HistoryTab(
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        rows = filteredTimeline,
                        unfilteredEmpty = timelineEvents.isEmpty(),
                        tint = tint,
                        isHabit = isHabit,
                        isJournal = isJournal,
                        isHabitJournal = isHabitJournal,
                        isOngoing = isOngoing,
                        iconKey = itemIconKey,
                        title = itemTitle,
                        subtitle = itemSubtitle,
                        motivation = itemMotivation,
                        category = itemCategory,
                        bottomClearance = bottomClearance,
                        canLoadMore = canLoadMoreTerminal,
                        onLoadMore = viewModel::loadMoreTerminal,
                        onOpenJournal = onOpenJournal,
                        onOpenRespond = onOpenRespond,
                        onOpenHabitJournalEdit = onOpenHabitJournalEdit,
                        onToggleHabit = viewModel::toggleHabitOccurrence
                    )
                    DetailTab.Stats -> StatsTab(
                        stats = stats,
                        tint = tint,
                        isOngoing = isOngoing,
                        showStreaks = showStreaks,
                        iconKey = itemIconKey,
                        title = itemTitle,
                        subtitle = itemSubtitle,
                        motivation = itemMotivation,
                        category = itemCategory,
                        bottomClearance = bottomClearance
                    )
                }
            }
        }

        // v0.5.5 — docked at the bottom like the app's main tab bar, not in-flow under the
        // header: reuses FloatingPillNav verbatim (no bespoke styling) with the two pseudo-routes
        // below as its "route" keys.
        FloatingPillNav(
            items = listOf(
                NavItemSpec(KEY_HISTORY, DaybookIcons.Clock, "History"),
                NavItemSpec(KEY_STATS, DaybookIcons.BarChart, "Stats")
            ),
            currentRoute = if (tab == DetailTab.History) KEY_HISTORY else KEY_STATS,
            onSelect = { tab = if (it == KEY_HISTORY) DetailTab.History else DetailTab.Stats },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * v0.5.3 Phase 5 (§5.7 / UI Q6) — the identity block. Now the first `item {}` of each tab list so
 * it scrolls away; the [BackHeader] keeps the title pinned for context.
 */
@Composable
private fun IdentityHeader(
    iconKey: String,
    title: String,
    subtitle: String?,
    motivation: String?,
    category: String?,
    tint: CardTint
) {
    Column {
        IconTile(icon = Icons.getIcon(iconKey), tint = tint, size = 64.dp)
        Spacer(Modifier.height(12.dp))
        BigHeadline(title.ifBlank { "Details" })
        subtitle?.let {
            Text(it, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
        }
        // Customization round (rec 8 / SD-6): the "why this matters" note, as a quoted line.
        motivation?.let {
            Spacer(Modifier.height(6.dp))
            Text("\u201C$it\u201D", style = DaybookText.CardSubtitle, color = tint.accent)
        }
        category?.let {
            Text("Category: $it", style = DaybookText.Metadata, color = DaybookColors.TextMuted)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HistoryTab(
    query: String,
    onQueryChange: (String) -> Unit,
    rows: List<TimelineEvent>,
    unfilteredEmpty: Boolean,
    tint: CardTint,
    isHabit: Boolean,
    isJournal: Boolean,
    isHabitJournal: Boolean,
    isOngoing: Boolean,
    iconKey: String,
    title: String,
    subtitle: String?,
    motivation: String?,
    category: String?,
    bottomClearance: androidx.compose.ui.unit.Dp,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenJournal: (String) -> Unit,
    onOpenRespond: (String) -> Unit,
    onOpenHabitJournalEdit: (String) -> Unit,
    onToggleHabit: (String) -> Unit
) {
    val rmDetail = LocalReduceMotion.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screenH, end = Spacing.screenH, top = 8.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
    ) {
        item { IdentityHeader(iconKey, title, subtitle, motivation, category, tint) }
        // v0.5.5 — an "Ongoing" habit has no day-to-day logs: keep the tab for layout consistency
        // but show only an empty state (no search field, no "Activity" section, no rows).
        if (isOngoing) {
            item {
                EmptyState(
                    icon = DaybookIcons.Clock,
                    title = "No activity",
                    body = "Ongoing habits don't track day-to-day logs."
                )
            }
            return@LazyColumn
        }
        item {
            DaybookTextField(
                value = query,
                onValueChange = onQueryChange,
                // §5.7 — no floating label; the placeholder carries the affordance.
                label = null,
                placeholder = "Search responses and notes",
                singleLine = true
            )
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("Activity")
        }

        when {
            unfilteredEmpty -> item {
                EmptyState(
                    icon = DaybookIcons.Clock,
                    title = "No activity yet",
                    body = "Actions on this item will appear here."
                )
            }
            rows.isEmpty() -> item {
                EmptyState(
                    icon = DaybookIcons.Clock,
                    title = "No matches",
                    body = "Nothing in this item's history matches your search."
                )
            }
            else -> items(rows, key = { it.id }) { event ->
                val occId = event.occurrenceId
                val onRowClick: (() -> Unit)? = when {
                    occId == null -> null
                    // Journal-as-habit round (B8): a habit-journal REPLIED row opens the plain
                    // edit-form, never the generic habit toggle — must sit ABOVE the plain
                    // `isHabit -> onToggleHabit` branch below.
                    isHabit && isHabitJournal && event.action == Event.Action.REPLIED -> ({ onOpenHabitJournalEdit(occId) })
                    isHabit -> ({ onToggleHabit(occId) })
                    event.action == Event.Action.REPLIED && isJournal -> ({ onOpenJournal(occId) })
                    event.action == Event.Action.REPLIED -> ({ onOpenRespond(occId) })
                    else -> null
                }
                TimelineRow(
                    event = event,
                    tint = tint,
                    // v0.5.4 Phase 5: a JOURNAL REPLIED row renders stacked Q&A from `qa_json`
                    // instead of the "Note: …" / description chips. Non-journal rows unaffected.
                    // Journal-as-habit round: the habit-side journal row shares the same rendering.
                    isJournal = isJournal || isHabitJournal,
                    onClick = onRowClick,
                    modifier = Modifier.animateItem(fadeInSpec = if (rmDetail) snap() else Motion.medium(), fadeOutSpec = if (rmDetail) snap() else Motion.fast())
                )
            }
        }

        // v0.5.3 Phase 3 (A4): the timeline loads the newest terminal page first; older pages are
        // appended on demand. Hidden while a search filter is active (the filter runs over the
        // already-loaded rows only).
        if (canLoadMore && query.isBlank() && rows.isNotEmpty()) {
            item {
                TextLink("Show older", onClick = onLoadMore)
            }
        }
    }
}

@Composable
private fun StatsTab(
    stats: DetailStats,
    tint: CardTint,
    isOngoing: Boolean,
    showStreaks: Boolean,
    iconKey: String,
    title: String,
    subtitle: String?,
    motivation: String?,
    category: String?,
    bottomClearance: androidx.compose.ui.unit.Dp
) {
    val rmDetail = LocalReduceMotion.current
    // rec 6 (S5): hide the streak figure when the user turned flames off — but an Ongoing habit's
    // day-count is the entire point of that type, so it always shows.
    val streaksVisible = isOngoing || showStreaks
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screenH, end = Spacing.screenH, top = 8.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
    ) {
        item { IdentityHeader(iconKey, title, subtitle, motivation, category, tint) }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("Stats")
        }
        // §5.7 — all cards CardTints.Neutral; the number carries the identity accent.
        if (streaksVisible) item(key = "stat-streaks") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.listGap),
                modifier = Modifier.animateItem(fadeInSpec = if (rmDetail) snap() else Motion.medium(), fadeOutSpec = if (rmDetail) snap() else Motion.fast())
            ) {
                StatCard(label = "Current streak", modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(DaybookIcons.Flame, null, tint = tint.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${stats.currentStreak}", style = MaterialTheme.typography.displayMedium, color = tint.accent)
                    }
                }
                StatCard(label = "Best", modifier = Modifier.weight(1f)) {
                    Text("${stats.longestStreak}", style = MaterialTheme.typography.displayMedium, color = tint.accent)
                }
            }
        }
        // v0.5.5 — completion-rate / this-month are meaningless for an "Ongoing" habit (zero
        // occurrences); show only the Current streak + Best row above.
        if (!isOngoing) {
            item(key = "stat-rates") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.listGap),
                    modifier = Modifier.animateItem(fadeInSpec = if (rmDetail) snap() else Motion.medium(), fadeOutSpec = if (rmDetail) snap() else Motion.fast())
                ) {
                    StatCard(label = "Completion rate", modifier = Modifier.weight(1f)) {
                        Text("${stats.completionRatePct}%", style = MaterialTheme.typography.displayMedium, color = tint.accent)
                    }
                    StatCard(label = "This month", modifier = Modifier.weight(1f)) {
                        Text("${stats.thisMonthCount}", style = MaterialTheme.typography.displayMedium, color = tint.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable ColumnScope.() -> Unit
) {
    SoftCard(tint = CardTints.Neutral, modifier = modifier) {
        Text(label, style = DaybookText.Metadata, color = DaybookColors.TextMuted)
        Spacer(Modifier.height(8.dp))
        value()
    }
}

/**
 * v0.5.4 Phase 5 — the (question, answer) pairs to stack under a JOURNAL REPLIED row, decoded from
 * its `qa_json` snapshot. Blank-answer rule: **skip the pair**. An entry made through the Phase-2
 * interim single-field screen only answers Q1, so rendering the rest as "—" would be a wall of
 * dashes; a skipped-blank list shows just what the user actually wrote. [JournalQa.decode] is
 * tolerant — null / blank / garbage → empty list. Pure — [JournalRowRenderTest].
 */
internal fun journalRowPairs(qaJson: String?): List<Pair<String, String>> =
    JournalQa.decode(qaJson).filter { it.second.isNotBlank() }

@Composable
private fun TimelineRow(
    event: TimelineEvent,
    tint: CardTint,
    isJournal: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // §5.7 — the little status glyph keeps its semantic colour; every card + chip surface is Neutral.
    val (icon: ImageVector, color: Color) = when (event.action) {
        Event.Action.COMPLETED -> MI.Filled.Check to DaybookColors.Success
        Event.Action.SKIPPED -> MI.Filled.Close to DaybookColors.Warning
        Event.Action.REPLIED -> DaybookIcons.Comment to tint.accent
        Event.Action.SHOWN, Event.Action.USER_SNOOZED -> DaybookIcons.Clock to DaybookColors.TextMuted
    }
    SoftCard(tint = CardTints.Neutral, onClick = onClick, modifier = modifier.fillMaxWidth(), contentPadding = 14.dp, elevation = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.timestamp, style = DaybookText.Metadata, color = DaybookColors.TextMuted)
                if (event.scheduledFor > 0L &&
                    com.daybook.app.util.DateTimeUtils.timestampToLocalDate(event.scheduledFor) !=
                    com.daybook.app.util.DateTimeUtils.timestampToLocalDate(event.rawTimestamp)
                ) {
                    Text(
                        "for " + com.daybook.app.util.DateTimeUtils.formatDate(
                            com.daybook.app.util.DateTimeUtils.timestampToLocalDate(event.scheduledFor)
                        ),
                        style = DaybookText.Metadata,
                        color = DaybookColors.TextMuted
                    )
                }
                Text(
                    event.displayLabel,
                    style = DaybookText.CardTitle,
                    color = DaybookColors.TextPrimary
                )
                // v0.5.4 Phase 5 — a JOURNAL REPLIED row renders its `qa_json` snapshot as stacked
                // (question, answer) pairs and suppresses the "Note: …" / description chips. Every
                // other row (FOOD/MED/CUSTOM REPLIED, SKIPPED, activity) is byte-identical to before.
                if (isJournal && event.action == Event.Action.REPLIED) {
                    journalRowPairs(event.qaJson).forEach { pair ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            pair.first,
                            style = DaybookText.Metadata,
                            color = DaybookColors.TextMuted
                        )
                        Text(
                            pair.second,
                            style = DaybookText.CardSubtitle,
                            color = DaybookColors.TextPrimary
                        )
                    }
                } else {
                    event.responseText?.let {
                        Spacer(Modifier.height(6.dp))
                        InnerChip(text = "Note: $it", tint = CardTints.Neutral)
                    }
                    event.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        InnerChip(text = it, tint = CardTints.Neutral)
                    }
                }
                // v0.5.4: Crohn's food-diary red-flag marker + suspected trigger food.
                event.redFlag?.takeIf { it != com.daybook.app.data.model.RedFlag.NONE }?.let { flag ->
                    Spacer(Modifier.height(6.dp))
                    val (flagLabel, flagColor) = when (flag) {
                        com.daybook.app.data.model.RedFlag.RED -> "Red flag" to DaybookColors.Danger
                        else -> "Possible trigger" to DaybookColors.Warning
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // v0.5.3 Phase 4 (§4.10) — semantic colour on the dot only; the text reads
                        // in the row's muted colour like the "Outside food" marker below.
                        Box(Modifier.size(8.dp).clip(CircleShape).background(flagColor))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            flagLabel + (event.suspectedFood?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()),
                            style = DaybookText.Metadata,
                            color = DaybookColors.TextMuted
                        )
                    }
                }
                // v0.5.2 build 8: "outside food" marker.
                if (event.outsideFood == true) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(DaybookColors.TextMuted))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Outside food",
                            style = DaybookText.Metadata,
                            color = DaybookColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}
