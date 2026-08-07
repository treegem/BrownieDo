package eu.sweetgeorgie.browniedo.data.todo

import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads and writes the entries of a single list at `lists/{listId}/todos`, see
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 */
class FirestoreTodoRepository(
    firestore: FirebaseFirestore,
    listId: String
) : TodoRepository {

    private val todoCollection = firestore
        .collection(LISTS_COLLECTION)
        .document(listId)
        .collection(TODOS_COLLECTION)

    override val todos: Flow<Result<List<Todo>>> = callbackFlow {
        val registration = todoCollection.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(error))
                snapshot != null -> trySend(Result.success(snapshot.toTodos()))
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * The server fills in the timestamps, so only the title is written here.
     *
     * The write is not awaited, see docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md.
     */
    override fun addTodo(title: String): Result<Unit> =
        runCatching { todoCollection.add(TodoDocument(title = title)) }.map { }

    companion object {
        /** The single list the app works with until list management arrives in roadmap phase 8. */
        const val DEFAULT_LIST_ID = "shared"

        private const val LISTS_COLLECTION = "lists"
        private const val TODOS_COLLECTION = "todos"
    }
}

/**
 * Documents that cannot be mapped are dropped instead of reported: they were not written by this
 * app, so neither of the two users can cause or fix that.
 *
 * Sorting happens here rather than through `orderBy`, see
 * docs/decisions/0010-sortierung-im-client-statt-orderby.md.
 */
private fun QuerySnapshot.toTodos(): List<Todo> =
    documents.mapNotNull { document ->
        document.toObject(TodoDocument::class.java, ESTIMATE)?.toTodo(document.id)
    }.sortedByDescending(Todo::createdAt)
