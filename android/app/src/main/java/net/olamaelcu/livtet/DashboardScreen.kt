package net.olamaelcu.livtet

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.olamaelcu.livtet.branding.BodyFamily
import net.olamaelcu.livtet.branding.HeadingFamily
import net.olamaelcu.livtet.branding.LivtetColors
import net.olamaelcu.livtet.core.designsystem.LivtetRadius
import net.olamaelcu.livtet.ffi.DashboardStats
import net.olamaelcu.livtet.ffi.Greeting
import net.olamaelcu.livtet.ffi.RecentSearch
import net.olamaelcu.livtet.ffi.RecentlyReadBook

@Composable
fun DashboardScreen(onNavigateToLibrary: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var greeting by remember { mutableStateOf<Greeting?>(null) }
    var stats by remember { mutableStateOf<DashboardStats?>(null) }
    var recentBook by remember { mutableStateOf<RecentlyReadBook?>(null) }
    var recentSearches by remember { mutableStateOf<List<RecentSearch>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            greeting = Bridge.getGreeting()
            stats = Bridge.getDashboardStats()
            val books = Bridge.getRecentlyReadBooks(1)
            recentBook = books.firstOrNull()
            recentSearches = Bridge.getRecentSearches(5)
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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

            Text(
                text = "${g.author} - ${g.material}",
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = BodyFamily,
                        fontStyle = FontStyle.Italic,
                    ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (error != null) {
            Text(
                text = "Could not load dashboard: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (stats != null) {
            StatsRow(stats = stats!!)

            val s = stats
            if (s != null) {
                val now = System.currentTimeMillis()
                val firstReadingAt = s.firstReadingAtMillis
                val daysSinceFirst =
                    if (firstReadingAt != null) {
                        (now - firstReadingAt) / (1000L * 60 * 60 * 24)
                    } else 0L

                val showAddBook = s.totalBooks < 10L
                val showRecordReading = firstReadingAt == null || daysSinceFirst < 14L
                val showFinishBook = s.finishedBooks == 0L

                if (showAddBook || showRecordReading || showFinishBook) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (showAddBook) {
                    QuickActionCard(
                        emoji = "\uD83D\uDCDA",
                        title = "Add Your First Book",
                        description = "Build your library to get started",
                        progress = (s.totalBooks.toFloat() / 10f).coerceIn(0f, 1f),
                        progressLabel = "${s.totalBooks}/10 books",
                        onClick = onNavigateToLibrary,
                    )
                }

                if (showRecordReading) {
                    QuickActionCard(
                        emoji = "\uD83D\uDCD6",
                        title = "Record Your Reading",
                        description =
                            if (firstReadingAt == null) "Log your first reading session"
                            else "Keep your reading streak going",
                        progress = (daysSinceFirst.toFloat() / 14f).coerceIn(0f, 1f),
                        progressLabel = "$daysSinceFirst/14 days",
                        onClick = onNavigateToLibrary,
                    )
                }

                if (showFinishBook) {
                    QuickActionCard(
                        emoji = "\uD83C\uDFAF",
                        title = "Finish a Book",
                        description = "Complete a book to make progress",
                        progress = null,
                        progressLabel = null,
                        onClick = onNavigateToLibrary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (recentBook != null) {
            ContinueReadingCard(book = recentBook!!)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (recentSearches != null && recentSearches!!.isNotEmpty()) {
            RecentSearchesRow(searches = recentSearches!!)
            Spacer(modifier = Modifier.height(24.dp))
        }

        FeedPlaceholderCard()
    }
}

@Composable
private fun QuickActionCard(
    emoji: String,
    title: String,
    description: String,
    progress: Float?,
    progressLabel: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(LivtetRadius.l),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 24.sp)

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                if (progress != null && progressLabel != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = LivtetColors.Brand,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "\u203A",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun StatsRow(stats: DashboardStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(LivtetRadius.l),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Reading Stats",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = BodyFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(value = stats.totalBooks.toString(), label = "Books")
                StatItem(value = stats.booksInProgress.toString(), label = "In Progress")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(value = stats.finishedBooks.toString(), label = "Finished")
                StatItem(value = formatDuration(stats.totalReadingTimeSecs), label = "Reading")
            }
        }
    }
}

@Composable
private fun RowScope.StatItem(value: String, label: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontFamily = HeadingFamily,
                    fontWeight = FontWeight.Medium,
                ),
            color = LivtetColors.Brand,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall.copy(fontFamily = BodyFamily, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContinueReadingCard(book: RecentlyReadBook) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LivtetColors.Brand),
        shape = RoundedCornerShape(LivtetRadius.l),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Continue Reading",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )

            if (book.authorName != null) {
                Text(
                    text = book.authorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            @Suppress("DEPRECATION")
            LinearProgressIndicator(
                progress = book.progress.toFloat(),
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${(book.progress * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun RecentSearchesRow(searches: List<RecentSearch>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recent Searches",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            searches.take(5).forEach { search ->
                Card(
                    modifier = Modifier.weight(1f, fill = false),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    shape = RoundedCornerShape(LivtetRadius.l),
                ) {
                    Text(
                        text = search.query,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPlaceholderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(LivtetRadius.l),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "\uD83D\uDCF0", fontSize = 32.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Feed",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = HeadingFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text =
                    "Friend activity and recommendations will appear here once the social feed is ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Coming Soon",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = BodyFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    return if (hours > 0) "${hours}h" else "${seconds / 60}m"
}
