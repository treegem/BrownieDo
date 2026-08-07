package eu.sweetgeorgie.browniedo.ui.todo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prüft die zweigeteilte Fehleranzeige aus [TodoListScreen]. Der Bildschirm ist zustandslos, der
 * Test kommt daher ohne Firebase und ohne Anmeldung aus.
 */
@RunWith(AndroidJUnit4::class)
class TodoListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aWriteErrorIsShownInASnackbarAndClearedAfterwards() {
        var errorShownCount = 0
        setScreenContent(TodoListError.ADD_FAILED) { errorShownCount++ }

        val message = composeTestRule.activity.getString(R.string.todo_list_error_add_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        // Die Snackbar verschwindet von selbst, erst danach wird der Fehler gelöscht.
        composeTestRule.waitUntil(timeoutMillis = DISMISS_TIMEOUT_MILLIS) { errorShownCount == 1 }
    }

    @Test
    fun aLoadErrorStaysVisibleAndIsNotCleared() {
        var errorShownCount = 0
        setScreenContent(TodoListError.LOAD_FAILED) { errorShownCount++ }

        val message = composeTestRule.activity.getString(R.string.todo_list_error_load_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        composeTestRule.waitForIdle()
        assertEquals(0, errorShownCount)
    }

    private fun setScreenContent(error: TodoListError, onErrorShown: () -> Unit) {
        composeTestRule.setContent {
            BrownieDoTheme(dynamicColor = false) {
                TodoListScreen(
                    uiState = TodoListUiState(isLoading = false, error = error),
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
