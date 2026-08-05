package eu.sweetgeorgie.browniedo.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult
    data object Cancelled : GoogleIdTokenResult
    data object NoGoogleAccount : GoogleIdTokenResult
    data class Failure(val cause: Throwable) : GoogleIdTokenResult
}

/**
 * Asks the Credential Manager for a Google ID token. Needs an activity context, so it stays in the
 * UI layer and hands only the raw token to the ViewModel.
 */
class GoogleIdTokenRequester(private val credentialManager: CredentialManager) {

    suspend fun requestIdToken(
        activityContext: Context,
        serverClientId: String
    ): GoogleIdTokenResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setNonce(generateHashedNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credential = credentialManager.getCredential(activityContext, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenResult.Success(
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                )
            } else {
                GoogleIdTokenResult.Failure(
                    IllegalStateException("Unexpected credential type: ${credential.type}")
                )
            }
        } catch (cancellation: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (noCredential: NoCredentialException) {
            GoogleIdTokenResult.NoGoogleAccount
        } catch (failure: Exception) {
            GoogleIdTokenResult.Failure(failure)
        }
    }

    private fun generateHashedNonce(): String {
        val rawNonce = ByteArray(NONCE_BYTE_COUNT).also(SecureRandom()::nextBytes)
        return MessageDigest.getInstance("SHA-256")
            .digest(rawNonce)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val NONCE_BYTE_COUNT = 32
    }
}
