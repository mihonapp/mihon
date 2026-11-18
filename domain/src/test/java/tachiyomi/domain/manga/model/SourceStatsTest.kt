package tachiyomi.domain.manga.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

class SourceStatsTest {

    @Test
    fun `memo rating is preferred`() {
        val manga = Manga.create().copy(
            memo = buildJsonObject {
                put("rating", JsonPrimitive(8.5))
                put("rating_count", JsonPrimitive(1200))
            },
            description = "Рейтинг: 1.0 (2)",
        )

        manga.sourceStats() shouldBe SourceStats(rating = 8.5, ratingCount = 1200)
    }

    @Test
    fun `description rating with count`() {
        val manga = Manga.create().copy(description = "Рейтинг: 9,2 (1 234)\n\nОписание")

        manga.sourceStats() shouldBe SourceStats(rating = 9.2, ratingCount = 1234)
    }

    @Test
    fun `missing rating`() {
        Manga.create().sourceStats() shouldBe SourceStats(rating = null, ratingCount = null)
    }

    @Test
    fun `stars with votes label`() {
        val manga = Manga.create().copy(
            description = "\u2605\u2605\u2605\u2605\u2606 8.2 (\u0433\u043e\u043b\u043e\u0441\u043e\u0432: 1234)\ntext",
        )

        manga.sourceStats() shouldBe SourceStats(rating = 8.2, ratingCount = 1234)
    }

    @Test
    fun `percent rating is converted to ten points`() {
        val manga = Manga.create().copy(
            description = "\u0420\u0435\u0439\u0442\u0438\u043d\u0433: 78%",
        )

        manga.sourceStats() shouldBe SourceStats(rating = 7.8, ratingCount = null)
    }

    @Test
    fun `five point rating is converted to ten points`() {
        val manga = Manga.create().copy(
            description = "\u041e\u0446\u0435\u043d\u043a\u0430: 4.3/5",
        )

        manga.sourceStats() shouldBe SourceStats(rating = 8.6, ratingCount = null)
    }

    @Test
    fun `favorites count as rating count`() {
        val manga = Manga.create().copy(
            description = "\u0420\u0435\u0439\u0442\u0438\u043d\u0433: 78%\n" +
                "\u0418\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435: 785",
        )

        manga.sourceStats() shouldBe SourceStats(rating = 7.8, ratingCount = 785)
    }

    @Test
    fun `half star still exposes the rating`() {
        val manga = Manga.create().copy(
            description = "\u2605\u2605\u2605\u2605\u272c 9.39 (\u0433\u043e\u043b\u043e\u0441\u043e\u0432: 2158)",
        )

        manga.sourceStats() shouldBe SourceStats(rating = 9.4, ratingCount = 2158)
    }

    @Test
    fun `memo rating keeps the vote count from the description`() {
        val manga = Manga.create().copy(
            memo = buildJsonObject { put("rating", JsonPrimitive(7.5)) },
            description = "\u2605\u2605\u2605\u2606\u2606 7.5 (\u0433\u043e\u043b\u043e\u0441\u043e\u0432: 42)",
        )

        manga.sourceStats() shouldBe SourceStats(rating = 7.5, ratingCount = 42)
    }
}
