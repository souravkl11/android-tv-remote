import Foundation
import Network
import Security

enum ChannelError: LocalizedError {
    case closed
    case malformed
    case message(String)

    var errorDescription: String? {
        switch self {
        case .closed: return "The TV closed the connection."
        case .malformed: return "The TV sent data this app couldn't read."
        case .message(let m): return m
        }
    }
}

/// Runs a closure at most once, from any thread.
final class Once: @unchecked Sendable {
    private let lock = NSLock()
    private var done = false

    func fire() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if done { return false }
        done = true
        return true
    }
}

/// Only touched on the channel's serial queue.
private final class WaitingError: @unchecked Sendable {
    var error: NWError?
}

private final class CertificateBox: @unchecked Sendable {
    private let lock = NSLock()
    private var certificate: SecCertificate?

    func set(_ value: SecCertificate) {
        lock.lock()
        certificate = value
        lock.unlock()
    }

    var value: SecCertificate? {
        lock.lock()
        defer { lock.unlock() }
        return certificate
    }
}

func friendlyDescription(of error: NWError) -> String {
    switch error {
    case .posix(let code):
        switch code {
        case .ECONNREFUSED: return "The TV refused the connection."
        case .ETIMEDOUT: return "The connection timed out."
        case .EHOSTUNREACH, .ENETUNREACH, .ENETDOWN: return "The TV can't be reached on this network."
        case .ECONNRESET, .ECONNABORTED: return "The TV dropped the connection."
        default: return "Network error (\(code.rawValue))."
        }
    case .dns(let code):
        if code == -65570 { return "Local network access is off for this app. Turn it on in Settings › Privacy & Security › Local Network." }
        return "Couldn't look up the TV (\(code))."
    case .tls(let status):
        return "Secure connection failed (\(status))."
    default:
        return "Network error."
    }
}

/// A TLS connection that presents our client certificate, accepts the TV's
/// self-signed certificate, and speaks length-delimited protobuf frames.
final class TLSChannel: @unchecked Sendable {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "tvremote.channel")
    private let certificateBox: CertificateBox
    private var buffer = Data()

    /// The TV's leaf certificate (available once `start()` has returned).
    var peerCertificate: SecCertificate? { certificateBox.value }

    init(host: String, port: UInt16, identity: SecIdentity) {
        let box = CertificateBox()
        certificateBox = box
        let queue = self.queue

        let tls = NWProtocolTLS.Options()
        let options = tls.securityProtocolOptions
        sec_protocol_options_set_min_tls_protocol_version(options, .TLSv12)
        sec_protocol_options_set_max_tls_protocol_version(options, .TLSv12)
        if let secIdentity = sec_identity_create(identity) {
            sec_protocol_options_set_local_identity(options, secIdentity)
        }
        sec_protocol_options_set_verify_block(options, { metadata, _, complete in
            var leaf: SecCertificate?
            sec_protocol_metadata_access_peer_certificate_chain(metadata) { secCertificate in
                if leaf == nil {
                    leaf = sec_certificate_copy_ref(secCertificate).takeRetainedValue()
                }
            }
            if let leaf { box.set(leaf) }
            complete(true)   // Android TVs use self-signed certificates.
        }, queue)

        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        tcp.enableKeepalive = true
        tcp.keepaliveIdle = 20

        let parameters = NWParameters(tls: tls, tcp: tcp)
        connection = NWConnection(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: port) ?? 6466,
            using: parameters
        )
    }

    func start(timeout: TimeInterval = 10) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let once = Once()
            let lastWaiting = WaitingError()
            let connection = self.connection

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if once.fire() { continuation.resume() }
                case .failed(let error):
                    if once.fire() {
                        continuation.resume(throwing: ChannelError.message(friendlyDescription(of: error)))
                    }
                    connection.cancel()
                case .waiting(let error):
                    lastWaiting.error = error
                    // Unreachable hosts won't recover by waiting; a pending Local Network
                    // permission prompt might, so everything else rides out the timeout.
                    if case .posix(let code) = error,
                       [.ECONNREFUSED, .EHOSTUNREACH, .ENETUNREACH, .ENETDOWN].contains(code) {
                        if once.fire() {
                            continuation.resume(throwing: ChannelError.message(friendlyDescription(of: error)))
                        }
                        connection.cancel()
                    }
                case .cancelled:
                    if once.fire() { continuation.resume(throwing: ChannelError.closed) }
                default:
                    break
                }
            }
            connection.start(queue: queue)

            queue.asyncAfter(deadline: .now() + timeout) {
                if once.fire() {
                    let message = lastWaiting.error.map(friendlyDescription(of:)) ?? "The TV didn't respond."
                    continuation.resume(throwing: ChannelError.message(message))
                    connection.cancel()
                }
            }
        }
    }

    func send(_ data: Data) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: data, completion: .contentProcessed { error in
                if let error {
                    continuation.resume(throwing: ChannelError.message(friendlyDescription(of: error)))
                } else {
                    continuation.resume()
                }
            })
        }
    }

    /// Fire-and-forget send, used for key presses.
    func sendDetached(_ data: Data) {
        connection.send(content: data, completion: .contentProcessed { _ in })
    }

    func receiveMessage() async throws -> Data {
        while true {
            if let message = try ProtoFraming.extractMessage(from: &buffer) {
                return message
            }
            buffer.append(try await receiveChunk())
        }
    }

    private func receiveChunk() async throws -> Data {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { data, _, isComplete, error in
                if let error {
                    continuation.resume(throwing: ChannelError.message(friendlyDescription(of: error)))
                } else if let data, !data.isEmpty {
                    continuation.resume(returning: data)
                } else if isComplete {
                    continuation.resume(throwing: ChannelError.closed)
                } else {
                    continuation.resume(returning: Data())
                }
            }
        }
    }

    func close() {
        connection.cancel()
    }
}
