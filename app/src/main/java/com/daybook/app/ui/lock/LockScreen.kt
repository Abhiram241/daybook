package com.daybook.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.ui.components.Avatar
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.clickableImpl
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent
import kotlin.math.roundToInt

/**
 * App-lock screen (v0.5.1 §K-UI). The outermost launch gate — full screen on `DaybookColors.Bg`,
 * no back affordance, `BackHandler(enabled = true) {}` so system back cannot dismiss it.
 *
 * Preserved from the logic agent's stub: the swallowing `BackHandler`, the one-shot biometric
 * auto-prompt on mount, and every state read/write going through [LockViewModel] (never the repo).
 * Decision 8: a wrong PIN shakes the dot row and clears — **no** attempt counter, **no** lockout
 * copy. Decision 9: **no** `FLAG_SECURE` / screenshot blocking anywhere.
 */
@Composable
fun LockScreen(
    activity: FragmentActivity?,
    vm: LockViewModel = hiltViewModel()
) {
    BackHandler(enabled = true) { /* system back must not dismiss the lock */ }

    val entry by vm.entry.collectAsState()
    val wrong by vm.wrongPin.collectAsState()
    val busy by vm.busy.collectAsState()
    val userName by vm.userName.collectAsState()
    var biometricOffered by rememberSaveable { mutableStateOf(false) }
    val biometricsAvailable = remember { activity != null && vm.biometricsAvailable() }

    val runBiometric: () -> Unit = {
        if (activity != null && biometricsAvailable) {
            vm.biometricGate.prompt(
                activity = activity,
                title = "Unlock Daybook",
                onSuccess = { vm.onBiometricSuccess() },
                onFallbackToPin = { }
            )
        }
    }

    // Auto-prompt once: a user who has biometrics configured should not have to tap first.
    LaunchedEffect(Unit) {
        if (!biometricOffered && biometricsAvailable) {
            biometricOffered = true
            runBiometric()
        }
    }

    // Wrong-PIN shake, then clear the one-shot flag.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong) {
            shake.snapTo(0f)
            repeat(3) {
                shake.animateTo(14f, tween(45))
                shake.animateTo(-14f, tween(45))
            }
            shake.animateTo(0f, tween(45))
            vm.clearError()
        }
    }

    // v0.5.3 Phase 5 (§5.3) — statusBarsPadding keeps the avatar off a camera cutout; the
    // verticalScroll is the fallback so the keypad can't overflow at a large font scale.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // v0.5.2 §6: profile photo → name monogram → person glyph (all handled inside Avatar).
        Avatar(
            photoPath = vm.photoPath,
            name = userName,
            size = 64.dp,
            ring = false
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Enter your PIN",
            style = MaterialTheme.typography.headlineSmall,
            color = DaybookColors.TextPrimary
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) { i ->
                val filled = i < entry.length
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                wrong -> DaybookColors.Danger
                                filled -> LocalAccent.current
                                else -> DaybookColors.SurfaceElevated
                            }
                        )
                        .then(
                            if (!filled && !wrong)
                                Modifier.border(1.dp, DaybookColors.Border, CircleShape) // v0.5.3 Phase 4 (§4.7)
                            else Modifier
                        )
                )
            }
        }
        Spacer(Modifier.height(40.dp))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            keys.chunked(3).forEach { rowKeys ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    rowKeys.forEach { k ->
                        when (k) {
                            "" -> Spacer(Modifier.size(72.dp))
                            "⌫" -> KeypadKey(
                                enabled = !busy && entry.isNotEmpty(),
                                onClick = { vm.onBackspace() }
                            ) {
                                Text(
                                    "⌫",
                                    // v0.5.3 Phase 5 (§5.3) — was headlineSmall; match the digits.
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = DaybookColors.TextPrimary
                                )
                            }
                            else -> KeypadKey(
                                enabled = !busy,
                                onClick = { vm.onDigit(k[0]) }
                            ) {
                                Text(
                                    k,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = DaybookColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (biometricsAvailable) {
            Spacer(Modifier.height(28.dp))
            GhostButton(
                text = "Unlock with fingerprint",
                onClick = runBiometric,
                modifier = Modifier.fillMaxWidth(0.85f),
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        DaybookIcons.Fingerprint,
                        contentDescription = null,
                        tint = DaybookColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        if (wrong) {
            Spacer(Modifier.height(14.dp))
            Text(
                "That PIN didn't match.",
                style = MaterialTheme.typography.bodySmall,
                // v0.5.3 Phase 5 (§5.3) — Danger red, matching the dot row (was Warning yellow).
                color = DaybookColors.Danger,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun KeypadKey(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    // v0.5.3 Phase 5 (§5.3) — CircleIconButton Ghost grammar: SurfaceElevated + 1dp Border.
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(DaybookColors.SurfaceElevated)
            .border(1.dp, DaybookColors.Border, CircleShape)
            .then(
                if (enabled)
                    Modifier.clickableImpl(remember { MutableInteractionSource() }, onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
