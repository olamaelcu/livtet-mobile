package net.olamaelcu.livtet

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import net.olamaelcu.livtet.branding.LivtetColors
import net.olamaelcu.livtet.core.designsystem.LivtetTheme

/**
 * Sole activity. Initialises the Rust core through [Bridge] and renders the barebone launcher:
 * brand mark, app name, then a placeholder line once the store is open.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) {
                LauncherScreen(databasePath = File(filesDir, "livtet.db").absolutePath)
            }
        }
    }
}

private sealed interface LauncherPhase {
    data object Loading : LauncherPhase

    data object Ready : LauncherPhase

    data class Failed(val message: String) : LauncherPhase
}

@Composable
private fun LauncherScreen(databasePath: String) {
    val context = LocalContext.current
    var phase: LauncherPhase by remember { mutableStateOf(LauncherPhase.Loading) }
    var readable: List<Book> by remember { mutableStateOf(emptyList()) }
    // What is waiting on a decision, so the inbox is one tap away instead of a
    // screen the reader has to remember to visit.
    var offersWaiting: Int by remember { mutableStateOf(0) }

    LaunchedEffect(databasePath) {
        phase =
            runCatching { Bridge.init(databasePath) }
                .fold(
                    onSuccess = { LauncherPhase.Ready },
                    onFailure = { LauncherPhase.Failed(it.message ?: it.toString()) },
                )
    }

    // Only books with a file to point at can be opened, so the launcher lists
    // exactly those: an entry here is a book the reader screen can resolve.
    LaunchedEffect(phase) {
        if (phase !is LauncherPhase.Ready) return@LaunchedEffect
        readable =
            runCatching {
                    Bridge.listBooks(limit = 50).mapNotNull { book ->
                        val editionId = book.primaryEditionId ?: return@mapNotNull null
                        val path = Bridge.editionFilePath(editionId) ?: return@mapNotNull null
                        if (java.io.File(path).isFile) book else null
                    }
                }
                .getOrDefault(emptyList())
        offersWaiting = runCatching { Bridge.pendingOffers().size }.getOrDefault(0)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Livtet",
            modifier = Modifier.size(96.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Livtet",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))
        when (val current = phase) {
            LauncherPhase.Loading -> {
                CircularProgressIndicator(color = LivtetColors.Brand, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading your library\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            LauncherPhase.Ready ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { context.startActivity(SyncActivity.intent(context)) }) {
                        Text("Sync & Devices")
                    }
                    if (offersWaiting > 0) {
                        TextButton(
                            onClick = { context.startActivity(OffersActivity.intent(context)) }
                        ) {
                            Text("Books waiting ($offersWaiting)")
                        }
                    }
                    LauncherBody(readable = readable, context = context)
                }
            is LauncherPhase.Failed ->
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
        }
    }
}

/** What the launcher offers once the library is open. */
@Composable
private fun LauncherBody(readable: List<Book>, context: android.content.Context) {
    if (readable.isEmpty()) {
        Text(
            text = "Mobile client under reconstruction",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        return
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Read something",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        readable.take(5).forEach { book ->
            val editionId = book.primaryEditionId ?: return@forEach
            TextButton(
                onClick = { context.startActivity(ReaderActivity.intent(context, editionId)) }
            ) {
                Text(text = book.title, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
