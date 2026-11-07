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

/** UI state for a review permalink. */
data class ReviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val review: BiblioReview? = null,
    val reviewer: BiblioActor? = null,
    val book: BookSummary? = null,
)

/**
 * Loads one review from its author's PDS, plus the reviewer's public profile and the book it points
 * at.
 *
 * The review itself is authoritative on the PDS; the profile and book are presentation and are
 * allowed to be missing.
 */
@HiltViewModel
class ReviewViewModel
    @Inject
    constructor(private val client: BiblioClient, savedStateHandle: SavedStateHandle) : ViewModel() {
        private val did: String = savedStateHandle.get<String>("did").orEmpty()

        private val rkey: String = savedStateHandle.get<String>("rkey").orEmpty()

        private val _state = MutableStateFlow(ReviewUiState())

        val state: StateFlow<ReviewUiState> = _state.asStateFlow()

        private var job: Job? = null

        fun load() {
            if (job?.isActive == true) return
            val requestedDid = did
            val requestedRkey = rkey
            job =
                viewModelScope.launch {
                    _state.value = ReviewUiState(isLoading = true)
                    val review = client.getReview(requestedDid, requestedRkey)
                    if (review == null) {
                        _state.value =
                            ReviewUiState(isLoading = false, error = "This review could not be loaded.")
                        return@launch
                    }
                    val reviewer = client.getActor(requestedDid)
                    val bookUri = review.record.bookUri()
                    _state.value =
                        ReviewUiState(
                            isLoading = false,
                            review = review,
                            reviewer = reviewer,
                            book = bookUri?.let { client.summarise(it) },
                        )
                }
        }
    }
