package eu.sweetgeorgie.browniedo.ui.auth

enum class LoginError {
    NO_GOOGLE_ACCOUNT,
    SIGN_IN_FAILED
}

data class LoginUiState(
    val isSigningIn: Boolean = false,
    val error: LoginError? = null
)
