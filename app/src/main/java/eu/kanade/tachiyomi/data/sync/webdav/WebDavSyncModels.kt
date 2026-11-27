package eu.kanade.tachiyomi.data.sync.webdav

import kotlinx.serialization.Serializable

data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remotePath: String,
)

data class WebDavSyncOptions(
    val syncLibrary: Boolean = true,
    val syncExtensionStores: Boolean = true,
    val syncExtensions: Boolean = true,
) {
    fun hasAnyEnabled(): Boolean = syncLibrary || syncExtensionStores || syncExtensions
}

data class WebDavSyncSummary(
    val localChanged: Boolean,
    val remoteChanged: Boolean,
    val missingExtensions: List<SyncInstalledExtension>,
)

@Serializable
data class WebDavSyncSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val updatedAt: Long = 0L,
    val deviceId: String = "",
    val categories: List<SyncCategory> = emptyList(),
    val manga: List<SyncManga> = emptyList(),
    val sources: List<SyncSource> = emptyList(),
    val extensionStores: List<SyncExtensionStore> = emptyList(),
    val installedExtensions: List<SyncInstalledExtension> = emptyList(),
) {
    fun normalizedForComparison(): WebDavSyncSnapshot = copy(
        updatedAt = 0L,
        deviceId = "",
        categories = categories.sortedWith(compareBy<SyncCategory> { it.order }.thenBy { it.name }),
        manga = manga
            .map { it.normalized() }
            .sortedWith(compareBy<SyncManga> { it.source }.thenBy { it.url }),
        sources = sources.sortedBy { it.sourceId },
        extensionStores = extensionStores.sortedBy { it.indexUrl },
        installedExtensions = installedExtensions.sortedBy { it.pkgName },
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class SyncCategory(
    val name: String,
    val order: Long,
    val flags: Long = 0L,
)

@Serializable
data class SyncManga(
    val source: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long = 0L,
    val thumbnailUrl: String? = null,
    val updateStrategy: Int = 0,
    val initialized: Boolean = false,
    val favoriteAt: Long? = null,
    val viewerFlags: Long = 0L,
    val chapterFlags: Long = 0L,
    val notes: String = "",
    val categories: List<String> = emptyList(),
    val excludedScanlators: List<String> = emptyList(),
    val chapters: List<SyncChapter> = emptyList(),
    val history: List<SyncHistory> = emptyList(),
    val tracks: List<SyncTrack> = emptyList(),
) {
    val favorite: Boolean
        get() = favoriteAt != null

    fun key(): SyncMangaKey = SyncMangaKey(source, url)

    fun normalized(): SyncManga = copy(
        categories = categories.distinct().sorted(),
        excludedScanlators = excludedScanlators.distinct().sorted(),
        chapters = chapters.sortedBy { it.url },
        history = history.sortedBy { it.chapterUrl },
        tracks = tracks.sortedBy { it.trackerId },
    )
}

data class SyncMangaKey(
    val source: Long,
    val url: String,
)

@Serializable
data class SyncChapter(
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val dateFetch: Long = 0L,
    val dateUpload: Long? = null,
    val chapterNumber: Double = -1.0,
    val sourceOrder: Long = 0L,
)

@Serializable
data class SyncHistory(
    val chapterUrl: String,
    val readAt: Long? = null,
    val readDuration: Long = 0L,
)

@Serializable
data class SyncTrack(
    val trackerId: Long,
    val remoteId: Long,
    val libraryId: Long? = null,
    val title: String,
    val lastChapterRead: Double = 0.0,
    val totalChapters: Long = 0L,
    val status: Long = 0L,
    val score: Double = 0.0,
    val remoteUrl: String = "",
    val startDate: Long? = null,
    val finishDate: Long? = null,
    val private: Boolean = false,
)

@Serializable
data class SyncSource(
    val sourceId: Long,
    val name: String,
)

@Serializable
data class SyncExtensionStore(
    val indexUrl: String,
    val name: String,
    val badgeLabel: String = "",
    val signingKey: String,
    val website: String = "",
    val discord: String? = null,
    val isLegacy: Boolean = false,
    val extensionListUrl: String? = null,
)

@Serializable
data class SyncInstalledExtension(
    val pkgName: String,
    val name: String,
    val lang: String? = null,
    val versionCode: Long = 0L,
    val versionName: String = "",
    val storeIndexUrl: String? = null,
    val signingKey: String? = null,
)
