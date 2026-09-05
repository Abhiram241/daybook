package com.daybook.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.*
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 3 (D2) — the post-login wizard: an optional name-ask step
 * (skipped when a name was already silently derived from the Google profile), followed by a
 * short feature tour. Every session sees the tour; `viewModel.configure(derivedName)` is called
 * once by the caller (`MainActivity`'s ONBOARDING branch) to pick the step list.
 *
 * Reuses the exact shell every other screen in this round does: a non-scroll outer `Column`, a
 * weighted **scrollable** content `Column`, and a [StickySaveBar] pinned at the bottom — never
 * reimplemented.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val steps by viewModel.steps.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val nameInput by viewModel.nameInput.collectAsState()

    val step = steps.getOrNull(currentStep) ?: return
    val isLast = isLastWizardStep(currentStep, steps.size)
    val nextEnabled = step != WizardStep.NameAsk || nameInput.isNotBlank()

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
            // Step-dots indicator — no existing dot/indicator primitive in ui/components, so a
            // trivial one lives here (Phase 3's own note: build one only if none already exists).
            StepDots(total = steps.size, current = currentStep)

            when (step) {
                WizardStep.NameAsk -> NameAskStep(name = nameInput, onNameChange = viewModel::onNameInputChange)
                is WizardStep.FeatureTip -> FeatureTipStep(step)
            }
        }

        // v0.5.3 Phase 4 (§4.2) — pinned action bar reuses the shared StickySaveBar. Phase 3 adds
        // a second control (Skip) beside Next/Get started — StickySaveBar's content slot already
        // accepts arbitrary content, so no new pinning primitive is needed.
        StickySaveBar {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextLink("Skip", onClick = viewModel::skip)
                PrimaryButton(
                    text = if (isLast) "Get started" else "Next",
                    onClick = viewModel::next,
                    enabled = nextEnabled,
                    modifier = Modifier.weight(1f).padding(start = Spacing.md)
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
    }
}

@Composable
private fun FeatureTipStep(tip: WizardStep.FeatureTip) {
    SoftCard(tint = tip.tint, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = tip.icon, tint = tip.tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tip.title, style = DaybookText.CardTitle, color = tip.tint.onFill)
                Text(
                    tip.body,
                    style = DaybookText.CardSubtitle,
                    color = tip.tint.onFillMuted,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
