import Foundation
import Combine
import Network

struct DiscoveredTV: Identifiable, Hashable {
    let id: String      // Bonjour service name
    let name: String
    let host: String
}

/// Finds Android TVs advertising `_androidtvremote2._tcp` on the local network.
@MainActor
final class TVDiscovery: ObservableObject {
    @Published private(set) var tvs: [DiscoveredTV] = []
    @Published private(set) var isScanning = false
    @Published private(set) var failure: String?

    private var browser: NWBrowser?
    private var resolving: Set<String> = []

    func start() {
        guard browser == nil else { return }
        failure = nil
        isScanning = true

        let browser = NWBrowser(
            for: .bonjour(type: "_androidtvremote2._tcp", domain: nil),
            using: NWParameters()
        )
        browser.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in self?.handle(state: state) }
        }
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            Task { @MainActor in self?.handle(results: results) }
        }
        browser.start(queue: .main)
        self.browser = browser
    }

    func stop() {
        browser?.cancel()
        browser = nil
        resolving.removeAll()
        isScanning = false
    }

    func restart() {
        stop()
        tvs = []
        start()
    }

    private func handle(state: NWBrowser.State) {
        switch state {
        case .failed(let error):
            failure = friendlyDescription(of: error)
            isScanning = false
        case .waiting(let error):
            failure = friendlyDescription(of: error)
        case .ready:
            failure = nil
        default:
            break
        }
    }

    private func handle(results: Set<NWBrowser.Result>) {
        var live: Set<String> = []
        for result in results {
            guard case .service(let name, _, _, _) = result.endpoint else { continue }
            live.insert(name)
            guard !resolving.contains(name), !tvs.contains(where: { $0.id == name }) else { continue }
            resolving.insert(name)

            let endpoint = result.endpoint
            Task { [weak self] in
                let host = await Self.resolveIPv4(of: endpoint)
                await MainActor.run {
                    guard let self else { return }
                    self.resolving.remove(name)
                    guard let host, !self.tvs.contains(where: { $0.id == name }) else { return }
                    self.tvs.append(DiscoveredTV(id: name, name: name, host: host))
                }
            }
        }
        tvs.removeAll { !live.contains($0.id) }
    }

    /// Opens a throwaway TCP connection to the Bonjour service so Network.framework
    /// resolves it, then reads the IPv4 address it connected to.
    private nonisolated static func resolveIPv4(of endpoint: NWEndpoint) async -> String? {
        await withCheckedContinuation { (continuation: CheckedContinuation<String?, Never>) in
            let parameters = NWParameters.tcp
            if let ip = parameters.defaultProtocolStack.internetProtocol as? NWProtocolIP.Options {
                ip.version = .v4
            }
            let connection = NWConnection(to: endpoint, using: parameters)
            let once = Once()
            let finish: @Sendable (String?) -> Void = { value in
                if once.fire() {
                    connection.cancel()
                    continuation.resume(returning: value)
                }
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if case .hostPort(let host, _)? = connection.currentPath?.remoteEndpoint,
                       case .ipv4(let address) = host {
                        let text = "\(address)".split(separator: "%").first.map(String.init)
                        finish(text)
                    } else {
                        finish(nil)
                    }
                case .failed, .waiting:
                    finish(nil)
                default:
                    break
                }
            }
            connection.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + 6) { finish(nil) }
        }
    }
}
