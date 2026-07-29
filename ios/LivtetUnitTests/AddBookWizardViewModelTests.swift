@testable import Livtet
import FastULID
import LivtetKitFFI
import XCTest

@MainActor
final class AddBookWizardViewModelTests: XCTestCase {
    private func makeULID(_ byte: UInt8) -> ULID {
        ULID(ulidData: Data(repeating: byte, count: 16))!
    }

    func testCanContinueFromTitleAndAuthorsRequiresBoth() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertFalse(viewModel.canContinueFromTitleAndAuthors)
        viewModel.data.title = "Test"
        XCTAssertFalse(viewModel.canContinueFromTitleAndAuthors)
        viewModel.data.authors = [AuthorEntry(name: "Author")]
        XCTAssertTrue(viewModel.canContinueFromTitleAndAuthors)
    }

    func testIsItemFilled() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertFalse(viewModel.isItemFilled(.description))
        viewModel.data.description = "A book"
        XCTAssertTrue(viewModel.isItemFilled(.description))
        XCTAssertFalse(viewModel.isItemFilled(.cover))
        viewModel.data.coverUrl = URL(string: "https://example.com/c.jpg")
        XCTAssertTrue(viewModel.isItemFilled(.cover))
    }

    func testClearItem() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.description = "Test"
        viewModel.data.coverUrl = URL(string: "https://example.com/c.jpg")
        viewModel.data.isbn = "123"
        viewModel.clearItem(.description)
        XCTAssertEqual(viewModel.data.description, "")
        viewModel.clearItem(.cover)
        XCTAssertNil(viewModel.data.coverUrl)
        viewModel.clearItem(.isbn)
        XCTAssertEqual(viewModel.data.isbn, "")
    }

    func testPreviewForItem() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.data.title = "Test"
        viewModel.data.authors = [AuthorEntry(name: "Alice")]
        XCTAssertEqual(viewModel.previewForItem(.titleAndAuthors), "\"Test\" — Alice")
    }

    func testSkipSearchAdvancesToTitleAndAuthors() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        XCTAssertEqual(viewModel.currentPage, .search)
        viewModel.skipSearch()
        XCTAssertEqual(viewModel.currentPage, .titleAndAuthors)
    }

    func testSelectResultPopulatesFields() {
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
        XCTAssertEqual(viewModel.data.coverUrl?.absoluteString, "https://example.com/c.jpg")
        XCTAssertEqual(viewModel.currentPage, .titleAndAuthors)
    }

    func testAddAuthor() {
        let viewModel = AddBookWizardViewModel(bridge: MockWizardBridge())
        viewModel.addAuthor(name: "Alice", role: "author")
        XCTAssertEqual(viewModel.data.authors.count, 1)
        XCTAssertEqual(viewModel.data.authors[0].name, "Alice")
    }

    func testSaveCallsCreateBookCompleteAndPostsNotification() async {
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
        viewModel.data.authors = [AuthorEntry(name: "Alice")]
        let expectation = expectation(forNotification: .livtetBookCreated, object: nil)
        viewModel.save()
        await fulfillment(of: [expectation], timeout: 2.0)
        XCTAssertTrue(viewModel.didCompleteSave)
    }

    func testSaveSetsDuplicateSummaryOnIsbnConflict() async {
        let mock = MockWizardBridge()
        mock.findByIsbnResult = ExistingWorkSummary(
            id: makeULID(1), title: "Existing", description: nil,
            editionCount: 1, identifierCount: 1,
            existingIsbns: ["978-0-06-112008-4"], editions: []
        )
        let viewModel = AddBookWizardViewModel(bridge: mock)
        viewModel.data.title = "Test"
        viewModel.data.authors = [AuthorEntry(name: "Alice")]
        viewModel.data.isbn = "978-0-06-112008-4"
        viewModel.save()
        try? await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertNotNil(viewModel.duplicateSummary)
    }

    func testFullFlowSearchSelectTagSave() async {
        let mock = MockWizardBridge()
        let workId = makeULID(1)
        mock.createBookResult = Book(id: workId, title: "Selected Book", description: nil)
        mock.editionsForWork = [Edition(
            id: makeULID(2), workId: workId, editionTitle: nil, isbn: "978-0-06-112008-4",
            publishedDate: nil, pageCount: nil, formatId: nil, languageId: nil,
            notes: nil, description: nil, createdAt: "", updatedAt: nil,
            inventoryId: nil, coverPath: nil
        )]

        let viewModel = AddBookWizardViewModel(bridge: mock)

        let result = ProviderResult(
            title: "Selected Book", authors: ["Alice"],
            isbn: "978-0-06-112008-4", year: 2024, publisher: "Pub",
            coverUrl: URL(string: "https://example.com/c.jpg"), source: "googlebooks"
        )
        viewModel.selectResult(result)
        XCTAssertEqual(viewModel.data.title, "Selected Book")
        XCTAssertEqual(viewModel.data.isbn, "978-0-06-112008-4")
        XCTAssertEqual(viewModel.currentPage, .titleAndAuthors)

        viewModel.addAuthor(name: "Bob", role: "illustrator")
        XCTAssertEqual(viewModel.data.authors.count, 2)

        viewModel.goToPage(.tags)
        viewModel.data.tags = ["fiction", "classics"]

        viewModel.goToPage(.genres)
        viewModel.data.genres = ["literary"]

        viewModel.goToPage(.subjects)
        viewModel.data.subjects = ["history"]

        viewModel.goToHub()
        let expectation = expectation(forNotification: .livtetBookCreated, object: nil)
        viewModel.save()
        await fulfillment(of: [expectation], timeout: 2.0)

        XCTAssertTrue(viewModel.didCompleteSave)
        XCTAssertEqual(mock.createBookCallCount, 1)
        XCTAssertEqual(mock.createBookCallArgs?.title, "Selected Book")
        XCTAssertEqual(mock.createBookCallArgs?.isbn, "9780061120084")
        XCTAssertEqual(mock.linkWorkTagCalls.count, 2)
        XCTAssertEqual(mock.linkWorkGenreCalls.count, 1)
        XCTAssertEqual(mock.linkWorkSubjectCalls.count, 1)
        XCTAssertEqual(mock.findOrCreateTagCalls, ["fiction", "classics"])
        XCTAssertEqual(mock.findOrCreateGenreCalls, ["literary"])
        XCTAssertEqual(mock.findOrCreateSubjectCalls, ["history"])
    }

    func testSaveLinksTagsGenresAndSubjects() async {
        let mock = MockWizardBridge()
        let workId = makeULID(1)
        mock.createBookResult = Book(id: workId, title: "Tagged Book", description: nil)
        mock.editionsForWork = [Edition(
            id: makeULID(2), workId: workId, editionTitle: nil, isbn: nil,
            publishedDate: nil, pageCount: nil, formatId: nil, languageId: nil,
            notes: nil, description: nil, createdAt: "", updatedAt: nil,
            inventoryId: nil, coverPath: nil
        )]

        let viewModel = AddBookWizardViewModel(bridge: mock)
        viewModel.data.title = "Tagged Book"
        viewModel.data.authors = [AuthorEntry(name: "Alice")]
        viewModel.data.tags = ["dystopian", "science fiction"]
        viewModel.data.genres = ["sci-fi"]
        viewModel.data.subjects = ["future", "technology"]

        let expectation = expectation(forNotification: .livtetBookCreated, object: nil)
        viewModel.save()
        await fulfillment(of: [expectation], timeout: 2.0)

        XCTAssertTrue(viewModel.didCompleteSave)
        XCTAssertEqual(mock.linkWorkTagCalls.count, 2)
        XCTAssertEqual(mock.linkWorkGenreCalls.count, 1)
        XCTAssertEqual(mock.linkWorkSubjectCalls.count, 2)
    }

    func testDuplicatedTagsAreIgnored() async {
        let mock = MockWizardBridge()
        let workId = makeULID(1)
        mock.createBookResult = Book(id: workId, title: "Dup Book", description: nil)
        mock.editionsForWork = [Edition(
            id: makeULID(2), workId: workId, editionTitle: nil, isbn: nil,
            publishedDate: nil, pageCount: nil, formatId: nil, languageId: nil,
            notes: nil, description: nil, createdAt: "", updatedAt: nil,
            inventoryId: nil, coverPath: nil
        )]

        let viewModel = AddBookWizardViewModel(bridge: mock)
        viewModel.data.title = "Dup Book"
        viewModel.data.authors = [AuthorEntry(name: "Alice")]
        viewModel.data.tags = ["fiction", "fiction"]

        let expectation = expectation(forNotification: .livtetBookCreated, object: nil)
        viewModel.save()
        await fulfillment(of: [expectation], timeout: 2.0)

        XCTAssertEqual(mock.findOrCreateTagCalls, ["fiction", "fiction"])
        XCTAssertEqual(mock.linkWorkTagCalls.count, 2)
    }

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
