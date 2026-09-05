package mihon.extension.validator

import kotlinx.serialization.json.Json
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ExtensionValidationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object ExtensionPackageValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val packageIdRegex = Regex("^[a-zA-Z0-9_.]+$")
    private val domainRegex =
        Regex("^(((\\*\\.)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})|([0-9]{1,3}(\\.[0-9]{1,3}){3})|localhost)$")

    const val MAX_PACKAGE_ENTRIES = 1_000
    const val MAX_EXPANDED_BYTES = 50 * 1024 * 1024L // 50 MiB

    fun validateManifest(manifest: ExtensionManifest) {
        if (manifest.id.isBlank()) {
            throw ExtensionValidationException("Extension ID must not be blank")
        }
        if (!packageIdRegex.matches(manifest.id)) {
            throw ExtensionValidationException("Extension ID '${manifest.id}' contains invalid characters")
        }
        if (manifest.name.isBlank()) {
            throw ExtensionValidationException("Extension name must not be blank")
        }
        if (manifest.version.isBlank()) {
            throw ExtensionValidationException("Extension version must not be blank")
        }
        if (manifest.versionCode <= 0) {
            throw ExtensionValidationException("Extension versionCode must be positive: ${manifest.versionCode}")
        }
        if (manifest.libVersion < 1.0) {
            throw ExtensionValidationException("Extension libVersion must be at least 1.0: ${manifest.libVersion}")
        }
        if (manifest.sources.isEmpty()) {
            throw ExtensionValidationException("Extension must declare at least one source")
        }

        manifest.sources.forEach { source ->
            validateSourceDescriptor(source)
        }

        manifest.declaredDomains.forEach { domain ->
            validateDomain(domain)
        }
    }

    private fun validateSourceDescriptor(source: SourceDescriptor) {
        if (source.id <= 0) {
            throw ExtensionValidationException("Source ID must be positive: ${source.id}")
        }
        if (source.name.isBlank()) {
            throw ExtensionValidationException("Source name must not be blank for ID ${source.id}")
        }
        if (source.lang.isBlank()) {
            throw ExtensionValidationException("Source language must not be blank for ID ${source.id}")
        }
        if (source.className.isBlank()) {
            throw ExtensionValidationException("Source className must not be blank for ID ${source.id}")
        }
    }

    private fun validateDomain(domain: String) {
        if (domain.isBlank()) {
            throw ExtensionValidationException("Declared domain must not be blank")
        }
        if (domain.contains("://") || domain.contains("/") || domain.contains(":") || domain.contains("\\")) {
            throw ExtensionValidationException("Declared domain must be a bare hostname or pattern, got: '$domain'")
        }
        if (!domainRegex.matches(domain)) {
            throw ExtensionValidationException("Invalid declared domain pattern: '$domain'")
        }
    }

    fun validatePackage(file: File): ExtensionManifest {
        if (!file.exists() || !file.isFile) {
            throw ExtensionValidationException("Package file does not exist: ${file.absolutePath}")
        }
        return file.inputStream().use { validatePackageStream(it) }
    }

    fun validatePackageStream(inputStream: InputStream): ExtensionManifest {
        val zip = ZipInputStream(inputStream)
        var manifestBytes: ByteArray? = null
        var entryCount = 0
        var totalExpandedBytes = 0L

        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            entryCount++
            if (entryCount > MAX_PACKAGE_ENTRIES) {
                throw ExtensionValidationException("Package exceeds maximum entry count ($MAX_PACKAGE_ENTRIES)")
            }

            val name = entry.name
            if (name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":") ||
                name.contains("\u0000")
            ) {
                throw ExtensionValidationException("Zip slip or unsafe entry rejected: '$name'")
            }

            if (!entry.isDirectory) {
                val bytes = zip.readBytes()
                totalExpandedBytes += bytes.size
                if (totalExpandedBytes > MAX_EXPANDED_BYTES) {
                    throw ExtensionValidationException(
                        "Package exceeds maximum expanded size ($MAX_EXPANDED_BYTES bytes)",
                    )
                }

                if (name == "manifest.json") {
                    manifestBytes = bytes
                }
            }

            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (manifestBytes == null) {
            throw ExtensionValidationException("Package does not contain manifest.json")
        }

        val manifest = try {
            json.decodeFromString<ExtensionManifest>(manifestBytes.decodeToString())
        } catch (e: Exception) {
            throw ExtensionValidationException("Failed to parse manifest.json: ${e.message}", e)
        }

        validateManifest(manifest)
        return manifest
    }
}
