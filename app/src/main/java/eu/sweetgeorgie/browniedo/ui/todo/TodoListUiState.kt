package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo

enum class TodoListError {
    LOAD_FAILED,
    ADD_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED
}

data class TodoListUiState(
    val lists: List<TodoList> = emptyList(),
    /** Null while the lists are still loading, or when the user belongs to none at all. */
    val selectedList: TodoList? = null,
    val todos: List<Todo> = emptyList(),
    val isLoading: Boolean = true,
    val newTodoTitle: String = "",
    val editedTodo: TodoEdit? = null,
    val error: TodoListError? = null
)

/** The entry currently open in the edit dialog, together with the text typed so far. */
data class TodoEdit(
    val todoId: String,
    val title: String
)
