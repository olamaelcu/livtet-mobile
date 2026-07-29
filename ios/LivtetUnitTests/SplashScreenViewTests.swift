import LivtetKit
@testable import Livtet
import SwiftUI
import XCTest

final class SplashScreenViewTests: XCTestCase {
    func testSplashInitializesDatabase() async {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        let testDbPath = tempDir.appendingPathComponent("test_livtet.db").path

        try? LivtetCoreBridge.initialize(databasePath: testDbPath)

        let expectation = XCTestExpectation(description: "Database initialized")

        DispatchQueue.global().async {
            while !LivtetCoreBridge.isReady {
                Thread.sleep(forTimeInterval: 0.1)
            }
            DispatchQueue.main.async {
                expectation.fulfill()
            }
        }

        await fulfillment(of: [expectation], timeout: 6.0)

        XCTAssertTrue(LivtetCoreBridge.isReady, "FFI should be initialized after SplashScreenView.initializeDatabase runs")
    }
}
