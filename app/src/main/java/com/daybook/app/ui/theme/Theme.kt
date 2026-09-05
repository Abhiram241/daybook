package com.daybook.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = DaybookColors.TextPrimary,
    onPrimary = DaybookColors.OnSolid,
    primaryContainer = DaybookColors.SurfaceElevated,
    onPrimaryContainer = DaybookColors.TextPrimary,
    secondary = DaybookColors.TextPrimary,
    onSecondary = DaybookColors.OnSolid,
    secondaryContainer = DaybookColors.SurfaceElevated,
    onSecondaryContainer = DaybookColors.TextPrimary,
    tertiary = DaybookColors.TextPrimary,
    onTertiary = DaybookColors.OnSolid,
    background = DaybookColors.Bg,
    onBackground = DaybookColors.TextPrimary,
    surface = DaybookColors.Surface,
    onSurface = DaybookColors.TextPrimary,
    surfaceVariant = DaybookColors.SurfaceElevated,
    onSurfaceVariant = DaybookColors.TextMuted,
    error = DaybookColors.Danger,
    onError = DaybookColors.OnSolid,
    outline = DaybookColors.Outline,
    outlineVariant = DaybookColors.Outline,
    scrim = DaybookColors.Bg
)

// v0.5.3 Phase 7 (#36) — one shape system. The M3 [Shapes] set is now derived from [AppShapes]
// so corner radii have a single source of truth. extraSmall/small/medium/large map 1:1 onto
// AppShapes.field / button / card / dialog — the radii are byte-identical to the pre-Phase-7
// literals (10 / 12 / 14 / 16 dp), so no M3 component that reads `MaterialTheme.shapes`
// (Switch track, Slider tick marks, Card, Chip, Menu, ExposedDropdownMenu, etc.) renders a
// different corner.
//
// extraLarge stays an explicit all-corners RoundedCornerShape(20.dp) rather than
// AppShapes.sheet: `sheet` is a top-corners-only shape (bottom radii 0, for a docked bottom
// sheet), but M3 maps `extraLarge` onto full-bleed dialogs (DatePickerDialog / TimePicker) that
// need all four corners rounded. ModalBottomSheet zeroes its own bottom corners regardless, so
// the docked-sheet case is unaffected. 20.dp is the same radius AppShapes.sheet uses.
private val DaybookShapes = Shapes(
    extraSmall = AppShapes.field,
    small = AppShapes.button,
    medium = AppShapes.card,
    large = AppShapes.dialog,
    extraLarge = RoundedCornerShape(20.dp)
)

/**
 * App is dark-only. [accent] is the user-selectable highlight colour (see [AccentColor]);
 * [fontChoice] swaps the app-wide typeface (see [FontChoice] / [daybookTypography]).
 */
@Composable
fun DaybookTheme(
    accent: AccentColor = AccentColor.DEFAULT,
    fontChoice: FontChoice = FontChoice.DEFAULT,
    // rec 4 — the user preference; OR-ed here with the OS ANIMATOR_DURATION_SCALE == 0 setting.
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val osNoAnim = remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        }.getOrDefault(1f) == 0f
    }
    val reduce = effectiveReduceMotion(reduceMotion, if (osNoAnim) 0f else 1f)
    val typography = remember(fontChoice) { daybookTypography(fontChoice) }
    // v0.5.3 Phase 0 (§3.12 / backlog #16) — key the scheme on `accent` so every default M3
    // control (Switch/RadioButton/Checkbox/CircularProgressIndicator/text-selection handles)
    // picks up the user accent instead of near-white `TextPrimary`.
    val scheme = remember(accent) {
        DarkScheme.copy(
            primary = accent.color,
            onPrimary = DaybookColors.OnSolid,
            secondary = accent.color,
            tertiary = accent.color
        )
    }
    CompositionLocalProvider(
        LocalAccent provides accent.color,
        LocalReduceMotion provides reduce
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = DaybookShapes,
            content = content
        )
    }
}
