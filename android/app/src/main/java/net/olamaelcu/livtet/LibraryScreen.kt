package net.olamaelcu.livtet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.olamaelcu.livtet.biblio.BookCover
import net.olamaelcu.livtet.biblio.SearchEditionsScreen
import net.olamaelcu.livtet.ffi.EmptyMessage
import net.olamaelcu.livtet.types.WorkStatus

/**
 * Library screen — the collection as shelves of covers.
 *
 * Each reading status is a horizontal lane of covers, so the page reads as
 * rows of book spines rather than a form. Covers come from the catalog URL an
 * imported edition stored, falling back to a local cover file, and to the
 * title's initial when an edition has neither.
 *
 * Wires through `Bridge.listBooks` (50 per page, newest first) and
 * `Bridge.getEmptyStateQuotation` (literary filler for the empty state). The
 * FloatingActionButton opens the XRPC edition search sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = viewModel(),
    onAddBook: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.books.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> ErrorView(message = state.error!!)
            state.books.isEmpty() -> EmptyView(message = state.emptyMessage)
            else -> ShelfLanes(books = state.books)
        }

        FloatingActionButton(
            onClick = {
                onAddBook()
                showSearch = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Search editions (XRPC)")
        }
    }

    if (showSearch) {
        SearchEditionsScreen(
            onDismiss = { showSearch = false },
            onImported = {
                showSearch = false
                viewModel.load()
            },
        )
    }
}

/** One lane: the label it shows and the books on it. */
private data class Shelf(val key: String, val label: String, val books: List<Book>)

/**
 * Lane order follows a reader's attention: what is being read, what is next,
 * then what is done. Works that were never shelved still have to appear, so
 * they get their own lane at the end rather than being dropped.
 */
private fun shelvesOf(books: List<Book>): List<Shelf> {
    val lanes =
        listOf(
            WorkStatus.READING to "Reading",
            WorkStatus.TO_READ to "Want to read",
            WorkStatus.QUEUED to "Queued",
            WorkStatus.ACTIVE to "Active",
            WorkStatus.FINISHED to "Finished",
            WorkStatus.ABANDONED to "Abandoned",
        )
    val shelved =
        lanes.mapNotNull { (status, label) ->
            val lane = books.filter { it.status == status }
            if (lane.isEmpty()) null else Shelf(status.name, label, lane)
        }
    val unshelved = books.filter { it.status == null }
    return if (unshelved.isEmpty()) shelved else shelved + Shelf("unshelved", "Unshelved", unshelved)
}

@Composable
private fun ShelfLanes(books: List<Book>) {
    val shelves = remember(books) { shelvesOf(books) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
    ) {
        shelves.forEach { shelf ->
            item(key = "header-${shelf.key}") {
                ShelfHeader(label = shelf.label, count = shelf.books.size)
            }
            item(key = "row-${shelf.key}") { ShelfRow(books = shelf.books) }
            item(key = "shelf-${shelf.key}") { ShelfEdge() }
        }
    }
}

/**
 * One lane's covers.
 *
 * A newly imported book lands at the head of the list, and a keyed `LazyRow`
 * keeps its anchor on whichever cover it was showing — which reads as the new
 * book arriving cut in half. Resetting when the head changes keeps the newest
 * cover fully visible without fighting a scroll the reader started.
 */
@Composable
private fun ShelfRow(books: List<Book>) {
    val listState = rememberLazyListState()
    LaunchedEffect(books.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex != 0) listState.scrollToItem(0)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.id }) { book -> CoverTile(book = book) }
    }
}

@Composable
private fun ShelfHeader(label: String, count: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (count == 1) "1 book" else "$count books",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The line a lane sits on — a shelf edge, not a divider between rows. */
@Composable
private fun ShelfEdge() {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun CoverTile(book: Book) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val koreaderInstalled by
        LivtetApp.getInstance().koreaderPresence.isInstalled.collectAsState()

    Column(modifier = Modifier.width(104.dp)) {
        Box(
            modifier =
                Modifier.clickable {
                        if (koreaderInstalled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            OpenInKoreader.launchKoreader(context)
                        }
                    }
                    .semantics {
                        contentDescription =
                            if (koreaderInstalled) "${book.title}, open in KOReader"
                            else book.title
                    }
        ) {
            BookCover(
                url = book.coverUrl,
                title = book.title,
                width = 104.dp,
                height = 156.dp,
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyView(message: EmptyMessage?) {
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
