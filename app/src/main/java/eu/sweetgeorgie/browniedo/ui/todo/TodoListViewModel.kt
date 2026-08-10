package eu.sweetgeorgie.browniedo.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.SelectedListRepository
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TodoListViewModel(
    private val todoRepository: TodoRepository,
    private val listRepository: ListRepository,
    private val selectedListRepository: SelectedListRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(TodoListUiState())
    val uiState: StateFlow<TodoListUiState> = mutableUiState.asStateFlow()

    /**
     * The list the todo listener is attached to. Written only by [observeLists] so that the stored
     * choice and the resolved fallback take the same path — the UI never sets it directly.
     */
    private val selectedListId = MutableStateFlow<String?>(null)

    init {
        observeLists()
        observeTodos()
    }

    /**
     * Resolves which list is actually on screen out of the available lists and the one remembered
     * on this device.
     *
     * The fallback to the first list covers two cases with one rule: nothing was ever picked, and
     * the remembered list is gone. Lists arrive sorted by name, so "first" is deterministic.
     */
    private fun observeLists() {
        viewModelScope.launch {
            combine(
                listRepository.lists,
                selectedListRepository.selectedListId
            ) { listsResult, rememberedId -> listsResult to rememberedId }
                .collect { (listsResult, rememberedId) ->
                    listsResult.fold(
                        onSuccess = { lists ->
                            val selected = lists.firstOrNull { it.id == rememberedId }
                                ?: lists.firstOrNull()
                            selectedListId.value = selected?.id
                            // Der Fehlerzustand wird hier bewusst nicht angefasst: Er gehört den
                            // Aufgaben, und ein erfolgreicher Listen-Snapshot sagt darüber nichts.
                            mutableUiState.update {
                                it.copy(lists = lists, selectedList = selected)
                            }
                        },
                        onFailure = {
                            // Ohne Listen gibt es auch keine Aufgaben. Derselbe Fehler wie beim
                            // Laden der Aufgaben — für den Nutzer ist es dieselbe Aussage.
                            mutableUiState.update {
                                it.copy(isLoading = false, error = TodoListError.LOAD_FAILED)
                            }
                        }
                    )
                }
        }
    }

    /**
     * Follows [selectedListId]: `flatMapLatest` unsubscribes the previous list's snapshot listener
     * and attaches one to the new list.
     *
     * Das Zurücksetzen vor dem Wechsel ist nicht kosmetisch. `LOAD_FAILED` bleibt sonst kleben
     * (siehe [onErrorShown]) und die neue Liste zeigte den Fehler der alten; und ohne `isLoading`
     * blitzen kurz die Aufgaben der vorigen Liste unter dem neuen Namen auf.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTodos() {
        viewModelScope.launch {
            // Kein distinctUntilChanged nötig: StateFlow entdoppelt gleiche Werte bereits selbst.
            selectedListId
                .onEach { listId ->
                    // Nur zurücksetzen, wenn es wirklich eine neue Liste zu laden gibt. Ohne diese
                    // Bedingung würde der Wechsel auf „gar keine Liste" den Ladefehler löschen, den
                    // observeLists gerade gesetzt hat.
                    if (listId != null) {
                        mutableUiState.update {
                            it.copy(todos = emptyList(), isLoading = true, error = null)
                        }
                    }
                }
                .flatMapLatest { listId ->
                    listId?.let(todoRepository::todos) ?: flowOf(null)
                }
                .collect { result ->
                    // null heißt: keine Liste gewählt. Der Fehlerzustand gehört dann den Listen und
                    // wird hier bewusst nicht angefasst.
                    if (result == null) {
                        mutableUiState.update { it.copy(todos = emptyList(), isLoading = false) }
                        return@collect
                    }
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

    fun onListSelected(list: TodoList) {
        viewModelScope.launch { selectedListRepository.select(list.id) }
    }

    fun onNewTodoTitleChange(title: String) =
        mutableUiState.update { it.copy(newTodoTitle = title) }

    fun addTodo() {
        val listId = selectedListId.value ?: return
        val title = mutableUiState.value.newTodoTitle.trim()
        if (title.isEmpty()) return
        todoRepository.addTodo(listId, title).fold(
            // The input is only cleared once the entry is queued, so a rejected write does not
            // lose what the user typed.
            onSuccess = { mutableUiState.update { it.copy(newTodoTitle = "", error = null) } },
            onFailure = { mutableUiState.update { it.copy(error = TodoListError.ADD_FAILED) } }
        )
    }

    fun onTodoDoneChange(todo: Todo, isDone: Boolean) {
        val listId = selectedListId.value ?: return
        val completedBy = authRepository.currentUser?.uid.takeIf { isDone }
        todoRepository.setDone(listId, todo.id, isDone, completedBy).onFailure {
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
        val listId = selectedListId.value ?: return
        val editedTodo = mutableUiState.value.editedTodo ?: return
        val title = editedTodo.title.trim()
        if (title.isEmpty()) return
        todoRepository.setTitle(listId, editedTodo.todoId, title).fold(
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
        val listId = selectedListId.value ?: return
        val editedTodo = mutableUiState.value.editedTodo ?: return
        todoRepository.deleteTodo(listId, editedTodo.todoId).fold(
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
        val listId = selectedListId.value ?: return
        todoRepository.deleteTodo(listId, todo.id).onFailure {
            mutableUiState.update { it.copy(error = TodoListError.DELETE_FAILED) }
        }
    }
}
