package com.daybook.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.*
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing

/**
 * UX overhaul item 1 — the first-run teaching wizard. Keeps the exact shell: a non-scroll outer
 * `Column`, a weighted **scrollable** content `Column`, and a [StickySaveBar] pinned at the
 * bottom. [onExitReview] is used only when the wizard is the "Replay the tour" re-run from
 * About & help (`viewModel.configureReview()`); [onOpenAddHabit] is the optional "Create your
 * first habit" link on the closing step. The three `onAllow*` callbacks fire the app's existing
 * permission request paths from the primer step.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onExitReview: () -> Unit = {},
    onOpenAddHabit: () -> Unit = {},
    onAllowNotifications: () -> Unit = {},
    onAllowExactAlarms: () -> Unit = {},
    onAllowBattery: () -> Unit = {}
) {
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val nameInput by viewModel.nameInput.collectAsStateWithLifecycle()
    val reviewMode by viewModel.reviewMode.collectAsStateWithLifecycle()
    val hasExistingData by viewModel.hasExistingData.collectAsStateWithLifecycle()

    val step = steps.getOrNull(currentStep) ?: return
    val isLast = isLastWizardStep(currentStep, steps.size)
    val nextEnabled = step != WizardStep.NameAsk || nameInput.isNotBlank()

    val primaryLabel = when {
        reviewMode && isLast -> "Done"
        isLast -> "Get started"
        else -> "Next"
    }
    val onPrimary: () -> Unit = {
        if (reviewMode && isLast) onExitReview() else viewModel.next()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screenH, end = Spacing.screenH, top = Spacing.xxxl, bottom = Spacing.xxl),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
        ) {
            StepDots(total = steps.size, current = currentStep)

            when (step) {
                WizardStep.NameAsk -> NameAskStep(name = nameInput, onNameChange = viewModel::onNameInputChange)
                is WizardStep.Teach -> TeachStep(step)
                WizardStep.PermissionPrimer -> PermissionPrimerStep(
                    onAllowNotifications = onAllowNotifications,
                    onAllowExactAlarms = onAllowExactAlarms,
                    onAllowBattery = onAllowBattery
                )
                WizardStep.Ready -> ReadyStep(
                    reviewMode = reviewMode,
                    hasExistingData = hasExistingData,
                    onCreateFirstHabit = {
                        viewModel.next()          // completes onboarding (no-op in review mode)
                        onOpenAddHabit()
                    }
                )
            }
        }

        StickySaveBar {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!reviewMode) {
                    TextLink("Skip", onClick = viewModel::skip)
                }
                PrimaryButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    enabled = nextEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (reviewMode) 0.dp else Spacing.md)
                )
            }
        }
    }
}

@Composable
private fun StepDots(total: Int, current: Int) {
    if (total <= 1) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val accent = LocalAccent.current
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) accent else DaybookColors.TextFaint)
            )
        }
    }
}

@Composable
private fun NameAskStep(name: String, onNameChange: (String) -> Unit) {
    Column {
        BigHeadline("Welcome to Daybook", style = DaybookText.Hero)
        Text(
            "Reminders that ask what you actually ate, took, and did — and keep the log for you.",
            style = MaterialTheme.typography.bodyLarge,
            color = DaybookColors.TextMuted
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("What should we call you?", style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
        DaybookTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Your name",
            placeholder = "e.g. Alex"
        )
        Text(
            "Used in the Today greeting. Change it any time in Settings.",
            style = DaybookText.CardSubtitle,
            color = DaybookColors.TextMuted
        )
    }
}

@Composable
private fun TeachStep(step: WizardStep.Teach) {
    val tint = CardTints.byIndex(step.illustration.ordinal)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(step.title, style = DaybookText.ScreenTitle, color = DaybookColors.TextPrimary)
        OnboardingIllustration(step.illustration)
        Text(
            step.body,
            style = MaterialTheme.typography.bodyLarge,
            color = DaybookColors.TextMuted
        )
        // A quiet accent hairline that ties the copy to the illustration's tint.
        Box(
            Modifier
                .width(48.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(tint.accent)
        )
    }
}

@Composable
private fun PermissionPrimerStep(
    onAllowNotifications: () -> Unit,
    onAllowExactAlarms: () -> Unit,
    onAllowBattery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text("A few permissions", style = DaybookText.ScreenTitle, color = DaybookColors.TextPrimary)
        Text(
            "Allow these now or later — Daybook asks again if it needs to.",
            style = MaterialTheme.typography.bodyLarge,
            color = DaybookColors.TextMuted
        )
        PermissionPrimerRow(
            title = "Notifications",
            body = "Without this, reminders are silent. They still appear in the app, but nothing pops up on your phone.",
            onAllow = onAllowNotifications
        )
        PermissionPrimerRow(
            title = "Exact alarms",
            body = "Lets a reminder fire at the exact minute you set. Without it, Android can delay it by up to about 15 minutes to save power.",
            onAllow = onAllowExactAlarms
        )
        PermissionPrimerRow(
            title = "Unrestricted battery",
            body = "Stops the system freezing Daybook in the background, which would silence reminders after a few days. One-time setting.",
            onAllow = onAllowBattery
        )
    }
}

@Composable
private fun PermissionPrimerRow(title: String, body: String, onAllow: () -> Unit) {
    SoftCard(tint = CardTints.Neutral, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted)
        Spacer(Modifier.height(12.dp))
        GhostButton(text = "Allow", onClick = onAllow)
    }
}

/**
 * D4 / LD9 — three copy variants driven by [reviewMode] / [hasExistingData]:
 *  - `reviewMode` — "That's the tour" (unchanged).
 *  - `hasExistingData == true` — "Welcome back", no link (an account with restored data).
 *  - `hasExistingData == false` or still `null` — the fresh-install "You're set" copy; the link
 *    itself only renders once `hasExistingData == false` is confirmed. While `null` the link's
 *    height is still reserved (a fixed-height Box) so nothing shifts when it resolves.
 */
@Composable
private fun ReadyStep(reviewMode: Boolean, hasExistingData: Boolean?, onCreateFirstHabit: () -> Unit) {
    val headline = when {
        reviewMode -> "That's the tour"
        hasExistingData == true -> "Welcome back"
        else -> "You're set"
    }
    val body = when {
        reviewMode -> "Tap Done to head back to settings."
        hasExistingData == true -> "Your habits and reminders are already here — Today has the details."
        else -> "Make your first habit now, or look around first."
    }
    val showLink = !reviewMode && hasExistingData == false

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BigHeadline(headline, style = DaybookText.Hero)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextMuted)
        if (!reviewMode) {
            Spacer(Modifier.height(Spacing.sm))
            // Fixed-height container so the link's arrival (once hasExistingData resolves to
            // false) never shifts the layout — LD9.
            Box(Modifier.heightIn(min = 44.dp)) {
                if (showLink) TextLink("Create your first habit", onClick = onCreateFirstHabit)
            }
        }
    }
}
