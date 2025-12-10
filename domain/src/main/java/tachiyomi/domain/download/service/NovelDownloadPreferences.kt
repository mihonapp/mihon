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

    companion object {
        /**
         * Represents a source-specific override for throttling settings
         */
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
