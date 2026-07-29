import FastULID
@testable import Livtet
import LivtetKit
import LivtetKitFFI
import XCTest

/// Mock bridge for unit-testing the duplicate management view-model
/// without the FFI. Each method has a settable return value and an
/// optional `error` to inject; failing one call doesn't poison the
/// others.
private final class MockDuplicateBridge: DuplicateBridge {
    var workCandidates: [DuplicateCandidateMobile] = []
    var editionInWorkCandidates: [EditionDuplicateCandidateMobile] = []
    var crossWorkEditionCandidates: [CrossWorkEditionDuplicateMobile] = []
    var mergeResult: MergeResultMobile?
    var error: Error?

    /// Per-method error overrides. When set, takes precedence over
    /// the shared `error`. Useful for testing that a per-work
    /// `findDuplicateEditionsInWork` failure is swallowed while the
    /// other work's results still land in the list.
    var workError: Error?
    var crossWorkError: Error?
    var editionInWorkError: Error?
    var mergeError: Error?
    var moveError: Error?

    func findDuplicateWorks(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) throws -> [DuplicateCandidateMobile] {
        if let workError { throw workError }
        if let error { throw error }
        return workCandidates
    }

    func findDuplicateEditionsInWork(
        workId: ULID,
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) throws -> [EditionDuplicateCandidateMobile] {
        if let editionInWorkError { throw editionInWorkError }
        if let error { throw error }
        return editionInWorkCandidates
    }

    func findCrossWorkEditionDuplicates(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) throws -> [CrossWorkEditionDuplicateMobile] {
        if let crossWorkError { throw crossWorkError }
        if let error { throw error }
        return crossWorkEditionCandidates
    }

    func mergeWorks(
        primaryWorkId: ULID,
        duplicateWorkId: ULID,
        conflictResolution: WorkMergeConflictResolutionMobile
    ) throws -> MergeResultMobile {
        if let mergeError { throw mergeError }
        if let error { throw error }
        return mergeResult ?? MergeResultMobile(
            movedEditions: 1,
            movedIdentifiers: 0,
            movedInventory: 0,
            movedReadingProgress: 0,
            deletedWork: true,
            deletedEdition: false
        )
    }

    func mergeEditions(
        primaryEditionId: ULID,
        duplicateEditionId: ULID,
        conflictResolution: EditionMergeConflictResolutionMobile
    ) throws -> MergeResultMobile {
        if let mergeError { throw mergeError }
        if let error { throw error }
        return mergeResult ?? MergeResultMobile(
            movedEditions: 0,
            movedIdentifiers: 0,
            movedInventory: 0,
            movedReadingProgress: 0,
            deletedWork: false,
            deletedEdition: true
        )
    }

    func moveEditionToWork(
        editionId: ULID,
        targetWorkId: ULID
    ) throws {
        if let moveError { throw moveError }
        if let error { throw error }
    }
}

@MainActor
final class DuplicateManagementViewModelTests: XCTestCase {
    private func makeULID(_ byte: UInt8) -> ULID {
        ULID(ulidData: Data(repeating: byte, count: 16))!
    }

    func testLoadSuccessPopulatesAllThreeLists() async {
        let mock = MockDuplicateBridge()
        let workA = makeULID(1)
        let workB = makeULID(2)
        let editionA = makeULID(3)
        let editionB = makeULID(4)
        mock.workCandidates = [
            DuplicateCandidateMobile(
                primaryWorkId: workA,
                duplicateWorkId: workB,
                matchKind: .exactIsbn,
                confidence: 0.95,
                matchingIdentifiers: ["urn:isbn:978-0-06-112008-4"],
                primaryTitle: "Beloved",
                duplicateTitle: "Beloved (Toni Morrison)"
            )
        ]
        mock.editionInWorkCandidates = [
            EditionDuplicateCandidateMobile(
                workId: workA,
                primaryEditionId: editionA,
                duplicateEditionId: editionB,
                matchKind: .exactIsbn,
                confidence: 0.99,
                matchingIsbns: ["urn:isbn:978-0-06-112008-4"]
            )
        ]
        mock.crossWorkEditionCandidates = []

        let viewModel = DuplicateManagementViewModel(bridge: mock)
        await viewModel.load()

        XCTAssertEqual(viewModel.workCandidates.count, 1)
        XCTAssertEqual(viewModel.editionInWorkCandidates.count, 1)
        XCTAssertEqual(viewModel.crossWorkEditionCandidates.count, 0)
        XCTAssertNil(viewModel.error)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testLoadFailureSetsError() async {
        let mock = MockDuplicateBridge()
        mock.error = AppError.database("scan failed")

        let viewModel = DuplicateManagementViewModel(bridge: mock)
        await viewModel.load()

        XCTAssertNotNil(viewModel.error)
        XCTAssertEqual(viewModel.error?.localizedDescription, "scan failed")
        XCTAssertFalse(viewModel.isLoading)
    }

    func testMergeWorksUpdatesResultAndReloads() async {
        let mock = MockDuplicateBridge()
        mock.mergeResult = MergeResultMobile(
            movedEditions: 3,
            movedIdentifiers: 2,
            movedInventory: 0,
            movedReadingProgress: 5,
            deletedWork: true,
            deletedEdition: false
        )
        // After the merge re-runs the load, the work candidates list
        // is empty (mock returns [] by default).
        let viewModel = DuplicateManagementViewModel(bridge: mock)

        await viewModel.mergeWorks(
            primaryWorkId: makeULID(1),
            duplicateWorkId: makeULID(2),
            resolution: WorkMergeConflictResolutionMobile(
                description: .keepPrimary,
                tags: nil,
                genres: nil,
                subjects: nil,
                publishers: nil,
                seriesType: nil,
                language: nil,
                sortTitle: nil
            )
        )

        XCTAssertNotNil(viewModel.lastResultMessage)
        XCTAssertTrue(viewModel.lastResultMessage?.contains("3 editions") ?? false)
        XCTAssertTrue(viewModel.lastResultMessage?.contains("work deleted") ?? false)
        XCTAssertNil(viewModel.error)
    }

    func testMoveEditionToWorkSetsMessage() async {
        let mock = MockDuplicateBridge()
        let viewModel = DuplicateManagementViewModel(bridge: mock)

        await viewModel.moveEditionToWork(
            editionId: makeULID(3),
            targetWorkId: makeULID(1)
        )

        XCTAssertEqual(viewModel.lastResultMessage, "Moved edition to primary work")
        XCTAssertNil(viewModel.error)
    }

    func testMoveEditionFailureSetsError() async {
        let mock = MockDuplicateBridge()
        mock.moveError = AppError.database("FK violation")
        let viewModel = DuplicateManagementViewModel(bridge: mock)

        await viewModel.moveEditionToWork(
            editionId: makeULID(3),
            targetWorkId: makeULID(1)
        )

        XCTAssertNotNil(viewModel.error)
        XCTAssertNil(viewModel.lastResultMessage)
    }

    func testDefaultMatchKindsMatchesAndroid() {
        // Mirrors Android's `DefaultMatchKinds` in
        // `DuplicateManagementScreen.kt`. If either side changes the
        // list, this assertion forces the other to follow.
        XCTAssertEqual(
            DuplicateManagementViewModel.defaultMatchKinds.count,
            4
        )
        if case .exactIsbn = DuplicateManagementViewModel.defaultMatchKinds[0] {} else {
            XCTFail("expected first default to be exactIsbn")
        }
        if case .titleAndAuthor(let sim) = DuplicateManagementViewModel.defaultMatchKinds[1] {
            XCTAssertEqual(sim, 0.85, accuracy: 0.001)
        } else {
            XCTFail("expected second default to be titleAndAuthor(0.85)")
        }
        if case .multiIdentifier(let min) = DuplicateManagementViewModel.defaultMatchKinds[2] {
            XCTAssertEqual(min, 2)
        } else {
            XCTFail("expected third default to be multiIdentifier(2)")
        }
        if case .publisherTitleYear = DuplicateManagementViewModel.defaultMatchKinds[3] {} else {
            XCTFail("expected fourth default to be publisherTitleYear")
        }
        XCTAssertEqual(DuplicateManagementViewModel.defaultMinConfidence, 0.6, accuracy: 0.001)
    }
}
