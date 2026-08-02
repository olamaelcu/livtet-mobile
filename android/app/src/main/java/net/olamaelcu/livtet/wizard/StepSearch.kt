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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.Bridge

/**
 * Search step of the Add Book wizard.
 *
 * The full FFI surface this step used to depend on (`findWorksByTitlePrefix` → `WorkSummary`,
 * `MobileException. ProviderException`, `ProviderErrorCategory`, `lookupIdentifier`,
 * `searchProviders` → `PluginHitMobile`) is partially missing from the current `core/livtet-ffi`
 * crate. Plugin search via `searchProviders` and `lookupIdentifier` is wired through [Bridge]; the
 * local-dedup (`WorkSummary`) and structured provider-error surfaces are stubbed until the upstream
 * FFI is restored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepSearch(data: WizardData, onNext: (WizardData) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(data.title) }
    var searchResults by remember { mutableStateOf<List<ProviderResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        if (title.isBlank()) return
        isSearching = true
        errorMessage = null
        scope.launch {
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
            } catch (e: Exception) {
                errorMessage = "Could not search online: ${e.message}"
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
        if (title.length >= 3) {
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
                                Modifier.fillMaxWidth().clickable {
                                    onNext(data.copy(title = result.title, currentStep = 1))
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
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.5f
                                            ),
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
                onClick = { onNext(data.copy(title = title.trim(), currentStep = 1)) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next: Authors")
            }
        }
    }
}
