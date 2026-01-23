package eu.kanade.tachiyomi.data.cache

import android.content.Context
import tachiyomi.core.common.util.system.logcat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import java.io.File
import java.io.IOException

/**
 * Cache for library settings data (extensions, tags) to avoid expensive DB queries
 */
class LibrarySettingsCache(private val context: Context) {

    private val cacheDir by lazy {
        File(context.filesDir, "library_cache").apply { mkdirs() }
    }

    private val extensionsFile by lazy { File(cacheDir, "extensions.json") }
    private val tagsFile by lazy { File(cacheDir, "tags.json") }

    private val json = Json { ignoreUnknownKeys = true }

    fun saveExtensions(extensions: List<Pair<Long, String>>) {
        try {
            val data = extensions.map { ExtensionData(it.first, it.second) }
            extensionsFile.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save extensions cache" }
        }
    }

    fun loadExtensions(): List<Pair<Long, String>>? {
        return try {
            if (!extensionsFile.exists()) return null
            val text = extensionsFile.readText()
            val data = json.decodeFromString<List<ExtensionData>>(text)
            data.map { it.id to it.name }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load extensions cache" }
            null
        }
    }

    fun saveTags(tags: List<Pair<String, Int>>, noTagsCount: Int) {
        try {
            val data = TagsCacheData(
                tags = tags.map { TagData(it.first, it.second) },
                noTagsCount = noTagsCount
            )
            tagsFile.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save tags cache" }
        }
    }

    fun loadTags(): Pair<List<Pair<String, Int>>, Int>? {
        return try {
            if (!tagsFile.exists()) return null
            val text = tagsFile.readText()
            val data = json.decodeFromString<TagsCacheData>(text)
            data.tags.map { it.name to it.count } to data.noTagsCount
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load tags cache" }
            null
        }
    }

    @Serializable
    private data class ExtensionData(
        val id: Long,
        val name: String
    )

    @Serializable
    private data class TagData(
        val name: String,
        val count: Int
    )

    @Serializable
    private data class TagsCacheData(
        val tags: List<TagData>,
        val noTagsCount: Int
    )
}
