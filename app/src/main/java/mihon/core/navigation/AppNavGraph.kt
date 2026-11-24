package mihon.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
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
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import eu.kanade.tachiyomi.ui.anilistwishlist.AnilistWishlistScreen
import eu.kanade.tachiyomi.ui.anilistwishlist.sources.AnilistWishlistSourcesScreen
import eu.kanade.tachiyomi.ui.anilistwishlist.search.AnilistWishlistSearchScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
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
        val navigator = LocalNavigator.current
        LocalAssistContentManager.current.Provide(route) {
            WebViewScreen(route, onNavigateUp = navigator::pop)
        }
    }

    entry<MangaNotesRoute> { route ->
        MangaNotesScreen(route.mangaId)
    }
    entry<MangaRoute> { route ->
        LocalAssistContentManager.current.Provide(route) {
            MangaScreen(route.mangaId, route.fromSource)
        }
    }
    entry<TrackInfoDialogRoute>(metadata = OverlaySheetScene.overlaySheet()) { route ->
        val navigator = LocalNavigator.current
        TrackInfoDialog(
            mangaId = route.mangaId,
            mangaTitle = route.mangaTitle,
            sourceId = route.sourceId,
            onDismissRequest = { navigator.pop() },
        )
    }

    entry<GlobalSearchRoute> { route ->
        GlobalSearchScreen(route.searchQuery, route.extensionFilter)
    }

    entry<UpcomingRoute> {
        UpcomingScreen()
    }

    entry<BrowseSourceRoute> { route ->
        LocalAssistContentManager.current.Provide(route) {
            BrowseSourceScreen(route.sourceId, route.listingQuery)
        }
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
        MigrateSourceSearchScreen(route.currentMangaId, route.sourceId, route.query)
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

    entry<CategoryRoute> {
        CategoryScreen()
    }
    entry<DownloadQueueRoute> {
        DownloadQueueScreen()
    }
    entry<NewUpdateRoute>(metadata = TwoPaneSettingsScene.fullScreen()) { route ->
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
    entry<SettingsMainScreen>(metadata = TwoPaneSettingsScene.listPane()) {
        SettingsMainScreen.Content()
    }
    entry<StatsRoute> {
        StatsScreen()
    }
    entry<AnilistWishlistRoute> {
        AnilistWishlistScreen()
    }
    entry<AnilistWishlistSourcesRoute> { route ->
        AnilistWishlistSourcesScreen(
            mediaId = route.mediaId,
            query = route.query,
            englishTitle = route.englishTitle,
            romajiTitle = route.romajiTitle,
        )
    }
    entry<AnilistWishlistSearchRoute> { route ->
        AnilistWishlistSearchScreen(
            mediaId = route.mediaId,
            sourceId = route.sourceId,
            query = route.query,
            englishTitle = route.englishTitle,
            romajiTitle = route.romajiTitle,
        )
    }
    entry<SupportUsRoute>(metadata = TwoPaneSettingsScene.fullScreen()) {
        SupportUsScreen()
    }

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
    entry<ExtensionStoresRoute> {
        ExtensionStoresScreen()
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

    entry<SettingsAdvancedScreen> {
        SettingsAdvancedScreen.Content()
    }
    entry<SettingsAppearanceScreen> {
        SettingsAppearanceScreen.Content()
    }
    entry<SettingsBrowseScreen> {
        SettingsBrowseScreen.Content()
    }
    entry<SettingsDataScreen> {
        SettingsDataScreen.Content()
    }
    entry<SettingsDownloadScreen> {
        SettingsDownloadScreen.Content()
    }
    entry<SettingsLibraryScreen> {
        SettingsLibraryScreen.Content()
    }
    entry<SettingsReaderScreen> {
        SettingsReaderScreen.Content()
    }
    entry<SettingsSecurityScreen> {
        SettingsSecurityScreen.Content()
    }
    entry<SettingsTrackingScreen> {
        SettingsTrackingScreen.Content()
    }
}






