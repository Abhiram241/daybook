package com.daybook.app.ui.components

import androidx.compose.runtime.Immutable

/** The bits of the user's profile the tab headers show in the corner [Avatar]. */
@Immutable
data class ProfileUi(val name: String, val photoPath: String?)
