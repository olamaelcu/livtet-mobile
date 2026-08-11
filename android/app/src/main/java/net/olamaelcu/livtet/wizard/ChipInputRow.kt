package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable chip input used for genres, subjects, and tags. The list of
 * items is owned by the caller; this composable only renders the chips
 * and forwards additions/removals through [onItemsChanged].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipInputRow(
    items: List<String>,
    placeholder: String,
    onItemsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        if (items.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    AssistChip(
                        onClick = { onItemsChanged(items.filterNot { it == item }) },
                        label = { Text(item) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove $item")
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = draft.trim()
                    if (trimmed.isNotEmpty() && trimmed !in items) {
                        onItemsChanged(items + trimmed)
                    }
                    draft = ""
                },
                enabled = draft.trim().isNotEmpty(),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    }
}
