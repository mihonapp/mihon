package mihon.desktop.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.platform.DistributionMode
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Serializable
data class AppReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long = 0,
    val contentType: String = "",
)

@Serializable
data class AppReleaseInfo(
    val version: String,
    val tagName: String,
    val releaseNotes: String = "",
    val htmlUrl: String = "",
    val publishedAt: String = "",
    val assets: List<AppReleaseAsset> = emptyList(),
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val release: AppReleaseInfo,
        val currentVersion: String,
        val matchedAsset: AppReleaseAsset?,
    ) : UpdateCheckResult

    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class CheckFailed(val message: String) : UpdateCheckResult
}

class DesktopAppUpdateService(
    val currentVersion: String = CURRENT_VERSION,
    val distributionMode: DistributionMode = DistributionMode.Installed,
    val repository: String = DEFAULT_REPO,
    private val fetchText: suspend (String) -> String = ::defaultFetchText,
    private val downloadStream: suspend (String) -> InputStream = ::defaultDownloadStream,
) {
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$repository/releases/latest"
            val jsonText = fetchText(url)
            val release = parseReleaseJson(jsonText)
            if (isNewerVersion(release.version, currentVersion)) {
                val matchedAsset = findBestAsset(release.assets, distributionMode)
                UpdateCheckResult.UpdateAvailable(release, currentVersion, matchedAsset)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateCheckResult.CheckFailed(e.message ?: "Failed to check for updates")
        }
    }

    suspend fun downloadAsset(
        asset: AppReleaseAsset,
        destination: Path,
        expectedSha256: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val tempFile = destination.resolveSibling("${destination.fileName}.download")
        try {
            downloadStream(asset.downloadUrl).use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }
            if (expectedSha256 != null && !verifySha256(tempFile, expectedSha256)) {
                Files.deleteIfExists(tempFile)
                return@withContext false
            }
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: Exception) {
            Files.deleteIfExists(tempFile)
            false
        }
    }

    companion object {
        val CURRENT_VERSION: String = requireNotNull(
            DesktopAppUpdateService::class.java.getResourceAsStream("/mihon-desktop-version.txt"),
        ) { "Desktop version resource is missing" }.bufferedReader().use { it.readText().trim() }
        const val DEFAULT_REPO = "mihonapp/mihon-w"

        private val json = Json { ignoreUnknownKeys = true }

        fun parseReleaseJson(jsonText: String): AppReleaseInfo {
            val element = json.parseToJsonElement(jsonText).jsonObject
            val tagName = element["tag_name"]?.jsonPrimitive?.content ?: ""
            val version = tagName.removePrefix("v").trim()
            val releaseNotes = element["body"]?.jsonPrimitive?.content ?: ""
            val htmlUrl = element["html_url"]?.jsonPrimitive?.content ?: ""
            val publishedAt = element["published_at"]?.jsonPrimitive?.content ?: ""

            val assets = element["assets"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                AppReleaseAsset(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    downloadUrl = obj["browser_download_url"]?.jsonPrimitive?.content ?: "",
                    size = obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    contentType = obj["content_type"]?.jsonPrimitive?.content ?: "",
                )
            } ?: emptyList()

            return AppReleaseInfo(
                version = version,
                tagName = tagName,
                releaseNotes = releaseNotes,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                assets = assets,
            )
        }

        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            val remoteParts = parseVersionParts(remoteVersion)
            val currentParts = parseVersionParts(currentVersion)
            val maxLength = maxOf(remoteParts.size, currentParts.size)

            for (i in 0 until maxLength) {
                val remote = remoteParts.getOrElse(i) { 0 }
                val current = currentParts.getOrElse(i) { 0 }
                if (remote > current) return true
                if (remote < current) return false
            }
            return false
        }

        private fun parseVersionParts(v: String): List<Int> =
            v.removePrefix("v")
                .split('.', '-', '_')
                .mapNotNull { it.toIntOrNull() }

        fun findBestAsset(assets: List<AppReleaseAsset>, mode: DistributionMode): AppReleaseAsset? = when (mode) {
            DistributionMode.Portable -> assets.firstOrNull {
                it.name.contains("portable", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true)
            } ?: assets.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
            DistributionMode.Installed -> assets.firstOrNull {
                it.name.endsWith(".exe", ignoreCase = true) && !it.name.contains("portable", ignoreCase = true)
            } ?: assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
        }

        fun verifySha256(file: Path, expectedHash: String): Boolean {
            if (!Files.exists(file)) return false
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            return actualHash.equals(expectedHash.trim(), ignoreCase = true)
        }

        private fun defaultFetchText(url: String): String {
            val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "MihonW-Desktop/$CURRENT_VERSION")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            return connection.inputStream.bufferedReader().use { it.readText() }
        }

        private fun defaultDownloadStream(url: String): InputStream {
            val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "MihonW-Desktop/$CURRENT_VERSION")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            return connection.inputStream
        }
    }
}
