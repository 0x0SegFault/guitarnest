import CommonCrypto
import CryptoKit
import Foundation

/// Same file format as Android (app/data/BackupCrypto.kt), so backups move between platforms:
/// MAGIC | iterations (Int32, big-endian) | salt (16) | iv (12) | AES-256-GCM ciphertext + tag, MAGIC as authenticated data.
/// Key: PBKDF2-HMAC-SHA256.
enum BackupCrypto {
    static let magic = Data("GUITARNEST-BACKUP-1\n".utf8)
    static let iterations: UInt32 = 600_000

    enum Failure: Error { case notABackup, wrongPassword }

    static func encrypt(_ plain: Data, password: String, rounds: UInt32 = iterations) throws -> Data {
        let salt = random(16)
        let iv = random(12)
        let key = try derive(password, salt: salt, rounds: rounds)
        let box = try AES.GCM.seal(plain, using: key, nonce: AES.GCM.Nonce(data: iv), authenticating: magic)
        var out = magic
        withUnsafeBytes(of: rounds.bigEndian) { out.append(contentsOf: $0) }
        out.append(salt)
        out.append(iv)
        out.append(box.ciphertext)
        out.append(box.tag)
        return out
    }

    static func decrypt(_ blob: Data, password: String) throws -> Data {
        let header = magic.count + 4 + 16 + 12
        guard blob.count > header + 16, blob.prefix(magic.count) == magic else { throw Failure.notABackup }
        let bytes = [UInt8](blob)
        let rounds = bytes[magic.count..<magic.count + 4].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
        guard (10_000...10_000_000).contains(rounds) else { throw Failure.notABackup }
        let salt = Data(bytes[magic.count + 4..<magic.count + 20])
        let iv = Data(bytes[magic.count + 20..<header])
        let sealed = Data(bytes[header...])
        let key = try derive(password, salt: salt, rounds: rounds)
        do {
            let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: sealed.dropLast(16), tag: sealed.suffix(16))
            return try AES.GCM.open(box, using: key, authenticating: magic)
        } catch {
            throw Failure.wrongPassword
        }
    }

    private static func random(_ count: Int) -> Data {
        var data = Data(count: count)
        _ = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
        return data
    }

    private static func derive(_ password: String, salt: Data, rounds: UInt32) throws -> SymmetricKey {
        var key = Data(count: 32)
        let utf8 = Array(password.utf8).map { Int8(bitPattern: $0) }
        let status = key.withUnsafeMutableBytes { keyBytes in
            salt.withUnsafeBytes { saltBytes in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2), utf8, utf8.count,
                    saltBytes.bindMemory(to: UInt8.self).baseAddress, salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), rounds,
                    keyBytes.bindMemory(to: UInt8.self).baseAddress, 32
                )
            }
        }
        guard status == kCCSuccess else { throw Failure.notABackup }
        return SymmetricKey(data: key)
    }
}
