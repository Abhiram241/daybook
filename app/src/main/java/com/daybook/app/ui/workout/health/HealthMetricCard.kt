package com.daybook.app.ui.workout.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.data.health.HealthMetric
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.workout.beast.BeastText

/** AI_CHAT_PROMPT_EXCLUSIONS_HEALTH_CARDS_PLAN.md §3.2/§3.4 — the shared health metric card, used
 *  by both the Health tab and (in [compact] mode) the Daily Report Health section.
 *
 *  User bug-fix round (post-plan): the original layout pinned a single-metric ("hero") card's
 *  value to the bottom via `Arrangement.SpaceBetween` on a `fillMaxHeight()` column — on a real
 *  device, when its row-neighbor had 2 metrics (and one wrapped its unit onto a second line), the
 *  intrinsic-height-matched hero card stretched to match and the gap between its header and value
 *  became huge. Fixed by top-aligning content instead (excess space, if any, now sits quietly
 *  below the content, not stretched into it) and by giving every metric its own label-above-
 *  value-below block (never a label/value competing for width on one row), which also removes the
 *  wrapping: a value+unit no longer has to share a row with a label that can crowd it out.
 */
@Composable
fun HealthMetricCard(
    title: String,
    metrics: List<HealthMetric>,
    tint: CardTint,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val description = "$title, " + metrics.joinToString(", ") {
        it.label + " " + it.value + (it.unit?.let { u -> " $u" } ?: "")
    }
    val sizeMod = if (compact) {
        modifier.fillMaxWidth()
    } else {
        modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 108.dp)
    }
    SoftCard(
        tint = tint,
        onClick = onClick,
        contentPadding = if (compact) 14.dp else 16.dp,
        modifier = sizeMod.semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(
            if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(tint.accent.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = tint.accent, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(
                        title, style = DaybookText.CardTitle, color = tint.onFill,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = DaybookText.Caption, color = tint.onFillMuted)
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            if (metrics.isEmpty()) {
                Spacer(Modifier.height(1.dp))
            } else if (metrics.size == 1) {
                HeroMetric(metrics[0], tint)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.forEach { m -> MetricBlock(m, tint) }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(metric: HealthMetric, tint: CardTint) {
    Column {
        Text(metric.label, style = DaybookText.Caption, color = tint.onFillMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(metric.value, style = BeastText.BigNumber, color = tint.onFill, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (metric.unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    metric.unit, style = DaybookText.Caption, color = tint.onFillMuted,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

/** A single label-above-value block — used for every row of a 2+-metric card. Full card width is
 *  available to the value+unit line (nothing competing for space on the same row), so a range like
 *  "57–82 bpm" fits on one line at default and slightly-scaled font sizes. */
@Composable
private fun MetricBlock(metric: HealthMetric, tint: CardTint) {
    Column {
        Text(metric.label, style = DaybookText.Caption, color = tint.onFillMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(metric.value, style = BeastText.TileNumber, color = tint.onFill, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (metric.unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    metric.unit, style = DaybookText.Caption, color = tint.onFillMuted,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
