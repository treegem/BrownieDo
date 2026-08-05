package eu.sweetgeorgie.browniedo.ui.auth

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onSignInClick: (activityContext: Context, serverClientId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalActivity.current
    val serverClientId = stringResource(R.string.default_web_client_id)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.login_headline),
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = stringResource(R.string.login_subline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        if (uiState.isSigningIn) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { activity?.let { onSignInClick(it, serverClientId) } },
                enabled = activity != null
            ) {
                Text(text = stringResource(R.string.login_sign_in_with_google))
            }
        }

        uiState.error?.let { error ->
            Text(
                text = stringResource(error.messageResId()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun LoginError.messageResId() = when (this) {
    LoginError.NO_GOOGLE_ACCOUNT -> R.string.login_error_no_google_account
    LoginError.SIGN_IN_FAILED -> R.string.login_error_sign_in_failed
}
