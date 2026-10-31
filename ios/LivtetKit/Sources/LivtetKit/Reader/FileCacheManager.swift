// SPDX-License-Identifier-Identifier: AGPL-3.0-only
//
// FileCacheManager.swift
// LivtetKit.Reader
//
// iOS equivalent of Android's `FileCacheManager`. Resolves cached file paths
// via `LivtetStore` and registers newly downloaded files. Blake3 verification
// happens on the Rust side; the Swift side does not re-implement hashing.
//

import Foundation

/// Resolves cached file paths and registers local files for the reader
/// module.
///
/// File metadata lives in the edition's 1:1 `digital_inventory` row, exposed
/// through the FFI as `EditionDetail.file` (`EditionFile`). The cache root is
/// the app's `Application Support` directory, matching the existing
/// `livtet.dat` location documented in the iOS README. Blake3 hash
/// verification is performed by the FFI's `setEditionFile` — this type does
/// not re-implement hashing.
public final class FileCacheManager {
    /// Cache root directory (the app's `Application Support` directory).
    public let cacheRoot: URL

    /// Store used for all database access.
    private let store: LivtetStore

    /// Creates a new `FileCacheManager` rooted at the app's
    /// `Application Support` directory.
    ///
    /// - Parameter store: The open `LivtetStore` handle.
    public init(store: LivtetStore) {
        self.store = store

        let fm = FileManager.default
        if let appSupport = try? fm.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ) {
            self.cacheRoot = appSupport
        } else {
            self.cacheRoot = URL(fileURLWithPath: NSTemporaryDirectory())
        }
    }

    /// Returns the cached file path for an edition, if one is registered.
    ///
    /// - Parameter id: The edition id.
    /// - Returns: A `file://` URL if the FFI has a registered path, `nil`
    ///   otherwise.
    /// - Throws: `LivtetError` if the FFI rejects the lookup.
    public func localPath(forEditionId id: DbId) async throws -> URL? {
        guard let pathString = try await store.getEdition(id: id)?.file?.filePath else {
            return nil
        }
        return URL(fileURLWithPath: pathString)
    }

    /// Registers a local file for an edition.
    ///
    /// The Rust side computes the Blake3 hash and stores the row in
    /// `digital_inventory`; the Swift side does not re-implement hashing.
    ///
    /// - Parameters:
    ///   - localPath: The `file://` URL of the downloaded file.
    ///   - editionId: The edition whose digital file is attached.
    ///   - fileFormat: Optional format label (e.g. `EPUB`).
    ///   - fileSizeBytes: Optional file size hint.
    /// - Returns: The resulting `EditionFile` row.
    /// - Throws: `LivtetError` if the FFI rejects the registration.
    public func registerLocalFile(
        localPath: URL,
        editionId: DbId,
        fileFormat: String? = nil,
        fileSizeBytes: Int64? = nil
    ) async throws -> EditionFile {
        try await store.setEditionFile(
            editionId: editionId,
            filePath: localPath.path,
            fileFormat: fileFormat,
            fileSizeBytes: fileSizeBytes
        )
        guard let file = try await store.getEdition(id: editionId)?.file else {
            throw LivtetError.NotFound(entity: "digital_inventory", id: editionId)
        }
        return file
    }
}
