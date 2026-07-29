// SPDX-License-Identifier-Identifier: AGPL-3.0-only
//
// SessionStore.swift
// LivtetKit.Reader
//
// Persists reading progress through the FFI. Phase 4 will add throttling;
// this task only ships the unthrottled call shape.
//

import Foundation
import LivtetKitFFI

/// Persists reading progress for the reader module via the FFI.
///
/// `recordProgress` forwards to `livtetFfiUpsertReadingProgress` without
/// throttling. Phase 4 will wrap this call in a throttle/debounce strategy
/// so the database isn't hit on every page turn.
public final class SessionStore {
    /// Creates a new `SessionStore`.
    public init() {}

    /// Records reading progress for an edition/format pair.
    ///
    /// - Parameters:
    ///   - editionId: The edition being read.
    ///   - formatId: The format (e.g. EPUB) being read.
    ///   - progress: Fraction of the publication read, in `[0, 1]`.
    ///   - lastLocation: Opaque locator string the reader produces; `nil`
    ///     clears the saved location.
    ///   - totalReadingTimeSecs: Accumulated wall-clock reading time, in
    ///     seconds.
    /// - Throws: `MobileError` if the FFI rejects the upsert.
    public func recordProgress(
        editionId: DbId,
        formatId: DbId,
        progress: Double,
        progressUnit: ProgressUnit,
        lastLocation: String?,
        totalReadingTimeSecs: Int64
    ) async throws {
        try await Task.detached(priority: .utility) {
            try livtetFfiUpsertReadingProgress(
                editionId: editionId,
                formatId: formatId,
                progress: progress,
                progressUnit: progressUnit,
                lastLocation: lastLocation,
                totalReadingTimeSecs: totalReadingTimeSecs
            )
        }.value
    }
}