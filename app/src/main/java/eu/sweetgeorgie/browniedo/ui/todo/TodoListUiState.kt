package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.user.Partner

enum class TodoListError {
    LOAD_FAILED,
    ADD_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED,
    LIST_ADD_FAILED,
    LIST_UPDATE_FAILED,
    LIST_DELETE_FAILED
}

data class TodoListUiState(
    val lists: List<TodoList> = emptyList(),
    /** Null while the lists are still loading, or when the user belongs to none at all. */
    val selectedList: TodoList? = null,
    /** Null when nobody is on file in `users`; only then a shared list cannot be offered. */
    val partner: Partner? = null,
    val todos: List<Todo> = emptyList(),
    val isLoading: Boolean = true,
    val newTodoTitle: String = "",
    val editedTodo: TodoEdit? = null,
    val newList: NewList? = null,
    val renamedList: RenamedList? = null,
    val listPendingDeletion: TodoList? = null,
    val error: TodoListError? = null
)

/** The entry currently open in the edit dialog, together with the text typed so far. */
data class TodoEdit(
    val todoId: String,
    val title: String
)

/** The list being created, together with what has been typed and picked so far. */
data class NewList(
    val name: String = "",
    val shared: Boolean = false
)

/** The list being renamed. [listId] is kept so the write cannot drift to another list. */
data class RenamedList(
    val listId: String,
    val name: String
)
