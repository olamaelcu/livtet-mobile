// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LivtetKit",
    platforms: [.iOS(.v16)],
    products: [
        .library(
            name: "LivtetKit",
            targets: ["LivtetKit"]
        ),
        .library(
            name: "LivtetKitFFI",
            targets: ["LivtetKitFFI"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/elijahdou/FastULID.git", from: "1.0.0"),
    ],
    targets: [
        .target(
            name: "LivtetKit",
            dependencies: [
                .product(name: "FastULID", package: "FastULID"),
                .target(name: "LivtetKitFFI"),
            ],
            path: "Sources/LivtetKit"
        ),
        .target(
            name: "LivtetKitFFI",
            dependencies: [
                .product(name: "FastULID", package: "FastULID"),
                .target(name: "livtet_ffiFFI"),
            ],
            path: "Sources/LivtetKitFFI",
            cSettings: [
                .headerSearchPath("../livtet_ffiFFI"),
            ]
        ),
        .target(
            name: "livtet_ffiFFI",
            dependencies: [
                .target(name: "livtet_ffiFFIBinary"),
            ],
            path: "Sources/livtet_ffiFFI",
            publicHeadersPath: "."
        ),
        .binaryTarget(
            name: "livtet_ffiFFIBinary",
            path: "../LivtetKit.xcframework"
        ),
    ]
)
