package com.daybook.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.components.CircleIconButton
import com.daybook.app.ui.components.CircleStyle
import com.daybook.app.ui.components.IconTile
import com.daybook.app.ui.components.SoftCard
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LocalAccent

/**
 * UX overhaul item 1 — the in-app-rendered mock illustration for a [TeachIllustration]. No image
 * assets: each is a cheap static `Column` of real primitives (`SoftCard`, `IconTile`,
 * `CircleIconButton`, a few tinted `Box`es) so the picture always tracks the live theme, accent
 * and font.
 */
@Composable
fun OnboardingIllustration(kind: TeachIllustration) {
    when (kind) {
        TeachIllustration.TODAY -> TodayMock()
        TeachIllustration.MAKE_HABIT -> MakeHabitMock()
        TeachIllustration.INTAKE -> IntakeMock()
        TeachIllustration.SHADE -> ShadeMock()
        TeachIllustration.YOURS -> YoursMock()
        TeachIllustration.STREAKS -> StreaksMock()
        TeachIllustration.PRIVACY -> PrivacyMock()
    }
}

@Composable
private fun MockFrame(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    SoftCard(tint = CardTints.Neutral, modifier = Modifier.fillMaxWidth(), content = content)
}

/** A pill/chip-shaped placeholder line. */
@Composable
private fun Bar(widthFraction: Float, height: Int = 10, color: androidx.compose.ui.graphics.Color = DaybookColors.SurfaceElevated) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun TodayMock() {
    MockFrame {
        // Mini week strip: 7 cells, the 4th filled with the accent.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val accent = LocalAccent.current
            repeat(7) { i ->
                Box(
                    Modifier
                        .size(width = 26.dp, height = 34.dp)
                        .clip(AppShapes.tile)
                        .background(if (i == 3) accent else DaybookColors.SurfaceElevated)
                        .border(1.dp, DaybookColors.Hairline, AppShapes.tile)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // Two progress cards side by side.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(CardTints.Mint, CardTints.Peach).forEach { t ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(AppShapes.card)
                        .background(t.fill)
                        .border(1.dp, DaybookColors.Hairline, AppShapes.card)
                        .padding(12.dp)
                ) {
                    Bar(0.55f, 8, t.onFillMuted)
                    Spacer(Modifier.height(10.dp))
                    Bar(1f, 6, t.accent)
                }
            }
        }
    }
}

@Composable
private fun HabitRow(tint: com.daybook.app.ui.theme.CardTint, icon: androidx.compose.ui.graphics.vector.ImageVector, done: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(tint.fill)
            .border(1.dp, DaybookColors.Hairline, AppShapes.card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon = icon, tint = tint, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Bar(0.7f, 9, tint.onFill)
            Spacer(Modifier.height(6.dp))
            Bar(0.4f, 7, tint.onFillMuted)
        }
        Spacer(Modifier.width(12.dp))
        CircleIconButton(
            icon = Icons.Filled.Check,
            contentDescription = null,
            onClick = {},
            style = if (done) CircleStyle.Success else CircleStyle.Ghost,
            size = 36.dp
        )
    }
}

@Composable
private fun MakeHabitMock() {
    MockFrame {
        HabitRow(CardTints.Lavender, DaybookIcons.DirectionsRun, done = true)
        Spacer(Modifier.height(10.dp))
        HabitRow(CardTints.SlateBlue, DaybookIcons.SelfImprovement, done = false)
    }
}

@Composable
private fun IntakeMock() {
    MockFrame {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = DaybookIcons.Restaurant, tint = CardTints.Peach, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("What did you have?", style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Bar(0.5f, 7, DaybookColors.TextFaint)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.field)
                .background(DaybookColors.SurfaceElevated)
                .border(1.dp, DaybookColors.Hairline, AppShapes.field)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Bar(0.6f, 8, DaybookColors.TextFaint)
            Spacer(Modifier.weight(1f))
            CircleIconButton(
                icon = DaybookIcons.Send,
                contentDescription = null,
                onClick = {},
                style = CircleStyle.Tonal,
                size = 30.dp
            )
        }
    }
}

@Composable
private fun ShadeMock() {
    MockFrame {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = DaybookIcons.AlarmClock, tint = CardTints.SlateBlue, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Bar(0.65f, 9, DaybookColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Bar(0.45f, 7, DaybookColors.TextMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Skip", "Snooze", "Done").forEach { label ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(AppShapes.pill)
                        .background(DaybookColors.SurfaceElevated)
                        .border(1.dp, DaybookColors.Hairline, AppShapes.pill)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = DaybookText.NavLabel, color = DaybookColors.TextMuted)
                }
            }
        }
    }
}

/**
 * LD8 — a flame glyph + a run count, and a faint 7-dot week track (5 filled with the accent,
 * 2 empty). ~120dp tall.
 */
@Composable
private fun StreaksMock() {
    MockFrame {
        val accent = LocalAccent.current
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = DaybookIcons.Flame, tint = CardTints.Butter, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("7-day streak", style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Bar(0.4f, 7, DaybookColors.TextMuted)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(7) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (i < 5) accent else DaybookColors.TextFaint.copy(alpha = 0.3f))
                        .border(1.dp, DaybookColors.Hairline, CircleShape)
                )
            }
        }
    }
}

/**
 * LD8 — three stacked mini-rows: an accent swatch strip, an "A" type sample, a lock glyph.
 */
@Composable
private fun PrivacyMock() {
    MockFrame {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTints.ALL.take(5).forEach { t ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(t.accent)
                        .border(1.dp, DaybookColors.Hairline, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aa", style = DaybookText.ScreenTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Bar(0.7f, 8, DaybookColors.TextMuted)
                Spacer(Modifier.height(6.dp))
                Bar(0.5f, 7, DaybookColors.TextFaint)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = DaybookIcons.Lock, tint = CardTints.SlateBlue, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Bar(0.55f, 9, DaybookColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Bar(0.35f, 7, DaybookColors.TextMuted)
            }
        }
    }
}

@Composable
private fun YoursMock() {
    MockFrame {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTints.ALL.take(5).forEach { t ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(t.accent)
                        .border(1.dp, DaybookColors.Hairline, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = DaybookIcons.Lock, tint = CardTints.Mint, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Bar(0.6f, 9, DaybookColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Bar(0.35f, 7, DaybookColors.TextMuted)
            }
        }
    }
}
