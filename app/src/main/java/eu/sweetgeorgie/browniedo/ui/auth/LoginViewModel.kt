package eu.sweetgeorgie.browniedo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    fun signIn(requestIdToken: suspend () -> GoogleIdTokenResult) {
        if (mutableUiState.value.isSigningIn) return
        mutableUiState.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            when (val tokenResult = requestIdToken()) {
                is GoogleIdTokenResult.Success -> signInWithFirebase(tokenResult.idToken)
                GoogleIdTokenResult.Cancelled -> finishWith(error = null)
                GoogleIdTokenResult.NoGoogleAccount -> finishWith(LoginError.NO_GOOGLE_ACCOUNT)
                is GoogleIdTokenResult.Failure -> finishWith(LoginError.SIGN_IN_FAILED)
            }
        }
    }

    fun dismissError() = mutableUiState.update { it.copy(error = null) }

    private suspend fun signInWithFirebase(idToken: String) {
        val error = authRepository.signInWithGoogleIdToken(idToken)
            .fold(onSuccess = { null }, onFailure = { LoginError.SIGN_IN_FAILED })
        finishWith(error)
    }

    private fun finishWith(error: LoginError?) =
        mutableUiState.update { it.copy(isSigningIn = false, error = error) }
}
