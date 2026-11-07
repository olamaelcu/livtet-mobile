package net.olamaelcu.livtet

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.designsystem.LivtetTheme
import net.olamaelcu.livtet.ffi.SyncPairedDevice

/**
 * The launch pad: your shelf, then the three things that are not a book.
 *
 * Books come first because that is what the app is for. Everything else — a device to sync with,
 * offers waiting on a decision, the feed — sits in a glance band below the shelf, where it can be
 * read without being in the way. A reader who opens this app to continue reading never has to
 * scroll past a feature.
 *
 * The home screen deliberately does not browse the network. Discovery lives on the devices screen,
 * where its battery cost is paid only by someone looking for a peer.
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
                HomeRoute(databasePath = File(filesDir, "livtet.db").absolutePath)
            }
        }
    }
}

/** Everything the band and the shelf need, read in one pass. */
private data class HomeState(
    val books: List<Book>,
    val shelf: List<ShelfBook>,
    val offersWaiting: Int,
    val firstOfferTitle: String?,
    val paired: List<SyncPairedDevice>,
)

/** A book plus how to open it: no edition, no path, no tap. */
private data class ShelfBook(val book: Book, val editionId: String?, val cover: ImageBitmap?)

private sealed interface HomePhase {
    data object Loading : HomePhase

    data object Ready : HomePhase

    data class Failed(val message: String) : HomePhase
}

@Composable
private fun HomeRoute(databasePath: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase: HomePhase by remember { mutableStateOf(HomePhase.Loading) }
    var state by remember {
        mutableStateOf(HomeState(emptyList(), emptyList(), 0, null, emptyList()))
    }
    var reloads by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(databasePath) {
        phase =
            runCatching { Bridge.init(databasePath) }
                .fold(
                    onSuccess = { HomePhase.Ready },
                    onFailure = { HomePhase.Failed(it.message ?: it.toString()) },
                )
    }

    LaunchedEffect(phase, reloads) {
        if (phase !is HomePhase.Ready) return@LaunchedEffect
        state =
            runCatching {
                    val books = Bridge.listBooks(limit = 200)
                    val shelf =
                        books
                            .mapNotNull { book ->
                                val editionId = book.primaryEditionId ?: return@mapNotNull null
                                val path =
                                    Bridge.editionFilePath(editionId) ?: return@mapNotNull null
                                if (!File(path).isFile) return@mapNotNull null
                                ShelfBook(
                                    book = book,
                                    editionId = editionId,
                                    cover = localCover(book),
                                )
                            }
                            .take(SHELF_SIZE)
                    val offers = runCatching { Bridge.pendingOffers() }.getOrDefault(emptyList())
                    HomeState(
                        books = books,
                        shelf = shelf,
                        offersWaiting = offers.size,
                        firstOfferTitle = offers.firstOrNull()?.title,
                        paired = runCatching { Bridge.pairedDevices() }.getOrDefault(emptyList()),
                    )
                }
                .getOrElse {
                    failure = it.message ?: it.toString()
                    HomeState(emptyList(), emptyList(), 0, null, emptyList())
                }
    }

    fun importFrom(uri: Uri) {
        scope.launch {
            working = "Importing\u2026"
            failure = null
            runCatching { ImportBook.from(context, uri) }
                .onSuccess { imported ->
                    notice = "\u201c${imported.title}\u201d is on this device"
                    reloads++
                }
                .onFailure { failure = it.message ?: it.toString() }
            working = null
        }
    }

    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            importFrom(uri)
        }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = phase) {
            HomePhase.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }

            is HomePhase.Failed ->
                Text(
                    text = current.message,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

            HomePhase.Ready ->
                HomeContent(
                    state = state,
                    working = working,
                    notice = notice,
                    failure = failure,
                    onSeeAll = { context.startActivity(LibraryActivity.intent(context)) },
                    onSearch = { context.startActivity(LibraryActivity.intent(context)) },
                    onImport = {
                        import.launch(arrayOf("application/epub+zip", "application/pdf"))
                    },
                    onOpenBook = { editionId ->
                        context.startActivity(ReaderActivity.intent(context, editionId))
                    },
                    onDevices = { context.startActivity(SyncActivity.intent(context)) },
                    onOffers = { context.startActivity(OffersActivity.intent(context)) },
                )
        }
    }
}

private const val SHELF_SIZE = 4

@Composable
private fun HomeContent(
    state: HomeState,
    working: String?,
    notice: String?,
    failure: String?,
    onSeeAll: () -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDevices: () -> Unit,
    onOffers: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "On this device",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text =
                    when (state.books.size) {
                        0 -> "no books yet"
                        1 -> "1 book"
                        else -> "${state.books.size} books"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        SearchPill(onClick = onSearch)
        Spacer(modifier = Modifier.height(20.dp))

        if (state.shelf.isEmpty()) {
            // The shelf slot becomes the way to fill it, so the screen keeps its
            // shape: the first accepted book simply replaces this card with covers.
            val prompt =
                HomePrompt.forState(
                    offersWaiting = state.offersWaiting,
                    pairedDevices = state.paired.size,
                    pairedName = state.paired.firstOrNull()?.name,
                )
            EmptyShelf(
                prompt = prompt,
                onPrimary = {
                    when (prompt.kind) {
                        HomePromptKind.REVIEW_OFFERS -> onOffers()
                        HomePromptKind.RECEIVE_FROM_PEER,
                        HomePromptKind.PAIR_DEVICE -> onDevices()
                    }
                },
                onImport = onImport,
            )
        } else {
            Shelf(shelf = state.shelf, onOpenBook = onOpenBook)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImport) { Text("Add a book") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }

        if (working != null) {
            Text(
                text = working,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        failure?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        GlanceBand(state = state, onDevices = onDevices, onOffers = onOffers)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Looking for a device? Discovery lives in Devices.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Search books",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun Shelf(shelf: List<ShelfBook>, onOpenBook: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shelf.forEach { entry ->
            val editionId = entry.editionId ?: return@forEach
            Column(
                modifier =
                    Modifier.weight(1f)
                        .clickable { onOpenBook(editionId) }
                        .semantics { contentDescription = "Open ${entry.book.title}" }
            ) {
                Cover(cover = entry.cover, title = entry.book.title)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.book.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Keep the last row's left alignment stable when there are fewer than four:
        // three covers should sit where they always do, not stretch to fill.
        repeat(SHELF_SIZE - shelf.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun Cover(cover: ImageBitmap?, title: String) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(136.dp)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials(title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun EmptyShelf(prompt: HomePrompt, onPrimary: () -> Unit, onImport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = prompt.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = prompt.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPrimary) { Text(prompt.primaryLabel) }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onImport) { Text("Import a file") }
            }
        }
    }
}

/**
 * The three things that are not a book, as tiles you can read at a glance.
 *
 * The feed tile is absent rather than empty: the platform feed needs a client this build does not
 * have, and a tile that only apologises is worse than no tile.
 */
@Composable
private fun GlanceBand(state: HomeState, onDevices: () -> Unit, onOffers: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlanceTile(label = "Devices", onClick = onDevices, modifier = Modifier.weight(1f)) {
            val paired = state.paired.size
            Text(
                text = if (paired == 0) "None" else "$paired",
                style = MaterialTheme.typography.headlineSmall,
                color =
                    if (paired == 0) {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
            )
            Text(
                text =
                    when (paired) {
                        0 -> "pair one to sync"
                        1 -> state.paired.first().name?.takeIf { it.isNotBlank() } ?: "paired"
                        else -> "paired devices"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlanceTile(
            label = "Offers",
            onClick = onOffers,
            modifier = Modifier.weight(1f),
            highlighted = state.offersWaiting > 0,
        ) {
            Text(
                text = "${state.offersWaiting}",
                style = MaterialTheme.typography.headlineSmall,
                color =
                    if (state.offersWaiting > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    },
            )
            Text(
                text = state.firstOfferTitle?.takeIf { state.offersWaiting > 0 } ?: "waiting",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GlanceTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    body: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            body()
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

/** The first letters of a title, for books with no cover image on disk. */
private fun initials(title: String): String =
    title
        .split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")

/**
 * A cover that is already on disk.
 *
 * [Book.coverUrl] is either a catalog URL or a local file path. Only the file can be drawn here —
 * remote covers would need an image loader, and the shelf must not block on the network to show a
 * title it already has.
 */
private fun localCover(book: Book): ImageBitmap? {
    val url = book.coverUrl ?: return null
    if (url.startsWith("http://") || url.startsWith("https://")) return null
    val file = File(url)
    if (!file.isFile) return null
    return BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
}
