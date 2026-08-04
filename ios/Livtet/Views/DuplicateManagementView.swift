import FastULID
import LivtetKit
import LivtetKitFFI
import SwiftUI

/// Top-level entry point for the duplicate detection & merge feature.
///
/// Mirrors Android's `DuplicateManagementScreen.kt`:
/// - Three independent lists (works / editions in work / cross-work
///   editions), rendered one at a time behind a segmented picker.
/// - "Rescan" toolbar button re-runs the scan via the view-model.
/// - Each row shows both sides of the pair plus a confidence score
///   and the matching signal; tapping the row opens the
///   merge-conflict sheet.
/// - Loading and error states share the same surface as the rest of
///   the app (`ErrorBanner` + a centered `ProgressView` while the
///   initial scan is in flight).
struct DuplicateManagementView: View {
    @StateObject private var viewModel = DuplicateManagementViewModel()
    @State private var selectedTab: DuplicateTab = .works

    var body: some View {
        // The parent Library tab wraps us in a `NavigationStack`, so
        // we don't add our own. Wrapping twice produces a nested
        // nav stack where the parent's chrome (back button, title
        // bar) and the child's chrome both render.
        content
            .background(Color("surfaceDefault").ignoresSafeArea())
            .navigationTitle("Manage Duplicates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
            .task { await viewModel.load() }
    }

    @ViewBuilder
    private var content: some View {
        VStack(spacing: 0) {
            Picker("Section", selection: $selectedTab) {
                ForEach(DuplicateTab.allCases) { tab in
                    Text(tab.title).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .accessibilityLabel("Duplicate list scope")
            .accessibilityHint("Select the type of duplicates to view. Works shows duplicate works, Editions shows duplicate editions within works, and Cross-Work shows editions that need to be moved to a different work.")

            if viewModel.isLoading && viewModel.workCandidates.isEmpty
                && viewModel.editionInWorkCandidates.isEmpty
                && viewModel.crossWorkEditionCandidates.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error = viewModel.error {
                ErrorBanner(
                    message: error.localizedDescription,
                    onRetry: { viewModel.rescan() }
                )
                .padding(.horizontal, 16)
                .padding(.top, 8)
                Spacer()
            } else {
                listForSelectedTab
            }

            if let lastResult = viewModel.lastResultMessage, viewModel.error == nil {
                Text(lastResult)
                    .font(.livtetBody(size: 12, weight: .medium))
                    .foregroundStyle(Color("textQuiet"))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
            }
        }
    }

    @ViewBuilder
    private var listForSelectedTab: some View {
        switch selectedTab {
        case .works:
            WorksList(
                candidates: viewModel.workCandidates,
                detail: { candidate in
                    DuplicateDetailView(
                        workCandidate: candidate,
                        onMerge: { resolution in
                            Task {
                                await viewModel.mergeWorks(
                                    primaryWorkId: candidate.primaryWorkId,
                                    duplicateWorkId: candidate.duplicateWorkId,
                                    resolution: resolution
                                )
                            }
                        }
                    )
                }
            )
        case .editionsInWork:
            EditionsInWorkList(
                candidates: viewModel.editionInWorkCandidates,
                detail: { candidate in
                    DuplicateDetailView(
                        editionCandidate: candidate,
                        onMerge: { resolution in
                            Task {
                                await viewModel.mergeEditions(
                                    primaryEditionId: candidate.primaryEditionId,
                                    duplicateEditionId: candidate.duplicateEditionId,
                                    resolution: resolution
                                )
                            }
                        }
                    )
                }
            )
        case .crossWorkEditions:
            CrossWorkEditionsList(
                candidates: viewModel.crossWorkEditionCandidates,
                detail: { candidate in
                    DuplicateDetailView(
                        crossWorkCandidate: candidate,
                        onMove: {
                            Task {
                                await viewModel.moveEditionToWork(
                                    editionId: candidate.duplicateEditionId,
                                    targetWorkId: candidate.primaryWorkId
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarTrailing) {
            Button {
                viewModel.rescan()
            } label: {
                Label("Rescan", systemImage: "arrow.clockwise")
            }
            .accessibilityLabel("Rescan for duplicates")
            .disabled(viewModel.isLoading)
        }
    }
}

/// Tabs surfaced by the segmented picker. Order matches Android's
/// `DuplicateTab` enum so screenshots and documentation line up
/// across platforms.
enum DuplicateTab: String, CaseIterable, Identifiable, Hashable {
    case works
    case editionsInWork
    case crossWorkEditions

    var id: String { rawValue }

    var title: String {
        switch self {
        case .works: return "Works"
        case .editionsInWork: return "Editions"
        case .crossWorkEditions: return "Cross-Work"
        }
    }
}

// MARK: - List sub-views

private struct WorksList<Detail: View>: View {
    let candidates: [DuplicateCandidateMobile]
    let detail: (DuplicateCandidateMobile) -> Detail

    var body: some View {
        if candidates.isEmpty {
            EmptyStateView(message: "No duplicate works found.")
        } else {
            List {
                ForEach(candidates, id: \.candidateId) { candidate in
                    NavigationLink {
                        detail(candidate)
                    } label: {
                        WorkCandidateRow(candidate: candidate)
                    }
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
        }
    }
}

private struct EditionsInWorkList<Detail: View>: View {
    let candidates: [EditionDuplicateCandidateMobile]
    let detail: (EditionDuplicateCandidateMobile) -> Detail

    var body: some View {
        if candidates.isEmpty {
            EmptyStateView(message: "No duplicate editions found within a single work.")
        } else {
            List {
                ForEach(candidates, id: \.candidateId) { candidate in
                    NavigationLink {
                        detail(candidate)
                    } label: {
                        EditionInWorkRow(candidate: candidate)
                    }
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
        }
    }
}

private struct CrossWorkEditionsList<Detail: View>: View {
    let candidates: [CrossWorkEditionDuplicateMobile]
    let detail: (CrossWorkEditionDuplicateMobile) -> Detail

    var body: some View {
        if candidates.isEmpty {
            EmptyStateView(message: "No cross-work edition duplicates found.")
        } else {
            List {
                ForEach(candidates, id: \.candidateId) { candidate in
                    NavigationLink {
                        detail(candidate)
                    } label: {
                        CrossWorkEditionRow(candidate: candidate)
                    }
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
        }
    }
}

private struct WorkCandidateRow: View {
    let candidate: DuplicateCandidateMobile

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Primary: \(candidate.primaryTitle)")
                .font(.livtetHeading(size: 14, weight: .semibold))
                .foregroundStyle(Color("textNormal"))
                .lineLimit(1)
            Text("Duplicate: \(candidate.duplicateTitle)")
                .font(.livtetBody(size: 12))
                .foregroundStyle(Color("textQuiet"))
                .lineLimit(1)
            if !candidate.matchingIdentifiers.isEmpty {
                Text("Shared: \(candidate.matchingIdentifiers.joined(separator: ", "))")
                    .font(.livtetBody(size: 11))
                    .foregroundStyle(Color("textQuiet").opacity(0.7))
                    .lineLimit(2)
                    .padding(.top, 2)
            }
            Text("\(matchKindLabel(candidate.matchKind)) \u{00B7} \(Int(candidate.confidence * 100))% confidence")
                .font(.livtetBody(size: 11))
                .foregroundStyle(Color("textQuiet").opacity(0.5))
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceDefault"))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(candidate.primaryTitle) and \(candidate.duplicateTitle)")
        .accessibilityHint("Double tap to view merge options for these duplicates")
    }
}

private struct EditionInWorkRow: View {
    let candidate: EditionDuplicateCandidateMobile

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Editions in same work")
                .font(.livtetHeading(size: 14, weight: .semibold))
                .foregroundStyle(Color("textNormal"))
            if !candidate.matchingIsbns.isEmpty {
                Text("Shared ISBNs: \(candidate.matchingIsbns.joined(separator: ", "))")
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("textQuiet"))
                    .padding(.top, 2)
            }
            Text("\(matchKindLabel(candidate.matchKind)) \u{00B7} \(Int(candidate.confidence * 100))% confidence")
                .font(.livtetBody(size: 11))
                .foregroundStyle(Color("textQuiet").opacity(0.5))
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceDefault"))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Duplicate editions in the same work")
        .accessibilityHint("Double tap to view merge options for these editions")
    }
}

private struct CrossWorkEditionRow: View {
    let candidate: CrossWorkEditionDuplicateMobile

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Cross-work edition duplicate")
                .font(.livtetHeading(size: 14, weight: .semibold))
                .foregroundStyle(Color("textNormal"))
            if !candidate.matchingIsbns.isEmpty {
                Text("Shared ISBNs: \(candidate.matchingIsbns.joined(separator: ", "))")
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("textQuiet"))
                    .padding(.top, 2)
            }
            Text("\(matchKindLabel(candidate.matchKind)) \u{00B7} \(Int(candidate.confidence * 100))% confidence")
                .font(.livtetBody(size: 11))
                .foregroundStyle(Color("textQuiet").opacity(0.5))
                .padding(.top, 2)
            Text("Tap to move edition to primary work")
                .font(.livtetBody(size: 11, weight: .medium))
                .foregroundStyle(Color("brand"))
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceDefault"))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Cross-work edition duplicate")
        .accessibilityHint("Double tap to move this edition to the primary work")
    }
}

private struct EmptyStateView: View {
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "checkmark.circle")
                .font(.livtetHeading(size: 36))
                .foregroundStyle(Color("textQuiet").opacity(0.5))
            Text(message)
                .font(.livtetBody(size: 14))
                .foregroundStyle(Color("textQuiet"))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Candidate identity for SwiftUI

extension DuplicateCandidateMobile: Identifiable {
    public var id: String { "\(primaryWorkId)|\(duplicateWorkId)|\(confidence)" }

    /// Stable id used by `ForEach` to drive diffing. The view never
    /// relies on the candidate record's own identity because two
    /// candidates can have identical work ids but different match
    /// signals.
    fileprivate var candidateId: String { id }
}

extension EditionDuplicateCandidateMobile: Identifiable {
    public var id: String { "\(primaryEditionId)|\(duplicateEditionId)|\(confidence)" }

    fileprivate var candidateId: String { id }
}

extension CrossWorkEditionDuplicateMobile: Identifiable {
    public var id: String { "\(primaryEditionId)|\(duplicateEditionId)|\(confidence)" }

    fileprivate var candidateId: String { id }
}

// MARK: - Match-kind label

/// Human-readable label for a `DuplicateMatchKindMobile`. Mirrors
/// Android's `matchKindLabel` helper in
/// `DuplicateManagementScreen.kt`.
func matchKindLabel(_ kind: DuplicateMatchKindMobile) -> String {
    switch kind {
    case .exactIsbn:
        return "Exact ISBN"
    case .titleAndAuthor(let titleSimilarity):
        return "Title + author (\u{2265} \(Int(titleSimilarity * 100))%)"
    case .multiIdentifier(let minMatches):
        return "Multi-identifier (\u{2265} \(minMatches))"
    case .publisherTitleYear:
        return "Publisher + title + year"
    case .composite:
        return "Composite rule"
    }
}

#if DEBUG
#Preview {
    // Wrap in a NavigationStack because the production use site is
    // pushed from the Library tab's own stack.
    NavigationStack {
        DuplicateManagementView()
    }
}
#endif
