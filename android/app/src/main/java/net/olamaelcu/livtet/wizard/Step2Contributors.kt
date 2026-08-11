package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.unit.dp

/**
 * Step 2 — Contributors. The user adds one or more contributors. At
 * least one contributor with role "author" is required to advance;
 * the other roles (illustrator, translator, narrator) are optional
 * and stackable on a single book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step2Contributors(
    viewModel: AddBookWizardViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val canContinue by remember(state.contributors) { viewModel.canContinueFromContributors }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("author") }
    var roleExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Contributors") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = "At least one author is required. Add illustrators, translators, and narrators as needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.contributors, key = { it.name + it.role }) { contributor ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contributor.name, style = MaterialTheme.typography.bodyMedium)
                                Text(contributor.role, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.onContributorsChanged(
                                        state.contributors.filterNot { it === contributor }
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Close, "Remove")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add contributor",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = it },
                ) {
                    OutlinedTextField(
                        value = newRole,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(120.dp),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded)
                        },
                    )
                    androidx.compose.material3.ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false },
                    ) {
                        CONTRIBUTOR_ROLES.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    newRole = role
                                    roleExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.onContributorsChanged(
                                state.contributors + ContributorEntry(name = trimmed, role = newRole)
                            )
                            newName = ""
                            newRole = "author"
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(
                    onClick = { viewModel.goToNext() },
                    enabled = canContinue,
                ) {
                    Text("Continue: Genres")
                }
            }
        }
    }
}
