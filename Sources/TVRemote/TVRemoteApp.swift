import SwiftUI

@main
struct TVRemoteApp: App {
    @StateObject private var remote = TVRemote()
    @StateObject private var discovery = TVDiscovery()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(remote)
                .environmentObject(discovery)
                .preferredColorScheme(.dark)
        }
    }
}
