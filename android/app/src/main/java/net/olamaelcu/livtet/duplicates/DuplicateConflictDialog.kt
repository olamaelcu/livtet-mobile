package net.olamaelcu.livtet.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.olamaelcu.livtet.ffi.CrossWorkEditionDuplicateMobile
import net.olamaelcu.livtet.ffi.DuplicateCandidateMobile
import net.olamaelcu.livtet.ffi.EditionDuplicateCandidateMobile
import net.olamaelcu.livtet.ffi.EditionMergeConflictResolutionMobile
import net.olamaelcu.livtet.ffi.WorkFieldResolutionMobile
import net.olamaelcu.livtet.ffi.WorkMergeConflictResolutionMobile

sealed class MergeAction {
    data class MergeWorks(
        val primaryWorkId: net.olamaelcu.livtet.ffi.DbId,
        val duplicateWorkId: net.olamaelcu.livtet.ffi.DbId,
        val resolution: WorkMergeConflictResolutionMobile,
    ) : MergeAction()

    data class MergeEditions(
        val primaryEditionId: net.olamaelcu.livtet.ffi.DbId,
        val duplicateEditionId: net.olamaelcu.livtet.ffi.DbId,
        val resolution: EditionMergeConflictResolutionMobile,
    ) : MergeAction()

    data class MoveEdition(
        val editionId: net.olamaelcu.livtet.ffi.DbId,
        val targetWorkId: net.olamaelcu.livtet.ffi.DbId,
    ) : MergeAction()

    data object Cancel : MergeAction()
}

@Composable
fun WorkDuplicateDialog(candidate: DuplicateCandidateMobile, onAction: (MergeAction) -> Unit) {
    var description by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var tags by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var genres by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var subjects by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var publishers by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var seriesType by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var language by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var sortTitle by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }

    AlertDialog(
        onDismissRequest = { onAction(MergeAction.Cancel) },
        title = { Text("Merge works") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Primary: \"${candidate.primaryTitle}\"",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Duplicate: \"${candidate.duplicateTitle}\"",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Match: ${matchKindLabel(candidate.matchKind)} · ${(candidate.confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidate.matchingIdentifiers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Shared identifiers: ${candidate.matchingIdentifiers.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        val all = WorkFieldResolutionMobile.KEEP_PRIMARY
                        description = all
                        tags = all
                        genres = all
                        subjects = all
                        publishers = all
                        seriesType = all
                        language = all
                        sortTitle = all
                    }) { Text("Keep primary (all)") }
                    Spacer(modifier = Modifier.padding(end = 4.dp))
                    TextButton(onClick = {
                        val all = WorkFieldResolutionMobile.KEEP_BOTH
                        description = all
                        tags = all
                        genres = all
                        subjects = all
                        publishers = all
                        seriesType = all
                        language = all
                        sortTitle = all
                    }) { Text("Keep both (all)") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                WorkFieldRow("Description", description) { description = it }
                WorkFieldRow("Tags", tags) { tags = it }
                WorkFieldRow("Genres", genres) { genres = it }
                WorkFieldRow("Subjects", subjects) { subjects = it }
                WorkFieldRow("Publishers", publishers) { publishers = it }
                WorkFieldRow("Series type", seriesType) { seriesType = it }
                WorkFieldRow("Language", language) { language = it }
                WorkFieldRow("Sort title", sortTitle) { sortTitle = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        MergeAction.MergeWorks(
                            primaryWorkId = candidate.primaryWorkId,
                            duplicateWorkId = candidate.duplicateWorkId,
                            resolution = WorkMergeConflictResolutionMobile(
                                description = description,
                                tags = tags,
                                genres = genres,
                                subjects = subjects,
                                publishers = publishers,
                                seriesType = seriesType,
                                language = language,
                                sortTitle = sortTitle,
                            ),
                        )
                    )
                }
            ) { Text("Merge") }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MergeAction.Cancel) }) { Text("Cancel") }
        },
    )
}

@Composable
fun EditionDuplicateInWorkDialog(
    candidate: EditionDuplicateCandidateMobile,
    onAction: (MergeAction) -> Unit,
) {
    var title by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var publishedDate by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var format by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var language by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var notes by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }
    var description by remember { mutableStateOf<WorkFieldResolutionMobile?>(null) }

    AlertDialog(
        onDismissRequest = { onAction(MergeAction.Cancel) },
        title = { Text("Merge editions") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Match: ${matchKindLabel(candidate.matchKind)} · ${(candidate.confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidate.matchingIsbns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Shared ISBNs: ${candidate.matchingIsbns.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        val all = WorkFieldResolutionMobile.KEEP_PRIMARY
                        title = all
                        publishedDate = all
                        format = all
                        language = all
                        notes = all
                        description = all
                    }) { Text("Keep primary (all)") }
                    Spacer(modifier = Modifier.padding(end = 4.dp))
                    TextButton(onClick = {
                        val all = WorkFieldResolutionMobile.KEEP_BOTH
                        title = all
                        publishedDate = all
                        format = all
                        language = all
                        notes = all
                        description = all
                    }) { Text("Keep both (all)") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                WorkFieldRow("Title", title) { title = it }
                WorkFieldRow("Published date", publishedDate) { publishedDate = it }
                WorkFieldRow("Format", format) { format = it }
                WorkFieldRow("Language", language) { language = it }
                WorkFieldRow("Notes", notes) { notes = it }
                WorkFieldRow("Description", description) { description = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        MergeAction.MergeEditions(
                            primaryEditionId = candidate.primaryEditionId,
                            duplicateEditionId = candidate.duplicateEditionId,
                            resolution = EditionMergeConflictResolutionMobile(
                                title = title,
                                publishedDate = publishedDate,
                                format = format,
                                language = language,
                                notes = notes,
                                description = description,
                                pageCount = null,
                            ),
                        )
                    )
                }
            ) { Text("Merge") }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MergeAction.Cancel) }) { Text("Cancel") }
        },
    )
}

@Composable
fun CrossWorkEditionDialog(
    candidate: CrossWorkEditionDuplicateMobile,
    onAction: (MergeAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(MergeAction.Cancel) },
        title = { Text("Move edition") },
        text = {
            Column {
                Text(
                    text = "Match: ${matchKindLabel(candidate.matchKind)} · ${(candidate.confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidate.matchingIsbns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Shared ISBNs: ${candidate.matchingIsbns.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "This will move the duplicate edition under the primary work, combining both records into one.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        MergeAction.MoveEdition(
                            editionId = candidate.duplicateEditionId,
                            targetWorkId = candidate.primaryWorkId,
                        )
                    )
                }
            ) { Text("Move edition") }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MergeAction.Cancel) }) { Text("Cancel") }
        },
    )
}

@Composable
private fun WorkFieldRow(
    label: String,
    current: WorkFieldResolutionMobile?,
    onChange: (WorkFieldResolutionMobile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResolutionRadio("Primary", WorkFieldResolutionMobile.KEEP_PRIMARY, current, onChange)
                ResolutionRadio("Duplicate", WorkFieldResolutionMobile.KEEP_DUPLICATE, current, onChange)
                ResolutionRadio("Both", WorkFieldResolutionMobile.KEEP_BOTH, current, onChange)
            }
        }
    }
}

@Composable
private fun ResolutionRadio(
    label: String,
    value: WorkFieldResolutionMobile,
    current: WorkFieldResolutionMobile?,
    onChange: (WorkFieldResolutionMobile) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = current == value,
            onClick = { onChange(value) },
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun matchKindLabel(kind: net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile): String =
    when (kind) {
        net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile.ExactIsbn -> "Exact ISBN"
        is net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile.TitleAndAuthor ->
            "Title + author (≥ ${(kind.titleSimilarity * 100).toInt()}%)"
        is net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile.MultiIdentifier ->
            "Multi-identifier (≥ ${kind.minMatches})"
        net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile.PublisherTitleYear ->
            "Publisher + title + year"
        is net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile.Composite -> "Composite rule"
    }
