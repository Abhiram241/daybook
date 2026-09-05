package com.daybook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.icons.DaybookIcons
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent

@Immutable
data class SortOption(val key: String, val label: String)

@Immutable
data class FacetOption(val key: String, val label: String, val count: Int?)

// The row shape + the tint an active (selected sort / checked facet / archived-on) row fills with.
private val RowShape = RoundedCornerShape(10.dp)
private fun Color.activeRowTint() = copy(alpha = 0.12f)

/**
 * Shared sort / filter sheet (v0.5.1 §A). One `ModalBottomSheet` in the same visual language as
 * [BottomSheetMenu] — `DaybookColors.Surface` + `AppShapes.sheet`.
 *
 * Parameterised so it covers three callers:
 *  - Intake: sort rows + a "Type" facet section + "Show archived" + Reset (§A);
 *  - Habits: sort rows + "Show archived only" + Reset, no facet (§B);
 *  - App-lock "Lock after": sort rows only ([showArchived]/[onReset] null), `dismissOnSelect` (§K-UI).
 *
 * build 18 restyle: content-sized (skipPartiallyExpanded), tighter rows, and every active row/
 * control now paints with the live [LocalAccent] — hoisted here and threaded down so it resolves
 * correctly inside the sheet's own dialog window rather than falling back to the default accent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    sortOptions: List<SortOption>,
    selectedSortKey: String,
    onSelectSort: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Sort by",
    facetTitle: String? = null,
    facetOptions: List<FacetOption> = emptyList(),
    selectedFacetKeys: Set<String> = emptySet(),
    onToggleFacet: (String) -> Unit = {},
    showArchived: Boolean? = null,
    onToggleArchived: (() -> Unit)? = null,
    archivedRowLabel: String = "Show archived",
    onReset: (() -> Unit)? = null,
    dismissOnSelect: Boolean = false,
    // v0.5.3 Phase 5 (§5.16 / UI Q7) — App-lock's "Lock after" is a plain single choice, not a
    // sort. With this the section header renders in normal case (not "SORT BY" uppercased).
    neutralHeader: Boolean = false,
) {
    if (!visible) return
    // Resolve the accent ONCE, out here in the host composition — passing it down means the sheet's
    // controls stay on-accent even though ModalBottomSheet hosts its content in a separate window.
    val accent = LocalAccent.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(2.dp))

            // v0.5.3: the Home "Reminders" filter reuses this sheet with no sort rows at all —
            // skip the section entirely rather than leaving a dangling header.
            if (sortOptions.isNotEmpty()) {
                SheetSectionLabel(title, accent = accent, neutral = neutralHeader)
                sortOptions.forEach { opt ->
                    SortRow(
                        label = opt.label,
                        selected = opt.key == selectedSortKey,
                        accent = accent,
                        onClick = {
                            onSelectSort(opt.key)
                            if (dismissOnSelect) onDismiss()
                        }
                    )
                }
            }

            if (facetTitle != null && facetOptions.isNotEmpty()) {
                if (sortOptions.isNotEmpty()) SheetDivider()
                SheetSectionLabel(facetTitle, accent = accent)
                facetOptions.forEach { f ->
                    FacetRow(
                        label = f.label,
                        count = f.count,
                        checked = f.key in selectedFacetKeys,
                        accent = accent,
                        onClick = { onToggleFacet(f.key) }
                    )
                }
            }

            if (showArchived != null && onToggleArchived != null) {
                SheetDivider()
                ArchivedRow(
                    label = archivedRowLabel,
                    checked = showArchived,
                    accent = accent,
                    onToggle = onToggleArchived
                )
            }

            if (onReset != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(AppShapes.pill)
                        .border(1.dp, accent.copy(alpha = 0.55f), AppShapes.pill)
                        .clickableImpl(remember { MutableInteractionSource() }) {
                            onReset()
                            onDismiss()
                        }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** "SORT BY" / "TYPE" section label, tinted with the accent so the sheet reads as themed. */
@Composable
internal fun SheetSectionLabel(text: String, accent: Color, neutral: Boolean = false) {
    Text(
        // v0.5.3 Phase 5 (§5.16 / UI Q7) — a neutral single-choice sheet keeps the title in normal case.
        text = if (neutral) text else text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        color = DaybookColors.Hairline,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
    )
}

/** Shared outer wrapper: horizontal inset, rounded active-state fill, ripple, inner padding. */
@Composable
private fun SheetRow(
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 2.dp)
        .clip(RowShape)
        .background(if (active) accent.activeRowTint() else Color.Transparent)
        .clickableImpl(remember { MutableInteractionSource() }, onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

@Composable
private fun SortRow(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    SheetRow(active = selected, accent = accent, onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else DaybookColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        RadioDot(selected = selected, accent = accent)
    }
}

/**
 * A compact radio indicator — replaces M3 `RadioButton`, which pads itself to a 48dp touch
 * target and made every sort row tower over the content. The whole [SheetRow] is the tap target.
 */
@Composable
private fun RadioDot(selected: Boolean, accent: Color) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) accent else DaybookColors.TextFaint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun FacetRow(label: String, count: Int?, checked: Boolean, accent: Color, onClick: () -> Unit) {
    SheetRow(active = checked, accent = accent, onClick = onClick) {
        Icon(
            imageVector = if (checked) DaybookIcons.CheckBox else DaybookIcons.CheckBoxBlank,
            contentDescription = null,
            tint = if (checked) accent else DaybookColors.TextFaint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (checked) accent else DaybookColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = if (checked) accent else DaybookColors.TextMuted,
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(
                        if (checked) accent.copy(alpha = 0.16f) else DaybookColors.Hairline
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ArchivedRow(label: String, checked: Boolean, accent: Color, onToggle: () -> Unit) {
    SheetRow(active = checked, accent = accent, onClick = onToggle) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (checked) accent else DaybookColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = DaybookColors.OnSolid,
                uncheckedTrackColor = DaybookColors.SurfaceElevated,
                uncheckedBorderColor = DaybookColors.Hairline,
                uncheckedThumbColor = DaybookColors.TextMuted
            )
        )
    }
}
