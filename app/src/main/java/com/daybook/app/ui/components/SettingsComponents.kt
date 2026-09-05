package com.daybook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent

/**
 * Settings design-language primitives (borrowed structure from the reference boards,
 * rendered with [DaybookColors] + [LocalAccent] for the dark-only theme).
 */

// v0.5.3 Phase 4 (§4.4 / §4.11) — the `SettingsSectionHeader` forwarding alias is gone; all
// callers now use the merged `SectionHeader` directly.

/** Rounded neutral card that wraps a vertical stack of [SettingsRow]s with hairline dividers. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    SoftCard(
        tint = CardTints.Neutral,
        modifier = modifier,
        elevation = 0.dp,
        contentPadding = 0.dp,
        content = content
    )
}

/** Thin divider between rows inside a [SettingsGroup]. */
@Composable
fun SettingsRowDivider() {
    HorizontalDivider(
        color = DaybookColors.Hairline,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 70.dp)
    )
}

@Composable
private fun ChevronIcon() {
    Icon(
        DaybookIcons.ChevronRight,
        contentDescription = null,
        tint = DaybookColors.TextFaint,
        modifier = Modifier.size(20.dp)
    )
}

/**
 * A single settings row: circular accent icon badge + title + optional subtitle + a
 * trailing slot (chevron by default; a Switch or custom content for toggle/action rows).
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = { ChevronIcon() }
) {
    val clickMod = if (onClick != null) {
        Modifier.clickableImpl(remember { MutableInteractionSource() }, onClick)
    } else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickMod)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                // v0.5.3 Phase 4 (§4.10) — was a solid accent circle ×N per screen, which read as
                // texture. Tinted 16% fill + accent glyph keeps the interactive-row affordance
                // while dropping the visual weight.
                .background(LocalAccent.current.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = LocalAccent.current,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = DaybookColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DaybookColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/**
 * Profile block for the Settings hub (v0.5.1 §L, mockup screenshot 3): a gradient rounded card,
 * a centred circular [Avatar] with a dark ring (Google photo when present, else accent monogram),
 * a large name, a "Your Daybook" subtitle and an outlined **Edit Profile** pill.
 *
 * SD-1: the `‹ ›` chevrons that flanked the name in the mockup are **gone** — the user decided
 * against them, not "decorative". [onPickPhoto] is the avatar tap; [onEditName] is the pill.
 */
@Composable
fun ProfileHeader(
    name: String,
    subtitle: String,
    photoPath: String?,
    onEditName: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trimmed = name.trim()
    val accent = LocalAccent.current
    // v0.5.3 Phase 5 (§5.12) — the shared pill token + 1dp Border edge role, not RoundedCornerShape(50) + Outline.
    val pill = AppShapes.pill
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.28f), DaybookColors.Surface)
                )
            )
            .border(1.dp, DaybookColors.Hairline, AppShapes.card)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .border(3.dp, DaybookColors.Bg, CircleShape)
                .padding(3.dp)
        ) {
            Avatar(
                photoPath = photoPath,
                name = name,
                size = 96.dp,
                ring = false,
                onClick = onPickPhoto
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (trimmed.isEmpty()) "Add your name" else trimmed,
            style = MaterialTheme.typography.headlineMedium,
            color = DaybookColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = DaybookColors.TextMuted
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .wrapContentWidth()
                .clip(pill)
                .border(1.dp, DaybookColors.Border, pill) // v0.5.3 Phase 4 (§4.7) — 1dp edge role
                .clickableImpl(remember { MutableInteractionSource() }, onEditName)
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                "Edit Profile",
                style = MaterialTheme.typography.labelLarge,
                color = DaybookColors.TextPrimary
            )
        }
    }
}
