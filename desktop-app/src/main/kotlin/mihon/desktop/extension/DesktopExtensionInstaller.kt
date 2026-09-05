package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.preferences.DesktopPreferenceStore
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
)

class DesktopExtensionInstaller(
    private val installRoot: File,
    private val preferenceStore: DesktopPreferenceStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    companion object {
        const val PREF_KEY_INSTALLED_EXTENSIONS = "extension.installed_list"
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
    ): InstalledExtension = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).build()
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

            installFromLocalFile(tempFile, expectedSha256, repoUrl)
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
    ): InstalledExtension = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            throw ExtensionValidationException("Extension package file does not exist: ${file.absolutePath}")
        }

        // 1. Verify SHA-256 if provided
        if (!expectedSha256.isNullOrBlank()) {
            val actualSha256 = computeSha256(file)
            if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                throw ExtensionValidationException(
                    "SHA-256 mismatch: expected '$expectedSha256', but computed '$actualSha256'",
                )
            }
        }

        // 2. Validate package and parse manifest
        val manifest = ExtensionPackageValidator.validatePackage(file)

        // 3. Destination folder: <installRoot>/<pkg>
        val targetDir = File(installRoot, manifest.id)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        val targetPackageFile = File(targetDir, "${manifest.id}.mext")
        file.copyTo(targetPackageFile, overwrite = true)

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
        )

        val current = getInstalledExtensions().filter { it.pkg != manifest.id }
        saveInstalledExtensions(current + installed)

        installed
    }

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
