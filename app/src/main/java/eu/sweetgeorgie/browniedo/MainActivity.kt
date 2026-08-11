package eu.sweetgeorgie.browniedo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.sweetgeorgie.browniedo.ui.auth.LoginScreen
import eu.sweetgeorgie.browniedo.ui.auth.LoginViewModel
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import eu.sweetgeorgie.browniedo.ui.todo.ListDialogActions
import eu.sweetgeorgie.browniedo.ui.todo.TodoActions
import eu.sweetgeorgie.browniedo.ui.todo.TodoEditActions
import eu.sweetgeorgie.browniedo.ui.todo.TodoListScreen
import eu.sweetgeorgie.browniedo.ui.todo.TodoListTopBarActions
import eu.sweetgeorgie.browniedo.ui.todo.TodoListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as BrownieDoApplication).appContainer
        setContent {
            BrownieDoTheme {
                val signedInUser by appContainer.authRepository.signedInUser
                    .collectAsStateWithLifecycle(initialValue = appContainer.authRepository.currentUser)

                // Jeder Bildschirm bringt sein eigenes Scaffold mit, weil dessen Leisten den
                // jeweiligen UI-State lesen — siehe ADR 0012.
                if (signedInUser == null) {
                    val loginViewModel: LoginViewModel =
                        viewModel(factory = appContainer.viewModelFactory)
                    val loginUiState by loginViewModel.uiState.collectAsStateWithLifecycle()
                    LoginScreen(
                        uiState = loginUiState,
                        onSignInClick = { activityContext, serverClientId ->
                            loginViewModel.signIn {
                                appContainer.googleIdTokenRequester
                                    .requestIdToken(activityContext, serverClientId)
                            }
                        }
                    )
                } else {
                    val todoListViewModel: TodoListViewModel =
                        viewModel(factory = appContainer.viewModelFactory)
                    val todoListUiState by todoListViewModel.uiState
                        .collectAsStateWithLifecycle()
                    // Die vier Halter werden gemerkt, statt bei jeder Rekomposition neu zu
                    // entstehen: onSignOutClick ist ein Lambda-Literal, das appContainer einfängt,
                    // und wäre sonst jedes Mal ein anderes Objekt. Zusammen mit @Immutable und der
                    // strukturellen equals der data class bleibt der Bildschirm damit
                    // überspringbar, siehe ADR 0028.
                    val topBarActions = remember(todoListViewModel) {
                        TodoListTopBarActions(
                            onListSelected = todoListViewModel::onListSelected,
                            onNewListClick = todoListViewModel::onNewListClick,
                            onRenameListClick = todoListViewModel::onRenameListClick,
                            onDeleteListClick = todoListViewModel::onDeleteListClick,
                            onSignOutClick = { appContainer.authRepository.signOut() }
                        )
                    }
                    val listDialogActions = remember(todoListViewModel) {
                        ListDialogActions(
                            onNewListNameChange = todoListViewModel::onNewListNameChange,
                            onNewListSharedChange = todoListViewModel::onNewListSharedChange,
                            onNewListConfirm = todoListViewModel::onNewListConfirm,
                            onNewListDismiss = todoListViewModel::onNewListDismiss,
                            onRenamedListNameChange = todoListViewModel::onRenamedListNameChange,
                            onRenameListConfirm = todoListViewModel::onRenameListConfirm,
                            onRenameListDismiss = todoListViewModel::onRenameListDismiss,
                            onDeleteListConfirm = todoListViewModel::onDeleteListConfirm,
                            onDeleteListDismiss = todoListViewModel::onDeleteListDismiss
                        )
                    }
                    val todoActions = remember(todoListViewModel) {
                        TodoActions(
                            onNewTodoTitleChange = todoListViewModel::onNewTodoTitleChange,
                            onAddTodoClick = todoListViewModel::addTodo,
                            onTodoDoneChange = todoListViewModel::onTodoDoneChange,
                            onTodoSwipedAway = todoListViewModel::onTodoSwipedAway,
                            onEditTodoClick = todoListViewModel::onEditTodoClick
                        )
                    }
                    val editActions = remember(todoListViewModel) {
                        TodoEditActions(
                            onTitleChange = todoListViewModel::onEditedTitleChange,
                            onPriorityChange = todoListViewModel::onEditedPriorityChange,
                            onTargetListChange = todoListViewModel::onEditedTargetListChange,
                            onConfirm = todoListViewModel::onEditConfirm,
                            onDelete = todoListViewModel::onDeleteTodoClick,
                            onDismiss = todoListViewModel::onEditDismiss
                        )
                    }

                    TodoListScreen(
                        uiState = todoListUiState,
                        topBarActions = topBarActions,
                        listDialogActions = listDialogActions,
                        todoActions = todoActions,
                        editActions = editActions,
                        onErrorShown = todoListViewModel::onErrorShown,
                        onMovedMessageShown = todoListViewModel::onMovedMessageShown
                    )
                }
            }
        }
    }
}
