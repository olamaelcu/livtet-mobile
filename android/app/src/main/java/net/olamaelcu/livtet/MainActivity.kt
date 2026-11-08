package net.olamaelcu.livtet

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
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
    /**
     * The `livtet://oauth/callback` redirect, when the browser sent the reader back here. Kept as
     * state so a warm activity handles it the same way a cold one does; the manifest marks this
     * activity `singleTop` for that reason.
     */
    private val redirect = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        redirect.value = intent?.dataString?.takeIf(::isSignInRedirect)
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) {
                val pendingRedirect by redirect.collectAsState()
                HomeRoute(
                    databasePath = File(filesDir, "livtet.db").absolutePath,
                    redirect = pendingRedirect,
                    onRedirectHandled = { redirect.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        redirect.value = intent.dataString?.takeIf(::isSignInRedirect) ?: redirect.value
    }

    /** The only `livtet://` link this activity completes is the sign-in one. */
    private fun isSignInRedirect(value: String): Boolean =
        value.startsWith("livtet://oauth", ignoreCase = true)
}

/** Everything the band and the shelf need, read in one pass. */
private data class HomeState(
    val books: List<Book>,
    val shelf: List<ShelfBook>,
    val offersWaiting: Int,
    val firstOfferTitle: String?,
    val paired: List<SyncPairedDevice>,
    /** The newest thing in the reader's feed, when signed in and reachable. */
    val feedHeadline: String? = null,
    val feedNeedsSignIn: Boolean = false,
)

/**
 * A book plus how to open it: no edition, no path, no tap.
 *
 * [progress] is the reader's own position in the edition, so the shelf can show where they stopped
 * instead of asking them to remember. Zero means "not started", which is also what a book that has
 * never been opened reports.
 */
private data class ShelfBook(
    val book: Book,
    val editionId: String?,
    val cover: ImageBitmap?,
    val progress: Double = 0.0,
    val author: String? = null,
)

private sealed interface HomePhase {
    data object Loading : HomePhase

    data object Ready : HomePhase

    data class Failed(val message: String) : HomePhase
}

@Composable
private fun HomeRoute(
    databasePath: String,
    redirect: String? = null,
    onRedirectHandled: () -> Unit = {},
) {
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

    // Coming back from the library or the reader can change what the shelf holds —
    // a book removed, progress written — so the screen re-reads on resume rather than
    // showing what was true when it was first composed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reloads++ }

    LaunchedEffect(databasePath) {
        phase =
            runCatching { Bridge.init(databasePath) }
                .fold(
                    onSuccess = { HomePhase.Ready },
                    onFailure = { HomePhase.Failed(it.message ?: it.toString()) },
                )
    }

    // The browser came back with a code: finish the exchange before the screen
    // asks the feed anything, so a successful sign-in lands in the same pass.
    LaunchedEffect(redirect) {
        val value = redirect ?: return@LaunchedEffect
        working = "Finishing sign-in\u2026"
        failure = null
        PlatformAuth.completeSignIn(context, value)
            .onSuccess {
                notice = "Signed in"
                reloads++
            }
            .onFailure { failure = it.message ?: it.toString() }
        working = null
        onRedirectHandled()
    }

    LaunchedEffect(phase, reloads) {
        if (phase !is HomePhase.Ready) return@LaunchedEffect
        state =
            runCatching {
                    val books = Bridge.listBooks(limit = 200)
                    // Only books with a file can be opened, so only those reach a shelf.
                    val readable = books.mapNotNull { book ->
                        val editionId = book.primaryEditionId ?: return@mapNotNull null
                        val path = Bridge.editionFilePath(editionId) ?: return@mapNotNull null
                        if (!File(path).isFile) return@mapNotNull null
                        ShelfBook(book = book, editionId = editionId, cover = localCover(book))
                    }
                    // Continue reading first: the shelf is where you pick up, so a book
                    // read ten minutes ago outranks one acquired first. The rest of the
                    // row is what is next, rather than empty slots next to your book.
                    val byWork = books.associateBy { it.id }
                    val continuing =
                        Bridge.getRecentlyReadBooks(SHELF_SIZE).mapNotNull { recent ->
                            val book = byWork[recent.workId] ?: return@mapNotNull null
                            val path = Bridge.editionFilePath(recent.editionId)
                            if (path == null || !File(path).isFile) return@mapNotNull null
                            ShelfBook(
                                book = book,
                                editionId = recent.editionId,
                                cover = localCover(book),
                                progress = recent.progress,
                                author =
                                    recent.authorName
                                        ?: Bridge.editionDetail(recent.editionId)
                                            ?.authors
                                            ?.firstOrNull(),
                            )
                        }
                    val alreadyOnShelf = continuing.mapNotNullTo(mutableSetOf()) { it.editionId }
                    val shelf =
                        (continuing + readable.filterNot { it.editionId in alreadyOnShelf }).take(
                            SHELF_SIZE
                        )
                    val offers = runCatching { Bridge.pendingOffers() }.getOrDefault(emptyList())
                    val signedIn = PlatformAuth.isSignedIn(context)
                    val headline =
                        if (signedIn) {
                            FeedClient.page(context, limit = 1).getOrNull()?.entries?.firstOrNull()
                        } else {
                            null
                        }
                    HomeState(
                        books = books,
                        shelf = shelf,
                        offersWaiting = offers.size,
                        firstOfferTitle = offers.firstOrNull()?.title,
                        paired = runCatching { Bridge.pairedDevices() }.getOrDefault(emptyList()),
                        feedHeadline = headline?.summary,
                        feedNeedsSignIn = !signedIn,
                    )
                }
                .getOrElse { error ->
                    // A recomposition key change cancels this effect, and
                    // CancellationException is a control-flow signal, not a
                    // failure to show: swallowing it printed Compose's own
                    // "coroutine scope left the composition" as a user error.
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    failure = error.message ?: error.toString()
                    HomeState(emptyList(), emptyList(), 0, null, emptyList())
                }
    }

    // Covers for catalog books live on the network until they have been fetched once,
    // so they are resolved after the shelf is already on screen: a slow or offline
    // cover degrades to initials rather than holding up the library.
    var resolvedCovers by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(state.shelf.map { it.editionId }) {
        state.shelf
            .mapNotNull { it.editionId }
            .forEach { editionId ->
                if (resolvedCovers.containsKey(editionId)) return@forEach
                val book = state.shelf.firstOrNull { it.editionId == editionId }?.book
                val url = book?.coverUrl?.takeIf { it.startsWith("http") } ?: return@forEach
                val path = runCatching { Bridge.resolveCoverPath(editionId, url) }.getOrNull()
                val bitmap = path?.takeIf { it.isNotBlank() }?.let { coverAt(it) } ?: return@forEach
                resolvedCovers = resolvedCovers + (editionId to bitmap)
            }
    }

    fun importFrom(uri: Uri) {
        scope.launch {
            working = "Importing\u2026"
            failure = null
            runCatching { ImportBook.from(context, uri) }
                .onSuccess { imported ->
                    notice =
                        if (imported.alreadyPresent) {
                            "\u201c${imported.title}\u201d was already on this device"
                        } else {
                            "\u201c${imported.title}\u201d is on this device"
                        }
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
                    covers = resolvedCovers,
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
                    onFeed = { context.startActivity(FeedActivity.intent(context)) },
                    onShelves = { context.startActivity(ShelvesActivity.intent(context)) },
                )
        }
    }
}

private const val SHELF_SIZE = 4

@Composable
private fun HomeContent(
    state: HomeState,
    covers: Map<String, ImageBitmap>,
    working: String?,
    notice: String?,
    failure: String?,
    onSeeAll: () -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDevices: () -> Unit,
    onOffers: () -> Unit,
    onFeed: () -> Unit,
    onShelves: () -> Unit,
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
            Shelf(shelf = state.shelf, covers = covers, onOpenBook = onOpenBook)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImport) { Text("Add a book") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onShelves) { Text("Shelves") }
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
        GlanceBand(state = state, onDevices = onDevices, onOffers = onOffers, onFeed = onFeed)
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
private fun Shelf(
    shelf: List<ShelfBook>,
    covers: Map<String, ImageBitmap>,
    onOpenBook: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shelf.forEach { entry ->
            val editionId = entry.editionId ?: return@forEach
            Column(
                modifier =
                    Modifier.weight(1f)
                        .clickable { onOpenBook(editionId) }
                        .semantics { contentDescription = "Open ${entry.book.title}" }
            ) {
                Cover(cover = covers[editionId] ?: entry.cover, title = entry.book.title)
                if (entry.progress > 0.0) {
                    ProgressUnder(progress = entry.progress)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.book.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        entry.author
                            ?: if (entry.progress > 0.0) {
                                "${(entry.progress * 100).toInt()}%"
                            } else {
                                ""
                            },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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
                // Fill `primary`, label `onPrimary` — the tokens pair them for
                // 5.19:1 in both schemes; see "Filled roles pair with the light
                // ink" in android/AGENTS.md before giving this button its own colours.
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
private fun GlanceBand(
    state: HomeState,
    onDevices: () -> Unit,
    onOffers: () -> Unit,
    onFeed: () -> Unit,
) {
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
        GlanceTile(
            label = "Feed",
            onClick = onFeed,
            modifier = Modifier.weight(1f),
            highlighted = state.feedHeadline != null,
        ) {
            Text(
                text =
                    if (state.feedNeedsSignIn) {
                        "Sign in"
                    } else if (state.feedHeadline != null) {
                        "New"
                    } else {
                        "Quiet"
                    },
                style = MaterialTheme.typography.headlineSmall,
                color =
                    if (state.feedHeadline != null) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    },
            )
            Text(
                text =
                    when {
                        state.feedNeedsSignIn -> "your feed"
                        state.feedHeadline != null -> state.feedHeadline
                        else -> "nothing new"
                    },
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
    return coverAt(url)
}

/** Decode a cover file, or null when it is missing or unreadable. */
private fun coverAt(path: String): ImageBitmap? {
    val file = File(path)
    if (!file.isFile) return null
    return BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
}

/**
 * How far into the book the reader is.
 *
 * A bar rather than a number: on a shelf of four covers the question is "which one was I in the
 * middle of", and length answers that faster than two digits do. The percentage still appears in
 * the caption when there is no author to show instead.
 */
@Composable
private fun ProgressUnder(progress: Double) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(3.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                )
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(progress.coerceIn(0.0, 1.0).toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
        )
    }
}
