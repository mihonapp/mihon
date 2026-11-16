package eu.kanade.tachiyomi.ui.webview

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class WebViewAdBlockerTest {
    @Test
    fun persistsFilterSelectionAndKeepsSavedRulesAfterInvalidEdit() = runTest {
        var saved = WebViewAdBlocker.Settings()
        val preference = mockk<Preference<WebViewAdBlocker.Settings>> {
            every { get() } answers { saved }
            every { set(any()) } answers { saved = firstArg() }
            every { changes() } answers { flowOf(saved) }
        }
        val preferences = mockk<PreferenceStore> {
            every { getObjectFromString(any(), any<WebViewAdBlocker.Settings>(), any(), any()) } returns preference
        }
        val blocker = WebViewAdBlocker(mockk(), preferences, Json, backgroundScope)
        blocker.settings.filterNotNull().first()

        blocker.setBuiltInEnabled(false)
        blocker.saveCustomRules("||ads.example.org^") shouldBe null
        blocker.saveCustomRules("manga.example.org##.banner") shouldBe WebViewAdBlocker.FilterError.UnsupportedRules
        blocker.shouldBlock("ads.example.org", isMainFrame = false) shouldBe true

        blocker.setEnabled(false)
        blocker.shouldBlock("ads.example.org", isMainFrame = false) shouldBe false
        blocker.setEnabled(true)
        blocker.setCustomRulesEnabled(false)
        blocker.shouldBlock("ads.example.org", isMainFrame = false) shouldBe false

        val restored = WebViewAdBlocker(mockk(), preferences, Json, backgroundScope)
        val settings = restored.settings.filterNotNull().first()
        settings.builtInEnabled shouldBe false
        settings.customRulesEnabled shouldBe false
        settings.customRules shouldBe "||ads.example.org^"
        restored.setCustomRulesEnabled(true)
        restored.shouldBlock("ads.example.org", isMainFrame = false) shouldBe true
    }
}
