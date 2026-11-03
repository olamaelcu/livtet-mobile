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
import net.olamaelcu.livtet.Bridge

/** UI state for the edition search sheet. */
data class SearchEditionsUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val editions: List<BiblioBook> = emptyList(),
    /** Edition URI currently being written to the library. */
    val importingUri: String? = null,
    /** Shown when an add was refused; the sheet stays open. */
    val importError: String? = null,
    /** Set once an edition is stored, so the caller can close and reload. */
    val importedUri: String? = null,
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

    /**
     * Clears the imported marker once the caller has closed the sheet.
     *
     * The view model outlives the sheet, so leaving the marker set would make the next
     * open look like a completed import and close immediately.
     */
    fun consumeImported() {
        if (_state.value.importedUri != null) {
            _state.value = _state.value.copy(importedUri = null)
        }
    }

    /**
     * Store a catalog edition in the library: the work, its edition, then every
     * identifier the catalog listed. An edition whose identifiers already exist
     * is refused rather than duplicated, because the identifier table is shared
     * library-wide and a second copy would be invisible.
     */
    fun import(edition: BiblioBook) {
        if (_state.value.importingUri != null) return
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    importingUri = edition.uri,
                    importError = null,
                    importedUri = null,
                )
            val urns = edition.identifiers.orEmpty().mapNotNull { it.toUrn() }
            try {
                if (urns.isNotEmpty() && Bridge.isInLibrary(urns)) {
                    _state.value =
                        _state.value.copy(
                            importingUri = null,
                            importError = "This edition is already in your library.",
                        )
                    return@launch
                }
                Bridge.importEdition(
                    title = edition.title,
                    description = edition.description,
                    identifiers = urns,
                )
                _state.value = _state.value.copy(importingUri = null, importedUri = edition.uri)
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        importingUri = null,
                        importError = "This edition could not be added.",
                    )
            }
        }
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
