import SwiftUI

struct ChipInputRow: View {
    @Binding var items: [String]
    let placeholder: String
    @State private var draft: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !items.isEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(items, id: \.self) { item in
                        ChipPill(label: item) {
                            items.removeAll { $0 == item }
                        }
                    }
                }
            }
            HStack {
                TextField(placeholder, text: $draft)
                    .textFieldStyle(.roundedBorder)
                Button("Add") {
                    let trimmed = draft.trimmingCharacters(in: .whitespaces)
                    guard !trimmed.isEmpty, !items.contains(trimmed) else { return }
                    items.append(trimmed)
                    draft = ""
                }
                .buttonStyle(.borderedProminent)
                .tint(.brand)
                .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
    }
}

private struct ChipPill: View {
    let label: String
    let onRemove: () -> Void
    var body: some View {
        HStack(spacing: 4) {
            Text(label).font(.livtetBody(size: 13))
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(Color("textQuiet"))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove \(label)")
            .accessibilityHint("Double tap to remove this item")
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceHighlighted")))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(label)
    }
}

private struct FlowLayout: Layout {
    var spacing: CGFloat = 6
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0, rowHeight: CGFloat = 0, totalHeight: CGFloat = 0, totalWidth: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth)
                rowWidth = 0; rowHeight = 0
            }
            rowWidth += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth)
        return CGSize(width: totalWidth, height: totalHeight)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var originX: CGFloat = bounds.minX, originY: CGFloat = bounds.minY, rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if originX + size.width > bounds.maxX {
                originX = bounds.minX; originY += rowHeight + spacing; rowHeight = 0
            }
            subview.place(at: CGPoint(x: originX, y: originY), proposal: ProposedViewSize(size))
            originX += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
