import Combine
import Foundation

/// What a profile permalink renders once loaded.
struct ProfileData {
    var actor: BiblioActor?
    var did: String
    var stats: ProfileStats
    var reviews: [BiblioReview]
    var reviewers: [String: BiblioActor]
    var reviewBooks: [String: BookSummary]
    var statusByBookUri: [String: String]
    var shelves: [ShelfSummary]
    /// True when the actor's PDS cannot be resolved: reviews and shelves are
    /// unreachable, but the identity header still renders.
    var unavailable: Bool

    var displayName: String {
        actor?.displayName?.nilIfEmpty
            ?? actor?.handle.map { "@\($0)" }
            ?? did.split(separator: ":").last.map(String.init)
            ?? did
    }

    /// Reviews the selected tab shows, after applying its status filter.
    func visibleReviews(for tab: ProfileViewTab) -> [BiblioReview] {
        guard let wanted = tab.shelvingStatus else { return reviews }
        return reviews.filter { statusByBookUri[$0.record.bookUri ?? ""] == wanted }
    }
}

/// Loads everything a profile permalink renders: identity from the Bluesky
/// AppView, then reviews, shelving, and shelf records straight from the actor's
/// PDS — the same split the platform uses, because the AppView's shelf reads
/// return nothing for real actors.
@MainActor
final class PublicProfileViewModel: ObservableObject {
    @Published private(set) var state: PublicLoadState<ProfileData> = .loading
    @Published private(set) var selectedTab: ProfileViewTab
    @Published private(set) var selectedShelf: String?
    @Published private(set) var shelfBooks: [ShelfBookRow] = []
    @Published private(set) var shelfBooksLoading = false

    private let client: BiblioClient
    private let actor: String
    private var loadTask: Task<Void, Never>?
    private var shelfTask: Task<Void, Never>?
    private var shelving: [BiblioShelving] = []

    init(
        actor: String,
        tab: ProfileViewTab = .reviews,
        shelf: String? = nil,
        client: BiblioClient = BiblioClient()
    ) {
        self.actor = actor
        selectedTab = tab
        selectedShelf = shelf
        self.client = client
    }

    func load() {
        loadTask?.cancel()
        let initialTab = selectedTab
        let initialShelf = selectedShelf
        loadTask = Task { [weak self] in
            await self?.performLoad(initialTab: initialTab, initialShelf: initialShelf)
        }
    }

    func selectTab(_ tab: ProfileViewTab) {
        selectedTab = tab
        if tab == .shelves, selectedShelf == nil {
            shelfBooks = []
        }
    }

    /// Opens one shelf. Titles are hydrated from the catalog here rather than
    /// during `load`, so a profile with hundreds of shelved books stays cheap.
    func selectShelf(_ rkey: String?) {
        selectedTab = .shelves
        selectedShelf = rkey
        shelfTask?.cancel()
        guard let rkey, !rkey.isEmpty else {
            shelfBooks = []
            shelfBooksLoading = false
            return
        }
        let entries = shelving.filter { AtUri(string: $0.value.shelf)?.rkey == rkey }
        shelfBooksLoading = true
        shelfBooks = []
        shelfTask = Task { [weak self] in
            guard let self else { return }
            // Reader-app records carry the title inline; only the ones that do
            // not need a catalog round-trip.
            let summaries = await client.summaries(
                for: entries
                    .filter { $0.value.book?.title?.nilIfEmpty == nil }
                    .compactMap { $0.value.book?.atUri }
            )
            guard !Task.isCancelled else { return }
            shelfBooks = entries.map { entry in
                let uri = entry.value.book?.atUri
                let title = uri.flatMap { summaries[$0]?.title }
                    ?? entry.value.book?.title?.nilIfEmpty
                    ?? uri.flatMap { AtUri(string: $0)?.rkey }
                    ?? "Untitled"
                return ShelfBookRow(
                    shelfRkey: rkey,
                    bookUri: uri,
                    title: title,
                    status: entry.value.metadata?.status
                )
            }
            shelfBooksLoading = false
        }
    }

    private func performLoad(initialTab: ProfileViewTab, initialShelf: String?) async {
        state = .loading
        let identity = try? await client.getActor(actor: actor)
        guard !Task.isCancelled else { return }
        let did = identity?.did.nilIfEmpty ?? (actor.hasPrefix("did:") ? actor : nil)
        guard let did else {
            state = .failed("This profile could not be found.")
            return
        }

        guard (try? await client.resolvePds(did: did)) != nil else {
            state = .loaded(
                ProfileData(
                    actor: identity,
                    did: did,
                    stats: ProfileStats(),
                    reviews: [],
                    reviewers: [:],
                    reviewBooks: [:],
                    statusByBookUri: [:],
                    shelves: [],
                    unavailable: true
                )
            )
            return
        }
        async let reviewsTask = client.listActorReviews(did: did)
        async let shelvingTask = client.listActorShelving(did: did)
        async let shelvesTask = client.listActorShelves(did: did)
        let (reviews, shelvingRecords, shelfNames) = await (reviewsTask, shelvingTask, shelvesTask)
        guard !Task.isCancelled else { return }
        shelving = shelvingRecords

        let reviewers = await client.actors(for: reviews.map(\.did))
        guard !Task.isCancelled else { return }
        let reviewBooks = await client.summaries(for: reviews.compactMap { $0.record.bookUri })
        guard !Task.isCancelled else { return }

        let counts = shelfCounts(shelvingRecords)
        var shelves = shelfNames
            .map { ShelfSummary(rkey: $0.key, name: $0.value, bookCount: counts[$0.key] ?? 0) }
            .sorted { $0.rkey < $1.rkey }
        if shelves.isEmpty {
            // A reader can have shelving records whose shelf record is missing;
            // show those shelves anyway.
            shelves = counts
                .map { ShelfSummary(rkey: $0.key, name: $0.key, bookCount: $0.value) }
                .sorted { $0.rkey < $1.rkey }
        }

        state = .loaded(
            ProfileData(
                actor: identity,
                did: did,
                stats: shelvingStats(shelvingRecords),
                reviews: reviews,
                reviewers: reviewers,
                reviewBooks: reviewBooks,
                statusByBookUri: statusByBookUri(shelvingRecords),
                shelves: shelves,
                unavailable: false
            )
        )
        selectedTab = initialTab
        if initialTab == .shelves {
            selectShelf(initialShelf)
        }
    }
}
