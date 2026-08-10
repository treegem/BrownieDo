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
}
