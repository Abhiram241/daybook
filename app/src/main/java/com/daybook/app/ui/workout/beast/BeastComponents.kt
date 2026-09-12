package com.daybook.app.ui.workout.beast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.components.IconTile
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.components.clickableImpl
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing

/**
 * Beast-only components (BEAST_MODE_REDESIGN_PLAN.md §3). Pure Compose/UI layer — nothing here
 * touches a ViewModel, repository, or the DB.
 */

/**
 * A stroke-based circular progress gauge (§3.2), à la Apple's activity rings. [progress] is
 * clamped to 0..1 — callers that want an "ever-filling" ring past a nominal baseline (e.g. the
 * live session's elapsed-time ring) should pre-fold their own value into that range (a lap past
 * 1.0 simply reads as a full ring, which is the deliberately simple resolution to the plan's open
 * question 3 — no separate "lap" animation).
 */
@Composable
fun RingStat(
    progress: Float,
    ringColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = DaybookColors.SurfaceElevated,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animatedProgress by if (animate) {
        animateFloatAsState(clamped, Motion.lowSpring(), label = "ringProgress")
    } else remember(clamped) { androidx.compose.runtime.mutableStateOf(clamped) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (animatedProgress > 0f) {
                drawArc(
                    color = ringColor, startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** A 2-column tinted grid tile (§3.3) — icon + big number + label, built on the existing
 *  [CardTint] system so it can pick up any pastel tint, not just Neutral. */
@Composable
fun StatGridTile(
    icon: ImageVector,
    value: String,
    label: String,
    tint: CardTint,
    modifier: Modifier = Modifier
) {
    SoftCard(tint = tint, modifier = modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = icon, tint = tint, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, style = BeastText.TileNumber, color = tint.onFill, maxLines = 1)
                Text(label, style = DaybookText.Caption, color = tint.onFillMuted, maxLines = 1)
            }
        }
    }
}

data class StatGridItem(val icon: ImageVector, val value: String, val label: String, val tint: CardTint)

/** Lays [items] out 2-per-row (§3.3). An odd final item spans alone rather than stretching to fill
 *  the row — matches how the rest of the app leaves a trailing odd tile un-stretched. */
@Composable
fun StatGrid(items: List<StatGridItem>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { item ->
                    StatGridTile(
                        icon = item.icon, value = item.value, label = item.label, tint = item.tint,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A heavier CTA variant (§3.6) for Beast Mode's primary actions: sharper corners, an accent-glow
 *  shadow (using [BeastPalette.hotAccent]), bolder label. Same click target/behaviour as
 *  [com.daybook.app.ui.components.PrimaryButton] — this is a skin, not a new interaction. */
@Composable
fun BeastPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pill: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, Motion.pressSpring(), label = "beastBtnScale")
    val accent = LocalAccent.current
    val glow = BeastPalette.hotAccent()
    val shape = if (pill) AppShapes.pill else RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (!pill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 52.dp)
            .shadow(
                10.dp, shape, clip = false,
                ambientColor = glow.copy(alpha = 0.5f), spotColor = glow.copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(if (enabled) accent else DaybookColors.SurfaceElevated)
            .clickableImpl(interaction) { if (enabled) onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) DaybookColors.OnAccent else DaybookColors.TextFaint,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

/** A small gold-tinted "PR" chip (§3.5), replacing the plain colour-swap-only star. */
@Composable
fun PrBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(DaybookColors.Warning.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            "PR", style = DaybookText.Caption.copy(fontWeight = FontWeight.Bold),
            color = DaybookColors.Warning
        )
    }
}

/** A one-shot scale/glow pulse (§3.5), played once each time [trigger] flips from false to true —
 *  the moment a set is marked a PR. Reuses [Motion.pressSpring] rather than a new animation
 *  system. Wrap the row content in this modifier. */
@Composable
fun Modifier.prCelebration(trigger: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            scale.animateTo(1.06f, Motion.pressSpring())
            scale.animateTo(1f, Motion.pressSpring())
        }
    }
    return this.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * Bug fix — the session screen's docked Discard/Finish bar floated with no backing of its own, so
 * scrolled list content (an exercise name, a card's edge) showed straight through/behind it,
 * reading as a layout bug rather than a footer. Mirrors
 * [com.daybook.app.ui.components.StickySaveBar]'s scrim-gradient/inset/trailing-space recipe, but
 * fades to [BeastPalette.ground] instead of the shared [DaybookColors.Bg] so the seam matches this
 * screen's own (darker) ground instead of the app-wide one.
 */
@Composable
fun BeastStickyBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val ground = BeastPalette.ground()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to ground))
            .padding(start = Spacing.screenH, end = Spacing.screenH, top = 24.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        content()
        Spacer(Modifier.height(12.dp))
    }
}
