package net.olamaelcu.livtet.wizard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * Step 1 of the AddBook wizard. The user enters the title (required)
 * and a cover (required) — sourced from one of three places:
 *
 *  1. An online search result (pre-fills title and cover)
 *  2. A manually-typed URL
 *  3. The OS photo picker (CoverSource.pendingLocal)
 *  4. The camera (CoverSource.remote with the camera-roll URL)
 *
 * In Phase 1 the cover is stored as a URL only; the download path that
 * the wizard would otherwise call through `bridge.downloadImage` +
 * `setEditionCover` is deferred until Phase 2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1TitleAndCover(
    viewModel: AddBookWizardViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val canContinue = remember(state) { viewModel.canContinueFromTitleAndCover }
    var coverMenuExpanded by remember { mutableStateOf(false) }
    var coverUrlInput by remember(state.cover) {
        mutableStateOf(
            when (val c = state.cover) {
                is CoverSource.Remote -> c.url
                else -> ""
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Title & Cover") },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Search by title or ISBN...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Icon(Icons.Default.Search, "Search")
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (searchError != null) {
                Text(
                    text = searchError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (state.onlineResults.isNotEmpty()) {
                    OnlineResultsList(
                        results = state.onlineResults,
                        onSelect = viewModel::onSearchResultSelected,
                    )
                } else if (!isSearching && state.searchQuery.trim().length >= 3) {
                    Text(
                        text = "No results found. Enter the title and cover manually below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            Divider()

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text("Title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverPreview(state.cover, modifier = Modifier.size(width = 72.dp, height = 96.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cover *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        FilledTonalButton(
                            onClick = { coverMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cover source")
                        }
                        DropdownMenu(
                            expanded = coverMenuExpanded,
                            onDismissRequest = { coverMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Choose from Photos") },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                                onClick = { coverMenuExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Take Photo") },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, null) },
                                onClick = { coverMenuExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear") },
                                leadingIcon = { Icon(Icons.Default.Image, null) },
                                onClick = {
                                    coverMenuExpanded = false
                                    viewModel.onCoverChanged(null)
                                    coverUrlInput = ""
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = coverUrlInput,
                onValueChange = { value ->
                    coverUrlInput = value
                    val trimmed = value.trim()
                    viewModel.onCoverChanged(
                        if (trimmed.isEmpty()) null else CoverSource.Remote(trimmed)
                    )
                },
                label = { Text("Or paste an image URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { viewModel.goToNext() },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue: Contributors")
            }
        }
    }
}

@Composable
private fun OnlineResultsList(
    results: List<ProviderResult>,
    onSelect: (ProviderResult) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(results, key = { it.title + it.source }) { result ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(result) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    CoverPreview(
                        source = result.coverUrl?.let { CoverSource.Remote(it) },
                        modifier = Modifier.size(width = 48.dp, height = 64.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (result.authors.isNotEmpty()) {
                            Text(
                                text = result.authors.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        if (result.year != null || result.isbn != null) {
                            Text(
                                text = buildString {
                                    if (result.year != null) append(result.year)
                                    if (result.isbn != null) {
                                        if (isNotEmpty()) append(" · ")
                                        append("ISBN: ${result.isbn}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        if (result.source.isNotBlank()) {
                            Text(
                                text = "via ${result.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverPreview(source: CoverSource?, modifier: Modifier = Modifier) {
    val url = source?.displayUrl
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = "Cover preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    CoverPlaceholder()
                },
            )
        } else {
            CoverPlaceholder()
        }
    }
}

@Composable
private fun CoverPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}
