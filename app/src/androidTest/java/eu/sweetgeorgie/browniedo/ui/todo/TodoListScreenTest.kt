package eu.sweetgeorgie.browniedo.ui.todo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
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

    private fun setScreenContent(
        uiState: TodoListUiState,
        onErrorShown: () -> Unit = {},
        onTodoSwipedAway: (Todo) -> Unit = {}
    ) {
        composeTestRule.setContent {
            BrownieDoTheme(dynamicColor = false) {
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
                    onEditConfirm = {},
                    onDeleteTodoClick = {},
                    onEditDismiss = {},
                    onErrorShown = onErrorShown,
                    onSignOutClick = {}
                )
            }
        }
    }

    private companion object {
        const val DISMISS_TIMEOUT_MILLIS = 10_000L

        val LIST = TodoList(id = "list-1", name = "Einkauf", isShared = true)

        val TIMESTAMP: Instant = Instant.parse("2026-08-07T20:00:00Z")

        val OPEN_TODO = Todo(
            id = "todo-open",
            title = "Milch kaufen",
            isDone = false,
            createdAt = TIMESTAMP,
            updatedAt = TIMESTAMP,
            completedBy = null
        )

        val FINISHED_TODO = OPEN_TODO.copy(
            id = "todo-finished",
            title = "Kaffee kaufen",
            isDone = true,
            completedBy = "uid-1"
        )

        val TODOS = listOf(OPEN_TODO, FINISHED_TODO)
    }
}
