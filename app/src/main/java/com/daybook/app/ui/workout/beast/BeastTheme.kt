package com.daybook.app.ui.workout.beast

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalIsDark

/**
 * User request — "there is no new accent colors, add that also for beast mode": Beast Mode's
 * accent picker used to just reuse the app-wide [com.daybook.app.ui.theme.AccentColor] enum
 * (same 5 colours as Settings > Appearance). This is a separate, Beast-only palette — the
 * original 5 are kept unchanged (byte-identical [storageKey]s and values) so an install that
 * already picked one of them keeps looking the same and the Room column's own SQL
 * `defaultValue = "CORAL"` still resolves; 4 new, more saturated "gym" colours are added on top.
 * Persisted in the SAME `app_settings.workout_accent_color` column as before (a plain String —
 * no schema change) via [storageKey]; [fromKey] falls back to the historical default so an
 * unrecognised/blank value never crashes.
 */
enum class BeastAccentColor(val storageKey: String, val dark: Color, val light: Color) {
    MINT("MINT", Color(0xFF2DD4BF), Color(0xFF0F9488)),
    LAVENDER("LAVENDER", Color(0xFFA78BFA), Color(0xFF7C5CE0)),
    CORAL("CORAL", Color(0xFFFB7185), Color(0xFFE23D5B)),
    SKY("SKY", Color(0xFF60A5FA), Color(0xFF2563EB)),
    AMBER("AMBER", Color(0xFFFBBF24), Color(0xFFB7791F)),
    // New — Beast-only, not offered anywhere in the main app's Settings.
    CRIMSON("CRIMSON", Color(0xFFF43F5E), Color(0xFFBE123C)),
    ELECTRIC("ELECTRIC", Color(0xFF22D3EE), Color(0xFF0E7490)),
    VOLT("VOLT", Color(0xFFA3E635), Color(0xFF4D7C0F)),
    VIOLET("VIOLET", Color(0xFF8B5CF6), Color(0xFF6D28D9));

    fun colorFor(dark: Boolean): Color = if (dark) this.dark else this.light

    companion object {
        // User request — Beast Mode's default accent is AMBER (was CORAL; still selectable).
        val DEFAULT = AMBER
        fun fromKey(k: String?): BeastAccentColor = entries.firstOrNull { it.storageKey == k } ?: DEFAULT
    }
}

/**
 * Beast-only design tokens (BEAST_MODE_REDESIGN_PLAN.md §3). Scoped to `ui/workout` — nothing
 * outside Beast Mode reads from this file, and none of the shared `ui/theme` tokens are touched.
 * Light theme deliberately keeps today's flat ground ([DaybookColors.Bg]); only dark mode gets the
 * deeper near-black + accent vignette (plan §3.1, open question 5 resolved this way).
 */
object BeastPalette {
    private val groundDark = Color(0xFF06070A)

    /** The Beast Mode screen ground — deeper than [DaybookColors.Bg] in dark mode only. */
    @Composable
    fun ground(): Color = if (LocalIsDark.current) groundDark else DaybookColors.Bg

    /** A subtle top-down accent-tinted vignette over [ground] (dark mode only — a flat fill in light). */
    @Composable
    fun groundBrush(): Brush {
        val accent = hotAccent()
        return if (LocalIsDark.current) {
            Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), groundDark, groundDark))
        } else {
            Brush.verticalGradient(listOf(DaybookColors.Bg, DaybookColors.Bg))
        }
    }

    /** A brighter/more saturated version of the user's chosen [LocalAccent], for glows/rings only —
     *  the base accent picker in Settings is untouched. Dark mode only; light mode keeps the plain
     *  accent since it already sits on a light ground with plenty of contrast headroom. */
    @Composable
    fun hotAccent(): Color {
        val accent = LocalAccent.current
        return if (LocalIsDark.current) brighten(accent) else accent
    }

    private fun brighten(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (c.red * 255f).toInt().coerceIn(0, 255),
            (c.green * 255f).toInt().coerceIn(0, 255),
            (c.blue * 255f).toInt().coerceIn(0, 255),
            hsv
        )
        hsv[1] = (hsv[1] * 0.85f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.25f).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

/** The bold, tabular-figure numeral treatment (§3.4) for any number that matters — elapsed time,
 *  total volume, a set's weight×reps, a PR value. Everywhere else in Beast Mode keeps today's
 *  [com.daybook.app.ui.theme.DaybookText] roles unchanged. */
object BeastText {
    val BigNumber: TextStyle
        @Composable get() = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
        )

    /** A smaller big-number for tiles/grids where the full [BigNumber] size would crowd. */
    val TileNumber: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
            fontSize = 20.sp
        )

    val MicroLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
}

/**
 * User request — "proper bold fonts ... that will only apply on beast mode." Takes the app's
 * currently-active [Typography] (so it still respects the user's [com.daybook.app.ui.theme.FontChoice]
 * and every size/line-height) and bumps only the label/title roles to the heaviest weight the
 * bundled variable fonts actually declare (Bold/700 — every [com.daybook.app.ui.theme.FontChoice]
 * family stops at 700, so requesting [FontWeight.Black] here would silently just resolve back to
 * Bold; asking for Bold directly is the real, visible weight bump). `display`/`headline` roles are
 * already Bold in the base scale, so they're left alone; `body` roles are deliberately untouched —
 * bolding a routine's notes/set values would hurt readability rather than read as "beast."
 * Applied by wrapping the Beast Mode nav-graph subtree in a nested `MaterialTheme(typography =
 * beastTypography(...))` (same colorScheme/shapes as the outer theme) — every existing
 * `DaybookText.*`/`MaterialTheme.typography.*` read inside that subtree picks this up for free,
 * with zero changes to the shared `ui/theme` scale or to individual Beast screens.
 */
fun beastTypography(base: Typography): Typography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold)
)

/**
 * §3.7 — exercise/routine/history rows pick a [CardTint] keyed off [MuscleGroup] instead of always
 * [CardTints.Neutral]. Groups with no natural mapping fall back to Neutral. Independent of
 * `AddExerciseScreen`'s illustration mapping (a different concern — that one picks a `.webp`
 * asset, this one picks a colour).
 */
@Composable
fun muscleTint(group: MuscleGroup): CardTint = when (group) {
    MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS -> CardTints.Rose
    MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.LATS,
    MuscleGroup.UPPER_BACK, MuscleGroup.TRAPS -> CardTints.SlateBlue
    MuscleGroup.ABDOMINALS, MuscleGroup.LOWER_BACK -> CardTints.Butter
    MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
    MuscleGroup.CALVES, MuscleGroup.ABDUCTORS, MuscleGroup.ADDUCTORS -> CardTints.Mint
    MuscleGroup.CARDIO -> CardTints.Peach
    MuscleGroup.FULL_BODY -> CardTints.Lavender
    MuscleGroup.NECK, MuscleGroup.OTHER -> CardTints.Neutral
}
