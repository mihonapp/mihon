package mihon.desktop.extension

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.preferences.DesktopPreferenceStore
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.JvmName

/** Trust state for an extension package signer. */
enum class ExtensionTrustStatus {
    TRUSTED,
    UNTRUSTED,
    UNKNOWN,
    INVALID,
    ;

    val label: String
        get() = when (this) {
            TRUSTED -> "Trusted"
            UNTRUSTED -> "Untrusted"
            UNKNOWN -> "Unknown"
            INVALID -> "Invalid"
        }

    val isTrusted: Boolean get() = this == TRUSTED
}

/** Alias kept for callers that use a shorter name. */
typealias TrustStatus = ExtensionTrustStatus

/** Alias kept for callers that use the Android-style "state" name. */
typealias ExtensionTrustState = ExtensionTrustStatus

/**
 * A pending explicit-trust request created when an install is blocked because the package signer is
 * unknown. The UI can display this request as a dialog and retry the install after trusting.
 */
@Serializable
data class ExtensionTrustRequest(
    val pkg: String,
    val fingerprints: List<String> = emptyList(),
    val filePath: String? = null,
    val reason: String = "",
    @kotlinx.serialization.Transient
    val storeItem: ExtensionStoreItem? = null,
)

@Serializable
private data class PersistedExtensionTrust(
    val trusted: Map<String, List<String>> = emptyMap(),
    val revoked: List<String> = emptyList(),
)

/**
 * Persistent extension signer trust store keyed by package id + signer certificate fingerprint.
 *
 * The store is persisted in [DesktopPreferenceStore] as a small JSON document so trust survives
 * application restarts. Fingerprints are normalized to lowercase hex (colons/spaces/base64 input
 * are accepted) before matching.
 *
 * A package can be trusted by an explicit user trust entry (including the empty fingerprint for
 * unsigned packages) or by a repository signing key supplied by the caller. [revoke] removes the
 * user trust entry and records a package-level revocation so repository keys do not silently
 * re-trust a package the user revoked. [trust] clears that revocation.
 */
class ExtensionTrustStore(
    private val preferenceStore: DesktopPreferenceStore,
    private val storageKey: String = PREF_KEY_TRUSTED_EXTENSIONS,
) {
    constructor(file: java.nio.file.Path) : this(DesktopPreferenceStore(file))

    constructor(file: java.io.File) : this(DesktopPreferenceStore(file.toPath()))

    companion object {
        const val PREF_KEY_TRUSTED_EXTENSIONS = "extension.trusted_signatures"
        const val PREF_KEY_TRUSTED_SIGNATURES = PREF_KEY_TRUSTED_EXTENSIONS
        const val PREF_KEY_TRUSTED_FINGERPRINTS = PREF_KEY_TRUSTED_EXTENSIONS
        const val PREF_KEY_REVOKED_EXTENSIONS = "extension.revoked_packages"

        private val activeStore = AtomicReference<ExtensionTrustStore?>(null)

        /** The most recently constructed store, used by Compose screens for default actions. */
        fun active(): ExtensionTrustStore? = activeStore.get()

        /** Test helper: forget the process-wide active store. */
        fun clearActiveForTesting() {
            activeStore.set(null)
        }

        /** Test helper: make this store the process-wide active store. */
        fun setActiveForTesting(store: ExtensionTrustStore?) {
            activeStore.set(store)
        }

        /**
         * Normalizes a fingerprint to lowercase hex. Accepts the common repository formats
         * (`AA:BB:...`, `aabb...`, or base64 DER hash). Blank and `NO_SIGNING_KEY` become "".
         */
        fun normalizeFingerprint(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            var trimmed = raw.trim()
            if (trimmed.startsWith("sha256:", ignoreCase = true)) trimmed = trimmed.substring(7)
            trimmed = trimmed.removePrefix("0x").removePrefix("0X")
            if (trimmed.equals("NO_SIGNING_KEY", ignoreCase = true)) return ""

            val compact = trimmed.replace(":", "").replace(" ", "").replace("-", "").lowercase()
            if (compact.isNotEmpty() && compact.all { it in '0'..'9' || it in 'a'..'f' }) {
                return compact
            }
            val base64Input = trimmed.replace(":", "").replace(" ", "")
            val decoded = try {
                Base64.getDecoder().decode(base64Input)
            } catch (_: IllegalArgumentException) {
                try {
                    Base64.getUrlDecoder().decode(base64Input)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            return when {
                decoded == null -> trimmed
                // 32 bytes is the common base64 encoding of a raw SHA-256 fingerprint.
                decoded.size == 32 -> decoded.joinToString("") { byte -> "%02x".format(byte) }
                // A DER certificate begins with an ASN.1 SEQUENCE tag; hash the certificate.
                decoded.isNotEmpty() && decoded[0] == 0x30.toByte() && decoded.size > 32 ->
                    ExtensionSignatureVerifier.sha256Hex(decoded)
                // Preserve opaque/fake fingerprints used by tests or future store formats.
                else -> trimmed
            }
        }

        fun normalizeFingerprints(raw: Collection<String>): Set<String> =
            raw.map { normalizeFingerprint(it) }.toSet()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _revision = MutableStateFlow(0L)

    /** Increments on every mutation; Compose screens collect this to recompute trust badges. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _pendingTrustRequest = MutableStateFlow<ExtensionTrustRequest?>(null)

    /** Pending explicit-trust request, if an install was blocked on an unknown signer. */
    val pendingTrustRequest: StateFlow<ExtensionTrustRequest?> = _pendingTrustRequest.asStateFlow()

    init {
        activeStore.set(this)
    }

    @Synchronized
    fun snapshot(): ExtensionTrustSnapshot {
        val persisted = readPersisted()
        return ExtensionTrustSnapshot(
            trusted = persisted.trusted.mapValues { (_, values) -> normalizeFingerprints(values) },
            revoked = persisted.revoked.toSet(),
        )
    }

    @Synchronized
    fun getTrustedFingerprints(pkg: String): Set<String> =
        snapshot().trusted[pkg].orEmpty()

    /** All packages with an explicit user trust entry. */
    @Synchronized
    fun getTrustedPackages(): Set<String> = snapshot().trusted.keys

    @Synchronized
    fun getTrusted(pkg: String): Set<String> = getTrustedFingerprints(pkg)

    @Synchronized
    fun getAll(): Map<String, Set<String>> = snapshot().trusted

    @Synchronized
    fun getAllTrustedFingerprints(): Set<String> = snapshot().trusted.values.flatten().toSet()

    val trusted: Map<String, Set<String>>
        get() = snapshot().trusted

    val trustedFingerprints: Map<String, Set<String>>
        get() = snapshot().trusted

    @get:JvmName("getRevokedSet")
    val revoked: Set<String>
        get() = snapshot().revoked

    fun getRevoked(): Set<String> = snapshot().revoked

    @Synchronized
    fun isTrusted(pkg: String, fingerprint: String?): Boolean =
        isTrusted(pkg, listOf(fingerprint.orEmpty()))

    @Synchronized
    fun isTrusted(pkg: String, fingerprints: Collection<String>): Boolean {
        val persisted = readPersisted()
        if (pkg in persisted.revoked) return false
        val trusted = normalizeFingerprints(persisted.trusted[pkg].orEmpty())
        if (trusted.isEmpty()) return false
        val normalized = fingerprints.map { normalizeFingerprint(it) }
        if (normalized.isEmpty()) return "" in trusted
        return normalized.any { it in trusted }
    }

    @Synchronized
    fun isUserTrusted(pkg: String, fingerprints: Collection<String>): Boolean =
        isTrusted(pkg, fingerprints)

    fun isPackageTrusted(pkg: String, fingerprint: String?): Boolean = isTrusted(pkg, fingerprint)

    fun isPackageTrusted(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKeys: Collection<String> = emptyList(),
    ): Boolean = isTrusted(pkg, fingerprints, trustedSigningKeys)

    fun isTrusted(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKeys: Collection<String>,
    ): Boolean = status(pkg, fingerprints, trustedSigningKeys) == ExtensionTrustStatus.TRUSTED

    fun isTrusted(
        pkg: String,
        fingerprint: String?,
        trustedSigningKey: String?,
    ): Boolean = status(pkg, fingerprint, trustedSigningKey) == ExtensionTrustStatus.TRUSTED

    @Synchronized
    fun isRevoked(pkg: String): Boolean = pkg in readPersisted().revoked

    @Synchronized
    fun trust(pkg: String, fingerprint: String?) {
        trust(pkg, listOf(fingerprint.orEmpty()))
    }

    @Synchronized
    fun trust(pkg: String, fingerprints: Collection<String>) {
        if (pkg.isBlank()) return
        val normalized = normalizeFingerprints(fingerprints)
        if (normalized.isEmpty()) return

        val persisted = readPersisted()
        val trusted = persisted.trusted.toMutableMap()
        val existing = trusted[pkg].orEmpty().map { normalizeFingerprint(it) }.toMutableSet()
        existing += normalized
        trusted[pkg] = existing.toList()
        writePersisted(persisted.copy(trusted = trusted, revoked = persisted.revoked - pkg))
    }

    /** Trusts an unsigned package. The empty fingerprint is the sentinel for "no signature". */
    @Synchronized
    fun trustUnsigned(pkg: String) {
        trust(pkg, listOf(""))
    }

    @Synchronized
    fun trustExtension(pkg: String, fingerprint: String?) = trust(pkg, fingerprint)

    @Synchronized
    fun revoke(pkg: String, fingerprint: String? = null) {
        if (pkg.isBlank()) return
        val persisted = readPersisted()
        val trusted = persisted.trusted.toMutableMap()
        val revoked = persisted.revoked.toMutableSet()

        if (fingerprint == null) {
            trusted.remove(pkg)
            revoked += pkg
        } else {
            val normalized = normalizeFingerprint(fingerprint)
            val existing = trusted[pkg].orEmpty().map { normalizeFingerprint(it) }.toMutableSet()
            existing.remove(normalized)
            if (existing.isEmpty()) {
                trusted.remove(pkg)
                revoked += pkg
            } else {
                trusted[pkg] = existing.toList()
            }
        }

        writePersisted(persisted.copy(trusted = trusted, revoked = revoked.toList()))
    }

    @Synchronized
    fun revokeExtension(pkg: String) = revoke(pkg)

    /** Clears all explicit user trust and package-level revocations. Repository keys stay trusted. */
    @Synchronized
    fun revokeAll() {
        writePersisted(PersistedExtensionTrust())
    }

    fun clear() = revokeAll()

    @Synchronized
    fun getRevokedPackages(): Set<String> = readPersisted().revoked.toSet()

    @get:JvmName("getTrustedPackagesSet")
    val trustedPackages: Set<String>
        get() = snapshot().trusted.keys

    @get:JvmName("getRevokedPackagesSet")
    val revokedPackages: Set<String>
        get() = snapshot().revoked

    fun trustFingerprint(pkg: String, fingerprint: String?) = trust(pkg, fingerprint)

    fun trustPackage(pkg: String, fingerprint: String?) = trust(pkg, fingerprint)

    fun revokePackage(pkg: String) = revoke(pkg)

    fun revokeFingerprint(pkg: String, fingerprint: String?) = revoke(pkg, fingerprint)

    /**
     * Resolves trust status from signer [fingerprints], optional repository [trustedSigningKeys],
     * and the package-level revocation marker.
     */
    fun status(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKeys: Collection<String> = emptyList(),
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus {
        if (!signatureValid) return ExtensionTrustStatus.INVALID

        val persisted = readPersisted()
        val userTrusted = normalizeFingerprints(persisted.trusted[pkg].orEmpty())
        val storeKeys = normalizeFingerprints(trustedSigningKeys).filter { it.isNotEmpty() }
        val normalized = fingerprints.map { normalizeFingerprint(it) }.filter { it.isNotEmpty() }
        val revoked = pkg in persisted.revoked

        if (revoked) {
            // A revoked package must be explicitly trusted again even when a repository key matches.
            return ExtensionTrustStatus.UNTRUSTED
        }

        if (normalized.isEmpty()) {
            return if ("" in userTrusted) ExtensionTrustStatus.TRUSTED else ExtensionTrustStatus.UNKNOWN
        }

        if (normalized.any { it in userTrusted }) return ExtensionTrustStatus.TRUSTED
        if (storeKeys.isNotEmpty() && normalized.any { it in storeKeys }) return ExtensionTrustStatus.TRUSTED
        return ExtensionTrustStatus.UNTRUSTED
    }

    fun status(
        pkg: String,
        fingerprints: Collection<String>,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprints, emptyList(), signatureValid)

    fun status(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKey: String?,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(
        pkg = pkg,
        fingerprints = fingerprints,
        trustedSigningKeys = listOfNotNull(trustedSigningKey?.takeIf { it.isNotBlank() }),
        signatureValid = signatureValid,
    )

    fun status(
        pkg: String,
        fingerprint: String?,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprint, null, signatureValid)

    fun status(
        pkg: String,
        fingerprint: String?,
        trustedSigningKey: String? = null,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(
        pkg = pkg,
        fingerprints = listOfNotNull(fingerprint?.takeIf { it.isNotBlank() }),
        trustedSigningKeys = listOfNotNull(trustedSigningKey?.takeIf { it.isNotBlank() }),
        signatureValid = signatureValid,
    )

    fun getStatus(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKeys: Collection<String> = emptyList(),
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprints, trustedSigningKeys, signatureValid)

    fun getStatus(
        pkg: String,
        fingerprint: String?,
        trustedSigningKey: String? = null,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprint, trustedSigningKey, signatureValid)

    fun getStatus(
        pkg: String,
        fingerprints: Collection<String>,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprints, emptyList(), signatureValid)

    fun getStatus(
        pkg: String,
        fingerprint: String?,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprint, null, signatureValid)

    fun getStatus(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKey: String?,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprints, trustedSigningKey, signatureValid)

    fun trustStatus(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKeys: Collection<String> = emptyList(),
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprints, trustedSigningKeys, signatureValid)

    fun trustStatus(
        pkg: String,
        fingerprint: String?,
        trustedSigningKey: String? = null,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprint, trustedSigningKey, signatureValid)

    fun trustStatus(
        pkg: String,
        fingerprints: Collection<String>,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprints, emptyList(), signatureValid)

    fun trustStatus(
        pkg: String,
        fingerprint: String?,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprint, null, signatureValid)

    fun trustStatus(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKey: String?,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprints, trustedSigningKey, signatureValid)

    fun getTrustStatus(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKeys: Collection<String> = emptyList(),
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprints, trustedSigningKeys, signatureValid)

    fun getTrustStatus(
        pkg: String,
        fingerprints: Collection<String>,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprints, emptyList(), signatureValid)

    fun getTrustStatus(
        pkg: String,
        fingerprint: String?,
        trustedSigningKey: String? = null,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprint, trustedSigningKey, signatureValid)

    fun getTrustStatus(
        pkg: String,
        fingerprint: String?,
        signatureValid: Boolean,
    ): ExtensionTrustStatus = status(pkg, fingerprint, null, signatureValid)

    fun getTrustStatus(
        pkg: String,
        fingerprints: Collection<String>,
        trustedSigningKey: String?,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = status(pkg, fingerprints, trustedSigningKey, signatureValid)

    fun getTrustStatus(extension: InstalledExtension): ExtensionTrustStatus = statusFor(extension)

    fun statusFor(extension: InstalledExtension): ExtensionTrustStatus {
        val usableSigningKey = normalizeFingerprint(extension.signingKey)
        val fingerprints = extension.signatureFingerprints.ifEmpty {
            listOfNotNull(
                extension.signatureFingerprint.takeIf { it.isNotBlank() },
                usableSigningKey.takeIf { it.isNotBlank() },
            )
        }
        val storeStatus = status(
            pkg = extension.pkg,
            fingerprints = fingerprints,
            trustedSigningKeys = listOfNotNull(usableSigningKey.takeIf { it.isNotBlank() }),
            signatureValid = extension.trustStatus != ExtensionTrustStatus.INVALID,
        )
        val storeHasOpinion = getTrustedFingerprints(extension.pkg).isNotEmpty() ||
            isRevoked(extension.pkg) ||
            usableSigningKey.isNotBlank()
        return if (!storeHasOpinion && extension.trustStatus != ExtensionTrustStatus.UNKNOWN) {
            extension.trustStatus
        } else {
            storeStatus
        }
    }

    /** Requests explicit user trust, usually after [ExtensionTrustRequiredException]. */
    fun requestTrust(
        pkg: String,
        fingerprints: List<String>,
        filePath: String? = null,
        storeItem: ExtensionStoreItem? = null,
        reason: String = "Extension signature is not trusted",
    ) {
        _pendingTrustRequest.value = ExtensionTrustRequest(
            pkg = pkg,
            fingerprints = fingerprints,
            filePath = filePath,
            storeItem = storeItem,
            reason = reason,
        )
    }

    fun pendingTrustRequest(): ExtensionTrustRequest? = _pendingTrustRequest.value

    fun clearPendingTrustRequest() {
        _pendingTrustRequest.value = null
    }

    fun consumePendingTrustRequest(): ExtensionTrustRequest? {
        val request = _pendingTrustRequest.value
        _pendingTrustRequest.value = null
        return request
    }

    @Synchronized
    private fun readPersisted(): PersistedExtensionTrust {
        val raw = preferenceStore.property(storageKey) ?: return PersistedExtensionTrust()
        // Accept a legacy bare map format: {"pkg":["fingerprint"]}. The primary format has
        // reserved "trusted"/"revoked" keys and is decoded below.
        try {
            val legacy = json.decodeFromString<Map<String, List<String>>>(raw)
            if (!legacy.containsKey("trusted") && !legacy.containsKey("revoked")) {
                return PersistedExtensionTrust(trusted = legacy)
            }
        } catch (_: Exception) {
            // Not the legacy format; fall through to the primary schema.
        }
        return try {
            json.decodeFromString<PersistedExtensionTrust>(raw)
        } catch (_: Exception) {
            PersistedExtensionTrust()
        }
    }

    @Synchronized
    private fun writePersisted(value: PersistedExtensionTrust) {
        preferenceStore.update {
            setProperty(storageKey, json.encodeToString(value))
        }
        _revision.value = _revision.value + 1
    }
}

/** Immutable view of the persisted trust store. */
data class ExtensionTrustSnapshot(
    val trusted: Map<String, Set<String>> = emptyMap(),
    val revoked: Set<String> = emptySet(),
)
