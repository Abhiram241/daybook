package com.daybook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.daybook.app.ui.theme.DaybookColors
import com.daybook.app.ui.theme.LocalAccent
import java.io.File

/**
 * Circular profile avatar. Shows the picked photo (via Coil) when [photoPath] points at a
 * readable file; otherwise falls back to an accent monogram (initials from [name], a person
 * glyph when the name is blank) — the same look the Settings header used before photos.
 */
@Composable
fun Avatar(
    photoPath: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val file = remember(photoPath) { photoPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 } }

    var box = modifier
        .size(size)
        .clip(CircleShape)
    if (onClick != null) {
        // v0.5.3 Phase 4 (§4.7) — a11y: name the action and give the control a Button role +
        // one consistent contentDescription regardless of the photo / monogram branch below.
        box = box
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Open settings",
                role = Role.Button,
                onClick = onClick
            )
    }
    box = box.semantics(mergeDescendants = true) { contentDescription = "Profile picture" }
    if (ring) {
        box = box.border(1.dp, DaybookColors.Hairline, CircleShape)
    }

    Box(modifier = box, contentAlignment = Alignment.Center) {
        if (file != null) {
            AsyncImage(
                model = remember(file) {
                    ImageRequest.Builder(context)
                        // The file in filesDir IS the store — disk-caching a local file is
                        // pointless and can serve a stale copy after a re-pick.
                        .data(file)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCacheKey(file.absolutePath)
                        .crossfade(true)
                        .build()
                },
                contentDescription = null, // v0.5.3 Phase 4 (§4.7) — described by the parent Box.
                contentScale = ContentScale.Crop,
                onLoading = {},
                onSuccess = {},
                onError = { Log.w("Avatar", "coil failed for $photoPath", it.result.throwable) },
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            Box(
                Modifier.size(size).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                val initials = remember(name) {
                    name.trim().split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                }
                if (initials.isEmpty()) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = DaybookColors.OnSolid,
                        modifier = Modifier.size(size * 0.52f)
                    )
                } else {
                    Text(
                        initials,
                        color = DaybookColors.OnSolid,
                        // v0.5.3 Phase 7 (#39) — the monogram style is a size-parametrised
                        // TextStyle derived straight from the theme's titleLarge slot. The old
                        // `LocalTextStyle.current.merge(...)` added nothing here (no ProvideTextStyle
                        // wraps this Box, so the ambient style is TextStyle.Default and every field
                        // titleLarge sets already won the merge); dropping it removes the ambiguity.
                        style = MaterialTheme.typography.titleLarge
                            .copy(fontSize = (size.value * 0.38f).sp)
                    )
                }
            }
        }
    }
}
