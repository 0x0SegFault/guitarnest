package app.guitarnest.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    @Test
    fun roundTripAndWrongPassword() {
        val plain = "songs".toByteArray()
        val blob = BackupCrypto.encrypt(plain, "correct horse".toCharArray(), iterations = 10_000)
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, "correct horse".toCharArray()))
        assertThrows(WrongPasswordException::class.java) { BackupCrypto.decrypt(blob, "wrong".toCharArray()) }
        assertThrows(NotABackupException::class.java) { BackupCrypto.decrypt("hello".toByteArray(), "x".toCharArray()) }
    }
}
