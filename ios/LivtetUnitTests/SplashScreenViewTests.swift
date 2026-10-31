import LivtetKit
@testable import Livtet
import SwiftUI
import XCTest

final class SplashScreenViewTests: XCTestCase {
    func testSplashInitializesDatabase() async throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        let testDbPath = tempDir.appendingPathComponent("test_livtet.db").path

        try await LivtetCoreBridge.initialize(databasePath: testDbPath)
        XCTAssertTrue(LivtetCoreBridge.isReady, "FFI should be initialized after SplashScreenView.initializeDatabase runs")

        try await LivtetCoreBridge.shutdown()
        XCTAssertFalse(LivtetCoreBridge.isReady)
    }
}
