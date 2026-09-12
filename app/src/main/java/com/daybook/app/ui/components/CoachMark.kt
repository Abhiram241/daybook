package com.daybook.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing

/**
 * A5 (§3.6.3 a) — a minimal, one-time coach-mark built out of [UndoSnack]'s existing visual
 * recipe (same surface, border, type ramp, reduce-motion branch), differing only in that it
 * persists until dismissed and carries an action. There is no other coach-mark/tooltip component
 * in the app; this is deliberately the only one, and deliberately not reusable beyond this one
 * caller's shape (a text line + a "Got it" action).
 */
@Composable
fun BoxScope.NavCoachMark(
    text: String,
    actionLabel: String,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit
) {
    val rm = LocalReduceMotion.current
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(if (rm) snap() else Motion.fast()),
        exit = fadeOut(if (rm) snap() else Motion.fast()),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = Spacing.screenH, bottom = bottomClearance + 12.dp)
            .widthIn(max = 280.dp)
    ) {
        Column(
            Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .shadow(2.dp, AppShapes.card, clip = false, ambientColor = Color.Black.copy(0.35f), spotColor = Color.Black.copy(0.35f))
                .clip(AppShapes.card)
                .background(DaybookColors.SurfaceElevated)
                .border(1.dp, DaybookColors.Hairline, AppShapes.card)
                .padding(Spacing.cardInner),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text, style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary, maxLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(text = actionLabel, onClick = onDismiss)
            }
        }
    }
}
