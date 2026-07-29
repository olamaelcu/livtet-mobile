import SwiftUI
struct GenresDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        DetailPageScaffold(title: "Genres",
            onSkip: { viewModel.clearItem(.genres); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                ChipInputRow(items: $viewModel.data.genres, placeholder: "Add a genre")
                Button("Save") { dismiss() }.buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }
    }
}
