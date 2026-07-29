import FastULID
import LivtetKitFFI
import SwiftUI

/// Sheet that lets the user pick a per-field conflict resolution
/// before confirming a merge.
///
/// Three flavors, picked by which initializer was used:
/// - **Work merge** — picks per-field resolution for description,
///   tags, genres, subjects, publishers, series type, language, and
///   sort title.
/// - **Edition merge** — picks per-field resolution for title,
///   published date, format, language, notes, and description.
/// - **Cross-work edition move** — no per-field conflict (the
///   edition record moves verbatim), so the sheet is a single
///   confirmation panel.
///
/// The "Apply to all" shortcuts at the top of the work / edition
/// sheets set every field to `.keepPrimary` or `.keepBoth` in one
/// tap, mirroring the Android
/// `DuplicateConflictDialog.kt` "Keep primary (all)" / "Keep both
/// (all)" buttons.
struct DuplicateMergeConflictView: View {
    @Environment(\.dismiss) private var dismiss

    // Work-merge state
    let workCandidate: DuplicateCandidateMobile?
    let onWorkMerge: ((WorkMergeConflictResolutionMobile) -> Void)?

    // Edition-merge state
    let editionCandidate: EditionDuplicateCandidateMobile?
    let onEditionMerge: ((EditionMergeConflictResolutionMobile) -> Void)?

    // Cross-work move state
    let crossWorkCandidate: CrossWorkEditionDuplicateMobile?
    let onCrossWorkMove: (() -> Void)?

    /// Common cancel callback for all three flavors.
    let onCancel: () -> Void

    // Per-field work state.
    @State private var workDescription: WorkFieldResolutionMobile?
    @State private var workTags: WorkFieldResolutionMobile?
    @State private var workGenres: WorkFieldResolutionMobile?
    @State private var workSubjects: WorkFieldResolutionMobile?
    @State private var workPublishers: WorkFieldResolutionMobile?
    @State private var workSeriesType: WorkFieldResolutionMobile?
    @State private var workLanguage: WorkFieldResolutionMobile?
    @State private var workSortTitle: WorkFieldResolutionMobile?

    // Per-field edition state.
    @State private var editionTitle: WorkFieldResolutionMobile?
    @State private var editionPublishedDate: WorkFieldResolutionMobile?
    @State private var editionFormat: WorkFieldResolutionMobile?
    @State private var editionLanguage: WorkFieldResolutionMobile?
    @State private var editionNotes: WorkFieldResolutionMobile?
    @State private var editionDescription: WorkFieldResolutionMobile?

    // MARK: - Work-merge init

    init(
        workCandidate: DuplicateCandidateMobile,
        onMerge: @escaping (WorkMergeConflictResolutionMobile) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.workCandidate = workCandidate
        self.onWorkMerge = onMerge
        self.editionCandidate = nil
        self.onEditionMerge = nil
        self.crossWorkCandidate = nil
        self.onCrossWorkMove = nil
        self.onCancel = onCancel
    }

    // MARK: - Edition-merge init

    init(
        editionCandidate: EditionDuplicateCandidateMobile,
        onMerge: @escaping (EditionMergeConflictResolutionMobile) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.workCandidate = nil
        self.onWorkMerge = nil
        self.editionCandidate = editionCandidate
        self.onEditionMerge = onMerge
        self.crossWorkCandidate = nil
        self.onCrossWorkMove = nil
        self.onCancel = onCancel
    }

    // MARK: - Cross-work move init

    init(
        crossWorkCandidate: CrossWorkEditionDuplicateMobile,
        onMove: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.workCandidate = nil
        self.onWorkMerge = nil
        self.editionCandidate = nil
        self.onEditionMerge = nil
        self.crossWorkCandidate = crossWorkCandidate
        self.onCrossWorkMove = onMove
        self.onCancel = onCancel
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            Group {
                if let candidate = workCandidate {
                    workMergeBody(candidate: candidate)
                } else if let candidate = editionCandidate {
                    editionMergeBody(candidate: candidate)
                } else if let candidate = crossWorkCandidate {
                    crossWorkMoveBody(candidate: candidate)
                } else {
                    Text("No candidate selected.")
                }
            }
            .background(Color("surfaceDefault").ignoresSafeArea())
            .navigationTitle(navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private var navigationTitle: String {
        if workCandidate != nil { return "Merge works" }
        if editionCandidate != nil { return "Merge editions" }
        return "Move edition"
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button("Cancel", action: handleCancel)
        }
    }

    private func handleCancel() {
        onCancel()
        dismiss()
    }

    // MARK: - Work merge

    @ViewBuilder
    private func workMergeBody(candidate: DuplicateCandidateMobile) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    summarySection(
                        primaryLabel: "Primary: \(candidate.primaryTitle)",
                        duplicateLabel: "Duplicate: \(candidate.duplicateTitle)",
                        matchLabel: "\(matchKindLabel(candidate.matchKind)) \u{00B7} \(Int(candidate.confidence * 100))% confidence"
                    )

                    if !candidate.matchingIdentifiers.isEmpty {
                        Text("Shared identifiers: \(candidate.matchingIdentifiers.joined(separator: ", "))")
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                    }

                    HStack(spacing: 8) {
                        Button {
                            applyToAllWork(.keepPrimary)
                        } label: {
                            Text("Keep primary (all)")
                                .font(.livtetBody(size: 12, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Keep primary (all)")
                        .accessibilityHint("Keep the primary version of all fields")

                        Button {
                            applyToAllWork(.keepBoth)
                        } label: {
                            Text("Keep both (all)")
                                .font(.livtetBody(size: 12, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Keep both (all)")
                        .accessibilityHint("Keep both versions of all fields")
                    }
                    .padding(.top, 4)

                    Group {
                        WorkFieldRow(label: "Description", selection: $workDescription)
                        WorkFieldRow(label: "Tags", selection: $workTags)
                        WorkFieldRow(label: "Genres", selection: $workGenres)
                        WorkFieldRow(label: "Subjects", selection: $workSubjects)
                        WorkFieldRow(label: "Publishers", selection: $workPublishers)
                        WorkFieldRow(label: "Series type", selection: $workSeriesType)
                        WorkFieldRow(label: "Language", selection: $workLanguage)
                        WorkFieldRow(label: "Sort title", selection: $workSortTitle)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }

            Divider()

            Button(action: confirmWorkMerge) {
                Text("Merge")
                    .font(.livtetBody(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(.brand)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .accessibilityLabel("Merge")
            .accessibilityHint("Confirm the merge with the selected field resolutions")
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Merge works: \(candidate.primaryTitle) and \(candidate.duplicateTitle)")
        .accessibilityHint("Review and resolve conflicts between the two duplicate works")
    }

    private func applyToAllWork(_ resolution: WorkFieldResolutionMobile) {
        workDescription = resolution
        workTags = resolution
        workGenres = resolution
        workSubjects = resolution
        workPublishers = resolution
        workSeriesType = resolution
        workLanguage = resolution
        workSortTitle = resolution
    }

    private func confirmWorkMerge() {
        let resolution = WorkMergeConflictResolutionMobile(
            description: workDescription,
            tags: workTags,
            genres: workGenres,
            subjects: workSubjects,
            publishers: workPublishers,
            seriesType: workSeriesType,
            language: workLanguage,
            sortTitle: workSortTitle
        )
        onWorkMerge?(resolution)
        dismiss()
    }

    // MARK: - Edition merge

    @ViewBuilder
    private func editionMergeBody(candidate: EditionDuplicateCandidateMobile) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    summarySection(
                        primaryLabel: nil,
                        duplicateLabel: nil,
                        matchLabel: "\(matchKindLabel(candidate.matchKind)) \u{00B7} \(Int(candidate.confidence * 100))% confidence"
                    )

                    if !candidate.matchingIsbns.isEmpty {
                        Text("Shared ISBNs: \(candidate.matchingIsbns.joined(separator: ", "))")
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                    }

                    HStack(spacing: 8) {
                        Button {
                            applyToAllEdition(.keepPrimary)
                        } label: {
                            Text("Keep primary (all)")
                                .font(.livtetBody(size: 12, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Keep primary (all)")
                        .accessibilityHint("Keep the primary version of all fields")

                        Button {
                            applyToAllEdition(.keepBoth)
                        } label: {
                            Text("Keep both (all)")
                                .font(.livtetBody(size: 12, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Keep both (all)")
                        .accessibilityHint("Keep both versions of all fields")
                    }
                    .padding(.top, 4)

                    Group {
                        WorkFieldRow(label: "Title", selection: $editionTitle)
                        WorkFieldRow(label: "Published date", selection: $editionPublishedDate)
                        WorkFieldRow(label: "Format", selection: $editionFormat)
                        WorkFieldRow(label: "Language", selection: $editionLanguage)
                        WorkFieldRow(label: "Notes", selection: $editionNotes)
                        WorkFieldRow(label: "Description", selection: $editionDescription)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }

            Divider()

            Button(action: confirmEditionMerge) {
                Text("Merge")
                    .font(.livtetBody(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(.brand)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .accessibilityLabel("Merge")
            .accessibilityHint("Confirm the merge with the selected field resolutions")
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Merge editions")
        .accessibilityHint("Review and resolve conflicts between the two duplicate editions")
    }

    private func applyToAllEdition(_ resolution: WorkFieldResolutionMobile) {
        editionTitle = resolution
        editionPublishedDate = resolution
        editionFormat = resolution
        editionLanguage = resolution
        editionNotes = resolution
        editionDescription = resolution
    }

    private func confirmEditionMerge() {
        let resolution = EditionMergeConflictResolutionMobile(
            title: editionTitle,
            publishedDate: editionPublishedDate,
            format: editionFormat,
            language: editionLanguage,
            notes: editionNotes,
            description: editionDescription,
            pageCount: nil
        )
        onEditionMerge?(resolution)
        dismiss()
    }

    // MARK: - Cross-work move

    @ViewBuilder
    private func crossWorkMoveBody(candidate: CrossWorkEditionDuplicateMobile) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    summarySection(
                        primaryLabel: nil,
                        duplicateLabel: nil,
                        matchLabel: "\(matchKindLabel(candidate.matchKind)) \u{00B7} \(Int(candidate.confidence * 100))% confidence"
                    )

                    if !candidate.matchingIsbns.isEmpty {
                        Text("Shared ISBNs: \(candidate.matchingIsbns.joined(separator: ", "))")
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("This will move the duplicate edition under the primary work, combining both records into one.")
                            .font(.livtetBody(size: 13))
                            .foregroundStyle(Color("textNormal"))
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                            .fill(Color("surfaceHighlighted"))
                    )
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }

            Divider()

            Button(action: confirmCrossWorkMove) {
                Text("Move edition")
                    .font(.livtetBody(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(.brand)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .accessibilityLabel("Move edition")
            .accessibilityHint("Move this edition under the primary work, combining both records into one")
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Cross-work edition move")
        .accessibilityHint("Confirm to move this edition under the primary work")
    }

    private func confirmCrossWorkMove() {
        onCrossWorkMove?()
        dismiss()
    }

    // MARK: - Summary section

    @ViewBuilder
    private func summarySection(
        primaryLabel: String?,
        duplicateLabel: String?,
        matchLabel: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if let primaryLabel {
                Text(primaryLabel)
                    .font(.livtetBody(size: 14, weight: .medium))
                    .foregroundStyle(Color("textNormal"))
            }
            if let duplicateLabel {
                Text(duplicateLabel)
                    .font(.livtetBody(size: 14, weight: .medium))
                    .foregroundStyle(Color("textNormal"))
            }
            Text(matchLabel)
                .font(.livtetBody(size: 11))
                .foregroundStyle(Color("textQuiet").opacity(0.7))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceHighlighted"))
        )
    }
}

// MARK: - Field row

/// A single field row in the conflict sheet. Renders a label above
/// a three-way `Picker` ("Primary" / "Duplicate" / "Both"). A `nil`
/// selection means "no choice yet" — the picker shows the system
/// default placeholder so the user is nudged to pick something.
struct WorkFieldRow: View {
    let label: String
    @Binding var selection: WorkFieldResolutionMobile?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.livtetBody(size: 12, weight: .semibold))
                .foregroundStyle(Color("textQuiet"))
            Picker(label, selection: Binding(
                get: { selection ?? .keepPrimary },
                set: { selection = $0 }
            )) {
                Text("Primary").tag(WorkFieldResolutionMobile.keepPrimary)
                Text("Duplicate").tag(WorkFieldResolutionMobile.keepDuplicate)
                Text("Both").tag(WorkFieldResolutionMobile.keepBoth)
            }
            .pickerStyle(.segmented)
            .accessibilityLabel("\(label) selection")
            .accessibilityHint("Choose how to resolve the \(label) conflict: keep primary, keep duplicate, or keep both")
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceHighlighted"))
        )
    }
}

#if DEBUG
#Preview("Work merge") {
    DuplicateMergeConflictView(
        workCandidate: DuplicateCandidateMobile(
            primaryWorkId: ULID(ulidData: Data(repeating: 0, count: 16))!,
            duplicateWorkId: ULID(ulidData: Data(repeating: 1, count: 16))!,
            matchKind: .exactIsbn,
            confidence: 0.95,
            matchingIdentifiers: ["urn:isbn:978-0-06-112008-4"],
            primaryTitle: "Beloved",
            duplicateTitle: "Beloved (Toni Morrison)"
        ),
        onMerge: { _ in },
        onCancel: {}
    )
}
#endif
