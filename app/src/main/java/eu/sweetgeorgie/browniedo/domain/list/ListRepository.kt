package eu.sweetgeorgie.browniedo.domain.list

import kotlinx.coroutines.flow.Flow

interface ListRepository {
    /**
     * Lists the signed-in user belongs to, ordered by name. Emits again on every remote or local
     * change, and an empty list while nobody is signed in.
     *
     * A failure means the lists could not be observed — the previously emitted ones stay valid
     * until the next successful emission, same as for todos.
     */
    val lists: Flow<Result<List<TodoList>>>

    /**
     * Creates a list. [shared] decides whether the partner is put on it as well; who that is stays
     * inside the data layer, see docs/decisions/0020-partner-aus-users-collection.md.
     *
     * Fails when [shared] is true and no partner is known — the caller is expected to not offer the
     * option in that case, this is the second line of defence.
     *
     * Unlike the todo writes this suspends: the partner has to be looked up first, see
     * docs/decisions/0019-schreibrechte-auf-listen-dokumente.md.
     */
    suspend fun createList(name: String, shared: Boolean): Result<Unit>

    /**
     * Renames a list. Who is on it cannot be changed — `firestore.rules` rejects that outright.
     */
    fun renameList(listId: String, name: String): Result<Unit>

    /**
     * Deletes a list **and every todo in it**. Firestore does not cascade, so the entries have to go
     * explicitly; the order matters and is guarded, see
     * docs/decisions/0019-schreibrechte-auf-listen-dokumente.md.
     */
    suspend fun deleteList(listId: String): Result<Unit>
}
