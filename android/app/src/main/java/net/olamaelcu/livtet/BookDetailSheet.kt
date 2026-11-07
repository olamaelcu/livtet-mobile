package net.olamaelcu.livtet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.olamaelcu.livtet.biblio.BookCover

/**
 * What the Library knows about one book, before its edition is read.
 *
 * The grid holds a work: a title, a cover, and the edition the work prefers. The rest of the drawer
 * lives on the edition, so it is fetched when the sheet opens.
 */
data class BookDetailTarget(val title: String, val coverUrl: String?, val editionId: String?)

/**
 * The book itself, opened from a cover tap — the mobile counterpart of the desktop's edition
 * drawer.
 *
 * Shows what the catalog holds for the edition (authors, description, format, language, publication
 * date, publishers, identifiers, and the file on disk) and the one action the phone has that the
 * desktop does not: handing the file to a reader app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailSheet(
    target: BookDetailTarget,
    onDismiss: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var detail by remember(target.editionId) { mutableStateOf<EditionDetailView?>(null) }
    var loading by remember(target.editionId) { mutableStateOf(target.editionId != null) }

    LaunchedEffect(target.editionId) {
        val id = target.editionId ?: return@LaunchedEffect
        detail = runCatching { Bridge.editionDetail(id) }.getOrNull()
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(
                    url = target.coverUrl,
                    title = target.title,
                    width = 104.dp,
                    height = 156.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = detail?.title ?: target.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    val authors = detail?.authors.orEmpty()
                    if (authors.isNotEmpty()) {
                        Text(
                            text = authors.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            detail?.let { edition ->
                val facts =
                    listOfNotNull(
                        edition.format?.let { "Format" to it },
                        edition.languageCode?.let { "Language" to it },
                        edition.publishedDate?.let { "Published" to it },
                        edition.publishers
                            .takeIf { it.isNotEmpty() }
                            ?.let {
                                (if (it.size == 1) "Publisher" else "Publishers") to
                                    it.joinToString(", ")
                            },
                        edition.identifiers
                            .takeIf { it.isNotEmpty() }
                            ?.let {
                                (if (it.size == 1) "Identifier" else "Identifiers") to
                                    it.joinToString("\n")
                            },
                        edition.fileSizeBytes?.let { "Size" to formatBytes(it) },
                    )
                facts.forEach { (label, value) -> FactRow(label, value) }

                edition.description
                    ?.takeIf { it.isNotBlank() }
                    ?.let { description ->
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(text = description, style = MaterialTheme.typography.bodyMedium)
                    }

                val filePath = edition.filePath
                if (filePath != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { onOpenFile(filePath) }) { Text("Open book") }
                    }
                } else {
                    Text(
                        text = "This edition has no file on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!loading && detail == null) {
                Text(
                    text = "No edition details are stored for this book yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.65f),
        )
    }
}

/** Byte count at the precision a reader cares about. */
private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
