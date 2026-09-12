package com.daybook.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.daybook.app.ui.components.IconTile
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.DaybookColors

/**
 * Bug fix (post-A6, deviation #1 in HEALTH_AND_WORKOUT_PROGRESS.md, now resolved) — the real
 * RepDB illustration for a builtin exercise (§3.3.3), loaded straight from the bundled asset via
 * Coil's built-in `file:///android_asset/...` support (already a dependency —
 * `ui/components/Avatar.kt` uses the same library for the profile photo). Falls back to the
 * existing taxonomy icon tile for a custom exercise (no [imageId]) or if the asset genuinely
 * fails to decode — never a broken-image glyph.
 */
@Composable
fun ExerciseThumbnail(
    imageId: String?,
    hasStartPeak: Boolean,
    fallbackIcon: ImageVector,
    tint: CardTint,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    var failed by remember(imageId) { mutableStateOf(false) }
    if (imageId != null && !failed) {
        val suffix = if (hasStartPeak) "start" else "main"
        AsyncImage(
            model = "file:///android_asset/exercises/images/flat/$imageId-$suffix.webp",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier
                .size(size)
                .clip(AppShapes.tile)
                .background(tint.fillRaised)
                .border(1.dp, DaybookColors.Hairline, AppShapes.tile)
        )
    } else {
        IconTile(icon = fallbackIcon, tint = tint, modifier = modifier, size = size)
    }
}
