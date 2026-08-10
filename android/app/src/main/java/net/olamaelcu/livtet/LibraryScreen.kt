package net.olamaelcu.livtet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.olamaelcu.livtet.core.designsystem.LivtetRadius
import net.olamaelcu.livtet.ffi.Book

/**
 * Library screen — paginated list of the user's stored works.
 *
 * Wires through `Bridge.listBooks` (50 per page, newest first) and
 * `Bridge.getEmptyStateQuotation` (literary filler for the empty state).
 * Filters, search, pull-to-refresh, cover thumbnails, and the Add Book
 * wizard entry point are each deferred to a follow-up commit.
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            state.isLoading && state.books.isEmpty() -> {
                CircularProgressIndicator()
            }
            state.error != null -> {
                ErrorView(message = state.error!!)
            }
            state.books.isEmpty() -> {
                EmptyView(message = state.emptyMessage)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.books, key = { it.id.toString() }) { book ->
                        BookRow(book = book)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: Book) {
    val context = LocalContext.current
    val koreaderInstalled by LivtetApp.getInstance().koreaderPresence.isInstalled.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(LivtetRadius.l),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val desc = book.description
            if (desc != null && desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // TODO(book-file-uri): replace `launchKoreader` with
            // `OpenInKoreader.openBookInKoreader(book)` once the FFI
            // exposes a local file URI per `Book`.
            if (koreaderInstalled) {
                TextButton(
                    onClick = { OpenInKoreader.launchKoreader(context) },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Open in KOReader")
                }
            }
        }
    }
}

@Composable
private fun EmptyView(message: net.olamaelcu.livtet.ffi.EmptyMessage?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "\uD83D\uDCDA", style = MaterialTheme.typography.displayMedium)
        Text(
            text = "Your library",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (message != null && message.text.isNotBlank()) {
            Text(
                text = "\u201C${message.text}\u201D",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 16.dp),
            )
            if (message.author.isNotBlank() || message.material.isNotBlank()) {
                Text(
                    text = "${message.author} — ${message.material}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "\u26A0\uFE0F", style = MaterialTheme.typography.displayMedium)
        Text(
            text = "Could not load your library",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
