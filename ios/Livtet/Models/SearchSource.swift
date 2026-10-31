import Foundation

enum SearchSource: String {
    case googleBooks = "googlebooks"
    case openLibrary = "openlibrary"
}

/// Failure category shared by the search-provider clients
/// (`GoogleBooksClient`, `OpenLibraryClient`) and surfaced in the UI's
/// `ProviderErrorInfo` callout.
enum ProviderErrorCategory: String, Equatable {
    /// The provider rejected the request for lack of credentials.
    case needsAuth
    /// The provider is throttling us; `retryAfterSeconds` may be set.
    case rateLimited
    /// The request exceeded the client timeout.
    case timeout
    /// The provider answered, but with nothing usable.
    case notFound
    /// The provider is failing server-side.
    case providerDown
}
