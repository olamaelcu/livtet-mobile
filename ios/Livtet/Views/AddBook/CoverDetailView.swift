import SwiftUI
struct CoverDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var urlString: String = ""
    var body: some View {
        DetailPageScaffold(title: "Cover Image",
            onSkip: { viewModel.clearItem(.cover); dismiss() }) {
            VStack(spacing: 16) {
                CoverImageView(url: viewModel.data.coverUrl, width: 120, height: 168)
                TextField("Image URL", text: $urlString).textFieldStyle(.roundedBorder)
                    .autocapitalization(.none).keyboardType(.URL)
                Button("Save") { viewModel.data.coverUrl = URL(string: urlString); dismiss() }
                    .buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }.onAppear { urlString = viewModel.data.coverUrl?.absoluteString ?? "" }
    }
}
