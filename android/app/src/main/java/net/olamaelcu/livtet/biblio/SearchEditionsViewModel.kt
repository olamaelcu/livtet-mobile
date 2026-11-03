package net.olamaelcu.livtet.biblio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the edition search sheet. */
data class SearchEditionsUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val editions: List<BiblioBook> = emptyList(),
)

/**
 * Searches catalog editions through `community.lexicon.book.searchEditions`.
 *
 * Each keystroke restarts the query after [DEBOUNCE_MS]; a blank field returns the newest editions
 * so the sheet is useful before anything is typed.
 */
@HiltViewModel
class SearchEditionsViewModel @Inject constructor(private val client: BiblioClient) : ViewModel() {

    private val _state = MutableStateFlow(SearchEditionsUiState())

    val state: StateFlow<SearchEditionsUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        search("")
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        job?.cancel()
        job =
            viewModelScope.launch {
                delay(DEBOUNCE_MS)
                runSearch(query)
            }
    }

    /** Runs the pending query immediately, for the keyboard's search action. */
    fun submit() {
        job?.cancel()
        job = viewModelScope.launch { runSearch(_state.value.query) }
    }

    fun retry() {
        job?.cancel()
        job = viewModelScope.launch { runSearch(_state.value.query) }
    }

    private fun search(query: String) {
        job?.cancel()
        job = viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val response = client.searchEditions(query.trim())
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    editions = response.items.filter { it.title.isNotBlank() },
                    error = null,
                )
        } catch (e: BiblioException) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    editions = emptyList(),
                    error = "The catalog could not be searched.",
                )
        }
    }

    companion object {
        const val DEBOUNCE_MS = 300L
    }
}
