import FastULID
import Foundation
@testable import Livtet
import LivtetKit
import LivtetKitFFI

/// Mock `LibraryBridge` for view-model unit tests. Records call
/// arguments so tests can assert what was sent; returns preset
/// fixtures or throws based on the `error` field.
final class MockLibraryBridge: LibraryBridge {
    /// Optional sleep injected before each call so tests can race the
    /// Combine debounce against an in-flight call.
    var artificialDelayNanos: UInt64 = 0

    /// If non-nil, the next call throws this error and clears it.
    var error: Error?

    var formats: [FormatInfo] = []
    var languages: [LanguageInfo] = []
    var statuses: [WorkStatusInfo] = []

    /// Books returned from `listBooksWithFilters`.
    var books: [Book] = []

    /// Recorded calls.
    private(set) var calls: [RecordedCall] = []

    struct RecordedCall: Equatable {
        let formatIds: [DbId]
        let languageIds: [DbId]
        let order: BookSearchSortOrder
    }

    func listBooksWithFilters(
        limit: Int32,
        offset: Int32,
        order: BookSearchSortOrder,
        filters: BookListFilters
    ) throws -> [Book] {
        try sleepIfNeeded()
        if let error { throw error }
        calls.append(RecordedCall(
            formatIds: filters.formatIds,
            languageIds: filters.languageIds,
            order: order
        ))
        return books
    }

    func getDistinctFormats() throws -> [FormatInfo] {
        try sleepIfNeeded()
        if let error { throw error }
        return formats
    }

    func getDistinctLanguages() throws -> [LanguageInfo] {
        try sleepIfNeeded()
        if let error { throw error }
        return languages
    }

    func getDistinctWorkStatuses() throws -> [WorkStatusInfo] {
        try sleepIfNeeded()
        if let error { throw error }
        return statuses
    }

    func getEmptyStateQuotation() -> EmptyMessage {
        EmptyMessage(text: "", author: "", material: "")
    }

    private func sleepIfNeeded() throws {
        if artificialDelayNanos > 0 {
            let sem = DispatchSemaphore(value: 0)
            DispatchQueue.global().asyncAfter(
                deadline: .now() + .nanoseconds(Int(artificialDelayNanos))
            ) { sem.signal() }
            sem.wait()
        }
    }
}
