// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CoverageThresholdChecker",
    platforms: [
        .macOS(.v14)
    ],
    dependencies: [
        .package(url: "https://github.com/davidahouse/XCResultKit", from: "1.2.0")
    ],
    targets: [
        .executableTarget(
            name: "CoverageThresholdChecker",
            dependencies: [
                .product(name: "XCResultKit", package: "XCResultKit")
            ]
        )
    ]
)
