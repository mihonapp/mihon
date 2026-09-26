package mihon.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedRoute
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceRoute
import eu.kanade.presentation.more.settings.screen.SettingsBrowseRoute
import eu.kanade.presentation.more.settings.screen.SettingsDataRoute
import eu.kanade.presentation.more.settings.screen.SettingsDownloadRoute
import eu.kanade.presentation.more.settings.screen.SettingsLibraryRoute
import eu.kanade.presentation.more.settings.screen.SettingsReaderRoute
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityRoute
import eu.kanade.presentation.more.settings.screen.SettingsTrackingRoute
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.more.settings.screen.about.OpenSourceLicensesScreen
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.more.settings.screen.debug.BackupSchemaScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.presentation.more.settings.screen.debug.WorkerInfoScreen
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSourceSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialog
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.core.navigation.util.LocalBackStack
import mihon.core.navigation.util.OverlaySheetScene
import mihon.core.navigation.util.TwoPaneSettingsScene
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.list.MigrationListScreen
import mihon.feature.support.SupportUsScreen
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
    entry<TrackInfoDialogRoute>(metadata = OverlaySheetScene.overlaySheet()) { route ->
        val backStack = LocalBackStack.current
        TrackInfoDialog(
            mangaId = route.mangaId,
            mangaTitle = route.mangaTitle,
            sourceId = route.sourceId,
            onDismissRequest = { backStack.removeLastOrNull() },
        )
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
    entry<SettingsRoute>(metadata = TwoPaneSettingsScene.listPane()) {
        SettingsScreen()
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
