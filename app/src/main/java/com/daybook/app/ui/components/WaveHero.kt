package com.daybook.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent

/**
 * LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 2 — the sign-in screen's themed hero, replacing the old
 * static coral mockup. Adapted from the user's figma_login.png reference: a solid [LocalAccent]
 * fill, a dense field of thin wavy "topographic contour" strokes across the whole panel (not a
 * few translucent bands), and one broad wave cut at the bottom that blends straight into the sheet
 * below — no rounded-corner clip, no icon overlaid on the illustration (the reference has neither).
 * Pure Compose drawing (no static asset) so it re-renders correctly the instant the user's chosen
 * [com.daybook.app.ui.theme.AccentColor] changes.
 */
@Composable
fun WaveHero(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val heightPx = constraints.maxHeight.toFloat()
        val widthPx = constraints.maxWidth.toFloat()
        val accent = LocalAccent.current
        val sheetColor = DaybookColors.Surface

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = widthPx.takeIf { it > 0f } ?: size.width
            val h = heightPx.takeIf { it > 0f } ?: size.height

            // 1) Solid accent fill for the whole hero — the reference is one flat color panel,
            // not a dark background with faded shapes floating on it.
            drawRect(color = accent, size = Size(w, h))

            // 2) Topographic contour lines: a dense stack of thin wavy strokes, each a gentle
            // two-hump S-curve, alternating phase so neighbouring lines don't read as parallel
            // copies of each other — mimics an elevation-contour / engraved-line texture.
            val contourColor = Color.Black.copy(alpha = 0.09f)
            val strokeWidth = 1.4.dp.toPx()
            val lineCount = 13
            val topInset = h * 0.04f
            val bottomInset = h * 0.66f // leave the lower third clear for the wave-to-sheet cut
            val bandHeight = (bottomInset - topInset) / lineCount
            for (i in 0 until lineCount) {
                val baseline = topInset + bandHeight * i
                val amplitude = bandHeight * (0.35f + (i % 3) * 0.12f)
                val phase = if (i % 2 == 0) 1f else -1f
                val path = Path().apply {
                    moveTo(-w * 0.05f, baseline)
                    cubicTo(
                        w * 0.18f, baseline - amplitude * phase,
                        w * 0.32f, baseline + amplitude * phase,
                        w * 0.5f, baseline
                    )
                    cubicTo(
                        w * 0.68f, baseline - amplitude * phase,
                        w * 0.82f, baseline + amplitude * phase,
                        w * 1.05f, baseline
                    )
                }
                drawPath(path, color = contourColor, style = Stroke(width = strokeWidth))
            }

            // 3) A few small dot accents scattered in the upper area, echoing the reference's
            // sparse sparkle marks.
            val dotColor = Color.White.copy(alpha = 0.35f)
            listOf(
                Offset(w * 0.80f, h * 0.10f) to 3.dp.toPx(),
                Offset(w * 0.88f, h * 0.18f) to 2.dp.toPx(),
                Offset(w * 0.15f, h * 0.08f) to 2.dp.toPx()
            ).forEach { (center, radius) ->
                drawCircle(color = dotColor, radius = radius, center = center)
            }

            // 4) The single broad wave that cuts the hero's bottom away in favour of the sheet
            // color — this IS the boundary into the scrollable content below, drawn here so it's
            // a true wave edge rather than a rounded-rectangle clip on the sheet Column.
            val waveBaseline = h * 0.72f
            val waveAmplitude = h * 0.05f
            val wavePath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, waveBaseline)
                cubicTo(
                    w * 0.25f, waveBaseline - waveAmplitude,
                    w * 0.40f, waveBaseline + waveAmplitude,
                    w * 0.60f, waveBaseline
                )
                cubicTo(
                    w * 0.80f, waveBaseline - waveAmplitude * 0.8f,
                    w * 0.92f, waveBaseline + waveAmplitude * 0.6f,
                    w, waveBaseline
                )
                lineTo(w, h)
                close()
            }
            drawPath(wavePath, color = sheetColor)
        }
    }
}
