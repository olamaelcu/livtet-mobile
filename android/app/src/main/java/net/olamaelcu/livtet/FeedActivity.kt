package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.designsystem.LivtetTheme

/**
 * The reader's feed, as the platform serves it.
 *
 * The platform route takes either a browser cookie or a bearer token; this app has no cookie, so
 * the screen exists only when a sign-in succeeded. Signed out it says so and offers the sign-in,
 * rather than showing an empty list that looks like a feed with nothing in it.
 *
 * The list is shaped before it is drawn: see [timeline] for why days are headed and shelvings are
 * collapsed. The screen only owns the fetch, the paging and the drawing.
 */
class FeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LivtetTheme(darkTheme = isSystemInDarkTheme()) { FeedRoute(onBack = { finish() }) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, FeedActivity::class.java)
    }
}

/** One request's worth of entries; the list pages itself from the cursor. */
private const val FEED_PAGE = 30

/** How near the last row the scroll must come before the next page is asked for. */
private const val INFINITE_SCROLL_THRESHOLD = 4

/** One post's books, captured when the reader taps its card. */
private data class FeedPost(
    val author: String?,
    val headline: String,
    val time: String?,
    val entries: List<FeedEntry>,
)

private sealed interface FeedState {
    data object SignedOut : FeedState

    data object Loading : FeedState

    data class Ready(
        val entries: List<FeedEntry>,
        val cursor: String?,
        val loadingMore: Boolean = false,
        val moreFailure: String? = null,
    ) : FeedState

    data class Failed(val message: String) : FeedState
}

@Composable
private fun FeedRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state: FeedState by remember {
        mutableStateOf(
            if (PlatformAuth.isSignedIn(context)) FeedState.Loading else FeedState.SignedOut
        )
    }
    var reloads by remember { mutableIntStateOf(0) }
    var post by remember { mutableStateOf<FeedPost?>(null) }

    /** A failed page is told apart from a failed transcript: only the first replaces the list. */
    fun failure(error: Throwable): FeedState =
        if (
            error is FeedClient.FeedError && error.kind == FeedClient.FeedError.Kind.SESSION_EXPIRED
        ) {
            FeedState.SignedOut
        } else {
            FeedState.Failed(error.message ?: error.toString())
        }

    fun loadMore() {
        val ready = state as? FeedState.Ready ?: return
        val cursor = ready.cursor ?: return
        if (ready.loadingMore) return
        state = ready.copy(loadingMore = true, moreFailure = null)
        scope.launch {
            val page = FeedClient.page(context, limit = FEED_PAGE, cursor = cursor)
            val current = state as? FeedState.Ready ?: return@launch
            state =
                page.fold(
                    onSuccess = { next ->
                        // A page can repeat a record the previous one ended on.
                        val seen = current.entries.mapNotNull { it.uri }.toSet()
                        current.copy(
                            entries =
                                current.entries +
                                    next.entries.filter { it.uri == null || it.uri !in seen },
                            cursor = next.cursor,
                            loadingMore = false,
                            moreFailure = null,
                        )
                    },
                    onFailure = { error ->
                        val replaced = failure(error)
                        if (replaced is FeedState.Failed) {
                            current.copy(loadingMore = false, moreFailure = replaced.message)
                        } else {
                            replaced
                        }
                    },
                )
        }
    }

    LaunchedEffect(reloads) {
        if (!PlatformAuth.isSignedIn(context)) {
            state = FeedState.SignedOut
            return@LaunchedEffect
        }
        state = FeedState.Loading
        state =
            FeedClient.page(context, limit = FEED_PAGE)
                .fold(
                    onSuccess = { page -> FeedState.Ready(page.entries, page.cursor) },
                    onFailure = { failure(it) },
                )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().appHeaderInset()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("\u2039  Back") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Feed", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                if (PlatformAuth.isSignedIn(context)) {
                    TextButton(
                        onClick = {
                            PlatformAuth.signOut(context)
                            reloads++
                        }
                    ) {
                        Text("Sign out")
                    }
                } else {
                    Spacer(modifier = Modifier.padding(end = 8.dp))
                }
            }

            when (val current = state) {
                FeedState.SignedOut ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Read what the people you follow are reading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text =
                                "Signing in asks the platform for your feed only \u2014 no library access, no reading position.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SignInButton(context)
                    }

                FeedState.Loading ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }

                is FeedState.Failed ->
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { reloads++ }) { Text("Try again") }
                    }

                is FeedState.Ready ->
                    if (current.entries.isEmpty()) {
                        Text(
                            text =
                                "Nothing here yet. Follow a reader on the platform to fill this.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    } else {
                        FeedRows(state = current, onLoadMore = ::loadMore, onOpen = { post = it })
                    }
            }
        }

        post?.let { open -> FeedPostSheet(post = open, onDismiss = { post = null }) }
    }
}

@Composable
private fun FeedRows(state: FeedState.Ready, onLoadMore: () -> Unit, onOpen: (FeedPost) -> Unit) {
    val rows = remember(state.entries) { timeline(state.entries) }
    val today = remember(rows) { LocalDate.now() }
    // One clock reading per list, so every card in a frame agrees on what "2 h" means.
    val now = remember(rows) { Instant.now() }
    val listState = rememberLazyListState()

    // The feed pages itself: reaching the last few rows asks for the next page, and the scroll's
    // own settling is what re-arms the request once the rows arrive. A failure stops it — only the
    // footer's retry continues, so an offline reader is not hammering the route on every fling.
    LaunchedEffect(listState) {
        snapshotFlow {
                val layout = listState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                last to layout.totalItemsCount
            }
            .collect { (last, total) ->
                val nearEnd = total > 0 && last >= total - INFINITE_SCROLL_THRESHOLD
                if (nearEnd && !state.loadingMore && state.moreFailure == null) onLoadMore()
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is FeedHeader ->
                    Text(
                        text = dayLabel(row.day, today),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )

                is FeedEvent ->
                    FeedEventCard(row.entry, now) {
                        onOpen(
                            FeedPost(
                                author = row.entry.author,
                                headline = verb(row.entry),
                                time = relativeTime(row.entry.createdAt, now),
                                entries = listOf(row.entry),
                            )
                        )
                    }

                is FeedRun ->
                    FeedRunCard(row, now) {
                        onOpen(
                            FeedPost(
                                author = row.author,
                                headline = "${row.entries.size} books ${runVerb(row.status)}",
                                time = relativeTime(row.entries.first().createdAt, now),
                                entries = row.entries,
                            )
                        )
                    }
            }
        }
        item(key = "footer") { FeedFooter(state, onLoadMore) }
    }
}

/**
 * The end of the list: a spinner while the next page is in flight, and nothing at all once the
 * generator runs out. A failed page keeps a way forward, because nothing else would retry it.
 */
@Composable
private fun FeedFooter(state: FeedState.Ready, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.loadingMore -> CircularProgressIndicator(strokeWidth = 3.dp)
            state.moreFailure != null ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.moreFailure,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onLoadMore) { Text("Try again") }
                }
        }
    }
}

/** One record: a shelving, a review, or a recommendation. */
@Composable
private fun FeedEventCard(entry: FeedEntry, now: Instant, onOpen: () -> Unit) {
    FeedCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Cover(entry, Modifier.width(44.dp).height(66.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ByLine(entry.author, relativeTime(entry.createdAt, now))
                Text(
                    text = verb(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                entry.bookTitle?.let { title ->
                    Text(
                        text = title,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                entry.rating?.let { rating ->
                    Text(
                        text = "${formatRating(rating)} / 10",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
                entry.text?.let { review ->
                    Text(
                        text = review,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A burst of one reader shelving the same way — a series, usually.
 *
 * The strip carries the covers, because the titles are what the card is about; the count carries
 * the rest, so the card keeps the height of one shelving however many it covers.
 */
@Composable
private fun FeedRunCard(run: FeedRun, now: Instant, onOpen: () -> Unit) {
    FeedCard(onClick = onOpen) {
        ByLine(run.author, relativeTime(run.entries.first().createdAt, now))
        Text(
            text = "${run.entries.size} books ${runVerb(run.status)}",
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            run.entries.take(FEED_RUN_COVERS).forEach { entry ->
                Cover(entry, Modifier.width(30.dp).height(45.dp))
            }
            val rest = run.entries.size - FEED_RUN_COVERS
            if (rest > 0) {
                Text(
                    text = "+$rest",
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "See all books",
                modifier = Modifier.padding(bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The author and the time, on one line, with the author yielding first: a handle is the least
 * useful part of the card and the only part that can be cut without losing what happened.
 */
@Composable
private fun ByLine(author: String?, time: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = displayHandle(author),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        time?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

/** The book's cover, or its initial when the catalog has no cover for it. */
@Composable
private fun Cover(entry: FeedEntry, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // The initial is drawn under the image, not instead of it: a cover that fails to load
        // (the catalog's hosts do rate-limit) then leaves the letter rather than an empty box.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = entry.bookTitle?.take(1)?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.bookCoverUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = entry.bookTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun FeedCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

/**
 * Every book one post names, hydrated from Bibliograph.
 *
 * The post's own fields draw first: a sheet that waits for the network before showing the books it
 * already knows would look broken on a slow connection, so the catalog's description and
 * contributors fill in behind what the feed carried.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPostSheet(post: FeedPost, onDismiss: () -> Unit) {
    val uris = remember(post.entries) { post.entries.mapNotNull { it.bookUri }.distinct() }
    var books by remember(post.entries) { mutableStateOf<Map<String, CatalogBook>>(emptyMap()) }
    var failed by remember(post.entries) { mutableStateOf(false) }
    LaunchedEffect(uris) {
        if (uris.isEmpty()) return@LaunchedEffect
        runCatching { CatalogClient.books(uris) { books = it } }.onFailure { failed = true }
    }
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayHandle(post.author),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                post.time?.let {
                    Spacer(Modifier.weight(1f))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            Text(
                text = post.headline,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            // A single post is about one book, so its own review belongs here rather than a tap
            // away: the rating and the text the card truncated.
            post.entries.singleOrNull()?.let { entry ->
                entry.rating?.let { rating ->
                    Text(
                        text = "${formatRating(rating)} / 10",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                    )
                }
                entry.text?.let { review ->
                    Text(
                        text = review,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(post.entries, key = { it.uri ?: it.bookTitle ?: it.hashCode().toString() }) {
                    entry ->
                    SheetBook(entry = entry, book = entry.bookUri?.let { books[it] })
                }
                if (failed) {
                    item(key = "hydrate-failure") {
                        Text(
                            text = "The catalog did not answer; showing what the post carried.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** One book in a post: the feed's title and cover, then the catalog's contributors and blurb. */
@Composable
private fun SheetBook(entry: FeedEntry, book: CatalogBook?) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    Row(modifier = Modifier.fillMaxWidth()) {
        Cover(
            entry = book?.coverUrl?.let { entry.copy(bookCoverUrl = it) } ?: entry,
            modifier = Modifier.width(56.dp).height(84.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book?.title ?: entry.bookTitle ?: "A book",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val authors = book?.authors.orEmpty()
            if (authors.isNotEmpty()) {
                Text(
                    text = authors.joinToString("; "),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            val meta =
                listOfNotNull(
                        book?.publishedDate?.take(4)?.takeIf { it.isNotBlank() },
                        book?.pages?.let { "$it pages" },
                    )
                    .joinToString(" \u00b7 ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
            book?.description?.let { description ->
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Start the browser sign-in.
 *
 * The authorize page belongs in a browser, not a webview: it is the platform's own login, and a
 * webview would have no cookie to give it. The redirect comes back through the `livtet://` filter
 * on [MainActivity].
 */
@Composable
internal fun SignInButton(context: Context) {
    TextButton(
        onClick = {
            // The pending verifier is created here, not during composition: each
            // recomposition would otherwise replace the one the callback needs.
            val signIn = PlatformAuth.beginSignIn(context)
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(signIn.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    ) {
        Text("Sign in to Livtet")
    }
}
