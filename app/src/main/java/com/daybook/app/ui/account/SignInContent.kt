package com.daybook.app.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.daybook.app.R
import com.daybook.app.ui.components.PrimaryButton
import com.daybook.app.ui.theme.DaybookColors

/**
 * The sign-in form, extracted from `AccountScreen`'s `SignInScreen` (v0.5.1 §D-UI) so the same
 * pixels serve two hosts: Settings → Account (`SettingsSubScreen`, with a back button) and the
 * blocking launch gate ([SignInGateScreen], full-bleed, no back, no skip).
 *
 * v0.5.2: Google is the only sign-in method. The email/password form is gone.
 *
 * @param blurb the one-line lead copy. Settings uses the default "synced across devices" line;
 *   the gate passes copy that says an account is now required.
 */
@Composable
fun SignInContent(
    form: AccountForm,
    vm: AccountViewModel,
    modifier: Modifier = Modifier,
    blurb: String = "Sign in to keep your Daybook synced across devices."
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = DaybookColors.TextMuted,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        GoogleSignInButton(form, vm)
    }
}

/**
 * v0.5.5 Phase 6 — the "Continue with Google" primary button + its inline error message.
 * Extracted from [SignInContent] so the launch gate ([SignInGateScreen]) can pin it in a bottom
 * bar while rendering the blurb itself in the scroll region. Settings → Account still calls
 * [SignInContent], so its rendering is unchanged.
 */
@Composable
fun GoogleSignInButton(
    form: AccountForm,
    vm: AccountViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier.fillMaxWidth()) {
        // v0.5.3 Phase 5 (§5.2) — the primary action on a blocking gate is a PrimaryButton, not a
        // GhostButton; shows a spinner while busy.
        PrimaryButton(
            text = "Continue with Google",
            onClick = { vm.continueWithGoogle(context) },
            enabled = !form.busy,
            loading = form.busy,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                // Image, not Icon: the mark carries its own four brand colours and must not
                // inherit a content tint (v0.5.1 §E).
                Image(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        form.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = DaybookColors.Warning)
        }
    }
}
