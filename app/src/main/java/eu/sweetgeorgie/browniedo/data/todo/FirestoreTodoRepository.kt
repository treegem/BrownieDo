package eu.sweetgeorgie.browniedo.data.todo

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import eu.sweetgeorgie.browniedo.data.LISTS_COLLECTION
import eu.sweetgeorgie.browniedo.data.TODOS_COLLECTION
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
 * Reads and writes the entries at `lists/{listId}/todos`, see
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 *
 * The list is resolved per call rather than held as state — the reasoning is in [TodoRepository].
 */
class FirestoreTodoRepository(private val firestore: FirebaseFirestore) : TodoRepository {

    private fun todoCollection(listId: String): CollectionReference = firestore
        .collection(LISTS_COLLECTION)
        .document(listId)
        .collection(TODOS_COLLECTION)

    override fun todos(listId: String): Flow<Result<List<Todo>>> = callbackFlow {
        val registration = todoCollection(listId).addSnapshotListener { snapshot, error ->
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
    override fun addTodo(listId: String, title: String): Result<Unit> =
        runCatching { todoCollection(listId).add(TodoDocument(title = title)) }.map { }

    /**
     * Writes single fields instead of the whole document, which is exactly the field-level
     * last-write-wins the project settled on: a title edited at the same time survives.
     *
     * [UPDATED_AT] has to be set by hand here and in [setTitle] — the `@ServerTimestamp`
     * annotation on [TodoDocument] only applies when the whole object is written, so a field
     * update would leave the old timestamp in place and break conflict resolution, see
     * docs/decisions/0006-server-zeitstempel-fuer-last-write-wins.md.
     */
    override fun setDone(
        listId: String,
        todoId: String,
        isDone: Boolean,
        completedBy: String?
    ): Result<Unit> =
        runCatching {
            todoCollection(listId).document(todoId).update(
                mapOf(
                    DONE to isDone,
                    COMPLETED_BY to completedBy,
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.map { }

    override fun setTitle(listId: String, todoId: String, title: String): Result<Unit> =
        runCatching {
            todoCollection(listId).document(todoId).update(
                mapOf(
                    TITLE to title,
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.map { }

    override fun deleteTodo(listId: String, todoId: String): Result<Unit> =
        runCatching { todoCollection(listId).document(todoId).delete() }.map { }
}

/**
 * Open entries first, and within both groups the newest on top. Ticking an entry off therefore
 * moves it out of the way immediately, which is what the list is walked down for while shopping.
 *
 * Kept apart from [toTodos] so it can be tested without a [QuerySnapshot].
 */
internal val TODO_ORDER: Comparator<Todo> =
    compareBy(Todo::isDone).thenByDescending(Todo::createdAt)

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
    }.sortedWith(TODO_ORDER)
