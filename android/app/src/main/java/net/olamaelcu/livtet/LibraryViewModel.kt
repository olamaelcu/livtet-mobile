package net.olamaelcu.livtet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.ffi.Book
import net.olamaelcu.livtet.ffi.BookSearchSortOrder
import net.olamaelcu.livtet.ffi.EmptyMessage

/** UI state for the Library screen. */
data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val emptyMessage: EmptyMessage? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * View-model for the Library screen. Loads the first page of books via
 * `Bridge.listBooks` (50 per page, newest first) plus a literary
 * empty-state quotation on each [load]. Pagination, search, and
 * filters are deliberately deferred to follow-up commits.
 */
class LibraryViewModel : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val books = Bridge.listBooks(
                    limit = PAGE_SIZE,
                    offset = 0,
                    order = BookSearchSortOrder.DESCENDING,
                )
                val empty = Bridge.getEmptyStateQuotation()
                _state.value = LibraryUiState(
                    books = books,
                    emptyMessage = empty,
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: e.toString(),
                )
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
