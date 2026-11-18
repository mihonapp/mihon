package mihon.domain.protectedfolder.service

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {

    const val PIN_LENGTH = 4

    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val ITERATIONS = 120_000

    fun isValidPin(pin: String): Boolean {
        return pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
    }

    fun newSalt(): ByteArray {
        return ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
    }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        return MessageDigest.isEqual(hash(pin, salt), expectedHash)
    }
}
