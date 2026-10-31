package net.olamaelcu.livtet.biblio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.olamaelcu.livtet.AppLinkRouter

/**
 * Read-only profile permalink: identity, reading-status counts, and the tabs the platform publishes
 * (`/profile/{did}?tab=…` plus the shelf routes).
 *
 * Identity comes from the Bluesky AppView; reviews, shelving, and shelves come from the actor's own
 * PDS.
 */
@Composable
fun ProfileScreen(
    onBookClick: (did: String, rkey: String) -> Unit,
    onReviewClick: (did: String, rkey: String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.isLoading -> LoadingState()
        state.error != null -> ErrorState(message = state.error ?: "", onRetry = viewModel::load)
        else ->
            Column(modifier = Modifier.fillMaxSize()) {
                ProfileHeader(state)
                if (state.unavailable) {
                    Text(
                        text = "Reviews and shelves unavailable.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                ScrollableTabRow(
                    selectedTabIndex =
                        AppLinkRouter.TABS.indexOf(state.selectedTab).coerceAtLeast(0),
                    edgePadding = 8.dp,
                ) {
                    AppLinkRouter.TABS.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = {
                                if (tab == AppLinkRouter.TAB_SHELVES) {
                                    viewModel.selectShelf(state.selectedShelf)
                                } else {
                                    viewModel.selectTab(tab)
                                }
                            },
                            text = { Text(tabLabel(tab)) },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.selectedTab == AppLinkRouter.TAB_SHELVES) {
                        ShelvesTab(
                            state = state,
                            onSelectShelf = viewModel::selectShelf,
                            onShowAllShelves = { viewModel.selectShelf(null) },
                            onBookClick = onBookClick,
                        )
                    } else {
                        ReviewsTab(
                            state = state,
                            onBookClick = onBookClick,
                            onReviewClick = onReviewClick,
                        )
                    }
                }
            }
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        RemoteAvatar(url = state.actor?.avatar, label = state.displayName, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The header prints the handle under the display name, and only
            // under it: when there is no display name the handle already is
            // the heading.
            state.actor
                ?.handle
                ?.takeIf { it.isNotBlank() }
                ?.let { handle ->
                    if (state.actor?.displayName?.isNotBlank() == true) {
                        Text(
                            text = "@$handle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            state.actor
                ?.description
                ?.takeIf { it.isNotBlank() }
                ?.let { description ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            Spacer(Modifier.height(8.dp))
            // Five counters do not fit across a phone: scroll instead of
            // hyphenating "Abandoned".
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Stat("Total", state.stats.total)
                Stat("Finished", state.stats.finished)
                Stat("Reading", state.stats.reading)
                Stat("To read", state.stats.toRead)
                Stat("Abandoned", state.stats.abandoned)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ReviewsTab(
    state: ProfileUiState,
    onBookClick: (did: String, rkey: String) -> Unit,
    onReviewClick: (did: String, rkey: String) -> Unit,
) {
    val reviews = state.visibleReviews
    if (reviews.isEmpty()) {
        EmptyCopy(
            text =
                if (state.selectedTab == AppLinkRouter.TAB_REVIEWS) {
                    "No reviews yet."
                } else {
                    "No books with this status."
                }
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reviews, key = { it.uri }) { review ->
            val bookUri = review.record.bookUri()
            val book = bookUri?.let { state.reviewBooks[it] }
            ReviewCard(
                review = review,
                reviewer = state.reviewers[review.did],
                book = book,
                onReviewClick = { onReviewClick(review.did, parseAtUri(review.uri)?.rkey ?: "") },
                onProfileClick = null,
                onBookClick =
                    if (book != null && bookUri != null) {
                        { parseAtUri(bookUri)?.let { atUri -> onBookClick(atUri.did, atUri.rkey) } }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun ShelvesTab(
    state: ProfileUiState,
    onSelectShelf: (String?) -> Unit,
    onShowAllShelves: () -> Unit,
    onBookClick: (did: String, rkey: String) -> Unit,
) {
    if (state.selectedShelf == null) {
        if (state.shelves.isEmpty()) {
            EmptyCopy("No shelves yet.")
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.shelves, key = { it.rkey }) { shelf ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onSelectShelf(shelf.rkey) }
                            .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = shelf.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "(${shelf.bookCount})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    if (state.shelfBooksLoading) {
        LoadingState()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { TextButton(onClick = onShowAllShelves) { Text("All shelves") } }
        if (state.shelfBooks.isEmpty()) {
            item { EmptyCopy("Nothing on this shelf.") }
        } else {
            items(state.shelfBooks, key = { "${it.shelfRkey}/${it.bookUri ?: it.title}" }) { row ->
                val atUri = parseAtUri(row.bookUri)
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .then(
                                if (atUri != null) {
                                    Modifier.clickable { onBookClick(atUri.did, atUri.rkey) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 10.dp)
                ) {
                    Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
                    row.status
                        ?.takeIf { it.isNotBlank() }
                        ?.let { status ->
                            Text(
                                text = statusLabel(status),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun EmptyCopy(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Tab label for a link tab value. */
private fun tabLabel(tab: String): String =
    when (tab) {
        AppLinkRouter.TAB_REVIEWS -> "Reviews"
        AppLinkRouter.TAB_READING -> "Reading"
        AppLinkRouter.TAB_FINISHED -> "Finished"
        AppLinkRouter.TAB_TO_READ -> "To Read"
        AppLinkRouter.TAB_ABANDONED -> "Abandoned"
        AppLinkRouter.TAB_SHELVES -> "Shelves"
        else -> tab
    }

/** Human label for a `bookShelving.metadata.status` value. */
private fun statusLabel(status: String): String =
    when (status) {
        "reading" -> "Reading"
        "read" -> "Finished"
        "to-read" -> "To read"
        "dnf" -> "Abandoned"
        else -> status
    }
