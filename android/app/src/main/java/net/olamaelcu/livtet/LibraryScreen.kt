package net.olamaelcu.livtet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.ffi.Book
import net.olamaelcu.livtet.ffi.FormatInfo
import net.olamaelcu.livtet.ffi.LanguageInfo
import net.olamaelcu.livtet.ffi.BookSearchSortOrder
import net.olamaelcu.livtet.ffi.WorkStatusInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var formats by remember { mutableStateOf<List<FormatInfo>>(emptyList()) }
    var languages by remember { mutableStateOf<List<LanguageInfo>>(emptyList()) }
    var statuses by remember { mutableStateOf<List<WorkStatusInfo>>(emptyList()) }
    var selectedFormat by remember { mutableStateOf<FormatInfo?>(null) }
    var selectedLanguage by remember { mutableStateOf<LanguageInfo?>(null) }
    var selectedStatus by remember { mutableStateOf<WorkStatusInfo?>(null) }
    var selectedSort by remember { mutableStateOf(BookSearchSortOrder.DESCENDING) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDuplicates by remember { mutableStateOf(false) }

    fun loadBooks() {
        scope.launch {
            isLoading = true
            try {
                books = Bridge.listBooks(50, 0, BookSearchSortOrder.DESCENDING)
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun loadFilters() {
        scope.launch {
            try {
                formats = Bridge.getDistinctFormats()
                languages = Bridge.getDistinctLanguages()
                statuses = Bridge.getDistinctWorkStatuses()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadBooks()
        loadFilters()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add book",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { showDuplicates = true }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Manage duplicates",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search your library...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                enabled = books.isNotEmpty(),
                modifier = Modifier.weight(1f).onFocusChanged { isSearchFocused = it.isFocused },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    ),
            )
        }

        AnimatedVisibility(
            visible = isSearchFocused && books.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
        ) {
            SearchFilterPanel(
                formats = formats,
                languages = languages,
                statuses = statuses,
                selectedFormat = selectedFormat,
                selectedLanguage = selectedLanguage,
                selectedStatus = selectedStatus,
                selectedSort = selectedSort,
                onFormatSelected = { selectedFormat = it },
                onLanguageSelected = { selectedLanguage = it },
                onStatusSelected = { selectedStatus = it },
                onSortSelected = { selectedSort = it },
            )
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { loadBooks() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (books.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No books yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = error ?: "Add your first book to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(books) { book -> BookRow(book = book) }
                }
            }
        }
    }

    if (showAddDialog) {
        net.olamaelcu.livtet.wizard.AddBookWizard(
            onDismiss = {
                showAddDialog = false
                loadBooks()
            }
        )
    }

    if (showDuplicates) {
        Dialog(
            onDismissRequest = { showDuplicates = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            net.olamaelcu.livtet.duplicates.DuplicateManagementScreen(
                onDismiss = {
                    showDuplicates = false
                    loadBooks()
                }
            )
        }
    }
}

@Composable
private fun SearchFilterPanel(
    formats: List<FormatInfo>,
    languages: List<LanguageInfo>,
    statuses: List<WorkStatusInfo>,
    selectedFormat: FormatInfo?,
    selectedLanguage: LanguageInfo?,
    selectedStatus: WorkStatusInfo?,
    selectedSort: BookSearchSortOrder,
    onFormatSelected: (FormatInfo?) -> Unit,
    onLanguageSelected: (LanguageInfo?) -> Unit,
    onStatusSelected: (WorkStatusInfo?) -> Unit,
    onSortSelected: (BookSearchSortOrder) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SortChip("Newest", BookSearchSortOrder.DESCENDING, selectedSort, onSortSelected)
            SortChip("Oldest", BookSearchSortOrder.ASCENDING, selectedSort, onSortSelected)
        }

        if (formats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Format",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedFormat == null,
                    onClick = { onFormatSelected(null) },
                    label = { Text("All") },
                )
                formats.forEach { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = { onFormatSelected(if (selectedFormat == fmt) null else fmt) },
                        label = { Text(fmt.name) },
                    )
                }
            }
        }

        if (languages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Language",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedLanguage == null,
                    onClick = { onLanguageSelected(null) },
                    label = { Text("All") },
                )
                languages.forEach { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang,
                        onClick = {
                            onLanguageSelected(if (selectedLanguage == lang) null else lang)
                        },
                        label = {
                            Text(
                                buildString {
                                    if (lang.flagEmoji != null) append("${lang.flagEmoji} ")
                                    append(lang.name)
                                }
                            )
                        },
                    )
                }
            }
        }

        if (statuses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedStatus == null,
                    onClick = { onStatusSelected(null) },
                    label = { Text("All") },
                )
                statuses.forEach { status ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = {
                            onStatusSelected(if (selectedStatus == status) null else status)
                        },
                        label = { Text(status.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    value: BookSearchSortOrder,
    current: BookSearchSortOrder,
    onSelected: (BookSearchSortOrder) -> Unit,
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelected(value) },
        label = { Text(label) },
    )
}

@Composable
private fun BookRow(book: Book) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.description != null) {
                Text(
                    text = book.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
