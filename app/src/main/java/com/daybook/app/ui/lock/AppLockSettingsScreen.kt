package com.daybook.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.data.lock.LockTimeout
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRow
import com.daybook.app.ui.components.SettingsRowDivider
import com.daybook.app.ui.components.SortOption
import com.daybook.app.ui.components.SortSheet
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.settings.SettingsSubScreen
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent

/** v0.5.3 Phase 5 (§5.16) — masked-PIN digit spacing. */
private val PinTracking = 10.sp

/** One step of the enable / disable / change-PIN dialog chain. */
private sealed interface LockFlow {
    data object EnableSetPin : LockFlow
    data class EnableConfirm(val first: String) : LockFlow
    data object DisablePin : LockFlow
    data object ChangeCurrent : LockFlow
    data class ChangeNew(val current: String) : LockFlow
    data class ChangeConfirm(val current: String, val next: String) : LockFlow
}

/**
 * "App lock" settings sub-screen (v0.5.1 §K-UI). Enable/disable toggle, set/change-PIN flows, and
 * the background-lock timeout picker. Everything routes through [LockViewModel] — never the repo.
 * Decision 6: enabling always sets a 4-digit PIN (the biometric fallback); if biometric hardware
 * is present the same flow confirms a fingerprint. Decision 9: no `FLAG_SECURE` anywhere.
 */
@Composable
fun AppLockSettingsScreen(
    onNavigateBack: () -> Unit,
    vm: LockViewModel = hiltViewModel()
) {
    val enabled by vm.isEnabled.collectAsState()
    val timeout by vm.timeout.collectAsState()
    val activity = LocalContext.current as? FragmentActivity
    val biometricsAvailable = remember { activity != null && vm.biometricsAvailable() }

    var flow by remember { mutableStateOf<LockFlow?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var timeoutSheet by remember { mutableStateOf(false) }

    fun clear() { flow = null; error = null }

    fun finishEnable(pin: String) {
        val commit = {
            vm.enable(pin) { ok ->
                if (ok) clear() else error = "Couldn't set the PIN. Use 4 digits."
            }
        }
        if (activity != null && biometricsAvailable) {
            vm.biometricGate.prompt(
                activity = activity,
                title = "Confirm your fingerprint",
                subtitle = "So you can unlock Daybook with it too",
                onSuccess = commit,
                onFallbackToPin = commit
            )
        } else commit()
    }

    fun beginDisable() {
        if (activity != null && biometricsAvailable) {
            vm.biometricGate.prompt(
                activity = activity,
                title = "Confirm it's you",
                onSuccess = { vm.disable("", bypassPin = true) { } },
                onFallbackToPin = { flow = LockFlow.DisablePin }
            )
        } else {
            flow = LockFlow.DisablePin
        }
    }

    SettingsSubScreen("App lock", onNavigateBack) {
        Text(
            "Ask for a PIN" + (if (biometricsAvailable) " or fingerprint" else "") +
                " before opening Daybook. Unlimited attempts, no lockout.",
            style = DaybookText.CardSubtitle,
            color = DaybookColors.TextMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SettingsGroup {
            SettingsRow(
                icon = DaybookIcons.Lock,
                title = "App lock",
                subtitle = if (enabled) "On" else "Off",
                // v0.5.3 Phase 5 (§5.16) — the whole row toggles the switch, matching SortSheet.ArchivedRow.
                onClick = {
                    error = null
                    if (!enabled) flow = LockFlow.EnableSetPin else beginDisable()
                },
                trailing = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            error = null
                            if (on) flow = LockFlow.EnableSetPin else beginDisable()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = LocalAccent.current,
                            checkedThumbColor = DaybookColors.OnSolid
                        )
                    )
                }
            )
            if (enabled) {
                SettingsRowDivider()
                SettingsRow(
                    icon = DaybookIcons.Lock,
                    title = "Change PIN",
                    subtitle = "Set a new 4-digit PIN",
                    onClick = { error = null; flow = LockFlow.ChangeCurrent }
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = DaybookIcons.Clock,
                    title = "Lock after",
                    subtitle = timeout.label,
                    onClick = { timeoutSheet = true }
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = DaybookText.Caption, color = DaybookColors.Danger)
        }
    }

    when (val f = flow) {
        LockFlow.EnableSetPin -> PinDialog(
            title = "Set a PIN",
            body = "Choose a 4-digit PIN. You'll enter it to open Daybook.",
            error = error,
            onDismiss = ::clear,
            onSubmit = { pin -> error = null; flow = LockFlow.EnableConfirm(pin) }
        )
        is LockFlow.EnableConfirm -> PinDialog(
            title = "Confirm your PIN",
            body = "Enter the same 4 digits again.",
            error = error,
            onDismiss = ::clear,
            onSubmit = { pin ->
                if (pin != f.first) error = "Those didn't match. Try again."
                else finishEnable(pin)
            }
        )
        LockFlow.DisablePin -> PinDialog(
            title = "Enter your PIN",
            body = "Confirm your current PIN to turn the lock off.",
            error = error,
            onDismiss = ::clear,
            onSubmit = { pin ->
                vm.disable(pin) { ok -> if (ok) clear() else error = "Wrong PIN." }
            }
        )
        LockFlow.ChangeCurrent -> PinDialog(
            title = "Current PIN",
            body = "Enter your current PIN.",
            error = error,
            onDismiss = ::clear,
            onSubmit = { pin -> error = null; flow = LockFlow.ChangeNew(pin) }
        )
        is LockFlow.ChangeNew -> PinDialog(
            title = "New PIN",
            body = "Choose a new 4-digit PIN.",
            error = error,
            onDismiss = ::clear,
            onSubmit = { pin -> error = null; flow = LockFlow.ChangeConfirm(f.current, pin) }
        )
        is LockFlow.ChangeConfirm -> PinDialog(
            title = "Confirm new PIN",
            body = "Enter the new PIN again.",
            error = error,
            onDismiss = ::clear,
            onSubmit = { pin ->
                if (pin != f.next) error = "Those didn't match. Try again."
                else vm.changePin(f.current, f.next) { ok ->
                    if (ok) clear() else error = "Couldn't change the PIN — check your current one."
                }
            }
        )
        null -> Unit
    }

    SortSheet(
        visible = timeoutSheet,
        onDismiss = { timeoutSheet = false },
        title = "Lock after",
        sortOptions = LockTimeout.entries.map { SortOption(it.name, it.label) },
        selectedSortKey = timeout.name,
        onSelectSort = { vm.setTimeout(LockTimeout.valueOf(it)) },
        dismissOnSelect = true,
        // v0.5.3 Phase 5 (§5.16 / UI Q7) — a plain single choice: normal-case "Lock after" header.
        neutralHeader = true
    )
}

@Composable
private fun PinDialog(
    title: String,
    body: String,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    // v0.5.3 Phase 4 (§4.5) — routed through the shared DaybookAlertDialog shell. The masked PIN
    // entry keeps its own BasicTextField (DaybookTextField has no visualTransformation /
    // NumberPassword keyboard); the confirm callback guards on `pin.length == 4` since the shell
    // has no per-button enabled state.
    // v0.5.3 Phase 5 (§5.16) — the PIN digit tracking, named instead of a bare 10.sp literal.
    val pinTracking = PinTracking
    DaybookAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            Column {
                Text(body, style = DaybookText.Caption, color = DaybookColors.TextMuted)
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = pin,
                    onValueChange = { s -> if (s.length <= 4 && s.all(Char::isDigit)) pin = s },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = DaybookColors.TextPrimary,
                        letterSpacing = pinTracking
                    ),
                    cursorBrush = SolidColor(DaybookColors.TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.field)
                        .background(DaybookColors.SurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = DaybookText.Caption, color = DaybookColors.Danger)
                }
            }
        },
        confirmLabel = "Continue",
        onConfirm = { if (pin.length == 4) onSubmit(pin) },
        dismissLabel = "Cancel",
        onDismiss = onDismiss
    )
}
