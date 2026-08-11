import SwiftUI

/// Step 3a of the guided Add Book wizard. Optional chip input for
/// genres. Skipping the step is allowed — the "Skip" button at the
/// bottom just advances.
struct Step3aGenresView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { viewModel.goToBack() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Contributors")
                    }
                    .foregroundStyle(Color("textNormal"))
                }
                Spacer()
                Text("Genres").font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
                Color.clear.frame(width: 80, height: 1)
            }.padding(.horizontal, 16).padding(.vertical, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Add the genres that fit this book. Skip this step if you don't want to tag genres.")
                        .font(.livtetBody(size: 13)).foregroundStyle(Color("textQuiet"))
                    ChipInputRow(items: $viewModel.data.genres, placeholder: "Add a genre")
                }
                .padding(.horizontal, 16).padding(.vertical, 16)
            }

            Divider()
            Button { viewModel.goToNext() } label: {
                Text(viewModel.data.genres.isEmpty ? "Skip" : "Continue: Subjects")
                    .font(.livtetBody(size: 16, weight: .semibold))
            }
            .tint(.brand).frame(maxWidth: .infinity).padding(.vertical, 12)
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }
}
