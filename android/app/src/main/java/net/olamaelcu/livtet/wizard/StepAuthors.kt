package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilledTonalButton
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

private val ROLES = listOf("author", "illustrator", "translator", "narrator")

private data class AuthorEntry(val name: String, val role: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepAuthors(data: WizardData, onBack: () -> Unit, onNext: (WizardData) -> Unit) {
    var authors by remember { mutableStateOf(listOf<AuthorEntry>()) }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("author") }
    var roleExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Authors") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (authors.isNotEmpty()) {
                Text(
                    text = "Added authors",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                authors.forEachIndexed { i, author ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(author.name, style = MaterialTheme.typography.bodyMedium)
                                Text(author.role, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(
                                onClick = {
                                    authors = authors.toMutableList().also { it.removeAt(i) }
                                }
                            ) {
                                Icon(Icons.Default.Close, "Remove")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Add author",
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
                        modifier = Modifier.menuAnchor().width(120.dp),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded)
                        },
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false },
                    ) {
                        ROLES.forEach { role ->
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
                FilledTonalButton(
                    onClick = {
                        if (newName.isBlank()) return@FilledTonalButton
                        authors = authors + AuthorEntry(newName.trim(), newRole)
                        newName = ""
                        newRole = "author"
                    }
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = { onNext(data.copy(currentStep = 2)) }) {
                    Text("Next: Review")
                }
            }
        }
    }
}