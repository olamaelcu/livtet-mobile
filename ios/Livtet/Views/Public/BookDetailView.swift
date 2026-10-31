import SwiftUI

/// Read-only book permalink: cover, title, authors, description, the edition's
/// metadata, its contributors, and the first page of reviews.
///
/// There is deliberately no shelving, editing, sharing, or "more by this
/// author" action — those are services of the signed-in web app.
struct BookDetailView: View {
    @StateObject private var viewModel: BookDetailViewModel

    init(did: String, rkey: String) {
        _viewModel = StateObject(wrappedValue: BookDetailViewModel(did: did, rkey: rkey))
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

    private func content(_ data: BookDetailData) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 16) {
                    PublicCover(url: data.coverURL, title: data.title, width: 120, height: 180)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(data.title)
                            .font(.livtetHeading(size: 22, weight: .semibold))
                            .foregroundStyle(Color("textNormal"))
                        if let author = data.author {
                            Text(author)
                                .font(.livtetBody(size: 15, weight: .medium))
                                .foregroundStyle(Color("textQuiet"))
                        }
                        if let format = data.book.format {
                            let label = [format.emoji, format.description]
                                .compactMap { $0 }
                                .filter { !$0.isEmpty }
                                .joined(separator: " ")
                            if !label.isEmpty {
                                Text(label)
                                    .font(.livtetBody(size: 12))
                                    .foregroundStyle(Color("textQuiet"))
                                    .padding(.top, 4)
                            }
                        }
                    }
                    Spacer(minLength: 0)
                }

                if let description = data.book.description?.nilIfEmpty {
                    Text(description)
                        .font(.livtetBody(size: 14))
                        .foregroundStyle(Color("textNormal"))
                }

                metadata(data.book)

                if let genres = data.book.genres?.filter({ ($0.name ?? "").isEmpty == false }), !genres.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(genres.enumerated()), id: \.offset) { _, genre in
                                Text([genre.emoji, genre.name].compactMap { $0 }.joined(separator: " "))
                                    .font(.livtetBody(size: 12))
                                    .foregroundStyle(Color("textNormal"))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color("surfaceRaised"))
                                    .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous))
                            }
                        }
                    }
                }

                contributors(data.book)

                Divider().overlay(Color("surfaceBorder"))

                PublicSectionLabel(text: "Reviews")

                if data.reviews.isEmpty {
                    Text("No reviews yet.")
                        .font(.livtetBody(size: 14))
                        .foregroundStyle(Color("textQuiet"))
                } else {
                    ForEach(data.reviews, id: \.uri) { review in
                        ReviewCardView(
                            review: review,
                            reviewer: data.reviewers[review.did],
                            book: nil,
                            reviewRoute: reviewRoute(for: review),
                            bookRoute: nil
                        )
                    }
                }
            }
            .padding(16)
        }
    }

    @ViewBuilder
    private func metadata(_ book: BiblioBook) -> some View {
        let published = book.publishDate ?? book.publishedYear.map { String($0) }
        let candidates = [
            MetadataRow(label: "Pages", value: book.pages.map { String($0) }),
            MetadataRow(label: "Published", value: published),
            MetadataRow(label: "Language", value: book.language),
            MetadataRow(label: "ISBN", value: book.isbn),
        ]
        let rows: [MetadataRow] = candidates.compactMap { row -> MetadataRow? in
            guard let value = row.value, !value.isEmpty else { return nil }
            return MetadataRow(label: row.label, value: value)
        }
        if !rows.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(rows) { row in
                    HStack(alignment: .top) {
                        Text(row.label)
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                            .frame(width: 88, alignment: .leading)
                        Text(row.value ?? "")
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textNormal"))
                        Spacer(minLength: 0)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func contributors(_ book: BiblioBook) -> some View {
        let rows = (book.contributors ?? []).filter { $0.person?.name?.nilIfEmpty != nil }
        if !rows.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, contribution in
                    let role = contribution.role?.replacingOccurrences(of: "_", with: " ") ?? ""
                    Text(
                        [contribution.person?.name, role.nilIfEmpty]
                            .compactMap { $0 }
                            .joined(separator: " — ")
                    )
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("textQuiet"))
                }
            }
        }
    }

    private func reviewRoute(for review: BiblioReview) -> PublicRoute? {
        guard let rkey = AtUri(string: review.uri)?.rkey else { return nil }
        return .review(did: review.did, rkey: rkey)
    }
}

/// One `label: value` line of the edition metadata block.
private struct MetadataRow: Identifiable {
    let label: String
    let value: String?

    var id: String { label }
}
