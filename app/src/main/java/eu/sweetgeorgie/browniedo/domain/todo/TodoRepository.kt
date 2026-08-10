package eu.sweetgeorgie.browniedo.domain.todo

import kotlinx.coroutines.flow.Flow

/**
 * Every method takes the list it works on instead of the repository remembering a current one.
 *
 * That is a deliberate choice: BrownieDo is built around two people working at the same time, and a
 * repository holding the selected list would make "which list am I writing to?" implicit. A write
 * still in flight while the user switches lists would land in the wrong one. The explicit parameter
 * makes that impossible.
 */
interface TodoRepository {
    /**
     * Entries of [listId], open ones first and newest on top. Emits again on every remote or local
     * change.
     *
     * A failure means the list could not be observed — the previously emitted entries stay valid
     * until the next successful emission.
     */
    fun todos(listId: String): Flow<Result<List<Todo>>>

    /**
     * Adds an entry to [listId]. The result only reports whether the write was accepted locally —
     * Firestore delivers it to the server on its own, see
     * docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md.
     */
    fun addTodo(listId: String, title: String): Result<Unit>

    /**
     * Marks an entry as done or open again. [completedBy] is the uid of the partner who ticked it
     * off and is `null` whenever [isDone] is `false`.
     */
    fun setDone(listId: String, todoId: String, isDone: Boolean, completedBy: String?): Result<Unit>

    fun setTitle(listId: String, todoId: String, title: String): Result<Unit>

    fun deleteTodo(listId: String, todoId: String): Result<Unit>
}
