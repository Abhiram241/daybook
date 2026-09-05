package com.daybook.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import kotlinx.coroutines.delay

/**
 * A one-line transient toast pinned near the bottom of a screen. Shown for ~2.6s after [token]
 * changes, then fades. No action button — the undo already ran on tap.
 */
@Composable
fun BoxScope.UndoSnack(token: Int, text: String = "Undone") {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        if (token == 0) return@LaunchedEffect
        visible = true
        delay(2600)
        visible = false
    }
    val rm = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(if (rm) snap() else Motion.fast()), // v0.5.3 Phase 4 (§4.7)
        exit = fadeOut(if (rm) snap() else Motion.fast()),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
    ) {
        Row(
            Modifier
                .clip(AppShapes.pill)
                .background(DaybookColors.SurfaceElevated)
                .border(1.dp, DaybookColors.Hairline, AppShapes.pill)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, style = DaybookText.CardSubtitle, color = DaybookColors.TextPrimary)
        }
    }
}
