package eu.kanade.tachiyomi.data.translation

import android.content.Context
import androidx.collection.LruCache
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Thread-safe persistent and memory cache for live translated pages.
 * Ensures zero-latency display when re-reading or pre-translating manga pages.
 */
object TranslatedPageCache {

    @Volatile
    private var cacheDir: File? = null

    // Holds up to 15 recent pages in RAM (~15-30MB) for instantaneous rendering
    private val memoryCache = object : LruCache<String, ByteArray>(15) {}

    fun init(context: Context) {
        if (cacheDir == null) {
            try {
                cacheDir = File(context.cacheDir, "translated_pages").apply { mkdirs() }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to initialize TranslatedPageCache directory" }
            }
        }
    }

    private const val CACHE_VERSION = "v4"

    private fun buildKey(mangaId: Long, chapterId: Long, pageIndex: Int, language: String, arabicStyle: String = ""): String {
        val stylePart = if (arabicStyle.isNotBlank()) "_$arabicStyle" else ""
        return "${CACHE_VERSION}_${mangaId}_${chapterId}_${pageIndex}_$language$stylePart"
    }

    fun get(mangaId: Long, chapterId: Long, pageIndex: Int, language: String, arabicStyle: String = ""): ByteArray? {
        val key = buildKey(mangaId, chapterId, pageIndex, language, arabicStyle)

        // 1. Check RAM memory cache
        memoryCache.get(key)?.let { return it }

        // 2. Check persistent disk cache
        val dir = cacheDir ?: return null
        val file = File(dir, "$key.bin")
        if (file.exists() && file.length() > 0) {
            return try {
                val bytes = file.readBytes()
                memoryCache.put(key, bytes)
                bytes
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to read cached translated page $key" }
                null
            }
        }
        return null
    }

    fun put(mangaId: Long, chapterId: Long, pageIndex: Int, language: String, bytes: ByteArray, arabicStyle: String = "") {
        val key = buildKey(mangaId, chapterId, pageIndex, language, arabicStyle)
        memoryCache.put(key, bytes)

        val dir = cacheDir ?: return
        try {
            val file = File(dir, "$key.bin")
            file.writeBytes(bytes)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to write translated page to cache $key" }
        }
    }

    fun has(mangaId: Long, chapterId: Long, pageIndex: Int, language: String, arabicStyle: String = ""): Boolean {
        val key = buildKey(mangaId, chapterId, pageIndex, language, arabicStyle)
        if (memoryCache.get(key) != null) return true
        val dir = cacheDir ?: return false
        val file = File(dir, "$key.bin")
        return file.exists() && file.length() > 0
    }

    fun clearChapter(chapterId: Long) {
        val dir = cacheDir ?: return
        try {
            dir.listFiles()?.filter { it.name.contains("_${chapterId}_") }?.forEach { it.delete() }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to clear cache for chapter $chapterId" }
        }
    }

    fun clearAll() {
        memoryCache.evictAll()
        try {
            cacheDir?.deleteRecursively()
            cacheDir?.mkdirs()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to clear all translated page cache" }
        }
    }

    fun getDiskCacheSizeBytes(): Long {
        val dir = cacheDir ?: return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    fun getDiskCacheSizeMB(): Float {
        return getDiskCacheSizeBytes() / (1024f * 1024f)
    }

    fun getCachedPagesCount(): Int {
        val dir = cacheDir ?: return 0
        return try {
            dir.listFiles()?.count { it.isFile && it.extension == "bin" } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
