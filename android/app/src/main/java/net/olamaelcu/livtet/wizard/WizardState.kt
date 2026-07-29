package net.olamaelcu.livtet.wizard

import net.olamaelcu.livtet.ffi.Book
import net.olamaelcu.livtet.ffi.DbId
import net.olamaelcu.livtet.ffi.WorkSummary

data class WizardData(
    val currentStep: Int = 0,
    val title: String = "",
    val description: String = "",
    val isbn: String = "",
    val publishedDate: String = "",
    val languageId: DbId? = null,
    val formatId: DbId? = null,
    val publisher: String = "",
    val status: String? = null,
    val authors: List<AuthorEntry> = emptyList(),
    val tags: List<DbId> = emptyList(),
    val genres: List<DbId> = emptyList(),
    val subjects: List<DbId> = emptyList(),
    val searchQuery: String = "",
    val localDedupResults: List<WorkSummary> = emptyList(),
    val searchResults: List<ProviderResult> = emptyList(),
    val createdBook: Book? = null,
)

data class AuthorEntry(val name: String = "", val role: String = "author")

data class ProviderResult(
    val title: String,
    val authors: List<String> = emptyList(),
    val isbn: String? = null,
    val year: Int? = null,
    val publisher: String? = null,
    val source: String = "",
)
