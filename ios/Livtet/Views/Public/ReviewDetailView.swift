import SwiftUI

/// Read-only review permalink: the review, who wrote it, when, and the edition
/// it is about.
///
/// The record is read from the reviewer's PDS, so this page works before any
/// index has seen the review.
struct ReviewDetailView: View {
    @StateObject private var viewModel: ReviewDetailViewModel

    init(did: String, rkey: String) {
        _viewModel = StateObject(wrappedValue: ReviewDetailViewModel(did: did, rkey: rkey))
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

    private func content(_ data: ReviewDetailData) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let book = data.book {
                    let strip = BookStripView(
                        coverURL: book.coverURL,
                        title: book.title,
                        author: book.author
                    )
                    if let atUri = AtUri(string: book.uri) {
                        NavigationLink(
                            value: PublicRoute.book(did: atUri.did, rkey: atUri.rkey)
                        ) {
                            strip
                        }
                        .buttonStyle(.plain)
                    } else {
                        strip
                    }
                }

                HStack(alignment: .top, spacing: 12) {
                    PublicAvatar(
                        url: data.reviewer?.avatar.flatMap { URL(string: $0) },
                        label: data.reviewerName,
                        size: 44
                    )
                    VStack(alignment: .leading, spacing: 2) {
                        Text(data.reviewerName)
                            .font(.livtetHeading(size: 17, weight: .medium))
                            .foregroundStyle(Color("textNormal"))
                        if let handle = data.reviewer?.handle?.nilIfEmpty {
                            Text("@\(handle)")
                                .font(.livtetBody(size: 12))
                                .foregroundStyle(Color("textQuiet"))
                        }
                    }
                    Spacer(minLength: 0)
                    if let rating = ratingLabel(data.review.record.rating) {
                        Text(rating)
                            .font(.livtetHeading(size: 17, weight: .semibold))
                            .foregroundStyle(Color("brand"))
                    }
                }

                Text(data.review.record.text)
                    .font(.livtetBody(size: 16))
                    .foregroundStyle(Color("textNormal"))

                if let date = PublicDateFormatting.string(from: data.review.record.createdAt) {
                    Text(date)
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                }

                NavigationLink(
                    value: PublicRoute.profile(actor: data.review.did, tab: .reviews, shelf: nil)
                ) {
                    Text("More reviews by this reader")
                        .font(.livtetBody(size: 14, weight: .semibold))
                        .foregroundStyle(Color("brand"))
                }
            }
            .padding(16)
        }
    }
}
