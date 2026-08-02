package net.olamaelcu.livtet.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.auth.AccountManager
import net.olamaelcu.livtet.core.auth.AccountState
import net.olamaelcu.livtet.core.auth.provider.AppleAuthProvider
import net.olamaelcu.livtet.core.auth.provider.AuthProvider
import net.olamaelcu.livtet.core.auth.provider.GoogleAuthProvider
import timber.log.Timber

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AccountState(emptyMap()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>()
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    init {
        AccountManager.init(application)
        viewModelScope.launch { AccountManager.accountState.collect { s -> _state.value = s } }
    }

    fun signIn(provider: AuthProvider) {
        viewModelScope.launch {
            try {
                Timber.d("AccountViewModel.signIn: $provider")
                AccountManager.signIn(getApplication(), provider)
                _events.emit(AccountEvent.SignInSucceeded(provider))
            } catch (e: AppleAuthProvider.AppleAuthException) {
                Timber.w(e, "Apple sign-in failed")
                _events.emit(
                    AccountEvent.SignInFailed(
                        provider,
                        e.message ?: "Apple sign-in is not available",
                    )
                )
            } catch (e: GoogleAuthProvider.GoogleAuthException) {
                Timber.w(e, "Google sign-in failed")
                _events.emit(
                    AccountEvent.SignInFailed(provider, "Could not sign in with Google. Try again.")
                )
            } catch (e: UnsupportedOperationException) {
                Timber.w(e, "ATProto requires OAuth flow")
                _events.emit(
                    AccountEvent.SignInFailed(provider, "ATProto sign-in requires the OAuth flow")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "sign-in failed")
                _events.emit(AccountEvent.SignInFailed(provider, "Sign-in failed. Try again."))
            }
        }
    }

    fun signOut(provider: AuthProvider) {
        viewModelScope.launch {
            AccountManager.signOut(getApplication(), provider)
            _events.emit(AccountEvent.SignOutComplete(provider))
        }
    }
}

sealed interface AccountEvent {
    data class SignInSucceeded(val provider: AuthProvider) : AccountEvent

    data class SignInFailed(val provider: AuthProvider, val message: String) : AccountEvent

    data class SignOutComplete(val provider: AuthProvider) : AccountEvent
}
