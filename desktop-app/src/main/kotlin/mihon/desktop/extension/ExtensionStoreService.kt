package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.model.SourceDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

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
)

class ExtensionStoreService(
    private val preferenceStore: DesktopPreferenceStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    companion object {
        const val PREF_KEY_REPOSITORIES = "extension.repositories"
        const val DEFAULT_REPO = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Synchronized
    fun getRepositories(): List<String> {
        val stored = preferenceStore.property(PREF_KEY_REPOSITORIES)
        return if (stored.isNullOrBlank()) {
            listOf(DEFAULT_REPO)
        } else {
            stored.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
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

    private fun normalizeRepoUrl(raw: String): String {
        return raw.trim().removeSuffix("/").removeSuffix("/index.min.json")
    }

    suspend fun fetchRepository(repoUrl: String): List<ExtensionStoreItem> = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoUrl(repoUrl)
        val targetUrl = "$normalized/index.min.json"
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "MihonW/0.1.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to fetch extension index from $targetUrl: HTTP ${response.code}")
        }

        val body = response.body.string()
        parseIndex(body, normalized)
    }

    fun parseIndex(indexJson: String, repoUrl: String): List<ExtensionStoreItem> {
        val jsonArray = json.decodeFromString<List<JsonObject>>(indexJson)
        return jsonArray.mapNotNull { obj ->
            try {
                val pkg = obj["pkg"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: pkg
                val version = obj["version"]?.jsonPrimitive?.content ?: "1.0.0"
                val code = obj["code"]?.jsonPrimitive?.longOrNull ?: 1L
                val lang = obj["lang"]?.jsonPrimitive?.content ?: "en"
                val libVersion = obj["libVersion"]?.jsonPrimitive?.doubleOrNull ?: 1.4
                val isNsfw = (obj["nsfw"]?.jsonPrimitive?.content == "1") ||
                    (obj["isNsfw"]?.jsonPrimitive?.booleanOrNull == true)

                val apkOrMext = obj["apk"]?.jsonPrimitive?.content
                    ?: obj["downloadUrl"]?.jsonPrimitive?.content
                    ?: ""
                val downloadUrl = resolveUrl(repoUrl, apkOrMext)

                val icon = obj["icon"]?.jsonPrimitive?.content ?: ""
                val iconUrl = if (icon.isNotEmpty()) resolveUrl(repoUrl, icon) else ""

                val sha256 = obj["sha256"]?.jsonPrimitive?.content ?: ""

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
                )
            } catch (_: Exception) {
                null
            }
        }
    }

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

    suspend fun fetchAvailableExtensions(): List<ExtensionStoreItem> {
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
        return allItems.groupBy { it.pkg }.map { (_, items) ->
            items.maxByOrNull { it.versionCode }!!
        }.sortedBy { it.name }
    }
}
