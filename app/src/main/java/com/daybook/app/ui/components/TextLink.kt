package com.daybook.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.IconSize
import com.daybook.app.ui.theme.LocalAccent

/**
 * v0.5.3 Phase 0 (§2.11 / backlog #9) — the one small tappable-text primitive. Promoted from
 * `AccountScreen.kt`'s `internal TextLink`; Phase 4 re-points the ~8 bare `Text.clickable` sites
 * (Journal/Respond "View history", Settings "Remove photo", the forms' "Remove x", the
 * `SectionHeader` action, the Home status pill) onto it. Min 44dp tap target, [Role.Button].
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(AppShapes.pill)
            .clickableImpl(remember { MutableInteractionSource() }, onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = color, modifier = Modifier.size(IconSize.Sm))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = DaybookText.ButtonLabel, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
