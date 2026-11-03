package net.olamaelcu.livtet.biblio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Add-book search over `community.lexicon.book.searchEditions`.
 *
 * Each result shows the edition's title, cover, and author line. "Add" writes the
 * work, its edition, and the edition's identifiers through the FFI, then asks the
 * caller to close and reload the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEditionsScreen(
    onDismiss: () -> Unit,
    onImported: () -> Unit,
    viewModel: SearchEditionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.importedUri) {
        if (state.importedUri != null) {
            viewModel.consumeImported()
            onImported()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a book") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search editions") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { viewModel.submit() }
                    ),
            )

            if (state.importError != null) {
                Text(
                    text = state.importError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when {
                state.isLoading && state.editions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = viewModel::retry) { Text("Try again") }
                    }
                }
                state.editions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No editions found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.editions, key = { it.uri }) { edition ->
                            EditionResultRow(
                                edition = edition,
                                isImporting = state.importingUri == edition.uri,
                                onClick = { viewModel.import(edition) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditionResultRow(edition: BiblioBook, isImporting: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            url = edition.coverImageUrl,
            title = edition.title,
            width = 56.dp,
            height = 84.dp,
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = edition.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val author = edition.authorLine()
            if (author != null) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val year = edition.publishedYear
            if (year != null) {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (isImporting) "Adding…" else "Add")
            }
        }
    }
}
