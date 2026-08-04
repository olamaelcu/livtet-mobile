import SwiftUI
struct DescriptionDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var draft: String = ""
    var body: some View {
        DetailPageScaffold(title: "Description",
            onSkip: { viewModel.clearItem(.description); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                TextEditor(text: $draft).frame(minHeight: 200).padding(8)
                    .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceDefault")))
                Button("Save") { viewModel.data.description = draft; dismiss() }
                    .buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }.onAppear { draft = viewModel.data.description }
    }
}
