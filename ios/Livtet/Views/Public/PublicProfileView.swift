import SwiftUI

/// Read-only profile permalink: identity, reading-status counts, and the tabs
/// the platform publishes (`/profile/{did}?tab=…` plus the shelf routes).
///
/// Identity comes from the Bluesky AppView; reviews, shelving, and shelves come
/// from the actor's own PDS.
struct PublicProfileView: View {
    @StateObject private var viewModel: PublicProfileViewModel

    init(actor: String, tab: ProfileViewTab = .reviews, shelf: String? = nil) {
        _viewModel = StateObject(
            wrappedValue: PublicProfileViewModel(actor: actor, tab: tab, shelf: shelf)
        )
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                PublicLoadingView()
            case let .failed(message):
                PublicErrorView(message: message) { viewModel.load() }
            case let .loaded(data):
                content(data)
            }
        }
        .task { viewModel.load() }
    }

    private func content(_ data: ProfileData) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header(data)

                if data.unavailable {
                    Text("Reviews and shelves unavailable.")
                        .font(.livtetBody(size: 14))
                        .foregroundStyle(Color("textQuiet"))
                } else {
                    tabBar
                    if viewModel.selectedTab == .shelves {
                        shelvesSection(data)
                    } else {
                        reviewsSection(data)
                    }
                }
            }
            .padding(16)
        }
    }

    private func header(_ data: ProfileData) -> some View {
        HStack(alignment: .top, spacing: 16) {
            PublicAvatar(
                url: data.actor?.avatar.flatMap { URL(string: $0) },
                label: data.displayName,
                size: 64
            )
            VStack(alignment: .leading, spacing: 4) {
                Text(data.displayName)
                    .font(.livtetHeading(size: 20, weight: .semibold))
                    .foregroundStyle(Color("textNormal"))
                if data.actor?.displayName?.nilIfEmpty != nil,
                   let handle = data.actor?.handle?.nilIfEmpty {
                    Text("@\(handle)")
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                }
                if let description = data.actor?.description?.nilIfEmpty {
                    Text(description)
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                        .lineLimit(4)
                }
                // Five counters do not fit across a phone: scroll instead of
                // hyphenating "Abandoned".
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        stat("Total", data.stats.total)
                        stat("Finished", data.stats.finished)
                        stat("Reading", data.stats.reading)
                        stat("To read", data.stats.toRead)
                        stat("Abandoned", data.stats.abandoned)
                    }
                }
                .padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
    }

    private func stat(_ label: String, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("\(value)")
                .font(.livtetBody(size: 14, weight: .semibold))
                .foregroundStyle(Color("textNormal"))
            Text(label)
                .font(.livtetBody(size: 10))
                .foregroundStyle(Color("textQuiet"))
                .lineLimit(1)
                .fixedSize()
        }
    }

    private var tabBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ProfileViewTab.allCases) { tab in
                    let selected = viewModel.selectedTab == tab
                    Button(tab.label) {
                        if tab == .shelves {
                            viewModel.selectShelf(viewModel.selectedShelf)
                        } else {
                            viewModel.selectTab(tab)
                        }
                    }
                    .font(.livtetBody(size: 13, weight: selected ? .semibold : .regular))
                    .foregroundStyle(selected ? Color("brand") : Color("textQuiet"))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(selected ? Color("surfaceRaised") : Color.clear)
                    .clipShape(
                        RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous)
                    )
                }
            }
        }
    }

    @ViewBuilder
    private func reviewsSection(_ data: ProfileData) -> some View {
        let reviews = data.visibleReviews(for: viewModel.selectedTab)
        if reviews.isEmpty {
            emptyCopy(
                viewModel.selectedTab == .reviews
                    ? "No reviews yet."
                    : "No books with this status."
            )
        } else {
            ForEach(reviews, id: \.uri) { review in
                let bookUri = review.record.bookUri
                ReviewCardView(
                    review: review,
                    reviewer: data.reviewers[review.did],
                    book: bookUri.flatMap { data.reviewBooks[$0] },
                    reviewRoute: reviewRoute(for: review),
                    bookRoute: bookUri.flatMap { uri in
                        AtUri(string: uri).map { .book(did: $0.did, rkey: $0.rkey) }
                    }
                )
            }
        }
    }

    @ViewBuilder
    private func shelvesSection(_ data: ProfileData) -> some View {
        if viewModel.selectedShelf == nil {
            if data.shelves.isEmpty {
                emptyCopy("No shelves yet.")
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(data.shelves) { shelf in
                        Button {
                            viewModel.selectShelf(shelf.rkey)
                        } label: {
                            HStack {
                                Text(shelf.name)
                                    .font(.livtetBody(size: 15))
                                    .foregroundStyle(Color("textNormal"))
                                Spacer(minLength: 0)
                                Text("(\(shelf.bookCount))")
                                    .font(.livtetBody(size: 12))
                                    .foregroundStyle(Color("textQuiet"))
                            }
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        } else if viewModel.shelfBooksLoading {
            PublicLoadingView()
                .frame(height: 120)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                Button("All shelves") {
                    viewModel.selectShelf(nil)
                }
                .font(.livtetBody(size: 13, weight: .semibold))
                .foregroundStyle(Color("brand"))
                .padding(.vertical, 8)

                if viewModel.shelfBooks.isEmpty {
                    emptyCopy("Nothing on this shelf.")
                } else {
                    ForEach(viewModel.shelfBooks) { row in
                        if let atUri = AtUri(string: row.bookUri) {
                            NavigationLink(
                                value: PublicRoute.book(did: atUri.did, rkey: atUri.rkey)
                            ) {
                                shelfBookRow(row)
                            }
                            .buttonStyle(.plain)
                        } else {
                            shelfBookRow(row)
                        }
                    }
                }
            }
        }
    }

    private func shelfBookRow(_ row: ShelfBookRow) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(row.title)
                .font(.livtetBody(size: 15))
                .foregroundStyle(Color("textNormal"))
            if let status = row.status?.nilIfEmpty {
                Text(statusLabel(status))
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("textQuiet"))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }

    private func emptyCopy(_ text: String) -> some View {
        Text(text)
            .font(.livtetBody(size: 14))
            .foregroundStyle(Color("textQuiet"))
            .padding(.vertical, 8)
    }

    private func reviewRoute(for review: BiblioReview) -> PublicRoute? {
        guard let rkey = AtUri(string: review.uri)?.rkey else { return nil }
        return .review(did: review.did, rkey: rkey)
    }

    /// Human label for a `bookShelving.metadata.status` value.
    private func statusLabel(_ status: String) -> String {
        switch status {
        case "reading": return "Reading"
        case "read": return "Finished"
        case "to-read": return "To read"
        case "dnf": return "Abandoned"
        default: return status
        }
    }
}
