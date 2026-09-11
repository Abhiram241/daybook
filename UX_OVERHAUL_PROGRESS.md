# UX Overhaul — progress

Build order: 2 → 4 → 5 → 1 → 3 → 7 → 8

- [x] Item 2 — dialog contrast: DaybookAlertDialog textContentColor + tonalElevation=0;
      MainActivity fuller copy on both dialogs; surfaceTint=Transparent both schemes.
- [x] Item 4 — light/dark theme + MIGRATION_19_20. compileDebugKotlin + theme JVM tests green.
      - Tokens.kt: DaybookColorScheme + DaybookColorsDark/Light + LocalDaybookColors +
        @Composable-getter DaybookColors shim; CardTintsDark/CardTintsLight + @Composable CardTints.
      - Accent.kt: AccentColor.dark/.light + colorFor(); LocalAccent now resolved Color.
      - Theme.kt: LightScheme, ThemeMode enum, themeMode param, surfaceTint=Transparent both.
      - DataModel AppSettings.themeMode; AppSettingsDao.updateThemeMode; AppSettingsRepository
        setThemeMode + SharedPreferences mirror (ThemeModePrefs.kt) + readThemeModeMirror;
        constructor now takes @ApplicationContext (DatabaseModule provider updated).
      - Migrations.kt MIGRATION_19_20 (ALTER TABLE app_settings ADD COLUMN theme_mode TEXT
        NOT NULL DEFAULT 'DARK'); DatabaseModule registered; AppDatabase version 20;
        app/schemas/.../20.json regenerated (committed on disk).
      - OnboardingViewModel.themeMode flow (initial = prefs mirror, Eagerly); SettingsViewModel
        themeMode flow + setThemeMode.
      - MainActivity: applyWindowTheme() sync setTheme() before setContent; pass themeMode.
      - res/values/themes.xml Theme.Daybook.Dark/.Light; colors.xml background_light.
      - Tests: ThemeModeTest (4), AccentColorThemeTest (4), MigrationTest migrate19To20 x2 +
        migrateAll_3To20 + fullOpenAtLatestVersion updated.
      - Fixed non-composable shim breaks: Navigation.kt drawBehind hairline hoist;
        SettingsScreen accent swatch colorFor; DetailScreen CardTints.byId un-remembered;
        OnboardingViewModel OnboardingTourSteps -> CardTintsDark.

- [x] Item 5 — settings reorg + AboutSettingsScreen. compileDebugKotlin green.
      - Hub: renames (Reminders & notifications / Privacy & lock / Backup & data),
        Navigation row removed, About & help row added, footer opens About (crash-log moved).
      - AppearanceSettingsScreen: Theme section (item 4) + NavigationLayoutSections folded in
        as "Layout" + "Show tabs"; "Accessibility" header -> "Motion".
      - NavigationSettingsScreen.kt: now exports ColumnScope.NavigationLayoutSections();
        old NavigationSettingsScreen composable + settings_navigation route deleted.
      - NotificationSettingsScreen: title "Reminders & notifications"; "Habit check-in" ->
        "Batch check-in"; "Snooze" -> "Default snooze"; Updates + Diagnostics sections removed.
      - New AboutSettingsScreen.kt: version block, Replay the tour row (onReplayTour ->
        onboarding_review), Check for updates toggle, Diagnostics (test notif / re-arm /
        copy crash log).
      - AccountScreen: 'Use "x" as your name' -> 'Set name to "x"'.
      - AppLockSettingsScreen title -> "Privacy & lock". DataSettingsScreen title -> "Backup & data".
      - MainActivity: settings_navigation removed; settings_about route added (onReplayTour
        navigates to onboarding_review — route added in item 1).

- [x] Item 1 — onboarding teaching rewrite. compileDebugKotlin green.
      - OnboardingViewModel: WizardStep sealed = NameAsk / Teach(title,body,illustration) /
        PermissionPrimer / Ready; TeachIllustration enum; OnboardingTeachSteps (5) +
        OnboardingTourSteps = teach*5 + PermissionPrimer + Ready; reviewMode + configureReview();
        next()/skip() no-op in review mode.
      - OnboardingScreen rewritten: Teach card + OnboardingIllustration; PermissionPrimer rows
        with Allow buttons; Ready step with "Create your first habit" TextLink; review-mode
        "Done" button + onExitReview.
      - New OnboardingIllustrations.kt: in-app mock composables per TeachIllustration.
      - MainActivity: onboarding branch passes onOpenAddHabit + onAllow* ; pendingOpenAddHabit
        StateFlow consumed by MainApp LaunchedEffect -> navigate("add_habit"); new
        onboarding_review composable route (fresh hiltViewModel + configureReview + onExitReview).
      - WizardStepTest rewritten for the new shape.

- [x] Item 3 — BATCH duplicate notification bugfix. compileDebugKotlin green.
      - OccurrenceScheduler: pure shouldPostIndividualHabitNotification(type)=type!=BATCH;
        armNextHabitInternal guards on armsOwnAlarm(owner.type); syncAll() cancels leaked
        per-occurrence alarm+notif for any BATCH habit's next-pending occurrence.
      - AlarmReceiver.fireHabit: early-return (no post, no SHOWN event, no re-nag) when
        !shouldPostIndividualHabitNotification(habit.type). INDIVIDUAL/JOURNAL/Intake untouched.
      - BootCompletedReceiver / WindowRefreshWorker only call syncAll() (confirmed) — no change.
      - Test: ShouldPostIndividualHabitNotificationTest.

- [x] Item 7 — Today grouping / ReminderCard / WeekStrip. compileDebugKotlin + tests green.
      - HomeViewModel: HomeSection data class + pure groupHomeItems(items, selectedDate, today,
        nowMillis, byType) -> Overdue/Now/Later/Done (today), To do/Missed/Logged/Skipped/Done
        (past), Upcoming (future), Habits/Intake/Journal (byType). NOW_WINDOW_MS=90min.
      - HomeScreen: groupByType rememberSaveable session state; renders per-section label
        (ReminderSectionLabel "NOW · 2") + items with a running global index for tint; SortSheet
        gets "Group by type" toggle; ReminderCard meta rows folded into one truncated line
        (time · flag · suspected · outside) with the flag dot inline.
      - SortSheet: groupByType/onToggleGroupByType/groupByTypeRowLabel params.
      - WeekStrip: "Back to today" now a fixed-height(36dp) Box (content toggled inside) so the
        calendar body no longer jumps.
      - Test: GroupHomeItemsTest (8).

- [x] Item 8 — bloat hide/move/fold. compileDebugKotlin green.
      - 8.1 Greeting: 3 controls -> one SegmentedControl Full/Simple/Off (sets greeting_tone +
        greeting_time_word together, pins hero_style=COUNT_LEFT). Columns stay live.
      - 8.2 Streaks: header "Streak display"->"Streaks"; removed Strict/Lenient + Rest days;
        kept "Show streak flames". streak_mode / streak_rest_days columns stay live.
      - 8.3 Motion: removed the Reduce motion row + section from Appearance. reduce_motion
        column stays live (still OR-ed in effectiveReduceMotion).
      - 8.11: removed "Default calendar view" segmented control. calendar_default_expanded
        stays live (HomeScreen still seeds from it).
      - 8.4 / 8.5 / 8.6: done in item 5 (Updates + Diagnostics -> About; Navigation -> Layout).
      - 8.7 untouched. 8.10 retired Intake-Journal files kept.
      - Also: "Reminders" header -> "Reminders list" (item 5 casing table).

## ALL 7 ITEMS IMPLEMENTED — verification gate GREEN.

Gate: `./gradlew clean testDebugUnitTest assembleDebug assembleRelease compileDebugAndroidTestKotlin`
-> BUILD SUCCESSFUL, exit 0. testDebugUnitTest = 469 tests, 0 failures, 0 errors
(baseline ~446 + 23 new). assembleRelease R8 + lintVitalRelease clean.
Signed release APK: app/build/outputs/apk/release/app-release.apk
7,413,127 bytes, Signer #1 SHA-1 39e62d0fb9b59e4d6376989d3f8329ce83f0ab0c (matches RELEASE_SIGNING.md).

MIGRATION_19_20 = `ALTER TABLE app_settings ADD COLUMN theme_mode TEXT NOT NULL DEFAULT 'DARK'`
AppDatabase.version 19 -> 20. app/schemas/.../20.json regenerated (untracked, needs `git add`).
No git writes, no Firebase push, no version/dependency/SDK changes, dark theme values byte-identical.
androidTest MigrationTest compiles here (migrate19To20 x2 + migrateAll_3To20) but not run (no device).
