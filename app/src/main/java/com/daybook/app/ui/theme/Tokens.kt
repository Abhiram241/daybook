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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Design tokens for Daybook. Dark-only. The reference's soft/rounded/pastel structure
 * translated onto the near-black palette. See ui-redesign-plan.md.
 */
object DaybookColors {
    val Bg = Color(0xFF0B0D0F)
    val Surface = Color(0xFF16181B)
    val SurfaceElevated = Color(0xFF1E2124)
    val Outline = Color(0xFF2A2D31)
    val Hairline = Color(0x14FFFFFF) // white 8%

    // v0.5.3 Phase 0 — one name for the 1dp edge role. `Border` for every 1dp edge (cards,
    // chips, tiles, fields); `Outline` stays reserved for dividers only (SettingsRowDivider,
    // HorizontalDivider in sheets). Phase 4 sweeps the interchangeable uses onto this.
    val Border = Hairline
    val TextPrimary = Color(0xFFF2F3F5)
    val TextMuted = Color(0xFF9AA0A6)
    val TextFaint = Color(0xFF6B7178)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFACC15)
    val Danger = Color(0xFFF87171)
    val OnSolid = Color(0xFF0B0D0F) // text/icon on a light (#F2F3F5) solid control
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

object CardTints {
    val Lavender = CardTint(Color(0xFF2A2536), Color(0xFF322C42), Color(0xFFF2F3F5), Color(0xFFB4B0BE), Color(0xFF8E8A98), Color(0xFFA78BFA))
    val Peach = CardTint(Color(0xFF332723), Color(0xFF3D2F2A), Color(0xFFF2F3F5), Color(0xFFC0B2AB), Color(0xFF9C9089), Color(0xFFF5A97F))
    val Mint = CardTint(Color(0xFF1F312B), Color(0xFF263C34), Color(0xFFF2F3F5), Color(0xFFA9BEB6), Color(0xFF8AA099), Color(0xFF7FD1B0))
    val Butter = CardTint(Color(0xFF322E22), Color(0xFF3C3729), Color(0xFFF2F3F5), Color(0xFFBEB6A2), Color(0xFF9C948A), Color(0xFFE6C878))
    val SlateBlue = CardTint(Color(0xFF232B38), Color(0xFF2B3444), Color(0xFFF2F3F5), Color(0xFFA9B4C2), Color(0xFF8A94A2), Color(0xFF8FB6E8))
    val Rose = CardTint(Color(0xFF33262E), Color(0xFF3D2E38), Color(0xFFF2F3F5), Color(0xFFC2AEB6), Color(0xFF9E8E96), Color(0xFFE68FB0))

    /** Utilitarian, non-pastel card (Settings, form groups). */
    val Neutral = CardTint(
        fill = DaybookColors.Surface,
        fillRaised = DaybookColors.SurfaceElevated,
        onFill = DaybookColors.TextPrimary,
        onFillMuted = DaybookColors.TextMuted,
        onFillFaint = DaybookColors.TextFaint,
        accent = DaybookColors.TextMuted
    )

    val ALL: List<CardTint> = listOf(Lavender, Peach, Mint, Butter, SlateBlue, Rose)

    private val OVERRIDE = mapOf(
        "LAVENDER" to Lavender,
        "PEACH" to Peach,
        "MINT" to Mint,
        "BUTTER" to Butter,
        "SLATE_BLUE" to SlateBlue,
        "ROSE" to Rose
    )

    fun byIndex(i: Int): CardTint = ALL[((i % ALL.size) + ALL.size) % ALL.size]

    fun byId(id: String): CardTint = ALL[abs(id.hashCode()) % ALL.size]

    /** Explicit per-item override wins; otherwise auto-assign by list position. */
    fun resolve(overrideName: String?, positionalIndex: Int): CardTint =
        OVERRIDE[overrideName] ?: byIndex(positionalIndex)
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
    /** Bottom contentPadding for a form list that sits under a [com.daybook.app.ui.components.StickySaveBar]. */
    val formSaveBarClearance = 96.dp

    /** Bottom contentPadding for a plain scrolling screen with no docked bar or FAB. */
    val screenBottomInset = 32.dp
}

/**
 * Corner-radius scale. Deliberately restrained — rounding is used where it reads as a
 * distinct surface (cards, fields, sheets) and dropped for decoration. Circles stay
 * circular for genuinely round elements (icon buttons, avatars, dots, count badges).
 */
object AppShapes {
    val card = RoundedCornerShape(14.dp)      // SoftCard / FormGroup / SettingsGroup / progress cards
    val button = RoundedCornerShape(12.dp)    // PrimaryButton / GhostButton — no longer pills
    val field = RoundedCornerShape(10.dp)     // text fields
    val pill = RoundedCornerShape(10.dp)      // chips, stat pills, small inline actions
    val tile = RoundedCornerShape(12.dp)      // small icon tiles / menu-row icon squares
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val nav = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val dialog = RoundedCornerShape(16.dp)

    // v0.5.3 Phase 0 (§3.4).
    val segmented = RoundedCornerShape(50)                 // SegmentedControl track/pill
    val navPill = RoundedCornerShape(28.dp)                // Detail floating nav footprint
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
