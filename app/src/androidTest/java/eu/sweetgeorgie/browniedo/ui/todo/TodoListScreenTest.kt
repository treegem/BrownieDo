package eu.sweetgeorgie.browniedo.ui.todo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Prüft, was [TodoListScreen] anstelle der Liste anzeigt (Fehler, Laden, Leerzustand) und dass die
 * Wischgeste nur erledigte Aufgaben löscht. Der Bildschirm ist zustandslos, der Test kommt daher
 * ohne Firebase und ohne Anmeldung aus.
 */
@RunWith(AndroidJUnit4::class)
class TodoListScreenTest {

    /**
     * Die Verfallswarnung verweist auf `androidx.compose.ui.test.junit4.v2` — und genau dieser
     * Import hat hier schon einmal **alle** Tests reproduzierbar an „No compose hierarchies found"
     * scheitern lassen, ohne dass es jemandem auffiel. `v2` tauscht nicht nur den Dispatcher,
     * sondern die ganze `AndroidComposeUiTestEnvironment`: Die Komposition wird eingereiht statt
     * sofort ausgeführt, Tests müssen danach von sich aus synchronisieren.
     *
     * Der Wechsel ist deshalb kein Import-Austausch, sondern eine Verhaltensänderung, die sich nur
     * **auf einem Gerät** belegen lässt — und der Fehlschlag sieht aus wie ein grüner Build. Bis
     * ein Gerät dafür da ist, bleibt die alte Fassung stehen und die Warnung unterdrückt; der
     * Punkt steht in `ROADMAP.md` unter „Querlaufend".
     */
    @Suppress("DEPRECATION")
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aWriteErrorIsShownInASnackbarAndClearedAfterwards() {
        var errorShownCount = 0
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, error = TodoListError.ADD_FAILED),
            onErrorShown = { errorShownCount++ }
        )

        val message = composeTestRule.activity.getString(R.string.todo_list_error_add_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        // Die Snackbar verschwindet von selbst, erst danach wird der Fehler gelöscht.
        composeTestRule.waitUntil(timeoutMillis = DISMISS_TIMEOUT_MILLIS) { errorShownCount == 1 }
    }

    @Test
    fun aLoadErrorStaysVisibleAndIsNotCleared() {
        var errorShownCount = 0
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, error = TodoListError.LOAD_FAILED),
            onErrorShown = { errorShownCount++ }
        )

        val message = composeTestRule.activity.getString(R.string.todo_list_error_load_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        composeTestRule.waitForIdle()
        assertEquals(0, errorShownCount)
    }

    @Test
    fun theProgressIndicatorIsShownWhileTheListIsLoading() {
        setScreenContent(uiState = TodoListUiState(selectedList = LIST, isLoading = true))

        val label = composeTestRule.activity.getString(R.string.todo_list_loading)
        composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }

    @Test
    fun anEmptyListInvitesTheUserToAddTheFirstEntry() {
        setScreenContent(uiState = TodoListUiState(selectedList = LIST, isLoading = false))

        val headline = composeTestRule.activity.getString(R.string.todo_list_empty_headline)
        val hint = composeTestRule.activity.getString(R.string.todo_list_empty_hint)
        composeTestRule.onNodeWithText(headline).assertIsDisplayed()
        composeTestRule.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun aLoadErrorIsShownInsteadOfTheEmptyState() {
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, error = TodoListError.LOAD_FAILED)
        )

        val message = composeTestRule.activity.getString(R.string.todo_list_error_load_failed)
        val headline = composeTestRule.activity.getString(R.string.todo_list_empty_headline)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithText(headline).assertDoesNotExist()
    }

    @Test
    fun aFinishedEntrySwipedToTheRightIsDeleted() {
        var swipedAway: Todo? = null
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, todos = TODOS),
            onTodoSwipedAway = { swipedAway = it }
        )

        composeTestRule.onNodeWithText(FINISHED_TODO.title).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertEquals(FINISHED_TODO.id, swipedAway?.id)
    }

    @Test
    fun anEntryThatIsStillOpenCannotBeSwipedAway() {
        var swipedAway: Todo? = null
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, todos = TODOS),
            onTodoSwipedAway = { swipedAway = it }
        )

        composeTestRule.onNodeWithText(OPEN_TODO.title).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertNull(swipedAway)
        // Die Zeile muss stehen bleiben, nicht nur den Rückruf unterlassen.
        composeTestRule.onNodeWithText(OPEN_TODO.title).assertIsDisplayed()
    }

    @Test
    fun anUrgentEntryCarriesAMarker() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(URGENT_TODO)
            )
        )

        composeTestRule.onNodeWithContentDescription(priorityDescription(R.string.todo_list_priority_high))
            .assertIsDisplayed()
    }

    @Test
    fun anEntryOfMiddlingPriorityCarriesNoMarker() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(OPEN_TODO)
            )
        )

        // „mittel" ist der Normalfall und bekommt bewusst kein Zeichen.
        composeTestRule.onNodeWithContentDescription(priorityDescription(R.string.todo_list_priority_medium))
            .assertDoesNotExist()
    }

    @Test
    fun theEditDialogPreselectsTheCurrentPriority() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(URGENT_TODO),
                editedTodo = TodoEdit(
                    todoId = URGENT_TODO.id,
                    title = URGENT_TODO.title,
                    priority = TodoPriority.HIGH,
                    targetListId = LIST.id
                )
            )
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_priority_high)
        composeTestRule.onNodeWithText(label).assertIsSelected()
    }

    @Test
    fun pickingAPriorityInTheEditDialogReportsIt() {
        var picked: TodoPriority? = null
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(OPEN_TODO),
                editedTodo = TodoEdit(
                    todoId = OPEN_TODO.id,
                    title = OPEN_TODO.title,
                    priority = TodoPriority.MEDIUM,
                    targetListId = LIST.id
                )
            ),
            onEditedPriorityChange = { picked = it }
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_priority_low)
        composeTestRule.onNodeWithText(label).performClick()

        assertEquals(TodoPriority.LOW, picked)
    }

    @Test
    fun theEditDialogShowsTheListTheEntryIsIn() {
        setScreenContent(uiState = editingIn(LIST, lists = listOf(LIST, OTHER_LIST)))

        composeTestRule.onNodeWithText(LIST.name).assertIsDisplayed()
    }

    @Test
    fun pickingAnotherListInTheEditDialogReportsIt() {
        var picked: TodoList? = null
        setScreenContent(
            uiState = editingIn(LIST, lists = listOf(LIST, OTHER_LIST)),
            onEditedTargetListChange = { picked = it }
        )

        val chooseLabel = composeTestRule.activity.getString(R.string.todo_list_choose_target_list)
        composeTestRule.onNodeWithContentDescription(chooseLabel).performClick()
        // Gegen den Namen der *anderen* Liste prüfen: Der Name der aktuellen steht bei offenem
        // Menü zweimal auf dem Bildschirm — im Feld und im Menü — und wäre mehrdeutig.
        composeTestRule.onNodeWithText(OTHER_LIST.name).performClick()

        assertEquals(OTHER_LIST, picked)
    }

    @Test
    fun theListCannotBePickedWhileItIsTheOnlyOne() {
        setScreenContent(uiState = editingIn(LIST, lists = listOf(LIST)))

        val chooseLabel = composeTestRule.activity.getString(R.string.todo_list_choose_target_list)
        composeTestRule.onNodeWithContentDescription(chooseLabel).performClick()

        // Das Menü bleibt zu: Das Symbol für die geteilte Liste gibt es nur im Menüeintrag.
        val sharedLabel = composeTestRule.activity.getString(R.string.todo_list_shared_list)
        composeTestRule.onNodeWithContentDescription(sharedLabel).assertDoesNotExist()
    }

    @Test
    fun aSuccessfulMoveIsConfirmedInASnackbar() {
        var shownCount = 0
        setScreenContent(
            uiState = TodoListUiState(
                lists = listOf(LIST, OTHER_LIST),
                selectedList = LIST,
                isLoading = false,
                movedToListName = OTHER_LIST.name
            ),
            onMovedMessageShown = { shownCount++ }
        )

        val message = composeTestRule.activity
            .getString(R.string.todo_list_moved_to, OTHER_LIST.name)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = DISMISS_TIMEOUT_MILLIS) { shownCount == 1 }
    }

    private fun editingIn(list: TodoList, lists: List<TodoList>) = TodoListUiState(
        lists = lists,
        selectedList = list,
        isLoading = false,
        todos = listOf(OPEN_TODO),
        editedTodo = TodoEdit(
            todoId = OPEN_TODO.id,
            title = OPEN_TODO.title,
            priority = TodoPriority.MEDIUM,
            targetListId = list.id
        )
    )

    private fun priorityDescription(labelResId: Int): String =
        composeTestRule.activity.getString(
            R.string.todo_list_priority_content_description,
            composeTestRule.activity.getString(labelResId)
        )

    private fun setScreenContent(
        uiState: TodoListUiState,
        onErrorShown: () -> Unit = {},
        onTodoSwipedAway: (Todo) -> Unit = {},
        onEditedPriorityChange: (TodoPriority) -> Unit = {},
        onEditedTargetListChange: (TodoList) -> Unit = {},
        onMovedMessageShown: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            BrownieDoTheme {
                TodoListScreen(
                    uiState = uiState,
                    onListSelected = {},
                    onNewListClick = {},
                    onNewListNameChange = {},
                    onNewListSharedChange = {},
                    onNewListConfirm = {},
                    onNewListDismiss = {},
                    onRenameListClick = {},
                    onRenamedListNameChange = {},
                    onRenameListConfirm = {},
                    onRenameListDismiss = {},
                    onDeleteListClick = {},
                    onDeleteListConfirm = {},
                    onDeleteListDismiss = {},
                    onNewTodoTitleChange = {},
                    onAddTodoClick = {},
                    onTodoDoneChange = { _, _ -> },
                    onTodoSwipedAway = onTodoSwipedAway,
                    onEditTodoClick = {},
                    onEditedTitleChange = {},
                    onEditedPriorityChange = onEditedPriorityChange,
                    onEditedTargetListChange = onEditedTargetListChange,
                    onEditConfirm = {},
                    onDeleteTodoClick = {},
                    onEditDismiss = {},
                    onErrorShown = onErrorShown,
                    onMovedMessageShown = onMovedMessageShown,
                    onSignOutClick = {}
                )
            }
        }
    }

    private companion object {
        const val DISMISS_TIMEOUT_MILLIS = 10_000L

        val LIST = TodoList(id = "list-1", name = "Einkauf", isShared = true)

        val OTHER_LIST = TodoList(id = "list-2", name = "Zuhause", isShared = false)

        val TIMESTAMP: Instant = Instant.parse("2026-08-07T20:00:00Z")

        val OPEN_TODO = Todo(
            id = "todo-open",
            title = "Milch kaufen",
            isDone = false,
            priority = TodoPriority.MEDIUM,
            createdAt = TIMESTAMP,
            updatedAt = TIMESTAMP,
            completedBy = null,
            completedAt = null
        )

        val FINISHED_TODO = OPEN_TODO.copy(
            id = "todo-finished",
            title = "Kaffee kaufen",
            isDone = true,
            completedBy = "uid-1",
            completedAt = TIMESTAMP
        )

        val URGENT_TODO = OPEN_TODO.copy(
            id = "todo-urgent",
            title = "Geschenk besorgen",
            priority = TodoPriority.HIGH
        )

        val TODOS = listOf(OPEN_TODO, FINISHED_TODO)
    }
}
