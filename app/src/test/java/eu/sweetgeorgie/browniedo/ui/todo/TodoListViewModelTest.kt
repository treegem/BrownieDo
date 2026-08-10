package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.auth.SignedInUser
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.SelectedListRepository
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `opening the edit dialog shows the current title`() = runTest(testDispatcher) {
        viewModel.onEditTodoClick(TODO_ENTRY)

        assertEquals(
            TodoEdit(todoId = TODO_ENTRY.id, title = TODO_ENTRY.title),
            viewModel.uiState.value.editedTodo
        )
    }

    @Test
    fun `saving an edit writes the trimmed title and closes the dialog`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTitleChange("  Brot kaufen  ")
            viewModel.onEditConfirm()

            assertEquals(
                SetTitleCall(LIST_A.id, TODO_ENTRY.id, "Brot kaufen"),
                todoRepository.lastSetTitleCall
            )
            assertNull(viewModel.uiState.value.editedTodo)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `an edit with a blank title is not written`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTitleChange("   ")
        viewModel.onEditConfirm()

        assertNull(todoRepository.lastSetTitleCall)
    }

    @Test
    fun `a failing edit keeps the dialog open and reports an update error`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            todoRepository.setTitleResult = Result.failure(IllegalStateException("no permission"))

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

        assertNull(todoRepository.lastSetTitleCall)
        assertNull(viewModel.uiState.value.editedTodo)
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
}

private val SIGNED_IN_USER = SignedInUser(uid = "uid-1", displayName = "Georg", email = null)

private val LIST_A = TodoList(id = "list-a", name = "Einkauf", isShared = true)

private val LIST_B = TodoList(id = "list-b", name = "Zuhause", isShared = false)

private val TODO_ENTRY = Todo(
    id = "todo-1",
    title = "Milch kaufen",
    isDone = false,
    createdAt = Instant.parse("2026-08-07T20:00:00Z"),
    updatedAt = Instant.parse("2026-08-07T20:00:00Z"),
    completedBy = null
)

private val FINISHED_TODO_ENTRY = TODO_ENTRY.copy(isDone = true, completedBy = "uid-1")

private data class AddCall(val listId: String, val title: String)

private data class SetDoneCall(
    val listId: String,
    val todoId: String,
    val isDone: Boolean,
    val completedBy: String?
)

private data class SetTitleCall(val listId: String, val todoId: String, val title: String)

private data class DeleteCall(val listId: String, val todoId: String)

private class FakeTodoRepository : TodoRepository {
    var addResult: Result<Unit> = Result.success(Unit)
    var setDoneResult: Result<Unit> = Result.success(Unit)
    var setTitleResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var addCallCount = 0
        private set
    var lastAddCall: AddCall? = null
        private set
    var lastSetDoneCall: SetDoneCall? = null
        private set
    var lastSetTitleCall: SetTitleCall? = null
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

    override fun setTitle(listId: String, todoId: String, title: String): Result<Unit> {
        lastSetTitleCall = SetTitleCall(listId, todoId, title)
        return setTitleResult
    }

    override fun deleteTodo(listId: String, todoId: String): Result<Unit> {
        lastDeleteCall = DeleteCall(listId, todoId)
        return deleteResult
    }
}

private class FakeListRepository : ListRepository {
    private val emitted =
        MutableStateFlow<Result<List<TodoList>>>(Result.success(listOf(LIST_A, LIST_B)))

    override val lists: Flow<Result<List<TodoList>>> = emitted

    fun emit(result: Result<List<TodoList>>) {
        emitted.value = result
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
