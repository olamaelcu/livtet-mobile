import SwiftUI

struct CoverImageView: View {
    let url: URL?
    let width: CGFloat
    let height: CGFloat
    var cornerRadius: CGFloat = LivtetRadius.s

    var body: some View {
        Group {
            if let url {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image): image.resizable().scaledToFill()
                    case .failure, .empty, _: placeholder
                    @unknown default: placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }

    private var placeholder: some View {
        ZStack {
            Color("surfaceHighlighted")
            Image(systemName: "book.closed")
                .font(.livtetHeading(size: min(width, height) * 0.4))
                .foregroundStyle(Color("textQuiet").opacity(0.4))
        }
    }
}
