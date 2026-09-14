package com.daybook.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * UX refinement round — Item 4 (configurable corner roundness). A single global multiplier on
 * the rectangular-ish [AppShapes] tokens, driven by a slider on Settings → Appearance.
 * `1.0` == today's exact radii (D5); `segmented` / `navPill` are fixed at every scale (LD11).
 */
const val MIN_CORNER_SCALE = 0f
const val MAX_CORNER_SCALE = 1.75f
const val DEFAULT_CORNER_SCALE = 0.75f
const val CORNER_SCALE_STEP = 0.25f

/** Interior steps for the M3 Slider: 8 positions -> 6 interior. */
const val CORNER_SCALE_SLIDER_STEPS = 6

fun clampCornerScale(v: Float): Float = v.coerceIn(MIN_CORNER_SCALE, MAX_CORNER_SCALE)

@Immutable
data class DaybookShapeScheme(
    val card: RoundedCornerShape,
    val button: RoundedCornerShape,
    val field: RoundedCornerShape,
    val pill: RoundedCornerShape,
    val tile: RoundedCornerShape,
    val sheet: RoundedCornerShape,
    val nav: RoundedCornerShape,
    val dialog: RoundedCornerShape,
    val segmented: RoundedCornerShape,
    val navPill: RoundedCornerShape
)

// pure — ScaledAppShapesTest
fun scaledAppShapes(scale: Float): DaybookShapeScheme {
    val s = clampCornerScale(scale)
    fun r(dp: Float) = RoundedCornerShape((dp * s).coerceAtLeast(0f).dp)
    fun top(dp: Float) = RoundedCornerShape(
        topStart = (dp * s).coerceAtLeast(0f).dp,
        topEnd = (dp * s).coerceAtLeast(0f).dp
    )
    return DaybookShapeScheme(
        card = r(14f),
        button = r(12f),
        field = r(10f),
        pill = r(10f),
        tile = r(12f),
        sheet = top(20f),
        nav = top(18f),
        dialog = r(16f),
        segmented = RoundedCornerShape(50),   // fixed at every scale (LD11)
        navPill = RoundedCornerShape(28.dp)   // fixed at every scale (LD11)
    )
}

// pure — builds the M3 Shapes from the same scale
fun scaledM3Shapes(scale: Float): Shapes {
    val a = scaledAppShapes(scale)
    return Shapes(
        extraSmall = a.field,
        small = a.button,
        medium = a.card,
        large = a.dialog,
        extraLarge = RoundedCornerShape((20f * clampCornerScale(scale)).coerceAtLeast(0f).dp)
    )
}

/** Provided by [DaybookTheme]; defaults to scale 1.0 so previews / tests keep today's look. */
val LocalDaybookShapes = staticCompositionLocalOf { scaledAppShapes(DEFAULT_CORNER_SCALE) }
