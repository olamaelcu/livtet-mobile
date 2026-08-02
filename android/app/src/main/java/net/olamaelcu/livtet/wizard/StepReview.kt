package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Review step of the Add Book wizard.
 *
 * Previously this step called `Bridge.findWorkByIsbn`, `Bridge.createBookComplete`,
 * `Bridge.mergeReplaceWork`, `Bridge.createEditionForWork`, `Bridge.linkIsbnToExistingEdition`, and
 * resolved a `MobileException` hierarchy against the `ExistingWorkSummary` / `DbId` types. None of
 * those FFI calls or types are present in the current `core/livtet-ffi` crate, so the step is
 * reduced to a form-only preview until upstream catches up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepReview(data: WizardData, onBack: () -> Unit, onComplete: (WizardData) -> Unit) {
    var title by remember { mutableStateOf(data.title) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Review Book") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Saving books is unavailable in this build",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The Rust core does not yet expose createBook / createBookComplete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = { onComplete(data.copy(title = title.trim())) },
                enabled = title.isNotBlank(),
            ) {
                Text("Done")
            }
        }
    }
}
