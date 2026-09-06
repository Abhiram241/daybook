package com.daybook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daybook.app.ui.theme.AppShapes
import com.daybook.app.ui.theme.CardTints
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent

@Immutable
data class SheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    /** Optional one-line hint under the label (v0.5.1 §C). Null keeps the row single-line. */
    val description: String? = null,
    val onClick: () -> Unit
)

/**
 * Compact action sheet (v0.5.1 §C — roughly half the previous row height). Each row is
 * icon + label + an optional one-line description, in the same visual language as [SortSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: List<SheetAction>
) {
    if (!visible) return
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = DaybookColors.Surface,
        shape = AppShapes.sheet
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.button)
                        .clickableImpl(remember { MutableInteractionSource() }) {
                            action.onClick()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(AppShapes.tile)
                            .background(
                                if (action.destructive) DaybookColors.Danger.copy(alpha = 0.16f)
                                else DaybookColors.SurfaceElevated
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = if (action.destructive) DaybookColors.Danger else DaybookColors.TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (action.destructive) DaybookColors.Danger else DaybookColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (action.description != null) {
                            Text(
                                action.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = DaybookColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

private val TintNames = listOf("LAVENDER", "PEACH", "MINT", "BUTTER", "SLATE_BLUE", "ROSE")

/** Optional per-card tint override. Selected value is a [ColorTag] name or "AUTO". */
@Composable
fun TintPicker(
    selectedName: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // v0.5.3 Phase 5 (§5.13 / backlog #24) — shared Swatch grammar with the Appearance accent
    // picker: rounded-square, 44dp target, selected Check (no unselected inner dot).
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Swatch(
            color = DaybookColors.SurfaceElevated,
            selected = selectedName == "AUTO",
            onClick = { onSelect("AUTO") },
            label = "A",
            checkColor = LocalAccent.current
        )
        TintNames.forEachIndexed { i, name ->
            val tint = CardTints.ALL[i]
            Swatch(
                color = tint.fill,
                selected = selectedName == name,
                onClick = { onSelect(name) },
                checkColor = tint.accent
            )
        }
    }
}
