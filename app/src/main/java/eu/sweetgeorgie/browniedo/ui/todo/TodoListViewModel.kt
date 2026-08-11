package eu.sweetgeorgie.browniedo.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.SelectedListRepository
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import eu.sweetgeorgie.browniedo.domain.user.PartnerRepository
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
    private val partnerRepository: PartnerRepository,
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
        observePartner()
    }

    private fun observePartner() {
        viewModelScope.launch {
            partnerRepository.partner.collect { partner ->
                mutableUiState.update { it.copy(partner = partner) }
            }
        }
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
                            mutableUiState.update {
                                it.copy(
                                    lists = lists,
                                    selectedList = selected,
                                    // Gibt es gar keine Liste mehr, ist ein hängendes LOAD_FAILED
                                    // eine veraltete Aussage: Wir wissen jetzt positiv, dass nichts
                                    // zu laden war. Sonst verdrängte der Fehler den Hinweis „Noch
                                    // keine Liste" — genau das passiert, wenn der Partner die
                                    // letzte Liste löscht und der Todo-Listener zuerst feuert.
                                    // Bei vorhandenen Listen bleibt der Fehler unangetastet: Er
                                    // gehört den Aufgaben, und ein Listen-Snapshot sagt darüber
                                    // nichts.
                                    error = if (lists.isEmpty()) null else it.error
                                )
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
                            // Offene Dialoge gehören der alten Liste. Blieben sie stehen, schriebe
                            // das Bestätigen gegen die neue — sie lesen die Liste erst dann.
                            it.copy(
                                todos = emptyList(),
                                isLoading = true,
                                error = null,
                                editedTodo = null,
                                newList = null,
                                renamedList = null,
                                listPendingDeletion = null
                            )
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

    // --- Liste anlegen ---

    fun onNewListClick() = mutableUiState.update { it.copy(newList = NewList(), error = null) }

    fun onNewListNameChange(name: String) = mutableUiState.update {
        it.copy(newList = it.newList?.copy(name = name))
    }

    /** Ohne hinterlegten Partner gibt es keine geteilte Liste — die UI bietet sie dann nicht an. */
    fun onNewListSharedChange(shared: Boolean) = mutableUiState.update {
        if (shared && it.partner == null) it else it.copy(newList = it.newList?.copy(shared = shared))
    }

    fun onNewListDismiss() = mutableUiState.update { it.copy(newList = null) }

    fun onNewListConfirm() {
        val newList = mutableUiState.value.newList ?: return
        val name = newList.name.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            listRepository.createList(name = name, shared = newList.shared).fold(
                // Die neue Liste wird nicht selbst ausgewählt: Sie taucht über den Listen-Snapshot
                // auf, und wer sie sofort öffnen will, tippt sie im Menü an.
                onSuccess = { mutableUiState.update { it.copy(newList = null, error = null) } },
                // Der Dialog bleibt offen, damit der eingetippte Name nicht verloren geht.
                onFailure = {
                    mutableUiState.update { it.copy(error = TodoListError.LIST_ADD_FAILED) }
                }
            )
        }
    }

    // --- Liste umbenennen ---

    fun onRenameListClick() = mutableUiState.update { state ->
        val selected = state.selectedList ?: return@update state
        state.copy(
            renamedList = RenamedList(listId = selected.id, name = selected.name),
            error = null
        )
    }

    fun onRenamedListNameChange(name: String) = mutableUiState.update {
        it.copy(renamedList = it.renamedList?.copy(name = name))
    }

    fun onRenameListDismiss() = mutableUiState.update { it.copy(renamedList = null) }

    fun onRenameListConfirm() {
        val renamedList = mutableUiState.value.renamedList ?: return
        val name = renamedList.name.trim()
        if (name.isEmpty()) return
        // Die Id kommt aus dem Dialog, nicht aus der aktuellen Auswahl: Sonst landete die Änderung
        // in der falschen Liste, wenn zwischenzeitlich gewechselt wurde.
        listRepository.renameList(renamedList.listId, name).fold(
            onSuccess = { mutableUiState.update { it.copy(renamedList = null, error = null) } },
            onFailure = {
                mutableUiState.update { it.copy(error = TodoListError.LIST_UPDATE_FAILED) }
            }
        )
    }

    // --- Liste löschen ---

    fun onDeleteListClick() = mutableUiState.update {
        it.copy(listPendingDeletion = it.selectedList, error = null)
    }

    fun onDeleteListDismiss() = mutableUiState.update { it.copy(listPendingDeletion = null) }

    fun onDeleteListConfirm() {
        val list = mutableUiState.value.listPendingDeletion ?: return
        viewModelScope.launch {
            listRepository.deleteList(list.id).fold(
                // Welche Liste danach offen ist, entscheidet der Rückfall in observeLists.
                onSuccess = {
                    mutableUiState.update { it.copy(listPendingDeletion = null, error = null) }
                },
                onFailure = {
                    mutableUiState.update { it.copy(error = TodoListError.LIST_DELETE_FAILED) }
                }
            )
        }
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
        it.copy(
            editedTodo = TodoEdit(
                todoId = todo.id,
                title = todo.title,
                priority = todo.priority
            ),
            error = null
        )
    }

    fun onEditedTitleChange(title: String) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(title = title))
    }

    fun onEditedPriorityChange(priority: TodoPriority) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(priority = priority))
    }

    fun onEditDismiss() = mutableUiState.update { it.copy(editedTodo = null) }

    fun onEditConfirm() {
        val listId = selectedListId.value ?: return
        val editedTodo = mutableUiState.value.editedTodo ?: return
        val title = editedTodo.title.trim()
        if (title.isEmpty()) return
        // Titel und Priorität gehen zusammen raus: ein Speichern ist ein Schreibvorgang, siehe
        // docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md.
        todoRepository.updateTodo(listId, editedTodo.todoId, title, editedTodo.priority).fold(
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
