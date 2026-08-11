import SwiftUI

/// Step 3b of the guided Add Book wizard. Optional chip input for
/// subjects (topical descriptors, as opposed to the more taxonomic
/// genres collected in step 3a).
struct Step3bSubjectsView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { viewModel.goToBack() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Genres")
                    }
                    .foregroundStyle(Color("textNormal"))
                }
                Spacer()
                Text("Subjects").font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
                Color.clear.frame(width: 80, height: 1)
            }.padding(.horizontal, 16).padding(.vertical, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Add subjects that describe what the book is about. Skip this step if you don't want to tag subjects.")
                        .font(.livtetBody(size: 13)).foregroundStyle(Color("textQuiet"))
                    ChipInputRow(items: $viewModel.data.subjects, placeholder: "Add a subject")
                }
                .padding(.horizontal, 16).padding(.vertical, 16)
            }

            Divider()
            Button { viewModel.goToNext() } label: {
                Text(viewModel.data.subjects.isEmpty ? "Skip" : "Continue: Tags")
                    .font(.livtetBody(size: 16, weight: .semibold))
            }
            .tint(.brand).frame(maxWidth: .infinity).padding(.vertical, 12)
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }
}
