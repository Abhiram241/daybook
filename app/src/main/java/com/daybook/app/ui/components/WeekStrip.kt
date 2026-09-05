package com.daybook.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.DaybookColors
import androidx.compose.animation.core.snap
import com.daybook.app.ui.theme.IconButtonSize
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.util.DateTimeUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// Centre page of the pager. ~50 years of weeks each way — far more than anyone scrolls.
private const val WEEK_ANCHOR = 2600
// Centre page of the month pager. ~50 years of months either way.
private const val MONTH_ANCHOR = 600

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    // rec 1 (C1): "MONDAY" / "SUNDAY" / "SATURDAY" — the device-local week-start preference.
    weekStart: String = "MONDAY"
) {
    val scope = rememberCoroutineScope()
    val todayWeekStart = remember(today, weekStart) { DateTimeUtils.startOfWeek(today, weekStart) }
    val todayMonth = remember(today) { YearMonth.from(today) }

    fun weekStartForPage(page: Int): LocalDate =
        todayWeekStart.plusWeeks((page - WEEK_ANCHOR).toLong())

    fun pageForDate(date: LocalDate): Int {
        val ws = DateTimeUtils.startOfWeek(date, weekStart)
        return WEEK_ANCHOR + ChronoUnit.WEEKS.between(todayWeekStart, ws).toInt()
    }

    fun monthForPage(page: Int): YearMonth = todayMonth.plusMonths((page - MONTH_ANCHOR).toLong())
    fun pageForMonth(date: LocalDate): Int =
        MONTH_ANCHOR + ChronoUnit.MONTHS.between(todayMonth, YearMonth.from(date)).toInt()

    val pagerState = rememberPagerState(initialPage = pageForDate(selectedDate)) { WEEK_ANCHOR * 2 }
    val monthPagerState = rememberPagerState(initialPage = pageForMonth(selectedDate)) { MONTH_ANCHOR * 2 }

    // Bug fix (calendar selection "shifts after picking a date"): Sync 1 below can still be
    // mid-flight — animating the week pager to catch up with a `selectedDate` set elsewhere (e.g.
    // a month-grid tap on a different week) — when Sync 2's settle fires. If `selectedDate` moves
    // AGAIN before that settle lands (a second tap while the first is still animating), Sync 2
    // would recombine the STALE settled page with the NEWEST selected date and silently overwrite
    // the user's latest choice with a third, wrong date. This tracks the page Sync 1 itself just
    // targeted so Sync 2 can tell "our own programmatic scroll settling" apart from a genuine user
    // swipe, and ignore exactly the one settle event that scroll produces.
    var lastProgrammaticPage by remember { mutableStateOf<Int?>(null) }

    // Sync 1 (week pager): the selected date changed elsewhere — move the pager onto its week.
    // Gated on `!expanded`: while the month grid is showing, the week pager's HorizontalPager is
    // not composed (AnimatedContent only mounts one body at a time), so animateScrollToPage would
    // run against a pager with no measured viewport and produce erratic settle events that Sync 2
    // then turns into wrong onSelect() calls — a feedback loop that drifts the selection on its
    // own. `expanded` is kept in the key set so collapsing back to the week view re-runs this and
    // catches the pager up to wherever `selectedDate` ended up from month-grid taps.
    val targetPage = pageForDate(selectedDate)
    LaunchedEffect(targetPage, expanded) {
        if (!expanded && pagerState.currentPage != targetPage) {
            lastProgrammaticPage = targetPage
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Sync 2 (week pager): the pager settled on a new week — carry the selection to the same
    // weekday. Reads settledPage off the drag frames; scoped to the WEEK pager only.
    val currentSelected by rememberUpdatedState(selectedDate)
    LaunchedEffect(pagerState, expanded) {
        // While the month grid is showing the week pager is unmounted; any settle it reports comes
        // from stale state, not a user swipe. Ignore settles entirely until the week view is back.
        if (expanded) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page == lastProgrammaticPage) {
                    // Consume: only the settle immediately following our own scroll is suppressed:
                    // a later genuine user swipe that happens to land back on this same page must
                    // still carry the selection forward as normal.
                    lastProgrammaticPage = null
                    return@collect
                }
                val sel = currentSelected
                // Carry the same *offset within the week*, not the same weekday number, so a
                // non-Monday week-start keeps the selection column stable.
                val newDate = weekStartForPage(page)
                    .plusDays(DateTimeUtils.indexInWeek(sel, weekStart).toLong())
                // Same rule DayCell enforces on direct taps (`if (!isFuture) onClick()`): the
                // programmatic settle path must never carry the selection onto a day that hasn't
                // happened yet.
                if (newDate != sel && !newDate.isAfter(today)) onSelect(newDate)
            }
    }

    // Month pager only FOLLOWS the selected date (a day tap in the grid). It never re-emits
    // onSelect on settle — paging months is a view move, not a selection change. Gated on
    // `expanded`: the grid is collapsed by default, so there's no reason to run a scroll effect
    // on a pager nobody's looking at on every Home recomposition.
    val targetMonthPage = pageForMonth(selectedDate)
    LaunchedEffect(targetMonthPage, expanded) {
        if (expanded && monthPagerState.currentPage != targetMonthPage) {
            monthPagerState.animateScrollToPage(targetMonthPage)
        }
    }

    val label = if (expanded) {
        val m = monthForPage(monthPagerState.settledPage)
        remember(m) { "${m.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${m.year}" }
    } else {
        val labelWeekStart = weekStartForPage(pagerState.settledPage)
        remember(labelWeekStart) {
            val end = labelWeekStart.plusDays(6)
            if (labelWeekStart.month == end.month) {
                "${labelWeekStart.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${labelWeekStart.year}"
            } else {
                "${labelWeekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} – " +
                    "${end.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${end.year}"
            }
        }
    }

    val rm = LocalReduceMotion.current
    val chevronRot by animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = if (rm) snap() else Motion.softSpring(),
        label = "calChevron"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = if (expanded) "Previous month" else "Previous week",
                onClick = {
                    scope.launch {
                        if (expanded) monthPagerState.animateScrollToPage(monthPagerState.currentPage - 1)
                        else pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                size = IconButtonSize.Sm.dp
            )
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = DaybookColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Next month" else "Next week",
                onClick = {
                    scope.launch {
                        if (expanded) monthPagerState.animateScrollToPage(monthPagerState.currentPage + 1)
                        else pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                size = IconButtonSize.Sm.dp
            )
        }

        // "Back to today" — only while a non-today date is selected. Springs in/out on the
        // selection crossing today so it doesn't occupy header space the rest of the time.
        AnimatedVisibility(visible = selectedDate != today) {
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                TextLink(text = "Back to today", onClick = { onSelect(today) })
            }
        }

        // Week strip <-> month grid swap. AnimatedContent cross-fades the two bodies while a
        // SizeTransform morphs the height, so the row below (the expand handle) glides instead
        // of jumping. The fade window also masks the month grid's first composition.
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                if (rm) {
                    ContentTransform(
                        targetContentEnter = fadeIn(snap()),
                        initialContentExit = fadeOut(snap()),
                        sizeTransform = SizeTransform { _, _ -> snap() }
                    )
                } else ContentTransform(
                    // v0.5.3 Phase 4 (§4.7) — literal tweens → Motion tokens.
                    targetContentEnter = fadeIn(Motion.medium()),
                    initialContentExit = fadeOut(Motion.medium()),
                    // A fixed short tween over the large week<->month height delta is far cheaper
                    // to run than a spring settling on that distance (v0.5.2 build 8 item 6).
                    sizeTransform = SizeTransform { _, _ -> Motion.slow() }
                )
            },
            label = "calBody"
        ) { isExpanded ->
            if (isExpanded) {
                MonthGrid(
                    pagerState = monthPagerState,
                    monthForPage = ::monthForPage,
                    selectedDate = selectedDate,
                    today = today,
                    onSelect = onSelect,
                    weekStart = weekStart
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    key = { it },
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val weekStart = weekStartForPage(page)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        (0L..6L).forEach { offset ->
                            val day = weekStart.plusDays(offset)
                            DayCell(
                                day = day,
                                selected = day == selectedDate,
                                isToday = day == today,
                                isFuture = day.isAfter(today),
                                dimmed = false,
                                onClick = { onSelect(day) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Expand / collapse handle, centred below the calendar body (not up in the label row).
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircleIconButton(
                icon = Icons.Filled.KeyboardArrowDown, // v0.5.3 Phase 4 (§4.6)
                contentDescription = if (expanded) "Collapse calendar" else "Expand month view",
                onClick = onToggleExpanded,
                size = IconButtonSize.Sm.dp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRot }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthGrid(
    pagerState: androidx.compose.foundation.pager.PagerState,
    monthForPage: (Int) -> YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    weekStart: String = "MONDAY"
) {
    val firstDow = remember(weekStart) { DateTimeUtils.firstDayOfWeek(weekStart) }
    val weekdayInitials = remember(firstDow) {
        (0..6).map {
            firstDow.plus(it.toLong())
                .getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdayInitials.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = DaybookColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(
            state = pagerState,
            key = { it },
            // 0, not 1: expanding must compose ONE month, not three, on the toggle frame.
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val ym = monthForPage(page)
            // Hoist the per-page grid math so it isn't recomputed on every recomposition of
            // this page (v0.5.2 build 8 item 6).
            val (rows, gridStart) = remember(ym, weekStart) {
                val firstOfMonth = ym.atDay(1)
                // Lead-in blanks depend on the configured week-start, not a hardcoded Monday.
                val leadingBlanks = DateTimeUtils.indexInWeek(firstOfMonth, weekStart)
                val start = firstOfMonth.minusDays(leadingBlanks.toLong())
                // Only the rows this month actually spans (4–6), not a fixed 6.
                val rowCount = (leadingBlanks + ym.lengthOfMonth() + 6) / 7
                rowCount to start
            }
            Column(Modifier.fillMaxWidth()) {
                (0 until rows).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (0 until 7).forEach { col ->
                            val day = gridStart.plusDays((row * 7 + col).toLong())
                            DayCell(
                                day = day,
                                selected = day == selectedDate,
                                isToday = day == today,
                                isFuture = day.isAfter(today),
                                dimmed = day.month != ym.month,
                                showWeekday = false,
                                onClick = { onSelect(day) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWeekday: Boolean = true
) {
    val accent = LocalAccent.current
    val cellDesc = remember(day, selected, isToday, isFuture) {
        val base = day.format(
            java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
        )
        val state = when {
            selected && isToday -> ", today, selected"
            selected -> ", selected"
            isToday -> ", today"
            isFuture -> ", upcoming"
            else -> ""
        }
        base + state
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .semantics { contentDescription = cellDesc }
            // No data exists for a day that hasn't happened yet — a future day is shown (dimmed
            // via `isFuture` below) but not selectable.
            .clickableImpl(remember { MutableInteractionSource() }) { if (!isFuture) onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showWeekday) {
            Text(
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                style = MaterialTheme.typography.labelSmall,
                color = DaybookColors.TextMuted
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) accent else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${day.dayOfMonth}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = when {
                    selected -> DaybookColors.OnSolid
                    dimmed -> DaybookColors.TextFaint
                    isFuture -> DaybookColors.TextFaint
                    else -> DaybookColors.TextPrimary
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (isToday && !selected) accent
                    else androidx.compose.ui.graphics.Color.Transparent
                )
        )
    }
}
