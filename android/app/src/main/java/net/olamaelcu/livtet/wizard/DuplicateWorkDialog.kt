package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import net.olamaelcu.livtet.R
import net.olamaelcu.livtet.ffi.DbId
import net.olamaelcu.livtet.ffi.EditionSummary
import net.olamaelcu.livtet.ffi.ExistingWorkSummary

sealed class DuplicateAction {
    data class Replace(val workId: DbId) : DuplicateAction()
    data class NewEdition(val workId: DbId) : DuplicateAction()
    data class LinkIsbn(val editionId: DbId) : DuplicateAction()
    data object Cancel : DuplicateAction()
}

@Composable
fun DuplicateWorkDialog(
    summary: ExistingWorkSummary,
    onAction: (DuplicateAction) -> Unit,
) {
    var showEditionPicker by remember { mutableStateOf(false) }

    if (showEditionPicker) {
        EditionPickerDialog(
            editions = summary.editions,
            onPick = { editionId ->
                showEditionPicker = false
                onAction(DuplicateAction.LinkIsbn(editionId))
            },
            onDismiss = { showEditionPicker = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { onAction(DuplicateAction.Cancel) },
        title = { Text(stringResource(R.string.duplicate_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.duplicate_dialog_message_work,
                    summary.title,
                    summary.editionCount.toInt(),
                    summary.identifierCount.toInt(),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAction(DuplicateAction.Replace(summary.id)) }) {
                Text(stringResource(R.string.duplicate_dialog_action_replace))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { onAction(DuplicateAction.NewEdition(summary.id)) }) {
                    Text(stringResource(R.string.duplicate_dialog_action_new_edition))
                }
                TextButton(onClick = { showEditionPicker = true }) {
                    Text(stringResource(R.string.duplicate_dialog_action_link_isbn))
                }
                TextButton(onClick = { onAction(DuplicateAction.Cancel) }) {
                    Text(stringResource(R.string.duplicate_dialog_action_cancel))
                }
            }
        },
    )
}

@Composable
private fun EditionPickerDialog(
    editions: List<EditionSummary>,
    onPick: (DbId) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edition_picker_title)) },
        text = {
            Column {
                if (editions.isEmpty()) {
                    Text(stringResource(R.string.edition_picker_no_editions))
                }
                editions.forEachIndexed { index, edition ->
                    val label = edition.editionTitle
                        ?: "${stringResource(R.string.edition_edition_label)} ${index + 1}"
                    val isbnText = edition.existingIsbns.joinToString(", ")
                        .ifEmpty { stringResource(R.string.edition_no_isbn) }
                    TextButton(onClick = { onPick(edition.id) }) {
                        Text("$label — $isbnText")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.duplicate_dialog_action_cancel))
            }
        },
    )
}
