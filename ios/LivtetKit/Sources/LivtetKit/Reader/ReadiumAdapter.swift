// SPDX-License-Identifier-Identifier: AGPL-3.0-only
//
// ReadiumAdapter.swift
// LivtetKit.Reader
//
// Wraps the Readium Swift toolkit's publication-opening pipeline so the rest
// of the reader module has a single, app-friendly entry point. v1 is EPUB
// only; PDF / audiobook / CBZ navigators are out of scope (Fable plan P2.7).
//
// NOTE: The Readium import is commented out because the Readium SPM
// dependency could not be resolved in this environment — see the task
// report for details. The types and method signatures below match the
// verified Readium 3.11.0 API surface and will compile once the
// dependency is available. Phase 3 will uncomment the imports and
// verify against the resolved package.
//

// import ReadiumNavigator
// import ReadiumShared
// import ReadiumStreamer
import Foundation
import UIKit

/// Errors surfaced by `ReadiumAdapter` while opening a publication or
/// constructing an EPUB navigator.
public enum ReadiumAdapterError: Error, LocalizedError {
    /// The file at the supplied URL is not a `file://` URL. v1 reads local
    /// files only.
    case notAFileURL(URL)

    /// Readium's `AssetRetriever` could not sniff the asset's format or the
    /// asset could not be opened.
    case openFailed(any Error)

    /// The EPUB navigator could not be constructed from the parsed
    /// publication (e.g. the publication is DRM-restricted).
    case navigatorInitFailed(any Error)

    public var errorDescription: String? {
        switch self {
        case let .notAFileURL(url):
            return "Expected a file URL, got \(url.absoluteString)"
        case let .openFailed(error):
            return "Readium could not open the publication: \(error.localizedDescription)"
        case let .navigatorInitFailed(error):
            return "Readium EPUB navigator init failed: \(error.localizedDescription)"
        }
    }
}

/// Placeholder for Readium's `Publication` type. Replace with
/// `ReadiumShared.Publication` once the SPM dependency resolves.
public typealias ReadiumPublication = Any

/// Placeholder for Readium's `Locator` type. Replace with
/// `ReadiumShared.Locator` once the SPM dependency resolves.
public typealias ReadiumLocator = Any

/// Placeholder for Readium's `EPUBNavigatorViewController` type. Replace
/// with `ReadiumNavigator.EPUBNavigatorViewController` once the SPM
/// dependency resolves.
public typealias EPUBNavigatorViewController = UIViewController

/// Wraps Readium's publication-opening pipeline (`AssetRetriever` +
/// `PublicationOpener`) and the EPUB navigator factory.
///
/// v1 is EPUB only. The adapter holds an `HTTPClient` configured for
/// no-network reads (local files only); Readium requires an HTTP client even
/// for local files because some publications reference remote resources.
/// PDF / audiobook / CBZ navigator factories are intentionally absent.
///
/// NOTE: The implementation is stubbed because the Readium SPM dependency
/// could not be resolved in this environment. The `fatalError` calls name
/// the phase that will fill them in.
public final class ReadiumAdapter {
    /// Creates a new adapter wired for local-file EPUB reads.
    public init() {}

    /// Opens a publication file at the given local `URL` and returns the
    /// parsed `Publication` plus the sniffed `Format`.
    ///
    /// - Parameter url: A `file://` URL pointing at the publication on disk.
    /// - Returns: The opened `Publication` and its detected `Format`.
    /// - Throws: `ReadiumAdapterError.notAFileURL` if `url` is not a file URL,
    ///   or `.openFailed` if Readium's retriever or opener rejects the asset.
    public func openPublication(at url: URL) async throws -> ReadiumPublication {
        fatalError("not yet implemented — Phase 3 (Readium SPM dependency unresolved in this environment)")
    }

    /// Constructs an `EPUBNavigatorViewController` for the given publication.
    ///
    /// - Parameters:
    ///   - publication: The parsed EPUB publication to render.
    ///   - initialLocator: Optional starting `Locator`; `nil` opens at the
    ///     beginning.
    /// - Returns: A configured `EPUBNavigatorViewController`.
    /// - Throws: `ReadiumAdapterError.navigatorInitFailed` wrapping the
    ///   Readium error if the navigator cannot be built.
    @MainActor
    public func makeEpubNavigator(
        publication: ReadiumPublication,
        initialLocator: ReadiumLocator?
    ) throws -> EPUBNavigatorViewController {
        fatalError("not yet implemented — Phase 3 (Readium SPM dependency unresolved in this environment)")
    }
}