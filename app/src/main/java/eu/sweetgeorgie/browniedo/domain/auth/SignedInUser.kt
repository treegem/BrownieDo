package eu.sweetgeorgie.browniedo.domain.auth

data class SignedInUser(
    val uid: String,
    val displayName: String?,
    val email: String?
)
