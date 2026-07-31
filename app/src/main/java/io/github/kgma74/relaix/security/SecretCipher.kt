package io.github.kgma74.relaix.security

/**
 * Encrypts and decrypts small secrets before they touch disk.
 *
 * An interface rather than a concrete AndroidKeyStore call because the key
 * store does not exist in a JVM unit test: everything that merely *stores* a
 * secret can then be tested with a fake, and only the real crypto needs a
 * device.
 */
interface SecretCipher {
    /** Returns an opaque, self-contained blob safe to persist as text. */
    fun encrypt(plaintext: String): String

    /** Reverses [encrypt]; throws if the blob was tampered with or the key is gone. */
    fun decrypt(blob: String): String
}
