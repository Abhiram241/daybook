package com.daybook.app.ui.onboarding

import com.daybook.app.util.safeLaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.FoodMedRepository
import com.daybook.app.data.HabitRepository
import com.daybook.app.ui.theme.AccentColor
import com.daybook.app.ui.theme.DarkStyle
import com.daybook.app.ui.theme.DEFAULT_CORNER_SCALE
import com.daybook.app.ui.theme.FontChoice
import com.daybook.app.ui.theme.LightStyle
import com.daybook.app.ui.theme.ThemeMode
import com.daybook.app.ui.theme.clampCornerScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UX overhaul item 1 — the first-run wizard rebuilt as a *teaching* flow inside the existing
 * shell. [NameAsk] is unchanged (shown only when no name was derivable from the Google profile).
 * [Teach] replaces the old marketing `FeatureTip` — each explains one loop and maps to a small,
 * in-app-rendered mock ([TeachIllustration]) so the picture always matches the live theme/accent/
 * font. [PermissionPrimer] explains the three permissions with optional inline "Allow" buttons.
 * [Ready] is the closing step with the skippable "Create your first habit" link.
 */
sealed class WizardStep {
    data object NameAsk : WizardStep()
    data class Teach(
        val title: String,
        val body: String,
        val illustration: TeachIllustration
    ) : WizardStep()
    data object PermissionPrimer : WizardStep()
    data object Ready : WizardStep()
}

/** The screen maps each of these to a cheap static mock built from real primitives. */
enum class TeachIllustration { TODAY, MAKE_HABIT, INTAKE, SHADE, YOURS, STREAKS, PRIVACY }

/**
 * UX refinement round (LD7) — 7 teaching cards, in order, copy shipped verbatim from
 * UX_REFINEMENT_PLAN.md §2.2. Was 5 steps; streaks and Journal now each get their own step.
 */
val OnboardingTeachSteps: List<WizardStep.Teach> = listOf(
    WizardStep.Teach(
        "Today is home base",
        "Today opens on a greeting and how many reminders are still open. The strip along the top " +
            "switches days — tap any past day to log something you missed. Tap the month name for the " +
            "full calendar. Two cards track today's habit and intake progress.",
        TeachIllustration.TODAY
    ),
    WizardStep.Teach(
        "Habits, four ways",
        "Add a habit from the Habits tab. Individual reminds you at each time you set. " +
            "Batch rolls several small habits into one evening check-in. Ongoing just counts " +
            "the days since you started, with no reminders. Journal asks you a short set of " +
            "questions each time. You pick the days, the times, and how long a snooze lasts.",
        TeachIllustration.MAKE_HABIT
    ),
    WizardStep.Teach(
        "Intake: what you actually had",
        "Intake reminders ask \"what did you have?\" and save your reply as the log — type it on the " +
            "card or straight from the notification. Use it for meals, medication, water, anything you " +
            "want a record of. Mark a food as a red flag and Daybook builds a diary of possible triggers.",
        TeachIllustration.INTAKE
    ),
    WizardStep.Teach(
        "Journalling",
        "A Journal habit turns a check-in into a few written prompts — \"How did you sleep?\", " +
            "\"What's on your mind?\". Answers are saved per day, and you can read the whole thread back " +
            "from the habit's detail screen. Edit the questions whenever you like.",
        TeachIllustration.INTAKE // LD8 — reused, no new JOURNAL mock
    ),
    WizardStep.Teach(
        "Reminders that follow up",
        "Every reminder lands as a notification with buttons: Complete or Skip for habits; " +
            "Reply, Skip or Snooze for intake. Anything you don't answer keeps nudging on " +
            "the snooze interval until you do. Quiet hours hold reminders back and release them " +
            "later — nothing is dropped.",
        TeachIllustration.SHADE
    ),
    WizardStep.Teach(
        "Streaks",
        "Each habit counts consecutive days done. The flame on Today shows your best current run; " +
            "a habit's detail screen shows its full history. A missed day resets it. Not your thing? " +
            "Turn the flame off in Settings → Today & calendar.",
        TeachIllustration.STREAKS
    ),
    WizardStep.Teach(
        "Yours, private, and backed up",
        "Pick an accent colour, a font, a light or dark look with a background style, and choose " +
            "which tabs show — all in Settings → Appearance. Everything is stored on your device " +
            "first. Signing in mirrors an encrypted copy to your account, so a new phone picks up " +
            "where you left off. Add a PIN or fingerprint lock in Settings → Privacy & lock.",
        TeachIllustration.PRIVACY
    )
)

/** The tour portion (everything after an optional [WizardStep.NameAsk]). */
val OnboardingTourSteps: List<WizardStep> =
    OnboardingTeachSteps + WizardStep.PermissionPrimer + WizardStep.Ready

/**
 * The step list for this wizard session: [WizardStep.NameAsk] is included only when no name
 * could be silently derived from the Google profile. Pure — see `WizardStepTest`.
 */
fun buildWizardSteps(hasAutoDerivedName: Boolean): List<WizardStep> =
    if (hasAutoDerivedName) OnboardingTourSteps else listOf(WizardStep.NameAsk) + OnboardingTourSteps

/**
 * Phase 3 — whether calling `next()` from [currentStep] (0-based, out of [stepCount] total steps)
 * should end the wizard (true, already on the last step) or merely advance it (false). Pure —
 * see `WizardStepTest`.
 */
fun isLastWizardStep(currentStep: Int, stepCount: Int): Boolean = currentStep >= stepCount - 1

/**
 * v0.5.5 Phase 7 — the name to persist silently on first sign-in, or null when none is known (in
 * which case the gate shows the name-entry screen). `restoredUserName` is always "" today
 * (sub-decision c: no backup carries `userName`); it is kept as a first-class parameter so adding
 * it to the wire model later is a one-line change. Pure — see `DeriveOnboardingNameTest`.
 */
fun deriveOnboardingName(displayName: String?, restoredUserName: String?): String? =
    restoredUserName?.trim()?.takeIf { it.isNotBlank() }
        ?: displayName?.trim()?.takeIf { it.isNotBlank() }

/**
 * v0.5.5 Phase 7 — the fire-once guard for [OnboardingViewModel.completeOnboarding]: skip a
 * (re)entry while a persist is already in flight or onboarding is already marked complete. Pure —
 * see `DeriveOnboardingNameTest`.
 */
fun shouldSkipCompleteOnboarding(isLoading: Boolean, completed: Boolean?): Boolean =
    isLoading || completed == true

/**
 * D4 / LD9 — true when the account already has habit or intake data, in which case the closing
 * onboarding step must not offer "Create your first habit". Pure — see `HasExistingDataTest`.
 */
fun hasExistingData(habitCount: Int, intakeCount: Int): Boolean = habitCount + intakeCount > 0

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val habitRepository: HabitRepository,
    private val foodMedRepository: FoodMedRepository
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * `null` until the persisted settings row has been read once (L1). The UI must render a
     * neutral splash while this is null instead of assuming "not onboarded" — assuming false
     * made the name-entry screen flash on every launch.
     */
    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 3 (D2) — the old `_autoCompleteFailed` flag (and the
    // silent, pre-tour `completeOnboarding` call it guarded against) is gone: `completeOnboarding`
    // is now only ever invoked from the wizard's Next/Skip actions (see `next`/`skip` below), so
    // there is no more "blank splash forever if the silent persist fails" case to fall back from.
    // A failure there still surfaces via [errorMessage] exactly as it always has.

    // ---- Phase 3 wizard state ----

    private var configured = false

    /** UX overhaul item 1 — true when the wizard is the "Replay the tour" re-run from About &
     *  help: the step list is tour-only (no NameAsk), the closing button reads "Done" and just
     *  pops back, and `onboardingCompleted` is never touched. */
    private val _reviewMode = MutableStateFlow(false)
    val reviewMode: StateFlow<Boolean> = _reviewMode.asStateFlow()

    private val _steps = MutableStateFlow<List<WizardStep>>(buildWizardSteps(hasAutoDerivedName = false))
    val steps: StateFlow<List<WizardStep>> = _steps.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _nameInput = MutableStateFlow("")
    val nameInput: StateFlow<String> = _nameInput.asStateFlow()

    fun onNameInputChange(value: String) { _nameInput.value = value }

    /**
     * Configures the wizard for this session: [derivedName] non-null means a name was already
     * silently derived from the Google profile (D2) — the step list skips [WizardStep.NameAsk]
     * and [nameInput] is pre-seeded with it; null means the wizard opens on the name field.
     * Idempotent — a second call (e.g. from a `LaunchedEffect` re-running on recomposition) is a
     * no-op so a wizard already in progress never has its step list or position reset out from
     * under the user.
     */
    fun configure(derivedName: String?) {
        if (configured) return
        configured = true
        _steps.value = buildWizardSteps(hasAutoDerivedName = derivedName != null)
        if (derivedName != null) _nameInput.value = derivedName
    }

    /**
     * UX overhaul item 1 — configure the wizard as the read-only "Replay the tour" re-run:
     * the tour-only step list (no [WizardStep.NameAsk]), position reset to the start. Idempotent.
     * `onboardingCompleted` is never read or written in this mode.
     */
    fun configureReview() {
        if (configured) return
        configured = true
        _reviewMode.value = true
        _steps.value = OnboardingTourSteps
        _currentStep.value = 0
    }

    /**
     * Advances the wizard, or — from the last step — ends it via [completeOnboarding]. In review
     * mode the last step is a no-op here: the screen pops back via its own exit callback, and
     * `onboardingCompleted` / the stored name are never touched.
     */
    fun next() {
        if (isLastWizardStep(_currentStep.value, _steps.value.size)) {
            if (!_reviewMode.value) completeOnboarding(_nameInput.value)
        } else {
            _currentStep.value += 1
        }
    }

    /** Ends the wizard immediately, from any step, with whatever name is currently known. */
    fun skip() {
        if (!_reviewMode.value) completeOnboarding(_nameInput.value)
    }

    /** Drives the app-wide accent; re-emits whenever the setting changes. */
    val accentColor: StateFlow<AccentColor> = settingsRepository.observeSettings()
        .map { it.accentColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentColor.DEFAULT)

    /** Drives the app-wide typeface; live even before onboarding completes (same shape as [accentColor]). */
    val fontChoice: StateFlow<FontChoice> = settingsRepository.observeSettings()
        .map { FontChoice.fromKeyOrDefault(it.fontChoice) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FontChoice.DEFAULT)

    /** rec 4 — the app-wide reduce-motion preference, fed to [com.daybook.app.ui.theme.DaybookTheme]. */
    val reduceMotion: StateFlow<Boolean> = settingsRepository.observeSettings()
        .map { it.reduceMotion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * UX overhaul item 4 — the app-wide theme mode, fed to [com.daybook.app.ui.theme.DaybookTheme].
     * The initial value comes from the SharedPreferences mirror (read synchronously), so the very
     * first composition in `MainActivity.setContent` is already the correct theme — zero flash.
     */
    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeSettings()
        .map { ThemeMode.fromKeyOrDefault(it.themeMode) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ThemeMode.fromKeyOrDefault(settingsRepository.readThemeModeMirror())
        )

    /**
     * UX refinement round — the dark/light background style + corner-radius scale, fed to
     * [com.daybook.app.ui.theme.DaybookTheme]. Same zero-flash shape as [themeMode]: seeded
     * `Eagerly` from the SharedPreferences mirror.
     */
    val darkStyle: StateFlow<DarkStyle> = settingsRepository.observeSettings()
        .map { DarkStyle.fromKeyOrDefault(it.darkStyle) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DarkStyle.fromKeyOrDefault(settingsRepository.readDarkStyleMirror())
        )

    val lightStyle: StateFlow<LightStyle> = settingsRepository.observeSettings()
        .map { LightStyle.fromKeyOrDefault(it.lightStyle) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            LightStyle.fromKeyOrDefault(settingsRepository.readLightStyleMirror())
        )

    val cornerScale: StateFlow<Float> = settingsRepository.observeSettings()
        .map { clampCornerScale(it.cornerScale) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            clampCornerScale(settingsRepository.readCornerScaleMirror())
        )

    /** rec 7 — the ordered CSV of visible bottom-nav route ids. */
    val navTabs: StateFlow<String> = settingsRepository.observeSettings()
        .map { it.navTabs }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "home,routines,foodmed")

    /** rec 7 — the route id the app opens on at cold start. */
    val defaultLandingTab: StateFlow<String> = settingsRepository.observeSettings()
        .map { it.defaultLandingTab }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "home")

    /**
     * D4 / LD9 — whether the account already has habit or intake data, so the closing
     * onboarding step can hide "Create your first habit" for a returning user whose cloud
     * bootstrap has restored their rows. `null` = the two row-count Flows have not emitted yet
     * — never seeded `false`, so the link can never flash for a returning user while the
     * bootstrap is still in flight (LD9).
     */
    val hasExistingData: StateFlow<Boolean?> =
        combine(
            habitRepository.observeAllHabits(),
            foodMedRepository.observeAllTasks()
        ) { h, i -> hasExistingData(h.size, i.size) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        checkOnboardingStatus()
    }

    private fun checkOnboardingStatus() {
        safeLaunch {
            _onboardingCompleted.value = runCatching {
                settingsRepository.getSettings().onboardingCompleted
            }.getOrDefault(false)
        }
    }

    fun completeOnboarding(name: String) {
        // v0.5.5 Phase 7 / LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 3 — fire-once guard: called from
        // both `next()` (last step) and `skip()` (any step), and either can be double-tapped
        // during a slow persist.
        if (shouldSkipCompleteOnboarding(_isLoading.value, _onboardingCompleted.value)) return
        safeLaunch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                settingsRepository.setUserName(name.trim())
                settingsRepository.setOnboardingCompleted(true)
                _onboardingCompleted.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Failed to complete onboarding: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}