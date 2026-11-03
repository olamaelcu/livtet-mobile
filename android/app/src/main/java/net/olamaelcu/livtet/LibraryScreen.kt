package net.olamaelcu.livtet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: LibraryViewModel = hiltViewModel(),
    onAddBook: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var shelfTarget by remember { mutableStateOf<Book?>(null) }
    var shelfAction by remember { mutableStateOf<Pair<Bridge.Shelf, Book>?>(null) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.books.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> ErrorView(message = state.error!!)
            state.books.isEmpty() -> EmptyView(message = state.emptyMessage)
            else ->
                ShelfLanes(
                    books = state.books,
                    shelves = state.shelves,
                    coverPaths = state.coverPaths,
                    isLoadingMore = state.isLoadingMore,
                    onLongPress = { shelfTarget = it },
                    onShelfLongPress = { shelf, book -> shelfAction = shelf to book },
                    onLoadMore = viewModel::loadMore,
                )
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
    }

    shelfTarget?.let { book ->
        ShelfPicker(
            book = book,
            shelves = state.shelves,
            onPick = { shelf ->
                viewModel.addToShelf(book, shelf.id)
                shelfTarget = null
            },
            onCreate = { name ->
                viewModel.addToShelf(book, shelfId = null, newShelfName = name)
                shelfTarget = null
            },
            onDelete = {
                deleteTarget = book
                shelfTarget = null
            },
            onDismiss = { shelfTarget = null },
        )
    }

    deleteTarget?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove from library?") },
            text = {
                Text(
                    "\"${book.title}\" and its editions, shelves and reading status go with it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(book)
                        deleteTarget = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    shelfAction?.let { (shelf, book) ->
        ShelfBookActions(
            book = book,
            canMoveEarlier = shelf.editionIds.indexOf(book.primaryEditionId) > 0,
            canMoveLater =
                shelf.editionIds.indexOf(book.primaryEditionId) in
                    0 until (shelf.editionIds.size - 1),
            onMoveEarlier = {
                viewModel.moveInShelf(shelf, book, -1)
                shelfAction = null
            },
            onMoveLater = {
                viewModel.moveInShelf(shelf, book, +1)
                shelfAction = null
            },
            onRemove = {
                viewModel.removeFromShelf(shelf, book)
                shelfAction = null
            },
            onDismiss = { shelfAction = null },
        )
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
 * then what is done. Works that sit on no shelf at all still have to appear, so
 * they get the catch-all lane at the end rather than being dropped.
 *
 * A work counts as shelved when it has a reading status or is filed on a named
 * shelf; either way it is listed where it was put, not in the catch-all.
 */
private fun shelvesOf(books: List<Book>, filedEditionIds: Set<String>): List<Shelf> {
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
    // A book filed on a named shelf is shelved, so it belongs on that shelf and
    // not in the catch-all: leaving it in both made the lane's name a lie.
    val unshelved =
        books.filter { book ->
            book.status == null &&
                book.primaryEditionId?.let { it !in filedEditionIds } != false
        }
    return if (unshelved.isEmpty()) shelved else shelved + Shelf("unshelved", "Unshelved", unshelved)
}

@Composable
private fun ShelfLanes(
    books: List<Book>,
    shelves: List<Bridge.Shelf>,
    coverPaths: Map<String, String>,
    isLoadingMore: Boolean,
    onLongPress: (Book) -> Unit,
    onShelfLongPress: (Bridge.Shelf, Book) -> Unit,
    onLoadMore: () -> Unit,
) {
    val filedEditionIds = remember(shelves) { shelves.flatMap { it.editionIds }.toSet() }
    val lanes = remember(books, filedEditionIds) { shelvesOf(books, filedEditionIds) }
    var openShelfId by remember { mutableStateOf<String?>(null) }

    // Fetch the next page once the reader is a screen away from the end, so the
    // list does not stall on a spinner in the middle of a scroll.
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) onLoadMore() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
    ) {
        if (shelves.isNotEmpty()) {
            item(key = "named-shelves") {
                NamedShelfStrip(
                    shelves = shelves,
                    openShelfId = openShelfId,
                    onToggle = { shelf ->
                        openShelfId = if (openShelfId == shelf.id) null else shelf.id
                    },
                )
            }
            val open = shelves.firstOrNull { it.id == openShelfId }
            if (open != null) {
                // A shelf stores editions; a row is a work, so only books whose
                // primary edition is on the shelf can appear here. The shelf's own
                // order decides the lane: sorting by library order would hide a move.
                val filed =
                    books
                        .filter { book -> book.primaryEditionId in open.editionIds }
                        .sortedBy { book -> open.editionIds.indexOf(book.primaryEditionId) }
                item(key = "shelf-open-header-${open.id}") {
                    ShelfHeader(label = open.name, count = filed.size)
                }
                if (filed.isEmpty()) {
                    item(key = "shelf-open-empty-${open.id}") {
                        Text(
                            text = "Nothing from this shelf is in the library view.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                } else {
                    item(key = "shelf-open-row-${open.id}") {
                        ShelfRow(
                            books = filed,
                            coverPaths = coverPaths,
                            onLongPress = { book -> onShelfLongPress(open, book) },
                        )
                    }
                }
                item(key = "shelf-open-edge-${open.id}") { ShelfEdge() }
            }
        }
        lanes.forEach { shelf ->
            item(key = "header-${shelf.key}") {
                ShelfHeader(label = shelf.label, count = shelf.books.size)
            }
            item(key = "row-${shelf.key}") {
                ShelfRow(
                    books = shelf.books,
                    coverPaths = coverPaths,
                    onLongPress = onLongPress,
                )
            }
            item(key = "shelf-${shelf.key}") { ShelfEdge() }
        }
        if (isLoadingMore) {
            item(key = "loading-more") {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** How close to the end of the list the next page starts loading. */
private const val LOAD_MORE_THRESHOLD = 3

/**
 * One lane's covers.
 *
 * A newly imported book lands at the head of the list, and a keyed `LazyRow`
 * keeps its anchor on whichever cover it was showing — which reads as the new
 * book arriving cut in half. Resetting when the head changes keeps the newest
 * cover fully visible without fighting a scroll the reader started.
 */
@Composable
private fun ShelfRow(
    books: List<Book>,
    coverPaths: Map<String, String>,
    onLongPress: (Book) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(books.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex != 0) listState.scrollToItem(0)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.id }) { book ->
            CoverTile(
                book = book,
                localCover = coverPaths[book.id],
                onLongPress = { onLongPress(book) },
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverTile(book: Book, localCover: String?, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val koreaderInstalled by
        LivtetApp.getInstance().koreaderPresence.isInstalled.collectAsState()

    Column(modifier = Modifier.width(104.dp)) {
        Box(
            modifier =
                Modifier.combinedClickable(
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                        onClick = {
                            if (koreaderInstalled) OpenInKoreader.launchKoreader(context)
                        },
                    )
                    .semantics {
                        contentDescription =
                            if (koreaderInstalled) "${book.title}, open in KOReader"
                            else book.title
                    }
        ) {
            BookCover(
                url = localCover ?: book.coverUrl,
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

/**
 * Named shelves. A chip opens that shelf's covers inline, directly under the
 * strip, so a shelf reads like any other lane instead of a separate screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NamedShelfStrip(
    shelves: List<Bridge.Shelf>,
    openShelfId: String?,
    onToggle: (Bridge.Shelf) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        Text(
            text = "Shelves",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shelves, key = { it.id }) { shelf ->
                FilterChip(
                    selected = shelf.id == openShelfId,
                    onClick = { onToggle(shelf) },
                    label = { Text("${shelf.name} · ${shelf.editionIds.size}") },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * What a long-press on a shelved cover offers: move it along the shelf, or take
 * it off. Positions are the shelf's own order, so both moves are one step.
 */
@Composable
private fun ShelfBookActions(
    book: Book,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shelf order") },
        text = {
            Column {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onMoveEarlier, enabled = canMoveEarlier) {
                    Text("Move earlier")
                }
                TextButton(onClick = onMoveLater, enabled = canMoveLater) {
                    Text("Move later")
                }
                TextButton(onClick = onRemove) { Text("Remove from this shelf") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Where a long-press files a book: an existing shelf, or a new one.
 *
 * A shelf stores editions, so a book that has no primary edition cannot be
 * filed and the dialog says so instead of silently doing nothing.
 */
@Composable
private fun ShelfPicker(
    book: Book,
    shelves: List<Bridge.Shelf>,
    onPick: (Bridge.Shelf) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    val canFile = book.primaryEditionId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (canFile) "Add to shelf" else "No edition to file") },
        text = {
            Column {
                if (!canFile) {
                    Text(
                        text = "\"${book.title}\" has no edition of its own, and a shelf stores editions.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    shelves.forEach { shelf ->
                        TextButton(onClick = { onPick(shelf) }) {
                            val here = shelf.editionIds.contains(book.primaryEditionId)
                            Text(if (here) "${shelf.name} (already filed)" else shelf.name)
                        }
                    }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New shelf") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = onDelete) { Text("Remove from library") }
                }
            }
        },
        confirmButton = {
            if (canFile) {
                TextButton(
                    onClick = { onCreate(newName) },
                    enabled = newName.isNotBlank(),
                ) {
                    Text("Create and add")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
