package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.updates.DesktopAppUpdateService
import mihon.extension.model.ExtensionManifest
import mihon.extension.validator.ExtensionPackageValidator
import mihon.extension.validator.ExtensionValidationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
data class InstalledExtension(
    val pkg: String,
    val manifest: ExtensionManifest,
    val installDir: String,
    val packageFile: String,
    val installedAt: Long,
    val repoUrl: String = "",
    val iconPath: String? = null,
    val isEnabled: Boolean = true,
    /** Last (primary) verified signer certificate SHA-256 fingerprint, or "" when unsigned. */
    val signatureFingerprint: String = "",
    /** All verified signer certificate SHA-256 fingerprints, or empty when unsigned. */
    val signatureFingerprints: List<String> = emptyList(),
    /** Repository signing key that was used to trust this package, if any. */
    val signingKey: String = "",
    /** Trust state recorded at install time. Prefer the live [ExtensionTrustStore] when available. */
    val trustStatus: ExtensionTrustStatus = ExtensionTrustStatus.UNKNOWN,
) {
    val signerFingerprint: String get() = signatureFingerprint
    val signerFingerprints: List<String> get() = signatureFingerprints
    val signatureHash: String get() = signatureFingerprint
    val fingerprint: String get() = signatureFingerprint
    val trustedFingerprints: List<String> get() = signatureFingerprints
    val trustState: ExtensionTrustStatus get() = trustStatus
    val isTrusted: Boolean get() = trustStatus == ExtensionTrustStatus.TRUSTED
    val trusted: Boolean get() = isTrusted
    val isUntrusted: Boolean get() = trustStatus == ExtensionTrustStatus.UNTRUSTED
}

class DesktopExtensionInstaller(
    val installRoot: File,
    val preferenceStore: DesktopPreferenceStore,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    signatureVerifier: ExtensionVerifier = ExtensionSignatureVerifier(),
    trustStore: ExtensionTrustStore = ExtensionTrustStore(preferenceStore),
    /** Alias constructor parameter for callers/tests that name the fake verifier `verifier`. */
    verifier: ExtensionVerifier? = null,
) {
    val signatureVerifier: ExtensionVerifier = verifier ?: signatureVerifier
    val verifier: ExtensionVerifier get() = signatureVerifier
    val trustStore: ExtensionTrustStore = trustStore
    val extensionTrustStore: ExtensionTrustStore get() = trustStore
    companion object {
        const val PREF_KEY_INSTALLED_EXTENSIONS = "extension.installed_list"
        const val PREF_KEY_TRUSTED_EXTENSIONS = ExtensionTrustStore.PREF_KEY_TRUSTED_EXTENSIONS
        const val PREF_KEY_REVOKED_EXTENSIONS = ExtensionTrustStore.PREF_KEY_REVOKED_EXTENSIONS
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    init {
        if (!installRoot.exists()) {
            installRoot.mkdirs()
        }
    }

    @Synchronized
    fun getInstalledExtensions(): List<InstalledExtension> {
        val raw = preferenceStore.property(PREF_KEY_INSTALLED_EXTENSIONS) ?: return emptyList()
        return try {
            json.decodeFromString<List<InstalledExtension>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveInstalledExtensions(list: List<InstalledExtension>) {
        val raw = json.encodeToString(list)
        preferenceStore.update {
            setProperty(PREF_KEY_INSTALLED_EXTENSIONS, raw)
        }
    }

    suspend fun downloadAndInstall(
        downloadUrl: String,
        expectedSha256: String?,
        repoUrl: String = "",
        storeItem: ExtensionStoreItem? = null,
        trustOnInstall: Boolean = false,
        allowUntrusted: Boolean = false,
        explicitTrust: Boolean = false,
        trustUnknown: Boolean = false,
    ): InstalledExtension = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
            .build()
        val tempFile = File.createTempFile("mext_dl_", ".mext")
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ExtensionValidationException("Failed to download extension package: HTTP ${response.code}")
                }
                val body = response.body
                FileOutputStream(tempFile).use { out ->
                    body.byteStream().copyTo(out)
                }
            }

            installFromLocalFile(
                file = tempFile,
                expectedSha256 = expectedSha256,
                repoUrl = repoUrl,
                storeItem = storeItem,
                trustOnInstall = trustOnInstall,
                allowUntrusted = allowUntrusted,
                explicitTrust = explicitTrust,
                trustUnknown = trustUnknown,
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun installFromLocalFile(
        file: File,
        expectedSha256: String? = null,
        repoUrl: String = "",
        storeItem: ExtensionStoreItem? = null,
        trustOnInstall: Boolean = false,
        allowUntrusted: Boolean = false,
        explicitTrust: Boolean = false,
        trustUnknown: Boolean = false,
    ): InstalledExtension = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            throw ExtensionValidationException("Extension package file does not exist: ${file.absolutePath}")
        }

        // A new install attempt supersedes any previous trust prompt.
        trustStore.clearPendingTrustRequest()

        // 1. Verify every known SHA-256 (explicit argument and/or store index digest).
        val expectedHashes = buildList {
            expectedSha256?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            storeItem?.sha256?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        }.distinct()
        if (expectedHashes.isNotEmpty()) {
            val actualSha256 = computeSha256(file)
            expectedHashes.forEach { expected ->
                if (!actualSha256.equals(expected, ignoreCase = true)) {
                    throw ExtensionValidationException(
                        "SHA-256 mismatch: expected '$expected', but computed '$actualSha256'",
                    )
                }
            }
        }

        // 2. Verify the v1 JAR/APK signature before conversion changes the package bytes.
        val verification = try {
            signatureVerifier.verify(file)
        } catch (e: ExtensionValidationException) {
            throw e
        } catch (e: Exception) {
            throw ExtensionSignatureException("Failed to verify extension signature: ${e.message}", e)
        }
        if (!verification.valid || verification.v2V3Only) {
            throw ExtensionSignatureException(
                verification.error ?: "APK Signature Scheme v2/v3-only packages are not supported",
            )
        }
        val signerFingerprints = verification.fingerprints
            .map { ExtensionTrustStore.normalizeFingerprint(it) }
            .filter { it.isNotEmpty() }
            .distinct()

        // 3. Validate package and parse manifest (converting Tachiyomi APK/JAR to .mext if needed)
        val effectivePackageFile: File
        val manifest: ExtensionManifest
        var tempConvertedMext: File? = null
        if (mihon.desktop.extension.compat.TachiyomiExtensionConverter.isTachiyomiPackage(file)) {
            val convertedFile = File.createTempFile("mext_conv_", ".mext")
            tempConvertedMext = convertedFile
            manifest =
                mihon.desktop.extension.compat.TachiyomiExtensionConverter.convertToMext(file, convertedFile, storeItem)
            effectivePackageFile = convertedFile
        } else {
            manifest = ExtensionPackageValidator.validatePackage(file)
            effectivePackageFile = file
        }

        try {
            // 4. Enforce signature trust policy before writing anything to the install root.
            val installedExtension = getInstalledExtensions().find { it.pkg == manifest.id }
            val installedFingerprints = buildSet {
                installedExtension?.signatureFingerprints.orEmpty().forEach {
                    add(ExtensionTrustStore.normalizeFingerprint(it))
                }
                installedExtension?.signatureFingerprint?.let { add(ExtensionTrustStore.normalizeFingerprint(it)) }
                installedExtension?.signingKey?.let { add(ExtensionTrustStore.normalizeFingerprint(it)) }
            }.filter { it.isNotEmpty() }.toSet()
            enforceTrustPolicy(
                file = file,
                pkg = manifest.id,
                signerFingerprints = signerFingerprints,
                storeItem = storeItem,
                installedFingerprints = installedFingerprints,
                explicitTrust = trustOnInstall || allowUntrusted || explicitTrust || trustUnknown ||
                    storeItem?.trustOnInstall == true,
            )

            // Persist a matching repository signing key so trust survives restarts and later
            // repository fetches can compare against a local, user-visible trust entry.
            val storeSigningKey = ExtensionTrustStore.normalizeFingerprint(storeItem?.signingKey)
            if (storeSigningKey.isNotEmpty() && signerFingerprints.contains(storeSigningKey)) {
                trustStore.trust(manifest.id, storeSigningKey)
            }

            // 5. Destination folder: <installRoot>/<pkg>
            val targetDir = File(installRoot, manifest.id)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val targetPackageFile = File(targetDir, "${manifest.id}.mext")
            effectivePackageFile.copyTo(targetPackageFile, overwrite = true)

            // Extract icon if present in package
            var iconPath: String? = null
            java.util.zip.ZipFile(targetPackageFile).use { zip ->
                val iconEntry = zip.getEntry("icon.png") ?: zip.getEntry("icon.jpg") ?: zip.getEntry("assets/icon.png")
                if (iconEntry != null) {
                    val iconFile = File(targetDir, "icon.png")
                    zip.getInputStream(iconEntry).use { input ->
                        FileOutputStream(iconFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    iconPath = iconFile.absolutePath
                }
            }

            val installed = InstalledExtension(
                pkg = manifest.id,
                manifest = manifest,
                installDir = targetDir.absolutePath,
                packageFile = targetPackageFile.absolutePath,
                installedAt = System.currentTimeMillis(),
                repoUrl = repoUrl,
                iconPath = iconPath,
                isEnabled = true,
                signatureFingerprint = signerFingerprints.lastOrNull().orEmpty(),
                signatureFingerprints = signerFingerprints,
                signingKey = ExtensionTrustStore.normalizeFingerprint(storeItem?.signingKey),
                trustStatus = ExtensionTrustStatus.TRUSTED,
            )

            val current = getInstalledExtensions().filter { it.pkg != manifest.id }
            saveInstalledExtensions(current + installed)

            installed
        } finally {
            tempConvertedMext?.delete()
        }
    }

    /** Alias for [installFromLocalFile]. */
    suspend fun installFromFile(
        file: File,
        expectedSha256: String? = null,
        repoUrl: String = "",
        storeItem: ExtensionStoreItem? = null,
        trustOnInstall: Boolean = false,
        allowUntrusted: Boolean = false,
        explicitTrust: Boolean = false,
        trustUnknown: Boolean = false,
    ): InstalledExtension = installFromLocalFile(
        file,
        expectedSha256,
        repoUrl,
        storeItem,
        trustOnInstall,
        allowUntrusted,
        explicitTrust,
        trustUnknown,
    )

    suspend fun uninstall(pkg: String): Boolean = withContext(Dispatchers.IO) {
        val current = getInstalledExtensions()
        val target = current.find { it.pkg == pkg } ?: return@withContext false

        val targetDir = File(target.installDir)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }

        saveInstalledExtensions(current.filter { it.pkg != pkg })
        true
    }

    suspend fun setExtensionEnabled(pkg: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val current = getInstalledExtensions()
        val index = current.indexOfFirst { it.pkg == pkg }
        if (index == -1) return@withContext false

        val updated = current.toMutableList()
        updated[index] = updated[index].copy(isEnabled = enabled)
        saveInstalledExtensions(updated)
        true
    }

    fun getTrustedFingerprints(pkg: String): Set<String> = trustStore.getTrustedFingerprints(pkg)

    fun isExtensionTrusted(extension: InstalledExtension): Boolean =
        getExtensionTrustStatus(extension) == ExtensionTrustStatus.TRUSTED

    fun isExtensionTrusted(
        pkg: String,
        fingerprints: Collection<String> = emptyList(),
        signingKey: String? = null,
    ): Boolean = getTrustStatus(pkg, fingerprints, signingKey) == ExtensionTrustStatus.TRUSTED

    fun isExtensionTrusted(pkg: String, fingerprint: String?): Boolean =
        getTrustStatus(pkg, fingerprint) == ExtensionTrustStatus.TRUSTED

    fun revokeAllExtensions() = trustStore.revokeAll()

    /**
     * Enforces the Android-style trust policy:
     *  - a package signed by a known repository key or a previously trusted fingerprint installs;
     *  - an unknown package requires explicit user trust (the Browse UI's "Trust & Install");
     *  - a package whose signer mismatches a known key/fingerprint is always rejected;
     *  - a revoked package must be explicitly trusted again, even when a repository key matches.
     */
    private fun enforceTrustPolicy(
        file: File,
        pkg: String,
        signerFingerprints: List<String>,
        storeItem: ExtensionStoreItem?,
        installedFingerprints: Set<String> = emptySet(),
        explicitTrust: Boolean,
    ) {
        val storeSigningKey = ExtensionTrustStore.normalizeFingerprint(storeItem?.signingKey)
        val userTrusted = trustStore.getTrustedFingerprints(pkg)
        val revoked = trustStore.isRevoked(pkg)
        val storeMatches = signerFingerprints.isNotEmpty() &&
            storeSigningKey.isNotEmpty() &&
            storeSigningKey in signerFingerprints
        val userMatches = signerFingerprints.isNotEmpty() && signerFingerprints.any { it in userTrusted }
        val installedMatches = signerFingerprints.isNotEmpty() &&
            installedFingerprints.any { it in signerFingerprints }
        val hasInstalledIdentity = installedFingerprints.isNotEmpty()
        val unsignedTrusted = signerFingerprints.isEmpty() && !revoked && "" in userTrusted
        val expectedKnown = storeSigningKey.isNotEmpty() || userTrusted.isNotEmpty()

        if (signerFingerprints.isEmpty()) {
            when {
                // A previously signed extension must not silently downgrade to an unsigned package.
                hasInstalledIdentity -> throw ExtensionSignatureMismatchException(
                    pkg = pkg,
                    expectedFingerprints = installedFingerprints,
                    actualFingerprints = emptyList(),
                )
                unsignedTrusted -> return
                storeSigningKey.isNotEmpty() -> throw ExtensionSignatureMismatchException(
                    pkg = pkg,
                    expectedFingerprints = setOf(storeSigningKey),
                    actualFingerprints = emptyList(),
                )
                userTrusted.isNotEmpty() -> throw ExtensionSignatureMismatchException(
                    pkg = pkg,
                    expectedFingerprints = userTrusted,
                    actualFingerprints = emptyList(),
                )
                explicitTrust -> {
                    trustStore.trustUnsigned(pkg)
                    return
                }
                else -> requestTrustAndThrow(
                    file = file,
                    pkg = pkg,
                    signerFingerprints = signerFingerprints,
                    storeItem = storeItem,
                    reason = "Extension package is unsigned and its signer is unknown",
                )
            }
        }

        // A known repository signing key must match the package, even when the installed
        // extension fingerprint would otherwise allow the update.
        if (storeSigningKey.isNotEmpty() && !storeMatches) {
            throw ExtensionSignatureMismatchException(pkg, setOf(storeSigningKey), signerFingerprints)
        }

        // Updating an installed extension may not change its signer, even with explicit trust.
        if (hasInstalledIdentity && !installedMatches) {
            throw ExtensionSignatureMismatchException(
                pkg = pkg,
                expectedFingerprints = installedFingerprints,
                actualFingerprints = signerFingerprints,
            )
        }

        if (storeMatches || userMatches || installedMatches) {
            if (!revoked) return
            if (explicitTrust) {
                trustStore.trust(pkg, signerFingerprints)
                return
            }
            requestTrustAndThrow(
                file = file,
                pkg = pkg,
                signerFingerprints = signerFingerprints,
                storeItem = storeItem,
                reason = "Trust for this extension was revoked",
            )
        }

        if (expectedKnown) {
            val expected = buildSet {
                addAll(userTrusted)
                if (storeSigningKey.isNotEmpty()) add(storeSigningKey)
            }
            throw ExtensionSignatureMismatchException(pkg, expected, signerFingerprints)
        }

        if (explicitTrust) {
            trustStore.trust(pkg, signerFingerprints)
            return
        }

        requestTrustAndThrow(
            file = file,
            pkg = pkg,
            signerFingerprints = signerFingerprints,
            storeItem = storeItem,
            reason = "Extension signer is unknown",
        )
    }

    private fun requestTrustAndThrow(
        file: File,
        pkg: String,
        signerFingerprints: List<String>,
        storeItem: ExtensionStoreItem?,
        reason: String,
    ): Nothing {
        trustStore.requestTrust(
            pkg = pkg,
            fingerprints = signerFingerprints,
            filePath = file.absolutePath,
            storeItem = storeItem,
            reason = reason,
        )
        throw ExtensionTrustRequiredException(pkg, signerFingerprints, reason)
    }

    /** Trusts all supplied signer fingerprints for [pkg]. */
    fun trustExtension(pkg: String, fingerprint: String?) = trustStore.trust(pkg, fingerprint)

    fun trustExtension(pkg: String, fingerprints: Collection<String>) = trustStore.trust(pkg, fingerprints)

    fun trust(pkg: String, fingerprint: String?) = trustExtension(pkg, fingerprint)

    fun trustUnsignedExtension(pkg: String) = trustStore.trustUnsigned(pkg)

    /** Revokes explicit user trust (and marks the package revoked) for [pkg]. */
    fun revokeExtension(pkg: String) = trustStore.revoke(pkg)

    fun revoke(pkg: String) = revokeExtension(pkg)

    fun getExtensionTrustStatus(extension: InstalledExtension): ExtensionTrustStatus =
        trustStore.statusFor(extension)

    fun getTrustStatus(extension: InstalledExtension): ExtensionTrustStatus = getExtensionTrustStatus(extension)

    fun getTrustStatus(pkg: String, fingerprint: String?): ExtensionTrustStatus =
        getTrustStatus(pkg, listOfNotNull(fingerprint?.takeIf { it.isNotBlank() }))

    fun getTrustStatus(pkg: String, fingerprint: String?, signingKey: String?): ExtensionTrustStatus =
        getTrustStatus(pkg, listOfNotNull(fingerprint?.takeIf { it.isNotBlank() }), signingKey)

    fun getTrustStatus(
        pkg: String,
        fingerprints: Collection<String> = emptyList(),
        signingKey: String? = null,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = trustStore.status(
        pkg = pkg,
        fingerprints = fingerprints,
        trustedSigningKeys = listOfNotNull(signingKey?.takeIf { it.isNotBlank() }),
        signatureValid = signatureValid,
    )

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
