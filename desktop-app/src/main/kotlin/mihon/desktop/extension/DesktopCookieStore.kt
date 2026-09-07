package mihon.desktop.extension

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class DomainCookieConfig(
    val domain: String,
    val cookies: Map<String, String> = emptyMap(),
    val customUserAgent: String? = null,
)

class DesktopCookieStore(
    private val storagePath: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
) {
    private val memoryMap = ConcurrentHashMap<String, DomainCookieConfig>()

    init {
        loadFromDisk()
    }

    @Synchronized
    private fun loadFromDisk() {
        if (!Files.exists(storagePath)) return
        try {
            val content = Files.readString(storagePath)
            if (content.isNotBlank()) {
                val list = json.decodeFromString<List<DomainCookieConfig>>(content)
                memoryMap.clear()
                list.forEach { config ->
                    memoryMap[normalizeDomain(config.domain)] = config
                }
            }
        } catch (_: Exception) {
            // Safe fallback if file corrupted
        }
    }

    @Synchronized
    private fun saveToDisk() {
        try {
            Files.createDirectories(storagePath.parent)
            val list = memoryMap.values.toList().sortedBy { it.domain }
            val content = json.encodeToString(list)
            val temp = storagePath.resolveSibling("${storagePath.fileName}.tmp")
            Files.writeString(temp, content)
            Files.move(temp, storagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Ignore disk write failure
        }
    }

    fun getCookieHeader(domain: String): String? {
        val config = findMatchingConfig(domain) ?: return null
        if (config.cookies.isEmpty()) return null
        return config.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun getUserAgent(domain: String): String? {
        return findMatchingConfig(domain)?.customUserAgent?.takeIf { it.isNotBlank() }
    }

    fun getDomainConfig(domain: String): DomainCookieConfig? {
        return memoryMap[normalizeDomain(domain)]
    }

    fun setCookies(
        domain: String,
        cookies: Map<String, String>,
        customUserAgent: String? = null,
    ) {
        val norm = normalizeDomain(domain)
        val existing = memoryMap[norm]
        val ua = customUserAgent ?: existing?.customUserAgent
        memoryMap[norm] = DomainCookieConfig(
            domain = norm,
            cookies = cookies,
            customUserAgent = ua,
        )
        saveToDisk()
    }

    fun removeCookies(domain: String) {
        val norm = normalizeDomain(domain)
        if (memoryMap.remove(norm) != null) {
            saveToDisk()
        }
    }

    fun listAll(): List<DomainCookieConfig> {
        return memoryMap.values.toList().sortedBy { it.domain }
    }

    private fun findMatchingConfig(host: String): DomainCookieConfig? {
        val cleanHost = normalizeDomain(host)
        // 1. Exact match
        memoryMap[cleanHost]?.let { return it }

        // 2. Suffix / parent domain match (e.g. api.mangadex.org matches mangadex.org)
        for ((registeredDomain, config) in memoryMap) {
            val root = registeredDomain.removePrefix(".")
            if (cleanHost == root || cleanHost.endsWith(".$root")) {
                return config
            }
        }
        return null
    }

    companion object {
        fun normalizeDomain(raw: String): String {
            return raw.trim().lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .substringBefore(":")
                .removePrefix(".")
        }

        /**
         * Parses a raw cookie header string into key-value map.
         * e.g. "cf_clearance=abc123xyz; session_id=987654"
         */
        fun parseRawCookies(raw: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            raw.split(";").forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isNotEmpty() && trimmed.contains("=")) {
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim()
                    if (key.isNotEmpty()) {
                        result[key] = value
                    }
                }
            }
            return result
        }
    }
}
