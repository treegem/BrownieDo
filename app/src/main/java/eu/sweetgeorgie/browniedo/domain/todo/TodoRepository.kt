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
     * Entries of [listId]: open ones first with the most urgent on top, finished ones below with
     * the most recently ticked off first, see
     * docs/decisions/0023-prioritaet-migration-und-sortierung.md. Emits again on every remote or
     * local change.
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
     * off and is `null` whenever [isDone] is `false`. The time it was ticked off is recorded
     * alongside it and comes from the server, not from the device.
     */
    fun setDone(listId: String, todoId: String, isDone: Boolean, completedBy: String?): Result<Unit>

    /**
     * Writes what the edit dialog owns — title and priority — in a single update, so that one save
     * is one write. What that means for concurrent edits is in
     * docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md.
     */
    fun updateTodo(
        listId: String,
        todoId: String,
        title: String,
        priority: TodoPriority
    ): Result<Unit>

    /**
     * Moves an entry from [fromListId] to [toListId]. Firestore has no move: the document is
     * created anew in the target list and removed from the source, both in one batch so the entry
     * never exists twice and never not at all.
     *
     * [todo] travels whole — every field describing the entry survives, only the document id and
     * `updatedAt` are new, see docs/decisions/0024-verschieben-behaelt-zustand.md. The caller
     * passes title and priority in the state the edit dialog holds: moving and editing are one
     * write, see docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md.
     *
     * Taking a whole [Todo] rather than the fields is the exception on this interface, and it
     * follows from ADR 0024: what gets written is the entry itself, not a change to one of its
     * fields.
     *
     * Unlike `ListRepository.deleteList` this does not wait for the server — there is nothing to
     * look up first, so docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md applies unchanged
     * and moving works offline.
     */
    fun moveTodo(fromListId: String, toListId: String, todo: Todo): Result<Unit>

    fun deleteTodo(listId: String, todoId: String): Result<Unit>
}
