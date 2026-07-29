import LivtetKitFFI
import SwiftUI

struct DuplicateWorkDialog: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @State private var showEditionPicker = false

    var body: some View {
        Group {
            if showEditionPicker, let summary = viewModel.duplicateSummary {
                EditionPickerSheet(
                    editions: summary.editions,
                    onPick: { editionId in
                        showEditionPicker = false
                        viewModel.handleDuplicateAction(.linkIsbn(editionId: editionId))
                    },
                    onCancel: { showEditionPicker = false }
                )
            } else if let summary = viewModel.duplicateSummary {
                mainDialog(summary: summary)
            }
        }
    }

    private func mainDialog(summary: ExistingWorkSummary) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 36)).foregroundStyle(Color("semanticDangerForeground"))
                .accessibilityHidden(true)
            Text("Duplicate Found").font(.livtetHeading(size: 18, weight: .semibold))
                .accessibilityLabel("Duplicate Found")
            Text("\(summary.title) already exists (\(summary.editionCount) editions).")
                .font(.livtetBody(size: 14)).multilineTextAlignment(.center)
                .accessibilityLabel("\(summary.title) already exists with \(summary.editionCount) editions")
            VStack(spacing: 8) {
                Button("Replace existing work") {
                    viewModel.handleDuplicateAction(.replace(workId: summary.id))
                }.buttonStyle(.borderedProminent).tint(.brand).frame(maxWidth: .infinity)
                    .accessibilityLabel("Replace existing work")
                    .accessibilityHint("Replace the existing work with the new data")
                Button("Add as new edition") {
                    viewModel.handleDuplicateAction(.newEdition(workId: summary.id))
                }.buttonStyle(.bordered).frame(maxWidth: .infinity)
                    .accessibilityLabel("Add as new edition")
                    .accessibilityHint("Add this as a new edition of the existing work")
                Button("Link ISBN to existing edition") { showEditionPicker = true }
                    .buttonStyle(.bordered).frame(maxWidth: .infinity)
                    .accessibilityLabel("Link ISBN to existing edition")
                    .accessibilityHint("Link the ISBN to an existing edition in the work")
                Button("Cancel") { viewModel.handleDuplicateAction(.cancel) }
                    .buttonStyle(.plain).foregroundStyle(Color("textQuiet")).frame(maxWidth: .infinity)
                    .accessibilityLabel("Cancel")
                    .accessibilityHint("Cancel the duplicate handling process")
            }
        }
        .padding(24)
        .background(Color("surfaceDefault"))
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l))
        .padding(.horizontal, 24)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Duplicate work found")
        .accessibilityHint("Choose how to handle the duplicate work: replace, add as new edition, link ISBN, or cancel")
    }
}
