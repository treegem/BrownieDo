package eu.sweetgeorgie.browniedo.ui.todo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prüft, was [TodoListScreen] anstelle der Liste anzeigt: die zweigeteilte Fehleranzeige sowie den
 * Lade- und den Leerzustand. Der Bildschirm ist zustandslos, der Test kommt daher ohne Firebase und
 * ohne Anmeldung aus.
 */
@RunWith(AndroidJUnit4::class)
class TodoListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aWriteErrorIsShownInASnackbarAndClearedAfterwards() {
        var errorShownCount = 0
        setScreenContent(
            TodoListUiState(isLoading = false, error = TodoListError.ADD_FAILED)
        ) { errorShownCount++ }

        val message = composeTestRule.activity.getString(R.string.todo_list_error_add_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        // Die Snackbar verschwindet von selbst, erst danach wird der Fehler gelöscht.
        composeTestRule.waitUntil(timeoutMillis = DISMISS_TIMEOUT_MILLIS) { errorShownCount == 1 }
    }

    @Test
    fun aLoadErrorStaysVisibleAndIsNotCleared() {
        var errorShownCount = 0
        setScreenContent(
            TodoListUiState(isLoading = false, error = TodoListError.LOAD_FAILED)
        ) { errorShownCount++ }

        val message = composeTestRule.activity.getString(R.string.todo_list_error_load_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        composeTestRule.waitForIdle()
        assertEquals(0, errorShownCount)
    }

    @Test
    fun theProgressIndicatorIsShownWhileTheListIsLoading() {
        setScreenContent(TodoListUiState(isLoading = true))

        val label = composeTestRule.activity.getString(R.string.todo_list_loading)
        composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }

    @Test
    fun anEmptyListInvitesTheUserToAddTheFirstEntry() {
        setScreenContent(TodoListUiState(isLoading = false))

        val headline = composeTestRule.activity.getString(R.string.todo_list_empty_headline)
        val hint = composeTestRule.activity.getString(R.string.todo_list_empty_hint)
        composeTestRule.onNodeWithText(headline).assertIsDisplayed()
        composeTestRule.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun aLoadErrorIsShownInsteadOfTheEmptyState() {
        setScreenContent(TodoListUiState(isLoading = false, error = TodoListError.LOAD_FAILED))

        val message = composeTestRule.activity.getString(R.string.todo_list_error_load_failed)
        val headline = composeTestRule.activity.getString(R.string.todo_list_empty_headline)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithText(headline).assertDoesNotExist()
    }

    private fun setScreenContent(uiState: TodoListUiState, onErrorShown: () -> Unit = {}) {
        composeTestRule.setContent {
            BrownieDoTheme(dynamicColor = false) {
                TodoListScreen(
                    uiState = uiState,
                    onNewTodoTitleChange = {},
                    onAddTodoClick = {},
                    onTodoDoneChange = { _, _ -> },
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
    }
}
