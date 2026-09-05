package com.daybook.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.Spacing

/**
 * The one pinned screen header. Owns the status-bar inset; the list below it uses
 * `contentPadding(top = 4.dp)`. Habits and Intake render this identically so their titles,
 * first-card Y and scroll behaviour match exactly (Section 4).
 *
 * @param actions trailing content in the title row (avatar, filter button…), laid out in a
 *   [RowScope] so callers can add their own [androidx.compose.foundation.layout.Spacer]s.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenH, end = Spacing.screenH, top = statusBarTop + 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigHeadline(title, modifier = Modifier.weight(1f))
            actions()
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DaybookColors.TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
        }
    }
}
