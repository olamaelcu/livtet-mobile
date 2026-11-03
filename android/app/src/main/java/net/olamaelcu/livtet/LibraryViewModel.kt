package net.olamaelcu.livtet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.ffi.EmptyMessage

/** UI state for the Library screen. */
data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val shelves: List<Bridge.Shelf> = emptyList(),
    /** Book id → local cover file, for the covers already on disk. */
    val coverPaths: Map<String, String> = emptyMap(),
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
                val shelves = Bridge.listShelves()
                // Resolving here rather than in composition keeps the network
                // off the frame and lets a hit be served from disk.
                val coverPaths =
                    books.mapNotNull { book ->
                        val editionId = book.primaryEditionId ?: return@mapNotNull null
                        val cover = book.coverUrl ?: return@mapNotNull null
                        Bridge.resolveCoverPath(editionId, cover)?.let { book.id to it }
                    }.toMap()
                _state.value = LibraryUiState(
                    books = books,
                    shelves = shelves,
                    coverPaths = coverPaths,
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

    /**
     * File a book on a shelf, creating the shelf first when [newShelfName] is
     * given. A book without a primary edition cannot be filed — a shelf stores
     * editions, so there is nothing to name.
     */
    fun addToShelf(book: Book, shelfId: String?, newShelfName: String? = null) {
        val editionId = book.primaryEditionId ?: return
        viewModelScope.launch {
            try {
                val target =
                    when {
                        shelfId != null -> shelfId
                        !newShelfName.isNullOrBlank() -> Bridge.createShelf(newShelfName.trim()).id
                        else -> return@launch
                    }
                Bridge.addToShelf(target, editionId)
                load()
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(error = e.message ?: "Could not add this book to a shelf.")
            }
        }
    }

    /** Move a book one place earlier or later on its shelf. */
    fun moveInShelf(shelf: Bridge.Shelf, book: Book, offset: Int) {
        val editionId = book.primaryEditionId ?: return
        val current = shelf.editionIds.indexOf(editionId)
        val target = current + offset
        if (current < 0 || target !in shelf.editionIds.indices) return
        viewModelScope.launch {
            try {
                Bridge.moveInShelf(shelf.id, editionId, target)
                load()
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(error = e.message ?: "Could not reorder this shelf.")
            }
        }
    }

    /** Take a book off a shelf. */
    fun removeFromShelf(shelf: Bridge.Shelf, book: Book) {
        val editionId = book.primaryEditionId ?: return
        viewModelScope.launch {
            try {
                Bridge.removeFromShelf(shelf.id, editionId)
                load()
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(error = e.message ?: "Could not remove this book.")
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
