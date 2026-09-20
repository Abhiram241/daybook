package com.daybook.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
// `Icons.AutoMirrored.Filled.ExitToApp` needs this extension import in scope even when
// referenced fully-qualified at the call site — Kotlin resolves extension properties by import,
// not just by qualifier.
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daybook.app.R
import com.daybook.app.ui.components.BackHeader
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.SegmentSpec
import com.daybook.app.ui.components.SegmentedControl
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRow
import com.daybook.app.ui.components.SettingsRowDivider
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.components.Swatch
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.MuscleGroupLabels
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.FontChoice
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalIsDark
import com.daybook.app.ui.theme.Spacing
import com.daybook.app.ui.theme.DarkStyle
import com.daybook.app.ui.theme.LightStyle
import com.daybook.app.ui.theme.ThemeMode
import com.daybook.app.ui.components.StyleSwatchRow
import com.daybook.app.ui.workout.beast.BeastAccentColor

private val REST_OPTIONS = linkedMapOf(
    0 to "Off", 30 to "30s", 60 to "60s", 90 to "90s", 120 to "2m", 180 to "3m", 300 to "5m"
)

/** Settings makeover (2026-09-20) — Beast Mode Settings hub's 5 sub-screens. Implemented as
 *  in-composable state (not new NavHost routes) so system/predictive back inside a sub-screen
 *  just returns to the hub (see [BackHandler] below) without touching `MainActivity`'s nav graph —
 *  same "gear -> hub -> sub-screen" depth the plan calls for, lower-risk than adding 5 new routes
 *  for what is, mechanically, one screen's internal navigation. */
private enum class BeastSettingsSubScreen { APPEARANCE, WORKOUT_DEFAULTS, HEALTH_SYNC, DATA_SOURCES, IMPORT_BACKUP }

/**
 * A7 (§3.8.2) — reachable ONLY from the gear on Beast Mode's landing header. Route
 * `"workout_settings"`, stacked, no pill nav. Wrapped in the Beast accent like every other
 * workout route, so its switches/segmented control are Coral.
 *
 * Settings makeover (2026-09-20) — restructured from one long flat list into a hub (this
 * function's top-level content) + 5 sub-screens (below), per SETTINGS_MAKEOVER_2026-09-20.md.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutSettingsScreen(
    onNavigateBack: () -> Unit,
    onLeaveBeastMode: () -> Unit,
    onImportFromHevy: () -> Unit,
    // Item 2 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — "Fix exercise details" row below.
    onFixExerciseDetails: () -> Unit,
    viewModel: WorkoutSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val exercisesNeedingDetailsCount by viewModel.exercisesNeedingDetailsCount.collectAsStateWithLifecycle()
    val fontChoice by viewModel.fontChoice.collectAsStateWithLifecycle()
    val beastTheme by viewModel.beastTheme.collectAsStateWithLifecycle()
    val sleepCountDay by viewModel.sleepCountDay.collectAsStateWithLifecycle()
    var showSleepCountSheet by remember { mutableStateOf(false) }
    var showRestSheet by remember { mutableStateOf(false) }
    // Build 50 item 2 — resolves the persisted tone URI to a human title, same call a system
    // ringtone-picker settings row always makes; guarded, since an app that owned the tone got
    // uninstalled (or a synced/restored value from a different device) can leave a dangling URI.
    val ctxForTone = androidx.compose.ui.platform.LocalContext.current
    val restTimerSoundLabel = remember(settings.restTimerSoundUri) {
        val uri = settings.restTimerSoundUri?.let { android.net.Uri.parse(it) }
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        runCatching { android.media.RingtoneManager.getRingtone(ctxForTone, uri)?.getTitle(ctxForTone) }
            .getOrNull() ?: "Default"
    }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    val haptics = com.daybook.app.ui.theme.rememberDaybookHaptics()

    // Round B (§7.4) — Health Connect state + actions.
    val healthGrantedCount by viewModel.healthGrantedCount.collectAsStateWithLifecycle()
    val healthMissingLabels by viewModel.healthMissingLabels.collectAsStateWithLifecycle()
    val healthActionResult by viewModel.healthActionResult.collectAsStateWithLifecycle()
    val healthActionIsFailure by viewModel.healthActionIsFailure.collectAsStateWithLifecycle()
    val healthIsBusy by viewModel.healthIsBusy.collectAsStateWithLifecycle()
    val healthStatusLine by viewModel.healthStatusLine.collectAsStateWithLifecycle()
    var showWhichDataSheet by remember { mutableStateOf(false) }
    // Owner feedback round 3 (2026-09-20) — "Data source priority": ranked-list picker per data
    // point, each populated on demand when its own sheet opens (see the `LaunchedEffect`s below).
    val sourceCandidates by viewModel.sourceCandidates.collectAsStateWithLifecycle()
    var showHealthSourceStepsSheet by remember { mutableStateOf(false) }
    var showHealthSourceDistanceSheet by remember { mutableStateOf(false) }
    var showHealthSourceCaloriesSheet by remember { mutableStateOf(false) }
    var showHealthSourceHeartRateSheet by remember { mutableStateOf(false) }
    var showHealthSourceSpo2Sheet by remember { mutableStateOf(false) }
    var showHealthSourceHydrationSheet by remember { mutableStateOf(false) }
    var showHealthSourceWeightSheet by remember { mutableStateOf(false) }
    var showHealthSourceNutritionSheet by remember { mutableStateOf(false) }
    // Load each picker's option list only when its own sheet actually opens — the discovery read
    // it triggers is a real (if cheap) Health Connect call, not a value worth paying for up front.
    androidx.compose.runtime.LaunchedEffect(showHealthSourceStepsSheet) {
        if (showHealthSourceStepsSheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.STEPS)
    }
    androidx.compose.runtime.LaunchedEffect(showHealthSourceDistanceSheet) {
        if (showHealthSourceDistanceSheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.DISTANCE)
    }
    androidx.compose.runtime.LaunchedEffect(showHealthSourceCaloriesSheet) {
        if (showHealthSourceCaloriesSheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.CALORIES)
    }
    androidx.compose.runtime.LaunchedEffect(showHealthSourceHeartRateSheet) {
        if (showHealthSourceHeartRateSheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.HEART_RATE)
    }
    androidx.compose.runtime.LaunchedEffect(showHealthSourceSpo2Sheet) {
        if (showHealthSourceSpo2Sheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.SPO2)
    }
    androidx.compose.runtime.LaunchedEffect(showHealthSourceHydrationSheet) {
        if (showHealthSourceHydrationSheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.HYDRATION)
    }
    androidx.compose.runtime.LaunchedEffect(showHealthSourceWeightSheet) {
        if (showHealthSourceWeightSheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.WEIGHT)
    }
    androidx.compose.runtime.LaunchedEffect(showHealthSourceNutritionSheet) {
        if (showHealthSourceNutritionSheet) viewModel.loadSourceCandidates(com.daybook.app.data.health.HealthConnectReader.SourceField.NUTRITION)
    }
    val healthPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = viewModel.requestHealthPermissionsContract()
    ) { granted -> viewModel.onHealthPermissionFlowFinished(granted) }
    // H6 fix — the optional-extras consent sheet (history + background reads), launched once
    // ahead of "Import my past data" so the import can actually reach further than 30 days.
    val healthExtrasLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = viewModel.requestHealthPermissionsContract()
    ) { viewModel.onExtrasPermissionFlowFinished() }

    // Build 50 item 2 — the rest timer's alert tone, via the system ringtone picker.
    val ringtonePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(
                it, android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java
            )
        }
        if (uri != null) viewModel.setRestTimerSoundUri(uri.toString())
    }

    // B5a (§7.5.1) — Beast Mode JSON import.
    val beastImportResult by viewModel.beastImportResult.collectAsStateWithLifecycle()
    val isImportingBeast by viewModel.isImportingBeast.collectAsStateWithLifecycle()
    val openBeastDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBeastModeFromUri(it) } }
    var confirmBeastImport by remember { mutableStateOf(false) }
    if (confirmBeastImport) {
        com.daybook.app.ui.components.DaybookAlertDialog(
            onDismissRequest = { confirmBeastImport = false },
            title = "Import a Beast Mode backup?",
            text = {
                androidx.compose.material3.Text(
                    "Importing replaces your current gym history, routines and health data with the " +
                        "backup's contents. Your habits and intake logs are never touched. This can't be undone.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = com.daybook.app.ui.theme.DaybookColors.TextMuted
                )
            },
            confirmLabel = "Choose file",
            onConfirm = {
                confirmBeastImport = false
                openBeastDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmBeastImport = false },
            destructive = true
        )
    }

    var subScreen by remember { mutableStateOf<BeastSettingsSubScreen?>(null) }
    BackHandler(enabled = subScreen != null) { subScreen = null }

    Column(Modifier.fillMaxSize()) {
        when (subScreen) {
            null -> {
                BackHeader(title = "Beast Mode settings", onBack = onNavigateBack)
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
                    item {
                        SettingsGroup {
                            SettingsRow(
                                icon = DaybookIcons.Palette,
                                title = "Appearance",
                                subtitle = "Theme, colors, and font — just for Beast Mode",
                                onClick = { subScreen = BeastSettingsSubScreen.APPEARANCE }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = ImageVector.vectorResource(R.drawable.ic_workout),
                                title = "Workout defaults",
                                subtitle = "Units, rest timer, default exercise filter, and the Today card",
                                onClick = { subScreen = BeastSettingsSubScreen.WORKOUT_DEFAULTS }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Health Connect sync",
                                subtitle = "Connect, check status, and choose what syncs",
                                onClick = { subScreen = BeastSettingsSubScreen.HEALTH_SYNC }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.ImportExport,
                                title = "Data source priority",
                                subtitle = "Pick which app to trust when more than one shares the same kind of data",
                                onClick = { subScreen = BeastSettingsSubScreen.DATA_SOURCES }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.ImportExport,
                                title = "Import & backup",
                                subtitle = "Bring in workouts from Hevy or restore a Beast Mode backup",
                                onClick = { subScreen = BeastSettingsSubScreen.IMPORT_BACKUP }
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(Spacing.sectionGap))
                        SettingsGroup {
                            SettingsRow(
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                title = "Leave Beast Mode",
                                onClick = onLeaveBeastMode,
                                trailing = {}
                            )
                        }
                    }
                }
            }
            BeastSettingsSubScreen.APPEARANCE -> {
                BackHeader(title = "Appearance", onBack = { subScreen = null })
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
                    item { SectionHeader("Theme", subtitle = "Beast Mode can look different from the rest of the app.") }
                    item {
                        SettingsGroup {
                            Column(Modifier.padding(Spacing.cardInner)) {
                                SegmentedControl(
                                    options = listOf(
                                        SegmentSpec("APP", "Match app"),
                                        SegmentSpec("DARK", "Dark"),
                                        SegmentSpec("LIGHT", "Light"),
                                        SegmentSpec("SYSTEM", "System")
                                    ),
                                    selectedKey = beastTheme.themeMode?.storageKey ?: "APP",
                                    onSelect = { key ->
                                        viewModel.setBeastThemeMode(
                                            key.takeIf { it != "APP" }?.let { ThemeMode.fromKeyOrDefault(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(Spacing.listGap))
                        SectionHeader("Dark style", subtitle = "Background palette used when Beast Mode is dark.")
                        SettingsGroup {
                            Column(Modifier.padding(Spacing.cardInner)) {
                                StyleSwatchRow(
                                    DarkStyle.entries,
                                    current = beastTheme.darkStyle ?: DarkStyle.fromKeyOrDefault(settings.darkStyle),
                                    onPick = viewModel::setBeastDarkStyle
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(Spacing.listGap))
                        SectionHeader("Light style", subtitle = "Background palette used when Beast Mode is light.")
                        SettingsGroup {
                            Column(Modifier.padding(Spacing.cardInner)) {
                                StyleSwatchRow(
                                    LightStyle.entries,
                                    current = beastTheme.lightStyle ?: LightStyle.fromKeyOrDefault(settings.lightStyle),
                                    onPick = viewModel::setBeastLightStyle
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(Spacing.sectionGap))
                        SectionHeader("Accent & font", subtitle = "The color and typeface Beast Mode uses instead of the app's own.")
                    }
                    item {
                        SettingsGroup {
                            val isDark = LocalIsDark.current
                            val current = BeastAccentColor.fromKey(settings.workoutAccentColor)
                            SettingsRow(
                                icon = DaybookIcons.Palette,
                                title = "Beast Mode accent",
                                subtitle = "Re-tints the mode, its nav bar, and the session ring/glow.",
                                trailing = {}
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(start = 70.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BeastAccentColor.entries.forEach { a ->
                                    Swatch(
                                        color = a.colorFor(isDark),
                                        selected = a == current,
                                        onClick = { viewModel.setWorkoutAccentColor(a.storageKey) },
                                        contentDescription = a.name.lowercase().replaceFirstChar { it.uppercase() }
                                    )
                                }
                            }
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.MenuBook,
                                title = "Beast Mode font",
                                subtitle = fontChoice?.label ?: "Match app font",
                                onClick = { showFontSheet = true }
                            )
                        }
                    }
                }
            }
            BeastSettingsSubScreen.WORKOUT_DEFAULTS -> {
                BackHeader(title = "Workout defaults", onBack = { subScreen = null })
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
                    item { SectionHeader("Workout", subtitle = "These are only starting points — you can always change them per workout.") }
                    item {
                        SettingsGroup {
                            SettingsRow(
                                icon = ImageVector.vectorResource(R.drawable.ic_workout),
                                title = "Weight unit",
                                trailing = {
                                    SegmentedControl(
                                        options = listOf(SegmentSpec("KG", "kg"), SegmentSpec("LB", "lb")),
                                        selectedKey = settings.weightUnit,
                                        onSelect = viewModel::setWeightUnit,
                                        modifier = Modifier.width0(120.dp)
                                    )
                                }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Clock,
                                title = "Default rest timer",
                                subtitle = "How long the rest timer counts down by default. Change it any time from inside a workout.",
                                onClick = { showRestSheet = true },
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Clock,
                                title = "Rest timer sound",
                                subtitle = restTimerSoundLabel,
                                onClick = {
                                    val current = settings.restTimerSoundUri?.let { android.net.Uri.parse(it) }
                                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                                    ringtonePickerLauncher.launch(
                                        android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Rest timer sound")
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                                        }
                                    )
                                }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Category,
                                title = "Default exercise group",
                                subtitle = settings.defaultExerciseGroup
                                    ?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }
                                    ?.let { MuscleGroupLabels[it] }
                                    ?: "All",
                                onClick = { showGroupSheet = true }
                            )
                            if (exercisesNeedingDetailsCount > 0) {
                                SettingsRowDivider()
                                SettingsRow(
                                    icon = DaybookIcons.Category,
                                    title = "Fix exercise details",
                                    subtitle = "$exercisesNeedingDetailsCount " +
                                        (if (exercisesNeedingDetailsCount == 1) "exercise is" else "exercises are") +
                                        " missing a muscle group or equipment",
                                    onClick = onFixExerciseDetails
                                )
                                if (settings.exerciseDetailsPromptDismissed) {
                                    SettingsRowDivider()
                                    SettingsRow(
                                        icon = DaybookIcons.Category,
                                        title = "Remind me about missing details",
                                        subtitle = "Shows a reminder popup on Routines until every exercise has a muscle group and equipment set.",
                                        trailing = {
                                            Switch(
                                                checked = !settings.exerciseDetailsPromptDismissed,
                                                onCheckedChange = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.setExerciseDetailsPromptDismissed(!it)
                                                },
                                                colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(Spacing.sectionGap))
                        SectionHeader("Today screen", subtitle = "Controls what shows up on your Today screen.")
                    }
                    item {
                        SettingsGroup {
                            SettingsRow(
                                icon = Icons.Filled.DateRange,
                                title = "Show Beast Mode on Today",
                                subtitle = "Adds a card to your Today screen showing today's planned or logged workout.",
                                trailing = {
                                    Switch(
                                        checked = settings.workoutTodayCardEnabled,
                                        onCheckedChange = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setShowOnToday(it)
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            BeastSettingsSubScreen.HEALTH_SYNC -> {
                BackHeader(title = "Health Connect sync", onBack = { subScreen = null })
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
                    item {
                        SectionHeader(
                            "Health Connect",
                            subtitle = "Health Connect is Android's shared health data store — Daybook reads from it, and it never leaves your phone."
                        )
                    }
                    item {
                        val missingCount = healthMissingLabels.size
                        val connected = healthGrantedCount > 0
                        SettingsGroup {
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = if (connected) "Connected" else "Connect Health Connect",
                                subtitle = when {
                                    !connected -> "Daybook reads what you share here. It only writes your Hydration habit's water amount back, if you turn that on."
                                    missingCount > 0 -> "$missingCount type(s) not shared"
                                    else -> "All 12 types shared"
                                },
                                trailing = {
                                    if (connected) {
                                        val ctx = androidx.compose.ui.platform.LocalContext.current
                                        com.daybook.app.ui.components.GhostButton(
                                            text = "Disconnect",
                                            modifier = Modifier.width0(110.dp),
                                            onClick = {
                                                runCatching {
                                                    ctx.startActivity(
                                                        com.daybook.app.data.health.HealthConnectAvailability.manageDataIntent(ctx)
                                                    )
                                                }
                                            }
                                        )
                                    } else {
                                        com.daybook.app.ui.components.PrimaryButton(
                                            text = "Connect",
                                            onClick = { healthPermissionLauncher.launch(viewModel.initialHealthPermissionSet()) },
                                            modifier = Modifier.width0(120.dp)
                                        )
                                    }
                                }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Clock,
                                title = "Status",
                                subtitle = healthStatusLine ?: "Not connected yet",
                                trailing = {}
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.ImportExport,
                                title = if (healthIsBusy) "Refreshing…" else "Refresh now",
                                subtitle = "Pulls the latest steps, heart rate, and other data from Health Connect right now, instead of waiting for the next automatic sync.",
                                onClick = { if (!healthIsBusy) viewModel.refreshHealthNow() }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.ImportExport,
                                title = if (healthIsBusy) "Importing…" else "Import my past data",
                                subtitle = "Brings in up to a year of your past Health Connect history, not just what's synced since you connected.",
                                onClick = {
                                    if (!healthIsBusy) {
                                        if (viewModel.historyExtrasAlreadyRequested()) {
                                            viewModel.importHealthPastData()
                                        } else {
                                            healthExtrasLauncher.launch(viewModel.optionalHealthExtras())
                                        }
                                    }
                                }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Category,
                                title = "Which data is shared",
                                subtitle = "See which of the 12 health data types Daybook can read, and open Health Connect to change what's shared.",
                                onClick = { showWhichDataSheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Bedtime,
                                title = "Count sleep hours on",
                                subtitle = sleepCountDay.label,
                                onClick = { showSleepCountSheet = true }
                            )
                        }
                        if (healthActionResult != null) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(top = 6.dp)) {
                                androidx.compose.material3.Text(
                                    healthActionResult!!,
                                    style = com.daybook.app.ui.theme.DaybookText.Caption,
                                    color = if (healthActionIsFailure) com.daybook.app.ui.theme.DaybookColors.Danger
                                    else com.daybook.app.ui.theme.DaybookColors.Success
                                )
                            }
                        }
                    }
                }
            }
            BeastSettingsSubScreen.DATA_SOURCES -> {
                BackHeader(title = "Data source priority", onBack = { subScreen = null })
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
                    item {
                        SectionHeader(
                            "Data source priority",
                            subtitle = "When more than one app writes the same kind of data to Health Connect, " +
                                "Daybook uses just one of them instead of adding them together."
                        )
                    }
                    item {
                        SettingsGroup {
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Steps",
                                subtitle = rankedSubtitle(settings.healthSourceStepsRanked),
                                onClick = { showHealthSourceStepsSheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Distance",
                                subtitle = rankedSubtitle(settings.healthSourceDistanceRanked),
                                onClick = { showHealthSourceDistanceSheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Calories",
                                subtitle = rankedSubtitle(settings.healthSourceCaloriesRanked),
                                onClick = { showHealthSourceCaloriesSheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Heart rate",
                                subtitle = rankedSubtitle(settings.healthSourceHeartRateRanked),
                                onClick = { showHealthSourceHeartRateSheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "SpO2",
                                subtitle = rankedSubtitle(settings.healthSourceSpo2Ranked),
                                onClick = { showHealthSourceSpo2Sheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Hydration",
                                subtitle = rankedSubtitle(settings.healthSourceHydrationRanked),
                                onClick = { showHealthSourceHydrationSheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Weight",
                                subtitle = rankedSubtitle(settings.healthSourceWeightRanked),
                                onClick = { showHealthSourceWeightSheet = true }
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.Heart,
                                title = "Nutrition",
                                subtitle = rankedSubtitle(settings.healthSourceNutritionRanked),
                                onClick = { showHealthSourceNutritionSheet = true }
                            )
                        }
                        if (healthActionResult != null) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(top = 6.dp)) {
                                androidx.compose.material3.Text(
                                    healthActionResult!!,
                                    style = com.daybook.app.ui.theme.DaybookText.Caption,
                                    color = if (healthActionIsFailure) com.daybook.app.ui.theme.DaybookColors.Danger
                                    else com.daybook.app.ui.theme.DaybookColors.Success
                                )
                            }
                        }
                    }
                }
            }
            BeastSettingsSubScreen.IMPORT_BACKUP -> {
                BackHeader(title = "Import & backup", onBack = { subScreen = null })
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screenH)) {
                    item { SectionHeader("Import & backup") }
                    item {
                        SettingsGroup {
                            SettingsRow(
                                icon = DaybookIcons.ImportExport,
                                title = "Import from Hevy",
                                subtitle = "Bring your workout history over from a Hevy CSV export",
                                onClick = onImportFromHevy
                            )
                            SettingsRowDivider()
                            SettingsRow(
                                icon = DaybookIcons.ImportExport,
                                title = if (isImportingBeast) "Importing…" else "Import Beast Mode backup",
                                subtitle = "Restore gym history, routines and health data from a Beast Mode JSON file",
                                onClick = { if (!isImportingBeast) confirmBeastImport = true }
                            )
                        }
                        if (beastImportResult != null) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(top = 6.dp)) {
                                val msg = beastImportResult!!
                                val ok = msg.startsWith("Import successful")
                                androidx.compose.material3.Text(
                                    msg,
                                    style = com.daybook.app.ui.theme.DaybookText.Caption,
                                    color = if (ok) com.daybook.app.ui.theme.DaybookColors.Success else com.daybook.app.ui.theme.DaybookColors.Danger
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Round B (§7.4) — "Which data is shared": a read-only sheet listing the 12 MVP types with a
    // granted/not-shared indicator, plus a "Change what's shared" deep-link into the Health
    // Connect app's own settings (§6.2).
    if (showWhichDataSheet) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        com.daybook.app.ui.components.BottomSheetMenu(
            visible = true,
            onDismiss = { showWhichDataSheet = false },
            actions = com.daybook.app.data.health.HealthPermissions.LABELS.values.sorted().map { label ->
                val notShared = label in healthMissingLabels
                com.daybook.app.ui.components.SheetAction(
                    icon = DaybookIcons.Heart,
                    label = if (notShared) "$label — not shared" else label,
                    onClick = {}
                )
            } + com.daybook.app.ui.components.SheetAction(
                icon = DaybookIcons.ImportExport,
                label = "Change what's shared",
                onClick = {
                    runCatching {
                        ctx.startActivity(com.daybook.app.data.health.HealthConnectAvailability.settingsIntent())
                    }
                }
            )
        )
    }

    SortSheet(
        visible = showSleepCountSheet,
        onDismiss = { showSleepCountSheet = false },
        title = "Count sleep hours on",
        sortOptions = com.daybook.app.data.health.SleepCountDay.entries.map { SortOption(it.storageKey, it.label) },
        selectedSortKey = sleepCountDay.storageKey,
        onSelectSort = { viewModel.setSleepCountDay(com.daybook.app.data.health.SleepCountDay.fromKey(it)) },
        dismissOnSelect = true,
        neutralHeader = true
    )

    SortSheet(
        visible = showRestSheet,
        onDismiss = { showRestSheet = false },
        title = "Default rest timer",
        sortOptions = REST_OPTIONS.map { (k, v) -> SortOption(k.toString(), v) },
        selectedSortKey = settings.restTimerDefaultSeconds.toString(),
        onSelectSort = { viewModel.setRestTimerDefaultSeconds(it.toInt()) },
        dismissOnSelect = true,
        neutralHeader = true
    )

    // Feature addition (post-A6) — the Add-Exercise picker's default active filter chip.
    // "ALL" is a sentinel sort-sheet key (not a real MuscleGroup) mapped to `null` on write.
    SortSheet(
        visible = showGroupSheet,
        onDismiss = { showGroupSheet = false },
        title = "Default exercise group",
        sortOptions = listOf(SortOption("ALL", "All")) + MuscleGroup.entries.map { SortOption(it.name, MuscleGroupLabels[it].orEmpty()) },
        selectedSortKey = settings.defaultExerciseGroup ?: "ALL",
        onSelectSort = { key -> viewModel.setDefaultExerciseGroup(key.takeIf { it != "ALL" }) },
        dismissOnSelect = true,
        neutralHeader = true
    )

    // User request — Beast Mode's own font choice. "APP" is a sentinel sort-sheet key (not a
    // real FontChoice) mapped to `null` on write, meaning "match the app's own font choice".
    SortSheet(
        visible = showFontSheet,
        onDismiss = { showFontSheet = false },
        title = "Beast Mode font",
        sortOptions = listOf(SortOption("APP", "Match app font")) +
            FontChoice.entries.map { SortOption(it.storageKey, it.label) },
        selectedSortKey = fontChoice?.storageKey ?: "APP",
        onSelectSort = { key ->
            viewModel.setFontChoice(key.takeIf { it != "APP" }?.let { FontChoice.fromKeyOrDefault(it) })
        },
        dismissOnSelect = true,
        neutralHeader = true
    )

    // Owner feedback round 3 (2026-09-20) — "Data source priority": ranked-list picker per data
    // point. `available` unions the on-demand [sourceCandidates] discovery with whatever is
    // currently ranked, so an already-picked app that's fallen out of the discovery window
    // doesn't just vanish out from under the user.
    fun availableFor(field: com.daybook.app.data.health.HealthConnectReader.SourceField, ranked: List<String>): List<String> =
        ((sourceCandidates[field] ?: emptyList()) + ranked).distinct()

    RankedSourceSheet(
        visible = showHealthSourceStepsSheet,
        onDismiss = { showHealthSourceStepsSheet = false },
        title = "Steps source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.STEPS, parseRanked(settings.healthSourceStepsRanked)),
        ranked = parseRanked(settings.healthSourceStepsRanked),
        onRankedChange = { viewModel.setHealthSourceStepsRanked(it) }
    )
    RankedSourceSheet(
        visible = showHealthSourceDistanceSheet,
        onDismiss = { showHealthSourceDistanceSheet = false },
        title = "Distance source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.DISTANCE, parseRanked(settings.healthSourceDistanceRanked)),
        ranked = parseRanked(settings.healthSourceDistanceRanked),
        onRankedChange = { viewModel.setHealthSourceDistanceRanked(it) }
    )
    RankedSourceSheet(
        visible = showHealthSourceCaloriesSheet,
        onDismiss = { showHealthSourceCaloriesSheet = false },
        title = "Calories source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.CALORIES, parseRanked(settings.healthSourceCaloriesRanked)),
        ranked = parseRanked(settings.healthSourceCaloriesRanked),
        onRankedChange = { viewModel.setHealthSourceCaloriesRanked(it) }
    )
    RankedSourceSheet(
        visible = showHealthSourceHeartRateSheet,
        onDismiss = { showHealthSourceHeartRateSheet = false },
        title = "Heart rate source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.HEART_RATE, parseRanked(settings.healthSourceHeartRateRanked)),
        ranked = parseRanked(settings.healthSourceHeartRateRanked),
        onRankedChange = { viewModel.setHealthSourceHeartRateRanked(it) }
    )
    RankedSourceSheet(
        visible = showHealthSourceSpo2Sheet,
        onDismiss = { showHealthSourceSpo2Sheet = false },
        title = "SpO2 source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.SPO2, parseRanked(settings.healthSourceSpo2Ranked)),
        ranked = parseRanked(settings.healthSourceSpo2Ranked),
        onRankedChange = { viewModel.setHealthSourceSpo2Ranked(it) }
    )
    RankedSourceSheet(
        visible = showHealthSourceHydrationSheet,
        onDismiss = { showHealthSourceHydrationSheet = false },
        title = "Hydration source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.HYDRATION, parseRanked(settings.healthSourceHydrationRanked)),
        ranked = parseRanked(settings.healthSourceHydrationRanked),
        onRankedChange = { viewModel.setHealthSourceHydrationRanked(it) }
    )
    RankedSourceSheet(
        visible = showHealthSourceWeightSheet,
        onDismiss = { showHealthSourceWeightSheet = false },
        title = "Weight source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.WEIGHT, parseRanked(settings.healthSourceWeightRanked)),
        ranked = parseRanked(settings.healthSourceWeightRanked),
        onRankedChange = { viewModel.setHealthSourceWeightRanked(it) }
    )
    RankedSourceSheet(
        visible = showHealthSourceNutritionSheet,
        onDismiss = { showHealthSourceNutritionSheet = false },
        title = "Nutrition source",
        available = availableFor(com.daybook.app.data.health.HealthConnectReader.SourceField.NUTRITION, parseRanked(settings.healthSourceNutritionRanked)),
        ranked = parseRanked(settings.healthSourceNutritionRanked),
        onRankedChange = { viewModel.setHealthSourceNutritionRanked(it) }
    )
}

private fun parseRanked(csv: String?): List<String> = com.daybook.app.data.health.HealthSourcePriority.parseRanked(csv)

private fun rankedSubtitle(csv: String?): String {
    val ranked = parseRanked(csv)
    if (ranked.isEmpty()) return "Auto"
    return ranked.joinToString(" › ") { com.daybook.app.data.health.SourceAppLabels.labelFor(it) ?: it }
}

private fun Modifier.width0(w: androidx.compose.ui.unit.Dp) = this.width(w)
