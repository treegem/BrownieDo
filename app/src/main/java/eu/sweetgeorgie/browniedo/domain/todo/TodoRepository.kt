package eu.sweetgeorgie.browniedo.domain.todo

import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    /**
     * Entries of the list, newest first. Emits again on every remote or local change.
     *
     * A failure means the list could not be observed — the previously emitted entries stay valid
     * until the next successful emission.
     */
    val todos: Flow<Result<List<Todo>>>

    /**
     * Adds an entry. The result only reports whether the write was accepted locally — Firestore
     * delivers it to the server on its own, see
     * docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md.
     */
    fun addTodo(title: String): Result<Unit>

    /**
     * Marks an entry as done or open again. [completedBy] is the uid of the partner who ticked it
     * off and is `null` whenever [isDone] is `false`.
     */
    fun setDone(todoId: String, isDone: Boolean, completedBy: String?): Result<Unit>

    fun setTitle(todoId: String, title: String): Result<Unit>
}
