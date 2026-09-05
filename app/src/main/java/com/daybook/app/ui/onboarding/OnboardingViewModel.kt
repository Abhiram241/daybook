package com.daybook.app.ui.onboarding

import com.daybook.app.util.safeLaunch

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AccentColor
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.FontChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 3 (D2) — a step in the post-login wizard. [NameAsk] is
 * today's existing name field (shown only when no name could be silently derived from the Google
 * profile); every session now also gets a short feature tour ([FeatureTip] steps) before landing
 * in the app, content pulled from FEATURES.md §2-§9, condensed per the plan's own recommendation.
 */
sealed class WizardStep {
    data object NameAsk : WizardStep()
    data class FeatureTip(
        val title: String,
        val body: String,
        val icon: ImageVector,
        val tint: CardTint
    ) : WizardStep()
}

/** Phase 3 — the fixed tour content, independent of whether NameAsk is shown. */
val OnboardingTourSteps: List<WizardStep.FeatureTip> = listOf(
    WizardStep.FeatureTip(
        "Today, at a glance",
        "Your daily hub: a greeting, how much you've done today, a week strip, and progress cards.",
        DaybookIcons.BarChart,
        CardTints.Mint
    ),
    WizardStep.FeatureTip(
        "Track anything",
        "Habits — individual, batch, ongoing streaks, or chat-style journal check-ins — plus " +
            "Food/Med reminders and a red-flag diary.",
        DaybookIcons.Restaurant,
        CardTints.Rose
    ),
    WizardStep.FeatureTip(
        "Reminders that adapt to you",
        "Skip, snooze, complete, or reply right from the notification — quiet hours and a " +
            "re-nag for anything missed, with Strict or Lenient streak modes and rest days.",
        DaybookIcons.AlarmClock,
        CardTints.SlateBlue
    ),
    WizardStep.FeatureTip(
        "Make it yours",
        "Pick an accent color, a font, and which tabs show in the bottom nav.",
        DaybookIcons.Palette,
        CardTints.Lavender
    ),
    WizardStep.FeatureTip(
        "Offline-first, synced, and locked down",
        "Everything lives on your device first and mirrors across devices once you're signed " +
            "in — add a PIN or biometric App Lock on top, if you want one.",
        DaybookIcons.Backup,
        CardTints.Butter
    )
)

/**
 * Phase 3 (D2) — the step list for this wizard session: [WizardStep.NameAsk] is included only
 * when no name could be silently derived from the Google profile. Pure — see `WizardStepTest`.
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

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository
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

    /** Advances the wizard, or — from the last step — ends it via [completeOnboarding]. */
    fun next() {
        if (isLastWizardStep(_currentStep.value, _steps.value.size)) {
            completeOnboarding(_nameInput.value)
        } else {
            _currentStep.value += 1
        }
    }

    /** Ends the wizard immediately, from any step, with whatever name is currently known. */
    fun skip() {
        completeOnboarding(_nameInput.value)
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

    /** rec 7 — the ordered CSV of visible bottom-nav route ids. */
    val navTabs: StateFlow<String> = settingsRepository.observeSettings()
        .map { it.navTabs }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "home,routines,foodmed")

    /** rec 7 — the route id the app opens on at cold start. */
    val defaultLandingTab: StateFlow<String> = settingsRepository.observeSettings()
        .map { it.defaultLandingTab }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "home")

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