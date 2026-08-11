package net.olamaelcu.livtet.wizard

/**
 * Source of the cover image entered in Step 1 — Title and Cover.
 *
 * [Remote] is the only case the Phase 1 wizard can land in: the user has
 * either typed/pasted a URL, picked a search result, or selected a photo
 * from the OS picker. The [PendingLocal] and [Downloaded] cases are
 * wired through the view-model state machine but their persistence paths
 * (the FFI download + `setEditionCover`) are intentionally not exercised
 * by Phase 1 — saving is disabled until the core/ FFI work described in
 * the Phase 2 spec lands.
 */
sealed class CoverSource {
    data class Remote(val url: String) : CoverSource()
    data class PendingLocal(val uri: String, val mimeType: String, val byteSize: Int) : CoverSource()
    data class Downloaded(val localPath: String) : CoverSource()

    val displayUrl: String?
        get() = when (this) {
            is Remote -> url
            is PendingLocal -> uri
            is Downloaded -> localPath
        }
}

data class WizardData(
    val title: String = "",
    val cover: CoverSource? = null,
    val searchQuery: String = "",
    val onlineResults: List<ProviderResult> = emptyList(),
    val contributors: List<ContributorEntry> = emptyList(),
    val genres: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

data class ContributorEntry(
    val name: String,
    val role: String = "author",
)

data class ProviderResult(
    val title: String,
    val authors: List<String> = emptyList(),
    val isbn: String? = null,
    val year: Int? = null,
    val publisher: String? = null,
    val source: String = "",
    val coverUrl: String? = null,
)

enum class WizardStep {
    TITLE_AND_COVER,
    CONTRIBUTORS,
    GENRES,
    SUBJECTS,
    TAGS,
}

/**
 * Role options for the contributors step. "author" is the only role
 * the gate (see [AddBookWizardViewModel.canContinueFromContributors])
 * accepts as a "valid" contributor — every other role is allowed but
 * does not satisfy the gate on its own.
 */
val CONTRIBUTOR_ROLES = listOf("author", "illustrator", "translator", "narrator")
