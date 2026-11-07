package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.io.File
import net.olamaelcu.livtet.core.designsystem.LivtetTheme

/**
 * Everything on this device.
 *
 * A flat, filterable list rather than shelves: this is the screen you come to when the home shelf
 * did not have what you wanted, so finding is the whole job. Books with no file are listed too —
 * they exist in the library and pretending otherwise would make a book vanish — but they cannot be
 * opened, and they say so.
 */
class LibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) { LibraryRoute(onBack = { finish() }) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LibraryActivity::class.java)
    }
}

private data class LibraryEntry(val book: Book, val editionId: String?, val readable: Boolean)

private sealed interface LibraryState {
    data object Loading : LibraryState

    data class Failed(val message: String) : LibraryState

    data class Ready(val entries: List<LibraryEntry>) : LibraryState
}

@Composable
private fun LibraryRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var state: LibraryState by remember { mutableStateOf(LibraryState.Loading) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!Bridge.isInitialized()) {
            Bridge.init(File(context.filesDir, "livtet.db").absolutePath)
        }
        state =
            try {
                val books = Bridge.listBooks(limit = 500)
                val entries = books.map { book ->
                    val editionId = book.primaryEditionId
                    val path = editionId?.let { Bridge.editionFilePath(it) }
                    LibraryEntry(
                        book = book,
                        editionId = editionId,
                        readable = path != null && File(path).isFile,
                    )
                }
                LibraryState.Ready(entries)
            } catch (error: Exception) {
                LibraryState.Failed(readable(error))
            }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("\u2039  Home") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Library", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                val count = (state as? LibraryState.Ready)?.entries?.size ?: 0
                Text(
                    text = if (count > 0) "$count" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                label = { Text("Filter by title") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            when (val current = state) {
                LibraryState.Loading ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }

                is LibraryState.Failed ->
                    Text(
                        text = current.message,
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                is LibraryState.Ready -> {
                    val filtered =
                        current.entries.filter { entry ->
                            query.isBlank() || entry.book.title.contains(query, ignoreCase = true)
                        }
                    if (filtered.isEmpty()) {
                        Text(
                            text =
                                if (current.entries.isEmpty()) {
                                    "Nothing here yet."
                                } else {
                                    "Nothing matches \u201c$query\u201d."
                                },
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.book.id }) { entry ->
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.book.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text =
                                                if (entry.readable) {
                                                    "On this device"
                                                } else {
                                                    "No file yet \u00b7 accept it from a device"
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                MaterialTheme.colorScheme.onBackground.copy(
                                                    alpha = 0.6f
                                                ),
                                        )
                                    }
                                    if (entry.readable && entry.editionId != null) {
                                        val editionId = entry.editionId
                                        TextButton(
                                            onClick = {
                                                context.startActivity(
                                                    ReaderActivity.intent(context, editionId)
                                                )
                                            }
                                        ) {
                                            Text("Read")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
