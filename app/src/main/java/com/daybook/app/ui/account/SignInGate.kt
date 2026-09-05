package com.daybook.app.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.BigHeadline
import com.daybook.app.ui.components.StickySaveBar
import com.daybook.app.ui.components.WaveHero
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.Spacing

/**
 * The blocking sign-in step of the launch gate (v0.5.1 §D). `MainActivity` renders this when the
 * user is not signed in. Full-bleed, **no** back affordance and **no** skip — the `BackHandler`
 * swallows system back so there is no way past it except signing in.
 *
 * v0.5.5 Phase 6 — adopts the [com.daybook.app.ui.onboarding.OnboardingScreen] shape: a non-scroll
 * outer `Column`, a weighted **scrollable** content `Column` (headline + blurb), and the
 * "Continue with Google" button pinned in a [StickySaveBar] — which brings `Spacing.screenH`
 * horizontal inset, a top scrim gradient, `navigationBarsPadding()` and `imePadding()`, so the
 * button stays above the nav-bar inset and rises with the IME while the headline scrolls.
 *
 * This reverses v0.5 decision D3 ("Daybook works fully without an account…"): first run and
 * post-reinstall now require network + an account (see plan R2).
 */
@Composable
fun SignInGateScreen(vm: AccountViewModel = hiltViewModel()) {
    // System back must not escape the gate.
    BackHandler(enabled = true) { }

    val form by vm.form.collectAsState()
    val conflict by vm.conflict.collectAsState()

    conflict?.let { info ->
        ConflictDialog(
            info = info,
            busy = form.busy,
            onRestore = { vm.resolveConflict(restoreFromCloud = true) },
            onKeep = { vm.resolveConflict(restoreFromCloud = false) },
            onDismiss = { vm.dismissConflict() }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
            .statusBarsPadding()
    ) {
        // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 2 — wave-hero redesign, adapted 2026-09-05 to match
        // the user's figma_login.png reference. The outer shape (a non-scroll Column with a
        // weighted scrollable child and a StickySaveBar pinned below, unweighted) is preserved
        // exactly; only what's inside the weighted child changes, plus this new hero above it.
        // fillMaxHeight(0.58f) lets the hero dominate more of the screen — a typical "cover"
        // illustration proportion — while still leaving room for the sheet below, even on a
        // short screen. WaveHero itself now draws its own wave-shaped cut down into
        // DaybookColors.Surface, so the Column below is a flat continuation of that same color
        // with NO rounded-corner clip — a clipped rounded top here would show as a mismatched
        // flat notch on top of the wave's curve.
        WaveHero(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.58f))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DaybookColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.screenH,
                    end = Spacing.screenH,
                    top = Spacing.xxxl,
                    bottom = Spacing.xxl
                ),
            // Anchor the headline + blurb to the bottom of the available space (just above
            // StickySaveBar) instead of letting them float right under the wave, per the
            // login-redesign fix: this was leaving an awkward empty gap down to the button.
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap, Alignment.Bottom)
        ) {
            BigHeadline("Welcome to Daybook")
            Text(
                "Sign in to sync your habits and reminders across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = DaybookColors.TextMuted
            )
        }

        StickySaveBar { GoogleSignInButton(form, vm) }
    }
}
