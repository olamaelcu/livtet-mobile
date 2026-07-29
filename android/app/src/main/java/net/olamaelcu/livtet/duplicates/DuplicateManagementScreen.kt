package net.olamaelcu.livtet.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.Bridge
import net.olamaelcu.livtet.ffi.CrossWorkEditionDuplicateMobile
import net.olamaelcu.livtet.ffi.DuplicateCandidateMobile
import net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile
import net.olamaelcu.livtet.ffi.EditionDuplicateCandidateMobile
import net.olamaelcu.livtet.ffi.MergeResultMobile

private enum class DuplicateTab(val title: String) {
    Works("Works"),
    EditionsInWork("Editions (in work)"),
    CrossWorkEditions("Cross-Work Editions"),
}

private val DefaultMatchKinds: List<DuplicateMatchKindMobile> = listOf(
    DuplicateMatchKindMobile.ExactIsbn,
    DuplicateMatchKindMobile.TitleAndAuthor(titleSimilarity = 0.85f),
    DuplicateMatchKindMobile.MultiIdentifier(minMatches = 2u),
    DuplicateMatchKindMobile.PublisherTitleYear,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateManagementScreen(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(DuplicateTab.Works) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var workCandidates by remember { mutableStateOf<List<DuplicateCandidateMobile>>(emptyList()) }
    var editionInWorkCandidates by remember {
        mutableStateOf<List<EditionDuplicateCandidateMobile>>(emptyList())
    }
    var crossWorkEditionCandidates by remember {
        mutableStateOf<List<CrossWorkEditionDuplicateMobile>>(emptyList())
    }

    var workCandidateToMerge by remember { mutableStateOf<DuplicateCandidateMobile?>(null) }
    var editionCandidateToMerge by remember {
        mutableStateOf<EditionDuplicateCandidateMobile?>(null)
    }
    var crossWorkCandidateToMove by remember {
        mutableStateOf<CrossWorkEditionDuplicateMobile?>(null)
    }

    fun rescan() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                workCandidates = Bridge.findDuplicateWorks(
                    matchKinds = DefaultMatchKinds,
                    minConfidence = 0.6f,
                )
                crossWorkEditionCandidates = Bridge.findCrossWorkEditionDuplicates(
                    matchKinds = DefaultMatchKinds,
                    minConfidence = 0.6f,
                )
                editionInWorkCandidates = aggregateEditionInWork(
                    workCandidates.flatMap { listOf(it.primaryWorkId, it.duplicateWorkId) }
                        .distinct()
                )
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { rescan() }

    fun runMergeAction(action: MergeAction) {
        scope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null
            try {
                when (action) {
                    is MergeAction.MergeWorks -> {
                        val result = Bridge.mergeWorks(
                            primaryWorkId = action.primaryWorkId,
                            duplicateWorkId = action.duplicateWorkId,
                            conflictResolution = action.resolution,
                        )
                        successMessage = mergeResultLabel(result)
                        workCandidateToMerge = null
                    }
                    is MergeAction.MergeEditions -> {
                        val result = Bridge.mergeEditions(
                            primaryEditionId = action.primaryEditionId,
                            duplicateEditionId = action.duplicateEditionId,
                            conflictResolution = action.resolution,
                        )
                        successMessage = mergeResultLabel(result)
                        editionCandidateToMerge = null
                    }
                    is MergeAction.MoveEdition -> {
                        Bridge.moveEditionToWork(
                            editionId = action.editionId,
                            targetWorkId = action.targetWorkId,
                        )
                        successMessage = "Moved edition to primary work"
                        crossWorkCandidateToMove = null
                    }
                    MergeAction.Cancel -> {
                        workCandidateToMerge = null
                        editionCandidateToMerge = null
                        crossWorkCandidateToMove = null
                    }
                }
                rescan()
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Manage Duplicates") },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { rescan() }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, "Rescan")
                }
            },
        )

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            DuplicateTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) },
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (selectedTab) {
                DuplicateTab.Works -> WorksList(
                    candidates = workCandidates,
                    onPick = { workCandidateToMerge = it },
                )
                DuplicateTab.EditionsInWork -> EditionsInWorkList(
                    candidates = editionInWorkCandidates,
                    onPick = { editionCandidateToMerge = it },
                )
                DuplicateTab.CrossWorkEditions -> CrossWorkEditionsList(
                    candidates = crossWorkEditionCandidates,
                    onPick = { crossWorkCandidateToMove = it },
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            successMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    workCandidateToMerge?.let { candidate ->
        WorkDuplicateDialog(
            candidate = candidate,
            onAction = { runMergeAction(it) },
        )
    }

    editionCandidateToMerge?.let { candidate ->
        EditionDuplicateInWorkDialog(
            candidate = candidate,
            onAction = { runMergeAction(it) },
        )
    }

    crossWorkCandidateToMove?.let { candidate ->
        CrossWorkEditionDialog(
            candidate = candidate,
            onAction = { runMergeAction(it) },
        )
    }
}

private suspend fun aggregateEditionInWork(
    workIds: List<net.olamaelcu.livtet.ffi.DbId>,
): List<EditionDuplicateCandidateMobile> {
    if (workIds.isEmpty()) return emptyList()
    val all = mutableListOf<EditionDuplicateCandidateMobile>()
    for (workId in workIds) {
        try {
            val rows = Bridge.findDuplicateEditionsInWork(
                workId = workId,
                matchKinds = listOf(DuplicateMatchKindMobile.ExactIsbn),
                minConfidence = 0.6f,
            )
            all += rows
        } catch (_: Exception) {
            // skip work-level failures, keep collecting
        }
    }
    return all
}

@Composable
private fun WorksList(
    candidates: List<DuplicateCandidateMobile>,
    onPick: (DuplicateCandidateMobile) -> Unit,
) {
    if (candidates.isEmpty()) {
        EmptyState("No duplicate works found.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(candidates, key = { it.primaryWorkId.toString() + it.duplicateWorkId.toString() }) { c ->
            WorkCandidateRow(candidate = c, onClick = { onPick(c) })
        }
    }
}

@Composable
private fun EditionsInWorkList(
    candidates: List<EditionDuplicateCandidateMobile>,
    onPick: (EditionDuplicateCandidateMobile) -> Unit,
) {
    if (candidates.isEmpty()) {
        EmptyState("No duplicate editions found within a single work.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(candidates, key = { it.primaryEditionId.toString() + it.duplicateEditionId.toString() }) { c ->
            EditionInWorkRow(candidate = c, onClick = { onPick(c) })
        }
    }
}

@Composable
private fun CrossWorkEditionsList(
    candidates: List<CrossWorkEditionDuplicateMobile>,
    onPick: (CrossWorkEditionDuplicateMobile) -> Unit,
) {
    if (candidates.isEmpty()) {
        EmptyState("No cross-work edition duplicates found.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(candidates, key = { it.primaryEditionId.toString() + it.duplicateEditionId.toString() }) { c ->
            CrossWorkEditionRow(candidate = c, onClick = { onPick(c) })
        }
    }
}

@Composable
private fun WorkCandidateRow(candidate: DuplicateCandidateMobile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Primary: ${candidate.primaryTitle}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Duplicate: ${candidate.duplicateTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (candidate.matchingIdentifiers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Shared identifiers: ${candidate.matchingIdentifiers.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${matchKindLabel(candidate.matchKind)} · ${(candidate.confidence * 100).toInt()}% confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun EditionInWorkRow(
    candidate: EditionDuplicateCandidateMobile,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Editions in same work",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.matchingIsbns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Shared ISBNs: ${candidate.matchingIsbns.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${matchKindLabel(candidate.matchKind)} · ${(candidate.confidence * 100).toInt()}% confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun CrossWorkEditionRow(
    candidate: CrossWorkEditionDuplicateMobile,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Cross-work edition duplicate",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.matchingIsbns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Shared ISBNs: ${candidate.matchingIsbns.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${matchKindLabel(candidate.matchKind)} · ${(candidate.confidence * 100).toInt()}% confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onClick) { Text("Move edition to primary work") }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun mergeResultLabel(result: MergeResultMobile): String {
    val parts = mutableListOf<String>()
    if (result.movedEditions > 0u) parts += "${result.movedEditions} editions"
    if (result.movedIdentifiers > 0u) parts += "${result.movedIdentifiers} identifiers"
    if (result.movedInventory > 0u) parts += "${result.movedInventory} inventory"
    if (result.movedReadingProgress > 0u) parts += "${result.movedReadingProgress} progress"
    if (result.deletedWork) parts += "work deleted"
    if (result.deletedEdition) parts += "edition deleted"
    return if (parts.isEmpty()) "Merge complete" else "Merged: ${parts.joinToString(", ")}"
}

private fun matchKindLabel(kind: DuplicateMatchKindMobile): String =
    when (kind) {
        DuplicateMatchKindMobile.ExactIsbn -> "Exact ISBN"
        is DuplicateMatchKindMobile.TitleAndAuthor ->
            "Title + author (≥ ${(kind.titleSimilarity * 100).toInt()}%)"
        is DuplicateMatchKindMobile.MultiIdentifier ->
            "Multi-identifier (≥ ${kind.minMatches})"
        DuplicateMatchKindMobile.PublisherTitleYear ->
            "Publisher + title + year"
        is DuplicateMatchKindMobile.Composite -> "Composite rule"
    }
