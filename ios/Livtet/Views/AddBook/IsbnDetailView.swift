import SwiftUI
struct IsbnDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var draft: String = ""
    var body: some View {
        DetailPageScaffold(title: "ISBN",
            onSkip: { viewModel.clearItem(.isbn); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                TextField("ISBN", text: $draft).textFieldStyle(.roundedBorder).keyboardType(.numbersAndPunctuation)
                Button("Save") { viewModel.updateIsbn(draft); dismiss() }
                    .buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }.onAppear { draft = viewModel.data.isbn }
    }
}
