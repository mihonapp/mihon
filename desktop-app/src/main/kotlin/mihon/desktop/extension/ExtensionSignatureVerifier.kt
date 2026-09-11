package mihon.desktop.extension

import mihon.extension.validator.ExtensionValidationException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile

/**
 * Thrown when an extension package has a malformed, invalid, or unsupported signature.
 *
 * APK/JAR packages using only APK Signature Scheme v2/v3 (no v1 JAR signature) are rejected with
 * an explicit message because the desktop installer currently has no v2/v3 signature verifier.
 */
open class ExtensionSignatureException(message: String, cause: Throwable? = null) :
    ExtensionValidationException(message, cause)

/** Thrown when a package signature is valid but the signer fingerprint is not trusted yet. */
open class ExtensionTrustRequiredException(
    val pkg: String,
    val fingerprints: List<String>,
    val reason: String = "Extension signature is not trusted",
) : ExtensionValidationException(
    "$reason: $pkg (signer fingerprints: ${fingerprints.ifEmpty { listOf("UNSIGNED") }.joinToString()}). " +
        "Explicitly trust this package to install it.",
)

/** Thrown when a package signer does not match a known key or a previously trusted fingerprint. */
open class ExtensionSignatureMismatchException(
    val pkg: String,
    val expectedFingerprints: Set<String>,
    val actualFingerprints: List<String>,
) : ExtensionValidationException(
    "Extension signature mismatch for '$pkg': expected one of " +
        "${expectedFingerprints.ifEmpty { setOf("<unknown>") }.joinToString()}, but package was signed by " +
        actualFingerprints.ifEmpty { listOf("<unsigned>") }.joinToString(),
)

/** Alias used by callers that model a validly signed but not-yet-trusted package. */
typealias ExtensionUntrustedException = ExtensionSignatureMismatchException

/** Broad alias for any trust-related install rejection. */
typealias ExtensionTrustException = ExtensionValidationException

/** Alias for callers that name the malformed-signature failure after verification. */
typealias ExtensionSignatureVerificationException = ExtensionSignatureException

/** Alias for the signature verification result. */
typealias SignatureVerificationResult = ExtensionSignatureVerification

/** Alias for the pluggable signature verifier contract. */
typealias SignatureVerifier = ExtensionVerifier

/**
 * Result of inspecting an extension package's v1 JAR/APK signature.
 *
 * [valid] describes whether the package could be parsed/verified. An unsigned package is valid
 * (it simply has no signer identity), whereas a package with broken signatures is invalid.
 */
data class ExtensionSignatureVerification(
    val fingerprints: List<String> = emptyList(),
    val hasV1Signature: Boolean = false,
    val hasApkSigningBlock: Boolean = false,
    val valid: Boolean = true,
    val error: String? = null,
) {
    val signed: Boolean get() = fingerprints.isNotEmpty()
    val isSigned: Boolean get() = signed
    val isValid: Boolean get() = valid
    val signerFingerprints: List<String> get() = fingerprints
    val signatureFingerprints: List<String> get() = fingerprints
    val sha256Fingerprints: List<String> get() = fingerprints
    val certificateFingerprints: List<String> get() = fingerprints
    val primaryFingerprint: String? get() = fingerprints.lastOrNull()
    val sha256: String? get() = primaryFingerprint
    val errorMessage: String? get() = error
    val failureReason: String? get() = error

    /** True when the APK carries an APK Signing Block but no verifiable v1 JAR signature. */
    val v2V3Only: Boolean get() = hasApkSigningBlock && fingerprints.isEmpty() && !hasV1Signature
    val isV2V3Only: Boolean get() = v2V3Only

    fun requireValid() {
        if (!valid || v2V3Only) {
            throw ExtensionSignatureException(error ?: "Extension signature verification failed")
        }
    }
}

/** Pluggable verifier contract. Tests may inject a fake verifier. */
fun interface ExtensionVerifier {
    fun verify(file: File): ExtensionSignatureVerification
}

/**
 * Extracts and verifies v1 JAR/APK signer certificate SHA-256 fingerprints.
 *
 * Verification is performed by [JarFile] with verification enabled: every non-directory entry is
 * read fully, which triggers the JDK's JAR signature validation. Certificates returned by
 * [java.util.jar.JarEntry.getCodeSigners] are then hashed with SHA-256.
 */
open class ExtensionSignatureVerifier : ExtensionVerifier {

    override fun verify(file: File): ExtensionSignatureVerification {
        if (!file.exists() || !file.isFile) {
            return invalid("Extension package file does not exist: ${file.absolutePath}")
        }

        val hasSigningBlock = try {
            hasApkSigningBlock(file)
        } catch (_: Exception) {
            false
        }

        val fingerprints = LinkedHashSet<String>()
        var hasV1SignatureFiles = false

        try {
            JarFile(file, true).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (isV1SignatureFile(entry.name)) {
                        hasV1SignatureFiles = true
                    }
                    if (entry.isDirectory) continue

                    try {
                        jar.getInputStream(entry).use { input -> drain(input) }
                    } catch (e: SecurityException) {
                        return invalid(
                            "JAR signature verification failed for entry '${entry.name}': " +
                                (e.message ?: "signature digest mismatch"),
                            hasSigningBlock,
                            hasV1SignatureFiles,
                        )
                    }

                    val codeSigners = entry.codeSigners
                    if (codeSigners != null) {
                        for (signer in codeSigners) {
                            // The signer certificate is the first certificate in the path; the rest
                            // are the CA chain and are not part of the package signer identity.
                            val x509 = signer.signerCertPath.certificates.firstOrNull() as? X509Certificate
                            if (x509 != null) {
                                fingerprints += sha256Hex(x509.encoded)
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            return invalid(
                "JAR signature verification failed: ${e.message ?: "invalid signature"}",
                hasSigningBlock,
                hasV1SignatureFiles,
            )
        } catch (e: Exception) {
            if (hasSigningBlock) {
                return invalid(
                    "APK carries an APK Signing Block (Signature Scheme v2/v3) but its ZIP/JAR structure " +
                        "could not be verified: ${e.message ?: e::class.simpleName}. The desktop installer can " +
                        "only verify v1 JAR signatures; this package was not trusted.",
                    hasSigningBlock = true,
                    hasV1SignatureFiles = hasV1SignatureFiles,
                )
            }
            return invalid(
                "Extension package is not a valid signed JAR/ZIP archive: ${e.message ?: e::class.simpleName}",
                hasSigningBlock,
                hasV1SignatureFiles,
            )
        }

        if (fingerprints.isEmpty()) {
            if (hasSigningBlock) {
                return invalid(
                    "APK is signed with APK Signature Scheme v2/v3 only. The desktop installer can only " +
                        "verify v1 JAR signatures; this package cannot be verified and was not trusted.",
                    hasSigningBlock = true,
                    hasV1SignatureFiles = hasV1SignatureFiles,
                )
            }
            if (hasV1SignatureFiles) {
                return invalid(
                    "Package contains JAR signature files but no valid signer certificates. " +
                        "The v1 signature could not be verified.",
                    hasSigningBlock = false,
                    hasV1SignatureFiles = true,
                )
            }
            return ExtensionSignatureVerification(
                fingerprints = emptyList(),
                hasV1Signature = false,
                hasApkSigningBlock = false,
                valid = true,
            )
        }

        return ExtensionSignatureVerification(
            fingerprints = fingerprints.toList(),
            hasV1Signature = hasV1SignatureFiles,
            hasApkSigningBlock = hasSigningBlock,
            valid = true,
        )
    }

    /** Returns signer fingerprints, throwing for invalid or v2/v3-only packages. */
    fun extractSignerFingerprints(file: File): List<String> = verifyOrThrow(file).fingerprints

    fun extractFingerprints(file: File): List<String> = extractSignerFingerprints(file)

    fun extract(file: File): List<String> = extractSignerFingerprints(file)

    fun getSignerFingerprints(file: File): List<String> = extractSignerFingerprints(file)

    fun verifySignatures(file: File): ExtensionSignatureVerification = verify(file)

    fun fingerprint(file: File): String? = extractSignerFingerprints(file).lastOrNull()

    fun signerFingerprint(file: File): String? = fingerprint(file)

    fun verifyOrThrow(file: File): ExtensionSignatureVerification {
        val result = verify(file)
        if (!result.valid || result.v2V3Only) {
            throw ExtensionSignatureException(result.error ?: "Extension signature verification failed")
        }
        return result
    }

    fun requireValid(file: File): ExtensionSignatureVerification = verifyOrThrow(file)

    private fun invalid(
        message: String,
        hasSigningBlock: Boolean = false,
        hasV1SignatureFiles: Boolean = false,
    ): ExtensionSignatureVerification = ExtensionSignatureVerification(
        fingerprints = emptyList(),
        hasV1Signature = hasV1SignatureFiles,
        hasApkSigningBlock = hasSigningBlock,
        valid = false,
        error = message,
    )

    companion object : ExtensionVerifier {
        private const val APK_SIG_BLOCK_MAGIC = "APK Sig Block 42"
        private const val EOCD_SIGNATURE = 0x06054b50
        private const val EOCD_MIN_SIZE = 22
        private const val MAX_ZIP_COMMENT = 0xFFFF

        override fun verify(file: File): ExtensionSignatureVerification = ExtensionSignatureVerifier().verify(file)

        fun extractSignerFingerprints(file: File): List<String> =
            ExtensionSignatureVerifier().extractSignerFingerprints(file)

        fun extractFingerprints(file: File): List<String> =
            ExtensionSignatureVerifier().extractFingerprints(file)

        fun extract(file: File): List<String> = ExtensionSignatureVerifier().extract(file)

        fun verifyOrThrow(file: File): ExtensionSignatureVerification =
            ExtensionSignatureVerifier().verifyOrThrow(file)

        fun verifySignatures(file: File): ExtensionSignatureVerification =
            ExtensionSignatureVerifier().verifySignatures(file)

        fun fingerprint(file: File): String? = ExtensionSignatureVerifier().fingerprint(file)

        fun isV1SignatureFile(name: String): Boolean {
            if (!name.startsWith("META-INF/", ignoreCase = true)) return false
            val upper = name.uppercase()
            return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") ||
                upper.endsWith(".EC")
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val builder = StringBuilder(digest.size * 2)
            for (byte in digest) {
                builder.append(Character.forDigit((byte.toInt() shr 4) and 0x0F, 16))
                builder.append(Character.forDigit(byte.toInt() and 0x0F, 16))
            }
            return builder.toString()
        }

        private fun drain(input: InputStream) {
            val buffer = ByteArray(8192)
            while (input.read(buffer) != -1) {
                // Reading the entry is what triggers JAR signature verification.
            }
        }

        /**
         * Detects the APK Signing Block v2/v3 magic. The canonical location is the 16 bytes
         * immediately before the central directory; a tail scan is used as a fallback for ZIP64
         * archives where the EOCD central directory offset is not a direct byte offset.
         */
        private fun hasApkSigningBlock(file: File): Boolean {
            if (file.length() < (EOCD_MIN_SIZE + APK_SIG_BLOCK_MAGIC.length).toLong()) return false
            val magicBytes = APK_SIG_BLOCK_MAGIC.toByteArray(Charsets.US_ASCII)

            RandomAccessFile(file, "r").use { raf ->
                val tailLength = minOf(
                    file.length(),
                    (EOCD_MIN_SIZE + MAX_ZIP_COMMENT + 64).toLong(),
                ).toInt()
                val tail = ByteArray(tailLength)
                raf.seek(file.length() - tailLength)
                raf.readFully(tail)

                val eocdOffset = findEndOfCentralDirectory(tail)
                if (eocdOffset >= 0) {
                    val centralDirectoryOffset = findCentralDirectoryOffset(
                        raf = raf,
                        tail = tail,
                        eocdOffset = eocdOffset,
                        fileLength = file.length(),
                    )
                    if (centralDirectoryOffset != null &&
                        centralDirectoryOffset >= magicBytes.size.toLong() &&
                        centralDirectoryOffset <= file.length()
                    ) {
                        val magicOffset = centralDirectoryOffset - magicBytes.size
                        val magic = ByteArray(magicBytes.size)
                        raf.seek(magicOffset)
                        raf.readFully(magic)
                        if (magic.contentEquals(magicBytes)) return true
                    }
                }

                return indexOf(tail, magicBytes) >= 0
            }
        }

        /**
         * Resolves the central directory offset, including the ZIP64 EOCD path where the standard
         * EOCD stores 0xFFFFFFFF and a ZIP64 EOCD locator/record is present.
         */
        private fun findCentralDirectoryOffset(
            raf: RandomAccessFile,
            tail: ByteArray,
            eocdOffset: Int,
            fileLength: Long,
        ): Long? {
            val standardOffset = readIntLe(tail, eocdOffset + 16).toLong() and 0xFFFFFFFFL
            if (standardOffset != 0xFFFFFFFFL && standardOffset <= fileLength) {
                return standardOffset
            }

            // ZIP64 end of central directory locator sits 20 bytes before the standard EOCD.
            val locatorOffset = eocdOffset - 20
            if (locatorOffset >= 0 && readIntLe(tail, locatorOffset) == 0x07064b50) {
                val zip64EocdOffset = readLongLe(tail, locatorOffset + 8)
                if (zip64EocdOffset >= 0 && zip64EocdOffset + 56 <= fileLength) {
                    val zip64Record = ByteArray(56)
                    raf.seek(zip64EocdOffset)
                    raf.readFully(zip64Record)
                    if (readIntLe(zip64Record, 0) == 0x06064b50) {
                        return readLongLe(zip64Record, 48)
                    }
                }
            }
            return null
        }

        private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
            if (bytes.size < EOCD_MIN_SIZE) return -1
            for (index in bytes.size - EOCD_MIN_SIZE downTo 0) {
                val signature = (bytes[index].toInt() and 0xFF) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[index + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 3].toInt() and 0xFF) shl 24)
                if (signature == EOCD_SIGNATURE) return index
            }
            return -1
        }

        private fun readLongLe(bytes: ByteArray, offset: Int): Long {
            if (offset + 8 > bytes.size) return -1L
            var value = 0L
            for (index in 0 until 8) {
                value = value or ((bytes[offset + index].toLong() and 0xFFL) shl (index * 8))
            }
            return value
        }

        private fun readIntLe(bytes: ByteArray, offset: Int): Int {
            if (offset + 4 > bytes.size) return -1
            return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        }

        private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
            if (needle.isEmpty() || haystack.size < needle.size) return -1
            outer@ for (index in 0..haystack.size - needle.size) {
                for (needleIndex in needle.indices) {
                    if (haystack[index + needleIndex] != needle[needleIndex]) continue@outer
                }
                return index
            }
            return -1
        }
    }
}
