import Foundation
import LivtetKit
import LivtetKitFFI
import PhotosUI
import SwiftUI
import os.log

@MainActor
final class SetCoverViewModel: ObservableObject {
    let edition: Edition
    let onComplete: () -> Void

    @Published var selectedTab = 0

    // Search
    @Published var searchQuery: String = ""
    @Published var searchResults: [CoverSearchResult] = []
    @Published var isSearching = false
    @Published var searchError: String?
    @Published var searchSource: SearchSource?

    // URL
    @Published var urlInput: String = ""
    @Published var isDownloadingUrl = false
    @Published var urlError: String?

    // Gallery
    @Published var selectedPhoto: PhotosPickerItem?
    @Published var selectedPhotoPreview: Image?
    @Published var isSavingPhoto = false
    @Published var galleryError: String?

    private let libraryBridge: LibraryBridge
    private let wizardBridge: WizardBridge
    private let googleBooksClient: GoogleBooksClient
    private let openLibraryClient: OpenLibraryClient
    private let imageDownloader: ImageDownloader
    private static let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "SetCoverViewModel")

    init(
        edition: Edition,
        libraryBridge: LibraryBridge,
        wizardBridge: WizardBridge,
        googleBooksClient: GoogleBooksClient = GoogleBooksClient(),
        openLibraryClient: OpenLibraryClient = OpenLibraryClient(),
        imageDownloader: ImageDownloader = ImageDownloader(),
        onComplete: @escaping () -> Void
    ) {
        self.edition = edition
        self.libraryBridge = libraryBridge
        self.wizardBridge = wizardBridge
        self.googleBooksClient = googleBooksClient
        self.openLibraryClient = openLibraryClient
        self.imageDownloader = imageDownloader
        self.onComplete = onComplete
        searchQuery = edition.workTitle ?? edition.editionTitle ?? ""
    }

    func runSearch() {
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        guard query.count >= 3 else { return }
        isSearching = true
        searchError = nil
        searchSource = nil
        Task {
            do {
                let items = try await googleBooksClient.search(query: query)
                searchResults = items.compactMap { item -> CoverSearchResult? in
                    guard let raw = item.volumeInfo.imageLinks?.thumbnail,
                          let cleanedURL = GoogleBooksClient.cleanCoverURL(raw).flatMap(URL.init(string:))
                    else { return nil }
                    return CoverSearchResult(
                        title: item.volumeInfo.title,
                        coverUrl: cleanedURL,
                        source: SearchSource.googleBooks.rawValue
                    )
                }
                searchSource = .googleBooks
                Self.logger.info("Cover search served by googleBooks: \(self.searchResults.count, privacy: .public) items")
            } catch let error as GoogleBooksError {
                switch error {
                case .providerDown:
                    Self.logger.notice("Google Books cover search returned providerDown; falling back to OpenLibrary")
                    do {
                        let docs = try await openLibraryClient.search(query: query)
                        searchResults = docs.compactMap { doc -> CoverSearchResult? in
                            guard let coverID = doc.coverI,
                                  let coverURL = OpenLibraryClient.coverURL(for: coverID)
                            else { return nil }
                            return CoverSearchResult(
                                title: doc.title ?? "",
                                coverUrl: coverURL,
                                source: SearchSource.openLibrary.rawValue
                            )
                        }
                        searchSource = .openLibrary
                        Self.logger.info("Cover search served by openLibrary (fallback): \(self.searchResults.count, privacy: .public) items")
                    } catch let fallbackError {
                        Self.logger.error("OpenLibrary cover fallback failed: \(fallbackError.localizedDescription, privacy: .public)")
                        searchError = error.localizedDescription
                    }
                default:
                    searchError = error.localizedDescription
                }
            } catch {
                searchError = error.localizedDescription
            }
            if searchResults.isEmpty {
                searchSource = nil
            }
            isSearching = false
        }
    }

    func applySearchResult(_ result: CoverSearchResult) async {
        guard let url = result.coverUrl else { return }
        searchError = nil
        do {
            let localUrl = try await imageDownloader.download(from: url)
            try libraryBridge.setEditionManualCover(editionId: edition.id, localPath: localUrl.path)
            onComplete()
        } catch {
            searchError = error.localizedDescription
        }
    }

    func applyUrl() async {
        guard let url = URL(string: urlInput.trimmingCharacters(in: .whitespaces)) else {
            urlError = "Invalid URL"
            return
        }
        isDownloadingUrl = true
        urlError = nil
        do {
            let localUrl = try await imageDownloader.download(from: url)
            try libraryBridge.setEditionManualCover(editionId: edition.id, localPath: localUrl.path)
            onComplete()
        } catch {
            urlError = error.localizedDescription
        }
        isDownloadingUrl = false
    }

    func applyPhotoItem() async {
        guard let item = selectedPhoto else { return }
        isSavingPhoto = true
        galleryError = nil
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                galleryError = "Could not load image data"
                isSavingPhoto = false
                return
            }
            let localUrl = try imageDownloader.save(imageData: data, id: edition.id.description)
            try libraryBridge.setEditionManualCover(editionId: edition.id, localPath: localUrl.path)
            onComplete()
        } catch {
            galleryError = error.localizedDescription
        }
        isSavingPhoto = false
    }

    func loadPhotoPreview() async {
        guard let item = selectedPhoto else { return }
        do {
            guard let data = try await item.loadTransferable(type: Data.self),
                  let uiImage = UIImage(data: data) else { return }
            selectedPhotoPreview = Image(uiImage: uiImage)
        } catch {}
    }
}
