package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.auth.SignedInUser
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.SelectedListRepository
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
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

        assertEquals(CreateListCall("Garten", shared = false), listRepository.lastCreateCall)
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

        assertEquals(CreateListCall("Garten", shared = true), listRepository.lastCreateCall)
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
                    notes = "Die haltbare"
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
                    "Brot kaufen",
                    TodoPriority.MEDIUM,
                    notes = null
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
                    TODO_ENTRY.title,
                    TodoPriority.HIGH,
                    notes = null
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
            todoRepository.lastUpdateTodoCall?.notes
        )
    }

    @Test
    fun `emptying the notes writes null instead of a blank string`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY.copy(notes = "Die haltbare"))
        viewModel.onEditedNotesChange("   ")
        viewModel.onEditConfirm()

        // „Keine Notiz" hat genau eine Form, und die ist null.
        assertNull(todoRepository.lastUpdateTodoCall?.notes)
    }

    @Test
    fun `typing notes writes nothing before the edit is saved`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedNotesChange("Die haltbare")

        assertNull(todoRepository.lastUpdateTodoCall)
        assertEquals("Die haltbare", viewModel.uiState.value.editedTodo?.notes)
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
}

private val SIGNED_IN_USER = SignedInUser(uid = "uid-1", displayName = "Georg", email = null)

private val PARTNER = Partner(uid = "uid-2", displayName = "Anna")

private val LIST_A = TodoList(id = "list-a", name = "Einkauf", isShared = true)

private val LIST_B = TodoList(id = "list-b", name = "Zuhause", isShared = false)

private val TODO_ENTRY = Todo(
    id = "todo-1",
    title = "Milch kaufen",
    isDone = false,
    priority = TodoPriority.MEDIUM,
    createdAt = Instant.parse("2026-08-07T20:00:00Z"),
    updatedAt = Instant.parse("2026-08-07T20:00:00Z"),
    completedBy = null,
    completedAt = null,
    notes = null
)

private val FINISHED_TODO_ENTRY = TODO_ENTRY.copy(
    isDone = true,
    completedBy = "uid-1",
    completedAt = Instant.parse("2026-08-07T20:05:00Z")
)

private data class AddCall(val listId: String, val title: String)

private data class SetDoneCall(
    val listId: String,
    val todoId: String,
    val isDone: Boolean,
    val completedBy: String?
)

private data class UpdateTodoCall(
    val listId: String,
    val todoId: String,
    val title: String,
    val priority: TodoPriority,
    val notes: String?
)

private data class MoveTodoCall(val fromListId: String, val toListId: String, val todo: Todo)

private data class DeleteCall(val listId: String, val todoId: String)

private class FakeTodoRepository : TodoRepository {
    var addResult: Result<Unit> = Result.success(Unit)
    var setDoneResult: Result<Unit> = Result.success(Unit)
    var updateTodoResult: Result<Unit> = Result.success(Unit)
    var moveTodoResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
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

    override fun updateTodo(
        listId: String,
        todoId: String,
        title: String,
        priority: TodoPriority,
        notes: String?
    ): Result<Unit> {
        lastUpdateTodoCall = UpdateTodoCall(listId, todoId, title, priority, notes)
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
}

private data class CreateListCall(val name: String, val shared: Boolean)

private data class RenameListCall(val listId: String, val name: String)

private class FakeListRepository : ListRepository {
    var createResult: Result<Unit> = Result.success(Unit)
    var renameResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var lastCreateCall: CreateListCall? = null
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

    override suspend fun createList(name: String, shared: Boolean): Result<Unit> {
        lastCreateCall = CreateListCall(name, shared)
        return createResult
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

    override val selectedListId: Flow<String?> = remembered

    override suspend fun select(listId: String) {
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
