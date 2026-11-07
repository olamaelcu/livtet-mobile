package net.olamaelcu.livtet.biblio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.olamaelcu.livtet.core.designsystem.LivtetRadius

/** Centred spinner for a screen that has nothing to show yet. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A failed load, with the one action that can help. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/** Small uppercase-ish heading that separates blocks inside a screen. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Circular avatar. Falls back to the first letter of [label] so a reader without a picture is still
 * identifiable.
 */
@Composable
fun RemoteAvatar(
    url: String?,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier =
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = label.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

/** A cover with a placeholder when the catalog has no image. */
@Composable
fun BookCover(
    url: String?,
    title: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(width = width, height = height)
                .clip(RoundedCornerShape(LivtetRadius.s))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The edition a review points at, as a tappable strip. */
@Composable
fun BookStrip(
    coverUrl: String?,
    title: String,
    author: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(LivtetRadius.m),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(url = coverUrl, title = title, width = 40.dp, height = 60.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!author.isNullOrBlank()) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One review, as the book page and the profile tabs show it.
 *
 * [onReviewClick] opens the review's permalink; [onBookClick] opens the edition, and is null where
 * the screen is already about that book.
 */
@Composable
fun ReviewCard(
    review: BiblioReview,
    reviewer: BiblioActor?,
    book: BookSummary?,
    onReviewClick: () -> Unit,
    onProfileClick: (() -> Unit)?,
    onBookClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val name = reviewer?.displayName?.takeIf { it.isNotBlank() } ?: reviewer?.handle ?: review.did
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onReviewClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(LivtetRadius.m),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier =
                    if (onProfileClick != null) {
                        Modifier.clickable(onClick = onProfileClick)
                    } else {
                        Modifier
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteAvatar(url = reviewer?.avatar, label = name, size = 36.dp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val handle = reviewer?.handle
                    if (!handle.isNullOrBlank()) {
                        Text(
                            text = "@$handle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ratingLabel(review.record.rating)?.let { rating ->
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = review.record.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            formatTimestamp(review.record.createdAt)?.let { date ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (book != null && onBookClick != null) {
                Spacer(Modifier.height(8.dp))
                BookStrip(
                    coverUrl = book.coverUrl,
                    title = book.title,
                    author = book.author,
                    onClick = onBookClick,
                )
            }
        }
    }
}

/** Divider used between the blocks of a detail screen. */
@Composable
fun BlockDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant)
}
