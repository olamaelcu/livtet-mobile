package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    // Bulk selection: a set of work ids, so a book staying selected is independent of the
    // page it arrived on and of the filter above the list.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var bulkShelving by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf<String?>(null) }

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

    fun deleteSelected() {
        pendingBulkDelete = false
        val targets = entries.filter { it.book.id in selected }
        if (targets.isEmpty()) return
        scope.launch {
            working = "Removing ${targets.size}…"
            val removed = mutableSetOf<String>()
            var failed = 0
            targets.forEach { entry ->
                runCatching { Bridge.deleteBook(entry.book.id) }
                    .onSuccess { if (it) removed += entry.book.id else failed += 1 }
                    .onFailure { failed += 1 }
            }
            entries = entries.filterNot { it.book.id in removed }
            selected = emptySet()
            selecting = false
            working = null
            if (failed > 0) failure = "$failed could not be removed"
        }
    }

    fun shelveSelected(shelfId: String) {
        bulkShelving = false
        val targets = entries.filter { it.book.id in selected }
        val files = targets.filter { it.editionId != null }
        if (files.isEmpty()) {
            selected = emptySet()
            selecting = false
            failure = "Those books have no edition to shelve yet."
            return
        }
        scope.launch {
            working = "Shelving ${files.size}…"
            var failed = 0
            files.forEach { entry ->
                runCatching { Bridge.addToShelf(shelfId, entry.editionId!!) }
                    .onFailure { failed += 1 }
            }
            selected = emptySet()
            selecting = false
            working = null
            if (failed > 0) failure = "$failed could not be shelved"
        }
    }

    if (pendingBulkDelete) {
        val count = entries.count { it.book.id in selected }
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            title = { Text(if (count == 1) "Remove this book?" else "Remove $count books?") },
            text = {
                Text(
                    "They and their reading positions leave this device. " +
                        "A paired device can send them again."
                )
            },
            confirmButton = {
                TextButton(onClick = ::deleteSelected) {
                    Text(if (count == 1) "Remove" else "Remove $count")
                }
            },
            dismissButton = { TextButton(onClick = { pendingBulkDelete = false }) { Text("Keep") } },
        )
    }

    if (bulkShelving) {
        BulkShelfDialog(
            count = entries.count { it.book.id in selected },
            onDismiss = { bulkShelving = false },
            onPick = ::shelveSelected,
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().appHeaderInset()) {
            val filtered = entries.filter { entry ->
                query.isBlank() || entry.book.title.contains(query, ignoreCase = true)
            }

            if (selecting) {
                BulkToolbar(
                    count = selected.size,
                    allSelected = filtered.isNotEmpty() && selected.size == filtered.size,
                    working = working,
                    onClose = {
                        selecting = false
                        selected = emptySet()
                    },
                    onSelectAll = { selected = filtered.mapTo(mutableSetOf()) { it.book.id } },
                    onShelve = { bulkShelving = true },
                    onDelete = { pendingBulkDelete = true },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("\u2039  Home") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Library", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    if (entries.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selecting = true
                                selected = emptySet()
                            }
                        ) {
                            Text("Select")
                        }
                    } else {
                        Spacer(modifier = Modifier.padding(end = 8.dp))
                    }
                }
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(filtered, key = { it.book.id }) { entry ->
                            val id = entry.book.id
                            val checked = id in selected
                            LibraryTile(
                                entry = entry,
                                selecting = selecting,
                                checked = checked,
                                onOpen = {
                                    val editionId = entry.editionId
                                    if (entry.readable && editionId != null) {
                                        context.startActivity(
                                            ReaderActivity.intent(context, editionId)
                                        )
                                    }
                                },
                                onToggle = {
                                    // A long-press on a remote book starts selection too: with
                                    // no file to open, selection is the only useful action.
                                    if (!selecting) selecting = true
                                    selected = if (checked) selected - id else selected + id
                                },
                            )
                        }
                        if (hasMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Loading more\u2026",
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
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

/**
 * The bulk action bar.
 *
 * Selection mode replaces the title row rather than adding a second bar: with a hundred rows of
 * library below it, the actions have to be where the eye already is, and the way out ("Close") is
 * the corner "Back" occupied.
 */
@Composable
private fun BulkToolbar(
    count: Int,
    allSelected: Boolean,
    working: String?,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShelve: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Close") }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = working ?: if (count == 1) "1 selected" else "$count selected",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onSelectAll, enabled = !allSelected && working == null) {
                    Text("All")
                }
            }
            Row(
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onShelve, enabled = count > 0 && working == null) {
                    Text("Shelve")
                }
                TextButton(onClick = onDelete, enabled = count > 0 && working == null) {
                    Text("Delete")
                }
            }
        }
    }
}

/**
 * One book on the shelf wall.
 *
 * A tap opens what can be opened; a long-press (or a tap on a remote book, which has nothing to
 * open) starts selection. There are no per-row buttons: the bulk toolbar is one gesture away and
 * covers the same actions for one book or twenty.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTile(
    entry: LibraryEntry,
    selecting: Boolean,
    checked: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(
        modifier =
            Modifier.combinedClickable(
                onClick = {
                    if (selecting) onToggle() else if (entry.readable) onOpen() else onToggle()
                },
                onLongClick = onToggle,
            )
    ) {
        Box {
            LibraryCover(book = entry.book, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (selecting) {
                SelectionMark(
                    checked = checked,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Text(
            text = entry.book.title,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (entry.readable) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (entry.readable) "On device" else "Remote",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (entry.readable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                },
        )
    }
}

/** The selection tick drawn over a cover, small enough to leave the art visible. */
@Composable
private fun SelectionMark(checked: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(20.dp),
        shape = CircleShape,
        color =
            if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Text(
                    text = "\u2713",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * A cover from whatever the catalog knows: a local file, a URL, or the title's initial.
 *
 * Most of this library has no art on this phone yet, so the initial is drawn *under* the image: a
 * fallback that survives a broken or missing source without an empty hole in the grid.
 */
@Composable
private fun LibraryCover(book: Book, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        val source = book.coverUrl
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = book.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                source.isNullOrBlank() -> Unit
                source.startsWith("http") ->
                    AsyncImage(
                        model = source,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                else -> {
                    val bitmap: ImageBitmap? =
                        remember(source) {
                            runCatching { BitmapFactory.decodeFile(source)?.asImageBitmap() }
                                .getOrNull()
                        }
                    bitmap?.let { loaded ->
                        Image(
                            bitmap = loaded,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
