package eu.kanade.tachiyomi.ui.browse.migration.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import kotlin.time.Instant

class MigrationOriginSummaryTest {

    private fun chapter(number: Double, uploadMillis: Long? = null) = Chapter.create().copy(
        chapterNumber = number,
        dateUpload = uploadMillis?.let { Instant.fromEpochMilliseconds(it) },
    )

    @Test
    fun `empty manga has unknown latest chapter and upload`() {
        summarizeMigrationOrigin("Source", emptyList()) shouldBe MigrationOriginSummary("Source", null, null)
    }

    @Test
    fun `highest recognized chapter wins regardless of list order`() {
        summarizeMigrationOrigin("A", listOf(chapter(5.0), chapter(20.5), chapter(9.0))).latestChapter shouldBe 20.5
    }

    @Test
    fun `unrecognized and nonfinite chapter numbers are ignored`() {
        val summary = summarizeMigrationOrigin(
            "A",
            listOf(chapter(-1.0), chapter(Double.NaN), chapter(Double.POSITIVE_INFINITY), chapter(7.0)),
        )
        summary.latestChapter shouldBe 7.0
    }

    @Test
    fun `latest available upload date is used independently of chapter order`() {
        val summary = summarizeMigrationOrigin(
            "B",
            listOf(chapter(9.0, 1_000L), chapter(3.0, 3_000L), chapter(12.0, 2_000L)),
        )
        summary.latestChapter shouldBe 12.0
        summary.latestUpload shouldBe Instant.fromEpochMilliseconds(3_000L)
    }

    @Test
    fun `missing upload dates remain optional`() {
        summarizeMigrationOrigin("C", listOf(chapter(3.0))).latestUpload shouldBe null
    }
}
