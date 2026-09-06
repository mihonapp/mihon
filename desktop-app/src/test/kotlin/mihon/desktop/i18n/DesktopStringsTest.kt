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

            DesktopDestination.entries.forEach { dest ->
                bundle.destinationLabel(dest).shouldNotBeBlank()
                bundle.destinationShortLabel(dest).shouldNotBeBlank()
            }
        }
    }
}
