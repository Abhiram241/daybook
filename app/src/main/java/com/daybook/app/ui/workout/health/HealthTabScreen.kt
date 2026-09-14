package com.daybook.app.ui.workout.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.data.health.HealthCardKind
import com.daybook.app.data.health.displayLabel
import com.daybook.app.data.health.visibleHealthCards
import com.daybook.app.data.model.HealthSession
import com.daybook.app.ui.DaybookDatePickerDialog
import com.daybook.app.ui.components.EmptyState
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.ScreenHeader
import com.daybook.app.ui.components.SegmentSpec
import com.daybook.app.ui.components.SegmentedControl
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.UndoSnack
import com.daybook.app.ui.components.WeekStrip
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.workout.beast.BeastPalette
import com.daybook.app.ui.workout.beast.BeastText
import com.daybook.app.util.formatHealthCalories
import com.daybook.app.util.formatHealthCount
import com.daybook.app.util.formatHealthDistanceKm
import com.daybook.app.util.formatHealthDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val RANGE_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** "1–7 Sep" style range label for the `Range` mode header/button. */
private fun formatHealthRangeSubtitle(start: LocalDate, end: LocalDate): String =
    "${start.format(RANGE_DATE_FMT)} – ${end.format(RANGE_DATE_FMT)}"

/**
 * B6 (§7.4) — the repurposed third Beast Mode nav destination. `Day` mode reuses `WeekStrip`
 * verbatim (byte-for-byte the same calendar as Today's), `Range` mode is an aggregate/mean-or-sum
 * summary (§7.4's `aggregateHealthDays`). Every card follows the per-metric hide rule
 * ([visibleHealthCards]) — no empty/dashed card ever renders. Cards lay out two-to-a-row
 * (user request — matches the density of a band-companion app) with wide items (Nutrition,
 * band sessions) spanning both columns; tapping a card opens [HealthDetailSheet] with the fuller
 * breakdown that doesn't fit a half-width tile (user request — "richer data ... when I click").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTabScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    viewModel: HealthTabViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRangeSheet by remember { mutableStateOf(false) }
    var showCustomStart by remember { mutableStateOf(false) }
    var showCustomEnd by remember { mutableStateOf(false) }
    var pendingCustomStart by remember { mutableStateOf(state.range.start) }
    var detailKind by remember { mutableStateOf<HealthCardKind?>(null) }
    var detailSession by remember { mutableStateOf<HealthSession?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.requestPermissionsContract()
    ) { viewModel.onPermissionFlowFinished() }

    Box(Modifier.fillMaxSize().background(BeastPalette.groundBrush())) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Health",
                subtitle = if (state.mode == HealthTabMode.DAY) {
                    state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
                } else {
                    formatHealthRangeSubtitle(state.range.start, state.range.end)
                },
                actions = {
                    Box {
                        com.daybook.app.ui.components.CircleIconButton(
                            icon = DaybookIcons.FilterList,
                            contentDescription = "Choose cards to show",
                            onClick = viewModel::openCardVisibilitySheet,
                            style = com.daybook.app.ui.components.CircleStyle.Tonal,
                            size = com.daybook.app.ui.theme.IconButtonSize.Lg.dp
                        )
                        if (state.hiddenCards.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(LocalAccent.current)
                            )
                        }
                    }
                }
            )
            Column(Modifier.padding(horizontal = 20.dp)) {
                SegmentedControl(
                    options = listOf(SegmentSpec("DAY", "Day"), SegmentSpec("RANGE", "Range")),
                    selectedKey = if (state.mode == HealthTabMode.DAY) "DAY" else "RANGE",
                    onSelect = { key -> viewModel.setMode(if (key == "RANGE") HealthTabMode.AGGREGATE else HealthTabMode.DAY) }
                )
            }
            Spacer(Modifier.height(8.dp))

            when (val ladder = state.ladder) {
                HealthLadderState.Unavailable -> EmptyState(
                    icon = DaybookIcons.Heart,
                    title = "Health Connect isn't available on this phone.",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                HealthLadderState.UpdateRequired -> EmptyState(
                    icon = DaybookIcons.Heart,
                    title = "Health Connect needs updating before Daybook can read your health data.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    actionLabel = "Open Settings",
                    onAction = onOpenSettings
                )
                HealthLadderState.NotConnected -> EmptyState(
                    icon = DaybookIcons.Heart,
                    title = "Connect Health Connect",
                    body = "Daybook only reads. It never writes anything to Health Connect.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    actionLabel = "Connect",
                    onAction = { permissionLauncher.launch(viewModel.initialPermissionSet()) }
                )
                is HealthLadderState.Connected -> {
                    // User request — no more persistent "Up to date…"/"Refreshing…" caption; that
                    // sync-status ping now only ever shows as a transient toast on a SUCCESSFUL
                    // manual refresh (see the `UndoSnack` below, keyed on `refreshToastToken`). The
                    // one thing still worth a persistent line here is a REAL, actionable, non-sync
                    // status: some permission types not shared with Daybook at all (M8).
                    if (ladder.missingCount > 0) {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text(
                                "${ladder.missingCount} type(s) not shared",
                                style = DaybookText.Caption,
                                color = DaybookColors.TextMuted
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                    // User request — since the explicit "Refresh now" button is gone, a one-line
                    // hint that the gesture exists (swipe-down is otherwise not discoverable).
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            "Swipe down to refresh",
                            style = DaybookText.Caption,
                            color = DaybookColors.TextMuted
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    // User request — "remove the normal refresh button, only keep swipe down":
                    // the "Refresh now" GhostButton is gone; swipe-down pull-to-refresh (below) is
                    // now the only way to trigger refreshNow() from this screen (besides the OS
                    // permission-flow callback).
                    val pullState = rememberPullToRefreshState()
                    val accent = LocalAccent.current
                    // User request — "just like Chrome reload": the indicator must appear and
                    // travel down WITH the finger as the user drags, arriving at its resting spot
                    // exactly when the pull is released, then spin continuously while the refresh
                    // runs. The previous version only reacted to the boolean `isRefreshing` — it
                    // had no idea a drag was even happening, so it stayed invisible for the whole
                    // gesture and only faded in once the refresh actually started, which read as
                    // "appearing out of nowhere" (the drag-follow behaviour it was missing is
                    // exactly what `PullToRefreshState.distanceFraction`, 0f at rest to 1f at the
                    // trigger threshold, is for). This tracks that fraction during the drag for
                    // position/scale/a determinate ring, then swaps to a real continuously-spinning
                    // `CircularProgressIndicator` once `isRefreshing` is true — the "never goes
                    // away" bug this session already root-caused and fixed separately (see
                    // `runCatchingCancellable`, util/CancellableRunCatching.kt) is unrelated to this
                    // visual and still holds regardless of which indicator shape is drawn here.
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { if (!state.isRefreshing) viewModel.refreshNow() },
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            val pullProgress = pullState.distanceFraction.coerceIn(0f, 1f)
                            val visible = state.isRefreshing || pullProgress > 0f
                            if (visible) {
                                // Travels from just under the header down to its resting spot as
                                // the fraction goes 0->1, exactly tracking the drag (Chrome-style);
                                // once refreshing, it's pinned at the resting spot, not still
                                // reacting to `distanceFraction` (which the library snaps back to 0
                                // as soon as the finger lifts, well before the refresh finishes).
                                val restingOffset = 56.dp
                                val offsetY = if (state.isRefreshing) restingOffset else restingOffset * pullProgress
                                val scale = if (state.isRefreshing) 1f else (0.5f + 0.5f * pullProgress)
                                Box(
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = offsetY)
                                        .graphicsLayer {
                                            scaleX = scale; scaleY = scale
                                            alpha = if (state.isRefreshing) 1f else pullProgress
                                        }
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(DaybookColors.SurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.isRefreshing) {
                                        // Real indeterminate spin — Compose's own continuous
                                        // rotation, not tied to any drag/refresh-state bookkeeping.
                                        CircularProgressIndicator(
                                            color = accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        // While dragging: a determinate ring that fills in with
                                        // how far the user has pulled, so releasing past the
                                        // threshold feels like a deliberate, well-understood action
                                        // (same "fill up, then let go" language Chrome/most apps use).
                                        CircularProgressIndicator(
                                            progress = { pullProgress },
                                            color = accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        HealthContent(
                            state = state,
                            onSelectDate = viewModel::selectDate,
                            onToggleExpanded = viewModel::toggleCalendarExpanded,
                            onOpenRangeSheet = { showRangeSheet = true },
                            onOpenDetail = { detailKind = it },
                            onOpenSessionDetail = { detailSession = it },
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }

        // User request — the sync-status ping is now a transient toast only on a SUCCESSFUL
        // manual refresh, not a persistent caption; goes away on its own like every other
        // `UndoSnack` in the app.
        UndoSnack(token = state.refreshToastToken, text = state.refreshToastMessage)
    }

    HealthAggregateSheet(
        visible = showRangeSheet,
        onDismiss = { showRangeSheet = false },
        onSelectPreset = { preset -> viewModel.setRangePreset(preset); showRangeSheet = false },
        onSelectCustom = { showRangeSheet = false; pendingCustomStart = state.range.start; showCustomStart = true }
    )
    if (showCustomStart) {
        DaybookDatePickerDialog(
            initial = pendingCustomStart,
            maxDate = state.today,
            onDismiss = { showCustomStart = false },
            onConfirm = { picked -> pendingCustomStart = picked; showCustomStart = false; showCustomEnd = true }
        )
    }
    if (showCustomEnd) {
        DaybookDatePickerDialog(
            initial = state.range.end,
            maxDate = state.today,
            onDismiss = { showCustomEnd = false },
            onConfirm = { picked -> viewModel.setCustomRange(pendingCustomStart, picked); showCustomEnd = false }
        )
    }

    HealthDetailSheet(
        kind = detailKind,
        state = state,
        onDismiss = { detailKind = null }
    )
    HealthSessionDetailSheet(
        session = detailSession,
        onDismiss = { detailSession = null }
    )

    // User request — "hide parameters I don't want to see in health section". Reuses the shared
    // sort/filter sheet's facet-only mode (no sort rows) exactly like the Home "Reminders" filter
    // does; checked = visible (shown), unchecked = hidden.
    if (state.showCardVisibilitySheet) {
        com.daybook.app.ui.components.SortSheet(
            visible = true,
            onDismiss = viewModel::dismissCardVisibilitySheet,
            sortOptions = emptyList(),
            selectedSortKey = "",
            onSelectSort = {},
            title = "Show cards",
            facetTitle = "Show cards",
            facetOptions = HealthCardKind.entries.map {
                com.daybook.app.ui.components.FacetOption(it.name, it.displayLabel(), null)
            },
            selectedFacetKeys = HealthCardKind.entries.filterNot { it in state.hiddenCards }.map { it.name }.toSet(),
            onToggleFacet = { key -> viewModel.toggleCardHidden(HealthCardKind.valueOf(key)) }
        )
    }
}

/** One tile's worth of pre-resolved card data — built once per composition from [metricsFor],
 *  then laid out two-to-a-row (§3.2). */
private data class HealthCardSpec(
    val kind: HealthCardKind,
    val title: String,
    val icon: ImageVector,
    val tint: CardTint,
    val metrics: List<com.daybook.app.data.health.HealthMetric>,
    val subtitle: String? = null
)

@Composable
private fun HealthContent(
    state: HealthTabUiState,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onToggleExpanded: () -> Unit,
    onOpenRangeSheet: () -> Unit,
    onOpenDetail: (HealthCardKind) -> Unit,
    onOpenSessionDetail: (HealthSession) -> Unit,
    contentPadding: PaddingValues
) {
    // User request — "hide parameters I don't want to see": subtract the user's hidden set after
    // the existing has-data gate, so a hidden card never re-appears just because it started
    // having data again — the hide is a deliberate user choice, not a data-presence toggle.
    val visible = (if (state.mode == HealthTabMode.DAY) {
        visibleHealthCards(state.day, state.daySessions.isNotEmpty())
    } else {
        visibleHealthCards(state.rangeAggregate, state.rangeSessions.isNotEmpty())
    }) - state.hiddenCards
    val displayMode = if (state.mode == HealthTabMode.DAY) {
        com.daybook.app.data.health.HealthCardDisplayMode.DAY
    } else {
        com.daybook.app.data.health.HealthCardDisplayMode.RANGE
    }
    val weightUnit = com.daybook.app.data.workout.parseWeightUnit(state.weightUnit)
    val agg = state.rangeAggregate
    fun metrics(kind: HealthCardKind) =
        com.daybook.app.data.health.metricsFor(kind, state.day, agg, displayMode, weightUnit)
    val rangeSubtitle = { kind: HealthCardKind ->
        if (state.mode == HealthTabMode.DAY) null else com.daybook.app.data.health.rangeSubtitleFor(kind)
    }

    val tileCards = buildList {
        if (HealthCardKind.STEPS in visible) add(HealthCardSpec(HealthCardKind.STEPS, "Activity", DaybookIcons.DirectionsRun, CardTints.Mint, metrics(HealthCardKind.STEPS), rangeSubtitle(HealthCardKind.STEPS)))
        if (HealthCardKind.CALORIES in visible) add(HealthCardSpec(HealthCardKind.CALORIES, "Calories", DaybookIcons.Flame, CardTints.Peach, metrics(HealthCardKind.CALORIES), rangeSubtitle(HealthCardKind.CALORIES)))
        if (HealthCardKind.HEART_RATE in visible) add(HealthCardSpec(HealthCardKind.HEART_RATE, "Heart rate", DaybookIcons.Heart, CardTints.Rose, metrics(HealthCardKind.HEART_RATE), rangeSubtitle(HealthCardKind.HEART_RATE)))
        if (HealthCardKind.SLEEP in visible) add(HealthCardSpec(HealthCardKind.SLEEP, "Sleep", DaybookIcons.Bedtime, CardTints.SlateBlue, metrics(HealthCardKind.SLEEP), rangeSubtitle(HealthCardKind.SLEEP)))
        if (HealthCardKind.SPO2 in visible) add(HealthCardSpec(HealthCardKind.SPO2, "Oxygen", DaybookIcons.WaterDrop, CardTints.Butter, metrics(HealthCardKind.SPO2), rangeSubtitle(HealthCardKind.SPO2)))
        if (HealthCardKind.WEIGHT in visible) add(HealthCardSpec(HealthCardKind.WEIGHT, "Weight", DaybookIcons.BarChart, CardTints.Neutral, metrics(HealthCardKind.WEIGHT), rangeSubtitle(HealthCardKind.WEIGHT)))
        if (HealthCardKind.HYDRATION in visible) add(HealthCardSpec(HealthCardKind.HYDRATION, "Hydration", DaybookIcons.WaterDrop, CardTints.Mint, metrics(HealthCardKind.HYDRATION), rangeSubtitle(HealthCardKind.HYDRATION)))
    }
    val tilePairs = tileCards.chunked(2)
    val nutritionVisible = HealthCardKind.NUTRITION in visible
    val sessions = if (state.mode == HealthTabMode.DAY) state.daySessions else state.rangeSessions

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            if (state.mode == HealthTabMode.DAY) {
                Column {
                    WeekStrip(
                        selectedDate = state.selectedDate,
                        today = state.today,
                        onSelect = onSelectDate,
                        expanded = state.calendarExpanded,
                        onToggleExpanded = onToggleExpanded,
                        weekStart = state.weekStart,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    GhostButton(
                        text = formatHealthRangeSubtitle(state.range.start, state.range.end),
                        onClick = onOpenRangeSheet,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.rangeAggregate.daysWithAnyData} of ${state.rangeAggregate.totalDays} days have data",
                        style = DaybookText.Caption, color = DaybookColors.TextMuted
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = DaybookIcons.Heart,
                    title = "No data yet",
                    body = "In Mi Fitness, open Profile → Settings → Health Connect and turn on Steps, Sleep, Heart rate and Workouts.",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // §3.2 — equal heights per grid row: each pair is a Row(IntrinsicSize.Min) with two
        // weight(1f).fillMaxHeight() cards, so the pair matches the taller card and grows with
        // font scale instead of clipping. An odd last card keeps half width with an empty Spacer.
        items(tilePairs) { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { spec ->
                    HealthMetricCard(
                        title = spec.title,
                        metrics = spec.metrics,
                        tint = spec.tint,
                        icon = spec.icon,
                        subtitle = spec.subtitle,
                        onClick = { onOpenDetail(spec.kind) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (nutritionVisible) {
            item {
                NutritionCard(
                    metrics = metrics(HealthCardKind.NUTRITION),
                    subtitle = com.daybook.app.data.health.SourceAppLabels.labelFor(
                        if (state.mode == HealthTabMode.DAY) state.day?.nutritionSourceApp else state.rangeAggregate.nutritionSourceApp
                    )?.let { "From $it" } ?: "From another app",
                    onClick = { onOpenDetail(HealthCardKind.NUTRITION) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        if (HealthCardKind.SESSIONS in visible) {
            items(sessions) { session ->
                Box(Modifier.padding(horizontal = 20.dp)) {
                    SessionRow(session, onClick = { onOpenSessionDetail(session) })
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** §3.2 — Nutrition (full width): Calories as hero on the left; Protein/Carbs/Fat as three equal
 *  columns on the right, each with the label on top and value below, centred. */
@Composable
private fun NutritionCard(
    metrics: List<com.daybook.app.data.health.HealthMetric>,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calories = metrics.firstOrNull { it.label == "Calories" }
    val macros = metrics.filter { it.label != "Calories" }
    SoftCard(tint = CardTints.Butter, onClick = onClick, contentPadding = 16.dp, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(CardTints.Butter.accent.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
                Icon(DaybookIcons.Restaurant, contentDescription = null, tint = CardTints.Butter.accent, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Nutrition", style = DaybookText.CardTitle, color = CardTints.Butter.onFill, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = DaybookText.Caption, color = CardTints.Butter.onFillMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(calories?.label ?: "Calories", style = DaybookText.CardSubtitle, color = CardTints.Butter.onFillMuted)
                Text(calories?.value ?: "–", style = BeastText.BigNumber, color = CardTints.Butter.onFill)
            }
            macros.forEach { m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(m.label, style = DaybookText.Caption, color = CardTints.Butter.onFillMuted)
                    Row {
                        Text(m.value, style = BeastText.TileNumber, color = CardTints.Butter.onFill)
                        m.unit?.let { Text(" $it", style = DaybookText.Caption, color = CardTints.Butter.onFillMuted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: HealthSession, onClick: () -> Unit) {
    SoftCard(tint = CardTints.Lavender, onClick = onClick, contentPadding = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Text(
            session.title?.takeIf { it.isNotBlank() }
                ?: com.daybook.app.data.health.ExerciseTypeLabels.labelFor(session.exerciseType),
            style = DaybookText.CardTitle, color = CardTints.Lavender.onFill
        )
        Text(
            formatHealthDuration(session.durationMinutes) +
                (session.activeCalories?.let { " · " + formatHealthCalories(it) } ?: ""),
            style = DaybookText.Caption, color = CardTints.Lavender.onFillMuted
        )
    }
}
