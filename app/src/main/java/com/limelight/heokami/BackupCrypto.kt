package com.limelight.heokami

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong backup password or corrupted data")
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Password-based encryption for backup credentials: PBKDF2-HMAC-SHA256 -> AES-256-GCM.
 *
 * Blob layout: magic(2) | version(1) | iterations(4) | salt(16) | iv(12) | ciphertext+tag.
 * Salt and IV are random per backup; the header is bound to the ciphertext as AAD so it can't be
 * tampered with. PBKDF2 comes from BouncyCastle because the JCE variant needs API 26+.
 */
object BackupCrypto {
    private const val MAGIC_0: Byte = 0x4D // 'M'
    private const val MAGIC_1: Byte = 0x42 // 'B'
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 200_000
    private const val MAX_ITERATIONS = 5_000_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 3 + 4 + SALT_LEN + IV_LEN

    private val random = SecureRandom()

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val gen = PKCS5S2ParametersGenerator(SHA256Digest())
        gen.init(PKCS5S2ParametersGenerator.PKCS5PasswordToUTF8Bytes(password), salt, iterations)
        return (gen.generateDerivedParameters(256) as KeyParameter).key
    }

    fun encrypt(password: CharArray, plaintext: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(password.isNotEmpty()) { "password must not be empty" }
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC_0).put(MAGIC_1).put(VERSION)
            .putInt(iterations).put(salt).put(iv).array()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(password, salt, iterations), "AES"),
            GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    /** @throws InvalidBackupException if the blob is malformed, [WrongPasswordException] if the tag doesn't verify */
    fun decrypt(password: CharArray, blob: ByteArray): ByteArray {
        if (blob.size < HEADER_LEN + TAG_BITS / 8 || blob[0] != MAGIC_0 || blob[1] != MAGIC_1) {
            throw InvalidBackupException("Not an encrypted backup blob")
        }
        if (blob[2] != VERSION) {
            throw InvalidBackupException("Unsupported backup encryption version ${blob[2]}")
        }
        val buf = ByteBuffer.wrap(blob, 3, HEADER_LEN - 3)
        val iterations = buf.int
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw InvalidBackupException("Unreasonable KDF iteration count")
        }
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(password, salt, iterations), "AES"),
                GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(blob, 0, HEADER_LEN)
            return cipher.doFinal(blob, HEADER_LEN, blob.size - HEADER_LEN)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        }
    }
}
