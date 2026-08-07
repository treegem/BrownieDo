package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo

enum class TodoListError {
    LOAD_FAILED,
    ADD_FAILED
}

data class TodoListUiState(
    val todos: List<Todo> = emptyList(),
    val isLoading: Boolean = true,
    val newTodoTitle: String = "",
    val error: TodoListError? = null
)
