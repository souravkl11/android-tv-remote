import Foundation
import Combine
import Security
import UIKit

struct VolumeState: Equatable {
    var level = 0
    var max = 100
    var muted = false
}

private struct PairingSession {
    let channel: TLSChannel
    let serverCertificate: SecCertificate
    let identity: SecIdentity
    let host: String
}

@MainActor
final class TVRemote: ObservableObject {
    enum Status: Equatable {
        case disconnected
        case connecting(String)
        case needsPairing
        case connected
        case error(String)
    }

    static let remotePort: UInt16 = 6466
    static let pairingPort: UInt16 = 6467

    @Published private(set) var status: Status = .disconnected
    @Published private(set) var volume = VolumeState()
    @Published private(set) var currentApp = ""
    @Published private(set) var host: String
    @Published private(set) var deviceName: String
    @Published var pinError: String?

    private let defaults = UserDefaults.standard
    private var generation = 0
    private var session: Task<Void, Never>?
    private var channel: TLSChannel?
    private var pairing: PairingSession?
    private var imeCounter = 0
    private var fieldCounter = 0

    init() {
        host = defaults.string(forKey: "lastHost") ?? ""
        deviceName = defaults.string(forKey: "lastName") ?? "Android TV"
    }

    var isConnected: Bool { status == .connected }

    var canReconnect: Bool {
        switch status {
        case .disconnected, .error: return true
        default: return false
        }
    }

    // MARK: Connection

    func connect(host newHost: String, name: String? = nil) {
        let cleaned = newHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return }

        host = cleaned
        if let name, !name.isEmpty { deviceName = name }
        defaults.set(host, forKey: "lastHost")
        defaults.set(deviceName, forKey: "lastName")

        tearDown()
        let current = generation
        session = Task { [weak self] in
            await self?.runRemote(host: cleaned, generation: current)
        }
    }

    func reconnectIfNeeded() {
        guard canReconnect, !host.isEmpty else { return }
        connect(host: host)
    }

    func disconnect() {
        tearDown()
        status = .disconnected
    }

    private func tearDown() {
        generation += 1
        session?.cancel()
        session = nil
        channel?.close()
        channel = nil
        pairing?.channel.close()
        pairing = nil
        pinError = nil
    }

    private func isCurrent(_ gen: Int) -> Bool { gen == generation }

    private func runRemote(host: String, generation gen: Int) async {
        status = .connecting("Connecting to \(host)…")

        let identity: SecIdentity
        do {
            identity = try ClientIdentity.load()
        } catch {
            if isCurrent(gen) { status = .error(error.localizedDescription) }
            return
        }

        let paired = isPaired(host)
        let attempts = paired ? 2 : 1
        var lastError: Error?

        for attempt in 0..<attempts {
            guard isCurrent(gen) else { return }
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard isCurrent(gen) else { return }
            }

            let candidate = TLSChannel(host: host, port: Self.remotePort, identity: identity)
            do {
                try await candidate.start()
            } catch {
                candidate.close()
                lastError = error
                continue
            }

            guard isCurrent(gen) else {
                candidate.close()
                return
            }
            channel = candidate
            status = .connecting("Starting session…")
            await readLoop(candidate, generation: gen)
            return
        }

        guard isCurrent(gen) else { return }
        if !paired {
            // The TV rejects clients it hasn't paired with, so start pairing.
            await startPairing(host: host, identity: identity, generation: gen)
        } else {
            let reason = lastError?.localizedDescription ?? "The TV didn't respond."
            status = .error("Couldn't reach the TV. \(reason)")
        }
    }

    private func readLoop(_ channel: TLSChannel, generation gen: Int) async {
        do {
            while !Task.isCancelled {
                let data = try await channel.receiveMessage()
                guard isCurrent(gen) else { return }
                handle(RemoteProto.parse(data), on: channel)
            }
        } catch {
            guard isCurrent(gen) else { return }
            channel.close()
            self.channel = nil
            status = .disconnected
        }
    }

    private func handle(_ event: RemoteEvent, on channel: TLSChannel) {
        switch event {
        case .configure:
            let model = UIDevice.current.model
            channel.sendDetached(RemoteProto.configureReply(model: model, vendor: "Apple", appVersion: "1.0.0"))
            markConnected()
        case .setActive:
            channel.sendDetached(RemoteProto.setActive())
            markConnected()
        case .start:
            markConnected()
        case .ping(let value):
            channel.sendDetached(RemoteProto.pingResponse(value))
        case .volume(let level, let max, let muted):
            volume = VolumeState(level: level, max: max, muted: muted)
        case .imeShow(let counter):
            fieldCounter = counter
        case .imeBatch(let ime, let field):
            imeCounter = ime
            fieldCounter = field
        case .app(let package):
            if !package.isEmpty { currentApp = package }
        case .error, .other:
            break
        }
    }

    private func markConnected() {
        if status != .connected { status = .connected }
    }

    // MARK: Pairing

    func rePair() {
        guard !host.isEmpty else { return }
        let target = host
        tearDown()
        setPaired(target, false)
        let current = generation
        session = Task { [weak self] in
            guard let self else { return }
            do {
                let identity = try ClientIdentity.load()
                await self.startPairing(host: target, identity: identity, generation: current)
            } catch {
                if self.isCurrent(current) { self.status = .error(error.localizedDescription) }
            }
        }
    }

    private func startPairing(host: String, identity: SecIdentity, generation gen: Int) async {
        status = .connecting("Starting pairing…")
        let pairingChannel = TLSChannel(host: host, port: Self.pairingPort, identity: identity)
        do {
            try await pairingChannel.start()
            guard let serverCertificate = pairingChannel.peerCertificate else {
                throw ChannelError.message("The TV didn't identify itself.")
            }

            try await pairingChannel.send(PairingProto.request(clientName: "Remote (\(UIDevice.current.name))"))
            try await expect(.requestAck, from: pairingChannel)
            try await pairingChannel.send(PairingProto.option())
            try await expect(.option, from: pairingChannel)
            try await pairingChannel.send(PairingProto.configuration())
            try await expect(.configurationAck, from: pairingChannel)

            guard isCurrent(gen) else {
                pairingChannel.close()
                return
            }
            pairing = PairingSession(
                channel: pairingChannel,
                serverCertificate: serverCertificate,
                identity: identity,
                host: host
            )
            pinError = nil
            status = .needsPairing   // The TV is now showing its code.
        } catch {
            pairingChannel.close()
            guard isCurrent(gen) else { return }
            status = .error("Pairing couldn't start. \(error.localizedDescription)")
        }
    }

    private func expect(_ kind: PairingProto.Kind, from channel: TLSChannel) async throws {
        let reply = PairingProto.parse(try await channel.receiveMessage())
        guard reply.status == 200, reply.kind == kind else {
            throw ChannelError.message("The TV declined to pair (status \(reply.status)).")
        }
    }

    func submitPIN(_ pin: String) {
        guard let pairing else { return }
        let secret: Data
        do {
            secret = try ClientIdentity.pairingSecret(
                identity: pairing.identity,
                serverCertificate: pairing.serverCertificate,
                pin: pin
            )
        } catch {
            // A typo: keep the pairing session open so the code can be re-entered.
            pinError = error.localizedDescription
            return
        }

        pinError = nil
        status = .connecting("Verifying code…")
        let gen = generation
        session = Task { [weak self] in
            guard let self else { return }
            do {
                try await pairing.channel.send(PairingProto.secret(secret))
                let reply = PairingProto.parse(try await pairing.channel.receiveMessage())
                guard reply.kind == .secretAck else {
                    throw ChannelError.message("The TV rejected the code.")
                }
                guard self.isCurrent(gen) else { return }
                self.setPaired(pairing.host, true)
                pairing.channel.close()
                self.pairing = nil
                try? await Task.sleep(nanoseconds: 400_000_000)
                guard self.isCurrent(gen) else { return }
                self.connect(host: pairing.host)
            } catch {
                pairing.channel.close()
                guard self.isCurrent(gen) else { return }
                self.pairing = nil
                self.status = .error("Pairing failed. \(error.localizedDescription)")
            }
        }
    }

    func cancelPairing() {
        tearDown()
        status = .disconnected
    }

    // MARK: Paired hosts

    private func isPaired(_ host: String) -> Bool {
        (defaults.stringArray(forKey: "pairedHosts") ?? []).contains(host)
    }

    private func setPaired(_ host: String, _ paired: Bool) {
        var hosts = Set(defaults.stringArray(forKey: "pairedHosts") ?? [])
        if paired { hosts.insert(host) } else { hosts.remove(host) }
        defaults.set(Array(hosts), forKey: "pairedHosts")
    }

    // MARK: Commands

    func send(_ key: RemoteKey, _ direction: KeyDirection = .short) {
        guard status == .connected, let channel else { return }
        channel.sendDetached(RemoteProto.key(key, direction: direction))
    }

    func sendText(_ text: String) {
        guard status == .connected, let channel, !text.isEmpty else { return }
        channel.sendDetached(RemoteProto.text(text, imeCounter: imeCounter, fieldCounter: fieldCounter))
    }
}
