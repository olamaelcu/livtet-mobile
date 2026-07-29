package net.olamaelcu.livtet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Library screen.
 *
 * The full Library UI used `Bridge.listBooks` (returning
 * `List<Book>`), `Bridge.getDistinctFormats`,
 * `Bridge.getDistinctLanguages`, and
 * `Bridge.getDistinctWorkStatuses` to render a paginated book
 * list with filter chips. None of those FFI calls or types are
 * present in the current `core/livtet-ffi` crate, so the screen
 * is reduced to an empty-state placeholder until upstream
 * catches up.
 */
@Composable
fun LibraryScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "📚",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "Your library",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Book list and filters are unavailable in this build — the Rust core does not yet expose listBooks or related filter calls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}