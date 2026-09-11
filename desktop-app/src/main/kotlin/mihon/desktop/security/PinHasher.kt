package mihon.desktop.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PIN hashing for the desktop app lock.
 *
 * Only the Base64 encoded salt, hash and iteration count are ever persisted. The raw PIN is
 * converted to a mutable char array, passed to PBKDF2WithHmacSHA256 and cleared again.
 */
object PinHasher {

    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val DEFAULT_ITERATIONS = 210_000
    const val SALT_LENGTH_BYTES = 16
    const val HASH_LENGTH_BITS = 256
    const val HASH_LENGTH_BYTES = HASH_LENGTH_BITS / 8

    private const val MIN_SALT_LENGTH_BYTES = 8
    private const val MAX_ITERATIONS = 5_000_000

    fun generateSalt(random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)
        return salt
    }

    fun generateSaltBase64(random: SecureRandom = SecureRandom()): String {
        return Base64.getEncoder().encodeToString(generateSalt(random))
    }

    fun hash(
        pin: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        require(iterations in 1..MAX_ITERATIONS) { "iterations must be between 1 and $MAX_ITERATIONS" }
        require(salt.size >= MIN_SALT_LENGTH_BYTES) { "salt must be at least $MIN_SALT_LENGTH_BYTES bytes" }

        val pinChars = pin.toCharArray()
        val spec = PBEKeySpec(pinChars, salt, iterations, HASH_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            pinChars.fill('\u0000')
        }
    }

    fun hashBase64(
        pin: String,
        saltBase64: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String? {
        val salt = decodeOrNull(saltBase64) ?: return null
        if (!isSupportedSalt(salt) || iterations !in 1..MAX_ITERATIONS) return null
        return try {
            Base64.getEncoder().encodeToString(hash(pin, salt, iterations))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Constant-time verification of a PIN against the persisted Base64 hash/salt/iterations.
     */
    fun verify(
        pin: String,
        hashBase64: String,
        saltBase64: String,
        iterations: Int,
    ): Boolean {
        val expectedHash = decodeOrNull(hashBase64) ?: return false
        val salt = decodeOrNull(saltBase64) ?: return false
        if (expectedHash.size != HASH_LENGTH_BYTES || !isSupportedSalt(salt)) return false
        if (iterations !in 1..MAX_ITERATIONS) return false

        val actualHash = try {
            hash(pin, salt, iterations)
        } catch (_: Exception) {
            return false
        }
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    fun isValidCredential(hashBase64: String, saltBase64: String, iterations: Int): Boolean {
        val hash = decodeOrNull(hashBase64) ?: return false
        val salt = decodeOrNull(saltBase64) ?: return false
        return hash.size == HASH_LENGTH_BYTES && isSupportedSalt(salt) && iterations in 1..MAX_ITERATIONS
    }

    private fun isSupportedSalt(salt: ByteArray): Boolean = salt.size >= MIN_SALT_LENGTH_BYTES

    private fun decodeOrNull(value: String): ByteArray? {
        if (value.isBlank()) return null
        return try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
