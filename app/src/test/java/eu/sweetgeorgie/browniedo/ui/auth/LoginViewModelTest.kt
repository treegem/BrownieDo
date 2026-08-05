package eu.sweetgeorgie.browniedo.ui.auth

import eu.sweetgeorgie.browniedo.domain.auth.AuthRepository
import eu.sweetgeorgie.browniedo.domain.auth.SignedInUser
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = FakeAuthRepository()
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(authRepository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful google sign in leaves no error behind`() = runTest(testDispatcher) {
        authRepository.signInResult = Result.success(SIGNED_IN_USER)

        viewModel.signIn { GoogleIdTokenResult.Success(ID_TOKEN) }
        advanceUntilIdle()

        assertEquals(ID_TOKEN, authRepository.receivedIdToken)
        assertFalse(viewModel.uiState.value.isSigningIn)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `failing firebase sign in reports a sign in error`() = runTest(testDispatcher) {
        authRepository.signInResult = Result.failure(IllegalStateException("no network"))

        viewModel.signIn { GoogleIdTokenResult.Success(ID_TOKEN) }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSigningIn)
        assertEquals(LoginError.SIGN_IN_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `missing google account reports a dedicated error`() = runTest(testDispatcher) {
        viewModel.signIn { GoogleIdTokenResult.NoGoogleAccount }
        advanceUntilIdle()

        assertEquals(LoginError.NO_GOOGLE_ACCOUNT, viewModel.uiState.value.error)
    }

    @Test
    fun `cancelling the credential dialog reports no error`() = runTest(testDispatcher) {
        viewModel.signIn { GoogleIdTokenResult.Cancelled }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSigningIn)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `sign in shows a loading state until the token request completes`() =
        runTest(testDispatcher) {
            val pendingToken = CompletableDeferred<GoogleIdTokenResult>()
            authRepository.signInResult = Result.success(SIGNED_IN_USER)

            viewModel.signIn { pendingToken.await() }
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isSigningIn)

            pendingToken.complete(GoogleIdTokenResult.Success(ID_TOKEN))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isSigningIn)
        }

    @Test
    fun `a second sign in is ignored while one is still running`() = runTest(testDispatcher) {
        val pendingToken = CompletableDeferred<GoogleIdTokenResult>()
        authRepository.signInResult = Result.success(SIGNED_IN_USER)

        viewModel.signIn { pendingToken.await() }
        advanceUntilIdle()
        viewModel.signIn { GoogleIdTokenResult.Success("other-token") }
        advanceUntilIdle()

        pendingToken.complete(GoogleIdTokenResult.Success(ID_TOKEN))
        advanceUntilIdle()

        assertEquals(1, authRepository.signInCallCount)
        assertEquals(ID_TOKEN, authRepository.receivedIdToken)
    }

    @Test
    fun `dismissing an error clears it from the ui state`() = runTest(testDispatcher) {
        viewModel.signIn { GoogleIdTokenResult.NoGoogleAccount }
        advanceUntilIdle()

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    private companion object {
        const val ID_TOKEN = "google-id-token"
        val SIGNED_IN_USER = SignedInUser(uid = "uid-1", displayName = "Georg", email = null)
    }
}

private class FakeAuthRepository : AuthRepository {
    var signInResult: Result<SignedInUser> = Result.failure(IllegalStateException("not configured"))
    var receivedIdToken: String? = null
        private set
    var signInCallCount = 0
        private set

    override val currentUser: SignedInUser? = null

    override val signedInUser: Flow<SignedInUser?> = MutableStateFlow(null)

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<SignedInUser> {
        signInCallCount++
        receivedIdToken = idToken
        return signInResult
    }

    override fun signOut() = Unit
}
