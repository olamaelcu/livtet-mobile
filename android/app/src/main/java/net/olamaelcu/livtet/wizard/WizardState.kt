package net.olamaelcu.livtet.wizard

/**
 * Wizard state model. The full wizard (search → authors → review)
 * previously depended on UniFFI types that are not yet present in
 * the `core/livtet-ffi` crate (`Book`, `WorkSummary`, `DbId`,
 * `AuthorInfo`, etc.). The wizard is non-functional until those
 * FFI calls land upstream; in the meantime this class is
 * intentionally minimal so the surrounding composables still
 * compile and the screen renders an "Add Book coming soon"
 * placeholder.
 */
data class WizardData(
    val currentStep: Int = 0,
    val title: String = "",
)

data class ProviderResult(
    val title: String,
    val authors: List<String> = emptyList(),
    val isbn: String? = null,
    val year: Int? = null,
    val publisher: String? = null,
    val source: String = "",
)