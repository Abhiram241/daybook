package com.daybook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.Spacing

/**
 * v0.5.3 Phase 0 (§2.3 / backlog #8) — the pinned save area re-built by hand in `HabitForm`,
 * `FoodMedForm`, `JournalScreen` and `RespondScreen`. Caller places it (`Modifier.align(
 * Alignment.BottomCenter)` inside a `Box`); this owns the scrim gradient, the horizontal inset,
 * the nav-bar + IME padding and the trailing 12dp. Phase 4 migrates the four screens + gives
 * Onboarding's bar the scrim. Nothing calls it yet.
 */
@Composable
fun StickySaveBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to DaybookColors.Bg))
            .padding(start = Spacing.screenH, end = Spacing.screenH, top = 20.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        content()
        Spacer(Modifier.height(12.dp))
    }
}
