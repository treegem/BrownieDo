package eu.sweetgeorgie.browniedo.data.list

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import eu.sweetgeorgie.browniedo.data.LISTS_COLLECTION
import eu.sweetgeorgie.browniedo.data.list.ListField.MEMBERS
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Reads the lists the signed-in user belongs to, see
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 *
 * Writing is not offered on purpose: `firestore.rules` forbids it with `write: if false`, list
 * documents are created by hand in the console until roadmap phase 8b lifts that.
 */
class FirestoreListRepository(
    private val firestore: FirebaseFirestore,
    authRepository: AuthRepository
) : ListRepository {

    /**
     * Follows the session: signing out empties the lists instead of leaving the previous user's
     * ones on screen, and signing in starts a fresh query for the new uid.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val lists: Flow<Result<List<TodoList>>> =
        authRepository.signedInUser.flatMapLatest { signedInUser ->
            signedInUser?.let { listsOf(it.uid) } ?: flowOf(Result.success(emptyList()))
        }

    /**
     * The [MEMBERS] filter is not an optimization — it is what makes the query legal at all.
     * Security rules are not filters: `firestore.rules` allows reading a list only for its members,
     * and Firestore accepts a collection query only when it can prove up front that every possible
     * result satisfies that rule. Without the filter the whole query fails, not just the foreign
     * documents.
     *
     * Sorting happens in the client for the same reason as for todos, see
     * docs/decisions/0010-sortierung-im-client-statt-orderby.md — an `orderBy` here would also
     * require a composite index.
     */
    private fun listsOf(uid: String): Flow<Result<List<TodoList>>> = callbackFlow {
        val registration = firestore.collection(LISTS_COLLECTION)
            .whereArrayContains(MEMBERS, uid)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(Result.failure(error))
                    snapshot != null -> trySend(Result.success(snapshot.toTodoLists()))
                }
            }
        awaitClose { registration.remove() }
    }
}

/**
 * Lists are ordered by name, case-insensitively — nothing in the app depends on when a list was
 * created. Kept apart from [toTodoLists] so it can be tested without a [QuerySnapshot].
 */
internal val LIST_ORDER: Comparator<TodoList> =
    compareBy(String.CASE_INSENSITIVE_ORDER, TodoList::name)

private fun QuerySnapshot.toTodoLists(): List<TodoList> =
    documents.mapNotNull { document ->
        document.toObject(ListDocument::class.java)?.toTodoList(document.id)
    }.sortedWith(LIST_ORDER)
