package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationMetadataTest {

    @Test
    fun `creators use NFKC separators and explicit circle prefixes`() {
        val manga = SManga.create().apply {
            author = "ＡＬＩＣＥ，Bob；山田　太郎\nAlice"
            artist = "Circle: Night Shift、社團：星組;サークル：月組\nGroup: Studio Five"
        }

        val creators = RecommendationMetadata.extractCreators(manga)

        assertEquals(
            listOf("alice", "bob", "山田 太郎", "night shift", "星組", "月組", "studio five"),
            creators.map(CreatorIdentity::normalizedName),
        )
        assertEquals(
            listOf("Night Shift", "星組", "月組", "Studio Five"),
            creators.drop(3).map(CreatorIdentity::displayName),
        )
    }

    @Test
    fun `ordinary prose is never guessed as creator or genre metadata`() {
        val manga = SManga.create().apply {
            author = "Known Author"
            genre = "Fantasy"
            description = "社团：Hidden Circle\nTag: Hidden Tag\nCircle: Another Hidden Circle"
        }

        assertEquals(
            listOf("known author"),
            RecommendationMetadata.extractCreators(manga).map(CreatorIdentity::normalizedName),
        )
        assertEquals(setOf("fantasy"), RecommendationMetadata.extractGenres(manga))
    }

    @Test
    fun `strict structured tag block supplies source tags and creators`() {
        val manga = SManga.create().apply {
            genre = "doujinshi"
            description = """
                Title: Example
                Tags:
                ・ language: <korean> <translated>
                ・ parody: <original>
                ・ group: <Circle Name>
                ・ artist: <Artist Name>
                ・ female: <big breasts> <gyaru>
                ・ other: <tankoubou> <rough translation>
            """.trimIndent()
        }

        assertEquals(
            setOf("big_breasts", "gyaru", "tankoubou"),
            RecommendationMetadata.extractGenres(manga),
        )
        assertEquals(
            listOf("circle name", "artist name"),
            RecommendationMetadata.extractCreators(manga).map(CreatorIdentity::normalizedName),
        )
        assertEquals(
            listOf("female:big breasts", "female:gyaru", "other:tankoubou"),
            RecommendationMetadata.extractGenreIdentities(manga).map(GenreIdentity::displayName),
        )
    }

    @Test
    fun `structured blocks accept previously unknown tag values without an alias entry`() {
        val manga = SManga.create().apply {
            description = """
                Tags:
                - female: <future unexplored tag>
            """.trimIndent()
        }

        assertEquals(
            setOf("future_unexplored_tag"),
            RecommendationMetadata.extractGenres(manga),
        )
        assertEquals(
            listOf("female:future unexplored tag"),
            RecommendationMetadata.extractGenreIdentities(manga).map(GenreIdentity::displayName),
        )
    }

    @Test
    fun `creator roles are preserved and duplicate names aggregate their roles`() {
        val manga = SManga.create().apply {
            author = "Alice, Writer"
            artist = "Ａｌｉｃｅ, Illustrator"
        }

        val creators = RecommendationMetadata.extractCreators(manga)

        assertEquals(listOf("alice", "writer", "illustrator"), creators.map { it.normalizedName })
        assertEquals(setOf(CreatorRole.AUTHOR, CreatorRole.ARTIST), creators.first().roles)
        assertEquals(setOf(CreatorRole.AUTHOR), creators[1].roles)
        assertEquals(setOf(CreatorRole.ARTIST), creators[2].roles)
    }

    @Test
    fun `artist creator only mutates an exact artist text field`() {
        val authors = GenreText("Authors")
        val groups = GenreText("Groups")
        val keyword = GenreText("Keyword").apply { state = "keep me" }
        val artists = GenreText("Artists")
        val creator = CreatorIdentity(
            displayName = "夜桜組",
            normalizedName = "夜桜組",
            roles = setOf(CreatorRole.ARTIST),
        )

        val match = applyExactCreatorTextFilter(
            FilterList(authors, groups, keyword, artists),
            creator,
        )

        assertEquals(
            CreatorFilterMatch(CreatorRole.ARTIST, CreatorFilterKind.ARTIST, "Artists"),
            match,
        )
        assertEquals("夜桜組", artists.state)
        assertEquals("", authors.state)
        assertEquals("", groups.state)
        assertEquals("keep me", keyword.state)
    }

    @Test
    fun `author creator uses author fields and never guesses a group role`() {
        val creator = CreatorIdentity(
            displayName = "Circle Name",
            normalizedName = "circle name",
            roles = setOf(CreatorRole.AUTHOR),
        )
        val groups = GenreText("Groups")
        val writers = GenreText("Writers")

        val authorMatch = applyExactCreatorTextFilter(FilterList(groups, writers), creator)

        assertEquals(
            CreatorFilterMatch(CreatorRole.AUTHOR, CreatorFilterKind.AUTHOR, "Writers"),
            authorMatch,
        )
        assertEquals("Circle Name", writers.state)
        assertEquals("", groups.state)

        val fallbackGroup = GenreText("社團")
        assertNull(applyExactCreatorTextFilter(FilterList(fallbackGroup), creator))
        assertEquals("", fallbackGroup.state)
    }

    @Test
    fun `creator route leaves unrelated text fields and wrong roles untouched`() {
        val filters = listOf("Keyword", "Search", "Uploader", "Pages").map { name ->
            GenreText(name).apply { state = "existing $name" }
        }
        val authors = GenreText("Authors")
        val groups = GenreText("Groups")
        val creator = CreatorIdentity(
            displayName = "Artist Name",
            normalizedName = "artist name",
            roles = setOf(CreatorRole.ARTIST),
        )

        val match = applyExactCreatorTextFilter(
            FilterList(*(filters + authors + groups).toTypedArray()),
            creator,
        )

        assertNull(match)
        filters.forEach { assertEquals("existing ${it.name}", it.state) }
        assertEquals("", authors.state)
        assertEquals("", groups.state)
    }

    @Test
    fun `title normalization is exact after NFKC punctuation and whitespace removal`() {
        val localized = RecommendationMetadata.normalizeTitle("ＦＲＩＥＲＥＮ： Beyond Journey’s End")
        val canonical = RecommendationMetadata.normalizeTitle("Frieren - Beyond Journey's End")

        assertEquals(canonical, localized)
        assertEquals("frierenbeyondjourneysend", canonical)
        assertNotEquals(
            RecommendationMetadata.normalizeTitle("One Piece"),
            RecommendationMetadata.normalizeTitle("One Punch Man"),
        )
    }

    @Test
    fun `recommendation URL key retains identity query and sorts non tracking parameters`() {
        assertEquals(
            "//example.org/g/123?a=first&b=second&id=7",
            RecommendationMetadata.recommendationUrlKey(
                "https://example.org/g/123/?utm_source=test&id=7&b=second&fbclid=drop&a=first#reader",
            ),
        )
        assertEquals(
            "//example.org/view.php?gid=12&id=34",
            RecommendationMetadata.recommendationUrlKey("https://example.org/view.php?id=34&gid=12"),
        )
        assertEquals(
            "//example.org?gid=12",
            RecommendationMetadata.recommendationUrlKey("https://example.org?utm_medium=test&gid=12"),
        )
    }

    @Test
    fun `same source relative and absolute URL forms retain one work identity`() {
        val relative = identityManga(
            url = "/g/123/?id=9&lang=en",
            title = "Relative title",
            author = "",
            cover = "",
        )
        val absolute = identityManga(
            url = "https://example.org/g/123?lang=en&id=9",
            title = "Absolute title",
            author = "",
            cover = "",
        )

        assertNotEquals(
            RecommendationMetadata.recommendationUrlKey(relative.url),
            RecommendationMetadata.recommendationUrlKey(absolute.url),
        )
        assertTrue(
            RecommendationMetadata.sameWork(
                RecommendationMetadata.identity(7L, relative),
                RecommendationMetadata.identity(7L, absolute),
            ),
        )
    }

    @Test
    fun `absolute URL identity keeps host and separates equal paths on different hosts`() {
        val first = identityManga(
            url = "https://one.example/g/123?id=9",
            title = "First host",
            author = "",
            cover = "",
        )
        val second = identityManga(
            url = "https://two.example/g/123?id=9",
            title = "Second host",
            author = "",
            cover = "",
        )
        val firstIdentity = RecommendationMetadata.identity(7L, first)
        val secondIdentity = RecommendationMetadata.identity(7L, second)

        assertNotEquals(firstIdentity.canonicalUrl, secondIdentity.canonicalUrl)
        assertFalse(RecommendationMetadata.sameWork(firstIdentity, secondIdentity))
        assertTrue((firstIdentity.exposureKeys intersect secondIdentity.exposureKeys).isEmpty())
    }

    @Test
    fun `same work identity is source scoped and requires conservative corroboration`() {
        val aliasA = identityManga(
            url = "/gallery/10",
            title = "A Story",
            author = "Alice",
            cover = "https://img.example/a.jpg",
        )
        val aliasB = identityManga(
            url = "/work/alias-10",
            title = "Ａ Story",
            author = "Bob, Alice",
            cover = "https://img.example/other.jpg",
        )
        val differentAuthor = identityManga(
            url = "/gallery/11",
            title = "A Story",
            author = "Carol",
            cover = "https://img.example/different.jpg",
        )

        val first = RecommendationMetadata.identity(7L, aliasA)
        val alias = RecommendationMetadata.identity(7L, aliasB)

        assertTrue(RecommendationMetadata.sameWork(first, alias))
        assertTrue((first.exposureKeys intersect alias.exposureKeys).isNotEmpty())
        assertFalse(RecommendationMetadata.sameWork(first, RecommendationMetadata.identity(7L, differentAuthor)))
        assertFalse(RecommendationMetadata.sameWork(first, RecommendationMetadata.identity(8L, aliasA)))
    }

    @Test
    fun `base title needs creator plus explicit series or exact cover`() {
        val translated = identityManga(
            url = "translation",
            title = "A Story [English]",
            author = "Alice",
            cover = "https://img.example/a.jpg",
            genres = "Series: Saga",
        )
        val digital = identityManga(
            url = "digital",
            title = "A Story [Digital]",
            author = "Alice",
            cover = "https://img.example/b.jpg",
            genres = "Series: Saga",
        )
        val uncorroborated = identityManga(
            url = "uncorroborated",
            title = "A Story [Digital]",
            author = "Alice",
            cover = "https://img.example/c.jpg",
        )
        val otherVolume = identityManga(
            url = "volume-two",
            title = "A Story Vol. 2 [Digital]",
            author = "Alice",
            cover = "https://img.example/a.jpg",
            genres = "Series: Saga",
        )
        val sourceId = 9L

        assertTrue(
            RecommendationMetadata.sameWork(
                RecommendationMetadata.identity(sourceId, translated),
                RecommendationMetadata.identity(sourceId, digital),
            ),
        )
        assertFalse(
            RecommendationMetadata.sameWork(
                RecommendationMetadata.identity(sourceId, translated),
                RecommendationMetadata.identity(sourceId, uncorroborated),
            ),
        )
        assertFalse(
            RecommendationMetadata.sameWork(
                RecommendationMetadata.identity(sourceId, translated),
                RecommendationMetadata.identity(sourceId, otherVolume),
            ),
        )
    }

    @Test
    fun `exact title and shared cover do not collapse works without creator metadata`() {
        val first = identityManga(
            url = "first-alias",
            title = "Alias Work",
            author = "",
            cover = "https://img.example/alias.jpg",
        )
        val second = identityManga(
            url = "second-alias",
            title = "Ａｌｉａｓ Work",
            author = "",
            cover = "https://img.example/alias.jpg",
        )

        val firstIdentity = RecommendationMetadata.identity(5L, first)
        val secondIdentity = RecommendationMetadata.identity(5L, second)
        assertFalse(RecommendationMetadata.sameWork(firstIdentity, secondIdentity))
        assertTrue((firstIdentity.exposureKeys intersect secondIdentity.exposureKeys).isEmpty())
    }

    @Test
    fun `genres use typed prefixes NFKC normalization and conservative separators`() {
        val manga = SManga.create().apply {
            genre = "Genre: Ｆａｎｔａｓｙ，Tag：School Life、類型: 青春；標籤：日常\nAction"
        }

        assertEquals(
            linkedSetOf("fantasy", "school_life", "青春", "slice_of_life", "action"),
            RecommendationMetadata.extractGenres(manga),
        )
    }

    @Test
    fun `Chinese traditional simplified and English genre aliases share canonical values`() {
        val manga = SManga.create().apply {
            genre = "日本、悬疑、懸疑、校园、校園、戀愛、爱情、科幻、Sci-Fi、連載"
        }

        assertEquals(
            linkedSetOf("mystery", "school_life", "romance", "science_fiction"),
            RecommendationMetadata.extractGenres(manga),
        )
    }

    @Test
    fun `traditional and simplified harem labels share one canonical value`() {
        assertEquals(setOf("harem"), RecommendationMetadata.normalizeGenres("後宮"))
        assertEquals(setOf("harem"), RecommendationMetadata.normalizeGenres("后宫"))
    }

    @Test
    fun `namespaced language and creator metadata do not become similarity tags`() {
        val manga = SManga.create().apply {
            genre = "language:japanese, artist:alice, group:circle, female:big breasts, parody:original"
        }

        assertEquals(
            linkedSetOf("big_breasts"),
            RecommendationMetadata.extractGenres(manga),
        )
    }

    @Test
    fun `ehentai exact query combines namespaced tags with AND semantics`() {
        assertEquals(
            "female:\"big breasts\$\" parody:original\$",
            RecommendationMetadata.ehentaiExactTagQuery(
                listOf(
                    GenreIdentity("female:big breasts", "female_big_breasts"),
                    GenreIdentity("parody:original", "parody_original"),
                ),
            ),
        )
    }

    @Test
    fun `origin status and format tags are excluded from recommendation evidence`() {
        val manga = SManga.create().apply {
            genre = "日本, 日漫, 中国漫画, 韓漫, 欧美, 漫畫, Manga, 连载, 完結"
        }

        assertTrue(RecommendationMetadata.extractGenres(manga).isEmpty())
    }

    @Test
    fun `recommendation routes prefer an explicit update time sort in descending order`() {
        val sort = GenreSort("排序", arrayOf("热门", "更新时间"))

        assertTrue(preferFreshRecommendationSort(FilterList(sort)))
        assertEquals(Filter.Sort.Selection(index = 1, ascending = false), sort.state)
    }

    @Test
    fun `freshness sort recognizes traditional Japanese Korean and English labels`() {
        listOf("更新時間", "更新日時", "최신", "Latest").forEach { label ->
            val sort = GenreSort("Sort", arrayOf("Popular", label))

            assertTrue(preferFreshRecommendationSort(FilterList(sort)), label)
            assertEquals(Filter.Sort.Selection(index = 1, ascending = false), sort.state, label)
        }
    }

    @Test
    fun `unrelated source sorts remain unchanged`() {
        val sort = GenreSort("排序", arrayOf("热门", "评分"))

        assertFalse(preferFreshRecommendationSort(FilterList(sort)))
        assertEquals(null, sort.state)
    }

    @Test
    fun `deferred genre loading marker is detected without relying on a source name`() {
        val loading = FilterList(Filter.Header("点击“重置”尝试刷新题材分类"))
        val ordinary = FilterList(Filter.Header("登录后可使用更多功能"))

        assertTrue(hasDeferredGenreFilterMarker(loading))
        assertFalse(hasDeferredGenreFilterMarker(ordinary))
    }

    @Test
    fun `exact checkbox match is found inside nested groups`() {
        val partial = GenreCheckBox("Dark Fantasy")
        val exact = GenreCheckBox("Ｆａｎｔａｓｙ")
        val filters = nestedFilters(partial, exact)

        assertTrue(applyExactGenreFilter(filters, RecommendationMetadata.normalize("Fantasy")))
        assertFalse(partial.state)
        assertTrue(exact.state)
    }

    @Test
    fun `combined structured route includes two independent native tags`() {
        val romance = GenreCheckBox("Romance")
        val mystery = GenreCheckBox("Mystery")
        val filters = nestedFilters(romance, mystery)

        assertTrue(
            applyCombinedStructuredGenreFilters(
                filters,
                listOf(
                    GenreIdentity("Romance", "romance"),
                    GenreIdentity("Mystery", "mystery"),
                ),
            ),
        )
        assertTrue(romance.state)
        assertTrue(mystery.state)
    }

    @Test
    fun `canonical Chinese genre matches traditional source filter`() {
        val exact = GenreCheckBox("校園")

        assertTrue(applyExactGenreFilter(nestedFilters(exact), "school_life"))
        assertTrue(exact.state)
    }

    @Test
    fun `exact tri state match is included inside nested groups`() {
        val partial = GenreTriState("Science Fiction")
        val exact = GenreTriState("Fiction")
        val filters = nestedFilters(partial, exact)

        assertTrue(applyExactGenreFilter(filters, RecommendationMetadata.normalize("Fiction")))
        assertEquals(Filter.TriState.STATE_IGNORE, partial.state)
        assertEquals(Filter.TriState.STATE_INCLUDE, exact.state)
    }

    @Test
    fun `exact select value is chosen inside nested groups`() {
        val select = GenreSelect("Genre", arrayOf("Any", "Dark Fantasy", "Ｆａｎｔａｓｙ"))
        val filters = nestedFilters(select)

        assertTrue(applyExactGenreFilter(filters, RecommendationMetadata.normalize("Fantasy")))
        assertEquals(2, select.state)
    }

    @Test
    fun `missing exact filter leaves every state unchanged`() {
        val checkBox = GenreCheckBox("Dark Fantasy")
        val triState = GenreTriState("Romantic Comedy")
        val select = GenreSelect("Genre", arrayOf("Any", "Action Adventure"))
        val filters = nestedFilters(checkBox, triState, select)

        assertFalse(applyExactGenreFilter(filters, RecommendationMetadata.normalize("Fantasy")))
        assertFalse(checkBox.state)
        assertEquals(Filter.TriState.STATE_IGNORE, triState.state)
        assertEquals(0, select.state)
    }

    @Test
    fun `tag text filter receives the original Chinese genre label`() {
        val tags = GenreText("Tags")

        val result = applyGenreFilter(
            FilterList(tags),
            GenreIdentity(displayName = "愛情", normalizedName = "romance"),
        )

        assertEquals(GenreFilterKind.TEXT_TAG, result)
        assertEquals("愛情", tags.state)
    }

    @Test
    fun `tag text filter is preferred over category text filter`() {
        val categories = GenreText("Categories")
        val tags = GenreText("Tags")

        val result = applyGenreFilter(
            FilterList(categories, tags),
            GenreIdentity(displayName = "懸疑", normalizedName = "mystery"),
        )

        assertEquals(GenreFilterKind.TEXT_TAG, result)
        assertEquals("", categories.state)
        assertEquals("懸疑", tags.state)
    }

    @Test
    fun `ordinary search and title text filters are not modified`() {
        val search = GenreText("Search").apply { state = "existing search" }
        val title = GenreText("Title").apply { state = "existing title" }

        val result = applyGenreFilter(
            FilterList(search, title),
            GenreIdentity(displayName = "校園", normalizedName = "school_life"),
        )

        assertEquals(GenreFilterKind.NONE, result)
        assertEquals("existing search", search.state)
        assertEquals("existing title", title.state)
    }

    @Test
    fun `text filter inherits a recognized tag field from nested groups`() {
        val value = GenreText("Value")
        val filters = FilterList(
            GenreGroup(
                "標籤",
                listOf(GenreGroup("Options", listOf(value))),
            ),
        )

        val result = applyGenreFilter(
            filters,
            GenreIdentity(displayName = "愛情", normalizedName = "romance"),
        )

        assertEquals(GenreFilterKind.TEXT_TAG, result)
        assertEquals("愛情", value.state)
    }

    @Test
    fun `structured genre matching never selects an exclusion group`() {
        val excluded = GenreCheckBox("恋愛")
        val included = GenreCheckBox("恋愛")
        val filters = FilterList(
            GenreGroup("除外タグ", listOf(excluded)),
            GenreGroup("含めるタグ", listOf(included)),
        )

        val result = applyGenreFilter(
            filters,
            GenreIdentity(displayName = "恋愛", normalizedName = "romance"),
        )

        assertEquals(GenreFilterKind.STRUCTURED, result)
        assertFalse(excluded.state)
        assertTrue(included.state)
    }

    @Test
    fun `text genre matching never writes into an exclusion field`() {
        val excluded = GenreText("値")
        val included = GenreText("タグ")
        val filters = FilterList(
            GenreGroup("Tags to exclude", listOf(excluded)),
            GenreGroup("Tags", listOf(included)),
        )

        val result = applyGenreFilter(
            filters,
            GenreIdentity(displayName = "校園", normalizedName = "school_life"),
        )

        assertEquals(GenreFilterKind.TEXT_TAG, result)
        assertEquals("", excluded.state)
        assertEquals("校園", included.state)
    }

    private fun nestedFilters(vararg filters: Filter<*>): FilterList {
        return FilterList(
            GenreGroup(
                "Outer",
                listOf(GenreGroup("Inner", filters.toList())),
            ),
        )
    }

    private fun identityManga(
        url: String,
        title: String,
        author: String,
        cover: String,
        genres: String? = null,
    ): SManga {
        return SManga.create().apply {
            this.url = url
            this.title = title
            this.author = author
            thumbnail_url = cover
            genre = genres
            initialized = true
        }
    }

    private class GenreCheckBox(name: String) : Filter.CheckBox(name)

    private class GenreTriState(name: String) : Filter.TriState(name)

    private class GenreSelect(name: String, values: Array<String>) : Filter.Select<String>(name, values)

    private class GenreText(name: String) : Filter.Text(name)

    private class GenreSort(name: String, values: Array<String>) : Filter.Sort(name, values)

    private class GenreGroup(name: String, filters: List<Filter<*>>) :
        Filter.Group<Filter<*>>(name, filters)
}
