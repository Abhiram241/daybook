package com.daybook.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.IconButtonSize
import com.daybook.app.ui.theme.Spacing

/**
 * v0.5.3 Phase 0 (§2.3 / backlog #3) — the pinned back header. Owns the status-bar inset +
 * [Spacing.headerInset]; the list below uses `contentPadding(top = Spacing.listTop)`. Carries a
 * compact title so context is not lost when a screen's identity block scrolls (UI Q6). Phase 4
 * adopts it on the ~8 sub-screens; nothing calls it yet.
 *
 * @param actions trailing content in the title row, laid out in a [RowScope].
 */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.screenH,
                end = Spacing.screenH,
                top = statusBarTop + Spacing.headerInset,
                bottom = Spacing.listTop
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            size = IconButtonSize.Lg.dp
        )
        Spacer(Modifier.width(Spacing.iconGap))
        Text(
            title,
            style = DaybookText.SectionTitle,
            color = DaybookColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
