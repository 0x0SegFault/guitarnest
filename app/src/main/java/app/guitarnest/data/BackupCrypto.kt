package app.guitarnest.data

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val TAG_BITS = 128
private const val IV_BYTES = 12

class WrongPasswordException : Exception()
class NotABackupException : Exception()

/**
 * Password-protected backup files, portable between devices (Android and iOS use the same format):
 * MAGIC | iterations (int) | salt (16) | iv (12) | AES-256-GCM(ciphertext + tag).
 * The key is derived with PBKDF2-HMAC-SHA256.
 */
object BackupCrypto {
    private val MAGIC = "GUITARNEST-BACKUP-1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    const val DEFAULT_ITERATIONS = 600_000

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + 4 + SALT_BYTES + IV_BYTES + sealed.size)
            .put(MAGIC).putInt(iterations).put(salt).put(iv).put(sealed).array()
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        val header = MAGIC.size + 4 + SALT_BYTES + IV_BYTES
        if (blob.size <= header || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw NotABackupException()
        }
        val buf = ByteBuffer.wrap(blob, MAGIC.size, blob.size - MAGIC.size)
        val iterations = buf.int
        if (iterations !in 10_000..10_000_000) throw NotABackupException()
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val iv = ByteArray(IV_BYTES).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(blob, header, blob.size - header)
        } catch (_: GeneralSecurityException) {
            throw WrongPasswordException()
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES").also { raw.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
