import Foundation
@testable import Livtet
import LivtetKit

/// Mock `LibraryBridge` for view-model unit tests. Records call arguments so
/// tests can assert what was sent; returns preset fixtures or throws based on
/// the `error` field.
final class MockLibraryBridge: LibraryBridge {
    /// Optional sleep injected before each call so tests can race the Combine
    /// debounce against an in-flight call.
    var artificialDelayNanos: UInt64 = 0

    /// If non-nil, the next call throws this error and clears it.
    var error: Error?

    var formats: [FormatInfo] = []
    var languages: [LibraryLanguage] = []
    var works: [WorkSummary] = []
    var editions: [EditionSummary] = []

    /// Recorded calls to `listWorks`.
    private(set) var calls: [RecordedCall] = []

    struct RecordedCall: Equatable {
        let formatIds: [DbId]
        let languageIds: [DbId]
        let sortDirection: SortDirection
    }

    func listWorks(
        limit: Int32,
        offset: Int32,
        sortDirection: SortDirection,
        formatIds: [DbId],
        languageIds: [DbId]
    ) async throws -> [WorkSummary] {
        sleepIfNeeded()
        if let error { throw error }
        calls.append(RecordedCall(
            formatIds: formatIds,
            languageIds: languageIds,
            sortDirection: sortDirection
        ))
        return works
    }

    func getDistinctFormats() async throws -> [FormatInfo] {
        sleepIfNeeded()
        if let error { throw error }
        return formats
    }

    func getDistinctLanguages() async throws -> [LibraryLanguage] {
        sleepIfNeeded()
        if let error { throw error }
        return languages
    }

    func getEditionsForWork(workId: DbId) async throws -> [EditionSummary] {
        if let error { throw error }
        return editions
    }

    func getEdition(id: DbId) async throws -> EditionDetail? {
        if let error { throw error }
        return nil
    }

    func getEmptyStateQuotation() -> EmptyMessage {
        EmptyMessage(text: "", author: "", material: "")
    }

    private func sleepIfNeeded() {
        guard artificialDelayNanos > 0 else { return }
        let sem = DispatchSemaphore(value: 0)
        DispatchQueue.global().asyncAfter(
            deadline: .now() + .nanoseconds(Int(artificialDelayNanos))
        ) { sem.signal() }
        sem.wait()
    }
}
