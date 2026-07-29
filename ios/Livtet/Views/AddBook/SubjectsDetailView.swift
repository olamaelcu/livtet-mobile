import SwiftUI
struct SubjectsDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        DetailPageScaffold(title: "Subjects",
            onSkip: { viewModel.clearItem(.subjects); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                ChipInputRow(items: $viewModel.data.subjects, placeholder: "Add a subject")
                Button("Save") { dismiss() }.buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }
    }
}
