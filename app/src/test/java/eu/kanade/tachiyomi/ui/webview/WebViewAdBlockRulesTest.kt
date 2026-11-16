package eu.kanade.tachiyomi.ui.webview

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WebViewAdBlockRulesTest {
    @Test
    fun blocksDomainsAndSubdomainsWithExceptionsAndLeavesNavigationAvailable() {
        val builder = WebViewAdBlockRules.Builder()
        builder.addRules(
            sequenceOf(
                "ads.example.org",
                "||banners.example.com^",
                "@@||login.ads.example.org^",
            ),
        ) shouldBe 0
        val rules = builder.build()

        rules.blocks("IMG.BANNERS.EXAMPLE.COM.", isMainFrame = false) shouldBe true
        rules.blocks("ads.example.org", isMainFrame = false) shouldBe true
        rules.blocks("notads.example.org", isMainFrame = false) shouldBe false
        rules.blocks("assets.login.ads.example.org", isMainFrame = false) shouldBe false
        rules.blocks("ads.example.org", isMainFrame = true) shouldBe false
    }

    @Test
    fun treatsHostsEntriesAsExactHostsAndIgnoresUnsupportedRules() {
        val builder = WebViewAdBlockRules.Builder()
        builder.addRules(
            sequenceOf(
                "\uFEFF[Adblock Plus 2.0]",
                "127.0.0.1 localhost",
                "0.0.0.0 ads.example.org videos.example.org # Ad servers",
                "manga.example.org##.banner",
                "||login.example.org^\$third-party",
                "||api.example.org/ads",
            ),
        ) shouldBe 3
        val rules = builder.build()

        rules.blocks("ads.example.org", isMainFrame = false) shouldBe true
        rules.blocks("videos.example.org", isMainFrame = false) shouldBe true
        rules.blocks("img.ads.example.org", isMainFrame = false) shouldBe false
        rules.blocks("manga.example.org", isMainFrame = false) shouldBe false
        rules.blocks("login.example.org", isMainFrame = false) shouldBe false
        rules.blocks("api.example.org", isMainFrame = false) shouldBe false
    }
}
