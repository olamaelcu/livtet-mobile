import FastULID
@testable import Livtet
import LivtetKit
import LivtetKitFFI
import SnapshotTesting
import SwiftUI
import XCTest

@MainActor
final class AddBookWizardSnapshotTests: XCTestCase {
    private static let snapshotsDirectory: String = "__Snapshots__"

    private var recordMode: Bool {
        ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "true"
    }

    private func makeULID(_ byte: UInt8) -> ULID {
        ULID(ulidData: Data(repeating: byte, count: 16))!
    }

    // MARK: - Step1TitleAndCoverView

    func testStep1Empty() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step1TitleAndCoverView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testStep1Empty",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testStep1WithResults() {
        let mock = MockWizardBridge()
        let viewModel = AddBookWizardViewModel(bridge: mock)
        viewModel.data.searchQuery = "mock"
        viewModel.data.searchResults = [
            ProviderResult(title: "To Kill a Mockingbird", authors: ["Harper Lee"],
                           isbn: "9780061120084", year: 1960, publisher: "Harper",
                           coverUrl: nil, source: "googlebooks"),
            ProviderResult(title: "1984", authors: ["George Orwell"],
                           isbn: "9780451524935", year: 1949, publisher: "Signet",
                           coverUrl: nil, source: "googlebooks")
        ]

        let view = Step1TitleAndCoverView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testStep1WithResults",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testStep1ProviderError() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.providerError = ProviderErrorInfo(
            category: .needsAuth, retryAfterSeconds: nil, providerId: "googlebooks"
        )

        let view = Step1TitleAndCoverView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testStep1ProviderError",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Step2ContributorsView

    func testStep2Empty() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step2ContributorsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testStep2Empty",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testStep2Filled() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.authors = [
            AuthorEntry(name: "Harper Lee", role: "author")
        ]

        let view = Step2ContributorsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testStep2Filled",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testStep2MultipleAuthors() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.authors = [
            AuthorEntry(name: "Terry Pratchett", role: "author"),
            AuthorEntry(name: "Neil Gaiman", role: "author")
        ]

        let view = Step2ContributorsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testStep2MultipleAuthors",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Step3aGenres / Step3bSubjects / Step4Tags

    func testStep3aGenresEmpty() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step3aGenresView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))
        assertSnapshot(matching: view, as: .image(layout: .device(config: .iPhone13)),
                       named: "testStep3aGenresEmpty", record: recordMode,
                       testName: Self.snapshotsDirectory)
    }

    func testStep3bSubjectsEmpty() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step3bSubjectsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))
        assertSnapshot(matching: view, as: .image(layout: .device(config: .iPhone13)),
                       named: "testStep3bSubjectsEmpty", record: recordMode,
                       testName: Self.snapshotsDirectory)
    }

    func testStep4TagsEmpty() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step4TagsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))
        assertSnapshot(matching: view, as: .image(layout: .device(config: .iPhone13)),
                       named: "testStep4TagsEmpty", record: recordMode,
                       testName: Self.snapshotsDirectory)
    }

    func testStep4TagsFilled() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Test"
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        viewModel.data.authors = [AuthorEntry(name: "Alice")]
        viewModel.data.tags = ["fiction", "classics"]
        viewModel.data.genres = ["literary"]
        viewModel.data.subjects = ["history"]
        let view = Step4TagsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))
        assertSnapshot(matching: view, as: .image(layout: .device(config: .iPhone13)),
                       named: "testStep4TagsFilled", record: recordMode,
                       testName: Self.snapshotsDirectory)
    }

    // MARK: - HubView

    func testHubMinimal() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Minimal Book"
        viewModel.data.authors = [AuthorEntry(name: "Alice")]

        let view = HubView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testHubMinimal",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testHubFullyPopulated() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Fully Populated Book"
        viewModel.data.authors = [AuthorEntry(name: "Alice"), AuthorEntry(name: "Bob", role: "illustrator")]
        viewModel.data.description = "A thorough description of the book"
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        viewModel.data.isbn = "978-0-06-112008-4"
        viewModel.data.publishedDate = "1960-07-11"
        viewModel.data.publisher = "Harper"
        viewModel.data.tags = ["fiction", "classics"]
        viewModel.data.genres = ["literary"]
        viewModel.data.subjects = ["history", "race"]

        let view = HubView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testHubFullyPopulated",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - DuplicateWorkDialog

    func testDuplicateDialog() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let workId = makeULID(1)
        viewModel.duplicateSummary = ExistingWorkSummary(
            id: workId, title: "Existing Book", description: nil,
            editionCount: 2, identifierCount: 2,
            existingIsbns: ["978-0-06-112008-4", "978-0-06-112008-5"],
            editions: [EditionSummary(id: makeULID(2), editionTitle: nil, publishedDate: nil, existingIsbns: ["978-0-06-112008-4"])]
        )

        let view = DuplicateWorkDialog(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testDuplicateDialog",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }
}
