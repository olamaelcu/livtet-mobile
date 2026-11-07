package net.olamaelcu.livtet.biblio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.olamaelcu.livtet.core.designsystem.LivtetRadius

/**
 * Read-only book permalink: cover, title, authors, description, the edition's metadata, its
 * contributors, and the first page of reviews.
 *
 * There is deliberately no shelving, editing, sharing, or "more by this author" action — those are
 * services of the signed-in web app.
 */
@Composable
fun BookScreen(
    onReviewClick: (did: String, rkey: String) -> Unit,
    onProfileClick: (actor: String) -> Unit,
    viewModel: BookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.isLoading -> LoadingState()
        state.error != null -> ErrorState(message = state.error ?: "", onRetry = viewModel::load)
        else -> {
            val book = state.book
            if (book == null) {
                ErrorState(message = "This book was not found.", onRetry = viewModel::load)
            } else {
                BookContent(
                    state = state,
                    book = book,
                    onReviewClick = onReviewClick,
                    onProfileClick = onProfileClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookContent(
    state: BookUiState,
    book: BiblioBook,
    onReviewClick: (did: String, rkey: String) -> Unit,
    onProfileClick: (actor: String) -> Unit,
) {
    val author = book.authorLine()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row {
                BookCover(url = state.coverUrl, title = book.title, width = 120.dp, height = 180.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!author.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = author,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    book.format?.let { format ->
                        val label =
                            listOfNotNull(format.emoji, format.description)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                        if (label.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        book.description
            ?.takeIf { it.isNotBlank() }
            ?.let { description ->
                item { Text(text = description, style = MaterialTheme.typography.bodyMedium) }
            }

        val metadata =
            listOfNotNull(
                "Pages" to book.pages?.toString(),
                "Published" to (book.publishDate ?: book.publishedYear?.toString()),
                "Language" to book.language,
                "ISBN" to book.isbn(),
            )
        if (metadata.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    metadata.forEach { (label, value) ->
                        if (!value.isNullOrBlank()) {
                            Row {
                                Text(
                                    text = label,
                                    modifier = Modifier.width(88.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(text = value, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        val genres = book.genres.orEmpty().filter { it.name.isNotBlank() }
        if (genres.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { genre ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(LivtetRadius.m),
                        ) {
                            Text(
                                text =
                                    listOfNotNull(genre.emoji, genre.name)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" "),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        val contributors = book.contributors.orEmpty().filter { !it.person()?.name.isNullOrBlank() }
        if (contributors.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    contributors.forEach { contribution ->
                        val role = contribution.role.orEmpty().replace('_', ' ')
                        Text(
                            text =
                                listOfNotNull(
                                    contribution.person()?.name,
                                    role.takeIf { it.isNotBlank() },
                                )
                                    .joinToString(" — "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { BlockDivider() }

        item { SectionLabel("Reviews") }

        if (state.reviews.isEmpty()) {
            item {
                Text(
                    text = "No reviews yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.reviews, key = { it.uri }) { review ->
                val reviewer = state.reviewers[review.did]
                ReviewCard(
                    review = review,
                    reviewer = reviewer,
                    book = null,
                    onReviewClick = {
                        onReviewClick(review.did, parseAtUri(review.uri)?.rkey ?: "")
                    },
                    onProfileClick = { onProfileClick(review.did) },
                    onBookClick = null,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
