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

    fun onEditTodoClick(todo: Todo) = mutableUiState.update {
        it.copy(editedTodo = TodoEdit(todoId = todo.id, title = todo.title), error = null)
    }

    fun onEditedTitleChange(title: String) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(title = title))
    }

    fun onEditDismiss() = mutableUiState.update { it.copy(editedTodo = null) }

    fun onEditConfirm() {
        val editedTodo = mutableUiState.value.editedTodo ?: return
        val title = editedTodo.title.trim()
        if (title.isEmpty()) return
        todoRepository.setTitle(editedTodo.todoId, title).fold(
            // The dialog stays open on failure so the typed title is not lost.
            onSuccess = { mutableUiState.update { it.copy(editedTodo = null, error = null) } },
            onFailure = { mutableUiState.update { it.copy(error = TodoListError.UPDATE_FAILED) } }
        )
    }

    // LOAD_FAILED bleibt bewusst stehen: Firestore baut den Snapshot-Listener nach einem Fehler
    // ab, die Liste aktualisiert sich also nicht mehr. Ein Hinweis, der nach ein paar Sekunden
    // verschwindet, würde den Nutzer vor einer still veralteten Liste sitzen lassen.
    fun onErrorShown() = mutableUiState.update {
        if (it.error == TodoListError.LOAD_FAILED) it else it.copy(error = null)
    }

    fun onDeleteTodoClick() {
        val editedTodo = mutableUiState.value.editedTodo ?: return
        todoRepository.deleteTodo(editedTodo.todoId).fold(
            onSuccess = { mutableUiState.update { it.copy(editedTodo = null, error = null) } },
            onFailure = { mutableUiState.update { it.copy(error = TodoListError.DELETE_FAILED) } }
        )
    }

    /**
     * Wischen löscht ohne Dialog — es gibt also keinen zu schließen.
     *
     * Die Prüfung auf [Todo.isDone] ist die eigentliche Regel und steht bewusst hier statt nur in
     * der Oberfläche: So ist sie ohne Gerät prüfbar und übersteht eine Unachtsamkeit im Bildschirm,
     * siehe docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md.
     */
    fun onTodoSwipedAway(todo: Todo) {
        if (!todo.isDone) return
        todoRepository.deleteTodo(todo.id).onFailure {
            mutableUiState.update { it.copy(error = TodoListError.DELETE_FAILED) }
        }
    }
}
