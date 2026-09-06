package com.daybook.app.ui.settings

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.BuildConfig
import com.daybook.app.R
import com.daybook.app.ui.DaybookDatePickerDialog
import com.daybook.app.ui.TimePickerDialog
import com.daybook.app.ui.components.*
import com.daybook.app.util.DateTimeUtils
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AccentColor
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.FontChoice
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.theme.fontChoiceFamily
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/* ------------------------------------------------------------------------- */
/* Hub                                                                        */
/* ------------------------------------------------------------------------- */

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenTodayCalendar: () -> Unit = {},
    onOpenNavigation: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenData: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenAppLock: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    accountViewModel: com.daybook.app.ui.account.AccountViewModel = hiltViewModel(),
    lockViewModel: com.daybook.app.ui.lock.LockViewModel = hiltViewModel()
) {
    val accountSubtitle by accountViewModel.accountSubtitle.collectAsState()
    val appLockEnabled by lockViewModel.isEnabled.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val fontChoice by viewModel.fontChoice.collectAsState()
    val nameDraft by viewModel.nameDraft.collectAsState()
    val profilePhotoPath by viewModel.profilePhotoPath.collectAsState()
    val photoError by viewModel.photoError.collectAsState()

    var editingName by remember { mutableStateOf(false) }
    // v0.5.4 Phase 1 (S1) — confirm dialog visibility for the hub "Sign out" row.
    var showSignOut by remember { mutableStateOf(false) }
    // v0.5.3 Phase 5 (§5.12) — a brief "Saved" cue after the debounced auto-commit, so the silent
    // 500ms commit is visible. Purely presentational.
    var nameSavedCue by remember { mutableStateOf(false) }
    LaunchedEffect(nameSavedCue) { if (nameSavedCue) { delay(1800); nameSavedCue = false } }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onProfilePhotoPicked(uri) }
    val pickPhoto: () -> Unit = {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Re-read the notification block state whenever the screen resumes (e.g. back from
    // system settings), same pattern the notifications sub-screen uses.
    var tick by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val notifSubtitle = remember(tick) {
        if (viewModel.notificationBlockReason() == null) "All set" else "Action needed"
    }

    val accentName = (settings?.accentColor ?: AccentColor.DEFAULT).name
        .lowercase().replaceFirstChar { it.uppercase() }

    if (nameDraft != null && editingName) {
        LaunchedEffect(nameDraft) {
            nameSavedCue = false
            delay(500)
            viewModel.commitName()
            nameSavedCue = true
        }
    }

    // v0.5.4 Phase 1 (S1) — confirm before signing out. Exercises the build-11
    // wipeLocalForSignOut path; the auth gate recomposes to sign-in, so no nav callback.
    if (showSignOut) {
        DaybookAlertDialog(
            onDismissRequest = { showSignOut = false },
            title = "Sign out?",
            text = {
                Text(
                    "Signing out clears Daybook's data on this device. Your data re-syncs when you sign back in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DaybookColors.TextMuted
                )
            },
            confirmLabel = "Sign out",
            onConfirm = { showSignOut = false; accountViewModel.signOut() },
            dismissLabel = "Cancel",
            onDismiss = { showSignOut = false },
            destructive = true
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        // v0.5.3 Phase 4 (§4.1) — pinned back header; the BigHeadline("Settings") list item is gone.
        BackHeader(title = "Settings", onBack = onNavigateBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = Spacing.screenH, end = Spacing.screenH,
                top = Spacing.listTop, bottom = Spacing.screenBottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap)
        ) {
            item {
                ProfileHeader(
                    name = settings?.userName ?: "",
                    subtitle = "Your Daybook",
                    photoPath = profilePhotoPath,
                    onEditName = { editingName = !editingName },
                    onPickPhoto = pickPhoto
                )
                if (profilePhotoPath != null) {
                    // v0.5.3 Phase 4 (§4.3) — TextLink primitive (44dp tap target).
                    TextLink(
                        "Remove photo",
                        onClick = viewModel::onRemoveProfilePhoto,
                        color = DaybookColors.TextMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                photoError?.let {
                    Text(
                        it,
                        style = DaybookText.Caption,
                        color = DaybookColors.Warning,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (editingName) {
                    Spacer(Modifier.height(12.dp))
                    DaybookTextField(
                        value = nameDraft ?: settings?.userName ?: "",
                        onValueChange = { viewModel.onNameDraftChange(it) },
                        label = "Name",
                        placeholder = "Add your name"
                    )
                    AnimatedVisibility(visible = nameSavedCue, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            "Saved",
                            style = DaybookText.Metadata,
                            color = DaybookColors.TextMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = MI.Filled.Person,
                        title = "Account & sync",
                        subtitle = accountSubtitle,
                        onClick = onOpenAccount
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = DaybookIcons.Palette,
                        title = "Appearance",
                        subtitle = "$accentName · ${fontChoice.label}",
                        onClick = onOpenAppearance
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = MI.Filled.DateRange,
                        title = "Today & calendar",
                        subtitle = "Week start, clock, greeting, streaks",
                        onClick = onOpenTodayCalendar
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = MI.Filled.Menu,
                        title = "Navigation",
                        subtitle = "Default tab and which tabs show",
                        onClick = onOpenNavigation
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = MI.Filled.Notifications,
                        title = "Notifications & alarms",
                        subtitle = notifSubtitle,
                        onClick = onOpenNotifications
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = DaybookIcons.Lock,
                        title = "App lock",
                        subtitle = if (appLockEnabled) "On" else "Off",
                        onClick = onOpenAppLock
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = DaybookIcons.ImportExport,
                        title = "Export & import",
                        subtitle = "Back up or restore your data",
                        onClick = onOpenData
                    )
                }
            }

            // v0.5.1 §M: the "Monthly backup reminder" SettingsGroup used to sit here. Removed —
            // the feature had no scheduling logic behind it, only a switch. Its dead column
            // `backup_reminder_enabled` was dropped by MIGRATION_11_12 (v0.5.2 D2).

            item {
                // v0.5.4 Phase 1 (S1) — sign out from the hub. Own group above the version footer;
                // no chevron (it is an action, not a nav row). Confirm via DaybookAlertDialog below.
                SettingsGroup {
                    SettingsRow(
                        icon = MI.AutoMirrored.Filled.ExitToApp,
                        title = "Sign out",
                        onClick = { showSignOut = true },
                        trailing = {}
                    )
                }
            }

            item {
                // v0.5.3 Phase 5 (§5.12) — the app's one deliberately centred standalone label:
                // CardTitle "Daybook" over a Metadata version line.
                // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 0a — "Copy crash log" row, visible only
                // when util/CrashHandler.kt has ever written a trace to internal storage. Lets a
                // user retrieve the last crash without a connected device.
                val clipboard = LocalClipboardManager.current
                val hasCrashLog = remember { viewModel.hasCrashLog() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 1 — the app's own launcher mark as a
                    // brand touch here. Image (not Icon): ic_launcher_foreground already renders in
                    // greyscale, so it re-themes cleanly under any AccentColor without needing a
                    // content tint.
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 8.dp)
                    )
                    Text(
                        "Daybook",
                        style = DaybookText.CardTitle,
                        color = DaybookColors.TextMuted
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = DaybookText.Metadata,
                        color = DaybookColors.TextFaint
                    )
                    if (hasCrashLog) {
                        TextLink(
                            "Copy crash log",
                            onClick = {
                                val text = viewModel.crashLogText()
                                if (!text.isNullOrEmpty()) {
                                    clipboard.setText(AnnotatedString(text))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Sub-screen shell                                                           */
/* ------------------------------------------------------------------------- */

@Composable
internal fun SettingsSubScreen(
    title: String,
    onNavigateBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
    ) {
        // v0.5.3 Phase 4 (§4.1) — pinned back header; the in-list BigHeadline(title) is gone.
        BackHeader(title = title, onBack = onNavigateBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    start = Spacing.screenH, end = Spacing.screenH,
                    top = Spacing.listTop, bottom = Spacing.screenBottomInset
                )
        ) {
            content()
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Appearance                                                                 */
/* ------------------------------------------------------------------------- */

@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val fontChoice by viewModel.fontChoice.collectAsState()
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    SettingsSubScreen("Appearance", onNavigateBack) {
        SectionHeader("Accent color", subtitle = "Tints buttons, toggles and highlights across the app.")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                val current = settings?.accentColor ?: AccentColor.DEFAULT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AccentColor.entries.forEach { a ->
                        // v0.5.3 Phase 5 (§5.13 / backlog #24) — shared Swatch grammar with TintPicker:
                        // rounded-square, 44dp target, selected Check.
                        Swatch(
                            color = a.color,
                            selected = a == current,
                            onClick = { if (settings != null) viewModel.setAccentColor(a.storageKey) },
                            checkColor = DaybookColors.OnSolid,
                            contentDescription = a.name.lowercase().replaceFirstChar { it.uppercase() }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader("Font", subtitle = "Changes the typeface across the whole app.")
        // v0.5.3 Phase 5 (§5.13) — SettingsGroup container. A font list needs no per-row leading
        // icon (unlike the hub's badged rows); the family preview on each label carries it.
        SettingsGroup {
          Column(Modifier.padding(horizontal = Spacing.cardInner)) {
            FontChoice.entries.forEachIndexed { index, choice ->
                if (index > 0) {
                    HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                }
                val selected = choice == fontChoice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableImpl(remember { MutableInteractionSource() }) {
                            viewModel.setFontChoice(choice.storageKey)
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        choice.label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = fontChoiceFamily(choice)
                        ),
                        color = DaybookColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            MI.Filled.Check,
                            contentDescription = "Selected",
                            tint = LocalAccent.current,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
          }
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader("Accessibility")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                SettingsToggleRow(
                    label = "Reduce motion",
                    subtitle = "Turns off springy animations and page slides.",
                    checked = reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Shared toggle row                                                          */
/* ------------------------------------------------------------------------- */

/** A label + optional subtitle + trailing [Switch]; the whole row toggles. Dark-only palette. */
@Composable
internal fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableImpl(remember { MutableInteractionSource() }) { if (enabled) onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) DaybookColors.TextPrimary else DaybookColors.TextFaint
            )
            if (subtitle != null) {
                Text(subtitle, style = DaybookText.Caption, color = DaybookColors.TextMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LocalAccent.current,
                checkedThumbColor = DaybookColors.OnSolid
            )
        )
    }
}

/** rec 5 — a "Start" / "End" quiet-hours time row; greyed out and inert when the switch is off. */
@Composable
private fun QuietTimeRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) DaybookColors.TextPrimary else DaybookColors.TextFaint,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.weight(1f)) {
            GhostButton(
                text = value,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Today & calendar (rec 1 / 2 / 6)                                           */
/* ------------------------------------------------------------------------- */

@Composable
fun TodayCalendarSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val weekStart by viewModel.weekStart.collectAsState()
    val clock24h by viewModel.clock24h.collectAsState()
    val calDefaultExpanded by viewModel.calendarDefaultExpanded.collectAsState()
    val greetingTone by viewModel.greetingTone.collectAsState()
    val greetingTimeWord by viewModel.greetingTimeWord.collectAsState()
    val heroStyle by viewModel.heroStyle.collectAsState()
    val hideResolved by viewModel.homeHideResolved.collectAsState()
    val streakMode by viewModel.streakMode.collectAsState()
    val showStreaks by viewModel.showStreaks.collectAsState()
    val restDaysCsv by viewModel.streakRestDays.collectAsState()

    SettingsSubScreen("Today & calendar", onNavigateBack) {
        // ---- Calendar (rec 1) ------------------------------------------------------------
        SectionHeader("Calendar")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Week starts on", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
                SegmentedControl(
                    options = listOf(
                        SegmentSpec("SUNDAY", "Sun"),
                        SegmentSpec("MONDAY", "Mon"),
                        SegmentSpec("SATURDAY", "Sat")
                    ),
                    selectedKey = weekStart,
                    onSelect = viewModel::setWeekStart
                )
                HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                SettingsToggleRow(
                    label = "24-hour time",
                    subtitle = "Show times as 18:05 instead of 6:05 PM.",
                    checked = clock24h,
                    onCheckedChange = viewModel::setClock24h
                )
                HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                Text("Default calendar view", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
                SegmentedControl(
                    options = listOf(
                        SegmentSpec("false", "Week"),
                        SegmentSpec("true", "Month")
                    ),
                    selectedKey = calDefaultExpanded.toString(),
                    onSelect = { viewModel.setCalendarDefaultExpanded(it.toBoolean()) }
                )
            }
        }

        Spacer(Modifier.height(Spacing.listGap))

        // ---- Greeting (rec 2) ----------------------------------------------------------
        SectionHeader("Greeting")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Greeting style", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
                SegmentedControl(
                    options = listOf(
                        SegmentSpec("WARM", "Warm"),
                        SegmentSpec("PLAIN", "Plain"),
                        SegmentSpec("MINIMAL", "Minimal")
                    ),
                    selectedKey = greetingTone,
                    onSelect = viewModel::setGreetingTone
                )
                HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                SettingsToggleRow(
                    label = "Show time-of-day word",
                    subtitle = "Adds \"Good morning / afternoon / evening\" to the greeting.",
                    checked = greetingTimeWord,
                    onCheckedChange = viewModel::setGreetingTimeWord,
                    enabled = greetingTone != "MINIMAL"
                )
                HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                Text("Hero line", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
                Column {
                    listOf(
                        "COUNT_LEFT" to "13 left today",
                        "COUNT_TO_GO" to "13 to go",
                        "COUNT_TASKS" to "13 tasks",
                        "HIDDEN" to "Hidden"
                    ).forEach { (key, label) ->
                        RadioRow(label = label, selected = heroStyle == key) { viewModel.setHeroStyle(key) }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.listGap))

        // ---- Reminders list (rec 3, hide-resolved only) ------------------------------
        SectionHeader("Reminders")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner)) {
                SettingsToggleRow(
                    label = "Hide resolved reminders by default",
                    subtitle = "The Today list starts with completed / skipped / logged items hidden.",
                    checked = hideResolved,
                    onCheckedChange = viewModel::setHomeHideResolved
                )
            }
        }

        Spacer(Modifier.height(Spacing.listGap))

        // ---- Streak display (rec 6) --------------------------------------------------
        SectionHeader("Streak display")
        SettingsGroup {
            Column(Modifier.padding(Spacing.cardInner), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Streak counting", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
                SegmentedControl(
                    options = listOf(
                        SegmentSpec("STRICT", "Strict"),
                        SegmentSpec("LENIENT", "Lenient")
                    ),
                    selectedKey = streakMode,
                    onSelect = viewModel::setStreakMode
                )
                Text(
                    "Lenient counts a day where everything was done or deliberately skipped.",
                    style = DaybookText.Caption,
                    color = DaybookColors.TextMuted
                )
                HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                Text("Rest days", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
                Text(
                    "Chosen weekdays never break a streak and are never required.",
                    style = DaybookText.Caption,
                    color = DaybookColors.TextMuted
                )
                val restDays = remember(restDaysCsv) { DateTimeUtils.jsonToDays(restDaysCsv) }
                DayOfWeekSelector(
                    selected = restDays,
                    onToggle = { d ->
                        val next = restDays.toMutableList()
                        if (!next.remove(d)) next.add(d)
                        viewModel.setStreakRestDays(DateTimeUtils.daysToJson(next))
                    }
                )
                HorizontalDivider(color = DaybookColors.Hairline, thickness = 1.dp)
                SettingsToggleRow(
                    label = "Show streak flames",
                    subtitle = "Hides the flame pill on Today and the streak figure on Detail stats.",
                    checked = showStreaks,
                    onCheckedChange = viewModel::setShowStreaks
                )
            }
        }
    }
}

/** Single-choice row: label + trailing radio; the whole row selects. */
@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableImpl(remember { MutableInteractionSource() }, onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(MI.Filled.Check, contentDescription = "Selected", tint = LocalAccent.current, modifier = Modifier.size(20.dp))
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Notifications & alarms                                                     */
/* ------------------------------------------------------------------------- */

@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    SettingsSubScreen("Notifications & alarms", onNavigateBack) {
        val ctx = LocalContext.current
        val clock24h by viewModel.clock24h.collectAsState()
        var tick by remember { mutableStateOf(0) }
        LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }

        // App-level toggle AND per-channel state. A channel the user turned off reports
        // IMPORTANCE_NONE and silently drops every post while the app-level check still
        // says "enabled" — so showing only the app-level state hides the real blocker.
        val blockReason = remember(tick) { viewModel.notificationBlockReason() }
        val notifsOn = blockReason == null
        val exactOn = remember(tick) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            else true
        }
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 12 (N-5): an OEM battery restriction can silently
        // drain the 7-day reminder window with no signal the user ever sees — surface both real
        // platform APIs (not just the generic "on some phones..." caption below, which stays as a
        // model-specific hint since Android's own APIs don't cover every OEM's proprietary killer).
        val batteryOk = remember(tick) {
            val ignoringOptimizations = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
            val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
            } else false
            ignoringOptimizations && !backgroundRestricted
        }
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { tick++ }

        SectionHeader("Permissions", subtitle = "Reminders can only alert you when both are allowed.")
        FormGroup(title = null) {
            PermissionRow(label = "Notifications", granted = notifsOn, onFix = {
                val act = ctx as? Activity
                val perm = Manifest.permission.POST_NOTIFICATIONS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED &&
                    act != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(act, perm)
                ) {
                    notifLauncher.launch(perm)
                } else {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }
            })
            Spacer(Modifier.height(12.dp))
            PermissionRow(label = "Exact alarms", granted = exactOn, onFix = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + ctx.packageName)
                        )
                    )
                }
            })
            Spacer(Modifier.height(12.dp))
            PermissionRow(label = "Battery", granted = batteryOk, onFix = {
                runCatching {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + ctx.packageName)
                        )
                    )
                }.onFailure {
                    // Some OEM builds don't implement this action; fall back to the app's own
                    // battery-usage settings page.
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    )
                }
            })
            if (!notifsOn || !exactOn || !batteryOk) {
                Spacer(Modifier.height(8.dp))
                // v0.5.3 Phase 5 (§5.14) — shorter copy; only shown while a permission is actually off.
                Text(
                    (blockReason?.plus(". ") ?: "") +
                        "On some phones (e.g. Motorola) also set Daybook's battery usage to Unrestricted.",
                    style = DaybookText.Caption,
                    color = DaybookColors.Warning
                )
            }
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Habit check-in",
            subtitle = "One notification for all your batch habits. This time is stored on this phone and isn't synced."
        )
        FormGroup(title = null) {
            val checkinHhmm by viewModel.habitCheckinTime.collectAsState()
            var showTimePicker by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Check-in time",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DaybookColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                // v0.5.4 Phase 1 (S2) — wrap the button in a weighted Box (mirrors DateFieldRow),
                // so it fills its half instead of grabbing the whole row and collapsing the label.
                Box(Modifier.weight(1f)) {
                    GhostButton(
                        text = DateTimeUtils.formatTime(DateTimeUtils.stringToTime(checkinHhmm), clock24h),
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (showTimePicker) {
                TimePickerDialog(
                    initial = DateTimeUtils.stringToTime(checkinHhmm),
                    onDismiss = { showTimePicker = false },
                    onConfirm = { t -> viewModel.setHabitCheckinTime(t); showTimePicker = false },
                    force24h = clock24h
                )
            }
        }

        // ---- Quiet hours (rec 5) --------------------------------------------------------
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Quiet hours",
            subtitle = "Reminders due inside this window are held until it ends — nothing is dropped."
        )
        FormGroup(title = null) {
            val qhEnabled by viewModel.quietHoursEnabled.collectAsState()
            val qhStart by viewModel.quietStart.collectAsState()
            val qhEnd by viewModel.quietEnd.collectAsState()
            var showQhStart by remember { mutableStateOf(false) }
            var showQhEnd by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsToggleRow(
                    label = "Quiet hours",
                    checked = qhEnabled,
                    onCheckedChange = viewModel::setQuietHoursEnabled
                )
                QuietTimeRow("Start", DateTimeUtils.formatTime(DateTimeUtils.stringToTime(qhStart), clock24h), qhEnabled) { showQhStart = true }
                QuietTimeRow("End", DateTimeUtils.formatTime(DateTimeUtils.stringToTime(qhEnd), clock24h), qhEnabled) { showQhEnd = true }
            }
            if (showQhStart) {
                TimePickerDialog(
                    initial = DateTimeUtils.stringToTime(qhStart),
                    onDismiss = { showQhStart = false },
                    onConfirm = { t -> viewModel.setQuietStart(t); showQhStart = false },
                    force24h = clock24h
                )
            }
            if (showQhEnd) {
                TimePickerDialog(
                    initial = DateTimeUtils.stringToTime(qhEnd),
                    onDismiss = { showQhEnd = false },
                    onConfirm = { t -> viewModel.setQuietEnd(t); showQhEnd = false },
                    force24h = clock24h
                )
            }
        }

        // ---- Default snooze (rec 3 / N2) ----------------------------------------------
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader("Snooze")
        FormGroup(title = null) {
            val snoozeMin by viewModel.defaultSnoozeMinutes.collectAsState()
            Text(
                "New reminders start with this snooze interval. The batch check-in also uses it.",
                style = DaybookText.Caption,
                color = DaybookColors.TextMuted
            )
            Spacer(Modifier.height(8.dp))
            SnoozeStepper(minutes = snoozeMin, onChange = viewModel::setDefaultSnoozeMinutes)
        }

        // ---- In-app updates (Firebase App Distribution) --------------------------------
        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Updates",
            subtitle = "Checks Firebase App Distribution for a newer build each time you open the app."
        )
        FormGroup(title = null) {
            val checkForUpdatesEnabled by viewModel.checkForUpdatesEnabled.collectAsState()
            SettingsToggleRow(
                label = "Check for updates",
                checked = checkForUpdatesEnabled,
                onCheckedChange = viewModel::setCheckForUpdatesEnabled
            )
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader("Diagnostics")
        FormGroup(title = null) {
            Text(
                "Send a test notification to check the notification path on its own, " +
                    "without waiting for a reminder time.",
                style = DaybookText.Caption,
                color = DaybookColors.TextMuted
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = "Send test notification",
                onClick = { viewModel.sendTestNotification(); tick++ },
                modifier = Modifier.fillMaxWidth()
            )
            // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 15 (N-10): was a silent no-op on a blocked
            // channel/permission (a bare Log.w only) — now surfaced right under the button.
            val testNotificationResult by viewModel.testNotificationResult.collectAsState()
            testNotificationResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = DaybookText.Caption, color = DaybookColors.TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = "Re-arm all reminders",
                onClick = { viewModel.resyncReminders() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Data (export & import)                                                     */
/* ------------------------------------------------------------------------- */

@Composable
fun DataSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val exportResult by viewModel.exportResult.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    // v0.5.3 Phase 6 (D2) — (done, total) while a signed-in range export hydrates cloud months.
    val hydrateProgress by viewModel.hydrateProgress.collectAsState()

    // v0.5.3 Phase 6 (D2) — the chosen export span. Defaults to "this month so far".
    var startDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    if (showStartPicker) {
        DaybookDatePickerDialog(
            initial = startDate,
            // Bug fix: exporting a future date range makes no sense — no data exists yet for a
            // day that hasn't happened. Structurally unselectable, matching the Ongoing-habit
            // "Start" date picker's existing precedent.
            maxDate = LocalDate.now(),
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                startDate = picked
                if (endDate.isBefore(picked)) endDate = picked
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        DaybookDatePickerDialog(
            initial = endDate,
            maxDate = LocalDate.now(),
            onDismiss = { showEndPicker = false },
            onConfirm = { picked ->
                endDate = picked
                if (startDate.isAfter(picked)) startDate = picked
                showEndPicker = false
            }
        )
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFromUri(it) } }

    // v0.5.3 Phase 5 (§5.15) — confirm before the picker, since import replaces all current data.
    var confirmImport by remember { mutableStateOf(false) }
    if (confirmImport) {
        DaybookAlertDialog(
            onDismissRequest = { confirmImport = false },
            title = "Import a backup?",
            text = {
                Text(
                    "Importing replaces all current data on this phone with the backup's contents. " +
                        "This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DaybookColors.TextMuted
                )
            },
            confirmLabel = "Choose file",
            onConfirm = {
                confirmImport = false
                openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmImport = false },
            destructive = true
        )
    }

    SettingsSubScreen("Export & import", onNavigateBack) {
        // v0.5.3 Phase 6 (D2) — pick a start and end date, download a JSON of just that range.
        SectionHeader(
            "Export a date range",
            subtitle = "Downloads a JSON of just the days you pick. On a signed-in account, months " +
                "no longer stored on this phone are fetched from the cloud first."
        )
        FormGroup(title = null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.listGap)) {
                DateFieldRow(
                    label = "Start",
                    value = startDate.format(dateFmt),
                    enabled = !isExporting,
                    onClick = { showStartPicker = true }
                )
                DateFieldRow(
                    label = "End",
                    value = endDate.format(dateFmt),
                    enabled = !isExporting,
                    onClick = { showEndPicker = true }
                )

                val progress = hydrateProgress
                if (progress != null) {
                    val (done, total) = progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { if (total <= 0) 0f else done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                            color = LocalAccent.current
                        )
                        Text(
                            "Fetching $done/$total months from the cloud…",
                            style = DaybookText.Caption,
                            color = DaybookColors.TextMuted
                        )
                    }
                }

                val rangeValid = !startDate.isAfter(endDate)
                PrimaryButton(
                    text = if (isExporting) "Exporting…" else "Export range",
                    onClick = { viewModel.exportRange(startDate, endDate) },
                    enabled = !isExporting && rangeValid,
                    loading = isExporting
                )
                if (!rangeValid) {
                    Text(
                        "The start date must be on or before the end date.",
                        style = DaybookText.Caption,
                        color = DaybookColors.Warning
                    )
                }

                // v0.5.3 Phase 5 (§5.15) — a fixed-height result slot so feedback doesn't shift layout.
                Box(Modifier.fillMaxWidth().heightIn(min = 36.dp)) {
                    val msg = importResult ?: exportResult
                    if (msg != null) {
                        val ok = msg.startsWith("Exported ") || msg.startsWith("Import successful")
                        Text(
                            msg,
                            style = DaybookText.Caption,
                            color = if (ok) DaybookColors.Success else DaybookColors.Danger
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader(
            "Restore & share",
            subtitle = "Your data stays on this device. Import a JSON backup, or share your latest export."
        )
        FormGroup(title = null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.listGap)) {
                GhostButton(
                    text = if (isImporting) "Importing…" else "Import JSON",
                    onClick = { confirmImport = true },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                )
                GhostButton(
                    text = "Share backup…",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.shareLatestExport(
                            onNoFile = {},
                            onShare = { uri ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share backup"))
                            }
                        )
                    }
                )
            }
        }
    }
}

/** v0.5.3 Phase 6 (D2) — a labelled row whose trailing control opens a date picker. Mirrors the
 *  "Check-in time" row in Notifications settings. */
@Composable
private fun DateFieldRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = DaybookColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.weight(1f)) {
            GhostButton(
                text = value,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/* ------------------------------------------------------------------------- */

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
            // v0.5.3 Phase 5 (§5.14) — the status line uses the app-wide Metadata (labelSmall) slot.
            Text(
                if (granted) "Allowed" else "Not allowed",
                style = DaybookText.Metadata,
                color = if (granted) DaybookColors.Success else DaybookColors.Warning
            )
        }
        if (!granted) {
            Spacer(Modifier.width(12.dp))
            // v0.5.3 Phase 5 (§5.14) — a compact PrimaryButton (width-constrained by the Box), not
            // a bespoke accent pill (which was a 3rd button shape).
            Box(Modifier.width(84.dp)) {
                PrimaryButton(text = "Fix", onClick = onFix)
            }
        }
    }
}
