package eu.kanade.tachiyomi.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedRoute
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceRoute
import eu.kanade.presentation.more.settings.screen.SettingsBrowseRoute
import eu.kanade.presentation.more.settings.screen.SettingsDataRoute
import eu.kanade.presentation.more.settings.screen.SettingsDownloadRoute
import eu.kanade.presentation.more.settings.screen.SettingsLibraryRoute
import eu.kanade.presentation.more.settings.screen.SettingsReaderRoute
import eu.kanade.presentation.more.settings.screen.SettingsSearchRoute
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityRoute
import eu.kanade.presentation.more.settings.screen.SettingsTrackingRoute
import eu.kanade.presentation.more.settings.screen.about.AboutRoute
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.more.settings.screen.about.OpenSourceLicensesRoute
import eu.kanade.presentation.more.settings.screen.about.OpenSourceLicensesScreen
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseRoute
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageRoute
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresRoute
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.more.settings.screen.data.CreateBackupRoute
import eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupRoute
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.more.settings.screen.debug.BackupSchemaRoute
import eu.kanade.presentation.more.settings.screen.debug.BackupSchemaScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoRoute
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.presentation.more.settings.screen.debug.WorkerInfoRoute
import eu.kanade.presentation.more.settings.screen.debug.WorkerInfoScreen
import eu.kanade.presentation.util.TwoPaneSettingsScene
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterRoute
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsRoute
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesRoute
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaRoute
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchRoute
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSourceSearchRoute
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSourceSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterRoute
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceRoute
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchRoute
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryRoute
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkRoute
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueRoute
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeRoute
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaRoute
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesRoute
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackChapterSelectorRoute
import eu.kanade.tachiyomi.ui.manga.track.TrackChapterSelectorScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackDateRemoverRoute
import eu.kanade.tachiyomi.ui.manga.track.TrackDateRemoverScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackDateSelectorRoute
import eu.kanade.tachiyomi.ui.manga.track.TrackDateSelectorScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeRoute
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackScoreSelectorRoute
import eu.kanade.tachiyomi.ui.manga.track.TrackScoreSelectorScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackStatusSelectorRoute
import eu.kanade.tachiyomi.ui.manga.track.TrackStatusSelectorScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackerRemoveRoute
import eu.kanade.tachiyomi.ui.manga.track.TrackerRemoveScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackerSearchRoute
import eu.kanade.tachiyomi.ui.more.NewUpdateRoute
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.more.OnboardingRoute
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.ui.setting.SettingsRoute
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsRoute
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewRoute
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.feature.migration.config.MigrationConfigRoute
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.list.MigrationListRoute
import mihon.feature.migration.list.MigrationListScreen
import mihon.feature.support.SupportUsRoute
import mihon.feature.support.SupportUsScreen
import mihon.feature.upcoming.UpcomingRoute
import mihon.feature.upcoming.UpcomingScreen

fun EntryProviderScope<NavKey>.appEntries() {
    entry<HomeRoute> {
        HomeScreen()
    }
    entry<DeepLinkRoute> { route ->
        DeepLinkScreen(route.query)
    }
    entry<WebViewRoute> { route ->
        WebViewScreen(
            url = route.url,
            initialTitle = route.initialTitle,
            sourceId = route.sourceId,
        )
    }

    // Entry
    entry<MangaNotesRoute> { route ->
        MangaNotesScreen(route.manga)
    }
    entry<MangaRoute> { route ->
        MangaScreen(route.mangaId, route.fromSource)
    }
    entry<TrackerRemoveRoute> { route ->
        TrackerRemoveScreen(route.mangaId, route.track, route.serviceId)
    }
    entry<TrackerSearchRoute> { route ->
        TrackerSearchRoute(route.mangaId, route.initialQuery, route.currentUrl, route.serviceId)
    }
    entry<TrackChapterSelectorRoute> { route ->
        TrackChapterSelectorScreen(route.track, route.serviceId)
    }
    entry<TrackDateRemoverRoute> { route ->
        TrackDateRemoverScreen(route.track, route.serviceId, route.start)
    }
    entry<TrackDateSelectorRoute> { route ->
        TrackDateSelectorScreen(route.track, route.serviceId, route.start)
    }
    entry<TrackInfoDialogHomeRoute> { route ->
        TrackInfoDialogHomeScreen(route.mangaId, route.mangaTitle, route.sourceId)
    }
    entry<TrackScoreSelectorRoute> { route ->
        TrackScoreSelectorScreen(route.track, route.serviceId)
    }
    entry<TrackStatusSelectorRoute> { route ->
        TrackStatusSelectorScreen(route.track, route.serviceId)
    }

    // Library tab
    entry<GlobalSearchRoute> { route ->
        GlobalSearchScreen(route.searchQuery, route.extensionFilter)
    }

    // Updates tab
    entry<UpcomingRoute> {
        UpcomingScreen()
    }

    // Browse tab
    entry<BrowseSourceRoute> { route ->
        BrowseSourceScreen(route.sourceId, route.listingQuery)
    }
    entry<ExtensionDetailsRoute> { route ->
        ExtensionDetailsScreen(route.pkgName)
    }
    entry<ExtensionFilterRoute> {
        ExtensionFilterScreen()
    }
    entry<MigrateMangaRoute> { route ->
        MigrateMangaScreen(route.sourceId)
    }
    entry<MigrateSearchRoute> { route ->
        MigrateSearchScreen(route.mangaId)
    }
    entry<MigrateSourceSearchRoute> { route ->
        MigrateSourceSearchScreen(route.currentManga, route.sourceId, route.query)
    }
    entry<MigrationConfigRoute> { route ->
        MigrationConfigScreen(route.mangaIds)
    }
    entry<MigrationListRoute> { route ->
        MigrationListScreen(route.mangaIds, route.extraSearchQuery)
    }
    entry<SourcesFilterRoute> {
        SourcesFilterScreen()
    }
    entry<SourcePreferencesRoute> { route ->
        SourcePreferencesScreen(route.sourceId)
    }

    // More tab
    entry<CategoryRoute> {
        CategoryScreen()
    }
    entry<DownloadQueueRoute> {
        DownloadQueueScreen()
    }
    entry<NewUpdateRoute> { route ->
        NewUpdateScreen(
            versionName = route.versionName,
            changelogInfo = route.changelogInfo,
            releaseLink = route.releaseLink,
            downloadLink = route.downloadLink,
        )
    }
    entry<OnboardingRoute> {
        OnboardingScreen()
    }
    entry<SettingsRoute>(metadata = TwoPaneSettingsScene.listPane()) { route ->
        SettingsScreen(route.settingsDestination)
    }
    entry<StatsRoute> {
        StatsScreen()
    }
    entry<SupportUsRoute> {
        SupportUsScreen()
    }

    // Settings
    entry<AboutRoute> {
        AboutScreen()
    }
    entry<AppLanguageRoute> {
        AppLanguageScreen()
    }
    entry<BackupSchemaRoute> {
        BackupSchemaScreen()
    }
    entry<ClearDatabaseRoute> {
        ClearDatabaseScreen()
    }
    entry<CreateBackupRoute> {
        CreateBackupScreen()
    }
    entry<DebugInfoRoute> {
        DebugInfoScreen()
    }
    entry<ExtensionStoresRoute> { route ->
        ExtensionStoresScreen(route.url)
    }
    entry<OpenSourceLicensesRoute> {
        OpenSourceLicensesScreen()
    }
    entry<RestoreBackupRoute> { route ->
        RestoreBackupScreen(route.uri)
    }
    entry<SettingsSearchRoute> {
        SettingsSearchScreen()
    }
    entry<WorkerInfoRoute> {
        WorkerInfoScreen()
    }

    // Searchable settings
    entry<SettingsAdvancedRoute> {
        SettingsAdvancedRoute.Content()
    }
    entry<SettingsAppearanceRoute> {
        SettingsAppearanceRoute.Content()
    }
    entry<SettingsBrowseRoute> {
        SettingsBrowseRoute.Content()
    }
    entry<SettingsDataRoute> {
        SettingsDataRoute.Content()
    }
    entry<SettingsDownloadRoute> {
        SettingsDownloadRoute.Content()
    }
    entry<SettingsLibraryRoute> {
        SettingsLibraryRoute.Content()
    }
    entry<SettingsReaderRoute> {
        SettingsReaderRoute.Content()
    }
    entry<SettingsSecurityRoute> {
        SettingsSecurityRoute.Content()
    }
    entry<SettingsTrackingRoute> {
        SettingsTrackingRoute.Content()
    }
}
