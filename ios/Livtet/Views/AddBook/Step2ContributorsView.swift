import SwiftUI

/// Step 2 of the guided Add Book wizard. The user adds one or more
/// contributors, each with a role (author, illustrator, translator,
/// narrator). At least one contributor with role "author" is required
/// to advance; the other roles are optional.
struct Step2ContributorsView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var newAuthorName: String = ""
    @State private var newAuthorRole: String = "author"
    private let roles = ["author", "illustrator", "translator", "narrator"]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button {
                    viewModel.goToBack()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Title & Cover")
                    }
                    .foregroundStyle(Color("textNormal"))
                }
                Spacer()
                Text("Contributors").font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
                Color.clear.frame(width: 80, height: 1)
            }.padding(.horizontal, 16).padding(.vertical, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Authors *").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
                        if viewModel.data.authors.isEmpty {
                            Text("At least one author is required")
                                .font(.livtetBody(size: 13))
                                .foregroundStyle(Color("textQuiet").opacity(0.6))
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
                                .background(RoundedRectangle(cornerRadius: LivtetRadius.s).fill(Color("surfaceDefault")))
                            }
                        }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Add contributor").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
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
            Button { viewModel.goToNext() } label: {
                Text("Continue: Genres")
                    .font(.livtetBody(size: 16, weight: .semibold))
            }
            .tint(.brand).frame(maxWidth: .infinity).padding(.vertical, 12)
            .disabled(!viewModel.canContinueFromContributors)
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }
}
