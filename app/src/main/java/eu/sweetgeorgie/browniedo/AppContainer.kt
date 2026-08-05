package eu.sweetgeorgie.browniedo

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import eu.sweetgeorgie.browniedo.data.auth.FirebaseAuthRepository
import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.ui.auth.GoogleIdTokenRequester
import eu.sweetgeorgie.browniedo.ui.auth.LoginViewModel

/** Manual dependency graph — the app is small enough that a DI framework would only add overhead. */
class AppContainer(applicationContext: Context) {

    val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())

    val googleIdTokenRequester = GoogleIdTokenRequester(CredentialManager.create(applicationContext))

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { LoginViewModel(authRepository) }
    }
}
