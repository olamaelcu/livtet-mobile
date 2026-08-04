import FastULID
import LivtetKitFFI
import SwiftUI

/// Drill-down view shown when the user taps a duplicate row in
/// [DuplicateManagementView].
///
/// Three flavors — picked by which initializer was used:
/// - **Work duplicate** — shows both titles, the match signal, and
///   the shared identifiers. The "Merge" button presents
///   [DuplicateMergeConflictView] as a sheet so the user can pick a
///   per-field conflict resolution.
/// - **Edition duplicate (in work)** — same shape but the heading
///   is "Editions in same work" and the work's ID is shown so the
///   user can navigate to the work in the Library tab.
/// - **Cross-work edition duplicate** — heading "Cross-work edition
///   duplicate", the shared ISBNs are highlighted, and the "Move
///   edition" button presents a confirmation sheet (the simpler
///   cross-work case, where the edition record moves verbatim with
///   no per-field conflict resolution).
struct DuplicateDetailView: View {
    @Environment(\.dismiss) private var dismiss

    // Work-merge flavor
    let workCandidate: DuplicateCandidateMobile?
    let onWorkMerge: ((WorkMergeConflictResolutionMobile) -> Void)?

    // Edition-merge flavor
    let editionCandidate: EditionDuplicateCandidateMobile?
    let onEditionMerge: ((EditionMergeConflictResolutionMobile) -> Void)?

    // Cross-work move flavor
    let crossWorkCandidate: CrossWorkEditionDuplicateMobile?
    let onCrossWorkMove: (() -> Void)?

    @State private var showWorkMergeSheet = false
    @State private var showEditionMergeSheet = false
    @State private var showCrossWorkMoveSheet = false

    // MARK: - Initializers

    init(
        workCandidate: DuplicateCandidateMobile,
        onMerge: @escaping (WorkMergeConflictResolutionMobile) -> Void
    ) {
        self.workCandidate = workCandidate
        self.onWorkMerge = onMerge
        self.editionCandidate = nil
        self.onEditionMerge = nil
        self.crossWorkCandidate = nil
        self.onCrossWorkMove = nil
    }

    init(
        editionCandidate: EditionDuplicateCandidateMobile,
        onMerge: @escaping (EditionMergeConflictResolutionMobile) -> Void
    ) {
        self.workCandidate = nil
        self.onWorkMerge = nil
        self.editionCandidate = editionCandidate
        self.onEditionMerge = onMerge
        self.crossWorkCandidate = nil
        self.onCrossWorkMove = nil
    }

    init(
        crossWorkCandidate: CrossWorkEditionDuplicateMobile,
        onMove: @escaping () -> Void
    ) {
        self.workCandidate = nil
        self.onWorkMerge = nil
        self.editionCandidate = nil
        self.onEditionMerge = nil
        self.crossWorkCandidate = crossWorkCandidate
        self.onCrossWorkMove = onMove
    }

    // MARK: - Body

    var body: some View {
        Group {
            if let candidate = workCandidate {
                workBody(candidate: candidate)
            } else if let candidate = editionCandidate {
                editionBody(candidate: candidate)
            } else if let candidate = crossWorkCandidate {
                crossWorkBody(candidate: candidate)
            } else {
                Text("No candidate selected.")
            }
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showWorkMergeSheet) {
            if let candidate = workCandidate {
                DuplicateMergeConflictView(
                    workCandidate: candidate,
                    onMerge: { resolution in
                        onWorkMerge?(resolution)
                        showWorkMergeSheet = false
                        dismiss()
                    },
                    onCancel: { showWorkMergeSheet = false }
                )
            }
        }
        .sheet(isPresented: $showEditionMergeSheet) {
            if let candidate = editionCandidate {
                DuplicateMergeConflictView(
                    editionCandidate: candidate,
                    onMerge: { resolution in
                        onEditionMerge?(resolution)
                        showEditionMergeSheet = false
                        dismiss()
                    },
                    onCancel: { showEditionMergeSheet = false }
                )
            }
        }
        .sheet(isPresented: $showCrossWorkMoveSheet) {
            if let candidate = crossWorkCandidate {
                DuplicateMergeConflictView(
                    crossWorkCandidate: candidate,
                    onMove: {
                        onCrossWorkMove?()
                        showCrossWorkMoveSheet = false
                        dismiss()
                    },
                    onCancel: { showCrossWorkMoveSheet = false }
                )
            }
        }
    }

    private var navigationTitle: String {
        if workCandidate != nil { return "Work duplicate" }
        if editionCandidate != nil { return "Edition duplicate" }
        return "Cross-work duplicate"
    }

    // MARK: - Work body

    @ViewBuilder
    private func workBody(candidate: DuplicateCandidateMobile) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    sideBySide(
                        leftTitle: "Primary work",
                        leftValue: candidate.primaryTitle,
                        rightTitle: "Duplicate work",
                        rightValue: candidate.duplicateTitle
                    )

                    matchSummary(
                        kindLabel: matchKindLabel(candidate.matchKind),
                        confidence: candidate.confidence
                    )

                    if !candidate.matchingIdentifiers.isEmpty {
                        sectionCard(
                            title: "Shared identifiers",
                            body: candidate.matchingIdentifiers.joined(separator: ", ")
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }

            Divider()

            HStack(spacing: 12) {
                Button("Cancel") { dismiss() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Cancel")
                    .accessibilityHint("Close this view without merging")

                Button("Merge") { showWorkMergeSheet = true }
                    .buttonStyle(.borderedProminent)
                    .tint(.brand)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Merge")
                    .accessibilityHint("Open the merge conflict resolution sheet to choose how to combine these duplicates")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Work duplicate: \(candidate.primaryTitle) and \(candidate.duplicateTitle)")
        .accessibilityHint("Review the duplicates and choose how to merge them")
    }

    // MARK: - Edition body

    @ViewBuilder
    private func editionBody(candidate: EditionDuplicateCandidateMobile) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Editions in same work")
                        .font(.livtetHeading(size: 16, weight: .semibold))
                        .foregroundStyle(Color("textNormal"))

                    matchSummary(
                        kindLabel: matchKindLabel(candidate.matchKind),
                        confidence: candidate.confidence
                    )

                    if !candidate.matchingIsbns.isEmpty {
                        sectionCard(
                            title: "Shared ISBNs",
                            body: candidate.matchingIsbns.joined(separator: ", ")
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }

            Divider()

            HStack(spacing: 12) {
                Button("Cancel") { dismiss() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Cancel")
                    .accessibilityHint("Close this view without merging")

                Button("Merge") { showEditionMergeSheet = true }
                    .buttonStyle(.borderedProminent)
                    .tint(.brand)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Merge")
                    .accessibilityHint("Open the merge conflict resolution sheet to choose how to combine these editions")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Edition duplicate in same work")
        .accessibilityHint("Review the editions and choose how to merge them")
    }

    // MARK: - Cross-work body

    @ViewBuilder
    private func crossWorkBody(candidate: CrossWorkEditionDuplicateMobile) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Cross-work edition duplicate")
                        .font(.livtetHeading(size: 16, weight: .semibold))
                        .foregroundStyle(Color("textNormal"))

                    matchSummary(
                        kindLabel: matchKindLabel(candidate.matchKind),
                        confidence: candidate.confidence
                    )

                    if !candidate.matchingIsbns.isEmpty {
                        sectionCard(
                            title: "Shared ISBNs",
                            body: candidate.matchingIsbns.joined(separator: ", ")
                        )
                    }

                    sectionCard(
                        title: nil,
                        body: "This will move the duplicate edition under the primary work, combining both records into one."
                    )
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }

            Divider()

            HStack(spacing: 12) {
                Button("Cancel") { dismiss() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Cancel")
                    .accessibilityHint("Close this view without moving the edition")

                Button("Move edition") { showCrossWorkMoveSheet = true }
                    .buttonStyle(.borderedProminent)
                    .tint(.brand)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Move edition")
                    .accessibilityHint("Move this edition under the primary work, combining both records into one")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Cross-work edition duplicate")
        .accessibilityHint("Review the duplicate and choose to move the edition to the primary work")
    }

    // MARK: - Building blocks

    @ViewBuilder
    private func sideBySide(
        leftTitle: String,
        leftValue: String,
        rightTitle: String,
        rightValue: String
    ) -> some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(leftTitle)
                    .font(.livtetBody(size: 11, weight: .semibold))
                    .foregroundStyle(Color("textQuiet"))
                Text(leftValue)
                    .font(.livtetHeading(size: 14, weight: .semibold))
                    .foregroundStyle(Color("textNormal"))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                    .fill(Color("surfaceDefault"))
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(rightTitle)
                    .font(.livtetBody(size: 11, weight: .semibold))
                    .foregroundStyle(Color("textQuiet"))
                Text(rightValue)
                    .font(.livtetHeading(size: 14, weight: .semibold))
                    .foregroundStyle(Color("textNormal"))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                    .fill(Color("surfaceDefault"))
            )
        }
    }

    @ViewBuilder
    private func matchSummary(kindLabel: String, confidence: Float) -> some View {
        Text("\(kindLabel) \u{00B7} \(Int(confidence * 100))% confidence")
            .font(.livtetBody(size: 12, weight: .medium))
            .foregroundStyle(Color("textQuiet"))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                    .fill(Color("surfaceDefault"))
            )
    }

    @ViewBuilder
    private func sectionCard(title: String?, body: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if let title {
                Text(title)
                    .font(.livtetBody(size: 11, weight: .semibold))
                    .foregroundStyle(Color("textQuiet"))
            }
            Text(body)
                .font(.livtetBody(size: 13))
                .foregroundStyle(Color("textNormal"))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceDefault"))
        )
    }
}

#if DEBUG
#Preview("Work duplicate") {
    NavigationStack {
        DuplicateDetailView(
            workCandidate: DuplicateCandidateMobile(
                primaryWorkId: ULID(ulidData: Data(repeating: 0, count: 16))!,
                duplicateWorkId: ULID(ulidData: Data(repeating: 1, count: 16))!,
                matchKind: .exactIsbn,
                confidence: 0.95,
                matchingIdentifiers: ["urn:isbn:978-0-06-112008-4"],
                primaryTitle: "Beloved",
                duplicateTitle: "Beloved (Toni Morrison)"
            ),
            onMerge: { _ in }
        )
    }
}
#endif
