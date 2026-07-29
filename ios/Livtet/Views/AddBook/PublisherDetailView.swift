import SwiftUI
struct PublisherDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var draft: String = ""
    var body: some View {
        DetailPageScaffold(title: "Publisher",
            onSkip: { viewModel.clearItem(.publisher); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                TextField("Publisher name", text: $draft).textFieldStyle(.roundedBorder)
                Button("Save") { viewModel.data.publisher = draft; dismiss() }
                    .buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }.onAppear { draft = viewModel.data.publisher }
    }
}
