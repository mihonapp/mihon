package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultilingualRecommendationMetadataTest {

    @Test
    fun `simplified traditional English Japanese Korean and European aliases share canonical ids`() {
        val romanceAliases = listOf(
            "Romance",
            "爱情",
            "愛情",
            "恋愛",
            "로맨스",
            "romántico",
            "romantique",
            "Romantik",
            "romantico",
            "romântico",
            "романтика",
            "romans",
        )
        val comedyAliases = listOf("喜剧", "歡樂向", "コメディ", "코미디", "comédie", "Komödie", "comédia")
        val schoolAliases = listOf("校园", "校園", "学園", "학원", "vida escolar", "vie scolaire", "Schulleben", "школа")

        romanceAliases.forEach { alias ->
            assertEquals(setOf("romance"), RecommendationMetadata.normalizeGenres(alias), alias)
        }
        comedyAliases.forEach { alias ->
            assertEquals(setOf("comedy"), RecommendationMetadata.normalizeGenres(alias), alias)
        }
        schoolAliases.forEach { alias ->
            assertEquals(setOf("school_life"), RecommendationMetadata.normalizeGenres(alias), alias)
        }
    }

    @Test
    fun `space separated and contiguous CJK labels are segmented without character tokenization`() {
        assertEquals(
            linkedSetOf("romance", "comedy", "school_life"),
            genresOf("爱情 欢乐向 校园"),
        )
        assertEquals(
            linkedSetOf("romance", "comedy", "school_life"),
            genresOf("愛情歡樂向校園"),
        )
    }

    @Test
    fun `known CJK labels survive while release markers are ignored`() {
        assertEquals(
            linkedSetOf("romance", "comedy", "school_life"),
            genresOf("爱情 欢乐向 校园 新作"),
        )
        assertEquals(
            linkedSetOf("romance", "comedy", "school_life"),
            genresOf("愛情歡樂向校園新作"),
        )
    }

    @Test
    fun `release markers do not become similarity tags in mixed language labels`() {
        assertEquals(linkedSetOf("romance"), genresOf("romance new"))
        assertEquals(linkedSetOf("romance"), genresOf("爱情 新作"))
        assertEquals(linkedSetOf("romance"), genresOf("愛情新作"))
        assertEquals(linkedSetOf("romance"), genresOf("恋愛新作"))
        assertEquals(linkedSetOf("romance"), genresOf("로맨스 신작"))
    }

    @Test
    fun `Japanese middle dot and common traditional forms normalize safely`() {
        assertEquals(linkedSetOf("romance", "comedy"), genresOf("恋愛・コメディー"))
        assertEquals(linkedSetOf("adventure", "comedy"), genresOf("冒險、輕鬆"))
        assertEquals(setOf("science_fiction"), genresOf("サイエンス・フィクション"))
    }

    @Test
    fun `known multiword English label is not split`() {
        assertEquals(setOf("school_life"), genresOf("School Life"))
    }

    @Test
    fun `Japanese romantic comedy expands to both canonical concepts`() {
        assertEquals(
            linkedSetOf("romance", "comedy"),
            RecommendationMetadata.normalizeGenres("ラブコメ"),
        )
        assertEquals(
            linkedSetOf("romance", "comedy"),
            genresOf("ラブコメ"),
        )
    }

    @Test
    fun `Japanese and Korean localized text fields select the correct filter kind`() {
        val japaneseGenre = TestTextFilter("ジャンル")
        val japaneseTag = TestTextFilter("タグ")

        val japaneseKind = applyGenreFilter(
            FilterList(japaneseGenre, japaneseTag),
            GenreIdentity(displayName = "恋愛", normalizedName = "romance"),
        )

        assertEquals(GenreFilterKind.TEXT_TAG, japaneseKind)
        assertEquals("恋愛", japaneseTag.state)
        assertEquals("", japaneseGenre.state)

        val koreanGenre = TestTextFilter("장르")
        val koreanKind = applyGenreFilter(
            FilterList(koreanGenre),
            GenreIdentity(displayName = "로맨스", normalizedName = "romance"),
        )

        assertEquals(GenreFilterKind.TEXT_GENRE, koreanKind)
        assertEquals("로맨스", koreanGenre.state)
    }

    @Test
    fun `Japanese structured option matches a canonical genre id`() {
        val japaneseRomance = TestCheckBox("恋愛")

        val kind = applyGenreFilter(
            FilterList(japaneseRomance),
            GenreIdentity(displayName = "爱情", normalizedName = "romance"),
        )

        assertEquals(GenreFilterKind.STRUCTURED, kind)
        assertTrue(japaneseRomance.state)
    }

    @Test
    fun `Japanese target and Chinese candidate rank through shared canonical ids`() {
        val candidate = SManga.create().apply {
            url = "candidate"
            title = "Candidate"
            genre = "爱情、校园"
            initialized = true
        }

        val targetGenres = genresOf("恋愛、学園")
        val ranked = RecommendationRanking.scoreCandidates(
            profile = RecommendationRanking.tagProfile(
                targetGenres = targetGenres,
                documentFrequency = emptyMap(),
                documentCount = 0,
            ),
            candidates = listOf(SimilarCandidate(candidate, CandidateEvidence(queryRank = 0))),
            documentFrequency = emptyMap(),
            documentCount = 0,
        ).map(RankedSimilarCandidate::manga)

        assertEquals(listOf(candidate), ranked)
    }

    @Test
    fun `Japanese language format and status labels are excluded`() {
        assertEquals(
            setOf("romance"),
            genresOf("日本語、日本マンガ、マンガ、連載中、完結済み、フルカラー、縦読み、恋愛"),
        )
    }

    @Test
    fun `explicit Chinese Japanese and Korean circles receive the group role`() {
        val manga = SManga.create().apply {
            author = "社團：星組; サークル：月組; 동인서클: 별빛"
        }

        val creators = RecommendationMetadata.extractCreators(manga)

        assertEquals(listOf("星組", "月組", "별빛"), creators.map(CreatorIdentity::displayName))
        assertTrue(creators.all { it.roles == setOf(CreatorRole.GROUP) })

        val artist = TestTextFilter("イラストレーター")
        val circle = TestTextFilter("サークル")
        val match = applyExactCreatorTextFilter(FilterList(artist, circle), creators.first())

        assertEquals(
            CreatorFilterMatch(CreatorRole.GROUP, CreatorFilterKind.GROUP, "サークル"),
            match,
        )
        assertEquals("星組", circle.state)
        assertEquals("", artist.state)
    }

    private fun genresOf(rawGenres: String): Set<String> {
        return RecommendationMetadata.extractGenres(
            SManga.create().apply { genre = rawGenres },
        )
    }

    private class TestTextFilter(name: String) : Filter.Text(name)

    private class TestCheckBox(name: String) : Filter.CheckBox(name)
}
