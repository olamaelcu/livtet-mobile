import LivtetKitFFI
import SwiftUI
struct LanguageDetailView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        DetailPageScaffold(title: "Language",
            onSkip: { viewModel.clearItem(.language); dismiss() }) {
            VStack(alignment: .leading, spacing: 8) {
                Picker("Language", selection: Binding(
                    get: { viewModel.data.languageId },
                    set: { viewModel.data.languageId = $0 }
                )) {
                    Text("None").tag(nil as DbId?)
                    ForEach(viewModel.languages, id: \.id) { lang in
                        Text(lang.flagEmoji.map { "\($0) " } ?? "" + lang.name).tag(Optional(lang.id))
                    }
                }.pickerStyle(.menu)
                Button("Save") { dismiss() }.buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
            }
        }.onAppear { viewModel.loadLanguages() }
    }
}
