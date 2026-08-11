import SwiftUI

/// Step 4 of the guided Add Book wizard. The final step in the linear
/// 5-step flow. The user adds optional tags and taps "Save book" —
/// or "More options" to revisit description, ISBN, language, format,
/// publisher, and published date through the existing Hub screens.
///
/// In Phase 1 the save path is intentionally disabled: the Core FFI
/// surface for `createBookComplete` / `findOrCreateTag` / `linkWorkTag`
/// is not yet exposed in the core/ submodule. The step displays a
/// banner explaining the delay and offers the user the choice to
/// discard the wizard. The "More options" link to the Hub remains
/// functional so users can still review the data they've entered.
struct Step4TagsView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { viewModel.goToBack() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Subjects")
                    }
                    .foregroundStyle(Color("textNormal"))
                }
                Spacer()
                Text("Tags").font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
                Color.clear.frame(width: 80, height: 1)
            }.padding(.horizontal, 16).padding(.vertical, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Tags are free-form labels you can search and filter on. Skip this step if you don't want to tag this book.")
                        .font(.livtetBody(size: 13)).foregroundStyle(Color("textQuiet"))
                    ChipInputRow(items: $viewModel.data.tags, placeholder: "Add a tag")

                    if !viewModel.isSaveAvailable {
                        SaveUnavailableBanner()
                    }

                    Button { viewModel.goToHub() } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "ellipsis.circle")
                            Text("More options")
                        }
                        .font(.livtetBody(size: 14, weight: .semibold))
                        .foregroundStyle(Color.brand)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceDefault")))
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 16)
            }

            Divider()
            HStack(spacing: 12) {
                if viewModel.isSaveAvailable {
                    Button { viewModel.save() } label: {
                        Text("Save book")
                            .font(.livtetBody(size: 16, weight: .semibold))
                    }
                    .tint(.brand).frame(maxWidth: .infinity).padding(.vertical, 12)
                    .disabled(!viewModel.canCreateBook || viewModel.isSaving)
                } else {
                    Button { dismiss() } label: {
                        Text("Discard")
                            .font(.livtetBody(size: 16, weight: .semibold))
                    }
                    .tint(Color("textQuiet")).frame(maxWidth: .infinity).padding(.vertical, 12)
                }
            }
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }
}

private struct SaveUnavailableBanner: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "clock.badge.exclamationmark")
                    .foregroundStyle(Color("dangerOnNormal"))
                Text("Saving is coming soon")
                    .font(.livtetBody(size: 14, weight: .semibold))
                    .foregroundStyle(Color("textNormal"))
            }
            Text("The Rust core does not yet expose the save path the wizard needs to write a book. The data you entered is held in this wizard only — discard it or close this sheet to leave the library unchanged.")
                .font(.livtetBody(size: 12))
                .foregroundStyle(Color("textQuiet"))
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("dangerFillNormal")))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Saving is coming soon. The Rust core does not yet expose the save path.")
    }
}
