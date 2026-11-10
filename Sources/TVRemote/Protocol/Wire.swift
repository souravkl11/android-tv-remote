import Foundation

// Message builders and parsers for the two protocols spoken by Android TV:
// pairing (port 6467) and remote control (port 6466). Field numbers come from
// Google's open `pairing.proto` / `remotemessage.proto` definitions.

enum PairingProto {
    enum Kind {
        case requestAck, option, configurationAck, secretAck, other
    }

    struct Reply {
        let status: Int
        let kind: Kind
    }

    private static let statusOK = 200
    private static let encodingHexadecimal = 3
    private static let roleInput = 1

    private static func envelope(_ build: (inout ProtoWriter) -> Void) -> Data {
        var w = ProtoWriter()
        w.int(1, 2)          // protocol_version
        w.int(2, statusOK)   // status
        build(&w)
        return w.lengthDelimited
    }

    static func request(clientName: String) -> Data {
        envelope { w in
            w.message(10) { m in
                m.string(1, "atvremote")   // service_name
                m.string(2, clientName)    // client_name
            }
        }
    }

    static func option() -> Data {
        envelope { w in
            w.message(20) { o in
                o.message(1) { e in
                    e.int(1, encodingHexadecimal)
                    e.int(2, 6)
                }
                o.int(3, roleInput)
            }
        }
    }

    static func configuration() -> Data {
        envelope { w in
            w.message(30) { c in
                c.message(1) { e in
                    e.int(1, encodingHexadecimal)
                    e.int(2, 6)
                }
                c.int(2, roleInput)
            }
        }
    }

    static func secret(_ secret: Data) -> Data {
        envelope { w in
            w.message(40) { s in
                s.bytes(1, secret)
            }
        }
    }

    static func parse(_ data: Data) -> Reply {
        let msg = ProtoMessage(data)
        let status = msg.first(2)?.int ?? 0
        let kind: Kind
        if msg.has(11) {
            kind = .requestAck
        } else if msg.has(20) {
            kind = .option
        } else if msg.has(31) {
            kind = .configurationAck
        } else if msg.has(41) {
            kind = .secretAck
        } else {
            kind = .other
        }
        return Reply(status: status, kind: kind)
    }
}

enum RemoteEvent {
    case configure
    case setActive
    case start
    case ping(Int)
    case volume(level: Int, max: Int, muted: Bool)
    case imeShow(fieldCounter: Int)
    case imeBatch(imeCounter: Int, fieldCounter: Int)
    case app(package: String)
    case error
    case other
}

enum RemoteProto {
    private static let clientCode = 622

    static func configureReply(model: String, vendor: String, appVersion: String) -> Data {
        var w = ProtoWriter()
        w.message(1) { c in
            c.int(1, clientCode)
            c.message(2) { d in
                d.string(1, model)
                d.string(2, vendor)
                d.int(3, 1)
                d.string(4, "1")
                d.string(5, "atvremote")
                d.string(6, appVersion)
            }
        }
        return w.lengthDelimited
    }

    static func setActive() -> Data {
        var w = ProtoWriter()
        w.message(2) { a in a.int(1, clientCode) }
        return w.lengthDelimited
    }

    static func pingResponse(_ value: Int) -> Data {
        var w = ProtoWriter()
        w.message(9) { p in p.int(1, value) }
        return w.lengthDelimited
    }

    static func key(_ key: RemoteKey, direction: KeyDirection) -> Data {
        var w = ProtoWriter()
        w.message(10) { k in
            k.int(1, key.rawValue)
            k.int(2, direction.rawValue)
        }
        return w.lengthDelimited
    }

    static func text(_ text: String, imeCounter: Int, fieldCounter: Int) -> Data {
        var w = ProtoWriter()
        w.message(21) { batch in
            batch.int(1, imeCounter)
            batch.int(2, fieldCounter)
            batch.message(3) { edit in
                edit.int(1, 0)
                edit.message(2) { field in
                    field.int(1, text.count)
                    field.int(2, text.count)
                    field.string(3, text)
                }
            }
        }
        return w.lengthDelimited
    }

    static func parse(_ data: Data) -> RemoteEvent {
        let msg = ProtoMessage(data)

        if msg.has(1) { return .configure }
        if msg.has(2) { return .setActive }
        if msg.has(3) { return .error }
        if let ping = msg.first(8) {
            return .ping(ping.message.first(1)?.int ?? 0)
        }
        if msg.has(40) { return .start }
        if let vol = msg.first(50)?.message {
            return .volume(
                level: vol.first(7)?.int ?? 0,
                max: vol.first(6)?.int ?? 100,
                muted: vol.first(8)?.bool ?? false
            )
        }
        if let show = msg.first(22)?.message {
            let counter = show.first(2)?.message.first(1)?.int ?? 0
            return .imeShow(fieldCounter: counter)
        }
        if let batch = msg.first(21)?.message {
            return .imeBatch(
                imeCounter: batch.first(1)?.int ?? 0,
                fieldCounter: batch.first(2)?.int ?? 0
            )
        }
        if let inject = msg.first(20)?.message {
            let package = inject.first(1)?.message.first(12)?.string ?? ""
            return .app(package: package)
        }
        return .other
    }
}
