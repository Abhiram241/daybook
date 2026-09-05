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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
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
import com.daybook.app.ui.theme.DaybookTheme
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

    /** (occurrenceId, isHabit) from a tapped notification, consumed once by [MainApp]. */
    private val deepLinkOccurrence = MutableStateFlow<Pair<String, Boolean>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        readDeepLink(intent)

        // Opt in to the display's highest refresh rate, but only when it's actually worth it.
        runCatching {
            val best = display?.supportedModes?.maxByOrNull { it.refreshRate }
            if (best != null && best.refreshRate > 90f) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
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
                                "Notifications are turned off for Daybook. Open settings to allow them so reminders can alert you."
                            else
                                "Daybook reminds you at your set times and needs notification access to do that. You can also change this later in Settings."
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
                            "Daybook fires reminders at exact times and needs the Alarms & reminders permission."
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

            val accent by onboardingViewModel.accentColor.collectAsState()
            val fontChoice by onboardingViewModel.fontChoice.collectAsState()
            val reduceMotion by onboardingViewModel.reduceMotion.collectAsState()
            DaybookTheme(accent = accent, fontChoice = fontChoice, reduceMotion = reduceMotion) {
                val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()
                val locked by appLockRepository.isLocked.collectAsState()
                val authState by authRepository.state.collectAsState()

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
                        OnboardingScreen(viewModel = onboardingViewModel)
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
        val navTabsCsv by onboardingViewModel.navTabs.collectAsState()
        val defaultLandingTab by onboardingViewModel.defaultLandingTab.collectAsState()
        val visibleRoutes = remember(navTabsCsv) { com.daybook.app.ui.NavConfig.visibleRoutesFrom(navTabsCsv) }
        // The top-level tabs live in one HorizontalPager (swipe between them + tab-order-aware
        // directional slide come for free). Detail/Add/Edit/Settings stay stacked over "main".
        val pagerState = rememberPagerState(
            initialPage = com.daybook.app.ui.NavConfig.landingIndex(defaultLandingTab, visibleRoutes),
            pageCount = { visibleRoutes.size }
        )

        // Route a tapped reminder notification to its detail screen (REV-07).
        val pendingDeepLink by deepLinkOccurrence.collectAsState()
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

        // v0.5.3 Phase 4 (§4.8 / §4.11) — the PaddingValues overload; `fabPresent = true` folds
        // the Habits/Intake FAB clearance into the list padding so the FAB stops overlapping the
        // last card. The deprecated `Dp` overload is gone.
        DaybookScaffold(
            showNav = onMain,
            currentRoute = currentRoute,
            navItems = navItems,
            onSelectRoute = onSelectRoute,
            fabPresent = true
        ) { scaffoldPadding ->
            NavHost(
                navController = navController,
                startDestination = "main",
                // v0.5.3 Phase 4 (§4.7) — literal tween durations → Motion tokens.
                // rec 4 — reduce-motion drops the slide/scale, keeping a plain cross-fade.
                enterTransition = { if (reduceMotion) fadeIn() else Motion.navEnter },
                exitTransition = { if (reduceMotion) fadeOut() else fadeOut(tween(110)) + scaleOut(targetScale = 0.98f) },
                popEnterTransition = { if (reduceMotion) fadeIn() else fadeIn(tween(160)) + scaleIn(initialScale = 0.98f) },
                popExitTransition = { if (reduceMotion) fadeOut() else fadeOut(tween(110)) + slideOutH() }
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
                        onOpenNavigation = { navController.navigate("settings_navigation") },
                        onOpenNotifications = { navController.navigate("settings_notifications") },
                        onOpenData = { navController.navigate("settings_data") },
                        onOpenAccount = { navController.navigate("settings_account") },
                        onOpenAppLock = { navController.navigate("settings_app_lock") }
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
                composable("settings_navigation") {
                    com.daybook.app.ui.settings.NavigationSettingsScreen(onNavigateBack = { navController.popBackStack() })
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
            }
        }
    }
}

private fun slideOutH() = androidx.compose.animation.slideOutHorizontally(Motion.medium()) { it / 6 }

/** SharedPreferences key: the exact-alarm dialog has been shown once (ask-once, like notifications). */
private const val KEY_ALARM_PERMISSION_ASKED = "alarm_permission_asked"
