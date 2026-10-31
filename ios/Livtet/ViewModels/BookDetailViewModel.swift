import Combine
import Foundation

/// What a book permalink renders once loaded.
struct BookDetailData {
    var book: BiblioBook
    var coverURL: URL?
    var reviews: [BiblioReview]
    var reviewers: [String: BiblioActor]

    var title: String { book.title?.nilIfEmpty ?? "Untitled" }
    var author: String? { book.authorLine }
}

/// Loads one catalog edition plus its reviews. Everything is read-only: the
/// edition from Bibliograph, the reviews from the AppView with a Constellation
/// fallback, exactly like the web page.
@MainActor
final class BookDetailViewModel: ObservableObject {
    @Published private(set) var state: PublicLoadState<BookDetailData> = .loading

    private let client: BiblioClient
    private let did: String
    private let rkey: String
    private var loadTask: Task<Void, Never>?

    init(did: String, rkey: String, client: BiblioClient = BiblioClient()) {
        self.did = did
        self.rkey = rkey
        self.client = client
    }

    private var editionUri: String {
        "at://\(did)/\(BiblioClient.editionCollection)/\(rkey)"
    }

    func load() {
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            await self?.performLoad()
        }
    }

    private func performLoad() async {
        state = .loading
        let uri = editionUri
        do {
            let response = try await client.getEdition(atUri: uri)
            guard !Task.isCancelled else { return }
            let book = response.value
            guard book.title?.nilIfEmpty != nil else {
                state = .failed("This book was not found.")
                return
            }
            let cover: URL?
            if let coverImage = book.coverImageUrl, let url = URL(string: coverImage) {
                cover = url
            } else {
                cover = try? await client.getImageForBook(atUri: uri)
            }
            guard !Task.isCancelled else { return }
            state = .loaded(BookDetailData(book: book, coverURL: cover, reviews: [], reviewers: [:]))

            // Reviews are secondary: a failure here leaves the page useful.
            let reviews = (try? await client.getBookReviews(atUri: uri)) ?? []
            guard !Task.isCancelled else { return }
            let reviewers = await client.actors(for: reviews.map(\.did))
            guard !Task.isCancelled else { return }
            state = .loaded(
                BookDetailData(book: book, coverURL: cover, reviews: reviews, reviewers: reviewers)
            )
        } catch {
            guard !Task.isCancelled else { return }
            state = .failed("This book could not be loaded.")
        }
    }
}
