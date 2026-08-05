package eu.sweetgeorgie.browniedo.domain.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Snapshot of the restored session, used as initial UI state before [signedInUser] emits. */
    val currentUser: SignedInUser?

    val signedInUser: Flow<SignedInUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): Result<SignedInUser>

    fun signOut()
}
