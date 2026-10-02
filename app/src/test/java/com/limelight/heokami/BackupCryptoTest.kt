package com.limelight.heokami

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val secret = "client-key-material".toByteArray()
    // Low iteration count keeps the tests fast; the format carries it in the header.
    private val fast = 1000

    @Test
    fun roundTripWithCorrectPassword() {
        val blob = BackupCrypto.encrypt("hunter2".toCharArray(), secret, fast)
        assertArrayEquals(secret, BackupCrypto.decrypt("hunter2".toCharArray(), blob))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val blob = BackupCrypto.encrypt("hunter2".toCharArray(), secret, fast)
        try {
            BackupCrypto.decrypt("hunter3".toCharArray(), blob)
            fail("expected WrongPasswordException")
        } catch (expected: WrongPasswordException) {
        }
    }

    @Test
    fun tamperedCiphertextOrHeaderIsRejected() {
        val blob = BackupCrypto.encrypt("pw".toCharArray(), secret, fast)
        val badBody = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val badSalt = blob.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        for (bad in listOf(badBody, badSalt)) {
            try {
                BackupCrypto.decrypt("pw".toCharArray(), bad)
                fail("expected WrongPasswordException")
            } catch (expected: WrongPasswordException) {
            }
        }
    }

    @Test
    fun saltAndIvAreRandomPerBackup() {
        val a = BackupCrypto.encrypt("pw".toCharArray(), secret, fast)
        val b = BackupCrypto.encrypt("pw".toCharArray(), secret, fast)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun plaintextDoesNotAppearInBlob() {
        val blob = BackupCrypto.encrypt("pw".toCharArray(), secret, fast)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("client-key-material"))
    }

    @Test
    fun malformedBlobsAreInvalidNotWrongPassword() {
        for (bad in listOf(ByteArray(0), ByteArray(64), "garbage".toByteArray())) {
            try {
                BackupCrypto.decrypt("pw".toCharArray(), bad)
                fail("expected InvalidBackupException")
            } catch (expected: InvalidBackupException) {
            }
        }
    }

    @Test
    fun absurdIterationCountIsRefused() {
        val blob = BackupCrypto.encrypt("pw".toCharArray(), secret, fast)
        // bytes 3..6 are the big-endian iteration count
        blob[3] = 0x7F
        try {
            BackupCrypto.decrypt("pw".toCharArray(), blob)
            fail("expected InvalidBackupException")
        } catch (expected: InvalidBackupException) {
        }
    }

    @Test
    fun emptyPasswordCannotEncrypt() {
        try {
            BackupCrypto.encrypt(CharArray(0), secret, fast)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(true)
    }
}
