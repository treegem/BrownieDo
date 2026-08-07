package eu.sweetgeorgie.browniedo.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TodoListViewModel(
    private val todoRepository: TodoRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(TodoListUiState())
    val uiState: StateFlow<TodoListUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            todoRepository.todos.collect { result ->
                result.fold(
                    onSuccess = { todos ->
                        mutableUiState.update {
                            it.copy(todos = todos, isLoading = false, error = null)
                        }
                    },
                    onFailure = {
                        mutableUiState.update {
                            it.copy(isLoading = false, error = TodoListError.LOAD_FAILED)
                        }
                    }
                )
            }
        }
    }

    fun onNewTodoTitleChange(title: String) =
        mutableUiState.update { it.copy(newTodoTitle = title) }

    fun addTodo() {
        val title = mutableUiState.value.newTodoTitle.trim()
        if (title.isEmpty()) return
        todoRepository.addTodo(title).fold(
            // The input is only cleared once the entry is queued, so a rejected write does not
            // lose what the user typed.
            onSuccess = { mutableUiState.update { it.copy(newTodoTitle = "", error = null) } },
            onFailure = { mutableUiState.update { it.copy(error = TodoListError.ADD_FAILED) } }
        )
    }

    fun onTodoDoneChange(todo: Todo, isDone: Boolean) {
        val completedBy = authRepository.currentUser?.uid.takeIf { isDone }
        todoRepository.setDone(todo.id, isDone, completedBy).onFailure {
            mutableUiState.update { it.copy(error = TodoListError.UPDATE_FAILED) }
        }
    }
}
