package eu.sweetgeorgie.browniedo.data.todo

import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import eu.sweetgeorgie.browniedo.data.todo.TodoField.COMPLETED_BY
import eu.sweetgeorgie.browniedo.data.todo.TodoField.DONE
import eu.sweetgeorgie.browniedo.data.todo.TodoField.TITLE
import eu.sweetgeorgie.browniedo.data.todo.TodoField.UPDATED_AT
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

    /**
     * Writes single fields instead of the whole document, which is exactly the field-level
     * last-write-wins the project settled on: a title edited at the same time survives.
     *
     * [UPDATED_AT] has to be set by hand here and in [setTitle] — the `@ServerTimestamp`
     * annotation on [TodoDocument] only applies when the whole object is written, so a field
     * update would leave the old timestamp in place and break conflict resolution, see
     * docs/decisions/0006-server-zeitstempel-fuer-last-write-wins.md.
     */
    override fun setDone(todoId: String, isDone: Boolean, completedBy: String?): Result<Unit> =
        runCatching {
            todoCollection.document(todoId).update(
                mapOf(
                    DONE to isDone,
                    COMPLETED_BY to completedBy,
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.map { }

    override fun setTitle(todoId: String, title: String): Result<Unit> =
        runCatching {
            todoCollection.document(todoId).update(
                mapOf(
                    TITLE to title,
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.map { }

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
