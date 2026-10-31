// SPDX-License-Identifier: AGPL-3.0-only
//
// SessionStore.swift
// LivtetKit.Reader
//
// Persists reading progress through `LivtetStore`. Phase 4 will add
// throttling; this task only ships the unthrottled call shape.
//

import Foundation

/// Persists reading progress for the reader module via the FFI.
///
/// `recordProgress` forwards to `LivtetStore.recordReadingProgress` without
/// throttling. Phase 4 will wrap this call in a throttle/debounce strategy
/// so the database isn't hit on every page turn.
public final class SessionStore {
    /// Store used for all database access.
    private let store: LivtetStore

    /// Creates a new `SessionStore`.
    ///
    /// - Parameter store: The open `LivtetStore` handle.
    public init(store: LivtetStore) {
        self.store = store
    }

    /// Records reading progress for an edition/format pair.
    ///
    /// - Parameters:
    ///   - editionId: The edition being read.
    ///   - formatId: The format (e.g. EPUB) being read.
    ///   - progress: Progress value in `progressUnit`.
    ///   - progressUnit: Semantic unit of `progress`.
    ///   - lastLocation: Opaque locator string the reader produces; `nil`
    ///     clears the saved location.
    ///   - totalReadingTimeSecs: Additional wall-clock reading time to
    ///     accumulate, in seconds.
    /// - Throws: `LivtetError` if the FFI rejects the upsert.
    public func recordProgress(
        editionId: DbId,
        formatId: DbId,
        progress: Double,
        progressUnit: ProgressUnit,
        lastLocation: String?,
        totalReadingTimeSecs: Int64
    ) async throws {
        _ = try await store.recordReadingProgress(
            editionId: editionId,
            formatId: formatId,
            progress: progress,
            unit: progressUnit,
            lastLocation: lastLocation,
            additionalSeconds: totalReadingTimeSecs
        )
    }
}
