// SPDX-License-Identifier: AGPL-3.0-only
//
// ReaderHostView.swift
// LivtetKit.Reader
//
// SwiftUI view that owns the Readium EPUB navigator. Phase 3 wires this view
// into `LibraryView`; this task only ships a compilable skeleton that renders
// a placeholder while the publication opens.
//

import Foundation
import SwiftUI
import UIKit

/// SwiftUI host view for the Readium EPUB navigator.
///
/// `ReaderHostView` takes the local file URL of an EPUB and an optional
/// `Locator` to resume from. While the publication is opening it renders a
/// `ProgressView`; once the publication is parsed it swaps in the
/// `EPUBNavigatorViewController` via `UIViewControllerRepresentable`.
///
/// Phase 3 inserts this view into `LibraryView` when a book row is tapped.
/// Phase 4 will wire reading-progress persistence (`SessionStore`); Phase 5
/// will add the reader settings UI. Neither is implemented here.
public struct ReaderHostView: View {
    /// Local `file://` URL of the EPUB to render.
    private let localFileURL: URL

    /// Optional starting `Locator`; `nil` opens at the beginning.
    private let initialLocator: ReadiumLocator?

    /// Adapter that owns the Readium opening pipeline. Injected so tests can
    /// swap in a mock; production callers pass `ReadiumAdapter()`.
    private let adapter: ReadiumAdapter

    /// The opened publication, set once the async open completes.
    @State private var publication: ReadiumPublication?

    /// Any error surfaced while opening the publication.
    @State private var openError: ReadiumAdapterError?

    public init(
        localFileURL: URL,
        initialLocator: ReadiumLocator? = nil,
        adapter: ReadiumAdapter = ReadiumAdapter()
    ) {
        self.localFileURL = localFileURL
        self.initialLocator = initialLocator
        self.adapter = adapter
    }

    public var body: some View {
        Group {
            if let publication {
                EPUBNavigatorHost(
                    publication: publication,
                    initialLocator: initialLocator,
                    adapter: adapter
                )
            } else if let openError {
                VStack(spacing: 12) {
                    Text("Unable to open book")
                        .font(.headline)
                    Text(openError.localizedDescription)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
            } else {
                ProgressView("Opening book…")
            }
        }
        .task {
            await open()
        }
    }

    /// Opens the publication at `localFileURL` and stores the result in
    /// `@State`. The navigator-creation path is stubbed for this task — it
    /// calls into `ReadiumAdapter` and `fatalError`s if the adapter isn't
    /// ready (Phase 3 will replace the placeholder with the real navigator).
    private func open() async {
        do {
            let opened = try await adapter.openPublication(at: localFileURL)
            publication = opened
        } catch {
            openError = error as? ReadiumAdapterError ?? ReadiumAdapterError.openFailed(error)
        }
    }
}

/// `UIViewControllerRepresentable` that bridges Readium's
/// `EPUBNavigatorViewController` into SwiftUI.
///
/// Constructed by `ReaderHostView` once the `Publication` is parsed. The
/// underlying view controller is created by `ReadiumAdapter.makeEpubNavigator`.
struct EPUBNavigatorHost: UIViewControllerRepresentable {
    let publication: ReadiumPublication
    let initialLocator: ReadiumLocator?
    let adapter: ReadiumAdapter

    func makeUIViewController(context: Context) -> EPUBNavigatorViewController {
        do {
            return try adapter.makeEpubNavigator(
                publication: publication,
                initialLocator: initialLocator
            )
        } catch {
            // The navigator cannot be constructed from a parsed publication
            // in normal operation. Phase 3 will surface a recoverable error
            // UI; for the skeleton, crash loudly so the failure is obvious
            // during development.
            fatalError("ReadiumAdapter.makeEpubNavigator failed — Phase 3 will surface a recoverable error UI: \(error)")
        }
    }

    func updateUIViewController(_ uiViewController: EPUBNavigatorViewController, context: Context) {
        // No preferences updates in the skeleton; Phase 5 wires settings.
    }
}