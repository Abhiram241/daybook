package com.daybook.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.IconSize
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion

@Immutable
data class SegmentSpec(val key: String, val label: String, val icon: ImageVector? = null)

/**
 * v0.5.3 Phase 0 (§UI Q1 / backlog #10) — in-screen tab switching. **Visually distinct from
 * [FloatingPillNav]**: one [AppShapes.segmented] track on [DaybookColors.SurfaceElevated], equal
 * `weight(1f)` segments, the selected one a filled `accent` pill that slides on
 * [Motion.placementSpring]. Never docked, no nav-bar scrim. The icon's `contentDescription` is
 * null because the label is always visible (§3.10). Phase 5.7 adopts it on Detail; nothing calls
 * it yet.
 *
 * v0.5.3 Phase 7 (#40) — deliberately draws **no** elevation shadow: separation is the
 * [DaybookColors.SurfaceElevated] track fill against the screen background. This is what removed
 * the old `DetailBottomNav` double-shadow (a `shadow()` pill inside a `shadow()` nav bar) that
 * rendered as a muddy black halo on API 26–27, where ambient/spot shadow colours are ignored.
 */
@Composable
fun SegmentedControl(
    options: List<SegmentSpec>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val count = options.size.coerceAtLeast(1)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(AppShapes.segmented)
            .background(DaybookColors.SurfaceElevated)
            .padding(4.dp)
    ) {
        val segWidth = maxWidth / count
        val rm = LocalReduceMotion.current
        val pillX by animateDpAsState(
            targetValue = segWidth * selectedIndex,
            animationSpec = if (rm) snap() else Motion.placementSpring(),
            label = "segPill"
        )
        Box(
            Modifier
                .offset(x = pillX)
                .width(segWidth)
                .fillMaxHeight()
                .clip(AppShapes.segmented)
                .background(accent)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { spec ->
                val selected = spec.key == selectedKey
                val contentColor = if (selected) DaybookColors.OnSolid else DaybookColors.TextMuted
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AppShapes.segmented)
                        .clickableImpl(remember { MutableInteractionSource() }) { onSelect(spec.key) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (spec.icon != null) {
                        Icon(spec.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(IconSize.Sm))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        spec.label,
                        style = DaybookText.NavLabel,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
