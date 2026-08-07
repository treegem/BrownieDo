package eu.sweetgeorgie.browniedo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.sweetgeorgie.browniedo.ui.auth.LoginScreen
import eu.sweetgeorgie.browniedo.ui.auth.LoginViewModel
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import eu.sweetgeorgie.browniedo.ui.todo.TodoListScreen
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
                    TodoListScreen(
                        uiState = todoListUiState,
                        onNewTodoTitleChange = todoListViewModel::onNewTodoTitleChange,
                        onAddTodoClick = todoListViewModel::addTodo,
                        onTodoDoneChange = todoListViewModel::onTodoDoneChange,
                        onEditTodoClick = todoListViewModel::onEditTodoClick,
                        onEditedTitleChange = todoListViewModel::onEditedTitleChange,
                        onEditConfirm = todoListViewModel::onEditConfirm,
                        onDeleteTodoClick = todoListViewModel::onDeleteTodoClick,
                        onEditDismiss = todoListViewModel::onEditDismiss,
                        onErrorShown = todoListViewModel::onErrorShown,
                        onSignOutClick = { appContainer.authRepository.signOut() }
                    )
                }
            }
        }
    }
}
