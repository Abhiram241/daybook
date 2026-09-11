package com.daybook.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Design tokens for Daybook. The reference's soft/rounded/pastel structure translated onto the
 * near-black palette. See ui-redesign-plan.md.
 *
 * UX overhaul item 4 — the 13 semantic colour roles are now a theme-resolved [DaybookColorScheme]
 * provided through [LocalDaybookColors]. The public [DaybookColors] name is kept as a
 * `@Composable`-getter shim so the ~316 `DaybookColors.X` call sites compile unchanged wherever
 * they sit inside a `@Composable`. The DARK values below are byte-identical to the pre-overhaul
 * constants; LIGHT is new. Non-composable readers (Theme.kt's scheme, CardTints.Neutral) read
 * [DaybookColorsDark] / [DaybookColorsLight] directly, never the shim.
 */
@Immutable
data class DaybookColorScheme(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val hairline: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val onSolid: Color
)

/**
 * The app-wide default schemes — now DERIVED from the default style ([DarkStyle.CHARCOAL] /
 * [LightStyle.PAPER]) so there is exactly one source of truth for "today's look" (UX
 * refinement round, Item 3). Byte-identical to the pre-refinement literal constants — guarded
 * by `DarkStyleTest` / `LightStyleTest` (D8).
 */
val DaybookColorsDark = darkSchemeFor(DarkStyle.CHARCOAL)
val DaybookColorsLight = lightSchemeFor(LightStyle.PAPER)

/** Provided by [DaybookTheme]; defaults to dark so previews / tests keep today's look. */
val LocalDaybookColors = staticCompositionLocalOf { DaybookColorsDark }

/**
 * Whether the active theme is dark. Provided by [DaybookTheme]. UX refinement round: a
 * non-Charcoal dark style is a *different* [DaybookColorScheme] object, so this can no longer be
 * a referential-equality check against [DaybookColorsDark] — that only ever matched Charcoal.
 */
val LocalIsDark = staticCompositionLocalOf { true }

/** True when the active [DaybookColorScheme] is a dark style. */
val isDaybookDarkThemeActive: Boolean
    @Composable get() = LocalIsDark.current

/**
 * `@Composable`-getter shim over [LocalDaybookColors]. Every member resolves per theme at read
 * time. `Border` and `Hairline` are the same 1dp-edge role (kept as two names for call-site
 * intent — `Border` for card/chip/field edges, `Outline` reserved for dividers).
 */
object DaybookColors {
    val Bg: Color @Composable get() = LocalDaybookColors.current.bg
    val Surface: Color @Composable get() = LocalDaybookColors.current.surface
    val SurfaceElevated: Color @Composable get() = LocalDaybookColors.current.surfaceElevated
    val Outline: Color @Composable get() = LocalDaybookColors.current.outline
    val Hairline: Color @Composable get() = LocalDaybookColors.current.hairline
    val Border: Color @Composable get() = LocalDaybookColors.current.border
    val TextPrimary: Color @Composable get() = LocalDaybookColors.current.textPrimary
    val TextMuted: Color @Composable get() = LocalDaybookColors.current.textMuted
    val TextFaint: Color @Composable get() = LocalDaybookColors.current.textFaint
    val Success: Color @Composable get() = LocalDaybookColors.current.success
    val Warning: Color @Composable get() = LocalDaybookColors.current.warning
    val Danger: Color @Composable get() = LocalDaybookColors.current.danger
    val OnSolid: Color @Composable get() = LocalDaybookColors.current.onSolid

    /**
     * LD13 — the higher-contrast ink for content sitting ON the current accent fill (selected
     * segmented label, chip label, primary-button label, etc). Computed per-accent by
     * [onAccentInk] and provided by [DaybookTheme] via [LocalOnAccent]. Dark mode resolves to
     * the same near-black as [OnSolid] for every accent (byte-identical to today); in light mode
     * this fixes the 3 accents (Mint / Amber / Coral) that fail AA against [OnSolid]'s fixed white.
     */
    val OnAccent: Color @Composable get() = LocalOnAccent.current
}

@Immutable
data class CardTint(
    val fill: Color,
    val fillRaised: Color,
    val onFill: Color,
    val onFillMuted: Color,
    // v0.5.3 Phase 0 — third text tier: title=onFill / subtitle=onFillMuted / metadata=onFillFaint.
    // ~8–10% lighter toward `fill` than `onFillMuted` so timestamps read below the subtitle.
    val onFillFaint: Color,
    val accent: Color
)

/** Dark pastel card tints — byte-identical to the pre-overhaul `CardTints`. */
object CardTintsDark {
    val Lavender = CardTint(Color(0xFF2A2536), Color(0xFF322C42), Color(0xFFF2F3F5), Color(0xFFB4B0BE), Color(0xFF8E8A98), Color(0xFFA78BFA))
    val Peach = CardTint(Color(0xFF332723), Color(0xFF3D2F2A), Color(0xFFF2F3F5), Color(0xFFC0B2AB), Color(0xFF9C9089), Color(0xFFF5A97F))
    val Mint = CardTint(Color(0xFF1F312B), Color(0xFF263C34), Color(0xFFF2F3F5), Color(0xFFA9BEB6), Color(0xFF8AA099), Color(0xFF7FD1B0))
    val Butter = CardTint(Color(0xFF322E22), Color(0xFF3C3729), Color(0xFFF2F3F5), Color(0xFFBEB6A2), Color(0xFF9C948A), Color(0xFFE6C878))
    val SlateBlue = CardTint(Color(0xFF232B38), Color(0xFF2B3444), Color(0xFFF2F3F5), Color(0xFFA9B4C2), Color(0xFF8A94A2), Color(0xFF8FB6E8))
    val Rose = CardTint(Color(0xFF33262E), Color(0xFF3D2E38), Color(0xFFF2F3F5), Color(0xFFC2AEB6), Color(0xFF9E8E96), Color(0xFFE68FB0))

    /** Utilitarian, non-pastel card (Settings, form groups). */
    val Neutral = CardTint(
        fill = DaybookColorsDark.surface,
        fillRaised = DaybookColorsDark.surfaceElevated,
        onFill = DaybookColorsDark.textPrimary,
        onFillMuted = DaybookColorsDark.textMuted,
        onFillFaint = DaybookColorsDark.textFaint,
        accent = DaybookColorsDark.textMuted
    )

    val ALL: List<CardTint> = listOf(Lavender, Peach, Mint, Butter, SlateBlue, Rose)

    private val OVERRIDE = mapOf(
        "LAVENDER" to Lavender, "PEACH" to Peach, "MINT" to Mint,
        "BUTTER" to Butter, "SLATE_BLUE" to SlateBlue, "ROSE" to Rose
    )

    fun byIndex(i: Int): CardTint = ALL[((i % ALL.size) + ALL.size) % ALL.size]
    fun byId(id: String): CardTint = ALL[abs(id.hashCode()) % ALL.size]
    fun resolve(overrideName: String?, positionalIndex: Int): CardTint =
        OVERRIDE[overrideName] ?: byIndex(positionalIndex)
}

/** Light pastel card tints for the paper ground — new in the UX overhaul. */
object CardTintsLight {
    private val onFill = Color(0xFF1B1D20)
    private val onFillMuted = Color(0xFF5B6068)
    private val onFillFaint = Color(0xFF8A9099)
    val Lavender = CardTint(Color(0xFFF1ECFB), Color(0xFFE7DEF7), onFill, onFillMuted, onFillFaint, Color(0xFF7C5CE0))
    val Peach = CardTint(Color(0xFFFCEEE6), Color(0xFFF8E1D3), onFill, onFillMuted, onFillFaint, Color(0xFFC2683B))
    val Mint = CardTint(Color(0xFFE5F5EF), Color(0xFFD6EDE3), onFill, onFillMuted, onFillFaint, Color(0xFF0F9488))
    val Butter = CardTint(Color(0xFFF9F1DD), Color(0xFFF3E8C7), onFill, onFillMuted, onFillFaint, Color(0xFFB7791F))
    val SlateBlue = CardTint(Color(0xFFE9F0FB), Color(0xFFDAE6F7), onFill, onFillMuted, onFillFaint, Color(0xFF2563EB))
    val Rose = CardTint(Color(0xFFFBEBF1), Color(0xFFF7DDE7), onFill, onFillMuted, onFillFaint, Color(0xFFE23D5B))

    val Neutral = CardTint(
        fill = DaybookColorsLight.surface,
        fillRaised = DaybookColorsLight.surfaceElevated,
        onFill = DaybookColorsLight.textPrimary,
        onFillMuted = DaybookColorsLight.textMuted,
        onFillFaint = DaybookColorsLight.textFaint,
        accent = DaybookColorsLight.textMuted
    )

    val ALL: List<CardTint> = listOf(Lavender, Peach, Mint, Butter, SlateBlue, Rose)

    private val OVERRIDE = mapOf(
        "LAVENDER" to Lavender, "PEACH" to Peach, "MINT" to Mint,
        "BUTTER" to Butter, "SLATE_BLUE" to SlateBlue, "ROSE" to Rose
    )

    fun byIndex(i: Int): CardTint = ALL[((i % ALL.size) + ALL.size) % ALL.size]
    fun byId(id: String): CardTint = ALL[abs(id.hashCode()) % ALL.size]
    fun resolve(overrideName: String?, positionalIndex: Int): CardTint =
        OVERRIDE[overrideName] ?: byIndex(positionalIndex)
}

/**
 * Theme-resolved accessor. Members are `@Composable` getters that pick the dark or light tint
 * set from [LocalDaybookColors] — the ~36 `CardTints.*` call sites (all inside composables)
 * compile unchanged. Non-composable readers use [CardTintsDark] directly.
 */
object CardTints {
    private val light: Boolean
        @Composable get() = !LocalIsDark.current

    val Lavender: CardTint @Composable get() = if (light) CardTintsLight.Lavender else CardTintsDark.Lavender
    val Peach: CardTint @Composable get() = if (light) CardTintsLight.Peach else CardTintsDark.Peach
    val Mint: CardTint @Composable get() = if (light) CardTintsLight.Mint else CardTintsDark.Mint
    val Butter: CardTint @Composable get() = if (light) CardTintsLight.Butter else CardTintsDark.Butter
    val SlateBlue: CardTint @Composable get() = if (light) CardTintsLight.SlateBlue else CardTintsDark.SlateBlue
    val Rose: CardTint @Composable get() = if (light) CardTintsLight.Rose else CardTintsDark.Rose
    val Neutral: CardTint @Composable get() = if (light) CardTintsLight.Neutral else CardTintsDark.Neutral
    val ALL: List<CardTint> @Composable get() = if (light) CardTintsLight.ALL else CardTintsDark.ALL

    @Composable fun byIndex(i: Int): CardTint = if (light) CardTintsLight.byIndex(i) else CardTintsDark.byIndex(i)
    @Composable fun byId(id: String): CardTint = if (light) CardTintsLight.byId(id) else CardTintsDark.byId(id)
    @Composable fun resolve(overrideName: String?, positionalIndex: Int): CardTint =
        if (light) CardTintsLight.resolve(overrideName, positionalIndex)
        else CardTintsDark.resolve(overrideName, positionalIndex)
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
    val screenH = 20.dp
    val cardInner = 20.dp
    val listGap = 12.dp
    val sectionGap = 28.dp

    // v0.5.3 Phase 0 — the most-repeated raw values that had no token (§2.5).
    /** Add to the `statusBars` top inset for a pinned header. */
    val headerInset = 8.dp

    /** Top contentPadding of a list that sits under a pinned header. */
    val listTop = 4.dp

    /** Gap between an icon tile / badge and the text beside it. */
    val iconGap = 12.dp

    /** Gap between adjacent chips. */
    val chipGap = 8.dp

    // v0.5.3 Phase 4 (§4.8) — named list bottom-clearance constants replacing the ad-hoc
    // `+24.dp` / `120.dp` / `72.dp` / `40.dp` literals scattered across the screens.
    /**
     * Legacy — the save bar is an in-flow sibling now (see KEYBOARD_FONT_FIXES_PLAN.md D1), so form
     * lists no longer reserve bottom clearance for it. Kept declared because `Spacing` is referenced
     * from tests.
     */
    val formSaveBarClearance = 96.dp

    /** Bottom contentPadding for a plain scrolling screen with no docked bar or FAB. */
    val screenBottomInset = 32.dp
}

/**
 * Corner-radius scale. Deliberately restrained — rounding is used where it reads as a
 * distinct surface (cards, fields, sheets) and dropped for decoration. Circles stay
 * circular for genuinely round elements (icon buttons, avatars, dots, count badges).
 */
// UX refinement round — Item 4: `AppShapes` becomes a `@Composable`-getter shim over
// [LocalDaybookShapes] (identical trick to `DaybookColors`), so the ~76 existing call sites
// compile unchanged. The default scale (1.0) reproduces every dp value below exactly (D5).
object AppShapes {
    val card: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.card
    val button: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.button
    val field: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.field
    val pill: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.pill
    val tile: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.tile
    val sheet: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.sheet
    val nav: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.nav
    val dialog: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.dialog

    // v0.5.3 Phase 0 (§3.4). Fixed at every corner scale (LD11).
    val segmented: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.segmented
    val navPill: RoundedCornerShape @Composable get() = LocalDaybookShapes.current.navPill
}

object Motion {
    fun <T> pressSpring() = spring<T>(dampingRatio = 0.55f, stiffness = 900f)
    fun <T> placementSpring() = spring<T>(dampingRatio = 0.85f, stiffness = 400f)
    fun <T> softSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 450f)
    // v0.5.2 Phase 4c: stiffness StiffnessLow (200f) -> StiffnessMedium (~400f). Still
    // DampingRatioNoBouncy, so no bounce — just halves the progress-bar settle tail on Home
    // (PastelProgressBar draws two of these), which was ~1.2 s of per-frame recomposition.
    fun <T> lowSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    // v0.5.3 Phase 0 (§2.8 / backlog #14) — the ~10 literal `tween(...)` durations scattered
    // across MainActivity, WeekStrip, UndoSnack, Forms and the lists get 3 named steps. Phase 4
    // replaces the literals with these.
    const val fastMillis = 120
    const val mediumMillis = 180
    const val slowMillis = 240
    fun <T> fast() = tween<T>(fastMillis)
    fun <T> medium() = tween<T>(mediumMillis)
    fun <T> slow() = tween<T>(slowMillis)

    // v0.5.3 Phase 5 (§3.9) — the Home calendar-expand gate. HomeScreen's `calendarAnimating`
    // window and WeekStrip's SizeTransform (`slow()`) are now the same duration.
    const val calendarExpandMillis = slowMillis

    /** Nav push-enter — matches MainActivity's slideInHorizontally(240) + fadeIn(180). */
    val navEnter: EnterTransition
        get() = slideInHorizontally(tween(240)) { it / 6 } + fadeIn(tween(180))

    /** Nav pop-exit — matches MainActivity's fadeOut(110) + slideOutHorizontally(180). */
    val navExit: ExitTransition
        get() = fadeOut(tween(110)) + slideOutHorizontally(tween(180)) { -it / 6 }
}

/**
 * v0.5.3 Phase 0 (§3.10) — the glyph-size scale. Two components sized icons off a ratio
 * (`size * 0.45f`), the rest were fixed literals with no scale. Phase 4 maps callers on.
 */
object IconSize {
    val Xs = 14.dp
    val Sm = 16.dp
    val Md = 20.dp
    val Lg = 24.dp
}

/**
 * v0.5.3 Phase 0 (§3.5 / backlog #19) — the discrete tap-target sizes for [
 * com.daybook.app.ui.components.CircleIconButton]. Collapses the six ad-hoc sizes
 * (32/36/40/44/56) onto four roles: 36 folds onto [Sm], and the Home filter button (36) moves
 * up to [Lg] to match Habits/Intake. Strictly ascending — see `IconButtonSizeTest`.
 */
enum class IconButtonSize(val dp: Dp) {
    Sm(32.dp),
    Md(40.dp),
    Lg(44.dp),
    Fab(56.dp)
}
