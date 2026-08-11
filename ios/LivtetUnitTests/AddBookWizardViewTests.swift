@testable import Livtet
import LivtetKitFFI
import SwiftUI
import ViewInspector
import XCTest

@MainActor
final class AddBookWizardViewTests: XCTestCase {

    // MARK: - Step1TitleAndCoverView

    func testStep1TitleFieldUpdatesViewModel() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step1TitleAndCoverView(viewModel: vm)
        let inspected = try view.inspect()
        // There are two text fields in Step 1 (search + title); find both
        // and tap the title one. The search field is bound to the search
        // query; the title field is bound to vm.data.title.
        let textFields = try inspected.findAll(ViewType.TextField.self)
        XCTAssertGreaterThanOrEqual(textFields.count, 2)
        try textFields[1].setInput("Manually-typed Title")
        XCTAssertEqual(vm.data.title, "Manually-typed Title")
    }

    func testStep1ContinueDisabledWhenIncomplete() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step1TitleAndCoverView(viewModel: vm)
        let inspected = try view.inspect()
        let button = try inspected.find(button: "Continue: Contributors")
        XCTAssertThrowsError(try button.tap())
    }

    func testStep1ContinueAdvancesWhenTitleAndCoverEntered() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.title = "Test"
        vm.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        XCTAssertTrue(vm.canContinueFromTitleAndCover)
        vm.continueFromTitleAndCover()
        XCTAssertEqual(vm.currentPage, .contributors)
    }

    // MARK: - Step2ContributorsView

    func testStep2ContinueDisabledWhenNoAuthor() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step2ContributorsView(viewModel: vm)
        let inspected = try view.inspect()
        let button = try inspected.find(button: "Continue: Genres")
        XCTAssertThrowsError(try button.tap())
    }

    func testStep2ContinueEnabledWhenAuthorPresent() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.authors = [AuthorEntry(name: "Alice", role: "author")]
        XCTAssertTrue(vm.canContinueFromContributors)
        vm.goToNext()
        XCTAssertEqual(vm.currentPage, .genres)
    }

    func testStep2ContinueDisabledWithNonAuthorRole() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.authors = [AuthorEntry(name: "Alice", role: "translator")]
        XCTAssertFalse(vm.canContinueFromContributors)
    }

    // MARK: - Step4TagsView

    func testStep4ShowsSaveUnavailableBannerInPhase1() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        let view = Step4TagsView(viewModel: vm)
        let inspected = try view.inspect()
        let banner = try inspected.find(text: "Saving is coming soon")
        XCTAssertNotNil(banner)
    }

    func testStep4DiscardsOnPhase1() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.title = "Test"
        vm.data.cover = .remote(URL(string: "https://example.com/c.jpg")!)
        vm.data.authors = [AuthorEntry(name: "Alice")]
        XCTAssertEqual(vm.currentPage, .titleAndCover)
        vm.goToNext()
        vm.goToNext()
        vm.goToNext()
        vm.goToNext()
        XCTAssertEqual(vm.currentPage, .tags)
    }

    // MARK: - HubView

    func testHubSaveButtonHiddenInPhase1() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.title = "A Book"
        vm.data.authors = [AuthorEntry(name: "Alice")]
        let view = HubView(viewModel: vm)
        let inspected = try view.inspect()
        XCTAssertThrowsError(try inspected.find(button: "Create Book"))
    }

    func testHubRendersTitleAndCoverSection() throws {
        let vm = AddBookWizardViewModel(bridge: MockWizardBridge())
        vm.data.title = "A Book"
        vm.data.authors = [AuthorEntry(name: "Alice")]
        let view = HubView(viewModel: vm)
        let inspected = try view.inspect()
        _ = try inspected.find(text: "Title & Cover")
        _ = try inspected.find(text: "Contributors")
    }
}
