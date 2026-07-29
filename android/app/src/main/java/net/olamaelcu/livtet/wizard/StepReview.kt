package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.Bridge
import net.olamaelcu.livtet.ffi.DbId
import net.olamaelcu.livtet.ffi.ExistingWorkSummary
import net.olamaelcu.livtet.ffi.LanguageInfo
import net.olamaelcu.livtet.ffi.MobileException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepReview(data: WizardData, onBack: () -> Unit, onComplete: (WizardData) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(data.title) }
    var description by remember { mutableStateOf(data.description) }
    var isbn by remember { mutableStateOf(data.isbn) }
    var pubDate by remember { mutableStateOf(data.publishedDate) }
    var publisher by remember { mutableStateOf(data.publisher) }

    var languages by remember { mutableStateOf<List<LanguageInfo>>(emptyList()) }
    var selectedLanguage by remember { mutableStateOf<DbId?>(data.languageId) }
    var langExpanded by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var duplicateSummary by remember { mutableStateOf<ExistingWorkSummary?>(null) }

    // Pre-check ISBN for duplicates when the step loads or ISBN changes
    LaunchedEffect(isbn) {
        val cleanIsbn = isbn.trim().replace("-", "")
        if (cleanIsbn.isNotBlank() && cleanIsbn.matches(Regex("^(?:ISBN-?1[03])?[: ]*([0-9X-]+)$"))) {
            scope.launch {
                try {
                    val existing = Bridge.findWorkByIsbn(cleanIsbn)
                    if (existing != null && duplicateSummary == null) {
                        duplicateSummary = existing
                    }
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            languages = Bridge.getDistinctLanguages()
        } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Review Book") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )

        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = isbn,
                onValueChange = { isbn = it },
                label = { Text("ISBN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = pubDate,
                    onValueChange = { pubDate = it },
                    label = { Text("Published (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it },
                ) {
                    val langName = languages.find { it.id == selectedLanguage }?.name ?: "Select"
                    OutlinedTextField(
                        value = langName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        modifier = Modifier.menuAnchor().width(140.dp),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded)
                        },
                    )
                    ExposedDropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false },
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.name) },
                                onClick = {
                                    selectedLanguage = lang.id
                                    langExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = publisher,
                onValueChange = { publisher = it },
                label = { Text("Publisher") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (data.authors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text =
                        "Authors: ${data.authors.joinToString(", ") { "${it.name} (${it.role})" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onBack) { Text("Edit authors") }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextButton(onClick = onBack) { Text("Back") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        // Input validation before attempting save
                        if (title.isBlank()) {
                            errorMessage = "Title is required"
                            return@TextButton
                        }
                        if (data.authors.isEmpty()) {
                            errorMessage = "At least one author is required"
                            return@TextButton
                        }
                        val cleanIsbn = isbn.trim().replace("-", "")
                        if (cleanIsbn.isNotBlank() && !cleanIsbn.matches(Regex("^(?:ISBN-?1[03])?[: ]*([0-9X-]+)$"))) {
                            errorMessage = "Invalid ISBN format"
                            return@TextButton
                        }
                        if (pubDate.isNotBlank() && !pubDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                            errorMessage = "Published date must be YYYY-MM-DD"
                            return@TextButton
                        }

                        isSaving = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val existing =
                                    if (cleanIsbn.isNotBlank()) {
                                        Bridge.findWorkByIsbn(cleanIsbn)
                                    } else {
                                        null
                                    }
                                if (existing != null) {
                                    duplicateSummary = existing
                                } else {
                                    saveBook(
                                        data,
                                        title,
                                        description,
                                        isbn,
                                        pubDate,
                                        selectedLanguage,
                                        publisher,
                                    )
                                    onComplete(data.copy(title = title.trim()))
                                }
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.Database) {
                                errorMessage = if (e.message.contains("UNIQUE constraint failed", ignoreCase = true)) {
                                    "This ISBN already exists in your library"
                                } else if (e.message.contains("constraint failed", ignoreCase = true)) {
                                    "Duplicate entry detected"
                                } else {
                                    "Database error: ${e.message}"
                                }
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.IsbnConflict) {
                                // Trigger duplicate dialog programmatically
                                val conflictWork = ExistingWorkSummary(
                                    id = e.workId,
                                    title = "",
                                    description = null,
                                    editionCount = 0u,
                                    identifierCount = 0u,
                                    existingIsbns = listOf(e.conflictingIsbn),
                                    editions = emptyList()
                                )
                                duplicateSummary = conflictWork
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.NotFound) {
                                errorMessage = "Referenced item not found: ${e.message}"
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException) {
                                errorMessage = "Save failed: ${e.message}"
                            } catch (e: Exception) {
                                errorMessage = "Unexpected error: ${e.message}"
                            }
                            isSaving = false
                        }
                    },
                    enabled = title.isNotBlank() && !isSaving,
                ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Create Book")
            }
        }
    }

    duplicateSummary?.let { summary ->
        DuplicateWorkDialog(
            summary = summary,
            onAction = { action ->
                scope.launch {
                    when (action) {
                        is DuplicateAction.Replace -> {
                            isSaving = true
                            try {
                                Bridge.mergeReplaceWork(
                                    workId = action.workId,
                                    newTitle = title.trim(),
                                    newDescription = description.trim().ifEmpty { null },
                                    newIsbn = isbn.trim().replace("-", ""),
                                    newEditionTitle = title.trim(),
                                    publishedDate = pubDate.ifBlank { null },
                                )
                                onComplete(data.copy(title = title.trim()))
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.Database) {
                                errorMessage = if (e.message.contains("UNIQUE constraint failed", ignoreCase = true)) {
                                    "This ISBN already exists in your library"
                                } else if (e.message.contains("constraint failed", ignoreCase = true)) {
                                    "Duplicate entry detected"
                                } else {
                                    "Database error: ${e.message}"
                                }
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.IsbnConflict) {
                                val conflictWork = ExistingWorkSummary(
                                    id = e.workId,
                                    title = "",
                                    description = null,
                                    editionCount = 0u,
                                    identifierCount = 0u,
                                    existingIsbns = listOf(e.conflictingIsbn),
                                    editions = emptyList()
                                )
                                duplicateSummary = conflictWork
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.NotFound) {
                                errorMessage = "Referenced item not found: ${e.message}"
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException) {
                                errorMessage = "Replace failed: ${e.message}"
                            } catch (e: Exception) {
                                errorMessage = "Unexpected error: ${e.message}"
                            }
                            isSaving = false
                        }
                        is DuplicateAction.NewEdition -> {
                            isSaving = true
                            try {
                                Bridge.createEditionForWork(
                                    workId = action.workId,
                                    editionTitle = title.trim(),
                                    isbn = isbn.trim().replace("-", ""),
                                    publishedDate = pubDate.ifBlank { null },
                                    languageId = selectedLanguage,
                                )
                                onComplete(data.copy(title = title.trim()))
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.Database) {
                                errorMessage = if (e.message.contains("UNIQUE constraint failed", ignoreCase = true)) {
                                    "This ISBN already exists in your library"
                                } else if (e.message.contains("constraint failed", ignoreCase = true)) {
                                    "Duplicate entry detected"
                                } else {
                                    "Database error: ${e.message}"
                                }
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.IsbnConflict) {
                                val conflictWork = ExistingWorkSummary(
                                    id = e.workId,
                                    title = "",
                                    description = null,
                                    editionCount = 0u,
                                    identifierCount = 0u,
                                    existingIsbns = listOf(e.conflictingIsbn),
                                    editions = emptyList()
                                )
                                duplicateSummary = conflictWork
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.NotFound) {
                                errorMessage = "Referenced item not found: ${e.message}"
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException) {
                                errorMessage = "Add edition failed: ${e.message}"
                            } catch (e: Exception) {
                                errorMessage = "Unexpected error: ${e.message}"
                            }
                            isSaving = false
                        }
                        is DuplicateAction.LinkIsbn -> {
                            isSaving = true
                            try {
                                Bridge.linkIsbnToExistingEdition(
                                    editionId = action.editionId,
                                    isbn = isbn.trim().replace("-", ""),
                                )
                                onComplete(data.copy(title = title.trim()))
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.Database) {
                                errorMessage = if (e.message.contains("UNIQUE constraint failed", ignoreCase = true)) {
                                    "This ISBN already exists in your library"
                                } else if (e.message.contains("constraint failed", ignoreCase = true)) {
                                    "Duplicate entry detected"
                                } else {
                                    "Database error: ${e.message}"
                                }
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.IsbnConflict) {
                                val conflictWork = ExistingWorkSummary(
                                    id = e.workId,
                                    title = "",
                                    description = null,
                                    editionCount = 0u,
                                    identifierCount = 0u,
                                    existingIsbns = listOf(e.conflictingIsbn),
                                    editions = emptyList()
                                )
                                duplicateSummary = conflictWork
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException.NotFound) {
                                errorMessage = "Referenced item not found: ${e.message}"
                            } catch (e: net.olamaelcu.livtet.ffi.MobileException) {
                                errorMessage = "Link ISBN failed: ${e.message}"
                            } catch (e: Exception) {
                                errorMessage = "Unexpected error: ${e.message}"
                            }
                            isSaving = false
                        }
                        is DuplicateAction.Cancel -> {
                            duplicateSummary = null
                        }
                    }
                    duplicateSummary = null
                }
            },
        )
    }
}

private suspend fun saveBook(
        data: WizardData,
        title: String,
        description: String,
        isbn: String,
        pubDate: String,
        languageId: DbId?,
        publisher: String,
    ) {
        val cleanIsbn = isbn.trim().replace("-", "")
        val authorNames = data.authors.map { it.name.trim() }

        val work = Bridge.createBookComplete(
            title = title.trim(),
            description = description.trim().ifEmpty { null },
            editionTitle = title.trim(),
            isbn = if (cleanIsbn.isNotBlank()) cleanIsbn else null,
            publishedDate = pubDate.ifBlank { null },
            languageId = languageId,
            authorNames = authorNames,
            publisher = publisher.trim().ifEmpty { null },
        )
        // The atomic function handles all the work internally
    }
