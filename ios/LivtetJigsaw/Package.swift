// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LivtetJigsaw",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "LivtetJigsaw", targets: ["LivtetJigsaw"])
    ],
    dependencies: [],
    targets: [
        .target(name: "LivtetJigsaw", path: "Sources/LivtetJigsaw"),
        .testTarget(name: "LivtetJigsawTests", dependencies: ["LivtetJigsaw"],
                    path: "Tests/LivtetJigsawTests")
    ]
)
