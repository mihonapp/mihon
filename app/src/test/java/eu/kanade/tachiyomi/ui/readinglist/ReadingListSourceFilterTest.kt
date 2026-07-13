package eu.kanade.tachiyomi.ui.readinglist

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReadingListSourceFilterTest {

    @Test
    fun `lists concrete installed languages without the all-language source`() {
        availableReadingListSourceLanguages(groups()) shouldContainExactly listOf("de", "en", "es")
    }

    @Test
    fun `preferred language includes matching and all-language variants`() {
        val filtered = filterReadingListSourceGroups(
            groups = groups(),
            preferredLanguage = "en",
            query = "",
        )

        filtered.map(ReadingListSourceGroup::key) shouldContainExactly listOf("alpha", "beta")
        filtered.first().sources.map(ReadingListSourceOption::language) shouldContainExactly listOf("en", "all")
        filtered.last().sources.map(ReadingListSourceOption::language) shouldContainExactly listOf("en")
    }

    @Test
    fun `all languages leaves installed variants visible`() {
        val filtered = filterReadingListSourceGroups(
            groups = groups(),
            preferredLanguage = "",
            query = "",
        )

        filtered.first().sources.map(ReadingListSourceOption::language) shouldContainExactly listOf(
            "de",
            "en",
            "es",
            "all",
        )
    }

    @Test
    fun `extension search keeps all language-filtered variants in a matching group`() {
        val filtered = filterReadingListSourceGroups(
            groups = groups(),
            preferredLanguage = "en",
            query = "alpha extension",
        )

        filtered.size shouldBe 1
        filtered.single().sources.map(ReadingListSourceOption::language) shouldContainExactly listOf("en", "all")
    }

    @Test
    fun `source search narrows a nonmatching extension to matching child sources`() {
        val filtered = filterReadingListSourceGroups(
            groups = groups(),
            preferredLanguage = "",
            query = "spanish source",
        )

        filtered.size shouldBe 1
        filtered.single().sources.map(ReadingListSourceOption::language) shouldContainExactly listOf("es")
    }

    @Test
    fun `unavailable saved sources remain visible through a language filter`() {
        val unavailable = ReadingListSourceGroup(
            key = "unavailable",
            extensionName = "",
            packageName = null,
            installed = false,
            sources = listOf(source(99, "Saved source 99", "", installed = false)),
        )

        val filtered = filterReadingListSourceGroups(
            groups = groups() + unavailable,
            preferredLanguage = "de",
            query = "",
        )

        filtered.last().key shouldBe "unavailable"
    }

    private fun groups(): List<ReadingListSourceGroup> {
        return listOf(
            ReadingListSourceGroup(
                key = "alpha",
                extensionName = "Alpha Extension",
                packageName = "org.example.alpha",
                installed = true,
                sources = listOf(
                    source(1, "German source", "de"),
                    source(2, "English source", "en"),
                    source(3, "Spanish source", "es"),
                    source(4, "All source", "all"),
                ),
            ),
            ReadingListSourceGroup(
                key = "beta",
                extensionName = "Beta Extension",
                packageName = "org.example.beta",
                installed = true,
                sources = listOf(source(5, "Beta English", "en")),
            ),
        )
    }

    private fun source(
        id: Long,
        name: String,
        language: String,
        installed: Boolean = true,
    ): ReadingListSourceOption {
        return ReadingListSourceOption(
            id = id,
            name = name,
            language = language,
            installed = installed,
        )
    }
}
