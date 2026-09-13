package mihon.desktop.i18n

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import mihon.desktop.navigation.DesktopDestination
import org.junit.jupiter.api.Test
import java.util.Locale

class DesktopStringsTest {

    @Test
    fun `AppLanguage parses code accurately with fallback to System`() {
        AppLanguage.fromCode("system") shouldBe AppLanguage.System
        AppLanguage.fromCode("zh-CN") shouldBe AppLanguage.SimplifiedChinese
        AppLanguage.fromCode("ZH-CN") shouldBe AppLanguage.SimplifiedChinese
        AppLanguage.fromCode("zh-TW") shouldBe AppLanguage.TraditionalChinese
        AppLanguage.fromCode("en") shouldBe AppLanguage.English
        AppLanguage.fromCode(null) shouldBe AppLanguage.System
        AppLanguage.fromCode("unknown") shouldBe AppLanguage.System
    }

    @Test
    fun `resolve returns Simplified Chinese when system locale is Chinese PRC`() {
        val strings = DesktopStrings.resolve(AppLanguage.System, Locale.SIMPLIFIED_CHINESE)
        strings shouldBe SimplifiedChineseStrings
        strings.destinationLabel(DesktopDestination.Library) shouldBe "书架"
        strings.destinationLabel(DesktopDestination.Settings) shouldBe "设置"
        strings.settingsLanguageSimplifiedChinese shouldBe "简体中文"
    }

    @Test
    fun `resolve returns Traditional Chinese when system locale is Chinese Taiwan or Hong Kong`() {
        val twStrings = DesktopStrings.resolve(AppLanguage.System, Locale.TRADITIONAL_CHINESE)
        twStrings shouldBe TraditionalChineseStrings
        twStrings.destinationLabel(DesktopDestination.Library) shouldBe "書架"
        twStrings.destinationLabel(DesktopDestination.Settings) shouldBe "設定"

        val hkStrings = DesktopStrings.resolve(AppLanguage.System, Locale("zh", "HK"))
        hkStrings shouldBe TraditionalChineseStrings
    }

    @Test
    fun `resolve returns English when system locale is English or other language`() {
        val strings = DesktopStrings.resolve(AppLanguage.System, Locale.US)
        strings shouldBe EnglishStrings
        strings.destinationLabel(DesktopDestination.Library) shouldBe "Library"
        strings.destinationLabel(DesktopDestination.Settings) shouldBe "Settings"
    }

    @Test
    fun `explicit language preference overrides system locale`() {
        val strings = DesktopStrings.resolve(AppLanguage.SimplifiedChinese, Locale.US)
        strings shouldBe SimplifiedChineseStrings

        val engStrings = DesktopStrings.resolve(AppLanguage.English, Locale.SIMPLIFIED_CHINESE)
        engStrings shouldBe EnglishStrings
    }

    @Test
    fun `all language bundles have non-blank core strings`() {
        listOf(EnglishStrings, SimplifiedChineseStrings, TraditionalChineseStrings).forEach { bundle ->
            bundle.appName.shouldNotBeBlank()
            bundle.libraryTitle.shouldNotBeBlank()
            bundle.libraryImportBackup.shouldNotBeBlank()
            bundle.settingsTitle.shouldNotBeBlank()
            bundle.aboutTitle.shouldNotBeBlank()
            bundle.historyTitle.shouldNotBeBlank()
            bundle.downloadsTitle.shouldNotBeBlank()
            bundle.updatesTitle.shouldNotBeBlank()
            bundle.browseTitle.shouldNotBeBlank()
            bundle.readerColorFilter.shouldNotBeBlank()
            bundle.readerBackgroundColor.shouldNotBeBlank()
            bundle.readerCropBorders.shouldNotBeBlank()
            bundle.readerCropBordersWebtoon.shouldNotBeBlank()
            bundle.readerWebtoonMaxWidth.shouldNotBeBlank()
            bundle.readerWebtoonSidePadding.shouldNotBeBlank()

            DesktopDestination.entries.forEach { dest ->
                bundle.destinationLabel(dest).shouldNotBeBlank()
                bundle.destinationShortLabel(dest).shouldNotBeBlank()
            }
        }
    }

    @Test
    fun `all language bundles have non-blank localized strings across all screens`() {
        listOf(EnglishStrings, SimplifiedChineseStrings, TraditionalChineseStrings).forEach { bundle ->
            // Category Dialog
            bundle.categoryManageTitle.shouldNotBeBlank()
            bundle.categoryNewNameLabel.shouldNotBeBlank()
            bundle.categoryAdd.shouldNotBeBlank()
            bundle.categoryEmpty.shouldNotBeBlank()
            bundle.categoryRename.shouldNotBeBlank()
            bundle.categoryDelete.shouldNotBeBlank()
            bundle.categoryRenameTitle.shouldNotBeBlank()
            bundle.categoryNameLabel.shouldNotBeBlank()
            bundle.categorySave.shouldNotBeBlank()
            bundle.categorySetTitle.shouldNotBeBlank()
            bundle.categoryNoneExist.shouldNotBeBlank()
            bundle.categoryNoneCreated.shouldNotBeBlank()

            // Edit Manga Info Dialog
            bundle.mangaDetailTitleLabel.shouldNotBeBlank()
            bundle.mangaDetailStatusLicensed.shouldNotBeBlank()
            bundle.mangaDetailStatusPublishingFinished.shouldNotBeBlank()
            bundle.mangaDetailGenresLabel.shouldNotBeBlank()
            bundle.mangaDetailDescriptionLabel.shouldNotBeBlank()
            bundle.mangaDetailSaveSuccess.shouldNotBeBlank()
            bundle.mangaDetailStatusOption(1L).shouldNotBeBlank()

            // Cookie Manager Dialog
            bundle.cookieManagerTitle.shouldNotBeBlank()
            bundle.cookieManagerDescription.shouldNotBeBlank()
            bundle.cookieConfiguredDomains(3).shouldNotBeBlank()
            bundle.cookieAddDomain.shouldNotBeBlank()
            bundle.cookieNoCustomDomains.shouldNotBeBlank()
            bundle.cookieCountSubtitle(2, true).shouldNotBeBlank()
            bundle.cookieDomainLabel.shouldNotBeBlank()
            bundle.cookieRawCookiesLabel.shouldNotBeBlank()
            bundle.cookieCustomUaLabel.shouldNotBeBlank()
            bundle.cookieDelete.shouldNotBeBlank()
            bundle.cookieSave.shouldNotBeBlank()
            bundle.cookieRemovedStatus("example.com").shouldNotBeBlank()
            bundle.cookieSavedStatus(2, "example.com").shouldNotBeBlank()

            // Tracker & Tracking Dialog
            bundle.trackerServerUrl.shouldNotBeBlank()
            bundle.trackerUsername.shouldNotBeBlank()
            bundle.trackerPasswordOrApiKey.shouldNotBeBlank()
            bundle.trackerToken.shouldNotBeBlank()
            bundle.trackerAccountName.shouldNotBeBlank()
            bundle.trackerPassword.shouldNotBeBlank()
            bundle.trackerConnect.shouldNotBeBlank()
            bundle.trackerLogin.shouldNotBeBlank()
            bundle.trackingNotTracking.shouldNotBeBlank()
            bundle.trackingNotLoggedIn.shouldNotBeBlank()
            bundle.trackingTrack.shouldNotBeBlank()
            bundle.trackingEdit.shouldNotBeBlank()
            bundle.trackingRemove.shouldNotBeBlank()
            bundle.trackingSearch.shouldNotBeBlank()
            bundle.trackingChaptersRead.shouldNotBeBlank()
            bundle.trackingScore.shouldNotBeBlank()

            // Reader
            bundle.readerFullscreen.shouldNotBeBlank()
            bundle.readerBorderless.shouldNotBeBlank()
            bundle.readerCropToggle(true).shouldNotBeBlank()
            bundle.readerCoverOffsetToggle(true).shouldNotBeBlank()
            bundle.readerSettingsDialogTitle.shouldNotBeBlank()
            bundle.readerSettingsTabReading.shouldNotBeBlank()
            bundle.readerSettingsTabGeneral.shouldNotBeBlank()
            bundle.readerSettingsTabFilter.shouldNotBeBlank()
            bundle.readerChapterTransitions.shouldNotBeBlank()
            bundle.readerClickRegions.shouldNotBeBlank()
            bundle.readerLoadingChapter.shouldNotBeBlank()
            bundle.readerClosed.shouldNotBeBlank()

            // Browse, Sources, Extensions, Migration, Filters, Global Search
            bundle.browseSourceLanguage("EN").shouldNotBeBlank()
            bundle.browseSourcePopular.shouldNotBeBlank()
            bundle.browseSourceLatest.shouldNotBeBlank()
            bundle.browseSearchTitlesPlaceholder.shouldNotBeBlank()
            bundle.browseSearchButton.shouldNotBeBlank()
            bundle.browseFiltersButton(0).shouldNotBeBlank()
            bundle.browseFiltersButton(2).shouldNotBeBlank()
            bundle.browseNoMangaFound.shouldNotBeBlank()
            bundle.browseInLibraryBadge.shouldNotBeBlank()
            bundle.browsePrevPage.shouldNotBeBlank()
            bundle.browsePageNumber(1).shouldNotBeBlank()
            bundle.browseNextPage.shouldNotBeBlank()
            bundle.extensionInfo.shouldNotBeBlank()
            bundle.extensionOpenRepo.shouldNotBeBlank()
            bundle.extensionEnableAll.shouldNotBeBlank()
            bundle.extensionDisableAll.shouldNotBeBlank()
            bundle.extensionClearCookies.shouldNotBeBlank()
            bundle.extensionVersion.shouldNotBeBlank()
            bundle.extensionLanguage.shouldNotBeBlank()
            bundle.extensionAgeRating.shouldNotBeBlank()
            bundle.extensionNsfwShort.shouldNotBeBlank()
            bundle.extensionNsfwWarning.shouldNotBeBlank()
            bundle.extensionUninstall.shouldNotBeBlank()
            bundle.extensionUpdate.shouldNotBeBlank()
            bundle.extensionTrust.shouldNotBeBlank()
            bundle.extensionRevoke.shouldNotBeBlank()
            bundle.extensionUntrusted.shouldNotBeBlank()
            bundle.extensionObsolete.shouldNotBeBlank()
            bundle.extensionObsoleteWarning.shouldNotBeBlank()
            bundle.extensionIncognitoMode.shouldNotBeBlank()
            bundle.extensionIncognitoSummary.shouldNotBeBlank()
            bundle.extensionDebugInfoCopied.shouldNotBeBlank()
            bundle.extensionCookiesCleared.shouldNotBeBlank()
            bundle.sourcePreferencesTitle.shouldNotBeBlank()
            bundle.sourcePreferencesEmpty.shouldNotBeBlank()
            bundle.sourcePreferencesUnsupported.shouldNotBeBlank()
            bundle.sourcePreferencesSaveError.shouldNotBeBlank()
            bundle.actionOk.shouldNotBeBlank()
            bundle.actionCancel.shouldNotBeBlank()
            bundle.actionSettings.shouldNotBeBlank()
            bundle.onlineDetailSource("MangaDex", "en").shouldNotBeBlank()
            bundle.onlineDetailAuthor("Author").shouldNotBeBlank()
            bundle.onlineDetailArtist("Artist").shouldNotBeBlank()
            bundle.onlineDetailGenres("Action").shouldNotBeBlank()
            bundle.onlineDetailChapters(10).shouldNotBeBlank()
            bundle.onlineDetailScanlator("Group").shouldNotBeBlank()
            bundle.migrateSelectSourceHeader.shouldNotBeBlank()
            bundle.migrateNoMangaInLibrary.shouldNotBeBlank()
            bundle.migrateMangaCount(5).shouldNotBeBlank()
            bundle.migrateViewManga.shouldNotBeBlank()
            bundle.migrateBackToSources.shouldNotBeBlank()
            bundle.migrateSelectMangaHeader.shouldNotBeBlank()
            bundle.migrateAction.shouldNotBeBlank()
            bundle.migrateDialogTitle("Title").shouldNotBeBlank()
            bundle.migrateDialogSubtitle.shouldNotBeBlank()
            bundle.migrateNoOtherSources.shouldNotBeBlank()
            bundle.migrateTargetSourceLabel.shouldNotBeBlank()
            bundle.migrateSearchPlaceholder.shouldNotBeBlank()
            bundle.migrateSelectedBadge.shouldNotBeBlank()
            bundle.migrateConfirm.shouldNotBeBlank()
            bundle.filterDialogTitle.shouldNotBeBlank()
            bundle.filterReset.shouldNotBeBlank()
            bundle.filterNoAvailable.shouldNotBeBlank()
            bundle.filterApply.shouldNotBeBlank()
            bundle.filterAscending.shouldNotBeBlank()
            bundle.filterDescending.shouldNotBeBlank()
            bundle.globalSearchEnterQuery.shouldNotBeBlank()
            bundle.globalSearchNoSources.shouldNotBeBlank()
            bundle.globalSearchViewAll.shouldNotBeBlank()
            bundle.globalSearchError("test").shouldNotBeBlank()
            bundle.globalSearchNoResults.shouldNotBeBlank()

            // Settings, Reports
            bundle.settingsDownloadCustomPath.shouldNotBeBlank()
            bundle.settingsDownloadCustomPathPlaceholder.shouldNotBeBlank()
            bundle.settingsDownloadAheadTitle.shouldNotBeBlank()
            bundle.settingsDownloadAheadDesc.shouldNotBeBlank()
            bundle.settingsDownloadAheadDisabled.shouldNotBeBlank()
            bundle.settingsDownloadAheadChapters(1).shouldNotBeBlank()
            bundle.settingsDeleteReadChaptersTitle.shouldNotBeBlank()
            bundle.settingsDeleteReadChaptersDesc.shouldNotBeBlank()
            bundle.settingsTrackerLoggedInAs("user", "server").shouldNotBeBlank()
            bundle.settingsTrackerLogout.shouldNotBeBlank()
            bundle.settingsLibraryUpdateResult(10, 2).shouldNotBeBlank()
            bundle.settingsLibraryUpdateCompleted.shouldNotBeBlank()
            bundle.settingsLibraryUpdateFailed("error").shouldNotBeBlank()
            bundle.readerUnavailable.shouldNotBeBlank()
            bundle.readerOpeningChapter.shouldNotBeBlank()
            bundle.importReportTitle(123L).shouldNotBeBlank()
            bundle.importReportManga(1L, 2L).shouldNotBeBlank()
            bundle.importReportChapters(3L, 4L).shouldNotBeBlank()
            bundle.importReportCategories(5L).shouldNotBeBlank()
            bundle.importReportPreferences(6L, 7L).shouldNotBeBlank()
            bundle.importReportSkipCategories("cat").shouldNotBeBlank()
            bundle.importReportCategory("cat").shouldNotBeBlank()
            bundle.mangaDetailBackToLibrary.shouldNotBeBlank()
        }
    }
}
