import LivtetKitFFI
import SwiftUI

struct EditionPickerSheet: View {
    let editions: [EditionSummary]
    let onPick: (DbId) -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationStack {
            List {
                if editions.isEmpty {
                    Text("No editions found").foregroundStyle(Color("textQuiet"))
                } else {
                    ForEach(Array(editions.enumerated()), id: \.element.id) { index, edition in
                        Button { onPick(edition.id) } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(edition.editionTitle ?? "Edition \(index + 1)")
                                    .font(.livtetBody(size: 14, weight: .medium))
                                if !edition.existingIsbns.isEmpty {
                                    Text(edition.existingIsbns.joined(separator: ", "))
                                        .font(.livtetBody(size: 12)).foregroundStyle(Color("textQuiet"))
                                } else {
                                    Text("No ISBN").font(.livtetBody(size: 12)).foregroundStyle(Color("textQuiet"))
                                }
                            }
                        }.buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle("Select Edition")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) { Button("Cancel") { onCancel() } }
            }
        }
    }
}
