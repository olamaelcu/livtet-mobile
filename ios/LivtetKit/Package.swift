// swift-tools-version: 5.9
import PackageDescription

// One Swift module (`LivtetKit`) carries the three generated sources
// (`livtet_ffi.swift`, `LivtetTypes.swift`, `LivtetSearch.swift`) plus the
// hand-written shims, so consumers `import LivtetKit` and see everything.
// All generated `.swift` must compile in a single module — Swift has no
// equivalent of Kotlin's `external_packages`. Each component keeps its own
// C bridge module (`livtet_ffiFFI`, `LivtetTypesFFI`, `LivtetSearchFFI`).
let package = Package(
    name: "LivtetKit",
    platforms: [.iOS(.v16)],
    products: [
        .library(
            name: "LivtetKit",
            targets: ["LivtetKit"]
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
                .target(name: "livtet_ffiFFI"),
                .target(name: "LivtetTypesFFI"),
                .target(name: "LivtetSearchFFI"),
            ],
            path: "Sources/LivtetKit",
            cSettings: [
                .headerSearchPath("../livtet_ffiFFI"),
                .headerSearchPath("../LivtetTypesFFI"),
                .headerSearchPath("../LivtetSearchFFI"),
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
        .target(
            name: "LivtetTypesFFI",
            dependencies: [
                .target(name: "livtet_ffiFFIBinary"),
            ],
            path: "Sources/LivtetTypesFFI",
            publicHeadersPath: "."
        ),
        .target(
            name: "LivtetSearchFFI",
            dependencies: [
                .target(name: "livtet_ffiFFIBinary"),
            ],
            path: "Sources/LivtetSearchFFI",
            publicHeadersPath: "."
        ),
        .binaryTarget(
            name: "livtet_ffiFFIBinary",
            path: "../LivtetKit.xcframework"
        ),
    ]
)
