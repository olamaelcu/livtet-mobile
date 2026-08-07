import SwiftUI

struct NewPuzzleSheet: View {
    @ObservedObject var viewModel: PuzzleTabViewModel
    @Binding var isPresented: Bool

    var body: some View {
        NavigationStack {
            List {
                Section("Difficulty") {
                    Picker("Difficulty", selection: $viewModel.difficulty) {
                        ForEach(PuzzleDifficulty.allCases, id: \.self) { diff in
                            Text(diff.rawValue).tag(diff)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Book") {
                    if viewModel.eligibleBooks.isEmpty {
                        Text("No books with covers.\nAdd a book with a cover in the Library tab.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.eligibleBooks, id: \.id) { book in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(book.title)
                                        .foregroundStyle(Color("textNormal"))
                                    if let desc = book.description, !desc.isEmpty {
                                        Text(desc)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                }
                                Spacer()
                                if book.id == viewModel.selectedBook?.id {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.brand)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture { viewModel.selectedBook = book }
                        }
                    }
                }
            }
            .navigationTitle("New Puzzle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { isPresented = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Start") {
                        viewModel.startPuzzle()
                        isPresented = false
                    }
                    .disabled(viewModel.selectedBook == nil)
                }
            }
        }
    }
}
