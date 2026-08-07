package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.auth.SignedInUser
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val todoRepository = FakeTodoRepository()
    private val authRepository = FakeAuthRepository(SIGNED_IN_USER)
    private lateinit var viewModel: TodoListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TodoListViewModel(todoRepository, authRepository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `entries from the repository end up in the ui state`() = runTest(testDispatcher) {
        todoRepository.emit(Result.success(listOf(TODO_ENTRY)))
        advanceUntilIdle()

        assertEquals(listOf(TODO_ENTRY), viewModel.uiState.value.todos)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failing repository flow reports a load error`() = runTest(testDispatcher) {
        todoRepository.emit(Result.failure(IllegalStateException("no permission")))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(TodoListError.LOAD_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `adding an entry trims the title and clears the input`() = runTest(testDispatcher) {
        viewModel.onNewTodoTitleChange("  Milch kaufen  ")
        viewModel.addTodo()
        advanceUntilIdle()

        assertEquals("Milch kaufen", todoRepository.addedTitle)
        assertEquals("", viewModel.uiState.value.newTodoTitle)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a blank title is not written`() = runTest(testDispatcher) {
        viewModel.onNewTodoTitleChange("   ")
        viewModel.addTodo()
        advanceUntilIdle()

        assertEquals(0, todoRepository.addCallCount)
    }

    @Test
    fun `a failing write reports an error and keeps the input`() = runTest(testDispatcher) {
        todoRepository.addResult = Result.failure(IllegalStateException("offline"))

        viewModel.onNewTodoTitleChange("Milch kaufen")
        viewModel.addTodo()
        advanceUntilIdle()

        assertEquals(TodoListError.ADD_FAILED, viewModel.uiState.value.error)
        assertEquals("Milch kaufen", viewModel.uiState.value.newTodoTitle)
    }

    @Test
    fun `a successful load clears a previous error`() = runTest(testDispatcher) {
        todoRepository.emit(Result.failure(IllegalStateException("no permission")))
        advanceUntilIdle()

        todoRepository.emit(Result.success(listOf(TODO_ENTRY)))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `ticking an entry off records it as done by the signed in user`() =
        runTest(testDispatcher) {
            viewModel.onTodoDoneChange(TODO_ENTRY, isDone = true)

            assertEquals(
                SetDoneCall(TODO_ENTRY.id, isDone = true, completedBy = SIGNED_IN_USER.uid),
                todoRepository.lastSetDoneCall
            )
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `reopening an entry clears who completed it`() = runTest(testDispatcher) {
        viewModel.onTodoDoneChange(TODO_ENTRY.copy(isDone = true), isDone = false)

        assertEquals(
            SetDoneCall(TODO_ENTRY.id, isDone = false, completedBy = null),
            todoRepository.lastSetDoneCall
        )
    }

    @Test
    fun `a failing update reports an update error`() = runTest(testDispatcher) {
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
            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTitleChange("  Brot kaufen  ")
            viewModel.onEditConfirm()

            assertEquals(
                SetTitleCall(TODO_ENTRY.id, "Brot kaufen"),
                todoRepository.lastSetTitleCall
            )
            assertNull(viewModel.uiState.value.editedTodo)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `an edit with a blank title is not written`() = runTest(testDispatcher) {
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTitleChange("   ")
        viewModel.onEditConfirm()

        assertNull(todoRepository.lastSetTitleCall)
    }

    @Test
    fun `a failing edit keeps the dialog open and reports an update error`() =
        runTest(testDispatcher) {
            todoRepository.setTitleResult = Result.failure(IllegalStateException("no permission"))

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onEditedTitleChange("Brot kaufen")
            viewModel.onEditConfirm()

            assertEquals(TodoListError.UPDATE_FAILED, viewModel.uiState.value.error)
            assertEquals("Brot kaufen", viewModel.uiState.value.editedTodo?.title)
        }

    @Test
    fun `cancelling an edit writes nothing`() = runTest(testDispatcher) {
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onEditedTitleChange("Brot kaufen")
        viewModel.onEditDismiss()

        assertNull(todoRepository.lastSetTitleCall)
        assertNull(viewModel.uiState.value.editedTodo)
    }

    @Test
    fun `deleting an entry removes it and closes the dialog`() = runTest(testDispatcher) {
        viewModel.onEditTodoClick(TODO_ENTRY)
        viewModel.onDeleteTodoClick()

        assertEquals(TODO_ENTRY.id, todoRepository.deletedTodoId)
        assertNull(viewModel.uiState.value.editedTodo)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failing delete keeps the dialog open and reports a delete error`() =
        runTest(testDispatcher) {
            todoRepository.deleteResult = Result.failure(IllegalStateException("no permission"))

            viewModel.onEditTodoClick(TODO_ENTRY)
            viewModel.onDeleteTodoClick()

            assertEquals(TodoListError.DELETE_FAILED, viewModel.uiState.value.error)
            assertEquals(TODO_ENTRY.id, viewModel.uiState.value.editedTodo?.todoId)
        }

    @Test
    fun `nothing is deleted while no dialog is open`() = runTest(testDispatcher) {
        viewModel.onDeleteTodoClick()

        assertNull(todoRepository.deletedTodoId)
    }

    private companion object {
        val SIGNED_IN_USER = SignedInUser(uid = "uid-1", displayName = "Georg", email = null)

        val TODO_ENTRY = Todo(
            id = "todo-1",
            title = "Milch kaufen",
            isDone = false,
            createdAt = Instant.parse("2026-08-07T20:00:00Z"),
            updatedAt = Instant.parse("2026-08-07T20:00:00Z"),
            completedBy = null
        )
    }
}

private data class SetDoneCall(val todoId: String, val isDone: Boolean, val completedBy: String?)

private data class SetTitleCall(val todoId: String, val title: String)

private class FakeTodoRepository : TodoRepository {
    var addResult: Result<Unit> = Result.success(Unit)
    var setDoneResult: Result<Unit> = Result.success(Unit)
    var setTitleResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var addedTitle: String? = null
        private set
    var addCallCount = 0
        private set
    var lastSetDoneCall: SetDoneCall? = null
        private set
    var lastSetTitleCall: SetTitleCall? = null
        private set
    var deletedTodoId: String? = null
        private set

    private val emittedTodos = MutableStateFlow<Result<List<Todo>>>(Result.success(emptyList()))

    override val todos: Flow<Result<List<Todo>>> = emittedTodos

    override fun addTodo(title: String): Result<Unit> {
        addCallCount++
        addedTitle = title
        return addResult
    }

    override fun setDone(todoId: String, isDone: Boolean, completedBy: String?): Result<Unit> {
        lastSetDoneCall = SetDoneCall(todoId, isDone, completedBy)
        return setDoneResult
    }

    override fun setTitle(todoId: String, title: String): Result<Unit> {
        lastSetTitleCall = SetTitleCall(todoId, title)
        return setTitleResult
    }

    override fun deleteTodo(todoId: String): Result<Unit> {
        deletedTodoId = todoId
        return deleteResult
    }

    fun emit(result: Result<List<Todo>>) {
        emittedTodos.value = result
    }
}

private class FakeAuthRepository(override val currentUser: SignedInUser?) : AuthRepository {
    override val signedInUser: Flow<SignedInUser?> = MutableStateFlow(currentUser)

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<SignedInUser> =
        Result.failure(UnsupportedOperationException("not used in this test"))

    override fun signOut() = Unit
}
