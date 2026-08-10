package eu.sweetgeorgie.browniedo.data.user

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import eu.sweetgeorgie.browniedo.data.USERS_COLLECTION
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.user.Partner
import eu.sweetgeorgie.browniedo.domain.user.PartnerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Reads the `users` collection to find the other person, see
 * docs/decisions/0020-partner-aus-users-collection.md.
 *
 * Read-only on purpose: the documents are maintained by hand in the console, and `firestore.rules`
 * forbids writing them.
 */
class FirestorePartnerRepository(
    private val firestore: FirebaseFirestore,
    authRepository: AuthRepository
) : PartnerRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val partner: Flow<Partner?> =
        authRepository.signedInUser.flatMapLatest { signedInUser ->
            signedInUser?.let { partnerOf(it.uid) } ?: flowOf(null)
        }

    /**
     * A failure ends up as null rather than as an error: for the caller a partner that cannot be
     * read is the same as one that does not exist — a shared list cannot be offered either way, and
     * the lists themselves are unaffected.
     */
    private fun partnerOf(ownUid: String): Flow<Partner?> = callbackFlow {
        val registration = firestore.collection(USERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(null)
                    snapshot != null -> trySend(snapshot.toPartner(ownUid))
                }
            }
        awaitClose { registration.remove() }
    }
}

/**
 * The first entry that is not the signed-in user. With exactly two documents that is unambiguous;
 * should a third ever appear, the rule stays deterministic only because the caller never depends on
 * *which* one — there is meant to be only one other.
 */
private fun QuerySnapshot.toPartner(ownUid: String): Partner? =
    documents.asSequence()
        .filter { it.id != ownUid }
        .mapNotNull { document ->
            document.toObject(UserDocument::class.java)?.toPartner(document.id)
        }
        .firstOrNull()
