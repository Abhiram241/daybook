package com.daybook.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText

/**
 * The single confirmation gate in front of every destructive delete in the app (v0.5.2 §8).
 * Archive is NOT destructive and is deliberately never routed through this — it stays one tap.
 *
 * v0.5.3 Phase 4 (§4.5) — reimplemented as a thin call to [DaybookAlertDialog] so every
 * confirm/alert in the app shares one shell (Surface container, [com.daybook.app.ui.theme.AppShapes.dialog],
 * accent / Danger text buttons).
 */
@Composable
fun ConfirmDeleteDialog(
    visible: Boolean,
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Delete $itemName?",
    body: String = "This can't be undone. Its history is removed too."
) {
    if (!visible) return
    DaybookAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = { Text(body, style = DaybookText.CardSubtitle, color = DaybookColors.TextMuted) },
        confirmLabel = "Delete",
        // Dismiss first, so the dialog is already gone if confirm triggers a navigation.
        onConfirm = { onDismiss(); onConfirm() },
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        destructive = true
    )
}
