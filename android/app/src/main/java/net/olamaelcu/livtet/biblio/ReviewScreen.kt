package net.olamaelcu.livtet.biblio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Read-only review permalink: the review, who wrote it, when, and the edition it is about.
 *
 * The record is read from the reviewer's PDS, so this page works before any index has seen the
 * review.
 */
@Composable
fun ReviewScreen(
    onBookClick: (did: String, rkey: String) -> Unit,
    onProfileClick: (actor: String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.isLoading -> LoadingState()
        state.error != null -> ErrorState(message = state.error ?: "", onRetry = viewModel::load)
        else -> {
            val review = state.review
            if (review == null) {
                ErrorState(message = "This review was not found.", onRetry = viewModel::load)
            } else {
                val reviewerName =
                    state.reviewer?.displayName?.takeIf { it.isNotBlank() }
                        ?: state.reviewer?.handle
                        ?: review.did
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.book?.let { book ->
                        item {
                            BookStrip(
                                coverUrl = book.coverUrl,
                                title = book.title,
                                author = book.author,
                                onClick = {
                                    parseAtUri(book.uri)?.let { atUri ->
                                        onBookClick(atUri.did, atUri.rkey)
                                    }
                                },
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.clickable { onProfileClick(review.did) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RemoteAvatar(
                                url = state.reviewer?.avatar,
                                label = reviewerName,
                                size = 44.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reviewerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                state.reviewer
                                    ?.handle
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { handle ->
                                        Text(
                                            text = "@$handle",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                            }
                            ratingLabel(review.record.rating)?.let { rating ->
                                Text(
                                    text = rating,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    item {
                        Text(text = review.record.text, style = MaterialTheme.typography.bodyLarge)
                    }

                    formatTimestamp(review.record.createdAt)?.let { date ->
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item {
                        TextButton(onClick = { onProfileClick(review.did) }) {
                            Text("More reviews by this reader")
                        }
                    }
                }
            }
        }
    }
}
