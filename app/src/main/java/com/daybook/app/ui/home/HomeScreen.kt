package com.daybook.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import com.daybook.app.ui.icons.DaybookIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.daybook.app.data.model.ColorTag
import com.daybook.app.ui.components.*
import com.daybook.app.ui.icons.Icons
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.IconButtonSize
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.util.notification.NotificationUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (itemType: String, itemId: String) -> Unit = { _, _ -> },
    onNavigateToJournal: (occurrenceId: String) -> Unit = {},
    onNavigateToJournalBackfill: (taskId: String, slotMillis: Long) -> Unit = { _, _ -> },
    // Journal Mode: edit a resolved (Logged) intake entry from the Today card, opening the same
    // editable RespondScreen the Detail→Activity list uses. Journal entries route via
    // onNavigateToJournal instead (the card picks the branch on item.isJournal).
    onOpenEntryEdit: (occurrenceId: String) -> Unit = {},
    // Journal-as-habit round: the habit-side counterparts. `onNavigateToHabitJournalChat` is for a
    // live PENDING occurrence id; `onNavigateToHabitJournalBackfill` is for a past-day synthetic
    // slot with no occurrence row yet (habitId + scheduled millis). A resolved (LOGGED) row's card
    // routes through `onOpenHabitJournalEdit` instead (B8) — the card picks the branch on
    // item.isHabitJournal, exactly like the existing FoodMed isJournal branch above.
    onNavigateToHabitJournalChat: (occurrenceId: String) -> Unit = {},
    onNavigateToHabitJournalBackfill: (habitId: String, slotMillis: Long) -> Unit = { _, _ -> },
    onOpenHabitJournalEdit: (occurrenceId: String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    // v0.5.3 Phase 4 (§4.8) — the scaffold PaddingValues contract; was a bare `Dp` clearance.
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HomeViewModel = hiltViewModel()
) {
    val items by viewModel.homeItems.collectAsState()
    val visibleItems by viewModel.visibleItems.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val showResolved by viewModel.showResolved.collectAsState()
    val monthReady by viewModel.monthReady.collectAsState()
    val habitStreak by viewModel.habitStreak.collectAsState()
    val foodMedStreak by viewModel.foodMedStreak.collectAsState()
    val showStreaks by viewModel.showStreaks.collectAsState()
    val heroStyle by viewModel.heroStyle.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val greeting by viewModel.greeting.collectAsState()
    val today by viewModel.today.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val calendarDefaultExpanded by viewModel.calendarDefaultExpanded.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-2) — re-read on resume, same pattern
    // SettingsScreen's notification health row already uses (a channel/permission toggled in
    // system Settings and back doesn't otherwise trigger recomposition here).
    var notifTick by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) { notifTick++; onPauseOrDispose { } }
    val notificationsBlocked = remember(notifTick) { viewModel.notificationBlockReason() != null }

    // rec 1 (C4): seed the calendar expand state from `calendar_default_expanded` on first
    // composition only, so a mid-session toggle wins and survives rotation (rememberSaveable).
    var calendarExpandedOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(calendarDefaultExpanded) {
        if (calendarExpandedOverride == null) calendarExpandedOverride = calendarDefaultExpanded
    }
    val calendarExpanded = calendarExpandedOverride ?: calendarDefaultExpanded
    // F3-2: while the WeekStrip's 200 ms SizeTransform is running, every reminder card below it
    // is re-laid-out each frame. Gate the placement animation off for the duration so the cards
    // track the calendar in lockstep instead of each restarting a spring from a different frame.
    var calendarAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(calendarExpanded) {
        // v0.5.3 Phase 5 (§3.9) — one shared duration with WeekStrip's SizeTransform.
        calendarAnimating = true; delay(Motion.calendarExpandMillis.toLong()); calendarAnimating = false
    }
    var undoToken by remember { mutableStateOf(0) }
    var remindersFilterOpen by remember { mutableStateOf(false) }
    // HARD (D10.2): headline count + progress ratios stay bound to the UNFILTERED list.
    val pending = remember(items) { items.count { it.canComplete || it.canReply || it.canSkip } }
    val habitRatio = remember(items) { ratio(items, isHabit = true) }
    val foodRatio = remember(items) { ratio(items, isHabit = false) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // v0.5.3 Phase 5 (§5.4 / UI Q2 / backlog #11) — the whole header is pinned now, matching
        // the sibling tabs' ScreenHeader: greeting + date sub-row, the Hero "$n left today" line,
        // and the trailing Avatar all in one non-scrolling block. The Hero no longer rides in the
        // LazyColumn as an item.
        HomeHeader(
            greeting = greeting,
            dateLabel = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")),
            heroText = heroLine(heroStyle, pending),
            showHero = heroStyle != "HIDDEN",
            profile = profile,
            onAvatarClick = onNavigateToSettings
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            // v0.5.3 Phase 4 (§4.8) — the scaffold PaddingValues straight through.
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
        ) {
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 4 (S-1) — a wedged/errored sync used to be
            // invisible outside Settings/Account. Surface it right on Today instead.
            if (syncStatus is com.daybook.app.data.sync.SyncStatus.Paused ||
                syncStatus is com.daybook.app.data.sync.SyncStatus.Error
            ) {
                item {
                    SyncStatusBanner(status = syncStatus, onClick = onNavigateToSettings)
                }
            }
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-2) — a blocked notification channel/
            // permission used to fail every reminder silently (a bare Log.w). Deep-links straight
            // to the channel-scoped system settings, same intent SettingsScreen's health row uses.
            if (notificationsBlocked) {
                item {
                    NotificationsBlockedBanner(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    })
                }
            }
            item {
                WeekStrip(
                    selectedDate = selectedDate,
                    today = today,
                    onSelect = { viewModel.selectDate(it) },
                    expanded = calendarExpanded,
                    onToggleExpanded = { calendarExpandedOverride = !calendarExpanded },
                    weekStart = weekStart
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader("Your progress")
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.listGap)
                ) {
                    ProgressCard("Habits", Icons.RUN, habitRatio, habitStreak, showStreaks, CardTints.Mint, Modifier.weight(1f).fillMaxHeight())
                    ProgressCard("Intake", Icons.MEDICATION, foodRatio, foodMedStreak, showStreaks, CardTints.Peach, Modifier.weight(1f).fillMaxHeight())
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                val filterActive = typeFilter.isNotEmpty() || showResolved
                SectionHeader(
                    title = "Reminders",
                    trailing = {
                        Box {
                            CircleIconButton(
                                icon = DaybookIcons.FilterList,
                                contentDescription = "Filter reminders",
                                onClick = { remindersFilterOpen = true },
                                // v0.5.3 Phase 4 (§4.7) — was 36; Lg to match Habits/Intake.
                                size = IconButtonSize.Lg.dp
                            )
                            if (filterActive) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(9.dp)
                                        .clip(CircleShape).background(LocalAccent.current)
                                )
                            }
                        }
                    }
                )
            }

            if (visibleItems.isEmpty()) {
                item {
                    val filtered = (typeFilter.isNotEmpty() || showResolved) && items.isNotEmpty()
                    EmptyState(
                        icon = Icons.getIcon(Icons.CHECK_CIRCLE),
                        title = if (filtered) "Nothing here" else "All caught up",
                        body = if (filtered) "No reminders match this filter." else "Nothing scheduled for this day.",
                        // v0.5.3 Phase 5 (§5.4) — curated glyph in a neutral tile, not a Mint one.
                        tint = CardTints.Neutral
                    )
                }
            } else {
                itemsIndexed(visibleItems, key = { _, it -> it.id }) { index, item ->
                    val openJournal = {
                        if (item.occurrenceId != null) onNavigateToJournal(item.occurrenceId)
                        else onNavigateToJournalBackfill(item.detailId, item.scheduledEpoch)
                    }
                    val openHabitJournalChat = {
                        if (item.occurrenceId != null) onNavigateToHabitJournalChat(item.occurrenceId)
                        else onNavigateToHabitJournalBackfill(item.detailId, item.scheduledEpoch)
                    }
                    ReminderCard(
                        item = item,
                        tint = CardTints.resolve(ColorTag.fromNameOrAuto(item.colorTag).name.takeIf { it != "AUTO" }, index),
                        backfillBlocked = item.isBackfill && !monthReady,
                        // F3-2 + F3-3: animateItem (not the deprecated animateItemPlacement) so a
                        // fully disjoint key set on a day switch cross-fades uniformly instead of
                        // popping. snap() during the calendar transition (F3-2) OR when reduce-motion
                        // is on (rec 4); otherwise the app's own placement spring.
                        modifier = Modifier.animateItem(
                            fadeInSpec = if (reduceMotion) snap() else Motion.medium(),
                            fadeOutSpec = if (reduceMotion) snap() else Motion.fast(),
                            placementSpec = if (calendarAnimating || reduceMotion) snap() else Motion.placementSpring()
                        ),
                        onComplete = { viewModel.completeItem(item) },
                        onSkip = { viewModel.skipItem(item) },
                        onSnooze = { viewModel.snoozeItem(item) },
                        onReply = { text, flag, suspected, outside -> viewModel.replyToItem(item, text, flag, suspected, outside) },
                        onUndo = { viewModel.revertItem(item); undoToken++ },
                        onOpenEntryEdit = { item.occurrenceId?.let { onOpenEntryEdit(it) } },
                        onOpenJournal = openJournal,
                        onOpenHabitJournalChat = openHabitJournalChat,
                        onOpenHabitJournalEdit = { item.occurrenceId?.let { onOpenHabitJournalEdit(it) } },
                        onOpen = {
                            when {
                                item.isJournal -> openJournal()
                                // B8: a resolved (LOGGED) Journal-habit entry opens the edit-form,
                                // never the chat, even from the whole-card tap.
                                item.isHabitJournal && item.statusLabel == LOGGED_LABEL ->
                                    item.occurrenceId?.let { onOpenHabitJournalEdit(it) } ?: openHabitJournalChat()
                                item.isHabitJournal -> openHabitJournalChat()
                                else -> onNavigateToDetail(if (item.isHabit) "habit" else "food_med", item.detailId)
                            }
                        }
                    )
                }
            }
        }
    }

        SortSheet(
            visible = remindersFilterOpen,
            onDismiss = { remindersFilterOpen = false },
            title = "Show",
            sortOptions = emptyList(),
            selectedSortKey = "",
            onSelectSort = {},
            facetTitle = "Type",
            facetOptions = listOf(
                FacetOption(ReminderFilter.HABITS.name, "Habits", null),
                FacetOption(ReminderFilter.INTAKE.name, "Intake", null),
                FacetOption(ReminderFilter.JOURNAL.name, "Journal", null),
            ),
            selectedFacetKeys = typeFilter.map { it.name }.toSet(),
            onToggleFacet = { key -> viewModel.toggleFilter(ReminderFilter.valueOf(key)) },
            showArchived = showResolved,
            onToggleArchived = { viewModel.setShowResolved(!showResolved) },
            archivedRowLabel = "Show completed",
            onReset = viewModel::resetReminderFilter,
        )

        UndoSnack(token = undoToken)
    }
}

/**
 * v0.5.3 Phase 5 (§5.4 / UI Q2) — the pinned Home header. Owns the status-bar inset like
 * [com.daybook.app.ui.components.ScreenHeader]; the list below uses `contentPadding(top = 0)`
 * straight from the scaffold. Greeting + date sub-row, trailing [Avatar], then the Hero line.
 */
/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 4 (S-1) — a small, tappable Today-screen banner for a
 * sync that's paused (unresolved conflict — S-1's own bug was the most common way to reach this)
 * or persistently erroring. Deliberately reuses [SoftCard]/[CardTints.Butter] rather than
 * inventing a new visual language for "warning" — this app has no dedicated banner primitive yet.
 */
@Composable
private fun SyncStatusBanner(status: com.daybook.app.data.sync.SyncStatus, onClick: () -> Unit) {
    val (title, body) = when (status) {
        is com.daybook.app.data.sync.SyncStatus.Paused ->
            "Sync paused" to "There's a data conflict to resolve — tap to open Account."
        is com.daybook.app.data.sync.SyncStatus.Error ->
            "Sync error" to (status.message?.let { "Couldn't sync: $it. Tap to retry." }
                ?: "Couldn't sync — tap to open Account and retry.")
        else -> return
    }
    SoftCard(
        tint = CardTints.Butter,
        borderColor = DaybookColors.Warning.copy(alpha = 0.4f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, style = DaybookText.CardTitle, color = CardTints.Butter.onFill)
        Text(body, style = DaybookText.CardSubtitle, color = CardTints.Butter.onFillMuted)
    }
}

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 7 (N-2) — Today-screen banner for a blocked notification
 * channel/permission, so a reminder silently failing to post is visible instead of only showing up
 * as "the app never buzzed." Shares [SoftCard]/[CardTints.Butter] with [SyncStatusBanner] rather
 * than a second visual language for "warning."
 */
@Composable
private fun NotificationsBlockedBanner(onClick: () -> Unit) {
    SoftCard(
        tint = CardTints.Butter,
        borderColor = DaybookColors.Warning.copy(alpha = 0.4f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Notifications are off", style = DaybookText.CardTitle, color = CardTints.Butter.onFill)
        Text(
            "Reminders can't be shown right now — tap to fix in system settings.",
            style = DaybookText.CardSubtitle,
            color = CardTints.Butter.onFillMuted
        )
    }
}

/** rec 2 (H4) — the hero-line phrasing. HIDDEN is handled by the caller (BigHeadline not composed). */
internal fun heroLine(style: String, pending: Int): String = when {
    pending <= 0 -> "All done"
    style == "COUNT_TO_GO" -> "$pending to go"
    style == "COUNT_TASKS" -> "$pending task" + (if (pending == 1) "" else "s")
    else -> "$pending left today" // COUNT_LEFT (default) + HIDDEN (text unused)
}

@Composable
private fun HomeHeader(
    greeting: String,
    dateLabel: String,
    heroText: String,
    showHero: Boolean,
    profile: ProfileUi,
    onAvatarClick: () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenH, end = Spacing.screenH, top = statusBarTop + Spacing.headerInset, bottom = Spacing.listTop)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // rec 2 (H1) — MINIMAL tone yields an empty string; skip the row entirely.
                if (greeting.isNotBlank()) {
                    Text(
                        greeting,
                        style = DaybookText.CardTitle,
                        color = DaybookColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    dateLabel,
                    style = DaybookText.Metadata,
                    color = DaybookColors.TextMuted
                )
            }
            Avatar(
                photoPath = profile.photoPath,
                name = profile.name,
                size = 40.dp,
                onClick = onAvatarClick
            )
        }
        // rec 2 (H4) — HIDDEN drops the BigHeadline and its top spacing.
        if (showHero) {
            BigHeadline(text = heroText, style = DaybookText.Hero)
        }
    }
}

private fun ratio(items: List<HomeItem>, isHabit: Boolean): Float {
    val group = items.filter { it.isHabit == isHabit }
    if (group.isEmpty()) return 0f
    // Completeness is a function of status only: Done / Logged / Skipped count, everything
    // else does not. "Missed" is a status label but is not progress; future/unanswered
    // items have no label and never counted before this fix (REV-10).
    val done = group.count { it.statusLabel != null && it.statusLabel != MISSED_LABEL }
    return done.toFloat() / group.size
}

// Fixed so the two "Your progress" cards are always the same height and width, in every
// state — the streak pill only renders when streak > 0, and SoftCard otherwise wraps content,
// which used to leave one card taller than its sibling (Section 5).
private val ProgressCardHeight = 144.dp
private val StreakSlotHeight = 36.dp

@Composable
private fun ProgressCard(
    title: String,
    iconKey: String,
    progress: Float,
    streak: Int,
    showStreak: Boolean,
    tint: CardTint,
    modifier: Modifier = Modifier
) {
    SoftCard(
        tint = tint,
        modifier = modifier
            .heightIn(min = ProgressCardHeight)
            .semantics {
                contentDescription = "$title ${(progress * 100).toInt()} percent complete" +
                    (if (streak > 0 && showStreak) ", $streak day streak" else "")
            }
    ) {
        Column(Modifier.fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon = Icons.getIcon(iconKey), tint = tint, size = IconButtonSize.Md.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = DaybookText.CardTitle,
                    color = tint.onFill,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            // v0.5.3 Phase 4 (§4.10) — no `color` override: the card-internal bar uses `tint.accent`
            // so Habits (Mint) and Intake (Peach) read as different colours, not both LocalAccent.
            PastelProgressBar(progress = progress, tint = tint)
            Spacer(Modifier.weight(1f))
            // Value + streak row: fixed height so it never changes when the pill is absent.
            Row(
                modifier = Modifier.heightIn(min = StreakSlotHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${(progress * 100).toInt()}%", style = DaybookText.CardTitle, color = tint.onFill)
                Spacer(Modifier.weight(1f))
                // v0.5.1 §I-UI: Opus keyed habitStreak / foodMedStreak to the selected date, so a
                // past empty day now yields 0 and the pill vanishes; a past day inside a real run
                // shows that run. StreakSlotHeight (36.dp) keeps both cards equal height whether or
                // not the pill is present — re-checked now the pill toggles far more often.
                if (streak > 0 && showStreak) {
                    StatPill(
                        icon = DaybookIcons.Flame,
                        value = "$streak",
                        label = if (streak == 1) "day" else "days",
                        tint = tint,
                        contentDescription = "$streak day streak"
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    item: HomeItem,
    tint: CardTint,
    backfillBlocked: Boolean = false,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
    onReply: (String, com.daybook.app.data.model.RedFlag?, String?, Boolean?) -> Unit,
    onUndo: () -> Unit,
    onOpenEntryEdit: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenHabitJournalChat: () -> Unit,
    onOpenHabitJournalEdit: () -> Unit,
    onOpen: () -> Unit
) {
    var sheetOpen by remember { mutableStateOf(false) }
    // Journal Mode: a resolved intake / journal text reply is editable (not undoable). Its status
    // tap and the sheet's "Edit" row open the entry editor; journal vs intake picks the route.
    // Journal-as-habit round: a resolved Journal-HABIT row is editable too (B8) — `isLoggedText`
    // stays FoodMed-only (it drives the generic `onOpenEntryEdit` respond-form route, which a habit
    // has no equivalent of); `isEditableHabitJournal` is its habit-side counterpart.
    val isLoggedText = !item.isHabit && item.statusLabel == LOGGED_LABEL && item.occurrenceId != null
    val isEditableHabitJournal = item.isHabit && item.isHabitJournal && item.statusLabel == LOGGED_LABEL && item.occurrenceId != null
    val editEntry: () -> Unit = {
        when {
            item.isJournal -> onOpenJournal()
            isEditableHabitJournal -> onOpenHabitJournalEdit()
            else -> onOpenEntryEdit()
        }
    }
    var replyOpen by remember(item.id) { mutableStateOf(false) }
    var draft by remember(item.id) { mutableStateOf("") }
    // v0.5.4: FOOD-only trigger-flag capture on the inline reply, pre-filled from the reminder's
    // defaults so a known-trigger reminder starts flagged.
    var flagDraft by remember(item.id) {
        mutableStateOf(item.defaultRedFlag ?: com.daybook.app.data.model.RedFlag.NONE)
    }
    var suspectedDraft by remember(item.id) { mutableStateOf(item.defaultSuspectedFood.orEmpty()) }
    // v0.5.2 build 8: FOOD-only "outside food" marker on the inline reply, pre-filled from the default.
    var outsideDraft by remember(item.id) { mutableStateOf(item.defaultOutsideFood == true) }

    // v0.5.3 Phase 5 (§5.4) — no size animation on the SoftCard root (it fought animateItem on
    // the same LazyColumn node). v0.5.3 Phase 7 (#38) — the inline reply now animates via
    // AnimatedVisibility below rather than animateContentSize on an inner Column.
    SoftCard(tint = tint, onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.getIcon(item.iconKey), tint = tint, contentDescription = item.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = DaybookText.CardTitle, color = tint.onFill, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let {
                    Text(it, style = DaybookText.CardSubtitle, color = tint.onFillMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // v0.5.4: recorded Crohn's trigger flag on a resolved FOOD log.
                item.loggedRedFlag?.takeIf { it != com.daybook.app.data.model.RedFlag.NONE }?.let { flag ->
                    val flagColor = if (flag == com.daybook.app.data.model.RedFlag.RED)
                        DaybookColors.Danger else DaybookColors.Warning
                    val flagText = (if (flag == com.daybook.app.data.model.RedFlag.RED) "Red flag" else "Possible trigger") +
                        (item.loggedSuspectedFood?.let { " · $it" }.orEmpty())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // v0.5.3 Phase 4 (§4.10) — the semantic colour stays on the dot; the text
                        // reads in `tint.onFillMuted` (Danger/Warning as text is low-contrast on
                        // the pastel `fill`).
                        Box(Modifier.size(7.dp).clip(CircleShape).background(flagColor))
                        Spacer(Modifier.width(5.dp))
                        // v0.5.3 Phase 5 (§5.4) — one caption system: flag text is Metadata / onFillFaint.
                        Text(
                            flagText,
                            style = DaybookText.Metadata,
                            color = tint.onFillFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // v0.5.2 build 8: recorded "outside food" marker on a resolved FOOD log.
                if (item.loggedOutsideFood == true) {
                    Text(
                        "Outside food",
                        style = DaybookText.Metadata,
                        color = tint.onFillFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        DaybookIcons.AlarmClock,
                        contentDescription = null,
                        tint = tint.onFillMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(item.scheduledTime, style = DaybookText.Metadata, color = tint.onFillMuted)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                backfillBlocked -> {
                    Text(
                        "Loading this month…",
                        style = DaybookText.Metadata,
                        color = tint.onFillMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                }
                item.statusLabel != null -> {
                    // Journal Mode: a Logged text reply is editable (tap → entry editor); habits
                    // (Done/Skipped) and a skipped intake/journal keep undo-to-pending.
                    // Journal-as-habit round: a resolved Journal-habit row is editable too (B8).
                    val editable = isLoggedText || isEditableHabitJournal
                    val undoable = !editable && item.occurrenceId != null && item.statusLabel != MISSED_LABEL
                    val interactive = editable || undoable
                    // v0.5.3 Phase 4 (§4.3) — the interactive status reads through TextLink (44dp
                    // tap target, Role.Button); the inert case stays a plain Text.
                    if (interactive) {
                        TextLink(
                            item.statusLabel,
                            onClick = { if (editable) editEntry() else onUndo() },
                            color = if (item.statusLabel == MISSED_LABEL) tint.onFillMuted else tint.accent
                        )
                    } else {
                        Text(
                            item.statusLabel,
                            style = DaybookText.ButtonLabel,
                            color = if (item.statusLabel == MISSED_LABEL) tint.onFillMuted else tint.accent
                        )
                    }
                    // Without this the resolved card renders no "…" trigger at all, so the sheet's
                    // "Undo" / "Edit" row (below) could never be opened.
                    if (interactive) {
                        Spacer(Modifier.width(6.dp))
                        CircleIconButton(icon = MI.Filled.MoreVert, contentDescription = "More", onClick = { sheetOpen = true }, size = IconButtonSize.Sm.dp)
                    }
                }
                item.canComplete -> {
                    CircleIconButton(icon = MI.Filled.Check, contentDescription = "Complete", onClick = onComplete, style = CircleStyle.Success, size = IconButtonSize.Md.dp)
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(icon = MI.Filled.MoreVert, contentDescription = "More", onClick = { sheetOpen = true }, size = IconButtonSize.Sm.dp)
                }
                item.isJournal && item.canReply -> {
                    CircleIconButton(icon = DaybookIcons.Comment, contentDescription = "Write entry", onClick = onOpenJournal, style = CircleStyle.Tonal, size = IconButtonSize.Md.dp)
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(icon = MI.Filled.MoreVert, contentDescription = "More", onClick = { sheetOpen = true }, size = IconButtonSize.Sm.dp)
                }
                // Journal-as-habit round: the habit-side counterpart of the FoodMed branch above —
                // a PENDING Journal-habit slot's trailing action opens the chat, not Complete/Skip.
                item.isHabitJournal && item.canReply -> {
                    CircleIconButton(icon = DaybookIcons.Comment, contentDescription = "Write entry", onClick = onOpenHabitJournalChat, style = CircleStyle.Tonal, size = IconButtonSize.Md.dp)
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(icon = MI.Filled.MoreVert, contentDescription = "More", onClick = { sheetOpen = true }, size = IconButtonSize.Sm.dp)
                }
                item.canReply -> {
                    CircleIconButton(
                        icon = DaybookIcons.Send,
                        contentDescription = "Reply",
                        onClick = { replyOpen = !replyOpen },
                        style = CircleStyle.Tonal,
                        size = IconButtonSize.Md.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(icon = MI.Filled.MoreVert, contentDescription = "More", onClick = { sheetOpen = true }, size = IconButtonSize.Sm.dp)
                }
                item.isFuture -> {
                    Text(
                        "Upcoming",
                        style = DaybookText.Metadata,
                        color = tint.onFillMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                }
            }
        }

        // v0.5.3 Phase 7 (#38) — one expand/collapse mechanism across the app: AnimatedVisibility
        // + expand/shrinkVertically for anything that shows/hides (matches AdvancedSection in
        // Forms.kt). `animateContentSize` is now reserved for content that resizes in place
        // without a visibility toggle. This also gives the inline reply a collapse animation it
        // previously lacked (the `if` removed it instantly).
        val rm = LocalReduceMotion.current
        AnimatedVisibility(
            visible = replyOpen && item.canReply && !item.isJournal && !item.isHabitJournal && !backfillBlocked,
            enter = if (rm) fadeIn() else fadeIn(Motion.fast()) + expandVertically(Motion.softSpring()),
            exit = if (rm) fadeOut() else fadeOut(Motion.fast()) + shrinkVertically(Motion.softSpring())
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // v0.5.3 Phase 5 (§5.4 / backlog #26) — the tint-aware DaybookTextField, not a
                    // hand-rolled Box + BasicTextField.
                    DaybookTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = null,
                        placeholder = NotificationUtils.resolvePrompt(item.promptMessage),
                        singleLine = true,
                        tint = tint,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        icon = DaybookIcons.Send,
                        contentDescription = "Log",
                        onClick = {
                            if (draft.isNotBlank()) {
                                onReply(
                                    draft.trim(),
                                    if (item.isFood) flagDraft else null,
                                    if (item.isFood) suspectedDraft.trim().takeIf { it.isNotEmpty() } else null,
                                    if (item.isFood) outsideDraft else null
                                )
                                draft = ""; replyOpen = false
                            }
                        },
                        style = CircleStyle.Tonal,
                        size = IconButtonSize.Md.dp
                    )
                }

                // v0.5.4: Crohn's trigger flag + suspected-food, inline, FOOD reminders only.
                if (item.isFood) {
                    Spacer(Modifier.height(10.dp))
                    // v0.5.3 Phase 5 (§5.9) — small "Trigger flag" label, matching RespondScreen.
                    Text("Trigger flag", style = DaybookText.Metadata, color = tint.onFillMuted)
                    Spacer(Modifier.height(6.dp))
                    RedFlagPicker(selected = flagDraft, onSelect = { flagDraft = it })
                    Spacer(Modifier.height(8.dp))
                    DaybookTextField(
                        value = suspectedDraft,
                        onValueChange = { suspectedDraft = it },
                        label = null,
                        placeholder = "Suspected trigger food (optional)",
                        singleLine = true,
                        tint = tint
                    )
                    Spacer(Modifier.height(8.dp))
                    DaybookChip(
                        label = "Outside food",
                        selected = outsideDraft,
                        onClick = { outsideDraft = !outsideDraft }
                    )
                }
            }
        }
    }

    BottomSheetMenu(
        visible = sheetOpen,
        onDismiss = { sheetOpen = false },
        actions = buildList {
            if (isLoggedText || isEditableHabitJournal) {
                // Journal Mode: edit the entry instead of undoing it (a text reply has an edit
                // path; there is no "undo" for it on the Today card anymore).
                add(SheetAction(MI.Filled.Edit, "Edit", onClick = editEntry))
            } else if (item.statusLabel != null && item.statusLabel != MISSED_LABEL && item.occurrenceId != null) {
                add(SheetAction(DaybookIcons.Unarchive, "Undo", onClick = onUndo))
            }
            // Snooze/Skip act on a *pending* slot: snoozing or skipping an already-resolved
            // occurrence would re-arm or re-write a finished row. Every card that could open this
            // sheet before v0.5.3 had statusLabel == null, so this list is unchanged for them.
            if (item.statusLabel == null) {
                add(SheetAction(DaybookIcons.Clock, "Snooze", onClick = onSnooze))
                add(SheetAction(MI.Filled.Close, "Skip", onClick = onSkip))
            }
        }
    )
}
