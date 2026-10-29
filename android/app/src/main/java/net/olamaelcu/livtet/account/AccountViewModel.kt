package net.olamaelcu.livtet.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import net.olamaelcu.livtet.core.auth.provider.AuthProvider
import net.olamaelcu.livtet.AtprotoAuthRedirectHandler
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountViewModel
@Inject
constructor(
    private val accountManager: AccountManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState(emptyMap()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>()
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch { accountManager.accountState.collect { s -> _state.value = s } }
        // Listen for ATProto OAuth redirects from the Activity
        viewModelScope.launch {
            AtprotoAuthRedirectHandler.redirects.collect { uri ->
                completeLogin(uri)
            }
        }
    }

    fun signIn(provider: AuthProvider) {
        viewModelScope.launch {
            try {
                Timber.d("AccountViewModel.signIn: $provider")
                val authUrl = accountManager.signIn(provider)
                if (authUrl != null) {
                    // ATProto — emit event to open Custom Tab
                    _events.emit(AccountEvent.OpenAuthUrl(authUrl))
                } else {
                    _events.emit(AccountEvent.SignInSucceeded(provider))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "sign-in failed")
                _events.emit(AccountEvent.SignInFailed(provider, "Sign-in failed. Try again."))
            }
        }
    }

    fun completeLogin(redirectUri: String) {
        viewModelScope.launch {
            try {
                Timber.d("AccountViewModel.completeLogin: $redirectUri")
                accountManager.completeAtprotoLogin(redirectUri)
                _events.emit(AccountEvent.SignInSucceeded(AuthProvider.Atproto(did = "", handle = "")))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "ATProto login completion failed")
                _events.emit(
                    AccountEvent.SignInFailed(
                        AuthProvider.Atproto(did = "", handle = ""),
                        "ATProto sign-in failed. Try again.",
                    )
                )
            }
        }
    }

    fun signOut(provider: AuthProvider) {
        viewModelScope.launch {
            accountManager.signOut(provider)
            _events.emit(AccountEvent.SignOutComplete(provider))
        }
    }
}

sealed interface AccountEvent {
    data class SignInSucceeded(val provider: AuthProvider) : AccountEvent

    data class SignInFailed(val provider: AuthProvider, val message: String) : AccountEvent

    data class SignOutComplete(val provider: AuthProvider) : AccountEvent

    data class OpenAuthUrl(val url: String) : AccountEvent
}
