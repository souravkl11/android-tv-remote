// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "TVRemote",
    platforms: [
        .iOS("26.0"),
        .macOS(.v14),
    ],
    products: [
        // An xtool project should contain exactly one library product,
        // representing the main app.
        .library(
            name: "TVRemote",
            targets: ["TVRemote"]
        ),
    ],
    targets: [
        .target(
            name: "TVRemote"
        ),
    ],
    swiftLanguageModes: [.v5]
)
