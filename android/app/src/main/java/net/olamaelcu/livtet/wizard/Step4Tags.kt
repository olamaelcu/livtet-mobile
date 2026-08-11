package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Step 4 — Tags. The final step in the linear 5-step flow. The user
 * adds optional tags. The "Save book" button is gated on
 * [AddBookWizardViewModel.isSaveAvailable] — in Phase 1 the underlying
 * Rust FFI save path is not yet exposed, so the step instead shows a
 * "Saving is coming soon" banner and offers the user the choice to
 * discard the wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step4Tags(
    viewModel: AddBookWizardViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tags") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = "Tags are free-form labels you can search and filter on. Skip this step if you don't want to tag this book.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))

            ChipInputRow(
                items = state.tags,
                placeholder = "Add a tag",
                onItemsChanged = viewModel::onTagsChanged,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            if (!viewModel.isSaveAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                SaveUnavailableBanner()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                if (viewModel.isSaveAvailable) {
                    TextButton(
                        onClick = { /* Phase 2: viewModel.save() */ },
                    ) { Text("Save book") }
                } else {
                    TextButton(onClick = { /* Phase 1: discard handled by the modal sheet dismiss */ }) {
                        Text("Discard")
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveUnavailableBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "Saving is coming soon",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "The Rust core does not yet expose the save path the wizard needs to write a book. The data you entered is held in this wizard only — discard it or close this sheet to leave the library unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
