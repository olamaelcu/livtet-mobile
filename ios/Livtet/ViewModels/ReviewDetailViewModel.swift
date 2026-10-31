import Combine
import Foundation

/// What a review permalink renders once loaded.
struct ReviewDetailData {
    var review: BiblioReview
    var reviewer: BiblioActor?
    var book: BookSummary?

    var reviewerName: String {
        reviewer?.displayName?.nilIfEmpty ?? reviewer?.handle ?? review.did
    }
}

/// Loads one review from its author's PDS, plus the reviewer's public profile
/// and the book it points at. The review itself is authoritative on the PDS;
/// the profile and book are presentation and are allowed to be missing.
@MainActor
final class ReviewDetailViewModel: ObservableObject {
    @Published private(set) var state: PublicLoadState<ReviewDetailData> = .loading

    private let client: BiblioClient
    private let did: String
    private let rkey: String
    private var loadTask: Task<Void, Never>?

    init(did: String, rkey: String, client: BiblioClient = BiblioClient()) {
        self.did = did
        self.rkey = rkey
        self.client = client
    }

    func load() {
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            await self?.performLoad()
        }
    }

    private func performLoad() async {
        state = .loading
        let did = did
        let rkey = rkey
        let review = try? await client.getReview(did: did, rkey: rkey)
        guard !Task.isCancelled else { return }
        guard let review else {
            state = .failed("This review could not be loaded.")
            return
        }
        let reviewer = try? await client.getActor(actor: did)
        guard !Task.isCancelled else { return }
        var book: BookSummary?
        if let bookUri = review.record.bookUri {
            book = await client.summarise(bookUri: bookUri)
        }
        guard !Task.isCancelled else { return }
        state = .loaded(ReviewDetailData(review: review, reviewer: reviewer, book: book))
    }
}
