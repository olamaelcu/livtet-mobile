import SwiftUI

struct StepTitleAndAuthorsView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var newAuthorName: String = ""
    @State private var newAuthorRole: String = "author"
    private let roles = ["author", "illustrator", "translator", "narrator"]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button {
                    dismiss()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Library")
                    }
                    .foregroundStyle(Color("textNormal"))
                }
                Spacer()
                Text("Title & Authors").font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
                Color.clear.frame(width: 80, height: 1)
            }.padding(.horizontal, 16).padding(.vertical, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Title *").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
                        TextField("Book title", text: $viewModel.data.title).textFieldStyle(.roundedBorder)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Authors *").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
                        if viewModel.data.authors.isEmpty {
                            Text("At least one author is required").font(.livtetBody(size: 13)).foregroundStyle(Color("textQuiet").opacity(0.6))
                        } else {
                            ForEach(viewModel.data.authors) { author in
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(author.name).font(.livtetBody(size: 14))
                                        Text(author.role).font(.livtetBody(size: 11)).foregroundStyle(Color("textQuiet"))
                                    }
                                    Spacer()
                                    Button { viewModel.removeAuthor(author) } label: {
                                        Image(systemName: "xmark.circle.fill").foregroundStyle(Color("textQuiet"))
                                    }.buttonStyle(.plain)
                                }
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: LivtetRadius.s).fill(Color("surfaceHighlighted")))
                            }
                        }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Add author").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
                        HStack {
                            TextField("Name", text: $newAuthorName).textFieldStyle(.roundedBorder)
                            Picker("Role", selection: $newAuthorRole) {
                                ForEach(roles, id: \.self) { role in
                                    Text(role.capitalized).tag(role)
                                }
                            }.pickerStyle(.menu)
                            Button {
                                viewModel.addAuthor(name: newAuthorName, role: newAuthorRole)
                                newAuthorName = ""; newAuthorRole = "author"
                            } label: {
                                Image(systemName: "plus.circle.fill").font(.title2).foregroundStyle(Color.brand)
                            }.buttonStyle(.plain)
                            .disabled(newAuthorName.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 16)
            }

            Divider()
            Button("Continue") { viewModel.goToHub() }
                .font(.livtetBody(size: 16, weight: .semibold))
                .tint(.brand).frame(maxWidth: .infinity).padding(.vertical, 12)
                .disabled(!viewModel.canContinueFromTitleAndAuthors)
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }
}
