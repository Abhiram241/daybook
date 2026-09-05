package com.daybook.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent

/**
 * v0.5.3 Phase 0 (§2.10 / backlog #15) — the shared confirm/alert wrapper. `containerColor =
 * Surface`, `shape = AppShapes.dialog`, title on [DaybookText.DialogTitle], text-button content
 * colour = accent (or [DaybookColors.Danger] when [destructive]). Phase 4 re-points
 * `ConfirmDeleteDialog`, MainActivity's rationale + exact-alarm dialogs, `ConflictDialog`,
 * `DeleteAccountDialog` and `PinDialog` onto it. Nothing calls it yet.
 */
@Composable
fun DaybookAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    val confirmColor = if (destructive) DaybookColors.Danger else LocalAccent.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.dialog,
        title = { Text(title, style = DaybookText.DialogTitle, color = DaybookColors.TextPrimary) },
        text = text,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = confirmColor) }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                TextButton(onClick = { (onDismiss ?: onDismissRequest)() }) {
                    Text(label, color = DaybookColors.TextPrimary)
                }
            }
        }
    )
}
