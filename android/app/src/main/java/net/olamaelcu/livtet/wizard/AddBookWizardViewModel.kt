package net.olamaelcu.livtet.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.Bridge

/**
 * State holder for the 5-step AddBook wizard.
 *
 * Phase 1 wires the read path (search, cover entry) but the write path
 * (createBookComplete, findOrCreateAuthor, linkWorkTag/Genre/Subject,
 * setEditionCover, downloadImage) is gated by [isSaveAvailable] which
 * defaults to `false`. The flag flips once the Phase 2 core/ FFI work
 * in `core/livtet-ffi` is complete.
 */
class AddBookWizardViewModel(
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow(WizardData())
    val state: StateFlow<WizardData> = _state.asStateFlow()

    private val _currentStep = MutableStateFlow(WizardStep.TITLE_AND_COVER)
    val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    /**
     * Phase 1: the Rust core does not yet expose the save path the wizard
     * needs to write a book. Step 4 (Tags) surfaces a banner explaining
     * the delay and offers the user the choice to discard the wizard.
     * Flip to `true` once the Phase 2 core/ FFI work lands.
     */
    val isSaveAvailable: Boolean = false

    private var searchJob: Job? = null

    init {
        savedState.get<String>(KEY_TITLE)?.let { _state.update { data -> data.copy(title = it) } }
        savedState.get<String>(KEY_QUERY)?.let { _state.update { data -> data.copy(searchQuery = it) } }
        viewModelScope.launch {
            try {
                Bridge.initPlugins()
            } catch (_: Exception) {
                // Ignore — search will surface a helpful error if plugins cannot be loaded.
            }
        }
    }

    fun onTitleChanged(value: String) {
        _state.update { it.copy(title = value) }
        savedState[KEY_TITLE] = value
    }

    fun onSearchQueryChanged(value: String) {
        _state.update { it.copy(searchQuery = value) }
        savedState[KEY_QUERY] = value
        scheduleSearch(value)
    }

    fun onCoverChanged(cover: CoverSource?) {
        _state.update { it.copy(cover = cover) }
    }

    fun onContributorsChanged(contributors: List<ContributorEntry>) {
        _state.update { it.copy(contributors = contributors) }
    }

    fun onGenresChanged(genres: List<String>) {
        _state.update { it.copy(genres = genres) }
    }

    fun onSubjectsChanged(subjects: List<String>) {
        _state.update { it.copy(subjects = subjects) }
    }

    fun onTagsChanged(tags: List<String>) {
        _state.update { it.copy(tags = tags) }
    }

    fun onSearchResultSelected(result: ProviderResult) {
        _state.update {
            it.copy(
                title = result.title,
                cover = result.coverUrl?.let { url -> CoverSource.Remote(url) },
                contributors = result.authors.map { name -> ContributorEntry(name = name) },
            )
        }
        advance()
    }

    fun goToNext() {
        advance()
    }

    fun goToBack() {
        _currentStep.value = when (_currentStep.value) {
            WizardStep.TITLE_AND_COVER -> WizardStep.TITLE_AND_COVER
            WizardStep.CONTRIBUTORS -> WizardStep.TITLE_AND_COVER
            WizardStep.GENRES -> WizardStep.CONTRIBUTORS
            WizardStep.SUBJECTS -> WizardStep.GENRES
            WizardStep.TAGS -> WizardStep.SUBJECTS
        }
    }

    val canContinueFromTitleAndCover: Boolean
        get() = _state.value.title.trim().isNotEmpty() && _state.value.cover != null

    val canContinueFromContributors: Boolean
        get() = _state.value.contributors.any { entry ->
            entry.role == "author" && entry.name.trim().isNotEmpty()
        }

    private fun advance() {
        _currentStep.value = when (_currentStep.value) {
            WizardStep.TITLE_AND_COVER -> WizardStep.CONTRIBUTORS
            WizardStep.CONTRIBUTORS -> WizardStep.GENRES
            WizardStep.GENRES -> WizardStep.SUBJECTS
            WizardStep.SUBJECTS -> WizardStep.TAGS
            WizardStep.TAGS -> WizardStep.TAGS
        }
    }

    private fun scheduleSearch(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 3) {
            _state.update { it.copy(onlineResults = emptyList()) }
            _searchError.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(750)
            _isSearching.value = true
            _searchError.value = null
            try {
                val hits = Bridge.searchProviders(trimmed)
                _state.update { data ->
                    data.copy(
                        onlineResults = hits.map { hit ->
                            ProviderResult(
                                title = hit.title,
                                authors = hit.authors,
                                isbn = hit.identifiers
                                    .firstOrNull { it.startsWith("urn:isbn:") }
                                    ?.removePrefix("urn:isbn:"),
                                year = hit.publishedDate?.take(4)?.toIntOrNull(),
                                publisher = hit.publisher,
                                source = hit.source,
                                coverUrl = hit.coverUrl,
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                _searchError.value = "Could not search online: ${e.message}"
            }
            _isSearching.value = false
        }
    }

    companion object {
        private const val KEY_TITLE = "wizard.title"
        private const val KEY_QUERY = "wizard.searchQuery"
    }
}
