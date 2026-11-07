package net.olamaelcu.livtet.biblio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.AppLinkRouter
import javax.inject.Inject

/** UI state for a profile permalink. */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val actor: BiblioActor? = null,
    val did: String? = null,
    val stats: ProfileStats = ProfileStats(),
    val reviews: List<BiblioReview> = emptyList(),
    val reviewers: Map<String, BiblioActor> = emptyMap(),
    val reviewBooks: Map<String, BookSummary> = emptyMap(),
    val statusByBookUri: Map<String, String> = emptyMap(),
    val shelves: List<ShelfSummary> = emptyList(),
    val selectedTab: String = AppLinkRouter.TAB_REVIEWS,
    val selectedShelf: String? = null,
    val shelfBooks: List<ShelfBookRow> = emptyList(),
    val shelfBooksLoading: Boolean = false,
    /** True when the actor's PDS cannot be resolved: reviews and shelves are unreachable. */
    val unavailable: Boolean = false,
) {
    /** The reviews the current tab shows, after applying its reading-status filter. */
    val visibleReviews: List<BiblioReview>
        get() {
            val wanted = AppLinkRouter.TAB_STATUSES[selectedTab] ?: return reviews
            return reviews.filter { statusByBookUri[it.record.bookUri()] == wanted }
        }

    val displayName: String
        get() =
            actor?.displayName?.takeIf { it.isNotBlank() }
                ?: actor?.handle?.let { "@$it" }
                ?: did?.let { tailOf(it) }
                ?: ""
}

/** The last `:`-separated chunk of a DID, used when an actor has no name or handle. */
private fun tailOf(did: String): String = did.substringAfterLast(':')

/**
 * Loads everything a profile permalink renders: identity from the Bluesky AppView, then reviews,
 * shelving, and shelf records straight from the actor's PDS — the same split the platform uses,
 * because the AppView's shelf reads return nothing for real actors.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(private val client: BiblioClient, savedStateHandle: SavedStateHandle) : ViewModel() {
        private val actor: String = savedStateHandle.get<String>("actor").orEmpty()

        private val initialTab: String = AppLinkRouter.normalizeTab(savedStateHandle.get<String>("tab"))

        private val initialShelf: String? = savedStateHandle.get<String>("shelf")

        private val _state = MutableStateFlow(ProfileUiState(selectedTab = initialTab))

        val state: StateFlow<ProfileUiState> = _state.asStateFlow()

        private var job: Job? = null

        private var shelfJob: Job? = null

        /** Shelving records, kept for the on-demand title hydration of one shelf. */
        private var shelving: List<BiblioShelving> = emptyList()

        private var loaded = false

        fun load() {
            if (job?.isActive == true || loaded) return
            val requestedActor = actor
            job =
                viewModelScope.launch {
                    _state.value = ProfileUiState(isLoading = true, selectedTab = initialTab)
                    val identity = client.getActor(requestedActor)
                    val did =
                        identity?.did?.takeIf { it.isNotEmpty() }
                            ?: requestedActor.takeIf { it.startsWith("did:") }
                    if (did == null) {
                        _state.value =
                            ProfileUiState(isLoading = false, error = "This profile could not be found.")
                        return@launch
                    }
                    loaded = true
                    val pds = client.resolvePds(did)
                    if (pds == null) {
                        _state.value =
                            ProfileUiState(
                                isLoading = false,
                                actor = identity,
                                did = did,
                                unavailable = true,
                                selectedTab = initialTab,
                            )
                        return@launch
                    }
                    val (reviews, shelvingRecords, shelves) =
                        coroutineScope {
                            val reviewsAsync = async { client.listActorReviews(did) }
                            val shelvingAsync = async { client.listActorShelving(did) }
                            val shelvesAsync = async { client.listActorShelves(did) }
                            Triple(reviewsAsync.await(), shelvingAsync.await(), shelvesAsync.await())
                        }
                    shelving = shelvingRecords
                    val reviewers = client.actorMap(reviews.map { it.did })
                    val reviewBooks = client.bookSummaries(reviews.mapNotNull { it.record.bookUri() })
                    val counts = shelfCounts(shelvingRecords)
                    val shelfRows =
                        shelves.entries
                            .sortedBy { it.key }
                            .map { (rkey, name) ->
                                ShelfSummary(rkey = rkey, name = name, bookCount = counts[rkey] ?: 0)
                            }
                    _state.value =
                        ProfileUiState(
                            isLoading = false,
                            actor = identity,
                            did = did,
                            stats = shelvingStats(shelvingRecords),
                            reviews = reviews,
                            reviewers = reviewers,
                            reviewBooks = reviewBooks,
                            statusByBookUri = statusByBookUri(shelvingRecords),
                            shelves =
                                if (shelfRows.isEmpty()) {
                                    // A reader can have shelving records whose shelf
                                    // record is missing; show those shelves anyway.
                                    counts.entries
                                        .sortedBy { it.key }
                                        .map { (rkey, count) ->
                                            ShelfSummary(rkey = rkey, name = rkey, bookCount = count)
                                        }
                                } else {
                                    shelfRows
                                },
                            selectedTab = initialTab,
                        )
                    if (initialTab == AppLinkRouter.TAB_SHELVES) selectShelf(initialShelf)
                }
        }

        fun selectTab(tab: String) {
            _state.value = _state.value.copy(selectedTab = AppLinkRouter.normalizeTab(tab))
        }

        /**
         * Opens one shelf. Titles are hydrated from the catalog here rather than during [load], so a
         * profile with hundreds of shelved books stays cheap.
         */
        fun selectShelf(shelfRkey: String?) {
            val target = shelfRkey?.takeIf { it.isNotBlank() }
            _state.value =
                _state.value.copy(
                    selectedTab = AppLinkRouter.TAB_SHELVES,
                    selectedShelf = target,
                    shelfBooks = emptyList(),
                    shelfBooksLoading = target != null,
                )
            if (target == null) {
                shelfJob?.cancel()
                return
            }
            shelfJob?.cancel()
            val entries = shelving.filter { parseAtUri(it.value.shelf)?.rkey == target }
            shelfJob =
                viewModelScope.launch {
                    // Reader-app records carry the title inline; only the ones that do
                    // not need a catalog round-trip.
                    val summaries =
                        client.bookSummaries(
                            entries
                                .filter { it.value.book.title.isNullOrBlank() }
                                .mapNotNull { it.value.book.atUri },
                        )
                    val rows =
                        entries.map { entry ->
                            val bookUri = entry.value.book.atUri
                            val title =
                                summaries[bookUri]?.title
                                    ?: entry.value.book.title?.takeIf { it.isNotBlank() }
                                    ?: parseAtUri(bookUri)?.rkey
                                    ?: "Untitled"
                            ShelfBookRow(
                                shelfRkey = target,
                                bookUri = bookUri,
                                title = title,
                                status = entry.value.metadata?.status,
                            )
                        }
                    _state.value = _state.value.copy(shelfBooks = rows, shelfBooksLoading = false)
                }
        }
    }
