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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // Keyboard-cover bug (builds 40/41 failed, see KEYBOARD_COVER_BUG_HANDOVER.md) — the real
    // cause was geometric, not timing: the requester sat on the inner `BasicTextField`, whose
    // bounds exclude the 14dp box padding around it. A scrollable parent's bring-into-view scrolls
    // the *minimum* distance, so it stopped as soon as the bare text line touched the keyboard's
    // top edge — leaving the box's bottom padding (and anything beside it, like the Home reply's
    // send button) under the keyboard however many times / however late it was re-issued. The
    // requester now covers the whole field (label, box, supporting text) plus a small margin.
    var isFieldFocused by remember { mutableStateOf(false) }
    Column(modifier.bringIntoViewWhileImeOpens(isFieldFocused)) {
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
                // Bug fix — placeholder text used to render at full body-text size/weight in
                // TextMuted, close enough to real content's contrast that an empty field could
                // be misread as already filled in. A lower alpha (still legible, clearly
                // secondary) keeps it from competing with actual values.
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DaybookColors.TextMuted.copy(alpha = 0.55f)
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusEvent { state -> isFieldFocused = state.isFocused }
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

/** Breathing room kept between a revealed element's bottom edge and the keyboard's top edge. */
private val ImeRevealMargin = 16.dp

/**
 * While [active], keeps this element's WHOLE bounds (plus [ImeRevealMargin] below) scrolled into
 * view of the nearest scrollable ancestor as the keyboard opens. Apply it to the outermost thing
 * that must stay visible — a whole card, not just the text field inside it.
 *
 * Re-issued on every frame of the IME inset animation (`WindowInsets.ime` is the raw window inset;
 * an ancestor's `imePadding()` only marks it consumed for other padding modifiers, it doesn't zero
 * this read — `WorkoutSessionScreen`'s set cells rely on the same thing), and once more after a
 * short settle for any expand animation still running inside the target.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.bringIntoViewWhileImeOpens(active: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val reveal: suspend () -> Unit = {
            val marginPx = with(density) { ImeRevealMargin.toPx() }
            requester.bringIntoView(
                Rect(0f, 0f, size.width.toFloat(), size.height.toFloat() + marginPx)
            )
        }
        coroutineScope {
            launch { snapshotFlow { imeInsets.getBottom(density) }.collectLatest { reveal() } }
            launch { delay(350); reveal() }
        }
    }
    return this
        .onSizeChanged { size = it }
        .bringIntoViewRequester(requester)
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
                    color = if (isSel) DaybookColors.OnAccent else DaybookColors.TextMuted
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
        Text(
            "Snooze interval",
            style = MaterialTheme.typography.bodyLarge,
            color = DaybookColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
            .heightIn(min = 34.dp)
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
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) DaybookColors.TextPrimary else DaybookColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
