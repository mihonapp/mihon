package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.data.library.ChapterNotificationPrivacy.SummaryChange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterNotificationPrivacyTest {
    private val privacy = ChapterNotificationPrivacy(setOf(10L))

    @Test
    fun unrelatedHiddenMangaDoesNotChangeVisibleNotifications() {
        privacy.isHidden(20, 20L) shouldBe false
        privacy.summaryChange(listOf(20L), listOf(20L), childrenRemoved = false) shouldBe null
    }

    @Test
    fun usesExactMangaIdAndStillRecognizesLegacyNotificationIds() {
        val visibleMangaId = 10L shl 32
        visibleMangaId.hashCode() shouldBe 10
        privacy.isHidden(10, visibleMangaId) shouldBe false
        privacy.isHidden(10, 10L) shouldBe true
        privacy.isHidden(10, null) shouldBe true
    }

    @Test
    fun updatesASummaryEvenIfTheHiddenChildWasAlreadyDismissed() {
        privacy.summaryChange(listOf(10L, 20L), listOf(20L), childrenRemoved = false) shouldBe
            SummaryChange.Replace(listOf(20L), mangaCount = 1)
    }

    @Test
    fun keepsTheGroupWhenAnOlderVisibleNotificationIsStillActive() {
        privacy.summaryChange(listOf(10L), listOf(20L), childrenRemoved = true) shouldBe
            SummaryChange.Replace(listOf(20L), mangaCount = 1)
    }

    @Test
    fun cancelsASummaryThatOnlyDescribesHiddenMangaWhenTheGroupIsEmpty() {
        privacy.summaryChange(listOf(10L), emptyList(), childrenRemoved = false) shouldBe SummaryChange.Cancel
    }

    @Test
    fun replacesLegacySummariesWithoutDiscardingTheirVisibleChildren() {
        privacy.summaryChange(null, listOf(null), childrenRemoved = true) shouldBe
            SummaryChange.Replace(emptyList(), mangaCount = 1)
    }
}
