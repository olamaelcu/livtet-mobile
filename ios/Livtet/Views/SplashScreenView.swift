import Inject
import LivtetKit
import LivtetKitFFI
import os
import SwiftUI

/// Boot screen for the Livtet iOS app.
///
/// Three phases:
/// 1. `.loading` — a centered spinner. On `.onAppear` we kick off
///    `initializeDatabase()` which calls `LivtetCoreBridge.initialize(databasePath:)`
///    on a background queue.
/// 2. `.error` — an inline error message + a Retry button. We only
///    show this for unrecoverable initialization failures (e.g.
///    database file-system errors).
/// 3. `.ready` — the root [RootTabView] appears with a cross-fade,
///    so the user sees the brand transition rather than a hard cut.
struct SplashScreenView: View {
    private let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "SplashScreenView")

    @State private var state: SplashState = .loading
    @State private var errorMessage: String?

    @ObserveInjection var forceRedraw

    enum SplashState {
        case loading
        case error
        case ready
    }

    private let databasePath: String

    init(databasePath: String? = nil) {
        self.databasePath = databasePath ?? Self.defaultDatabasePath
    }

    private static var defaultDatabasePath: String {
        let supportDir = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first!
            .appendingPathComponent("net.olamaelcu.livtet", isDirectory: true)
        return supportDir.appendingPathComponent("livtet.db").path
    }

    var body: some View {
        // ZStack base layer paints cream edge-to-edge regardless of
        // which splash state is showing. A plain `.background()` on a
        // Group only fills the content-sized frame — `Color` as the
        // first ZStack child fills the whole screen.
        ZStack {
            Color("surfaceDefault").ignoresSafeArea()

            Group {
                switch state {
                case .loading:
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle())
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("Loading")
                    .accessibilityHint("Setting up your local database...")
                    .onAppear {
                        if state == .loading {
                            initializeDatabase()
                        }
                    }

                case .error:
                    VStack(spacing: 16) {
                        Image.lucideInfo
                            .font(.largeTitle)
                            .foregroundStyle(Color.brand)
                            .accessibilityHidden(true)
                        Text(errorMessage ?? "Unknown error")
                            .multilineTextAlignment(.center)
                            .padding()
                            .accessibilityLabel("Error")
                            .accessibilityHint(errorMessage ?? "An unexpected error occurred")
                        Button("Retry startup") {
                            state = .loading
                            initializeDatabase()
                        }
                        .accessibilityLabel("Retry")
                        .accessibilityHint("Attempt to initialize the database again")
                    }

                case .ready:
                    RootTabView()
                        .transition(.opacity)
                }
            }
            .padding()
        }
        .animation(.easeInOut(duration: 0.25), value: state)
        .enableInjection()
    }

    private func initializeDatabase() {
        logger.info("Starting database initialization: \(databasePath, privacy: .public)")
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                // Ensure the Application Support subdirectory exists
                logger.debug("Checking/creating Application Support directory")
                let supportDir = FileManager.default
                    .urls(for: .applicationSupportDirectory, in: .userDomainMask)
                    .first!
                    .appendingPathComponent("net.olamaelcu.livtet", isDirectory: true)

                // Create directory if it doesn't exist
                if !FileManager.default.fileExists(atPath: supportDir.path) {
                    logger.info("Making database directory at \(supportDir.path)")
                    try FileManager.default.createDirectory(at: supportDir, withIntermediateDirectories: true)
                }

                // Clean up any stale WAL files from previous incomplete sessions
                let dbPath = databasePath

                logger.info("Initializing LivtetCoreBridge")
                try LivtetCoreBridge.initialize(databasePath: databasePath)
                logger.info("Database initialization succeeded")

                #if DEBUG
                Task {
                    logger.info("Seeding database with demo data (30 works)")
                    do {
                        let seedResult = try await LivtetCoreBridge.seedDatabase(works: 30)
                        logger.info("Seeding complete: \(seedResult.worksCreated) works, \(seedResult.editionsCreated) editions, \(seedResult.authorsCreated) authors")
                    } catch {
                        logger.warning("Seeding failed: \(error.localizedDescription, privacy: .public)")
                    }
                }
                #endif

                DispatchQueue.main.async {
                    logger.info("Transitioning to ready state")
                    state = .ready
                }
            } catch {
                logger.error("Database initialization failed: \(error.localizedDescription, privacy: .public)")
                let fullError = String(describing: error)
                logger.error("Full error: \(fullError, privacy: .public)")
                if let mobileError = error as? MobileError {
                    logger.error("MobileError code: \(String(describing: mobileError), privacy: .public)")
                }
                DispatchQueue.main.async {
                    state = .error
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

#if DEBUG
#Preview {
    SplashScreenView()
}
#endif
