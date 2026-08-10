package eu.sweetgeorgie.browniedo

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import eu.sweetgeorgie.browniedo.data.auth.FirebaseAuthRepository
import eu.sweetgeorgie.browniedo.data.list.DataStoreSelectedListRepository
import eu.sweetgeorgie.browniedo.data.list.FirestoreListRepository
import eu.sweetgeorgie.browniedo.data.list.selectedListDataStoreOf
import eu.sweetgeorgie.browniedo.data.todo.FirestoreTodoRepository
import eu.sweetgeorgie.browniedo.data.user.FirestorePartnerRepository
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.list.ListRepository
import eu.sweetgeorgie.browniedo.domain.list.SelectedListRepository
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import eu.sweetgeorgie.browniedo.domain.user.PartnerRepository
import eu.sweetgeorgie.browniedo.ui.auth.GoogleIdTokenRequester
import eu.sweetgeorgie.browniedo.ui.auth.LoginViewModel
import eu.sweetgeorgie.browniedo.ui.todo.TodoListViewModel

/** Manual dependency graph — the app is small enough that a DI framework would only add overhead. */
class AppContainer(applicationContext: Context) {

    val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())

    private val todoRepository: TodoRepository =
        FirestoreTodoRepository(FirebaseFirestore.getInstance())

    private val partnerRepository: PartnerRepository =
        FirestorePartnerRepository(FirebaseFirestore.getInstance(), authRepository)

    private val listRepository: ListRepository = FirestoreListRepository(
        firestore = FirebaseFirestore.getInstance(),
        authRepository = authRepository,
        partnerRepository = partnerRepository
    )

    private val selectedListRepository: SelectedListRepository =
        DataStoreSelectedListRepository(selectedListDataStoreOf(applicationContext))

    val googleIdTokenRequester = GoogleIdTokenRequester(CredentialManager.create(applicationContext))

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { LoginViewModel(authRepository) }
        initializer {
            TodoListViewModel(
                todoRepository = todoRepository,
                listRepository = listRepository,
                selectedListRepository = selectedListRepository,
                partnerRepository = partnerRepository,
                authRepository = authRepository
            )
        }
    }
}
