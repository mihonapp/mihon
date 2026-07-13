package eu.kanade.tachiyomi.ui.readinglist

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ReadingListSourceGroupingTest {

    @Test
    fun `groups multilingual variants under one installed extension`() {
        val groups = buildReadingListSourceGroups(
            listOf(
                extension(
                    name = "Example Comics",
                    packageName = "org.example.comics",
                    source(id = 2, name = "Example Comics", language = "de"),
                    source(id = 1, name = "Example Comics", language = "en"),
                ),
            ),
        )

        groups.size shouldBe 1
        groups.single().extensionName shouldBe "Example Comics"
        groups.single().sources.map(ReadingListSourceOption::language) shouldContainExactly listOf("de", "en")
    }

    @Test
    fun `keeps installed extensions with the same display name separate`() {
        val groups = buildReadingListSourceGroups(
            listOf(
                extension(
                    name = "Comics",
                    packageName = "org.example.second",
                    source(id = 20, name = "Comics", language = "en"),
                ),
                extension(
                    name = "Comics",
                    packageName = "org.example.first",
                    source(id = 10, name = "Comics", language = "en"),
                ),
            ),
        )

        groups.map(ReadingListSourceGroup::key) shouldContainExactly listOf(
            "org.example.first",
            "org.example.second",
        )
    }

    @Test
    fun `omits installed extensions without online sources`() {
        val groups = buildReadingListSourceGroups(
            listOf(
                extension(
                    name = "Empty",
                    packageName = "org.example.empty",
                ),
                extension(
                    name = "Usable",
                    packageName = "org.example.usable",
                    source(id = 1, name = "Usable", language = "en"),
                ),
            ),
        )

        groups.map(ReadingListSourceGroup::key) shouldContainExactly listOf("org.example.usable")
    }

    @Test
    fun `deduplicates conflicting source IDs deterministically`() {
        val groups = buildReadingListSourceGroups(
            listOf(
                extension(
                    name = "Alpha",
                    packageName = "org.example.alpha",
                    source(id = 7, name = "Alpha", language = "en"),
                ),
                extension(
                    name = "Beta",
                    packageName = "org.example.beta",
                    source(id = 7, name = "Beta", language = "en"),
                    source(id = 8, name = "Beta", language = "de"),
                ),
            ),
        )

        groups.map(ReadingListSourceGroup::key) shouldContainExactly listOf(
            "org.example.alpha",
            "org.example.beta",
        )
        groups.flatMap(ReadingListSourceGroup::sources).map(ReadingListSourceOption::id) shouldContainExactly listOf(
            7,
            8,
        )
    }

    private fun extension(
        name: String,
        packageName: String,
        vararg sources: ReadingListSourceOption,
    ): InstalledReadingListExtension {
        return InstalledReadingListExtension(
            extensionName = name,
            packageName = packageName,
            sources = sources.toList(),
        )
    }

    private fun source(
        id: Long,
        name: String,
        language: String,
    ): ReadingListSourceOption {
        return ReadingListSourceOption(
            id = id,
            name = name,
            language = language,
            installed = true,
        )
    }
}
