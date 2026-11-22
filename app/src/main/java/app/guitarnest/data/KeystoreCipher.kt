package app.guitarnest.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12

/**
 * Protects the AI API key: AES-256-GCM with a non-exportable key held by the Android Keystore
 * (hardware-backed / StrongBox when the device has it).
 */
class KeystoreCipher(private val alias: String = "guitarnest_ai_key") {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + iv.size + sealed.size)
            .put(FORMAT_VERSION).put(iv).put(sealed).array()
    }

    fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size < 1 + GCM_IV_BYTES || blob[0] != FORMAT_VERSION) {
            throw GeneralSecurityException("Unknown data format")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = blob.copyOfRange(1, 1 + GCM_IV_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(blob, 1 + GCM_IV_BYTES, blob.size - 1 - GCM_IV_BYTES)
    }

    fun deleteKey() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun existingKey(): SecretKey =
        keyStore.getKey(alias, null) as? SecretKey
            ?: throw GeneralSecurityException("Encryption key is missing")

    private fun key(): SecretKey = (keyStore.getKey(alias, null) as? SecretKey) ?: generate()

    private fun generate(): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generate(strongBox = true)
            } catch (_: Exception) {
                // No StrongBox on this device; fall back to the TEE-backed keystore.
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val FORMAT_VERSION: Byte = 1
    }
}

