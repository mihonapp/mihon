package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Preferences for novel download throttling and rate limiting.
 * These settings help avoid getting rate-limited by novel sources.
 */
class NovelDownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * Enable download throttling for novel sources
     */
    fun enableThrottling() = preferenceStore.getBoolean(
        "novel_download_throttling_enabled",
        true,
    )

    /**
     * Base delay between chapter downloads from the same source (in milliseconds)
     */
    fun downloadDelay() = preferenceStore.getInt(
        "novel_download_delay_ms",
        2000, // Default 2 seconds
    )

    /**
     * Random delay range to add to base delay (in milliseconds)
     * Actual delay = baseDelay + random(0, randomDelayRange)
     */
    fun randomDelayRange() = preferenceStore.getInt(
        "novel_download_random_delay_ms",
        1000, // Default 0-1 second random
    )

    /**
     * Enable delay between library updates for novel sources
     */
    fun enableUpdateThrottling() = preferenceStore.getBoolean(
        "novel_update_throttling_enabled",
        true,
    )

    /**
     * Delay between checking novels during library update (in milliseconds)
     */
    fun updateDelay() = preferenceStore.getInt(
        "novel_update_delay_ms",
        1500, // Default 1.5 seconds
    )

    /**
     * Maximum parallel library updates for novel sources (per extension)
     */
    fun parallelNovelUpdates() = preferenceStore.getInt(
        "novel_parallel_updates",
        2, // Default to 2 concurrent novel sources
    )

    /**
     * Enable delay for mass import operations
     */
    fun enableMassImportThrottling() = preferenceStore.getBoolean(
        "novel_mass_import_throttling_enabled",
        true,
    )

    /**
     * Delay between imports during mass import (in milliseconds)
     */
    fun massImportDelay() = preferenceStore.getInt(
        "novel_mass_import_delay_ms",
        1000, // Default 1 second
    )

    /**
     * Number of concurrent mass imports
     */
    fun parallelMassImport() = preferenceStore.getInt(
        "novel_parallel_mass_import",
        1, // Default 1
    )

    /**
     * Stored source-specific overrides as JSON string
     * Format: Map<sourceId: Long, SourceOverride>
     */
    fun sourceOverrides() = preferenceStore.getString(
        "novel_source_overrides",
        "{}",
    )

    /**
     * Maximum parallel downloads for novels (separate from manga)
     */
    fun parallelNovelDownloads() = preferenceStore.getInt(
        "novel_parallel_downloads",
        1, // Default to 1 for rate limiting
    )

    /**
     * Download images from chapter HTML and embed them as base64
     */
    fun downloadChapterImages() = preferenceStore.getBoolean(
        "novel_download_chapter_images",
        false,
    )

    /**
     * Maximum image size in KB before compression (0 = no limit)
     */
    fun maxImageSizeKb() = preferenceStore.getInt(
        "novel_max_image_size_kb",
        500, // Default 500KB
    )

    /**
     * Image compression quality (1-100)
     */
    fun imageCompressionQuality() = preferenceStore.getInt(
        "novel_image_compression_quality",
        80,
    )

    /**
     * Get source override for a specific source ID
     */
    fun getSourceOverride(sourceId: Long): SourceOverride? {
        return try {
            val json = sourceOverrides().get()
            if (json.isEmpty() || json == "{}") return null
            
            val overrides = kotlinx.serialization.json.Json.decodeFromString<Map<String, SourceOverride>>(json)
            overrides[sourceId.toString()]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Set source override for a specific source
     */
    fun setSourceOverride(override: SourceOverride) {
        try {
            val currentJson = sourceOverrides().get()
            val overrides = if (currentJson.isEmpty() || currentJson == "{}") {
                mutableMapOf()
            } else {
                kotlinx.serialization.json.Json.decodeFromString<MutableMap<String, SourceOverride>>(currentJson)
            }
            
            overrides[override.sourceId.toString()] = override
            val newJson = kotlinx.serialization.json.Json.encodeToString(overrides)
            sourceOverrides().set(newJson)
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Remove source override
     */
    fun removeSourceOverride(sourceId: Long) {
        try {
            val currentJson = sourceOverrides().get()
            if (currentJson.isEmpty() || currentJson == "{}") return
            
            val overrides = kotlinx.serialization.json.Json.decodeFromString<MutableMap<String, SourceOverride>>(currentJson)
            overrides.remove(sourceId.toString())
            
            val newJson = if (overrides.isEmpty()) {
                "{}"
            } else {
                kotlinx.serialization.json.Json.encodeToString(overrides)
            }
            sourceOverrides().set(newJson)
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Get all source overrides
     */
    fun getAllSourceOverrides(): List<SourceOverride> {
        return try {
            val json = sourceOverrides().get()
            if (json.isEmpty() || json == "{}") return emptyList()
            
            val overrides = kotlinx.serialization.json.Json.decodeFromString<Map<String, SourceOverride>>(json)
            overrides.values.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        /**
         * Represents a source-specific override for throttling settings
         */
        @kotlinx.serialization.Serializable
        data class SourceOverride(
            val sourceId: Long,
            val downloadDelay: Int? = null,
            val randomDelayRange: Int? = null,
            val updateDelay: Int? = null,
            val massImportDelay: Int? = null,
            val enabled: Boolean = true,
        )
    }
}
