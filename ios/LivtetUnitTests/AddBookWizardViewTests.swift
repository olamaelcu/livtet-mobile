@testable import Livtet
import LivtetKitFFI
import SwiftUI
import ViewInspector
import XCTest

@MainActor
final class AddBookWizardViewTests: XCTestCase {

    // MARK: - StepSearchView

    func testSearchFieldUpdatesViewModel() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = StepSearchView(viewModel: vm)
        let inspected = try view.inspect()
        let textField = try inspected.find(ViewType.TextField.self)
        try textField.setInput("Lord of")
        XCTAssertEqual(vm.data.searchQuery, "Lord of")
    }

    func testSkipSearchAdvancesPage() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = StepSearchView(viewModel: vm)
        let inspected = try view.inspect()
        try inspected.find(button: "Skip search and add manually").tap()
        XCTAssertEqual(vm.currentPage, .titleAndAuthors)
    }

    // MARK: - StepTitleAndAuthorsView

    func testContinueButtonDisabledWhenIncomplete() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = StepTitleAndAuthorsView(viewModel: vm)
        let inspected = try view.inspect()
        let button = try inspected.find(button: "Continue")
        XCTAssertThrowsError(try button.tap())
    }

    func testContinueButtonEnabledWhenComplete() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.title = "A Book"
        vm.data.authors = [AuthorEntry(name: "Alice")]
        let view = StepTitleAndAuthorsView(viewModel: vm)
        let inspected = try view.inspect()
        let button = try inspected.find(button: "Continue")
        XCTAssertNoThrow(try button.tap())
    }

    func testAddAuthorFieldIsPresent() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = StepTitleAndAuthorsView(viewModel: vm)
        let inspected = try view.inspect()
        _ = try inspected.find(ViewType.TextField.self)
    }

    // MARK: - HubView

    func testCreateBookButtonExistsWhenComplete() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.title = "A Book"
        vm.data.authors = [AuthorEntry(name: "Alice")]
        let view = HubView(viewModel: vm)
        let inspected = try view.inspect()
        _ = try inspected.find(button: "Create Book")
    }

    func testHubRendersRequiredSection() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.title = "A Book"
        vm.data.authors = [AuthorEntry(name: "Alice")]
        let view = HubView(viewModel: vm)
        let inspected = try view.inspect()
        _ = try inspected.find(text: "Title & Authors")
    }
}
