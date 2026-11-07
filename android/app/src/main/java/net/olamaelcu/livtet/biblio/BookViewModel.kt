package net.olamaelcu.livtet.biblio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for a book permalink. */
data class BookUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val book: BiblioBook? = null,
    val coverUrl: String? = null,
    val reviews: List<BiblioReview> = emptyList(),
    val reviewers: Map<String, BiblioActor> = emptyMap(),
)

/**
 * Loads one catalog edition plus its reviews.
 *
 * Everything is read-only: the edition comes from Bibliograph and the reviews from the AppView with
 * a Constellation fallback, exactly like the web page.
 */
@HiltViewModel
class BookViewModel
    @Inject
    constructor(private val client: BiblioClient, savedStateHandle: SavedStateHandle) : ViewModel() {
        private val did: String = savedStateHandle.get<String>("did").orEmpty()

        private val rkey: String = savedStateHandle.get<String>("rkey").orEmpty()

        private val bookUri = "at://$did/${BiblioClient.EDITION_COLLECTION}/$rkey"

        private val _state = MutableStateFlow(BookUiState())

        val state: StateFlow<BookUiState> = _state.asStateFlow()

        private var job: Job? = null

        fun load() {
            if (job?.isActive == true) return
            val requestedDid = did
            val requestedRkey = rkey
            job =
                viewModelScope.launch {
                    _state.value = BookUiState(isLoading = true)
                    val book =
                        try {
                            client.getEdition(requestedDid, requestedRkey)
                        } catch (e: BiblioException) {
                            _state.value =
                                BookUiState(isLoading = false, error = "This book could not be loaded.")
                            return@launch
                        }
                    if (book.title.isBlank()) {
                        _state.value = BookUiState(isLoading = false, error = "This book was not found.")
                        return@launch
                    }
                    val cover =
                        book.coverImageUrl?.takeIf { it.isNotBlank() } ?: client.getImageForBook(bookUri)
                    _state.value = BookUiState(isLoading = false, book = book, coverUrl = cover)
                    // Reviews are secondary: a failure here leaves the page useful.
                    val reviews = client.getBookReviews(bookUri)
                    val reviewers = client.actorMap(reviews.map { it.did })
                    _state.value = _state.value.copy(reviews = reviews, reviewers = reviewers)
                }
        }
    }
