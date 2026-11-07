package net.olamaelcu.livtet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.biblio.BiblioClient
import net.olamaelcu.livtet.biblio.toUrn
import net.olamaelcu.livtet.ffi.EmptyMessage
import javax.inject.Inject

/** UI state for the Library screen. */
data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val shelves: List<Bridge.Shelf> = emptyList(),
    /** Book id → local cover file, for the covers already on disk. */
    val coverPaths: Map<String, String> = emptyMap(),
    val emptyMessage: EmptyMessage? = null,
    val query: String = "",
    val isLoading: Boolean = false,
    /** A further page is being appended to what is already shown. */
    val isLoadingMore: Boolean = false,
    /** The last page came back full, so another may exist. */
    val hasMore: Boolean = true,
    val error: String? = null,
) {
    /** Every loaded book matched the query, so there is nothing more to fetch. */
    val isSearching: Boolean
        get() = query.isNotBlank()
}

/**
 * Loads the library a page at a time, searching as the reader types.
 *
 * A search filters in SQL (title and author name), which keeps covers, status and the primary
 * edition on every row and keeps the offset meaningful; the Tantivy index is left to the
 * cross-entity search that does not render rows.
 *
 * Covers are resolved once per load. A book imported before the catalog cover was stored has none,
 * so it is backfilled from the catalog by matching its stored identifiers before the cover cache is
 * asked again.
 */
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(private val catalog: BiblioClient) : ViewModel() {
        private val _state = MutableStateFlow(LibraryUiState())
        val state: StateFlow<LibraryUiState> = _state.asStateFlow()

        private var job: Job? = null
        private var searchJob: Job? = null
        private var loadedPages = 0

        fun load() {
            job?.cancel()
            job =
                viewModelScope.launch {
                    loadedPages = 0
                    _state.value = _state.value.copy(isLoading = true, error = null)
                    try {
                        val books = Bridge.listBooks(limit = PAGE_SIZE, offset = 0, query = currentQuery())
                        val shelves = Bridge.listShelves()
                        loadedPages = 1
                        _state.value =
                            _state.value.copy(
                                books = books,
                                shelves = shelves,
                                coverPaths = resolveCovers(books),
                                emptyMessage = Bridge.getEmptyStateQuotation(),
                                isLoading = false,
                                isLoadingMore = false,
                                hasMore = books.size >= PAGE_SIZE,
                                error = null,
                            )
                    } catch (e: Exception) {
                        _state.value =
                            _state.value.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = e.message ?: e.toString(),
                            )
                    }
                }
        }

        /** Append the next page. Ignored while a page is already in flight. */
        fun loadMore() {
            val current = _state.value
            if (current.isLoading || current.isLoadingMore || !current.hasMore) return
            job =
                viewModelScope.launch {
                    _state.value = current.copy(isLoadingMore = true)
                    try {
                        val next =
                            Bridge.listBooks(
                                limit = PAGE_SIZE,
                                offset = loadedPages * PAGE_SIZE,
                                query = currentQuery(),
                            )
                        loadedPages += 1
                        val combined = current.books + next
                        _state.value =
                            _state.value.copy(
                                books = combined,
                                // Only the new page needs resolving.
                                coverPaths = _state.value.coverPaths + resolveCovers(next),
                                isLoadingMore = false,
                                hasMore = next.size >= PAGE_SIZE,
                            )
                    } catch (e: Exception) {
                        _state.value =
                            _state.value.copy(isLoadingMore = false, error = e.message ?: e.toString())
                    }
                }
        }

        /** Search as the reader types; the query is debounced so each keystroke is not a list. */
        fun onQueryChange(query: String) {
            _state.value = _state.value.copy(query = query)
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    load()
                }
        }

        fun deleteBook(book: Book) {
            viewModelScope.launch {
                try {
                    Bridge.deleteBook(book.id)
                    load()
                } catch (e: Exception) {
                    _state.value = _state.value.copy(error = e.message ?: "Could not remove this book.")
                }
            }
        }

        /**
         * Cover paths for a page of books, backfilling a missing catalog URL first.
         *
         * Backfill runs at most once per book: the URL is stored, so the next load reads it from the
         * database.
         */
        private suspend fun resolveCovers(books: List<Book>): Map<String, String> =
            books
                .mapNotNull { book ->
                    val editionId = book.primaryEditionId ?: return@mapNotNull null
                    val remote = book.coverUrl ?: backfillCoverUrl(editionId, book.title)
                    if (remote == null) return@mapNotNull null
                    Bridge.resolveCoverPath(editionId, remote)?.let { book.id to it }
                }
                .toMap()

        /**
         * Find the catalog cover for an edition imported before covers were stored.
         *
         * The catalog is searched by title and the candidates are matched on the identifiers this
         * edition already holds, so a same-titled book by another author is not mistaken for this one.
         */
        private suspend fun backfillCoverUrl(
            editionId: String,
            title: String,
        ): String? {
            val stored = runCatching { Bridge.listEditionIdentifiers(editionId) }.getOrNull()
            if (stored.isNullOrEmpty()) return null

            val candidates =
                runCatching { catalog.searchEditions(title, limit = BACKFILL_CANDIDATES).items }
                    .getOrNull()
                    .orEmpty()
            val match =
                candidates.firstOrNull { candidate ->
                    candidate.identifiers.orEmpty().mapNotNull { it.toUrn() }.any { it in stored }
                } ?: return null
            val url = match.coverImageUrl?.takeIf { it.isNotBlank() } ?: return null

            runCatching { Bridge.setEditionCoverUrl(editionId, url) }.getOrNull() ?: return null
            return url
        }

        /**
         * File a book on a shelf, creating the shelf first when [newShelfName] is given. A book without
         * a primary edition cannot be filed — a shelf stores editions, so there is nothing to name.
         */
        fun addToShelf(
            book: Book,
            shelfId: String?,
            newShelfName: String? = null,
        ) {
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
        fun moveInShelf(
            shelf: Bridge.Shelf,
            book: Book,
            offset: Int,
        ) {
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
        fun removeFromShelf(
            shelf: Bridge.Shelf,
            book: Book,
        ) {
            val editionId = book.primaryEditionId ?: return
            viewModelScope.launch {
                try {
                    Bridge.removeFromShelf(shelf.id, editionId)
                    load()
                } catch (e: Exception) {
                    _state.value = _state.value.copy(error = e.message ?: "Could not remove this book.")
                }
            }
        }

        private fun currentQuery(): String? = _state.value.query.takeIf { it.isNotBlank() }

        companion object {
            const val PAGE_SIZE = 50
            const val SEARCH_DEBOUNCE_MS = 300L
            const val BACKFILL_CANDIDATES = 10
        }
    }
