import SwiftUI

struct OverdriveLibraryPicker: View {
    @Binding var selectedCode: String
    @State private var searchText = ""
    @Environment(\.dismiss) private var dismiss

    private let libraries: [OverdriveLibrary] = OverdriveLibraryLoader.load()

    var body: some View {
        NavigationStack {
            Group {
                if libraries.isEmpty {
                    Text("No libraries loaded")
                        .foregroundStyle(Color("textQuiet"))
                } else {
                    List(libraries) { library in
                        Button {
                            selectedCode = library.code
                            dismiss()
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(library.name)
                                        .font(.system(size: 14, weight: .medium))
                                    Text(library.code)
                                        .font(.system(size: 12))
                                        .foregroundStyle(Color("textQuiet"))
                                }
                                Spacer()
                                if library.code == selectedCode {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(Color.brand)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                    .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
                }
            }
            .navigationTitle("Overdrive Library")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
