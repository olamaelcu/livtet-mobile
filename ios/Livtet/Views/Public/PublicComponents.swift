import SwiftUI

/// Circular avatar. Falls back to the first letter of `label` so a reader
/// without a picture is still identifiable.
struct PublicAvatar: View {
    let url: URL?
    let label: String
    let size: CGFloat

    var body: some View {
        Group {
            if let url {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case let .success(image): image.resizable().scaledToFill()
                    default: fallback
                    }
                }
            } else {
                fallback
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var fallback: some View {
        ZStack {
            Color("surfaceRaised")
            Text(label.trimmingCharacters(in: .whitespaces).prefix(1).uppercased())
                .font(.livtetHeading(size: size * 0.4))
                .foregroundStyle(Color("textQuiet"))
        }
    }
}

/// A cover with a placeholder when the catalog has no image.
struct PublicCover: View {
    let url: URL?
    let title: String
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        Group {
            if let url {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case let .success(image): image.resizable().scaledToFill()
                    default: placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.s, style: .continuous))
    }

    private var placeholder: some View {
        ZStack {
            Color("surfaceRaised")
            Image(systemName: "book.closed")
                .font(.livtetHeading(size: min(width, height) * 0.4))
                .foregroundStyle(Color("textQuiet").opacity(0.4))
        }
    }
}

/// Small heading that separates blocks inside a screen.
struct PublicSectionLabel: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.livtetBody(size: 12, weight: .semibold))
            .foregroundStyle(Color("textQuiet"))
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct PublicLoadingView: View {
    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text("Loading…")
                .font(.livtetBody(size: 12))
                .foregroundStyle(Color("textQuiet"))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct PublicErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text(message)
                .font(.livtetBody(size: 14))
                .foregroundStyle(Color("textNormal"))
                .multilineTextAlignment(.center)
            Button("Retry", action: onRetry)
                .font(.livtetBody(size: 13, weight: .semibold))
                .foregroundStyle(Color("brand"))
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// The edition a review points at, as a tappable strip.
struct BookStripView: View {
    let coverURL: URL?
    let title: String
    let author: String?

    var body: some View {
        HStack(spacing: 12) {
            PublicCover(url: coverURL, title: title, width: 40, height: 60)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.livtetBody(size: 14, weight: .medium))
                    .foregroundStyle(Color("textNormal"))
                    .lineLimit(2)
                if let author, !author.isEmpty {
                    Text(author)
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(8)
        .background(Color("surfaceRaised"))
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous))
    }
}

/// One review, as the book page and the profile tabs show it.
///
/// The review and the edition it points at are two sibling links rather than
/// nested ones: nested `NavigationLink`s inside a sheet's stack do not deliver
/// taps reliably.
struct ReviewCardView: View {
    let review: BiblioReview
    let reviewer: BiblioActor?
    let book: BookSummary?
    let reviewRoute: PublicRoute?
    let bookRoute: PublicRoute?

    private var reviewerName: String {
        reviewer?.displayName?.nilIfEmpty ?? reviewer?.handle ?? review.did
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let reviewRoute {
                NavigationLink(value: reviewRoute) {
                    textBlock
                }
                .buttonStyle(.plain)
            } else {
                textBlock
            }

            if let book, let bookRoute {
                NavigationLink(value: bookRoute) {
                    BookStripView(coverURL: book.coverURL, title: book.title, author: book.author)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(Color("surfaceDefault"))
        .overlay(
            RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous)
                .stroke(Color("surfaceBorder"), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous))
    }

    private var textBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                PublicAvatar(
                    url: reviewer?.avatar.flatMap { URL(string: $0) },
                    label: reviewerName,
                    size: 36
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(reviewerName)
                        .font(.livtetBody(size: 13, weight: .medium))
                        .foregroundStyle(Color("textNormal"))
                        .lineLimit(1)
                    if let handle = reviewer?.handle?.nilIfEmpty {
                        Text("@\(handle)")
                            .font(.livtetBody(size: 11))
                            .foregroundStyle(Color("textQuiet"))
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                if let rating = ratingLabel(review.record.rating) {
                    Text(rating)
                        .font(.livtetBody(size: 13, weight: .semibold))
                        .foregroundStyle(Color("brand"))
                }
            }
            Text(review.record.text)
                .font(.livtetBody(size: 14))
                .foregroundStyle(Color("textNormal"))
                .lineLimit(10)
                .multilineTextAlignment(.leading)
            if let date = PublicDateFormatting.string(from: review.record.createdAt) {
                Text(date)
                    .font(.livtetBody(size: 11))
                    .foregroundStyle(Color("textQuiet"))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}
