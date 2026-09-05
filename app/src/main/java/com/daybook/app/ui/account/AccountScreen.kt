package com.daybook.app.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons as MI
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daybook.app.data.auth.AuthState
import com.daybook.app.data.sync.SyncStatus
import com.daybook.app.ui.components.DaybookAlertDialog
import com.daybook.app.ui.components.GhostButton
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.SettingsGroup
import com.daybook.app.ui.components.SettingsRow
import com.daybook.app.ui.components.SettingsRowDivider
import com.daybook.app.ui.components.SectionHeader
import com.daybook.app.ui.components.TextLink
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.settings.SettingsSubScreen
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.Spacing

/**
 * Account & sync screen (FIREBASE_0.5_PLAN.md §6). Signed-out → [SignInScreen]; signed-in →
 * [SignedInAccount]. D3 promise ("Daybook works fully without an account…") is made on the
 * signed-out screen.
 */
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    vm: AccountViewModel = hiltViewModel()
) {
    val authState by vm.authState.collectAsState()
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

    when (val st = authState) {
        is AuthState.SignedIn -> SignedInAccount(st, form, vm, onNavigateBack)
        else -> SignInScreen(form, vm, onNavigateBack)
    }
}

/* ----------------------------------------------------------------------------- signed out */

@Composable
private fun SignInScreen(
    form: AccountForm,
    vm: AccountViewModel,
    onNavigateBack: () -> Unit
) {
    // Settings → Account keeps the back button and the same pixels; the blocking launch gate
    // (SignInGateScreen) renders the same SignInContent in a full-bleed scaffold with no back.
    SettingsSubScreen("Sign in", onNavigateBack) {
        SignInContent(form, vm)
    }
}

/* ------------------------------------------------------------------------------ signed in */

@Composable
private fun SignedInAccount(
    st: AuthState.SignedIn,
    form: AccountForm,
    vm: AccountViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val syncStatus by vm.syncStatus.collectAsState()
    val canUseGooglePhoto by vm.canUseGooglePhoto.collectAsState()
    val suggestedName by vm.suggestedDisplayName.collectAsState()
    var showDelete by remember { mutableStateOf(false) }

    if (showDelete) {
        DeleteAccountDialog(
            busy = form.busy,
            onConfirm = { alsoErase -> vm.deleteAccount(alsoErase, context) },
            onDismiss = { showDelete = false }
        )
    }

    SettingsSubScreen("Account & sync", onNavigateBack) {
        // Identity. v0.5.3 Phase 5 (§5.17) — the email is an attached, copyable row now, not a
        // header subtitle.
        val identity = st.email ?: st.uid
        SectionHeader("Signed in")
        SettingsGroup {
            SettingsRow(
                icon = MI.Filled.Person,
                title = identity,
                trailing = {
                    TextLink("Copy", onClick = { clipboard.setText(AnnotatedString(identity)) })
                }
            )
        }

        Spacer(Modifier.height(Spacing.listGap))
        SectionHeader("Sync")
        SettingsGroup {
            SyncStatusRow(syncStatus) { vm.syncNow() }
        }

        if (canUseGooglePhoto || suggestedName != null) {
            Spacer(Modifier.height(Spacing.listGap))
            SectionHeader("From your Google account")
            SettingsGroup {
                if (canUseGooglePhoto) {
                    SettingsRow(
                        // v0.5.3 Phase 5 (§5.17) — a real photo glyph, not the Category placeholder.
                        icon = DaybookIcons.Image,
                        title = "Use Google photo",
                        subtitle = "Replace your profile photo with your Google picture",
                        onClick = { vm.useGooglePhoto() }
                    )
                    if (suggestedName != null) SettingsRowDivider()
                }
                suggestedName?.let { name ->
                    SettingsRow(
                        icon = MI.Filled.Person,
                        title = "Use \"$name\" as your name",
                        subtitle = null,
                        onClick = { vm.useDisplayNameAsName() }
                    )
                }
            }
        }

        // v0.5.3 Phase 5 (§5.17) — the deliberate weight difference: "Sign out" is a GhostButton
        // (reversible), "Delete account" a Danger TextLink inside the danger box (irreversible).
        Spacer(Modifier.height(Spacing.sectionGap))
        GhostButton(text = "Sign out", onClick = { vm.signOut() }, enabled = !form.busy, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(Spacing.listGap))
        // v0.5.3 Phase 5 (§5.17 / backlog #32) — SoftCard with a Danger-tinted border, not a
        // hand-rolled Column.clip.border.padding.
        SoftCard(
            tint = CardTints.Neutral,
            borderColor = DaybookColors.Danger.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp
        ) {
            Text("Danger zone", style = DaybookText.CardTitle, color = DaybookColors.Danger)
            Text(
                "Deleting your account removes all Daybook data from the cloud and can't be undone. " +
                    "Your data on this phone is kept unless you choose otherwise.",
                style = MaterialTheme.typography.bodySmall,
                color = DaybookColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            TextLink("Delete account", onClick = { showDelete = true }, color = DaybookColors.Danger)
        }

        form.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = DaybookColors.TextMuted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* -------------------------------------------------------------------------------- pieces */

@Composable
internal fun SyncStatusRow(status: SyncStatus, onSyncNow: () -> Unit) {
    val (title, subtitle) = when (status) {
        is SyncStatus.Idle -> "Synced" to relativeOrDefault(status.lastSyncedAtMillis)
        SyncStatus.Syncing -> "Syncing…" to "Talking to the cloud"
        SyncStatus.Offline -> "Offline" to "Will sync when you're back online"
        is SyncStatus.Error -> "Sync error" to (status.message ?: "Tap to retry")
        SyncStatus.Disabled -> "Sync paused" to "Sign in to sync"
        // v0.5.3 Phase 3 (finding 19): a dismissed conflict / in-flight account delete halts sync
        // for the session — was showing a stale "Synced".
        SyncStatus.Paused -> "Sync paused" to "Resolve the data conflict to resume — reopens next launch"
    }
    SettingsRow(
        icon = DaybookIcons.Backup,
        title = title,
        subtitle = subtitle,
        onClick = onSyncNow,
        trailing = { TextLink("Sync now", onClick = onSyncNow) }
    )
}

@Composable
internal fun ConflictDialog(
    info: com.daybook.app.data.sync.ConflictInfo,
    busy: Boolean,
    onRestore: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit
) {
    // v0.5.3 Phase 4 (§4.5) — routed through DaybookAlertDialog. `busy` no longer greys the
    // buttons (the shell has no enabled state); instead each callback is a no-op while busy.
    DaybookAlertDialog(
        onDismissRequest = onDismiss,
        title = "Two versions of your data",
        text = {
            Text(
                "This phone has ${info.localHabits} habits and ${info.localDays} days of history. " +
                    "Your account has ${info.remoteHabits} habits and ${info.remoteDays} days.\n\n" +
                    "Pick one — the other is replaced. This is asked once.",
                style = MaterialTheme.typography.bodyMedium,
                color = DaybookColors.TextMuted
            )
        },
        confirmLabel = "Restore from cloud",
        onConfirm = { if (!busy) onRestore() },
        dismissLabel = "Keep this device",
        onDismiss = { if (!busy) onKeep() }
    )
}

@Composable
private fun DeleteAccountDialog(
    busy: Boolean,
    onConfirm: (alsoErase: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var alsoErase by remember { mutableStateOf(false) }
    // v0.5.3 Phase 4 (§4.5) — routed through DaybookAlertDialog; the Checkbox stays in the text
    // slot. `busy` guards the confirm callback rather than disabling the button.
    DaybookAlertDialog(
        onDismissRequest = onDismiss,
        title = "Delete account?",
        text = {
            Column {
                Text(
                    "Your cloud data is deleted either way and cannot be recovered. " +
                        "By default the copy on this phone is kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DaybookColors.TextMuted
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = alsoErase,
                        onCheckedChange = { alsoErase = it },
                        colors = CheckboxDefaults.colors(checkedColor = DaybookColors.Danger)
                    )
                    Text("Also erase all Daybook data on this phone.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmLabel = "Delete",
        onConfirm = { if (!busy) onConfirm(alsoErase) },
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        destructive = true
    )
}

// v0.5.3 Phase 0 — the `internal fun TextLink` that lived here is promoted to
// ui/components/TextLink.kt and imported above.

private fun relativeOrDefault(millis: Long): String {
    if (millis <= 0L) return "Not synced yet"
    val d = System.currentTimeMillis() - millis
    val min = d / 60_000
    return when {
        min < 1 -> "Just now"
        min < 60 -> "$min min ago"
        min < 1440 -> "${min / 60} h ago"
        else -> "${min / 1440} d ago"
    }
}
