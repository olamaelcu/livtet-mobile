package net.olamaelcu.livtet.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.olamaelcu.livtet.BuildConfig
import net.olamaelcu.livtet.core.auth.ProviderAccount
import net.olamaelcu.livtet.core.auth.provider.AuthProvider

private val GoogleBlue = Color(0xFF4285F4)
private val AtProtocolBlue = Color(0xFF1185FE)

@Composable
fun AccountScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSigningIn by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountEvent.SignInSucceeded -> {
                    isSigningIn = false
                    snackbarHostState.showSnackbar(
                        "Signed in with ${providerLabel(event.provider)}"
                    )
                }
                is AccountEvent.SignInFailed -> {
                    isSigningIn = false
                    snackbarHostState.showSnackbar(event.message)
                }
                is AccountEvent.SignOutComplete -> {
                    snackbarHostState.showSnackbar("Signed out of ${providerLabel(event.provider)}")
                }
                is AccountEvent.OpenAuthUrl -> {
                    isSigningIn = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    context.startActivity(intent)
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.isAnySignedIn) {
                item {
                    SignedOutContent(
                        isSigningIn = isSigningIn,
                        onGoogleClick = {
                            isSigningIn = true
                            viewModel.signIn(AuthProvider.Google)
                        },
                        onAppleClick = {
                            isSigningIn = true
                            viewModel.signIn(AuthProvider.Apple)
                        },
                        onAtprotoClick = { handle ->
                            isSigningIn = true
                            viewModel.signIn(AuthProvider.Atproto(did = "", handle = handle))
                        },
                    )
                }
            } else {
                item {
                    Text(
                        "Your Accounts",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(state.providers.size) { i ->
                    val (provider, account) = state.providers.entries.elementAt(i)
                    SignedInCard(account, onSignOut = { viewModel.signOut(provider) })
                }
                item {
                    SignedOutContent(
                        isSigningIn = isSigningIn,
                        compact = true,
                        onGoogleClick = {
                            isSigningIn = true
                            viewModel.signIn(AuthProvider.Google)
                        },
                        onAppleClick = {
                            isSigningIn = true
                            viewModel.signIn(AuthProvider.Apple)
                        },
                        onAtprotoClick = { handle ->
                            isSigningIn = true
                            viewModel.signIn(AuthProvider.Atproto(did = "", handle = handle))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedOutContent(
    isSigningIn: Boolean,
    compact: Boolean = false,
    onGoogleClick: () -> Unit = {},
    onAppleClick: () -> Unit = {},
    onAtprotoClick: (String) -> Unit = {},
) {
    var atprotoHandle by remember { mutableStateOf("") }

    if (!compact) {
        Text(
            "Sign in to unlock social features",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    if (BuildConfig.GOOGLE_SIGN_IN_ENABLED) {
        ProviderButton("Continue with Google", GoogleBlue, isSigningIn, onGoogleClick)
        Spacer(Modifier.height(8.dp))
    }

    if (BuildConfig.APPLE_SIGN_IN_ENABLED) {
        ProviderButton("Continue with Apple", Color.Black, isSigningIn, onAppleClick)
        Spacer(Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = atprotoHandle,
        onValueChange = { atprotoHandle = it },
        label = { Text("ATProto handle") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isSigningIn,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Uri,
        ),
    )
    Spacer(Modifier.height(4.dp))
    ProviderButton(
        "Sign in with AT Protocol",
        AtProtocolBlue,
        isSigningIn,
        { onAtprotoClick(atprotoHandle) },
    )
}

@Composable
private fun ProviderButton(
    label: String,
    backgroundColor: Color,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        enabled = !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label, color = Color.White)
        }
    }
}

@Composable
private fun SignedInCard(account: ProviderAccount, onSignOut: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(providerLabel(account.provider), fontWeight = FontWeight.Medium)
            Text(account.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val email = account.email
            if (email != null) {
                Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val dateStr =
                remember(account.signedInAt) {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        .format(Date(account.signedInAt))
                }
            Text("Signed in since $dateStr", color = MaterialTheme.colorScheme.outline)
            TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.End)) {
                Text("Sign out")
            }
        }
    }
}

private fun providerLabel(provider: AuthProvider): String =
    when (provider) {
        AuthProvider.Google -> "Google"
        AuthProvider.Apple -> "Apple"
        is AuthProvider.Atproto -> "AT Protocol"
    }
