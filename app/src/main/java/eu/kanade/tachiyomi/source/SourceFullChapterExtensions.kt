package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Extensions and utilities for Source full chapter download functionality.
 */

/**
 * Checks if the given source supports full chapter downloads and has it enabled in preferences.
 *
 * @param source the source to check
 * @return true if the source supports full chapter downloads and it's enabled, false otherwise
 */
fun isFullChapterDownloadEnabled(source: Source): Boolean {
    // Check if source supports full chapter downloads
    val supportsFullChapter = when (source) {
        is FullChapterSource -> source.supportsFullChapterDownload()
        is HttpSource -> source.supportsFullChapterDownload()
        else -> false
    }

    if (!supportsFullChapter) {
        return false
    }

    // Check if the feature is enabled in source preferences
    return if (source is ConfigurableSource) {
        source.sourcePreferences().getBoolean(FULL_CHAPTER_DOWNLOAD_PREF_KEY, false)
    } else {
        false
    }
}

/**
 * Gets the full chapter download capability for a source.
 * This is used by the download system to determine the download method.
 *
 * @param source the source to check
 * @return FullChapterCapability indicating the source's capability and preference state
 */
fun getFullChapterCapability(source: Source): FullChapterCapability {
    val supportsFullChapter = when (source) {
        is FullChapterSource -> source.supportsFullChapterDownload()
        is HttpSource -> source.supportsFullChapterDownload()
        else -> false
    }

    if (!supportsFullChapter) {
        return FullChapterCapability.NOT_SUPPORTED
    }

    val isEnabled = if (source is ConfigurableSource) {
        source.sourcePreferences().getBoolean(FULL_CHAPTER_DOWNLOAD_PREF_KEY, false)
    } else {
        false
    }

    return if (isEnabled) {
        FullChapterCapability.ENABLED
    } else {
        FullChapterCapability.DISABLED
    }
}

/**
 * Represents the full chapter download capability of a source.
 */
enum class FullChapterCapability {
    /** Source does not support full chapter downloads */
    NOT_SUPPORTED,

    /** Source supports full chapter downloads but it's disabled in preferences */
    DISABLED,

    /** Source supports full chapter downloads and it's enabled in preferences */
    ENABLED,
}

/**
 * Extension function to get the full chapter download response from a source.
 * This handles both FullChapterSource and HttpSource implementations.
 *
 * @param chapter the chapter to download
 * @return Response containing the full chapter archive
 * @throws UnsupportedOperationException if the source doesn't support full chapter downloads
 */
suspend fun Source.getFullChapterResponse(chapter: eu.kanade.tachiyomi.source.model.SChapter): okhttp3.Response {
    return when (this) {
        is FullChapterSource -> getFullChapter(chapter)
        is HttpSource -> getFullChapter(chapter)
        else -> throw UnsupportedOperationException("Source does not support full chapter downloads")
    }
}
