package com.daybook.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.IconButtonSize
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion
import com.daybook.app.ui.theme.Spacing
import kotlinx.coroutines.delay

@Immutable
data class NavItemSpec(val route: String, val icon: ImageVector, val label: String)

internal val NavContentHeight = 62.dp

/**
 * A5 (§3.6.2) — the dead-zone-then-ramp arithmetic behind the press-and-hold feedback, extracted
 * so it is testable without Compose. `longPressTimeoutMillis` is the platform's own value (via
 * `LocalViewConfiguration`, itself honouring the user's own Accessibility -> Touch and hold delay
 * setting) — never a hard-coded 500. The ramp must finish exactly when the gesture triggers, so
 * it is the timeout minus the dead zone that already elapsed doing nothing.
 */
internal fun longPressRampMillis(longPressTimeoutMillis: Long, deadZoneMillis: Int = 120): Int =
    (longPressTimeoutMillis - deadZoneMillis).coerceAtLeast(1).toInt()

private const val HOLD_DEAD_ZONE_MILLIS = 120L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingPillNav(
    items: List<NavItemSpec>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    // A5 (§3.6.1 b) — one implementation, two configurations: Daybook's own nav passes
    // longPressRoute = "home"; Beast Mode's nav passes longPressRoute = WorkoutRoutes.HOME. The
    // gate is a ROUTE check, not an index check (§3.6.1 note 1).
    onLongSelect: ((String) -> Unit)? = null,
    longPressRoute: String? = null,
    longPressLabel: String? = null,
    hintDotRoutes: Set<String> = emptySet()
) {
    val NavShape = AppShapes.nav
    val accent = LocalAccent.current
    // Hoisted: DaybookColors.* are @Composable getters and can't be read inside drawBehind's
    // DrawScope lambda.
    val hairlineColor = DaybookColors.Hairline

    Column(
        modifier = modifier
            .fillMaxWidth()
            // v0.5.3 Phase 7 (#40) — the 6.dp radius is kept: the pill nav is a single docked
            // surface (no card stacked under it), so there is no shadow-on-shadow "muddy halo".
            // On API 26–27 `ambientColor`/`spotColor` are ignored and this draws as a flat black
            // projection at ~0.4 alpha — acceptable for one edge. NEEDS a physical API 26–27
            // device check before shipping 0.5.3 (none available); see V053_REGRESSION.md.
            .shadow(6.dp, NavShape, clip = false, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
            .clip(NavShape)
            .background(DaybookColors.Surface)
            .drawBehind {
                drawLine(
                    color = hairlineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NavContentHeight)
        ) {
            Row(
                // B23 hotfix — was `fillMaxSize()`. With the outer Box now `heightIn(min = …)`
                // (B9) instead of a fixed `height(…)`, its max height propagates as the full
                // screen; `fillMaxSize()`'s height-fill made this Row (and the nav bar) expand to
                // consume the whole MainActivity content column, collapsing the pager to 0 and
                // stranding the nav items mid-screen. Width-fill only; the Row wraps its content
                // height and the Box's `heightIn(min = NavContentHeight)` sets the floor.
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val reduceMotion = LocalReduceMotion.current
                val haptics = com.daybook.app.ui.theme.rememberDaybookHaptics()
                val longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
                val rampMillis = remember(longPressTimeoutMillis) { longPressRampMillis(longPressTimeoutMillis) }

                items.forEach { item ->
                    val selected = item.route == currentRoute
                    val tint by animateColorAsState(
                        if (selected) accent else DaybookColors.TextMuted,
                        Motion.softSpring(),
                        label = "navTint"
                    )
                    val longPressable = onLongSelect != null && longPressRoute != null && item.route == longPressRoute
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()

                    // A5 (§3.6.2) — the dead-zone-then-ramp state. Only the long-pressable item
                    // ever sets this true; every other item's holdActive never leaves false.
                    var holdActive by remember { mutableStateOf(false) }
                    if (longPressable) {
                        LaunchedEffect(pressed) {
                            if (pressed) {
                                delay(HOLD_DEAD_ZONE_MILLIS)
                                holdActive = true
                            } else {
                                holdActive = false
                            }
                        }
                    }

                    // Scale ramps 1.00 -> 1.18 linearly over rampMillis while holding (dropped
                    // entirely under reduce-motion — "no growth"); releasing early springs back
                    // via the same spring CircleIconButton uses. Tint lerps to the accent the same
                    // way, except under reduce-motion it snaps instead — the haptic still fires
                    // either way (a haptic is not motion).
                    val holdScaleTarget = if (!reduceMotion && holdActive) 1.18f else 1f
                    val holdScale by animateFloatAsState(
                        holdScaleTarget,
                        if (holdActive) tween(durationMillis = rampMillis, easing = LinearEasing) else Motion.pressSpring(),
                        label = "navHoldScale"
                    )
                    val holdTint by animateColorAsState(
                        if (holdActive) accent else tint,
                        when {
                            reduceMotion -> snap()
                            holdActive -> tween(durationMillis = rampMillis, easing = LinearEasing)
                            else -> Motion.softSpring()
                        },
                        label = "navHoldTint"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // B23 hotfix — `fillMaxHeight()` removed. It assumed the fixed-height
                            // parent Box from before B9; with the Box now `heightIn(min = …)` its
                            // bounded max height is the full screen, so this fill would re-inflate
                            // the Row to full-screen height even after the Row itself stopped
                            // filling. The column wraps its content (icon + label + 8.dp vertical
                            // padding); `Arrangement.Center` + the Box's `heightIn(min =
                            // NavContentHeight)` keep the items vertically centred in the bar.
                            .then(
                                if (longPressable) {
                                    Modifier.combinedClickableImpl(
                                        interaction = interaction,
                                        onLongClickLabel = longPressLabel ?: "",
                                        onLongClick = {
                                            // A5 (§3.6.2) — the haptic tick fires first, while the
                                            // finger is still down, before navigation itself.
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onLongSelect!!(item.route)
                                        },
                                        onClick = { onSelect(item.route) }
                                    )
                                } else {
                                    Modifier.clickableImpl(interaction) {
                                        // Polish pass — a subtle tick only on an actual tab switch,
                                        // not on re-tapping the already-selected tab, so it stays
                                        // out of the way of ordinary repeated navigation.
                                        if (item.route != currentRoute) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        onSelect(item.route)
                                    }
                                }
                            )
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box {
                            Icon(
                                imageVector = item.icon,
                                // v0.5.3 Phase 0 (§3.10) — label Text below is always visible, so
                                // the icon is decorative; null stops TalkBack double-reading
                                // "Today, Today". The gesture reaches TalkBack via onLongClickLabel.
                                contentDescription = null,
                                tint = holdTint,
                                modifier = Modifier
                                    .size(if (selected) 24.dp else 22.dp)
                                    .graphicsLayer { scaleX = holdScale; scaleY = holdScale }
                            )
                            // A5 (§3.6.3 b) — the persistent hint dot, until the gesture is used
                            // once. Decorative; the coach-mark text carries the announcement.
                            if (item.route in hintDotRoutes) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-2).dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Replaces per-screen Scaffold. Owns the bg, the docked nav and its scrim.
 *
 * v0.5.3 Phase 0 (§2.4 / backlog #2,#18) — the content lambda now receives one [PaddingValues]
 * that already carries `start/end = Spacing.screenH`, `top = Spacing.listTop` and
 * `bottom = navClearance (+ FAB clearance when [fabPresent])`. Phase 4 deletes the four
 * hand-computed bottom clearances scattered across the screens and consumes this instead.
 *
 * NOTE (§3.1): the per-screen `.clipToBounds()` at `HomeScreen.kt:91`, `RoutinesScreen.kt:56`,
 * `FoodMedScreen.kt:56`, `DetailScreen.kt:70`, `SettingsScreen.kt:115` is redundant with the
 * one below and clips pressed-card shadows at the screen edges — removed in Phase 4/5.
 */
@Composable
fun DaybookScaffold(
    showNav: Boolean,
    currentRoute: String?,
    navItems: List<NavItemSpec>,
    onSelectRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    fabPresent: Boolean = false,
    // A5 (§3.6.1 b) — threaded straight through to FloatingPillNav; DaybookScaffold itself does
    // nothing with them beyond passing them on. Defaulted so every existing call site (there was
    // only ever one, MainActivity.kt) stays source-compatible.
    onLongSelect: ((String) -> Unit)? = null,
    longPressRoute: String? = null,
    longPressLabel: String? = null,
    hintDotRoutes: Set<String> = emptySet(),
    // A5 (§3.6.3 a) — the one-time coach-mark's slot. Rendered ABOVE the nav (so it floats over
    // it, not under), only while `showNav` — there is nothing to anchor it to otherwise.
    coachMark: (@Composable BoxScope.(navClearance: Dp) -> Unit)? = null,
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navClearance: Dp = if (showNav) NavContentHeight + 12.dp + navBarInset else 0.dp
    val fabClearance: Dp = if (fabPresent) IconButtonSize.Fab.dp + 16.dp else 0.dp
    val contentPadding = PaddingValues(
        start = Spacing.screenH,
        end = Spacing.screenH,
        top = Spacing.listTop,
        bottom = navClearance + fabClearance
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DaybookColors.Bg)
            .clipToBounds()
    ) {
        content(contentPadding)
        DaybookScaffoldNav(
            showNav, currentRoute, navItems, onSelectRoute,
            onLongSelect, longPressRoute, longPressLabel, hintDotRoutes
        )
        if (showNav && coachMark != null) {
            coachMark(navClearance)
        }
    }
}

// v0.5.3 Phase 4 (§4.11) — the deprecated `Dp`-clearance `DaybookScaffold` overload is gone;
// every screen consumes the [PaddingValues] slot now.

@Composable
private fun BoxScope.DaybookScaffoldNav(
    showNav: Boolean,
    currentRoute: String?,
    navItems: List<NavItemSpec>,
    onSelectRoute: (String) -> Unit,
    onLongSelect: ((String) -> Unit)? = null,
    longPressRoute: String? = null,
    longPressLabel: String? = null,
    hintDotRoutes: Set<String> = emptySet()
) {
    if (!showNav) return
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(120.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to DaybookColors.Bg
                )
            )
    )
    FloatingPillNav(
        items = navItems,
        currentRoute = currentRoute,
        onSelect = onSelectRoute,
        modifier = Modifier.align(Alignment.BottomCenter),
        onLongSelect = onLongSelect,
        longPressRoute = longPressRoute,
        longPressLabel = longPressLabel,
        hintDotRoutes = hintDotRoutes
    )
}
