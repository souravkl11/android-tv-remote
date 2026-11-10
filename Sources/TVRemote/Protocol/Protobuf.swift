import Foundation

// A tiny protobuf codec. The Android TV Remote v2 protocol only needs
// varints and length-delimited fields, so a full protobuf runtime is overkill.

struct ProtoWriter {
    private(set) var data = Data()

    mutating func writeVarint(_ value: UInt64) {
        var v = value
        while v >= 0x80 {
            data.append(UInt8(truncatingIfNeeded: v) | 0x80)
            v >>= 7
        }
        data.append(UInt8(truncatingIfNeeded: v))
    }

    private mutating func writeTag(_ field: Int, wireType: UInt64) {
        writeVarint((UInt64(field) << 3) | wireType)
    }

    mutating func int(_ field: Int, _ value: Int) {
        writeTag(field, wireType: 0)
        writeVarint(UInt64(bitPattern: Int64(value)))
    }

    mutating func bool(_ field: Int, _ value: Bool) {
        int(field, value ? 1 : 0)
    }

    mutating func bytes(_ field: Int, _ value: Data) {
        writeTag(field, wireType: 2)
        writeVarint(UInt64(value.count))
        data.append(value)
    }

    mutating func string(_ field: Int, _ value: String) {
        bytes(field, Data(value.utf8))
    }

    mutating func message(_ field: Int, _ build: (inout ProtoWriter) -> Void) {
        var inner = ProtoWriter()
        build(&inner)
        bytes(field, inner.data)
    }

    /// The message prefixed with its varint length (protobuf "delimited" framing).
    var lengthDelimited: Data {
        var prefix = ProtoWriter()
        prefix.writeVarint(UInt64(data.count))
        return prefix.data + data
    }
}

struct ProtoField {
    enum Value {
        case varint(UInt64)
        case bytes(Data)
    }

    let number: Int
    let value: Value

    var int: Int {
        if case .varint(let v) = value { return Int(truncatingIfNeeded: Int64(bitPattern: v)) }
        return 0
    }

    var bool: Bool { int != 0 }

    var data: Data {
        if case .bytes(let d) = value { return d }
        return Data()
    }

    var string: String { String(decoding: data, as: UTF8.self) }

    var message: ProtoMessage { ProtoMessage(data) }
}

struct ProtoMessage {
    let fields: [ProtoField]

    init(_ data: Data) {
        let bytes = [UInt8](data)
        var pos = 0
        var parsed: [ProtoField] = []

        func varint() -> UInt64? {
            var result: UInt64 = 0
            var shift: UInt64 = 0
            while pos < bytes.count, shift < 64 {
                let byte = bytes[pos]
                pos += 1
                result |= UInt64(byte & 0x7F) << shift
                if byte & 0x80 == 0 { return result }
                shift += 7
            }
            return nil
        }

        while pos < bytes.count {
            guard let key = varint() else { break }
            let number = Int(key >> 3)
            switch key & 7 {
            case 0:
                guard let v = varint() else { fields = parsed; return }
                parsed.append(ProtoField(number: number, value: .varint(v)))
            case 1:
                guard pos + 8 <= bytes.count else { fields = parsed; return }
                var v: UInt64 = 0
                for i in 0..<8 { v |= UInt64(bytes[pos + i]) << UInt64(8 * i) }
                pos += 8
                parsed.append(ProtoField(number: number, value: .varint(v)))
            case 2:
                guard let len = varint(), len <= UInt64(bytes.count - pos) else { fields = parsed; return }
                let end = pos + Int(len)
                parsed.append(ProtoField(number: number, value: .bytes(Data(bytes[pos..<end]))))
                pos = end
            case 5:
                guard pos + 4 <= bytes.count else { fields = parsed; return }
                var v: UInt64 = 0
                for i in 0..<4 { v |= UInt64(bytes[pos + i]) << UInt64(8 * i) }
                pos += 4
                parsed.append(ProtoField(number: number, value: .varint(v)))
            default:
                fields = parsed
                return
            }
        }
        fields = parsed
    }

    func first(_ number: Int) -> ProtoField? {
        fields.first { $0.number == number }
    }

    func has(_ number: Int) -> Bool {
        first(number) != nil
    }
}

enum ProtoFraming {
    /// Removes and returns one length-delimited message from `buffer`, or nil if
    /// the buffer does not contain a complete message yet.
    static func extractMessage(from buffer: inout Data) throws -> Data? {
        var length: UInt64 = 0
        var shift: UInt64 = 0
        var index = buffer.startIndex

        while true {
            guard index < buffer.endIndex else { return nil }
            let byte = buffer[index]
            index = buffer.index(after: index)
            length |= UInt64(byte & 0x7F) << shift
            if byte & 0x80 == 0 { break }
            shift += 7
            if shift > 35 { throw ChannelError.malformed }
        }

        guard length <= 4_000_000 else { throw ChannelError.malformed }
        let end = index + Int(length)
        guard end <= buffer.endIndex else { return nil }

        let message = Data(buffer[index..<end])
        buffer = Data(buffer[end...])
        return message
    }
}
