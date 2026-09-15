package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.updates.DesktopAppUpdateService
import mihon.extension.model.SourceDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.GZIPInputStream

@Serializable
data class ExtensionStoreItem(
    val pkg: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val libVersion: Double = 1.4,
    val lang: String = "en",
    val isNsfw: Boolean = false,
    val sources: List<SourceDescriptor> = emptyList(),
    val downloadUrl: String = "",
    val iconUrl: String = "",
    val sha256: String = "",
    val repoUrl: String = "",
    val declaredDomains: List<String> = emptyList(),
    /** Repository signing key (certificate SHA-256 fingerprint) for this package, if known. */
    val signingKey: String = "",
    /** Set by the trust confirmation UI; causes the installer to persist explicit user trust. */
    val trustOnInstall: Boolean = false,
) {
    val signerFingerprint: String get() = signingKey
    val signingKeyFingerprint: String get() = signingKey
    val hasSigningKey: Boolean get() = signingKey.isNotBlank()
}

@OptIn(ExperimentalSerializationApi::class)
class ExtensionStoreService(
    private val preferenceStore: DesktopPreferenceStore,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        const val PREF_KEY_REPOSITORIES = "extension.repositories"

        /** Desktop can't install these promotional/meta packages; hide them from the store. */
        val HIDDEN_EXTENSION_PACKAGES = setOf(
            "eu.kanade.tachiyomi.extension.all.keiyoushi",
            "eu.kanade.tachiyomi.extension.all.mihon",
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Synchronized
    fun getRepositories(): List<String> {
        val stored = preferenceStore.property(PREF_KEY_REPOSITORIES)
        return stored.orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    @Synchronized
    fun addRepository(repoUrl: String) {
        val normalized = normalizeRepoUrl(repoUrl)
        val current = getRepositories().toMutableList()
        if (!current.contains(normalized)) {
            current.add(normalized)
            preferenceStore.update {
                setProperty(PREF_KEY_REPOSITORIES, current.joinToString("\n"))
            }
        }
    }

    @Synchronized
    fun removeRepository(repoUrl: String) {
        val normalized = normalizeRepoUrl(repoUrl)
        val current = getRepositories().toMutableList()
        if (current.remove(normalized)) {
            preferenceStore.update {
                setProperty(PREF_KEY_REPOSITORIES, current.joinToString("\n"))
            }
        }
    }

    fun normalizeRepoUrl(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        // Strip any known index file suffixes so the user can paste full URLs
        val knownSuffixes = listOf("/index.min.json", "/index.json", "/index.pb", "/repo.json")
        for (suffix in knownSuffixes) {
            if (url.endsWith(suffix)) {
                url = url.removeSuffix(suffix)
                break
            }
        }
        // Convert github.com/<user>/<repo>/(raw|tree)/<branch> to raw.githubusercontent.com/<user>/<repo>/<branch>
        val githubRawRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/(?:raw|tree)/(.+)$""")
        val match = githubRawRegex.matchEntire(url)
        if (match != null) {
            val (user, repo, branch) = match.destructured
            url = "https://raw.githubusercontent.com/$user/$repo/$branch"
        }
        return url.removeSuffix("/")
    }

    /**
     * Fetch extensions from a repository URL.
     *
     * Mirrors the Android Mihon strategy:
     * 1. Try fetching `index.pb` (protobuf, possibly gzip-compressed) first
     * 2. Decompress gzip if detected (magic bytes 0x1f 0x8b)
     * 3. Ignore an optional UTF-8 BOM and JSON whitespace before sniffing `[`; otherwise parse protobuf
     * 4. Fall back to `index.min.json` if `index.pb` fails
     */
    suspend fun fetchRepository(repoUrl: String): List<ExtensionStoreItem> = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoUrl(repoUrl)

        // Try index.pb first (modern protobuf format); it carries its own signingKey.
        val pbResult = tryFetchAndParse(normalized, "$normalized/index.pb")
        if (pbResult != null) return@withContext applyLegacySigningKey(normalized, pbResult)

        // Fall back to index.min.json (legacy JSON format)
        val jsonResult = tryFetchAndParse(normalized, "$normalized/index.min.json")
        if (jsonResult != null) return@withContext applyLegacySigningKey(normalized, jsonResult)

        throw IllegalStateException(
            "Failed to fetch extension index from $normalized: neither index.pb nor index.min.json available",
        )
    }

    /**
     * Legacy repositories publish their signing key separately in repo.json. Fetch it only when
     * the index itself does not carry usable signing metadata for every extension.
     */
    private fun applyLegacySigningKey(
        repoUrl: String,
        items: List<ExtensionStoreItem>,
    ): List<ExtensionStoreItem> {
        if (items.none { !it.hasUsableSigningKey() }) return items
        val legacySigningKey = fetchLegacySigningKey(repoUrl)
        if (legacySigningKey.isBlank()) return items
        return items.map { item ->
            if (item.hasUsableSigningKey()) item else item.copy(signingKey = legacySigningKey)
        }
    }

    /**
     * Fetch a legacy `repo.json` and return its signing key fingerprint, if present.
     * Failures are intentionally ignored so repositories without repo.json still work.
     */
    private fun fetchLegacySigningKey(repoUrl: String): String {
        val request = Request.Builder()
            .url("$repoUrl/repo.json")
            .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: Exception) {
            return ""
        }
        if (!response.isSuccessful) {
            response.close()
            return ""
        }
        return try {
            val body = decompressIfGzipped(response.body.bytes()).decodeToString()
            val obj = json.parseToJsonElement(body).jsonObject
            val meta = obj["meta"]?.jsonObject
            obj["signingKey"]?.jsonPrimitive?.content
                ?: obj["signingKeyFingerprint"]?.jsonPrimitive?.content
                ?: meta?.get("signingKeyFingerprint")?.jsonPrimitive?.content
                ?: meta?.get("signingKey")?.jsonPrimitive?.content
                ?: ""
        } catch (_: Exception) {
            ""
        } finally {
            response.close()
        }
    }

    /**
     * Fetch a URL and auto-detect the format (protobuf vs JSON) based on content sniffing.
     * Returns null if the HTTP request fails (e.g. 404).
     */
    private fun tryFetchAndParse(
        repoBaseUrl: String,
        targetUrl: String,
        defaultSigningKey: String = "",
    ): List<ExtensionStoreItem>? {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: Exception) {
            return null
        }

        if (!response.isSuccessful) {
            response.close()
            return null
        }

        return response.use {
            val decompressed = decompressIfGzipped(it.body.bytes())
            if (decompressed.isEmpty()) return@use null

            val jsonPayload = decompressed.jsonPayloadOrNull()
            if (jsonPayload != null) {
                if (jsonPayload.firstOrNull() == '[') {
                    parseIndex(jsonPayload, repoBaseUrl, defaultSigningKey)
                } else {
                    parseNetworkStore(json.decodeFromString(jsonPayload), repoBaseUrl)
                }
            } else {
                parseProtobufIndex(decompressed, repoBaseUrl)
            }
        }
    }

    private fun ByteArray.jsonPayloadOrNull(): String? {
        var offset = if (
            size >= 3 &&
            this[0] == 0xEF.toByte() &&
            this[1] == 0xBB.toByte() &&
            this[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        while (offset < size && this[offset].toInt().toChar().isWhitespace()) offset++
        if (offset >= size || (this[offset] != '['.code.toByte() && this[offset] != '{'.code.toByte())) return null
        return decodeToString(offset, size)
    }

    /**
     * Decompress gzip data if the magic bytes (0x1f 0x8b) are detected.
     */
    private fun decompressIfGzipped(data: ByteArray): ByteArray {
        if (data.size < 2) return data
        val isGzip = data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
        if (!isGzip) return data

        return try {
            GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        } catch (_: Exception) {
            data // Return original if decompression fails
        }
    }

    /**
     * Parse a protobuf-encoded [DesktopNetworkExtensionStore] and convert to [ExtensionStoreItem] list.
     */
    fun parseProtobufIndex(data: ByteArray, repoBaseUrl: String): List<ExtensionStoreItem> {
        val store = ProtoBuf.decodeFromByteArray<DesktopNetworkExtensionStore>(data)
        return parseNetworkStore(store, repoBaseUrl)
    }

    private fun parseNetworkStore(
        store: DesktopNetworkExtensionStore,
        repoBaseUrl: String,
    ): List<ExtensionStoreItem> {
        // If the store has a separate extensionListUrl, fetch that too
        val extensionList = store.extensionList
            ?: store.extensionListUrl?.let { listUrl ->
                val listRequest = Request.Builder()
                    .url(resolveUrl(repoBaseUrl, listUrl))
                    .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
                    .build()
                httpClient.newCall(listRequest).execute().use { listResponse ->
                    if (listResponse.isSuccessful) {
                        val listBytes = decompressIfGzipped(listResponse.body.bytes())
                        val jsonPayload = listBytes.jsonPayloadOrNull()
                        if (jsonPayload != null) {
                            json.decodeFromString<DesktopNetworkExtensionStore.ExtensionList>(jsonPayload)
                        } else {
                            ProtoBuf.decodeFromByteArray<DesktopNetworkExtensionStore.ExtensionList>(listBytes)
                        }
                    } else {
                        null
                    }
                }
            }

        if (extensionList == null) return emptyList()

        return extensionList.extensions.mapNotNull { ext ->
            try {
                val pkg = ext.packageName
                if (pkg.isBlank() || pkg in HIDDEN_EXTENSION_PACKAGES) return@mapNotNull null

                val lang = ext.sources.map { it.language }.toSet()
                val isNsfw = ext.contentWarning >= DesktopNetworkExtensionStore.ContentWarning.MIXED

                val sources = ext.sources.map { src ->
                    SourceDescriptor(
                        id = src.id,
                        name = src.name,
                        lang = src.language,
                        className = "",
                        supportsLatest = true,
                    )
                }

                val downloadUrl = when {
                    ext.resources.jarUrl.isNotBlank() -> resolveUrl(repoBaseUrl, ext.resources.jarUrl)
                    ext.resources.apkUrl.isNotBlank() -> resolveUrl(repoBaseUrl, ext.resources.apkUrl)
                    else -> ""
                }
                val iconUrl = if (ext.resources.iconUrl.isNotBlank()) {
                    resolveUrl(repoBaseUrl, ext.resources.iconUrl)
                } else if (pkg.isNotEmpty()) {
                    resolveUrl(repoBaseUrl, "icon/$pkg.png")
                } else {
                    ""
                }

                ExtensionStoreItem(
                    pkg = pkg,
                    name = ext.name,
                    version = ext.versionName,
                    versionCode = ext.versionCode,
                    libVersion = ext.extensionLib.toDoubleOrNull() ?: 1.4,
                    lang = if (lang.size == 1) lang.first() else "all",
                    isNsfw = isNsfw,
                    sources = sources,
                    downloadUrl = downloadUrl,
                    iconUrl = iconUrl,
                    repoUrl = repoBaseUrl,
                    signingKey = store.signingKey,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun parseIndex(
        indexJson: String,
        repoUrl: String,
        defaultSigningKey: String = "",
    ): List<ExtensionStoreItem> {
        val jsonArray = json.decodeFromString<List<JsonObject>>(indexJson)
        return jsonArray.mapNotNull { obj ->
            try {
                val pkg = obj["pkg"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (pkg in HIDDEN_EXTENSION_PACKAGES) {
                    return@mapNotNull null
                }
                val name = obj["name"]?.jsonPrimitive?.content ?: pkg
                val version = obj["version"]?.jsonPrimitive?.content ?: "1.0.0"
                val code = obj["code"]?.jsonPrimitive?.longOrNull ?: 1L
                val lang = obj["lang"]?.jsonPrimitive?.content ?: "en"
                val libVersion = obj["libVersion"]?.jsonPrimitive?.doubleOrNull ?: 1.4
                val isNsfw = (obj["nsfw"]?.jsonPrimitive?.content == "1") ||
                    (obj["isNsfw"]?.jsonPrimitive?.booleanOrNull == true)

                // Legacy repos (keiyoushi, etc.) store APKs under /apk/ subdirectory.
                // The "apk" field is a bare filename; "downloadUrl" is already a full path/URL.
                val apkField = obj["apk"]?.jsonPrimitive?.content
                val downloadUrlField = obj["downloadUrl"]?.jsonPrimitive?.content
                val downloadUrl = when {
                    apkField != null -> resolveUrl(repoUrl, "apk/$apkField")
                    downloadUrlField != null -> resolveUrl(repoUrl, downloadUrlField)
                    else -> ""
                }

                // Legacy repos store icons under /icon/{pkg}.png
                val iconField = obj["icon"]?.jsonPrimitive?.content
                val iconUrl = if (iconField != null) {
                    resolveUrl(repoUrl, iconField)
                } else if (pkg.isNotEmpty()) {
                    resolveUrl(repoUrl, "icon/$pkg.png")
                } else {
                    ""
                }

                val sha256 = obj["sha256"]?.jsonPrimitive?.content ?: ""
                val signingKey = obj["signingKey"]?.jsonPrimitive?.content?.takeIf { it.hasUsableSigningKeyValue() }
                    ?: obj["signingKeyFingerprint"]?.jsonPrimitive?.content?.takeIf { it.hasUsableSigningKeyValue() }
                    ?: defaultSigningKey.takeIf { it.hasUsableSigningKeyValue() } ?: ""

                val sources = obj["sources"]?.jsonArray?.mapNotNull { sObj ->
                    val s = sObj.jsonObject
                    val sId = s["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val sName = s["name"]?.jsonPrimitive?.content ?: ""
                    val sLang = s["lang"]?.jsonPrimitive?.content ?: lang
                    val sClass = s["className"]?.jsonPrimitive?.content
                        ?: s["class"]?.jsonPrimitive?.content
                        ?: ""
                    val sLatest = s["supportsLatest"]?.jsonPrimitive?.booleanOrNull ?: true
                    SourceDescriptor(sId, sName, sLang, sClass, sLatest)
                } ?: emptyList()

                val declaredDomains = obj["declaredDomains"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: emptyList()

                ExtensionStoreItem(
                    pkg = pkg,
                    name = name,
                    version = version,
                    versionCode = code,
                    libVersion = libVersion,
                    lang = lang,
                    isNsfw = isNsfw,
                    sources = sources,
                    downloadUrl = downloadUrl,
                    iconUrl = iconUrl,
                    sha256 = sha256,
                    repoUrl = repoUrl,
                    declaredDomains = declaredDomains,
                    signingKey = signingKey,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun ExtensionStoreItem.hasUsableSigningKey(): Boolean =
        signingKey.hasUsableSigningKeyValue()

    private fun String.hasUsableSigningKeyValue(): Boolean =
        isNotBlank() && !equals("NO_SIGNING_KEY", ignoreCase = true)

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative
        }
        return try {
            val baseUri = URI(if (base.endsWith("/")) base else "$base/")
            baseUri.resolve(relative.removePrefix("/")).toString()
        } catch (_: Exception) {
            "$base/$relative"
        }
    }

    suspend fun fetchAvailableExtensions(): List<ExtensionStoreItem> = withContext(Dispatchers.IO) {
        val repos = getRepositories()
        val allItems = mutableListOf<ExtensionStoreItem>()
        for (repo in repos) {
            try {
                val items = fetchRepository(repo)
                allItems.addAll(items)
            } catch (_: Exception) {
                // Ignore failure in one repo, keep others
            }
        }
        // Deduplicate by package name, taking the highest versionCode
        allItems.groupBy { it.pkg }.map { (_, items) ->
            items.maxByOrNull { it.versionCode }!!
        }.sortedBy { it.name }
    }
}
