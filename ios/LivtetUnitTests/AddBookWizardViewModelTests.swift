@testable import Livtet
import Combine
import FastULID
import LivtetKitFFI
import XCTest

@MainActor
final class AddBookWizardViewModelTests: XCTestCase {
    private func makeULID(_ byte: UInt8) -> ULID {
        ULID(ulidData: Data(repeating: byte, count: 16))!
    }

    // MARK: - Initial state

    func testInitialPageIsTitleAndCover() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertEqual(viewModel.currentPage, .titleAndCover)
    }

    func testCanContinueFromTitleAndCoverRequiresBoth() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertFalse(viewModel.canContinueFromTitleAndCover)
        viewModel.data.title = "Test"
        XCTAssertFalse(viewModel.canContinueFromTitleAndCover)
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        XCTAssertTrue(viewModel.canContinueFromTitleAndCover)
    }

    func testCanContinueFromContributorsRequiresAuthor() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertFalse(viewModel.canContinueFromContributors)
        viewModel.data.authors = [AuthorEntry(name: "Alice", role: "translator")]
        XCTAssertFalse(viewModel.canContinueFromContributors)
        viewModel.data.authors = [AuthorEntry(name: "Alice", role: "author")]
        XCTAssertTrue(viewModel.canContinueFromContributors)
    }

    func testCanContinueFromTitleAndAuthorsRequiresBothNewSteps() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertFalse(viewModel.canContinueFromTitleAndAuthors)
        viewModel.data.title = "Test"
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        viewModel.data.authors = [AuthorEntry(name: "Alice", role: "author")]
        XCTAssertTrue(viewModel.canContinueFromTitleAndAuthors)
    }

    // MARK: - Cover transitions

    func testCoverRoundTrip() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertNil(viewModel.data.cover)
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        XCTAssertEqual(viewModel.data.coverUrl?.absoluteString, "https://example.com/c.jpg")
        viewModel.data.cover = nil
        XCTAssertNil(viewModel.data.coverUrl)
    }

    func testIsItemFilled() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertFalse(viewModel.isItemFilled(.titleAndCover))
        viewModel.data.title = "Test"
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        XCTAssertTrue(viewModel.isItemFilled(.titleAndCover))
        XCTAssertFalse(viewModel.isItemFilled(.contributors))
        viewModel.data.authors = [AuthorEntry(name: "Alice", role: "author")]
        XCTAssertTrue(viewModel.isItemFilled(.contributors))
        XCTAssertFalse(viewModel.isItemFilled(.description))
        viewModel.data.description = "A book"
        XCTAssertTrue(viewModel.isItemFilled(.description))
    }

    func testClearItem() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.description = "Test"
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        viewModel.data.isbn = "123"
        viewModel.clearItem(.description)
        XCTAssertEqual(viewModel.data.description, "")
        viewModel.clearItem(.cover)
        XCTAssertNil(viewModel.data.cover)
        viewModel.clearItem(.isbn)
        XCTAssertEqual(viewModel.data.isbn, "")
    }

    func testPreviewForItem() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Test"
        viewModel.data.authors = [AuthorEntry(name: "Alice")]
        XCTAssertEqual(viewModel.previewForItem(.titleAndCover), "Test")
        XCTAssertEqual(viewModel.previewForItem(.contributors), "Alice")
        XCTAssertEqual(viewModel.previewForItem(.titleAndAuthors), "\"Test\" — Alice")
    }

    // MARK: - Step navigation

    func testContinueFromTitleAndCoverAdvancesToContributors() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Test"
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        viewModel.continueFromTitleAndCover()
        XCTAssertEqual(viewModel.currentPage, .contributors)
    }

    func testContinueFromTitleAndCoverNoOpWhenIncomplete() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Test"
        viewModel.continueFromTitleAndCover()
        XCTAssertEqual(viewModel.currentPage, .titleAndCover)
    }

    func testGoToNextWalksTheLinearFlow() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertEqual(viewModel.currentPage, .titleAndCover)
        viewModel.goToNext()
        XCTAssertEqual(viewModel.currentPage, .contributors)
        viewModel.goToNext()
        XCTAssertEqual(viewModel.currentPage, .genres)
        viewModel.goToNext()
        XCTAssertEqual(viewModel.currentPage, .subjects)
        viewModel.goToNext()
        XCTAssertEqual(viewModel.currentPage, .tags)
        viewModel.goToNext()
        XCTAssertEqual(viewModel.currentPage, .tags)
    }

    func testGoToBackReverts() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.goToNext()
        viewModel.goToNext()
        viewModel.goToNext()
        XCTAssertEqual(viewModel.currentPage, .subjects)
        viewModel.goToBack()
        XCTAssertEqual(viewModel.currentPage, .genres)
    }

    func testSelectResultPopulatesFieldsAndAdvances() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        let result = ProviderResult(
            title: "Selected", authors: ["Author"], isbn: "978-0-06-112008-4",
            year: 2024, publisher: "Pub",
            coverUrl: URL(string: "https://example.com/c.jpg"), source: "googlebooks"
        )
        viewModel.selectResult(result)
        XCTAssertEqual(viewModel.data.title, "Selected")
        XCTAssertEqual(viewModel.data.isbn, "978-0-06-112008-4")
        XCTAssertEqual(viewModel.data.publishedDate, "2024-01-01")
        XCTAssertEqual(viewModel.data.publisher, "Pub")
        XCTAssertEqual(viewModel.data.authors.count, 1)
        XCTAssertEqual(viewModel.data.cover?.displayURL?.absoluteString, "https://example.com/c.jpg")
        XCTAssertEqual(viewModel.currentPage, .contributors)
    }

    func testAddAuthor() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.addAuthor(name: "Alice", role: "author")
        XCTAssertEqual(viewModel.data.authors.count, 1)
        XCTAssertEqual(viewModel.data.authors[0].name, "Alice")
    }

    // MARK: - Save is unavailable in Phase 1

    func testSaveIsUnavailableInPhase1() {
        let mock = MockWizardBridge()
        let workId = makeULID(1)
        mock.createBookResult = Book(id: workId, title: "Test", description: nil)
        mock.editionsForWork = [Edition(
            id: makeULID(2), workId: workId, editionTitle: nil, isbn: nil,
            publishedDate: nil, pageCount: nil, formatId: nil, languageId: nil,
            notes: nil, description: nil, createdAt: "", updatedAt: nil,
            inventoryId: nil, coverPath: nil
        )]

        let viewModel = AddBookWizardViewModel(bridge: mock)
        viewModel.data.title = "Test"
        viewModel.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        viewModel.data.authors = [AuthorEntry(name: "Alice", role: "author")]
        viewModel.save()
        XCTAssertFalse(viewModel.didCompleteSave)
        XCTAssertFalse(viewModel.isSaving)
    }

    // MARK: - Search

    func testSearchWithShortQueryReturnsNoResults() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.searchQuery = "ab"
        XCTAssertTrue(viewModel.data.searchResults.isEmpty)
        XCTAssertTrue(viewModel.data.localDedupResults.isEmpty)
        XCTAssertFalse(viewModel.isSearching)
    }

    func testSearchShowsProviderError() async {
        let mock = MockWizardBridge()
        mock.searchError = MobileError.ProviderError(
            category: .needsAuth, retryAfterSeconds: nil, providerId: "googlebooks"
        )
        let viewModel = AddBookWizardViewModel(bridge: mock)
        await viewModel.performSearch(query: "test query")
        XCTAssertNotNil(viewModel.providerError)
        XCTAssertEqual(viewModel.providerError?.providerId, "googlebooks")
    }

    func testProviderErrorClearedOnRetry() async {
        let mock = MockWizardBridge()
        mock.searchError = MobileError.ProviderError(
            category: .needsAuth, retryAfterSeconds: nil, providerId: "googlebooks"
        )
        let viewModel = AddBookWizardViewModel(bridge: mock)
        await viewModel.performSearch(query: "test query")
        XCTAssertNotNil(viewModel.providerError)

        mock.searchError = nil
        mock.searchResults = []
        await viewModel.performSearch(query: "new query")
        XCTAssertNil(viewModel.providerError)
    }
}
