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
import eu.sweetgeorgie.browniedo.domain.todo.TodoUpdate
import eu.sweetgeorgie.browniedo.domain.todo.formatQuantity
import eu.sweetgeorgie.browniedo.domain.todo.renumberedSortOrders
import eu.sweetgeorgie.browniedo.domain.todo.scaledBy
import eu.sweetgeorgie.browniedo.domain.todo.sortOrderBetween
import eu.sweetgeorgie.browniedo.domain.todo.toPositiveDecimalOrNull
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
     *
     * **Der Rückfall bevorzugt eine Arbeitsliste**, obwohl eine Vorlage genauso zu öffnen ist: Wer
     * gerade eine Liste verloren hat, will weiterarbeiten und nicht in einer Vorlage landen. Erst
     * wenn es keine Arbeitsliste gibt, greift die erste Vorlage — ein leerer Bildschirm neben einer
     * vorhandenen Vorlage wäre die schlechtere Antwort. Die *gemerkte* Liste gewinnt in jedem Fall,
     * die darf eine Vorlage sein (ADR 0018).
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
                            val (templates, workingLists) = lists.partition { it.isTemplate }
                            val selected = lists.firstOrNull { it.id == rememberedId }
                                ?: workingLists.firstOrNull()
                                ?: templates.firstOrNull()
                            selectedListId.value = selected?.id
                            mutableUiState.update {
                                it.copy(
                                    lists = workingLists,
                                    templates = templates,
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
                            //
                            // movedToListName gehört bewusst NICHT hierher: Die Bestätigung
                            // betrifft eine abgeschlossene Aktion und bleibt wahr. Sie hier zu
                            // löschen risse eine laufende Snackbar mittendrin ab.
                            //
                            // deletedTodo dagegen schon: Es ist kein Rückblick, sondern ein
                            // Angebot, und [onUndoDelete] schreibt in die *gewählte* Liste. Bliebe
                            // es stehen, legte ein Rückgängig nach dem Wechsel die Aufgabe in der
                            // falschen Liste an.
                            it.copy(
                                todos = emptyList(),
                                isLoading = true,
                                error = null,
                                editedTodo = null,
                                newList = null,
                                renamedList = null,
                                listPendingDeletion = null,
                                deletedTodo = null
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

    // --- Liste, Vorlage oder Liste aus einer Vorlage anlegen ---

    fun onNewListClick() = mutableUiState.update { it.copy(newList = NewList(), error = null) }

    fun onNewTemplateClick() = mutableUiState.update {
        it.copy(newList = NewList(kind = NewListKind.TEMPLATE), error = null)
    }

    /**
     * Öffnet denselben Dialog für den Weg von der Vorlage zur Arbeitsliste, vorbelegt aus der
     * Vorlage: ihr Name als Vorschlag, und geteilt bleibt geteilt — eine gemeinsame Packliste ergibt
     * eine gemeinsame Reise. Ohne hinterlegten Partner fällt das auf „nur für mich" zurück, sonst
     * scheiterte das Anlegen an einer Option, die die Oberfläche gar nicht anbietet.
     */
    fun onCreateListFromTemplateClick() = mutableUiState.update { state ->
        val template = state.selectedList?.takeIf { it.isTemplate } ?: return@update state
        state.copy(
            newList = NewList(
                name = template.name,
                shared = template.isShared && state.partner != null,
                kind = NewListKind.FROM_TEMPLATE
            ),
            error = null
        )
    }

    fun onNewListNameChange(name: String) = mutableUiState.update {
        it.copy(newList = it.newList?.copy(name = name))
    }

    /** Ohne hinterlegten Partner gibt es keine geteilte Liste — die UI bietet sie dann nicht an. */
    fun onNewListSharedChange(shared: Boolean) = mutableUiState.update {
        if (shared && it.partner == null) it else it.copy(newList = it.newList?.copy(shared = shared))
    }

    fun onNewListFactorChange(factor: String) = mutableUiState.update {
        it.copy(newList = it.newList?.copy(factor = factor))
    }

    fun onNewListDismiss() = mutableUiState.update { it.copy(newList = null) }

    /**
     * Alle drei Anlegewege enden gleich: Was angelegt wurde, wird auch geöffnet, siehe
     * docs/decisions/0036-neue-liste-wird-geoeffnet-und-listener-wiederholt.md. Geöffnet wird über
     * den gemerkten Stand, damit Auswahl und Rückfall denselben Weg nehmen wie sonst.
     */
    fun onNewListConfirm() {
        val newList = mutableUiState.value.newList ?: return
        val name = newList.name.trim()
        if (name.isEmpty()) return
        // Beim Instanziieren kommen die Einträge aus dem angezeigten Stand der Vorlage, nicht aus
        // einer eigenen Abfrage — dieselbe Quelle wie beim Verschieben, und die einzige, die auch
        // offline etwas liefert. Gelesen wird vor dem Start der Coroutine, damit der Stand der ist,
        // den der Nutzer beim Bestätigen vor sich hatte.
        val templateEntries = if (newList.kind == NewListKind.FROM_TEMPLATE) {
            mutableUiState.value.todos
        } else {
            emptyList()
        }
        val factor = if (newList.kind == NewListKind.FROM_TEMPLATE) {
            // Wie beim Mengenfeld: Die Oberfläche blendet „Anlegen" bei unlesbarem Faktor ab, das
            // hier ist die zweite Verteidigungslinie.
            newList.factor.toPositiveDecimalOrNull() ?: return
        } else {
            // Die anderen beiden Wege legen leer an, es gibt nichts zu skalieren.
            1.0
        }

        viewModelScope.launch {
            val created = when (newList.kind) {
                NewListKind.FROM_TEMPLATE -> listRepository.createListFromTemplate(
                    name = name,
                    shared = newList.shared,
                    // Eine frische Liste ist offen. Vorlagen kennen kein Abhaken, das ist also
                    // Vorsorge für einen Bestand, der von Hand entstanden sein kann — und es sagt,
                    // was eine Instanz erbt und was nicht.
                    //
                    // Skaliert wird hier und nicht im Repository: Es ist eine Regel darüber, was
                    // eine Instanz erbt, und sie gehört zu der aus ADR 0037 (ADR 0034 hält dasselbe
                    // schon für den Erledigt-Zustand fest).
                    todos = templateEntries.map {
                        it.copy(isDone = false, completedBy = null, completedAt = null)
                            .scaledBy(factor)
                    }
                )

                NewListKind.LIST, NewListKind.TEMPLATE -> listRepository.createList(
                    name = name,
                    shared = newList.shared,
                    isTemplate = newList.kind == NewListKind.TEMPLATE
                )
            }

            created.fold(
                onSuccess = { listId ->
                    mutableUiState.update { it.copy(newList = null, error = null) }
                    selectedListRepository.select(listId)
                },
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

    /**
     * Ordnet [todo] zwischen [above] und [below] ein — beides die Nachbarn **nach** dem Ablegen, aus
     * der Liste ohne die gezogene Aufgabe. Trägt das Ziehen und die zwei TalkBack-Aktionen
     * gleichermaßen, siehe docs/decisions/0039-manuelle-sortierung-ueber-createdat-als-anker.md.
     *
     * Die Regel steht hier ein **zweites** Mal, obwohl die Oberfläche einen unerlaubten Zug gar nicht
     * erst zulässt: dieselbe zweite Verteidigungslinie wie bei `isDone` und beim Verschieben — und
     * anders als die Geste ist sie ohne Gerät prüfbar. Erledigte Aufgaben sind nicht sortierbar
     * (ihr Block ist ein Protokoll), und ein Nachbar aus einer anderen Stufe hieße, die Priorität zu
     * übergehen.
     */
    fun onTodoReordered(todo: Todo, above: Todo?, below: Todo?) {
        val listId = selectedListId.value ?: return
        if (todo.isDone) return
        if (!above.fitsBeside(todo) || !below.fitsBeside(todo)) return

        val sortOrder = sortOrderBetween(above, below)
        val result = if (sortOrder != null) {
            todoRepository.setSortOrder(listId, todo.id, sortOrder)
        } else {
            // Der Platz lässt sich nicht mehr als Zahl ausdrücken — die ganze Gruppe bekommt frische
            // Werte. Passiert praktisch nie, aber ohne diesen Zweig bliebe der Zug wirkungslos, und
            // zwar dauerhaft und unerklärlich.
            todoRepository.renumberTodos(listId, renumberedSortOrders(reordered(todo, above)))
        }
        result.onFailure {
            mutableUiState.update { it.copy(error = TodoListError.UPDATE_FAILED) }
        }
    }

    /** Ein Nachbar passt, wenn es ihn nicht gibt oder er offen ist und dieselbe Stufe trägt. */
    private fun Todo?.fitsBeside(todo: Todo): Boolean =
        this == null || (!isDone && priority == todo.priority)

    /**
     * Die Prioritätsgruppe von [todo] in der Reihenfolge **nach** dem Zug — die Vorlage für das
     * Neunummerieren. [above] ist der Nachbar, hinter den die Aufgabe rückt; ohne ihn kommt sie an
     * den Anfang.
     */
    private fun reordered(todo: Todo, above: Todo?): List<Todo> {
        val group = mutableUiState.value.todos
            .filter { !it.isDone && it.priority == todo.priority && it.id != todo.id }
        val index = above?.let { group.indexOfFirst { entry -> entry.id == it.id } + 1 } ?: 0
        return group.toMutableList().apply { add(index, todo) }
    }

    fun onEditTodoClick(todo: Todo) {
        // Dieselbe Quelle, gegen die onEditConfirm später vergleicht — nicht uiState.selectedList,
        // sonst hingen Vorbelegung und Vergleich an zwei Werten.
        val listId = selectedListId.value ?: return
        mutableUiState.update {
            it.copy(
                editedTodo = TodoEdit(
                    todoId = todo.id,
                    title = todo.title,
                    priority = todo.priority,
                    targetListId = listId,
                    // Aus null wird der leere Puffer des Textfelds; beim Speichern wieder zurück.
                    notes = todo.notes.orEmpty(),
                    // Formatiert und nicht `toString()`: Im Feld soll „1,5" stehen und nicht „1.5" —
                    // und was der Nutzer dort sieht, ist auch das, was er wieder eintippen würde.
                    quantity = todo.quantity?.let(::formatQuantity).orEmpty()
                ),
                error = null
            )
        }
    }

    fun onEditedTitleChange(title: String) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(title = title))
    }

    fun onEditedPriorityChange(priority: TodoPriority) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(priority = priority))
    }

    fun onEditedTargetListChange(list: TodoList) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(targetListId = list.id))
    }

    fun onEditedNotesChange(notes: String) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(notes = notes))
    }

    fun onEditedQuantityChange(quantity: String) = mutableUiState.update {
        it.copy(editedTodo = it.editedTodo?.copy(quantity = quantity))
    }

    fun onEditDismiss() = mutableUiState.update { it.copy(editedTodo = null) }

    /**
     * Ein Speichern ist ein Schreibvorgang — entweder an Ort und Stelle oder in die neue Liste, nie
     * beides, siehe docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md. Titel und
     * Priorität reisen beim Verschieben mit, statt hinterher noch einmal geschrieben zu werden.
     */
    fun onEditConfirm() {
        val listId = selectedListId.value ?: return
        val editedTodo = mutableUiState.value.editedTodo ?: return
        val title = editedTodo.title.trim()
        if (title.isEmpty()) return
        // Vor der Verzweigung, damit beide Zweige denselben Wert schreiben. Der leere Puffer wird
        // hier wieder zu null — „keine Notiz" hat damit genau eine Form.
        val notes = editedTodo.notes.trim().takeIf { it.isNotBlank() }

        val quantityText = editedTodo.quantity.trim()
        val quantity = quantityText.toPositiveDecimalOrNull()
        // Leer heißt „keine Menge" und ist erlaubt; unlesbar heißt, dass wir nicht wissen, was
        // gemeint war — und dann wird nichts geschrieben, statt die Menge still zu löschen. Die
        // Oberfläche blendet „Speichern" dafür ab, das hier ist die zweite Verteidigungslinie.
        if (quantityText.isNotEmpty() && quantity == null) return

        if (editedTodo.targetListId == listId) {
            // Titel, Priorität, Notiz und Menge gehen zusammen raus (ADR 0025).
            todoRepository.updateTodo(
                listId = listId,
                todoId = editedTodo.todoId,
                update = TodoUpdate(
                    title = title,
                    priority = editedTodo.priority,
                    notes = notes,
                    quantity = quantity
                )
            ).fold(
                // Bei einem Fehlschlag bleibt der Dialog offen, damit der getippte Titel nicht
                // verloren geht.
                onSuccess = { mutableUiState.update { it.copy(editedTodo = null, error = null) } },
                onFailure = {
                    mutableUiState.update { it.copy(error = TodoListError.UPDATE_FAILED) }
                }
            )
            return
        }

        val state = mutableUiState.value
        // Die Aufgabe kommt aus dem angezeigten Stand, nicht aus dem Dialog: Der Dialog trägt nur
        // Titel, Priorität und Notiz, alle übrigen Felder wandern unverändert mit (ADR 0024). Damit
        // ist auch der Erledigt-Zustand so frisch wie der letzte Snapshot und nicht so alt wie der
        // Dialog.
        val todo = state.todos.firstOrNull { it.id == editedTodo.todoId }
        // Nur unter Gleichartigem gesucht — dieselbe Menge, die das Feld im Dialog anbietet.
        val target = state.targetLists.firstOrNull { it.id == editedTodo.targetListId }
        if (todo == null || target == null) {
            // Der Eintrag oder die Zielliste ist verschwunden, während der Dialog offen stand — der
            // Partner hat gelöscht. Anders als die übrigen Wächter hier wird das gemeldet statt
            // still übergangen: Es ist ein Zustand, den der Partner herstellen kann, und ein
            // Verschieben würde einen gelöschten Eintrag in der Zielliste wieder auferstehen lassen.
            mutableUiState.update { it.copy(error = TodoListError.MOVE_FAILED) }
            return
        }

        todoRepository.moveTodo(
            fromListId = listId,
            toListId = target.id,
            // Was der Dialog besitzt, überschreibt den Snapshot — sonst reiste beim gleichzeitigen
            // Verschieben und Ändern die *alte* Notiz mit und die getippte wäre verloren. Dasselbe
            // gilt seit Phase 14b für die Menge; kein Mapper-Test findet diesen Fall.
            todo = todo.copy(
                title = title,
                priority = editedTodo.priority,
                notes = notes,
                quantity = quantity
            )
        ).fold(
            onSuccess = {
                mutableUiState.update {
                    it.copy(editedTodo = null, error = null, movedToListName = target.name)
                }
            },
            // Wie beim Speichern: Der Dialog bleibt offen, der Eintrag steht noch in der alten
            // Liste.
            onFailure = { mutableUiState.update { it.copy(error = TodoListError.MOVE_FAILED) } }
        )
    }

    fun onMovedMessageShown() = mutableUiState.update { it.copy(movedToListName = null) }

    /**
     * Der einzige Fehler, der nicht aus einem Schreibvorgang kommt: Der Kalender-Intent hat keine
     * App gefunden. Er läuft trotzdem über [TodoListUiState.error] statt über einen dritten
     * Meldungskanal im Bildschirm, siehe
     * docs/decisions/0029-kalender-fehler-ueber-todolisterror.md.
     */
    fun onCalendarAppMissing() = mutableUiState.update {
        it.copy(error = TodoListError.CALENDAR_APP_MISSING)
    }

    // LOAD_FAILED bleibt bewusst stehen: Firestore baut den Snapshot-Listener nach einem Fehler
    // ab, die Liste aktualisiert sich also nicht mehr. Ein Hinweis, der nach ein paar Sekunden
    // verschwindet, würde den Nutzer vor einer still veralteten Liste sitzen lassen.
    fun onErrorShown() = mutableUiState.update {
        if (it.error == TodoListError.LOAD_FAILED) it else it.copy(error = null)
    }

    /**
     * Löschen bleibt ein Tipp ohne Rückfrage — das Netz spannt sich danach auf, siehe
     * docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md.
     *
     * Zurückgelegt wird der Stand aus dem Snapshot, **nicht** der des offenen Dialogs: Was dort
     * getippt und nicht gespeichert wurde, stand nie in Firestore, und ein Rückgängig soll den
     * Zustand von vor dem Löschen wiederherstellen, nicht einen, den es nie gab. Dieselbe
     * Überlegung wie beim Verschieben in [onEditConfirm], nur mit der umgekehrten Antwort — dort
     * *will* der Dialog gewinnen, weil sein Inhalt gerade gespeichert wird.
     *
     * Fehlt der Eintrag im Snapshot, wird trotzdem gelöscht und nur das Rückgängig entfällt: Der
     * Partner hat ihn dann bereits entfernt, und die Aufgabe ist ohnehin weg.
     */
    fun onDeleteTodoClick() {
        val listId = selectedListId.value ?: return
        val state = mutableUiState.value
        val editedTodo = state.editedTodo ?: return
        val snapshot = state.todos.firstOrNull { it.id == editedTodo.todoId }
        todoRepository.deleteTodo(listId, editedTodo.todoId).fold(
            onSuccess = {
                mutableUiState.update {
                    it.copy(editedTodo = null, error = null, deletedTodo = snapshot)
                }
            },
            onFailure = { mutableUiState.update { it.copy(error = TodoListError.DELETE_FAILED) } }
        )
    }

    /**
     * Wischen löscht ohne Dialog — es gibt also keinen zu schließen.
     *
     * Die Prüfung auf [Todo.isDone] ist die eigentliche Regel und steht bewusst hier statt nur in
     * der Oberfläche: So ist sie ohne Gerät prüfbar und übersteht eine Unachtsamkeit im Bildschirm,
     * siehe docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md.
     *
     * Das Rückgängig gibt es hier ebenso wie im Dialog. Die Wischstrecke von 85 % bleibt davon
     * unberührt, siehe ADR 0031.
     */
    fun onTodoSwipedAway(todo: Todo) {
        if (!todo.isDone) return
        val listId = selectedListId.value ?: return
        todoRepository.deleteTodo(listId, todo.id).fold(
            onSuccess = { mutableUiState.update { it.copy(deletedTodo = todo, error = null) } },
            onFailure = { mutableUiState.update { it.copy(error = TodoListError.DELETE_FAILED) } }
        )
    }

    /**
     * Legt die zuletzt gelöschte Aufgabe wieder an. Der Slot wird in beiden Fällen geleert: Nach
     * einem Fehlschlag ein zweites Mal anzubieten hieße, die Snackbar über sich selbst zu stapeln —
     * der Fehler sagt bereits, dass die Aufgabe weg bleibt.
     */
    fun onUndoDelete() {
        val listId = selectedListId.value ?: return
        val deletedTodo = mutableUiState.value.deletedTodo ?: return
        todoRepository.restoreTodo(listId, deletedTodo).fold(
            onSuccess = { mutableUiState.update { it.copy(deletedTodo = null, error = null) } },
            onFailure = {
                mutableUiState.update {
                    it.copy(deletedTodo = null, error = TodoListError.RESTORE_FAILED)
                }
            }
        )
    }

    fun onDeletedMessageShown() = mutableUiState.update { it.copy(deletedTodo = null) }

    // --- Erledigte auf einmal löschen (ADR 0040) ---

    fun onDeleteFinishedClick() =
        mutableUiState.update { it.copy(finishedTodosPendingDeletion = true, error = null) }

    fun onDeleteFinishedDismiss() =
        mutableUiState.update { it.copy(finishedTodosPendingDeletion = false) }

    /**
     * Räumt alle abgehakten Aufgaben der offenen Liste weg — mit Rückfrage statt Rückgängig, weil
     * hier viele auf einmal gehen und die Anzahl im Dialog die Folge greifbar macht (ADR 0040).
     *
     * Ein Fehlschlag lässt den Dialog stehen, so wie beim Löschen einer Liste: Die Fehler-Snackbar
     * legt sich davor, und wer will, versucht es noch einmal.
     */
    fun onDeleteFinishedConfirm() {
        val listId = selectedListId.value ?: return
        val finishedIds = mutableUiState.value.todos.filter(Todo::isDone).map(Todo::id)
        if (finishedIds.isEmpty()) return

        todoRepository.deleteTodos(listId, finishedIds).fold(
            onSuccess = {
                mutableUiState.update {
                    it.copy(
                        finishedTodosPendingDeletion = false,
                        error = null,
                        // Ein offenes „Rückgängig" aus einer Einzellöschung holte sonst eine
                        // Aufgabe zurück, die gerade mit weggeräumt wurde — das Angebot verfällt.
                        deletedTodo = null
                    )
                }
            },
            onFailure = {
                mutableUiState.update { it.copy(error = TodoListError.DELETE_FINISHED_FAILED) }
            }
        )
    }
}
