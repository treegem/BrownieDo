package eu.sweetgeorgie.browniedo.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.auth.SignedInUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val firebaseAuth: FirebaseAuth) : AuthRepository {

    override val currentUser: SignedInUser?
        get() = firebaseAuth.currentUser?.toSignedInUser()

    override val signedInUser: Flow<SignedInUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toSignedInUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<SignedInUser> =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val signedInFirebaseUser = firebaseAuth.signInWithCredential(credential).await().user
                ?: error("Firebase returned no user after a successful sign-in.")
            signedInFirebaseUser.toSignedInUser()
        }

    override fun signOut() = firebaseAuth.signOut()
}

private fun FirebaseUser.toSignedInUser() = SignedInUser(
    uid = uid,
    displayName = displayName,
    email = email
)
