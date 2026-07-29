import SwiftUI
struct PublishedDateDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var draft: String = ""
    var body: some View {
        DetailPageScaffold(title: "Published Date",
            onSkip: { viewModel.clearItem(.publishedDate); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                TextField("YYYY-MM-DD", text: $draft).textFieldStyle(.roundedBorder).keyboardType(.numbersAndPunctuation)
                Button("Save") { viewModel.data.publishedDate = draft; dismiss() }
                    .buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }.onAppear { draft = viewModel.data.publishedDate }
    }
}
