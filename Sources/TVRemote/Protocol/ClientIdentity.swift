import Foundation
import Security
import CryptoKit

enum IdentityError: LocalizedError {
    case keyGeneration(String)
    case certificate(String)
    case keychain(String, OSStatus)
    case unsupportedServerKey
    case invalidPIN
    case wrongPIN

    var errorDescription: String? {
        switch self {
        case .keyGeneration(let m): return "Couldn't create a pairing key (\(m))."
        case .certificate(let m): return "Couldn't create a pairing certificate (\(m))."
        case .keychain(let what, let status): return "Keychain error while \(what) (\(status))."
        case .unsupportedServerKey: return "The TV sent a certificate this app can't read."
        case .invalidPIN: return "Enter all 6 characters shown on your TV."
        case .wrongPIN: return "That code doesn't match. Check your TV and try again."
        }
    }
}

// MARK: - Minimal DER encoding / decoding

enum DER {
    static func length(_ n: Int) -> [UInt8] {
        if n < 0x80 { return [UInt8(n)] }
        var bytes: [UInt8] = []
        var v = n
        while v > 0 {
            bytes.insert(UInt8(v & 0xFF), at: 0)
            v >>= 8
        }
        return [0x80 | UInt8(bytes.count)] + bytes
    }

    static func wrap(_ tag: UInt8, _ content: [UInt8]) -> [UInt8] {
        [tag] + length(content.count) + content
    }

    static func sequence(_ parts: [UInt8]...) -> [UInt8] {
        wrap(0x30, parts.flatMap { $0 })
    }

    static func set(_ parts: [UInt8]...) -> [UInt8] {
        wrap(0x31, parts.flatMap { $0 })
    }

    static func integer(_ magnitude: [UInt8]) -> [UInt8] {
        var bytes = Array(magnitude.drop(while: { $0 == 0 }))
        if bytes.isEmpty { bytes = [0] }
        if bytes[0] & 0x80 != 0 { bytes.insert(0, at: 0) }
        return wrap(0x02, bytes)
    }

    static func bitString(_ content: [UInt8]) -> [UInt8] {
        wrap(0x03, [0] + content)
    }

    static func utf8String(_ s: String) -> [UInt8] {
        wrap(0x0C, Array(s.utf8))
    }

    static func utcTime(_ date: Date) -> [UInt8] {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyMMddHHmmss'Z'"
        return wrap(0x17, Array(formatter.string(from: date).utf8))
    }

    static let null: [UInt8] = [0x05, 0x00]
    static let oidSHA256WithRSA: [UInt8] = [0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0B]
    static let oidRSAEncryption: [UInt8] = [0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01]
    static let oidCommonName: [UInt8] = [0x06, 0x03, 0x55, 0x04, 0x03]
}

struct DERReader {
    private let bytes: [UInt8]
    private var pos = 0

    init(_ bytes: [UInt8]) {
        self.bytes = bytes
    }

    mutating func read(tag: UInt8) throws -> [UInt8] {
        guard pos < bytes.count, bytes[pos] == tag else { throw IdentityError.unsupportedServerKey }
        pos += 1
        guard pos < bytes.count else { throw IdentityError.unsupportedServerKey }

        var len = Int(bytes[pos])
        pos += 1
        if len & 0x80 != 0 {
            let count = len & 0x7F
            guard count > 0, count <= 4, pos + count <= bytes.count else { throw IdentityError.unsupportedServerKey }
            len = 0
            for _ in 0..<count {
                len = (len << 8) | Int(bytes[pos])
                pos += 1
            }
        }

        guard len >= 0, pos + len <= bytes.count else { throw IdentityError.unsupportedServerKey }
        let value = Array(bytes[pos..<(pos + len)])
        pos += len
        return value
    }
}

// MARK: - Client identity (self-signed RSA certificate kept in the Keychain)

enum ClientIdentity {
    private static let keyTag = Data("com.example.TVRemote.client-key".utf8)
    private static let certLabel = "com.example.TVRemote.client-cert"
    private static let commonName = "atvremote"

    /// Returns the saved identity, creating and storing a new one on first use.
    static func load() throws -> SecIdentity {
        if let existing = fetchIdentity() { return existing }
        try create()
        if let created = fetchIdentity() { return created }
        throw IdentityError.keychain("loading the new identity", errSecItemNotFound)
    }

    /// Forgets the identity. The TV will need to be paired again.
    static func reset() {
        SecItemDelete([
            kSecClass: kSecClassCertificate,
            kSecAttrLabel: certLabel,
        ] as CFDictionary)
        SecItemDelete([
            kSecClass: kSecClassKey,
            kSecAttrApplicationTag: keyTag,
        ] as CFDictionary)
    }

    private static func fetchIdentity() -> SecIdentity? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassIdentity,
            kSecAttrLabel: certLabel,
            kSecReturnRef: true,
            kSecMatchLimit: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let item else {
            return nil
        }
        return (item as! SecIdentity)
    }

    private static func create() throws {
        reset()

        let keyAttributes: [CFString: Any] = [
            kSecAttrKeyType: kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits: 2048,
            kSecPrivateKeyAttrs: [
                kSecAttrIsPermanent: true,
                kSecAttrApplicationTag: keyTag,
            ] as [CFString: Any],
        ]

        var error: Unmanaged<CFError>?
        guard let privateKey = SecKeyCreateRandomKey(keyAttributes as CFDictionary, &error) else {
            throw IdentityError.keyGeneration(describe(error))
        }
        guard let publicKey = SecKeyCopyPublicKey(privateKey),
              let publicData = SecKeyCopyExternalRepresentation(publicKey, &error) as Data? else {
            throw IdentityError.keyGeneration(describe(error))
        }

        let certificate = try buildCertificate(publicKeyPKCS1: [UInt8](publicData), signingKey: privateKey)

        let status = SecItemAdd([
            kSecClass: kSecClassCertificate,
            kSecValueRef: certificate,
            kSecAttrLabel: certLabel,
        ] as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw IdentityError.keychain("saving the certificate", status)
        }
    }

    private static func buildCertificate(publicKeyPKCS1: [UInt8], signingKey: SecKey) throws -> SecCertificate {
        var serial = (0..<8).map { _ in UInt8.random(in: 0...255) }
        serial[0] &= 0x7F
        serial[7] |= 0x01

        let name = DER.sequence(
            DER.set(
                DER.sequence(DER.oidCommonName, DER.utf8String(commonName))
            )
        )
        let signatureAlgorithm = DER.sequence(DER.oidSHA256WithRSA, DER.null)
        let now = Date()
        let validity = DER.sequence(
            DER.utcTime(now.addingTimeInterval(-24 * 3600)),
            DER.utcTime(now.addingTimeInterval(10 * 365 * 24 * 3600))
        )
        let subjectPublicKeyInfo = DER.sequence(
            DER.sequence(DER.oidRSAEncryption, DER.null),
            DER.bitString(publicKeyPKCS1)
        )

        let toBeSigned = DER.sequence(
            [0xA0, 0x03, 0x02, 0x01, 0x02],   // version: v3
            DER.integer(serial),
            signatureAlgorithm,
            name,
            validity,
            name,
            subjectPublicKeyInfo
        )

        var error: Unmanaged<CFError>?
        guard let signature = SecKeyCreateSignature(
            signingKey,
            .rsaSignatureMessagePKCS1v15SHA256,
            Data(toBeSigned) as CFData,
            &error
        ) as Data? else {
            throw IdentityError.certificate(describe(error))
        }

        let der = DER.sequence(toBeSigned, signatureAlgorithm, DER.bitString([UInt8](signature)))
        guard let certificate = SecCertificateCreateWithData(nil, Data(der) as CFData) else {
            throw IdentityError.certificate("the generated certificate was rejected")
        }
        return certificate
    }

    private static func describe(_ error: Unmanaged<CFError>?) -> String {
        guard let error else { return "unknown error" }
        return (error.takeRetainedValue() as Error).localizedDescription
    }

    // MARK: Pairing secret

    /// Modulus and exponent of an RSA public key as big-endian magnitudes.
    private static func rsaComponents(of key: SecKey) throws -> (modulus: [UInt8], exponent: [UInt8]) {
        var error: Unmanaged<CFError>?
        guard let data = SecKeyCopyExternalRepresentation(key, &error) as Data? else {
            throw IdentityError.unsupportedServerKey
        }
        var outer = DERReader([UInt8](data))
        var sequence = DERReader(try outer.read(tag: 0x30))
        let modulus = try sequence.read(tag: 0x02)
        let exponent = try sequence.read(tag: 0x02)
        return (stripLeadingZeros(modulus), stripLeadingZeros(exponent))
    }

    private static func stripLeadingZeros(_ bytes: [UInt8]) -> [UInt8] {
        Array(bytes.drop(while: { $0 == 0 }))
    }

    private static func hexBytes(_ hex: String) -> [UInt8]? {
        guard hex.count % 2 == 0 else { return nil }
        var out: [UInt8] = []
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            out.append(byte)
            index = next
        }
        return out
    }

    /// SHA-256(clientModulus ‖ clientExponent ‖ serverModulus ‖ serverExponent ‖ PIN[2...]).
    /// The first byte of the hash must equal the first byte of the PIN shown on the TV.
    static func pairingSecret(identity: SecIdentity, serverCertificate: SecCertificate, pin: String) throws -> Data {
        let code = pin.trimmingCharacters(in: .whitespaces).uppercased()
        guard code.count == 6,
              let prefix = UInt8(code.prefix(2), radix: 16),
              let suffix = hexBytes(String(code.dropFirst(2))) else {
            throw IdentityError.invalidPIN
        }

        var certificateRef: SecCertificate?
        SecIdentityCopyCertificate(identity, &certificateRef)
        guard let clientCertificate = certificateRef,
              let clientKey = SecCertificateCopyKey(clientCertificate),
              let serverKey = SecCertificateCopyKey(serverCertificate) else {
            throw IdentityError.unsupportedServerKey
        }

        let client = try rsaComponents(of: clientKey)
        let server = try rsaComponents(of: serverKey)

        var hasher = SHA256()
        hasher.update(data: client.modulus)
        hasher.update(data: client.exponent)
        hasher.update(data: server.modulus)
        hasher.update(data: server.exponent)
        hasher.update(data: suffix)
        let digest = Data(hasher.finalize())

        guard digest.first == prefix else { throw IdentityError.wrongPIN }
        return digest
    }
}
