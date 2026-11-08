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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
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

/** How many rows a page asks the store for. */
private const val PAGE_SIZE = 50

@Composable
private fun LibraryRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(Bridge.SortBy.RECENT) }
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<LibraryEntry>()) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<LibraryEntry?>(null) }
    var shelving by remember { mutableStateOf<String?>(null) }

    suspend fun page(offset: Int): List<LibraryEntry> =
        Bridge.listBooks(limit = PAGE_SIZE, offset = offset, sortBy = sort).map { book ->
            val editionId = book.primaryEditionId
            val path = editionId?.let { Bridge.editionFilePath(it) }
            LibraryEntry(
                book = book,
                editionId = editionId,
                readable = path != null && File(path).isFile,
            )
        }

    // A new order starts from the first page: the rows already loaded belong to the
    // old one, and appending a sorted page to an unsorted list would show neither.
    LaunchedEffect(sort) {
        if (!Bridge.isInitialized()) {
            Bridge.init(File(context.filesDir, "livtet.db").absolutePath)
        }
        loading = true
        failure = null
        runCatching { page(0) }
            .onSuccess {
                entries = it
                hasMore = it.size == PAGE_SIZE
            }
            .onFailure { failure = readable(it) }
        loading = false
    }

    // Pages arrive as the end of the list comes into view; a scroll through a large
    // library should not have paid for every row up front.
    LaunchedEffect(entries.size, hasMore) {
        if (entries.isEmpty() || !hasMore || loading) return@LaunchedEffect
        loading = true
        runCatching { page(entries.size) }
            .onSuccess { rows ->
                val known = entries.mapTo(mutableSetOf()) { it.book.id }
                entries = entries + rows.filterNot { it.book.id in known }
                hasMore = rows.size == PAGE_SIZE
            }
            .onFailure { failure = readable(it) }
        loading = false
    }

    fun remove(entry: LibraryEntry) {
        scope.launch {
            pendingDelete = null
            runCatching { Bridge.deleteBook(entry.book.id) }
                .onSuccess { removed ->
                    if (removed) {
                        entries = entries.filterNot { it.book.id == entry.book.id }
                    } else {
                        failure = "\u201c${entry.book.title}\u201d could not be removed"
                    }
                }
                .onFailure { failure = readable(it) }
        }
    }

    shelving?.let { editionId ->
        AddToShelfDialog(editionId = editionId, onDismiss = { shelving = null })
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this book?") },
            text = {
                Text(
                    "\u201c${entry.book.title}\u201d and its reading position leave this device. " +
                        "A paired device can send it again."
                )
            },
            confirmButton = { TextButton(onClick = { remove(entry) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
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
                Text(
                    text = if (entries.isNotEmpty()) "${entries.size}" else "",
                    modifier = Modifier.padding(end = 8.dp),
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A chip rather than a text button: the order in use should look chosen,
                // and an enabled-looking button next to a grey one reads the other way
                // round.
                FilterChip(
                    selected = sort == Bridge.SortBy.RECENT,
                    onClick = { sort = Bridge.SortBy.RECENT },
                    label = { Text("Recently added") },
                )
                FilterChip(
                    selected = sort == Bridge.SortBy.TITLE,
                    onClick = { sort = Bridge.SortBy.TITLE },
                    label = { Text("Title") },
                )
            }

            failure?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val filtered = entries.filter { entry ->
                query.isBlank() || entry.book.title.contains(query, ignoreCase = true)
            }

            when {
                entries.isEmpty() && loading ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }

                entries.isEmpty() && failure == null ->
                    Text(
                        text = "Nothing here yet.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )

                filtered.isEmpty() ->
                    Text(
                        text = "Nothing matches \u201c$query\u201d.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )

                else ->
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
                                entry.editionId?.let { editionId ->
                                    TextButton(onClick = { shelving = editionId }) {
                                        Text("Shelve")
                                    }
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
                                TextButton(onClick = { pendingDelete = entry }) { Text("Remove") }
                            }
                        }
                        if (hasMore) {
                            item {
                                Text(
                                    text = "Loading more\u2026",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
            }
        }
    }
}
