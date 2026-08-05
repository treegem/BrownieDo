package eu.sweetgeorgie.browniedo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.sweetgeorgie.browniedo.ui.auth.LoginScreen
import eu.sweetgeorgie.browniedo.ui.auth.LoginViewModel
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import eu.sweetgeorgie.browniedo.ui.todo.TodoListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as BrownieDoApplication).appContainer
        setContent {
            BrownieDoTheme {
                val signedInUser by appContainer.authRepository.signedInUser
                    .collectAsStateWithLifecycle(initialValue = appContainer.authRepository.currentUser)

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding)
                    val user = signedInUser
                    if (user == null) {
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
                            },
                            modifier = contentModifier
                        )
                    } else {
                        TodoListScreen(
                            signedInUserLabel = user.displayName ?: user.email ?: user.uid,
                            onSignOutClick = { appContainer.authRepository.signOut() },
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }
}
