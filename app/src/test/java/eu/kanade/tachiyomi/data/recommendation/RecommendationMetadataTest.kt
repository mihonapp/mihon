package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationMetadataTest {

    @Test
    fun `normalization uses NFKC and root casing without translating tags`() {
        assertEquals(
            "romance school",
            RecommendationMetadata.normalize("\uFF32\uFF2F\uFF2D\uFF21\uFF2E\uFF23\uFF25--School"),
        )
        assertEquals("i", RecommendationMetadata.normalize("I"))
        assertEquals("\u611B\u60C5", RecommendationMetadata.normalize("\u611B\u60C5"))
        assertEquals("\u7231\u60C5", RecommendationMetadata.normalize("\u7231\u60C5"))
        assertEquals("\u604B\u611B", RecommendationMetadata.normalize("\u604B\u611B"))
        assertEquals("\uB85C\uB9E8\uC2A4", RecommendationMetadata.normalize("\uB85C\uB9E8\uC2A4"))
    }

    @Test
    fun `unicode delimiters produce independent source native tags`() {
        val manga = manga("work", "Title").apply {
            genre = "romance\uFF0Cschool\u3001\u30DF\u30B9\u30C6\u30EA\u30FC\uFF1B\uC561\uC158"
        }

        assertEquals(
            setOf("romance", "school", "\u30DF\u30B9\u30C6\u30EA\u30FC", "\uC561\uC158"),
            RecommendationMetadata.extractTags(manga),
        )
    }

    @Test
    fun `creator parsing merges exact author and artist roles`() {
        val manga = manga("work", "Title").apply {
            author = "Alice; Bob"
            artist = "\uFF21\uFF2C\uFF29\uFF23\uFF25"
        }

        val creators = RecommendationMetadata.extractCreators(manga).associateBy(CreatorIdentity::normalizedName)

        assertEquals(setOf(CreatorRole.AUTHOR, CreatorRole.ARTIST), creators.getValue("alice").roles)
        assertEquals(setOf(CreatorRole.AUTHOR), creators.getValue("bob").roles)
    }

    @Test
    fun `only explicit group metadata lines are extracted from descriptions`() {
        val manga = manga("work", "Title").apply {
            description = listOf(
                "A story that mentions Circle: Not Metadata in ordinary prose.",
                "Group: Studio A",
                "\u793E\u5718\uFF1A Studio B",
                "\u30B5\u30FC\u30AF\u30EB: Studio C",
            ).joinToString("\n")
        }

        val groups = RecommendationMetadata.extractCreators(manga)
            .filter { CreatorRole.GROUP in it.roles }
            .map(CreatorIdentity::normalizedName)

        assertEquals(listOf("studio a", "studio b", "studio c"), groups)
        assertFalse(groups.contains("not metadata in ordinary prose"))
    }

    @Test
    fun `URL identity keeps identity query and removes only tracking parameters`() {
        val absolute = manga(
            "https://Example.com/item?utm_source=test&lang=en&id=7&fbclid=x",
            "First",
        ).apply { author = "Creator" }
        val relative = manga("/item?id=7&lang=en", "Alias").apply { author = "Other" }
        val otherId = manga("/item?id=8&lang=en", "Different").apply { author = "Creator" }
        val left = RecommendationMetadata.identity(10L, absolute)
        val alias = RecommendationMetadata.identity(10L, relative)

        assertEquals("//example.com/item?id=7&lang=en", left.canonicalUrl)
        assertTrue(RecommendationMetadata.sameWork(left, alias))
        assertFalse(RecommendationMetadata.sameWork(left, RecommendationMetadata.identity(10L, otherId)))
        assertFalse(RecommendationMetadata.sameWork(left, left.copy(sourceId = 11L)))
    }

    @Test
    fun `query-only URLs retain their identity parameter`() {
        val first = RecommendationMetadata.identity(1L, manga("?id=7&utm_medium=test", "One"))
        val alias = RecommendationMetadata.identity(1L, manga("/?id=7", "Alias"))
        val other = RecommendationMetadata.identity(1L, manga("?id=8", "One"))

        assertEquals("/?id=7", first.canonicalUrl)
        assertTrue(RecommendationMetadata.sameWork(first, alias))
        assertFalse(RecommendationMetadata.sameWork(first, other))
    }

    @Test
    fun `same title needs a creator and different volumes remain distinct`() {
        val first = manga("/one", "Series (1)").apply {
            author = "Creator"
            thumbnail_url = "/cover"
        }
        val second = manga("/two", "Series (2)").apply {
            author = "Creator"
            thumbnail_url = "/cover"
        }
        val alias = manga("/alias", "Series (1)").apply { author = "Creator" }
        val unrelated = manga("/unrelated", "Series (1)").apply { author = "Someone Else" }
        val target = RecommendationMetadata.identity(1L, first)

        assertTrue(RecommendationMetadata.sameWork(target, RecommendationMetadata.identity(1L, alias)))
        assertFalse(RecommendationMetadata.sameWork(target, RecommendationMetadata.identity(1L, second)))
        assertFalse(RecommendationMetadata.sameWork(target, RecommendationMetadata.identity(1L, unrelated)))
    }

    @Test
    fun `exact generic filters are applied recursively without changing text or sort`() {
        val checkbox = TestCheckBox("Romance")
        val triState = TestTriState("School")
        val select = TestSelect("Genre", arrayOf("Any", "Mystery"))
        val text = TestText("Query")
        val sort = TestSort("Order", arrayOf("Newest"))
        val filters = FilterList(TestGroup("Tags", listOf(checkbox, triState, select, text, sort)))

        assertTrue(RecommendationMetadata.applyExactTagFilter(filters, "\uFF2D\uFF39\uFF33\uFF34\uFF25\uFF32\uFF39"))
        assertEquals(1, select.state)
        assertEquals("", text.state)
        assertEquals(null, sort.state)
        assertTrue(RecommendationMetadata.applyExactTagFilter(filters, "school"))
        assertEquals(Filter.TriState.STATE_INCLUDE, triState.state)
        assertFalse(RecommendationMetadata.applyExactTagFilter(filters, "unrelated"))
    }

    private fun manga(url: String, title: String): SManga = SManga.create().apply {
        this.url = url
        this.title = title
    }

    private class TestCheckBox(name: String) : Filter.CheckBox(name)
    private class TestTriState(name: String) : Filter.TriState(name)
    private class TestSelect(name: String, values: Array<String>) : Filter.Select<String>(name, values)
    private class TestText(name: String) : Filter.Text(name)
    private class TestSort(name: String, values: Array<String>) : Filter.Sort(name, values)
    private class TestGroup(name: String, filters: List<Filter<*>>) : Filter.Group<Filter<*>>(name, filters)
}
