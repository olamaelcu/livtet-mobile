package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.Bridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepSearch(data: WizardData, onNext: (WizardData) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(data.title) }
    var searchResults by remember { mutableStateOf<List<ProviderResult>>(emptyList()) }
    var localResults by remember {
        mutableStateOf<List<net.olamaelcu.livtet.ffi.WorkSummary>>(emptyList())
    }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var providerError by remember { mutableStateOf<net.olamaelcu.livtet.ffi.MobileException.ProviderException?>(null) }
    var skipSearch by remember { mutableStateOf(false) }

    fun runSearch() {
        if (title.isBlank()) return
        isSearching = true
        errorMessage = null
        providerError = null
        scope.launch {
            try {
                localResults = Bridge.findWorksByTitlePrefix(title.trim(), 5)
            } catch (_: Exception) {}
            try {
                val hits = Bridge.searchProviders(title.trim())
                searchResults = hits.map { hit ->
                    val year = hit.publishedDate?.take(4)?.toIntOrNull()
                    ProviderResult(
                        title = hit.title,
                        authors = hit.authors,
                        isbn =
                            hit.identifiers
                                .firstOrNull { it.startsWith("urn:isbn:") }
                                ?.removePrefix("urn:isbn:"),
                        year = year,
                        publisher = hit.publisher,
                        source = hit.source,
                    )
                }
            } catch (e: net.olamaelcu.livtet.ffi.MobileException.ProviderException) {
                if (!skipSearch) providerError = e
            } catch (e: Exception) {
                if (!skipSearch) errorMessage = "Could not search online: ${e.message}"
            }
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            Bridge.initPlugins()
        } catch (_: Exception) {}
    }

    LaunchedEffect(title) {
        if (title.length >= 3 && !skipSearch) {
            delay(750)
            runSearch()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Add Book") },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Search by title or ISBN...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        FilledIconButton(onClick = { runSearch() }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            providerError?.let { err ->
                ProviderErrorCallout(
                    error = err,
                    onDismiss = { providerError = null },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (localResults.isNotEmpty()) {
                Text(
                    text = "Already in your library:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                localResults.take(3).forEach { work ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        Text(
                            text = work.title,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (searchResults.isNotEmpty()) {
                Text(
                    text = "Online results:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(searchResults) { result ->
                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .semantics { contentDescription = "ProviderResult-source" }
                                    .clickable {
                                    onNext(
                                        data.copy(
                                            title = result.title,
                                            isbn = result.isbn ?: "",
                                            publishedDate = result.year?.let { "$it-01-01" } ?: "",
                                            publisher = result.publisher ?: "",
                                            authors = result.authors.map { AuthorEntry(it) },
                                            currentStep = 1,
                                        )
                                    )
                                },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
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
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                            ),
                                    )
                                }
                                if (result.year != null || result.isbn != null) {
                                    Text(
                                        text =
                                            buildString {
                                                if (result.year != null) append(result.year)
                                                if (result.isbn != null) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append("ISBN: ${result.isbn}")
                                                }
                                            },
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.5f
                                            ),
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
            } else if (!isSearching && title.isNotBlank()) {
                Text(
                    text = "No results found. The title entered will be used for manual entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            TextButton(
                onClick = { skipSearch = true },
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text("Skip search and add manually")
            }

            TextButton(
                onClick = {
                    onNext(
                        data.copy(title = title.trim(), searchQuery = title.trim(), currentStep = 1)
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next: Authors")
            }
        }
    }
}

/// Callout that surfaces a structured [`MobileException.ProviderException`]
/// from the bridge. Each [`ProviderErrorCategory`] gets a distinct
/// variant and message; the user can dismiss the callout to retry.
@Composable
private fun ProviderErrorCallout(
    error: net.olamaelcu.livtet.ffi.MobileException.ProviderException,
    onDismiss: () -> Unit,
) {
    val category = error.category
    val message =
        when (category) {
            net.olamaelcu.livtet.ffi.ProviderErrorCategory.NEEDS_AUTH ->
                "Search needs authentication. Add an API key in Settings to use Google Books."
            net.olamaelcu.livtet.ffi.ProviderErrorCategory.RATE_LIMITED ->
                error.retryAfterSeconds
                    ?.let { "Rate limited — try again in $it seconds, or add an API key in Settings." }
                    ?: "Rate limited — try again shortly, or add an API key in Settings."
            net.olamaelcu.livtet.ffi.ProviderErrorCategory.TIMEOUT ->
                "Search timed out. Try again."
            net.olamaelcu.livtet.ffi.ProviderErrorCategory.NOT_FOUND ->
                "No results found."
            net.olamaelcu.livtet.ffi.ProviderErrorCategory.PROVIDER_DOWN ->
                "Search is having problems. Try again later."
        }
    Card(
        modifier =
            Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics {
                contentDescription = "ProviderErrorCallout"
            },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (category == net.olamaelcu.livtet.ffi.ProviderErrorCategory.NEEDS_AUTH ||
                        category == net.olamaelcu.livtet.ffi.ProviderErrorCategory.RATE_LIMITED
                    ) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}
