import LivtetKitFFI
import SwiftUI
struct FormatDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        DetailPageScaffold(title: "Format",
            onSkip: { viewModel.clearItem(.format); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                Picker("Format", selection: Binding(
                    get: { viewModel.data.formatId },
                    set: { viewModel.data.formatId = $0 }
                )) {
                    Text("None").tag(nil as DbId?)
                    ForEach(viewModel.formats, id: \.id) { format in
                        Text(format.name).tag(Optional(format.id))
                    }
                }.pickerStyle(.menu)
                Button("Save") { dismiss() }.buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }.onAppear { viewModel.loadFormats() }
    }
}
