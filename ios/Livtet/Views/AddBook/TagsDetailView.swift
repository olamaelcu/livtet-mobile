import SwiftUI
struct TagsDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        DetailPageScaffold(title: "Tags",
            onSkip: { viewModel.clearItem(.tags); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                ChipInputRow(items: $viewModel.data.tags, placeholder: "Add a tag")
                Button("Save") { dismiss() }.buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }
    }
}
