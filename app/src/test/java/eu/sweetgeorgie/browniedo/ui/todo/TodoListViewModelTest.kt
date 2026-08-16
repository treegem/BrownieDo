package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.auth.SignedInUser
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.SelectedListRepository
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import eu.sweetgeorgie.browniedo.domain.todo.TodoUpdate
import eu.sweetgeorgie.browniedo.domain.todo.effectiveOrder
import eu.sweetgeorgie.browniedo.domain.user.Partner
import eu.sweetgeorgie.browniedo.domain.user.PartnerRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val todoRepository = FakeTodoRepository()
    private val listRepository = FakeListRepository()
    private val selectedListRepository = FakeSelectedListRepository()
    private val partnerRepository = FakePartnerRepository()
    private val authRepository = FakeAuthRepository(SIGNED_IN_USER)
    private lateinit var viewModel: TodoListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        createViewModel()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() {
        viewModel = TodoListViewModel(
            todoRepository = todoRepository,
            listRepository = listRepository,
            selectedListRepository = selectedListRepository,
            partnerRepository = partnerRepository,
            authRepository = authRepository
        )
    }

    // --- Listen-Auswahl ---

    @Test
    fun `the first list is selected when nothing was remembered`() = runTest(testDispatcher) {
        advanceUntilIdle()

        assertEquals(LIST_A, viewModel.uiState.value.selectedList)
        assertEquals(listOf(LIST_A, LIST_B), viewModel.uiState.value.lists)
    }

    @Test
    fun `the remembered list is selected`() = runTest(testDispatcher) {
        selectedListRepository.setRemembered(LIST_B.id)
        createViewModel()
        advanceUntilIdle()

        assertEquals(LIST_B, viewModel.uiState.value.selectedList)
    }

    @Test
    fun `a remembered list that no longer exists falls back to the first`() =
        runTest(testDispatcher) {
            selectedListRepository.setRemembered("list-deleted-by-the-partner")
            createViewModel()
            advanceUntilIdle()

            assertEquals(LIST_A, viewModel.uiState.value.selectedList)
        }

    @Test
    fun `switching lists observes the new list`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onListSelected(LIST_B)
        advanceUntilIdle()

        assertEquals(LIST_B, viewModel.uiState.value.selectedList)
        assertEquals(listOf(LIST_A.id, LIST_B.id), todoRepository.observedListIds)
    }

    @Test
    fun `switching lists clears the sticky load error of the previous one`() =
        runTest(testDispatcher) {
            todoRepository.emit(LIST_A.id, Result.failure(IllegalStateException("no permission")))
            advanceUntilIdle()
            assertEquals(TodoListError.LOAD_FAILED, viewModel.uiState.value.error)

            viewModel.onListSelected(LIST_B)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
            assertEquals(emptyList<Todo>(), viewModel.uiState.value.todos)
        }

    @Test
    fun `belonging to no list at all leaves the selection empty`() = runTest(testDispatcher) {
        listRepository.emit(Result.success(emptyList()))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedList)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `nothing is written while no list is selected`() = runTest(testDispatcher) {
        listRepository.emit(Result.success(emptyList()))
        advanceUntilIdle()

        viewModel.onNewTodoTitleChange("Milch kaufen")
        viewModel.addTodo()

        assertEquals(0, todoRepository.addCallCount)
    }

    @Test
    fun `a failing list load reports a load error`() = runTest(testDispatcher) {
        listRepository.emit(Result.failure(IllegalStateException("no permission")))
        advanceUntilIdle()

        assertEquals(TodoListError.LOAD_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // --- Listen anlegen, umbenennen, löschen ---

    @Test
    fun `a private list is created`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewListClick()
        viewModel.onNewListNameChange("  Garten  ")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals(
            CreateListCall("Garten", shared = false, isTemplate = false),
            listRepository.lastCreateCall
        )
        assertNull(viewModel.uiState.value.newList)
    }

    @Test
    fun `a shared list is created`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewListClick()
        viewModel.onNewListNameChange("Garten")
        viewModel.onNewListSharedChange(true)
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals(
            CreateListCall("Garten", shared = true, isTemplate = false),
            listRepository.lastCreateCall
        )
    }

    /** Wer eine Liste anlegt, will sie füllen — und landet deshalb direkt darin (ADR 0036). */
    @Test
    fun `a new list is opened right away`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewListClick()
        viewModel.onNewListNameChange("Garten")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals(CREATED_LIST_ID, selectedListRepository.lastSelectedId)
    }

    @Test
    fun `a new template is opened right away`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewTemplateClick()
        viewModel.onNewListNameChange("Urlaub packen")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals(CREATED_LIST_ID, selectedListRepository.lastSelectedId)
    }

    @Test
    fun `a failing create opens nothing`() = runTest(testDispatcher) {
        advanceUntilIdle()
        listRepository.createResult = Result.failure(IllegalStateException("no permission"))

        viewModel.onNewListClick()
        viewModel.onNewListNameChange("Garten")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertNull(selectedListRepository.lastSelectedId)
    }

    @Test
    fun `sharing stays off while no partner is on file`() = runTest(testDispatcher) {
        partnerRepository.emit(null)
        advanceUntilIdle()

        viewModel.onNewListClick()
        viewModel.onNewListSharedChange(true)

        assertFalse(viewModel.uiState.value.newList!!.shared)
    }

    @Test
    fun `a blank list name is not written`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewListClick()
        viewModel.onNewListNameChange("   ")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertNull(listRepository.lastCreateCall)
    }

    @Test
    fun `a failing create keeps the dialog open and reports its own error`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            listRepository.createResult = Result.failure(IllegalStateException("no permission"))

            viewModel.onNewListClick()
            viewModel.onNewListNameChange("Garten")
            viewModel.onNewListConfirm()
            advanceUntilIdle()

            assertEquals(TodoListError.LIST_ADD_FAILED, viewModel.uiState.value.error)
            assertEquals("Garten", viewModel.uiState.value.newList?.name)
        }

    @Test
    fun `renaming writes against the list the dialog was opened for`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onRenameListClick()
        viewModel.onRenamedListNameChange("  Wocheneinkauf  ")
        viewModel.onRenameListConfirm()

        assertEquals(RenameListCall(LIST_A.id, "Wocheneinkauf"), listRepository.lastRenameCall)
        assertNull(viewModel.uiState.value.renamedList)
    }

    @Test
    fun `a failing rename reports its own error`() = runTest(testDispatcher) {
        advanceUntilIdle()
        listRepository.renameResult = Result.failure(IllegalStateException("no permission"))

        viewModel.onRenameListClick()
        viewModel.onRenamedListNameChange("Wocheneinkauf")
        viewModel.onRenameListConfirm()

        assertEquals(TodoListError.LIST_UPDATE_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `deleting removes the selected list`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onDeleteListClick()
        assertEquals(LIST_A, viewModel.uiState.value.listPendingDeletion)

        viewModel.onDeleteListConfirm()
        advanceUntilIdle()

        assertEquals(LIST_A.id, listRepository.deletedListId)
        assertNull(viewModel.uiState.value.listPendingDeletion)
    }

    @Test
    fun `a failing delete reports its own error`() = runTest(testDispatcher) {
        advanceUntilIdle()
        listRepository.deleteResult = Result.failure(IllegalStateException("no permission"))

        viewModel.onDeleteListClick()
        viewModel.onDeleteListConfirm()
        advanceUntilIdle()

        assertEquals(TodoListError.LIST_DELETE_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `switching lists closes every open dialog`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onRenameListClick()

        viewModel.onListSelected(LIST_B)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editedTodo)
        assertNull(viewModel.uiState.value.renamedList)
        assertNull(viewModel.uiState.value.listPendingDeletion)
    }

    @Test
    fun `losing the last list clears a sticky load error`() = runTest(testDispatcher) {
        todoRepository.emit(LIST_A.id, Result.failure(IllegalStateException("no permission")))
        advanceUntilIdle()
        assertEquals(TodoListError.LOAD_FAILED, viewModel.uiState.value.error)

        // Der Partner löscht die letzte Liste: Ohne das Aufräumen verdrängte der Fehler den
        // Hinweis „Noch keine Liste".
        listRepository.emit(Result.success(emptyList()))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.selectedList)
    }

    // --- Aufgaben ---

    @Test
    fun `entries from the repository end up in the ui state`() = runTest(testDispatcher) {
        todoRepository.emit(LIST_A.id, Result.success(listOf(TODO_ENTRY)))
        advanceUntilIdle()

        assertEquals(listOf(TODO_ENTRY), viewModel.uiState.value.todos)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failing repository flow reports a load error`() = runTest(testDispatcher) {
        todoRepository.emit(LIST_A.id, Result.failure(IllegalStateException("no permission")))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(TodoListError.LOAD_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `adding an entry trims the title and clears the input`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewTodoTitleChange("  Milch kaufen  ")
        viewModel.addTodo()
        advanceUntilIdle()

        assertEquals(AddCall(LIST_A.id, "Milch kaufen"), todoRepository.lastAddCall)
        assertEquals("", viewModel.uiState.value.newTodoTitle)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `an entry is added to the selected list`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onListSelected(LIST_B)
        advanceUntilIdle()

        viewModel.onNewTodoTitleChange("Milch kaufen")
        viewModel.addTodo()

        assertEquals(AddCall(LIST_B.id, "Milch kaufen"), todoRepository.lastAddCall)
    }

    @Test
    fun `a blank title is not written`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewTodoTitleChange("   ")
        viewModel.addTodo()

        assertEquals(0, todoRepository.addCallCount)
    }

    @Test
    fun `a failing write reports an error and keeps the input`() = runTest(testDispatcher) {
        advanceUntilIdle()
        todoRepository.addResult = Result.failure(IllegalStateException("offline"))

        viewModel.onNewTodoTitleChange("Milch kaufen")
        viewModel.addTodo()

        assertEquals(TodoListError.ADD_FAILED, viewModel.uiState.value.error)
        assertEquals("Milch kaufen", viewModel.uiState.value.newTodoTitle)
    }

    @Test
    fun `a successful load clears a previous error`() = runTest(testDispatcher) {
        todoRepository.emit(LIST_A.id, Result.failure(IllegalStateException("no permission")))
        advanceUntilIdle()

        todoRepository.emit(LIST_A.id, Result.success(listOf(TODO_ENTRY)))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `ticking an entry off records it as done by the signed in user`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onTodoDoneChange(TODO_ENTRY, isDone = true)

            assertEquals(
                SetDoneCall(LIST_A.id, TODO_ENTRY.id, isDone = true, completedBy = SIGNED_IN_USER.uid),
                todoRepository.lastSetDoneCall
            )
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `reopening an entry clears who completed it`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onTodoDoneChange(TODO_ENTRY.copy(isDone = true), isDone = false)

        assertEquals(
            SetDoneCall(LIST_A.id, TODO_ENTRY.id, isDone = false, completedBy = null),
            todoRepository.lastSetDoneCall
        )
    }

    @Test
    fun `a failing update reports an update error`() = runTest(testDispatcher) {
        advanceUntilIdle()
        todoRepository.setDoneResult = Result.failure(IllegalStateException("no permission"))

        viewModel.onTodoDoneChange(TODO_ENTRY, isDone = true)

        assertEquals(TodoListError.UPDATE_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `opening the edit dialog shows the current title, priority, list and notes`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            val urgentEntry =
                TODO_ENTRY.copy(priority = TodoPriority.HIGH, notes = "Die haltbare")

            viewModel.onEditTodoClick(urgentEntry)

            assertEquals(
                TodoEdit(
                    todoId = urgentEntry.id,
                    title = urgentEntry.title,
                    priority = TodoPriority.HIGH,
                    // Die Zielliste startet auf der Liste, in der die Aufgabe steht: „Speichern"
                    // schreibt dann an Ort und Stelle.
                    targetListId = LIST_A.id,
                    notes = "Die haltbare",
                    quantity = ""
                ),
                viewModel.uiState.value.editedTodo
            )
        }

    @Test
    fun `an entry without notes opens with an empty notes field`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)

        // Aus null wird der leere Puffer — ein Textfeld kann kein null anzeigen.
        assertEquals("", viewModel.uiState.value.editedTodo?.notes)
    }

    @Test
    fun `saving an edit writes the trimmed title and closes the dialog`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTitleChange("  Brot kaufen  ")
            viewModel.onEditConfirm()

            assertEquals(
                UpdateTodoCall(
                    LIST_A.id,
                    TODO_ENTRY.id,
                    TodoUpdate(
                        title = "Brot kaufen",
                        priority = TodoPriority.MEDIUM,
                        notes = null,
                        quantity = null
                    )
                ),
                todoRepository.lastUpdateTodoCall
            )
            assertNull(viewModel.uiState.value.editedTodo)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `saving an edit writes the picked priority together with the title`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedPriorityChange(TodoPriority.HIGH)
            viewModel.onEditConfirm()

            assertEquals(
                UpdateTodoCall(
                    LIST_A.id,
                    TODO_ENTRY.id,
                    TodoUpdate(
                        title = TODO_ENTRY.title,
                        priority = TodoPriority.HIGH,
                        notes = null,
                        quantity = null
                    )
                ),
                todoRepository.lastUpdateTodoCall
            )
        }

    @Test
    fun `picking a priority writes nothing before the edit is saved`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedPriorityChange(TodoPriority.LOW)

        assertNull(todoRepository.lastUpdateTodoCall)
        assertEquals(TodoPriority.LOW, viewModel.uiState.value.editedTodo?.priority)
    }

    @Test
    fun `saving an edit writes the trimmed notes`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedNotesChange("  Die haltbare, nicht die frische  ")
        viewModel.onEditConfirm()

        assertEquals(
            "Die haltbare, nicht die frische",
            todoRepository.lastUpdateTodoCall?.update?.notes
        )
    }

    @Test
    fun `emptying the notes writes null instead of a blank string`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY.copy(notes = "Die haltbare"))
        viewModel.onEditedNotesChange("   ")
        viewModel.onEditConfirm()

        // „Keine Notiz" hat genau eine Form, und die ist null.
        assertNull(todoRepository.lastUpdateTodoCall?.update?.notes)
    }

    @Test
    fun `typing notes writes nothing before the edit is saved`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedNotesChange("Die haltbare")

        assertNull(todoRepository.lastUpdateTodoCall)
        assertEquals("Die haltbare", viewModel.uiState.value.editedTodo?.notes)
    }

    // --- Menge am Eintrag (ADR 0037) ---

    @Test
    fun `opening the edit dialog shows the current quantity`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY.copy(quantity = 1.5))

        // Formatiert, nicht `toString()`: Im Feld steht „1,5" und nicht „1.5".
        assertEquals("1,5", viewModel.uiState.value.editedTodo?.quantity)
    }

    @Test
    fun `an entry without a quantity opens with an empty quantity field`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onEditTodoClick(TODO_ENTRY)

            assertEquals("", viewModel.uiState.value.editedTodo?.quantity)
        }

    @Test
    fun `saving an edit writes the typed quantity`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedQuantityChange("2,5")
        viewModel.onEditConfirm()

        assertEquals(2.5, todoRepository.lastUpdateTodoCall?.update?.quantity!!, 0.0)
    }

    @Test
    fun `emptying the quantity writes null instead of zero`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY.copy(quantity = 2.0))
        viewModel.onEditedQuantityChange("  ")
        viewModel.onEditConfirm()

        // Leer heißt „skaliert nicht" — und das hat genau eine Form.
        assertNull(todoRepository.lastUpdateTodoCall?.update?.quantity)
    }

    /**
     * Die zweite Verteidigungslinie hinter dem abgeblendeten Knopf: Was nicht als Zahl lesbar ist,
     * wird gar nicht geschrieben — sonst ginge stillschweigend „keine Menge" raus.
     */
    @Test
    fun `an unreadable quantity is not written`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedQuantityChange("zwei")
        viewModel.onEditConfirm()

        assertNull(todoRepository.lastUpdateTodoCall)
        assertNotNull(viewModel.uiState.value.editedTodo)
    }

    @Test
    fun `an edit with a blank title is not written`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTitleChange("   ")
        viewModel.onEditedPriorityChange(TodoPriority.HIGH)
        viewModel.onEditConfirm()

        // Der leere Titel hält auch die Priorität zurück — beide gehen zusammen raus.
        assertNull(todoRepository.lastUpdateTodoCall)
    }

    @Test
    fun `a failing edit keeps the dialog open and reports an update error`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            todoRepository.updateTodoResult =
                Result.failure(IllegalStateException("no permission"))

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTitleChange("Brot kaufen")
            viewModel.onEditConfirm()

            assertEquals(TodoListError.UPDATE_FAILED, viewModel.uiState.value.error)
            assertEquals("Brot kaufen", viewModel.uiState.value.editedTodo?.title)
        }

    @Test
    fun `cancelling an edit writes nothing`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTitleChange("Brot kaufen")
        viewModel.onEditDismiss()

        assertNull(todoRepository.lastUpdateTodoCall)
        assertNull(viewModel.uiState.value.editedTodo)
    }

    // --- Verschieben ---

    @Test
    fun `saving without changing the list writes in place`() = runTest(testDispatcher) {
        seedEntryInFirstList()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditConfirm()

        assertNotNull(todoRepository.lastUpdateTodoCall)
        assertNull(todoRepository.lastMoveTodoCall)
    }

    @Test
    fun `saving after picking another list moves the entry`() = runTest(testDispatcher) {
        seedEntryInFirstList()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertEquals(
            MoveTodoCall(LIST_A.id, LIST_B.id, TODO_ENTRY),
            todoRepository.lastMoveTodoCall
        )
        // Ein Speichern ist ein Schreibvorgang — verschoben *oder* an Ort und Stelle geschrieben.
        assertNull(todoRepository.lastUpdateTodoCall)
        assertNull(viewModel.uiState.value.editedTodo)
    }

    @Test
    fun `a move carries the edited title, priority and notes`() = runTest(testDispatcher) {
        seedEntryInFirstList()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTitleChange("  Brot kaufen  ")
        viewModel.onEditedPriorityChange(TodoPriority.HIGH)
        viewModel.onEditedNotesChange("  Vom Bäcker, nicht vom Supermarkt  ")
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        val moved = todoRepository.lastMoveTodoCall?.todo
        assertEquals("Brot kaufen", moved?.title)
        assertEquals(TodoPriority.HIGH, moved?.priority)
        // Der Fall, der durch keinen Mapper-Test auffällt: Die verschobene Aufgabe kommt aus dem
        // Snapshot, und was der Dialog besitzt, muss darauf überschrieben werden — sonst reiste
        // hier die *alte* Notiz mit und die getippte wäre verloren.
        assertEquals("Vom Bäcker, nicht vom Supermarkt", moved?.notes)
    }

    @Test
    fun `a move carries the stored notes when they were not edited`() = runTest(testDispatcher) {
        val entryWithNotes = TODO_ENTRY.copy(notes = "Die haltbare")
        seedEntryInFirstList(entryWithNotes)

        viewModel.onEditTodoClick(entryWithNotes)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertEquals("Die haltbare", todoRepository.lastMoveTodoCall?.todo?.notes)
    }

    @Test
    fun `a move can clear the notes`() = runTest(testDispatcher) {
        val entryWithNotes = TODO_ENTRY.copy(notes = "Die haltbare")
        seedEntryInFirstList(entryWithNotes)

        viewModel.onEditTodoClick(entryWithNotes)
        viewModel.onEditedNotesChange("")
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertNull(todoRepository.lastMoveTodoCall?.todo?.notes)
    }

    /**
     * Derselbe Fallstrick wie bei der Notiz in Phase 12, jetzt für die Menge: Die verschobene Aufgabe
     * kommt aus dem Snapshot, und was der Dialog besitzt, muss darauf überschrieben werden — sonst
     * reiste die *alte* Menge mit. Kein Mapper-Test findet das.
     */
    @Test
    fun `a move carries the edited quantity`() = runTest(testDispatcher) {
        val entryWithQuantity = TODO_ENTRY.copy(quantity = 1.0)
        seedEntryInFirstList(entryWithQuantity)

        viewModel.onEditTodoClick(entryWithQuantity)
        viewModel.onEditedQuantityChange("4")
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertEquals(4.0, todoRepository.lastMoveTodoCall?.todo?.quantity!!, 0.0)
    }

    @Test
    fun `a move carries the stored quantity when it was not edited`() = runTest(testDispatcher) {
        val entryWithQuantity = TODO_ENTRY.copy(quantity = 1.5)
        seedEntryInFirstList(entryWithQuantity)

        viewModel.onEditTodoClick(entryWithQuantity)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertEquals(1.5, todoRepository.lastMoveTodoCall?.todo?.quantity!!, 0.0)
    }

    @Test
    fun `a move leaves every other field untouched`() = runTest(testDispatcher) {
        seedEntryInFirstList(FINISHED_TODO_ENTRY)

        viewModel.onEditTodoClick(FINISHED_TODO_ENTRY)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        // Verschieben ist reine Organisation, siehe
        // docs/decisions/0024-verschieben-behaelt-zustand.md.
        val moved = todoRepository.lastMoveTodoCall?.todo
        assertEquals(FINISHED_TODO_ENTRY.createdAt, moved?.createdAt)
        assertEquals(FINISHED_TODO_ENTRY.isDone, moved?.isDone)
        assertEquals(FINISHED_TODO_ENTRY.completedBy, moved?.completedBy)
        assertEquals(FINISHED_TODO_ENTRY.completedAt, moved?.completedAt)
    }

    @Test
    fun `a move reports the target list for the confirmation`() = runTest(testDispatcher) {
        seedEntryInFirstList()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertEquals(LIST_B.name, viewModel.uiState.value.movedToListName)
    }

    @Test
    fun `a shown move confirmation is cleared`() = runTest(testDispatcher) {
        seedEntryInFirstList()
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        viewModel.onMovedMessageShown()

        assertNull(viewModel.uiState.value.movedToListName)
    }

    @Test
    fun `a failing move keeps the dialog open and reports a move error`() =
        runTest(testDispatcher) {
            seedEntryInFirstList()
            todoRepository.moveTodoResult = Result.failure(IllegalStateException("no permission"))

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTargetListChange(LIST_B)
            viewModel.onEditConfirm()

            assertEquals(TodoListError.MOVE_FAILED, viewModel.uiState.value.error)
            assertNotNull(viewModel.uiState.value.editedTodo)
            assertNull(viewModel.uiState.value.movedToListName)
        }

    @Test
    fun `an entry that vanished while the dialog was open is not moved`() =
        runTest(testDispatcher) {
            seedEntryInFirstList()
            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTargetListChange(LIST_B)

            // Der Partner hat den Eintrag gelöscht, während der Dialog offen stand.
            todoRepository.emit(LIST_A.id, Result.success(emptyList()))
            advanceUntilIdle()
            viewModel.onEditConfirm()

            // Verschieben würde ihn in der Zielliste wieder auferstehen lassen.
            assertNull(todoRepository.lastMoveTodoCall)
            assertEquals(TodoListError.MOVE_FAILED, viewModel.uiState.value.error)
        }

    @Test
    fun `a move with a blank title is not written`() = runTest(testDispatcher) {
        seedEntryInFirstList()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTitleChange("   ")
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertNull(todoRepository.lastMoveTodoCall)
    }

    /**
     * Die Verschiebe-Tests brauchen den Eintrag wirklich im angezeigten Stand: Das ViewModel holt
     * die Aufgabe von dort, nicht aus dem Dialog.
     */
    private fun TestScope.seedEntryInFirstList(entry: Todo = TODO_ENTRY) {
        advanceUntilIdle()
        todoRepository.emit(LIST_A.id, Result.success(listOf(entry)))
        advanceUntilIdle()
    }

    // --- Von Hand sortieren (ADR 0039) ---

    @Test
    fun `dropping an entry between two neighbours writes the computed place`() =
        runTest(testDispatcher) {
            seedGroupInFirstList()

            viewModel.onTodoReordered(THIRD_ENTRY, above = FIRST_ENTRY, below = SECOND_ENTRY)

            val call = todoRepository.lastSetSortOrderCall
            assertEquals(LIST_A.id, call?.listId)
            assertEquals(THIRD_ENTRY.id, call?.todoId)
            // Echt zwischen den beiden Ankern — die Zahl selbst rechnet die Domäne aus.
            assertTrue(call!!.sortOrder < FIRST_ENTRY.effectiveOrder)
            assertTrue(call.sortOrder > SECOND_ENTRY.effectiveOrder)
        }

    /**
     * Der Abseits-um-eins, den jede Ziehimplementierung einmal einbaut: Beim Zug nach unten sind die
     * Nachbarn die aus der Liste **ohne** die gezogene Aufgabe. Landet der Wert nicht zwischen
     * genau diesem Paar, rutscht der Eintrag um eine Stelle zu wenig.
     */
    @Test
    fun `dropping an entry one place down lands between the right pair`() =
        runTest(testDispatcher) {
            seedGroupInFirstList()

            viewModel.onTodoReordered(FIRST_ENTRY, above = SECOND_ENTRY, below = THIRD_ENTRY)

            val written = todoRepository.lastSetSortOrderCall!!.sortOrder
            assertTrue(written < SECOND_ENTRY.effectiveOrder)
            assertTrue(written > THIRD_ENTRY.effectiveOrder)
        }

    @Test
    fun `dropping an entry at the top of its group puts it above the first neighbour`() =
        runTest(testDispatcher) {
            seedGroupInFirstList()

            viewModel.onTodoReordered(THIRD_ENTRY, above = null, below = FIRST_ENTRY)

            assertTrue(
                todoRepository.lastSetSortOrderCall!!.sortOrder > FIRST_ENTRY.effectiveOrder
            )
        }

    /** Regel 2, ein zweites Mal geprüft — die Oberfläche lässt den Zug gar nicht erst zu. */
    @Test
    fun `refuses to sort an entry next to a neighbour of another priority`() =
        runTest(testDispatcher) {
            seedGroupInFirstList()

            viewModel.onTodoReordered(
                FIRST_ENTRY,
                above = URGENT_ENTRY,
                below = null
            )

            assertEquals(0, todoRepository.setSortOrderCallCount)
        }

    /** Regel 1: Der erledigte Block ist ein Protokoll und wird nicht umsortiert. */
    @Test
    fun `refuses to sort a ticked entry`() = runTest(testDispatcher) {
        seedGroupInFirstList()

        viewModel.onTodoReordered(FINISHED_TODO_ENTRY, above = FIRST_ENTRY, below = SECOND_ENTRY)

        assertEquals(0, todoRepository.setSortOrderCallCount)
    }

    @Test
    fun `refuses to sort an entry next to a ticked neighbour`() = runTest(testDispatcher) {
        seedGroupInFirstList()

        viewModel.onTodoReordered(FIRST_ENTRY, above = FINISHED_TODO_ENTRY, below = null)

        assertEquals(0, todoRepository.setSortOrderCallCount)
    }

    /**
     * Lässt sich der Platz nicht als Zahl ausdrücken — hier zwei Nachbarn mit demselben Anker —,
     * bekommt die ganze Gruppe frische Werte, statt dass der Zug wirkungslos verpufft.
     */
    @Test
    fun `renumbers the group when the place cannot be expressed as a number`() =
        runTest(testDispatcher) {
            val twin = SECOND_ENTRY.copy(id = "todo-twin")
            advanceUntilIdle()
            todoRepository.emit(
                LIST_A.id,
                Result.success(listOf(FIRST_ENTRY, SECOND_ENTRY, twin))
            )
            advanceUntilIdle()

            viewModel.onTodoReordered(FIRST_ENTRY, above = SECOND_ENTRY, below = twin)

            val call = todoRepository.lastRenumberCall
            assertEquals(LIST_A.id, call?.listId)
            // Die ganze Gruppe, mit der gezogenen Aufgabe an ihrem neuen Platz.
            assertEquals(
                listOf(SECOND_ENTRY.id, FIRST_ENTRY.id, twin.id),
                call?.sortOrders?.keys?.toList()
            )
            assertNull(todoRepository.lastSetSortOrderCall)
        }

    @Test
    fun `a failing sort reports an update error`() = runTest(testDispatcher) {
        seedGroupInFirstList()
        todoRepository.setSortOrderResult = Result.failure(IllegalStateException("nope"))

        viewModel.onTodoReordered(THIRD_ENTRY, above = FIRST_ENTRY, below = SECOND_ENTRY)

        assertEquals(TodoListError.UPDATE_FAILED, viewModel.uiState.value.error)
    }

    /**
     * Die Priorität im Bearbeiten-Dialog zu ändern **behält** den von Hand gewählten Platz: Ihn
     * stillschweigend zu löschen zerstörte den Zug, den der Partner eine Sekunde vorher gemacht hat
     * (ADR 0039).
     */
    @Test
    fun `changing only the priority keeps the hand-sorted place`() = runTest(testDispatcher) {
        val sorted = TODO_ENTRY.copy(sortOrder = 500.0)
        seedEntryInFirstList(sorted)

        viewModel.onEditTodoClick(sorted)
        viewModel.onEditedPriorityChange(TodoPriority.HIGH)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertEquals(500.0, todoRepository.lastMoveTodoCall?.todo?.sortOrder)
    }

    // --- Erledigte auf einmal löschen (ADR 0040) ---

    @Test
    fun `the dialog for clearing finished entries opens and closes`() = runTest(testDispatcher) {
        seedMixedListInFirstList()

        viewModel.onDeleteFinishedClick()
        assertTrue(viewModel.uiState.value.finishedTodosPendingDeletion)

        viewModel.onDeleteFinishedDismiss()
        assertFalse(viewModel.uiState.value.finishedTodosPendingDeletion)
        assertNull(todoRepository.lastDeleteTodosCall)
    }

    /** Der Kern: Genau die abgehakten Einträge gehen, keiner der offenen. */
    @Test
    fun `clearing finished entries deletes exactly the ticked ones`() = runTest(testDispatcher) {
        seedMixedListInFirstList()

        viewModel.onDeleteFinishedClick()
        viewModel.onDeleteFinishedConfirm()

        assertEquals(
            DeleteTodosCall(LIST_A.id, listOf(FINISHED_TODO_ENTRY.id, SECOND_FINISHED_ENTRY.id)),
            todoRepository.lastDeleteTodosCall
        )
        assertFalse(viewModel.uiState.value.finishedTodosPendingDeletion)
    }

    @Test
    fun `a failing clear reports its own error and keeps the dialog open`() =
        runTest(testDispatcher) {
            seedMixedListInFirstList()
            todoRepository.deleteTodosResult = Result.failure(IllegalStateException("nope"))

            viewModel.onDeleteFinishedClick()
            viewModel.onDeleteFinishedConfirm()

            assertEquals(TodoListError.DELETE_FINISHED_FAILED, viewModel.uiState.value.error)
            // Wie beim Löschen einer Liste: Die Snackbar legt sich davor, der Dialog bleibt stehen.
            assertTrue(viewModel.uiState.value.finishedTodosPendingDeletion)
        }

    /**
     * Sonst holte das „Rückgängig" einer vorherigen Einzellöschung eine Aufgabe zurück, die gerade
     * mit weggeräumt wurde — das Angebot muss verfallen.
     */
    @Test
    fun `clearing finished entries drops a pending undo offer`() = runTest(testDispatcher) {
        seedMixedListInFirstList()
        viewModel.onTodoSwipedAway(FINISHED_TODO_ENTRY)
        assertNotNull(viewModel.uiState.value.deletedTodo)

        viewModel.onDeleteFinishedClick()
        viewModel.onDeleteFinishedConfirm()

        assertNull(viewModel.uiState.value.deletedTodo)
    }

    /** Ohne Erledigtes gibt es den Menüeintrag nicht — und falls doch, schreibt hier nichts. */
    @Test
    fun `clearing writes nothing when no entry is ticked off`() = runTest(testDispatcher) {
        seedEntryInFirstList()

        viewModel.onDeleteFinishedClick()
        viewModel.onDeleteFinishedConfirm()

        assertEquals(0, todoRepository.deleteTodosCallCount)
    }

    /** Zwei offene und zwei erledigte Einträge — die Liste, die man nach einer Woche aufräumt. */
    private fun TestScope.seedMixedListInFirstList() {
        advanceUntilIdle()
        todoRepository.emit(
            LIST_A.id,
            Result.success(
                listOf(TODO_ENTRY, FINISHED_TODO_ENTRY, SECOND_ENTRY, SECOND_FINISHED_ENTRY)
            )
        )
        advanceUntilIdle()
    }

    /** Drei offene Einträge derselben Stufe, absteigend nach Alter — die Gruppe, in der gezogen wird. */
    private fun TestScope.seedGroupInFirstList() {
        advanceUntilIdle()
        todoRepository.emit(
            LIST_A.id,
            Result.success(listOf(FIRST_ENTRY, SECOND_ENTRY, THIRD_ENTRY))
        )
        advanceUntilIdle()
    }

    @Test
    fun `deleting an entry removes it and closes the dialog`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        assertEquals(DeleteCall(LIST_A.id, TODO_ENTRY.id), todoRepository.lastDeleteCall)
        assertNull(viewModel.uiState.value.editedTodo)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failing delete keeps the dialog open and reports a delete error`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            todoRepository.deleteResult = Result.failure(IllegalStateException("no permission"))

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onDeleteTodoClick()

            assertEquals(TodoListError.DELETE_FAILED, viewModel.uiState.value.error)
            assertEquals(TODO_ENTRY.id, viewModel.uiState.value.editedTodo?.todoId)
        }

    @Test
    fun `nothing is deleted while no dialog is open`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onDeleteTodoClick()

        assertNull(todoRepository.lastDeleteCall)
    }

    @Test
    fun `swiping a finished entry away deletes it`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onTodoSwipedAway(FINISHED_TODO_ENTRY)

        assertEquals(DeleteCall(LIST_A.id, FINISHED_TODO_ENTRY.id), todoRepository.lastDeleteCall)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `swiping does not delete an entry that is still open`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onTodoSwipedAway(TODO_ENTRY)

        assertNull(todoRepository.lastDeleteCall)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failing swipe delete reports a delete error`() = runTest(testDispatcher) {
        advanceUntilIdle()
        todoRepository.deleteResult = Result.failure(IllegalStateException("no permission"))

        viewModel.onTodoSwipedAway(FINISHED_TODO_ENTRY)

        assertEquals(TodoListError.DELETE_FAILED, viewModel.uiState.value.error)
    }

    // --- Rückgängig nach dem Löschen (ADR 0031) ---

    @Test
    fun `deleting an entry from the dialog offers to undo it`() = runTest(testDispatcher) {
        seedEntryInFirstList()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        assertEquals(TODO_ENTRY, viewModel.uiState.value.deletedTodo)
    }

    @Test
    fun `swiping a finished entry away offers to undo it`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onTodoSwipedAway(FINISHED_TODO_ENTRY)

        assertEquals(FINISHED_TODO_ENTRY, viewModel.uiState.value.deletedTodo)
    }

    /**
     * Der Dialog kann Getipptes tragen, das nie gespeichert wurde. Rückgängig stellt den Stand von
     * vor dem Löschen her — nicht einen, den es in Firestore nie gab.
     */
    @Test
    fun `the undo offer carries the stored entry and not the unsaved dialog input`() =
        runTest(testDispatcher) {
            seedEntryInFirstList()

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTitleChange("Etwas ganz anderes")
            viewModel.onEditedNotesChange("Nie gespeichert")
            viewModel.onDeleteTodoClick()

            assertEquals(TODO_ENTRY, viewModel.uiState.value.deletedTodo)
        }

    @Test
    fun `undoing a delete restores the entry under its old id`() = runTest(testDispatcher) {
        seedEntryInFirstList()
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        viewModel.onUndoDelete()

        assertEquals(RestoreTodoCall(LIST_A.id, TODO_ENTRY), todoRepository.lastRestoreTodoCall)
        assertNull(viewModel.uiState.value.deletedTodo)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failing undo reports a restore error and drops the offer`() = runTest(testDispatcher) {
        seedEntryInFirstList()
        todoRepository.restoreTodoResult = Result.failure(IllegalStateException("no permission"))
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        viewModel.onUndoDelete()

        assertEquals(TodoListError.RESTORE_FAILED, viewModel.uiState.value.error)
        // Ein zweites Angebot hieße, die Snackbar über sich selbst zu stapeln.
        assertNull(viewModel.uiState.value.deletedTodo)
    }

    @Test
    fun `a failing delete offers no undo`() = runTest(testDispatcher) {
        seedEntryInFirstList()
        todoRepository.deleteResult = Result.failure(IllegalStateException("no permission"))

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        assertNull(viewModel.uiState.value.deletedTodo)
    }

    /** Der Partner war schneller: Zu löschen ist noch etwas, zurückzuholen nichts mehr. */
    @Test
    fun `deleting an entry that is no longer in the list offers no undo`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onDeleteTodoClick()

            assertEquals(DeleteCall(LIST_A.id, TODO_ENTRY.id), todoRepository.lastDeleteCall)
            assertNull(viewModel.uiState.value.deletedTodo)
        }

    @Test
    fun `a shown delete message drops the undo offer`() = runTest(testDispatcher) {
        seedEntryInFirstList()
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        viewModel.onDeletedMessageShown()

        assertNull(viewModel.uiState.value.deletedTodo)
    }

    /**
     * Anders als die Verschiebe-Bestätigung überlebt das Angebot keinen Listenwechsel: Es würde
     * sonst in die neu gewählte Liste zurücklegen.
     */
    @Test
    fun `switching lists drops the undo offer`() = runTest(testDispatcher) {
        seedEntryInFirstList()
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        viewModel.onListSelected(LIST_B)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deletedTodo)
        viewModel.onUndoDelete()
        assertNull(todoRepository.lastRestoreTodoCall)
    }

    @Test
    fun `a shown write error is cleared`() = runTest(testDispatcher) {
        advanceUntilIdle()
        todoRepository.addResult = Result.failure(IllegalStateException("offline"))
        viewModel.onNewTodoTitleChange("Milch kaufen")
        viewModel.addTodo()

        viewModel.onErrorShown()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a shown load error stays visible`() = runTest(testDispatcher) {
        todoRepository.emit(LIST_A.id, Result.failure(IllegalStateException("no permission")))
        advanceUntilIdle()

        viewModel.onErrorShown()

        assertEquals(TodoListError.LOAD_FAILED, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.todos.isEmpty())
    }

    @Test
    fun `a missing calendar app becomes an error`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onCalendarAppMissing()

        assertEquals(TodoListError.CALENDAR_APP_MISSING, viewModel.uiState.value.error)
    }

    @Test
    fun `a shown calendar error is cleared`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onCalendarAppMissing()

        viewModel.onErrorShown()

        // Anders als LOAD_FAILED klebt er nicht: Die Liste ist danach wieder in Ordnung.
        assertNull(viewModel.uiState.value.error)
    }

    // --- Vorlagen (ADR 0034) ---

    @Test
    fun `templates are kept apart from the working lists`() = runTest(testDispatcher) {
        listRepository.emit(Result.success(listOf(TEMPLATE, LIST_A, LIST_B)))
        advanceUntilIdle()

        assertEquals(listOf(LIST_A, LIST_B), viewModel.uiState.value.lists)
        assertEquals(listOf(TEMPLATE), viewModel.uiState.value.templates)
    }

    /** Wer eine Liste verloren hat, will weiterarbeiten — nicht in einer Vorlage landen. */
    @Test
    fun `the fallback prefers a working list over a template`() = runTest(testDispatcher) {
        listRepository.emit(Result.success(listOf(TEMPLATE, LIST_A, LIST_B)))
        advanceUntilIdle()

        assertEquals(LIST_A, viewModel.uiState.value.selectedList)
    }

    /** Ein leerer Bildschirm neben einer vorhandenen Vorlage wäre die schlechtere Antwort. */
    @Test
    fun `a template is opened when there is no working list`() = runTest(testDispatcher) {
        listRepository.emit(Result.success(listOf(TEMPLATE)))
        advanceUntilIdle()

        assertEquals(TEMPLATE, viewModel.uiState.value.selectedList)
        assertTrue(viewModel.uiState.value.isTemplateOpen)
    }

    @Test
    fun `a remembered template is opened`() = runTest(testDispatcher) {
        selectedListRepository.setRemembered(TEMPLATE.id)
        listRepository.emit(Result.success(listOf(TEMPLATE, LIST_A, LIST_B)))
        createViewModel()
        advanceUntilIdle()

        assertEquals(TEMPLATE, viewModel.uiState.value.selectedList)
    }

    @Test
    fun `a new template is created with the template flag`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onNewTemplateClick()
        viewModel.onNewListNameChange("  Urlaub packen  ")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals(
            CreateListCall("Urlaub packen", shared = false, isTemplate = true),
            listRepository.lastCreateCall
        )
        assertNull(viewModel.uiState.value.newList)
    }

    @Test
    fun `the instantiation dialog is prefilled from the template`() = runTest(testDispatcher) {
        openTemplate()

        viewModel.onCreateListFromTemplateClick()

        assertEquals(
            NewList(name = TEMPLATE.name, shared = true, kind = NewListKind.FROM_TEMPLATE),
            viewModel.uiState.value.newList
        )
    }

    /** Ohne hinterlegten Partner bietet die Oberfläche „geteilt" nicht an — also darf es nicht vorbelegt sein. */
    @Test
    fun `instantiating a shared template stays private without a partner`() =
        runTest(testDispatcher) {
            partnerRepository.emit(null)
            openTemplate()

            viewModel.onCreateListFromTemplateClick()

            assertFalse(viewModel.uiState.value.newList!!.shared)
        }

    @Test
    fun `instantiating a template writes its entries into a new list`() = runTest(testDispatcher) {
        openTemplate()

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListNameChange("  Mallorca  ")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals(
            CreateListFromTemplateCall("Mallorca", shared = true, todos = listOf(TODO_ENTRY)),
            listRepository.lastCreateFromTemplateCall
        )
        // Die Vorlage selbst bleibt unangetastet — sie ist der Stempel, nicht der Abdruck.
        assertNull(todoRepository.lastDeleteCall)
        assertNull(viewModel.uiState.value.newList)
    }

    @Test
    fun `an instantiated list starts with open entries`() = runTest(testDispatcher) {
        openTemplate(entries = listOf(FINISHED_TODO_ENTRY))

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        val entry = listRepository.lastCreateFromTemplateCall?.todos?.single()
        assertEquals(false, entry?.isDone)
        assertNull(entry?.completedBy)
        assertNull(entry?.completedAt)
        // Was die Aufgabe beschreibt, kommt unverändert an.
        assertEquals(FINISHED_TODO_ENTRY.createdAt, entry?.createdAt)
        assertEquals(FINISHED_TODO_ENTRY.title, entry?.title)
    }

    /** Der einzige Anlegeweg, der die neue Liste auch öffnet. */
    @Test
    fun `instantiating a template opens the new list`() = runTest(testDispatcher) {
        openTemplate()

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals(LIST_FROM_TEMPLATE_ID, selectedListRepository.lastSelectedId)
    }

    @Test
    fun `a failing instantiation keeps the dialog open and reports its own error`() =
        runTest(testDispatcher) {
            openTemplate()
            listRepository.createFromTemplateResult =
                Result.failure(IllegalStateException("no permission"))

            viewModel.onCreateListFromTemplateClick()
            viewModel.onNewListConfirm()
            advanceUntilIdle()

            assertEquals(TodoListError.LIST_ADD_FAILED, viewModel.uiState.value.error)
            assertEquals(TEMPLATE.name, viewModel.uiState.value.newList?.name)
        }

    @Test
    fun `an entry in a template can be moved to another template`() = runTest(testDispatcher) {
        openTemplate()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTargetListChange(SECOND_TEMPLATE)
        viewModel.onEditConfirm()

        assertEquals(
            MoveTodoCall(TEMPLATE.id, SECOND_TEMPLATE.id, TODO_ENTRY),
            todoRepository.lastMoveTodoCall
        )
    }

    /**
     * Die zweite Verteidigungslinie hinter dem Feld im Dialog: Eine Vorlage ist kein Ort, an dem eine
     * Aufgabe abgelegt wird, und aus einer Vorlage heraus ist eine Arbeitsliste kein Ziel.
     */
    @Test
    fun `an entry in a template is not moved into a working list`() = runTest(testDispatcher) {
        openTemplate()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTargetListChange(LIST_B)
        viewModel.onEditConfirm()

        assertNull(todoRepository.lastMoveTodoCall)
        assertEquals(TodoListError.MOVE_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `an entry in a working list is not moved into a template`() = runTest(testDispatcher) {
        listRepository.emit(Result.success(listOf(TEMPLATE, LIST_A, LIST_B)))
        seedEntryInFirstList()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTargetListChange(TEMPLATE)
        viewModel.onEditConfirm()

        assertNull(todoRepository.lastMoveTodoCall)
        assertEquals(TodoListError.MOVE_FAILED, viewModel.uiState.value.error)
    }

    // --- Faktor beim Instanziieren (ADR 0037) ---

    @Test
    fun `instantiating scales the entries that carry a quantity`() = runTest(testDispatcher) {
        openTemplate(entries = listOf(SHIRT_ENTRY, SHAMPOO_ENTRY))

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListFactorChange("3")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        val titles = listRepository.lastCreateFromTemplateCall?.todos?.map(Todo::title)
        // Drei Tage heißen drei T-Shirts, aber nicht drei Shampoo.
        assertEquals(listOf("3 T-Shirt", "Shampoo"), titles)
    }

    @Test
    fun `the scaled amount does not travel as a field`() = runTest(testDispatcher) {
        openTemplate(entries = listOf(SHIRT_ENTRY))

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListFactorChange("3")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        // Was entsteht, ist eine gewöhnliche Liste ohne Sonderregeln.
        assertNull(listRepository.lastCreateFromTemplateCall?.todos?.single()?.quantity)
    }

    @Test
    fun `a factor with a comma is accepted`() = runTest(testDispatcher) {
        openTemplate(entries = listOf(SHIRT_ENTRY.copy(quantity = 2.0)))

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListFactorChange("2,5")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals("5 T-Shirt", listRepository.lastCreateFromTemplateCall?.todos?.single()?.title)
    }

    /** Der Normalfall bleibt der billigste: Faktor 1 ergibt, was in der Vorlage steht. */
    @Test
    fun `the default factor of one writes the amount unchanged`() = runTest(testDispatcher) {
        openTemplate(entries = listOf(SHIRT_ENTRY))

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertEquals("1 T-Shirt", listRepository.lastCreateFromTemplateCall?.todos?.single()?.title)
    }

    @Test
    fun `an unreadable factor creates nothing`() = runTest(testDispatcher) {
        openTemplate()

        viewModel.onCreateListFromTemplateClick()
        viewModel.onNewListFactorChange("drei")
        viewModel.onNewListConfirm()
        advanceUntilIdle()

        assertNull(listRepository.lastCreateFromTemplateCall)
        // Der Dialog bleibt offen, damit der eingetippte Name nicht verloren geht.
        assertNotNull(viewModel.uiState.value.newList)
    }

    /** Öffnet die Vorlage mit ihren Einträgen — der Ausgangspunkt der Vorlagen-Tests. */
    private fun TestScope.openTemplate(entries: List<Todo> = listOf(TODO_ENTRY)) {
        listRepository.emit(Result.success(listOf(TEMPLATE, SECOND_TEMPLATE, LIST_A, LIST_B)))
        advanceUntilIdle()
        viewModel.onListSelected(TEMPLATE)
        advanceUntilIdle()
        todoRepository.emit(TEMPLATE.id, Result.success(entries))
        advanceUntilIdle()
    }
}

private val SIGNED_IN_USER = SignedInUser(uid = "uid-1", displayName = "Georg", email = null)

private val PARTNER = Partner(uid = "uid-2", displayName = "Anna")

private val LIST_A = TodoList(id = "list-a", name = "Einkauf", isShared = true, isTemplate = false)

private val LIST_B = TodoList(id = "list-b", name = "Zuhause", isShared = false, isTemplate = false)

/**
 * Sortiert vor beide Arbeitslisten — nur so zeigt der Rückfall-Test wirklich etwas: Stünde die
 * Vorlage hinten, wäre „die erste Arbeitsliste" auch ohne Regel die erste überhaupt.
 */
private val TEMPLATE =
    TodoList(id = "template-a", name = "Ausrüstung", isShared = true, isTemplate = true)

private val SECOND_TEMPLATE =
    TodoList(id = "template-b", name = "Bergtour", isShared = false, isTemplate = true)

private const val CREATED_LIST_ID = "list-created"

private const val LIST_FROM_TEMPLATE_ID = "list-from-template"

private val TODO_ENTRY = Todo(
    id = "todo-1",
    title = "Milch kaufen",
    isDone = false,
    priority = TodoPriority.MEDIUM,
    createdAt = Instant.parse("2026-08-07T20:00:00Z"),
    updatedAt = Instant.parse("2026-08-07T20:00:00Z"),
    completedBy = null,
    completedAt = null,
    notes = null,
    quantity = null,
    sortOrder = null
)

/** Ein Vorlagen-Eintrag, der mitskaliert — und einer, der es ausdrücklich nicht tut. */
private val SHIRT_ENTRY = TODO_ENTRY.copy(id = "todo-shirt", title = "T-Shirt", quantity = 1.0)

private val SHAMPOO_ENTRY =
    TODO_ENTRY.copy(id = "todo-shampoo", title = "Shampoo", quantity = null)

/*
 * Drei offene Einträge derselben Stufe, deren Anker absteigend sind — also genau die Reihenfolge,
 * in der sie auch auf dem Bildschirm stehen. Der vierte trägt eine andere Stufe und ist die
 * Gegenprobe für die Gruppengrenze.
 */
private val FIRST_ENTRY = TODO_ENTRY.copy(id = "todo-first", sortOrder = 300.0)

private val SECOND_ENTRY = TODO_ENTRY.copy(id = "todo-second", sortOrder = 200.0)

private val THIRD_ENTRY = TODO_ENTRY.copy(id = "todo-third", sortOrder = 100.0)

private val URGENT_ENTRY =
    TODO_ENTRY.copy(id = "todo-urgent", priority = TodoPriority.HIGH, sortOrder = 300.0)

private val FINISHED_TODO_ENTRY = TODO_ENTRY.copy(
    isDone = true,
    completedBy = "uid-1",
    completedAt = Instant.parse("2026-08-07T20:05:00Z")
)

private val SECOND_FINISHED_ENTRY = FINISHED_TODO_ENTRY.copy(
    id = "todo-finished-2",
    title = "Kaffee kaufen",
    completedAt = Instant.parse("2026-08-07T20:10:00Z")
)

private data class AddCall(val listId: String, val title: String)

private data class SetDoneCall(
    val listId: String,
    val todoId: String,
    val isDone: Boolean,
    val completedBy: String?
)

private data class UpdateTodoCall(val listId: String, val todoId: String, val update: TodoUpdate)

private data class MoveTodoCall(val fromListId: String, val toListId: String, val todo: Todo)

private data class DeleteCall(val listId: String, val todoId: String)

private data class DeleteTodosCall(val listId: String, val todoIds: List<String>)

private data class RestoreTodoCall(val listId: String, val todo: Todo)

private data class SetSortOrderCall(val listId: String, val todoId: String, val sortOrder: Double)

private data class RenumberCall(val listId: String, val sortOrders: Map<String, Double>)

private class FakeTodoRepository : TodoRepository {
    var addResult: Result<Unit> = Result.success(Unit)
    var setDoneResult: Result<Unit> = Result.success(Unit)
    var updateTodoResult: Result<Unit> = Result.success(Unit)
    var moveTodoResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var deleteTodosResult: Result<Unit> = Result.success(Unit)
    var restoreTodoResult: Result<Unit> = Result.success(Unit)
    var setSortOrderResult: Result<Unit> = Result.success(Unit)
    var addCallCount = 0
        private set
    var lastAddCall: AddCall? = null
        private set
    var lastSetDoneCall: SetDoneCall? = null
        private set
    var lastUpdateTodoCall: UpdateTodoCall? = null
        private set
    var lastMoveTodoCall: MoveTodoCall? = null
        private set
    var lastDeleteCall: DeleteCall? = null
        private set
    var lastDeleteTodosCall: DeleteTodosCall? = null
        private set
    var deleteTodosCallCount = 0
        private set
    var lastRestoreTodoCall: RestoreTodoCall? = null
        private set
    var lastSetSortOrderCall: SetSortOrderCall? = null
        private set
    var setSortOrderCallCount = 0
        private set
    var lastRenumberCall: RenumberCall? = null
        private set

    /** Lists a flow was requested for, in order — that is what a list switch has to change. */
    val observedListIds = mutableListOf<String>()

    private val emitted = mutableMapOf<String, MutableStateFlow<Result<List<Todo>>>>()

    override fun todos(listId: String): Flow<Result<List<Todo>>> {
        observedListIds += listId
        return flowFor(listId)
    }

    fun emit(listId: String, result: Result<List<Todo>>) {
        flowFor(listId).value = result
    }

    private fun flowFor(listId: String) =
        emitted.getOrPut(listId) { MutableStateFlow(Result.success(emptyList())) }

    override fun addTodo(listId: String, title: String): Result<Unit> {
        addCallCount++
        lastAddCall = AddCall(listId, title)
        return addResult
    }

    override fun setDone(
        listId: String,
        todoId: String,
        isDone: Boolean,
        completedBy: String?
    ): Result<Unit> {
        lastSetDoneCall = SetDoneCall(listId, todoId, isDone, completedBy)
        return setDoneResult
    }

    override fun updateTodo(listId: String, todoId: String, update: TodoUpdate): Result<Unit> {
        lastUpdateTodoCall = UpdateTodoCall(listId, todoId, update)
        return updateTodoResult
    }

    override fun moveTodo(fromListId: String, toListId: String, todo: Todo): Result<Unit> {
        lastMoveTodoCall = MoveTodoCall(fromListId, toListId, todo)
        return moveTodoResult
    }

    override fun deleteTodo(listId: String, todoId: String): Result<Unit> {
        lastDeleteCall = DeleteCall(listId, todoId)
        return deleteResult
    }

    override fun deleteTodos(listId: String, todoIds: List<String>): Result<Unit> {
        deleteTodosCallCount++
        lastDeleteTodosCall = DeleteTodosCall(listId, todoIds)
        return deleteTodosResult
    }

    override fun restoreTodo(listId: String, todo: Todo): Result<Unit> {
        lastRestoreTodoCall = RestoreTodoCall(listId, todo)
        return restoreTodoResult
    }

    override fun setSortOrder(listId: String, todoId: String, sortOrder: Double): Result<Unit> {
        setSortOrderCallCount++
        lastSetSortOrderCall = SetSortOrderCall(listId, todoId, sortOrder)
        return setSortOrderResult
    }

    override fun renumberTodos(listId: String, sortOrders: Map<String, Double>): Result<Unit> {
        lastRenumberCall = RenumberCall(listId, sortOrders)
        return Result.success(Unit)
    }
}

private data class CreateListCall(val name: String, val shared: Boolean, val isTemplate: Boolean)

private data class CreateListFromTemplateCall(
    val name: String,
    val shared: Boolean,
    val todos: List<Todo>
)

private data class RenameListCall(val listId: String, val name: String)

private class FakeListRepository : ListRepository {
    var createResult: Result<String> = Result.success(CREATED_LIST_ID)
    var createFromTemplateResult: Result<String> = Result.success(LIST_FROM_TEMPLATE_ID)
    var renameResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var lastCreateCall: CreateListCall? = null
        private set
    var lastCreateFromTemplateCall: CreateListFromTemplateCall? = null
        private set
    var lastRenameCall: RenameListCall? = null
        private set
    var deletedListId: String? = null
        private set

    private val emitted =
        MutableStateFlow<Result<List<TodoList>>>(Result.success(listOf(LIST_A, LIST_B)))

    override val lists: Flow<Result<List<TodoList>>> = emitted

    fun emit(result: Result<List<TodoList>>) {
        emitted.value = result
    }

    override suspend fun createList(
        name: String,
        shared: Boolean,
        isTemplate: Boolean
    ): Result<String> {
        lastCreateCall = CreateListCall(name, shared, isTemplate)
        return createResult
    }

    override suspend fun createListFromTemplate(
        name: String,
        shared: Boolean,
        todos: List<Todo>
    ): Result<String> {
        lastCreateFromTemplateCall = CreateListFromTemplateCall(name, shared, todos)
        return createFromTemplateResult
    }

    override fun renameList(listId: String, name: String): Result<Unit> {
        lastRenameCall = RenameListCall(listId, name)
        return renameResult
    }

    override suspend fun deleteList(listId: String): Result<Unit> {
        deletedListId = listId
        return deleteResult
    }
}

private class FakePartnerRepository(partner: Partner? = PARTNER) : PartnerRepository {
    private val emitted = MutableStateFlow(partner)

    override val partner: Flow<Partner?> = emitted

    fun emit(partner: Partner?) {
        emitted.value = partner
    }
}

private class FakeSelectedListRepository : SelectedListRepository {
    private val remembered = MutableStateFlow<String?>(null)

    /**
     * Was zuletzt gemerkt wurde. Die einzige Spur, über die sich prüfen lässt, dass eine aus einer
     * Vorlage erzeugte Liste geöffnet wird — sie existiert im Attrappen-Bestand ja nicht.
     */
    var lastSelectedId: String? = null
        private set

    override val selectedListId: Flow<String?> = remembered

    override suspend fun select(listId: String) {
        lastSelectedId = listId
        remembered.value = listId
    }

    /** Seeds what a previous session left behind, before the view model is built. */
    fun setRemembered(listId: String?) {
        remembered.value = listId
    }
}

private class FakeAuthRepository(private val user: SignedInUser?) : AuthRepository {
    override val currentUser: SignedInUser? = user

    override val signedInUser: Flow<SignedInUser?> = MutableStateFlow(user)

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<SignedInUser> =
        user?.let { Result.success(it) } ?: Result.failure(IllegalStateException("not signed in"))

    override fun signOut() = Unit
}
