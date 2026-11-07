package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.olamaelcu.livtet.core.designsystem.LivtetTheme

/**
 * The reader's feed, as the platform serves it.
 *
 * The platform route takes either a browser cookie or a bearer token; this app has no cookie, so
 * the screen exists only when a sign-in succeeded. Signed out it says so and offers the sign-in,
 * rather than showing an empty list that looks like a feed with nothing in it.
 */
class FeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) { FeedRoute(onBack = { finish() }) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, FeedActivity::class.java)
    }
}

private sealed interface FeedState {
    data object SignedOut : FeedState

    data object Loading : FeedState

    data class Ready(val entries: List<FeedEntry>) : FeedState

    data class Failed(val message: String) : FeedState
}

@Composable
private fun FeedRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var state: FeedState by remember {
        mutableStateOf(
            if (PlatformAuth.isSignedIn(context)) FeedState.Loading else FeedState.SignedOut
        )
    }
    var reloads by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloads) {
        if (!PlatformAuth.isSignedIn(context)) {
            state = FeedState.SignedOut
            return@LaunchedEffect
        }
        state = FeedState.Loading
        state =
            FeedClient.page(context, limit = 30)
                .fold(
                    onSuccess = { page ->
                        if (page.entries.isEmpty()) {
                            FeedState.Ready(emptyList())
                        } else {
                            FeedState.Ready(page.entries)
                        }
                    },
                    onFailure = { error ->
                        if (
                            error is FeedClient.FeedError &&
                                error.kind == FeedClient.FeedError.Kind.SESSION_EXPIRED
                        ) {
                            FeedState.SignedOut
                        } else {
                            FeedState.Failed(error.message ?: error.toString())
                        }
                    },
                )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("\u2039  Back") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Feed", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                if (PlatformAuth.isSignedIn(context)) {
                    TextButton(
                        onClick = {
                            PlatformAuth.signOut(context)
                            reloads++
                        }
                    ) {
                        Text("Sign out")
                    }
                } else {
                    Spacer(modifier = Modifier.padding(end = 8.dp))
                }
            }

            when (val current = state) {
                FeedState.SignedOut ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Read what the people you follow are reading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text =
                                "Signing in asks the platform for your feed only \u2014 no library access, no reading position.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SignInButton(context)
                    }

                FeedState.Loading ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }

                is FeedState.Failed ->
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { reloads++ }) { Text("Try again") }
                    }

                is FeedState.Ready ->
                    if (current.entries.isEmpty()) {
                        Text(
                            text =
                                "Nothing here yet. Follow a reader on the platform to fill this.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(current.entries) { entry ->
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(
                                        text = entry.summary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    entry.text?.let {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color =
                                                MaterialTheme.colorScheme.onBackground.copy(
                                                    alpha = 0.7f
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

/**
 * Start the browser sign-in.
 *
 * The authorize page belongs in a browser, not a webview: it is the platform's own login, and a
 * webview would have no cookie to give it. The redirect comes back through the `livtet://` filter
 * on [MainActivity].
 */
@Composable
internal fun SignInButton(context: Context) {
    TextButton(
        onClick = {
            // The pending verifier is created here, not during composition: each
            // recomposition would otherwise replace the one the callback needs.
            val signIn = PlatformAuth.beginSignIn(context)
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(signIn.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    ) {
        Text("Sign in to Livtet")
    }
}
