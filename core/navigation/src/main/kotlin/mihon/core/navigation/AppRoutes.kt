package mihon.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import mihon.core.navigation.domain.SettingsDestination
import tachiyomi.domain.manga.model.Manga

@Serializable
data object HomeRoute : NavKey

@Serializable
data class DeepLinkRoute(val query: String = "") : NavKey

@Serializable
data class WebViewRoute(
    val url: String,
    val initialTitle: String? = null,
    val sourceId: Long? = null,
) : NavKey, AssistContentRoute

// Entry
@Serializable
data class MangaNotesRoute(val manga: Manga) : NavKey

@Serializable
data class MangaRoute(
    val mangaId: Long,
    val fromSource: Boolean = false,
) : NavKey, AssistContentRoute

@Serializable
data class TrackInfoDialogRoute(
    val mangaId: Long,
    val mangaTitle: String,
    val sourceId: Long,
) : NavKey

// Library tab
@Serializable
data class GlobalSearchRoute(
    val searchQuery: String = "",
    val extensionFilter: String? = null,
) : NavKey

// Updates tab
@Serializable
data object UpcomingRoute : NavKey

// Browse tab
@Serializable
data class BrowseSourceRoute(
    val sourceId: Long,
    val listingQuery: String?,
) : NavKey, AssistContentRoute

@Serializable
data class ExtensionDetailsRoute(val pkgName: String) : NavKey

@Serializable
data object ExtensionFilterRoute : NavKey

@Serializable
data class MigrateMangaRoute(val sourceId: Long) : NavKey

@Serializable
data class MigrateSearchRoute(val mangaId: Long) : NavKey

@Serializable
data class MigrateSourceSearchRoute(
    val currentManga: Manga,
    val sourceId: Long,
    val query: String?,
) : NavKey

@Serializable
data class MigrationConfigRoute(val mangaIds: Collection<Long>) : NavKey {
    constructor(mangaId: Long) : this(listOf(mangaId))
}

@Serializable
data class MigrationListRoute(
    val mangaIds: Collection<Long>,
    val extraSearchQuery: String?,
) : NavKey

@Serializable
data object SourcesFilterRoute : NavKey

@Serializable
data class SourcePreferencesRoute(val sourceId: Long) : NavKey

// More tab
@Serializable
data object CategoryRoute : NavKey

@Serializable
data object DownloadQueueRoute : NavKey

@Serializable
data class NewUpdateRoute(
    val versionName: String,
    val changelogInfo: String,
    val releaseLink: String,
    val downloadLink: String,
) : NavKey

@Serializable
data object OnboardingRoute : NavKey

@Serializable
data class SettingsRoute(val settingsDestination: SettingsDestination? = null) : NavKey

@Serializable
data object StatsRoute : NavKey

@Serializable
data object SupportUsRoute : NavKey

// Settings
@Serializable
data object AboutRoute : NavKey

@Serializable
data object AppLanguageRoute : NavKey

@Serializable
data object BackupSchemaRoute : NavKey {
    const val TITLE = "Backup file schema"
}

@Serializable
data object ClearDatabaseRoute : NavKey

@Serializable
data object CreateBackupRoute : NavKey

@Serializable
data object DebugInfoRoute : NavKey

@Serializable
data class ExtensionStoresRoute(val url: String? = null) : NavKey

@Serializable
data object OpenSourceLicensesRoute : NavKey

@Serializable
data class RestoreBackupRoute(val uri: String) : NavKey

@Serializable
data object SettingsSearchRoute : NavKey

@Serializable
data object WorkerInfoRoute : NavKey {
    const val TITLE = "Worker info"
}
