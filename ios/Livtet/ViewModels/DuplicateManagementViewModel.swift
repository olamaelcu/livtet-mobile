import Combine
import Foundation
import LivtetKit
import LivtetKitFFI

/// View-state for the Duplicate Management screen.
///
/// The screen shows three independent lists sourced from the FFI:
/// - **Work duplicates** — full pairs of work records that look like the
///   same book under different titles.
/// - **Edition duplicates in a work** — pairs of editions that share a
///   work but match on a duplicate signal (e.g. shared ISBN).
/// - **Cross-work edition duplicates** — editions that share a signal
///   (typically ISBN) but live under different works. The recovery
///   action is `moveEditionToWork`, not a full merge.
///
/// On `load()` we fire the work-level + cross-work scan in parallel;
/// the "editions in work" list is then derived by walking the work IDs
/// surfaced by the work-level scan (mirrors Android's
/// `aggregateEditionInWork` helper). On `merge*` / `move*` we re-run
/// the scan so the UI reflects the post-action state.
///
/// Mirrors `LibraryViewModel` in shape: the FFI bridge methods are
/// declared `throws` (not `async throws`), so the view-model awaits
/// sync FFI calls directly. The MainActor is briefly blocked for the
/// duration of the FFI call (sub-millisecond on the SQLite path),
/// which matches the rest of the iOS app's view-models.
@MainActor
final class DuplicateManagementViewModel: ObservableObject {
    // MARK: - Output

    @Published private(set) var workCandidates: [DuplicateCandidateMobile] = []
    @Published private(set) var editionInWorkCandidates: [EditionDuplicateCandidateMobile] = []
    @Published private(set) var crossWorkEditionCandidates: [CrossWorkEditionDuplicateMobile] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: AppError?
    @Published private(set) var lastResultMessage: String?

    // MARK: - Dependencies

    private let bridge: DuplicateBridge

    init(bridge: DuplicateBridge = LivtetDuplicateBridgeAdapter()) {
        self.bridge = bridge
    }

    // MARK: - Actions

    /// Initial scan: finds work duplicates, cross-work edition
    /// duplicates, and edition duplicates for every work the work
    /// scan surfaced. Any single failure aborts the whole scan and
    /// surfaces the [AppError] via the published `error` so the user
    /// sees a coherent "couldn't scan" state.
    func load() async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        lastResultMessage = nil
        defer { isLoading = false }

        do {
            // Fire the work-level + cross-work scan in parallel via
            // `async let`. The bridge methods are sync (`throws`),
            // but `async let` lets us model the two scans as
            // independent values that we then collect with a
            // tuple-await — same shape as `DashboardViewModel.load()`.
            async let workTask = bridge.findDuplicateWorks(
                matchKinds: Self.defaultMatchKinds,
                minConfidence: Self.defaultMinConfidence
            )
            async let crossWorkTask = bridge.findCrossWorkEditionDuplicates(
                matchKinds: Self.defaultMatchKinds,
                minConfidence: Self.defaultMinConfidence
            )
            let (loadedWorks, loadedCrossWork) = try await (workTask, crossWorkTask)

            workCandidates = loadedWorks
            crossWorkEditionCandidates = loadedCrossWork

            // Walk every work ID surfaced by the work-level scan and
            // collect per-work edition duplicates. The Android side
            // does the same thing (see
            // `aggregateEditionInWork` in
            // `DuplicateManagementScreen.kt`).
            let workIds = Array(
                Set(
                    loadedWorks.flatMap { [$0.primaryWorkId, $0.duplicateWorkId] }
                )
            )
            editionInWorkCandidates = await aggregateEditionInWork(workIds: workIds)
        } catch let appError as AppError {
            error = appError
        } catch {
            self.error = .unknown(error.localizedDescription)
        }
    }

    /// Re-runs the scan, e.g. from the "Rescan" toolbar button.
    func rescan() {
        Task { await load() }
    }

    /// Apply the user's choice for a work-merge candidate.
    /// On success, refreshes the lists and sets a human-readable
    /// `lastResultMessage` summarizing the move counts.
    func mergeWorks(
        primaryWorkId: DbId,
        duplicateWorkId: DbId,
        resolution: WorkMergeConflictResolutionMobile
    ) async {
        await runMergeAction(
            context: "mergeWorks",
            work: { [bridge] in
                try await bridge.mergeWorks(
                    primaryWorkId: primaryWorkId,
                    duplicateWorkId: duplicateWorkId,
                    conflictResolution: resolution
                )
            }
        )
    }

    /// Apply the user's choice for an in-work edition-merge candidate.
    func mergeEditions(
        primaryEditionId: DbId,
        duplicateEditionId: DbId,
        resolution: EditionMergeConflictResolutionMobile
    ) async {
        await runMergeAction(
            context: "mergeEditions",
            work: { [bridge] in
                try await bridge.mergeEditions(
                    primaryEditionId: primaryEditionId,
                    duplicateEditionId: duplicateEditionId,
                    conflictResolution: resolution
                )
            }
        )
    }

    /// Apply the recovery action for a cross-work edition duplicate:
    /// re-assign the duplicate edition's `work_id` to the primary
    /// work. No conflict resolution is needed (the edition record
    /// moves verbatim).
    func moveEditionToWork(
        editionId: DbId,
        targetWorkId: DbId
    ) async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            try await bridge.moveEditionToWork(
                editionId: editionId,
                targetWorkId: targetWorkId
            )
            lastResultMessage = "Moved edition to primary work"
            await load()
        } catch let appError as AppError {
            error = appError
        } catch {
            self.error = .unknown(error.localizedDescription)
        }
    }

    // MARK: - Constants

    /// Default rule set used by the "Rescan" toolbar button. Mirrors
    /// Android's `DefaultMatchKinds` in
    /// `DuplicateManagementScreen.kt`.
    static let defaultMatchKinds: [DuplicateMatchKindMobile] = [
        .exactIsbn,
        .titleAndAuthor(titleSimilarity: 0.85),
        .multiIdentifier(minMatches: 2),
        .publisherTitleYear
    ]

    /// Default confidence floor used by the "Rescan" toolbar button.
    /// 0.6 matches Android's `minConfidence = 0.6f` default.
    static let defaultMinConfidence: Float = 0.6

    // MARK: - Private

    private func runMergeAction(
        context: String,
        work: @escaping () async throws -> MergeResultMobile
    ) async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            let result = try await work()
            lastResultMessage = Self.mergeResultLabel(result)
            await load()
        } catch let appError as AppError {
            error = appError
        } catch {
            self.error = .unknown("\(context) failed: \(error.localizedDescription)")
        }
    }

    private func aggregateEditionInWork(
        workIds: [DbId]
    ) async -> [EditionDuplicateCandidateMobile] {
        var collected: [EditionDuplicateCandidateMobile] = []
        for workId in workIds {
            // Per-work failures are swallowed so the partial list
            // from the other works is still shown. Mirrors the
            // Android helper's `try { … } catch (_) { }` shape.
            do {
                let rows = try await bridge.findDuplicateEditionsInWork(
                    workId: workId,
                    matchKinds: [.exactIsbn],
                    minConfidence: Self.defaultMinConfidence
                )
                collected.append(contentsOf: rows)
            } catch {
                continue
            }
        }
        return collected
    }

    private static func mergeResultLabel(_ result: MergeResultMobile) -> String {
        var parts: [String] = []
        if result.movedEditions > 0 {
            parts.append("\(result.movedEditions) editions")
        }
        if result.movedIdentifiers > 0 {
            parts.append("\(result.movedIdentifiers) identifiers")
        }
        if result.movedInventory > 0 {
            parts.append("\(result.movedInventory) inventory")
        }
        if result.movedReadingProgress > 0 {
            parts.append("\(result.movedReadingProgress) progress")
        }
        if result.deletedWork {
            parts.append("work deleted")
        }
        if result.deletedEdition {
            parts.append("edition deleted")
        }
        return parts.isEmpty ? "Merge complete" : "Merged: \(parts.joined(separator: ", "))"
    }
}
