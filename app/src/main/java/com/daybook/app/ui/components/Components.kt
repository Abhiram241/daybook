package com.daybook.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing

private val CardShape = AppShapes.card

/** The one card primitive. */
@Composable
fun SoftCard(
    tint: CardTint,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.cardInner,
    elevation: Dp = 1.dp,
    // v0.5.3 Phase 0 (§2.6 / backlog #32) — default is the Hairline alias, so unchanged.
    // The Account "Danger zone" box folds into SoftCard(..., borderColor = Danger.copy(0.4f)).
    borderColor: Color = DaybookColors.Border,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // rec 4 — swap the spec feeding the scale for snap() (the graphicsLayer itself is untouched;
    // the yellow-blob GPU-leak fix from v0.5.1 §9 depends on it staying).
    val rm = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, if (rm) snap() else Motion.pressSpring(), label = "cardScale")
    val fill by animateColorAsState(if (pressed) tint.fillRaised else tint.fill, label = "cardFill")

    val clickMod = if (onClick != null) {
        Modifier.clickableImpl(interaction, onClick)
    } else Modifier

    // The lambda-taking graphicsLayer defers its reads to the draw phase and doesn't
    // recompose; applying it unconditionally keeps the modifier chain a constant length
    // (a conditional swap rebuilt the coordinator chain twice per tap — REV-29).
    val scaleMod = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }

    // clip = false here: the single .clip(CardShape) below already clips content to the
    // shape in every case (including elevation == 0, where there's no shadow modifier).
    // v0.5.3 Phase 7 (#40) — cap the shadow radius at 2.dp. On API 26–27 the elevation
    // shadow ignores ambient/spot colour and renders a flat black projection; stacked cards
    // with a larger radius produce a "muddy halo" between them. Every current caller passes
    // elevation 0.dp or the 1.dp default, so this is a no-op guard that bounds the worst case.
    val shadowElevation = elevation.coerceAtMost(2.dp)
    val shadowMod = if (shadowElevation > 0.dp) {
        Modifier.shadow(shadowElevation, CardShape, clip = false, ambientColor = Color.Black.copy(0.35f), spotColor = Color.Black.copy(0.35f))
    } else Modifier

    Column(
        modifier = modifier
            .then(scaleMod)
            .then(shadowMod)
            .clip(CardShape)
            .background(fill)
            .border(1.dp, borderColor, CardShape)
            .then(clickMod)
            .padding(contentPadding),
        content = content
    )
}

internal fun Modifier.clickableImpl(
    interaction: MutableInteractionSource,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick
)

enum class CircleStyle { Ghost, Solid, Tonal, Success, Warn, Danger }

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CircleStyle = CircleStyle.Ghost,
    size: Dp = 44.dp,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.92f else 1f, Motion.pressSpring(), label = "btnScale")
    val accent = LocalAccent.current

    val (bg, fg, border) = when (style) {
        CircleStyle.Ghost -> Triple(DaybookColors.SurfaceElevated, DaybookColors.TextPrimary, DaybookColors.Hairline)
        // v0.5.5 — Solid stays a full-saturation fill for FABs, which are meant to read as bold/
        // prominent. Everything else that used to reach for Solid (reply/send/write-entry
        // buttons on a card) reads as a glaring, "neon" full-accent circle at that smaller size —
        // Tonal gives the same accent identity as a soft accent-tinted fill instead, matching the
        // Success/Warn/Danger pattern already used everywhere else in this file.
        CircleStyle.Solid -> Triple(accent, DaybookColors.OnSolid, Color.Transparent)
        CircleStyle.Tonal -> Triple(accent.copy(alpha = 0.16f), accent, Color.Transparent)
        CircleStyle.Success -> Triple(DaybookColors.Success.copy(alpha = 0.16f), DaybookColors.Success, Color.Transparent)
        CircleStyle.Warn -> Triple(DaybookColors.Warning.copy(alpha = 0.16f), DaybookColors.Warning, Color.Transparent)
        CircleStyle.Danger -> Triple(DaybookColors.Danger.copy(alpha = 0.16f), DaybookColors.Danger, Color.Transparent)
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) bg else DaybookColors.SurfaceElevated)
            .border(1.dp, border, CircleShape)
            .clickableImpl(interaction) { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) fg else DaybookColors.TextFaint,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

@Composable
fun IconTile(
    icon: ImageVector,
    tint: CardTint,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    // rec 4 (X5) — supply the owning item's title so TalkBack announces the reminder icon.
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AppShapes.tile)
            .background(tint.fillRaised)
            .border(1.dp, DaybookColors.Hairline, AppShapes.tile),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.accent,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

enum class ChipSize { Medium, Small }

@Composable
fun DaybookChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    size: ChipSize = ChipSize.Medium,
    leadingIcon: ImageVector? = null
) {
    val height = if (size == ChipSize.Medium) 34.dp else 26.dp
    val accent = LocalAccent.current
    val bg by animateColorAsState(
        if (selected) accent else DaybookColors.SurfaceElevated, label = "chipBg"
    )
    val fg = if (selected) DaybookColors.OnSolid else DaybookColors.TextMuted
    Row(
        modifier = modifier
            .height(height)
            .clip(AppShapes.pill)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else DaybookColors.Hairline, AppShapes.pill)
            // v0.5.3 Phase 4 (§4.7) — choice affordance: RadioButton role + selected state; when a
            // count badge follows the label, merge the two into one spoken string.
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .then(
                if (count != null) Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$label, $count"
                } else Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) DaybookColors.OnSolid.copy(alpha = 0.12f) else DaybookColors.TextMuted.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = fg)
            }
        }
    }
}

/**
 * v0.5.3 Phase 0 (§1.9 / §3.2 / backlog #6) — the one section header. Merges the old
 * `SectionHeader` with `SettingsSectionHeader` (now a thin alias, removed in Phase 4). Title →
 * [DaybookText.SectionTitle], subtitle → [DaybookText.CardSubtitle], action → [TextLink] (44dp).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = DaybookText.SectionTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            trailing?.invoke(this)
            if (actionLabel != null && onAction != null) {
                TextLink(actionLabel, onClick = onAction)
            }
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = DaybookText.CardSubtitle,
                color = DaybookColors.TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun BigHeadline(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle? = null,
    accentWord: String? = null
) {
    val accent = LocalAccent.current
    val resolved = style ?: MaterialTheme.typography.displayMedium
    // v0.5.3 Phase 7 (#39) — guard the accent span: require a non-empty accentWord that actually
    // occurs in `text` (an empty string used to slip past `contains` and emit a zero-width styled
    // span). Only the FIRST occurrence is highlighted — a repeated word leaves later matches
    // unstyled by design. `resolved` is in the remember key so a style swap rebuilds the string.
    if (!accentWord.isNullOrEmpty() && text.contains(accentWord)) {
        val annotated = remember(text, accentWord, accent, resolved) {
            buildAnnotatedString {
                val i = text.indexOf(accentWord)
                append(text.substring(0, i))
                withStyle(SpanStyle(color = accent)) { append(accentWord) }
                append(text.substring(i + accentWord.length))
            }
        }
        Text(
            text = annotated,
            style = resolved,
            color = DaybookColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.padding(top = 8.dp, bottom = 16.dp)
        )
    } else {
        Text(
            text = text,
            style = resolved,
            color = DaybookColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.padding(top = 8.dp, bottom = 16.dp)
        )
    }
}

@Composable
fun PastelProgressBar(
    progress: Float,
    tint: CardTint,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color? = null
) {
    val rm = LocalReduceMotion.current
    val p by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        if (rm) snap() else Motion.lowSpring(),
        label = "progress"
    )
    val track = tint.onFill.copy(alpha = 0.12f)
    val fillColor = color ?: tint.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val r = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = track, cornerRadius = r)
                if (p > 0f) {
                    drawRoundRect(
                        color = fillColor,
                        size = size.copy(width = size.width * p),
                        cornerRadius = r
                    )
                }
            }
    )
}

@Composable
fun StatPill(
    icon: ImageVector,
    value: String,
    tint: CardTint,
    modifier: Modifier = Modifier,
    label: String? = null,
    // rec 4 (X5) — a single spoken phrase for the whole pill (e.g. "5 day streak").
    contentDescription: String? = null
) {
    Row(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(tint.fillRaised)
            .border(1.dp, DaybookColors.Hairline, AppShapes.pill)
            .then(
                if (contentDescription != null)
                    Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint.onFill)
        if (label != null) {
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint.onFillMuted)
        }
    }
}

/**
 * v0.5.3 Phase 0 (§4.4 / backlog #4) — a non-interactive time/label pill. Replaces
 * `DaybookChip(selected = false, onClick = {})` on the Habit/Intake cards, which advertised a
 * fake tap target. No `clickable`, inert semantics. Phase 4 adopts it on the cards.
 */
@Composable
fun TimeTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(DaybookColors.SurfaceElevated)
            .border(1.dp, DaybookColors.Border, AppShapes.pill)
            .semantics { }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = DaybookText.Caption, color = DaybookColors.TextMuted, maxLines = 1)
    }
}

/**
 * v0.5.3 Phase 5 (§5.13 / backlog #24) — the one colour-swatch primitive shared by the Appearance
 * accent picker and the [TintPicker]. Rounded-square ([AppShapes.tile]), a 44dp touch target
 * around a 40dp visual dot, a selected accent ring, and a [Check] when selected (no inner dot on
 * the unselected state). [label] is for the "A" auto swatch; [checkColor] flips for dark vs light
 * swatch fills.
 */
@Composable
fun Swatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    checkColor: Color = DaybookColors.OnSolid,
    // rec 4 (X5) — the colour's name, so TalkBack doesn't announce an unlabeled square.
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(AppShapes.tile)
            .then(
                if (contentDescription != null)
                    Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                else Modifier
            )
            .clickableImpl(remember { MutableInteractionSource() }, onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(AppShapes.tile)
                .background(color)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) DaybookColors.TextPrimary else DaybookColors.Border,
                    AppShapes.tile
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                selected -> Icon(Icons.Filled.Check, contentDescription = "Selected", tint = checkColor, modifier = Modifier.size(18.dp))
                label != null -> Text(label, style = MaterialTheme.typography.labelLarge, color = DaybookColors.TextMuted)
            }
        }
    }
}

/**
 * v0.5.3 Phase 5 (§5.5) — a tiny inline badge (the Habit card "B" batch marker). Reuses the
 * [TimeTag] shell language (pill, hairline) at a smaller footprint; the caller supplies the
 * `contentDescription`.
 */
@Composable
fun MiniBadge(
    text: String,
    tint: CardTint,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(tint.accent.copy(alpha = 0.16f))
            .border(1.dp, DaybookColors.Border, AppShapes.pill)
            .then(
                if (contentDescription != null)
                    Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = DaybookText.Caption, color = tint.accent, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    /**
     * v0.5.3 Phase 5 (§5.2) — optional leading slot, laid out before the label. Pass an [Image]
     * (not [Icon]) for a multicolour brand mark so it keeps its own colours (the Google "G" on the
     * sign-in gate).
     */
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, Motion.pressSpring(), label = "pbScale")
    val accent = LocalAccent.current
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .height(50.dp)
            .clip(AppShapes.button)
            .background(if (enabled) accent else DaybookColors.SurfaceElevated)
            .clickableImpl(interaction) { if (enabled && !loading) onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = DaybookColors.OnSolid, modifier = Modifier.size(18.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) DaybookColors.OnSolid else DaybookColors.TextFaint
                )
            }
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    /**
     * Optional leading slot, laid out before the label. Pass an [Image] (not [Icon]) for a
     * multicolour brand mark so it is not flattened to the content colour — v0.5.1 §E.
     */
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, Motion.pressSpring(), label = "gbScale")
    // v0.5.3 Phase 0 (§3.5 / backlog #33) — no baked `.fillMaxWidth()`. Inside a Row next to a
    // weighted sibling the button now sizes to content; full-width callers pass
    // `Modifier.fillMaxWidth()` themselves.
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(50.dp)
            .clip(AppShapes.button)
            .border(BorderStroke(1.5.dp, DaybookColors.Hairline), AppShapes.button)
            .clickableImpl(interaction) { if (enabled && !loading) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xl), // fix: shrink-wrap callers (no fillMaxWidth) hugged the label with zero margin
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null && !loading) {
                leadingIcon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (loading) "…" else text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) DaybookColors.TextPrimary else DaybookColors.TextFaint
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    // v0.5.3 Phase 0 (§3.7 / backlog #35) — the icon tile was hard-wired to Mint. Default kept
    // at Mint so Phase 0 is a no-op visually; Phase 4 passes per-domain curated tints.
    tint: CardTint = CardTints.Mint
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconTile(icon = icon, tint = tint, size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = DaybookColors.TextPrimary, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextMuted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}
