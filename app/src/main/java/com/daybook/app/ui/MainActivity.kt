package com.daybook.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import com.daybook.app.ui.theme.Motion
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daybook.app.R
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.OccurrenceScheduler
import com.daybook.app.data.auth.AuthRepository
import com.daybook.app.data.auth.AuthState
import com.daybook.app.data.lock.AppLockRepository
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.DaybookScaffold
import com.daybook.app.ui.components.NavItemSpec
import com.daybook.app.ui.detail.DetailScreen
import com.daybook.app.ui.foodmed.AddFoodMedScreen
import com.daybook.app.ui.foodmed.EditFoodMedScreen
import com.daybook.app.ui.foodmed.FoodMedScreen
import com.daybook.app.ui.account.SignInGateScreen
import com.daybook.app.ui.home.HomeScreen
import com.daybook.app.ui.lock.AppLockSettingsScreen
import com.daybook.app.ui.lock.LockScreen
import com.daybook.app.ui.onboarding.OnboardingScreen
import com.daybook.app.ui.onboarding.OnboardingViewModel
import com.daybook.app.ui.routines.AddHabitScreen
import com.daybook.app.ui.routines.EditHabitScreen
import com.daybook.app.ui.routines.RoutinesScreen
import com.daybook.app.ui.settings.AppearanceSettingsScreen
import com.daybook.app.ui.settings.DataSettingsScreen
import com.daybook.app.ui.settings.NotificationSettingsScreen
import com.daybook.app.ui.settings.SettingsScreen
import com.daybook.app.ui.settings.TodayCalendarSettingsScreen
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.DaybookTheme
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalOnAccent
import com.daybook.app.ui.theme.onAccentInk
import com.daybook.app.ui.workout.WorkoutRoutes
import com.daybook.app.ui.workout.AddExerciseScreen
import com.daybook.app.ui.workout.ExercisePickerMode
import com.daybook.app.ui.workout.ExerciseFormScreen
import com.daybook.app.ui.workout.RoutineEditScreen
import com.daybook.app.ui.workout.WorkoutDetailScreen
import com.daybook.app.ui.workout.WorkoutHistoryScreen
import com.daybook.app.ui.workout.WorkoutHomeScreen
import com.daybook.app.ui.workout.WorkoutSessionScreen
import com.daybook.app.ui.workout.WorkoutSettingsScreen
import com.daybook.app.ui.components.NavCoachMark
import com.daybook.app.util.notification.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v0.5.1 §K: extends `FragmentActivity`, not `ComponentActivity`. `BiometricPrompt`'s constructor
 * takes a `FragmentActivity`, and `ComponentActivity` is its superclass, so nothing else moves:
 * `@AndroidEntryPoint` and `by viewModels()` still work (Hilt supports both), `setContent` still
 * works (it is an extension on `ComponentActivity`), `onNewIntent` / the notification deep link are
 * unchanged, and `WindowCompat.setDecorFitsSystemWindows` / insets behave identically. The only new
 * plumbing is a `FragmentManager`, which the biometric prompt needs and nothing else touches.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    @Inject lateinit var occurrenceScheduler: OccurrenceScheduler
    @Inject lateinit var appSettingsRepository: AppSettingsRepository
    @Inject lateinit var cloudSyncRepository: com.daybook.app.data.sync.CloudSyncRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var appLockRepository: AppLockRepository
    @Inject lateinit var workoutFontPrefs: com.daybook.app.data.workout.WorkoutFontPrefs

    /** (occurrenceId, isHabit) from a tapped notification, consumed once by [MainApp]. */
    private val deepLinkOccurrence = MutableStateFlow<Pair<String, Boolean>?>(null)

    /** UX overhaul item 1 — set by onboarding's "Create your first habit" link; [MainApp]
     *  navigates to Add Habit once it mounts, then clears it. */
    private val pendingOpenAddHabit = MutableStateFlow(false)

    /**
     * Bug fix (post-A6) — the Add-Exercise picker's result, for whichever screen navigated to it
     * (the live session or the routine editor). §3.6.6's original design routed this through the
     * PREVIOUS `NavBackStackEntry`'s own `SavedStateHandle`, observed from inside each consumer's
     * `ViewModel` — on-device testing found that path unreliable (tapping a row popped back to
     * the session with nothing added: the write to `previousBackStackEntry?.savedStateHandle`
     * was not reaching the still-alive `WorkoutSessionViewModel`'s collector in practice, most
     * likely a `NavBackStackEntry` timing/identity subtlety around an immediate
     * set-then-`popBackStack()`). Replaced with the exact same proven mechanism this file already
     * uses for the notification deep link (`deepLinkOccurrence` above): a plain
     * `MutableStateFlow` on the Activity, set by the picker and consumed by a `LaunchedEffect` in
     * whichever destination is current after the pop — no `NavBackStackEntry` indirection at all.
     */
    private val pickedExerciseId = MutableStateFlow<List<String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UX overhaul item 4 — pick the Activity window background synchronously (before
        // setContent) from the theme_mode SharedPreferences mirror, so the pre-inflate splash
        // matches the chosen theme and there is no dark/light flash on cold start.
        applyWindowTheme()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        readDeepLink(intent)

        // Ask for the display's highest refresh rate as a SOFT hint, but only when it's worth it.
        // `preferredRefreshRate` (unlike the old `preferredDisplayModeId`) does not pin the panel
        // to one mode for the whole session — the system stays free to drop the panel to a lower
        // rate for static / idle frames (LTPO down-clocking), which is what keeps the device from
        // running warm while the app is merely foregrounded and not animating. Compose still
        // requests the panel's high rate on its own during scroll / fling / animation.
        runCatching {
            val best = display?.supportedModes?.maxByOrNull { it.refreshRate }
            if (best != null && best.refreshRate > 90f) {
                window.attributes = window.attributes.apply { preferredRefreshRate = best.refreshRate }
            }
        }

        // Regenerate the rolling occurrence window and re-arm alarms once the window is at
        // least RESUMED, so it doesn't contend with cold-start work. Runs once per launch.
        lifecycleScope.launch {
            var done = false
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!done) {
                    done = true
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        runCatching { occurrenceScheduler.syncAll() }
                    }
                }
            }
        }

        setContent {
            var showRationale by remember { mutableStateOf(false) }
            // True once the runtime prompt can no longer be shown (permanently denied): the
            // system launcher would return "denied" instantly with no dialog, so we point the
            // user at app notification settings instead of a silent no-op (REV-18).
            var permanentlyDenied by remember { mutableStateOf(false) }
            // The exact-alarm ask is sequenced *after* the notification decision settles so the
            // two system dialogs never stack (Section 2).
            var notifFlowResolved by remember { mutableStateOf(false) }
            var showExactAlarmDialog by remember { mutableStateOf(false) }
            val notifPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                notifFlowResolved = true
                if (granted) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching { occurrenceScheduler.syncAll() }
                    }
                }
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    val alreadyAsked = runCatching {
                        appSettingsRepository.getSettings().notifPermissionAsked
                    }.getOrDefault(false)
                    val canPrompt =
                        shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    when {
                        !alreadyAsked -> {
                            runCatching { appSettingsRepository.setNotifPermissionAsked(true) }
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        canPrompt -> {
                            permanentlyDenied = false
                            showRationale = true
                        }
                        else -> {
                            permanentlyDenied = true
                            showRationale = true
                        }
                    }
                } else {
                    // Nothing to ask (granted already, or pre-API-33) — go straight to the alarm check.
                    notifFlowResolved = true
                }
            }
            if (showRationale) {
                // v0.5.3 Phase 4 (§4.5) — routed through the shared DaybookAlertDialog shell.
                DaybookAlertDialog(
                    onDismissRequest = { showRationale = false; notifFlowResolved = true },
                    title = "Turn on notifications",
                    text = {
                        Text(
                            if (permanentlyDenied)
                                "Notifications are turned off for Daybook, so reminders can't alert you at all. " +
                                    "Open settings and allow notifications, then your set times will start coming through."
                            else
                                "Daybook reminds you at the times you set, and it needs notification access to do that. " +
                                    "Without it, reminders are silent. You can change this later in Settings.",
                            style = DaybookText.CardSubtitle,
                            color = DaybookColors.TextPrimary
                        )
                    },
                    confirmLabel = if (permanentlyDenied) "Open settings" else "Allow",
                    onConfirm = {
                        showRationale = false
                        if (permanentlyDenied) {
                            notifFlowResolved = true
                            startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            )
                        } else {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    dismissLabel = "Not now",
                    onDismiss = { showRationale = false; notifFlowResolved = true }
                )
            }

            // Exact-alarm ask — once the notification flow is resolved, check the Alarms &
            // reminders permission (API 31+). Asked once ever, tracked in SharedPreferences so the
            // v7 migration stays limited to font_choice; the Settings row covers re-granting.
            LaunchedEffect(notifFlowResolved) {
                if (!notifFlowResolved) return@LaunchedEffect
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (am.canScheduleExactAlarms()) return@LaunchedEffect
                val prefs = getSharedPreferences("daybook_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean(KEY_ALARM_PERMISSION_ASKED, false)) return@LaunchedEffect
                prefs.edit().putBoolean(KEY_ALARM_PERMISSION_ASKED, true).apply()
                showExactAlarmDialog = true
            }
            if (showExactAlarmDialog) {
                // v0.5.3 Phase 4 (§4.5) — routed through the shared DaybookAlertDialog shell.
                DaybookAlertDialog(
                    onDismissRequest = { showExactAlarmDialog = false },
                    title = "Allow exact alarms",
                    text = {
                        Text(
                            "Without this, reminders can still arrive but the system may batch them and fire them late. " +
                                "Tap Allow, then turn on ‘Alarms & reminders’ for Daybook.",
                            style = DaybookText.CardSubtitle,
                            color = DaybookColors.TextPrimary
                        )
                    },
                    confirmLabel = "Allow",
                    onConfirm = {
                        showExactAlarmDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            }
                        }
                    },
                    dismissLabel = "Not now",
                    onDismiss = { showExactAlarmDialog = false }
                )
            }

            val accent by onboardingViewModel.accentColor.collectAsStateWithLifecycle()
            val fontChoice by onboardingViewModel.fontChoice.collectAsStateWithLifecycle()
            val reduceMotion by onboardingViewModel.reduceMotion.collectAsStateWithLifecycle()
            val themeMode by onboardingViewModel.themeMode.collectAsStateWithLifecycle()
            val darkStyle by onboardingViewModel.darkStyle.collectAsStateWithLifecycle()
            val lightStyle by onboardingViewModel.lightStyle.collectAsStateWithLifecycle()
            val cornerScale by onboardingViewModel.cornerScale.collectAsStateWithLifecycle()
            DaybookTheme(
                accent = accent,
                fontChoice = fontChoice,
                themeMode = themeMode,
                darkStyle = darkStyle,
                lightStyle = lightStyle,
                cornerScale = cornerScale,
                reduceMotion = reduceMotion
            ) {
                val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsStateWithLifecycle()
                val locked by appLockRepository.isLocked.collectAsStateWithLifecycle()
                val authState by authRepository.state.collectAsStateWithLifecycle()

                // v0.5.1 §D + §K — the four-stage launch gate, outermost first:
                //
                //   1. LOCK       isLocked          -> LockScreen, over everything including the
                //                                     sign-in form (a locked device must not expose
                //                                     the account email), and still first for a user
                //                                     who enabled the lock and then signed out.
                //   2. AUTH       Loading           -> neutral splash
                //                 !is SignedIn      -> blocking sign-in, no back, no skip
                //   3. ONBOARDING null              -> neutral splash
                //                 false             -> OnboardingScreen (name step + tour wizard,
                //                                     LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 3/D2)
                //   4. APP                          -> MainApp()
                //
                // Auth before onboarding because onboarding pre-fills the name from displayName,
                // which only exists once signed in.
                //
                // v0.5.2: Google is the only sign-in method. The gate passes on plain `SignedIn`,
                // full stop, and sync is enabled for every signed-in user (a fresh Google sign-in
                // keeps the same uid, so no cloud data is stranded by the email/password removal).
                //
                // L1's rule ("never route from an unsettled snapshot") now covers three flows, not
                // one: every null/Loading state renders the SAME neutral splash. Getting this wrong
                // reintroduces the onboarding-screen flash on every launch.
                when {
                    locked -> LockScreen(activity = this@MainActivity)
                    authState is AuthState.Loading ->
                        Box(Modifier.fillMaxSize().background(DaybookColors.Bg))
                    authState !is AuthState.SignedIn -> SignInGateScreen()
                    onboardingCompleted == null ->
                        Box(Modifier.fillMaxSize().background(DaybookColors.Bg))
                    onboardingCompleted == false -> {
                        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 3 (D2) — show the tour wizard to
                        // EVERY first login, not just the ones with no derivable name. A name
                        // silently derivable from the Google profile (`derived != null`) skips
                        // straight to the tour-only step list (no NameAsk); otherwise the wizard
                        // opens on the name field. Either way `completeOnboarding` fires exactly
                        // once, now at the end of the wizard (Skip, or Next on the last step)
                        // instead of immediately behind a blank splash.
                        val derived = com.daybook.app.ui.onboarding.deriveOnboardingName(
                            (authState as? AuthState.SignedIn)?.displayName,
                            restoredUserName = null   // sub-decision (c)
                        )
                        LaunchedEffect(derived) { onboardingViewModel.configure(derived) }
                        OnboardingScreen(
                            viewModel = onboardingViewModel,
                            onOpenAddHabit = { pendingOpenAddHabit.value = true },
                            onAllowNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onAllowExactAlarms = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    runCatching {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:$packageName")
                                            )
                                        )
                                    }
                                }
                            },
                            onAllowBattery = {
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                }
                            }
                        )
                    }
                    else -> MainApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeepLink(intent)
    }

    override fun onStop() {
        super.onStop()
        // v0.5.1 §K: stamp the background time before anything else can fail. Persisted, so a
        // process death between here and onResume still locks correctly.
        runCatching { appLockRepository.onAppBackgrounded() }
        // The moment the user is most likely to leave — flush any pending cloud push immediately
        // instead of waiting out the 3 s debounce (FIREBASE_0.5_PLAN.md §4). Failure-inert.
        runCatching { cloudSyncRepository.onAppStop() }
    }

    override fun onResume() {
        super.onResume()
        // v0.5.1 §K: re-arm the lock if the app sat in the background longer than the timeout.
        // onResume runs before setContent's recomposition on a warm resume, so the flag is already
        // correct by the time the gate composable reads it.
        runCatching { appLockRepository.onAppForegrounded() }
        // Accent-updates round (Phase 6, SD-8) — release builds only. `debug` builds are already
        // shipped unminified/debuggable for a different reason (crash debugging), and gating this
        // there too would just be noise; a release build is what actually gets sideloaded via
        // App Distribution to testers.
        // "Check for updates" toggle round — only actually call the SDK when the user hasn't
        // opted out (either manually in Settings, or automatically after declining the SDK's own
        // "Enable testing features" sign-in prompt once — see InAppUpdateChecker).
        if (!com.daybook.app.BuildConfig.DEBUG) {
            lifecycleScope.launch {
                val enabled = runCatching { appSettingsRepository.getSettings().checkForUpdatesEnabled }.getOrDefault(true)
                if (enabled) {
                    runCatching {
                        com.daybook.app.util.update.InAppUpdateChecker.checkForUpdate(this@MainActivity) {
                            lifecycleScope.launch {
                                runCatching { appSettingsRepository.setCheckForUpdatesEnabled(false) }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * UX overhaul item 4 — sync theme pick from the SharedPreferences mirror. UX refinement
     * round — the pre-inflate splash background must match the chosen *style*, not just
     * dark/light: resolve the style ground colour from the same mirror and paint the window
     * background with it (no per-style `windowBackground` resources — that would be
     * combinatorial). `setTheme` still picks the Dark/Light base theme so the status-bar icon
     * polarity stays correct.
     */
    private fun applyWindowTheme() {
        val dark = when (com.daybook.app.data.ThemePrefs.read(this)) {
            "LIGHT" -> false
            "SYSTEM" -> (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            else -> true
        }
        val groundBg =
            if (dark) {
                com.daybook.app.ui.theme.DarkStyle.fromKeyOrDefault(
                    com.daybook.app.data.ThemePrefs.readDarkStyle(this)
                ).ground.bg
            } else {
                com.daybook.app.ui.theme.LightStyle.fromKeyOrDefault(
                    com.daybook.app.data.ThemePrefs.readLightStyle(this)
                ).ground.bg
            }
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(groundBg.toArgb())
        )
        setTheme(if (dark) R.style.Theme_Daybook_Dark else R.style.Theme_Daybook_Light)
    }

    private fun readDeepLink(intent: Intent?) {
        val occId = intent?.getStringExtra(NotificationUtils.EXTRA_OPEN_OCCURRENCE_ID) ?: return
        val isHabit = intent.getBooleanExtra(NotificationUtils.EXTRA_OPEN_IS_HABIT, false)
        deepLinkOccurrence.value = occId to isHabit
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun MainApp() {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        val reduceMotion = com.daybook.app.ui.theme.LocalReduceMotion.current
        // rec 7 (SD-2) — the bottom-nav tabs are configurable now: default landing tab + hide tabs
        // (NOT reorder). Today ("home") is always present and always first, preserving the
        // "index 0 == Today" invariant BackHandler + deep-link fallbacks rely on.
        val navTabsCsv by onboardingViewModel.navTabs.collectAsStateWithLifecycle()
        val defaultLandingTab by onboardingViewModel.defaultLandingTab.collectAsStateWithLifecycle()
        val visibleRoutes = remember(navTabsCsv) { com.daybook.app.ui.NavConfig.visibleRoutesFrom(navTabsCsv) }
        // The top-level tabs live in one HorizontalPager (swipe between them + tab-order-aware
        // directional slide come for free). Detail/Add/Edit/Settings stay stacked over "main".
        val pagerState = rememberPagerState(
            initialPage = com.daybook.app.ui.NavConfig.landingIndex(defaultLandingTab, visibleRoutes),
            pageCount = { visibleRoutes.size }
        )

        // UX overhaul item 1 — onboarding's "Create your first habit" link opens Add Habit once
        // the app is actually mounted (the nav graph doesn't exist during the onboarding gate).
        val pendingAddHabit by pendingOpenAddHabit.collectAsStateWithLifecycle()
        LaunchedEffect(pendingAddHabit) {
            if (pendingAddHabit) {
                pendingOpenAddHabit.value = false
                navController.navigate("add_habit")
            }
        }

        // Route a tapped reminder notification to its detail screen (REV-07).
        val pendingDeepLink by deepLinkOccurrence.collectAsStateWithLifecycle()
        LaunchedEffect(pendingDeepLink) {
            val (occId, isHabit) = pendingDeepLink ?: return@LaunchedEffect
            val isJournal = !isHabit && kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { occurrenceScheduler.isJournalOccurrence(occId) }.getOrDefault(false)
            }
            if (isJournal) {
                deepLinkOccurrence.value = null
                navController.navigate("journal/$occId/0") { launchSingleTop = true }
                return@LaunchedEffect
            }
            // Journal-as-habit round: the habit-side counterpart. A tapped notification always
            // targets a still-PENDING occurrence (a resolved one has no armed alarm/notification to
            // tap), so this always opens the chat, never the edit-form — B8's PENDING-vs-not routing
            // rule only matters for the Home/Detail tap-in-app paths below.
            val isHabitJournal = isHabit && kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { occurrenceScheduler.isHabitJournalOccurrence(occId) }.getOrDefault(false)
            }
            if (isHabitJournal) {
                deepLinkOccurrence.value = null
                navController.navigate("habit_journal_chat/$occId/0") { launchSingleTop = true }
                return@LaunchedEffect
            }
            deepLinkOccurrence.value = null
            navController.navigate("respond/$occId?isHabit=$isHabit") { launchSingleTop = true }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val backStackRoute = navBackStackEntry?.destination?.route
        val onMain = backStackRoute == null || backStackRoute == "main"
        // L2: read the *settled* page index only. currentPage already ignores the drag offset;
        // wrapping it in derivedStateOf keeps this scope from invalidating on anything else the
        // pager mutates mid-drag, so the scaffold/nav recompose once per swipe, not per frame.
        val settledPage by remember { derivedStateOf { pagerState.currentPage } }
        // The bottom nav still keys off a route string; on "main" it tracks the pager page.
        // Guard the index during a recomposition where `visibleRoutes` just shrank.
        val currentRoute = if (onMain) visibleRoutes.getOrElse(settledPage) { visibleRoutes.first() }
            else backStackRoute

        // vectorResource() is @Composable — it can't run inside the remember{} lambda (plain
        // function body, not a composable scope). Hoist the three reads, then key remember on
        // them so the list keeps stable identity (DaybookScaffold recompose guard, L2 comments).
        val homeIcon: ImageVector = ImageVector.vectorResource(R.drawable.ic_nav_home)
        val habitsIcon: ImageVector = ImageVector.vectorResource(R.drawable.ic_nav_habits)
        val intakeIcon: ImageVector = ImageVector.vectorResource(R.drawable.ic_nav_intake)
        val navItems = remember(visibleRoutes, homeIcon, habitsIcon, intakeIcon) {
            val specByRoute = mapOf(
                "home" to NavItemSpec("home", homeIcon, "Today"),
                "routines" to NavItemSpec("routines", habitsIcon, "Habits"),
                "foodmed" to NavItemSpec("foodmed", intakeIcon, "Intake")
            )
            visibleRoutes.mapNotNull { specByRoute[it] }
        }

        // L3: programmatic page changes (a nav-bar tap, system back) snap when they would skip
        // a page. PagerState.animateScrollToPage only teleports past the pages in between when
        // the distance is >= 3 (foundation 1.6.4 compares abs(target - firstVisible) to 3 before
        // calling snapToItem), so a 0 -> 2 jump animates *through* page 1 — and with
        // beyondBoundsPageCount = 0 that forces RoutinesScreen to compose cold mid-fling and
        // FoodMedScreen to compose cold on arrival: two full-screen cold compositions inside one
        // animation, which is the stutter. Snapping composes the destination only. Adjacent moves
        // keep the slide (they compose one page either way), and dragging is untouched.
        // v0.5.1 §O (Option C): every nav-bar tap snaps. The non-adjacent snap above is extended
        // to adjacent taps too — a tab tap is a destination change, not a gesture, so it should
        // not travel. Dragging is untouched: HorizontalPager's own drag/fling still slides, and
        // the nav pill's own highlight animation is inside the nav bar, unaffected by this.
        val goToPage: (Int) -> Unit = remember(pagerState, scope) {
            { idx -> scope.launch { pagerState.scrollToPage(idx) }; Unit }
        }
        // L2: stable callbacks. Allocated fresh on every recomposition these invalidated each
        // pager page (and DaybookScaffold) whenever anything above them recomposed.
        val onSelectRoute: (String) -> Unit = remember(visibleRoutes, goToPage) {
            { route -> goToPage(visibleRoutes.indexOf(route).coerceAtLeast(0)) }
        }
        val goDetail: (String, String) -> Unit = remember(navController) {
            { itemType, itemId -> navController.navigate("detail/$itemType/$itemId") }
        }
        val goJournal: (String) -> Unit = remember(navController) {
            { occId -> navController.navigate("journal/$occId/0") }
        }
        val goJournalBackfill: (String, Long) -> Unit = remember(navController) {
            { taskId, slot -> navController.navigate("journal/$taskId/$slot") }
        }
        // Journal-as-habit round: the habit-side counterparts of the three FoodMed journal
        // callbacks above, plus the dedicated (non-chat) edit-form route (B8).
        val goHabitJournalChat: (String) -> Unit = remember(navController) {
            { occId -> navController.navigate("habit_journal_chat/$occId/0") }
        }
        val goHabitJournalBackfill: (String, Long) -> Unit = remember(navController) {
            { habitId, slot -> navController.navigate("habit_journal_chat/$habitId/$slot") }
        }
        val goHabitJournalEdit: (String) -> Unit = remember(navController) {
            { occId -> navController.navigate("habit_journal_edit/$occId") }
        }
        // Journal Mode: edit a resolved (Logged) intake entry straight from the Today card — the
        // same editable RespondScreen the Detail→Activity list opens. Journal entries route through
        // goJournal instead (HomeScreen picks the branch on item.isJournal).
        val goEntryEdit: (String) -> Unit = remember(navController) {
            { occId -> navController.navigate("respond/$occId?isHabit=false") }
        }
        // v0.5.3 item 6: the only entry point to "respond/…" is the notification deep link, which
        // navigates inline in the LaunchedEffect above — no screen-level callback is needed here.
        val goSettings: () -> Unit = remember(navController) {
            { navController.navigate("settings") { launchSingleTop = true } }
        }
        val goAddHabit: () -> Unit = remember(navController) { { navController.navigate("add_habit") } }
        val goEditHabit: (String) -> Unit = remember(navController) {
            { habitId -> navController.navigate("edit_habit/$habitId") }
        }
        val goAddFoodMed: () -> Unit = remember(navController) { { navController.navigate("add_foodmed") } }
        val goEditFoodMed: (String) -> Unit = remember(navController) {
            { taskId -> navController.navigate("edit_foodmed/$taskId") }
        }

        // ------------------------------------------------------------------ A5: Beast Mode shell
        // (§3.6) — long-press "Today" to enter, long-press Beast Mode's own leftmost nav item to
        // leave. Eight stacked `composable(...)` destinations, siblings of "main" — not a nested
        // `navigation(...)` graph and not a fourth page of "main"'s own pager (§3.6.0/§3.6.6). Item
        // 3 (Workout UI fixes plan, LOCKED — PRIORITY): Routines/History/Exercises are no longer
        // three of those destinations — they're pages of their OWN `HorizontalPager`, hosted
        // inside the single HOME destination (see `beastPagerState` below and the `composable
        // (WorkoutRoutes.HOME)` block further down).
        val appSettings by appSettingsRepository.observeSettings()
            .collectAsStateWithLifecycle(initialValue = com.daybook.app.data.model.AppSettings())
        val workoutAccent = remember(appSettings.workoutAccentColor) {
            com.daybook.app.ui.workout.beast.BeastAccentColor.fromKey(appSettings.workoutAccentColor)
        }
        // A5 (§3.6.3) — the coach-mark's lifecycle: dismissed (and workout_hint_state advanced)
        // by tapping "Got it" (-> 1, handled in the coachMark slot below), performing the
        // long-press (-> 2, handled in onLongSelect below), or navigating away from `main` (-> 1).
        LaunchedEffect(onMain, appSettings.workoutHintState) {
            if (!onMain && appSettings.workoutHintState == 0) {
                runCatching { appSettingsRepository.setWorkoutHintState(1) }
            }
        }

        // Item 3 (Workout UI fixes plan, LOCKED — PRIORITY) — Beast Mode's 3 tabs (Routines/
        // History/Exercises, `WorkoutRoutes.NAV`) rebuilt as pages of one `HorizontalPager`,
        // exactly the same mechanism as `"main"`'s own pager above: physically swipeable, one
        // shared back-stack entry (`WorkoutRoutes.HOME`, the only one of the three still a real
        // NavHost destination — HISTORY/LIBRARY are now just pager pages, not destinations) so
        // the 3 screens' ViewModels co-instantiate for instant switching, and `goToBeastPage`
        // mirrors `goToPage` (a plain `scrollToPage` — every nav-bar tap snaps, per §O below;
        // dragging is the pager's own gesture and still slides).
        val beastPagerState = rememberPagerState(initialPage = 0, pageCount = { WorkoutRoutes.NAV.size })
        val beastSettledPage by remember { derivedStateOf { beastPagerState.currentPage } }
        val goToBeastPage: (Int) -> Unit = remember(beastPagerState, scope) {
            { idx -> scope.launch { beastPagerState.scrollToPage(idx) }; Unit }
        }
        val onSelectBeastRoute: (String) -> Unit = remember(goToBeastPage) {
            { route -> goToBeastPage(WorkoutRoutes.NAV.indexOf(route).coerceAtLeast(0)) }
        }
        val goWorkout: () -> Unit = remember(navController, goToBeastPage) {
            {
                // Always land on Routines for a fresh entry into Beast Mode (long-press "Today",
                // or Settings -> "Beast Mode") — mirrors `exitBeastMode`'s own goToPage(0) below,
                // and matches today's behaviour where `goWorkout` always opened the HOME
                // destination specifically. `beastPagerState` otherwise persists its page across
                // stacked child destinations (Settings, a routine, a session) the same way
                // `pagerState` persists across "main"'s own child destinations.
                goToBeastPage(0)
                navController.navigate(WorkoutRoutes.HOME) { launchSingleTop = true }
            }
        }
        // A5 (§3.6.8) — popBackStack(to "main") clears the whole workout stack in one pop,
        // however deep the user was; goToPage(0) lands on TODAY specifically, since the gesture
        // began on Today.
        val exitBeastMode: () -> Unit = remember(navController, goToPage) {
            {
                navController.popBackStack(route = "main", inclusive = false)
                goToPage(0)
            }
        }
        val goWorkoutSettings: () -> Unit = remember(navController) {
            { navController.navigate(WorkoutRoutes.SETTINGS) }
        }
        val goWorkoutSession: (String) -> Unit = remember(navController) {
            { id -> navController.navigate(WorkoutRoutes.session(id)) { launchSingleTop = true } }
        }
        val goWorkoutDetail: (String) -> Unit = remember(navController) {
            { id -> navController.navigate(WorkoutRoutes.detail(id)) }
        }
        val goRoutineEdit: (String?) -> Unit = remember(navController) {
            { id -> navController.navigate(WorkoutRoutes.routineEdit(id)) }
        }
        val goPickExercise: () -> Unit = remember(navController) {
            { navController.navigate(WorkoutRoutes.PICK_EXERCISE) }
        }
        val goNewExercise: () -> Unit = remember(navController) {
            { navController.navigate(WorkoutRoutes.NEW_EXERCISE) }
        }
        val goEditExercise: (String) -> Unit = remember(navController) {
            { id -> navController.navigate(WorkoutRoutes.editExercise(id)) }
        }
        val goSettingsData: () -> Unit = remember(navController) {
            { navController.navigate("settings_data") { launchSingleTop = true } }
        }

        // Item 3 — HISTORY/LIBRARY are no longer separate NavHost destinations (they're pager
        // pages inside the single HOME entry), so "in the Beast Mode nav" is now just "is HOME the
        // current back-stack entry", exactly as "onMain" is "is 'main' the current entry" above.
        val inBeastNav = backStackRoute == WorkoutRoutes.HOME
        val inBeast = backStackRoute in WorkoutRoutes.ALL
        val showNav = onMain || inBeastNav
        // The pill nav still keys off a route string; on the Beast Mode pager it tracks the
        // settled page the same way `currentRoute` tracks `settledPage` for "main" above.
        val beastCurrentRoute = WorkoutRoutes.NAV.getOrElse(beastSettledPage) { WorkoutRoutes.HOME }
        val workoutIcon: ImageVector = ImageVector.vectorResource(R.drawable.ic_workout)
        val beastNavItems = remember(workoutIcon) {
            listOf(
                NavItemSpec(WorkoutRoutes.HOME, workoutIcon, "Routines"),
                NavItemSpec(WorkoutRoutes.HISTORY, com.daybook.app.ui.icons.DaybookIcons.Clock, "History"),
                NavItemSpec(WorkoutRoutes.LIBRARY, com.daybook.app.ui.icons.DaybookIcons.Category, "Exercises")
            )
        }

        // A5 (§3.8.3) — the current APP accent, already resolved by the outer DaybookTheme; only
        // the Beast Mode branch needs its own colorFor(dark) resolution.
        val isDark = com.daybook.app.ui.theme.LocalIsDark.current
        val appAccentColor = LocalAccent.current
        val modeAccentColor = if (inBeast) workoutAccent.colorFor(isDark) else appAccentColor

        // v0.5.3 Phase 4 (§4.8 / §4.11) — the PaddingValues overload; `fabPresent = true` folds
        // the Habits/Intake FAB clearance into the list padding so the FAB stops overlapping the
        // last card. The deprecated `Dp` overload is gone.
        //
        // A5 (§3.8.3) — the Beast Mode accent covers EVERY workout route AND the pill nav,
        // because the nav is drawn by DaybookScaffold, outside the NavHost. Gated on the route
        // (WorkoutRoutes.ALL, not a hand-written literal set), so `main` and every settings/
        // detail/form route keep the app accent exactly as today.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAccent provides modeAccentColor,
            LocalOnAccent provides onAccentInk(modeAccentColor)
        ) {
        // User request — bold fonts (AND, separately, an optional different typeface) scoped to
        // Beast Mode only. `beastFontOverride` is Beast Mode's own font pick from its Settings
        // screen (independent of Settings > Appearance > Font; see `WorkoutFontPrefs`'s KDoc) —
        // `null` means "match the app's own font choice". Either way, `beastTypography` then
        // bumps the label/title weights on top (see its KDoc for why Bold, not Black, is the
        // real bump). Same colorScheme/shapes as the outer DaybookTheme; only while `inBeast` —
        // every `DaybookText.*` read inside this subtree (including the nav pill, drawn by
        // `DaybookScaffold` below) picks this up for free.
        val beastFontOverride by workoutFontPrefs.fontChoice.collectAsStateWithLifecycle()
        val appTypography = androidx.compose.material3.MaterialTheme.typography
        val baseTypography = if (inBeast && beastFontOverride != null) {
            remember(beastFontOverride) { com.daybook.app.ui.theme.daybookTypography(beastFontOverride!!) }
        } else appTypography
        val scopedTypography = if (inBeast) {
            remember(baseTypography) { com.daybook.app.ui.workout.beast.beastTypography(baseTypography) }
        } else baseTypography
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.MaterialTheme.colorScheme,
            typography = scopedTypography,
            shapes = androidx.compose.material3.MaterialTheme.shapes
        ) {
        DaybookScaffold(
            showNav = showNav,
            currentRoute = if (onMain) currentRoute else if (inBeastNav) beastCurrentRoute else backStackRoute,
            navItems = if (inBeastNav) beastNavItems else navItems,
            onSelectRoute = if (inBeastNav) onSelectBeastRoute else onSelectRoute,
            fabPresent = true,
            // A5 (§3.6.1/§3.6.8) — one mechanism, two configurations: hold Today to enter, hold
            // Beast Mode's own leftmost item (Routines) to leave.
            onLongSelect = if (inBeastNav) { _ -> exitBeastMode() } else { _ ->
                if (appSettings.workoutHintState < 2) {
                    scope.launch { runCatching { appSettingsRepository.setWorkoutHintState(2) } }
                }
                goWorkout()
            },
            longPressRoute = if (inBeastNav) WorkoutRoutes.HOME else "home",
            longPressLabel = if (inBeastNav) "Leave Beast Mode" else "Start a workout",
            hintDotRoutes = if (!inBeastNav && appSettings.workoutHintState < 2) setOf("home") else emptySet(),
            // A5 (§3.6.3 a) — the one-time coach-mark, `main` only, `workout_hint_state == 0`.
            coachMark = if (onMain && appSettings.workoutHintState == 0) { navClearance ->
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(600); shown = true }
                if (shown) {
                    NavCoachMark(
                        text = "Hold \"Today\" to start a workout.",
                        actionLabel = "Got it",
                        bottomClearance = navClearance,
                        onDismiss = {
                            scope.launch { runCatching { appSettingsRepository.setWorkoutHintState(1) } }
                        }
                    )
                }
            } else null
        ) { scaffoldPadding ->
            NavHost(
                navController = navController,
                startDestination = "main",
                // v0.5.3 Phase 4 (§4.7) — literal tween durations → Motion tokens.
                // rec 4 — reduce-motion drops the slide/scale, keeping a plain cross-fade.
                // BEAST_MODE_REDESIGN_PLAN.md follow-up — crossing the main/Beast Mode boundary
                // (either direction) gets its own "portal" scale+fade instead of the plain
                // slide/scale every other nav transition uses, so entering/leaving the mode reads
                // as a deliberate shift into a different visual language, not just another push.
                enterTransition = {
                    if (crossesIntoBeast(initialState, targetState)) beastEnter(reduceMotion)
                    else if (reduceMotion) fadeIn() else Motion.navEnter
                },
                exitTransition = {
                    if (crossesIntoBeast(initialState, targetState)) beastExit(reduceMotion)
                    else if (reduceMotion) fadeOut() else fadeOut(tween(110)) + scaleOut(targetScale = 0.98f)
                },
                popEnterTransition = {
                    if (crossesOutOfBeast(initialState, targetState)) beastPopEnter(reduceMotion)
                    else if (reduceMotion) fadeIn() else fadeIn(tween(160)) + scaleIn(initialScale = 0.98f)
                },
                popExitTransition = {
                    if (crossesOutOfBeast(initialState, targetState)) beastPopExit(reduceMotion)
                    else if (reduceMotion) fadeOut() else fadeOut(tween(110)) + slideOutH()
                }
            ) {
                composable("main") {
                    // System back from Habits/Intake returns to Today first (matches the old
                    // popUpTo("home") behaviour) before the activity exits.
                    BackHandler(enabled = settledPage != 0) { goToPage(0) }
                    HorizontalPager(
                        state = pagerState,
                        key = { it },
                        // v0.5.2 perf: 1, not 0. The old fear (all 3 screens live → swipe stutter)
                        // was from animating a fling *through* a cold page — which no longer
                        // happens: §O made every nav-bar tap a snap, and the per-screen N+1 reads
                        // are gone. At 0 a snapped-to tab cold-composes its whole screen +
                        // ViewModel + first Room query on arrival, which is the lag the user
                        // feels. At 1 the neighbours stay laid out and warm; their flows are all
                        // flowOn(Default) + WhileSubscribed(5s), so an idle off-screen page costs
                        // almost nothing.
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        // rec 7 — page index is meaningful only through `visibleRoutes`; the branch
                        // maps the id, not a literal position (reorder still deferred, so the only
                        // reordering is "Today to front").
                        when (visibleRoutes.getOrElse(page) { "home" }) {
                            "home" -> HomeScreen(
                                contentPadding = scaffoldPadding,
                                onNavigateToDetail = goDetail,
                                onNavigateToJournal = goJournal,
                                onNavigateToJournalBackfill = goJournalBackfill,
                                onOpenEntryEdit = goEntryEdit,
                                onNavigateToHabitJournalChat = goHabitJournalChat,
                                onNavigateToHabitJournalBackfill = goHabitJournalBackfill,
                                onOpenHabitJournalEdit = goHabitJournalEdit,
                                onNavigateToSettings = goSettings
                            )
                            "routines" -> RoutinesScreen(
                                contentPadding = scaffoldPadding,
                                onNavigateToAddHabit = goAddHabit,
                                onNavigateToEditHabit = goEditHabit,
                                onNavigateToDetail = { id -> goDetail("habit", id) },
                                onNavigateToSettings = goSettings
                            )
                            else -> FoodMedScreen(
                                contentPadding = scaffoldPadding,
                                onNavigateToAddFoodMed = goAddFoodMed,
                                onNavigateToEditFoodMed = goEditFoodMed,
                                onNavigateToDetail = { id -> goDetail("food_med", id) },
                                onNavigateToSettings = goSettings
                            )
                        }
                    }
                }
                composable("settings") {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenAppearance = { navController.navigate("settings_appearance") },
                        onOpenTodayCalendar = { navController.navigate("settings_today") },
                        onOpenNotifications = { navController.navigate("settings_notifications") },
                        onOpenData = { navController.navigate("settings_data") },
                        onOpenAccount = { navController.navigate("settings_account") },
                        onOpenAppLock = { navController.navigate("settings_app_lock") },
                        onOpenAbout = { navController.navigate("settings_about") },
                        onOpenWorkout = goWorkout
                    )
                }
                composable("settings_app_lock") {
                    AppLockSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("settings_account") {
                    com.daybook.app.ui.account.AccountScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings_appearance") {
                    AppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("settings_today") {
                    TodayCalendarSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("settings_about") {
                    com.daybook.app.ui.settings.AboutSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onReplayTour = { navController.navigate("onboarding_review") }
                    )
                }
                composable("onboarding_review") {
                    val reviewVm: OnboardingViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    LaunchedEffect(Unit) { reviewVm.configureReview() }
                    OnboardingScreen(
                        viewModel = reviewVm,
                        onExitReview = { navController.popBackStack() }
                    )
                }
                composable("settings_notifications") {
                    NotificationSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("settings_data") {
                    DataSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("add_habit") {
                    AddHabitScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("edit_habit/{habitId}") { backStackEntry ->
                    val habitId = backStackEntry.arguments?.getString("habitId") ?: ""
                    EditHabitScreen(habitId = habitId, onNavigateBack = { navController.popBackStack() })
                }
                composable("add_foodmed") {
                    AddFoodMedScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("edit_foodmed/{taskId}") { backStackEntry ->
                    val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                    EditFoodMedScreen(taskId = taskId, onNavigateBack = { navController.popBackStack() })
                }
                composable("detail/{itemType}/{itemId}") { backStackEntry ->
                    val itemType = backStackEntry.arguments?.getString("itemType") ?: ""
                    val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                    DetailScreen(
                        itemType = itemType,
                        itemId = itemId,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenJournal = goJournal,
                        onOpenRespond = { occId -> navController.navigate("respond/$occId?isHabit=false") },
                        onOpenHabitJournalEdit = goHabitJournalEdit
                    )
                }
                composable("journal/{arg0}/{slotMillis}") {
                    com.daybook.app.ui.journal.JournalScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenHistory = { itemType, itemId -> navController.navigate("detail/$itemType/$itemId") }
                    )
                }
                composable("habit_journal_chat/{arg0}/{slotMillis}") {
                    com.daybook.app.ui.journal.HabitJournalChatScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("habit_journal_edit/{occurrenceId}") {
                    com.daybook.app.ui.journal.HabitJournalEditScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "respond/{occId}?isHabit={isHabit}",
                    arguments = listOf(
                        navArgument("isHabit") { type = NavType.StringType; defaultValue = "false" }
                    )
                ) { backStackEntry ->
                    com.daybook.app.ui.respond.RespondScreen(
                        // Journal Mode: plain popBackStack so an edit opened from Detail→Activity
                        // returns to that Detail screen (not all the way to Today). From the
                        // notification deep-link stack [main, respond] it still pops to main.
                        onDone = { navController.popBackStack() },
                        onOpenHistory = { itemType, itemId -> navController.navigate("detail/$itemType/$itemId") }
                    )
                }

                // ---------------------------------------------------------- A5/A6: Beast Mode
                // Eight stacked destinations, siblings of "main" (§3.6.6). Item 3 (LOCKED,
                // PRIORITY) — Routines/History/Exercises are no longer three of them: they're
                // pages of one HorizontalPager inside this single HOME entry, the same mechanism
                // "main" uses for Today/Habits/Intake above (physically swipeable; a nav-bar tap
                // snaps via `goToBeastPage`/`scrollToPage`, matching `goToPage`'s own behaviour;
                // the 3 screens' ViewModels co-instantiate under this one back-stack entry for
                // instant switching). System back from History/Exercises returns to Routines via
                // the same `BackHandler` pattern "main" uses; back from Routines falls through to
                // the NavHost's normal pop (this entry has no `popUpTo`), landing on whatever was
                // below (matches today's behaviour). The rest render full-screen with no nav via
                // `showNav`.
                composable(WorkoutRoutes.HOME) {
                    BackHandler(enabled = beastSettledPage != 0) { goToBeastPage(0) }
                    HorizontalPager(
                        state = beastPagerState,
                        key = { it },
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (WorkoutRoutes.NAV.getOrElse(page) { WorkoutRoutes.HOME }) {
                            WorkoutRoutes.HISTORY -> WorkoutHistoryScreen(
                                contentPadding = scaffoldPadding,
                                onOpenSession = goWorkoutSession,
                                onOpenDetail = goWorkoutDetail
                            )
                            WorkoutRoutes.LIBRARY -> AddExerciseScreen(
                                mode = ExercisePickerMode.BROWSE,
                                contentPadding = scaffoldPadding,
                                onPick = {},
                                onBack = null,
                                onNewExercise = goNewExercise,
                                onEditExercise = goEditExercise,
                                onOpenHistory = {}
                            )
                            else -> WorkoutHomeScreen(
                                contentPadding = scaffoldPadding,
                                onOpenWorkoutSettings = goWorkoutSettings,
                                onStartSession = goWorkoutSession,
                                onNewRoutine = { goRoutineEdit(null) },
                                onEditRoutine = { id -> goRoutineEdit(id) }
                            )
                        }
                    }
                }
                composable(WorkoutRoutes.SETTINGS) {
                    WorkoutSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onLeaveBeastMode = exitBeastMode,
                        onImportFromHevy = goSettingsData
                    )
                }
                composable(WorkoutRoutes.PICK_EXERCISE) {
                    AddExerciseScreen(
                        mode = ExercisePickerMode.PICK,
                        contentPadding = scaffoldPadding,
                        onPick = { exerciseIds ->
                            // Bug fix — see `pickedExerciseId`'s KDoc. Set BEFORE popping so the
                            // consumer's LaunchedEffect (keyed on this flow) sees the new value
                            // the instant its screen recomposes back into view.
                            pickedExerciseId.value = exerciseIds
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                        onNewExercise = goNewExercise,
                        onEditExercise = goEditExercise,
                        onOpenHistory = {}
                    )
                }
                composable(WorkoutRoutes.NEW_EXERCISE) {
                    ExerciseFormScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(WorkoutRoutes.EDIT_EXERCISE) {
                    ExerciseFormScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(
                    WorkoutRoutes.ROUTINE_EDIT,
                    arguments = listOf(navArgument("routineId") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) {
                    val routineEditViewModel: com.daybook.app.ui.workout.RoutineEditViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val pendingPick by pickedExerciseId.collectAsStateWithLifecycle()
                    LaunchedEffect(pendingPick) {
                        pendingPick?.let { ids ->
                            ids.forEach { routineEditViewModel.addExercise(it) }
                            pickedExerciseId.value = null
                        }
                    }
                    RoutineEditScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onPickExercise = goPickExercise,
                        viewModel = routineEditViewModel
                    )
                }
                composable(WorkoutRoutes.SESSION) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                    val sessionViewModel: com.daybook.app.ui.workout.WorkoutSessionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val pendingPick by pickedExerciseId.collectAsStateWithLifecycle()
                    LaunchedEffect(pendingPick) {
                        pendingPick?.let { ids ->
                            ids.forEach { sessionViewModel.addExercise(it) }
                            pickedExerciseId.value = null
                        }
                    }
                    WorkoutSessionScreen(
                        sessionId = sessionId,
                        viewModel = sessionViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onPickExercise = goPickExercise,
                        onFinished = { navController.popBackStack() }
                    )
                }
                composable(WorkoutRoutes.DETAIL) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                    WorkoutDetailScreen(
                        sessionId = sessionId,
                        onNavigateBack = { navController.popBackStack() },
                        onEdit = { goWorkoutSession(sessionId) },
                        onSessionStarted = goWorkoutSession
                    )
                }
            }
        }
        }
        }
    }
}

private fun slideOutH() = androidx.compose.animation.slideOutHorizontally(Motion.medium()) { it / 6 }

/** True for the forward nav ("main" -> a Beast Mode route) that opens Beast Mode. */
private fun crossesIntoBeast(
    initialState: androidx.navigation.NavBackStackEntry,
    targetState: androidx.navigation.NavBackStackEntry
): Boolean = initialState.destination.route == "main" && targetState.destination.route in com.daybook.app.ui.workout.WorkoutRoutes.ALL

/** True for the pop (a Beast Mode route -> "main") that closes Beast Mode — fires regardless of
 *  which Beast screen was on top (Settings/Session/Detail/…) when the long-press-exit or system
 *  back triggered it. */
private fun crossesOutOfBeast(
    initialState: androidx.navigation.NavBackStackEntry,
    targetState: androidx.navigation.NavBackStackEntry
): Boolean = initialState.destination.route in com.daybook.app.ui.workout.WorkoutRoutes.ALL && targetState.destination.route == "main"

/** BEAST_MODE_REDESIGN_PLAN.md follow-up — the "portal" transition into Beast Mode's darker,
 *  bolder visual language: a slight zoom-in + fade rather than the app's usual horizontal slide. */
private fun beastEnter(reduceMotion: Boolean) =
    if (reduceMotion) fadeIn() else fadeIn(tween(360)) + scaleIn(initialScale = 0.90f, animationSpec = tween(360))

private fun beastExit(reduceMotion: Boolean) =
    if (reduceMotion) fadeOut() else fadeOut(tween(220)) + scaleOut(targetScale = 1.06f, animationSpec = tween(220))

private fun beastPopEnter(reduceMotion: Boolean) =
    if (reduceMotion) fadeIn() else fadeIn(tween(300)) + scaleIn(initialScale = 1.06f, animationSpec = tween(300))

private fun beastPopExit(reduceMotion: Boolean) =
    if (reduceMotion) fadeOut() else fadeOut(tween(240)) + scaleOut(targetScale = 0.90f, animationSpec = tween(240))

/** SharedPreferences key: the exact-alarm dialog has been shown once (ask-once, like notifications). */
private const val KEY_ALARM_PERMISSION_ASKED = "alarm_permission_asked"
