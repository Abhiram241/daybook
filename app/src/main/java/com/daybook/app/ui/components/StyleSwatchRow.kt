package com.daybook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DarkStyle
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.LightStyle
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.Spacing

/**
 * UX refinement round — Item 3 (§3.4). A row of preview chips for the Dark-style / Light-style
 * pickers in Settings → Appearance (D1). Each chip paints the style's actual ground colours (its
 * `bg`, an inset `surface` rect, a `textPrimary` bar) rather than a colour name, so the picker
 * shows what the app will actually look like. 4 chips fit a 360dp-wide screen; still wrapped in
 * `horizontalScroll` so a large system font scale (which only grows the label beneath, not the
 * chip) can't clip the row.
 *
 * Two thin overloads (one per style enum) over a shared private core — `DarkStyle.ground` /
 * `LightStyle.ground` stay `internal` (LD15: no public accessor, no helper extension); this file
 * reads them directly because the app is a single `:app` module, exactly like `MainActivity`
 * does for `applyWindowTheme()`.
 */
@Composable
fun StyleSwatchRow(
    items: List<DarkStyle>,
    current: DarkStyle,
    onPick: (DarkStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    StyleSwatchRowImpl(
        modifier = modifier,
        count = items.size,
        labelOf = { items[it].label },
        bgOf = { items[it].ground.bg },
        surfaceOf = { items[it].ground.surface },
        textOf = { items[it].ground.textPrimary },
        selectedOf = { items[it] == current },
        onPick = { onPick(items[it]) }
    )
}

@Composable
fun StyleSwatchRow(
    items: List<LightStyle>,
    current: LightStyle,
    onPick: (LightStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    StyleSwatchRowImpl(
        modifier = modifier,
        count = items.size,
        labelOf = { items[it].label },
        bgOf = { items[it].ground.bg },
        surfaceOf = { items[it].ground.surface },
        textOf = { items[it].ground.textPrimary },
        selectedOf = { items[it] == current },
        onPick = { onPick(items[it]) }
    )
}

@Composable
private fun StyleSwatchRowImpl(
    modifier: Modifier,
    count: Int,
    labelOf: (Int) -> String,
    bgOf: (Int) -> Color,
    surfaceOf: (Int) -> Color,
    textOf: (Int) -> Color,
    selectedOf: (Int) -> Boolean,
    onPick: (Int) -> Unit
) {
    val accent = LocalAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        repeat(count) { i ->
            val selected = selectedOf(i)
            val label = labelOf(i)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(AppShapes.card)
                    .clickableImpl(remember { MutableInteractionSource() }) { onPick(i) }
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .padding(6.dp)
            ) {
                Box(
                    Modifier
                        .size(width = 72.dp, height = 56.dp)
                        .clip(AppShapes.card)
                        .background(bgOf(i))
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) accent else DaybookColors.Border,
                            AppShapes.card
                        )
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(width = 28.dp, height = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(surfaceOf(i))
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp)
                            .size(width = 30.dp, height = 3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(textOf(i))
                    )
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = DaybookText.Caption, color = DaybookColors.TextMuted)
            }
        }
    }
}
