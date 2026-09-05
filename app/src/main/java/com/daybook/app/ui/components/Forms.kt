package com.daybook.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.daybook.app.ui.icons.DaybookIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.daybook.app.data.model.DayOfWeek
import com.daybook.app.data.model.RedFlag
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTint
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.DaybookText
import com.daybook.app.ui.theme.IconButtonSize
import com.daybook.app.ui.theme.LocalAccent
import com.daybook.app.ui.theme.LocalReduceMotion
import com.daybook.app.ui.theme.Motion

/** A neutral SoftCard group with a small caption. */
@Composable
fun FormGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    SoftCard(tint = CardTints.Neutral, modifier = modifier.fillMaxWidth(), elevation = 0.dp) {
        if (title != null) {
            // v0.5.3 Phase 4 (§4.4) — group header inside a card: CardTitle role, not raw titleMedium.
            Text(title, style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/**
 * v0.5.3 Phase 4 (§4.9 / backlog #7) — the app's only loading state. Edit forms render this
 * inside their scaffold while the ViewModel hydrates instead of flashing a blank screen.
 */
@Composable
fun FormLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = LocalAccent.current, strokeWidth = 2.dp)
    }
}

/**
 * v0.5.3 Phase 0 (§3.8 / backlog #26,#28). New params, all defaulted so the plain call is
 * unchanged:
 * - [isError] / [supportingText] — an error state + a fixed spot for the message.
 * - [minLines] — a multiline field looks multiline before the user types.
 * - [tint] — when non-null the box uses `tint.fillRaised` / `tint.onFill` / `tint.accent`
 *   (cursor); for the Home inline reply that currently hand-rolls the field twice.
 */
@Composable
fun DaybookTextField(
    value: String,
    onValueChange: (String) -> Unit,
    // v0.5.3 Phase 5 (§5.7) — nullable: a search field with an inline placeholder needs no
    // floating label. When null the label row + its 6dp spacer are omitted entirely.
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    minLines: Int = 1,
    tint: CardTint? = null
) {
    val boxBg = tint?.fillRaised ?: DaybookColors.SurfaceElevated
    val textColor = tint?.onFill ?: DaybookColors.TextPrimary
    val cursorColor = tint?.accent ?: DaybookColors.TextPrimary
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = DaybookColors.TextMuted)
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.field)
                .background(boxBg)
                .then(if (isError) Modifier.border(1.dp, DaybookColors.Danger, AppShapes.field) else Modifier)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge
                ).copy(color = textColor),
                cursorBrush = SolidColor(cursorColor),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (supportingText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) DaybookColors.Danger else DaybookColors.TextMuted
            )
        }
    }
}

@Composable
fun DayOfWeekSelector(
    selected: List<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    showAllWhenEmpty: Boolean = false
) {
    val accent = LocalAccent.current
    val allSelected = selected.isEmpty() && showAllWhenEmpty
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSel = allSelected || selected.contains(day)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    // v0.5.3 Phase 5 (§5.10) — one day-token shape with WeekStrip's day cells
                    // (RoundedCornerShape(12.dp)); was CircleShape.
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) accent else DaybookColors.SurfaceElevated)
                    .clickableImpl(remember { MutableInteractionSource() }) { onToggle(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    day.name.take(1),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSel) DaybookColors.OnSolid else DaybookColors.TextMuted
                )
            }
        }
    }
}

/**
 * Collapsible "Advanced" section for the add/edit forms.
 *
 * v0.5.3 Phase 5 (§5.10) — when [expandedInitially] is true (the Edit form, where a field inside
 * was customised) the section starts **collapsed** and animates open once via the
 * [AnimatedVisibility] below, so the auto-expansion is visible and understood rather than just
 * "already open". A one-shot saved guard means a later manual collapse sticks.
 */
@Composable
fun AdvancedSection(
    expandedInitially: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var autoExpandDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(expandedInitially) {
        if (expandedInitially && !autoExpandDone) {
            expanded = true
            autoExpandDone = true
        }
    }
    val rm = LocalReduceMotion.current
    val rot by animateFloatAsState(if (expanded) 180f else 0f, if (rm) snap() else Motion.softSpring(), label = "advChevron")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickableImpl(remember { MutableInteractionSource() }) { expanded = !expanded }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Advanced", style = DaybookText.CardTitle, color = DaybookColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = DaybookColors.TextMuted,
                modifier = Modifier.graphicsLayer { rotationZ = rot }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (rm) fadeIn() else fadeIn(Motion.fast()) + expandVertically(Motion.softSpring()),
            exit = if (rm) fadeOut() else fadeOut(Motion.fast()) + shrinkVertically(Motion.softSpring())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun SnoozeStepper(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // v0.5.3 Phase 5 (§5.10) — DaybookIcons.Remove and Material Icons.Filled.Add render at the same
    // (core) weight after the Phase 4.6 icon standardisation, so the mixed source is accepted.
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Snooze interval", style = MaterialTheme.typography.bodyLarge, color = DaybookColors.TextPrimary)
        Spacer(Modifier.weight(1f))
        CircleIconButton(
            icon = DaybookIcons.Remove,
            contentDescription = "Decrease",
            // Floor at 10 min: below that, Doze rate-limits setExactAndAllowWhileIdle to roughly
            // one delivery per app per ~9-10 min, so a shorter interval silently degrades anyway.
            onClick = { if (minutes > 10) onChange(minutes - 5) },
            size = IconButtonSize.Md.dp
        )
        Spacer(Modifier.width(12.dp))
        Text("$minutes min", style = MaterialTheme.typography.titleMedium, color = DaybookColors.TextPrimary)
        Spacer(Modifier.width(12.dp))
        CircleIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Increase",
            onClick = { onChange(minutes + 5) },
            size = IconButtonSize.Md.dp
        )
    }
}

/**
 * v0.5.4 — three-way Crohn's food-diary trigger picker: "Not a flag" / "Maybe" / "Red flag".
 * Semantic colours (muted / amber / red) rather than the app accent, so a flagged meal reads as
 * a warning at a glance. [RedFlag.NONE] is the unflagged state.
 */
@Composable
fun RedFlagPicker(
    selected: RedFlag,
    onSelect: (RedFlag) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RedFlagOption("Not a flag", DaybookColors.TextMuted, selected == RedFlag.NONE) { onSelect(RedFlag.NONE) }
        RedFlagOption("Maybe", DaybookColors.Warning, selected == RedFlag.MAYBE) { onSelect(RedFlag.MAYBE) }
        RedFlagOption("Red flag", DaybookColors.Danger, selected == RedFlag.RED) { onSelect(RedFlag.RED) }
    }
}

@Composable
private fun RowScope.RedFlagOption(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .clip(AppShapes.pill)
            .background(if (selected) color.copy(alpha = 0.18f) else DaybookColors.SurfaceElevated)
            .border(1.dp, if (selected) color else DaybookColors.Hairline, AppShapes.pill)
            // v0.5.3 Phase 4 (§4.7) — single-choice affordance: RadioButton role + selected state.
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) DaybookColors.TextPrimary else DaybookColors.TextMuted,
            maxLines = 1
        )
    }
}

/** Inner status/note chip inside a pastel card. */
@Composable
fun InnerChip(text: String, tint: CardTint, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(tint.fillRaised)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint.onFillMuted)
    }
}
