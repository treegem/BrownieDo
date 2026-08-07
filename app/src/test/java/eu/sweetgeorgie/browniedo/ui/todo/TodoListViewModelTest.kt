package eu.sweetgeorgie.browniedo.ui.todo

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
    private lateinit var viewModel: TodoListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TodoListViewModel(todoRepository)
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

    private companion object {
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

private class FakeTodoRepository : TodoRepository {
    var addResult: Result<Unit> = Result.success(Unit)
    var addedTitle: String? = null
        private set
    var addCallCount = 0
        private set

    private val emittedTodos = MutableStateFlow<Result<List<Todo>>>(Result.success(emptyList()))

    override val todos: Flow<Result<List<Todo>>> = emittedTodos

    override fun addTodo(title: String): Result<Unit> {
        addCallCount++
        addedTitle = title
        return addResult
    }

    fun emit(result: Result<List<Todo>>) {
        emittedTodos.value = result
    }
}
