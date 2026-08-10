package eu.sweetgeorgie.browniedo.data.list

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import eu.sweetgeorgie.browniedo.data.LISTS_COLLECTION
import eu.sweetgeorgie.browniedo.data.TODOS_COLLECTION
import eu.sweetgeorgie.browniedo.data.list.ListField.MEMBERS
import eu.sweetgeorgie.browniedo.data.list.ListField.NAME
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.user.PartnerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Reads and writes the lists the signed-in user belongs to, see
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md and
 * docs/decisions/0019-schreibrechte-auf-listen-dokumente.md.
 */
class FirestoreListRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val partnerRepository: PartnerRepository
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

    override suspend fun createList(name: String, shared: Boolean): Result<Unit> = runCatching {
        val ownUid = authRepository.currentUser?.uid ?: error("Nobody is signed in.")
        val members = if (shared) {
            val partnerUid = partnerRepository.partner.first()?.uid
                ?: error("No partner is on file, a shared list cannot be created.")
            listOf(ownUid, partnerUid)
        } else {
            listOf(ownUid)
        }

        firestore.collection(LISTS_COLLECTION)
            .add(ListDocument(name = name, members = members))
            .await()
    }.map { }

    /**
     * Writes only the name. Sending the whole [ListDocument] would resubmit `members` and reset
     * `createdAt`, and the update rule rejects any change to `members` anyway.
     */
    override fun renameList(listId: String, name: String): Result<Unit> =
        runCatching {
            firestore.collection(LISTS_COLLECTION).document(listId).update(NAME, name)
        }.map { }

    /**
     * Deletes the entries and the list document in **one batch**. Security rules evaluate every
     * operation of a batch against the state *before* the commit, so `isListMember` still finds the
     * list document although the same batch removes it. That sidesteps a trap: deleting the list
     * document first would leave its todos permanently undeletable, because the membership lookup
     * in the rule has nothing left to read.
     *
     * A batch holds at most 500 operations. With more entries the deletion is chunked, and the list
     * document goes into the **last** chunk. If an earlier chunk fails, some todos are gone and the
     * list is still there — a state that can simply be retried. Never the other way round.
     */
    override suspend fun deleteList(listId: String): Result<Unit> = runCatching {
        val listDocument = firestore.collection(LISTS_COLLECTION).document(listId)
        val todoIds = listDocument.collection(TODOS_COLLECTION).get().await()
            .documents.map { it.id }

        val chunks = todoIds.chunked(MAX_DELETES_PER_BATCH).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { index, todoIdChunk ->
            val batch = firestore.batch()
            todoIdChunk.forEach { todoId ->
                batch.delete(listDocument.collection(TODOS_COLLECTION).document(todoId))
            }
            if (index == chunks.lastIndex) batch.delete(listDocument)
            batch.commit().await()
        }
    }.map { }

    private companion object {
        /** 500 is the hard limit of a batch; one slot stays free for the list document itself. */
        const val MAX_DELETES_PER_BATCH = 499
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
