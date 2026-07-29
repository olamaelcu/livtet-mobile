// SPDX-License-Identifier-Identifier: AGPL-3.0-only
//
// FileCacheManager.swift
// LivtetKit.Reader
//
// iOS equivalent of Android's `FileCacheManager`. Resolves cached file paths
// via the FFI and registers newly downloaded files. Blake3 verification
// happens on the Rust side; the Swift side does not re-implement hashing.
//

import Foundation
import LivtetKitFFI

/// Resolves cached file paths and registers local files for the reader
/// module.
///
/// The cache root is the app's `Application Support` directory, matching the
/// existing `livtet.dat` location documented in the iOS README. Blake3 hash
/// verification is performed by the FFI's `register_local_file` — this type
/// does not re-implement hashing.
public final class FileCacheManager {
    /// Cache root directory (the app's `Application Support` directory).
    public let cacheRoot: URL

    /// Creates a new `FileCacheManager` rooted at the app's
    /// `Application Support` directory.
    public init() {
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

    /// Returns the cached file path for an inventory item, if one is
    /// registered.
    ///
    /// - Parameter id: The `digital_inventory` row id.
    /// - Returns: A `file://` URL if the FFI has a registered path, `nil`
    ///   otherwise.
    /// - Throws: `MobileError` if the FFI rejects the lookup.
    public func localPath(forInventoryId id: DbId) throws -> URL? {
        guard let pathString = try livtetFfiGetCachedFilePath(inventoryId: id) else {
            return nil
        }
        return URL(fileURLWithPath: pathString)
    }

    /// Registers a local file for an inventory item.
    ///
    /// The Rust side computes the Blake3 hash and stores the row in
    /// `digital_inventory`; the Swift side does not re-implement hashing.
    ///
    /// - Parameters:
    ///   - localPath: The `file://` URL of the downloaded file.
    ///   - inventoryId: The `digital_inventory` row id to associate the file
    ///     with.
    /// - Returns: The resulting `CachedFile` row.
    /// - Throws: `MobileError` if the FFI rejects the registration.
    public func registerLocalFile(localPath: URL, inventoryId: DbId) async throws -> CachedFile {
        let pathString = localPath.path
        return try await Task.detached(priority: .utility) {
            try livtetFfiRegisterLocalFile(
                inventoryId: inventoryId,
                localPath: pathString
            )
        }.value
    }
}