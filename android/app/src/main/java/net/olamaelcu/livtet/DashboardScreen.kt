package net.olamaelcu.livtet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.olamaelcu.livtet.biblio.BookCover
import net.olamaelcu.livtet.types.WorkStatus
import net.olamaelcu.livtet.ffi.Greeting

/**
 * Dashboard — the reader's shelf, not a form.
 *
 * The page answers one question a reader opens the app for: *what am I reading,
 * and what is waiting*. A greeting puts the reader in a reading mood, a single
 * summary line counts the collection, and below it the library is laid out as
 * shelf lanes — the same lanes the Library screen uses — so the dashboard reads
 * as the front of the same bookcase rather than a separate stats page.
 */
@Composable
fun DashboardScreen(onOpenBook: (Book) -> Unit = {}, onLongPressBook: (Book) -> Unit = {}) {
    var greeting by remember { mutableStateOf<Greeting?>(null) }
    var stats by remember { mutableStateOf<DashboardStats?>(null) }
    var books by remember { mutableStateOf<List<Book>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            greeting = Bridge.getGreeting()
            stats = Bridge.getDashboardStats()
            books = Bridge.listBooks(limit = 50)
        } catch (e: Exception) {
            error = e.message
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
    ) {
        item(key = "masthead") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                val g = greeting
                if (g != null) {
                    Text(
                        text = g.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = g.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (g.author.isNotBlank() || g.material.isNotBlank()) {
                        Text(
                            text = "${g.author} - ${g.material}",
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = FontStyle.Italic,
                                ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                when {
                    error != null ->
                        Text(
                            text = "Could not load dashboard: $error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    stats != null -> SummaryRow(stats = stats!!)
                }
            }
        }

        val shelfBooks = books
        if (shelfBooks != null && shelfBooks.isNotEmpty()) {
            shelfLanes(
                books = shelfBooks,
                coverPaths = emptyMap(),
                onLongPress = onLongPressBook,
                onOpen = onOpenBook,
            )
        } else if (shelfBooks != null) {
            item(key = "empty") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) { EmptyStateCard() }
            }
        }
    }
}

@Composable
private fun SummaryRow(stats: DashboardStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatItem(value = stats.totalBooks.toString(), label = "Books")
        StatItem(value = stats.booksInProgress.toString(), label = "Reading")
        StatItem(value = stats.finishedBooks.toString(), label = "Finished")
        StatItem(value = formatDuration(stats.totalReadingTimeSecs), label = "Time read")
    }
}

@Composable
private fun RowScope.StatItem(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * The shelf lanes themselves. This is a [LazyListScope] extension rather than a
 * composable so the lanes stay items of the dashboard's single scrolling list —
 * a nested scrolling container would be measured with infinite height.
 */
private fun LazyListScope.shelfLanes(
    books: List<Book>,
    coverPaths: Map<String, String>,
    onLongPress: (Book) -> Unit,
    onOpen: (Book) -> Unit,
) {
    shelvesOf(books).forEach { shelf ->
        item(key = "header-${shelf.key}") {
            ShelfHeader(label = shelf.label, count = shelf.books.size)
        }
        item(key = "row-${shelf.key}") {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shelf.books, key = { it.id }) { book ->
                    CoverTile(
                        book = book,
                        localCover = coverPaths[book.id],
                        onLongPress = { onLongPress(book) },
                        onOpen = { onOpen(book) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfHeader(label: String, count: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverTile(
    book: Book,
    localCover: String?,
    onLongPress: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(modifier = Modifier.width(104.dp)) {
        Box(
            modifier = Modifier.combinedClickable(
                onLongClick = { onLongPress() },
                onClick = onOpen,
            ),
        ) {
            Card(
                modifier = Modifier.size(width = 104.dp, height = 156.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                BookCover(
                    url = localCover ?: book.coverUrl,
                    title = book.title,
                    width = 104.dp,
                    height = 156.dp,
                )
            }
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
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "\uD83D\uDCDA", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your shelf is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Import books from your catalog to start reading here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Lane order follows a reader's attention: what is being read, what is next,
 * then what is done. Works on no shelf still appear, in a catch-all lane.
 */
private fun shelvesOf(books: List<Book>): List<DashShelf> {
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
            if (lane.isEmpty()) null else DashShelf(status.name, label, lane)
        }
    val unshelved = books.filter { it.status == null }
    return if (unshelved.isEmpty()) {
        shelved
    } else {
        shelved + DashShelf("unshelved", "Unshelved", unshelved)
    }
}

/** One dashboard lane: its stable key, the label it shows, and the books on it. */
private data class DashShelf(val key: String, val label: String, val books: List<Book>)