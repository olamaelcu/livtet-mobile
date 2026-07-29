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

    // MARK: - StepSearchView

    func testSearchViewEmpty() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = StepSearchView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testSearchViewEmpty",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testSearchViewWithResults() {
        let mock = MockWizardBridge()
        mock.searchResults = [
            PluginHitMobile(
                title: "To Kill a Mockingbird", authors: ["Harper Lee"],
                identifiers: ["urn:isbn:9780061120084"], coverUrl: nil, publisher: "Harper",
                publishedDate: "1960", pageCount: nil, language: "en", description: nil,
                source: "googlebooks", sourceUrl: ""
            ),
            PluginHitMobile(
                title: "1984", authors: ["George Orwell"],
                identifiers: ["urn:isbn:9780451524935"], coverUrl: nil, publisher: "Signet",
                publishedDate: "1949", pageCount: nil, language: "en", description: nil,
                source: "googlebooks", sourceUrl: ""
            )
        ]
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

        let view = StepSearchView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testSearchViewWithResults",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testSearchViewProviderError() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.providerError = ProviderErrorInfo(
            category: .needsAuth, retryAfterSeconds: nil, providerId: "googlebooks"
        )

        let view = StepSearchView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testSearchViewProviderError",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - StepTitleAndAuthorsView

    func testTitleAndAuthorsEmpty() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = StepTitleAndAuthorsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testTitleAndAuthorsEmpty",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testTitleAndAuthorsFilled() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "To Kill a Mockingbird"
        viewModel.data.authors = [
            AuthorEntry(name: "Harper Lee", role: "author")
        ]

        let view = StepTitleAndAuthorsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testTitleAndAuthorsFilled",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testTitleAndAuthorsMultipleAuthors() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Good Omens"
        viewModel.data.authors = [
            AuthorEntry(name: "Terry Pratchett", role: "author"),
            AuthorEntry(name: "Neil Gaiman", role: "author")
        ]

        let view = StepTitleAndAuthorsView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testTitleAndAuthorsMultipleAuthors",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
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
        viewModel.data.coverUrl = URL(string: "https://example.com/c.jpg")
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

    func testHubSaving() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Saving Book"
        viewModel.data.authors = [AuthorEntry(name: "Alice")]

        let view = HubView(viewModel: viewModel)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testHubSaving",
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
